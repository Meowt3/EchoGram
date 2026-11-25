package com.tgaudiobooks.app.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.tgaudiobooks.app.data.model.AudioBookmark
import com.tgaudiobooks.app.data.model.AudioChapter
import com.tgaudiobooks.app.data.model.AudioStreamFormatInfo
import com.tgaudiobooks.app.data.model.AudioTrack
import com.tgaudiobooks.app.data.model.BookSeriesGroup
import com.tgaudiobooks.app.data.model.DacDeviceInfo
import com.tgaudiobooks.app.data.model.DacOutputMode
import com.tgaudiobooks.app.data.model.DiscoveredCategory
import com.tgaudiobooks.app.data.model.DspPreset
import com.tgaudiobooks.app.data.model.MixCollection
import com.tgaudiobooks.app.data.model.PlaybackSignal
import com.tgaudiobooks.app.data.model.ProgressBarStyle
import com.tgaudiobooks.app.data.model.ReadingSummary
import com.tgaudiobooks.app.data.model.SleepTimerState
import com.tgaudiobooks.app.data.model.SourceType
import com.tgaudiobooks.app.data.model.StorageCacheStats
import com.tgaudiobooks.app.data.model.TelegramAuthState
import com.tgaudiobooks.app.data.model.TelegramChannel
import com.tgaudiobooks.app.data.model.TelegramChatTarget
import com.tgaudiobooks.app.data.model.TelegramChatUiState
import com.tgaudiobooks.app.data.model.TelegramCloudSyncState
import com.tgaudiobooks.app.data.model.TelegramProxyItem
import com.tgaudiobooks.app.data.model.TelegramProxyType
import com.tgaudiobooks.app.data.model.ThemeMode
import com.tgaudiobooks.app.data.model.UserPlaylist
import com.tgaudiobooks.app.widget.EchoGramWidgetProvider
import com.tgaudiobooks.app.data.recommendations.RecommendationEngine
import com.tgaudiobooks.app.data.telegram.TelegramRepository
import com.tgaudiobooks.app.player.AudiobookPlayerService
import com.tgaudiobooks.app.player.DacAudioManager
import com.tgaudiobooks.app.ui.theme.AppIconManager
import com.tgaudiobooks.app.ui.theme.AppIconPreset
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("echogram_prefs", Context.MODE_PRIVATE)

    val telegramRepo = TelegramRepository.getInstance(application)
    private val dacManager = DacAudioManager.getInstance(application)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val persistScope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    private var mediaController: MediaController? = null
    private var pendingPlayAction: (() -> Unit)? = null
    private var sleepJob: Job? = null
    private var playJob: Job? = null
    private var autoCloudPushJob: Job? = null
    private var coverFlushJob: Job? = null
    private var refreshTracksJob: Job? = null
    private var prefetchJob: Job? = null
    private var chatLoadJob: Job? = null
    private val inFlightChatPhotoIds = ConcurrentHashMap.newKeySet<Int>()
    private val mixBuildLock = Mutex()
    private val trackLoadJobs = ConcurrentHashMap<String, Job>()
    private val coverSemaphore = Semaphore(10)
    private val resolvedCovers = ConcurrentHashMap<String, String>()
    private val inFlightCoverIds = ConcurrentHashMap.newKeySet<String>()
    private val pendingCoverUpdates = ConcurrentHashMap<String, String>()
    private val trackPositionsLock = Any()
    private val trackPositions = LinkedHashMap<String, Long>(64, 0.75f, true)
    private val shuffleDeck = ArrayDeque<String>()
    private val playHistoryStack = ArrayDeque<String>()
    private var trackStartedAtMs = 0L
    private var didAutoLoadChannels = false
    private var didAutoPullCloudSync = false
    private var lastCloudSaveMs = 0L
    private var lastSavedTrackId: String? = null
    private var lastSavedCoverPath: String? = null
    private var lastSavedPosMs: Long = -1L
    private var lastPrefsPosSaveMs: Long = 0L
    private var lastWidgetSignature: String? = null
    private var retriedErrorTrackId: String? = null
    @Volatile
    private var lastPausedAtMs: Long = 0L
    @Volatile
    private var lastPrefetchedPair: String? = null
    @Volatile
    private var lastMixesBuildMs: Long = prefs.getLong(PREF_LAST_MIXES_BUILD_MS, 0L)
    @Volatile
    private var lastMixesChannelSig: String = prefs.getString(PREF_MIXES_CHANNEL_SIG, "") ?: ""
    @Volatile
    private var lockedMixSnapshots: List<LockedMixSnapshot> = loadLockedMixSnapshots()
    @Volatile
    private var lastCategoriesFingerprint: String = ""

    private val _cloudSyncState = MutableStateFlow(
        TelegramCloudSyncState(
            isBusy = false,
            autoSyncEnabled = prefs.getBoolean(PREF_TG_CLOUD_AUTOSYNC, true),
            lastSyncedAtMs = prefs.getLong(PREF_TG_CLOUD_LAST_SYNC_MS, 0L)
        )
    )
    val cloudSyncState: StateFlow<TelegramCloudSyncState> = _cloudSyncState.asStateFlow()

    private val _themeMode = MutableStateFlow(
        ThemeMode.fromWire(prefs.getString("theme_mode", ThemeMode.SYSTEM.wireValue))
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _progressBarStyle = MutableStateFlow(
        ProgressBarStyle.fromWire(prefs.getString("progress_bar_style", ProgressBarStyle.WAVE.wireValue))
    )
    val progressBarStyle: StateFlow<ProgressBarStyle> = _progressBarStyle.asStateFlow()

    private val _appIcon = MutableStateFlow(AppIconManager.getSelectedIcon(application))
    val appIcon: StateFlow<AppIconPreset> = _appIcon.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(false)
    val repeatMode: StateFlow<Boolean> = _repeatMode.asStateFlow()

    private val _channels = MutableStateFlow<List<TelegramChannel>>(emptyList())
    val channels: StateFlow<List<TelegramChannel>> = _channels.asStateFlow()

    private val _isLoadingChannels = MutableStateFlow(false)
    val isLoadingChannels: StateFlow<Boolean> = _isLoadingChannels.asStateFlow()

    private val loadingChannelIds = ConcurrentHashMap.newKeySet<String>()
    private val _isLoadingTracks = MutableStateFlow(false)
    val isLoadingTracks: StateFlow<Boolean> = _isLoadingTracks.asStateFlow()

    private val _tracksByChannel = MutableStateFlow<Map<String, List<AudioTrack>>>(emptyMap())
    val tracksByChannel: StateFlow<Map<String, List<AudioTrack>>> = _tracksByChannel.asStateFlow()

    private val _favorites = MutableStateFlow<List<AudioTrack>>(emptyList())
    val favorites: StateFlow<List<AudioTrack>> = _favorites.asStateFlow()

    private val _playlists = MutableStateFlow<List<UserPlaylist>>(emptyList())
    val playlists: StateFlow<List<UserPlaylist>> = _playlists.asStateFlow()

    private val _playHistory = MutableStateFlow<List<PlaybackSignal>>(emptyList())
    val playHistory: StateFlow<List<PlaybackSignal>> = _playHistory.asStateFlow()

    private val _recommendationSeed = MutableStateFlow(prefs.getInt(PREF_RECOMMENDATION_SEED, 1))
    val recommendationSeed: StateFlow<Int> = _recommendationSeed.asStateFlow()

    private val _currentTrack = MutableStateFlow<AudioTrack?>(loadSavedLastTrack())
    val currentTrack: StateFlow<AudioTrack?> = _currentTrack.asStateFlow()

    private val _isBufferingTrack = MutableStateFlow(false)
    val isBufferingTrack: StateFlow<Boolean> = _isBufferingTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(prefs.getLong("last_position_ms", 0L).coerceAtLeast(0L))
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(
        (_currentTrack.value?.durationSec ?: 0L).coerceAtLeast(0L) * 1000L
    )
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(prefs.getFloat("playback_speed", 1.0f))
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _sleepTimer = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)
    val sleepTimer: StateFlow<SleepTimerState> = _sleepTimer.asStateFlow()

    private val _queue = MutableStateFlow<List<AudioTrack>>(emptyList())
    val queue: StateFlow<List<AudioTrack>> = _queue.asStateFlow()

    private val _tracklist = MutableStateFlow<List<AudioTrack>>(emptyList())
    val tracklist: StateFlow<List<AudioTrack>> = _tracklist.asStateFlow()

    private val _isTracklistMode = MutableStateFlow(prefs.getBoolean("tracklist_mode", false))
    val isTracklistMode: StateFlow<Boolean> = _isTracklistMode.asStateFlow()

    val proxies: StateFlow<List<TelegramProxyItem>> = telegramRepo.proxies

    private val _isProxyBusy = MutableStateFlow(false)
    val isProxyBusy: StateFlow<Boolean> = _isProxyBusy.asStateFlow()

    private val _proxyError = MutableStateFlow<String?>(null)
    val proxyError: StateFlow<String?> = _proxyError.asStateFlow()

    private val _proxyMessage = MutableStateFlow<String?>(null)
    val proxyMessage: StateFlow<String?> = _proxyMessage.asStateFlow()

    private val _cacheStats = MutableStateFlow(StorageCacheStats())
    val cacheStats: StateFlow<StorageCacheStats> = _cacheStats.asStateFlow()

    private val _isCacheBusy = MutableStateFlow(false)
    val isCacheBusy: StateFlow<Boolean> = _isCacheBusy.asStateFlow()

    private val _cacheMessage = MutableStateFlow<String?>(null)
    val cacheMessage: StateFlow<String?> = _cacheMessage.asStateFlow()

    private val _cacheTtlDays = MutableStateFlow(prefs.getInt("cache_ttl_days", 7))
    val cacheTtlDays: StateFlow<Int> = _cacheTtlDays.asStateFlow()

    private val _dspPreset = MutableStateFlow(
        DspPreset.fromWire(prefs.getString("dsp_preset", DspPreset.STANDARD.wireValue))
    )
    val dspPreset: StateFlow<DspPreset> = _dspPreset.asStateFlow()

    private val _skipSilence = MutableStateFlow(prefs.getBoolean("skip_silence", false))
    val skipSilence: StateFlow<Boolean> = _skipSilence.asStateFlow()

    val dacDevices: StateFlow<List<DacDeviceInfo>> = dacManager.devices
    val dacRoutingSummary: StateFlow<String> = dacManager.activeRoutingSummary
    val dacBitPerfectActive: StateFlow<Boolean> = dacManager.isBitPerfectMixerActive
    val streamFormat: StateFlow<AudioStreamFormatInfo> = dacManager.streamFormat
    val embeddedChapters: StateFlow<Pair<String, List<AudioChapter>>> = AudiobookPlayerService.embeddedChapters

    private val _dacOutputMode = MutableStateFlow(
        DacOutputMode.fromWire(prefs.getString("dac_output_mode", DacOutputMode.AUTO.wireValue))
    )
    val dacOutputMode: StateFlow<DacOutputMode> = _dacOutputMode.asStateFlow()

    private val _dacPreferredDeviceId = MutableStateFlow(
        prefs.getInt("dac_preferred_device_id", -1).takeIf { it > 0 }
    )
    val dacPreferredDeviceId: StateFlow<Int?> = _dacPreferredDeviceId.asStateFlow()

    private val _loudnessBoostDb = MutableStateFlow(
        prefs.getInt("loudness_boost_db", 0).coerceIn(0, 12)
    )
    val loudnessBoostDb: StateFlow<Int> = _loudnessBoostDb.asStateFlow()

    private val _seekStepSec = MutableStateFlow(
        prefs.getInt("seek_step_sec", 15).let { if (it == 10 || it == 15 || it == 30) it else 15 }
    )
    val seekStepSec: StateFlow<Int> = _seekStepSec.asStateFlow()

    private val _smartRewindEnabled = MutableStateFlow(
        prefs.getBoolean("smart_rewind_enabled", true)
    )
    val smartRewindEnabled: StateFlow<Boolean> = _smartRewindEnabled.asStateFlow()

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<AudioBookmark>>(emptyList())
    val bookmarks: StateFlow<List<AudioBookmark>> = _bookmarks.asStateFlow()

    private val _completedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val completedTrackIds: StateFlow<Set<String>> = _completedTrackIds.asStateFlow()

    private val _dislikedIds = MutableStateFlow<Set<String>>(emptySet())
    val dislikedIds: StateFlow<Set<String>> = _dislikedIds.asStateFlow()

    private val _tasteClusters = MutableStateFlow<List<String>>(emptyList())
    val tasteClusters: StateFlow<List<String>> = _tasteClusters.asStateFlow()

    private val _dailyStats = MutableStateFlow<Map<String, Long>>(emptyMap())
    val dailyStats: StateFlow<Map<String, Long>> = _dailyStats.asStateFlow()

    private val _chatUiState = MutableStateFlow(TelegramChatUiState())
    val chatUiState: StateFlow<TelegramChatUiState> = _chatUiState.asStateFlow()

    val downloadedTracks: StateFlow<List<AudioTrack>> = telegramRepo.downloadedTracks
    val downloadedTrackIds: StateFlow<Set<String>> = telegramRepo.downloadedTrackIds
    val downloadingProgress: StateFlow<Map<String, Float>> = telegramRepo.downloadingProgress

    private data class HistoryDigest(
        val size: Int,
        val headTrackId: String?,
        val totalPlayCount: Int,
        val totalSkipCount: Int
    )
    private data class LockedMixSnapshot(
        val id: String,
        val title: String,
        val subtitle: String,
        val kicker: String,
        val description: String,
        val isAutoPlaylist: Boolean,
        val trackIds: List<String>
    )
    private data class CatalogSyncKey(
        val fingerprint: String,
        val loadingChannels: Boolean,
        val loadingTracks: Boolean,
        val completed: Set<String>,
        val historyDigest: HistoryDigest,
        val dislikedCount: Int
    )
    private data class SyncResultBundle(
        val mixes: List<MixCollection>,
        val autoPlaylists: List<MixCollection>,
        val seriesGroups: List<BookSeriesGroup>,
        val categories: List<DiscoveredCategory>?,
        val tasteClusters: List<String>?,
        val commitData: Triple<List<LockedMixSnapshot>, Long, String>?
    )

    private val historyDigest = _playHistory
        .map { list ->
            HistoryDigest(
                size = list.size,
                headTrackId = list.firstOrNull()?.trackId,
                totalPlayCount = list.sumOf { it.playCount },
                totalSkipCount = list.sumOf { it.skipCount }
            )
        }
        .distinctUntilChanged()

    val allTracks: StateFlow<List<AudioTrack>> = _tracksByChannel
        .map { map -> map.values.flatten().sortedByDescending { it.dateEpoch } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _mixes = MutableStateFlow<List<MixCollection>>(emptyList())
    val mixes: StateFlow<List<MixCollection>> = _mixes.asStateFlow()

    private val _autoPlaylists = MutableStateFlow<List<MixCollection>>(emptyList())
    val autoPlaylists: StateFlow<List<MixCollection>> = _autoPlaylists.asStateFlow()

    private val _seriesGroups = MutableStateFlow<List<BookSeriesGroup>>(emptyList())
    val seriesGroups: StateFlow<List<BookSeriesGroup>> = _seriesGroups.asStateFlow()

    private val _categories = MutableStateFlow<List<DiscoveredCategory>>(emptyList())
    val categories: StateFlow<List<DiscoveredCategory>> = _categories.asStateFlow()

    @OptIn(FlowPreview::class)
    val readingSummary: StateFlow<ReadingSummary> = combine(
        _dailyStats,
        historyDigest,
        _completedTrackIds
    ) { stats, _, completed ->
        Triple(stats, _playHistory.value, completed)
    }
        .debounce(200L)
        .map { (stats, signals, completed) ->
            RecommendationEngine.computeReadingSummary(stats, signals, completed)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReadingSummary())

    init {
        AudiobookPlayerService.applyDspPreset(_dspPreset.value)
        AudiobookPlayerService.applySkipSilence(_skipSilence.value)
        AudiobookPlayerService.applyDacConfig(_dacOutputMode.value, _dacPreferredDeviceId.value)
        AudiobookPlayerService.applyLoudnessBoostDb(_loudnessBoostDb.value)
        AudiobookPlayerService.applySeekStepSec(_seekStepSec.value)
        AudiobookPlayerService.onSkipNextRequested = { playNext() }
        AudiobookPlayerService.onSkipPreviousRequested = { playPrevious() }
        AudiobookPlayerService.onWidgetPlayPauseRequested = { togglePlayPause() }

        persistScope.launch {
            loadTrackPositions()
            val loadedFavs = loadTrackList("favorites_json")
            val loadedPlaylists = loadPlaylists()
            val loadedHistory = loadPlaybackSignals()
            val loadedTracklist = loadTrackList("tracklist_json")
            val loadedBookmarks = loadBookmarks()
            val loadedCompleted = loadCompletedIds()
            val loadedDisliked = loadDislikedIds()
            val loadedDailyStats = loadDailyStats()

            if (_favorites.value.isEmpty() && loadedFavs.isNotEmpty()) _favorites.value = loadedFavs
            if (_playlists.value.isEmpty() && loadedPlaylists.isNotEmpty()) _playlists.value = loadedPlaylists
            if (_playHistory.value.isEmpty() && loadedHistory.isNotEmpty()) _playHistory.value = loadedHistory
            if (_tracklist.value.isEmpty() && loadedTracklist.isNotEmpty()) _tracklist.value = loadedTracklist
            if (_bookmarks.value.isEmpty() && loadedBookmarks.isNotEmpty()) _bookmarks.value = loadedBookmarks
            if (_completedTrackIds.value.isEmpty() && loadedCompleted.isNotEmpty()) _completedTrackIds.value = loadedCompleted
            if (_dislikedIds.value.isEmpty() && loadedDisliked.isNotEmpty()) _dislikedIds.value = loadedDisliked
            if (_dailyStats.value.isEmpty() && loadedDailyStats.isNotEmpty()) _dailyStats.value = loadedDailyStats
        }

        _currentTrack.value?.let { cur ->
            lastSavedTrackId = cur.trackId
            lastSavedCoverPath = cur.coverPath
            updateWidgetAsync(cur, isPlaying = false)
        }

        observeCatalogForMixesAndSeries()
        connectMediaController()
        startPositionTicker()
        observeTelegramConnection()
        telegramRepo.initializeFromStoredOrDefaults()
    }

    @OptIn(FlowPreview::class)
    private fun observeCatalogForMixesAndSeries() {
        viewModelScope.launch {
            combine(
                allTracks,
                _isLoadingChannels,
                _isLoadingTracks,
                _completedTrackIds,
                historyDigest
            ) { tracks, loadingCh, loadingTr, completed, hDigest ->
                CatalogSyncKey(
                    fingerprint = computeCatalogFingerprint(tracks),
                    loadingChannels = loadingCh,
                    loadingTracks = loadingTr,
                    completed = completed,
                    historyDigest = hDigest,
                    dislikedCount = _dislikedIds.value.size
                )
            }
                .distinctUntilChanged()
                .debounce(220L)
                .collect {
                    syncMixesAndSeries(forceRebuild = false)
                }
        }
    }

    private fun selectedChannelSignature(): String {
        val liveChannels = _channels.value
        val ids = if (liveChannels.isNotEmpty()) {
            liveChannels.filter { it.isSelected }.map { it.channelId }
        } else {
            prefs.getStringSet("selected_channel_ids", emptySet()).orEmpty().toList()
        }
        return ids.sorted().joinToString(",")
    }

    private fun computeCatalogFingerprint(tracks: List<AudioTrack>): String {
        var h = 1125899906842597L
        for (t in tracks) {
            h = h * 31L + t.trackId.hashCode().toLong()
        }
        return "${tracks.size}|$h"
    }

    private fun hydrateMixSnapshots(
        snapshots: List<LockedMixSnapshot>,
        tracks: List<AudioTrack>
    ): Pair<List<MixCollection>, List<MixCollection>> {
        if (snapshots.isEmpty() || tracks.isEmpty()) return emptyList<MixCollection>() to emptyList()
        val byId = tracks.associateBy { it.trackId }
        val mixesOut = ArrayList<MixCollection>()
        val autoOut = ArrayList<MixCollection>()
        for (snap in snapshots) {
            val resolved = snap.trackIds.mapNotNull { tid -> byId[tid]?.let { applyCachedCover(it) } }
            if (resolved.isEmpty()) continue
            val collection = MixCollection(
                id = snap.id,
                title = snap.title,
                subtitle = snap.subtitle,
                kicker = snap.kicker,
                description = snap.description,
                isAutoPlaylist = snap.isAutoPlaylist,
                coverPath = RecommendationEngine.pickMixCover(resolved),
                tracks = resolved
            )
            if (snap.isAutoPlaylist) autoOut += collection else mixesOut += collection
        }
        return mixesOut to autoOut
    }

    private suspend fun syncMixesAndSeries(forceRebuild: Boolean) {
        mixBuildLock.withLock {
            val tracks = allTracks.value
            val channelSig = selectedChannelSignature()
            val loading = _isLoadingChannels.value || _isLoadingTracks.value

            if (tracks.isEmpty()) {
                if (!loading || channelSig.isBlank()) {
                    _mixes.value = emptyList()
                    _autoPlaylists.value = emptyList()
                    _seriesGroups.value = emptyList()
                    _categories.value = emptyList()
                    lastCategoriesFingerprint = ""
                }
                return
            }

            val tracksFp = computeCatalogFingerprint(tracks)
            val needCategories = tracksFp != lastCategoriesFingerprint || _categories.value.isEmpty()

            val signals = _playHistory.value
            val completed = _completedTrackIds.value
            val disliked = _dislikedIds.value
            val favorites = _favorites.value
            val playlists = _playlists.value
            val bookmarkedIds = _bookmarks.value.map { it.trackId }.toSet()
            val seed = _recommendationSeed.value

            val result = withContext(Dispatchers.Default) {
                val playedMap = signals.associate { it.trackId to it.playedSec }
                val nextSeries = RecommendationEngine.groupTracksIntoSeries(tracks, playedMap, completed)
                val nextCategories = if (needCategories) {
                    RecommendationEngine.discoverCategories(tracks)
                } else {
                    null
                }

                val now = System.currentTimeMillis()
                val cachedSnapshots = lockedMixSnapshots
                val ageMs = now - lastMixesBuildMs
                val isLocked24h = !forceRebuild &&
                    cachedSnapshots.isNotEmpty() &&
                    lastMixesBuildMs > 0L &&
                    ageMs in 0L until MIX_REFRESH_INTERVAL_MS &&
                    lastMixesChannelSig == channelSig

                if (isLocked24h) {
                    val (hydratedMixes, hydratedAuto) = hydrateMixSnapshots(cachedSnapshots, tracks)
                    if (hydratedMixes.isNotEmpty() || hydratedAuto.isNotEmpty()) {
                        return@withContext SyncResultBundle(
                            mixes = hydratedMixes,
                            autoPlaylists = hydratedAuto,
                            seriesGroups = nextSeries,
                            categories = nextCategories,
                            tasteClusters = null,
                            commitData = null
                        )
                    }
                }

                if (!forceRebuild && loading && cachedSnapshots.isNotEmpty() && lastMixesChannelSig == channelSig) {
                    val (oldMixes, oldAuto) = hydrateMixSnapshots(cachedSnapshots, tracks)
                    if (oldMixes.isNotEmpty() || oldAuto.isNotEmpty()) {
                        return@withContext SyncResultBundle(
                            mixes = oldMixes,
                            autoPlaylists = oldAuto,
                            seriesGroups = nextSeries,
                            categories = nextCategories,
                            tasteClusters = null,
                            commitData = null
                        )
                    }
                }

                val built = RecommendationEngine.buildAll(
                    allTracks = tracks,
                    favorites = favorites,
                    playlists = playlists,
                    signals = signals,
                    seed = seed,
                    completedIdsExternal = completed,
                    bookmarkedIdsExternal = bookmarkedIds,
                    dislikedIdsExternal = disliked
                )

                val shouldCommitLock = forceRebuild || !loading
                val commitData = if (shouldCommitLock && (built.mixes.isNotEmpty() || built.autoPlaylists.isNotEmpty())) {
                    val snapshots = (built.mixes + built.autoPlaylists).map { coll ->
                        LockedMixSnapshot(
                            id = coll.id,
                            title = coll.title,
                            subtitle = coll.subtitle,
                            kicker = coll.kicker,
                            description = coll.description,
                            isAutoPlaylist = coll.isAutoPlaylist,
                            trackIds = coll.tracks.map { it.trackId }
                        )
                    }
                    Triple(snapshots, now, channelSig)
                } else {
                    null
                }

                SyncResultBundle(
                    mixes = built.mixes,
                    autoPlaylists = built.autoPlaylists,
                    seriesGroups = built.seriesGroups,
                    categories = nextCategories,
                    tasteClusters = built.tasteClusters,
                    commitData = commitData
                )
            }

            _mixes.value = result.mixes
            _autoPlaylists.value = result.autoPlaylists
            _seriesGroups.value = result.seriesGroups
            result.tasteClusters?.let { _tasteClusters.value = it }
            result.categories?.let { builtCategories ->
                _categories.value = builtCategories
                lastCategoriesFingerprint = tracksFp
            }

            val leadCoverCandidates = buildList {
                result.mixes.forEach { m ->
                    if (m.coverPath.isNullOrBlank()) m.tracks.firstOrNull { it.coverPath.isNullOrBlank() }?.let(::add)
                }
                result.autoPlaylists.forEach { m ->
                    if (m.coverPath.isNullOrBlank()) m.tracks.firstOrNull { it.coverPath.isNullOrBlank() }?.let(::add)
                }
                result.seriesGroups.take(24).forEach { s ->
                    if (s.coverPath.isNullOrBlank()) s.tracks.firstOrNull { it.coverPath.isNullOrBlank() }?.let(::add)
                }
                _categories.value.take(28).forEach { c ->
                    if (c.coverPath.isNullOrBlank()) c.tracks.firstOrNull { it.coverPath.isNullOrBlank() }?.let(::add)
                }
            }.distinctBy { it.trackId }.take(40)
            if (leadCoverCandidates.isNotEmpty()) {
                prefetchInitialCovers(leadCoverCandidates)
            }

            result.commitData?.let { (snapshots, committedAtMs, committedSig) ->
                lockedMixSnapshots = snapshots
                lastMixesBuildMs = committedAtMs
                lastMixesChannelSig = committedSig
                saveLockedMixSnapshots(snapshots, committedAtMs, committedSig)
            }
        }
    }

    private fun observeTelegramConnection() {
        viewModelScope.launch {
            var wasConnected = false
            telegramRepo.authState.collect { state ->
                if (state is TelegramAuthState.Connected) {
                    wasConnected = true
                    if (!didAutoLoadChannels) {
                        didAutoLoadChannels = true
                        refreshChannels()
                        val ttl = _cacheTtlDays.value
                        if (ttl > 0) telegramRepo.applyCacheTtl(ttl)
                    }
                    if (!didAutoPullCloudSync && _cloudSyncState.value.autoSyncEnabled) {
                        didAutoPullCloudSync = true
                        syncCloudPull(silent = true)
                    }
                    refreshProxies()
                } else if (state is TelegramAuthState.WaitingPhoneNumber) {
                    didAutoLoadChannels = false
                    didAutoPullCloudSync = false
                    if (wasConnected) {
                        wasConnected = false
                        clearTelegramSessionState()
                    }
                    refreshProxies()
                } else if (state is TelegramAuthState.WaitingApiCredentials) {
                    didAutoLoadChannels = false
                    didAutoPullCloudSync = false
                    if (wasConnected) {
                        wasConnected = false
                        clearTelegramSessionState()
                    }
                }
            }
        }
    }

    fun logoutTelegram() {
        clearTelegramSessionState()
        telegramRepo.logout()
    }

    private fun clearTelegramSessionState() {
        refreshTracksJob?.cancel()
        refreshTracksJob = null
        prefetchJob?.cancel()
        prefetchJob = null
        trackLoadJobs.values.forEach { it.cancel() }
        trackLoadJobs.clear()
        loadingChannelIds.clear()
        telegramRepo.clearChannelTrackCache()
        _chatUiState.value = TelegramChatUiState()
        chatLoadJob?.cancel()
        chatLoadJob = null
        _isLoadingTracks.value = false
        _isLoadingChannels.value = false
        _channels.value = emptyList()
        _tracksByChannel.value = emptyMap()
        _mixes.value = emptyList()
        _autoPlaylists.value = emptyList()
        _seriesGroups.value = emptyList()
        _categories.value = emptyList()
        lastCategoriesFingerprint = ""
        lockedMixSnapshots = emptyList()
        lastMixesBuildMs = 0L
        lastMixesChannelSig = ""
        prefs.edit()
            .remove("selected_channel_ids")
            .remove(PREF_LOCKED_MIXES_JSON)
            .remove(PREF_LAST_MIXES_BUILD_MS)
            .remove(PREF_MIXES_CHANNEL_SIG)
            .apply()
    }

    fun setAppIcon(preset: AppIconPreset) {
        _appIcon.value = preset
        AppIconManager.applyAppIcon(getApplication(), preset)
    }

    private fun connectMediaController() {
        val ctx = getApplication<Application>()
        val token = SessionToken(ctx, ComponentName(ctx, AudiobookPlayerService::class.java))
        val future = MediaController.Builder(ctx, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    if (mediaController === controller) {
                        mediaController = null
                        _isPlaying.value = false
                        _isBufferingTrack.value = false
                    }
                }
            })
            .buildAsync()
        future.addListener({
            runCatching { future.get() }.onSuccess { controller ->
                mediaController = controller.apply {
                    addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            _isPlaying.value = isPlaying
                            updateWidgetAsync(_currentTrack.value, isPlaying)
                            if (isPlaying) {
                                lastPausedAtMs = 0L
                            } else {
                                lastPausedAtMs = System.currentTimeMillis()
                                persistPlaybackSnapshot(forceCloud = true, forceLocal = true)
                            }
                        }

                        override fun onPlaybackStateChanged(state: Int) {
                            _isBufferingTrack.value = (state == Player.STATE_BUFFERING)
                            if (state == Player.STATE_READY) {
                                _playbackError.value = null
                            }
                            if (state == Player.STATE_ENDED) {
                                _currentTrack.value?.let { finished ->
                                    if (finished.trackId !in _completedTrackIds.value) {
                                        markTrackCompleted(finished.trackId, manual = false)
                                    }
                                }
                                if (_sleepTimer.value is SleepTimerState.EndOfTrack) {
                                    _sleepTimer.value = SleepTimerState.Off
                                    mediaController?.pause()
                                } else {
                                    playNext(isAutoAdvance = true)
                                }
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            val activeMediaId = mediaController?.currentMediaItem?.mediaId
                            val cur = _currentTrack.value ?: return
                            if (!activeMediaId.isNullOrBlank() && activeMediaId != cur.trackId) return
                            _isBufferingTrack.value = false
                            _isPlaying.value = false
                            val chatIdLong = cur.channelId.toLongOrNull() ?: 0L
                            val isUnsupportedCodec = error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ||
                                error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
                            val activeUri = mediaController?.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty()
                            val wasAlreadyLocalFile = activeUri.startsWith("file://")

                            if (retriedErrorTrackId != cur.trackId &&
                                !isUnsupportedCodec &&
                                !wasAlreadyLocalFile &&
                                (cur.fileId > 0 || (chatIdLong != 0L && cur.messageId > 0L))
                            ) {
                                retriedErrorTrackId = cur.trackId
                                val resumeMs = _positionMs.value
                                playJob?.cancel()
                                playJob = viewModelScope.launch {
                                    _isBufferingTrack.value = true
                                    telegramRepo.resetStreamStateForRetry(cur.fileId)
                                    val fresh = if (chatIdLong != 0L && cur.messageId > 0L) {
                                        telegramRepo.refreshTrackFileFromMessage(chatIdLong, cur.messageId)
                                    } else {
                                        null
                                    }
                                    if (_currentTrack.value?.trackId != cur.trackId) return@launch

                                    val targetFileId = fresh?.id?.takeIf { it > 0 } ?: cur.fileId
                                    val refreshedSize = when {
                                        fresh != null && fresh.size > 0L -> fresh.size
                                        cur.fileSizeBytes > 0L -> cur.fileSizeBytes
                                        fresh != null && fresh.expectedSize > 0L -> fresh.expectedSize
                                        else -> 0L
                                    }
                                    val refreshedTrack = cur.copy(
                                        fileId = targetFileId,
                                        fileSizeBytes = refreshedSize
                                    )
                                    if (fresh != null && fresh.id > 0) {
                                        val currentMap = _tracksByChannel.value
                                        val chTracks = currentMap[cur.channelId]
                                        if (chTracks != null) {
                                            _tracksByChannel.value = currentMap.toMutableMap().apply {
                                                put(
                                                    cur.channelId,
                                                    chTracks.map { t ->
                                                        if (t.trackId == cur.trackId) {
                                                            t.copy(fileId = fresh.id, fileSizeBytes = refreshedSize)
                                                        } else t
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    _positionMs.value = resumeMs
                                    _currentTrack.value = refreshedTrack

                                    // Fallback to clean sequential download into TDLib local cache for tracks
                                    // up to 320 MB so non-faststart M4A/M4B, OGG/Opus, or tricky containers play
                                    // directly from disk via native FileDataSource without partial-seek issues.
                                    val maxDirectCacheBytes = 320L * 1024L * 1024L
                                    if (targetFileId > 0 && (refreshedSize <= 0L || refreshedSize <= maxDirectCacheBytes)) {
                                        val cachedFile = telegramRepo.downloadFileToLocalCacheForPlayback(
                                            fileId = targetFileId,
                                            chatId = chatIdLong,
                                            messageId = cur.messageId
                                        )
                                        if (_currentTrack.value?.trackId != cur.trackId) return@launch
                                        if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0L) {
                                            playTrack(
                                                refreshedTrack,
                                                preserveFileId = true,
                                                forcedLocalFile = cachedFile
                                            )
                                            return@launch
                                        }
                                        _isBufferingTrack.value = false
                                        val reason = telegramRepo.lastStreamError?.takeIf { it.isNotBlank() }
                                        _playbackError.value = if (reason != null) {
                                            "$reason · «${cur.channelTitle}» (${error.errorCodeName})."
                                        } else {
                                            "Не удалось загрузить аудио из канала «${cur.channelTitle}» (${error.errorCodeName})."
                                        }
                                        return@launch
                                    }

                                    if (targetFileId > 0) {
                                        playTrack(refreshedTrack, preserveFileId = true)
                                    } else {
                                        _isBufferingTrack.value = false
                                        val reason = telegramRepo.lastStreamError?.takeIf { it.isNotBlank() }
                                        _playbackError.value = if (reason != null) {
                                            "$reason · «${cur.channelTitle}»."
                                        } else {
                                            "Не удалось загрузить аудио из канала «${cur.channelTitle}»."
                                        }
                                    }
                                }
                            } else {
                                val streamReason = telegramRepo.lastStreamError?.takeIf { it.isNotBlank() }
                                val causeDetail = error.cause?.let { c ->
                                    val msg = c.message?.trim()?.take(72).orEmpty()
                                    if (msg.isNotBlank()) "${c.javaClass.simpleName}: $msg" else c.javaClass.simpleName
                                }.orEmpty()
                                val mimeTag = cur.mimeType.takeIf { it.isNotBlank() }?.let { " [$it]" }.orEmpty()
                                _playbackError.value = when {
                                    isUnsupportedCodec || error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
                                        "Формат аудио «${cur.title.take(32)}»$mimeTag не поддерживается (${error.errorCodeName})."
                                    streamReason != null ->
                                        "$streamReason · «${cur.channelTitle}» (${error.errorCodeName})."
                                    causeDetail.isNotBlank() ->
                                        "Ошибка воспроизведения (${error.errorCodeName}$mimeTag): $causeDetail"
                                    else ->
                                        "Ошибка воспроизведения в канале «${cur.channelTitle}» (${error.errorCodeName}$mimeTag)."
                                }
                            }
                        }
                    })
                }
                _isPlaying.value = controller.isPlaying
                _isBufferingTrack.value = (controller.playbackState == Player.STATE_BUFFERING)
                if (controller.mediaItemCount > 0) {
                    val pos = controller.currentPosition.coerceAtLeast(0L)
                    if (pos > 0L) _positionMs.value = pos
                    val dur = controller.duration
                    if (dur > 0L) _durationMs.value = dur
                    val activeId = controller.currentMediaItem?.mediaId
                    if (!activeId.isNullOrBlank() && _currentTrack.value?.trackId != activeId) {
                        allSourceTracks().firstOrNull { it.trackId == activeId }?.let { matched ->
                            _currentTrack.value = applyCachedCover(matched)
                        }
                    }
                    updateWidgetAsync(_currentTrack.value, controller.isPlaying)
                }
                pendingPlayAction?.let { action ->
                    pendingPlayAction = null
                    action.invoke()
                }
            }
        }, MoreExecutors.directExecutor())
    }

    private fun updateWidgetAsync(track: AudioTrack?, isPlaying: Boolean) {
        val cur = track ?: return
        val sig = "${cur.trackId}|${cur.coverPath.orEmpty()}|$isPlaying"
        if (sig == lastWidgetSignature) return
        lastWidgetSignature = sig
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            EchoGramWidgetProvider.updateAll(
                context = app,
                title = cur.title,
                author = cur.author.ifBlank { cur.channelTitle },
                coverPath = cur.coverPath,
                isPlaying = isPlaying
            )
        }
    }

    private fun startPositionTicker() {
        viewModelScope.launch {
            var statTicks = 0
            while (true) {
                mediaController?.let { mc ->
                    if (mc.mediaItemCount > 0) {
                        val pos = mc.currentPosition.coerceAtLeast(0L)
                        if (pos != _positionMs.value) {
                            _positionMs.value = pos
                        }
                        val dur = mc.duration
                        if (dur > 0L && dur != _durationMs.value) {
                            _durationMs.value = dur
                        }
                        val active = _currentTrack.value
                        if (active != null && dur > 30_000L && pos >= (dur * 0.92).toLong()) {
                            if (active.trackId !in _completedTrackIds.value) {
                                markTrackCompleted(active.trackId, manual = false)
                            }
                        }
                        if (mc.isPlaying) {
                            persistPlaybackSnapshot(forceCloud = false, forceLocal = false)
                            if (dur > 10_000L && dur - pos in 1L..90_000L) {
                                triggerNextTrackPrefetch(delayMs = 0L)
                            }
                            statTicks++
                            if (statTicks >= 10) {
                                statTicks = 0
                                incrementDailyReadingSeconds(5L)
                            }
                        }
                    }
                }
                delay(500L)
            }
        }
    }

    private fun incrementDailyReadingSeconds(deltaSec: Long) {
        val dateKey = RecommendationEngine.getLocalDateKey()
        val nextMap = _dailyStats.value.toMutableMap()
        val updatedSec = (nextMap[dateKey] ?: 0L) + deltaSec
        nextMap[dateKey] = updatedSec
        _dailyStats.value = nextMap
        saveDailyStats(nextMap)
    }

    private fun recordTrackPositionInMemory(trackId: String, posMs: Long, durMs: Long) {
        val cleanPos = if (durMs > 10_000L && posMs >= durMs - 5_000L) 0L else posMs.coerceAtLeast(0L)
        synchronized(trackPositionsLock) {
            if (cleanPos <= 0L) {
                trackPositions.remove(trackId)
            } else {
                trackPositions[trackId] = cleanPos
                while (trackPositions.size > 400) {
                    val eldest = trackPositions.keys.firstOrNull() ?: break
                    trackPositions.remove(eldest)
                }
            }
        }
    }

    private fun serializeTrackPositionsLocked(): String {
        val obj = JSONObject()
        synchronized(trackPositionsLock) {
            trackPositions.forEach { (k, v) -> obj.put(k, v) }
        }
        return obj.toString()
    }

    private fun persistPlaybackSnapshot(forceCloud: Boolean, forceLocal: Boolean = false) {
        val active = _currentTrack.value ?: return
        val posMs = _positionMs.value.coerceAtLeast(0L)
        val durMs = maxOf(_durationMs.value, active.durationSec * 1000L)
        val now = System.currentTimeMillis()
        val trackChanged = active.trackId != lastSavedTrackId || active.coverPath != lastSavedCoverPath
        val posChangedEnough = forceLocal || forceCloud || (now - lastPrefsPosSaveMs >= 3_000L && abs(posMs - lastSavedPosMs) >= 1_500L)

        if (trackChanged || posChangedEnough) {
            if (trackChanged) {
                lastSavedTrackId = active.trackId
                lastSavedCoverPath = active.coverPath
            }
            if (posChangedEnough) {
                lastSavedPosMs = posMs
                lastPrefsPosSaveMs = now
                recordTrackPositionInMemory(active.trackId, posMs, durMs)
            }
            persistScope.launch {
                val editor = prefs.edit()
                    .putLong("last_position_ms", posMs)
                    .putString(PREF_TRACK_POSITIONS_JSON, serializeTrackPositionsLocked())
                if (trackChanged || forceLocal) {
                    editor.putString("last_track_json", trackToJson(active).toString())
                }
                editor.apply()
            }
        }

        if (forceCloud || now - lastCloudSaveMs >= 15_000L) {
            lastCloudSaveMs = now
            recordPlaybackSignal(active, posMs / 1000L)
            if (forceCloud) {
                scheduleAutoCloudPush(delayMs = 20_000L)
            }
        }
    }

    private fun recordPlaybackSignal(
        track: AudioTrack,
        playedSec: Long,
        countPlay: Boolean = false,
        countSkip: Boolean = false
    ) {
        val current = _playHistory.value
        val idx = current.indexOfFirst { it.trackId == track.trackId }
        val now = System.currentTimeMillis()
        val updatedSignal = if (idx >= 0) {
            val prev = current[idx]
            prev.copy(
                playedSec = maxOf(prev.playedSec, playedSec.coerceAtLeast(0L)),
                playCount = if (countPlay) prev.playCount + 1 else prev.playCount,
                skipCount = if (countSkip) prev.skipCount + 1 else prev.skipCount,
                lastPlayedAtMs = now
            )
        } else {
            PlaybackSignal(
                trackId = track.trackId,
                channelId = track.channelId,
                author = track.author,
                title = track.title,
                durationSec = track.durationSec,
                playedSec = playedSec.coerceAtLeast(0L),
                playCount = 1,
                skipCount = if (countSkip) 1 else 0,
                lastPlayedAtMs = now
            )
        }
        val merged = (listOf(updatedSignal) + current.filterNot { it.trackId == track.trackId }).take(250)
        _playHistory.value = merged
        savePlaybackSignals(merged)
    }

    fun setAutoCloudSync(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_TG_CLOUD_AUTOSYNC, enabled).apply()
        _cloudSyncState.value = _cloudSyncState.value.copy(
            autoSyncEnabled = enabled,
            statusMessage = if (enabled) "Автосинхронизация с Telegram включена" else "Автосинхронизация отключена",
            error = null
        )
        if (!enabled) {
            autoCloudPushJob?.cancel()
            autoCloudPushJob = null
        }
    }

    private fun scheduleAutoCloudPush(delayMs: Long = 15_000L) {
        if (!_cloudSyncState.value.autoSyncEnabled) return
        if (telegramRepo.authState.value !is TelegramAuthState.Connected) return
        autoCloudPushJob?.cancel()
        autoCloudPushJob = viewModelScope.launch {
            delay(delayMs)
            syncCloudPush(silent = true)
        }
    }

    fun buildBackupSnapshotJson(): String {
        val selectedIds = prefs.getStringSet("selected_channel_ids", emptySet()).orEmpty() +
            _channels.value.filter { it.isSelected }.map { it.channelId }
        val root = JSONObject().apply {
            put("version", 1)
            put("updatedAt", System.currentTimeMillis())
            put("device", "android")
            put(
                "settings",
                JSONObject().apply {
                    put("themeMode", _themeMode.value.wireValue)
                    put("progressBarStyle", _progressBarStyle.value.wireValue)
                    put("playbackSpeed", _playbackSpeed.value.toDouble())
                    put("dspPreset", _dspPreset.value.wireValue)
                    put("skipSilence", _skipSilence.value)
                }
            )
            put(
                "selectedChannelIds",
                JSONArray().apply { selectedIds.forEach { put(it) } }
            )
            put(
                "favorites",
                JSONArray().apply { _favorites.value.forEach { put(trackToPortableJson(it)) } }
            )
            put(
                "playlists",
                JSONArray().apply {
                    _playlists.value.forEach { pl ->
                        put(
                            JSONObject().apply {
                                put("id", pl.id)
                                put("name", pl.name)
                                put("description", pl.description)
                                put("items", JSONArray().apply { pl.items.forEach { put(trackToPortableJson(it)) } })
                            }
                        )
                    }
                }
            )
            put(
                "tracklist",
                JSONArray().apply { _tracklist.value.forEach { put(trackToPortableJson(it)) } }
            )
            put(
                "bookmarks",
                JSONArray().apply {
                    _bookmarks.value.forEach { b ->
                        put(
                            JSONObject().apply {
                                put("bookmarkId", b.bookmarkId)
                                put("trackId", b.trackId)
                                put("title", b.title)
                                put("positionSec", b.positionSec)
                                put("note", b.note)
                                put("createdAtMs", b.createdAtMs)
                            }
                        )
                    }
                }
            )
            put(
                "completedIds",
                JSONArray().apply { _completedTrackIds.value.forEach { put(it) } }
            )
            put(
                "dislikedIds",
                JSONArray().apply { _dislikedIds.value.forEach { put(it) } }
            )
            put(
                "playbackSignals",
                JSONArray().apply {
                    _playHistory.value.take(250).forEach { s ->
                        put(
                            JSONObject().apply {
                                put("trackId", s.trackId)
                                put("channelId", s.channelId)
                                put("author", s.author)
                                put("title", s.title)
                                put("durationSec", s.durationSec)
                                put("duration", s.durationSec)
                                put("playedSec", s.playedSec)
                                put("playCount", s.playCount)
                                put("skipCount", s.skipCount)
                                put("lastPlayedAtMs", s.lastPlayedAtMs)
                                put("lastPlayedAt", s.lastPlayedAtMs)
                            }
                        )
                    }
                }
            )
            put(
                "trackPositions",
                JSONObject().apply {
                    synchronized(trackPositionsLock) {
                        trackPositions.forEach { (k, ms) ->
                            val sec = (ms / 1000L).coerceAtLeast(0L)
                            if (sec > 0L) put(k, sec)
                        }
                    }
                }
            )
            put(
                "dailyStats",
                JSONObject().apply { _dailyStats.value.forEach { (k, v) -> put(k, v) } }
            )
            put(
                "playback",
                JSONObject().apply {
                    val cur = _currentTrack.value
                    put("track", if (cur != null) trackToPortableJson(cur) else JSONObject.NULL)
                    put("positionSec", (_positionMs.value / 1000L).coerceAtLeast(0L))
                    put("isTracklistMode", _isTracklistMode.value)
                }
            )
        }
        return root.toString()
    }

    private fun trackToPortableJson(t: AudioTrack): JSONObject = trackToJson(t).apply {
        put("duration", t.durationSec)
        put("fileSize", t.fileSizeBytes)
        put("date", t.dateEpoch)
    }

    private fun portableJsonToTrack(o: JSONObject): AudioTrack {
        val duration = o.optLong("durationSec", -1L).let { if (it >= 0L) it else o.optLong("duration", 0L) }
        val fileSize = o.optLong("fileSizeBytes", -1L).let { if (it >= 0L) it else o.optLong("fileSize", 0L) }
        val date = o.optLong("dateEpoch", -1L).let { if (it >= 0L) it else o.optLong("date", 0L) }
        return AudioTrack(
            trackId = o.optString("trackId"),
            channelId = o.optString("channelId"),
            channelTitle = o.optString("channelTitle"),
            channelUsername = o.optString("channelUsername"),
            messageId = o.optLong("messageId"),
            fileId = o.optInt("fileId"),
            title = o.optString("title"),
            author = o.optString("author"),
            description = o.optString("description"),
            durationSec = duration.coerceAtLeast(0L),
            fileSizeBytes = fileSize.coerceAtLeast(0L),
            mimeType = o.optString("mimeType", "audio/mpeg"),
            coverPath = o.optString("coverPath").takeIf { it.isNotBlank() },
            coverFileId = o.optInt("coverFileId"),
            coverMaxDim = o.optInt("coverMaxDim"),
            dateEpoch = date.coerceAtLeast(0L)
        )
    }

    private fun applyBackupSnapshotJson(jsonText: String, mergeWithLocal: Boolean): Boolean {
        val root = runCatching { JSONObject(jsonText) }.getOrNull() ?: return false

        root.optJSONObject("settings")?.let { s ->
            if (s.has("themeMode")) {
                val mode = ThemeMode.fromWire(s.optString("themeMode"))
                _themeMode.value = mode
                prefs.edit().putString("theme_mode", mode.wireValue).apply()
            }
            if (s.has("progressBarStyle")) {
                val style = ProgressBarStyle.fromWire(s.optString("progressBarStyle"))
                _progressBarStyle.value = style
                prefs.edit().putString("progress_bar_style", style.wireValue).apply()
            }
            if (s.has("playbackSpeed")) {
                val sp = s.optDouble("playbackSpeed", 1.0).toFloat().coerceIn(0.5f, 3.0f)
                _playbackSpeed.value = sp
                prefs.edit().putFloat("playback_speed", sp).apply()
                mediaController?.playbackParameters = PlaybackParameters(sp)
            }
            if (s.has("dspPreset")) {
                val preset = DspPreset.fromWire(s.optString("dspPreset"))
                _dspPreset.value = preset
                prefs.edit().putString("dsp_preset", preset.wireValue).apply()
                AudiobookPlayerService.applyDspPreset(preset)
            }
            if (s.has("skipSilence")) {
                val skip = s.optBoolean("skipSilence", false)
                _skipSilence.value = skip
                prefs.edit().putBoolean("skip_silence", skip).apply()
                AudiobookPlayerService.applySkipSilence(skip)
            }
        }

        root.optJSONArray("selectedChannelIds")?.let { arr ->
            val incoming = buildSet {
                for (i in 0 until arr.length()) {
                    val id = arr.optString(i).trim()
                    if (id.isNotEmpty()) add(id)
                }
            }
            if (incoming.isNotEmpty()) {
                val persisted = prefs.getStringSet("selected_channel_ids", emptySet()).orEmpty()
                val merged = if (mergeWithLocal) persisted + incoming else incoming
                prefs.edit().putStringSet("selected_channel_ids", merged).apply()
                val curChannels = _channels.value
                if (curChannels.isNotEmpty()) {
                    val next = curChannels.map { it.copy(isSelected = it.channelId in merged) }
                    _channels.value = next
                    next.filter { it.isSelected && !_tracksByChannel.value.containsKey(it.channelId) }
                        .forEach { loadChannelAudio(it) }
                }
            }
        }

        root.optJSONArray("favorites")?.let { arr ->
            val parsed = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val t = portableJsonToTrack(o)
                    if (t.trackId.isNotBlank()) add(applyCachedCover(t))
                }
            }
            if (!mergeWithLocal || _favorites.value.isEmpty() || parsed.isNotEmpty()) {
                val finalFavs = if (mergeWithLocal) {
                    val existingIds = _favorites.value.map { it.trackId }.toSet()
                    _favorites.value + parsed.filterNot { it.trackId in existingIds }
                } else parsed
                _favorites.value = finalFavs
                saveTrackList("favorites_json", finalFavs)
            }
        }

        root.optJSONArray("playlists")?.let { arr ->
            val parsed = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id").trim()
                    val name = o.optString("name").trim()
                    if (id.isEmpty() || name.isEmpty()) continue
                    val itemsArr = o.optJSONArray("items") ?: JSONArray()
                    val items = buildList {
                        for (j in 0 until itemsArr.length()) {
                            val itemObj = itemsArr.optJSONObject(j) ?: continue
                            val t = portableJsonToTrack(itemObj)
                            if (t.trackId.isNotBlank()) add(applyCachedCover(t))
                        }
                    }
                    add(UserPlaylist(id = id, name = name, description = o.optString("description"), items = items))
                }
            }
            if (!mergeWithLocal || _playlists.value.isEmpty() || parsed.isNotEmpty()) {
                val finalPls = if (mergeWithLocal) {
                    val existingIds = _playlists.value.map { it.id }.toSet()
                    _playlists.value + parsed.filterNot { it.id in existingIds }
                } else parsed
                _playlists.value = finalPls
                savePlaylists(finalPls)
            }
        }

        root.optJSONArray("tracklist")?.let { arr ->
            val parsed = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val t = portableJsonToTrack(o)
                    if (t.trackId.isNotBlank()) add(applyCachedCover(t))
                }
            }
            if (!mergeWithLocal || _tracklist.value.isEmpty()) {
                _tracklist.value = parsed
                saveTrackList("tracklist_json", parsed)
            }
        }

        root.optJSONArray("bookmarks")?.let { arr ->
            val parsed = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val bid = o.optString("bookmarkId").trim()
                    val tid = o.optString("trackId").trim()
                    if (bid.isEmpty() || tid.isEmpty()) continue
                    add(
                        AudioBookmark(
                            bookmarkId = bid,
                            trackId = tid,
                            title = o.optString("title", "Закладка"),
                            positionSec = o.optLong("positionSec", 0L).coerceAtLeast(0L),
                            note = o.optString("note"),
                            createdAtMs = o.optLong("createdAtMs", System.currentTimeMillis())
                        )
                    )
                }
            }
            if (parsed.isNotEmpty() || !mergeWithLocal) {
                val merged = if (mergeWithLocal) {
                    val existingIds = _bookmarks.value.map { it.bookmarkId }.toSet()
                    (_bookmarks.value + parsed.filterNot { it.bookmarkId in existingIds }).take(200)
                } else parsed.take(200)
                _bookmarks.value = merged
                saveBookmarks(merged)
            }
        }

        root.optJSONArray("completedIds")?.let { arr ->
            val parsed = buildSet {
                for (i in 0 until arr.length()) {
                    val id = arr.optString(i).trim()
                    if (id.isNotEmpty()) add(id)
                }
            }
            if (parsed.isNotEmpty() || !mergeWithLocal) {
                val merged = if (mergeWithLocal) _completedTrackIds.value + parsed else parsed
                _completedTrackIds.value = merged
                saveCompletedIds(merged)
            }
        }

        root.optJSONArray("dislikedIds")?.let { arr ->
            val parsed = buildSet {
                for (i in 0 until arr.length()) {
                    val id = arr.optString(i).trim()
                    if (id.isNotEmpty()) add(id)
                }
            }
            if (parsed.isNotEmpty() || !mergeWithLocal) {
                val merged = if (mergeWithLocal) _dislikedIds.value + parsed else parsed
                _dislikedIds.value = merged
                saveDislikedIds(merged)
            }
        }

        root.optJSONArray("playbackSignals")?.let { arr ->
            val parsed = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val tid = o.optString("trackId").trim()
                    if (tid.isEmpty()) continue
                    add(
                        PlaybackSignal(
                            trackId = tid,
                            channelId = o.optString("channelId"),
                            author = o.optString("author"),
                            title = o.optString("title"),
                            durationSec = (
                                if (o.has("durationSec")) o.optLong("durationSec", 0L)
                                else o.optLong("duration", 0L)
                            ).coerceAtLeast(0L),
                            playedSec = o.optLong("playedSec", 0L).coerceAtLeast(0L),
                            playCount = o.optInt("playCount", 1).coerceAtLeast(1),
                            skipCount = o.optInt("skipCount", 0).coerceAtLeast(0),
                            lastPlayedAtMs = (
                                if (o.has("lastPlayedAtMs")) o.optLong("lastPlayedAtMs", 0L)
                                else o.optLong("lastPlayedAt", 0L)
                            ).coerceAtLeast(0L)
                        )
                    )
                }
            }
            if (parsed.isNotEmpty() || !mergeWithLocal) {
                val merged = if (mergeWithLocal) {
                    val byId = LinkedHashMap<String, PlaybackSignal>()
                    _playHistory.value.forEach { byId[it.trackId] = it }
                    parsed.forEach { inc ->
                        val ex = byId[inc.trackId]
                        byId[inc.trackId] = if (ex == null) inc else ex.copy(
                            playedSec = maxOf(ex.playedSec, inc.playedSec),
                            playCount = maxOf(ex.playCount, inc.playCount),
                            skipCount = maxOf(ex.skipCount, inc.skipCount),
                            lastPlayedAtMs = maxOf(ex.lastPlayedAtMs, inc.lastPlayedAtMs)
                        )
                    }
                    byId.values.sortedByDescending { it.lastPlayedAtMs }.take(250)
                } else {
                    parsed.sortedByDescending { it.lastPlayedAtMs }.take(250)
                }
                _playHistory.value = merged
                savePlaybackSignals(merged)
            }
        }

        root.optJSONObject("trackPositions")?.let { obj ->
            synchronized(trackPositionsLock) {
                if (!mergeWithLocal) trackPositions.clear()
                obj.keys().forEach { k ->
                    val sec = obj.optLong(k, 0L)
                    if (k.isNotBlank() && sec > 0L) {
                        val ms = sec * 1000L
                        val cur = trackPositions[k] ?: 0L
                        trackPositions[k] = if (mergeWithLocal) maxOf(cur, ms) else ms
                    }
                }
                while (trackPositions.size > 400) {
                    val eldest = trackPositions.keys.firstOrNull() ?: break
                    trackPositions.remove(eldest)
                }
            }
            persistScope.launch {
                prefs.edit().putString(PREF_TRACK_POSITIONS_JSON, serializeTrackPositionsLocked()).apply()
            }
        }

        root.optJSONObject("dailyStats")?.let { obj ->
            val merged = if (mergeWithLocal) _dailyStats.value.toMutableMap() else mutableMapOf()
            obj.keys().forEach { k ->
                val v = obj.optLong(k, 0L).coerceAtLeast(0L)
                merged[k] = maxOf(merged[k] ?: 0L, v)
            }
            _dailyStats.value = merged
            saveDailyStats(merged)
        }

        root.optJSONObject("playback")?.let { pb ->
            val trackObj = pb.optJSONObject("track")
            if (trackObj != null && (_currentTrack.value == null || !mergeWithLocal)) {
                val parsedTrack = portableJsonToTrack(trackObj)
                if (parsedTrack.trackId.isNotBlank()) {
                    val liveMatch = allSourceTracks().firstOrNull { it.trackId == parsedTrack.trackId } ?: parsedTrack
                    val posSec = pb.optLong("positionSec", 0L).coerceAtLeast(0L)
                    _currentTrack.value = applyCachedCover(liveMatch)
                    _positionMs.value = posSec * 1000L
                    if (liveMatch.durationSec > 0L) _durationMs.value = liveMatch.durationSec * 1000L
                    val tlMode = pb.optBoolean("isTracklistMode", false)
                    if (tlMode && _tracklist.value.isNotEmpty()) {
                        _isTracklistMode.value = true
                        prefs.edit().putBoolean("tracklist_mode", true).apply()
                    }
                }
            }
        }

        return true
    }

    fun syncCloudPush(silent: Boolean = false) {
        viewModelScope.launch {
            if (_cloudSyncState.value.isBusy) return@launch
            _cloudSyncState.value = _cloudSyncState.value.copy(isBusy = true, error = null)
            val json = withContext(Dispatchers.Default) { buildBackupSnapshotJson() }
            val result = telegramRepo.pushCloudSyncSnapshot(json)
            result.onSuccess { syncedAt ->
                prefs.edit().putLong(PREF_TG_CLOUD_LAST_SYNC_MS, syncedAt).apply()
                _cloudSyncState.value = _cloudSyncState.value.copy(
                    isBusy = false,
                    lastSyncedAtMs = syncedAt,
                    statusMessage = "Сохранено в «Избранное» Telegram",
                    error = null
                )
            }.onFailure { err ->
                _cloudSyncState.value = _cloudSyncState.value.copy(
                    isBusy = false,
                    error = if (silent) null else (err.message ?: "Не удалось сохранить резервную копию в Telegram")
                )
            }
        }
    }

    fun syncCloudPull(silent: Boolean = false) {
        viewModelScope.launch {
            if (_cloudSyncState.value.isBusy) return@launch
            _cloudSyncState.value = _cloudSyncState.value.copy(isBusy = true, error = null)
            val result = telegramRepo.pullCloudSyncSnapshot()
            result.onSuccess { pair ->
                if (pair == null) {
                    _cloudSyncState.value = _cloudSyncState.value.copy(
                        isBusy = false,
                        statusMessage = if (silent) _cloudSyncState.value.statusMessage else "В «Избранном» Telegram пока нет копии",
                        error = null
                    )
                } else {
                    val (jsonText, msgTimeMs) = pair
                    val ok = applyBackupSnapshotJson(jsonText, mergeWithLocal = silent)
                    val updatedAt = runCatching { JSONObject(jsonText).optLong("updatedAt", msgTimeMs) }.getOrDefault(msgTimeMs)
                    if (ok) {
                        prefs.edit().putLong(PREF_TG_CLOUD_LAST_SYNC_MS, updatedAt).apply()
                        _cloudSyncState.value = _cloudSyncState.value.copy(
                            isBusy = false,
                            lastSyncedAtMs = updatedAt,
                            statusMessage = "Загружено из «Избранного» Telegram",
                            error = null
                        )
                    } else {
                        _cloudSyncState.value = _cloudSyncState.value.copy(
                            isBusy = false,
                            error = if (silent) null else "Повреждённый файл синхронизации в Telegram"
                        )
                    }
                }
            }.onFailure { err ->
                _cloudSyncState.value = _cloudSyncState.value.copy(
                    isBusy = false,
                    error = if (silent) null else (err.message ?: "Не удалось загрузить копию из Telegram")
                )
            }
        }
    }

    fun importBackupJson(jsonText: String) {
        val ok = applyBackupSnapshotJson(jsonText, mergeWithLocal = false)
        _cloudSyncState.value = if (ok) {
            _cloudSyncState.value.copy(
                statusMessage = "Импортировано из локального JSON",
                error = null
            )
        } else {
            _cloudSyncState.value.copy(
                error = "Некорректный JSON-файл резервной копии"
            )
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString("theme_mode", mode.wireValue).apply()
        scheduleAutoCloudPush()
    }

    fun setProgressBarStyle(style: ProgressBarStyle) {
        _progressBarStyle.value = style
        prefs.edit().putString("progress_bar_style", style.wireValue).apply()
        scheduleAutoCloudPush()
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
    }

    fun toggleRepeat() {
        _repeatMode.value = !_repeatMode.value
    }

    fun refreshChannels() {
        viewModelScope.launch {
            _isLoadingChannels.value = true
            try {
                val loaded = telegramRepo.getSubscribedChannels()
                val persistedIds = prefs.getStringSet("selected_channel_ids", emptySet()).orEmpty()
                val memoryIds = _channels.value.filter { it.isSelected }.map { it.channelId }.toSet()
                val selectedIds = persistedIds + memoryIds

                val updated = loaded.map { it.copy(isSelected = it.channelId in selectedIds) }
                _channels.value = updated

                updated.filter { it.isSelected }.forEach { ch ->
                    if (!_tracksByChannel.value.containsKey(ch.channelId)) {
                        loadChannelAudio(ch)
                    }
                }
            } finally {
                _isLoadingChannels.value = false
            }
        }
    }

    fun toggleChannelSource(channel: TelegramChannel) {
        val next = _channels.value.map {
            if (it.channelId == channel.channelId) it.copy(isSelected = !it.isSelected) else it
        }
        _channels.value = next
        val selectedIds = next.filter { it.isSelected }.map { it.channelId }.toSet()
        prefs.edit().putStringSet("selected_channel_ids", selectedIds).apply()

        val updated = next.firstOrNull { it.channelId == channel.channelId } ?: return
        scheduleAutoCloudPush()
        if (updated.isSelected) {
            loadChannelAudio(updated)
        } else {
            trackLoadJobs.remove(updated.channelId)?.cancel()
            loadingChannelIds.remove(updated.channelId)
            _isLoadingTracks.value = loadingChannelIds.isNotEmpty()
            _tracksByChannel.value = _tracksByChannel.value.toMutableMap().apply {
                remove(updated.channelId)
            }
        }
    }

    fun loadChannelAudio(channel: TelegramChannel) {
        trackLoadJobs.remove(channel.channelId)?.cancel()
        loadingChannelIds.add(channel.channelId)
        _isLoadingTracks.value = true
        lateinit var launchedJob: Job
        launchedJob = viewModelScope.launch {
            try {
                if (_tracksByChannel.value[channel.channelId].isNullOrEmpty()) {
                    val cached = telegramRepo.loadCachedChannelTracks(channel.channelId)
                    if (cached.isNotEmpty()) {
                        val enrichedCached = withContext(Dispatchers.Default) {
                            cached.map { applyCachedCover(it) }
                        }
                        _tracksByChannel.value = _tracksByChannel.value.toMutableMap().apply {
                            put(channel.channelId, enrichedCached)
                        }
                        reconcileActiveTrackFileId(enrichedCached)
                        prefetchInitialCovers(enrichedCached.take(18))
                    }
                }

                val finalTracks = telegramRepo.streamChannelAudioTracks(
                    channelId = channel.channelId,
                    channelTitle = channel.title,
                    channelUsername = channel.username
                ) { batch ->
                    val enriched = withContext(Dispatchers.Default) {
                        batch.map { applyCachedCover(it) }
                    }
                    _tracksByChannel.value = _tracksByChannel.value.toMutableMap().apply {
                        put(channel.channelId, enriched)
                    }
                    prefetchInitialCovers(enriched.take(20))
                }

                val enrichedFinal = withContext(Dispatchers.Default) {
                    finalTracks.map { applyCachedCover(it) }
                }
                _tracksByChannel.value = _tracksByChannel.value.toMutableMap().apply {
                    put(channel.channelId, enrichedFinal)
                }
                reconcileActiveTrackFileId(enrichedFinal)
                prefetchInitialCovers(enrichedFinal.take(28))
                launch {
                    telegramRepo.saveCachedChannelTracks(channel.channelId, enrichedFinal)
                }
            } finally {
                if (trackLoadJobs[channel.channelId] === launchedJob) {
                    trackLoadJobs.remove(channel.channelId)
                    loadingChannelIds.remove(channel.channelId)
                    _isLoadingTracks.value = loadingChannelIds.isNotEmpty()
                }
            }
        }
        trackLoadJobs[channel.channelId] = launchedJob
    }

    fun shuffleRecommendations() {
        val nextSeed = _recommendationSeed.value + 1
        _recommendationSeed.value = nextSeed
        prefs.edit().putInt(PREF_RECOMMENDATION_SEED, nextSeed).apply()
        viewModelScope.launch {
            syncMixesAndSeries(forceRebuild = true)
        }
    }

    fun refreshTracks() {
        val selected = _channels.value.filter { it.isSelected }
        if (selected.isEmpty()) {
            refreshChannels()
            return
        }
        refreshTracksJob?.cancel()
        lateinit var currentRefreshJob: Job
        currentRefreshJob = viewModelScope.launch {
            _isLoadingChannels.value = true
            try {
                val jobs = selected.map { ch ->
                    loadChannelAudio(ch)
                    trackLoadJobs[ch.channelId]
                }
                jobs.forEach { it?.join() }
            } finally {
                if (refreshTracksJob === currentRefreshJob) {
                    _isLoadingChannels.value = false
                }
            }
        }
        refreshTracksJob = currentRefreshJob
    }

    private fun reconcileActiveTrackFileId(loadedTracks: List<AudioTrack>) {
        val cur = _currentTrack.value ?: return
        val match = loadedTracks.firstOrNull { it.trackId == cur.trackId } ?: return
        if (cur.fileId == 0 || cur.coverPath.isNullOrBlank()) {
            _currentTrack.value = cur.copy(
                fileId = if (cur.fileId > 0) cur.fileId else match.fileId,
                fileSizeBytes = if (cur.fileSizeBytes > 0L) cur.fileSizeBytes else match.fileSizeBytes,
                coverPath = cur.coverPath ?: match.coverPath,
                coverFileId = if (cur.coverFileId > 0) cur.coverFileId else match.coverFileId,
                coverMaxDim = if (cur.coverMaxDim > 0) cur.coverMaxDim else match.coverMaxDim
            )
        }
    }

    private fun prefetchInitialCovers(tracks: List<AudioTrack>) {
        tracks.forEach { t ->
            if (t.coverPath.isNullOrBlank()) {
                requestTrackCover(t, priority = false)
            }
        }
    }

    fun requestTrackCover(track: AudioTrack, priority: Boolean = false) {
        val known = resolvedCovers[track.trackId] ?: telegramRepo.getCachedCoverPathIfAny(track.trackId)
        if (!known.isNullOrBlank()) {
            resolvedCovers[track.trackId] = known
            if (track.coverPath != known) {
                enqueueCoverUpdate(track.trackId, known, immediate = priority)
            }
            return
        }
        if (!inFlightCoverIds.add(track.trackId)) return

        viewModelScope.launch {
            try {
                val resolved = if (priority) {
                    telegramRepo.resolveTrackHdCover(track, priority = true)
                } else {
                    coverSemaphore.withPermit {
                        telegramRepo.resolveTrackHdCover(track, priority = false)
                    }
                }
                if (!resolved.isNullOrBlank()) {
                    resolvedCovers[track.trackId] = resolved
                    enqueueCoverUpdate(track.trackId, resolved, immediate = priority)
                }
            } finally {
                inFlightCoverIds.remove(track.trackId)
            }
        }
    }

    private fun applyCachedCover(track: AudioTrack): AudioTrack {
        val cached = resolvedCovers[track.trackId] ?: telegramRepo.getCachedCoverPathIfAny(track.trackId)
        return if (!cached.isNullOrBlank()) {
            resolvedCovers[track.trackId] = cached
            if (track.coverPath == cached) track else track.copy(coverPath = cached)
        } else {
            track
        }
    }

    private fun enqueueCoverUpdate(trackId: String, coverPath: String, immediate: Boolean) {
        pendingCoverUpdates[trackId] = coverPath
        if (immediate && _currentTrack.value?.trackId == trackId) {
            _currentTrack.value = _currentTrack.value?.copy(coverPath = coverPath)?.also { updated ->
                updateWidgetAsync(updated, _isPlaying.value)
            }
        }
        val flushSoon = immediate || pendingCoverUpdates.size >= 8
        if (coverFlushJob?.isActive == true && !flushSoon) return
        if (flushSoon) {
            coverFlushJob?.cancel()
            coverFlushJob = viewModelScope.launch(Dispatchers.Default) {
                flushPendingCoverUpdates()
            }
        } else {
            coverFlushJob = viewModelScope.launch(Dispatchers.Default) {
                delay(90L)
                flushPendingCoverUpdates()
            }
        }
    }

    private fun flushPendingCoverUpdates() {
        if (pendingCoverUpdates.isEmpty()) return
        val batch = HashMap<String, String>(pendingCoverUpdates.size)
        val iterator = pendingCoverUpdates.entries.iterator()
        while (iterator.hasNext()) {
            val e = iterator.next()
            batch[e.key] = e.value
            iterator.remove()
        }
        if (batch.isEmpty()) return

        _currentTrack.value?.let { cur ->
            val newCover = batch[cur.trackId]
            if (newCover != null && cur.coverPath != newCover) {
                val updated = cur.copy(coverPath = newCover)
                _currentTrack.value = updated
                updateWidgetAsync(updated, _isPlaying.value)
            }
        }

        val queueNow = _queue.value
        if (queueNow.any { batch.containsKey(it.trackId) && batch[it.trackId] != it.coverPath }) {
            _queue.value = queueNow.map { t ->
                val c = batch[t.trackId]
                if (c != null && t.coverPath != c) t.copy(coverPath = c) else t
            }
        }

        val tracklistNow = _tracklist.value
        if (tracklistNow.any { batch.containsKey(it.trackId) && batch[it.trackId] != it.coverPath }) {
            _tracklist.value = tracklistNow.map { t ->
                val c = batch[t.trackId]
                if (c != null && t.coverPath != c) t.copy(coverPath = c) else t
            }
        }

        val favsNow = _favorites.value
        if (favsNow.any { batch.containsKey(it.trackId) && batch[it.trackId] != it.coverPath }) {
            _favorites.value = favsNow.map { t ->
                val c = batch[t.trackId]
                if (c != null && t.coverPath != c) t.copy(coverPath = c) else t
            }
        }

        val plsNow = _playlists.value
        var plsChanged = false
        val updatedPls = plsNow.map { pl ->
            if (pl.items.any { batch.containsKey(it.trackId) && batch[it.trackId] != it.coverPath }) {
                plsChanged = true
                pl.copy(items = pl.items.map { t ->
                    val c = batch[t.trackId]
                    if (c != null && t.coverPath != c) t.copy(coverPath = c) else t
                })
            } else pl
        }
        if (plsChanged) {
            _playlists.value = updatedPls
        }

        val mapNow = _tracksByChannel.value
        var mapChanged = false
        val updatedMap = HashMap<String, List<AudioTrack>>(mapNow.size)
        for ((cid, list) in mapNow) {
            if (list.any { batch.containsKey(it.trackId) && batch[it.trackId] != it.coverPath }) {
                mapChanged = true
                updatedMap[cid] = list.map { t ->
                    val c = batch[t.trackId]
                    if (c != null && t.coverPath != c) t.copy(coverPath = c) else t
                }
            } else {
                updatedMap[cid] = list
            }
        }
        if (mapChanged) {
            _tracksByChannel.value = updatedMap
        }

        val mixesNow = _mixes.value
        if (mixesNow.any { m -> m.tracks.any { batch.containsKey(it.trackId) && batch[it.trackId] != it.coverPath } }) {
            _mixes.value = mixesNow.map { m ->
                if (m.tracks.any { batch.containsKey(it.trackId) && batch[it.trackId] != it.coverPath }) {
                    val nextTracks = m.tracks.map { t ->
                        val c = batch[t.trackId]
                        if (c != null && t.coverPath != c) t.copy(coverPath = c) else t
                    }
                    m.copy(
                        coverPath = RecommendationEngine.pickMixCover(nextTracks) ?: m.coverPath,
                        tracks = nextTracks
                    )
                } else m
            }
        }

        val autoNow = _autoPlaylists.value
        if (autoNow.any { m -> m.tracks.any { batch.containsKey(it.trackId) && batch[it.trackId] != it.coverPath } }) {
            _autoPlaylists.value = autoNow.map { m ->
                if (m.tracks.any { batch.containsKey(it.trackId) && batch[it.trackId] != it.coverPath }) {
                    val nextTracks = m.tracks.map { t ->
                        val c = batch[t.trackId]
                        if (c != null && t.coverPath != c) t.copy(coverPath = c) else t
                    }
                    m.copy(
                        coverPath = RecommendationEngine.pickMixCover(nextTracks) ?: m.coverPath,
                        tracks = nextTracks
                    )
                } else m
            }
        }

        val seriesNow = _seriesGroups.value
        if (seriesNow.any { s -> s.tracks.any { batch.containsKey(it.trackId) && batch[it.trackId] != it.coverPath } }) {
            _seriesGroups.value = seriesNow.map { s ->
                if (s.tracks.any { batch.containsKey(it.trackId) && batch[it.trackId] != it.coverPath }) {
                    val nextTracks = s.tracks.map { t ->
                        val c = batch[t.trackId]
                        if (c != null && t.coverPath != c) t.copy(coverPath = c) else t
                    }
                    s.copy(
                        coverPath = RecommendationEngine.pickMixCover(nextTracks) ?: s.coverPath,
                        tracks = nextTracks
                    )
                } else s
            }
        }

        val catsNow = _categories.value
        if (catsNow.any { c -> c.tracks.any { batch.containsKey(it.trackId) && batch[it.trackId] != it.coverPath } }) {
            _categories.value = catsNow.map { c ->
                if (c.tracks.any { batch.containsKey(it.trackId) && batch[it.trackId] != it.coverPath }) {
                    val nextTracks = c.tracks.map { t ->
                        val cov = batch[t.trackId]
                        if (cov != null && t.coverPath != cov) t.copy(coverPath = cov) else t
                    }
                    c.copy(
                        coverPath = RecommendationEngine.pickMixCover(nextTracks) ?: c.coverPath,
                        tracks = nextTracks
                    )
                } else c
            }
        }
    }

    fun allSourceTracks(): List<AudioTrack> =
        allTracks.value.ifEmpty { _tracksByChannel.value.values.flatten().sortedByDescending { it.dateEpoch } }

    fun saveAutoPlaylistToUser(collection: MixCollection) {
        val tracks = collection.tracks.take(60)
        if (tracks.isEmpty()) return
        val newPl = UserPlaylist(
            id = "pl_${System.currentTimeMillis()}",
            name = collection.title,
            description = collection.description.ifBlank { collection.subtitle },
            items = tracks
        )
        val updated = _playlists.value + newPl
        _playlists.value = updated
        savePlaylists(updated)
        scheduleAutoCloudPush()
    }

    fun playShuffled(tracks: List<AudioTrack>) {
        if (tracks.isEmpty()) return
        val shuffled = tracks.shuffled()
        _isShuffle.value = true
        playTrack(shuffled.first(), shuffled)
    }

    fun playTrack(
        track: AudioTrack,
        contextQueue: List<AudioTrack>? = null,
        preserveFileId: Boolean = false,
        pushHistory: Boolean = true,
        forcedLocalFile: File? = null
    ) {
        if (!contextQueue.isNullOrEmpty()) {
            _queue.value = contextQueue
            shuffleDeck.clear()
        }
        playJob?.cancel()
        prefetchJob?.cancel()

        val mcNow = mediaController
        if (mcNow != null &&
            mcNow.mediaItemCount > 0 &&
            mcNow.currentMediaItem?.mediaId == track.trackId &&
            mcNow.playbackState != Player.STATE_IDLE &&
            mcNow.playerError == null &&
            !preserveFileId &&
            forcedLocalFile == null
        ) {
            if (mcNow.playbackState == Player.STATE_ENDED) {
                _positionMs.value = 0L
                mcNow.seekTo(0L)
                lastPausedAtMs = 0L
                trackStartedAtMs = System.currentTimeMillis()
                mcNow.play()
                recordPlaybackSignal(track, 0L, countPlay = true)
            } else if (!mcNow.isPlaying) {
                applySmartRewindIfNeeded(mcNow)
                lastPausedAtMs = 0L
                mcNow.play()
            }
            return
        }

        _currentTrack.value?.let { prev ->
            if (prev.trackId != track.trackId) {
                val prevPos = _positionMs.value
                val prevDur = maxOf(_durationMs.value, prev.durationSec * 1000L)
                recordTrackPositionInMemory(prev.trackId, prevPos, prevDur)
                if (pushHistory) {
                    if (playHistoryStack.lastOrNull() != prev.trackId) {
                        playHistoryStack.addLast(prev.trackId)
                        while (playHistoryStack.size > 50) playHistoryStack.removeFirst()
                    }
                }
                val elapsedSinceStart = System.currentTimeMillis() - trackStartedAtMs
                val ratio = if (prevDur > 0L) prevPos.toDouble() / prevDur.toDouble() else 0.0
                if (trackStartedAtMs > 0L &&
                    elapsedSinceStart >= 1_500L &&
                    prevDur >= 180_000L &&
                    prevPos < 45_000L &&
                    ratio < 0.08 &&
                    prev.trackId !in _completedTrackIds.value
                ) {
                    recordPlaybackSignal(prev, prevPos / 1000L, countPlay = false, countSkip = true)
                }
            }
        }
        trackStartedAtMs = System.currentTimeMillis()

        val liveMatch = allSourceTracks().firstOrNull { it.trackId == track.trackId }
        val baseTrack = if (liveMatch != null) {
            liveMatch.copy(
                fileId = if (preserveFileId && track.fileId > 0) track.fileId else if (liveMatch.fileId > 0) liveMatch.fileId else track.fileId,
                fileSizeBytes = maxOf(track.fileSizeBytes, liveMatch.fileSizeBytes),
                coverPath = track.coverPath ?: liveMatch.coverPath,
                coverFileId = if (liveMatch.coverFileId > 0) liveMatch.coverFileId else track.coverFileId,
                coverMaxDim = maxOf(track.coverMaxDim, liveMatch.coverMaxDim)
            )
        } else {
            track
        }
        val resolvedTrack = applyCachedCover(baseTrack)
        val savedPerTrackMs = synchronized(trackPositionsLock) {
            trackPositions[resolvedTrack.trackId] ?: 0L
        }
        val knownDurationMs = maxOf(
            if (_currentTrack.value?.trackId == resolvedTrack.trackId) _durationMs.value else 0L,
            resolvedTrack.durationSec * 1000L
        )
        val rawResumePos = when {
            _currentTrack.value?.trackId == resolvedTrack.trackId &&
                ((mcNow?.mediaItemCount ?: 0) == 0 ||
                    mcNow?.playbackState == Player.STATE_IDLE ||
                    mcNow?.playerError != null) &&
                _positionMs.value > 0L -> _positionMs.value
            savedPerTrackMs > 0L -> savedPerTrackMs
            else -> 0L
        }
        val sameTrackResumePos = if (knownDurationMs > 10_000L && rawResumePos >= knownDurationMs - 5_000L) {
            0L
        } else {
            rawResumePos.coerceAtLeast(0L)
        }

        val prevFileId = telegramRepo.activePlaybackFileId
        if (prevFileId > 0 && prevFileId != resolvedTrack.fileId) {
            telegramRepo.cancelFileDownload(prevFileId)
        }
        telegramRepo.activePlaybackFileId = resolvedTrack.fileId

        if ((!preserveFileId && forcedLocalFile == null) || retriedErrorTrackId != resolvedTrack.trackId) {
            retriedErrorTrackId = null
        }
        telegramRepo.lastStreamError = null
        _playbackError.value = null
        _currentTrack.value = resolvedTrack
        _durationMs.value = resolvedTrack.durationSec * 1000L
        _positionMs.value = sameTrackResumePos
        lastPausedAtMs = 0L

        // Always load crisp HD cover immediately in background
        requestTrackCover(resolvedTrack, priority = true)
        updateWidgetAsync(resolvedTrack, isPlaying = true)

        playJob = viewModelScope.launch {
            val localFileReady = forcedLocalFile?.takeIf { it.exists() && it.length() > 0L }
                ?: telegramRepo.getOfflineAudioFile(resolvedTrack.trackId)
            var playableTrack = resolvedTrack
            if (localFileReady == null && playableTrack.fileId <= 0) {
                _isBufferingTrack.value = true
                val chatIdLong = playableTrack.channelId.toLongOrNull() ?: 0L
                val fresh = if (chatIdLong != 0L && playableTrack.messageId > 0L) {
                    telegramRepo.refreshTrackFileFromMessage(chatIdLong, playableTrack.messageId)
                } else {
                    null
                }
                if (fresh != null && fresh.id > 0) {
                    val refreshedSize = when {
                        fresh.size > 0L -> fresh.size
                        playableTrack.fileSizeBytes > 0L -> playableTrack.fileSizeBytes
                        else -> fresh.expectedSize
                    }
                    playableTrack = playableTrack.copy(
                        fileId = fresh.id,
                        fileSizeBytes = refreshedSize
                    )
                    telegramRepo.activePlaybackFileId = fresh.id
                    if (_currentTrack.value?.trackId == playableTrack.trackId) {
                        _currentTrack.value = playableTrack
                    }
                } else {
                    _isBufferingTrack.value = false
                    _playbackError.value =
                        "Не удалось загрузить аудио из канала «${playableTrack.channelTitle}». Проверьте подключение к Telegram."
                    return@launch
                }
            }
            _isBufferingTrack.value = true
            val streamUri = if (localFileReady != null) {
                Uri.fromFile(localFileReady).toString()
            } else {
                "tdlib://${playableTrack.fileId}?size=${playableTrack.fileSizeBytes}&trackId=${Uri.encode(playableTrack.trackId)}&chatId=${Uri.encode(playableTrack.channelId)}&msgId=${playableTrack.messageId}&mime=${Uri.encode(playableTrack.mimeType)}"
            }
            val item = AudiobookPlayerService.buildMediaItem(playableTrack, streamUri)
            val controller = mediaController
            if (controller != null) {
                controller.apply {
                    setMediaItem(item)
                    playbackParameters = PlaybackParameters(_playbackSpeed.value)
                    prepare()
                    if (sameTrackResumePos > 0L) {
                        seekTo(sameTrackResumePos)
                    }
                    play()
                }
                recordPlaybackSignal(playableTrack, sameTrackResumePos / 1000L, countPlay = true)
                triggerNextTrackPrefetch(delayMs = 2_500L)
            } else {
                pendingPlayAction = { playTrack(playableTrack, contextQueue, preserveFileId, pushHistory, forcedLocalFile) }
            }
            persistPlaybackSnapshot(forceCloud = true, forceLocal = true)
        }
    }

    private fun applySmartRewindIfNeeded(mc: MediaController) {
        val pausedAt = lastPausedAtMs
        if (_smartRewindEnabled.value && pausedAt > 0L) {
            val pausedForMs = System.currentTimeMillis() - pausedAt
            if (pausedForMs >= 600_000L) {
                val curPos = mc.currentPosition.coerceAtLeast(0L)
                if (curPos > 8_000L) {
                    val rewound = (curPos - 5_000L).coerceAtLeast(0L)
                    _positionMs.value = rewound
                    mc.seekTo(rewound)
                }
            }
        }
    }

    private fun triggerNextTrackPrefetch(delayMs: Long) {
        val cur = _currentTrack.value ?: return
        val list = if (_isTracklistMode.value && _tracklist.value.isNotEmpty()) {
            _tracklist.value
        } else {
            _queue.value
        }
        if (list.size < 2 || _repeatMode.value || _isShuffle.value) return
        val idx = list.indexOfFirst { it.trackId == cur.trackId }
        val next = list[(idx + 1).coerceAtLeast(0) % list.size]
        if (next.trackId == cur.trackId || next.fileId <= 0) return
        val pairKey = "${cur.trackId}->${next.trackId}"
        if (lastPrefetchedPair == pairKey && prefetchJob?.isActive == true) return
        if (lastPrefetchedPair == pairKey && delayMs == 0L) return
        lastPrefetchedPair = pairKey
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch(Dispatchers.IO) {
            if (delayMs > 0L) delay(delayMs)
            if (_currentTrack.value?.trackId != cur.trackId) return@launch
            if (telegramRepo.getOfflineAudioFile(next.trackId) != null) return@launch
            telegramRepo.prefetchAudioPrefix(
                fileId = next.fileId,
                chatId = next.channelId.toLongOrNull() ?: 0L,
                messageId = next.messageId
            )
        }
    }

    fun togglePlayPause() {
        val mc = mediaController
        val cur = _currentTrack.value
        if (mc == null) {
            if (cur != null) {
                pendingPlayAction = { togglePlayPause() }
            }
            return
        }
        if ((mc.mediaItemCount == 0 || mc.playbackState == Player.STATE_IDLE || mc.playerError != null) && cur != null) {
            retriedErrorTrackId = null
            playTrack(cur, _queue.value.ifEmpty { allSourceTracks() })
            return
        }
        if (mc.playbackState == Player.STATE_ENDED) {
            _positionMs.value = 0L
            mc.seekTo(0L)
            lastPausedAtMs = 0L
            mc.play()
        } else if (mc.isPlaying) {
            mc.pause()
        } else {
            applySmartRewindIfNeeded(mc)
            lastPausedAtMs = 0L
            mc.play()
        }
    }

    fun seekTo(ms: Long) {
        val target = ms.coerceAtLeast(0L)
        _positionMs.value = target
        mediaController?.seekTo(target)
    }

    fun skipBySeconds(deltaSec: Int) {
        val mc = mediaController ?: return
        val target = (mc.currentPosition + deltaSec * 1000L).coerceAtLeast(0L)
        _positionMs.value = target
        mc.seekTo(target)
    }

    fun playNext(isAutoAdvance: Boolean = false) {
        val list = if (_isTracklistMode.value && _tracklist.value.isNotEmpty()) {
            _tracklist.value
        } else {
            _queue.value.ifEmpty {
                allSourceTracks().also { if (it.isNotEmpty()) _queue.value = it }
            }
        }
        val cur = _currentTrack.value ?: return
        if (list.isEmpty()) return
        if (_repeatMode.value) {
            val mc = mediaController
            if (mc != null && mc.mediaItemCount > 0) {
                seekTo(0L)
                mc.play()
            } else {
                playTrack(cur, list)
            }
            return
        }
        val next = if (_isShuffle.value && list.size > 1) {
            val byId = list.associateBy { it.trackId }
            while (shuffleDeck.isNotEmpty() && (shuffleDeck.first() !in byId || shuffleDeck.first() == cur.trackId)) {
                shuffleDeck.removeFirst()
            }
            if (shuffleDeck.isEmpty()) {
                val freshIds = list.map { it.trackId }.filter { it != cur.trackId }.shuffled()
                shuffleDeck.addAll(freshIds)
            }
            val nextId = shuffleDeck.removeFirstOrNull()
            (nextId?.let { byId[it] }) ?: list.first { it.trackId != cur.trackId }
        } else {
            val idx = list.indexOfFirst { it.trackId == cur.trackId }
            if (isAutoAdvance && idx >= list.lastIndex) {
                mediaController?.pause()
                return
            }
            list[(idx + 1).coerceAtLeast(0) % list.size]
        }
        playTrack(next)
    }

    fun playPrevious() {
        val list = if (_isTracklistMode.value && _tracklist.value.isNotEmpty()) {
            _tracklist.value
        } else {
            _queue.value.ifEmpty {
                allSourceTracks().also { if (it.isNotEmpty()) _queue.value = it }
            }
        }
        val cur = _currentTrack.value ?: return
        if (list.isEmpty()) return
        if (_positionMs.value > 4_000L) {
            seekTo(0L)
            return
        }
        val byId = list.associateBy { it.trackId }
        while (playHistoryStack.isNotEmpty()) {
            val prevId = playHistoryStack.removeLast()
            val match = byId[prevId] ?: allSourceTracks().firstOrNull { it.trackId == prevId }
            if (match != null && match.trackId != cur.trackId) {
                playTrack(match, pushHistory = false)
                return
            }
        }
        val idx = list.indexOfFirst { it.trackId == cur.trackId }
        val prev = list[if (idx <= 0) list.lastIndex else idx - 1]
        playTrack(prev, pushHistory = false)
    }

    fun toggleDislikeTrack(track: AudioTrack) {
        val already = track.trackId in _dislikedIds.value
        val updated = if (already) _dislikedIds.value - track.trackId else _dislikedIds.value + track.trackId
        _dislikedIds.value = updated
        saveDislikedIds(updated)
        scheduleAutoCloudPush()
        if (!already) {
            _uiMessage.value = "Скрыто из рекомендаций: ${track.title.take(36)}"
            if (_currentTrack.value?.trackId == track.trackId) {
                playNext()
            }
        } else {
            _uiMessage.value = "Возвращено в рекомендации"
        }
    }

    fun dismissUiMessage() {
        _uiMessage.value = null
    }

    fun openTelegramChat(
        channelId: String,
        title: String,
        username: String = "",
        sourceType: SourceType = SourceType.CHANNEL,
        focusMessageId: Long = 0L
    ) {
        val matched = _channels.value.firstOrNull { it.channelId == channelId }
        val resolvedTarget = TelegramChatTarget(
            channelId = channelId,
            title = title.ifBlank { matched?.title ?: "Telegram" },
            username = username.ifBlank { matched?.username.orEmpty() },
            sourceType = matched?.sourceType ?: sourceType,
            focusMessageId = focusMessageId
        )
        _chatUiState.value = TelegramChatUiState(
            target = resolvedTarget,
            messages = emptyList(),
            isLoading = true,
            hasMoreOlder = true,
            error = null
        )
        loadChatPageInternal(resolvedTarget, fromMessageId = 0L, appendOlder = false)
    }

    fun closeTelegramChat() {
        val prevChannelId = _chatUiState.value.target?.channelId
        chatLoadJob?.cancel()
        chatLoadJob = null
        _chatUiState.value = TelegramChatUiState()
        if (!prevChannelId.isNullOrBlank()) {
            telegramRepo.closeChat(prevChannelId)
        }
    }

    fun refreshTelegramChat() {
        val target = _chatUiState.value.target ?: return
        _chatUiState.value = _chatUiState.value.copy(isLoading = true, error = null)
        loadChatPageInternal(target.copy(focusMessageId = 0L), fromMessageId = 0L, appendOlder = false)
    }

    fun loadOlderChatMessages() {
        val state = _chatUiState.value
        val target = state.target ?: return
        if (state.isLoading || state.isLoadingOlder || !state.hasMoreOlder) return
        val oldestId = state.messages.firstOrNull()?.messageId ?: return
        _chatUiState.value = state.copy(isLoadingOlder = true, error = null)
        loadChatPageInternal(target, fromMessageId = oldestId, appendOlder = true)
    }

    private fun loadChatPageInternal(
        target: TelegramChatTarget,
        fromMessageId: Long,
        appendOlder: Boolean
    ) {
        chatLoadJob?.cancel()
        chatLoadJob = viewModelScope.launch {
            val result = telegramRepo.loadChatHistoryPage(
                channelId = target.channelId,
                channelTitle = target.title,
                channelUsername = target.username,
                fromMessageId = fromMessageId,
                focusMessageId = if (appendOlder) 0L else target.focusMessageId
            )
            if (_chatUiState.value.target?.channelId != target.channelId) return@launch
            result.onSuccess { page ->
                val hydrated = page.messages.map { m ->
                    val tr = m.audioTrack?.let { applyCachedCover(it) }
                    if (tr != null) m.copy(audioTrack = tr) else m
                }
                val merged = if (appendOlder) {
                    val existingById = _chatUiState.value.messages.associateBy { it.messageId }.toMutableMap()
                    hydrated.forEach { existingById[it.messageId] = it }
                    existingById.values.sortedWith(compareBy({ it.dateEpochSec }, { it.messageId }))
                } else {
                    hydrated
                }
                _chatUiState.value = _chatUiState.value.copy(
                    messages = merged,
                    isLoading = false,
                    isLoadingOlder = false,
                    canSendMessages = page.canSendMessages,
                    hasMoreOlder = page.hasMore && (!appendOlder || hydrated.isNotEmpty()),
                    error = null
                )
            }.onFailure { err ->
                _chatUiState.value = _chatUiState.value.copy(
                    isLoading = false,
                    isLoadingOlder = false,
                    error = err.message ?: "Не удалось загрузить сообщения чата."
                )
            }
        }
    }

    fun requestChatPhoto(messageId: Long, photoFileId: Int) {
        if (photoFileId <= 0 || !inFlightChatPhotoIds.add(photoFileId)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val path = telegramRepo.downloadChatPhoto(photoFileId) ?: return@launch
                val cur = _chatUiState.value
                if (cur.target == null) return@launch
                val updated = cur.messages.map { m ->
                    if (m.messageId == messageId && m.photoFileId == photoFileId) {
                        m.copy(photoPath = path)
                    } else m
                }
                _chatUiState.value = cur.copy(messages = updated)
            } finally {
                inFlightChatPhotoIds.remove(photoFileId)
            }
        }
    }

    fun sendTelegramChatMessage(text: String) {
        val state = _chatUiState.value
        val target = state.target ?: return
        val clean = text.trim()
        if (clean.isEmpty() || state.isSending) return
        _chatUiState.value = state.copy(isSending = true, error = null)
        viewModelScope.launch {
            val res = telegramRepo.sendChatTextMessage(
                channelId = target.channelId,
                channelTitle = target.title,
                channelUsername = target.username,
                text = clean
            )
            if (_chatUiState.value.target?.channelId != target.channelId) return@launch
            res.onSuccess { sent ->
                val merged = (_chatUiState.value.messages.filterNot { it.messageId == sent.messageId } + sent)
                    .sortedWith(compareBy({ it.dateEpochSec }, { it.messageId }))
                _chatUiState.value = _chatUiState.value.copy(
                    messages = merged,
                    isSending = false,
                    error = null
                )
            }.onFailure { err ->
                _chatUiState.value = _chatUiState.value.copy(
                    isSending = false,
                    error = err.message ?: "Не удалось отправить сообщение."
                )
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 3.0f)
        _playbackSpeed.value = clamped
        prefs.edit().putFloat("playback_speed", clamped).apply()
        mediaController?.playbackParameters = PlaybackParameters(clamped)
        scheduleAutoCloudPush()
    }

    fun setDspPreset(preset: DspPreset) {
        _dspPreset.value = preset
        prefs.edit().putString("dsp_preset", preset.wireValue).apply()
        AudiobookPlayerService.applyDspPreset(preset)
        scheduleAutoCloudPush()
    }

    fun setSkipSilence(enabled: Boolean) {
        _skipSilence.value = enabled
        prefs.edit().putBoolean("skip_silence", enabled).apply()
        AudiobookPlayerService.applySkipSilence(enabled)
        scheduleAutoCloudPush()
    }

    fun setDacOutputMode(mode: DacOutputMode) {
        _dacOutputMode.value = mode
        prefs.edit().putString("dac_output_mode", mode.wireValue).apply()
        AudiobookPlayerService.applyDacConfig(mode, _dacPreferredDeviceId.value)
    }

    fun setDacPreferredDeviceId(deviceId: Int?) {
        val cleanId = deviceId?.takeIf { it > 0 }
        _dacPreferredDeviceId.value = cleanId
        prefs.edit().putInt("dac_preferred_device_id", cleanId ?: -1).apply()
        AudiobookPlayerService.applyDacConfig(_dacOutputMode.value, cleanId)
    }

    fun refreshDacDevices() {
        dacManager.refreshDevices()
        AudiobookPlayerService.applyDacConfig(_dacOutputMode.value, _dacPreferredDeviceId.value)
    }

    fun setLoudnessBoostDb(db: Int) {
        val clean = db.coerceIn(0, 12)
        _loudnessBoostDb.value = clean
        prefs.edit().putInt("loudness_boost_db", clean).apply()
        AudiobookPlayerService.applyLoudnessBoostDb(clean)
    }

    fun setSeekStepSec(sec: Int) {
        val clean = when (sec) {
            10, 15, 30 -> sec
            else -> 15
        }
        _seekStepSec.value = clean
        prefs.edit().putInt("seek_step_sec", clean).apply()
        AudiobookPlayerService.applySeekStepSec(clean)
    }

    fun setSmartRewindEnabled(enabled: Boolean) {
        _smartRewindEnabled.value = enabled
        prefs.edit().putBoolean("smart_rewind_enabled", enabled).apply()
    }

    fun dismissPlaybackError() {
        _playbackError.value = null
    }

    fun addBookmark(track: AudioTrack, positionSec: Long, note: String = "") {
        val safeSec = positionSec.coerceAtLeast(0L)
        val entry = AudioBookmark(
            bookmarkId = "bm_${System.currentTimeMillis()}_${(1000..9999).random()}",
            trackId = track.trackId,
            title = track.title.ifBlank { "Закладка" },
            positionSec = safeSec,
            note = note.trim(),
            createdAtMs = System.currentTimeMillis()
        )
        val updated = (listOf(entry) + _bookmarks.value).take(200)
        _bookmarks.value = updated
        saveBookmarks(updated)
        scheduleAutoCloudPush()
    }

    fun deleteBookmark(bookmarkId: String) {
        val updated = _bookmarks.value.filterNot { it.bookmarkId == bookmarkId }
        _bookmarks.value = updated
        saveBookmarks(updated)
        scheduleAutoCloudPush()
    }

    fun markTrackCompleted(trackId: String, manual: Boolean = true) {
        if (trackId in _completedTrackIds.value) return
        val updated = _completedTrackIds.value + trackId
        _completedTrackIds.value = updated
        saveCompletedIds(updated)
        scheduleAutoCloudPush()
    }

    fun toggleTrackCompleted(trackId: String) {
        val isCompleted = trackId in _completedTrackIds.value
        val updated = if (isCompleted) _completedTrackIds.value - trackId else _completedTrackIds.value + trackId
        _completedTrackIds.value = updated
        saveCompletedIds(updated)
        scheduleAutoCloudPush()
    }

    fun downloadTrackOffline(track: AudioTrack) {
        viewModelScope.launch {
            telegramRepo.downloadTrackForOffline(track)
        }
    }

    fun removeOfflineTrack(trackId: String) {
        viewModelScope.launch {
            telegramRepo.removeOfflineTrack(trackId)
        }
    }

    fun clearOfflineDownloads() {
        viewModelScope.launch {
            telegramRepo.clearOfflineDownloads()
        }
    }

    fun setSleepTimerMinutes(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null) {
            _sleepTimer.value = SleepTimerState.Off
            return
        }
        if (minutes == -1) {
            _sleepTimer.value = SleepTimerState.EndOfTrack
            return
        }
        sleepJob = viewModelScope.launch {
            var remaining = minutes * 60
            while (remaining > 0) {
                _sleepTimer.value = SleepTimerState.Countdown(remaining)
                delay(1000L)
                remaining--
            }
            mediaController?.pause()
            _sleepTimer.value = SleepTimerState.Off
        }
    }

    fun addToTracklist(track: AudioTrack) {
        if (_tracklist.value.any { it.trackId == track.trackId }) return
        val updated = _tracklist.value + track
        _tracklist.value = updated
        _isTracklistMode.value = true
        saveTrackList("tracklist_json", updated)
        prefs.edit().putBoolean("tracklist_mode", true).apply()
        scheduleAutoCloudPush()
    }

    fun removeFromTracklist(trackId: String) {
        val updated = _tracklist.value.filterNot { it.trackId == trackId }
        _tracklist.value = updated
        saveTrackList("tracklist_json", updated)
        if (updated.isEmpty()) {
            _isTracklistMode.value = false
            prefs.edit().putBoolean("tracklist_mode", false).apply()
        }
        scheduleAutoCloudPush()
    }

    fun moveTracklistItem(fromIndex: Int, toIndex: Int) {
        val mutable = _tracklist.value.toMutableList()
        if (fromIndex !in mutable.indices || toIndex !in mutable.indices || fromIndex == toIndex) return
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        _tracklist.value = mutable
        saveTrackList("tracklist_json", mutable)
        scheduleAutoCloudPush()
    }

    fun toggleTracklistMode(enabled: Boolean) {
        if (enabled && _tracklist.value.isEmpty()) return
        _isTracklistMode.value = enabled
        prefs.edit().putBoolean("tracklist_mode", enabled).apply()
        persistPlaybackSnapshot(forceCloud = true, forceLocal = true)
    }

    fun toggleFavorite(track: AudioTrack) {
        val exists = _favorites.value.any { it.trackId == track.trackId }
        val updated = if (exists) {
            _favorites.value.filterNot { it.trackId == track.trackId }
        } else {
            listOf(track) + _favorites.value
        }
        _favorites.value = updated
        saveTrackList("favorites_json", updated)
        scheduleAutoCloudPush()
    }

    fun createPlaylist(name: String, initialTrack: AudioTrack? = null, description: String = "") {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val newPl = UserPlaylist(
            id = "pl_${System.currentTimeMillis()}",
            name = clean,
            description = description.trim(),
            items = listOfNotNull(initialTrack)
        )
        val updated = _playlists.value + newPl
        _playlists.value = updated
        savePlaylists(updated)
        scheduleAutoCloudPush()
    }

    fun deletePlaylist(playlistId: String) {
        val updated = _playlists.value.filterNot { it.id == playlistId }
        _playlists.value = updated
        savePlaylists(updated)
        scheduleAutoCloudPush()
    }

    fun addTrackToPlaylist(playlistId: String, track: AudioTrack) {
        var addedOrder = -1
        val updated = _playlists.value.map { pl ->
            if (pl.id == playlistId && pl.items.none { it.trackId == track.trackId }) {
                addedOrder = pl.items.size
                pl.copy(items = pl.items + track)
            } else pl
        }
        _playlists.value = updated
        savePlaylists(updated)
        if (addedOrder >= 0) {
            scheduleAutoCloudPush()
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        val updated = _playlists.value.map { pl ->
            if (pl.id == playlistId) {
                pl.copy(items = pl.items.filterNot { it.trackId == trackId })
            } else pl
        }
        _playlists.value = updated
        savePlaylists(updated)
        scheduleAutoCloudPush()
    }

    fun refreshProxies() {
        viewModelScope.launch {
            telegramRepo.loadProxies()
        }
    }

    fun toggleUseProxy(enabled: Boolean) {
        viewModelScope.launch {
            _isProxyBusy.value = true
            _proxyError.value = null
            _proxyMessage.value = null
            try {
                if (!enabled) {
                    telegramRepo.disableProxy()
                        .onSuccess { _proxyMessage.value = "Прокси отключён. Используется прямое соединение." }
                        .onFailure { _proxyError.value = it.message ?: "Не удалось отключить прокси." }
                } else {
                    val savedId = prefs.getInt("last_proxy_id", -1)
                    val target = proxies.value.firstOrNull { it.id == savedId } ?: proxies.value.firstOrNull()
                    if (target == null) {
                        _proxyError.value = "Сначала добавьте прокси-сервер ниже."
                    } else {
                        telegramRepo.enableProxy(target.id)
                            .onSuccess {
                                prefs.edit().putInt("last_proxy_id", target.id).apply()
                                _proxyMessage.value = "Подключено через ${target.server}:${target.port}"
                            }
                            .onFailure { _proxyError.value = it.message ?: "Не удалось включить прокси." }
                    }
                }
            } finally {
                _isProxyBusy.value = false
            }
        }
    }

    fun addProxy(
        type: TelegramProxyType,
        server: String,
        port: Int,
        username: String = "",
        password: String = "",
        secret: String = "",
        enable: Boolean = true
    ) {
        viewModelScope.launch {
            _isProxyBusy.value = true
            _proxyError.value = null
            _proxyMessage.value = null
            try {
                telegramRepo.addProxy(type, server, port, username, password, secret, enable)
                    .onSuccess { item ->
                        prefs.edit().putInt("last_proxy_id", item.id).apply()
                        _proxyMessage.value = "Прокси ${item.server}:${item.port} сохранён"
                        telegramRepo.pingProxy(item.id)
                    }
                    .onFailure { err ->
                        _proxyError.value = err.message ?: "Не удалось добавить прокси."
                    }
            } finally {
                _isProxyBusy.value = false
            }
        }
    }

    fun enableProxy(proxyId: Int) {
        viewModelScope.launch {
            _isProxyBusy.value = true
            _proxyError.value = null
            _proxyMessage.value = null
            try {
                telegramRepo.enableProxy(proxyId)
                    .onSuccess {
                        prefs.edit().putInt("last_proxy_id", proxyId).apply()
                        val item = proxies.value.firstOrNull { it.id == proxyId }
                        _proxyMessage.value = if (item != null) {
                            "Активен ${item.server}:${item.port}"
                        } else {
                            "Прокси включён"
                        }
                    }
                    .onFailure { _proxyError.value = it.message ?: "Не удалось включить прокси." }
            } finally {
                _isProxyBusy.value = false
            }
        }
    }

    fun removeProxy(proxyId: Int) {
        viewModelScope.launch {
            _isProxyBusy.value = true
            _proxyError.value = null
            _proxyMessage.value = null
            try {
                telegramRepo.removeProxy(proxyId)
                    .onSuccess { _proxyMessage.value = "Прокси удалён" }
                    .onFailure { _proxyError.value = it.message ?: "Не удалось удалить прокси." }
            } finally {
                _isProxyBusy.value = false
            }
        }
    }

    fun pingProxy(proxyId: Int) {
        viewModelScope.launch {
            telegramRepo.pingProxy(proxyId)
        }
    }

    fun refreshCacheStats() {
        viewModelScope.launch {
            _isCacheBusy.value = true
            try {
                _cacheStats.value = telegramRepo.getStorageCacheStats()
            } finally {
                _isCacheBusy.value = false
            }
        }
    }

    fun setCacheTtlDays(days: Int) {
        _cacheTtlDays.value = days
        prefs.edit().putInt("cache_ttl_days", days).apply()
        if (days > 0) {
            telegramRepo.applyCacheTtl(days)
        }
    }

    fun clearCache(clearAudio: Boolean, clearCovers: Boolean, clearOther: Boolean) {
        if (!clearAudio && !clearCovers && !clearOther) return
        viewModelScope.launch {
            _isCacheBusy.value = true
            _cacheMessage.value = null
            try {
                if (clearAudio && _isPlaying.value) {
                    mediaController?.pause()
                }
                val afterStats = telegramRepo.optimizeAndClearCache(
                    clearAudio = clearAudio,
                    clearCovers = clearCovers,
                    clearOther = clearOther
                )
                if (clearCovers) {
                    resolvedCovers.clear()
                    inFlightCoverIds.clear()
                    _currentTrack.value = _currentTrack.value?.copy(coverPath = null)
                    _queue.value = _queue.value.map { it.copy(coverPath = null) }
                    _tracklist.value = _tracklist.value.map { it.copy(coverPath = null) }
                    _favorites.value = _favorites.value.map { it.copy(coverPath = null) }
                    _playlists.value = _playlists.value.map { pl ->
                        pl.copy(items = pl.items.map { it.copy(coverPath = null) })
                    }
                    _mixes.value = _mixes.value.map { m ->
                        m.copy(coverPath = null, tracks = m.tracks.map { it.copy(coverPath = null) })
                    }
                    _autoPlaylists.value = _autoPlaylists.value.map { m ->
                        m.copy(coverPath = null, tracks = m.tracks.map { it.copy(coverPath = null) })
                    }
                    _seriesGroups.value = _seriesGroups.value.map { s ->
                        s.copy(coverPath = null, tracks = s.tracks.map { it.copy(coverPath = null) })
                    }
                    _categories.value = _categories.value.map { c ->
                        c.copy(coverPath = null, tracks = c.tracks.map { it.copy(coverPath = null) })
                    }
                    val updatedMap = _tracksByChannel.value.mapValues { (_, list) ->
                        list.map { it.copy(coverPath = null) }
                    }
                    _tracksByChannel.value = updatedMap
                    persistPlaybackSnapshot(forceCloud = false, forceLocal = true)
                    updateWidgetAsync(_currentTrack.value, _isPlaying.value)
                }
                _cacheStats.value = afterStats
                _cacheMessage.value = "Кеш успешно очищен. Избранное, плейлисты и аккаунт сохранены."
            } finally {
                _isCacheBusy.value = false
            }
        }
    }

    private fun loadSavedLastTrack(): AudioTrack? = runCatching {
        val raw = prefs.getString("last_track_json", null) ?: return null
        applyCachedCover(jsonToTrack(JSONObject(raw)))
    }.getOrNull()

    private fun trackToJson(t: AudioTrack): JSONObject = JSONObject().apply {
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

    private fun jsonToTrack(o: JSONObject): AudioTrack = AudioTrack(
        trackId = o.optString("trackId"),
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
        coverPath = o.optString("coverPath").takeIf { it.isNotBlank() },
        coverFileId = o.optInt("coverFileId"),
        coverMaxDim = o.optInt("coverMaxDim"),
        dateEpoch = o.optLong("dateEpoch")
    )

    private fun saveTrackList(key: String, list: List<AudioTrack>) {
        persistScope.launch {
            val arr = JSONArray()
            list.forEach { arr.put(trackToJson(it)) }
            prefs.edit().putString(key, arr.toString()).apply()
        }
    }

    private fun loadTrackList(key: String): List<AudioTrack> = runCatching {
        val raw = prefs.getString(key, null) ?: return emptyList()
        val arr = JSONArray(raw)
        List(arr.length()) { idx -> applyCachedCover(jsonToTrack(arr.getJSONObject(idx))) }
    }.getOrDefault(emptyList())

    private fun savePlaylists(list: List<UserPlaylist>) {
        persistScope.launch {
            val arr = JSONArray()
            list.forEach { pl ->
                val itemsArr = JSONArray()
                pl.items.forEach { itemsArr.put(trackToJson(it)) }
                arr.put(JSONObject().apply {
                    put("id", pl.id)
                    put("name", pl.name)
                    put("description", pl.description)
                    put("items", itemsArr)
                })
            }
            prefs.edit().putString("playlists_json", arr.toString()).apply()
        }
    }

    private fun loadPlaylists(): List<UserPlaylist> = runCatching {
        val raw = prefs.getString("playlists_json", null) ?: return emptyList()
        val arr = JSONArray(raw)
        List(arr.length()) { idx ->
            val o = arr.getJSONObject(idx)
            val itemsArr = o.optJSONArray("items") ?: JSONArray()
            UserPlaylist(
                id = o.optString("id"),
                name = o.optString("name"),
                description = o.optString("description"),
                items = List(itemsArr.length()) { j -> applyCachedCover(jsonToTrack(itemsArr.getJSONObject(j))) }
            )
        }
    }.getOrDefault(emptyList())

    private fun savePlaybackSignals(list: List<PlaybackSignal>) {
        persistScope.launch {
            val arr = JSONArray()
            list.forEach { s ->
                arr.put(JSONObject().apply {
                    put("trackId", s.trackId)
                    put("channelId", s.channelId)
                    put("author", s.author)
                    put("title", s.title)
                    put("durationSec", s.durationSec)
                    put("playedSec", s.playedSec)
                    put("playCount", s.playCount)
                    put("skipCount", s.skipCount)
                    put("lastPlayedAtMs", s.lastPlayedAtMs)
                })
            }
            prefs.edit().putString("play_history_json", arr.toString()).apply()
        }
    }

    private fun loadPlaybackSignals(): List<PlaybackSignal> = runCatching {
        val raw = prefs.getString("play_history_json", null) ?: return emptyList()
        val arr = JSONArray(raw)
        List(arr.length()) { idx ->
            val o = arr.getJSONObject(idx)
            PlaybackSignal(
                trackId = o.optString("trackId"),
                channelId = o.optString("channelId"),
                author = o.optString("author"),
                title = o.optString("title"),
                durationSec = o.optLong("durationSec"),
                playedSec = o.optLong("playedSec"),
                playCount = o.optInt("playCount", 1),
                skipCount = o.optInt("skipCount", 0),
                lastPlayedAtMs = o.optLong("lastPlayedAtMs")
            )
        }
    }.getOrDefault(emptyList())

    private fun saveBookmarks(list: List<AudioBookmark>) {
        persistScope.launch {
            val arr = JSONArray()
            list.forEach { b ->
                arr.put(JSONObject().apply {
                    put("bookmarkId", b.bookmarkId)
                    put("trackId", b.trackId)
                    put("title", b.title)
                    put("positionSec", b.positionSec)
                    put("note", b.note)
                    put("createdAtMs", b.createdAtMs)
                })
            }
            prefs.edit().putString("bookmarks_json", arr.toString()).apply()
        }
    }

    private fun loadBookmarks(): List<AudioBookmark> = runCatching {
        val raw = prefs.getString("bookmarks_json", null) ?: return emptyList()
        val arr = JSONArray(raw)
        List(arr.length()) { idx ->
            val o = arr.getJSONObject(idx)
            AudioBookmark(
                bookmarkId = o.optString("bookmarkId"),
                trackId = o.optString("trackId"),
                title = o.optString("title"),
                positionSec = o.optLong("positionSec"),
                note = o.optString("note"),
                createdAtMs = o.optLong("createdAtMs")
            )
        }
    }.getOrDefault(emptyList())

    private fun saveCompletedIds(ids: Set<String>) {
        persistScope.launch {
            val arr = JSONArray()
            ids.forEach { arr.put(it) }
            prefs.edit().putString("completed_ids_json", arr.toString()).apply()
        }
    }

    private fun loadCompletedIds(): Set<String> = runCatching {
        val raw = prefs.getString("completed_ids_json", null) ?: return emptySet()
        val arr = JSONArray(raw)
        buildSet {
            for (i in 0 until arr.length()) {
                val id = arr.optString(i)
                if (id.isNotBlank()) add(id)
            }
        }
    }.getOrDefault(emptySet())

    private fun saveDislikedIds(ids: Set<String>) {
        persistScope.launch {
            val arr = JSONArray()
            ids.forEach { arr.put(it) }
            prefs.edit().putString(PREF_DISLIKED_IDS_JSON, arr.toString()).apply()
        }
    }

    private fun loadDislikedIds(): Set<String> = runCatching {
        val raw = prefs.getString(PREF_DISLIKED_IDS_JSON, null) ?: return emptySet()
        val arr = JSONArray(raw)
        buildSet {
            for (i in 0 until arr.length()) {
                val id = arr.optString(i)
                if (id.isNotBlank()) add(id)
            }
        }
    }.getOrDefault(emptySet())

    private fun saveDailyStats(map: Map<String, Long>) {
        persistScope.launch {
            val obj = JSONObject()
            map.forEach { (k, v) -> obj.put(k, v) }
            prefs.edit().putString("daily_stats_json", obj.toString()).apply()
        }
    }

    private fun loadDailyStats(): Map<String, Long> = runCatching {
        val raw = prefs.getString("daily_stats_json", null) ?: return emptyMap()
        val obj = JSONObject(raw)
        buildMap {
            obj.keys().forEach { k -> put(k, obj.optLong(k, 0L).coerceAtLeast(0L)) }
        }
    }.getOrDefault(emptyMap())

    private fun saveLockedMixSnapshots(
        snapshots: List<LockedMixSnapshot>,
        builtAtMs: Long,
        channelSig: String
    ) {
        persistScope.launch {
            val arr = JSONArray()
            snapshots.forEach { s ->
                val idsArr = JSONArray()
                s.trackIds.forEach { idsArr.put(it) }
                arr.put(
                    JSONObject().apply {
                        put("id", s.id)
                        put("title", s.title)
                        put("subtitle", s.subtitle)
                        put("kicker", s.kicker)
                        put("description", s.description)
                        put("isAutoPlaylist", s.isAutoPlaylist)
                        put("trackIds", idsArr)
                    }
                )
            }
            prefs.edit()
                .putString(PREF_LOCKED_MIXES_JSON, arr.toString())
                .putLong(PREF_LAST_MIXES_BUILD_MS, builtAtMs)
                .putString(PREF_MIXES_CHANNEL_SIG, channelSig)
                .apply()
        }
    }

    private fun loadLockedMixSnapshots(): List<LockedMixSnapshot> = runCatching {
        val raw = prefs.getString(PREF_LOCKED_MIXES_JSON, null) ?: return emptyList()
        val arr = JSONArray(raw)
        buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val idsArr = o.optJSONArray("trackIds") ?: continue
                val ids = buildList(idsArr.length()) {
                    for (j in 0 until idsArr.length()) {
                        val tid = idsArr.optString(j)
                        if (tid.isNotBlank()) add(tid)
                    }
                }
                if (ids.isNotEmpty()) {
                    add(
                        LockedMixSnapshot(
                            id = o.optString("id"),
                            title = o.optString("title"),
                            subtitle = o.optString("subtitle"),
                            kicker = o.optString("kicker"),
                            description = o.optString("description"),
                            isAutoPlaylist = o.optBoolean("isAutoPlaylist", false),
                            trackIds = ids
                        )
                    )
                }
            }
        }
    }.getOrDefault(emptyList())

    private fun loadTrackPositions() {
        runCatching {
            val raw = prefs.getString(PREF_TRACK_POSITIONS_JSON, null) ?: return
            val obj = JSONObject(raw)
            synchronized(trackPositionsLock) {
                trackPositions.clear()
                obj.keys().forEach { k ->
                    val pos = obj.optLong(k, 0L)
                    if (k.isNotBlank() && pos > 0L) {
                        trackPositions[k] = pos
                    }
                }
            }
        }
    }

    companion object {
        private const val MIX_REFRESH_INTERVAL_MS = 24L * 60L * 60L * 1000L
        private const val PREF_RECOMMENDATION_SEED = "recommendation_seed"
        private const val PREF_LAST_MIXES_BUILD_MS = "last_mixes_build_ms"
        private const val PREF_MIXES_CHANNEL_SIG = "mixes_channel_sig"
        private const val PREF_LOCKED_MIXES_JSON = "locked_mixes_snapshot_json"
        private const val PREF_TRACK_POSITIONS_JSON = "track_positions_json"
        private const val PREF_DISLIKED_IDS_JSON = "disliked_ids_json"
        private const val PREF_TG_CLOUD_AUTOSYNC = "tg_cloud_autosync"
        private const val PREF_TG_CLOUD_LAST_SYNC_MS = "tg_cloud_last_sync_ms"
    }
}
