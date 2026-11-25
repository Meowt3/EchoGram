package com.tgaudiobooks.app.data.model

enum class AppTab(val title: String) {
    HOME("Главная"),
    CATEGORIES("Категории"),
    SEARCH("Поиск"),
    LIBRARY("Библиотека")
}

enum class LibrarySubTab(val title: String) {
    FAVORITES("Избранное"),
    PLAYLISTS("Плейлисты"),
    DOWNLOADS("Скачанное"),
    SERIES("Серии"),
    STATS("Статистика"),
    CHANNELS("Источники")
}

enum class ThemeMode(val wireValue: String) {
    LIGHT("light"),
    DARK("dark"),
    SYSTEM("system");

    companion object {
        fun fromWire(value: String?): ThemeMode =
            entries.firstOrNull { it.wireValue == value } ?: SYSTEM
    }
}

enum class ProgressBarStyle(
    val wireValue: String,
    val label: String,
    val hint: String
) {
    WAVE("wave", "Волна", "Живая синусоида в стиле Material 3"),
    GLOW("glow", "Свечение", "Градиент с бегущим бликом"),
    SEGMENTS("segments", "Импульсы", "Пульсирующие сегменты ритма"),
    EQUALIZER("equalizer", "Эквалайзер", "Зеркальный аудиоспектр полос"),
    NEON("neon", "Комета", "Неоновый луч с искрой прогресса"),
    DOTS("dots", "Пунктир", "Волна из световых бусин"),
    CLASSIC("classic", "Классика", "Чёткая минималистичная линия");

    companion object {
        fun fromWire(value: String?): ProgressBarStyle =
            entries.firstOrNull { it.wireValue == value } ?: WAVE
    }
}

enum class DspPreset(
    val wireValue: String,
    val label: String,
    val hint: String
) {
    STANDARD("standard", "Стандарт", "Ровное оригинальное звучание"),
    VOICE("voice", "Чёткая речь", "Подъём разборчивости голоса чтеца"),
    NIGHT("night", "Ночной режим", "Мягкий компрессор без резких пиков"),
    BASS("bass", "Глубокий бас", "Тёплый бархатный тембр");

    companion object {
        fun fromWire(value: String?): DspPreset =
            entries.firstOrNull { it.wireValue == value } ?: STANDARD
    }
}

enum class DacOutputMode(
    val wireValue: String,
    val label: String,
    val hint: String
) {
    AUTO("auto", "Авто (система)", "Стандартный вывод Android через системный микшер"),
    USB_HIRES("usb_hires", "USB ЦАП Hi-Res", "Приоритет внешнего ЦАП, 32-bit Float PCM и обход речевых фильтров"),
    BIT_PERFECT("bit_perfect", "Bit-Perfect (Direct)", "Прямой вывод без DSP/EQ и с обходом микшера на Android 14+");

    companion object {
        fun fromWire(value: String?): DacOutputMode =
            entries.firstOrNull { it.wireValue == value } ?: AUTO
    }
}

data class DacDeviceInfo(
    val id: Int,
    val name: String,
    val typeLabel: String,
    val isUsbDac: Boolean,
    val sampleRatesHz: List<Int>,
    val maxBitDepth: Int,
    val supportsFloatPcm: Boolean,
    val supportsBitPerfectMixer: Boolean
)

data class AudioStreamFormatInfo(
    val codec: String = "",
    val sampleRateHz: Int = 0,
    val bitrateBps: Int = 0,
    val channelCount: Int = 0
)

enum class TelegramConnectionStatus(val label: String) {
    WAITING_NETWORK("Ожидание сети"),
    CONNECTING_PROXY("Подключение к прокси"),
    CONNECTING("Подключение..."),
    UPDATING("Обновление..."),
    READY("В сети")
}

sealed class TelegramAuthState {
    data object Idle : TelegramAuthState()
    data object WaitingApiCredentials : TelegramAuthState()
    data object WaitingPhoneNumber : TelegramAuthState()
    data class WaitingQrConfirmation(val link: String) : TelegramAuthState()
    data class WaitingCode(val phoneNumber: String) : TelegramAuthState()
    data object WaitingEmailAddress : TelegramAuthState()
    data class WaitingEmailCode(val emailPattern: String) : TelegramAuthState()
    data object WaitingRegistration : TelegramAuthState()
    data class WaitingPassword(val hint: String) : TelegramAuthState()
    data class Connected(val userName: String, val phoneMasked: String) : TelegramAuthState()
    data class Error(val message: String) : TelegramAuthState()
}

enum class SourceType(val label: String) {
    CHANNEL("Канал"),
    GROUP("Чат"),
    BOT("Бот"),
    USER("Личный чат")
}

data class TelegramChannel(
    val channelId: String,
    val title: String,
    val username: String = "",
    val sourceType: SourceType = SourceType.CHANNEL,
    val isSelected: Boolean = false,
    val trackCount: Int = 0
)

data class AudioTrack(
    val trackId: String,
    val channelId: String,
    val channelTitle: String,
    val channelUsername: String = "",
    val messageId: Long,
    val fileId: Int = 0,
    val title: String,
    val author: String,
    val description: String = "",
    val durationSec: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val mimeType: String = "audio/mpeg",
    val coverPath: String? = null,
    val coverFileId: Int = 0,
    val coverMaxDim: Int = 0,
    val dateEpoch: Long = 0L,
    val recommendationReason: String = ""
)

data class AudioChapter(
    val id: String,
    val title: String,
    val startSec: Long
)

data class AudioBookmark(
    val bookmarkId: String,
    val trackId: String,
    val title: String,
    val positionSec: Long,
    val note: String = "",
    val createdAtMs: Long = System.currentTimeMillis()
)

data class BookSeriesGroup(
    val key: String,
    val title: String,
    val author: String,
    val coverPath: String?,
    val tracks: List<AudioTrack>,
    val totalDurationSec: Long,
    val completedCount: Int,
    val progressPercent: Int
)

data class DailyStatPoint(
    val dateKey: String,
    val label: String,
    val seconds: Long
)

data class ReadingSummary(
    val totalSeconds: Long = 0L,
    val todaySeconds: Long = 0L,
    val streakDays: Int = 0,
    val last7Days: List<DailyStatPoint> = emptyList(),
    val completedCount: Int = 0,
    val topAuthors: List<Pair<String, Long>> = emptyList()
)

data class UserPlaylist(
    val id: String,
    val name: String,
    val description: String = "",
    val items: List<AudioTrack> = emptyList()
)

data class MixCollection(
    val id: String,
    val title: String,
    val subtitle: String,
    val kicker: String = "",
    val description: String = "",
    val reason: String = "",
    val isAutoPlaylist: Boolean = false,
    val coverPath: String?,
    val tracks: List<AudioTrack>
)

enum class CategorySourceKind(val label: String) {
    HASHTAG("По хештегу"),
    META_TAG("Из описания"),
    PHRASE("Ключевая тема"),
    KEYWORD("По содержанию"),
    DURATION("По длительности"),
    OTHER("Разное")
}

data class DiscoveredCategory(
    val id: String,
    val title: String,
    val subtitle: String,
    val sourceKind: CategorySourceKind,
    val relatedTags: List<String> = emptyList(),
    val coverPath: String? = null,
    val tracks: List<AudioTrack>,
    val totalDurationSec: Long = 0L
)

data class PlaybackSignal(
    val trackId: String,
    val channelId: String,
    val author: String,
    val title: String,
    val durationSec: Long,
    val playedSec: Long,
    val playCount: Int,
    val skipCount: Int = 0,
    val lastPlayedAtMs: Long
)

sealed class SleepTimerState {
    data object Off : SleepTimerState()
    data class Countdown(val remainingSec: Int) : SleepTimerState()
    data object EndOfTrack : SleepTimerState()
}

enum class TelegramProxyType(val label: String) {
    SOCKS5("SOCKS5"),
    MTPROTO("MTProto"),
    HTTP("HTTP")
}

data class TelegramProxyItem(
    val id: Int,
    val type: TelegramProxyType,
    val server: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
    val secret: String = "",
    val isEnabled: Boolean = false,
    val lastUsedDate: Int = 0,
    val latencyMs: Int? = null,
    val pingError: String? = null
)

data class ParsedProxyLink(
    val type: TelegramProxyType,
    val server: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
    val secret: String = ""
)

data class StorageCacheStats(
    val audioBytes: Long = 0L,
    val audioCount: Int = 0,
    val coversBytes: Long = 0L,
    val coversCount: Int = 0,
    val otherBytes: Long = 0L,
    val otherCount: Int = 0,
    val databaseBytes: Long = 0L
) {
    val totalCacheBytes: Long
        get() = audioBytes + coversBytes + otherBytes
}

data class TelegramCloudSyncState(
    val isBusy: Boolean = false,
    val autoSyncEnabled: Boolean = true,
    val lastSyncedAtMs: Long = 0L,
    val statusMessage: String? = null,
    val error: String? = null
)

data class TelegramChatMessage(
    val messageId: Long,
    val chatId: Long,
    val senderName: String = "",
    val isOutgoing: Boolean = false,
    val dateEpochSec: Long = 0L,
    val text: String = "",
    val mediaLabel: String = "",
    val photoFileId: Int = 0,
    val photoPath: String? = null,
    val audioTrack: AudioTrack? = null,
    val views: Int = 0
)

data class TelegramChatTarget(
    val channelId: String,
    val title: String,
    val username: String = "",
    val sourceType: SourceType = SourceType.CHANNEL,
    val focusMessageId: Long = 0L
)

data class TelegramChatUiState(
    val target: TelegramChatTarget? = null,
    val messages: List<TelegramChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val isSending: Boolean = false,
    val canSendMessages: Boolean = false,
    val hasMoreOlder: Boolean = true,
    val error: String? = null
)


