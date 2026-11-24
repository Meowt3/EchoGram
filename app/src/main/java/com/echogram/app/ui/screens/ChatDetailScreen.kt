package com.echogram.app.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.echogram.app.data.model.AudioTrack
import com.echogram.app.data.model.ChatMessage
import com.echogram.app.ui.ActiveChatState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    state: ActiveChatState,
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    favoriteIds: Set<String>,
    completedIds: Set<String>,
    offlineIds: Set<String>,
    downloadingProgress: Map<String, Float>,
    onBack: () -> Unit,
    onToggleStar: () -> Unit,
    onLoadOlder: () -> Unit,
    onSendMessage: (String) -> Unit,
    onPlayTrack: (AudioTrack, List<AudioTrack>) -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleFavorite: (AudioTrack) -> Unit,
    onDownloadOffline: (AudioTrack) -> Unit
) {
    val chat = state.chat ?: return
    BackHandler(onBack = onBack)

    // 0 = Аудиотека канала, 1 = Лента чата Telegram
    var mode by remember(chat.chatId) { mutableIntStateOf(if (chat.isStarredAudiobookSource) 0 else 1) }
    var draft by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = {
                    Column {
                        Text(chat.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                        val sub = buildString {
                            if (chat.username.isNotBlank()) append("@${chat.username} · ")
                            append("${state.audioTracks.size} аудио")
                        }
                        Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = onToggleStar) {
                        Icon(
                            imageVector = if (chat.isStarredAudiobookSource) Icons.Default.Star else Icons.Outlined.StarOutline,
                            contentDescription = "В источники аудиокниг",
                            tint = if (chat.isStarredAudiobookSource) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                SegmentedButton(
                    selected = mode == 0,
                    onClick = { mode = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("Аудиокниги (${state.audioTracks.size})")
                }
                SegmentedButton(
                    selected = mode == 1,
                    onClick = { mode = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("Чат Telegram")
                }
            }

            if (mode == 0) {
                if (state.isLoadingAudio && state.audioTracks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (state.audioTracks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "В этом чате пока не найдено аудиофайлов.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.audioTracks, key = { it.trackId }) { track ->
                            AudioTrackCard(
                                track = track,
                                isCurrent = currentTrack?.trackId == track.trackId,
                                isPlaying = isPlaying && currentTrack?.trackId == track.trackId,
                                isFavorite = track.trackId in favoriteIds,
                                isCompleted = track.trackId in completedIds,
                                isOffline = track.trackId in offlineIds,
                                downloadProgress = downloadingProgress[track.trackId],
                                onPlay = {
                                    if (currentTrack?.trackId == track.trackId) onTogglePlayPause()
                                    else onPlayTrack(track, state.audioTracks)
                                },
                                onToggleFavorite = { onToggleFavorite(track) },
                                onDownload = { onDownloadOffline(track) }
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "load_older") {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (state.isLoadingMessages) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            } else {
                                TextButton(onClick = onLoadOlder) {
                                    Text("Загрузить более ранние сообщения")
                                }
                            }
                        }
                    }
                    items(state.messages, key = { it.messageId }) { msg ->
                        ChatBubble(
                            msg = msg,
                            isCurrentAudio = msg.audioTrack != null && currentTrack?.trackId == msg.audioTrack.trackId,
                            isPlaying = isPlaying && msg.audioTrack != null && currentTrack?.trackId == msg.audioTrack.trackId,
                            onPlayAudio = { track ->
                                if (currentTrack?.trackId == track.trackId) onTogglePlayPause()
                                else onPlayTrack(track, state.audioTracks.ifEmpty { listOf(track) })
                            }
                        )
                    }
                }

                if (state.canSend) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            placeholder = { Text("Сообщение или команда боту...") },
                            singleLine = true,
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            enabled = draft.isNotBlank(),
                            onClick = {
                                val text = draft.trim()
                                draft = ""
                                onSendMessage(text)
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AudioTrackCard(
    track: AudioTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    isCompleted: Boolean,
    isOffline: Boolean,
    downloadProgress: Float?,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onPlay)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isCompleted) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Прослушано",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                val meta = buildString {
                    if (track.author.isNotBlank()) append(track.author)
                    if (track.durationSec > 0L) {
                        if (isNotEmpty()) append(" · ")
                        append(formatDurationSec(track.durationSec))
                    }
                }
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Избранное",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDownload, enabled = !isOffline && downloadProgress == null) {
                when {
                    downloadProgress != null -> CircularProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    isOffline -> Icon(
                        Icons.Default.DownloadDone,
                        contentDescription = "Скачано",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    else -> Icon(Icons.Default.Download, contentDescription = "Скачать офлайн")
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    msg: ChatMessage,
    isCurrentAudio: Boolean,
    isPlaying: Boolean,
    onPlayAudio: (AudioTrack) -> Unit
) {
    val bg = if (msg.isOutgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (msg.isOutgoing) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = bg,
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (msg.senderName.isNotBlank() && !msg.isOutgoing) {
                    Text(
                        text = msg.senderName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                val photoFile = msg.photoPath?.let { File(it) }?.takeIf { it.exists() }
                if (photoFile != null) {
                    AsyncImage(
                        model = photoFile,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
                msg.audioTrack?.let { audio ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPlayAudio(audio) }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = audio.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isCurrentAudio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                if (audio.durationSec > 0L) {
                                    Text(
                                        text = formatDurationSec(audio.durationSec),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(Icons.Default.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (msg.text.isNotBlank()) {
                    Text(text = msg.text, style = MaterialTheme.typography.bodyMedium)
                } else if (msg.mediaLabel.isNotBlank()) {
                    Text(
                        text = msg.mediaLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

fun formatDurationSec(sec: Long): String {
    val s = sec.coerceAtLeast(0L)
    val h = s / 3600
    val m = (s % 3600) / 60
    val r = s % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, r) else String.format("%d:%02d", m, r)
}
