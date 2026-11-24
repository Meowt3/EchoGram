package com.echogram.app.data.telegram

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import com.echogram.app.BuildConfig
import com.echogram.app.data.model.AudioTrack
import com.echogram.app.data.model.CacheStats
import com.echogram.app.data.model.ChatKind
import com.echogram.app.data.model.ChatMessage
import com.echogram.app.data.model.ConnectionStatus
import com.echogram.app.data.model.ProxyItem
import com.echogram.app.data.model.ProxyType
import com.echogram.app.data.model.TelegramChat
import com.echogram.app.data.model.AuthState
import com.echogram.app.data.storage.LibraryStore
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import org.json.JSONArray

/**
 * Clean, race-free TDLib MTProto client manager for EchoGram.
 * Handles:
 * - Zero-friction auto-initialization from BuildConfig (with optional custom API override)
 * - Phone, QR-code, Code, Email, Registration, and 2FA password auth flows
 * - Live connection status & ConnectivityManager -> SetNetworkType synchronization
 * - Full paginated Main + Archive chat loading, public channel search, and real-time messaging
 * - Hybrid GetChatHistory + SearchChatMessages audiobook discovery
 * - Progressive chunked streaming with stale file_reference & stalled DownloadFile recovery
 */
class TelegramClientManager private constructor(private val context: Context) {

    private val authPrefs = context.getSharedPreferences("echogram_v2_auth", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var client: Client? = null

    @Volatile
    private var apiId: Int = 0

    @Volatile
    private var apiHash: String = ""

    @Volatile
    private var currentTdAuthState: TdApi.AuthorizationState? = null

    @Volatile
    private var myUserId: Long = 0L

    @Volatile
    private var lastSubmittedPhone: String = ""

    @Volatile
    private var reinitAfterClose: Boolean = false

    @Volatile
    private var isClosingClient: Boolean = false

    @Volatile
    private var pendingPhoneAfterReady: String? = null

    @Volatile
    private var pendingQrAfterReady: Boolean = false

    private val clientLifecycleLock = Any()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _isAuthBusy = MutableStateFlow(false)
    val isAuthBusy: StateFlow<Boolean> = _isAuthBusy.asStateFlow()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.CONNECTING)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _proxies = MutableStateFlow<List<ProxyItem>>(emptyList())
    val proxies: StateFlow<List<ProxyItem>> = _proxies.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<Pair<Long, TdApi.Message>>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<Pair<Long, TdApi.Message>> = _incomingMessages.asSharedFlow()

    private val _chatListInvalidated = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val chatListInvalidated: SharedFlow<Unit> = _chatListInvalidated.asSharedFlow()

    private val _downloadedTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val downloadedTracks: StateFlow<List<AudioTrack>> = _downloadedTracks.asStateFlow()

    private val _downloadedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedTrackIds: StateFlow<Set<String>> = _downloadedTrackIds.asStateFlow()

    private val _downloadingProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadingProgress: StateFlow<Map<String, Float>> = _downloadingProgress.asStateFlow()

    private val fileFlows = ConcurrentHashMap<Int, MutableStateFlow<TdApi.File>>()
    private val fileLocks = ConcurrentHashMap<Int, Mutex>()
    private val promotedStreamFileIds = ConcurrentHashMap.newKeySet<Int>()
    private val offlineWriteLock = Mutex()
    private val activeStreamReaders = AtomicInteger(0)

    @Volatile
    var activePlaybackFileId: Int = 0

    @Volatile
    var lastStreamError: String? = null

    private val tdlibDbDir: File by lazy { File(context.filesDir, "tdlib_v2_db").apply { mkdirs() } }
    private val tdlibFilesDir: File by lazy { File(context.filesDir, "tdlib_v2_files").apply { mkdirs() } }
    private val offlineDir: File by lazy { File(context.filesDir, "offline_audio_v2").apply { mkdirs() } }
    private val offlineIndexFile: File by lazy { File(context.filesDir, "offline_index_v2.json") }
    private val coversCacheDir: File by lazy { File(context.cacheDir, "covers_v2").apply { mkdirs() } }

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = pushCurrentNetworkType()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = pushCurrentNetworkType()
        override fun onLost(network: Network) = pushCurrentNetworkType()
    }

    init {
        registerNetworkMonitor()
        scope.launch {
            offlineWriteLock.withLock { reloadOfflineIndex() }
        }
        startWithStoredOrBuiltInCredentials()
    }

    private fun registerNetworkMonitor() {
        runCatching {
            connectivityManager?.registerDefaultNetworkCallback(networkCallback)
        }
    }

    private fun pushCurrentNetworkType() {
        val activeClient = client ?: return
        val caps = runCatching {
            connectivityManager?.getNetworkCapabilities(connectivityManager.activeNetwork)
        }.getOrNull()
        val tdNetType: TdApi.NetworkType = when {
            caps == null -> TdApi.NetworkTypeNone()
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> TdApi.NetworkTypeWiFi()
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> TdApi.NetworkTypeMobile()
            else -> TdApi.NetworkTypeOther()
        }
        activeClient.send(TdApi.SetNetworkType(tdNetType)) {}
    }

    fun setAppOnline(online: Boolean) {
        client?.send(TdApi.SetOption("online", TdApi.OptionValueBoolean(online))) {}
    }

    fun startWithStoredOrBuiltInCredentials() {
        val customId = authPrefs.getInt(KEY_CUSTOM_API_ID, 0)
        val customHash = authPrefs.getString(KEY_CUSTOM_API_HASH, "").orEmpty().trim()
        val builtInId = BuildConfig.TG_API_ID
        val builtInHash = BuildConfig.TG_API_HASH.trim()

        val (resolvedId, resolvedHash) = when {
            customId > 0 && customHash.isNotBlank() -> customId to customHash
            builtInId > 0 && builtInHash.isNotBlank() -> builtInId to builtInHash
            else -> 0 to ""
        }

        if (resolvedId <= 0 || resolvedHash.isBlank()) {
            _authState.value = AuthState.WaitingApiCredentials
            _isAuthBusy.value = false
            return
        }
        configureAndStart(resolvedId, resolvedHash, saveAsCustom = false)
    }

    fun configureAndStart(newApiId: Int, newApiHash: String, saveAsCustom: Boolean = true) {
        val cleanHash = newApiHash.trim()
        if (newApiId <= 0 || cleanHash.isBlank()) {
            _authError.value = "Укажите корректные API ID и API Hash."
            _authState.value = AuthState.WaitingApiCredentials
            return
        }

        synchronized(clientLifecycleLock) {
            apiId = newApiId
            apiHash = cleanHash
            if (saveAsCustom) {
                authPrefs.edit()
                    .putInt(KEY_CUSTOM_API_ID, newApiId)
                    .putString(KEY_CUSTOM_API_HASH, cleanHash)
                    .apply()
            }
            _authError.value = null

            val existing = client
            if (existing != null && !isClosingClient) {
                if (currentTdAuthState is TdApi.AuthorizationStateWaitTdlibParameters) {
                    sendTdlibParameters(existing)
                }
                return
            }

            if (isClosingClient) {
                reinitAfterClose = true
                return
            }

            _isAuthBusy.value = true
            createClientLocked()
        }
    }

    fun resetCustomApiCredentials() {
        authPrefs.edit()
            .remove(KEY_CUSTOM_API_ID)
            .remove(KEY_CUSTOM_API_HASH)
            .apply()
        startWithStoredOrBuiltInCredentials()
    }

    private fun createClientLocked() {
        Client.execute(TdApi.SetLogVerbosityLevel(1))
        client = Client.create(
            { update -> handleTdUpdate(update) },
            { err ->
                _isAuthBusy.value = false
                _authError.value = formatTdError(err.message)
            },
            { err ->
                _isAuthBusy.value = false
                _authError.value = formatTdError(err.message)
            }
        )
    }

    private fun sendTdlibParameters(targetClient: Client?) {
        if (apiId <= 0 || apiHash.isBlank()) {
            _isAuthBusy.value = false
            _authState.value = AuthState.WaitingApiCredentials
            return
        }
        val params = TdApi.SetTdlibParameters().apply {
            useTestDc = false
            databaseDirectory = tdlibDbDir.absolutePath
            filesDirectory = tdlibFilesDir.absolutePath
            databaseEncryptionKey = ByteArray(0)
            useFileDatabase = true
            useChatInfoDatabase = true
            useMessageDatabase = true
            useSecretChats = false
            this.apiId = this@TelegramClientManager.apiId
            this.apiHash = this@TelegramClientManager.apiHash
            systemLanguageCode = "ru"
            deviceModel = (Build.MODEL ?: "Android").ifBlank { "Android" }
            systemVersion = (Build.VERSION.RELEASE ?: "14").ifBlank { "14" }
            applicationVersion = BuildConfig.VERSION_NAME
        }
        targetClient?.send(params) { res ->
            if (res is TdApi.Error) {
                _isAuthBusy.value = false
                _authError.value = formatTdError(res.message)
                val up = res.message.orEmpty().uppercase()
                if (up.contains("API_ID") || up.contains("API_HASH")) {
                    _authState.value = AuthState.WaitingApiCredentials
                }
            }
        }
    }

    private fun handleTdUpdate(obj: TdApi.Object) {
        when (obj) {
            is TdApi.UpdateFile -> {
                val flow = fileFlows.getOrPut(obj.file.id) { MutableStateFlow(obj.file) }
                flow.value = obj.file
            }

            is TdApi.UpdateConnectionState -> {
                _connectionStatus.value = when (obj.state) {
                    is TdApi.ConnectionStateWaitingForNetwork -> ConnectionStatus.WAITING_NETWORK
                    is TdApi.ConnectionStateConnectingToProxy -> ConnectionStatus.CONNECTING_PROXY
                    is TdApi.ConnectionStateConnecting -> ConnectionStatus.CONNECTING
                    is TdApi.ConnectionStateUpdating -> ConnectionStatus.UPDATING
                    is TdApi.ConnectionStateReady -> ConnectionStatus.READY
                    else -> ConnectionStatus.CONNECTING
                }
            }

            is TdApi.UpdateNewMessage -> {
                _incomingMessages.tryEmit(obj.message.chatId to obj.message)
                _chatListInvalidated.tryEmit(Unit)
            }

            is TdApi.UpdateChatLastMessage -> {
                _chatListInvalidated.tryEmit(Unit)
            }

            is TdApi.UpdateAuthorizationState -> {
                handleAuthorizationState(obj.authorizationState)
            }
        }
    }

    private fun handleAuthorizationState(state: TdApi.AuthorizationState) {
        currentTdAuthState = state
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                sendTdlibParameters(client)
            }

            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                _isAuthBusy.value = false
                isClosingClient = false
                _authError.value = null
                _authState.value = AuthState.WaitingPhoneNumber
                pushCurrentNetworkType()
                refreshProxiesAsync()

                val queuedPhone = pendingPhoneAfterReady
                val queuedQr = pendingQrAfterReady
                pendingPhoneAfterReady = null
                pendingQrAfterReady = false

                when {
                    !queuedPhone.isNullOrBlank() -> submitPhoneNumber(queuedPhone)
                    queuedQr -> requestQrLogin()
                }
            }

            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> {
                _isAuthBusy.value = false
                _authError.value = null
                _authState.value = AuthState.WaitingQrConfirmation(state.link)
            }

            is TdApi.AuthorizationStateWaitCode -> {
                _isAuthBusy.value = false
                _authError.value = null
                val hint = when (state.codeInfo?.type) {
                    is TdApi.AuthenticationCodeTypeTelegramMessage -> "Код отправлен в приложение Telegram"
                    is TdApi.AuthenticationCodeTypeSms -> "Код отправлен по SMS"
                    is TdApi.AuthenticationCodeTypeCall -> "Ожидайте звонок с кодом"
                    is TdApi.AuthenticationCodeTypeFragment -> "Код отправлен через Fragment"
                    else -> "Проверьте чат Telegram или SMS"
                }
                _authState.value = AuthState.WaitingCode(lastSubmittedPhone, hint)
            }

            is TdApi.AuthorizationStateWaitEmailAddress -> {
                _isAuthBusy.value = false
                _authError.value = null
                _authState.value = AuthState.WaitingEmailAddress(state.allowAppleId)
            }

            is TdApi.AuthorizationStateWaitEmailCode -> {
                _isAuthBusy.value = false
                _authError.value = null
                _authState.value = AuthState.WaitingEmailCode(state.codeInfo?.emailAddressPattern.orEmpty())
            }

            is TdApi.AuthorizationStateWaitRegistration -> {
                _isAuthBusy.value = false
                _authError.value = null
                _authState.value = AuthState.WaitingRegistration
            }

            is TdApi.AuthorizationStateWaitPassword -> {
                _isAuthBusy.value = false
                _authError.value = null
                _authState.value = AuthState.WaitingPassword(state.passwordHint.orEmpty())
            }

            is TdApi.AuthorizationStateReady -> {
                _isAuthBusy.value = false
                isClosingClient = false
                pendingPhoneAfterReady = null
                pendingQrAfterReady = false
                _authError.value = null
                pushCurrentNetworkType()
                setAppOnline(true)
                val cachedName = authPrefs.getString(KEY_CACHED_NAME, "").orEmpty().ifBlank { "Telegram" }
                val cachedUser = authPrefs.getString(KEY_CACHED_USERNAME, "").orEmpty()
                val cachedPhone = authPrefs.getString(KEY_CACHED_PHONE, "").orEmpty()
                if (_authState.value !is AuthState.Authorized) {
                    _authState.value = AuthState.Authorized(
                        userId = myUserId,
                        displayName = cachedName,
                        username = cachedUser,
                        phoneMasked = cachedPhone
                    )
                }
                fetchCurrentUser()
                refreshProxiesAsync()
            }

            is TdApi.AuthorizationStateLoggingOut,
            is TdApi.AuthorizationStateClosing -> {
                _isAuthBusy.value = true
                isClosingClient = true
            }

            is TdApi.AuthorizationStateClosed -> {
                synchronized(clientLifecycleLock) {
                    client = null
                    currentTdAuthState = null
                    isClosingClient = false
                    fileFlows.clear()
                    fileLocks.clear()
                    promotedStreamFileIds.clear()
                    _isAuthBusy.value = false
                    _authError.value = null

                    if (reinitAfterClose && apiId > 0 && apiHash.isNotBlank()) {
                        reinitAfterClose = false
                        _isAuthBusy.value = true
                        createClientLocked()
                    } else if (apiId > 0 && apiHash.isNotBlank()) {
                        _authState.value = AuthState.WaitingPhoneNumber
                    } else {
                        _authState.value = AuthState.WaitingApiCredentials
                    }
                }
            }
        }
    }

    private fun fetchCurrentUser() {
        client?.send(TdApi.GetMe()) { res ->
            if (res is TdApi.User) {
                myUserId = res.id
                val name = listOf(res.firstName, res.lastName)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { "Telegram" }
                val uname = res.usernames?.activeUsernames?.firstOrNull().orEmpty()
                val phone = res.phoneNumber.orEmpty()
                val masked = if (phone.length >= 4) "+***${phone.takeLast(4)}" else ""
                authPrefs.edit()
                    .putString(KEY_CACHED_NAME, name)
                    .putString(KEY_CACHED_USERNAME, uname)
                    .putString(KEY_CACHED_PHONE, masked)
                    .apply()
                _authState.value = AuthState.Authorized(
                    userId = res.id,
                    displayName = name,
                    username = uname,
                    phoneMasked = masked
                )
                val photoFileId = res.profilePhoto?.small?.id ?: 0
                if (photoFileId > 0) {
                    scope.launch {
                        val path = downloadSmallFile(photoFileId)
                        val cur = _authState.value as? AuthState.Authorized ?: return@launch
                        if (!path.isNullOrBlank()) {
                            _authState.value = cur.copy(avatarPath = path)
                        }
                    }
                }
            }
        }
    }

    // ---- Auth Actions ----

    fun clearAuthError() {
        _authError.value = null
    }

    fun submitPhoneNumber(rawPhone: String) {
        val normalized = normalizePhone(rawPhone)
        if (normalized.length < 8) {
            _authError.value = "Введите корректный номер телефона, например +7 999 123-45-67"
            return
        }
        _authError.value = null
        lastSubmittedPhone = normalized

        synchronized(clientLifecycleLock) {
            if (isClosingClient || client == null || currentTdAuthState !is TdApi.AuthorizationStateWaitPhoneNumber) {
                pendingPhoneAfterReady = normalized
                pendingQrAfterReady = false
                _isAuthBusy.value = true
                if (client == null && !isClosingClient && apiId > 0 && apiHash.isNotBlank()) {
                    createClientLocked()
                }
                return
            }
        }

        sendAuthCommand(
            TdApi.SetAuthenticationPhoneNumber(
                normalized,
                TdApi.PhoneNumberAuthenticationSettings()
            )
        )
    }

    fun requestQrLogin() {
        _authError.value = null
        synchronized(clientLifecycleLock) {
            if (isClosingClient || client == null || currentTdAuthState !is TdApi.AuthorizationStateWaitPhoneNumber) {
                pendingQrAfterReady = true
                pendingPhoneAfterReady = null
                _isAuthBusy.value = true
                if (client == null && !isClosingClient && apiId > 0 && apiHash.isNotBlank()) {
                    createClientLocked()
                }
                return
            }
        }
        sendAuthCommand(TdApi.RequestQrCodeAuthentication(LongArray(0)))
    }

    fun submitCode(rawCode: String) {
        val digits = rawCode.filter { it.isDigit() }
        if (digits.length < 4) {
            _authError.value = "Введите код подтверждения"
            return
        }
        sendAuthCommand(TdApi.CheckAuthenticationCode(digits))
    }

    fun resendCode() {
        sendAuthCommand(TdApi.ResendAuthenticationCode())
    }

    fun submitEmailAddress(email: String) {
        val clean = email.trim()
        if (!clean.contains("@")) {
            _authError.value = "Введите корректный email"
            return
        }
        sendAuthCommand(TdApi.SetAuthenticationEmailAddress(clean))
    }

    fun submitEmailCode(code: String) {
        val clean = code.trim()
        if (clean.isEmpty()) {
            _authError.value = "Введите код из письма"
            return
        }
        sendAuthCommand(TdApi.CheckAuthenticationEmailCode(TdApi.EmailAddressAuthenticationCode(clean)))
    }

    fun submitRegistration(firstName: String, lastName: String) {
        if (firstName.isBlank()) {
            _authError.value = "Укажите имя"
            return
        }
        sendAuthCommand(TdApi.RegisterUser(firstName.trim(), lastName.trim(), false))
    }

    fun submitPassword(password: String) {
        if (password.isEmpty()) {
            _authError.value = "Введите облачный пароль 2FA"
            return
        }
        sendAuthCommand(TdApi.CheckAuthenticationPassword(password))
    }

    fun returnToPhoneInput() {
        _authError.value = null
        pendingPhoneAfterReady = null
        pendingQrAfterReady = false
        synchronized(clientLifecycleLock) {
            val active = client
            val st = currentTdAuthState
            if (active != null && (st is TdApi.AuthorizationStateWaitCode ||
                    st is TdApi.AuthorizationStateWaitPassword ||
                    st is TdApi.AuthorizationStateWaitOtherDeviceConfirmation ||
                    st is TdApi.AuthorizationStateWaitEmailAddress ||
                    st is TdApi.AuthorizationStateWaitEmailCode ||
                    st is TdApi.AuthorizationStateWaitRegistration)
            ) {
                reinitAfterClose = true
                isClosingClient = true
                _isAuthBusy.value = true
                active.send(TdApi.LogOut()) { res ->
                    if (res is TdApi.Error) {
                        // Fallback: close and re-open cleanly
                        active.send(TdApi.Close()) {}
                    }
                }
            } else {
                _isAuthBusy.value = false
                _authState.value = AuthState.WaitingPhoneNumber
            }
        }
    }

    fun logout() {
        _authError.value = null
        pendingPhoneAfterReady = null
        pendingQrAfterReady = false
        authPrefs.edit()
            .remove(KEY_CACHED_NAME)
            .remove(KEY_CACHED_USERNAME)
            .remove(KEY_CACHED_PHONE)
            .apply()
        synchronized(clientLifecycleLock) {
            reinitAfterClose = true
            isClosingClient = true
            _isAuthBusy.value = true
            client?.send(TdApi.LogOut()) { res ->
                if (res is TdApi.Error) {
                    isClosingClient = false
                    _isAuthBusy.value = false
                    _authError.value = formatTdError(res.message)
                }
            }
        }
    }

    private fun sendAuthCommand(fn: TdApi.Function<*>) {
        val active = client
        if (active == null) {
            _authError.value = "Клиент Telegram инициализируется, повторите через секунду"
            return
        }
        _isAuthBusy.value = true
        _authError.value = null
        active.send(fn) { res ->
            if (res is TdApi.Error) {
                _isAuthBusy.value = false
                _authError.value = formatTdError(res.message)
            }
        }
    }

    // ---- Proxies ----

    fun refreshProxiesAsync() {
        scope.launch { refreshProxies() }
    }

    suspend fun refreshProxies(): List<ProxyItem> = withContext(Dispatchers.IO) {
        val res = sendSuspend(TdApi.GetProxies()) as? TdApi.Proxies ?: return@withContext emptyList()
        val list = res.proxies.mapNotNull { p ->
            val type = when (p.type) {
                is TdApi.ProxyTypeMtproto -> ProxyType.MTPROTO
                is TdApi.ProxyTypeSocks5 -> ProxyType.SOCKS5
                is TdApi.ProxyTypeHttp -> ProxyType.HTTP
                else -> return@mapNotNull null
            }
            ProxyItem(
                id = p.id,
                server = p.server,
                port = p.port,
                type = type,
                isEnabled = p.isEnabled
            )
        }
        _proxies.value = list
        list
    }

    suspend fun addProxy(
        type: ProxyType,
        server: String,
        port: Int,
        username: String = "",
        password: String = "",
        secret: String = "",
        enable: Boolean = true
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val tdType: TdApi.ProxyType = when (type) {
            ProxyType.MTPROTO -> TdApi.ProxyTypeMtproto(secret.trim())
            ProxyType.SOCKS5 -> TdApi.ProxyTypeSocks5(username.trim(), password)
            ProxyType.HTTP -> TdApi.ProxyTypeHttp(username.trim(), password, false)
        }
        when (val res = sendSuspend(TdApi.AddProxy(server.trim(), port, enable, tdType))) {
            is TdApi.Proxy -> {
                refreshProxies()
                Result.success(Unit)
            }
            is TdApi.Error -> Result.failure(IllegalStateException(formatTdError(res.message)))
            else -> Result.failure(IllegalStateException("Не удалось добавить прокси"))
        }
    }

    suspend fun enableProxy(id: Int) = withContext(Dispatchers.IO) {
        sendSuspend(TdApi.EnableProxy(id))
        refreshProxies()
    }

    suspend fun disableAllProxies() = withContext(Dispatchers.IO) {
        sendSuspend(TdApi.DisableProxy())
        refreshProxies()
    }

    suspend fun removeProxy(id: Int) = withContext(Dispatchers.IO) {
        sendSuspend(TdApi.RemoveProxy(id))
        refreshProxies()
    }

    suspend fun pingProxy(id: Int) = withContext(Dispatchers.IO) {
        val res = withTimeoutOrNull(10_000L) { sendSuspend(TdApi.PingProxy(id)) }
        _proxies.value = _proxies.value.map { item ->
            if (item.id != id) item else when (res) {
                is TdApi.Seconds -> item.copy(pingMs = (res.seconds * 1000.0).roundToInt(), pingError = null)
                is TdApi.Error -> item.copy(pingMs = null, pingError = formatTdError(res.message))
                else -> item.copy(pingMs = null, pingError = "Таймаут")
            }
        }
    }

    // ---- Chats & Telegram Fork Discovery ----

    suspend fun loadAllChats(starredIds: Set<Long>): List<TelegramChat> = withContext(Dispatchers.IO) {
        // Paginate LoadChats on both Main and Archive lists so TDLib populates local chat cache completely
        paginateLoadChats(TdApi.ChatListMain())
        paginateLoadChats(TdApi.ChatListArchive())

        val mainIds = (withTimeoutOrNull(15_000L) {
            sendSuspend(TdApi.GetChats(TdApi.ChatListMain(), 200))
        } as? TdApi.Chats)?.chatIds?.toList().orEmpty()

        val archiveIds = (withTimeoutOrNull(10_000L) {
            sendSuspend(TdApi.GetChats(TdApi.ChatListArchive(), 100))
        } as? TdApi.Chats)?.chatIds?.toList().orEmpty()

        val archiveSet = archiveIds.toSet()
        val allIds = LinkedHashSet<Long>().apply {
            addAll(mainIds)
            addAll(archiveIds)
            addAll(starredIds)
        }

        val result = ArrayList<TelegramChat>(allIds.size)
        for (chatId in allIds) {
            val chat = withTimeoutOrNull(5_000L) {
                sendSuspend(TdApi.GetChat(chatId))
            } as? TdApi.Chat ?: continue
            result.add(
                mapTdChat(
                    chat = chat,
                    isArchived = chatId in archiveSet && chatId !in mainIds,
                    isStarred = chatId in starredIds
                )
            )
        }
        result.sortedWith(
            compareByDescending<TelegramChat> { it.kind == ChatKind.SAVED_MESSAGES }
                .thenByDescending { it.isStarredAudiobookSource }
                .thenByDescending { it.lastMessageDateSec }
        )
    }

    private suspend fun paginateLoadChats(chatList: TdApi.ChatList, maxPages: Int = 6) {
        repeat(maxPages) {
            val res = withTimeoutOrNull(8_000L) {
                sendSuspend(TdApi.LoadChats(chatList, 50))
            }
            if (res == null || res is TdApi.Error) return
        }
    }

    suspend fun searchGlobalChats(query: String, starredIds: Set<Long>): List<TelegramChat> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.length < 2) return@withContext emptyList()
        val ids = LinkedHashSet<Long>()
        (withTimeoutOrNull(8_000L) { sendSuspend(TdApi.SearchChats(clean, 30)) } as? TdApi.Chats)
            ?.chatIds?.forEach { ids.add(it) }
        (withTimeoutOrNull(10_000L) { sendSuspend(TdApi.SearchPublicChats(clean)) } as? TdApi.Chats)
            ?.chatIds?.forEach { ids.add(it) }

        ids.mapNotNull { cid ->
            val chat = withTimeoutOrNull(5_000L) { sendSuspend(TdApi.GetChat(cid)) } as? TdApi.Chat ?: return@mapNotNull null
            mapTdChat(chat, isArchived = false, isStarred = cid in starredIds)
        }
    }

    suspend fun joinChat(chatId: Long): Boolean = withContext(Dispatchers.IO) {
        val res = withTimeoutOrNull(10_000L) { sendSuspend(TdApi.JoinChat(chatId)) }
        res is TdApi.Ok
    }

    private suspend fun mapTdChat(chat: TdApi.Chat, isArchived: Boolean, isStarred: Boolean): TelegramChat {
        var username = ""
        var members = 0
        val kind = when (val t = chat.type) {
            is TdApi.ChatTypePrivate -> {
                if (t.userId == myUserId && myUserId != 0L) {
                    ChatKind.SAVED_MESSAGES
                } else {
                    val user = withTimeoutOrNull(4_000L) { sendSuspend(TdApi.GetUser(t.userId)) } as? TdApi.User
                    username = user?.usernames?.activeUsernames?.firstOrNull().orEmpty()
                    if (user?.type is TdApi.UserTypeBot) ChatKind.BOT else ChatKind.USER
                }
            }
            is TdApi.ChatTypeSupergroup -> {
                val sg = withTimeoutOrNull(4_000L) { sendSuspend(TdApi.GetSupergroup(t.supergroupId)) } as? TdApi.Supergroup
                username = sg?.usernames?.activeUsernames?.firstOrNull().orEmpty()
                members = sg?.memberCount ?: 0
                if (t.isChannel) ChatKind.CHANNEL else ChatKind.GROUP
            }
            is TdApi.ChatTypeBasicGroup -> ChatKind.GROUP
            else -> ChatKind.USER
        }

        val lastMsg = chat.lastMessage
        val preview = lastMsg?.let { summarizeMessageContent(it.content) }.orEmpty()
        val avatarFile = chat.photo?.small
        val avatarPath = avatarFile?.local?.takeIf { it.isDownloadingCompleted && it.path.isNotBlank() }?.path
        val title = if (kind == ChatKind.SAVED_MESSAGES) "Избранное (Saved Messages)" else chat.title

        return TelegramChat(
            chatId = chat.id,
            title = title,
            username = username,
            kind = kind,
            lastMessagePreview = preview,
            lastMessageDateSec = lastMsg?.date?.toLong() ?: 0L,
            unreadCount = chat.unreadCount,
            isArchived = isArchived,
            isStarredAudiobookSource = isStarred,
            avatarFileId = avatarFile?.id ?: 0,
            avatarPath = avatarPath,
            memberCount = members
        )
    }

    // ---- Chat History & Messaging ----

    data class ChatMessagesPage(
        val messages: List<ChatMessage>,
        val canSend: Boolean,
        val oldestMessageId: Long
    )

    suspend fun loadChatMessages(
        chatId: Long,
        channelTitle: String,
        channelUsername: String,
        fromMessageId: Long = 0L,
        limit: Int = 50
    ): ChatMessagesPage = withContext(Dispatchers.IO) {
        val chat = withTimeoutOrNull(6_000L) { sendSuspend(TdApi.GetChat(chatId)) } as? TdApi.Chat
        runCatching { withTimeoutOrNull(4_000L) { sendSuspend(TdApi.OpenChat(chatId)) } }

        val canSend = when (val t = chat?.type) {
            is TdApi.ChatTypePrivate -> true
            is TdApi.ChatTypeBasicGroup -> chat.permissions?.canSendBasicMessages ?: true
            is TdApi.ChatTypeSupergroup -> {
                if (!t.isChannel) {
                    chat.permissions?.canSendBasicMessages ?: true
                } else {
                    val sg = withTimeoutOrNull(4_000L) { sendSuspend(TdApi.GetSupergroup(t.supergroupId)) } as? TdApi.Supergroup
                    sg?.status is TdApi.ChatMemberStatusCreator ||
                        (sg?.status as? TdApi.ChatMemberStatusAdministrator)?.rights?.canPostMessages == true
                }
            }
            else -> false
        }

        val raw = ArrayList<TdApi.Message>()
        val seen = HashSet<Long>()
        suspend fun fetch(startId: Long): List<TdApi.Message> {
            val res = withTimeoutOrNull(12_000L) {
                sendSuspend(TdApi.GetChatHistory(chatId, startId, 0, limit, false))
            } as? TdApi.Messages ?: return emptyList()
            return res.messages.filterNotNull()
        }

        fetch(fromMessageId).forEach { if (seen.add(it.id)) raw.add(it) }
        if (raw.size in 1 until 15) {
            val oldest = raw.minOfOrNull { it.id } ?: 0L
            if (oldest > 0L && oldest != fromMessageId) {
                fetch(oldest).forEach { if (seen.add(it.id)) raw.add(it) }
            }
        }

        val senderCache = HashMap<Long, String>()
        val items = raw.map { msg ->
            val senderName = resolveSenderName(msg.senderId, chatId, chat?.title ?: channelTitle, senderCache)
            mapTdMessage(msg, chatId, chat?.title ?: channelTitle, channelUsername, senderName)
        }.sortedWith(compareBy<ChatMessage> { it.dateEpochSec }.thenBy { it.messageId })

        ChatMessagesPage(
            messages = items,
            canSend = canSend,
            oldestMessageId = items.firstOrNull()?.messageId ?: 0L
        )
    }

    fun closeChatAsync(chatId: Long) {
        client?.send(TdApi.CloseChat(chatId)) {}
    }

    suspend fun sendTextMessage(chatId: Long, text: String): Result<Unit> = withContext(Dispatchers.IO) {
        val clean = text.trim()
        if (clean.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Пустое сообщение"))
        val req = TdApi.SendMessage().apply {
            this.chatId = chatId
            inputMessageContent = TdApi.InputMessageText().apply {
                this.text = TdApi.FormattedText(clean, emptyArray())
            }
        }
        when (val res = withTimeoutOrNull(12_000L) { sendSuspend(req) }) {
            is TdApi.Message -> Result.success(Unit)
            is TdApi.Error -> Result.failure(IllegalStateException(formatTdError(res.message)))
            else -> Result.failure(IllegalStateException("Не удалось отправить сообщение"))
        }
    }

    // ---- Hybrid Audiobook Track Extraction ----

    suspend fun loadChatAudioTracks(
        chatId: Long,
        channelTitle: String,
        channelUsername: String = "",
        maxTracks: Int = 600
    ): List<AudioTrack> = withContext(Dispatchers.IO) {
        val seenMsgIds = HashSet<Long>()
        val tracks = ArrayList<AudioTrack>()

        runCatching { withTimeoutOrNull(5_000L) { sendSuspend(TdApi.OpenChat(chatId)) } }
        try {
            // Pass 1: Hybrid recent GetChatHistory pass (catches brand-new posts before search index catches up)
            var histFromId = 0L
            repeat(3) {
                val hist = withTimeoutOrNull(10_000L) {
                    sendSuspend(TdApi.GetChatHistory(chatId, histFromId, 0, 50, false))
                } as? TdApi.Messages ?: return@repeat
                val batch = hist.messages.filterNotNull()
                if (batch.isEmpty()) return@repeat
                for (msg in batch) {
                    if (!seenMsgIds.add(msg.id)) continue
                    parseAudioTrack(msg, chatId, channelTitle, channelUsername)?.let { tracks.add(it) }
                }
                val lastId = batch.last().id
                if (lastId == histFromId) return@repeat
                histFromId = lastId
            }

            // Pass 2: SearchChatMessages across Audio, Document, VoiceNote
            val filters = listOf(
                TdApi.SearchMessagesFilterAudio(),
                TdApi.SearchMessagesFilterDocument(),
                TdApi.SearchMessagesFilterVoiceNote()
            )
            for (filter in filters) {
                var fromId = 0L
                var pages = 0
                while (tracks.size < maxTracks && pages < 8) {
                    pages++
                    val found = withTimeoutOrNull(15_000L) {
                        sendSuspend(
                            TdApi.SearchChatMessages().apply {
                                this.chatId = chatId
                                this.query = ""
                                this.fromMessageId = fromId
                                this.offset = 0
                                this.limit = 100
                                this.filter = filter
                            }
                        )
                    } as? TdApi.FoundChatMessages ?: break
                    val batch = found.messages.filterNotNull()
                    if (batch.isEmpty()) break
                    for (msg in batch) {
                        if (!seenMsgIds.add(msg.id)) continue
                        parseAudioTrack(msg, chatId, channelTitle, channelUsername)?.let { tracks.add(it) }
                    }
                    val lastId = batch.last().id
                    if (lastId == fromId) break
                    fromId = lastId
                }
            }
        } finally {
            client?.send(TdApi.CloseChat(chatId)) {}
        }

        tracks.sortedByDescending { it.dateEpochSec }
    }

    fun parseAudioTrack(
        msg: TdApi.Message,
        chatId: Long,
        channelTitle: String,
        channelUsername: String
    ): AudioTrack? {
        return when (val c = msg.content) {
            is TdApi.MessageAudio -> {
                val audio = c.audio
                val file = audio.audio
                val fName = audio.fileName.orEmpty()
                if (!isSupportedAudioFormat(fName, audio.mimeType.orEmpty(), isExplicitAudio = true)) return null
                val rawTitle = audio.title.orEmpty().trim()
                    .ifBlank { fName.substringBeforeLast('.').replace('_', ' ').trim() }
                    .ifBlank { "Аудиокнига #${msg.id}" }
                val author = audio.performer.orEmpty().trim().ifBlank { channelTitle }
                val thumbFile = audio.albumCoverThumbnail?.file
                val thumbPath = thumbFile?.local?.takeIf { it.isDownloadingCompleted && it.path.isNotBlank() }?.path
                AudioTrack(
                    trackId = "${chatId}_${msg.id}",
                    chatId = chatId,
                    messageId = msg.id,
                    fileId = file.id,
                    title = rawTitle,
                    author = author,
                    channelTitle = channelTitle,
                    channelUsername = channelUsername,
                    durationSec = audio.duration.toLong().coerceAtLeast(0L),
                    fileSizeBytes = file.size.takeIf { it > 0L } ?: file.expectedSize.coerceAtLeast(0L),
                    fileName = fName,
                    mimeType = "", // Leave blank so Media3 DefaultExtractorsFactory sniffs container headers accurately
                    dateEpochSec = msg.date.toLong(),
                    coverFileId = thumbFile?.id ?: 0,
                    coverPath = thumbPath,
                    caption = c.caption?.text.orEmpty()
                )
            }

            is TdApi.MessageDocument -> {
                val doc = c.document
                val fName = doc.fileName.orEmpty()
                val mime = doc.mimeType.orEmpty()
                if (!isSupportedAudioFormat(fName, mime, isExplicitAudio = false)) return null
                val file = doc.document
                val cleanTitle = fName.substringBeforeLast('.').replace('_', ' ').trim()
                    .ifBlank { c.caption?.text?.lineSequence()?.firstOrNull()?.trim().orEmpty() }
                    .ifBlank { "Аудиофайл #${msg.id}" }
                val thumbFile = doc.thumbnail?.file
                val thumbPath = thumbFile?.local?.takeIf { it.isDownloadingCompleted && it.path.isNotBlank() }?.path
                AudioTrack(
                    trackId = "${chatId}_${msg.id}",
                    chatId = chatId,
                    messageId = msg.id,
                    fileId = file.id,
                    title = cleanTitle,
                    author = channelTitle,
                    channelTitle = channelTitle,
                    channelUsername = channelUsername,
                    durationSec = 0L,
                    fileSizeBytes = file.size.takeIf { it > 0L } ?: file.expectedSize.coerceAtLeast(0L),
                    fileName = fName,
                    mimeType = "",
                    dateEpochSec = msg.date.toLong(),
                    coverFileId = thumbFile?.id ?: 0,
                    coverPath = thumbPath,
                    caption = c.caption?.text.orEmpty()
                )
            }

            is TdApi.MessageVoiceNote -> {
                val voice = c.voiceNote
                val file = voice.voice
                val captionTitle = c.caption?.text?.lineSequence()?.firstOrNull()?.trim().orEmpty()
                AudioTrack(
                    trackId = "${chatId}_${msg.id}",
                    chatId = chatId,
                    messageId = msg.id,
                    fileId = file.id,
                    title = captionTitle.ifBlank { "Голосовая запись #${msg.id}" },
                    author = channelTitle,
                    channelTitle = channelTitle,
                    channelUsername = channelUsername,
                    durationSec = voice.duration.toLong().coerceAtLeast(0L),
                    fileSizeBytes = file.size.takeIf { it > 0L } ?: file.expectedSize.coerceAtLeast(0L),
                    fileName = "voice_${msg.id}.ogg",
                    mimeType = "",
                    dateEpochSec = msg.date.toLong(),
                    caption = c.caption?.text.orEmpty()
                )
            }

            else -> null
        }
    }

    // ---- Progressive TDLib Audio Streaming ----

    suspend fun resolvePlayableFile(fileId: Int, chatId: Long, messageId: Long): TdApi.File? = withContext(Dispatchers.IO) {
        var file = withTimeoutOrNull(8_000L) { sendSuspend(TdApi.GetFile(fileId)) } as? TdApi.File
        val isCold = file == null || (!file.local.isDownloadingCompleted && (file.local.downloadedPrefixSize == 0L || file.size == 0L))
        if (isCold && chatId != 0L && messageId > 0L) {
            val refreshed = refreshTrackFileFromMessage(chatId, messageId)
            if (refreshed != null) file = refreshed
        }
        file
    }

    suspend fun refreshTrackFileFromMessage(chatId: Long, messageId: Long): TdApi.File? = withContext(Dispatchers.IO) {
        val msg = withTimeoutOrNull(10_000L) { sendSuspend(TdApi.GetMessage(chatId, messageId)) } as? TdApi.Message
            ?: return@withContext null
        when (val c = msg.content) {
            is TdApi.MessageAudio -> c.audio.audio
            is TdApi.MessageDocument -> c.document.document
            is TdApi.MessageVoiceNote -> c.voiceNote.voice
            else -> null
        }
    }

    suspend fun readAudioStreamBytes(
        fileId: Int,
        expectedSize: Long,
        offset: Long,
        buffer: ByteArray,
        outOffset: Int,
        maxLength: Int,
        chatId: Long,
        messageId: Long,
        onResolvedFileId: (Int) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        activeStreamReaders.incrementAndGet()
        try {
            var effFileId = fileId
            lastStreamError = null
            val lock = fileLocks.getOrPut(effFileId) { Mutex() }

            var file = withTimeoutOrNull(6_000L) { sendSuspend(TdApi.GetFile(effFileId)) } as? TdApi.File
            if (file == null && chatId != 0L && messageId > 0L) {
                file = refreshTrackFileFromMessage(chatId, messageId)
                if (file != null && file.id > 0) {
                    effFileId = file.id
                    onResolvedFileId(effFileId)
                }
            }
            if (file == null) {
                lastStreamError = "Файл Telegram недоступен"
                return@withContext -2
            }

            if (file.size > 0L && offset >= file.size) return@withContext 0

            val flow = fileFlows.getOrPut(effFileId) { MutableStateFlow(file) }
            flow.value = file

            var lastDownloadOffset = -1L
            var lastPrefixBytes = -1L
            var lastProgressMs = System.currentTimeMillis()
            var retriedStall = false

            while (true) {
                val cur = flow.value
                val path = cur.local.path
                val dlStart = cur.local.downloadOffset
                val dlEnd = dlStart + cur.local.downloadedPrefixSize
                val isComplete = cur.local.isDownloadingCompleted

                if (path.isNotBlank() && (isComplete || (offset >= dlStart && offset < dlEnd))) {
                    val f = File(path)
                    if (f.exists()) {
                        val avail = if (isComplete) {
                            (f.length() - offset).coerceAtLeast(0L)
                        } else {
                            (dlEnd - offset).coerceAtLeast(0L)
                        }
                        if (avail > 0L) {
                            val toRead = minOf(maxLength.toLong(), avail).toInt()
                            val n = RandomAccessFile(f, "r").use { raf ->
                                raf.seek(offset)
                                raf.read(buffer, outOffset, toRead)
                            }
                            if (n > 0) return@withContext n
                        } else if (isComplete) {
                            return@withContext 0
                        }
                    }
                }

                // Request download from requested offset if not covering offset
                val coversOffset = cur.local.isDownloadingActive && offset >= dlStart && offset <= dlEnd + 512 * 1024L
                if (!coversOffset && !isComplete) {
                    lock.withLock {
                        val safeOffset = offset.coerceAtLeast(0L)
                        val dlRes = withTimeoutOrNull(8_000L) {
                            sendSuspend(TdApi.DownloadFile(effFileId, 32, safeOffset, 0L, false))
                        }
                        if (dlRes is TdApi.File) {
                            flow.value = dlRes
                            promotedStreamFileIds.add(effFileId)
                            lastDownloadOffset = dlRes.local.downloadOffset
                            lastPrefixBytes = dlRes.local.downloadedPrefixSize
                            lastProgressMs = System.currentTimeMillis()
                        } else if (dlRes is TdApi.Error) {
                            lastStreamError = formatTdError(dlRes.message)
                        }
                    }
                }

                // Reset stall timer when downloadOffset changes on seek
                if (dlStart != lastDownloadOffset || cur.local.downloadedPrefixSize != lastPrefixBytes) {
                    lastDownloadOffset = dlStart
                    lastPrefixBytes = cur.local.downloadedPrefixSize
                    lastProgressMs = System.currentTimeMillis()
                }

                // Wait for next file update or timeout
                val updated = withTimeoutOrNull(2_000L) {
                    flow.first { f ->
                        f.local.isDownloadingCompleted ||
                            (f.local.path.isNotBlank() && offset >= f.local.downloadOffset && offset < f.local.downloadOffset + f.local.downloadedPrefixSize) ||
                            f.local.downloadedPrefixSize != cur.local.downloadedPrefixSize ||
                            !f.local.isDownloadingActive
                    }
                }

                val latest = updated ?: flow.value
                if (latest.local.isDownloadingCompleted && latest.size > 0L && offset >= latest.size) {
                    return@withContext 0
                }
                if (latest.size == 0L && !latest.local.isDownloadingActive && offset >= latest.local.downloadOffset + latest.local.downloadedPrefixSize && latest.local.downloadedPrefixSize > 0L) {
                    return@withContext 0
                }

                val stalledMs = System.currentTimeMillis() - lastProgressMs
                if (stalledMs > 11_000L) {
                    if (!retriedStall) {
                        retriedStall = true
                        // Cancel stalled download before refreshing message & restarting (tdlib/td#2585)
                        withTimeoutOrNull(4_000L) { sendSuspend(TdApi.CancelDownloadFile(effFileId, false)) }
                        if (chatId != 0L && messageId > 0L) {
                            val fresh = refreshTrackFileFromMessage(chatId, messageId)
                            if (fresh != null && fresh.id > 0) {
                                effFileId = fresh.id
                                onResolvedFileId(effFileId)
                                fileFlows.getOrPut(effFileId) { MutableStateFlow(fresh) }.value = fresh
                            }
                        }
                        withTimeoutOrNull(6_000L) {
                            sendSuspend(TdApi.DownloadFile(effFileId, 32, offset.coerceAtLeast(0L), 0L, false))
                        }
                        lastProgressMs = System.currentTimeMillis()
                    } else {
                        lastStreamError = "Превышено время ожидания загрузки из Telegram"
                        return@withContext -2
                    }
                }
            }
            @Suppress("UNREACHABLE_CODE")
            0
        } catch (ce: CancellationException) {
            throw ce
        } finally {
            activeStreamReaders.decrementAndGet()
        }
    }

    // ---- Offline Downloads & Covers ----

    fun getOfflineAudioFile(trackId: String): File? {
        val f = File(offlineDir, "${trackId.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.audio")
        return f.takeIf { it.exists() && it.length() > 0L }
    }

    suspend fun downloadTrackOffline(track: AudioTrack): Result<Unit> = withContext(Dispatchers.IO) {
        if (getOfflineAudioFile(track.trackId) != null) return@withContext Result.success(Unit)
        var fileId = track.fileId
        val fresh = refreshTrackFileFromMessage(track.chatId, track.messageId)
        if (fresh != null && fresh.id > 0) fileId = fresh.id

        _downloadingProgress.value = _downloadingProgress.value + (track.trackId to 0.02f)
        try {
            val initFile = withTimeoutOrNull(10_000L) {
                sendSuspend(TdApi.DownloadFile(fileId, 16, 0L, 0L, false))
            } as? TdApi.File ?: return@withContext Result.failure(IllegalStateException("Не удалось начать скачивание"))

            val flow = fileFlows.getOrPut(fileId) { MutableStateFlow(initFile) }
            flow.value = initFile

            while (true) {
                val cur = flow.value
                val total = cur.size.takeIf { it > 0L } ?: cur.expectedSize.coerceAtLeast(1L)
                val prog = (cur.local.downloadedSize.toFloat() / total.toFloat()).coerceIn(0.02f, 0.99f)
                _downloadingProgress.value = _downloadingProgress.value + (track.trackId to prog)

                if (cur.local.isDownloadingCompleted && cur.local.path.isNotBlank()) {
                    val src = File(cur.local.path)
                    if (src.exists() && src.length() > 0L) {
                        val dst = File(offlineDir, "${track.trackId.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.audio")
                        src.copyTo(dst, overwrite = true)
                        offlineWriteLock.withLock {
                            val updated = (_downloadedTracks.value.filterNot { it.trackId == track.trackId } + track)
                            saveOfflineIndex(updated)
                            _downloadedTracks.value = updated
                            _downloadedTrackIds.value = updated.map { it.trackId }.toSet()
                        }
                        return@withContext Result.success(Unit)
                    }
                }
                delay(400L)
            }
            @Suppress("UNREACHABLE_CODE")
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _downloadingProgress.value = _downloadingProgress.value - track.trackId
        }
    }

    suspend fun removeOfflineTrack(trackId: String) = withContext(Dispatchers.IO) {
        getOfflineAudioFile(trackId)?.delete()
        offlineWriteLock.withLock {
            val updated = _downloadedTracks.value.filterNot { it.trackId == trackId }
            saveOfflineIndex(updated)
            _downloadedTracks.value = updated
            _downloadedTrackIds.value = updated.map { it.trackId }.toSet()
        }
    }

    suspend fun downloadSmallFile(fileId: Int): String? = withContext(Dispatchers.IO) {
        if (fileId <= 0) return@withContext null
        // Yield background cover downloads when active audio streaming is buffering
        if (activeStreamReaders.get() > 0) delay(350L)
        val res = withTimeoutOrNull(12_000L) {
            sendSuspend(TdApi.DownloadFile(fileId, 8, 0L, 0L, true))
        } as? TdApi.File ?: return@withContext null
        val path = res.local.path
        if (path.isNotBlank() && File(path).exists()) path else null
    }

    suspend fun computeCacheStats(): CacheStats = withContext(Dispatchers.IO) {
        CacheStats(
            tdlibBytes = dirSize(tdlibFilesDir),
            offlineBytes = dirSize(offlineDir),
            coversBytes = dirSize(coversCacheDir)
        )
    }

    suspend fun clearStreamingCache(): CacheStats = withContext(Dispatchers.IO) {
        runCatching { tdlibFilesDir.listFiles()?.forEach { it.deleteRecursively() } }
        runCatching { coversCacheDir.listFiles()?.forEach { it.deleteRecursively() } }
        computeCacheStats()
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun reloadOfflineIndex() {
        if (!offlineIndexFile.exists()) return
        runCatching {
            val arr = JSONArray(offlineIndexFile.readText())
            val list = buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { obj ->
                        val t = LibraryStore.decodeTrack(obj)
                        if (getOfflineAudioFile(t.trackId) != null) add(t)
                    }
                }
            }.distinctBy { it.trackId }
            _downloadedTracks.value = list
            _downloadedTrackIds.value = list.map { it.trackId }.toSet()
        }
    }

    private fun saveOfflineIndex(tracks: List<AudioTrack>) {
        val arr = JSONArray()
        tracks.forEach { arr.put(LibraryStore.encodeTrack(it)) }
        offlineIndexFile.writeText(arr.toString())
    }

    // ---- Helpers ----

    private suspend fun sendSuspend(fn: TdApi.Function<*>): TdApi.Object? {
        val active = client ?: return null
        val deferred = CompletableDeferred<TdApi.Object?>()
        active.send(fn) { result ->
            if (!deferred.isCompleted) deferred.complete(result)
        }
        return deferred.await()
    }

    private suspend fun resolveSenderName(
        sender: TdApi.MessageSender?,
        chatId: Long,
        fallbackChatTitle: String,
        cache: MutableMap<Long, String>
    ): String = when (sender) {
        is TdApi.MessageSenderUser -> cache.getOrPut(sender.userId) {
            val u = withTimeoutOrNull(3_000L) { sendSuspend(TdApi.GetUser(sender.userId)) } as? TdApi.User
            if (u != null) listOf(u.firstName, u.lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Пользователь" } else ""
        }
        is TdApi.MessageSenderChat -> if (sender.chatId == chatId) fallbackChatTitle else cache.getOrPut(sender.chatId) {
            val c = withTimeoutOrNull(3_000L) { sendSuspend(TdApi.GetChat(sender.chatId)) } as? TdApi.Chat
            c?.title.orEmpty()
        }
        else -> ""
    }

    private fun mapTdMessage(
        msg: TdApi.Message,
        chatId: Long,
        channelTitle: String,
        channelUsername: String,
        senderName: String
    ): ChatMessage {
        val track = parseAudioTrack(msg, chatId, channelTitle, channelUsername)
        var text = ""
        var mediaLabel = ""
        var photoFileId = 0
        var photoPath: String? = null

        when (val c = msg.content) {
            is TdApi.MessageText -> text = c.text?.text.orEmpty()
            is TdApi.MessagePhoto -> {
                text = c.caption?.text.orEmpty()
                val sz = c.photo.sizes.lastOrNull { it.width <= 1280 } ?: c.photo.sizes.lastOrNull()
                if (sz != null) {
                    photoFileId = sz.photo.id
                    if (sz.photo.local.isDownloadingCompleted && sz.photo.local.path.isNotBlank()) {
                        photoPath = sz.photo.local.path
                    }
                }
                if (photoFileId <= 0) mediaLabel = "Фотография"
            }
            is TdApi.MessageAudio -> text = c.caption?.text.orEmpty()
            is TdApi.MessageDocument -> {
                text = c.caption?.text.orEmpty()
                if (track == null) mediaLabel = "Файл: ${c.document.fileName}"
            }
            is TdApi.MessageVoiceNote -> text = c.caption?.text.orEmpty()
            is TdApi.MessageVideo -> {
                text = c.caption?.text.orEmpty()
                mediaLabel = "Видео"
            }
            else -> mediaLabel = "Вложение"
        }

        return ChatMessage(
            messageId = msg.id,
            chatId = chatId,
            senderName = senderName,
            isOutgoing = msg.isOutgoing,
            dateEpochSec = msg.date.toLong(),
            text = text,
            mediaLabel = mediaLabel,
            photoFileId = photoFileId,
            photoPath = photoPath,
            audioTrack = track
        )
    }

    private fun summarizeMessageContent(content: TdApi.MessageContent): String = when (content) {
        is TdApi.MessageText -> content.text?.text.orEmpty().replace('\n', ' ').take(90)
        is TdApi.MessageAudio -> "🎧 ${content.audio.title.ifBlank { content.audio.fileName }}"
        is TdApi.MessageVoiceNote -> "🎤 Голосовое сообщение"
        is TdApi.MessageDocument -> "📎 ${content.document.fileName}"
        is TdApi.MessagePhoto -> "🖼 Фото ${content.caption?.text.orEmpty().take(50)}"
        is TdApi.MessageVideo -> "🎬 Видео"
        else -> "Сообщение"
    }

    private fun isSupportedAudioFormat(fileName: String, mimeType: String, isExplicitAudio: Boolean): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext in UNSUPPORTED_EXTENSIONS) return false
        if (ext in SUPPORTED_AUDIO_EXTENSIONS) return true
        if (isExplicitAudio && ext.isBlank()) return true
        val mime = mimeType.lowercase()
        return mime.startsWith("audio/") && !mime.contains("wma") && !mime.contains("aiff")
    }

    private fun normalizePhone(raw: String): String {
        val trimmed = raw.trim()
        val hasPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isEmpty()) return ""
        if (!hasPlus) {
            if (digits.length == 11 && digits.startsWith("8")) return "+7${digits.substring(1)}"
            if (digits.length == 10 && digits.startsWith("9")) return "+7$digits"
        }
        return "+$digits"
    }

    private fun formatTdError(raw: String?): String {
        val msg = raw.orEmpty()
        val up = msg.uppercase()
        return when {
            up.contains("PHONE_NUMBER_INVALID") -> "Неверный формат номера телефона"
            up.contains("PHONE_CODE_INVALID") -> "Неверный код подтверждения"
            up.contains("PHONE_CODE_EXPIRED") -> "Срок действия кода истёк — запросите новый"
            up.contains("PASSWORD_HASH_INVALID") -> "Неверный облачный пароль 2FA"
            up.contains("FLOOD_WAIT") -> "Слишком много попыток — подождите немного и попробуйте снова"
            up.contains("API_ID_INVALID") || up.contains("API_HASH") -> "Некорректные ключи Telegram API"
            msg.isBlank() -> "Ошибка соединения с Telegram"
            else -> msg
        }
    }

    companion object {
        private const val KEY_CUSTOM_API_ID = "custom_api_id"
        private const val KEY_CUSTOM_API_HASH = "custom_api_hash"
        private const val KEY_CACHED_NAME = "cached_user_name"
        private const val KEY_CACHED_USERNAME = "cached_username"
        private const val KEY_CACHED_PHONE = "cached_phone"

        private val SUPPORTED_AUDIO_EXTENSIONS = setOf(
            "mp3", "m4a", "m4b", "aac", "ogg", "oga", "opus", "flac", "wav", "amr", "mka", "webm", "3gp"
        )
        private val UNSUPPORTED_EXTENSIONS = setOf("wma", "ape", "wv", "aiff", "aif", "alac")

        @Volatile
        private var instance: TelegramClientManager? = null

        fun getInstance(context: Context): TelegramClientManager =
            instance ?: synchronized(this) {
                instance ?: TelegramClientManager(context.applicationContext).also { instance = it }
            }
    }
}
