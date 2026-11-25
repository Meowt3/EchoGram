package com.tgaudiobooks.app.data.telegram

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import com.tgaudiobooks.app.data.model.TelegramConnectionStatus
import coil.Coil
import com.tgaudiobooks.app.data.model.AudioTrack
import com.tgaudiobooks.app.data.model.ParsedProxyLink
import com.tgaudiobooks.app.data.model.SourceType
import com.tgaudiobooks.app.data.model.StorageCacheStats
import com.tgaudiobooks.app.data.model.TelegramAuthState
import com.tgaudiobooks.app.data.model.TelegramChannel
import com.tgaudiobooks.app.data.model.TelegramChatMessage
import com.tgaudiobooks.app.data.model.TelegramProxyItem
import com.tgaudiobooks.app.data.model.TelegramProxyType
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import org.json.JSONArray
import org.json.JSONObject

/**
 * Full TDLib MTProto client repository supporting:
 * - Phone/code/2FA authentication
 * - Incremental concurrent channel/bot/chat track loading (batch streaming)
 * - Progressive chunked TDLib streaming for Media3 ExoPlayer (instant playback + seek)
 * - Multi-layer HD cover art extraction (HD thumb, ID3v2/MP4 embedded cover, 640px channel avatar)
 */
class TelegramRepository(private val context: Context) {

    private val authPrefs = context.getSharedPreferences("echogram_tg_auth", Context.MODE_PRIVATE)
    private var client: Client? = null
    private var apiId: Int = 0
    private var apiHash: String = ""
    private var lastSubmittedPhone: String = ""
    private var currentTdAuthState: TdApi.AuthorizationState? = null
    private var reinitAfterClose: Boolean = false
    @Volatile
    private var isClosingClient: Boolean = false
    @Volatile
    private var pendingPhoneAfterClose: String? = null
    @Volatile
    private var pendingQrAfterClose: Boolean = false

    private val _connectionStatus = MutableStateFlow(TelegramConnectionStatus.CONNECTING)
    val connectionStatus: StateFlow<TelegramConnectionStatus> = _connectionStatus.asStateFlow()

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = pushCurrentNetworkType()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = pushCurrentNetworkType()
        override fun onLost(network: Network) = pushCurrentNetworkType()
    }

    private fun pushCurrentNetworkType() {
        val activeClient = client ?: return
        val caps = runCatching { connectivityManager?.getNetworkCapabilities(connectivityManager.activeNetwork) }.getOrNull()
        val tdNet: TdApi.NetworkType = when {
            caps == null -> TdApi.NetworkTypeNone()
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> TdApi.NetworkTypeWiFi()
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> TdApi.NetworkTypeMobile()
            else -> TdApi.NetworkTypeOther()
        }
        activeClient.send(TdApi.SetNetworkType(tdNet)) {}
    }

    fun setAppOnline(online: Boolean) {
        client?.send(TdApi.SetOption("online", TdApi.OptionValueBoolean(online))) {}
    }

    private val _authState = MutableStateFlow<TelegramAuthState>(TelegramAuthState.Idle)
    val authState: StateFlow<TelegramAuthState> = _authState.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _isAuthBusy = MutableStateFlow(false)
    val isAuthBusy: StateFlow<Boolean> = _isAuthBusy.asStateFlow()

    private val _proxies = MutableStateFlow<List<TelegramProxyItem>>(emptyList())
    val proxies: StateFlow<List<TelegramProxyItem>> = _proxies.asStateFlow()

    private val offlineAudioDir: File by lazy {
        File(context.filesDir, "offline_audio").apply { mkdirs() }
    }
    private val offlineIndexFile: File by lazy {
        File(context.filesDir, "offline_tracks.json")
    }
    private val channelCacheDir: File by lazy {
        File(context.filesDir, "channel_cache").apply { mkdirs() }
    }

    private val _downloadedTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val downloadedTracks: StateFlow<List<AudioTrack>> = _downloadedTracks.asStateFlow()

    private val _downloadedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedTrackIds: StateFlow<Set<String>> = _downloadedTrackIds.asStateFlow()

    private val _downloadingProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadingProgress: StateFlow<Map<String, Float>> = _downloadingProgress.asStateFlow()

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val offlineWriteLock = Mutex()
    private val channelCacheLock = Mutex()

    private val fileFlows = ConcurrentHashMap<Int, MutableStateFlow<TdApi.File>>()
    private val activeOfflineFileIds = ConcurrentHashMap.newKeySet<Int>()
    private val promotedStreamFileIds = ConcurrentHashMap.newKeySet<Int>()
    private val fileLocks = ConcurrentHashMap<Int, Mutex>()
    private val coverLocks = ConcurrentHashMap<String, Mutex>()
    private val cachedCoverIndex = ConcurrentHashMap<String, String>()
    private val channelPhotoCache = ConcurrentHashMap<String, String>()
    private val channelPhotoLocks = ConcurrentHashMap<String, Mutex>()
    private val failedCoverUntil = ConcurrentHashMap<String, Long>()
    private val bgHeaderSemaphore = Semaphore(2)
    private val activeStreamReaders = AtomicInteger(0)

    @Volatile
    var activePlaybackFileId: Int = 0

    @Volatile
    var lastStreamError: String? = null

    private val hqCoversDir: File by lazy {
        File(context.cacheDir, "hq_covers").apply { mkdirs() }
    }

    init {
        runCatching { connectivityManager?.registerDefaultNetworkCallback(networkCallback) }
        initializeFromStoredOrDefaults()
        repoScope.launch {
            rebuildCachedCoverIndex()
            offlineWriteLock.withLock {
                refreshOfflineIndexState()
            }
        }
    }

    private fun rebuildCachedCoverIndex() {
        runCatching {
            hqCoversDir.listFiles()?.forEach { file ->
                if (file.isFile && file.length() > 256L) {
                    val base = file.nameWithoutExtension
                    when {
                        base.startsWith("hq_") -> {
                            val key = base.removePrefix("hq_")
                            if (key.isNotEmpty()) cachedCoverIndex[key] = file.absolutePath
                        }
                        base.startsWith("ch_") -> {
                            val key = base.removePrefix("ch_")
                            if (key.isNotEmpty()) channelPhotoCache[key] = file.absolutePath
                        }
                    }
                }
            }
        }
    }

    fun hasConfiguredCredentials(): Boolean {
        val savedId = authPrefs.getInt(KEY_API_ID, 0)
        val savedHash = authPrefs.getString(KEY_API_HASH, "").orEmpty().trim()
        if (savedId > 0 && savedHash.isNotBlank()) return true
        val defId = TelegramDefaults.getApiId(context)
        val defHash = TelegramDefaults.getApiHash(context)
        return defId > 0 && defHash.isNotBlank()
    }

    fun initializeFromStoredOrDefaults(): Boolean {
        val savedId = authPrefs.getInt(KEY_API_ID, 0)
        val savedHash = authPrefs.getString(KEY_API_HASH, "").orEmpty().trim()
        val defId = TelegramDefaults.getApiId(context)
        val defHash = TelegramDefaults.getApiHash(context)
        val (resolvedId, resolvedHash) = when {
            savedId > 0 && savedHash.isNotBlank() -> savedId to savedHash
            defId > 0 && defHash.isNotBlank() -> defId to defHash
            else -> 0 to ""
        }
        if (resolvedId <= 0 || resolvedHash.isBlank()) {
            if (_authState.value is TelegramAuthState.Idle) {
                _authState.value = TelegramAuthState.WaitingApiCredentials
            }
            return false
        }
        initialize(resolvedId, resolvedHash)
        return true
    }

    @Synchronized
    fun initialize(apiId: Int, apiHash: String) {
        val cleanHash = apiHash.trim()
        if (apiId <= 0 || cleanHash.isBlank()) {
            _authError.value = "Укажите корректные API ID и API Hash."
            _authState.value = TelegramAuthState.WaitingApiCredentials
            return
        }
        this.apiId = apiId
        this.apiHash = cleanHash
        authPrefs.edit()
            .putInt(KEY_API_ID, apiId)
            .putString(KEY_API_HASH, cleanHash)
            .apply()
        _authError.value = null

        val activeClient = client
        if (activeClient != null) {
            if (currentTdAuthState is TdApi.AuthorizationStateWaitTdlibParameters) {
                sendTdlibParameters(activeClient)
            }
            return
        }

        _isAuthBusy.value = true
        createClient()
    }

    private fun createClient() {
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
            _authState.value = TelegramAuthState.WaitingApiCredentials
            return
        }
        val dbDir = File(context.filesDir, "tdlib").apply { mkdirs() }.absolutePath
        val filesDir = File(context.filesDir, "tdlib_files").apply { mkdirs() }.absolutePath
        val params = TdApi.SetTdlibParameters().apply {
            useTestDc = false
            databaseDirectory = dbDir
            filesDirectory = filesDir
            databaseEncryptionKey = ByteArray(0)
            useFileDatabase = true
            useChatInfoDatabase = true
            useMessageDatabase = true
            useSecretChats = false
            this.apiId = this@TelegramRepository.apiId
            this.apiHash = this@TelegramRepository.apiHash
            systemLanguageCode = "ru"
            deviceModel = (Build.MODEL ?: "Android").ifBlank { "Android" }
            systemVersion = (Build.VERSION.RELEASE ?: "14").ifBlank { "14" }
            applicationVersion = "1.0.0"
        }
        targetClient?.send(params) { res ->
            if (res is TdApi.Error) {
                _isAuthBusy.value = false
                _authError.value = formatTdError(res.message)
                val msgUpper = res.message.orEmpty().uppercase()
                if (msgUpper.contains("API_ID") || msgUpper.contains("API_HASH")) {
                    _authState.value = TelegramAuthState.WaitingApiCredentials
                }
            }
        }
    }

    private fun getFileFlow(fileId: Int): MutableStateFlow<TdApi.File>? = fileFlows[fileId]

    private fun publishFileUpdate(file: TdApi.File) {
        val flow = fileFlows.getOrPut(file.id) { MutableStateFlow(file) }
        flow.value = file
    }

    private fun handleTdUpdate(obj: TdApi.Object) {
        when (obj) {
            is TdApi.UpdateFile -> {
                publishFileUpdate(obj.file)
            }
            is TdApi.UpdateConnectionState -> {
                _connectionStatus.value = when (obj.state) {
                    is TdApi.ConnectionStateWaitingForNetwork -> TelegramConnectionStatus.WAITING_NETWORK
                    is TdApi.ConnectionStateConnectingToProxy -> TelegramConnectionStatus.CONNECTING_PROXY
                    is TdApi.ConnectionStateConnecting -> TelegramConnectionStatus.CONNECTING
                    is TdApi.ConnectionStateUpdating -> TelegramConnectionStatus.UPDATING
                    is TdApi.ConnectionStateReady -> TelegramConnectionStatus.READY
                    else -> TelegramConnectionStatus.CONNECTING
                }
            }
            is TdApi.UpdateAuthorizationState -> {
                currentTdAuthState = obj.authorizationState
                when (val state = obj.authorizationState) {
                    is TdApi.AuthorizationStateWaitTdlibParameters -> {
                        sendTdlibParameters(client)
                    }
                    is TdApi.AuthorizationStateWaitPhoneNumber -> {
                        _isAuthBusy.value = false
                        isClosingClient = false
                        _authError.value = null
                        authPrefs.edit()
                            .putBoolean(KEY_WAS_AUTHORIZED, false)
                            .remove(KEY_CACHED_USER_NAME)
                            .remove(KEY_CACHED_PHONE_MASKED)
                            .apply()
                        _authState.value = TelegramAuthState.WaitingPhoneNumber
                        pushCurrentNetworkType()
                        refreshProxiesAsync()
                        val queuedPhone = pendingPhoneAfterClose
                        val queuedQr = pendingQrAfterClose
                        pendingPhoneAfterClose = null
                        pendingQrAfterClose = false
                        if (!queuedPhone.isNullOrBlank()) {
                            lastSubmittedPhone = queuedPhone
                            sendRaw(
                                TdApi.SetAuthenticationPhoneNumber(
                                    queuedPhone,
                                    TdApi.PhoneNumberAuthenticationSettings()
                                )
                            )
                        } else if (queuedQr) {
                            sendRaw(TdApi.RequestQrCodeAuthentication(LongArray(0)))
                        }
                    }
                    is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> {
                        _isAuthBusy.value = false
                        _authError.value = null
                        _authState.value = TelegramAuthState.WaitingQrConfirmation(state.link)
                    }
                    is TdApi.AuthorizationStateWaitEmailAddress -> {
                        _isAuthBusy.value = false
                        _authError.value = null
                        _authState.value = TelegramAuthState.WaitingEmailAddress
                    }
                    is TdApi.AuthorizationStateWaitEmailCode -> {
                        _isAuthBusy.value = false
                        _authError.value = null
                        _authState.value = TelegramAuthState.WaitingEmailCode(state.codeInfo?.emailAddressPattern.orEmpty())
                    }
                    is TdApi.AuthorizationStateWaitRegistration -> {
                        _isAuthBusy.value = false
                        _authError.value = null
                        _authState.value = TelegramAuthState.WaitingRegistration
                    }
                    is TdApi.AuthorizationStateWaitCode -> {
                        _isAuthBusy.value = false
                        _authError.value = null
                        _authState.value = TelegramAuthState.WaitingCode(lastSubmittedPhone)
                    }
                    is TdApi.AuthorizationStateWaitPassword -> {
                        _isAuthBusy.value = false
                        _authError.value = null
                        _authState.value = TelegramAuthState.WaitingPassword(state.passwordHint ?: "")
                    }
                    is TdApi.AuthorizationStateReady -> {
                        _isAuthBusy.value = false
                        isClosingClient = false
                        pendingPhoneAfterClose = null
                        _authError.value = null
                        authPrefs.edit().putBoolean(KEY_WAS_AUTHORIZED, true).apply()
                        val cachedName = authPrefs.getString(KEY_CACHED_USER_NAME, "").orEmpty().ifBlank { "Telegram" }
                        val cachedPhone = authPrefs.getString(KEY_CACHED_PHONE_MASKED, "").orEmpty()
                        if (_authState.value !is TelegramAuthState.Connected) {
                            _authState.value = TelegramAuthState.Connected(
                                userName = cachedName,
                                phoneMasked = cachedPhone
                            )
                        }
                        fetchCurrentUser(retryOnFailure = true)
                        refreshProxiesAsync()
                    }
                    is TdApi.AuthorizationStateLoggingOut,
                    is TdApi.AuthorizationStateClosing -> {
                        _isAuthBusy.value = true
                        isClosingClient = true
                    }
                    is TdApi.AuthorizationStateClosed -> {
                        _isAuthBusy.value = false
                        client = null
                        currentTdAuthState = null
                        isClosingClient = false
                        fileFlows.clear()
                        fileLocks.clear()
                        promotedStreamFileIds.clear()
                        _authError.value = null
                        if (reinitAfterClose && apiId > 0 && apiHash.isNotBlank()) {
                            reinitAfterClose = false
                            _isAuthBusy.value = true
                            createClient()
                        } else {
                            pendingPhoneAfterClose = null
                            _authState.value = if (apiId > 0 && apiHash.isNotBlank()) {
                                TelegramAuthState.WaitingPhoneNumber
                            } else {
                                TelegramAuthState.WaitingApiCredentials
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun fetchCurrentUser(retryOnFailure: Boolean = false) {
        client?.send(TdApi.GetMe()) { res ->
            if (res is TdApi.User) {
                val phone = res.phoneNumber
                val masked = if (phone.length >= 4) "***${phone.takeLast(4)}" else ""
                val displayName = listOf(res.firstName, res.lastName)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { res.usernames?.activeUsernames?.firstOrNull()?.let { "@$it" } ?: "Telegram" }
                authPrefs.edit()
                    .putBoolean(KEY_WAS_AUTHORIZED, true)
                    .putString(KEY_CACHED_USER_NAME, displayName)
                    .putString(KEY_CACHED_PHONE_MASKED, masked)
                    .apply()
                _authState.value = TelegramAuthState.Connected(
                    userName = displayName,
                    phoneMasked = masked
                )
            } else if (retryOnFailure && currentTdAuthState is TdApi.AuthorizationStateReady) {
                repoScope.launch {
                    kotlinx.coroutines.delay(3_000L)
                    if (currentTdAuthState is TdApi.AuthorizationStateReady) {
                        fetchCurrentUser(retryOnFailure = false)
                    }
                }
            }
        }
    }

    fun clearAuthError() {
        _authError.value = null
    }

    private fun normalizePhoneNumber(rawPhone: String): String {
        val trimmed = rawPhone.trim()
        val hasPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isEmpty()) return ""
        if (!hasPlus) {
            if (digits.length == 11 && digits.startsWith("8")) {
                return "+7${digits.substring(1)}"
            }
            if (digits.length == 10 && digits.startsWith("9")) {
                return "+7$digits"
            }
        }
        return "+$digits"
    }

    fun submitPhoneNumber(rawPhone: String) {
        val normalized = normalizePhoneNumber(rawPhone)
        if (normalized.length < 8) {
            _authError.value = "Введите корректный номер телефона, например +79991234567."
            return
        }
        if (isClosingClient || client == null || currentTdAuthState !is TdApi.AuthorizationStateWaitPhoneNumber) {
            pendingPhoneAfterClose = normalized
            pendingQrAfterClose = false
            _authError.value = null
            _isAuthBusy.value = true
            if (client == null && !isClosingClient && apiId > 0 && apiHash.isNotBlank()) {
                createClient()
            }
            return
        }
        lastSubmittedPhone = normalized
        sendRaw(
            TdApi.SetAuthenticationPhoneNumber(
                normalized,
                TdApi.PhoneNumberAuthenticationSettings()
            )
        )
    }

    fun submitAuthCode(code: String) {
        val trimmed = code.filter { it.isDigit() }.trim()
        if (trimmed.isEmpty()) {
            _authError.value = "Введите код подтверждения из Telegram или SMS."
            return
        }
        sendRaw(TdApi.CheckAuthenticationCode(trimmed))
    }

    fun resendAuthCode() {
        sendRaw(TdApi.ResendAuthenticationCode())
    }

    fun resetToPhoneInput() {
        _authError.value = null
        _isAuthBusy.value = false
        pendingPhoneAfterClose = null
        val tdState = currentTdAuthState
        if (tdState is TdApi.AuthorizationStateWaitCode ||
            tdState is TdApi.AuthorizationStateWaitPassword ||
            tdState is TdApi.AuthorizationStateWaitOtherDeviceConfirmation
        ) {
            reinitAfterClose = true
            isClosingClient = true
            _authState.value = TelegramAuthState.WaitingPhoneNumber
            client?.send(TdApi.LogOut()) { res ->
                if (res is TdApi.Error) {
                    isClosingClient = false
                }
            }
        } else {
            _authState.value = TelegramAuthState.WaitingPhoneNumber
        }
    }

    fun requestQrLogin() {
        _authError.value = null
        if (isClosingClient || client == null || currentTdAuthState !is TdApi.AuthorizationStateWaitPhoneNumber) {
            pendingQrAfterClose = true
            pendingPhoneAfterClose = null
            _isAuthBusy.value = true
            if (client == null && !isClosingClient && apiId > 0 && apiHash.isNotBlank()) {
                createClient()
            }
            return
        }
        sendRaw(TdApi.RequestQrCodeAuthentication(LongArray(0)))
    }

    fun submitEmailAddress(email: String) {
        val clean = email.trim()
        if (!clean.contains("@")) {
            _authError.value = "Введите корректный email."
            return
        }
        sendRaw(TdApi.SetAuthenticationEmailAddress(clean))
    }

    fun submitEmailCode(code: String) {
        val clean = code.trim()
        if (clean.isEmpty()) {
            _authError.value = "Введите код из письма."
            return
        }
        sendRaw(TdApi.CheckAuthenticationEmailCode(TdApi.EmailAddressAuthenticationCode(clean)))
    }

    fun submitRegistration(firstName: String, lastName: String) {
        if (firstName.isBlank()) {
            _authError.value = "Укажите имя."
            return
        }
        sendRaw(TdApi.RegisterUser(firstName.trim(), lastName.trim(), false))
    }

    fun submitTwoFactorPassword(password: String) {
        if (password.isEmpty()) {
            _authError.value = "Введите облачный пароль 2FA."
            return
        }
        sendRaw(TdApi.CheckAuthenticationPassword(password))
    }

    fun logout() {
        _authError.value = null
        pendingPhoneAfterClose = null
        reinitAfterClose = true
        isClosingClient = true
        authPrefs.edit()
            .putBoolean(KEY_WAS_AUTHORIZED, false)
            .remove(KEY_CACHED_USER_NAME)
            .remove(KEY_CACHED_PHONE_MASKED)
            .apply()
        sendRaw(TdApi.LogOut())
    }

    suspend fun getSubscribedChannels(): List<TelegramChannel> = withContext(Dispatchers.IO) {
        // Paginate LoadChats on Main and Archive lists so TDLib populates all subscribed channels/bots
        repeat(6) {
            val r = withTimeoutOrNull(8_000L) { sendSuspend(TdApi.LoadChats(TdApi.ChatListMain(), 50)) }
            if (r == null || r is TdApi.Error) return@repeat
        }
        repeat(4) {
            val r = withTimeoutOrNull(6_000L) { sendSuspend(TdApi.LoadChats(TdApi.ChatListArchive(), 50)) }
            if (r == null || r is TdApi.Error) return@repeat
        }

        val mainIds = (withTimeoutOrNull(15_000L) {
            sendSuspend(TdApi.GetChats(TdApi.ChatListMain(), 250))
        } as? TdApi.Chats)?.chatIds?.toList().orEmpty()

        val archiveIds = (withTimeoutOrNull(10_000L) {
            sendSuspend(TdApi.GetChats(TdApi.ChatListArchive(), 150))
        } as? TdApi.Chats)?.chatIds?.toList().orEmpty()

        val allChatIds = LinkedHashSet<Long>().apply {
            addAll(mainIds)
            addAll(archiveIds)
        }
        if (allChatIds.isEmpty()) return@withContext emptyList()

        val result = ArrayList<TelegramChannel>(allChatIds.size)
        for (chatId in allChatIds) {
            val chat = withTimeoutOrNull(8_000L) {
                sendSuspend(TdApi.GetChat(chatId))
            } as? TdApi.Chat ?: continue
            val item = when (val type = chat.type) {
                is TdApi.ChatTypeSupergroup -> {
                    val sg = withTimeoutOrNull(8_000L) {
                        sendSuspend(TdApi.GetSupergroup(type.supergroupId))
                    } as? TdApi.Supergroup
                    val username = sg?.usernames?.activeUsernames?.firstOrNull() ?: ""
                    TelegramChannel(
                        channelId = chat.id.toString(),
                        title = chat.title,
                        username = username,
                        sourceType = if (type.isChannel) SourceType.CHANNEL else SourceType.GROUP
                    )
                }
                is TdApi.ChatTypeBasicGroup -> {
                    TelegramChannel(
                        channelId = chat.id.toString(),
                        title = chat.title,
                        username = "",
                        sourceType = SourceType.GROUP
                    )
                }
                is TdApi.ChatTypePrivate -> {
                    val user = withTimeoutOrNull(8_000L) {
                        sendSuspend(TdApi.GetUser(type.userId))
                    } as? TdApi.User
                    val username = user?.usernames?.activeUsernames?.firstOrNull() ?: ""
                    val isBot = user?.type is TdApi.UserTypeBot
                    TelegramChannel(
                        channelId = chat.id.toString(),
                        title = chat.title,
                        username = username,
                        sourceType = if (isBot) SourceType.BOT else SourceType.USER
                    )
                }
                else -> null
            }
            if (item != null) {
                result.add(item)
            }
        }
        result
    }

    /**
     * Progressively scans Music, Document and VoiceNote filters concurrently in TDLib
     * and invokes [onBatch] after every fetched page so stories appear immediately.
     */
    suspend fun streamChannelAudioTracks(
        channelId: String,
        channelTitle: String,
        channelUsername: String = "",
        maxTracks: Int = 3000,
        onBatch: suspend (List<AudioTrack>) -> Unit
    ): List<AudioTrack> = withContext(Dispatchers.IO) {
        val chatId = channelId.toLongOrNull() ?: return@withContext emptyList()
        val seenIds = HashSet<Long>()
        val collected = ArrayList<AudioTrack>()
        val collectMutex = Mutex()
        var emittedFirst = false
        var lastEmitAtMs = 0L

        runCatching { withTimeoutOrNull(8_000L) { sendSuspend(TdApi.OpenChat(chatId)) } }
        repoScope.launch { runCatching { fetchChannelBigPhotoPath(channelId) } }
        try {
            suspend fun paginateFilter(filter: TdApi.SearchMessagesFilter, limitCap: Int) {
                var fromMessageId = 0L
                while (true) {
                    val reachedCap = collectMutex.withLock { collected.size >= limitCap }
                    if (reachedCap) break

                    val res = withTimeoutOrNull(25_000L) {
                        sendSuspend(
                            TdApi.SearchChatMessages().apply {
                                this.chatId = chatId
                                this.query = ""
                                this.fromMessageId = fromMessageId
                                this.offset = 0
                                this.limit = 100
                                this.filter = filter
                            }
                        )
                    } as? TdApi.FoundChatMessages ?: break

                    val batch = res.messages
                    if (batch.isEmpty()) break

                    val snapshot: List<AudioTrack>? = collectMutex.withLock {
                        var addedAny = false
                        for (msg in batch) {
                            if (!seenIds.add(msg.id)) continue
                            val track = parseMessageToTrack(
                                msg = msg,
                                channelId = channelId,
                                channelTitle = channelTitle,
                                channelUsername = channelUsername
                            ) ?: continue
                            collected.add(track)
                            addedAny = true
                        }
                        if (!addedAny) {
                            null
                        } else {
                            val now = System.currentTimeMillis()
                            if (!emittedFirst || now - lastEmitAtMs >= 650L) {
                                emittedFirst = true
                                lastEmitAtMs = now
                                collected.sortedByDescending { it.dateEpoch }
                            } else {
                                null
                            }
                        }
                    }

                    if (snapshot != null) {
                        onBatch(snapshot)
                    }

                    val oldestId = batch.last().id
                    if (oldestId == fromMessageId) break
                    fromMessageId = oldestId
                }
            }

            // Hybrid recent GetChatHistory pass so freshly posted audiobooks appear immediately before server index catches up
            var recentFromId = 0L
            repeat(3) {
                val hist = withTimeoutOrNull(10_000L) {
                    sendSuspend(TdApi.GetChatHistory(chatId, recentFromId, 0, 50, false))
                } as? TdApi.Messages ?: return@repeat
                val batch = hist.messages.filterNotNull()
                if (batch.isEmpty()) return@repeat
                val snap = collectMutex.withLock {
                    var added = false
                    for (msg in batch) {
                        if (!seenIds.add(msg.id)) continue
                        val tr = parseMessageToTrack(msg, channelId, channelTitle, channelUsername) ?: continue
                        collected.add(tr)
                        added = true
                    }
                    if (added) collected.sortedByDescending { it.dateEpoch } else null
                }
                if (snap != null) onBatch(snap)
                val lastId = batch.last().id
                if (lastId == recentFromId) return@repeat
                recentFromId = lastId
            }

            coroutineScope {
                launch { paginateFilter(TdApi.SearchMessagesFilterAudio(), maxTracks) }
                launch { paginateFilter(TdApi.SearchMessagesFilterDocument(), maxTracks.coerceAtMost(2000)) }
                launch { paginateFilter(TdApi.SearchMessagesFilterVoiceNote(), maxTracks.coerceAtMost(800)) }
            }

            collectMutex.withLock { collected.sortedByDescending { it.dateEpoch } }
        } finally {
            runCatching { client?.send(TdApi.CloseChat(chatId)) {} }
        }
    }

    data class ChatHistoryPage(
        val messages: List<TelegramChatMessage>,
        val canSendMessages: Boolean,
        val oldestMessageId: Long,
        val hasMore: Boolean
    )

    suspend fun loadChatHistoryPage(
        channelId: String,
        channelTitle: String,
        channelUsername: String = "",
        fromMessageId: Long = 0L,
        focusMessageId: Long = 0L,
        limit: Int = 45
    ): Result<ChatHistoryPage> = withContext(Dispatchers.IO) {
        if (client == null || _authState.value !is TelegramAuthState.Connected) {
            return@withContext Result.failure(IllegalStateException("Telegram не подключен."))
        }
        val chatId = channelId.toLongOrNull()
            ?: return@withContext Result.failure(IllegalArgumentException("Некорректный ID чата."))

        val chat = withTimeoutOrNull(8_000L) {
            sendSuspend(TdApi.GetChat(chatId))
        } as? TdApi.Chat
        runCatching { withTimeoutOrNull(6_000L) { sendSuspend(TdApi.OpenChat(chatId)) } }

        val canSend = when (val type = chat?.type) {
            is TdApi.ChatTypePrivate -> true
            is TdApi.ChatTypeBasicGroup -> chat.permissions?.canSendBasicMessages ?: true
            is TdApi.ChatTypeSupergroup -> {
                if (!type.isChannel) {
                    chat.permissions?.canSendBasicMessages ?: true
                } else {
                    val sg = withTimeoutOrNull(6_000L) {
                        sendSuspend(TdApi.GetSupergroup(type.supergroupId))
                    } as? TdApi.Supergroup
                    when (val st = sg?.status) {
                        is TdApi.ChatMemberStatusCreator -> true
                        is TdApi.ChatMemberStatusAdministrator -> st.rights?.canPostMessages == true
                        else -> false
                    }
                }
            }
            else -> false
        }

        val rawList = ArrayList<TdApi.Message>()
        val seenIds = HashSet<Long>()

        suspend fun fetchChunk(startId: Long, offset: Int, count: Int): List<TdApi.Message> {
            val res = withTimeoutOrNull(12_000L) {
                sendSuspend(TdApi.GetChatHistory(chatId, startId, offset, count, false))
            } as? TdApi.Messages ?: return emptyList()
            return res.messages.filterNotNull()
        }

        if (fromMessageId == 0L && focusMessageId > 0L) {
            val around = fetchChunk(focusMessageId, -20, limit)
            for (m in around) {
                if (seenIds.add(m.id)) rawList.add(m)
            }
        }
        if (rawList.isEmpty()) {
            val first = fetchChunk(fromMessageId, 0, limit)
            for (m in first) {
                if (seenIds.add(m.id)) rawList.add(m)
            }
        }
        // TDLib first GetChatHistory call often returns only cached messages; fetch one more page if sparse
        if (rawList.size in 1 until 18) {
            val cursor = rawList.minOfOrNull { it.id } ?: 0L
            if (cursor > 0L && cursor != fromMessageId) {
                val extra = fetchChunk(cursor, 0, limit)
                for (m in extra) {
                    if (seenIds.add(m.id)) rawList.add(m)
                }
            }
        }

        val senderNameCache = HashMap<Long, String>()
        suspend fun resolveSenderName(sender: TdApi.MessageSender?): String {
            return when (sender) {
                is TdApi.MessageSenderUser -> {
                    senderNameCache[sender.userId] ?: run {
                        val user = withTimeoutOrNull(4_000L) {
                            sendSuspend(TdApi.GetUser(sender.userId))
                        } as? TdApi.User
                        val name = if (user != null) {
                            listOf(user.firstName, user.lastName)
                                .filter { it.isNotBlank() }
                                .joinToString(" ")
                                .ifBlank { user.usernames?.activeUsernames?.firstOrNull()?.let { "@$it" } ?: "Пользователь" }
                        } else {
                            ""
                        }
                        senderNameCache[sender.userId] = name
                        name
                    }
                }
                is TdApi.MessageSenderChat -> {
                    if (sender.chatId == chatId) {
                        chat?.title ?: channelTitle
                    } else {
                        senderNameCache[sender.chatId] ?: run {
                            val senderChat = withTimeoutOrNull(4_000L) {
                                sendSuspend(TdApi.GetChat(sender.chatId))
                            } as? TdApi.Chat
                            val name = senderChat?.title.orEmpty()
                            senderNameCache[sender.chatId] = name
                            name
                        }
                    }
                }
                else -> ""
            }
        }

        val parsed = ArrayList<TelegramChatMessage>(rawList.size)
        for (msg in rawList) {
            val senderName = resolveSenderName(msg.senderId)
            parsed.add(
                mapTdMessageToChatItem(
                    msg = msg,
                    channelId = channelId,
                    channelTitle = chat?.title?.ifBlank { channelTitle } ?: channelTitle,
                    channelUsername = channelUsername,
                    senderName = senderName
                )
            )
        }

        // Sort chronologically (oldest -> newest) for natural chat reading
        parsed.sortWith(compareBy<TelegramChatMessage> { it.dateEpochSec }.thenBy { it.messageId })
        val oldestId = parsed.firstOrNull()?.messageId ?: 0L
        Result.success(
            ChatHistoryPage(
                messages = parsed,
                canSendMessages = canSend,
                oldestMessageId = oldestId,
                hasMore = rawList.size >= 10
            )
        )
    }

    private fun mapTdMessageToChatItem(
        msg: TdApi.Message,
        channelId: String,
        channelTitle: String,
        channelUsername: String,
        senderName: String
    ): TelegramChatMessage {
        val audioTrack = parseMessageToTrack(msg, channelId, channelTitle, channelUsername)
        var text = ""
        var mediaLabel = ""
        var photoFileId = 0
        var photoPath: String? = null

        when (val c = msg.content) {
            is TdApi.MessageText -> {
                text = c.text?.text.orEmpty()
            }
            is TdApi.MessagePhoto -> {
                text = c.caption?.text.orEmpty()
                val chosen = c.photo.sizes.lastOrNull { it.width <= 1280 } ?: c.photo.sizes.lastOrNull()
                if (chosen != null) {
                    photoFileId = chosen.photo.id
                    if (chosen.photo.local.isDownloadingCompleted && chosen.photo.local.path.isNotBlank()) {
                        val f = File(chosen.photo.local.path)
                        if (f.exists() && f.length() > 0L) photoPath = f.absolutePath
                    }
                }
                if (photoFileId <= 0) mediaLabel = "Фотография"
            }
            is TdApi.MessageAudio -> {
                text = c.caption?.text.orEmpty()
            }
            is TdApi.MessageVoiceNote -> {
                text = c.caption?.text.orEmpty()
                if (audioTrack == null) mediaLabel = "Голосовое сообщение"
            }
            is TdApi.MessageDocument -> {
                text = c.caption?.text.orEmpty()
                if (audioTrack == null) {
                    val fileName = c.document.fileName.orEmpty().ifBlank { "Документ" }
                    mediaLabel = "Файл: $fileName"
                }
            }
            is TdApi.MessageVideo -> {
                text = c.caption?.text.orEmpty()
                val dur = c.video.duration
                mediaLabel = if (dur > 0) "Видео · ${dur / 60}:${(dur % 60).toString().padStart(2, '0')}" else "Видео"
            }
            is TdApi.MessageAnimation -> {
                text = c.caption?.text.orEmpty()
                mediaLabel = "GIF-анимация"
            }
            is TdApi.MessageSticker -> {
                mediaLabel = "Стикер ${c.sticker.emoji.orEmpty()}".trim()
            }
            is TdApi.MessageVideoNote -> {
                mediaLabel = "Видеосообщение"
            }
            is TdApi.MessagePoll -> {
                mediaLabel = "Опрос"
            }
            is TdApi.MessageLocation -> {
                mediaLabel = "Геолокация"
            }
            is TdApi.MessageContact -> {
                mediaLabel = "Контакт: ${c.contact.firstName}"
            }
            else -> {
                if (audioTrack == null) {
                    mediaLabel = "Сообщение Telegram"
                }
            }
        }

        return TelegramChatMessage(
            messageId = msg.id,
            chatId = msg.chatId,
            senderName = senderName,
            isOutgoing = msg.isOutgoing,
            dateEpochSec = msg.date.toLong(),
            text = text,
            mediaLabel = mediaLabel,
            photoFileId = photoFileId,
            photoPath = photoPath,
            audioTrack = audioTrack,
            views = msg.interactionInfo?.viewCount ?: 0
        )
    }

    suspend fun downloadChatPhoto(fileId: Int): String? = withContext(Dispatchers.IO) {
        if (fileId <= 0 || client == null) return@withContext null
        val downloaded = withTimeoutOrNull(14_000L) {
            sendSuspend(TdApi.DownloadFile(fileId, 16, 0L, 0L, true))
        } as? TdApi.File ?: return@withContext null
        val path = downloaded.local.path
        if (path.isNotBlank()) {
            val f = File(path)
            if (f.exists() && f.length() > 0L) return@withContext f.absolutePath
        }
        null
    }

    suspend fun sendChatTextMessage(
        channelId: String,
        channelTitle: String,
        channelUsername: String,
        text: String
    ): Result<TelegramChatMessage> = withContext(Dispatchers.IO) {
        if (client == null || _authState.value !is TelegramAuthState.Connected) {
            return@withContext Result.failure(IllegalStateException("Telegram не подключен."))
        }
        val chatId = channelId.toLongOrNull()
            ?: return@withContext Result.failure(IllegalArgumentException("Некорректный ID чата."))
        val clean = text.trim()
        if (clean.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Пустое сообщение."))
        }
        val res = withTimeoutOrNull(15_000L) {
            sendSuspend(
                TdApi.SendMessage().apply {
                    this.chatId = chatId
                    inputMessageContent = TdApi.InputMessageText().apply {
                        this.text = TdApi.FormattedText().apply {
                            this.text = clean
                        }
                    }
                }
            )
        }
        when (res) {
            is TdApi.Message -> {
                Result.success(
                    mapTdMessageToChatItem(
                        msg = res,
                        channelId = channelId,
                        channelTitle = channelTitle,
                        channelUsername = channelUsername,
                        senderName = "Вы"
                    )
                )
            }
            is TdApi.Error -> Result.failure(IllegalStateException(formatTdError(res.message)))
            else -> Result.failure(IllegalStateException("Не удалось отправить сообщение."))
        }
    }

    fun closeChat(channelId: String) {
        val chatId = channelId.toLongOrNull() ?: return
        client?.send(TdApi.CloseChat(chatId)) {}
    }

    private fun parseMessageToTrack(
        msg: TdApi.Message,
        channelId: String,
        channelTitle: String,
        channelUsername: String
    ): AudioTrack? {
        val hqCached = getCachedCoverPathIfAny("${channelId}_${msg.id}")
        when (val content = msg.content) {
            is TdApi.MessageAudio -> {
                val audio = content.audio
                val file = audio.audio
                publishFileUpdate(file)
                val thumb = audio.albumCoverThumbnail
                val thumbFile = thumb?.file
                val thumbId = thumbFile?.id ?: 0
                val thumbMaxDim = maxOf(thumb?.width ?: 0, thumb?.height ?: 0)
                val localThumb = thumbFile?.local?.path?.takeIf {
                    it.isNotBlank() && thumbFile.local.isDownloadingCompleted && thumbMaxDim >= 240
                }
                val captionLines = content.caption.text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                val cleanName = audio.fileName.substringBeforeLast('.').replace('_', ' ').trim()
                val title = audio.title.trim().ifBlank {
                    cleanName.ifBlank { captionLines.firstOrNull().orEmpty().ifBlank { "Аудиокнига #${msg.id}" } }
                }
                val author = audio.performer.trim().ifBlank {
                    captionLines.getOrNull(1).orEmpty().ifBlank { channelTitle }
                }
                val fileSize = if (file.size > 0L) file.size else file.expectedSize
                return AudioTrack(
                    trackId = "${channelId}_${msg.id}",
                    channelId = channelId,
                    channelTitle = channelTitle,
                    channelUsername = channelUsername,
                    messageId = msg.id,
                    fileId = file.id,
                    title = title,
                    author = author,
                    description = content.caption.text,
                    durationSec = audio.duration.toLong(),
                    fileSizeBytes = fileSize,
                    mimeType = inferAudioMimeType(audio.mimeType, audio.fileName),
                    coverPath = hqCached ?: localThumb,
                    coverFileId = thumbId,
                    coverMaxDim = thumbMaxDim,
                    dateEpoch = msg.date.toLong()
                )
            }
            is TdApi.MessageDocument -> {
                val doc = content.document
                val mime = doc.mimeType.lowercase()
                if (!mime.startsWith("audio/") && !audioExtRegex.containsMatchIn(doc.fileName)) return null
                publishFileUpdate(doc.document)
                val thumb = doc.thumbnail
                val thumbFile = thumb?.file
                val thumbId = thumbFile?.id ?: 0
                val thumbMaxDim = maxOf(thumb?.width ?: 0, thumb?.height ?: 0)
                val localThumb = thumbFile?.local?.path?.takeIf {
                    it.isNotBlank() && thumbFile.local.isDownloadingCompleted && thumbMaxDim >= 240
                }
                val captionLines = content.caption.text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                val cleanName = doc.fileName.substringBeforeLast('.').replace('_', ' ').trim()
                val title = cleanName.ifBlank {
                    captionLines.firstOrNull().orEmpty().ifBlank { "Аудиозапись #${msg.id}" }
                }
                val author = captionLines.getOrNull(1).orEmpty().ifBlank { channelTitle }
                val fileSize = if (doc.document.size > 0L) doc.document.size else doc.document.expectedSize
                return AudioTrack(
                    trackId = "${channelId}_${msg.id}",
                    channelId = channelId,
                    channelTitle = channelTitle,
                    channelUsername = channelUsername,
                    messageId = msg.id,
                    fileId = doc.document.id,
                    title = title,
                    author = author,
                    description = content.caption.text,
                    durationSec = 0L,
                    fileSizeBytes = fileSize,
                    mimeType = inferAudioMimeType(doc.mimeType, doc.fileName),
                    coverPath = hqCached ?: localThumb,
                    coverFileId = thumbId,
                    coverMaxDim = thumbMaxDim,
                    dateEpoch = msg.date.toLong()
                )
            }
            is TdApi.MessageVoiceNote -> {
                val vn = content.voiceNote
                val file = vn.voice
                publishFileUpdate(file)
                val captionLines = content.caption.text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                val title = captionLines.firstOrNull().orEmpty().ifBlank { "Аудиоистория #${msg.id}" }
                val vnFileSize = if (file.size > 0L) file.size else file.expectedSize
                return AudioTrack(
                    trackId = "${channelId}_${msg.id}",
                    channelId = channelId,
                    channelTitle = channelTitle,
                    channelUsername = channelUsername,
                    messageId = msg.id,
                    fileId = file.id,
                    title = title,
                    author = channelTitle,
                    description = content.caption.text,
                    durationSec = vn.duration.toLong(),
                    fileSizeBytes = vnFileSize,
                    mimeType = vn.mimeType.ifBlank { "audio/ogg" },
                    coverPath = hqCached,
                    coverFileId = 0,
                    coverMaxDim = 0,
                    dateEpoch = msg.date.toLong()
                )
            }
            else -> return null
        }
    }

    private fun inferAudioMimeType(rawMime: String, fileName: String): String {
        val cleanMime = rawMime.trim().lowercase()
        if (cleanMime.startsWith("audio/") &&
            cleanMime != "audio/x-unknown" &&
            cleanMime != "audio/unknown"
        ) {
            return cleanMime
        }
        val ext = fileName.substringAfterLast('.', "").trim().lowercase()
        return when (ext) {
            "mp3" -> "audio/mpeg"
            "m4a", "m4b", "mp4" -> "audio/mp4"
            "aac" -> "audio/aac"
            "ogg", "oga" -> "audio/ogg"
            "opus" -> "audio/opus"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "wma" -> "audio/x-ms-wma"
            "mka", "mkv", "webm" -> "audio/x-matroska"
            else -> "" // empty -> Media3 DefaultExtractorsFactory auto-sniffs container
        }
    }

    private fun getExactFileSize(file: TdApi.File): Long {
        if (file.local.isDownloadingCompleted && file.local.path.isNotBlank()) {
            val diskLen = runCatching { File(file.local.path).length() }.getOrDefault(0L)
            if (diskLen > 0L) return diskLen
        }
        return if (file.size > 0L) file.size else 0L
    }

    /**
     * Resolves exact file info & updates cached TdApi.File for streaming DataSource.
     */
    suspend fun getFileInfo(fileId: Int): TdApi.File? = withContext(Dispatchers.IO) {
        if (fileId <= 0) return@withContext null
        val file = withTimeoutOrNull(8_000L) {
            sendSuspend(TdApi.GetFile(fileId))
        } as? TdApi.File ?: return@withContext null
        publishFileUpdate(file)
        file
    }

    /**
     * Resolves the best up-to-date TdApi.File for playback open():
     * checks TDLib file DB first, falls back to refreshing the Telegram message if stale/missing.
     */
    suspend fun resolvePlayableFile(fileId: Int, chatId: Long, messageId: Long): TdApi.File? =
        withContext(Dispatchers.IO) {
            var info = if (fileId > 0) getFileInfo(fileId) ?: getFileFlow(fileId)?.value else null
            if (info == null && chatId != 0L && messageId > 0L) {
                info = refreshTrackFileFromMessage(chatId, messageId)
            }
            if (info != null && info.local.isDownloadingCompleted) {
                val f = info.local.path.takeIf { it.isNotBlank() }?.let(::File)
                if (f == null || !f.exists() || f.length() == 0L) {
                    invalidateBrokenLocalFile(info.id)
                    info = getFileInfo(info.id) ?: info
                }
            }
            info
        }

    /**
     * Clears a broken/ghost TDLib local file record (e.g., marked completed but deleted on disk)
     * so TDLib re-downloads it cleanly without orphaning active StateFlow listeners.
     */
    suspend fun invalidateBrokenLocalFile(fileId: Int) = withContext(Dispatchers.IO) {
        if (fileId <= 0) return@withContext
        promotedStreamFileIds.remove(fileId)
        runCatching {
            withTimeoutOrNull(5_000L) {
                sendSuspend(TdApi.DeleteFile(fileId))
            }
        }
        getFileInfo(fileId)
    }

    /**
     * Resets stream promotion / stuck download state before retrying a failed track.
     */
    suspend fun resetStreamStateForRetry(fileId: Int) = withContext(Dispatchers.IO) {
        if (fileId <= 0) return@withContext
        promotedStreamFileIds.remove(fileId)
        if (fileId !in activeOfflineFileIds) {
            runCatching {
                withTimeoutOrNull(4_000L) {
                    sendSuspend(TdApi.CancelDownloadFile(fileId, false))
                }
            }
        }
        val info = getFileInfo(fileId)
        if (info != null && info.local.isDownloadingCompleted) {
            val f = info.local.path.takeIf { it.isNotBlank() }?.let(::File)
            if (f == null || !f.exists() || f.length() == 0L) {
                invalidateBrokenLocalFile(fileId)
            }
        }
    }

    /**
     * Re-fetches the Telegram message to obtain a fresh TdApi.File / fileId when a cached
     * file reference expires or a channel message wasn't opened in the current session.
     */
    suspend fun refreshTrackFileFromMessage(chatId: Long, messageId: Long): TdApi.File? =
        withContext(Dispatchers.IO) {
            if (chatId == 0L || messageId <= 0L) return@withContext null
            runCatching {
                withTimeoutOrNull(6_000L) { sendSuspend(TdApi.GetChat(chatId)) }
            }
            var msg = withTimeoutOrNull(8_000L) {
                sendSuspend(TdApi.GetMessage(chatId, messageId))
            } as? TdApi.Message
            if (msg == null) {
                runCatching { withTimeoutOrNull(6_000L) { sendSuspend(TdApi.OpenChat(chatId)) } }
                msg = withTimeoutOrNull(8_000L) {
                    sendSuspend(TdApi.GetMessage(chatId, messageId))
                } as? TdApi.Message
            }
            if (msg == null) {
                val batch = withTimeoutOrNull(8_000L) {
                    sendSuspend(TdApi.GetMessages(chatId, longArrayOf(messageId)))
                } as? TdApi.Messages
                msg = batch?.messages?.firstOrNull { it != null }
            }
            if (msg == null) {
                val hist = withTimeoutOrNull(8_000L) {
                    sendSuspend(TdApi.GetChatHistory(chatId, messageId, -1, 3, false))
                } as? TdApi.Messages
                msg = hist?.messages?.firstOrNull { it != null && it.id == messageId }
            }
            if (msg == null) return@withContext null

            val file = when (val c = msg.content) {
                is TdApi.MessageAudio -> c.audio.audio
                is TdApi.MessageDocument -> c.document.document
                is TdApi.MessageVoiceNote -> c.voiceNote.voice
                else -> null
            } ?: return@withContext null

            publishFileUpdate(file)
            file
        }

    /**
     * Clean sequential download from byte 0 into TDLib local cache for tracks whose container
     * or partial-seek layout failed progressive streaming. Returns the complete local File on disk.
     */
    suspend fun downloadFileToLocalCacheForPlayback(
        fileId: Int,
        chatId: Long,
        messageId: Long
    ): File? = withContext(Dispatchers.IO) {
        if (client == null) {
            lastStreamError = "Telegram не подключён"
            return@withContext null
        }
        activeStreamReaders.incrementAndGet()
        var effectiveFileId = fileId
        try {
            val resolved: TdApi.File? = if (chatId != 0L && messageId > 0L) {
                refreshTrackFileFromMessage(chatId, messageId)
            } else {
                null
            } ?: if (effectiveFileId > 0) getFileInfo(effectiveFileId) else null

            if (resolved == null || resolved.id <= 0) {
                lastStreamError = "Файл не найден в Telegram"
                return@withContext null
            }
            var currentFile: TdApi.File = resolved
            effectiveFileId = currentFile.id
            activePlaybackFileId = effectiveFileId

            // Check if already completely downloaded and intact on disk
            if (currentFile.local.isDownloadingCompleted && currentFile.local.path.isNotBlank()) {
                val existing = File(currentFile.local.path)
                if (existing.exists() && existing.length() > 0L) {
                    lastStreamError = null
                    return@withContext existing
                }
                invalidateBrokenLocalFile(effectiveFileId)
                currentFile = getFileInfo(effectiveFileId) ?: currentFile
            }

            if (!currentFile.local.canBeDownloaded) {
                lastStreamError = "Файл недоступен для скачивания в Telegram"
                return@withContext null
            }

            val lock = fileLocks.getOrPut(effectiveFileId) { Mutex() }
            val startRes = lock.withLock {
                promotedStreamFileIds.add(effectiveFileId)
                withTimeoutOrNull(10_000L) {
                    sendSuspend(TdApi.DownloadFile(effectiveFileId, 32, 0L, 0L, false))
                }
            }
            when (startRes) {
                is TdApi.File -> {
                    publishFileUpdate(startRes)
                    currentFile = startRes
                }
                is TdApi.Error -> {
                    lastStreamError = formatTdError(startRes.message)
                    return@withContext null
                }
                else -> Unit
            }

            val flow = fileFlows.getOrPut(effectiveFileId) { MutableStateFlow(currentFile) }
            var lastBytes = currentFile.local.downloadedSize + currentFile.local.downloadedPrefixSize
            var lastProgressMs = System.currentTimeMillis()
            var restarts = 0
            var completedFile: File? = null

            while (true) {
                val latest = flow.value
                if (latest.local.isDownloadingCompleted && latest.local.path.isNotBlank()) {
                    val done = File(latest.local.path)
                    if (done.exists() && done.length() > 0L) {
                        lastStreamError = null
                        completedFile = done
                        break
                    }
                }
                val nowBytes = latest.local.downloadedSize + latest.local.downloadedPrefixSize
                if (nowBytes > lastBytes) {
                    lastBytes = nowBytes
                    lastProgressMs = System.currentTimeMillis()
                }
                if (System.currentTimeMillis() - lastProgressMs > 45_000L) {
                    lastStreamError = "Таймаут загрузки аудио из Telegram"
                    break
                }
                if (!latest.local.isDownloadingActive && !latest.local.isDownloadingCompleted) {
                    if (restarts < 3) {
                        restarts++
                        kotlinx.coroutines.delay(800L)
                        val retryRes = lock.withLock {
                            withTimeoutOrNull(10_000L) {
                                sendSuspend(TdApi.DownloadFile(effectiveFileId, 32, 0L, 0L, false))
                            }
                        }
                        if (retryRes is TdApi.File) {
                            publishFileUpdate(retryRes)
                            continue
                        } else if (retryRes is TdApi.Error) {
                            lastStreamError = formatTdError(retryRes.message)
                            break
                        }
                    } else {
                        lastStreamError = "Загрузка аудио прервана сервером Telegram"
                        break
                    }
                }

                val snapSize = latest.local.downloadedSize
                val snapPrefix = latest.local.downloadedPrefixSize
                val updated = withTimeoutOrNull(2_500L) {
                    flow.first { f ->
                        f.local.isDownloadingCompleted ||
                            !f.local.isDownloadingActive ||
                            f.local.downloadedSize != snapSize ||
                            f.local.downloadedPrefixSize != snapPrefix
                    }
                }
                if (updated == null) {
                    getFileInfo(effectiveFileId)?.let { polled ->
                        val pBytes = polled.local.downloadedSize + polled.local.downloadedPrefixSize
                        if (pBytes > lastBytes) {
                            lastBytes = pBytes
                            lastProgressMs = System.currentTimeMillis()
                        }
                    }
                }
            }
            completedFile
        } catch (ce: CancellationException) {
            if (effectiveFileId > 0 && effectiveFileId != activePlaybackFileId && effectiveFileId !in activeOfflineFileIds) {
                cancelFileDownload(effectiveFileId)
            }
            throw ce
        } finally {
            activeStreamReaders.decrementAndGet()
        }
    }

    /**
     * Progressive byte-range reader for Media3 ExoPlayer.
     * Starts TDLib sequential download from [offset] (64 KB aligned) with limit=0 and returns
     * bytes as soon as the first chunk at [offset] arrives locally — zero waiting for 100% file!
     * Returns -1 on clean EOF, -2 on unrecoverable download stall/error.
     */
    suspend fun readAudioStreamBytes(
        fileId: Int,
        expectedSize: Long,
        offset: Long,
        buffer: ByteArray,
        outOffset: Int,
        maxLength: Int,
        chatId: Long = 0L,
        messageId: Long = 0L,
        onResolvedFileId: ((Int) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        if (fileId <= 0 || maxLength <= 0) return@withContext -1
        if (expectedSize > 0L && offset >= expectedSize) return@withContext -1

        activeStreamReaders.incrementAndGet()
        try {
            var effectiveFileId = fileId
            val initialFile = getFileFlow(effectiveFileId)?.value ?: getFileInfo(effectiveFileId)
                ?: if (chatId != 0L && messageId > 0L) {
                    refreshTrackFileFromMessage(chatId, messageId)?.also {
                        if (it.id > 0) {
                            effectiveFileId = it.id
                            activePlaybackFileId = effectiveFileId
                            onResolvedFileId?.invoke(effectiveFileId)
                        }
                    }
                } else {
                    null
                }
            var currentFile: TdApi.File = initialFile ?: run {
                lastStreamError = "Файл не найден в Telegram"
                return@withContext -2
            }

            val maxPasses = if (chatId != 0L && messageId > 0L) 2 else 1
            for (pass in 0 until maxPasses) {
                if (pass > 0) {
                    val refreshed = refreshTrackFileFromMessage(chatId, messageId) ?: run {
                        lastStreamError = "Не удалось обновить ссылку на аудио из канала"
                        return@withContext -2
                    }
                    if (refreshed.id <= 0) return@withContext -2
                    effectiveFileId = refreshed.id
                    activePlaybackFileId = effectiveFileId
                    promotedStreamFileIds.remove(effectiveFileId)
                    onResolvedFileId?.invoke(effectiveFileId)
                    currentFile = refreshed
                }

                // If TDLib marked file completed, verify real file on disk
                if (currentFile.local.isDownloadingCompleted) {
                    val f = currentFile.local.path.takeIf { it.isNotBlank() }?.let(::File)
                    if (f == null || !f.exists() || f.length() == 0L) {
                        invalidateBrokenLocalFile(effectiveFileId)
                        currentFile = getFileInfo(effectiveFileId) ?: currentFile
                    } else if (offset >= f.length()) {
                        return@withContext -1
                    }
                }

                val exactTotal = maxOf(expectedSize, getExactFileSize(currentFile))
                if (exactTotal > 0L && offset >= exactTotal) return@withContext -1

                // Fast path: requested byte is already flushed on disk
                if (isOffsetReadable(currentFile, offset)) {
                    val availNow = availableBytesAt(currentFile, offset)
                    if (availNow > 0L) {
                        val directRead = readLocalSlice(
                            path = currentFile.local.path,
                            offset = offset,
                            buffer = buffer,
                            outOffset = outOffset,
                            count = minOf(maxLength.toLong(), availNow).toInt()
                        )
                        if (directRead > 0) {
                            lastStreamError = null
                            return@withContext directRead
                        }
                    }
                }

                var streamFailed = false
                var reachedEof = false
                val lock = fileLocks.getOrPut(effectiveFileId) { Mutex() }

                suspend fun requestDownload(fromOffset: Long): TdApi.Object? {
                    val safeOffset = fromOffset.coerceAtLeast(0L)
                    val res = withTimeoutOrNull(10_000L) {
                        sendSuspend(TdApi.DownloadFile(effectiveFileId, 32, safeOffset, 0L, false))
                    }
                    if (res is TdApi.File) {
                        publishFileUpdate(res)
                    }
                    return res
                }

                fun isImmediateEofAfterDownload(file: TdApi.File): Boolean {
                    if (offset <= 0L) return false
                    if (file.size > 0L && offset >= file.size) return true
                    val loc = file.local
                    if (!loc.isDownloadingActive && !loc.isDownloadingCompleted && loc.canBeDownloaded) {
                        if (loc.path.isNotBlank()) {
                            val diskLen = runCatching { File(loc.path).length() }.getOrDefault(0L)
                            if (diskLen > 0L && offset >= diskLen && loc.downloadOffset <= offset && loc.downloadedPrefixSize == 0L) {
                                return true
                            }
                        }
                    }
                    return false
                }

                var lastTriggerAtMs = 0L
                lock.withLock {
                    currentFile = getFileFlow(effectiveFileId)?.value ?: getFileInfo(effectiveFileId) ?: currentFile
                    if (!isOffsetReadable(currentFile, offset)) {
                        val local = currentFile.local
                        if (local.isDownloadingCompleted) {
                            val diskFile = local.path.takeIf { it.isNotBlank() }?.let(::File)
                            if (diskFile == null || !diskFile.exists() || diskFile.length() == 0L) {
                                invalidateBrokenLocalFile(effectiveFileId)
                                currentFile = getFileInfo(effectiveFileId) ?: currentFile
                            } else if (offset >= diskFile.length()) {
                                reachedEof = true
                                return@withLock
                            }
                        }
                        val refreshedLocal = currentFile.local
                        if (!refreshedLocal.canBeDownloaded && !refreshedLocal.isDownloadingCompleted) {
                            lastStreamError = "Файл недоступен для скачивания в Telegram"
                            streamFailed = true
                            return@withLock
                        }
                        val windowEnd = refreshedLocal.downloadOffset + refreshedLocal.downloadedPrefixSize
                        val newlyPromoted = promotedStreamFileIds.add(effectiveFileId)
                        val needsStart = !refreshedLocal.isDownloadingActive ||
                            offset < refreshedLocal.downloadOffset ||
                            offset > windowEnd + (512L * 1024L) ||
                            newlyPromoted

                        if (needsStart && !refreshedLocal.isDownloadingCompleted) {
                            lastTriggerAtMs = System.currentTimeMillis()
                            when (val resp = requestDownload(offset)) {
                                is TdApi.File -> {
                                    currentFile = resp
                                    if (isImmediateEofAfterDownload(resp)) {
                                        reachedEof = true
                                    }
                                }
                                is TdApi.Error -> {
                                    if (offset > 0L && resp.message.contains("offset", ignoreCase = true)) {
                                        reachedEof = true
                                    } else {
                                        lastStreamError = formatTdError(resp.message)
                                        streamFailed = true
                                    }
                                }
                                else -> {
                                    lastStreamError = "Таймаут ответа Telegram"
                                }
                            }
                        }
                    }
                }

                if (reachedEof) return@withContext -1

                if (!streamFailed && !isOffsetReadable(currentFile, offset)) {
                    val flow = fileFlows.getOrPut(effectiveFileId) { MutableStateFlow(currentFile) }
                    var lastSeenBytes = currentFile.local.downloadedPrefixSize + currentFile.local.downloadedSize
                    var lastProgressMs = System.currentTimeMillis()
                    var restartCount = 0

                    while (true) {
                        val latest = flow.value
                        if (latest.size > 0L && offset >= latest.size) {
                            return@withContext -1
                        }
                        if (isOffsetReadable(latest, offset)) {
                            currentFile = latest
                            break
                        }
                        if (latest.local.isDownloadingCompleted) {
                            val f = latest.local.path.takeIf { it.isNotBlank() }?.let(::File)
                            val diskLen = if (f != null && f.exists()) f.length() else 0L
                            if (diskLen > 0L && offset >= diskLen) {
                                return@withContext -1
                            }
                            if (diskLen > offset) {
                                currentFile = latest
                                break
                            }
                            if (restartCount < 2) {
                                restartCount++
                                invalidateBrokenLocalFile(effectiveFileId)
                                lastTriggerAtMs = System.currentTimeMillis()
                                val resp = lock.withLock { requestDownload(offset) }
                                if (resp is TdApi.File) {
                                    currentFile = resp
                                    continue
                                }
                            }
                            lastStreamError = "Кешированный файл повреждён"
                            streamFailed = true
                            break
                        }

                        val nowBytes = latest.local.downloadedPrefixSize + latest.local.downloadedSize
                        if (nowBytes > lastSeenBytes) {
                            lastSeenBytes = nowBytes
                            lastProgressMs = System.currentTimeMillis()
                        }

                        val now = System.currentTimeMillis()
                        if (now - lastProgressMs > 35_000L) {
                            lastStreamError = "Таймаут загрузки аудио из Telegram"
                            streamFailed = true
                            break
                        }

                        val windowEnd = latest.local.downloadOffset + latest.local.downloadedPrefixSize
                        val wrongCursor = latest.local.downloadOffset > offset ||
                            offset > windowEnd + (1_048_576L)
                        val stopped = !latest.local.isDownloadingActive

                        if (stopped || wrongCursor) {
                            if (!latest.local.canBeDownloaded) {
                                lastStreamError = "Файл недоступен для скачивания"
                                streamFailed = true
                                break
                            }
                            val msSinceTrigger = now - lastTriggerAtMs
                            if (msSinceTrigger < 1_500L) {
                                kotlinx.coroutines.delay(220L)
                                continue
                            }
                            if (restartCount < 5) {
                                restartCount++
                                lastTriggerAtMs = System.currentTimeMillis()
                                val resp = lock.withLock { requestDownload(offset) }
                                when (resp) {
                                    is TdApi.File -> {
                                        currentFile = resp
                                        if (isImmediateEofAfterDownload(resp)) {
                                            return@withContext -1
                                        }
                                        if (isOffsetReadable(resp, offset)) break
                                    }
                                    is TdApi.Error -> {
                                        if (offset > 0L && resp.message.contains("offset", ignoreCase = true)) {
                                            return@withContext -1
                                        }
                                        lastStreamError = formatTdError(resp.message)
                                        streamFailed = true
                                        break
                                    }
                                    else -> {
                                        kotlinx.coroutines.delay(450L)
                                    }
                                }
                                continue
                            } else {
                                lastStreamError = "Загрузка аудио прервана сервером Telegram"
                                streamFailed = true
                                break
                            }
                        }

                        // Wait strictly for a NEW UpdateFile change (never spin on unchanged StateFlow value!)
                        val snapshotOffset = latest.local.downloadOffset
                        val snapshotPrefix = latest.local.downloadedPrefixSize
                        val snapshotTotal = latest.local.downloadedSize
                        val waitedFile = withTimeoutOrNull(2_500L) {
                            flow.first { f ->
                                (f.size > 0L && offset >= f.size) ||
                                    isOffsetReadable(f, offset) ||
                                    f.local.isDownloadingCompleted ||
                                    !f.local.isDownloadingActive ||
                                    f.local.downloadOffset != snapshotOffset ||
                                    f.local.downloadedPrefixSize != snapshotPrefix ||
                                    f.local.downloadedSize != snapshotTotal
                            }
                        }

                        if (waitedFile != null) {
                            currentFile = waitedFile
                            val updBytes = waitedFile.local.downloadedPrefixSize + waitedFile.local.downloadedSize
                            if (updBytes > lastSeenBytes) {
                                lastSeenBytes = updBytes
                                lastProgressMs = System.currentTimeMillis()
                            }
                            if (isOffsetReadable(waitedFile, offset)) break
                        } else {
                            // Poll TDLib explicitly in case UpdateFile stalled
                            getFileInfo(effectiveFileId)?.let { polled ->
                                currentFile = polled
                                val polledBytes = polled.local.downloadedPrefixSize + polled.local.downloadedSize
                                if (polledBytes > lastSeenBytes) {
                                    lastSeenBytes = polledBytes
                                    lastProgressMs = System.currentTimeMillis()
                                }
                            }
                        }
                    }
                }

                if (streamFailed) {
                    if (pass + 1 < maxPasses) continue
                    return@withContext -2
                }

                // Read slice with retry across TDLib temp/ -> music/ rename transition
                for (readAttempt in 0 until 3) {
                    val fresh = if (readAttempt == 0) {
                        getFileFlow(effectiveFileId)?.value ?: currentFile
                    } else {
                        if (readAttempt > 1) kotlinx.coroutines.delay(90L)
                        getFileInfo(effectiveFileId) ?: getFileFlow(effectiveFileId)?.value ?: currentFile
                    }
                    if (fresh.local.isDownloadingCompleted && fresh.local.path.isNotBlank()) {
                        val diskFile = File(fresh.local.path)
                        if (diskFile.exists()) {
                            val diskLen = diskFile.length()
                            if (diskLen > 0L && offset >= diskLen) {
                                return@withContext -1
                            }
                        }
                    }
                    val avail = availableBytesAt(fresh, offset)
                    if (avail > 0L) {
                        val readBytes = readLocalSlice(
                            path = fresh.local.path,
                            offset = offset,
                            buffer = buffer,
                            outOffset = outOffset,
                            count = minOf(maxLength.toLong(), avail).toInt()
                        )
                        if (readBytes > 0) {
                            lastStreamError = null
                            return@withContext readBytes
                        }
                    }
                }

                if (pass + 1 >= maxPasses) {
                    lastStreamError = "Не удалось прочитать загруженный фрагмент файла"
                    return@withContext -2
                }
            }

            -2
        } finally {
            activeStreamReaders.decrementAndGet()
        }
    }

    private fun isOffsetReadable(file: TdApi.File, offset: Long): Boolean {
        val local = file.local
        if (local.path.isBlank()) return false
        val diskFile = File(local.path)
        if (!diskFile.exists()) return false
        val diskLen = diskFile.length()
        if (diskLen <= offset) return false
        if (local.isDownloadingCompleted) {
            return true
        }
        return offset >= local.downloadOffset && offset < local.downloadOffset + local.downloadedPrefixSize
    }

    private fun availableBytesAt(file: TdApi.File, offset: Long): Long {
        val local = file.local
        if (local.path.isBlank()) return 0L
        val diskFile = File(local.path)
        if (!diskFile.exists()) return 0L
        val diskLen = diskFile.length()
        if (diskLen <= offset) return 0L
        if (local.isDownloadingCompleted) {
            return (diskLen - offset).coerceAtLeast(0L)
        }
        if (offset < local.downloadOffset) return 0L
        val end = minOf(diskLen, local.downloadOffset + local.downloadedPrefixSize)
        return (end - offset).coerceAtLeast(0L)
    }

    private fun readLocalSlice(
        path: String,
        offset: Long,
        buffer: ByteArray,
        outOffset: Int,
        count: Int
    ): Int {
        if (path.isBlank() || count <= 0) return -1
        return runCatching {
            RandomAccessFile(path, "r").use { raf ->
                val len = raf.length()
                if (offset >= len) return@runCatching -1
                val toRead = minOf(count.toLong(), len - offset).toInt()
                if (toRead <= 0) return@runCatching -1
                raf.seek(offset)
                raf.read(buffer, outOffset, toRead)
            }
        }.getOrDefault(-1)
    }

    fun getCachedCoverPathIfAny(trackId: String): String? =
        cachedCoverIndex[sanitizeTrackKey(trackId)]

    private fun sanitizeTrackKey(raw: String): String =
        sanitizeKeyRegex.replace(raw, "_").take(120).ifBlank { "track" }

    private fun findCachedCoverFile(trackId: String): File? {
        val key = sanitizeTrackKey(trackId)
        cachedCoverIndex[key]?.let { path ->
            val f = File(path)
            if (f.exists() && f.length() > 256L) return f
            cachedCoverIndex.remove(key, path)
        }
        for (ext in coverExtensions) {
            val f = File(hqCoversDir, "hq_${key}.$ext")
            if (f.exists() && f.length() > 256L) {
                cachedCoverIndex[key] = f.absolutePath
                return f
            }
        }
        return null
    }

    /**
     * Multi-layer HD Cover Resolution matching Web server/telegramService.ts:
     * 1. Cached HQ cover on disk
     * 2. HD Telegram doc/audio thumbnail (>= 240px)
     * 3. Embedded ID3v2 (APIC/PIC) or MP4/M4B (covr) cover from first 512KB (+ block 1 if large tag)
     * 4. Full 640x640 HD Channel/Chat Avatar (cached per channel)
     * 5. Smaller Telegram thumbnail fallback
     */
    suspend fun resolveTrackHdCover(track: AudioTrack, priority: Boolean = false): String? =
        withContext(Dispatchers.IO) {
            findCachedCoverFile(track.trackId)?.let { return@withContext it.absolutePath }

            val failUntil = failedCoverUntil[track.trackId]
            if (!priority && failUntil != null && failUntil > System.currentTimeMillis()) {
                return@withContext null
            }

            if (coverLocks.size > 512) {
                coverLocks.entries.removeIf { !it.value.isLocked }
            }
            val lock = coverLocks.getOrPut(track.trackId) { Mutex() }
            try {
                lock.withLock {
                    findCachedCoverFile(track.trackId)?.let { return@withLock it.absolutePath }

                    // 1. High-resolution Telegram thumbnail (>= 240px)
                    if (track.coverFileId > 0 && track.coverMaxDim >= 240) {
                        val thumbPath = downloadSmallFileComplete(track.coverFileId, if (priority) 24 else 14)
                        if (thumbPath != null) {
                            val bytes = runCatching { File(thumbPath).readBytes() }.getOrNull()
                            if (bytes != null && bytes.size > 256) {
                                val ext = detectImageExtension(bytes) ?: "jpg"
                                val out = saveHqCoverBytes(track.trackId, bytes, ext)
                                if (out != null) return@withLock out
                            }
                        }
                    }

                    // 2. Embedded HD artwork (ID3v2 APIC/PIC or MP4/M4B covr) via fast 32KB header probe
                    val mimeLower = track.mimeType.lowercase()
                    val supportsEmbeddedCover = mimeLower.isBlank() ||
                        "mpeg" in mimeLower || "mp3" in mimeLower ||
                        "mp4" in mimeLower || "m4a" in mimeLower || "m4b" in mimeLower ||
                        "aac" in mimeLower || "octet-stream" in mimeLower
                    if (supportsEmbeddedCover && track.fileId > 0) {
                        val embedded = extractEmbeddedArtFromAudioFile(track.fileId, priority)
                        if (embedded != null) {
                            val out = saveHqCoverBytes(track.trackId, embedded.first, embedded.second)
                            if (out != null) return@withLock out
                        }
                    }

                    // 3. Full 640x640 HD Channel/Chat Avatar if thumb is small or missing
                    if (track.coverMaxDim < 200 || track.coverFileId <= 0) {
                        val avatarPath = fetchChannelBigPhotoPath(track.channelId)
                        if (avatarPath != null) {
                            return@withLock avatarPath
                        }
                    }

                    // 4. Fallback to smaller Telegram thumb if nothing else existed
                    if (track.coverFileId > 0) {
                        val thumbPath = downloadSmallFileComplete(track.coverFileId, if (priority) 20 else 12)
                        if (thumbPath != null) {
                            val bytes = runCatching { File(thumbPath).readBytes() }.getOrNull()
                            if (bytes != null && bytes.size > 128) {
                                val ext = detectImageExtension(bytes) ?: "jpg"
                                val out = saveHqCoverBytes(track.trackId, bytes, ext)
                                if (out != null) return@withLock out
                            }
                        }
                    }

                    // 5. Fallback to channel avatar even if small thumb failed
                    val avatarFallback = fetchChannelBigPhotoPath(track.channelId)
                    if (avatarFallback != null) {
                        return@withLock avatarFallback
                    }

                    failedCoverUntil[track.trackId] = System.currentTimeMillis() + 5 * 60 * 1000L
                    null
                }
            } finally {
                if (!lock.isLocked && coverLocks.size > 256) {
                    coverLocks.remove(track.trackId, lock)
                }
            }
        }

    private fun saveHqCoverBytes(trackId: String, bytes: ByteArray, ext: String): String? {
        return runCatching {
            val key = sanitizeTrackKey(trackId)
            val target = File(hqCoversDir, "hq_${key}.$ext")
            target.writeBytes(bytes)
            cachedCoverIndex[key] = target.absolutePath
            target.absolutePath
        }.getOrNull()
    }

    private suspend fun fetchChannelBigPhotoPath(channelId: String): String? {
        channelPhotoCache[channelId]?.let { cached ->
            if (File(cached).exists()) return cached
        }
        val lock = channelPhotoLocks.getOrPut(channelId) { Mutex() }
        return lock.withLock {
            channelPhotoCache[channelId]?.let { cached ->
                if (File(cached).exists()) return@withLock cached
            }
            val chatId = channelId.toLongOrNull() ?: return@withLock null
            val chat = sendSuspend(TdApi.GetChat(chatId)) as? TdApi.Chat ?: return@withLock null
            val photoFile = chat.photo?.big ?: chat.photo?.small ?: return@withLock null
            val localPath = if (photoFile.local.isDownloadingCompleted && photoFile.local.path.isNotBlank()) {
                photoFile.local.path
            } else {
                downloadSmallFileComplete(photoFile.id, 16)
            }
            if (!localPath.isNullOrBlank()) {
                val bytes = runCatching { File(localPath).readBytes() }.getOrNull()
                if (bytes != null && bytes.size > 256) {
                    val ext = detectImageExtension(bytes) ?: "jpg"
                    val target = File(hqCoversDir, "ch_${sanitizeTrackKey(channelId)}.$ext")
                    runCatching { target.writeBytes(bytes) }
                    if (target.exists()) {
                        channelPhotoCache[channelId] = target.absolutePath
                        return@withLock target.absolutePath
                    }
                }
                channelPhotoCache[channelId] = localPath
            }
            localPath
        }
    }

    private suspend fun downloadSmallFileComplete(fileId: Int, priority: Int): String? {
        if (fileId <= 0) return null
        val file = withTimeoutOrNull(14_000L) {
            sendSuspend(TdApi.DownloadFile(fileId, priority, 0L, 0L, true)) as? TdApi.File
        }
        return file?.local?.path?.takeIf { it.isNotBlank() && File(it).exists() }
    }

    private suspend fun extractEmbeddedArtFromAudioFile(
        fileId: Int,
        priority: Boolean
    ): Pair<ByteArray, String>? {
        // Fast 32KB probe: skip downloading 512KB+ when files lack ID3v2 / MP4 headers
        val probe = readAudioPrefixBytes(fileId, 32L * 1024L, priority) ?: return null
        if (probe.size < 32) return null

        val hasId3 = u8(probe[0]) == 0x49 && u8(probe[1]) == 0x44 && u8(probe[2]) == 0x33
        if (hasId3) {
            val id3Total = getId3v2TotalSize(probe)
            if (id3Total < 1024) return null
            val neededBytes = minOf(id3Total.toLong(), 3L * 512L * 1024L)
            val headerBuf = if (probe.size >= neededBytes) {
                probe
            } else {
                readAudioPrefixBytes(fileId, neededBytes, priority) ?: probe
            }
            return extractEmbeddedCoverFromBytes(headerBuf)
        }

        val isMp4 = indexOfAscii(probe, "ftyp", 0) in 0..64 || indexOfAscii(probe, "moov", 0) >= 0
        if (!isMp4) return null

        extractEmbeddedCoverFromBytes(probe)?.let { return it }
        val mp4Header = readAudioPrefixBytes(fileId, 512L * 1024L, priority) ?: return null
        return extractEmbeddedCoverFromBytes(mp4Header)
    }

    private suspend fun readAudioPrefixBytes(
        fileId: Int,
        wantBytes: Long,
        priority: Boolean
    ): ByteArray? {
        val info = getFileInfo(fileId) ?: return null
        val local = info.local
        val targetBytes = if (info.size in 1L until wantBytes) info.size else wantBytes

        // Fast path: prefix from byte 0 is already downloaded on disk
        if (local.path.isNotBlank() &&
            File(local.path).exists() &&
            (local.isDownloadingCompleted || (local.downloadOffset == 0L && local.downloadedPrefixSize >= targetBytes))
        ) {
            return readPrefixSlice(local.path, targetBytes.toInt())
        }

        // Never disturb an active seek/streaming DownloadFile cursor on the currently playing file
        if (fileId == activePlaybackFileId || promotedStreamFileIds.contains(fileId)) {
            return null
        }

        // Yield background cover extraction while active audio playback is waiting for network chunks
        if (!priority && activeStreamReaders.get() > 0) {
            return null
        }

        suspend fun fetchViaTdlib(): ByteArray? {
            if (fileId == activePlaybackFileId || promotedStreamFileIds.contains(fileId)) {
                return null
            }
            if (!priority && activeStreamReaders.get() > 0) {
                return null
            }
            // Do NOT hold fileLocks[fileId] across the synchronous TDLib download so playback never blocks on cover art
            val dlFile = withTimeoutOrNull(12_000L) {
                sendSuspend(TdApi.DownloadFile(fileId, if (priority) 12 else 4, 0L, targetBytes, true)) as? TdApi.File
            } ?: return null
            val path = dlFile.local.path
            if (path.isBlank() || dlFile.local.downloadedPrefixSize <= 0L) return null
            val avail = if (dlFile.local.isDownloadingCompleted) targetBytes else minOf(targetBytes, dlFile.local.downloadedPrefixSize)
            return readPrefixSlice(path, avail.toInt())
        }

        return if (priority) {
            fetchViaTdlib()
        } else {
            bgHeaderSemaphore.withPermit { fetchViaTdlib() }
        }
    }

    private fun readPrefixSlice(path: String, maxBytes: Int): ByteArray? = runCatching {
        RandomAccessFile(path, "r").use { raf ->
            val count = minOf(maxBytes.toLong(), raf.length()).toInt()
            if (count <= 0) return@runCatching null
            val out = ByteArray(count)
            raf.seek(0L)
            var total = 0
            while (total < count) {
                val n = raf.read(out, total, count - total)
                if (n <= 0) break
                total += n
            }
            if (total == count) out else out.copyOf(total)
        }
    }.getOrNull()

    fun cancelFileDownload(fileId: Int) {
        if (fileId <= 0) return
        promotedStreamFileIds.remove(fileId)
        if (fileId in activeOfflineFileIds) return
        client?.send(TdApi.CancelDownloadFile(fileId, false)) {}
    }

    /**
     * Background prefix pre-buffering for the upcoming track in Queue / Tracklist.
     * Downloads the first ~1.5 MB at low priority (10) so track switches feel instantaneous
     * without interfering with active stream playback.
     */
    suspend fun prefetchAudioPrefix(
        fileId: Int,
        chatId: Long = 0L,
        messageId: Long = 0L,
        bytes: Long = 1_572_864L
    ) = withContext(Dispatchers.IO) {
        if (fileId <= 0 || client == null) return@withContext
        if (fileId == activePlaybackFileId || promotedStreamFileIds.contains(fileId) || activeOfflineFileIds.contains(fileId)) {
            return@withContext
        }
        var info = getFileFlow(fileId)?.value ?: getFileInfo(fileId)
        if (info == null && chatId != 0L && messageId > 0L) {
            info = refreshTrackFileFromMessage(chatId, messageId)
        }
        val file = info ?: return@withContext
        val effectiveId = file.id
        if (effectiveId <= 0 || effectiveId == activePlaybackFileId || promotedStreamFileIds.contains(effectiveId)) {
            return@withContext
        }
        val targetBytes = if (file.size in 1L until bytes) file.size else bytes
        val local = file.local
        if (local.isDownloadingCompleted ||
            !local.canBeDownloaded ||
            (local.downloadOffset == 0L && local.downloadedPrefixSize >= targetBytes)
        ) {
            return@withContext
        }

        val lock = fileLocks.getOrPut(effectiveId) { Mutex() }
        lock.withLock {
            if (effectiveId == activePlaybackFileId || promotedStreamFileIds.contains(effectiveId)) {
                return@withLock
            }
            val latest = getFileFlow(effectiveId)?.value ?: file
            val latestLocal = latest.local
            if (latestLocal.isDownloadingCompleted ||
                !latestLocal.canBeDownloaded ||
                latestLocal.isDownloadingActive ||
                (latestLocal.downloadOffset == 0L && latestLocal.downloadedPrefixSize >= targetBytes)
            ) {
                return@withLock
            }
            client?.send(TdApi.DownloadFile(effectiveId, 10, 0L, targetBytes, false)) {}
        }
    }

    private fun channelCacheFile(channelId: String): File =
        File(channelCacheDir, "ch_${sanitizeTrackKey(channelId)}.json")

    suspend fun loadCachedChannelTracks(channelId: String): List<AudioTrack> = withContext(Dispatchers.IO) {
        if (channelId.isBlank()) return@withContext emptyList()
        channelCacheLock.withLock {
            runCatching {
                val file = channelCacheFile(channelId)
                if (!file.exists()) return@runCatching emptyList()
                val arr = JSONArray(file.readText())
                buildList(arr.length()) {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val tid = o.optString("trackId")
                        if (tid.isBlank()) continue
                        val cachedCover = getCachedCoverPathIfAny(tid)
                        add(
                            AudioTrack(
                                trackId = tid,
                                channelId = o.optString("channelId"),
                                channelTitle = o.optString("channelTitle"),
                                channelUsername = o.optString("channelUsername"),
                                messageId = o.optLong("messageId"),
                                fileId = o.optInt("fileId"),
                                title = o.optString("title"),
                                author = o.optString("author"),
                                description = o.optString("description"),
                                durationSec = o.optLong("durationSec"),
                                fileSizeBytes = o.optLong("fileSizeBytes"),
                                mimeType = o.optString("mimeType", "audio/mpeg"),
                                coverPath = cachedCover ?: o.optString("coverPath").takeIf { it.isNotBlank() },
                                coverFileId = o.optInt("coverFileId"),
                                coverMaxDim = o.optInt("coverMaxDim"),
                                dateEpoch = o.optLong("dateEpoch")
                            )
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }
    }

    suspend fun saveCachedChannelTracks(channelId: String, tracks: List<AudioTrack>) = withContext(Dispatchers.IO) {
        if (channelId.isBlank()) return@withContext
        channelCacheLock.withLock {
            runCatching {
                val target = channelCacheFile(channelId)
                if (tracks.isEmpty()) {
                    target.delete()
                    return@runCatching
                }
                val arr = JSONArray()
                tracks.take(600).forEach { t ->
                    arr.put(
                        JSONObject().apply {
                            put("trackId", t.trackId)
                            put("channelId", t.channelId)
                            put("channelTitle", t.channelTitle)
                            put("channelUsername", t.channelUsername)
                            put("messageId", t.messageId)
                            put("fileId", t.fileId)
                            put("title", t.title)
                            put("author", t.author)
                            put("description", t.description)
                            put("durationSec", t.durationSec)
                            put("fileSizeBytes", t.fileSizeBytes)
                            put("mimeType", t.mimeType)
                            put("coverPath", t.coverPath ?: "")
                            put("coverFileId", t.coverFileId)
                            put("coverMaxDim", t.coverMaxDim)
                            put("dateEpoch", t.dateEpoch)
                        }
                    )
                }
                val tmp = File(channelCacheDir, "ch_${sanitizeTrackKey(channelId)}.tmp")
                tmp.writeText(arr.toString())
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                }
            }
        }
    }

    fun clearChannelTrackCache() {
        runCatching {
            channelCacheDir.listFiles()?.forEach { it.delete() }
        }
    }

    private fun refreshProxiesAsync() {
        val activeClient = client ?: return
        activeClient.send(TdApi.GetProxies()) { obj ->
            if (obj is TdApi.Proxies) {
                val mapped = obj.proxies.mapNotNull { proxy -> mapProxy(proxy) }
                _proxies.value = mapped
            }
        }
    }

    private fun mapProxy(p: TdApi.Proxy): TelegramProxyItem? {
        val prev = _proxies.value.firstOrNull { it.id == p.id }
        return when (val type = p.type) {
            is TdApi.ProxyTypeSocks5 -> TelegramProxyItem(
                id = p.id,
                type = TelegramProxyType.SOCKS5,
                server = p.server,
                port = p.port,
                username = type.username ?: "",
                password = type.password ?: "",
                isEnabled = p.isEnabled,
                lastUsedDate = p.lastUsedDate,
                latencyMs = prev?.latencyMs,
                pingError = prev?.pingError
            )
            is TdApi.ProxyTypeMtproto -> TelegramProxyItem(
                id = p.id,
                type = TelegramProxyType.MTPROTO,
                server = p.server,
                port = p.port,
                secret = type.secret ?: "",
                isEnabled = p.isEnabled,
                lastUsedDate = p.lastUsedDate,
                latencyMs = prev?.latencyMs,
                pingError = prev?.pingError
            )
            is TdApi.ProxyTypeHttp -> TelegramProxyItem(
                id = p.id,
                type = TelegramProxyType.HTTP,
                server = p.server,
                port = p.port,
                username = type.username ?: "",
                password = type.password ?: "",
                isEnabled = p.isEnabled,
                lastUsedDate = p.lastUsedDate,
                latencyMs = prev?.latencyMs,
                pingError = prev?.pingError
            )
            else -> null
        }
    }

    suspend fun loadProxies(): List<TelegramProxyItem> = withContext(Dispatchers.IO) {
        val res = sendSuspend(TdApi.GetProxies())
        if (res is TdApi.Proxies) {
            val mapped = res.proxies.mapNotNull { mapProxy(it) }
            _proxies.value = mapped
            mapped
        } else {
            _proxies.value
        }
    }

    suspend fun addProxy(
        type: TelegramProxyType,
        server: String,
        port: Int,
        username: String = "",
        password: String = "",
        secret: String = "",
        enable: Boolean = true
    ): Result<TelegramProxyItem> = withContext(Dispatchers.IO) {
        val cleanServer = server.trim()
        if (cleanServer.isEmpty() || port !in 1..65535) {
            return@withContext Result.failure(IllegalArgumentException("Укажите корректный сервер и порт прокси (1–65535)."))
        }
        val proxyType: TdApi.ProxyType = when (type) {
            TelegramProxyType.SOCKS5 -> TdApi.ProxyTypeSocks5(username.trim(), password)
            TelegramProxyType.HTTP -> TdApi.ProxyTypeHttp(username.trim(), password, false)
            TelegramProxyType.MTPROTO -> {
                val cleanSecret = secret.trim()
                if (cleanSecret.isEmpty()) {
                    return@withContext Result.failure(IllegalArgumentException("Укажите секретный ключ (secret) для MTProto."))
                }
                TdApi.ProxyTypeMtproto(cleanSecret)
            }
        }
        when (val res = sendSuspend(TdApi.AddProxy(cleanServer, port, enable, proxyType))) {
            is TdApi.Proxy -> {
                val item = mapProxy(res)
                loadProxies()
                if (item != null) Result.success(item) else Result.failure(IllegalStateException("Не удалось разобрать прокси."))
            }
            is TdApi.Error -> Result.failure(IllegalStateException(formatTdError(res.message)))
            else -> Result.failure(IllegalStateException("Клиент Telegram не отвечает."))
        }
    }

    suspend fun enableProxy(proxyId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        when (val res = sendSuspend(TdApi.EnableProxy(proxyId))) {
            is TdApi.Ok -> {
                loadProxies()
                Result.success(Unit)
            }
            is TdApi.Error -> Result.failure(IllegalStateException(formatTdError(res.message)))
            else -> Result.failure(IllegalStateException("Не удалось включить прокси."))
        }
    }

    suspend fun disableProxy(): Result<Unit> = withContext(Dispatchers.IO) {
        when (val res = sendSuspend(TdApi.DisableProxy())) {
            is TdApi.Ok -> {
                loadProxies()
                Result.success(Unit)
            }
            is TdApi.Error -> Result.failure(IllegalStateException(formatTdError(res.message)))
            else -> Result.failure(IllegalStateException("Не удалось отключить прокси."))
        }
    }

    suspend fun removeProxy(proxyId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        when (val res = sendSuspend(TdApi.RemoveProxy(proxyId))) {
            is TdApi.Ok -> {
                loadProxies()
                Result.success(Unit)
            }
            is TdApi.Error -> Result.failure(IllegalStateException(formatTdError(res.message)))
            else -> Result.failure(IllegalStateException("Не удалось удалить прокси."))
        }
    }

    suspend fun pingProxy(proxyId: Int): Result<Int> = withContext(Dispatchers.IO) {
        val res = withTimeoutOrNull(10_000L) {
            sendSuspend(TdApi.PingProxy(proxyId))
        }
        when (res) {
            is TdApi.Seconds -> {
                val ms = (res.seconds * 1000.0).roundToInt().coerceAtLeast(1)
                _proxies.value = _proxies.value.map {
                    if (it.id == proxyId) it.copy(latencyMs = ms, pingError = null) else it
                }
                Result.success(ms)
            }
            is TdApi.Error -> {
                val err = formatTdError(res.message)
                _proxies.value = _proxies.value.map {
                    if (it.id == proxyId) it.copy(latencyMs = null, pingError = err) else it
                }
                Result.failure(IllegalStateException(err))
            }
            else -> {
                val err = "Таймаут проверки"
                _proxies.value = _proxies.value.map {
                    if (it.id == proxyId) it.copy(latencyMs = null, pingError = err) else it
                }
                Result.failure(IllegalStateException(err))
            }
        }
    }

    fun parseProxyLink(raw: String): ParsedProxyLink? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            val uri = Uri.parse(trimmed)
            val path = uri.path.orEmpty().lowercase()
            val host = uri.host.orEmpty().lowercase()
            val server = uri.getQueryParameter("server")?.trim().orEmpty()
            val port = uri.getQueryParameter("port")?.toIntOrNull() ?: 0
            if (server.isEmpty() || port !in 1..65535) return@runCatching null

            val isMtproto = path.contains("proxy") || host == "proxy" || !uri.getQueryParameter("secret").isNullOrBlank()
            if (isMtproto) {
                val secret = uri.getQueryParameter("secret")?.trim().orEmpty()
                if (secret.isEmpty()) return@runCatching null
                ParsedProxyLink(
                    type = TelegramProxyType.MTPROTO,
                    server = server,
                    port = port,
                    secret = secret
                )
            } else {
                ParsedProxyLink(
                    type = TelegramProxyType.SOCKS5,
                    server = server,
                    port = port,
                    username = uri.getQueryParameter("user")?.trim() ?: "",
                    password = uri.getQueryParameter("pass") ?: ""
                )
            }
        }.getOrNull()
    }

    private fun dirSizeAndCount(dir: File): Pair<Long, Int> {
        if (!dir.exists()) return 0L to 0
        var total = 0L
        var count = 0
        dir.walkTopDown().forEach { f ->
            if (f.isFile) {
                total += f.length()
                count++
            }
        }
        return total to count
    }

    suspend fun getStorageCacheStats(): StorageCacheStats = withContext(Dispatchers.IO) {
        var audioBytes = 0L
        var audioCount = 0
        var tdCoverBytes = 0L
        var tdCoverCount = 0
        var otherBytes = 0L
        var otherCount = 0
        var databaseBytes = 0L

        val fast = sendSuspend(TdApi.GetStorageStatisticsFast()) as? TdApi.StorageStatisticsFast
        if (fast != null) {
            databaseBytes = fast.databaseSize + fast.languagePackDatabaseSize + fast.logSize
        }

        val detailed = sendSuspend(TdApi.GetStorageStatistics(50)) as? TdApi.StorageStatistics
        if (detailed != null) {
            for (byChat in detailed.byChat) {
                for (ft in byChat.byFileType) {
                    when (ft.fileType) {
                        is TdApi.FileTypeAudio,
                        is TdApi.FileTypeVoiceNote,
                        is TdApi.FileTypeDocument -> {
                            audioBytes += ft.size
                            audioCount += ft.count
                        }
                        is TdApi.FileTypeThumbnail,
                        is TdApi.FileTypeProfilePhoto -> {
                            tdCoverBytes += ft.size
                            tdCoverCount += ft.count
                        }
                        else -> {
                            otherBytes += ft.size
                            otherCount += ft.count
                        }
                    }
                }
            }
        } else if (fast != null) {
            audioBytes = fast.filesSize
            audioCount = fast.fileCount
        } else {
            val (tdFilesSize, tdFilesCount) = dirSizeAndCount(File(context.filesDir, "tdlib_files"))
            audioBytes = tdFilesSize
            audioCount = tdFilesCount
        }

        val (hqBytes, hqCount) = dirSizeAndCount(hqCoversDir)
        val (coilBytes, coilCount) = dirSizeAndCount(File(context.cacheDir, "image_cache"))

        StorageCacheStats(
            audioBytes = audioBytes,
            audioCount = audioCount,
            coversBytes = tdCoverBytes + hqBytes + coilBytes,
            coversCount = tdCoverCount + hqCount + coilCount,
            otherBytes = otherBytes,
            otherCount = otherCount,
            databaseBytes = databaseBytes
        )
    }

    suspend fun optimizeAndClearCache(
        clearAudio: Boolean,
        clearCovers: Boolean,
        clearOther: Boolean
    ): StorageCacheStats = withContext(Dispatchers.IO) {
        val types = ArrayList<TdApi.FileType>()
        if (clearAudio) {
            types.add(TdApi.FileTypeAudio())
            types.add(TdApi.FileTypeVoiceNote())
            types.add(TdApi.FileTypeDocument())
            if (activePlaybackFileId > 0) {
                cancelFileDownload(activePlaybackFileId)
            }
            val flowIter = fileFlows.entries.iterator()
            while (flowIter.hasNext()) {
                val entry = flowIter.next()
                val fid = entry.key
                if (fid != activePlaybackFileId && fid !in activeOfflineFileIds && !entry.value.value.local.isDownloadingActive) {
                    flowIter.remove()
                }
            }
        }
        if (clearCovers) {
            types.add(TdApi.FileTypeThumbnail())
            types.add(TdApi.FileTypeProfilePhoto())
            runCatching {
                hqCoversDir.listFiles()?.forEach { it.delete() }
            }
            runCatching {
                File(context.cacheDir, "image_cache").deleteRecursively()
            }
            withContext(Dispatchers.Main) {
                runCatching {
                    Coil.imageLoader(context).memoryCache?.clear()
                }
            }
            cachedCoverIndex.clear()
            channelPhotoCache.clear()
            failedCoverUntil.clear()
            offlineWriteLock.withLock {
                val updatedOffline = _downloadedTracks.value.map { it.copy(coverPath = null) }
                _downloadedTracks.value = updatedOffline
                persistOfflineIndexLocked(updatedOffline)
            }
        }
        if (clearOther) {
            types.add(TdApi.FileTypePhoto())
            types.add(TdApi.FileTypeVideo())
            types.add(TdApi.FileTypeAnimation())
            types.add(TdApi.FileTypeSticker())
            types.add(TdApi.FileTypeSecretThumbnail())
            types.add(TdApi.FileTypeUnknown())
            types.add(TdApi.FileTypeVideoNote())
        }

        if (types.isNotEmpty()) {
            sendSuspend(
                TdApi.OptimizeStorage(
                    0L,
                    0,
                    0,
                    0,
                    types.toTypedArray(),
                    LongArray(0),
                    LongArray(0),
                    false,
                    50
                )
            )
        }
        getStorageCacheStats()
    }

    fun applyCacheTtl(ttlDays: Int) {
        if (ttlDays <= 0) return
        val ttlSec = (ttlDays.toLong() * 86_400L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        client?.send(
            TdApi.OptimizeStorage(
                -1L,
                ttlSec,
                -1,
                3600,
                emptyArray(),
                LongArray(0),
                LongArray(0),
                false,
                50
            )
        ) {}
    }

    private fun offlineBinaryFile(trackId: String): File =
        File(offlineAudioDir, "${sanitizeTrackKey(trackId)}.bin")

    fun getOfflineAudioFile(trackId: String): File? {
        val file = offlineBinaryFile(trackId)
        return if (file.exists() && file.length() > 0L) file else null
    }

    fun getOfflineStorageBytes(): Long =
        dirSizeAndCount(offlineAudioDir).first

    private fun refreshOfflineIndexState() {
        val loaded = runCatching {
            if (!offlineIndexFile.exists()) return@runCatching emptyList()
            val arr = JSONArray(offlineIndexFile.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val tid = o.optString("trackId")
                    if (tid.isBlank() || !offlineBinaryFile(tid).exists()) continue
                    val coverCached = getCachedCoverPathIfAny(tid)
                    add(
                        AudioTrack(
                            trackId = tid,
                            channelId = o.optString("channelId"),
                            channelTitle = o.optString("channelTitle"),
                            channelUsername = o.optString("channelUsername"),
                            messageId = o.optLong("messageId"),
                            fileId = o.optInt("fileId"),
                            title = o.optString("title"),
                            author = o.optString("author"),
                            description = o.optString("description"),
                            durationSec = o.optLong("durationSec"),
                            fileSizeBytes = o.optLong("fileSizeBytes"),
                            mimeType = o.optString("mimeType", "audio/mpeg"),
                            coverPath = coverCached ?: o.optString("coverPath").takeIf { it.isNotBlank() },
                            coverFileId = o.optInt("coverFileId"),
                            coverMaxDim = o.optInt("coverMaxDim"),
                            dateEpoch = o.optLong("dateEpoch")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
        _downloadedTracks.value = loaded
        _downloadedTrackIds.value = loaded.map { it.trackId }.toSet()
    }

    private fun persistOfflineIndexLocked(tracks: List<AudioTrack>) {
        val arr = JSONArray()
        tracks.forEach { t ->
            arr.put(
                JSONObject().apply {
                    put("trackId", t.trackId)
                    put("channelId", t.channelId)
                    put("channelTitle", t.channelTitle)
                    put("channelUsername", t.channelUsername)
                    put("messageId", t.messageId)
                    put("fileId", t.fileId)
                    put("title", t.title)
                    put("author", t.author)
                    put("description", t.description)
                    put("durationSec", t.durationSec)
                    put("fileSizeBytes", t.fileSizeBytes)
                    put("mimeType", t.mimeType)
                    put("coverPath", t.coverPath ?: "")
                    put("coverFileId", t.coverFileId)
                    put("coverMaxDim", t.coverMaxDim)
                    put("dateEpoch", t.dateEpoch)
                }
            )
        }
        offlineIndexFile.writeText(arr.toString())
    }

    suspend fun downloadTrackForOffline(track: AudioTrack): Boolean = withContext(Dispatchers.IO) {
        if (getOfflineAudioFile(track.trackId) != null) {
            offlineWriteLock.withLock {
                if (_downloadedTracks.value.none { it.trackId == track.trackId }) {
                    val updated = listOf(track) + _downloadedTracks.value
                    _downloadedTracks.value = updated
                    _downloadedTrackIds.value = updated.map { it.trackId }.toSet()
                    persistOfflineIndexLocked(updated)
                }
            }
            return@withContext true
        }
        if (track.fileId <= 0 || client == null) return@withContext false

        _downloadingProgress.value = _downloadingProgress.value + (track.trackId to 0.02f)
        activeOfflineFileIds.add(track.fileId)
        try {
            var info = getFileInfo(track.fileId)
            if (info == null || !info.local.isDownloadingCompleted || info.local.path.isBlank()) {
                val downloadFailedSignal = CompletableDeferred<Boolean>()
                val activeClient = client
                if (activeClient == null) {
                    downloadFailedSignal.complete(true)
                } else {
                    activeClient.send(TdApi.DownloadFile(track.fileId, 16, 0L, 0L, false)) { res ->
                        when (res) {
                            is TdApi.File -> {
                                publishFileUpdate(res)
                                downloadFailedSignal.complete(false)
                            }
                            else -> downloadFailedSignal.complete(true)
                        }
                    }
                }
                val flow = fileFlows.getOrPut(track.fileId) {
                    MutableStateFlow(info ?: TdApi.File())
                }
                var abortedOnError = false
                val doneFile = coroutineScope {
                    val progressJob = launch {
                        flow.collect { f ->
                            val expected = maxOf(track.fileSizeBytes, f.expectedSize, f.size, 1L)
                            val pct = if (f.local.isDownloadingCompleted) {
                                1f
                            } else {
                                (f.local.downloadedSize.toFloat() / expected.toFloat()).coerceIn(0.02f, 0.99f)
                            }
                            _downloadingProgress.value = _downloadingProgress.value + (track.trackId to pct)
                        }
                    }
                    try {
                        withTimeoutOrNull(600_000L) {
                            coroutineScope {
                                val flowDeferred = async {
                                    flow.first { f ->
                                        f.local.isDownloadingCompleted &&
                                            f.local.path.isNotBlank() &&
                                            File(f.local.path).exists()
                                    }
                                }
                                val errJob = launch {
                                    if (downloadFailedSignal.await()) {
                                        abortedOnError = true
                                        flowDeferred.cancel()
                                    }
                                }
                                try {
                                    flowDeferred.await()
                                } catch (ce: CancellationException) {
                                    if (abortedOnError) null else throw ce
                                } finally {
                                    errJob.cancel()
                                }
                            }
                        }
                    } finally {
                        progressJob.cancel()
                    }
                }
                if (abortedOnError) return@withContext false
                info = doneFile ?: getFileInfo(track.fileId)
            }

            val srcPath = info?.local?.path
            val srcFile = srcPath?.takeIf { it.isNotBlank() }?.let(::File)
            if (srcFile == null || !srcFile.exists() || srcFile.length() == 0L) {
                return@withContext false
            }

            val target = offlineBinaryFile(track.trackId)
            val tmp = File(offlineAudioDir, "${sanitizeTrackKey(track.trackId)}.tmp")
            srcFile.inputStream().use { input ->
                tmp.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 65536)
                }
            }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }

            val resolvedCover = resolveTrackHdCover(track, priority = true) ?: track.coverPath
            val enriched = track.copy(
                coverPath = resolvedCover,
                fileSizeBytes = maxOf(track.fileSizeBytes, target.length())
            )

            offlineWriteLock.withLock {
                val updated = listOf(enriched) + _downloadedTracks.value.filterNot { it.trackId == enriched.trackId }
                _downloadedTracks.value = updated
                _downloadedTrackIds.value = updated.map { it.trackId }.toSet()
                persistOfflineIndexLocked(updated)
            }
            true
        } finally {
            activeOfflineFileIds.remove(track.fileId)
            _downloadingProgress.value = _downloadingProgress.value - track.trackId
        }
    }

    suspend fun removeOfflineTrack(trackId: String) = withContext(Dispatchers.IO) {
        offlineWriteLock.withLock {
            runCatching { offlineBinaryFile(trackId).delete() }
            val updated = _downloadedTracks.value.filterNot { it.trackId == trackId }
            _downloadedTracks.value = updated
            _downloadedTrackIds.value = updated.map { it.trackId }.toSet()
            persistOfflineIndexLocked(updated)
        }
    }

    suspend fun clearOfflineDownloads() = withContext(Dispatchers.IO) {
        offlineWriteLock.withLock {
            runCatching {
                offlineAudioDir.listFiles()?.forEach { it.delete() }
            }
            runCatching { offlineIndexFile.delete() }
            _downloadedTracks.value = emptyList()
            _downloadedTrackIds.value = emptySet()
        }
    }

    private suspend fun getSavedMessagesChatId(): Long? {
        val me = withTimeoutOrNull(12_000L) {
            sendSuspend(TdApi.GetMe())
        } as? TdApi.User ?: return null
        val chat = withTimeoutOrNull(12_000L) {
            sendSuspend(
                TdApi.CreatePrivateChat().apply {
                    userId = me.id
                    force = false
                }
            )
        } as? TdApi.Chat ?: return null
        return chat.id
    }

    suspend fun pullCloudSyncSnapshot(): Result<Pair<String, Long>?> = withContext(Dispatchers.IO) {
        if (client == null || _authState.value !is TelegramAuthState.Connected) {
            return@withContext Result.failure(IllegalStateException("Сначала подключите аккаунт Telegram."))
        }
        val savedChatId = getSavedMessagesChatId()
            ?: return@withContext Result.failure(IllegalStateException("Не удалось открыть «Избранное» Telegram."))

        val found = withTimeoutOrNull(20_000L) {
            sendSuspend(
                TdApi.SearchChatMessages().apply {
                    chatId = savedChatId
                    query = CLOUD_SYNC_TAG
                    fromMessageId = 0L
                    offset = 0
                    limit = 15
                    filter = TdApi.SearchMessagesFilterDocument()
                }
            )
        } as? TdApi.FoundChatMessages
            ?: return@withContext Result.failure(IllegalStateException("Не удалось проверить «Избранное» Telegram."))

        val candidate = found.messages.firstOrNull { msg ->
            val docMsg = msg.content as? TdApi.MessageDocument ?: return@firstOrNull false
            val caption = docMsg.caption?.text.orEmpty()
            val fileName = docMsg.document.fileName.orEmpty()
            caption.contains(CLOUD_SYNC_TAG) || fileName.equals(CLOUD_SYNC_FILENAME, ignoreCase = true)
        } ?: return@withContext Result.success(null)

        val docContent = candidate.content as TdApi.MessageDocument
        val file = docContent.document.document
        val downloaded = if (file.local.isDownloadingCompleted && file.local.path.isNotBlank() && File(file.local.path).exists()) {
            file
        } else {
            withTimeoutOrNull(30_000L) {
                sendSuspend(TdApi.DownloadFile(file.id, 32, 0L, 0L, true))
            } as? TdApi.File
        }

        val path = downloaded?.local?.path
        val localFile = path?.takeIf { it.isNotBlank() }?.let(::File)
        if (localFile == null || !localFile.exists() || localFile.length() == 0L || localFile.length() > MAX_CLOUD_SYNC_BYTES) {
            return@withContext Result.failure(IllegalStateException("Не удалось скачать файл синхронизации из Telegram."))
        }

        val jsonText = runCatching { localFile.readText(Charsets.UTF_8) }.getOrElse {
            return@withContext Result.failure(IllegalStateException("Не удалось прочитать файл синхронизации."))
        }
        Result.success(jsonText to (candidate.date.toLong() * 1000L))
    }

    suspend fun pushCloudSyncSnapshot(jsonText: String): Result<Long> = withContext(Dispatchers.IO) {
        if (client == null || _authState.value !is TelegramAuthState.Connected) {
            return@withContext Result.failure(IllegalStateException("Сначала подключите аккаунт Telegram."))
        }
        val bytes = jsonText.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_CLOUD_SYNC_BYTES) {
            return@withContext Result.failure(IllegalStateException("Размер резервной копии превышает лимит 4 МБ."))
        }
        val savedChatId = getSavedMessagesChatId()
            ?: return@withContext Result.failure(IllegalStateException("Не удалось открыть «Избранное» Telegram."))

        val oldMessageIds = runCatching {
            val found = withTimeoutOrNull(15_000L) {
                sendSuspend(
                    TdApi.SearchChatMessages().apply {
                        chatId = savedChatId
                        query = CLOUD_SYNC_TAG
                        fromMessageId = 0L
                        offset = 0
                        limit = 15
                        filter = TdApi.SearchMessagesFilterDocument()
                    }
                )
            } as? TdApi.FoundChatMessages
            found?.messages
                ?.filter { msg ->
                    val docMsg = msg.content as? TdApi.MessageDocument ?: return@filter false
                    val caption = docMsg.caption?.text.orEmpty()
                    val fileName = docMsg.document.fileName.orEmpty()
                    caption.contains(CLOUD_SYNC_TAG) || fileName.equals(CLOUD_SYNC_FILENAME, ignoreCase = true)
                }
                ?.map { it.id }
                ?: emptyList()
        }.getOrDefault(emptyList())

        val syncDir = File(context.cacheDir, "cloud_sync").apply { mkdirs() }
        val syncFile = File(syncDir, CLOUD_SYNC_FILENAME)
        runCatching { syncFile.writeBytes(bytes) }.getOrElse {
            return@withContext Result.failure(IllegalStateException("Не удалось подготовить файл синхронизации."))
        }

        val sendResult = withTimeoutOrNull(30_000L) {
            sendSuspend(
                TdApi.SendMessage().apply {
                    chatId = savedChatId
                    options = TdApi.MessageSendOptions().apply {
                        disableNotification = true
                    }
                    inputMessageContent = TdApi.InputMessageDocument().apply {
                        document = TdApi.InputFileLocal().apply {
                            path = syncFile.absolutePath
                        }
                        disableContentTypeDetection = true
                        caption = TdApi.FormattedText().apply {
                            text = "$CLOUD_SYNC_TAG · Облачная синхронизация EchoGram"
                        }
                    }
                }
            )
        }

        when (sendResult) {
            is TdApi.Message -> {
                val toDelete = oldMessageIds.filter { it != sendResult.id }.toLongArray()
                if (toDelete.isNotEmpty()) {
                    runCatching {
                        withTimeoutOrNull(10_000L) {
                            sendSuspend(
                                TdApi.DeleteMessages().apply {
                                    chatId = savedChatId
                                    messageIds = toDelete
                                    revoke = true
                                }
                            )
                        }
                    }
                }
                Result.success(System.currentTimeMillis())
            }
            is TdApi.Error -> Result.failure(IllegalStateException(formatTdError(sendResult.message)))
            else -> Result.failure(IllegalStateException("Не удалось отправить резервную копию в «Избранное» Telegram."))
        }
    }

    private fun sendRaw(function: TdApi.Function<*>) {
        val activeClient = client
        if (activeClient == null) {
            _isAuthBusy.value = false
            _authError.value = "Клиент Telegram ещё не инициализирован. Подождите секунду и попробуйте снова."
            return
        }
        _authError.value = null
        _isAuthBusy.value = true
        activeClient.send(function) { res ->
            _isAuthBusy.value = false
            if (res is TdApi.Error) {
                _authError.value = formatTdError(res.message)
            }
        }
    }

    private fun formatTdError(raw: String?): String {
        val msg = raw?.trim().orEmpty()
        return when {
            msg.contains("PHONE_CODE_INVALID") ->
                "Неверный код подтверждения. Проверьте цифры в Telegram/SMS и введите заново."
            msg.contains("PHONE_CODE_EXPIRED") ->
                "Срок действия кода истёк. Нажмите «Отправить повторно»."
            msg.contains("PHONE_NUMBER_INVALID") ->
                "Неверный номер телефона. Укажите номер в формате +79991234567."
            msg.contains("PHONE_NUMBER_BANNED") ->
                "Этот номер заблокирован в Telegram."
            msg.contains("PHONE_NUMBER_FLOOD") ->
                "Слишком много запросов кода на этот номер. Попробуйте позже."
            msg.contains("PASSWORD_HASH_INVALID") ->
                "Неверный облачный пароль 2FA."
            msg.contains("API_ID_INVALID") || msg.contains("API_ID_PUBLISHED_FLOOD") ->
                "Неверный или заблокированный API ID / API Hash."
            msg.contains("FLOOD_WAIT_") -> {
                val sec = Regex("FLOOD_WAIT_(\\d+)").find(msg)?.groupValues?.getOrNull(1)
                if (sec != null) "Слишком много попыток. Подождите $sec сек." else "Слишком много попыток. Попробуйте позже."
            }
            msg.isBlank() -> "Ошибка подключения к Telegram."
            else -> msg
        }
    }

    private suspend fun sendSuspend(function: TdApi.Function<*>): TdApi.Object? {
        val activeClient = client ?: return null
        val deferred = CompletableDeferred<TdApi.Object?>()
        activeClient.send(function) { obj ->
            deferred.complete(obj)
        }
        return deferred.await()
    }

    companion object {
        private const val KEY_API_ID = "saved_api_id"
        private const val KEY_API_HASH = "saved_api_hash"
        private const val KEY_WAS_AUTHORIZED = "was_authorized"
        private const val KEY_CACHED_USER_NAME = "cached_user_name"
        private const val KEY_CACHED_PHONE_MASKED = "cached_phone_masked"
        private const val CLOUD_SYNC_TAG = "#echogram_sync_v1"
        private const val CLOUD_SYNC_FILENAME = "echogram-sync.json"
        private const val MAX_CLOUD_SYNC_BYTES = 4 * 1024 * 1024
        private val audioExtRegex = Regex("\\.(mp3|m4b|m4a|ogg|oga|opus|flac|wav|aac|weba)$", RegexOption.IGNORE_CASE)
        private val sanitizeKeyRegex = Regex("[^a-zA-Z0-9_-]")
        private val coverExtensions = listOf("jpg", "png", "webp")

        @Volatile
        private var instance: TelegramRepository? = null

        fun getInstance(context: Context): TelegramRepository =
            instance ?: synchronized(this) {
                instance ?: TelegramRepository(context.applicationContext).also { instance = it }
            }

        private fun u8(b: Byte): Int = b.toInt() and 0xFF

        private fun readU32BE(buf: ByteArray, offset: Int): Long {
            if (offset + 4 > buf.size) return 0L
            return ((u8(buf[offset]).toLong() shl 24) or
                (u8(buf[offset + 1]).toLong() shl 16) or
                (u8(buf[offset + 2]).toLong() shl 8) or
                u8(buf[offset + 3]).toLong())
        }

        private fun readSynchsafe(buf: ByteArray, offset: Int): Int {
            if (offset + 4 > buf.size) return 0
            return ((u8(buf[offset]) and 0x7f) shl 21) or
                ((u8(buf[offset + 1]) and 0x7f) shl 14) or
                ((u8(buf[offset + 2]) and 0x7f) shl 7) or
                (u8(buf[offset + 3]) and 0x7f)
        }

        private fun getId3v2TotalSize(buf: ByteArray): Int {
            if (buf.size < 10 || u8(buf[0]) != 0x49 || u8(buf[1]) != 0x44 || u8(buf[2]) != 0x33) return 0
            return 10 + readSynchsafe(buf, 6)
        }

        private fun deUnsync(src: ByteArray): ByteArray {
            val out = ByteArray(src.size)
            var j = 0
            var i = 0
            while (i < src.size) {
                out[j++] = src[i]
                if (u8(src[i]) == 0xFF && i + 1 < src.size && u8(src[i + 1]) == 0x00) {
                    i++
                }
                i++
            }
            return out.copyOf(j)
        }

        private fun skipId3Text(payload: ByteArray, offset: Int, utf16: Boolean): Int {
            if (!utf16) {
                for (i in offset until payload.size) {
                    if (payload[i].toInt() == 0) return i + 1
                }
                return payload.size
            }
            var i = offset
            while (i + 1 < payload.size) {
                if (payload[i].toInt() == 0 && payload[i + 1].toInt() == 0) return i + 2
                i += 2
            }
            return payload.size
        }

        private fun detectImageExtension(buf: ByteArray, start: Int = 0): String? {
            if (start + 12 > buf.size) return null
            if (u8(buf[start]) == 0xFF && u8(buf[start + 1]) == 0xD8 && u8(buf[start + 2]) == 0xFF) return "jpg"
            if (u8(buf[start]) == 0x89 && u8(buf[start + 1]) == 0x50 && u8(buf[start + 2]) == 0x4E && u8(buf[start + 3]) == 0x47) return "png"
            if (buf.size >= start + 12 &&
                buf[start] == 'R'.code.toByte() && buf[start + 1] == 'I'.code.toByte() &&
                buf[start + 2] == 'F'.code.toByte() && buf[start + 3] == 'F'.code.toByte() &&
                buf[start + 8] == 'W'.code.toByte() && buf[start + 9] == 'E'.code.toByte() &&
                buf[start + 10] == 'B'.code.toByte() && buf[start + 11] == 'P'.code.toByte()
            ) return "webp"
            return null
        }

        private fun sliceToImageMagic(raw: ByteArray): Pair<ByteArray, String>? {
            val directExt = detectImageExtension(raw, 0)
            if (directExt != null && raw.size > 256) {
                return raw to directExt
            }
            val limit = minOf(raw.size - 12, 64)
            for (i in 1 until limit) {
                val ext = detectImageExtension(raw, i)
                if ((ext == "jpg" || ext == "png") && raw.size - i > 256) {
                    return raw.copyOfRange(i, raw.size) to ext
                }
            }
            return null
        }

        private fun indexOfAscii(buf: ByteArray, text: String, fromIndex: Int): Int {
            val pattern = text.toByteArray(Charsets.US_ASCII)
            if (pattern.isEmpty()) return fromIndex
            val max = buf.size - pattern.size
            for (i in fromIndex..max) {
                var matched = true
                for (j in pattern.indices) {
                    if (buf[i + j] != pattern[j]) {
                        matched = false
                        break
                    }
                }
                if (matched) return i
            }
            return -1
        }

        fun extractEmbeddedCoverFromBytes(buf: ByteArray): Pair<ByteArray, String>? {
            if (buf.size < 32) return null

            // 1. ID3v2 APIC / PIC tag at start of MP3/AAC
            if (u8(buf[0]) == 0x49 && u8(buf[1]) == 0x44 && u8(buf[2]) == 0x33) {
                val major = u8(buf[3])
                val flags = u8(buf[5])
                val tagUnsync = (flags and 0x80) != 0
                val totalSize = getId3v2TotalSize(buf)
                val bodyRaw = buf.copyOfRange(10, minOf(buf.size, totalSize))
                val body = if (tagUnsync) deUnsync(bodyRaw) else bodyRaw
                var pos = 0

                if ((flags and 0x40) != 0 && pos + 4 <= body.size) {
                    val extSize = if (major == 4) readSynchsafe(body, pos) else readU32BE(body, pos).toInt()
                    val step = if (major == 4) extSize else extSize + 4
                    if (step <= 0) return null
                    pos += step
                }

                if (major == 2) {
                    while (pos + 6 <= body.size) {
                        val frameId = String(body, pos, 3, Charsets.US_ASCII)
                        val frameSize = (u8(body[pos + 3]) shl 16) or (u8(body[pos + 4]) shl 8) or u8(body[pos + 5])
                        pos += 6
                        if (frameId == "\u0000\u0000\u0000" || frameSize <= 0 || pos + frameSize > body.size) break
                        if (frameId == "PIC" && frameSize > 8) {
                            val payload = body.copyOfRange(pos, pos + frameSize)
                            val enc = u8(payload[0])
                            val imgStart = skipId3Text(payload, 5, enc == 1 || enc == 2)
                            if (imgStart < payload.size) {
                                val parsed = sliceToImageMagic(payload.copyOfRange(imgStart, payload.size))
                                if (parsed != null) return parsed
                            }
                        }
                        pos += frameSize
                    }
                } else if (major == 3 || major == 4) {
                    while (pos + 10 <= body.size) {
                        val frameId = String(body, pos, 4, Charsets.US_ASCII)
                        val frameSize = if (major == 4) readSynchsafe(body, pos + 4) else readU32BE(body, pos + 4).toInt()
                        val frameFlagsLow = u8(body[pos + 9])
                        pos += 10
                        if (frameId == "\u0000\u0000\u0000\u0000" || frameSize <= 0 || pos + frameSize > body.size) break
                        if (frameId == "APIC" && frameSize > 8) {
                            var payload = body.copyOfRange(pos, pos + frameSize)
                            if (major == 4 && (frameFlagsLow and 0x02) != 0 && !tagUnsync) {
                                payload = deUnsync(payload)
                            }
                            val enc = u8(payload[0])
                            var mimeEnd = -1
                            for (i in 1 until payload.size) {
                                if (payload[i].toInt() == 0) {
                                    mimeEnd = i
                                    break
                                }
                            }
                            if (mimeEnd != -1 && mimeEnd + 2 < payload.size) {
                                val descStart = mimeEnd + 2
                                val imgStart = skipId3Text(payload, descStart, enc == 1 || enc == 2)
                                if (imgStart < payload.size) {
                                    val parsed = sliceToImageMagic(payload.copyOfRange(imgStart, payload.size))
                                    if (parsed != null) return parsed
                                }
                            }
                        }
                        pos += frameSize
                    }
                }
            }

            // 2. MP4 / M4B 'covr' -> 'data' atom
            val covrIdx = indexOfAscii(buf, "covr", 4)
            if (covrIdx >= 4 && covrIdx + 24 <= buf.size) {
                val covrSize = readU32BE(buf, covrIdx - 4)
                val covrEnd = if (covrSize > 8L) minOf(buf.size.toLong(), (covrIdx - 4).toLong() + covrSize).toInt() else buf.size
                val dataIdx = indexOfAscii(buf, "data", covrIdx + 4)
                if (dataIdx >= covrIdx + 8 && dataIdx + 12 < covrEnd) {
                    val dataSize = readU32BE(buf, dataIdx - 4)
                    val dataEnd = if (dataSize > 16L) minOf(covrEnd.toLong(), (dataIdx - 4).toLong() + dataSize).toInt() else covrEnd
                    val payloadStart = dataIdx + 12
                    if (payloadStart < dataEnd) {
                        val parsed = sliceToImageMagic(buf.copyOfRange(payloadStart, dataEnd))
                        if (parsed != null) return parsed
                    }
                }
            }

            return null
        }
    }
}
