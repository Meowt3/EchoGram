package com.tgaudiobooks.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tgaudiobooks.app.data.model.AudioTrack
import com.tgaudiobooks.app.data.model.TelegramChannel
import com.tgaudiobooks.app.data.model.TelegramChatMessage
import com.tgaudiobooks.app.data.model.TelegramChatUiState
import com.tgaudiobooks.app.data.telegram.TelegramDeepLinks
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramChatScreen(
    state: TelegramChatUiState,
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onLoadOlder: () -> Unit,
    onRequestPhoto: (messageId: Long, photoFileId: Int) -> Unit,
    onSendMessage: (String) -> Unit,
    onPlayTrack: (AudioTrack, List<AudioTrack>) -> Unit,
    onTogglePlayPause: () -> Unit
) {
    val target = state.target ?: return
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var composerText by remember(target.channelId) { mutableStateOf("") }
    var didInitialScroll by remember(target.channelId, target.focusMessageId) { mutableStateOf(false) }
    var prevLastMessageId by remember(target.channelId) { mutableStateOf(0L) }
    var prevCount by remember(target.channelId) { mutableIntStateOf(0) }

    BackHandler(onBack = onClose)

    val chatAudioQueue = remember(state.messages) {
        state.messages.mapNotNull { it.audioTrack }
    }

    val externalLink = remember(target.channelId, target.username, target.sourceType, target.focusMessageId, state.messages.lastOrNull()?.messageId) {
        val ch = TelegramChannel(
            channelId = target.channelId,
            title = target.title,
            username = target.username,
            sourceType = target.sourceType
        )
        val fallbackTrack = state.messages.firstOrNull { it.messageId == target.focusMessageId }?.audioTrack
            ?: state.messages.lastOrNull { it.audioTrack != null }?.audioTrack
        TelegramDeepLinks.buildChannelLinks(ch, fallbackTrack)
    }

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.messageId, state.isLoading) {
        val msgs = state.messages
        if (msgs.isEmpty()) return@LaunchedEffect
        val lastMsg = msgs.last()
        if (!didInitialScroll) {
            didInitialScroll = true
            val headerOffset = if (state.hasMoreOlder) 1 else 0
            val focusIdx = if (target.focusMessageId > 0L) {
                msgs.indexOfFirst { it.messageId == target.focusMessageId }
            } else {
                -1
            }
            val scrollIndex = if (focusIdx >= 0) {
                (focusIdx + headerOffset).coerceAtLeast(0)
            } else {
                (msgs.lastIndex + headerOffset).coerceAtLeast(0)
            }
            listState.scrollToItem(scrollIndex)
        } else if (msgs.size > prevCount && lastMsg.messageId != prevLastMessageId && prevLastMessageId != 0L) {
            val headerOffset = if (state.hasMoreOlder) 1 else 0
            listState.animateScrollToItem((msgs.lastIndex + headerOffset).coerceAtLeast(0))
        }
        prevLastMessageId = lastMsg.messageId
        prevCount = msgs.size
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Назад"
                            )
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = target.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    append(target.sourceType.label)
                                    if (target.username.isNotBlank()) {
                                        append(" · @${target.username.removePrefix("@")}")
                                    }
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onRefresh, enabled = !state.isLoading) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Обновить чат"
                            )
                        }
                        if (externalLink != null) {
                            IconButton(
                                onClick = { TelegramDeepLinks.openTelegramChat(context, externalLink) }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Открыть во внешнем Telegram"
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                    ) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                        if (state.canSendMessages) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                OutlinedTextField(
                                    value = composerText,
                                    onValueChange = { composerText = it },
                                    placeholder = { Text("Написать сообщение...") },
                                    maxLines = 4,
                                    shape = RoundedCornerShape(24.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                FilledIconButton(
                                    enabled = composerText.isNotBlank() && !state.isSending,
                                    onClick = {
                                        val payload = composerText.trim()
                                        if (payload.isNotEmpty()) {
                                            composerText = ""
                                            onSendMessage(payload)
                                        }
                                    },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    if (state.isSending) {
                                        CircularProgressIndicator(
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Отправить"
                                        )
                                    }
                                }
                            }
                        } else {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "Канал открыт в режиме чтения",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when {
                    state.isLoading && state.messages.isEmpty() -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "Загрузка сообщений Telegram...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    state.error != null && state.messages.isEmpty() -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 24.dp)
                        ) {
                            Text(
                                text = state.error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            FilledTonalButton(onClick = onRefresh) {
                                Text("Повторить")
                            }
                        }
                    }

                    state.messages.isEmpty() -> {
                        Text(
                            text = "В этом чате пока нет сообщений",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    else -> {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (state.hasMoreOlder) {
                                item(key = "load_older_header") {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        if (state.isLoadingOlder) {
                                            CircularProgressIndicator(
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        } else {
                                            TextButton(onClick = onLoadOlder) {
                                                Text("Загрузить ранние сообщения")
                                            }
                                        }
                                    }
                                }
                            }

                            if (state.error != null) {
                                item(key = "chat_inline_error") {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = state.error,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }

                            itemsIndexed(
                                items = state.messages,
                                key = { _, msg -> msg.messageId }
                            ) { index, msg ->
                                val prevDay = state.messages.getOrNull(index - 1)?.let { formatChatDayKey(it.dateEpochSec) }
                                val curDay = formatChatDayKey(msg.dateEpochSec)
                                if (curDay != prevDay && msg.dateEpochSec > 0L) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = formatChatDateHeader(msg.dateEpochSec),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                TelegramMessageBubble(
                                    message = msg,
                                    isFocused = target.focusMessageId > 0L && msg.messageId == target.focusMessageId,
                                    currentTrack = currentTrack,
                                    isPlaying = isPlaying,
                                    onRequestPhoto = onRequestPhoto,
                                    onPlayAudio = { audio ->
                                        if (currentTrack?.trackId == audio.trackId) {
                                            onTogglePlayPause()
                                        } else {
                                            val queue = chatAudioQueue.ifEmpty { listOf(audio) }
                                            onPlayTrack(audio, queue)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelegramMessageBubble(
    message: TelegramChatMessage,
    isFocused: Boolean,
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    onRequestPhoto: (Long, Int) -> Unit,
    onPlayAudio: (AudioTrack) -> Unit
) {
    val context = LocalContext.current
    val isOutgoing = message.isOutgoing
    val bubbleColor = if (isOutgoing) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val textColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isOutgoing) 18.dp else 6.dp,
        bottomEnd = if (isOutgoing) 6.dp else 18.dp
    )

    LaunchedEffect(message.messageId, message.photoFileId, message.photoPath) {
        if (message.photoFileId > 0 && message.photoPath.isNullOrBlank()) {
            onRequestPhoto(message.messageId, message.photoFileId)
        }
    }

    Row(
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = bubbleShape,
            color = bubbleColor,
            border = if (isFocused) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier.widthIn(min = 96.dp, max = 330.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                if (!isOutgoing && message.senderName.isNotBlank()) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (message.photoFileId > 0) {
                    val photoRequest = remember(message.photoPath, context) {
                        message.photoPath?.takeIf { it.isNotBlank() }?.let { path ->
                            val data = if (path.startsWith("/")) File(path) else path
                            ImageRequest.Builder(context)
                                .data(data)
                                .crossfade(120)
                                .build()
                        }
                    }
                    if (photoRequest != null) {
                        AsyncImage(
                            model = photoRequest,
                            contentDescription = "Фото",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 260.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = secondaryColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Загрузка фото...",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = secondaryColor
                                )
                            }
                        }
                    }
                }

                message.audioTrack?.let { audio ->
                    val isCurrentAudio = currentTrack?.trackId == audio.trackId
                    val isThisPlaying = isCurrentAudio && isPlaying
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isOutgoing) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onPlayAudio(audio) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(
                                    imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isThisPlaying) "Пауза" else "Слушать",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = audio.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textColor,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = buildString {
                                        if (audio.author.isNotBlank()) append(audio.author)
                                        if (audio.durationSec > 0L) {
                                            if (isNotEmpty()) append(" · ")
                                            append(formatChatDuration(audio.durationSec))
                                        }
                                    }.ifBlank { "Аудиофайл" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = secondaryColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                if (message.mediaLabel.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = null,
                            tint = secondaryColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = message.mediaLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = secondaryColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (message.views > 0) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            tint = secondaryColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = formatViewsCount(message.views),
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryColor
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    if (message.dateEpochSec > 0L) {
                        Text(
                            text = formatChatTime(message.dateEpochSec),
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryColor
                        )
                    }
                }
            }
        }
    }
}

private fun formatChatTime(epochSec: Long): String {
    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    return fmt.format(Date(epochSec * 1000L))
}

private fun formatChatDayKey(epochSec: Long): String {
    if (epochSec <= 0L) return ""
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return fmt.format(Date(epochSec * 1000L))
}

private fun formatChatDateHeader(epochSec: Long): String {
    val msgCal = Calendar.getInstance().apply { timeInMillis = epochSec * 1000L }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val sameYear = msgCal.get(Calendar.YEAR) == today.get(Calendar.YEAR)
    return when {
        sameYear && msgCal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Сегодня"
        msgCal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
            msgCal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> "Вчера"
        sameYear -> SimpleDateFormat("d MMMM", Locale("ru")).format(Date(epochSec * 1000L))
        else -> SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date(epochSec * 1000L))
    }
}

private fun formatChatDuration(sec: Long): String {
    val total = sec.coerceAtLeast(0L)
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val s = total % 60L
    return if (h > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%d:%02d", m, s)
    }
}

private fun formatViewsCount(views: Int): String = when {
    views >= 1_000_000 -> String.format(Locale.US, "%.1fM", views / 1_000_000.0)
    views >= 1_000 -> String.format(Locale.US, "%.1fK", views / 1_000.0)
    else -> views.toString()
}
