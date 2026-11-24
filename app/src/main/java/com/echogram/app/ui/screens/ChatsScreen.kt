package com.echogram.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.echogram.app.data.model.ChatKind
import com.echogram.app.data.model.ConnectionStatus
import com.echogram.app.data.model.TelegramChat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ChatFolder(val label: String) {
    ALL("Все"),
    AUDIOBOOKS("Аудиокниги"),
    CHANNELS("Каналы"),
    GROUPS("Группы"),
    BOTS("Боты"),
    ARCHIVE("Архив")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    chats: List<TelegramChat>,
    isLoading: Boolean,
    connectionStatus: ConnectionStatus,
    onRefresh: () -> Unit,
    onOpenChat: (TelegramChat) -> Unit,
    onToggleStarred: (TelegramChat) -> Unit
) {
    var folder by remember { mutableStateOf(ChatFolder.ALL) }

    val filtered = remember(chats, folder) {
        when (folder) {
            ChatFolder.ALL -> chats.filterNot { it.isArchived }
            ChatFolder.AUDIOBOOKS -> chats.filter { it.isStarredAudiobookSource || it.kind == ChatKind.SAVED_MESSAGES }
            ChatFolder.CHANNELS -> chats.filter { !it.isArchived && it.kind == ChatKind.CHANNEL }
            ChatFolder.GROUPS -> chats.filter { !it.isArchived && it.kind == ChatKind.GROUP }
            ChatFolder.BOTS -> chats.filter { !it.isArchived && it.kind == ChatKind.BOT }
            ChatFolder.ARCHIVE -> chats.filter { it.isArchived }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("EchoGram", fontWeight = FontWeight.Bold)
                    Text(
                        text = connectionStatus.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            actions = {
                IconButton(onClick = onRefresh) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                }
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChatFolder.entries.forEach { f ->
                FilterChip(
                    selected = folder == f,
                    onClick = { folder = f },
                    label = { Text(f.label) }
                )
            }
        }

        if (filtered.isEmpty() && !isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (folder == ChatFolder.AUDIOBOOKS) {
                        "Нажмите звёздочку рядом с любым каналом или ботом, чтобы добавить его в источники аудиокниг."
                    } else {
                        "Чаты не найдены. Нажмите обновить или найдите каналы во вкладке «Поиск»."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filtered, key = { it.chatId }) { chat ->
                    ChatRowItem(
                        chat = chat,
                        onClick = { onOpenChat(chat) },
                        onToggleStar = { onToggleStarred(chat) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 78.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatRowItem(
    chat: TelegramChat,
    onClick: () -> Unit,
    onToggleStar: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            val avatarFile = chat.avatarPath?.let { File(it) }?.takeIf { it.exists() }
            if (avatarFile != null) {
                AsyncImage(
                    model = avatarFile,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = when (chat.kind) {
                        ChatKind.SAVED_MESSAGES -> Icons.Default.Bookmark
                        ChatKind.CHANNEL -> Icons.Default.Campaign
                        ChatKind.GROUP -> Icons.Default.Group
                        ChatKind.BOT -> Icons.Default.SmartToy
                        ChatKind.USER -> Icons.Default.Person
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = chat.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (chat.lastMessageDateSec > 0L) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatShortTime(chat.lastMessageDateSec),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = chat.lastMessagePreview.ifBlank {
                        if (chat.username.isNotBlank()) "@${chat.username}" else "Открыть чат"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (chat.unreadCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = chat.unreadCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        IconButton(onClick = onToggleStar) {
            Icon(
                imageVector = if (chat.isStarredAudiobookSource) Icons.Default.Star else Icons.Outlined.StarOutline,
                contentDescription = "Источник аудиокниг",
                tint = if (chat.isStarredAudiobookSource) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatShortTime(epochSec: Long): String {
    return runCatching {
        val date = Date(epochSec * 1000L)
        SimpleDateFormat("dd.MM", Locale.getDefault()).format(date)
    }.getOrDefault("")
}
