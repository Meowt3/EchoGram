package com.tgaudiobooks.app.ui.screens

import androidx.compose.animation.AnimatedContent
import com.tgaudiobooks.app.ui.components.BubbleMotion
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tgaudiobooks.app.data.model.AudioTrack
import com.tgaudiobooks.app.data.model.BookSeriesGroup
import com.tgaudiobooks.app.data.model.LibrarySubTab
import com.tgaudiobooks.app.data.model.ReadingSummary
import com.tgaudiobooks.app.data.model.TelegramChannel
import com.tgaudiobooks.app.data.model.UserPlaylist
import java.io.File

@Composable
fun LibraryScreen(
    activeSection: LibrarySubTab?,
    onActiveSectionChange: (LibrarySubTab?) -> Unit,
    openedChannel: TelegramChannel?,
    onOpenedChannelChange: (TelegramChannel?) -> Unit,
    openedPlaylist: UserPlaylist?,
    onOpenedPlaylistChange: (UserPlaylist?) -> Unit,
    favorites: List<AudioTrack>,
    playlists: List<UserPlaylist>,
    selectedChannels: List<TelegramChannel>,
    tracksByChannel: Map<String, List<AudioTrack>>,
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    favoriteIds: Set<String>,
    completedIds: Set<String> = emptySet(),
    offlineTracks: List<AudioTrack> = emptyList(),
    offlineIds: Set<String> = emptySet(),
    downloadingProgress: Map<String, Float> = emptyMap(),
    seriesGroups: List<BookSeriesGroup> = emptyList(),
    readingSummary: ReadingSummary = ReadingSummary(),
    onPlayTrack: (AudioTrack, List<AudioTrack>) -> Unit,
    onToggleFavorite: (AudioTrack) -> Unit,
    onAddToPlaylist: (AudioTrack) -> Unit,
    onCreatePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onRemoveFromPlaylist: (String, String) -> Unit,
    onRemoveOffline: (String) -> Unit = {},
    onClearOffline: () -> Unit = {},
    onRequestCover: (AudioTrack) -> Unit = {},
    onOpenTelegramChat: (TelegramChannel) -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val subTabs = LibrarySubTab.entries
    var channelTrackSearch by remember(openedChannel?.channelId) { mutableStateOf("") }
    var playlistTrackSearch by remember(openedPlaylist?.id) { mutableStateOf("") }
    var showNewPlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var newPlaylistDesc by remember { mutableStateOf("") }

    val screenKey = when {
        openedChannel != null -> "channel:${openedChannel.channelId}"
        openedPlaylist != null -> "playlist:${openedPlaylist.id}"
        activeSection != null -> "section:${activeSection.name}"
        else -> "hub"
    }

    AnimatedContent(
        targetState = screenKey,
        transitionSpec = { BubbleMotion.bubbleContentTransform() },
        label = "library_nav"
    ) { state ->
        if (state.startsWith("channel:") && openedChannel != null) {
            val allChannelTracks = tracksByChannel[openedChannel.channelId].orEmpty()
            val tracks = remember(allChannelTracks, channelTrackSearch) {
                val q = channelTrackSearch.trim().lowercase()
                if (q.isEmpty()) allChannelTracks
                else allChannelTracks.filter {
                    it.title.lowercase().contains(q) ||
                        it.author.lowercase().contains(q) ||
                        it.description.lowercase().contains(q)
                }
            }
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Icon(
                                    imageVector = sourceTypeIcon(openedChannel.sourceType),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = openedChannel.sourceType.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = buildString {
                                        if (openedChannel.username.isNotBlank()) append("@${openedChannel.username} · ")
                                        append("${allChannelTracks.size} аудио")
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (allChannelTracks.isNotEmpty()) {
                                Button(
                                    onClick = { onPlayTrack(allChannelTracks.first(), allChannelTracks) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Слушать", maxLines = 1)
                                }
                            }
                            FilledTonalButton(
                                onClick = { onOpenTelegramChat(openedChannel) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Открыть чат", maxLines = 1)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                M3SearchBar(
                    value = channelTrackSearch,
                    onValueChange = { channelTrackSearch = it },
                    placeholder = "Поиск внутри источника..."
                )
                Spacer(Modifier.height(12.dp))
                if (tracks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (channelTrackSearch.isBlank()) "Аудиофайлы в этом источнике не найдены" else "Ничего не найдено",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(tracks, key = { it.trackId }) { track ->
                            AndroidTrackRow(
                                track = track,
                                isActive = currentTrack?.trackId == track.trackId,
                                isPlaying = isPlaying,
                                isFavorite = track.trackId in favoriteIds,
                                isCompleted = track.trackId in completedIds,
                                isOffline = track.trackId in offlineIds,
                                downloadProgress = downloadingProgress[track.trackId],
                                onClick = { onPlayTrack(track, allChannelTracks) },
                                onToggleFavorite = { onToggleFavorite(track) },
                                onAddToPlaylist = { onAddToPlaylist(track) },
                                onRequestCover = onRequestCover
                            )
                        }
                    }
                }
            }
            return@AnimatedContent
        }

        if (state.startsWith("playlist:") && openedPlaylist != null) {
            val filteredItems = remember(openedPlaylist.items, playlistTrackSearch) {
                val q = playlistTrackSearch.trim().lowercase()
                if (q.isEmpty()) {
                    openedPlaylist.items
                } else {
                    openedPlaylist.items.filter {
                        it.title.lowercase().contains(q) ||
                            it.author.lowercase().contains(q) ||
                            it.channelTitle.lowercase().contains(q)
                    }
                }
            }

            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        CoverArtworkBox(
                            coverPath = openedPlaylist.items.firstOrNull()?.coverPath,
                            contentDescription = openedPlaylist.name,
                            size = 54.dp,
                            cornerRadius = 14.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${openedPlaylist.items.size} аудио",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (openedPlaylist.description.isNotBlank()) {
                                Text(
                                    text = openedPlaylist.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (openedPlaylist.items.isNotEmpty()) {
                            Button(
                                onClick = { onPlayTrack(openedPlaylist.items.first(), openedPlaylist.items) },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Играть")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                M3SearchBar(
                    value = playlistTrackSearch,
                    onValueChange = { playlistTrackSearch = it },
                    placeholder = "Поиск в плейлисте..."
                )
                Spacer(Modifier.height(12.dp))
                if (filteredItems.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (openedPlaylist.items.isEmpty()) {
                                "Плейлист пока пуст. Добавляйте аудио через кнопку «+» у любого трека."
                            } else {
                                "По вашему запросу ничего не найдено."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredItems, key = { it.trackId }) { track ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    AndroidTrackRow(
                                        track = track,
                                        isActive = currentTrack?.trackId == track.trackId,
                                        isPlaying = isPlaying,
                                        isFavorite = track.trackId in favoriteIds,
                                        isCompleted = track.trackId in completedIds,
                                        isOffline = track.trackId in offlineIds,
                                        downloadProgress = downloadingProgress[track.trackId],
                                        onClick = { onPlayTrack(track, openedPlaylist.items) },
                                        onToggleFavorite = { onToggleFavorite(track) },
                                        onAddToPlaylist = { onAddToPlaylist(track) },
                                        onRequestCover = onRequestCover
                                    )
                                }
                                IconButton(onClick = { onRemoveFromPlaylist(openedPlaylist.id, track.trackId) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Убрать из плейлиста",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            return@AnimatedContent
        }

        if (state == "hub" || activeSection == null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Библиотека", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Выберите раздел для просмотра, офлайн-доступа и статистики",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Настройки")
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                items(subTabs, key = { it.name }) { tab ->
                    val (countLabel, icon, badgeBg, badgeFg) = when (tab) {
                        LibrarySubTab.FAVORITES -> Quad(
                            "${favorites.size} книг",
                            Icons.Default.Favorite,
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        LibrarySubTab.PLAYLISTS -> Quad(
                            "${playlists.size} плейлистов",
                            Icons.AutoMirrored.Filled.QueueMusic,
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        LibrarySubTab.DOWNLOADS -> Quad(
                            "${offlineTracks.size} аудио офлайн",
                            Icons.Default.DownloadDone,
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        LibrarySubTab.SERIES -> Quad(
                            "${seriesGroups.size} циклов и серий",
                            Icons.Default.FolderCopy,
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        LibrarySubTab.STATS -> Quad(
                            "${formatMinutesRu(readingSummary.totalSeconds)} · серия ${readingSummary.streakDays} дн.",
                            Icons.Default.BarChart,
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        LibrarySubTab.CHANNELS -> Quad(
                            "${selectedChannels.size} каналов, ботов и чатов",
                            Icons.Default.Radio,
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .clickable { onActiveSectionChange(tab) }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(badgeBg)
                            ) {
                                Icon(icon, contentDescription = null, tint = badgeFg, modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tab.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    text = countLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            return@AnimatedContent
        }

        when (activeSection) {
            LibrarySubTab.FAVORITES -> {
                var favQuery by remember { mutableStateOf("") }
                val filtered = remember(favorites, favQuery) {
                    val q = favQuery.trim().lowercase()
                    if (q.isEmpty()) favorites
                    else favorites.filter {
                        it.title.lowercase().contains(q) ||
                            it.author.lowercase().contains(q) ||
                            it.channelTitle.lowercase().contains(q)
                    }
                }
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    M3SearchBar(
                        value = favQuery,
                        onValueChange = { favQuery = it },
                        placeholder = "Поиск в избранном..."
                    )
                    Spacer(Modifier.height(12.dp))
                    if (filtered.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = if (favorites.isEmpty()) "В избранном пока пусто" else "Ничего не найдено",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (favorites.isEmpty()) {
                                        "Нажмите на сердечко у любой аудиокниги, чтобы сохранить её здесь."
                                    } else {
                                        "Попробуйте другой поисковый запрос."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filtered, key = { it.trackId }) { track ->
                                AndroidTrackRow(
                                    track = track,
                                    isActive = currentTrack?.trackId == track.trackId,
                                    isPlaying = isPlaying,
                                    isFavorite = true,
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

            LibrarySubTab.PLAYLISTS -> {
                var plQuery by remember { mutableStateOf("") }
                val filtered = remember(playlists, plQuery) {
                    val q = plQuery.trim().lowercase()
                    if (q.isEmpty()) playlists
                    else playlists.filter { pl ->
                        pl.name.lowercase().contains(q) ||
                            pl.description.lowercase().contains(q) ||
                            pl.items.any { it.title.lowercase().contains(q) }
                    }
                }
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            M3SearchBar(
                                value = plQuery,
                                onValueChange = { plQuery = it },
                                placeholder = "Поиск по плейлистам..."
                            )
                        }
                        FilledTonalButton(
                            onClick = { showNewPlaylistDialog = true },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.height(50.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Создать")
                            Spacer(Modifier.width(4.dp))
                            Text("Создать")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (filtered.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = if (playlists.isEmpty()) "Плейлистов пока нет" else "Плейлисты не найдены",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Создайте плейлист и добавляйте туда аудиокниги из любого источника.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filtered, key = { it.id }) { pl ->
                                Surface(
                                    shape = RoundedCornerShape(22.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(22.dp))
                                        .clickable { onOpenedPlaylistChange(pl) }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(14.dp)
                                    ) {
                                        CoverArtworkBox(
                                            coverPath = pl.items.firstOrNull()?.coverPath,
                                            contentDescription = pl.name,
                                            size = 58.dp,
                                            cornerRadius = 14.dp
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = pl.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${pl.items.size} аудио",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (pl.items.isNotEmpty()) {
                                            Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clickable { onPlayTrack(pl.items.first(), pl.items) }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = "Играть", modifier = Modifier.size(20.dp))
                                                }
                                            }
                                            Spacer(Modifier.width(4.dp))
                                        }
                                        IconButton(onClick = { onDeletePlaylist(pl.id) }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Удалить плейлист",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            LibrarySubTab.CHANNELS -> {
                var chQuery by remember { mutableStateOf("") }
                val filtered = remember(selectedChannels, chQuery) {
                    val q = chQuery.trim().lowercase()
                    if (q.isEmpty()) selectedChannels
                    else selectedChannels.filter {
                        it.title.lowercase().contains(q) ||
                            it.username.lowercase().contains(q)
                    }
                }
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    M3SearchBar(
                        value = chQuery,
                        onValueChange = { chQuery = it },
                        placeholder = "Поиск по каналам, ботам и чатам..."
                    )
                    Spacer(Modifier.height(12.dp))
                    if (filtered.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(Icons.Default.Radio, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = if (selectedChannels.isEmpty()) "Источники не выбраны" else "Источники не найдены",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Выберите каналы, ботов или чаты Telegram в настройках, чтобы видеть здесь их аудиотеки.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filtered, key = { it.channelId }) { ch ->
                                val context = LocalContext.current
                                val tracks = tracksByChannel[ch.channelId].orEmpty()
                                val firstCover = tracks.firstOrNull { !it.coverPath.isNullOrBlank() }?.coverPath
                                Surface(
                                    shape = RoundedCornerShape(22.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(22.dp))
                                        .clickable { onOpenedChannelChange(ch) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer)
                                        ) {
                                            val channelCoverRequest = remember(firstCover, context) {
                                                firstCover?.takeIf { it.isNotBlank() }?.let { path ->
                                                    val data = if (path.startsWith("/")) File(path) else path
                                                    ImageRequest.Builder(context)
                                                        .data(data)
                                                        .size(160)
                                                        .crossfade(100)
                                                        .build()
                                                }
                                            }
                                            if (channelCoverRequest != null) {
                                                AsyncImage(
                                                    model = channelCoverRequest,
                                                    contentDescription = ch.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = sourceTypeIcon(ch.sourceType),
                                                    contentDescription = ch.sourceType.label,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = ch.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val subtitle = buildString {
                                                append(ch.sourceType.label)
                                                if (ch.username.isNotBlank()) append(" · @${ch.username}")
                                                append(" · ${tracks.size} аудио")
                                            }
                                            Text(
                                                text = subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable { onOpenTelegramChat(ch) }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.Send,
                                                    contentDescription = "Открыть чат Telegram",
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        if (tracks.isNotEmpty()) {
                                            Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .clickable { onPlayTrack(tracks.first(), tracks) }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = "Слушать", modifier = Modifier.size(20.dp))
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

            LibrarySubTab.DOWNLOADS -> {
                var dlQuery by remember { mutableStateOf("") }
                val filtered = remember(offlineTracks, dlQuery) {
                    val q = dlQuery.trim().lowercase()
                    if (q.isEmpty()) offlineTracks
                    else offlineTracks.filter {
                        it.title.lowercase().contains(q) ||
                            it.author.lowercase().contains(q) ||
                            it.channelTitle.lowercase().contains(q)
                    }
                }
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            M3SearchBar(
                                value = dlQuery,
                                onValueChange = { dlQuery = it },
                                placeholder = "Поиск по скачанному..."
                            )
                        }
                        if (offlineTracks.isNotEmpty()) {
                            TextButton(onClick = onClearOffline) {
                                Text("Очистить")
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (filtered.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(Icons.Default.DownloadDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = if (offlineTracks.isEmpty()) "Нет скачанных аудиокниг" else "Ничего не найдено",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Откройте плеер и нажмите иконку загрузки, чтобы слушать книгу без интернета.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filtered, key = { it.trackId }) { track ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        AndroidTrackRow(
                                            track = track,
                                            isActive = currentTrack?.trackId == track.trackId,
                                            isPlaying = isPlaying,
                                            isFavorite = track.trackId in favoriteIds,
                                            isCompleted = track.trackId in completedIds,
                                            isOffline = true,
                                            downloadProgress = downloadingProgress[track.trackId],
                                            onClick = { onPlayTrack(track, offlineTracks) },
                                            onToggleFavorite = { onToggleFavorite(track) },
                                            onAddToPlaylist = { onAddToPlaylist(track) },
                                            onRequestCover = onRequestCover
                                        )
                                    }
                                    IconButton(onClick = { onRemoveOffline(track.trackId) }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Удалить из офлайн",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            LibrarySubTab.SERIES -> {
                var seriesQuery by remember { mutableStateOf("") }
                var expandedKey by remember { mutableStateOf<String?>(null) }
                val filtered = remember(seriesGroups, seriesQuery) {
                    val q = seriesQuery.trim().lowercase()
                    if (q.isEmpty()) seriesGroups
                    else seriesGroups.filter {
                        it.title.lowercase().contains(q) ||
                            it.author.lowercase().contains(q)
                    }
                }
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    M3SearchBar(
                        value = seriesQuery,
                        onValueChange = { seriesQuery = it },
                        placeholder = "Поиск по сериям и циклам..."
                    )
                    Spacer(Modifier.height(12.dp))
                    if (filtered.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(Icons.Default.FolderCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = if (seriesGroups.isEmpty()) "Многотомные серии не найдены" else "Серии не найдены",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Когда в каналах есть книги с нумерацией частей или глав, алгоритм соберёт их в общие циклы.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filtered, key = { it.key }) { group ->
                                val isExpanded = expandedKey == group.key
                                Surface(
                                    shape = RoundedCornerShape(22.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable { expandedKey = if (isExpanded) null else group.key }
                                        ) {
                                            CoverArtworkBox(
                                                coverPath = group.coverPath,
                                                contentDescription = group.title,
                                                size = 56.dp,
                                                cornerRadius = 14.dp
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = group.title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${group.author} · ${group.tracks.size} частей · ${group.progressPercent}%",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .clickable {
                                                        val nextUnfinished = group.tracks.firstOrNull { it.trackId !in completedIds }
                                                            ?: group.tracks.firstOrNull()
                                                        if (nextUnfinished != null) onPlayTrack(nextUnfinished, group.tracks)
                                                    }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = "Слушать серию", modifier = Modifier.size(20.dp))
                                                }
                                            }
                                            IconButton(onClick = { expandedKey = if (isExpanded) null else group.key }) {
                                                Icon(
                                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                    contentDescription = null
                                                )
                                            }
                                        }
                                        LinearProgressIndicator(
                                            progress = { (group.progressPercent / 100f).coerceIn(0f, 1f) },
                                            strokeCap = StrokeCap.Round,
                                            modifier = Modifier.fillMaxWidth().height(4.dp)
                                        )
                                        if (isExpanded) {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                group.tracks.forEach { track ->
                                                    AndroidTrackRow(
                                                        track = track,
                                                        isActive = currentTrack?.trackId == track.trackId,
                                                        isPlaying = isPlaying,
                                                        isFavorite = track.trackId in favoriteIds,
                                                        isCompleted = track.trackId in completedIds,
                                                        isOffline = track.trackId in offlineIds,
                                                        downloadProgress = downloadingProgress[track.trackId],
                                                        onClick = { onPlayTrack(track, group.tracks) },
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
                }
            }

            LibrarySubTab.STATS -> {
                val maxDaySec = readingSummary.last7Days.maxOfOrNull { it.seconds }?.coerceAtLeast(60L) ?: 60L
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            StatMetricCard(
                                label = "Всего",
                                value = formatMinutesRu(readingSummary.totalSeconds),
                                modifier = Modifier.weight(1f)
                            )
                            StatMetricCard(
                                label = "Серия",
                                value = "${readingSummary.streakDays} дн.",
                                modifier = Modifier.weight(1f)
                            )
                            StatMetricCard(
                                label = "Дочитано",
                                value = "${readingSummary.completedCount}",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Активность за 7 дней", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Сегодня: ${formatMinutesRu(readingSummary.todaySeconds)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Bottom,
                                    modifier = Modifier.fillMaxWidth().height(132.dp)
                                ) {
                                    readingSummary.last7Days.forEach { day ->
                                        val ratio = (day.seconds.toFloat() / maxDaySec.toFloat()).coerceIn(0f, 1f)
                                        val barHeight = (ratio * 84f).coerceAtLeast(if (day.seconds > 0L) 8f else 4f).dp
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Bottom,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = if (day.seconds >= 60L) "${day.seconds / 60L}м" else "0",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(0.65f)
                                                    .height(barHeight)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        if (day.seconds > 0L) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.surfaceContainerHighest
                                                    )
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = day.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (readingSummary.topAuthors.isNotEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Любимые авторы и чтецы", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    readingSummary.topAuthors.forEach { (author, secs) ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = author,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                                            )
                                            Text(
                                                text = formatMinutesRu(secs),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.primary
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
    }

    if (showNewPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showNewPlaylistDialog = false },
            title = { Text("Новый плейлист") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Название") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPlaylistDesc,
                        onValueChange = { newPlaylistDesc = it },
                        label = { Text("Описание (необязательно)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            onCreatePlaylist(newPlaylistName, newPlaylistDesc)
                            newPlaylistName = ""
                            newPlaylistDesc = ""
                            showNewPlaylistDialog = false
                        }
                    }
                ) { Text("Создать") }
            },
            dismissButton = {
                TextButton(onClick = { showNewPlaylistDialog = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun StatMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatMinutesRu(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0L)
    val hrs = safe / 3600L
    val mins = (safe % 3600L) / 60L
    return when {
        hrs > 0L -> "$hrs ч $mins м"
        mins > 0L -> "$mins мин"
        else -> "0 мин"
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
