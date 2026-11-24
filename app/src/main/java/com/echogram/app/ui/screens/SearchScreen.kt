package com.echogram.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echogram.app.data.model.AudioTrack
import com.echogram.app.data.model.TelegramChat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    allTracks: List<AudioTrack>,
    globalChats: List<TelegramChat>,
    isSearchingGlobal: Boolean,
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    favoriteIds: Set<String>,
    completedIds: Set<String>,
    offlineIds: Set<String>,
    downloadingProgress: Map<String, Float>,
    onQueryGlobalChats: (String) -> Unit,
    onJoinAndOpenChat: (TelegramChat) -> Unit,
    onPlayTrack: (AudioTrack, List<AudioTrack>) -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleFavorite: (AudioTrack) -> Unit,
    onDownloadOffline: (AudioTrack) -> Unit
) {
    var query by remember { mutableStateOf("") }

    val localMatches = remember(allTracks, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) emptyList()
        else allTracks.filter {
            it.title.lowercase().contains(q) ||
                it.author.lowercase().contains(q) ||
                it.channelTitle.lowercase().contains(q)
        }.take(60)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Поиск", fontWeight = FontWeight.Bold) })

        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                onQueryGlobalChats(it)
            },
            placeholder = { Text("Книга, автор или @канал Telegram...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = ""; onQueryGlobalChats("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Очистить")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isSearchingGlobal) {
                item(key = "global_loading") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Поиск публичных каналов Telegram...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (globalChats.isNotEmpty()) {
                item(key = "hdr_global") {
                    Text(
                        text = "Каналы и боты Telegram",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(globalChats, key = { "g_${it.chatId}" }) { ch ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onJoinAndOpenChat(ch) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ch.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (ch.username.isNotBlank()) {
                                    Text("@${ch.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            FilledTonalButton(onClick = { onJoinAndOpenChat(ch) }) {
                                Text("Открыть")
                            }
                        }
                    }
                }
            }

            if (localMatches.isNotEmpty()) {
                item(key = "hdr_local") {
                    Text(
                        text = "Аудиокниги в ваших источниках (${localMatches.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(localMatches, key = { it.trackId }) { t ->
                    AudioTrackCard(
                        track = t,
                        isCurrent = currentTrack?.trackId == t.trackId,
                        isPlaying = isPlaying && currentTrack?.trackId == t.trackId,
                        isFavorite = t.trackId in favoriteIds,
                        isCompleted = t.trackId in completedIds,
                        isOffline = t.trackId in offlineIds,
                        downloadProgress = downloadingProgress[t.trackId],
                        onPlay = {
                            if (currentTrack?.trackId == t.trackId) onTogglePlayPause()
                            else onPlayTrack(t, localMatches)
                        },
                        onToggleFavorite = { onToggleFavorite(t) },
                        onDownload = { onDownloadOffline(t) }
                    )
                }
            }
        }
    }
}
