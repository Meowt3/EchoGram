package com.echogram.app.data.storage

import android.content.Context
import com.echogram.app.data.model.AudioBookmark
import com.echogram.app.data.model.AudioTrack
import com.echogram.app.data.model.DacOutputMode
import com.echogram.app.data.model.DspPreset
import com.echogram.app.data.model.ThemeMode
import org.json.JSONArray
import org.json.JSONObject

/**
 * Clean local persistence for audiobook positions, starred Telegram sources,
 * favorites, bookmarks, completed tracks, and player/UI preferences.
 */
class LibraryStore(context: Context) {

    private val prefs = context.getSharedPreferences("echogram_v2_store", Context.MODE_PRIVATE)

    fun loadThemeMode(): ThemeMode = ThemeMode.fromWire(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.wire))
    fun saveThemeMode(mode: ThemeMode) = prefs.edit().putString(KEY_THEME, mode.wire).apply()

    fun loadPlaybackSpeed(): Float = prefs.getFloat(KEY_SPEED, 1.0f).takeIf { it.isFinite() }?.coerceIn(0.5f, 3.0f) ?: 1.0f
    fun savePlaybackSpeed(speed: Float) = prefs.edit().putFloat(KEY_SPEED, speed.coerceIn(0.5f, 3.0f)).apply()

    fun loadSkipSilence(): Boolean = prefs.getBoolean(KEY_SKIP_SILENCE, false)
    fun saveSkipSilence(enabled: Boolean) = prefs.edit().putBoolean(KEY_SKIP_SILENCE, enabled).apply()

    fun loadSeekStepSec(): Int = prefs.getInt(KEY_SEEK_STEP, 15).let { if (it in listOf(10, 15, 30)) it else 15 }
    fun saveSeekStepSec(sec: Int) = prefs.edit().putInt(KEY_SEEK_STEP, sec).apply()

    fun loadSmartRewind(): Boolean = prefs.getBoolean(KEY_SMART_REWIND, true)
    fun saveSmartRewind(enabled: Boolean) = prefs.edit().putBoolean(KEY_SMART_REWIND, enabled).apply()

    fun loadDspPreset(): DspPreset = DspPreset.fromWire(prefs.getString(KEY_DSP, DspPreset.STANDARD.wire))
    fun saveDspPreset(preset: DspPreset) = prefs.edit().putString(KEY_DSP, preset.wire).apply()

    fun loadLoudnessBoostDb(): Int = prefs.getInt(KEY_LOUDNESS_DB, 0).coerceIn(0, 12)
    fun saveLoudnessBoostDb(db: Int) = prefs.edit().putInt(KEY_LOUDNESS_DB, db.coerceIn(0, 12)).apply()

    fun loadDacMode(): DacOutputMode = DacOutputMode.fromWire(prefs.getString(KEY_DAC_MODE, DacOutputMode.AUTO.wire))
    fun saveDacMode(mode: DacOutputMode) = prefs.edit().putString(KEY_DAC_MODE, mode.wire).apply()

    fun loadPreferredDacId(): Int? = prefs.getInt(KEY_DAC_ID, -1).takeIf { it > 0 }
    fun savePreferredDacId(id: Int?) = prefs.edit().putInt(KEY_DAC_ID, id ?: -1).apply()

    fun loadStarredChatIds(): Set<Long> {
        val raw = prefs.getStringSet(KEY_STARRED_CHATS, emptySet()).orEmpty()
        return raw.mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun saveStarredChatIds(ids: Set<Long>) {
        prefs.edit().putStringSet(KEY_STARRED_CHATS, ids.map { it.toString() }.toSet()).apply()
    }

    fun loadTrackPositions(): MutableMap<String, Long> {
        val out = LinkedHashMap<String, Long>()
        val raw = prefs.getString(KEY_POSITIONS, null) ?: return out
        runCatching {
            val obj = JSONObject(raw)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.optLong(k, 0L)
                if (v > 0L) out[k] = v
            }
        }
        return out
    }

    fun saveTrackPositions(positions: Map<String, Long>) {
        val obj = JSONObject()
        positions.entries.take(500).forEach { (k, v) ->
            if (v > 0L) obj.put(k, v)
        }
        prefs.edit().putString(KEY_POSITIONS, obj.toString()).apply()
    }

    fun loadLastTrack(): AudioTrack? {
        val raw = prefs.getString(KEY_LAST_TRACK, null) ?: return null
        return runCatching { decodeTrack(JSONObject(raw)) }.getOrNull()
    }

    fun saveLastTrack(track: AudioTrack?, positionMs: Long) {
        val editor = prefs.edit().putLong(KEY_LAST_POS, positionMs.coerceAtLeast(0L))
        if (track == null) {
            editor.remove(KEY_LAST_TRACK)
        } else {
            editor.putString(KEY_LAST_TRACK, encodeTrack(track).toString())
        }
        editor.apply()
    }

    fun loadLastPositionMs(): Long = prefs.getLong(KEY_LAST_POS, 0L).coerceAtLeast(0L)

    fun loadFavorites(): List<AudioTrack> = loadTrackList(KEY_FAVORITES)
    fun saveFavorites(list: List<AudioTrack>) = saveTrackList(KEY_FAVORITES, list)

    fun loadCompletedIds(): Set<String> = prefs.getStringSet(KEY_COMPLETED, emptySet()).orEmpty().toSet()
    fun saveCompletedIds(ids: Set<String>) = prefs.edit().putStringSet(KEY_COMPLETED, ids).apply()

    fun loadBookmarks(): List<AudioBookmark> {
        val raw = prefs.getString(KEY_BOOKMARKS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        AudioBookmark(
                            id = o.optString("id"),
                            trackId = o.optString("trackId"),
                            trackTitle = o.optString("trackTitle"),
                            positionSec = o.optLong("positionSec", 0L),
                            note = o.optString("note"),
                            createdAtMs = o.optLong("createdAtMs", 0L)
                        )
                    )
                }
            }.distinctBy { it.id }
        }.getOrDefault(emptyList())
    }

    fun saveBookmarks(list: List<AudioBookmark>) {
        val arr = JSONArray()
        list.distinctBy { it.id }.forEach { b ->
            arr.put(
                JSONObject()
                    .put("id", b.id)
                    .put("trackId", b.trackId)
                    .put("trackTitle", b.trackTitle)
                    .put("positionSec", b.positionSec)
                    .put("note", b.note)
                    .put("createdAtMs", b.createdAtMs)
            )
        }
        prefs.edit().putString(KEY_BOOKMARKS, arr.toString()).apply()
    }

    fun clearSessionSelections() {
        prefs.edit().remove(KEY_STARRED_CHATS).apply()
    }

    private fun loadTrackList(key: String): List<AudioTrack> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { add(decodeTrack(it)) }
                }
            }.distinctBy { it.trackId }
        }.getOrDefault(emptyList())
    }

    private fun saveTrackList(key: String, tracks: List<AudioTrack>) {
        val arr = JSONArray()
        tracks.distinctBy { it.trackId }.forEach { arr.put(encodeTrack(it)) }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    companion object {
        private const val KEY_THEME = "theme_mode"
        private const val KEY_SPEED = "playback_speed"
        private const val KEY_SKIP_SILENCE = "skip_silence"
        private const val KEY_SEEK_STEP = "seek_step_sec"
        private const val KEY_SMART_REWIND = "smart_rewind"
        private const val KEY_DSP = "dsp_preset"
        private const val KEY_LOUDNESS_DB = "loudness_db"
        private const val KEY_DAC_MODE = "dac_mode"
        private const val KEY_DAC_ID = "dac_preferred_id"
        private const val KEY_STARRED_CHATS = "starred_chat_ids"
        private const val KEY_POSITIONS = "track_positions_json"
        private const val KEY_LAST_TRACK = "last_track_json"
        private const val KEY_LAST_POS = "last_pos_ms"
        private const val KEY_FAVORITES = "favorites_json"
        private const val KEY_COMPLETED = "completed_ids"
        private const val KEY_BOOKMARKS = "bookmarks_json"

        fun encodeTrack(t: AudioTrack): JSONObject = JSONObject()
            .put("trackId", t.trackId)
            .put("chatId", t.chatId)
            .put("messageId", t.messageId)
            .put("fileId", t.fileId)
            .put("title", t.title)
            .put("author", t.author)
            .put("channelTitle", t.channelTitle)
            .put("channelUsername", t.channelUsername)
            .put("durationSec", t.durationSec)
            .put("fileSizeBytes", t.fileSizeBytes)
            .put("fileName", t.fileName)
            .put("mimeType", t.mimeType)
            .put("dateEpochSec", t.dateEpochSec)
            .put("coverFileId", t.coverFileId)
            .put("coverPath", t.coverPath ?: "")
            .put("caption", t.caption)

        fun decodeTrack(o: JSONObject): AudioTrack = AudioTrack(
            trackId = o.optString("trackId"),
            chatId = o.optLong("chatId"),
            messageId = o.optLong("messageId"),
            fileId = o.optInt("fileId"),
            title = o.optString("title", "Аудиокнига"),
            author = o.optString("author", ""),
            channelTitle = o.optString("channelTitle", ""),
            channelUsername = o.optString("channelUsername", ""),
            durationSec = o.optLong("durationSec", 0L),
            fileSizeBytes = o.optLong("fileSizeBytes", 0L),
            fileName = o.optString("fileName", ""),
            mimeType = o.optString("mimeType", ""),
            dateEpochSec = o.optLong("dateEpochSec", 0L),
            coverFileId = o.optInt("coverFileId", 0),
            coverPath = o.optString("coverPath", "").takeIf { it.isNotBlank() },
            caption = o.optString("caption", "")
        )
    }
}
