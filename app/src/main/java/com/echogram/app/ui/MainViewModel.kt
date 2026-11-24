package com.echogram.app.ui

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.echogram.app.data.model.AudioBookmark
import com.echogram.app.data.model.AudioChapter
import com.echogram.app.data.model.AudioTrack
import com.echogram.app.data.model.AuthState
import com.echogram.app.data.model.BookSeries
import com.echogram.app.data.model.CacheStats
import com.echogram.app.data.model.ChatMessage
import com.echogram.app.data.model.ConnectionStatus
import com.echogram.app.data.model.DacDevice
import com.echogram.app.data.model.DacOutputMode
import com.echogram.app.data.model.DspPreset
import com.echogram.app.data.model.ProxyItem
import com.echogram.app.data.model.ProxyType
import com.echogram.app.data.model.SleepTimerState
import com.echogram.app.data.model.StreamFormatInfo
import com.echogram.app.data.model.TelegramChat
import com.echogram.app.data.model.ThemeMode
import com.echogram.app.data.storage.LibraryStore
import com.echogram.app.data.telegram.TelegramClientManager
import com.echogram.app.player.AudiobookPlayerService
import com.echogram.app.player.DacAudioManager
import com.google.common.util.concurrent.MoreExecutors
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ActiveChatState(
    val chat: TelegramChat? = null,
    val messages: List<ChatMessage> = emptyList(),
    val audioTracks: List<AudioTrack> = emptyList(),
    val canSend: Boolean = false,
    val isLoadingMessages: Boolean = false,
    val isLoadingAudio: Boolean = false,
    val oldestMessageId: Long = 0L
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val telegram = TelegramClientManager.getInstance(application)
    private val store = LibraryStore(application)
    private val dacManager = DacAudioManager.getInstance(application)

    // Independent SupervisorJob so final persistence flushes in onCleared() are never cancelled
    @OptIn(ExperimentalCoroutinesApi::class)
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    private var mediaController: MediaController? = null
    private var sleepJob: Job? = null
    private var lastPausedAtMs: Long = 0L
    private val trackPositions = ConcurrentHashMap<String, Long>()

    // ---- Telegram & Connection ----
    val authState: StateFlow<AuthState> = telegram.authState
    val authError: StateFlow<String?> = telegram.authError
    val isAuthBusy: StateFlow<Boolean> = telegram.isAuthBusy
    val connectionStatus: StateFlow<ConnectionStatus> = telegram.connectionStatus
    val proxies: StateFlow<List<ProxyItem>> = telegram.proxies
    val downloadedTracks: StateFlow<List<AudioTrack>> = telegram.downloadedTracks
    val downloadedTrackIds: StateFlow<Set<String>> = telegram.downloadedTrackIds
    val downloadingProgress: StateFlow<Map<String, Float>> = telegram.downloadingProgress

    // ---- DAC & Chapters ----
    val dacDevices: StateFlow<List<DacDevice>> = dacManager.devices
    val dacRoutingSummary: StateFlow<String> = dacManager.routingSummary
    val dacBitPerfectActive: StateFlow<Boolean> = dacManager.bitPerfectActive
    val streamFormat: StateFlow<StreamFormatInfo> = dacManager.streamFormat
    val embeddedChapters: StateFlow<Pair<String, List<AudioChapter>>> = AudiobookPlayerService.embeddedChapters

    // ---- Chats & Library ----
    private val _starredChatIds = MutableStateFlow(store.loadStarredChatIds())
    val starredChatIds: StateFlow<Set<Long>> = _starredChatIds.asStateFlow()

    private val _chats = MutableStateFlow<List<TelegramChat>>(emptyList())
    val chats: StateFlow<List<TelegramChat>> = _chats.asStateFlow()

    private val _isLoadingChats = MutableStateFlow(false)
    val isLoadingChats: StateFlow<Boolean> = _isLoadingChats.asStateFlow()

    private val _activeChat = MutableStateFlow(ActiveChatState())
    val activeChat: StateFlow<ActiveChatState> = _activeChat.asStateFlow()

    private val _tracksByChat = MutableStateFlow<Map<Long, List<AudioTrack>>>(emptyMap())
    val allLibraryTracks: StateFlow<List<AudioTrack>> = _tracksByChat
        .map { m -> m.values.flatten().distinctBy { it.trackId }.sortedByDescending { it.dateEpochSec } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _globalSearchResults = MutableStateFlow<List<TelegramChat>>(emptyList())
    val globalSearchResults: StateFlow<List<TelegramChat>> = _globalSearchResults.asStateFlow()

    private val _isSearchingGlobal = MutableStateFlow(false)
    val isSearchingGlobal: StateFlow<Boolean> = _isSearchingGlobal.asStateFlow()

    private val _favorites = MutableStateFlow(store.loadFavorites())
    val favorites: StateFlow<List<AudioTrack>> = _favorites.asStateFlow()

    private val _completedIds = MutableStateFlow(store.loadCompletedIds())
    val completedIds: StateFlow<Set<String>> = _completedIds.asStateFlow()

    private val _bookmarks = MutableStateFlow(store.loadBookmarks())
    val bookmarks: StateFlow<List<AudioBookmark>> = _bookmarks.asStateFlow()

    val bookSeries: StateFlow<List<BookSeries>> = allLibraryTracks
        .map { tracks -> groupIntoSeries(tracks, _completedIds.value) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---- Playback ----
    private val _currentTrack = MutableStateFlow(store.loadLastTrack())
    val currentTrack: StateFlow<AudioTrack?> = _currentTrack.asStateFlow()

    private val _queue = MutableStateFlow<List<AudioTrack>>(emptyList())
    val queue: StateFlow<List<AudioTrack>> = _queue.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _positionMs = MutableStateFlow(store.loadLastPositionMs())
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow((_currentTrack.value?.durationSec ?: 0L) * 1000L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(store.loadPlaybackSpeed())
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _sleepTimer = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)
    val sleepTimer: StateFlow<SleepTimerState> = _sleepTimer.asStateFlow()

    // ---- Settings & UI ----
    private val _themeMode = MutableStateFlow(store.loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _skipSilence = MutableStateFlow(store.loadSkipSilence())
    val skipSilence: StateFlow<Boolean> = _skipSilence.asStateFlow()

    private val _seekStepSec = MutableStateFlow(store.loadSeekStepSec())
    val seekStepSec: StateFlow<Int> = _seekStepSec.asStateFlow()

    private val _smartRewind = MutableStateFlow(store.loadSmartRewind())
    val smartRewind: StateFlow<Boolean> = _smartRewind.asStateFlow()

    private val _dspPreset = MutableStateFlow(store.loadDspPreset())
    val dspPreset: StateFlow<DspPreset> = _dspPreset.asStateFlow()

    private val _loudnessBoostDb = MutableStateFlow(store.loadLoudnessBoostDb())
    val loudnessBoostDb: StateFlow<Int> = _loudnessBoostDb.asStateFlow()

    private val _dacMode = MutableStateFlow(store.loadDacMode())
    val dacMode: StateFlow<DacOutputMode> = _dacMode.asStateFlow()

    private val _preferredDacId = MutableStateFlow(store.loadPreferredDacId())
    val preferredDacId: StateFlow<Int?> = _preferredDacId.asStateFlow()

    private val _cacheStats = MutableStateFlow(CacheStats())
    val cacheStats: StateFlow<CacheStats> = _cacheStats.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    private var didInitialSync = false

    init {
        trackPositions.putAll(store.loadTrackPositions())
        AudiobookPlayerService.applyDspPreset(_dspPreset.value)
        AudiobookPlayerService.applySkipSilence(_skipSilence.value)
        AudiobookPlayerService.applyDacConfig(_dacMode.value, _preferredDacId.value)
        AudiobookPlayerService.applyLoudnessBoostDb(_loudnessBoostDb.value)
        AudiobookPlayerService.applySeekStepSec(_seekStepSec.value)
        AudiobookPlayerService.onSkipNextRequested = { playNext() }
        AudiobookPlayerService.onSkipPreviousRequested = { playPrevious() }

        connectMediaController()
        startPositionTicker()
        observeAuthAndUpdates()
    }

    private fun observeAuthAndUpdates() {
        viewModelScope.launch {
            authState.collect { st ->
                if (st is AuthState.Authorized && !didInitialSync) {
                    didInitialSync = true
                    refreshChats()
                    refreshCacheStats()
                } else if (st !is AuthState.Authorized) {
                    didInitialSync = false
                    _chats.value = emptyList()
                    _activeChat.value = ActiveChatState()
                    _tracksByChat.value = emptyMap()
                }
            }
        }

        viewModelScope.launch {
            telegram.incomingMessages.collect { (chatId, tdMsg) ->
                val open = _activeChat.value
                if (open.chat?.chatId == chatId) {
                    val audio = telegram.parseAudioTrack(tdMsg, chatId, open.chat.title, open.chat.username)
                    if (audio != null && open.audioTracks.none { it.trackId == audio.trackId }) {
                        val updatedAudio = listOf(audio) + open.audioTracks
                        _activeChat.value = _activeChat.value.copy(audioTracks = updatedAudio)
                        if (chatId in _starredChatIds.value) {
                            _tracksByChat.value = _tracksByChat.value + (chatId to updatedAudio)
                        }
                    }
                    // Reload latest page gently to include resolved sender & text
                    reloadActiveChatMessagesSilent()
                }
            }
        }
    }

    // ---- MediaController ----

    private fun connectMediaController() {
        val ctx = getApplication<Application>()
        val token = SessionToken(ctx, ComponentName(ctx, AudiobookPlayerService::class.java))
        val future = MediaController.Builder(ctx, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    if (mediaController === controller) {
                        mediaController = null
                        _isPlaying.value = false
                        _isBuffering.value = false
                    }
                }
            })
            .buildAsync()

        future.addListener({
            runCatching { future.get() }.onSuccess { controller ->
                mediaController = controller
                controller.playbackParameters = PlaybackParameters(_playbackSpeed.value)
                controller.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                        if (isPlaying) {
                            lastPausedAtMs = 0L
                        } else {
                            lastPausedAtMs = System.currentTimeMillis()
                            flushPositions()
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        _isBuffering.value = playbackState == Player.STATE_BUFFERING
                        if (playbackState == Player.STATE_ENDED) {
                            _currentTrack.value?.let { markCompleted(it.trackId, true) }
                            if (_sleepTimer.value is SleepTimerState.EndOfTrack) {
                                _sleepTimer.value = SleepTimerState.Off
                                mediaController?.pause()
                            } else {
                                playNext()
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        val activeId = mediaController?.currentMediaItem?.mediaId
                        val currentId = _currentTrack.value?.trackId
                        if (activeId != null && currentId != null && activeId != currentId) return
                        _isBuffering.value = false
                        _isPlaying.value = false
                        _uiMessage.value = "Ошибка воспроизведения: ${error.localizedMessage ?: "не удалось загрузить трек"}"
                    }
                })
            }
        }, MoreExecutors.directExecutor())
    }

    private fun startPositionTicker() {
        viewModelScope.launch {
            var tick = 0
            while (true) {
                delay(500L)
                val ctrl = mediaController ?: continue
                if (ctrl.isPlaying != _isPlaying.value) {
                    _isPlaying.value = ctrl.isPlaying
                }
                if (ctrl.mediaItemCount > 0) {
                    val pos = ctrl.currentPosition.coerceAtLeast(0L)
                    val dur = ctrl.duration.takeIf { it > 0L } ?: ((_currentTrack.value?.durationSec ?: 0L) * 1000L)
                    _positionMs.value = pos
                    _durationMs.value = dur
                    _currentTrack.value?.let { cur ->
                        trackPositions[cur.trackId] = pos
                    }
                    tick++
                    if (tick % 10 == 0 && ctrl.isPlaying) {
                        flushPositions()
                    }
                }
            }
        }
    }

    private fun flushPositions() {
        val snapshot = HashMap(trackPositions)
        val cur = _currentTrack.value
        val pos = _positionMs.value
        persistScope.launch {
            store.saveTrackPositions(snapshot)
            store.saveLastTrack(cur, pos)
        }
    }

    // ---- Chat & Audiobook Actions ----

    fun refreshChats() {
        viewModelScope.launch {
            _isLoadingChats.value = true
            val list = telegram.loadAllChats(_starredChatIds.value)
            _chats.value = list
            _isLoadingChats.value = false

            // Prefetch small avatars in background
            list.take(30).forEach { chat ->
                if (chat.avatarPath == null && chat.avatarFileId > 0) {
                    launch(Dispatchers.IO) {
                        val p = telegram.downloadSmallFile(chat.avatarFileId)
                        if (!p.isNullOrBlank()) {
                            _chats.value = _chats.value.map {
                                if (it.chatId == chat.chatId) it.copy(avatarPath = p) else it
                            }
                        }
                    }
                }
            }

            // Load audio tracks for starred sources
            val starred = _starredChatIds.value
            list.filter { it.chatId in starred }.forEach { ch ->
                loadTracksForStarredChat(ch)
            }
        }
    }

    private fun loadTracksForStarredChat(chat: TelegramChat) {
        viewModelScope.launch {
            val loaded = telegram.loadChatAudioTracks(chat.chatId, chat.title, chat.username)
            if (loaded.isNotEmpty()) {
                _tracksByChat.value = _tracksByChat.value + (chat.chatId to loaded)
            }
        }
    }

    fun toggleStarredSource(chat: TelegramChat) {
        val cur = _starredChatIds.value
        val updated = if (chat.chatId in cur) cur - chat.chatId else cur + chat.chatId
        _starredChatIds.value = updated
        persistScope.launch { store.saveStarredChatIds(updated) }
        _chats.value = _chats.value.map {
            if (it.chatId == chat.chatId) it.copy(isStarredAudiobookSource = chat.chatId in updated) else it
        }
        _activeChat.value.chat?.let { open ->
            if (open.chatId == chat.chatId) {
                _activeChat.value = _activeChat.value.copy(
                    chat = open.copy(isStarredAudiobookSource = chat.chatId in updated)
                )
            }
        }
        if (chat.chatId in updated) {
            loadTracksForStarredChat(chat)
            _uiMessage.value = "«${chat.title}» добавлен в источники аудиокниг"
        } else {
            _tracksByChat.value = _tracksByChat.value - chat.chatId
            _uiMessage.value = "«${chat.title}» убран из источников"
        }
    }

    fun openChat(chat: TelegramChat) {
        _activeChat.value = ActiveChatState(chat = chat, isLoadingMessages = true, isLoadingAudio = true)
        viewModelScope.launch {
            val page = telegram.loadChatMessages(chat.chatId, chat.title, chat.username)
            _activeChat.value = _activeChat.value.copy(
                messages = page.messages,
                canSend = page.canSend,
                oldestMessageId = page.oldestMessageId,
                isLoadingMessages = false
            )
            // Download visible photos
            page.messages.filter { it.photoPath == null && it.photoFileId > 0 }.take(12).forEach { msg ->
                launch(Dispatchers.IO) {
                    val path = telegram.downloadSmallFile(msg.photoFileId)
                    if (!path.isNullOrBlank()) {
                        _activeChat.value = _activeChat.value.copy(
                            messages = _activeChat.value.messages.map {
                                if (it.messageId == msg.messageId) it.copy(photoPath = path) else it
                            }
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            val tracks = telegram.loadChatAudioTracks(chat.chatId, chat.title, chat.username)
            _activeChat.value = _activeChat.value.copy(audioTracks = tracks, isLoadingAudio = false)
            if (chat.chatId in _starredChatIds.value && tracks.isNotEmpty()) {
                _tracksByChat.value = _tracksByChat.value + (chat.chatId to tracks)
            }
        }
    }

    fun closeActiveChat() {
        _activeChat.value.chat?.let { telegram.closeChatAsync(it.chatId) }
        _activeChat.value = ActiveChatState()
    }

    fun loadOlderMessages() {
        val state = _activeChat.value
        val chat = state.chat ?: return
        if (state.isLoadingMessages || state.oldestMessageId <= 0L) return
        _activeChat.value = state.copy(isLoadingMessages = true)
        viewModelScope.launch {
            val page = telegram.loadChatMessages(chat.chatId, chat.title, chat.username, fromMessageId = state.oldestMessageId)
            val merged = (page.messages + _activeChat.value.messages)
                .distinctBy { it.messageId }
                .sortedWith(compareBy<ChatMessage> { it.dateEpochSec }.thenBy { it.messageId })
            _activeChat.value = _activeChat.value.copy(
                messages = merged,
                oldestMessageId = merged.firstOrNull()?.messageId ?: state.oldestMessageId,
                isLoadingMessages = false
            )
        }
    }

    private fun reloadActiveChatMessagesSilent() {
        val chat = _activeChat.value.chat ?: return
        viewModelScope.launch {
            val page = telegram.loadChatMessages(chat.chatId, chat.title, chat.username, limit = 30)
            val merged = (_activeChat.value.messages + page.messages)
                .distinctBy { it.messageId }
                .sortedWith(compareBy<ChatMessage> { it.dateEpochSec }.thenBy { it.messageId })
            _activeChat.value = _activeChat.value.copy(messages = merged, canSend = page.canSend)
        }
    }

    fun sendChatMessage(text: String) {
        val chat = _activeChat.value.chat ?: return
        viewModelScope.launch {
            val res = telegram.sendTextMessage(chat.chatId, text)
            if (res.isFailure) {
                _uiMessage.value = res.exceptionOrNull()?.message ?: "Не удалось отправить"
            } else {
                reloadActiveChatMessagesSilent()
            }
        }
    }

    fun searchPublicChannels(query: String) {
        if (query.trim().length < 2) {
            _globalSearchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearchingGlobal.value = true
            _globalSearchResults.value = telegram.searchGlobalChats(query, _starredChatIds.value)
            _isSearchingGlobal.value = false
        }
    }

    fun joinAndOpenChat(chat: TelegramChat) {
        viewModelScope.launch {
            telegram.joinChat(chat.chatId)
            openChat(chat)
            refreshChats()
        }
    }

    // ---- Playback Controls ----

    fun playTrack(track: AudioTrack, trackQueue: List<AudioTrack> = emptyList()) {
        val q = if (trackQueue.isNotEmpty()) trackQueue else _queue.value.ifEmpty { listOf(track) }
        _queue.value = q
        _currentTrack.value = track
        val savedPos = trackPositions[track.trackId] ?: 0L
        _positionMs.value = savedPos
        _durationMs.value = track.durationSec * 1000L

        val ctrl = mediaController
        if (ctrl == null) {
            connectMediaController()
            _uiMessage.value = "Плеер подключается, нажмите ещё раз"
            return
        }

        _isBuffering.value = true
        ctrl.setMediaItem(AudiobookPlayerService.buildMediaItem(track), savedPos)
        ctrl.playbackParameters = PlaybackParameters(_playbackSpeed.value)
        ctrl.prepare()
        ctrl.play()
        flushPositions()

        if (track.coverPath == null && track.coverFileId > 0) {
            viewModelScope.launch(Dispatchers.IO) {
                val p = telegram.downloadSmallFile(track.coverFileId)
                if (!p.isNullOrBlank() && _currentTrack.value?.trackId == track.trackId) {
                    _currentTrack.value = _currentTrack.value?.copy(coverPath = p)
                }
            }
        }
    }

    fun togglePlayPause() {
        val ctrl = mediaController ?: run {
            connectMediaController()
            return
        }
        val cur = _currentTrack.value
        if (ctrl.mediaItemCount == 0 && cur != null) {
            playTrack(cur, _queue.value)
            return
        }
        if (ctrl.isPlaying) {
            ctrl.pause()
        } else {
            // Smart rewind after pause > 30s
            if (_smartRewind.value && lastPausedAtMs > 0L && System.currentTimeMillis() - lastPausedAtMs > 30_000L) {
                val rewound = (ctrl.currentPosition - 4_000L).coerceAtLeast(0L)
                ctrl.seekTo(rewound)
            }
            ctrl.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceAtLeast(0L)
        _positionMs.value = target
        mediaController?.seekTo(target)
        _currentTrack.value?.let { trackPositions[it.trackId] = target }
    }

    fun skipBySeconds(deltaSec: Int) {
        val cur = mediaController?.currentPosition ?: _positionMs.value
        val dur = _durationMs.value
        val next = (cur + deltaSec * 1000L).coerceAtLeast(0L)
        seekTo(if (dur > 0L) minOf(dur, next) else next)
    }

    fun playNext() {
        val q = _queue.value
        val cur = _currentTrack.value ?: return
        val idx = q.indexOfFirst { it.trackId == cur.trackId }
        if (idx in 0 until q.lastIndex) {
            playTrack(q[idx + 1], q)
        }
    }

    fun playPrevious() {
        val ctrl = mediaController
        if (ctrl != null && ctrl.currentPosition > 5_000L) {
            seekTo(0L)
            return
        }
        val q = _queue.value
        val cur = _currentTrack.value ?: return
        val idx = q.indexOfFirst { it.trackId == cur.trackId }
        if (idx > 0) {
            playTrack(q[idx - 1], q)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val safe = speed.takeIf { it.isFinite() }?.coerceIn(0.5f, 3.0f) ?: 1.0f
        _playbackSpeed.value = safe
        mediaController?.playbackParameters = PlaybackParameters(safe)
        persistScope.launch { store.savePlaybackSpeed(safe) }
    }

    fun setSleepTimerMinutes(minutes: Int) {
        sleepJob?.cancel()
        when {
            minutes == 0 -> _sleepTimer.value = SleepTimerState.Off
            minutes < 0 -> _sleepTimer.value = SleepTimerState.EndOfTrack
            else -> {
                val totalSec = minutes * 60
                _sleepTimer.value = SleepTimerState.Countdown(totalSec)
                sleepJob = viewModelScope.launch {
                    var rem = totalSec
                    while (rem > 0) {
                        delay(1_000L)
                        rem--
                        _sleepTimer.value = SleepTimerState.Countdown(rem)
                    }
                    _sleepTimer.value = SleepTimerState.Off
                    mediaController?.pause()
                    _uiMessage.value = "Таймер сна завершён — воспроизведение приостановлено"
                }
            }
        }
    }

    // ---- Bookmarks, Favorites, Completed, Offline ----

    fun toggleFavorite(track: AudioTrack) {
        val cur = _favorites.value
        val updated = if (cur.any { it.trackId == track.trackId }) {
            cur.filterNot { it.trackId == track.trackId }
        } else {
            listOf(track) + cur
        }
        _favorites.value = updated
        persistScope.launch { store.saveFavorites(updated) }
    }

    fun markCompleted(trackId: String, completed: Boolean) {
        val updated = if (completed) _completedIds.value + trackId else _completedIds.value - trackId
        _completedIds.value = updated
        persistScope.launch { store.saveCompletedIds(updated) }
    }

    fun addBookmark(track: AudioTrack, positionSec: Long, note: String) {
        val bm = AudioBookmark(
            id = UUID.randomUUID().toString(),
            trackId = track.trackId,
            trackTitle = track.title,
            positionSec = positionSec.coerceAtLeast(0L),
            note = note.trim().ifBlank { "Закладка" },
            createdAtMs = System.currentTimeMillis()
        )
        val updated = listOf(bm) + _bookmarks.value
        _bookmarks.value = updated
        persistScope.launch { store.saveBookmarks(updated) }
        _uiMessage.value = "Закладка сохранена"
    }

    fun deleteBookmark(id: String) {
        val updated = _bookmarks.value.filterNot { it.id == id }
        _bookmarks.value = updated
        persistScope.launch { store.saveBookmarks(updated) }
    }

    fun downloadOffline(track: AudioTrack) {
        viewModelScope.launch {
            val res = telegram.downloadTrackOffline(track)
            if (res.isSuccess) {
                _uiMessage.value = "Скачано офлайн: ${track.title}"
                refreshCacheStats()
            } else {
                _uiMessage.value = res.exceptionOrNull()?.message ?: "Ошибка скачивания"
            }
        }
    }

    fun removeOffline(trackId: String) {
        viewModelScope.launch {
            telegram.removeOfflineTrack(trackId)
            refreshCacheStats()
        }
    }

    // ---- Settings & Proxies ----

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        persistScope.launch { store.saveThemeMode(mode) }
    }

    fun setSkipSilence(enabled: Boolean) {
        _skipSilence.value = enabled
        AudiobookPlayerService.applySkipSilence(enabled)
        persistScope.launch { store.saveSkipSilence(enabled) }
    }

    fun setSeekStepSec(sec: Int) {
        _seekStepSec.value = sec
        AudiobookPlayerService.applySeekStepSec(sec)
        persistScope.launch { store.saveSeekStepSec(sec) }
    }

    fun setSmartRewind(enabled: Boolean) {
        _smartRewind.value = enabled
        persistScope.launch { store.saveSmartRewind(enabled) }
    }

    fun setDspPreset(preset: DspPreset) {
        _dspPreset.value = preset
        AudiobookPlayerService.applyDspPreset(preset)
        persistScope.launch { store.saveDspPreset(preset) }
    }

    fun setLoudnessBoostDb(db: Int) {
        val safe = db.coerceIn(0, 12)
        _loudnessBoostDb.value = safe
        AudiobookPlayerService.applyLoudnessBoostDb(safe)
        persistScope.launch { store.saveLoudnessBoostDb(safe) }
    }

    fun setDacMode(mode: DacOutputMode) {
        _dacMode.value = mode
        AudiobookPlayerService.applyDacConfig(mode, _preferredDacId.value)
        persistScope.launch { store.saveDacMode(mode) }
    }

    fun setPreferredDac(id: Int?) {
        _preferredDacId.value = id
        AudiobookPlayerService.applyDacConfig(_dacMode.value, id)
        persistScope.launch { store.savePreferredDacId(id) }
    }

    fun refreshDacDevices() = dacManager.refreshDevices()

    fun addProxy(type: ProxyType, server: String, port: Int, user: String, pass: String, secret: String) {
        viewModelScope.launch {
            val res = telegram.addProxy(type, server, port, user, pass, secret, enable = true)
            _uiMessage.value = if (res.isSuccess) "Прокси добавлен и включён" else res.exceptionOrNull()?.message
        }
    }

    fun enableProxy(id: Int) {
        viewModelScope.launch { telegram.enableProxy(id) }
    }

    fun disableProxies() {
        viewModelScope.launch { telegram.disableAllProxies() }
    }

    fun removeProxy(id: Int) {
        viewModelScope.launch { telegram.removeProxy(id) }
    }

    fun pingProxy(id: Int) {
        viewModelScope.launch { telegram.pingProxy(id) }
    }

    fun refreshCacheStats() {
        viewModelScope.launch { _cacheStats.value = telegram.computeCacheStats() }
    }

    fun clearStreamingCache() {
        viewModelScope.launch {
            _cacheStats.value = telegram.clearStreamingCache()
            _uiMessage.value = "Кеш стриминга очищен (офлайн-книги сохранены)"
        }
    }

    fun logout() {
        mediaController?.stop()
        _starredChatIds.value = emptySet()
        persistScope.launch { store.clearSessionSelections() }
        telegram.logout()
    }

    fun dismissUiMessage() {
        _uiMessage.value = null
    }

    fun getTrackPositionMs(trackId: String): Long = trackPositions[trackId] ?: 0L

    private fun groupIntoSeries(tracks: List<AudioTrack>, completed: Set<String>): List<BookSeries> {
        val grouped = tracks.groupBy { t ->
            val base = t.title
                .replace(Regex("""(?i)\b(глава|часть|книга|том|файл|ч\.|гл\.|part|chapter|ch\.?)\s*\d+.*$"""), "")
                .replace(Regex("""\s*[-–—:#(\[]+\s*\d+\s*[\])]*$"""), "")
                .trim()
                .ifBlank { t.channelTitle }
            "${t.chatId}|${base.lowercase()}"
        }
        return grouped.values
            .filter { it.size >= 2 }
            .map { list ->
                val sorted = list.sortedBy { it.messageId }
                val first = sorted.first()
                val title = first.title
                    .replace(Regex("""(?i)\b(глава|часть|книга|том|файл|ч\.|гл\.|part|chapter|ch\.?)\s*\d+.*$"""), "")
                    .replace(Regex("""\s*[-–—:#(\[]+\s*\d+\s*[\])]*$"""), "")
                    .trim()
                    .ifBlank { first.channelTitle }
                BookSeries(
                    id = "${first.chatId}_${title.hashCode()}",
                    title = title,
                    author = first.author,
                    channelTitle = first.channelTitle,
                    coverPath = sorted.firstNotNullOfOrNull { it.coverPath },
                    tracks = sorted,
                    completedCount = sorted.count { it.trackId in completed }
                )
            }
            .sortedByDescending { it.tracks.size }
    }

    override fun onCleared() {
        flushPositions()
        mediaController?.release()
        mediaController = null
        super.onCleared()
    }
}
