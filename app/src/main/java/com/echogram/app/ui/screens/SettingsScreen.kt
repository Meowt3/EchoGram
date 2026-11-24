package com.echogram.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.echogram.app.data.model.AuthState
import com.echogram.app.data.model.CacheStats
import com.echogram.app.data.model.ConnectionStatus
import com.echogram.app.data.model.DacDevice
import com.echogram.app.data.model.DacOutputMode
import com.echogram.app.data.model.DspPreset
import com.echogram.app.data.model.ProxyItem
import com.echogram.app.data.model.ProxyType
import com.echogram.app.data.model.StreamFormatInfo
import com.echogram.app.data.model.ThemeMode
import com.echogram.app.data.telegram.ProxyLinkParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authState: AuthState,
    connectionStatus: ConnectionStatus,
    themeMode: ThemeMode,
    playbackSpeed: Float,
    skipSilence: Boolean,
    seekStepSec: Int,
    smartRewind: Boolean,
    dspPreset: DspPreset,
    loudnessBoostDb: Int,
    dacMode: DacOutputMode,
    dacDevices: List<DacDevice>,
    dacRoutingSummary: String,
    streamFormat: StreamFormatInfo,
    proxies: List<ProxyItem>,
    cacheStats: CacheStats,
    onThemeModeChange: (ThemeMode) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSkipSilenceChange: (Boolean) -> Unit,
    onSeekStepChange: (Int) -> Unit,
    onSmartRewindChange: (Boolean) -> Unit,
    onDspPresetChange: (DspPreset) -> Unit,
    onLoudnessBoostChange: (Int) -> Unit,
    onDacModeChange: (DacOutputMode) -> Unit,
    onRefreshDac: () -> Unit,
    onAddProxy: (ProxyType, String, Int, String, String, String) -> Unit,
    onEnableProxy: (Int) -> Unit,
    onDisableProxies: () -> Unit,
    onRemoveProxy: (Int) -> Unit,
    onPingProxy: (Int) -> Unit,
    onClearCache: () -> Unit,
    onLogout: () -> Unit
) {
    var proxyLink by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("1080") }
    var secret by remember { mutableStateOf("") }
    var pType by remember { mutableStateOf(ProxyType.MTPROTO) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Настройки", fontWeight = FontWeight.Bold) })

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Profile card
            item(key = "profile") {
                val user = authState as? AuthState.Authorized
                SettingsCard("Аккаунт Telegram") {
                    Text(
                        text = user?.displayName ?: "Telegram",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val meta = listOfNotNull(
                        user?.username?.takeIf { it.isNotBlank() }?.let { "@$it" },
                        user?.phoneMasked?.takeIf { it.isNotBlank() },
                        connectionStatus.label
                    ).joinToString(" · ")
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onLogout) {
                        Text("Выйти из аккаунта")
                    }
                }
            }

            // Theme
            item(key = "theme") {
                SettingsCard("Оформление") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEach { m ->
                            FilterChip(
                                selected = themeMode == m,
                                onClick = { onThemeModeChange(m) },
                                label = { Text(m.label) }
                            )
                        }
                    }
                }
            }

            // Audio & Audiobook controls
            item(key = "audio") {
                SettingsCard("Аудиокниги и звук") {
                    Text("Скорость: ${String.format("%.2fx", playbackSpeed)}", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = playbackSpeed,
                        onValueChange = onSpeedChange,
                        valueRange = 0.75f..2.5f
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Пропуск пауз (Skip Silence)")
                        Switch(checked = skipSilence, onCheckedChange = onSkipSilenceChange)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Умная перемотка после паузы")
                        Switch(checked = smartRewind, onCheckedChange = onSmartRewindChange)
                    }

                    Text("Шаг перемотки", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10, 15, 30).forEach { sec ->
                            FilterChip(
                                selected = seekStepSec == sec,
                                onClick = { onSeekStepChange(sec) },
                                label = { Text("${sec}с") }
                            )
                        }
                    }

                    Text("Пресет DSP", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DspPreset.entries.forEach { p ->
                            FilterChip(
                                selected = dspPreset == p,
                                onClick = { onDspPresetChange(p) },
                                label = { Text(p.label) }
                            )
                        }
                    }

                    Text("Усиление громкости: +${loudnessBoostDb} дБ", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = loudnessBoostDb.toFloat(),
                        onValueChange = { onLoudnessBoostChange(it.toInt()) },
                        valueRange = 0f..12f,
                        steps = 11
                    )
                }
            }

            // USB DAC & Bit-Perfect
            item(key = "dac") {
                SettingsCard("USB ЦАП и вывод звука") {
                    Text(dacRoutingSummary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    val fmt = streamFormat.summary()
                    if (fmt.isNotBlank()) {
                        Text("Поток: $fmt", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DacOutputMode.entries.forEach { m ->
                            FilterChip(
                                selected = dacMode == m,
                                onClick = { onDacModeChange(m) },
                                label = { Text(m.label) }
                            )
                        }
                    }
                    if (dacDevices.isNotEmpty()) {
                        Text(
                            text = "Устройства: ${dacDevices.joinToString { it.name }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onRefreshDac) { Text("Обновить устройства вывода") }
                }
            }

            // Proxies
            item(key = "proxy") {
                SettingsCard("Прокси Telegram") {
                    OutlinedTextField(
                        value = proxyLink,
                        onValueChange = {
                            proxyLink = it
                            ProxyLinkParser.parse(it)?.let { parsed ->
                                pType = parsed.type
                                server = parsed.server
                                port = parsed.port.toString()
                                secret = parsed.secret
                            }
                        },
                        label = { Text("Ссылка tg://proxy или t.me/proxy") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = server,
                            onValueChange = { server = it },
                            label = { Text("Сервер") },
                            singleLine = true,
                            modifier = Modifier.weight(2f)
                        )
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it.filter(Char::isDigit) },
                            label = { Text("Порт") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (pType == ProxyType.MTPROTO) {
                        OutlinedTextField(
                            value = secret,
                            onValueChange = { secret = it },
                            label = { Text("Secret") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Button(
                        enabled = server.isNotBlank() && (port.toIntOrNull() ?: 0) in 1..65535,
                        onClick = {
                            onAddProxy(pType, server, port.toIntOrNull() ?: 443, "", "", secret)
                            server = ""
                            secret = ""
                            proxyLink = ""
                        }
                    ) { Text("Добавить прокси") }

                    proxies.forEach { p ->
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${p.type.label} · ${p.server}:${p.port}", fontWeight = FontWeight.SemiBold)
                                val sub = when {
                                    p.pingMs != null -> "${p.pingMs} мс"
                                    p.pingError != null -> p.pingError
                                    p.isEnabled -> "Активен"
                                    else -> "Отключён"
                                }
                                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row {
                                TextButton(onClick = { onPingProxy(p.id) }) { Text("Пинг") }
                                TextButton(onClick = { if (p.isEnabled) onDisableProxies() else onEnableProxy(p.id) }) {
                                    Text(if (p.isEnabled) "Выкл" else "Вкл")
                                }
                                TextButton(onClick = { onRemoveProxy(p.id) }) { Text("×") }
                            }
                        }
                    }
                }
            }

            // Cache
            item(key = "cache") {
                SettingsCard("Хранилище и кеш") {
                    Text(
                        text = "Кеш стриминга: ${formatBytes(cacheStats.tdlibBytes + cacheStats.coversBytes)}\nОфлайн-книги: ${formatBytes(cacheStats.offlineBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = onClearCache) {
                        Text("Очистить кеш стриминга")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 МБ"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb < 1024.0) String.format("%.1f МБ", mb) else String.format("%.2f ГБ", mb / 1024.0)
}
