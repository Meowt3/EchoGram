package com.tgaudiobooks.app.ui.screens

import androidx.compose.animation.AnimatedContent
import com.tgaudiobooks.app.ui.components.BubbleMotion
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tgaudiobooks.app.data.model.AudioTrack
import com.tgaudiobooks.app.data.model.LibrarySubTab
import com.tgaudiobooks.app.data.model.MixCollection
import com.tgaudiobooks.app.data.model.SourceType
import com.tgaudiobooks.app.data.model.UserPlaylist
import com.tgaudiobooks.app.ui.components.PlayingEqualizerBars
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

fun formatTrackDuration(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0L)
    val hrs = safe / 3600L
    val mins = (safe % 3600L) / 60L
    val secs = safe % 60L
    return if (hrs > 0L) {
        String.format("%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format("%d:%02d", mins, secs)
    }
}

fun sourceTypeIcon(type: SourceType): ImageVector = when (type) {
    SourceType.BOT -> Icons.Default.SmartToy
    SourceType.GROUP -> Icons.Default.Groups
    SourceType.USER -> Icons.Default.ChatBubbleOutline
    SourceType.CHANNEL -> Icons.Default.Radio
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun M3SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .bringIntoViewRequester(bringIntoViewRequester)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier.weight(1f)
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { state ->
                            if (state.isFocused) {
                                coroutineScope.launch {
                                    delay(220L)
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        }
                )
            }
            if (value.isNotEmpty()) {
                IconButton(
                    onClick = { onValueChange("") },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Очистить",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    isTelegramConnected: Boolean,
    isLoadingSources: Boolean,
    selectedChannelsCount: Int,
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    positionMsFlow: StateFlow<Long>,
    durationMsFlow: StateFlow<Long>,
    mixes: List<MixCollection>,
    autoPlaylists: List<MixCollection>,
    playlists: List<UserPlaylist>,
    tracks: List<AudioTrack>,
    favoriteIds: Set<String>,
    completedIds: Set<String> = emptySet(),
    offlineIds: Set<String> = emptySet(),
    downloadingProgress: Map<String, Float> = emptyMap(),
    openedMixId: String?,
    onOpenMixId: (String?) -> Unit,
    onPlayTrack: (AudioTrack, List<AudioTrack>) -> Unit,
    onPlayShuffled: (List<AudioTrack>) -> Unit,
    onToggleFavorite: (AudioTrack) -> Unit,
    onAddToPlaylist: (AudioTrack) -> Unit,
    onSaveAutoPlaylist: (MixCollection) -> Unit,
    onShuffleRecommendations: () -> Unit,
    onRequestCover: (AudioTrack) -> Unit = {},
    onOpenPlaylistDetail: (String) -> Unit,
    onOpenLibrarySection: (LibrarySubTab) -> Unit = {},
    onTogglePlayPause: () -> Unit = {},
    onOpenFullPlayer: () -> Unit = {},
    onRefreshTracks: () -> Unit,
    onOpenSettings: () -> Unit,
    tasteClusters: List<String> = emptyList()
) {
    val allCollections = remember(mixes, autoPlaylists) { mixes + autoPlaylists }
    val openedMix = remember(allCollections, openedMixId) {
        allCollections.firstOrNull { it.id == openedMixId }
    }
    val autoPlaylistRows = remember(autoPlaylists) { autoPlaylists.chunked(2) }
    val savedPlaylistNames = remember(playlists) {
        playlists.mapTo(HashSet(playlists.size)) { it.name.lowercase() }
    }
    val screenMode = when {
        !isTelegramConnected || selectedChannelsCount == 0 -> "empty"
        openedMix != null -> "detail:${openedMix.id}"
        else -> "feed"
    }

    AnimatedContent(
        targetState = screenMode,
        transitionSpec = { BubbleMotion.bubbleContentTransform() },
        label = "home_screen_mode"
    ) { mode ->
        when {
            mode == "empty" -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Radio,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Text(
                                text = if (!isTelegramConnected) {
                                    "Подключите ваш аккаунт Telegram"
                                } else {
                                    "Выберите источники аудиокниг"
                                },
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (!isTelegramConnected) {
                                    "Войдите по номеру телефона через официальный протокол MTProto и отметьте каналы, ботов или чаты с аудиокнигами — здесь сразу появятся миксы, новинки и авто-плейлисты."
                                } else {
                                    "Аккаунт Telegram подключен. Откройте настройки и отметьте каналы, ботов или чаты, из которых нужно загружать аудиокниги."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = onOpenSettings,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(if (!isTelegramConnected) "Подключить Telegram" else "Выбрать источники")
                            }
                        }
                    }
                }
            }

            mode.startsWith("detail:") && openedMix != null -> {
                var mixQuery by remember(openedMix.id) { mutableStateOf("") }
                val filteredMixTracks = remember(openedMix.tracks, mixQuery) {
                    val q = mixQuery.trim().lowercase()
                    if (q.isEmpty()) openedMix.tracks
                    else openedMix.tracks.filter {
                        it.title.lowercase().contains(q) ||
                            it.author.lowercase().contains(q) ||
                            it.channelTitle.lowercase().contains(q) ||
                            it.description.lowercase().contains(q)
                    }
                }
                val alreadySaved = remember(playlists, openedMix.title) {
                    playlists.any { it.name.equals(openedMix.title, ignoreCase = true) }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CoverArtworkBox(
                                        coverPath = openedMix.coverPath,
                                        contentDescription = openedMix.title,
                                        size = 84.dp,
                                        cornerRadius = 18.dp
                                    )
                                    Spacer(Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = openedMix.kicker.ifBlank {
                                                if (openedMix.isAutoPlaylist) "Авто-плейлист · Собрано алгоритмом" else "Микс"
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = openedMix.title,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = openedMix.subtitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (openedMix.description.isNotBlank()) {
                                    Text(
                                        text = openedMix.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (openedMix.tracks.isNotEmpty()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        Button(
                                            onClick = { openedMix.tracks.firstOrNull()?.let { onPlayTrack(it, openedMix.tracks) } },
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Слушать")
                                        }
                                        FilledTonalButton(
                                            onClick = { onPlayShuffled(openedMix.tracks) },
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Перемешать")
                                        }
                                    }
                                    if (openedMix.isAutoPlaylist) {
                                        FilledTonalButton(
                                            enabled = !alreadySaved,
                                            onClick = { onSaveAutoPlaylist(openedMix) },
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                imageVector = if (alreadySaved) Icons.Default.Check else Icons.Default.BookmarkAdd,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(if (alreadySaved) "Сохранено в ваших плейлистах" else "Сохранить в мои плейлисты")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (openedMix.tracks.size > 4) {
                        item {
                            M3SearchBar(
                                value = mixQuery,
                                onValueChange = { mixQuery = it },
                                placeholder = "Поиск внутри подборки..."
                            )
                        }
                    }

                    if (filteredMixTracks.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Ничего не найдено по вашему запросу.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(24.dp)
                                )
                            }
                        }
                    } else {
                        items(filteredMixTracks, key = { it.trackId }) { track ->
                            AndroidTrackRow(
                                track = track,
                                isActive = currentTrack?.trackId == track.trackId,
                                isPlaying = isPlaying,
                                isFavorite = track.trackId in favoriteIds,
                                isCompleted = track.trackId in completedIds,
                                isOffline = track.trackId in offlineIds,
                                downloadProgress = downloadingProgress[track.trackId],
                                onClick = { onPlayTrack(track, openedMix.tracks) },
                                onToggleFavorite = { onToggleFavorite(track) },
                                onAddToPlaylist = { onAddToPlaylist(track) },
                                onRequestCover = onRequestCover
                            )
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // 1. Header FIRST at top
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Главная", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "Источников: $selectedChannelsCount · Треков: ${tracks.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilledTonalButton(
                                    enabled = !isLoadingSources,
                                    onClick = onRefreshTracks,
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    if (isLoadingSources) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text("Обновить", style = MaterialTheme.typography.labelLarge)
                                }
                                IconButton(onClick = onOpenSettings) {
                                    Icon(Icons.Default.Settings, contentDescription = "Настройки")
                                }
                            }
                        }
                    }

                    // 2. Continue Listening Card (isolated StateFlow collection)
                    if (currentTrack != null) {
                        item(key = "continue_listening") {
                            ContinueListeningCard(
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                positionMsFlow = positionMsFlow,
                                durationMsFlow = durationMsFlow,
                                onOpenFullPlayer = onOpenFullPlayer,
                                onTogglePlayPause = onTogglePlayPause
                            )
                        }
                    }

                    if (tracks.isEmpty()) {
                        item {
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
                                    if (isLoadingSources) {
                                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                                        Text("Загружаем аудиокниги из Telegram...", style = MaterialTheme.typography.titleMedium)
                                    } else {
                                        Icon(
                                            Icons.Default.Headphones,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Text(
                                            "В выбранных источниках пока не найдено аудиофайлов",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            "Проверьте выбранные каналы, боты или чаты в настройках или нажмите «Обновить».",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. Expanded Smart Mixes (up to 14 mixes + Пересобрать)
                    if (mixes.isNotEmpty()) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Миксы для вас", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "${mixes.size} персональных подборок · обновление раз в сутки",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = onShuffleRecommendations) {
                                    Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Пересобрать")
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(mixes, key = { it.id }) { mix ->
                                    val context = LocalContext.current
                                    Card(
                                        shape = RoundedCornerShape(22.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
                                        modifier = Modifier
                                            .width(178.dp)
                                            .clickable { onOpenMixId(mix.id) }
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(1f)
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                            ) {
                                                val mixCoverRequest = remember(mix.coverPath, context) {
                                                    mix.coverPath?.takeIf { it.isNotBlank() }?.let { path ->
                                                        val data = if (path.startsWith("/")) File(path) else path
                                                        ImageRequest.Builder(context)
                                                            .data(data)
                                                            .size(380)
                                                            .crossfade(100)
                                                            .build()
                                                    }
                                                }
                                                if (mixCoverRequest != null) {
                                                    AsyncImage(
                                                        model = mixCoverRequest,
                                                        contentDescription = mix.title,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    Icon(
                                                        Icons.Default.LibraryMusic,
                                                        contentDescription = mix.title,
                                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                                                        modifier = Modifier.size(44.dp).align(Alignment.Center)
                                                    )
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(64.dp)
                                                        .align(Alignment.BottomCenter)
                                                        .background(
                                                            Brush.verticalGradient(
                                                                colors = listOf(
                                                                    Color.Transparent,
                                                                    Color.Black.copy(alpha = 0.52f)
                                                                )
                                                            )
                                                        )
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(14.dp),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                                    shadowElevation = 4.dp,
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .padding(8.dp)
                                                        .size(38.dp)
                                                        .clickable {
                                                            mix.tracks.firstOrNull()?.let { onPlayTrack(it, mix.tracks) }
                                                        }
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(Icons.Default.PlayArrow, contentDescription = "Слушать", modifier = Modifier.size(22.dp))
                                                    }
                                                }
                                            }
                                            Spacer(Modifier.height(10.dp))
                                            if (mix.kicker.isNotBlank()) {
                                                Text(
                                                    text = mix.kicker,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                text = mix.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = mix.reason.ifBlank { mix.subtitle },
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
                    }

                    // 4. Auto-Curated Algorithm Playlists as Multi-Row Square Tile Cards
                    if (autoPlaylistRows.isNotEmpty()) {
                        item(key = "auto_playlists_header") {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text("Авто-плейлисты", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "Алгоритм собирает новинки, долгое чтение и волны по вкусу",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        items(
                            items = autoPlaylistRows,
                            key = { row -> "apl_row_" + row.joinToString("_") { it.id } }
                        ) { rowItems ->
                            val context = LocalContext.current
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                rowItems.forEach { apl ->
                                    val saved = apl.title.lowercase() in savedPlaylistNames
                                    Card(
                                        shape = RoundedCornerShape(22.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onOpenMixId(apl.id) }
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(1f)
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                            ) {
                                                val aplCoverRequest = remember(apl.coverPath, context) {
                                                    apl.coverPath?.takeIf { it.isNotBlank() }?.let { path ->
                                                        val data = if (path.startsWith("/")) File(path) else path
                                                        ImageRequest.Builder(context)
                                                            .data(data)
                                                            .size(380)
                                                            .crossfade(100)
                                                            .build()
                                                    }
                                                }
                                                if (aplCoverRequest != null) {
                                                    AsyncImage(
                                                        model = aplCoverRequest,
                                                        contentDescription = apl.title,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    Icon(
                                                        Icons.Default.AutoAwesome,
                                                        contentDescription = apl.title,
                                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                                                        modifier = Modifier.size(44.dp).align(Alignment.Center)
                                                    )
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(64.dp)
                                                        .align(Alignment.BottomCenter)
                                                        .background(
                                                            Brush.verticalGradient(
                                                                colors = listOf(
                                                                    Color.Transparent,
                                                                    Color.Black.copy(alpha = 0.52f)
                                                                )
                                                            )
                                                        )
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (saved) {
                                                        MaterialTheme.colorScheme.secondaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f)
                                                    },
                                                    contentColor = if (saved) {
                                                        MaterialTheme.colorScheme.onSecondaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    },
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(8.dp)
                                                        .size(34.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .clickable(enabled = !saved) { onSaveAutoPlaylist(apl) }
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = if (saved) Icons.Default.Check else Icons.Default.BookmarkAdd,
                                                            contentDescription = if (saved) "Сохранено" else "В плейлисты",
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                                if (apl.tracks.isNotEmpty()) {
                                                    Surface(
                                                        shape = RoundedCornerShape(14.dp),
                                                        color = MaterialTheme.colorScheme.primary,
                                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                                        shadowElevation = 4.dp,
                                                        modifier = Modifier
                                                            .align(Alignment.BottomEnd)
                                                            .padding(8.dp)
                                                            .size(38.dp)
                                                            .clip(RoundedCornerShape(14.dp))
                                                            .clickable { onPlayTrack(apl.tracks.first(), apl.tracks) }
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(Icons.Default.PlayArrow, contentDescription = "Слушать", modifier = Modifier.size(22.dp))
                                                        }
                                                    }
                                                }
                                            }
                                            Spacer(Modifier.height(10.dp))
                                            Text(
                                                text = apl.kicker.ifBlank { "Авто-плейлист" },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = apl.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = apl.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                if (rowItems.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    // 4.5 Shortcuts for Series & Reading Stats
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { onOpenLibrarySection(LibrarySubTab.SERIES) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                    ) {
                                        Icon(
                                            Icons.Default.FolderCopy,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Серии", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(
                                            "Циклы по томам",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { onOpenLibrarySection(LibrarySubTab.STATS) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                                    ) {
                                        Icon(
                                            Icons.Default.BarChart,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Статистика", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(
                                            "Серия и часы",
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

                    // 5. User Playlists
                    if (playlists.isNotEmpty()) {
                        item(key = "user_playlists_header") {
                            Text("Ваши плейлисты", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        items(playlists, key = { "user_pl_" + it.id }) { pl ->
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { onOpenPlaylistDetail(pl.id) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    CoverArtworkBox(
                                        coverPath = pl.items.firstOrNull()?.coverPath,
                                        contentDescription = pl.name,
                                        size = 52.dp,
                                        cornerRadius = 12.dp
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
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable { onPlayTrack(pl.items.first(), pl.items) }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = "Играть", modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    } else {
                                        Icon(
                                            Icons.Default.ChevronRight,
                                            contentDescription = null,
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
    }
}

@Composable
private fun ContinueListeningCard(
    currentTrack: AudioTrack,
    isPlaying: Boolean,
    positionMsFlow: StateFlow<Long>,
    durationMsFlow: StateFlow<Long>,
    onOpenFullPlayer: () -> Unit,
    onTogglePlayPause: () -> Unit
) {
    val positionMs by positionMsFlow.collectAsState()
    val durationMs by durationMsFlow.collectAsState()
    val activeDurMs = if (durationMs > 0L) durationMs else currentTrack.durationSec * 1000L
    val activeDurSec = activeDurMs / 1000L
    val progressRatio = if (activeDurMs > 0L) {
        (positionMs.toFloat() / activeDurMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val authorOrChan = remember(currentTrack.trackId, currentTrack.author, currentTrack.channelTitle) {
        currentTrack.author.ifBlank { currentTrack.channelTitle }
    }

    val heroStart = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
    val heroEnd = MaterialTheme.colorScheme.surfaceContainer
    val heroBrush = remember(heroStart, heroEnd) {
        Brush.linearGradient(colors = listOf(heroStart, heroEnd))
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable { onOpenFullPlayer() }
    ) {
        Column(modifier = Modifier.background(heroBrush)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(14.dp)
            ) {
                CoverArtworkBox(
                    coverPath = currentTrack.coverPath,
                    contentDescription = currentTrack.title,
                    size = 60.dp,
                    cornerRadius = 15.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PlayingEqualizerBars(
                            isPlaying = isPlaying,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (isPlaying) "СЕЙЧАС ИГРАЕТ" else "ПРОДОЛЖИТЬ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = currentTrack.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val timeStr = buildString {
                        append(formatTrackDuration(positionMs / 1000L))
                        if (activeDurSec > 0L) append(" / ${formatTrackDuration(activeDurSec)}")
                    }
                    Text(
                        text = "$authorOrChan · $timeStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(10.dp))
                Surface(
                    shape = RoundedCornerShape(15.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .clickable { onTogglePlayPause() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Пауза" else "Продолжить",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            LinearProgressIndicator(
                progress = { progressRatio },
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
                modifier = Modifier.fillMaxWidth().height(3.dp)
            )
        }
    }
}

@Composable
fun CoverArtworkBox(
    coverPath: String?,
    contentDescription: String,
    size: Dp,
    cornerRadius: Dp = 12.dp
) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx().coerceAtLeast(64) }
    val request = remember(coverPath, sizePx, context) {
        coverPath?.takeIf { it.isNotBlank() }?.let { path ->
            val data = if (path.startsWith("/")) File(path) else path
            ImageRequest.Builder(context)
                .data(data)
                .size(sizePx)
                .crossfade(100)
                .build()
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.LibraryMusic,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                modifier = Modifier.size(size * 0.48f)
            )
        }
    }
}

@Composable
fun AndroidTrackRow(
    track: AudioTrack,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    isFavorite: Boolean,
    isCompleted: Boolean = false,
    isOffline: Boolean = false,
    downloadProgress: Float? = null,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    onRequestCover: ((AudioTrack) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(track.trackId, track.coverPath) {
        if (track.coverPath.isNullOrBlank()) {
            onRequestCover?.invoke(track)
        }
    }
    val containerColor = if (isActive) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val titleColor = if (isActive) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val subColor = if (isActive) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val subtitle = remember(track.trackId, track.author, track.channelTitle, track.durationSec) {
        buildString {
            append(track.author.ifBlank { track.channelTitle })
            append(" · ${track.channelTitle}")
            if (track.durationSec > 0L) {
                append(" · ${formatTrackDuration(track.durationSec)}")
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.16f)
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                CoverArtworkBox(
                    coverPath = track.coverPath,
                    contentDescription = track.title,
                    size = 52.dp,
                    cornerRadius = 12.dp
                )
                if (isActive) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.52f))
                    ) {
                        PlayingEqualizerBars(
                            isPlaying = isPlaying,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isCompleted) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Прослушано",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    if (downloadProgress != null) {
                        CircularProgressIndicator(
                            progress = { downloadProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                    } else if (isOffline) {
                        Icon(
                            imageVector = Icons.Default.DownloadDone,
                            contentDescription = "Офлайн",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = subColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (track.recommendationReason.isNotBlank()) {
                    Text(
                        text = track.recommendationReason,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "В избранное",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else subColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (onAddToPlaylist != null) {
                IconButton(onClick = onAddToPlaylist, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = "Добавить в плейлист",
                        tint = subColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
