package com.tgaudiobooks.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tgaudiobooks.app.data.model.AudioStreamFormatInfo
import com.tgaudiobooks.app.data.model.DacDeviceInfo
import com.tgaudiobooks.app.data.model.DacOutputMode
import com.tgaudiobooks.app.data.model.DspPreset
import com.tgaudiobooks.app.data.model.ParsedProxyLink
import com.tgaudiobooks.app.data.model.ProgressBarStyle
import com.tgaudiobooks.app.data.model.SourceType
import com.tgaudiobooks.app.data.model.StorageCacheStats
import com.tgaudiobooks.app.data.model.TelegramAuthState
import com.tgaudiobooks.app.data.model.TelegramChannel
import com.tgaudiobooks.app.data.model.TelegramCloudSyncState
import com.tgaudiobooks.app.data.model.TelegramProxyItem
import com.tgaudiobooks.app.data.model.TelegramProxyType
import com.tgaudiobooks.app.data.model.ThemeMode
import com.tgaudiobooks.app.data.telegram.TelegramDefaults
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import com.tgaudiobooks.app.ui.components.AnimatedProgressBarCanvas
import com.tgaudiobooks.app.ui.components.BubbleMotion
import com.tgaudiobooks.app.ui.theme.AppIconPreset
import com.tgaudiobooks.app.ui.theme.EchoGramBrandIcon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatSyncTimestamp(epochMs: Long): String {
    if (epochMs <= 0L) return ""
    return runCatching {
        SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(epochMs))
    }.getOrDefault("")
}

private enum class SettingsTab(val label: String, val icon: ImageVector) {
    APPEARANCE("Вид", Icons.Default.Palette),
    SOUND("Звук", Icons.Default.GraphicEq),
    ACCOUNT("Аккаунт", Icons.Default.Cloud),
    SOURCES("Источники", Icons.Default.Radio),
    NETWORK("Сеть", Icons.Default.Shield)
}

private fun formatStorageBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 КБ"
    if (bytes < 1024L) return "$bytes Б"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(if (kb >= 100.0) "%.0f КБ" else "%.1f КБ", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(if (mb >= 100.0) "%.0f МБ" else "%.1f МБ", mb)
    val gb = mb / 1024.0
    return String.format("%.2f ГБ", gb)
}

@Composable
fun SettingsScreen(
    cloudSyncState: TelegramCloudSyncState,
    onSyncCloudPush: () -> Unit,
    onSyncCloudPull: () -> Unit,
    onToggleAutoCloudSync: (Boolean) -> Unit,
    onBuildBackupJson: () -> String,
    onImportBackupJson: (String) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    progressBarStyle: ProgressBarStyle,
    onProgressBarStyleChange: (ProgressBarStyle) -> Unit,
    appIcon: AppIconPreset,
    onAppIconChange: (AppIconPreset) -> Unit,
    authState: TelegramAuthState,
    authError: String?,
    isAuthBusy: Boolean,
    isLoadingChannels: Boolean,
    channels: List<TelegramChannel>,
    onInitTdlib: (Int, String) -> Unit,
    onSubmitPhone: (String) -> Unit,
    onSubmitCode: (String) -> Unit,
    onResendCode: () -> Unit,
    onResetToPhone: () -> Unit,
    onSubmitPassword: (String) -> Unit,
    onLogout: () -> Unit,
    onToggleChannel: (TelegramChannel) -> Unit,
    onRefreshChannels: () -> Unit,
    proxies: List<TelegramProxyItem> = emptyList(),
    isProxyBusy: Boolean = false,
    proxyError: String? = null,
    proxyMessage: String? = null,
    onToggleUseProxy: (Boolean) -> Unit = {},
    onEnableProxy: (Int) -> Unit = {},
    onRemoveProxy: (Int) -> Unit = {},
    onPingProxy: (Int) -> Unit = {},
    onAddProxy: (TelegramProxyType, String, Int, String, String, String) -> Unit = { _, _, _, _, _, _ -> },
    onParseProxyLink: (String) -> ParsedProxyLink? = { null },
    cacheStats: StorageCacheStats = StorageCacheStats(),
    isCacheBusy: Boolean = false,
    cacheMessage: String? = null,
    cacheTtlDays: Int = 7,
    onRefreshCacheStats: () -> Unit = {},
    onClearCache: (Boolean, Boolean, Boolean) -> Unit = { _, _, _ -> },
    onCacheTtlChange: (Int) -> Unit = {},
    dacDevices: List<DacDeviceInfo> = emptyList(),
    dacOutputMode: DacOutputMode = DacOutputMode.AUTO,
    dacPreferredDeviceId: Int? = null,
    dacRoutingSummary: String = "Системный вывод Android",
    dacBitPerfectActive: Boolean = false,
    streamFormat: AudioStreamFormatInfo = AudioStreamFormatInfo(),
    onSelectDacOutputMode: (DacOutputMode) -> Unit = {},
    onSelectDacDevice: (Int?) -> Unit = {},
    onRefreshDacDevices: () -> Unit = {},
    dspPreset: DspPreset = DspPreset.STANDARD,
    skipSilence: Boolean = false,
    playbackSpeed: Float = 1.0f,
    loudnessBoostDb: Int = 0,
    seekStepSec: Int = 15,
    smartRewindEnabled: Boolean = true,
    onSelectDspPreset: (DspPreset) -> Unit = {},
    onToggleSkipSilence: (Boolean) -> Unit = {},
    onSelectPlaybackSpeed: (Float) -> Unit = {},
    onSelectLoudnessBoostDb: (Int) -> Unit = {},
    onSelectSeekStepSec: (Int) -> Unit = {},
    onToggleSmartRewind: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(SettingsTab.APPEARANCE) }

    var apiId by remember {
        val defId = TelegramDefaults.getApiId(context)
        mutableStateOf(if (defId > 0) defId.toString() else "")
    }
    var apiHash by remember {
        mutableStateOf(TelegramDefaults.getApiHash(context))
    }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var localValidationError by remember { mutableStateOf<String?>(null) }
    var sourceFilter by remember { mutableStateOf("all") }
    var sourceSearchQuery by remember { mutableStateOf("") }

    // Proxy form state
    var showAddProxy by remember { mutableStateOf(false) }
    var proxyLinkInput by remember { mutableStateOf("") }
    var proxyFormType by remember { mutableStateOf(TelegramProxyType.SOCKS5) }
    var proxyFormServer by remember { mutableStateOf("") }
    var proxyFormPort by remember { mutableStateOf("1080") }
    var proxyFormUser by remember { mutableStateOf("") }
    var proxyFormPass by remember { mutableStateOf("") }
    var proxyFormSecret by remember { mutableStateOf("") }

    // Cache cleanup checkboxes
    var clearAudioChecked by remember { mutableStateOf(true) }
    var clearCoversChecked by remember { mutableStateOf(true) }
    var clearOtherChecked by remember { mutableStateOf(true) }

    LaunchedEffect(activeTab) {
        when (activeTab) {
            SettingsTab.NETWORK -> onRefreshCacheStats()
            SettingsTab.SOUND -> onRefreshDacDevices()
            else -> Unit
        }
    }

    val visibleError = localValidationError ?: authError

    val filteredChannels = remember(channels, sourceFilter, sourceSearchQuery) {
        val q = sourceSearchQuery.trim().lowercase()
        channels.filter { ch ->
            val kindOk = when (sourceFilter) {
                "channel" -> ch.sourceType == SourceType.CHANNEL
                "bot" -> ch.sourceType == SourceType.BOT
                "chat" -> ch.sourceType == SourceType.GROUP || ch.sourceType == SourceType.USER
                else -> true
            }
            if (!kindOk) return@filter false
            if (q.isEmpty()) return@filter true
            ch.title.lowercase().contains(q) || ch.username.lowercase().contains(q)
        }
    }

    val selectedCount = remember(channels) { channels.count { it.isSelected } }
    val proxyEnabled = remember(proxies) { proxies.any { it.isEnabled } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
    ) {
        // Header & 4-Tab Bar (pinned top)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column {
                Text("Настройки", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = "Вид · Звук и ЦАП · Аккаунт · Источники · Сеть и кеш",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(4.dp)
                ) {
                    SettingsTab.entries.forEach { tab ->
                        val active = activeTab == tab
                        val bg by animateColorAsState(
                            targetValue = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                            animationSpec = tween(180),
                            label = "settings_tab_bg"
                        )
                        val fg by animateColorAsState(
                            targetValue = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = tween(180),
                            label = "settings_tab_fg"
                        )
                        val tabScale by animateFloatAsState(
                            targetValue = if (active) 1.04f else 0.98f,
                            animationSpec = BubbleMotion.bouncySpring(),
                            label = "settings_tab_scale"
                        )
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = bg,
                            contentColor = fg,
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer { scaleX = tabScale; scaleY = tabScale }
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { activeTab = tab }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp)
                            ) {
                                Icon(tab.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedContent(
            targetState = activeTab,
            transitionSpec = { BubbleMotion.bubbleContentTransform() },
            modifier = Modifier.weight(1f),
            label = "settings_tab_content"
        ) { tab ->
            when (tab) {
                SettingsTab.APPEARANCE -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Theme selector
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Оформление Material 3 (под обложку)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(6.dp)
                                    ) {
                                        ThemeMode.entries.forEach { mode ->
                                            val selected = themeMode == mode
                                            val (label, icon) = when (mode) {
                                                ThemeMode.LIGHT -> "Светлая" to Icons.Default.LightMode
                                                ThemeMode.DARK -> "Тёмная" to Icons.Default.DarkMode
                                                ThemeMode.SYSTEM -> "Авто" to Icons.Default.BrightnessAuto
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                                contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .clickable { onThemeModeChange(mode) }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center,
                                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp)
                                                ) {
                                                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        text = label,
                                                        style = MaterialTheme.typography.labelLarge,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 7 live progress bar styles (only active animates at 60fps; items rendered lazily)
                        item(key = "progress_styles_header") {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text("Анимация полоски прогресса", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        }
                                        Text(
                                            text = progressBarStyle.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = "Предпросмотр: выбранный стиль воспроизводит живую анимацию, остальные статичны для экономии батареи.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        items(ProgressBarStyle.entries, key = { "pb_style_${it.name}" }) { style ->
                            val active = progressBarStyle == style
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (active) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = if (active) 1.5.dp else 1.dp,
                                        color = if (active) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                        },
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onProgressBarStyleChange(style) }
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = style.label,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = style.hint,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (active) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Выбрано",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    AnimatedProgressBarCanvas(
                                        progress = 0.62f,
                                        style = style,
                                        isPlaying = active,
                                        interactive = false,
                                        showThumb = true,
                                        modifier = Modifier.fillMaxWidth().height(26.dp)
                                    )
                                }
                            }
                        }

                        // Telegram-style App Icon Picker
                        item {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Иконка приложения (Telegram)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(appIcon.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        AppIconPreset.entries.forEach { preset ->
                                            val selected = appIcon == preset
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = if (selected) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceContainerLow
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .border(
                                                        width = if (selected) 2.dp else 0.dp,
                                                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                        shape = RoundedCornerShape(16.dp)
                                                    )
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .clickable { onAppIconChange(preset) }
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp)
                                                ) {
                                                    EchoGramBrandIcon(preset = preset, size = 38.dp)
                                                    Text(
                                                        text = preset.label,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                SettingsTab.SOUND -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // USB DAC / External Audio Output
                        item {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Usb,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                            Column {
                                                Text(
                                                    text = "Интеграция с ЦАП (USB DAC)",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = dacRoutingSummary,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (dacOutputMode != DacOutputMode.AUTO || dacDevices.any { it.isUsbDac }) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        IconButton(onClick = onRefreshDacDevices) {
                                            Icon(Icons.Default.Refresh, contentDescription = "Обновить устройства")
                                        }
                                    }

                                    if (streamFormat.codec.isNotBlank() || streamFormat.sampleRateHz > 0) {
                                        val parts = buildList {
                                            if (streamFormat.codec.isNotBlank()) add(streamFormat.codec)
                                            if (streamFormat.sampleRateHz > 0) {
                                                val khz = streamFormat.sampleRateHz / 1000.0
                                                val khzText = if (khz % 1.0 == 0.0) "${khz.toInt()} кГц" else String.format("%.1f кГц", khz)
                                                add(khzText)
                                            }
                                            if (streamFormat.bitrateBps > 0) {
                                                add("${streamFormat.bitrateBps / 1000} кбит/с")
                                            }
                                            if (streamFormat.channelCount > 0) {
                                                add(
                                                    when (streamFormat.channelCount) {
                                                        1 -> "Моно"
                                                        2 -> "Стерео"
                                                        else -> "${streamFormat.channelCount} кан."
                                                    }
                                                )
                                            }
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                            ) {
                                                Text(
                                                    text = "Поток",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = parts.joinToString(" · "),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    // Output mode selector
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "Режим вывода звука",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        DacOutputMode.entries.forEach { mode ->
                                            val active = dacOutputMode == mode
                                            Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = if (active) {
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceContainerLow
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .border(
                                                        width = if (active) 1.5.dp else 1.dp,
                                                        color = if (active) {
                                                            MaterialTheme.colorScheme.primary
                                                        } else {
                                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                                        },
                                                        shape = RoundedCornerShape(14.dp)
                                                    )
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .clickable { onSelectDacOutputMode(mode) }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                                ) {
                                                    RadioButton(
                                                        selected = active,
                                                        onClick = { onSelectDacOutputMode(mode) }
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            Text(
                                                                text = mode.label,
                                                                style = MaterialTheme.typography.titleSmall,
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                            if (mode == DacOutputMode.BIT_PERFECT && active && dacBitPerfectActive) {
                                                                Text(
                                                                    text = "АКТИВЕН",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                        }
                                                        Text(
                                                            text = mode.hint,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Connected DAC / Audio devices list
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "Целевое устройство вывода",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = if (dacPreferredDeviceId == null) {
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerLow
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable { onSelectDacDevice(null) }
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                            ) {
                                                RadioButton(
                                                    selected = dacPreferredDeviceId == null,
                                                    onClick = { onSelectDacDevice(null) }
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "Автовыбор внешнего ЦАП",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = "Автоматически переключается на подключённый USB DAC",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        if (dacDevices.isEmpty()) {
                                            Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                    modifier = Modifier.padding(12.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Headphones,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        text = "Внешние USB ЦАП или проводные выходы не обнаружены. Подключите ЦАП по USB-C / OTG и нажмите «Обновить».",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        } else {
                                            dacDevices.forEach { dev ->
                                                val selected = dacPreferredDeviceId == dev.id
                                                val ratesText = if (dev.sampleRatesHz.isNotEmpty()) {
                                                    val minKhz = dev.sampleRatesHz.first() / 1000.0
                                                    val maxKhz = dev.sampleRatesHz.last() / 1000.0
                                                    val fmtMin = if (minKhz % 1.0 == 0.0) "${minKhz.toInt()}" else "$minKhz"
                                                    val fmtMax = if (maxKhz % 1.0 == 0.0) "${maxKhz.toInt()}" else "$maxKhz"
                                                    if (fmtMin == fmtMax) "$fmtMax кГц" else "$fmtMin–$fmtMax кГц"
                                                } else {
                                                    "до 48 кГц"
                                                }
                                                val depthText = buildString {
                                                    append("${dev.maxBitDepth}-bit")
                                                    if (dev.supportsFloatPcm) append(" Float")
                                                    append(" · $ratesText")
                                                    if (dev.supportsBitPerfectMixer) append(" · Bit-Perfect")
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(14.dp),
                                                    color = if (selected) {
                                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                                    } else {
                                                        MaterialTheme.colorScheme.surfaceContainerLow
                                                    },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .clickable {
                                                            onSelectDacDevice(dev.id)
                                                            if (dacOutputMode == DacOutputMode.AUTO) {
                                                                onSelectDacOutputMode(DacOutputMode.USB_HIRES)
                                                            }
                                                        }
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                                    ) {
                                                        RadioButton(
                                                            selected = selected,
                                                            onClick = {
                                                                onSelectDacDevice(dev.id)
                                                                if (dacOutputMode == DacOutputMode.AUTO) {
                                                                    onSelectDacOutputMode(DacOutputMode.USB_HIRES)
                                                                }
                                                            }
                                                        )
                                                        Spacer(Modifier.width(4.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = dev.name,
                                                                style = MaterialTheme.typography.titleSmall,
                                                                fontWeight = FontWeight.SemiBold,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                text = "${dev.typeLabel} · $depthText",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                maxLines = 2,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Audio DSP & Silence Skip
                        item {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Аудио DSP и обработка речи",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (dacOutputMode == DacOutputMode.BIT_PERFECT) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "В режиме Bit-Perfect эквалайзер и пропуск тишины отключены, чтобы передавать аудио на ЦАП без искажений.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(10.dp)
                                            )
                                        }
                                    }
                                    DspPreset.entries.forEach { preset ->
                                        val active = dspPreset == preset
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = if (active) {
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerLow
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable { onSelectDspPreset(preset) }
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(preset.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                                    Text(
                                                        text = preset.hint,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (active) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                Text("Пропуск тишины", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    text = "Ускорять длинные паузы чтеца",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Switch(
                                                checked = skipSilence,
                                                enabled = dacOutputMode != DacOutputMode.BIT_PERFECT,
                                                onCheckedChange = onToggleSkipSilence
                                            )
                                        }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("Усиление громкости речи", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                                    Text(
                                                        text = if (dacOutputMode == DacOutputMode.BIT_PERFECT) {
                                                            "Отключено в режиме Bit-Perfect"
                                                        } else {
                                                            "Подъём громкости для тихих записей"
                                                        },
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Text(
                                                    text = if (loudnessBoostDb > 0) "+$loudnessBoostDb дБ" else "0 дБ",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (loudnessBoostDb > 0 && dacOutputMode != DacOutputMode.BIT_PERFECT) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    }
                                                )
                                            }
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                listOf(0, 3, 6, 9, 12).forEach { db ->
                                                    val active = loudnessBoostDb == db
                                                    val enabled = dacOutputMode != DacOutputMode.BIT_PERFECT
                                                    Surface(
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = if (active && enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                                        contentColor = if (active && enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .clickable(enabled = enabled) { onSelectLoudnessBoostDb(db) }
                                                    ) {
                                                        Box(
                                                            contentAlignment = Alignment.Center,
                                                            modifier = Modifier.padding(vertical = 8.dp)
                                                        ) {
                                                            Text(
                                                                text = if (db == 0) "Выкл" else "+$db",
                                                                style = MaterialTheme.typography.labelMedium,
                                                                fontWeight = FontWeight.SemiBold,
                                                                maxLines = 1
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Playback Speed
                        item {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Text(
                                            text = "Скорость воспроизведения (${playbackSpeed}x)",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        listOf(0.8f, 1.0f, 1.2f, 1.5f, 1.75f, 2.0f).forEach { spd ->
                                            val active = kotlin.math.abs(playbackSpeed - spd) < 0.01f
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                                contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .clickable { onSelectPlaybackSpeed(spd) }
                                            ) {
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier.padding(vertical = 10.dp)
                                                ) {
                                                    Text(
                                                        text = "${spd}x",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Seek Step & Smart Rewind
                        item {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "Управление и перемотка",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "Шаг кнопок перемотки (-$seekStepSec / +$seekStepSec сек)",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            listOf(10, 15, 30).forEach { step ->
                                                val active = seekStepSec == step
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                                    contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .clickable { onSelectSeekStepSec(step) }
                                                ) {
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier.padding(vertical = 10.dp)
                                                    ) {
                                                        Text(
                                                            text = "$step сек",
                                                            style = MaterialTheme.typography.labelLarge,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                Text("Умный автооткат (−5 сек)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    text = "Отматывать назад при возобновлении после долгой паузы (>10 мин)",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Switch(
                                                checked = smartRewindEnabled,
                                                onCheckedChange = onToggleSmartRewind
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                SettingsTab.ACCOUNT -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Telegram Cloud Sync (Saved Messages) + Local JSON Backup — Zero Console!
                        item {
                            val isTgConnected = authState is TelegramAuthState.Connected
                            var localBackupNotice by remember { mutableStateOf<String?>(null) }
                            val exportBackupLauncher = rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.CreateDocument("application/json")
                            ) { uri ->
                                if (uri != null) {
                                    runCatching {
                                        val payload = onBuildBackupJson()
                                        context.contentResolver.openOutputStream(uri)?.use { out ->
                                            out.write(payload.toByteArray(Charsets.UTF_8))
                                        }
                                    }.onSuccess {
                                        localBackupNotice = "Файл бэкапа сохранён (.json)"
                                    }.onFailure {
                                        localBackupNotice = "Не удалось сохранить файл бэкапа"
                                    }
                                }
                            }
                            val importBackupLauncher = rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.OpenDocument()
                            ) { uri ->
                                if (uri != null) {
                                    runCatching {
                                        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                                    }.onSuccess { text ->
                                        if (!text.isNullOrBlank()) {
                                            onImportBackupJson(text)
                                            localBackupNotice = null
                                        } else {
                                            localBackupNotice = "Выбран пустой файл"
                                        }
                                    }.onFailure {
                                        localBackupNotice = "Не удалось прочитать файл бэкапа"
                                    }
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer)
                                        ) {
                                            Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Облако Telegram («Избранное»)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = if (isTgConnected) {
                                                    "Синхронизация избранного, плейлистов, закладок и позиции через ваш чат «Избранное» — без Google и без консоли"
                                                } else {
                                                    "Войдите в Telegram ниже — облачная синхронизация заработает автоматически без дополнительной регистрации"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (cloudSyncState.lastSyncedAtMs > 0L || !cloudSyncState.statusMessage.isNullOrBlank()) {
                                        val timePart = formatSyncTimestamp(cloudSyncState.lastSyncedAtMs)
                                        val metaText = buildString {
                                            if (timePart.isNotEmpty()) append("Последняя синхронизация: $timePart")
                                            if (!cloudSyncState.statusMessage.isNullOrBlank()) {
                                                if (isNotEmpty()) append(" · ")
                                                append(cloudSyncState.statusMessage)
                                            }
                                        }
                                        if (metaText.isNotEmpty()) {
                                            Text(
                                                text = metaText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (!localBackupNotice.isNullOrBlank()) {
                                        Text(
                                            text = localBackupNotice!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    if (!cloudSyncState.error.isNullOrBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.errorContainer,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = cloudSyncState.error,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    }

                                    if (isTgConnected) {
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                            ) {
                                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                    Text("Автосинхронизация", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                                    Text(
                                                        text = "Автоматически сохранять изменения в «Избранное» Telegram",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Switch(
                                                    checked = cloudSyncState.autoSyncEnabled,
                                                    onCheckedChange = onToggleAutoCloudSync
                                                )
                                            }
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Button(
                                                enabled = !cloudSyncState.isBusy,
                                                onClick = onSyncCloudPush,
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                if (cloudSyncState.isBusy) {
                                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                } else {
                                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                                }
                                                Spacer(Modifier.width(6.dp))
                                                Text("Сохранить", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            FilledTonalButton(
                                                enabled = !cloudSyncState.isBusy,
                                                onClick = onSyncCloudPull,
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Загрузить", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Локальный бэкап (.json)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = {
                                                    val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                                    exportBackupLauncher.launch("echogram-backup-$stamp.json")
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Экспорт", maxLines = 1)
                                            }
                                            OutlinedButton(
                                                onClick = { importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Импорт", maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Telegram MTProto Account
                        item {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer)
                                        ) {
                                            Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Telegram MTProto", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            if (authState is TelegramAuthState.Connected) {
                                                val label = if (authState.phoneMasked.isNotBlank()) {
                                                    "${authState.userName} (${authState.phoneMasked})"
                                                } else {
                                                    authState.userName
                                                }
                                                Text(
                                                    text = "Подключен: $label",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            } else {
                                                Text(
                                                    text = "Официальный протокол Telegram для чтения аудиотеки",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    if (!visibleError.isNullOrBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.errorContainer,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = visibleError,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    }

                                    when (authState) {
                                        is TelegramAuthState.Idle -> {
                                            val hasCreds = (apiId.trim().toIntOrNull() ?: 0) > 0 && apiHash.trim().isNotBlank()
                                            if (hasCreds) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                    modifier = Modifier.padding(vertical = 8.dp)
                                                ) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(18.dp),
                                                        strokeWidth = 2.dp
                                                    )
                                                    Text(
                                                        text = "Восстановление сессии Telegram...",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            } else {
                                                OutlinedTextField(
                                                    value = apiId,
                                                    onValueChange = {
                                                        apiId = it.filter { ch -> ch.isDigit() }
                                                        localValidationError = null
                                                    },
                                                    label = { Text("API ID") },
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                OutlinedTextField(
                                                    value = apiHash,
                                                    onValueChange = {
                                                        apiHash = it
                                                        localValidationError = null
                                                    },
                                                    label = { Text("API Hash") },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                Button(
                                                    enabled = !isAuthBusy,
                                                    shape = RoundedCornerShape(14.dp),
                                                    onClick = {
                                                        val parsed = apiId.trim().toIntOrNull()
                                                        if (parsed == null || parsed <= 0 || apiHash.trim().isEmpty()) {
                                                            localValidationError = "Введите корректные API ID и API Hash."
                                                        } else {
                                                            localValidationError = null
                                                            onInitTdlib(parsed, apiHash.trim())
                                                        }
                                                    }
                                                ) {
                                                    if (isAuthBusy) {
                                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                        Spacer(Modifier.width(8.dp))
                                                    }
                                                    Text("Подключить клиент")
                                                }
                                            }
                                        }

                                        is TelegramAuthState.WaitingApiCredentials -> {
                                            OutlinedTextField(
                                                value = apiId,
                                                onValueChange = {
                                                    apiId = it.filter { ch -> ch.isDigit() }
                                                    localValidationError = null
                                                },
                                                label = { Text("API ID") },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            OutlinedTextField(
                                                value = apiHash,
                                                onValueChange = {
                                                    apiHash = it
                                                    localValidationError = null
                                                },
                                                label = { Text("API Hash") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Button(
                                                enabled = !isAuthBusy,
                                                shape = RoundedCornerShape(14.dp),
                                                onClick = {
                                                    val parsed = apiId.trim().toIntOrNull()
                                                    if (parsed == null || parsed <= 0 || apiHash.trim().isEmpty()) {
                                                        localValidationError = "Введите корректные API ID и API Hash."
                                                    } else {
                                                        localValidationError = null
                                                        onInitTdlib(parsed, apiHash.trim())
                                                    }
                                                }
                                            ) {
                                                if (isAuthBusy) {
                                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                    Spacer(Modifier.width(8.dp))
                                                }
                                                Text("Подключить клиент")
                                            }
                                        }

                                        is TelegramAuthState.WaitingPhoneNumber -> {
                                            OutlinedTextField(
                                                value = phone,
                                                onValueChange = {
                                                    phone = it
                                                    localValidationError = null
                                                },
                                                label = { Text("Номер телефона (+7... или 89...)") },
                                                placeholder = { Text("+7 999 123-45-67") },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Button(
                                                    enabled = !isAuthBusy,
                                                    shape = RoundedCornerShape(14.dp),
                                                    onClick = {
                                                        localValidationError = null
                                                        onSubmitPhone(phone)
                                                    }
                                                ) {
                                                    if (isAuthBusy) {
                                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                        Spacer(Modifier.width(8.dp))
                                                    }
                                                    Text("Получить код")
                                                }
                                                TextButton(onClick = { activeTab = SettingsTab.NETWORK }) {
                                                    Text("Прокси")
                                                }
                                            }
                                        }

                                        is TelegramAuthState.WaitingCode -> {
                                            if (authState.phoneNumber.isNotBlank()) {
                                                Text(
                                                    text = "Код отправлен на ${authState.phoneNumber} (проверьте чат Telegram или SMS).",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            OutlinedTextField(
                                                value = code,
                                                onValueChange = {
                                                    code = it
                                                    localValidationError = null
                                                },
                                                label = { Text("Код подтверждения") },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Button(
                                                enabled = !isAuthBusy,
                                                shape = RoundedCornerShape(14.dp),
                                                onClick = {
                                                    localValidationError = null
                                                    onSubmitCode(code)
                                                }
                                            ) {
                                                if (isAuthBusy) {
                                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                    Spacer(Modifier.width(8.dp))
                                                }
                                                Text("Подтвердить код")
                                            }
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                TextButton(
                                                    enabled = !isAuthBusy,
                                                    onClick = {
                                                        localValidationError = null
                                                        onResendCode()
                                                    }
                                                ) {
                                                    Text("Отправить повторно")
                                                }
                                                TextButton(
                                                    enabled = !isAuthBusy,
                                                    onClick = {
                                                        code = ""
                                                        localValidationError = null
                                                        onResetToPhone()
                                                    }
                                                ) {
                                                    Text("Другой номер")
                                                }
                                            }
                                        }

                                        is TelegramAuthState.WaitingPassword -> {
                                            if (authState.hint.isNotBlank()) {
                                                Text(
                                                    text = "Подсказка: ${authState.hint}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            OutlinedTextField(
                                                value = password,
                                                onValueChange = {
                                                    password = it
                                                    localValidationError = null
                                                },
                                                label = { Text("Пароль 2FA") },
                                                singleLine = true,
                                                visualTransformation = PasswordVisualTransformation(),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(
                                                    enabled = !isAuthBusy,
                                                    shape = RoundedCornerShape(14.dp),
                                                    onClick = {
                                                        localValidationError = null
                                                        onSubmitPassword(password)
                                                    }
                                                ) {
                                                    if (isAuthBusy) {
                                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                        Spacer(Modifier.width(8.dp))
                                                    }
                                                    Text("Войти")
                                                }
                                                TextButton(
                                                    enabled = !isAuthBusy,
                                                    onClick = {
                                                        password = ""
                                                        code = ""
                                                        localValidationError = null
                                                        onResetToPhone()
                                                    }
                                                ) {
                                                    Text("Назад")
                                                }
                                            }
                                        }

                                        is TelegramAuthState.Connected -> {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(
                                                    enabled = !isLoadingChannels,
                                                    onClick = onRefreshChannels,
                                                    shape = RoundedCornerShape(14.dp)
                                                ) {
                                                    if (isLoadingChannels) {
                                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                    } else {
                                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    }
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(if (channels.isEmpty()) "Загрузить источники" else "Обновить")
                                                }
                                                OutlinedButton(onClick = onLogout, shape = RoundedCornerShape(14.dp)) {
                                                    Text("Выйти")
                                                }
                                            }
                                        }

                                        is TelegramAuthState.WaitingQrConfirmation,
                                        is TelegramAuthState.WaitingEmailAddress,
                                        is TelegramAuthState.WaitingEmailCode,
                                        is TelegramAuthState.WaitingRegistration -> {
                                            Text(
                                                text = "Завершите вход в Telegram на экране авторизации.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            TextButton(onClick = onResetToPhone) {
                                                Text("Вернуться к вводу номера")
                                            }
                                        }

                                        is TelegramAuthState.Error -> {
                                            Text(authState.message, color = MaterialTheme.colorScheme.error)
                                            Button(onClick = {
                                                localValidationError = null
                                                onResetToPhone()
                                            }) {
                                                Text("Повторить вход")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                SettingsTab.SOURCES -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (authState !is TelegramAuthState.Connected) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(22.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.padding(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Radio,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Text(
                                            text = "Сначала подключите Telegram",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Войдите в Telegram на вкладке «Аккаунт», чтобы выбрать каналы, ботов и чаты с аудиокнигами.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Button(
                                            onClick = { activeTab = SettingsTab.ACCOUNT },
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Text("Перейти в Аккаунт")
                                        }
                                    }
                                }
                            }
                        } else {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Источники аудиокниг", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = "Каналы, боты и чаты · Выбрано: $selectedCount",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        FilledTonalButton(
                                            enabled = !isLoadingChannels,
                                            onClick = onRefreshChannels,
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Обновить", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(4.dp)
                                        ) {
                                            listOf(
                                                "all" to "Все",
                                                "channel" to "Каналы",
                                                "bot" to "Боты",
                                                "chat" to "Чаты"
                                            ).forEach { (id, label) ->
                                                val active = sourceFilter == id
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                    contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .clickable { sourceFilter = id }
                                                ) {
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                                                    ) {
                                                        Text(
                                                            text = label,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    M3SearchBar(
                                        value = sourceSearchQuery,
                                        onValueChange = { sourceSearchQuery = it },
                                        placeholder = "Фильтр каналов, ботов и чатов..."
                                    )
                                }
                            }

                            if (isLoadingChannels && channels.isEmpty()) {
                                item {
                                    Row(
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(10.dp))
                                        Text("Загрузка ваших каналов, ботов и чатов...", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            } else if (filteredChannels.isEmpty()) {
                                item {
                                    Text(
                                        text = "Источники не найдены.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )
                                }
                            } else {
                                items(filteredChannels, key = { it.channelId }) { ch ->
                                    val checked = ch.isSelected
                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = if (checked) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainer
                                        },
                                        contentColor = if (checked) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(18.dp))
                                            .clickable { onToggleChannel(ch) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = sourceTypeIcon(ch.sourceType),
                                                contentDescription = ch.sourceType.label,
                                                tint = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = ch.title,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val subtitle = buildString {
                                                    append(ch.sourceType.label)
                                                    if (ch.username.isNotBlank()) append(" · @${ch.username}")
                                                }
                                                Text(
                                                    text = subtitle,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (checked) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Spacer(Modifier.width(10.dp))
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        if (checked) MaterialTheme.colorScheme.primary else Color.Transparent
                                                    )
                                                    .border(
                                                        width = 1.5.dp,
                                                        color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                            ) {
                                                if (checked) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Выбрано",
                                                        tint = MaterialTheme.colorScheme.onPrimary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                SettingsTab.NETWORK -> {
                    val totalCache = cacheStats.totalCacheBytes
                    val audioShare = if (totalCache > 0L) (cacheStats.audioBytes.toFloat() / totalCache.toFloat()).coerceIn(0f, 1f) else 0f
                    val coversShare = if (totalCache > 0L) (cacheStats.coversBytes.toFloat() / totalCache.toFloat()).coerceIn(0f, 1f) else 0f
                    val otherShare = if (totalCache > 0L) (cacheStats.otherBytes.toFloat() / totalCache.toFloat()).coerceIn(0f, 1f) else 0f
                    val selectedClearBytes =
                        (if (clearAudioChecked) cacheStats.audioBytes else 0L) +
                        (if (clearCoversChecked) cacheStats.coversBytes else 0L) +
                        (if (clearOtherChecked) cacheStats.otherBytes else 0L)

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Proxy Section (Telegram Native Style)
                        item {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Public,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Прокси-сервер Telegram",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = if (proxyEnabled) {
                                                        "Трафик TDLib идёт через выбранный прокси"
                                                    } else {
                                                        "Прямое соединение без прокси"
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Switch(
                                            checked = proxyEnabled,
                                            enabled = !isProxyBusy,
                                            onCheckedChange = onToggleUseProxy
                                        )
                                    }

                                    if (!proxyError.isNullOrBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.errorContainer,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = proxyError,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    }

                                    if (!proxyMessage.isNullOrBlank() && proxyError.isNullOrBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = proxyMessage,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    }

                                    if (proxies.isNotEmpty()) {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            proxies.forEach { proxy ->
                                                Surface(
                                                    shape = RoundedCornerShape(16.dp),
                                                    color = if (proxy.isEnabled) {
                                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                                    } else {
                                                        MaterialTheme.colorScheme.surfaceContainerLow
                                                    },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .border(
                                                            width = if (proxy.isEnabled) 1.5.dp else 1.dp,
                                                            color = if (proxy.isEnabled) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                            shape = RoundedCornerShape(16.dp)
                                                        )
                                                        .clip(RoundedCornerShape(16.dp))
                                                        .clickable { onEnableProxy(proxy.id) }
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                                    ) {
                                                        RadioButton(
                                                            selected = proxy.isEnabled,
                                                            onClick = { onEnableProxy(proxy.id) }
                                                        )
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = "${proxy.server}:${proxy.port}",
                                                                style = MaterialTheme.typography.titleSmall,
                                                                fontWeight = FontWeight.SemiBold,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            val statusText = buildString {
                                                                append(proxy.type.label)
                                                                if (proxy.isEnabled) append(" · Активен")
                                                                if (proxy.latencyMs != null) {
                                                                    append(" · Доступен (${proxy.latencyMs} мс)")
                                                                } else if (!proxy.pingError.isNullOrBlank()) {
                                                                    append(" · ${proxy.pingError}")
                                                                }
                                                            }
                                                            Text(
                                                                text = statusText,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                        IconButton(onClick = { onPingProxy(proxy.id) }) {
                                                            Icon(
                                                                imageVector = Icons.Default.Speed,
                                                                contentDescription = "Проверить пинг",
                                                                tint = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                        IconButton(onClick = { onRemoveProxy(proxy.id) }) {
                                                            Icon(
                                                                imageVector = Icons.Default.Delete,
                                                                contentDescription = "Удалить прокси",
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        FilledTonalButton(
                                            onClick = { showAddProxy = !showAddProxy },
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(if (showAddProxy) "Скрыть форму" else "Добавить прокси")
                                        }
                                        if (isProxyBusy) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        }
                                    }

                                    AnimatedVisibility(visible = showAddProxy) {
                                        Surface(
                                            shape = RoundedCornerShape(18.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.padding(14.dp)
                                            ) {
                                                OutlinedTextField(
                                                    value = proxyLinkInput,
                                                    onValueChange = { raw ->
                                                        proxyLinkInput = raw
                                                        if (raw.contains("server=", ignoreCase = true)) {
                                                            onParseProxyLink(raw)?.let { parsed ->
                                                                proxyFormType = parsed.type
                                                                proxyFormServer = parsed.server
                                                                proxyFormPort = parsed.port.toString()
                                                                proxyFormUser = parsed.username
                                                                proxyFormPass = parsed.password
                                                                proxyFormSecret = parsed.secret
                                                            }
                                                        }
                                                    },
                                                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                                                    label = { Text("Ссылка t.me/proxy или tg://socks") },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )

                                                // Protocol selector
                                                Surface(
                                                    shape = RoundedCornerShape(14.dp),
                                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                        modifier = Modifier.padding(4.dp)
                                                    ) {
                                                        TelegramProxyType.entries.forEach { pt ->
                                                            val active = proxyFormType == pt
                                                            Surface(
                                                                shape = RoundedCornerShape(10.dp),
                                                                color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                                contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier
                                                                    .weight(1f)
                                                                    .clip(RoundedCornerShape(10.dp))
                                                                    .clickable {
                                                                        proxyFormType = pt
                                                                        if (proxyFormPort.isBlank() || proxyFormPort == "1080" || proxyFormPort == "443" || proxyFormPort == "8080") {
                                                                            proxyFormPort = when (pt) {
                                                                                TelegramProxyType.MTPROTO -> "443"
                                                                                TelegramProxyType.HTTP -> "8080"
                                                                                TelegramProxyType.SOCKS5 -> "1080"
                                                                            }
                                                                        }
                                                                    }
                                                            ) {
                                                                Box(
                                                                    contentAlignment = Alignment.Center,
                                                                    modifier = Modifier.padding(vertical = 8.dp)
                                                                ) {
                                                                    Text(pt.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                                    OutlinedTextField(
                                                        value = proxyFormServer,
                                                        onValueChange = { proxyFormServer = it },
                                                        label = { Text("Сервер (IP/домен)") },
                                                        singleLine = true,
                                                        modifier = Modifier.weight(2f)
                                                    )
                                                    OutlinedTextField(
                                                        value = proxyFormPort,
                                                        onValueChange = { proxyFormPort = it.filter { ch -> ch.isDigit() } },
                                                        label = { Text("Порт") },
                                                        singleLine = true,
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }

                                                if (proxyFormType == TelegramProxyType.MTPROTO) {
                                                    OutlinedTextField(
                                                        value = proxyFormSecret,
                                                        onValueChange = { proxyFormSecret = it },
                                                        label = { Text("Секретный ключ (Secret)") },
                                                        singleLine = true,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                } else {
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                                        OutlinedTextField(
                                                            value = proxyFormUser,
                                                            onValueChange = { proxyFormUser = it },
                                                            label = { Text("Логин (опц.)") },
                                                            singleLine = true,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        OutlinedTextField(
                                                            value = proxyFormPass,
                                                            onValueChange = { proxyFormPass = it },
                                                            label = { Text("Пароль (опц.)") },
                                                            singleLine = true,
                                                            visualTransformation = PasswordVisualTransformation(),
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                }

                                                Button(
                                                    enabled = !isProxyBusy && proxyFormServer.isNotBlank() && (proxyFormPort.toIntOrNull() ?: 0) in 1..65535,
                                                    shape = RoundedCornerShape(14.dp),
                                                    modifier = Modifier.fillMaxWidth(),
                                                    onClick = {
                                                        val portNum = proxyFormPort.toIntOrNull() ?: 1080
                                                        onAddProxy(
                                                            proxyFormType,
                                                            proxyFormServer.trim(),
                                                            portNum,
                                                            proxyFormUser.trim(),
                                                            proxyFormPass,
                                                            proxyFormSecret.trim()
                                                        )
                                                        showAddProxy = false
                                                        proxyLinkInput = ""
                                                        proxyFormServer = ""
                                                        proxyFormUser = ""
                                                        proxyFormPass = ""
                                                        proxyFormSecret = ""
                                                    }
                                                ) {
                                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("Сохранить и подключить")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Storage & Cache Section (Original Telegram Storage Usage Style)
                        item {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Storage,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                            Column {
                                                Text("Использование памяти и кеш", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                                Text(
                                                    text = "Кеш медиа: ${formatStorageBytes(totalCache)}" +
                                                        if (cacheStats.databaseBytes > 0L) " · БД: ${formatStorageBytes(cacheStats.databaseBytes)}" else "",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        IconButton(onClick = onRefreshCacheStats, enabled = !isCacheBusy) {
                                            if (isCacheBusy) {
                                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                            } else {
                                                Icon(Icons.Default.Refresh, contentDescription = "Пересчитать")
                                            }
                                        }
                                    }

                                    // Proportional bar
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(10.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    ) {
                                        if (totalCache > 0L) {
                                            if (audioShare > 0f) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .weight(audioShare.coerceAtLeast(0.03f))
                                                        .background(MaterialTheme.colorScheme.primary)
                                                )
                                            }
                                            if (coversShare > 0f) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .weight(coversShare.coerceAtLeast(0.03f))
                                                        .background(MaterialTheme.colorScheme.tertiary)
                                                )
                                            }
                                            if (otherShare > 0f) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .weight(otherShare.coerceAtLeast(0.03f))
                                                        .background(MaterialTheme.colorScheme.secondary)
                                                )
                                            }
                                        }
                                    }

                                    // Breakdown rows
                                    val categories = listOf(
                                        Triple(
                                            "Аудиокниги и поток",
                                            "${cacheStats.audioCount} файлов · ${formatStorageBytes(cacheStats.audioBytes)}",
                                            Triple(MaterialTheme.colorScheme.primary, clearAudioChecked) { clearAudioChecked = !clearAudioChecked }
                                        ),
                                        Triple(
                                            "Обложки и фото каналов",
                                            "${cacheStats.coversCount} файлов · ${formatStorageBytes(cacheStats.coversBytes)}",
                                            Triple(MaterialTheme.colorScheme.tertiary, clearCoversChecked) { clearCoversChecked = !clearCoversChecked }
                                        ),
                                        Triple(
                                            "Прочий кеш Telegram",
                                            "${cacheStats.otherCount} файлов · ${formatStorageBytes(cacheStats.otherBytes)}",
                                            Triple(MaterialTheme.colorScheme.secondary, clearOtherChecked) { clearOtherChecked = !clearOtherChecked }
                                        )
                                    )

                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        categories.forEach { (title, sub, meta) ->
                                            val (dotColor, checked, toggle) = meta
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .clickable { toggle() }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                                ) {
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier
                                                            .size(22.dp)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(if (checked) MaterialTheme.colorScheme.primary else Color.Transparent)
                                                            .border(
                                                                width = 1.5.dp,
                                                                color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                                shape = RoundedCornerShape(6.dp)
                                                            )
                                                    ) {
                                                        if (checked) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                        }
                                                    }
                                                    Spacer(Modifier.width(10.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .size(10.dp)
                                                            .clip(CircleShape)
                                                            .background(dotColor)
                                                    )
                                                    Spacer(Modifier.width(10.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = title,
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.SemiBold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = sub,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Auto-cleanup TTL selector
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "Автоудаление старого кеша",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.padding(4.dp)
                                            ) {
                                                listOf(
                                                    3 to "3 дня",
                                                    7 to "1 нед",
                                                    30 to "1 мес",
                                                    0 to "Всегда"
                                                ).forEach { (days, label) ->
                                                    val active = cacheTtlDays == days
                                                    Surface(
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                                        contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .clickable { onCacheTtlChange(days) }
                                                    ) {
                                                        Box(
                                                            contentAlignment = Alignment.Center,
                                                            modifier = Modifier.padding(vertical = 8.dp)
                                                        ) {
                                                            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (!cacheMessage.isNullOrBlank()) {
                                        Text(
                                            text = cacheMessage,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Button(
                                        enabled = !isCacheBusy && (clearAudioChecked || clearCoversChecked || clearOtherChecked),
                                        onClick = {
                                            onClearCache(clearAudioChecked, clearCoversChecked, clearOtherChecked)
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Очистить кеш (${formatStorageBytes(selectedClearBytes)})")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
