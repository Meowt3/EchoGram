package com.tgaudiobooks.app.ui.screens

import androidx.compose.animation.AnimatedContent
import com.tgaudiobooks.app.ui.components.BubbleMotion
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tgaudiobooks.app.data.model.AudioTrack
import com.tgaudiobooks.app.data.model.TelegramChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun SearchScreen(
    selectedChannels: List<TelegramChannel>,
    allSourceTracks: List<AudioTrack>,
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    favoriteIds: Set<String>,
    completedIds: Set<String> = emptySet(),
    offlineIds: Set<String> = emptySet(),
    downloadingProgress: Map<String, Float> = emptyMap(),
    onPlayTrack: (AudioTrack, List<AudioTrack>) -> Unit,
    onToggleFavorite: (AudioTrack) -> Unit,
    onAddToPlaylist: (AudioTrack) -> Unit,
    onRequestCover: (AudioTrack) -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var channelFilter by remember { mutableStateOf("all") }

    LaunchedEffect(query) {
        val next = query.trim().lowercase()
        if (next.isEmpty()) {
            debouncedQuery = ""
        } else {
            delay(120L)
            debouncedQuery = next
        }
    }

    val filtered by produceState<List<AudioTrack>>(
        initialValue = emptyList(),
        debouncedQuery,
        channelFilter,
        allSourceTracks
    ) {
        if (debouncedQuery.isEmpty()) {
            value = emptyList()
        } else {
            val q = debouncedQuery
            val ch = channelFilter
            val source = allSourceTracks
            value = withContext(Dispatchers.Default) {
                source.filter { track ->
                    if (ch != "all" && track.channelId != ch) return@filter false
                    track.title.lowercase().contains(q) ||
                        track.author.lowercase().contains(q) ||
                        track.channelTitle.lowercase().contains(q) ||
                        track.description.lowercase().contains(q)
                }
            }
        }
    }
    val displayed = remember(filtered) { filtered.take(200) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Поиск по источникам", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = "Индексировано ${allSourceTracks.size} треков из ${selectedChannels.size} источников",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Настройки")
            }
        }
        Spacer(Modifier.height(14.dp))
        M3SearchBar(
            value = query,
            onValueChange = { query = it },
            placeholder = "Введите название книги, автора или тему..."
        )

        if (selectedChannels.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "chip_all") {
                    val active = channelFilter == "all"
                    val chipBg = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                    val chipFg = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = chipBg,
                        contentColor = chipFg,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { channelFilter = "all" }
                    ) {
                        Text(
                            text = "Все источники",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
                items(selectedChannels, key = { it.channelId }) { ch ->
                    val active = channelFilter == ch.channelId
                    val chipBg = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                    val chipFg = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = chipBg,
                        contentColor = chipFg,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { channelFilter = ch.channelId }
                    ) {
                        Text(
                            text = ch.title,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        val searchViewKey = when {
            selectedChannels.isEmpty() -> "no_sources"
            debouncedQuery.isEmpty() -> "idle"
            filtered.isEmpty() -> "empty"
            else -> "results"
        }

        AnimatedContent(
            targetState = searchViewKey,
            transitionSpec = { BubbleMotion.bubbleContentTransform() },
            modifier = Modifier.weight(1f),
            label = "search_state"
        ) { stateKey ->
            when (stateKey) {
                "no_sources" -> {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text("Источники еще не выбраны", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Подключите Telegram и выберите каналы, ботов или чаты в настройках, чтобы искать по их аудиотеке.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Button(onClick = onOpenSettings, shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Открыть настройки")
                            }
                        }
                    }
                }
                "idle" -> {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text("Начните ввод для поиска", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Введите имя автора, название книги или часть описания поста. Можно также отфильтровать по конкретному каналу, боту или чату выше.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                "empty" -> {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text("Ничего не найдено", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Попробуйте изменить запрос или выбрать другой источник.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = if (filtered.size > displayed.size) {
                                "Показано ${displayed.size} из ${filtered.size}"
                            } else {
                                "Найдено: ${filtered.size}"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(displayed, key = { it.trackId }) { track ->
                                AndroidTrackRow(
                                    track = track,
                                    isActive = currentTrack?.trackId == track.trackId,
                                    isPlaying = isPlaying,
                                    isFavorite = track.trackId in favoriteIds,
                                    isCompleted = track.trackId in completedIds,
                                    isOffline = track.trackId in offlineIds,
                                    downloadProgress = downloadingProgress[track.trackId],
                                    onClick = { onPlayTrack(track, filtered) },
                                    onToggleFavorite = { onToggleFavorite(track) },
                                    onAddToPlaylist = { onAddToPlaylist(track) },
                                    onRequestCover = onRequestCover
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
