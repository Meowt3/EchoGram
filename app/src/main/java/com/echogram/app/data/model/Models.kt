package com.echogram.app.data.model

enum class AppTab(val title: String) {
    CHATS("Чаты"),
    LIBRARY("Аудиотека"),
    SEARCH("Поиск"),
    SETTINGS("Настройки")
}

enum class ChatKind {
    SAVED_MESSAGES,
    CHANNEL,
    GROUP,
    BOT,
    USER
}

enum class ConnectionStatus(val label: String) {
    WAITING_NETWORK("Ожидание сети"),
    CONNECTING_PROXY("Подключение к прокси"),
    CONNECTING("Подключение..."),
    UPDATING("Обновление..."),
    READY("В сети")
}

sealed interface AuthState {
    data object Initializing : AuthState
    data object WaitingApiCredentials : AuthState
    data object WaitingPhoneNumber : AuthState
    data class WaitingQrConfirmation(val link: String) : AuthState
    data class WaitingCode(val phoneNumber: String, val codeTypeHint: String = "") : AuthState
    data class WaitingEmailAddress(val allowAppleId: Boolean = false) : AuthState
    data class WaitingEmailCode(val emailPattern: String = "") : AuthState
    data object WaitingRegistration : AuthState
    data class WaitingPassword(val hint: String = "") : AuthState
    data class Authorized(
        val userId: Long = 0L,
        val displayName: String = "Telegram",
        val username: String = "",
        val phoneMasked: String = "",
        val avatarPath: String? = null
    ) : AuthState
}

data class TelegramChat(
    val chatId: Long,
    val title: String,
    val username: String = "",
    val kind: ChatKind = ChatKind.CHANNEL,
    val lastMessagePreview: String = "",
    val lastMessageDateSec: Long = 0L,
    val unreadCount: Int = 0,
    val isArchived: Boolean = false,
    val isStarredAudiobookSource: Boolean = false,
    val avatarFileId: Int = 0,
    val avatarPath: String? = null,
    val memberCount: Int = 0
)

data class AudioTrack(
    val trackId: String,
    val chatId: Long,
    val messageId: Long,
    val fileId: Int,
    val title: String,
    val author: String,
    val channelTitle: String,
    val channelUsername: String = "",
    val durationSec: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val fileName: String = "",
    val mimeType: String = "",
    val dateEpochSec: Long = 0L,
    val coverFileId: Int = 0,
    val coverPath: String? = null,
    val caption: String = ""
) {
    val streamUri: String
        get() = "tdlib://$fileId?chatId=$chatId&msgId=$messageId&trackId=$trackId"
}

data class ChatMessage(
    val messageId: Long,
    val chatId: Long,
    val senderName: String,
    val isOutgoing: Boolean,
    val dateEpochSec: Long,
    val text: String,
    val mediaLabel: String = "",
    val photoFileId: Int = 0,
    val photoPath: String? = null,
    val audioTrack: AudioTrack? = null
)

data class AudioChapter(
    val id: String,
    val title: String,
    val startSec: Long
)

data class AudioBookmark(
    val id: String,
    val trackId: String,
    val trackTitle: String,
    val positionSec: Long,
    val note: String,
    val createdAtMs: Long
)

data class BookSeries(
    val id: String,
    val title: String,
    val author: String,
    val channelTitle: String,
    val coverPath: String?,
    val tracks: List<AudioTrack>,
    val completedCount: Int
)

enum class ProxyType(val label: String) {
    MTPROTO("MTProto"),
    SOCKS5("SOCKS5"),
    HTTP("HTTP")
}

data class ProxyItem(
    val id: Int,
    val server: String,
    val port: Int,
    val type: ProxyType,
    val isEnabled: Boolean,
    val pingMs: Int? = null,
    val pingError: String? = null
)

data class ParsedProxy(
    val type: ProxyType,
    val server: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
    val secret: String = ""
)

enum class DacOutputMode(val wire: String, val label: String) {
    AUTO("auto", "Авто (система)"),
    HI_RES_USB("hi_res", "USB ЦАП"),
    BIT_PERFECT("bit_perfect", "Bit-Perfect (без DSP)");

    companion object {
        fun fromWire(value: String?): DacOutputMode =
            entries.firstOrNull { it.wire == value } ?: AUTO
    }
}

data class DacDevice(
    val id: Int,
    val name: String,
    val typeLabel: String,
    val isUsbDac: Boolean,
    val maxBitDepth: Int,
    val sampleRatesHz: List<Int>,
    val supportsBitPerfect: Boolean
)

data class StreamFormatInfo(
    val codec: String = "",
    val sampleRateHz: Int = 0,
    val bitrateBps: Int = 0,
    val channelCount: Int = 0
) {
    fun summary(): String {
        if (codec.isBlank() && sampleRateHz <= 0) return ""
        val parts = ArrayList<String>(3)
        if (codec.isNotBlank()) parts += codec
        if (sampleRateHz > 0) parts += "${sampleRateHz / 1000.0} кГц".replace(".0 ", " ")
        if (bitrateBps > 0) parts += "${bitrateBps / 1000} кбит/с"
        return parts.joinToString(" · ")
    }
}

enum class DspPreset(val wire: String, val label: String) {
    STANDARD("standard", "Стандарт"),
    VOICE("voice", "Чёткий голос"),
    NIGHT("night", "Ночной режим"),
    BASS("bass", "Глубокий бас");

    companion object {
        fun fromWire(value: String?): DspPreset =
            entries.firstOrNull { it.wire == value } ?: STANDARD
    }
}

enum class ThemeMode(val wire: String, val label: String) {
    SYSTEM("system", "Системная"),
    LIGHT("light", "Светлая"),
    DARK("dark", "Тёмная"),
    AMOLED("amoled", "AMOLED");

    companion object {
        fun fromWire(value: String?): ThemeMode =
            entries.firstOrNull { it.wire == value } ?: SYSTEM
    }
}

sealed interface SleepTimerState {
    data object Off : SleepTimerState
    data class Countdown(val remainingSec: Int) : SleepTimerState
    data object EndOfTrack : SleepTimerState
}

data class CacheStats(
    val tdlibBytes: Long = 0L,
    val offlineBytes: Long = 0L,
    val coversBytes: Long = 0L
) {
    val totalBytes: Long get() = tdlibBytes + offlineBytes + coversBytes
}
