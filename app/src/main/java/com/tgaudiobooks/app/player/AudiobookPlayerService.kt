package com.tgaudiobooks.app.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.metadata.id3.ChapterFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.tgaudiobooks.app.MainActivity
import com.tgaudiobooks.app.data.model.AudioChapter
import com.tgaudiobooks.app.data.model.AudioStreamFormatInfo
import com.tgaudiobooks.app.data.model.AudioTrack
import com.tgaudiobooks.app.data.model.DacOutputMode
import com.tgaudiobooks.app.data.model.DspPreset
import com.tgaudiobooks.app.data.telegram.TelegramRepository
import com.tgaudiobooks.app.widget.EchoGramWidgetProvider
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground Media3 MediaSessionService for background audiobook playback,
 * Samsung One UI / Android system notification with full Previous / -15s / Play-Pause / +15s / Next controls,
 * hardware Audio DSP (Equalizer + LoudnessEnhancer + Skip Silence), widget intents,
 * and instant progressive TDLib chunk streaming.
 */
@OptIn(UnstableApi::class)
class AudiobookPlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var forwardingPlayer: ForwardingPlayer
    private val mainHandler = Handler(Looper.getMainLooper())
    private val widgetExecutor = Executors.newSingleThreadExecutor()

    private var equalizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private lateinit var dacManager: DacAudioManager

    private val rewindCommand = SessionCommand(ACTION_REWIND_15, Bundle.EMPTY)
    private val forwardCommand = SessionCommand(ACTION_FORWARD_15, Bundle.EMPTY)

    override fun onCreate() {
        super.onCreate()
        activeService = this

        dacManager = DacAudioManager.getInstance(applicationContext)
        val audioAttributes = dacManager.buildMedia3AudioAttributes(activeDacOutputMode)

        val telegramRepo = TelegramRepository.getInstance(applicationContext)
        val dataSourceFactory = TdlibAudioDataSource.Factory(applicationContext, telegramRepo)
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(1))

        val renderersFactory = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
            .setEnableAudioFloatOutput(false)
            .setEnableAudioTrackPlaybackParams(false)

        // Start playback fast on cellular/Telegram connections after initial 1.5s audio buffer
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000, // minBufferMs
                60_000, // maxBufferMs
                1_500,  // bufferForPlaybackMs (instant start!)
                3_000   // bufferForPlaybackAfterRebufferMs
            )
            .build()

        player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(activeSeekStepSec * 1000L)
            .setSeekForwardIncrementMs(activeSeekStepSec * 1000L)
            .build()

        dacManager.onDevicesChangedListener = {
            mainHandler.post { applyDacAndDspInternal(flushAudioTrack = false) }
        }
        applyDacAndDspInternal(flushAudioTrack = false)

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    bindAudioEffects(audioSessionId)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId.orEmpty()
                if (_embeddedChapters.value.first != id) {
                    _embeddedChapters.value = id to emptyList()
                }
                if (id.isEmpty()) {
                    dacManager.updateStreamFormat(AudioStreamFormatInfo())
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                var selectedFormat: androidx.media3.common.Format? = null
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_AUDIO && group.isSelected) {
                        for (i in 0 until group.length) {
                            if (group.isTrackSelected(i)) {
                                selectedFormat = group.getTrackFormat(i)
                                break
                            }
                        }
                    }
                    if (selectedFormat != null) break
                }
                if (selectedFormat == null) {
                    dacManager.updateStreamFormat(AudioStreamFormatInfo())
                } else {
                    val mime = selectedFormat.sampleMimeType.orEmpty().lowercase()
                    val codecRaw = selectedFormat.codecs.orEmpty().trim()
                    val codecLabel = when {
                        mime.contains("flac") -> "FLAC"
                        mime.contains("opus") -> "Opus"
                        mime.contains("vorbis") -> "Ogg Vorbis"
                        mime.contains("mp4a") || mime.contains("aac") -> "AAC / M4B"
                        mime.contains("mpeg") -> "MP3"
                        mime.contains("wav") || mime.contains("raw") -> "PCM"
                        codecRaw.isNotBlank() -> codecRaw.uppercase()
                        mime.isNotBlank() -> mime.substringAfter("/").uppercase()
                        else -> ""
                    }
                    dacManager.updateStreamFormat(
                        AudioStreamFormatInfo(
                            codec = codecLabel,
                            sampleRateHz = selectedFormat.sampleRate.coerceAtLeast(0),
                            bitrateBps = selectedFormat.bitrate.coerceAtLeast(0),
                            channelCount = selectedFormat.channelCount.coerceAtLeast(0)
                        )
                    )
                    if (activeDacOutputMode == DacOutputMode.BIT_PERFECT) {
                        applyDacAndDspInternal(flushAudioTrack = false)
                    }
                }
            }

            override fun onMetadata(metadata: Metadata) {
                val mediaId = player.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() } ?: return
                val extracted = ArrayList<AudioChapter>()
                for (i in 0 until metadata.length()) {
                    val entry = metadata.get(i)
                    if (entry is ChapterFrame) {
                        var title = ""
                        for (j in 0 until entry.subFrameCount) {
                            val sub = entry.getSubFrame(j)
                            if (sub is TextInformationFrame && sub.id == "TIT2") {
                                title = sub.values.firstOrNull()?.trim().orEmpty()
                                if (title.isNotEmpty()) break
                            }
                        }
                        if (title.isEmpty()) {
                            title = entry.chapterId?.takeIf { it.isNotBlank() } ?: "Глава ${extracted.size + 1}"
                        }
                        extracted += AudioChapter(
                            id = "id3_${mediaId}_${entry.startTimeMs}",
                            title = title,
                            startSec = (entry.startTimeMs / 1000L).coerceAtLeast(0L)
                        )
                    }
                }
                if (extracted.isNotEmpty()) {
                    val existing = if (_embeddedChapters.value.first == mediaId) _embeddedChapters.value.second else emptyList()
                    val merged = (existing + extracted).distinctBy { it.startSec }.sortedBy { it.startSec }
                    _embeddedChapters.value = mediaId to merged
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (player.mediaItemCount > 0) {
                    dismissWidgetFallbackNotification()
                }
                syncWidgetFromPlayer(isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (player.mediaItemCount > 0 && playbackState != Player.STATE_IDLE) {
                    dismissWidgetFallbackNotification()
                }
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                if (player.mediaItemCount > 0) {
                    dismissWidgetFallbackNotification()
                }
                syncWidgetFromPlayer(player.isPlaying)
            }
        })

        // Wrap single-item ExoPlayer in a ForwardingPlayer that exposes Previous / Next / +-15s
        // so Samsung One UI and Android 13+ MediaStyle notifications always display Prev & Next buttons
        // instead of collapsing to Pause + Back.
        forwardingPlayer = object : ForwardingPlayer(player) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(COMMAND_SEEK_TO_PREVIOUS)
                    .add(COMMAND_SEEK_TO_NEXT)
                    .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(COMMAND_SEEK_BACK)
                    .add(COMMAND_SEEK_FORWARD)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    COMMAND_SEEK_TO_PREVIOUS,
                    COMMAND_SEEK_TO_NEXT,
                    COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    COMMAND_SEEK_BACK,
                    COMMAND_SEEK_FORWARD -> true
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun hasNextMediaItem(): Boolean = true

            override fun hasPreviousMediaItem(): Boolean = true

            override fun seekToNext() {
                mainHandler.post { onSkipNextRequested?.invoke() }
            }

            override fun seekToNextMediaItem() {
                mainHandler.post { onSkipNextRequested?.invoke() }
            }

            override fun seekToPrevious() {
                if (currentPosition > 5_000L) {
                    seekTo(0L)
                } else {
                    mainHandler.post { onSkipPreviousRequested?.invoke() }
                }
            }

            override fun seekToPreviousMediaItem() {
                if (currentPosition > 5_000L) {
                    seekTo(0L)
                } else {
                    mainHandler.post { onSkipPreviousRequested?.invoke() }
                }
            }

            override fun seekBack() {
                seekTo((currentPosition - activeSeekStepSec * 1000L).coerceAtLeast(0L))
            }

            override fun seekForward() {
                val dur = duration
                val target = currentPosition + activeSeekStepSec * 1000L
                seekTo(if (dur > 0L) minOf(dur, target) else target)
            }
        }

        val customButtons = buildCustomButtons()
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val base = super.onConnect(session, controller)
                val sessionCmds = base.availableSessionCommands.buildUpon()
                    .add(rewindCommand)
                    .add(forwardCommand)
                    .build()
                val playerCmds = base.availablePlayerCommands.buildUpon()
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_BACK)
                    .add(Player.COMMAND_SEEK_FORWARD)
                    .build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(sessionCmds)
                    .setAvailablePlayerCommands(playerCmds)
                    .build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                when (customCommand.customAction) {
                    ACTION_REWIND_15 -> forwardingPlayer.seekBack()
                    ACTION_FORWARD_15 -> forwardingPlayer.seekForward()
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(sessionActivity)
            .setCallback(callback)
            .build()
            .also { session ->
                session.setCustomLayout(customButtons)
            }
    }

    private fun buildCustomButtons(): List<CommandButton> {
        val step = activeSeekStepSec
        return listOf(
            CommandButton.Builder()
                .setDisplayName("-$step сек")
                .setSessionCommand(rewindCommand)
                .setIconResId(android.R.drawable.ic_media_rew)
                .build(),
            CommandButton.Builder()
                .setDisplayName("+$step сек")
                .setSessionCommand(forwardCommand)
                .setIconResId(android.R.drawable.ic_media_ff)
                .build()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_WIDGET_PLAY_PAUSE -> {
                ensureWidgetForegroundFallbackIfCold()
                mainHandler.post {
                    val cb = onWidgetPlayPauseRequested
                    if (cb != null) {
                        cb.invoke()
                    } else if (::player.isInitialized && player.mediaItemCount > 0) {
                        if (player.isPlaying) player.pause() else player.play()
                    } else {
                        launchMainActivityForWidgetResume()
                    }
                }
            }
            ACTION_WIDGET_NEXT -> {
                ensureWidgetForegroundFallbackIfCold()
                mainHandler.post {
                    val cb = onSkipNextRequested
                    if (cb != null) cb.invoke() else launchMainActivityForWidgetResume()
                }
            }
            ACTION_WIDGET_PREV -> {
                ensureWidgetForegroundFallbackIfCold()
                mainHandler.post {
                    val cb = onSkipPreviousRequested
                    if (cb != null) cb.invoke() else launchMainActivityForWidgetResume()
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun ensureWidgetForegroundFallbackIfCold() {
        if (!::player.isInitialized || player.mediaItemCount == 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = getSystemService(NotificationManager::class.java)
                val ch = NotificationChannel(
                    WIDGET_FALLBACK_CHANNEL,
                    "Воспроизведение аудиокниг",
                    NotificationManager.IMPORTANCE_LOW
                )
                nm?.createNotificationChannel(ch)
            }
            val notif = NotificationCompat.Builder(this, WIDGET_FALLBACK_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("EchoGram")
                .setContentText("Запуск воспроизведения...")
                .setSilent(true)
                .build()
            runCatching {
                val fgType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                } else {
                    0
                }
                ServiceCompat.startForeground(this, WIDGET_FALLBACK_NOTIF_ID, notif, fgType)
            }
            mainHandler.postDelayed({
                if (!::player.isInitialized || player.mediaItemCount == 0) {
                    runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
                    dismissWidgetFallbackNotification()
                }
            }, 8_000L)
        }
    }

    private fun dismissWidgetFallbackNotification() {
        runCatching {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.cancel(WIDGET_FALLBACK_NOTIF_ID)
        }
    }

    private fun launchMainActivityForWidgetResume() {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_WIDGET_AUTO_PLAY, true)
        }
        runCatching { startActivity(openIntent) }
    }

    private fun syncWidgetFromPlayer(isPlaying: Boolean) {
        val meta = if (::player.isInitialized) player.mediaMetadata else null
        val title = meta?.title?.toString()
        val author = meta?.artist?.toString()
        val artPath = meta?.artworkUri?.path
        if (!title.isNullOrBlank()) {
            val appCtx = applicationContext
            widgetExecutor.execute {
                EchoGramWidgetProvider.updateAll(
                    context = appCtx,
                    title = title,
                    author = author,
                    coverPath = artPath,
                    isPlaying = isPlaying
                )
            }
        }
    }

    private fun bindAudioEffects(audioSessionId: Int) {
        releaseAudioEffects()
        // Never attach Android AudioEffect instances in BIT_PERFECT mode —
        // even disabled effects can force AudioFlinger onto its software mixer path.
        if (activeDacOutputMode == DacOutputMode.BIT_PERFECT) {
            return
        }
        val needEq = activeDspPreset != DspPreset.STANDARD
        val nightMb = if (activeDspPreset == DspPreset.NIGHT) 450 else 0
        val boostMb = activeLoudnessBoostMb.coerceIn(0, 1200)
        val targetMb = maxOf(nightMb, boostMb)
        if (needEq) {
            runCatching {
                equalizer = Equalizer(0, audioSessionId).apply {
                    applyEqualizerPreset(this, activeDspPreset)
                }
            }
        }
        if (targetMb > 0) {
            runCatching {
                loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                    setTargetGain(targetMb)
                    enabled = true
                }
            }
        }
    }

    private fun applyEqualizerPreset(eq: Equalizer, preset: DspPreset) {
        if (preset == DspPreset.STANDARD || activeDacOutputMode == DacOutputMode.BIT_PERFECT) {
            eq.enabled = false
            return
        }
        val bands = eq.numberOfBands.toInt().coerceAtLeast(1)
        val range = eq.bandLevelRange
        val minLevel = range.getOrNull(0)?.toInt() ?: -1500
        val maxLevel = range.getOrNull(1)?.toInt() ?: 1500

        for (b in 0 until bands) {
            val centerHz = runCatching { eq.getCenterFreq(b.toShort()) / 1000 }.getOrDefault(1000)
            val targetMb = when (preset) {
                DspPreset.STANDARD -> 0
                DspPreset.VOICE -> when {
                    centerHz < 220 -> -450
                    centerHz in 900..4200 -> 550
                    else -> 200
                }
                DspPreset.NIGHT -> when {
                    centerHz < 200 -> -250
                    centerHz in 800..3200 -> 250
                    centerHz > 5000 -> -300
                    else -> 0
                }
                DspPreset.BASS -> when {
                    centerHz < 260 -> 600
                    centerHz in 260..1200 -> 150
                    else -> -100
                }
            }
            eq.setBandLevel(b.toShort(), targetMb.coerceIn(minLevel, maxLevel).toShort())
        }
        eq.enabled = true
    }

    private fun applyDacAndDspInternal(flushAudioTrack: Boolean = false) {
        val bypassDsp = (activeDacOutputMode == DacOutputMode.BIT_PERFECT)
        if (::dacManager.isInitialized) {
            val targetDevice = dacManager.resolveTargetAudioDevice(
                mode = activeDacOutputMode,
                preferredDeviceId = activeDacPreferredDeviceId
            )
            if (::player.isInitialized) {
                runCatching { player.setPreferredAudioDevice(targetDevice) }
                runCatching {
                    player.setAudioAttributes(
                        dacManager.buildMedia3AudioAttributes(activeDacOutputMode),
                        true
                    )
                }
            }
            dacManager.applyMixerRouting(targetDevice, activeDacOutputMode)
        }
        if (::player.isInitialized) {
            player.skipSilenceEnabled = if (bypassDsp) false else activeSkipSilence
        }
        if (bypassDsp) {
            releaseAudioEffects()
        } else {
            val needEq = activeDspPreset != DspPreset.STANDARD
            val nightMb = if (activeDspPreset == DspPreset.NIGHT) 450 else 0
            val boostMb = activeLoudnessBoostMb.coerceIn(0, 1200)
            val targetMb = maxOf(nightMb, boostMb)
            val needLoudness = targetMb > 0
            if (!needEq && !needLoudness) {
                releaseAudioEffects()
            } else if (::player.isInitialized && player.audioSessionId > 0) {
                if ((needEq && equalizer == null) || (needLoudness && loudnessEnhancer == null)) {
                    bindAudioEffects(player.audioSessionId)
                } else {
                    if (!needEq) {
                        runCatching { equalizer?.release() }
                        equalizer = null
                    } else {
                        equalizer?.let { eq ->
                            runCatching { applyEqualizerPreset(eq, activeDspPreset) }
                        }
                    }
                    if (!needLoudness) {
                        runCatching { loudnessEnhancer?.release() }
                        loudnessEnhancer = null
                    } else {
                        loudnessEnhancer?.let { le ->
                            runCatching {
                                le.setTargetGain(targetMb)
                                le.enabled = true
                            }
                        }
                    }
                }
            }
        }
        if (flushAudioTrack && ::player.isInitialized && player.mediaItemCount > 0 && player.playbackState != Player.STATE_IDLE) {
            runCatching {
                val pos = player.currentPosition.coerceAtLeast(0L)
                player.seekTo(pos)
            }
        }
    }

    private fun releaseAudioEffects() {
        runCatching { equalizer?.release() }
        equalizer = null
        runCatching { loudnessEnhancer?.release() }
        loudnessEnhancer = null
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = mediaSession?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0 || p.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        if (activeService === this) {
            activeService = null
        }
        if (::dacManager.isInitialized) {
            dacManager.onDevicesChangedListener = null
            dacManager.clearMixerOverride()
            dacManager.updateStreamFormat(AudioStreamFormatInfo())
        }
        _embeddedChapters.value = "" to emptyList()
        mainHandler.removeCallbacksAndMessages(null)
        widgetExecutor.shutdown()
        dismissWidgetFallbackNotification()
        releaseAudioEffects()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_REWIND_15 = "com.tgaudiobooks.app.action.REWIND_15"
        const val ACTION_FORWARD_15 = "com.tgaudiobooks.app.action.FORWARD_15"
        const val ACTION_WIDGET_PLAY_PAUSE = "com.tgaudiobooks.app.widget.PLAY_PAUSE"
        const val ACTION_WIDGET_NEXT = "com.tgaudiobooks.app.widget.NEXT"
        const val ACTION_WIDGET_PREV = "com.tgaudiobooks.app.widget.PREV"
        const val EXTRA_WIDGET_AUTO_PLAY = "echogram_widget_auto_play"
        private const val WIDGET_FALLBACK_CHANNEL = "echogram_widget_playback"
        private const val WIDGET_FALLBACK_NOTIF_ID = 4102

        @Volatile
        var onSkipNextRequested: (() -> Unit)? = null

        @Volatile
        var onSkipPreviousRequested: (() -> Unit)? = null

        @Volatile
        var onWidgetPlayPauseRequested: (() -> Unit)? = null

        private val _embeddedChapters = MutableStateFlow<Pair<String, List<AudioChapter>>>("" to emptyList())
        val embeddedChapters: StateFlow<Pair<String, List<AudioChapter>>> = _embeddedChapters.asStateFlow()

        @Volatile
        var activeDspPreset: DspPreset = DspPreset.STANDARD
            private set

        @Volatile
        var activeSkipSilence: Boolean = false
            private set

        @Volatile
        var activeDacOutputMode: DacOutputMode = DacOutputMode.AUTO
            private set

        @Volatile
        var activeDacPreferredDeviceId: Int? = null
            private set

        @Volatile
        var activeLoudnessBoostMb: Int = 0
            private set

        @Volatile
        var activeSeekStepSec: Int = 15
            private set

        @Volatile
        private var activeService: AudiobookPlayerService? = null

        fun applyDspPreset(preset: DspPreset) {
            activeDspPreset = preset
            activeService?.mainHandler?.post {
                activeService?.applyDacAndDspInternal(flushAudioTrack = false)
            }
        }

        fun applySkipSilence(enabled: Boolean) {
            activeSkipSilence = enabled
            activeService?.mainHandler?.post {
                activeService?.applyDacAndDspInternal(flushAudioTrack = false)
            }
        }

        fun applyDacConfig(mode: DacOutputMode, preferredDeviceId: Int?) {
            val changed = (activeDacOutputMode != mode || activeDacPreferredDeviceId != preferredDeviceId)
            activeDacOutputMode = mode
            activeDacPreferredDeviceId = preferredDeviceId
            activeService?.mainHandler?.post {
                activeService?.applyDacAndDspInternal(flushAudioTrack = changed)
            }
        }

        fun applyLoudnessBoostDb(db: Int) {
            activeLoudnessBoostMb = db.coerceIn(0, 12) * 100
            activeService?.mainHandler?.post {
                activeService?.applyDacAndDspInternal(flushAudioTrack = false)
            }
        }

        fun applySeekStepSec(sec: Int) {
            val safe = when (sec) {
                10, 15, 30 -> sec
                else -> 15
            }
            activeSeekStepSec = safe
            activeService?.mainHandler?.post {
                activeService?.let { svc ->
                    svc.mediaSession?.setCustomLayout(svc.buildCustomButtons())
                }
            }
        }

        fun buildMediaItem(track: AudioTrack, streamUriOrPath: String): MediaItem {
            val coverUri = track.coverPath?.takeIf { it.isNotBlank() }?.let { path ->
                val f = File(path)
                if (f.exists()) Uri.fromFile(f) else null
            }
            val metadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.author)
                .setAlbumTitle(track.channelTitle)
                .apply {
                    if (coverUri != null) setArtworkUri(coverUri)
                }
                .build()

            return MediaItem.Builder()
                .setMediaId(track.trackId)
                .setUri(streamUriOrPath)
                .setMediaMetadata(metadata)
                .build()
        }
    }
}
