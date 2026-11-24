package com.echogram.app.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echogram.app.data.model.AudioBookmark
import com.echogram.app.data.model.AudioTrack
import com.echogram.app.data.model.BookSeries

private enum class LibraryTab(val label: String) {
    ALL("Все книги"),
    SERIES("Серии"),
    OFFLINE("Офлайн"),
    FAVORITES("Избранное"),
    BOOKMARKS("Закладки")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    allTracks: List<AudioTrack>,
    series: List<BookSeries>,
    offlineTracks: List<AudioTrack>,
    favorites: List<AudioTrack>,
    bookmarks: List<AudioBookmark>,
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    favoriteIds: Set<String>,
    completedIds: Set<String>,
    offlineIds: Set<String>,
    downloadingProgress: Map<String, Float>,
    onPlayTrack: (AudioTrack, List<AudioTrack>) -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleFavorite: (AudioTrack) -> Unit,
    onDownloadOffline: (AudioTrack) -> Unit,
    onRemoveOffline: (String) -> Unit,
    onSeekToBookmark: (AudioBookmark) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    onOpenFullPlayer: () -> Unit
) {
    var tab by remember { mutableStateOf(LibraryTab.ALL) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Аудиотека", fontWeight = FontWeight.Bold) }
        )

        // Continue listening card
        currentTrack?.let { cur ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onOpenFullPlayer)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Headphones,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Продолжить слушать",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Text(
                            text = cur.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onTogglePlayPause) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Слушать",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LibraryTab.entries.take(4).forEach { t ->
                FilterChip(
                    selected = tab == t,
                    onClick = { tab = t },
                    label = { Text(t.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = tab == LibraryTab.BOOKMARKS,
                onClick = { tab = LibraryTab.BOOKMARKS },
                label = { Text("Закладки (${bookmarks.size})") }
            )
        }

        when (tab) {
            LibraryTab.ALL -> TrackListBody(
                tracks = allTracks,
                emptyText = "Отметьте звёздочкой каналы с аудиокнигами во вкладке «Чаты» — их книги появятся здесь.",
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                favoriteIds = favoriteIds,
                completedIds = completedIds,
                offlineIds = offlineIds,
                downloadingProgress = downloadingProgress,
                onPlayTrack = onPlayTrack,
                onTogglePlayPause = onTogglePlayPause,
                onToggleFavorite = onToggleFavorite,
                onDownloadOffline = onDownloadOffline
            )

            LibraryTab.SERIES -> {
                if (series.isEmpty()) {
                    EmptyCentered("Серии книг появятся автоматически, когда в каналах найдутся многосерийные главы.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(series, key = { it.id }) { s ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        s.tracks.firstOrNull()?.let { first -> onPlayTrack(first, s.tracks) }
                                    }
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(s.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "${s.channelTitle} · ${s.tracks.size} частей (прослушано ${s.completedCount})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            LibraryTab.OFFLINE -> {
                if (offlineTracks.isEmpty()) {
                    EmptyCentered("Нет скачанных офлайн-книг. Нажмите иконку загрузки у любого трека.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(offlineTracks, key = { it.trackId }) { t ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.weight(1f)) {
                                    AudioTrackCard(
                                        track = t,
                                        isCurrent = currentTrack?.trackId == t.trackId,
                                        isPlaying = isPlaying && currentTrack?.trackId == t.trackId,
                                        isFavorite = t.trackId in favoriteIds,
                                        isCompleted = t.trackId in completedIds,
                                        isOffline = true,
                                        downloadProgress = null,
                                        onPlay = {
                                            if (currentTrack?.trackId == t.trackId) onTogglePlayPause()
                                            else onPlayTrack(t, offlineTracks)
                                        },
                                        onToggleFavorite = { onToggleFavorite(t) },
                                        onDownload = {}
                                    )
                                }
                                IconButton(onClick = { onRemoveOffline(t.trackId) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Удалить")
                                }
                            }
                        }
                    }
                }
            }

            LibraryTab.FAVORITES -> TrackListBody(
                tracks = favorites,
                emptyText = "Добавляйте любимые аудиокниги в избранное сердечком.",
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                favoriteIds = favoriteIds,
                completedIds = completedIds,
                offlineIds = offlineIds,
                downloadingProgress = downloadingProgress,
                onPlayTrack = onPlayTrack,
                onTogglePlayPause = onTogglePlayPause,
                onToggleFavorite = onToggleFavorite,
                onDownloadOffline = onDownloadOffline
            )

            LibraryTab.BOOKMARKS -> {
                if (bookmarks.isEmpty()) {
                    EmptyCentered("Сохраняйте важные моменты в плеере с помощью закладок.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(bookmarks, key = { it.id }) { bm ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onSeekToBookmark(bm) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(bm.note, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            text = "${bm.trackTitle} · ${formatDurationSec(bm.positionSec)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(onClick = { onDeleteBookmark(bm.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Удалить")
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

@Composable
private fun TrackListBody(
    tracks: List<AudioTrack>,
    emptyText: String,
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    favoriteIds: Set<String>,
    completedIds: Set<String>,
    offlineIds: Set<String>,
    downloadingProgress: Map<String, Float>,
    onPlayTrack: (AudioTrack, List<AudioTrack>) -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleFavorite: (AudioTrack) -> Unit,
    onDownloadOffline: (AudioTrack) -> Unit
) {
    if (tracks.isEmpty()) {
        EmptyCentered(emptyText)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(tracks, key = { it.trackId }) { t ->
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
                    else onPlayTrack(t, tracks)
                },
                onToggleFavorite = { onToggleFavorite(t) },
                onDownload = { onDownloadOffline(t) }
            )
        }
    }
}

@Composable
private fun EmptyCentered(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
