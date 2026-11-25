package com.tgaudiobooks.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tgaudiobooks.app.data.model.ParsedProxyLink
import com.tgaudiobooks.app.data.model.TelegramAuthState
import com.tgaudiobooks.app.data.model.TelegramConnectionStatus
import com.tgaudiobooks.app.data.model.TelegramProxyItem
import com.tgaudiobooks.app.data.model.TelegramProxyType
import com.tgaudiobooks.app.ui.components.BubbleMotion
import com.tgaudiobooks.app.ui.components.QrCodeGenerator
import com.tgaudiobooks.app.ui.theme.AppIconPreset
import com.tgaudiobooks.app.ui.theme.EchoGramBrandIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramAuthScreen(
    authState: TelegramAuthState,
    authError: String?,
    isAuthBusy: Boolean,
    connectionStatus: TelegramConnectionStatus,
    appIcon: AppIconPreset,
    proxies: List<TelegramProxyItem>,
    hasOfflineTracks: Boolean,
    onInitTdlib: (Int, String) -> Unit,
    onSubmitPhone: (String) -> Unit,
    onRequestQr: () -> Unit,
    onSubmitCode: (String) -> Unit,
    onResendCode: () -> Unit,
    onSubmitEmail: (String) -> Unit,
    onSubmitEmailCode: (String) -> Unit,
    onSubmitRegistration: (String, String) -> Unit,
    onSubmitPassword: (String) -> Unit,
    onResetToPhone: () -> Unit,
    onClearError: () -> Unit,
    onParseProxyLink: (String) -> ParsedProxyLink?,
    onAddProxy: (TelegramProxyType, String, Int, String, String, String) -> Unit,
    onEnableProxy: (Int) -> Unit,
    onToggleUseProxy: (Boolean) -> Unit,
    onRemoveProxy: (Int) -> Unit,
    onContinueOffline: () -> Unit
) {
    val context = LocalContext.current
    var showProxySheet by remember { mutableStateOf(false) }
    var showCustomApiDialog by remember { mutableStateOf(false) }

    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var emailCode by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var customApiId by remember { mutableStateOf("") }
    var customApiHash by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (connectionStatus == TelegramConnectionStatus.READY) Color(0xFF22C55E)
                                    else MaterialTheme.colorScheme.primary
                                )
                        )
                        Text(
                            text = connectionStatus.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(
                        onClick = { showProxySheet = true },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (proxies.any { it.isEnabled }) "Прокси ✓" else "Прокси")
                    }
                    IconButton(onClick = { showCustomApiDialog = true }) {
                        Icon(Icons.Default.Key, contentDescription = "Свои API ключи")
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                EchoGramBrandIcon(preset = appIcon, size = 84.dp)
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "EchoGram",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Войдите в Telegram, чтобы слушать аудиокниги из ваших каналов и чатов",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))

                if (!authError.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                    ) {
                        Text(
                            text = authError,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                AnimatedContent(
                    targetState = authState,
                    transitionSpec = { BubbleMotion.bubbleContentTransform() },
                    label = "tg_auth_step"
                ) { state ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (state) {
                            is TelegramAuthState.Idle -> {
                                CircularProgressIndicator()
                                Text(
                                    text = "Подключение к Telegram...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            is TelegramAuthState.WaitingApiCredentials -> {
                                Text(
                                    text = "Укажите ключи Telegram API (my.telegram.org) один раз:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                OutlinedTextField(
                                    value = customApiId,
                                    onValueChange = { customApiId = it.filter(Char::isDigit); onClearError() },
                                    label = { Text("API ID") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = customApiHash,
                                    onValueChange = { customApiHash = it; onClearError() },
                                    label = { Text("API Hash") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    enabled = !isAuthBusy,
                                    onClick = { onInitTdlib(customApiId.toIntOrNull() ?: 0, customApiHash) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth().height(52.dp)
                                ) { Text("Сохранить и продолжить") }
                            }

                            is TelegramAuthState.WaitingPhoneNumber -> {
                                OutlinedTextField(
                                    value = phone,
                                    onValueChange = { phone = it; onClearError() },
                                    label = { Text("Номер телефона") },
                                    placeholder = { Text("+7 999 123-45-67") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Phone,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(onDone = { onSubmitPhone(phone) }),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    enabled = !isAuthBusy,
                                    onClick = { onSubmitPhone(phone) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth().height(52.dp)
                                ) {
                                    if (isAuthBusy) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text("Получить код")
                                }
                                OutlinedButton(
                                    enabled = !isAuthBusy,
                                    onClick = onRequestQr,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth().height(50.dp)
                                ) {
                                    Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Войти по QR-коду")
                                }
                            }

                            is TelegramAuthState.WaitingQrConfirmation -> {
                                val qrBitmap = remember(state.link) { QrCodeGenerator.generate(state.link, 560) }
                                Text(
                                    text = "Откройте Telegram → Настройки → Устройства → Подключить устройство и отсканируйте QR-код",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                if (qrBitmap != null) {
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = Color.White,
                                        modifier = Modifier.size(220.dp)
                                    ) {
                                        Image(
                                            bitmap = qrBitmap.asImageBitmap(),
                                            contentDescription = "Telegram QR",
                                            modifier = Modifier.fillMaxSize().padding(14.dp)
                                        )
                                    }
                                }
                                Button(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.link)))
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth().height(50.dp)
                                ) {
                                    Text("Открыть в Telegram на этом устройстве")
                                }
                                TextButton(onClick = onResetToPhone) {
                                    Text("Войти по номеру телефона")
                                }
                            }

                            is TelegramAuthState.WaitingCode -> {
                                Text(
                                    text = "Код отправлен на ${state.phoneNumber}\nПроверьте чат Telegram или SMS",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                OutlinedTextField(
                                    value = code,
                                    onValueChange = { code = it.filter(Char::isDigit).take(6); onClearError() },
                                    label = { Text("Код подтверждения") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(onDone = { onSubmitCode(code) }),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    enabled = !isAuthBusy && code.length >= 4,
                                    onClick = { onSubmitCode(code) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth().height(52.dp)
                                ) {
                                    if (isAuthBusy) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text("Подтвердить код")
                                }
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    TextButton(enabled = !isAuthBusy, onClick = onResendCode) {
                                        Text("Отправить повторно")
                                    }
                                    TextButton(enabled = !isAuthBusy, onClick = { code = ""; onResetToPhone() }) {
                                        Text("Другой номер")
                                    }
                                }
                            }

                            is TelegramAuthState.WaitingEmailAddress -> {
                                Text("Укажите email для входа в Telegram", style = MaterialTheme.typography.bodyMedium)
                                OutlinedTextField(
                                    value = email,
                                    onValueChange = { email = it; onClearError() },
                                    label = { Text("Email") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    enabled = !isAuthBusy,
                                    onClick = { onSubmitEmail(email) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth().height(50.dp)
                                ) { Text("Отправить код") }
                            }

                            is TelegramAuthState.WaitingEmailCode -> {
                                Text("Введите код из письма (${state.emailPattern})", textAlign = TextAlign.Center)
                                OutlinedTextField(
                                    value = emailCode,
                                    onValueChange = { emailCode = it; onClearError() },
                                    label = { Text("Код из письма") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    enabled = !isAuthBusy,
                                    onClick = { onSubmitEmailCode(emailCode) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth().height(50.dp)
                                ) { Text("Подтвердить") }
                            }

                            is TelegramAuthState.WaitingRegistration -> {
                                Text("Регистрация нового аккаунта Telegram", style = MaterialTheme.typography.bodyMedium)
                                OutlinedTextField(
                                    value = firstName,
                                    onValueChange = { firstName = it },
                                    label = { Text("Имя") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = lastName,
                                    onValueChange = { lastName = it },
                                    label = { Text("Фамилия (необязательно)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    enabled = !isAuthBusy,
                                    onClick = { onSubmitRegistration(firstName, lastName) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth().height(50.dp)
                                ) { Text("Создать аккаунт") }
                            }

                            is TelegramAuthState.WaitingPassword -> {
                                Text(
                                    text = if (state.hint.isNotBlank()) "Облачный пароль 2FA\nПодсказка: ${state.hint}"
                                    else "Введите облачный пароль 2FA",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it; onClearError() },
                                    label = { Text("Пароль 2FA") },
                                    singleLine = true,
                                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showPassword = !showPassword }) {
                                            Icon(
                                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = null
                                            )
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(onDone = { onSubmitPassword(password) }),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    enabled = !isAuthBusy && password.isNotEmpty(),
                                    onClick = { onSubmitPassword(password) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth().height(52.dp)
                                ) {
                                    if (isAuthBusy) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text("Войти")
                                }
                                TextButton(onClick = { password = ""; onResetToPhone() }) {
                                    Text("Назад")
                                }
                            }

                            is TelegramAuthState.Error -> {
                                Text(state.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                                Button(onClick = onResetToPhone, shape = RoundedCornerShape(14.dp)) {
                                    Text("Повторить вход")
                                }
                            }

                            is TelegramAuthState.Connected -> Unit
                        }
                    }
                }

                if (hasOfflineTracks) {
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = onContinueOffline) {
                        Text("Открыть скачанные книги офлайн")
                    }
                }
            }
        }
        }
    }

    if (showProxySheet) {
        var linkInput by remember { mutableStateOf("") }
        var server by remember { mutableStateOf("") }
        var port by remember { mutableStateOf("1080") }
        var secret by remember { mutableStateOf("") }
        var pType by remember { mutableStateOf(TelegramProxyType.MTPROTO) }

        ModalBottomSheet(onDismissRequest = { showProxySheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Прокси Telegram", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = linkInput,
                    onValueChange = {
                        linkInput = it
                        onParseProxyLink(it)?.let { parsed ->
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
                    TelegramProxyType.entries.forEach { t ->
                        FilledTonalButton(
                            onClick = { pType = t },
                            modifier = Modifier.weight(1f)
                        ) { Text(if (pType == t) "✓ ${t.label}" else t.label) }
                    }
                }
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
                if (pType == TelegramProxyType.MTPROTO) {
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
                        linkInput = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Добавить и включить") }

                proxies.forEach { p ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${p.type.label} · ${p.server}:${p.port}", fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (p.isEnabled) "Активен" else "Отключён",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (p.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row {
                                TextButton(onClick = { if (p.isEnabled) onToggleUseProxy(false) else onEnableProxy(p.id) }) {
                                    Text(if (p.isEnabled) "Выкл" else "Вкл")
                                }
                                TextButton(onClick = { onRemoveProxy(p.id) }) { Text("Удалить") }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if (showCustomApiDialog) {
        AlertDialog(
            onDismissRequest = { showCustomApiDialog = false },
            title = { Text("Свои ключи Telegram API") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "По умолчанию используются встроенные ключи. Меняйте их только если хотите подключить собственное приложение с my.telegram.org.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = customApiId,
                        onValueChange = { customApiId = it.filter(Char::isDigit) },
                        label = { Text("API ID") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = customApiHash,
                        onValueChange = { customApiHash = it },
                        label = { Text("API Hash") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = customApiId.toIntOrNull() ?: 0
                        if (id > 0 && customApiHash.isNotBlank()) {
                            onInitTdlib(id, customApiHash)
                            showCustomApiDialog = false
                        }
                    }
                ) { Text("Применить") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomApiDialog = false }) { Text("Отмена") }
            }
        )
    }
}
