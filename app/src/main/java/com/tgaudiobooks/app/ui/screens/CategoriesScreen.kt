package com.tgaudiobooks.app.ui.screens

import androidx.compose.animation.AnimatedContent
import com.tgaudiobooks.app.ui.components.BubbleMotion
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tgaudiobooks.app.data.model.AudioTrack
import com.tgaudiobooks.app.data.model.CategorySourceKind
import com.tgaudiobooks.app.data.model.DiscoveredCategory
import com.tgaudiobooks.app.data.model.TelegramChannel
import com.tgaudiobooks.app.data.recommendations.RecommendationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CategoriesScreen(
    isTelegramConnected: Boolean,
    isLoadingSources: Boolean,
    selectedChannels: List<TelegramChannel>,
    allSourceTracks: List<AudioTrack>,
    discoveredCategories: List<DiscoveredCategory>,
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    favoriteIds: Set<String>,
    completedIds: Set<String> = emptySet(),
    offlineIds: Set<String> = emptySet(),
    downloadingProgress: Map<String, Float> = emptyMap(),
    openedCategoryId: String?,
    onOpenCategory: (DiscoveredCategory?) -> Unit,
    onPlayTrack: (AudioTrack, List<AudioTrack>) -> Unit,
    onPlayShuffled: (List<AudioTrack>) -> Unit,
    onToggleFavorite: (AudioTrack) -> Unit,
    onAddToPlaylist: (AudioTrack) -> Unit,
    onRequestCover: (AudioTrack) -> Unit = {},
    onRefreshTracks: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    var channelFilter by rememberSaveable { mutableStateOf("all") }
    var searchQuery by remember { mutableStateOf("") }
    var isDurationExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(selectedChannels) {
        if (channelFilter != "all" && selectedChannels.none { it.channelId == channelFilter }) {
            channelFilter = "all"
        }
    }

    val scopedTracks = remember(allSourceTracks, channelFilter) {
        if (channelFilter == "all") allSourceTracks else allSourceTracks.filter { it.channelId == channelFilter }
    }

    val activeCategories by produceState(
        initialValue = discoveredCategories,
        channelFilter,
        scopedTracks,
        discoveredCategories
    ) {
        value = if (channelFilter == "all") {
            discoveredCategories
        } else {
            withContext(Dispatchers.Default) {
                RecommendationEngine.discoverCategories(scopedTracks)
            }
        }
    }

    val durationCategories by produceState(
        initialValue = emptyList<DiscoveredCategory>(),
        scopedTracks
    ) {
        value = withContext(Dispatchers.Default) {
            RecommendationEngine.buildDurationCategories(scopedTracks)
        }
    }

    val openedCategory = remember(activeCategories, durationCategories, openedCategoryId) {
        openedCategoryId?.let { id ->
            activeCategories.firstOrNull { it.id == id }
                ?: durationCategories.firstOrNull { it.id == id }
        }
    }
    var lastOpenedCategory by remember { mutableStateOf<DiscoveredCategory?>(null) }
    LaunchedEffect(openedCategory) {
        if (openedCategory != null) {
            lastOpenedCategory = openedCategory
        }
    }

    AnimatedContent(
        targetState = openedCategoryId != null,
        transitionSpec = { BubbleMotion.bubbleContentTransform() },
        label = "categories_nav"
    ) { isDetailOpen ->
        val visibleCategory = openedCategory ?: lastOpenedCategory
        if (isDetailOpen && visibleCategory != null) {
            CategoryDetailView(
                category = visibleCategory,
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                favoriteIds = favoriteIds,
                completedIds = completedIds,
                offlineIds = offlineIds,
                downloadingProgress = downloadingProgress,
                onPlayTrack = onPlayTrack,
                onPlayShuffled = onPlayShuffled,
                onToggleFavorite = onToggleFavorite,
                onAddToPlaylist = onAddToPlaylist,
                onRequestCover = onRequestCover
            )
        } else if (isDetailOpen) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                if (isLoadingSources || scopedTracks.isNotEmpty()) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        text = "Категория больше не содержит треков",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val normalizedQuery = remember(searchQuery) { searchQuery.trim().lowercase() }
            val otherCategory = remember(activeCategories) {
                activeCategories.firstOrNull { it.sourceKind == CategorySourceKind.OTHER }
            }
            val topicCategories = remember(activeCategories) {
                activeCategories.filter { it.sourceKind != CategorySourceKind.OTHER }
            }

            val filteredTopics = remember(topicCategories, normalizedQuery) {
                if (normalizedQuery.isEmpty()) {
                    topicCategories
                } else {
                    topicCategories.filter { cat ->
                        cat.title.lowercase().contains(normalizedQuery) ||
                            cat.relatedTags.any { it.lowercase().contains(normalizedQuery) } ||
                            cat.tracks.any {
                                it.title.lowercase().contains(normalizedQuery) ||
                                    it.author.lowercase().contains(normalizedQuery)
                            }
                    }
                }
            }

            val filteredDuration = remember(durationCategories, normalizedQuery) {
                if (normalizedQuery.isEmpty()) {
                    durationCategories
                } else {
                    durationCategories.filter { cat ->
                        cat.title.lowercase().contains(normalizedQuery) ||
                            "длительность".contains(normalizedQuery)
                    }
                }
            }

            val showOther = remember(otherCategory, normalizedQuery) {
                otherCategory != null && (
                    normalizedQuery.isEmpty() ||
                        otherCategory.title.lowercase().contains(normalizedQuery) ||
                        otherCategory.tracks.any {
                            it.title.lowercase().contains(normalizedQuery) ||
                                it.author.lowercase().contains(normalizedQuery)
                        }
                    )
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "cat_header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Категории",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (scopedTracks.isNotEmpty()) {
                                    "Найдено ${topicCategories.size} тем · ${scopedTracks.size} аудио"
                                } else {
                                    "Автоматический разбор по тегам и названиям"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row {
                            IconButton(onClick = onRefreshTracks) {
                                Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                            }
                            IconButton(onClick = onOpenSettings) {
                                Icon(Icons.Default.Settings, contentDescription = "Настройки")
                            }
                        }
                    }
                }

                if (!isTelegramConnected || selectedChannels.isEmpty()) {
                    item(key = "cat_no_sources") {
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
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Category,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Text(
                                    text = if (!isTelegramConnected) {
                                        "Подключите Telegram"
                                    } else {
                                        "Выберите каналы или чаты"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Алгоритм сам проанализирует хештеги, описания и названия ваших аудиокниг и соберёт категории.",
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
                } else if (allSourceTracks.isEmpty()) {
                    item(key = "cat_loading_tracks") {
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
                                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                    Text(
                                        text = "Загружаем треки и выделяем категории...",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center
                                    )
                                } else {
                                    Text(
                                        text = "Треки ещё не загружены",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    FilledTonalButton(onClick = onRefreshTracks, shape = RoundedCornerShape(14.dp)) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Обновить список")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    item(key = "cat_search_bar") {
                        M3SearchBar(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = "Поиск по категориям, #тегам или книгам..."
                        )
                    }

                    if (selectedChannels.size > 1) {
                        item(key = "cat_channel_chips") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item(key = "cat_ch_all") {
                                    val active = channelFilter == "all"
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                        contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
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
                                items(selectedChannels, key = { "cat_ch_" + it.channelId }) { ch ->
                                    val active = channelFilter == ch.channelId
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                        contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
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
                    }

                    // Row 1: "По длительности" expandable folder (matches Telegram menu UX)
                    if (filteredDuration.isNotEmpty()) {
                        item(key = "cat_duration_group") {
                            val totalDurTracks = remember(filteredDuration) {
                                filteredDuration.sumOf { it.tracks.size }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable { isDurationExpanded = !isDurationExpanded }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.tertiaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Schedule,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "По длительности",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${filteredDuration.size} интервала · $totalDurTracks аудио",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Icon(
                                            imageVector = if (isDurationExpanded || normalizedQuery.isNotEmpty()) {
                                                Icons.Default.ExpandLess
                                            } else {
                                                Icons.Default.ExpandMore
                                            },
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                AnimatedVisibility(
                                    visible = isDurationExpanded || normalizedQuery.isNotEmpty(),
                                    enter = expandVertically(tween(200)) + fadeIn(tween(180)),
                                    exit = shrinkVertically(tween(160)) + fadeOut(tween(120))
                                ) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(start = 12.dp)
                                    ) {
                                        filteredDuration.forEach { durCat ->
                                            CategoryMenuRow(
                                                category = durCat,
                                                onOpen = { onOpenCategory(durCat) },
                                                onQuickPlay = {
                                                    if (durCat.tracks.isNotEmpty()) {
                                                        onPlayTrack(durCat.tracks.first(), durCat.tracks)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Row 2: "Разное" for untagged/uncategorized audio (matches screenshot placement)
                    if (showOther && otherCategory != null) {
                        item(key = otherCategory.id) {
                            CategoryMenuRow(
                                category = otherCategory,
                                onOpen = { onOpenCategory(otherCategory) },
                                onQuickPlay = {
                                    if (otherCategory.tracks.isNotEmpty()) {
                                        onPlayTrack(otherCategory.tracks.first(), otherCategory.tracks)
                                    }
                                },
                                onRequestCover = onRequestCover
                            )
                        }
                    }

                    // Dynamically discovered topic categories (hashtags, caption tags, phrases, keywords)
                    if (filteredTopics.isNotEmpty()) {
                        item(key = "cat_topics_label") {
                            Text(
                                text = "Темы из вашей библиотеки",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                            )
                        }
                        items(filteredTopics, key = { it.id }) { cat ->
                            CategoryMenuRow(
                                category = cat,
                                onOpen = { onOpenCategory(cat) },
                                onQuickPlay = {
                                    if (cat.tracks.isNotEmpty()) {
                                        onPlayTrack(cat.tracks.first(), cat.tracks)
                                    }
                                },
                                onRequestCover = onRequestCover
                            )
                        }
                    } else if (normalizedQuery.isNotEmpty() && filteredDuration.isEmpty() && !showOther) {
                        item(key = "cat_empty_search") {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Text(
                                        text = "Категории не найдены",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Попробуйте другой запрос или переключите фильтр источников.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
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

@Composable
private fun CategoryMenuRow(
    category: DiscoveredCategory,
    onOpen: () -> Unit,
    onQuickPlay: () -> Unit,
    onRequestCover: (AudioTrack) -> Unit = {}
) {
    val (badgeBg, badgeFg, iconVector) = categoryBadgeVisuals(category.sourceKind)
    val hasCover = !category.coverPath.isNullOrBlank()
    LaunchedEffect(category.id, category.coverPath) {
        if (category.coverPath.isNullOrBlank()) {
            val lead = category.tracks.firstOrNull { !it.coverPath.isNullOrBlank() }
                ?: category.tracks.firstOrNull { it.coverFileId > 0 || it.fileId > 0 }
                ?: category.tracks.firstOrNull()
            if (lead != null && lead.coverPath.isNullOrBlank()) {
                onRequestCover(lead)
            }
        }
    }
    val metaLine = remember(category.id, category.tracks.size, category.sourceKind, category.relatedTags) {
        buildString {
            append("${category.tracks.size} аудио · ${category.sourceKind.label}")
            if (category.relatedTags.isNotEmpty()) {
                append(" · ")
                append(category.relatedTags.take(2).joinToString(" "))
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onOpen)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Box(modifier = Modifier.size(48.dp)) {
                if (hasCover) {
                    CoverArtworkBox(
                        coverPath = category.coverPath,
                        contentDescription = category.title,
                        size = 48.dp,
                        cornerRadius = 14.dp
                    )
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = badgeBg,
                        contentColor = badgeFg,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(19.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = iconVector,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = badgeBg,
                        contentColor = badgeFg,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = iconVector,
                                contentDescription = null,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = metaLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (category.tracks.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onQuickPlay)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Слушать",
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun CategoryDetailView(
    category: DiscoveredCategory,
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    favoriteIds: Set<String>,
    completedIds: Set<String>,
    offlineIds: Set<String>,
    downloadingProgress: Map<String, Float>,
    onPlayTrack: (AudioTrack, List<AudioTrack>) -> Unit,
    onPlayShuffled: (List<AudioTrack>) -> Unit,
    onToggleFavorite: (AudioTrack) -> Unit,
    onAddToPlaylist: (AudioTrack) -> Unit,
    onRequestCover: (AudioTrack) -> Unit
) {
    var selectedSubTag by remember(category.id) { mutableStateOf<String?>(null) }
    var detailQuery by remember(category.id) { mutableStateOf("") }

    val filteredTracks = remember(category.tracks, selectedSubTag, detailQuery) {
        val q = detailQuery.trim().lowercase()
        val tagLower = selectedSubTag?.lowercase()
        category.tracks.filter { track ->
            val matchesTag = if (tagLower == null) {
                true
            } else {
                track.title.lowercase().contains(tagLower) ||
                    track.description.lowercase().contains(tagLower)
            }
            val matchesQuery = if (q.isEmpty()) {
                true
            } else {
                track.title.lowercase().contains(q) ||
                    track.author.lowercase().contains(q) ||
                    track.description.lowercase().contains(q)
            }
            matchesTag && matchesQuery
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "cat_detail_card") {
            val (badgeBg, badgeFg, iconVector) = categoryBadgeVisuals(category.sourceKind)
            val heroBrush = Brush.verticalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.26f),
                    MaterialTheme.colorScheme.surfaceContainer
                )
            )
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .background(heroBrush)
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!category.coverPath.isNullOrBlank()) {
                            CoverArtworkBox(
                                coverPath = category.coverPath,
                                contentDescription = category.title,
                                size = 72.dp,
                                cornerRadius = 16.dp
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = badgeBg,
                                contentColor = badgeFg,
                                modifier = Modifier.size(72.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = iconVector,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = category.sourceKind.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = category.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatCategoryStatsLine(category.tracks.size, category.totalDurationSec),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (category.relatedTags.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (selectedSubTag != null) {
                                item(key = "subtag_reset") {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { selectedSubTag = null }
                                    ) {
                                        Text(
                                            text = "Все",
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                        )
                                    }
                                }
                            }
                            items(category.relatedTags, key = { "subtag_$it" }) { tag ->
                                val active = selectedSubTag == tag
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (active) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    contentColor = if (active) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            selectedSubTag = if (active) null else tag
                                        }
                                ) {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            enabled = filteredTracks.isNotEmpty(),
                            onClick = {
                                if (filteredTracks.isNotEmpty()) {
                                    onPlayTrack(filteredTracks.first(), filteredTracks)
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Слушать")
                        }
                        FilledTonalButton(
                            enabled = filteredTracks.size > 1,
                            onClick = { onPlayShuffled(filteredTracks) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Перемешать")
                        }
                    }
                }
            }
        }

        if (category.tracks.size > 5) {
            item(key = "cat_detail_search") {
                M3SearchBar(
                    value = detailQuery,
                    onValueChange = { detailQuery = it },
                    placeholder = "Найти в этой категории..."
                )
            }
        }

        if (filteredTracks.isEmpty()) {
            item(key = "cat_detail_empty") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "По выбранному фильтру аудио не найдено",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
        } else {
            items(filteredTracks, key = { it.trackId }) { track ->
                AndroidTrackRow(
                    track = track,
                    isActive = currentTrack?.trackId == track.trackId,
                    isPlaying = isPlaying,
                    isFavorite = track.trackId in favoriteIds,
                    isCompleted = track.trackId in completedIds,
                    isOffline = track.trackId in offlineIds,
                    downloadProgress = downloadingProgress[track.trackId],
                    onClick = { onPlayTrack(track, filteredTracks) },
                    onToggleFavorite = { onToggleFavorite(track) },
                    onAddToPlaylist = { onAddToPlaylist(track) },
                    onRequestCover = onRequestCover
                )
            }
        }
    }
}

@Composable
private fun categoryBadgeVisuals(kind: CategorySourceKind): Triple<Color, Color, ImageVector> = when (kind) {
    CategorySourceKind.HASHTAG -> Triple(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.onPrimaryContainer,
        Icons.Default.Tag
    )
    CategorySourceKind.META_TAG -> Triple(
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.onSecondaryContainer,
        Icons.Default.Description
    )
    CategorySourceKind.PHRASE -> Triple(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.onPrimaryContainer,
        Icons.Default.AutoAwesome
    )
    CategorySourceKind.KEYWORD -> Triple(
        MaterialTheme.colorScheme.surfaceContainerHighest,
        MaterialTheme.colorScheme.primary,
        Icons.Default.Category
    )
    CategorySourceKind.DURATION -> Triple(
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.onTertiaryContainer,
        Icons.Default.Schedule
    )
    CategorySourceKind.OTHER -> Triple(
        MaterialTheme.colorScheme.surfaceContainerHighest,
        MaterialTheme.colorScheme.onSurfaceVariant,
        Icons.Default.FolderCopy
    )
}

private fun formatCategoryStatsLine(count: Int, totalDurationSec: Long): String {
    val totalMin = (totalDurationSec / 60L).toInt()
    val hours = totalMin / 60
    val mins = totalMin % 60
    return when {
        hours > 0 -> "$count аудио · ${hours} ч ${mins} мин"
        mins > 0 -> "$count аудио · ${mins} мин"
        else -> "$count аудио"
    }
}
