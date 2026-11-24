package com.echogram.app.player

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
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
import com.echogram.app.MainActivity
import com.echogram.app.data.model.AudioChapter
import com.echogram.app.data.model.AudioTrack
import com.echogram.app.data.model.DacOutputMode
import com.echogram.app.data.model.DspPreset
import com.echogram.app.data.model.StreamFormatInfo
import com.echogram.app.data.telegram.TelegramClientManager
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@OptIn(UnstableApi::class)
class AudiobookPlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var forwardingPlayer: ForwardingPlayer
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var dacManager: DacAudioManager

    private var equalizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private val rewindCmd = SessionCommand(ACTION_REWIND, Bundle.EMPTY)
    private val forwardCmd = SessionCommand(ACTION_FORWARD, Bundle.EMPTY)

    override fun onCreate() {
        super.onCreate()
        activeService = this
        dacManager = DacAudioManager.getInstance(applicationContext)

        val telegram = TelegramClientManager.getInstance(applicationContext)
        val dataSourceFactory = TdlibAudioDataSource.Factory(applicationContext, telegram)
        val extractorsFactory = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(1))

        val renderersFactory = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
            .setEnableAudioFloatOutput(false)
            .setEnableAudioTrackPlaybackParams(false)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 60_000, 1_500, 3_000)
            .build()

        player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(dacManager.buildMedia3AudioAttributes(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(activeSeekStepSec * 1000L)
            .setSeekForwardIncrementMs(activeSeekStepSec * 1000L)
            .build()

        dacManager.onDevicesChanged = {
            mainHandler.post { applyDacAndDspInternal() }
        }
        applyDacAndDspInternal()

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId > 0) {
                    bindAudioEffects(audioSessionId)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId.orEmpty()
                if (_embeddedChapters.value.first != id) {
                    _embeddedChapters.value = id to emptyList()
                }
                if (id.isEmpty()) {
                    dacManager.updateStreamFormat(StreamFormatInfo())
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                var format: androidx.media3.common.Format? = null
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_AUDIO && group.isSelected) {
                        for (i in 0 until group.length) {
                            if (group.isTrackSelected(i)) {
                                format = group.getTrackFormat(i)
                                break
                            }
                        }
                    }
                    if (format != null) break
                }
                if (format == null) {
                    dacManager.updateStreamFormat(StreamFormatInfo())
                } else {
                    val mime = format.sampleMimeType.orEmpty().lowercase()
                    val codec = when {
                        mime.contains("flac") -> "FLAC"
                        mime.contains("opus") -> "Opus"
                        mime.contains("vorbis") -> "Ogg"
                        mime.contains("mp4a") || mime.contains("aac") -> "AAC/M4B"
                        mime.contains("mpeg") -> "MP3"
                        mime.contains("wav") || mime.contains("raw") -> "PCM"
                        else -> mime.substringAfter("/").uppercase()
                    }
                    dacManager.updateStreamFormat(
                        StreamFormatInfo(
                            codec = codec,
                            sampleRateHz = format.sampleRate.coerceAtLeast(0),
                            bitrateBps = format.bitrate.coerceAtLeast(0),
                            channelCount = format.channelCount.coerceAtLeast(0)
                        )
                    )
                    if (activeDacMode == DacOutputMode.BIT_PERFECT) {
                        applyDacAndDspInternal()
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
                            id = "ch_${mediaId}_${entry.startTimeMs}",
                            title = title,
                            startSec = (entry.startTimeMs / 1000L).coerceAtLeast(0L)
                        )
                    }
                }
                if (extracted.isNotEmpty()) {
                    val prev = if (_embeddedChapters.value.first == mediaId) _embeddedChapters.value.second else emptyList()
                    _embeddedChapters.value = mediaId to (prev + extracted).distinctBy { it.startSec }.sortedBy { it.startSec }
                }
            }
        })

        // IMPORTANT: Do NOT override addListener via Kotlin `by` delegation on Player.Listener
        forwardingPlayer = object : ForwardingPlayer(player) {
            override fun getAvailableCommands(): Player.Commands =
                super.getAvailableCommands().buildUpon()
                    .add(COMMAND_SEEK_TO_PREVIOUS)
                    .add(COMMAND_SEEK_TO_NEXT)
                    .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(COMMAND_SEEK_BACK)
                    .add(COMMAND_SEEK_FORWARD)
                    .build()

            override fun isCommandAvailable(command: Int): Boolean = when (command) {
                COMMAND_SEEK_TO_PREVIOUS,
                COMMAND_SEEK_TO_NEXT,
                COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                COMMAND_SEEK_BACK,
                COMMAND_SEEK_FORWARD -> true
                else -> super.isCommandAvailable(command)
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
                if (currentPosition > 5_000L) seekTo(0L) else mainHandler.post { onSkipPreviousRequested?.invoke() }
            }

            override fun seekToPreviousMediaItem() {
                if (currentPosition > 5_000L) seekTo(0L) else mainHandler.post { onSkipPreviousRequested?.invoke() }
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
                    .add(rewindCmd)
                    .add(forwardCmd)
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
                    ACTION_REWIND -> forwardingPlayer.seekBack()
                    ACTION_FORWARD -> forwardingPlayer.seekForward()
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(sessionActivity)
            .setCallback(callback)
            .build()
            .also { it.setCustomLayout(buildCustomButtons()) }
    }

    private fun buildCustomButtons(): List<CommandButton> = listOf(
        CommandButton.Builder()
            .setDisplayName("-${activeSeekStepSec}с")
            .setSessionCommand(rewindCmd)
            .setIconResId(android.R.drawable.ic_media_rew)
            .build(),
        CommandButton.Builder()
            .setDisplayName("+${activeSeekStepSec}с")
            .setSessionCommand(forwardCmd)
            .setIconResId(android.R.drawable.ic_media_ff)
            .build()
    )

    private fun bindAudioEffects(audioSessionId: Int) {
        releaseAudioEffects()
        if (activeDacMode == DacOutputMode.BIT_PERFECT) return
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId <= 0) return

        val needEq = activeDspPreset != DspPreset.STANDARD
        val nightMb = if (activeDspPreset == DspPreset.NIGHT) 450 else 0
        val targetMb = maxOf(nightMb, activeLoudnessMb.coerceIn(0, 1200))

        if (needEq) {
            runCatching {
                equalizer = Equalizer(0, audioSessionId).apply {
                    applyEqPreset(this, activeDspPreset)
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

    private fun applyEqPreset(eq: Equalizer, preset: DspPreset) {
        if (preset == DspPreset.STANDARD) {
            eq.enabled = false
            return
        }
        val bands = eq.numberOfBands.toInt().coerceAtLeast(1)
        val range = eq.bandLevelRange
        val minL = range.getOrNull(0)?.toInt() ?: -1500
        val maxL = range.getOrNull(1)?.toInt() ?: 1500
        for (b in 0 until bands) {
            val hz = runCatching { eq.getCenterFreq(b.toShort()) / 1000 }.getOrDefault(1000)
            val gain = when (preset) {
                DspPreset.STANDARD -> 0
                DspPreset.VOICE -> when {
                    hz < 220 -> -450
                    hz in 900..4200 -> 550
                    else -> 200
                }
                DspPreset.NIGHT -> when {
                    hz < 200 -> -250
                    hz in 800..3200 -> 250
                    hz > 5000 -> -300
                    else -> 0
                }
                DspPreset.BASS -> when {
                    hz < 260 -> 600
                    hz in 260..1200 -> 150
                    else -> -100
                }
            }
            eq.setBandLevel(b.toShort(), gain.coerceIn(minL, maxL).toShort())
        }
        eq.enabled = true
    }

    private fun applyDacAndDspInternal() {
        val bypass = activeDacMode == DacOutputMode.BIT_PERFECT
        if (::dacManager.isInitialized) {
            val target = dacManager.resolveTargetDevice(activeDacMode, activePreferredDacId)
            if (::player.isInitialized) {
                runCatching { player.setPreferredAudioDevice(target) }
            }
            dacManager.applyMixerRouting(target, activeDacMode)
        }
        if (::player.isInitialized) {
            player.skipSilenceEnabled = if (bypass) false else activeSkipSilence
            if (bypass) {
                releaseAudioEffects()
            } else if (player.audioSessionId > 0) {
                bindAudioEffects(player.audioSessionId)
            }
        }
    }

    private fun releaseAudioEffects() {
        runCatching { equalizer?.release() }
        equalizer = null
        runCatching { loudnessEnhancer?.release() }
        loudnessEnhancer = null
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = mediaSession?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0 || p.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        if (activeService === this) activeService = null
        if (::dacManager.isInitialized) {
            dacManager.onDevicesChanged = null
            dacManager.clearMixerOverride()
            dacManager.updateStreamFormat(StreamFormatInfo())
        }
        _embeddedChapters.value = "" to emptyList()
        mainHandler.removeCallbacksAndMessages(null)
        releaseAudioEffects()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_REWIND = "com.echogram.app.action.REWIND"
        const val ACTION_FORWARD = "com.echogram.app.action.FORWARD"

        @Volatile var onSkipNextRequested: (() -> Unit)? = null
        @Volatile var onSkipPreviousRequested: (() -> Unit)? = null

        private val _embeddedChapters = MutableStateFlow<Pair<String, List<AudioChapter>>>("" to emptyList())
        val embeddedChapters: StateFlow<Pair<String, List<AudioChapter>>> = _embeddedChapters.asStateFlow()

        @Volatile private var activeService: AudiobookPlayerService? = null
        @Volatile private var activeDspPreset: DspPreset = DspPreset.STANDARD
        @Volatile private var activeSkipSilence: Boolean = false
        @Volatile private var activeDacMode: DacOutputMode = DacOutputMode.AUTO
        @Volatile private var activePreferredDacId: Int? = null
        @Volatile private var activeLoudnessMb: Int = 0
        @Volatile private var activeSeekStepSec: Int = 15

        fun applyDspPreset(preset: DspPreset) {
            activeDspPreset = preset
            activeService?.mainHandler?.post { activeService?.applyDacAndDspInternal() }
        }

        fun applySkipSilence(enabled: Boolean) {
            activeSkipSilence = enabled
            activeService?.mainHandler?.post { activeService?.applyDacAndDspInternal() }
        }

        fun applyDacConfig(mode: DacOutputMode, deviceId: Int?) {
            activeDacMode = mode
            activePreferredDacId = deviceId
            activeService?.mainHandler?.post { activeService?.applyDacAndDspInternal() }
        }

        fun applyLoudnessBoostDb(db: Int) {
            activeLoudnessMb = db.coerceIn(0, 12) * 100
            activeService?.mainHandler?.post { activeService?.applyDacAndDspInternal() }
        }

        fun applySeekStepSec(sec: Int) {
            activeSeekStepSec = if (sec in listOf(10, 15, 30)) sec else 15
            activeService?.mainHandler?.post {
                activeService?.let { svc ->
                    svc.mediaSession?.setCustomLayout(svc.buildCustomButtons())
                }
            }
        }

        fun buildMediaItem(track: AudioTrack): MediaItem {
            val coverUri = track.coverPath?.takeIf { it.isNotBlank() }?.let { path ->
                File(path).takeIf { it.exists() }?.let { Uri.fromFile(it) }
            }
            val meta = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.author.ifBlank { track.channelTitle })
                .setAlbumTitle(track.channelTitle)
                .apply { if (coverUri != null) setArtworkUri(coverUri) }
                .build()
            return MediaItem.Builder()
                .setMediaId(track.trackId)
                .setUri(track.streamUri)
                .setMediaMetadata(meta)
                .build()
        }
    }
}
