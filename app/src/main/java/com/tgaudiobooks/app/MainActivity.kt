package com.tgaudiobooks.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.tgaudiobooks.app.data.telegram.TelegramRepository
import com.tgaudiobooks.app.ui.screens.TelegramAuthScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tgaudiobooks.app.data.model.AppTab
import com.tgaudiobooks.app.data.model.AudioTrack
import com.tgaudiobooks.app.data.model.LibrarySubTab
import com.tgaudiobooks.app.data.model.TelegramAuthState
import com.tgaudiobooks.app.player.AudiobookPlayerService
import com.tgaudiobooks.app.ui.MainViewModel
import com.tgaudiobooks.app.ui.components.FullScreenPlayerModal
import com.tgaudiobooks.app.ui.components.MiniPlayerBar
import com.tgaudiobooks.app.ui.screens.CategoriesScreen
import com.tgaudiobooks.app.ui.screens.HomeScreen
import com.tgaudiobooks.app.ui.screens.LibraryScreen
import com.tgaudiobooks.app.ui.screens.SearchScreen
import com.tgaudiobooks.app.ui.screens.SettingsScreen
import com.tgaudiobooks.app.ui.screens.TelegramChatScreen
import com.tgaudiobooks.app.ui.theme.EchoGramTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var widgetAutoPlayTrigger = mutableStateOf(false)

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (intent?.getBooleanExtra(AudiobookPlayerService.EXTRA_WIDGET_AUTO_PLAY, false) == true) {
            widgetAutoPlayTrigger.value = true
        }
        setContent {
            EchoGramApp(
                widgetAutoPlayTrigger = widgetAutoPlayTrigger.value,
                onConsumeWidgetAutoPlay = { widgetAutoPlayTrigger.value = false }
            )
        }
    }

    override fun onStart() {
        super.onStart()
        TelegramRepository.getInstance(applicationContext).setAppOnline(true)
    }

    override fun onStop() {
        super.onStop()
        TelegramRepository.getInstance(applicationContext).setAppOnline(false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(AudiobookPlayerService.EXTRA_WIDGET_AUTO_PLAY, false)) {
            widgetAutoPlayTrigger.value = true
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EchoGramApp(
    viewModel: MainViewModel = viewModel(),
    widgetAutoPlayTrigger: Boolean = false,
    onConsumeWidgetAutoPlay: () -> Unit = {}
) {
    val cloudSyncState by viewModel.cloudSyncState.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val progressBarStyle by viewModel.progressBarStyle.collectAsState()
    val appIcon by viewModel.appIcon.collectAsState()
    val isShuffle by viewModel.isShuffle.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val authState by viewModel.telegramRepo.authState.collectAsState()
    val authError by viewModel.telegramRepo.authError.collectAsState()
    val isAuthBusy by viewModel.telegramRepo.isAuthBusy.collectAsState()
    val connectionStatus by viewModel.telegramRepo.connectionStatus.collectAsState()
    var continueOffline by rememberSaveable { mutableStateOf(false) }
    val channels by viewModel.channels.collectAsState()
    val isLoadingChannels by viewModel.isLoadingChannels.collectAsState()
    val isLoadingTracks by viewModel.isLoadingTracks.collectAsState()
    val tracksByChannel by viewModel.tracksByChannel.collectAsState()
    val allTracks by viewModel.allTracks.collectAsState()
    val mixes by viewModel.mixes.collectAsState()
    val autoPlaylists by viewModel.autoPlaylists.collectAsState()
    val seriesGroups by viewModel.seriesGroups.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val readingSummary by viewModel.readingSummary.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isBufferingTrack by viewModel.isBufferingTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val speed by viewModel.playbackSpeed.collectAsState()
    val sleepTimer by viewModel.sleepTimer.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val tracklist by viewModel.tracklist.collectAsState()
    val isTracklistMode by viewModel.isTracklistMode.collectAsState()
    val proxies by viewModel.proxies.collectAsState()
    val isProxyBusy by viewModel.isProxyBusy.collectAsState()
    val proxyError by viewModel.proxyError.collectAsState()
    val proxyMessage by viewModel.proxyMessage.collectAsState()
    val cacheStats by viewModel.cacheStats.collectAsState()
    val isCacheBusy by viewModel.isCacheBusy.collectAsState()
    val cacheMessage by viewModel.cacheMessage.collectAsState()
    val cacheTtlDays by viewModel.cacheTtlDays.collectAsState()
    val completedIds by viewModel.completedTrackIds.collectAsState()
    val offlineTracks by viewModel.downloadedTracks.collectAsState()
    val offlineIds by viewModel.downloadedTrackIds.collectAsState()
    val downloadingProgress by viewModel.downloadingProgress.collectAsState()
    val dspPreset by viewModel.dspPreset.collectAsState()
    val skipSilence by viewModel.skipSilence.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val dacDevices by viewModel.dacDevices.collectAsState()
    val dacOutputMode by viewModel.dacOutputMode.collectAsState()
    val dacPreferredDeviceId by viewModel.dacPreferredDeviceId.collectAsState()
    val dacRoutingSummary by viewModel.dacRoutingSummary.collectAsState()
    val dacBitPerfectActive by viewModel.dacBitPerfectActive.collectAsState()
    val streamFormat by viewModel.streamFormat.collectAsState()
    val embeddedChaptersState by viewModel.embeddedChapters.collectAsState()
    val loudnessBoostDb by viewModel.loudnessBoostDb.collectAsState()
    val seekStepSec by viewModel.seekStepSec.collectAsState()
    val smartRewindEnabled by viewModel.smartRewindEnabled.collectAsState()
    val playbackError by viewModel.playbackError.collectAsState()
    val uiMessage by viewModel.uiMessage.collectAsState()
    val tasteClusters by viewModel.tasteClusters.collectAsState()
    val dislikedIds by viewModel.dislikedIds.collectAsState()
    val chatUiState by viewModel.chatUiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(playbackError) {
        val msg = playbackError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.dismissPlaybackError()
    }

    LaunchedEffect(uiMessage) {
        val msg = uiMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.dismissUiMessage()
    }

    LaunchedEffect(widgetAutoPlayTrigger, currentTrack) {
        if (widgetAutoPlayTrigger && currentTrack != null) {
            delay(450L)
            if (!isPlaying) {
                viewModel.togglePlayPause()
            }
            onConsumeWidgetAutoPlay()
        }
    }

    var coverBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val coverPath = currentTrack?.coverPath
    LaunchedEffect(coverPath) {
        coverBitmap = if (coverPath.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                decodeSampledPaletteBitmap(coverPath, maxDim = 128)
            }
        }
    }

    var showSettings by remember { mutableStateOf(false) }
    var isFullPlayerOpen by remember { mutableStateOf(false) }
    var openedMixId by rememberSaveable { mutableStateOf<String?>(null) }
    var openedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var openedCategoryTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var activeLibrarySection by rememberSaveable { mutableStateOf<LibrarySubTab?>(null) }
    var openedLibraryChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    var openedPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var playlistPickerTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var quickNewPlaylistName by remember { mutableStateOf("") }

    val tabs = AppTab.entries
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    val favoriteIds = remember(favorites) { favorites.map { it.trackId }.toSet() }
    val selectedChannels = remember(channels) { channels.filter { it.isSelected } }

    val openedMix = remember(mixes, autoPlaylists, openedMixId) {
        (mixes + autoPlaylists).firstOrNull { it.id == openedMixId }
    }
    val openedLibraryChannel = remember(selectedChannels, openedLibraryChannelId) {
        selectedChannels.firstOrNull { it.channelId == openedLibraryChannelId }
    }
    val openedPlaylist = remember(playlists, openedPlaylistId) {
        playlists.firstOrNull { it.id == openedPlaylistId }
    }

    val currentTab = tabs[pagerState.currentPage]
    val subScreenBackAction: (() -> Unit)? = when {
        currentTab == AppTab.HOME && openedMix != null -> ({ openedMixId = null })
        currentTab == AppTab.CATEGORIES && openedCategoryId != null -> ({
            openedCategoryId = null
            openedCategoryTitle = null
        })
        currentTab == AppTab.LIBRARY && openedLibraryChannel != null -> ({ openedLibraryChannelId = null })
        currentTab == AppTab.LIBRARY && openedPlaylist != null -> ({ openedPlaylistId = null })
        currentTab == AppTab.LIBRARY && activeLibrarySection != null -> ({
            openedLibraryChannelId = null
            openedPlaylistId = null
            activeLibrarySection = null
        })
        else -> null
    }
    val topBarTitle = when {
        currentTab == AppTab.HOME && openedMix != null -> openedMix.title
        currentTab == AppTab.CATEGORIES && openedCategoryId != null ->
            openedCategoryTitle ?: categories.firstOrNull { it.id == openedCategoryId }?.title ?: "Категория"
        currentTab == AppTab.LIBRARY && openedLibraryChannel != null -> openedLibraryChannel.title
        currentTab == AppTab.LIBRARY && openedPlaylist != null -> openedPlaylist.name
        currentTab == AppTab.LIBRARY && activeLibrarySection != null -> activeLibrarySection!!.title
        else -> "EchoGram"
    }

    BackHandler(
        enabled = !showSettings && !isFullPlayerOpen && chatUiState.target == null && playlistPickerTrack == null &&
            (subScreenBackAction != null || pagerState.currentPage != 0)
    ) {
        if (subScreenBackAction != null) {
            subScreenBackAction()
        } else if (pagerState.currentPage != 0) {
            scope.launch { pagerState.animateScrollToPage(0) }
        }
    }

    EchoGramTheme(themeMode = themeMode, coverBitmap = coverBitmap) {
        if (authState !is TelegramAuthState.Connected && !continueOffline) {
            TelegramAuthScreen(
                authState = authState,
                authError = authError,
                isAuthBusy = isAuthBusy,
                connectionStatus = connectionStatus,
                appIcon = appIcon,
                proxies = proxies,
                hasOfflineTracks = offlineTracks.isNotEmpty(),
                onInitTdlib = { id, hash -> viewModel.telegramRepo.initialize(id, hash) },
                onSubmitPhone = { viewModel.telegramRepo.submitPhoneNumber(it) },
                onRequestQr = { viewModel.telegramRepo.requestQrLogin() },
                onSubmitCode = { viewModel.telegramRepo.submitAuthCode(it) },
                onResendCode = { viewModel.telegramRepo.resendAuthCode() },
                onSubmitEmail = { viewModel.telegramRepo.submitEmailAddress(it) },
                onSubmitEmailCode = { viewModel.telegramRepo.submitEmailCode(it) },
                onSubmitRegistration = { f, l -> viewModel.telegramRepo.submitRegistration(f, l) },
                onSubmitPassword = { viewModel.telegramRepo.submitTwoFactorPassword(it) },
                onResetToPhone = { viewModel.telegramRepo.resetToPhoneInput() },
                onClearError = { viewModel.telegramRepo.clearAuthError() },
                onParseProxyLink = { viewModel.telegramRepo.parseProxyLink(it) },
                onAddProxy = { type, server, port, user, pass, secret ->
                    viewModel.addProxy(type, server, port, user, pass, secret, enable = true)
                },
                onEnableProxy = { viewModel.enableProxy(it) },
                onToggleUseProxy = { viewModel.toggleUseProxy(it) },
                onRemoveProxy = { viewModel.removeProxy(it) },
                onContinueOffline = { continueOffline = true }
            )
            return@EchoGramTheme
        }
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                snackbarHost = {
                    SnackbarHost(hostState = snackbarHostState) { data ->
                        Snackbar(
                            snackbarData = data,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            actionColor = MaterialTheme.colorScheme.primary,
                            dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = RoundedCornerShape(18.dp)
                        )
                    }
                },
                topBar = {
                    if (subScreenBackAction != null) {
                        TopAppBar(
                            navigationIcon = {
                                IconButton(onClick = subScreenBackAction) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Назад"
                                    )
                                }
                            },
                            title = {
                                Text(
                                    text = topBarTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            actions = {
                                IconButton(onClick = { showSettings = true }) {
                                    Icon(Icons.Default.Settings, contentDescription = "Настройки")
                                }
                            }
                        )
                    }
                },
                bottomBar = {
                    Column {
                        val isPlayingMainUi = isPlaying && !isFullPlayerOpen && !showSettings
                        currentTrack?.let { active ->
                            MiniPlayerBar(
                                track = active,
                                isPlaying = isPlayingMainUi,
                                isBuffering = isBufferingTrack,
                                positionMsFlow = viewModel.positionMs,
                                durationMsFlow = viewModel.durationMs,
                                progressBarStyle = progressBarStyle,
                                onExpand = { isFullPlayerOpen = true },
                                onTogglePlayPause = { viewModel.togglePlayPause() },
                                onNext = { viewModel.playNext() },
                                onPrevious = { viewModel.playPrevious() }
                            )
                        }
                        androidx.compose.material3.HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
                        )
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            tabs.forEachIndexed { index, tab ->
                                NavigationBarItem(
                                    selected = pagerState.currentPage == index,
                                    onClick = {
                                        if (pagerState.currentPage == index) {
                                            when (tab) {
                                                AppTab.HOME -> openedMixId = null
                                                AppTab.CATEGORIES -> {
                                                    openedCategoryId = null
                                                    openedCategoryTitle = null
                                                }
                                                AppTab.LIBRARY -> {
                                                    openedLibraryChannelId = null
                                                    openedPlaylistId = null
                                                    activeLibrarySection = null
                                                }
                                                AppTab.SEARCH -> Unit
                                            }
                                        } else {
                                            scope.launch { pagerState.animateScrollToPage(index) }
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            when (tab) {
                                                AppTab.HOME -> Icons.Default.Home
                                                AppTab.CATEGORIES -> Icons.Default.Category
                                                AppTab.SEARCH -> Icons.Default.Search
                                                AppTab.LIBRARY -> Icons.Default.LibraryMusic
                                            },
                                            contentDescription = tab.title
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
                        .imePadding()
                ) { page ->
                    when (tabs[page]) {
                        AppTab.HOME -> HomeScreen(
                            isTelegramConnected = authState is TelegramAuthState.Connected,
                            isLoadingSources = isLoadingChannels || isLoadingTracks,
                            selectedChannelsCount = selectedChannels.size,
                            currentTrack = currentTrack,
                            isPlaying = isPlaying && !isFullPlayerOpen && !showSettings,
                            positionMsFlow = viewModel.positionMs,
                            durationMsFlow = viewModel.durationMs,
                            mixes = mixes,
                            autoPlaylists = autoPlaylists,
                            playlists = playlists,
                            tracks = allTracks,
                            favoriteIds = favoriteIds,
                            completedIds = completedIds,
                            offlineIds = offlineIds,
                            downloadingProgress = downloadingProgress,
                            openedMixId = openedMixId,
                            onOpenMixId = { openedMixId = it },
                            onPlayTrack = { t, q -> viewModel.playTrack(t, q) },
                            onPlayShuffled = { q -> viewModel.playShuffled(q) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onAddToPlaylist = { track -> playlistPickerTrack = track },
                            onSaveAutoPlaylist = { col -> viewModel.saveAutoPlaylistToUser(col) },
                            onShuffleRecommendations = { viewModel.shuffleRecommendations() },
                            onRequestCover = { viewModel.requestTrackCover(it) },
                            onOpenPlaylistDetail = { plId ->
                                activeLibrarySection = LibrarySubTab.PLAYLISTS
                                openedLibraryChannelId = null
                                openedPlaylistId = plId
                                scope.launch { pagerState.animateScrollToPage(tabs.indexOf(AppTab.LIBRARY)) }
                            },
                            onOpenLibrarySection = { section ->
                                activeLibrarySection = section
                                openedLibraryChannelId = null
                                openedPlaylistId = null
                                scope.launch { pagerState.animateScrollToPage(tabs.indexOf(AppTab.LIBRARY)) }
                            },
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onOpenFullPlayer = { isFullPlayerOpen = true },
                            onRefreshTracks = { viewModel.refreshTracks() },
                            onOpenSettings = { showSettings = true },
                            tasteClusters = tasteClusters
                        )
                        AppTab.CATEGORIES -> CategoriesScreen(
                            isTelegramConnected = authState is TelegramAuthState.Connected,
                            isLoadingSources = isLoadingChannels || isLoadingTracks,
                            selectedChannels = selectedChannels,
                            allSourceTracks = allTracks,
                            discoveredCategories = categories,
                            currentTrack = currentTrack,
                            isPlaying = isPlaying && !isFullPlayerOpen && !showSettings,
                            favoriteIds = favoriteIds,
                            completedIds = completedIds,
                            offlineIds = offlineIds,
                            downloadingProgress = downloadingProgress,
                            openedCategoryId = openedCategoryId,
                            onOpenCategory = { cat ->
                                openedCategoryId = cat?.id
                                openedCategoryTitle = cat?.title
                            },
                            onPlayTrack = { t, q -> viewModel.playTrack(t, q) },
                            onPlayShuffled = { q -> viewModel.playShuffled(q) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onAddToPlaylist = { track -> playlistPickerTrack = track },
                            onRequestCover = { viewModel.requestTrackCover(it) },
                            onRefreshTracks = { viewModel.refreshTracks() },
                            onOpenSettings = { showSettings = true }
                        )
                        AppTab.SEARCH -> SearchScreen(
                            selectedChannels = selectedChannels,
                            allSourceTracks = allTracks,
                            currentTrack = currentTrack,
                            isPlaying = isPlaying,
                            favoriteIds = favoriteIds,
                            completedIds = completedIds,
                            offlineIds = offlineIds,
                            downloadingProgress = downloadingProgress,
                            onPlayTrack = { t, q -> viewModel.playTrack(t, q) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onAddToPlaylist = { track -> playlistPickerTrack = track },
                            onRequestCover = { viewModel.requestTrackCover(it) },
                            onOpenSettings = { showSettings = true }
                        )
                        AppTab.LIBRARY -> LibraryScreen(
                            activeSection = activeLibrarySection,
                            onActiveSectionChange = {
                                openedLibraryChannelId = null
                                openedPlaylistId = null
                                activeLibrarySection = it
                            },
                            openedChannel = openedLibraryChannel,
                            onOpenedChannelChange = { openedLibraryChannelId = it?.channelId },
                            openedPlaylist = openedPlaylist,
                            onOpenedPlaylistChange = { openedPlaylistId = it?.id },
                            favorites = favorites,
                            playlists = playlists,
                            selectedChannels = selectedChannels,
                            tracksByChannel = tracksByChannel,
                            currentTrack = currentTrack,
                            isPlaying = isPlaying,
                            favoriteIds = favoriteIds,
                            completedIds = completedIds,
                            offlineTracks = offlineTracks,
                            offlineIds = offlineIds,
                            downloadingProgress = downloadingProgress,
                            seriesGroups = seriesGroups,
                            readingSummary = readingSummary,
                            onPlayTrack = { t, q -> viewModel.playTrack(t, q) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onAddToPlaylist = { track -> playlistPickerTrack = track },
                            onCreatePlaylist = { name, desc -> viewModel.createPlaylist(name, description = desc) },
                            onDeletePlaylist = { id ->
                                if (openedPlaylistId == id) openedPlaylistId = null
                                viewModel.deletePlaylist(id)
                            },
                            onRemoveFromPlaylist = { plId, trackId -> viewModel.removeTrackFromPlaylist(plId, trackId) },
                            onRemoveOffline = { viewModel.removeOfflineTrack(it) },
                            onClearOffline = { viewModel.clearOfflineDownloads() },
                            onRequestCover = { viewModel.requestTrackCover(it) },
                            onOpenTelegramChat = { ch ->
                                viewModel.openTelegramChat(
                                    channelId = ch.channelId,
                                    title = ch.title,
                                    username = ch.username,
                                    sourceType = ch.sourceType
                                )
                            },
                            onOpenSettings = { showSettings = true }
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isFullPlayerOpen && currentTrack != null,
                enter = slideInVertically(tween(260)) { it / 2 } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(220)) { it / 2 } + fadeOut(tween(180))
            ) {
                currentTrack?.let { activeTrack ->
                    FullScreenPlayerModal(
                        track = activeTrack,
                        isPlaying = isPlaying && !showSettings,
                        isBuffering = isBufferingTrack,
                        positionMsFlow = viewModel.positionMs,
                        durationMsFlow = viewModel.durationMs,
                        playbackSpeed = speed,
                        progressBarStyle = progressBarStyle,
                        isShuffle = isShuffle,
                        repeatMode = repeatMode,
                        sleepTimer = sleepTimer,
                        isFavorite = activeTrack.trackId in favoriteIds,
                        isCompleted = activeTrack.trackId in completedIds,
                        isDisliked = activeTrack.trackId in dislikedIds,
                        isOffline = activeTrack.trackId in offlineIds,
                        downloadProgress = downloadingProgress[activeTrack.trackId],
                        dspPreset = dspPreset,
                        skipSilence = skipSilence,
                        seekStepSec = seekStepSec,
                        embeddedChapters = if (embeddedChaptersState.first == activeTrack.trackId) embeddedChaptersState.second else emptyList(),
                        bookmarks = bookmarks,
                        queue = queue,
                        tracklist = tracklist,
                        isTracklistMode = isTracklistMode,
                        playlists = playlists,
                        channels = channels,
                        onCollapse = { isFullPlayerOpen = false },
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        onSeekTo = { viewModel.seekTo(it) },
                        onSkip15 = { viewModel.skipBySeconds(it) },
                        onNext = { viewModel.playNext() },
                        onPrevious = { viewModel.playPrevious() },
                        onToggleShuffle = { viewModel.toggleShuffle() },
                        onToggleRepeat = { viewModel.toggleRepeat() },
                        onToggleFavorite = { viewModel.toggleFavorite(activeTrack) },
                        onToggleCompleted = { viewModel.toggleTrackCompleted(activeTrack.trackId) },
                        onToggleDislike = { viewModel.toggleDislikeTrack(activeTrack) },
                        onDownloadOffline = { viewModel.downloadTrackOffline(activeTrack) },
                        onRemoveOffline = { viewModel.removeOfflineTrack(activeTrack.trackId) },
                        onSelectSpeed = { viewModel.setPlaybackSpeed(it) },
                        onSelectDspPreset = { viewModel.setDspPreset(it) },
                        onToggleSkipSilence = { viewModel.setSkipSilence(it) },
                        onSelectSleepTimer = { viewModel.setSleepTimerMinutes(it) },
                        onAddBookmark = { posSec, note -> viewModel.addBookmark(activeTrack, posSec, note) },
                        onDeleteBookmark = { viewModel.deleteBookmark(it) },
                        onAddToPlaylist = { pid, t -> viewModel.addTrackToPlaylist(pid, t) },
                        onCreatePlaylistAndAdd = { name, t -> viewModel.createPlaylist(name, t) },
                        onPlayFromQueue = { viewModel.playTrack(it, queue) },
                        onPlayFromTracklist = { viewModel.playTrack(it) },
                        onLongPressAddToTracklist = { viewModel.addToTracklist(it) },
                        onRemoveFromTracklist = { viewModel.removeFromTracklist(it) },
                        onMoveTracklist = { f, t -> viewModel.moveTracklistItem(f, t) },
                        onToggleTracklistMode = { viewModel.toggleTracklistMode(it) },
                        onOpenChannel = { chId ->
                            activeLibrarySection = LibrarySubTab.CHANNELS
                            openedLibraryChannelId = chId
                            openedPlaylistId = null
                            scope.launch { pagerState.animateScrollToPage(tabs.indexOf(AppTab.LIBRARY)) }
                        },
                        onOpenTelegramChat = { chId, chTitle, chUser, focusMsgId ->
                            viewModel.openTelegramChat(
                                channelId = chId,
                                title = chTitle,
                                username = chUser,
                                focusMessageId = focusMsgId
                            )
                        },
                        onRequestCover = { viewModel.requestTrackCover(it) }
                    )
                }
            }

            AnimatedVisibility(
                visible = chatUiState.target != null,
                enter = slideInVertically(tween(240)) { it / 3 } + fadeIn(tween(180)),
                exit = slideOutVertically(tween(200)) { it / 3 } + fadeOut(tween(150))
            ) {
                TelegramChatScreen(
                    state = chatUiState,
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    onClose = { viewModel.closeTelegramChat() },
                    onRefresh = { viewModel.refreshTelegramChat() },
                    onLoadOlder = { viewModel.loadOlderChatMessages() },
                    onRequestPhoto = { msgId, fileId -> viewModel.requestChatPhoto(msgId, fileId) },
                    onSendMessage = { text -> viewModel.sendTelegramChatMessage(text) },
                    onPlayTrack = { track, queueList -> viewModel.playTrack(track, queueList) },
                    onTogglePlayPause = { viewModel.togglePlayPause() }
                )
            }

            if (showSettings) {
                val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { showSettings = false },
                    sheetState = settingsSheetState
                ) {
                    SettingsScreen(
                        cloudSyncState = cloudSyncState,
                        onSyncCloudPush = { viewModel.syncCloudPush(silent = false) },
                        onSyncCloudPull = { viewModel.syncCloudPull(silent = false) },
                        onToggleAutoCloudSync = { viewModel.setAutoCloudSync(it) },
                        onBuildBackupJson = { viewModel.buildBackupSnapshotJson() },
                        onImportBackupJson = { viewModel.importBackupJson(it) },
                        themeMode = themeMode,
                        onThemeModeChange = { viewModel.setThemeMode(it) },
                        progressBarStyle = progressBarStyle,
                        onProgressBarStyleChange = { viewModel.setProgressBarStyle(it) },
                        appIcon = appIcon,
                        onAppIconChange = { viewModel.setAppIcon(it) },
                        authState = authState,
                        authError = authError,
                        isAuthBusy = isAuthBusy,
                        isLoadingChannels = isLoadingChannels,
                        channels = channels,
                        onInitTdlib = { id, hash -> viewModel.telegramRepo.initialize(id, hash) },
                        onSubmitPhone = { viewModel.telegramRepo.submitPhoneNumber(it) },
                        onSubmitCode = { viewModel.telegramRepo.submitAuthCode(it) },
                        onResendCode = { viewModel.telegramRepo.resendAuthCode() },
                        onResetToPhone = { viewModel.telegramRepo.resetToPhoneInput() },
                        onSubmitPassword = { viewModel.telegramRepo.submitTwoFactorPassword(it) },
                        onLogout = { viewModel.logoutTelegram() },
                        onToggleChannel = { viewModel.toggleChannelSource(it) },
                        onRefreshChannels = { viewModel.refreshChannels() },
                        proxies = proxies,
                        isProxyBusy = isProxyBusy,
                        proxyError = proxyError,
                        proxyMessage = proxyMessage,
                        onToggleUseProxy = { viewModel.toggleUseProxy(it) },
                        onEnableProxy = { viewModel.enableProxy(it) },
                        onRemoveProxy = { viewModel.removeProxy(it) },
                        onPingProxy = { viewModel.pingProxy(it) },
                        onAddProxy = { type, server, port, user, pass, secret ->
                            viewModel.addProxy(type, server, port, user, pass, secret, enable = true)
                        },
                        onParseProxyLink = { raw -> viewModel.telegramRepo.parseProxyLink(raw) },
                        cacheStats = cacheStats,
                        isCacheBusy = isCacheBusy,
                        cacheMessage = cacheMessage,
                        cacheTtlDays = cacheTtlDays,
                        onRefreshCacheStats = { viewModel.refreshCacheStats() },
                        onClearCache = { a, c, o -> viewModel.clearCache(a, c, o) },
                        onCacheTtlChange = { viewModel.setCacheTtlDays(it) },
                        dacDevices = dacDevices,
                        dacOutputMode = dacOutputMode,
                        dacPreferredDeviceId = dacPreferredDeviceId,
                        dacRoutingSummary = dacRoutingSummary,
                        dacBitPerfectActive = dacBitPerfectActive,
                        streamFormat = streamFormat,
                        onSelectDacOutputMode = { viewModel.setDacOutputMode(it) },
                        onSelectDacDevice = { viewModel.setDacPreferredDeviceId(it) },
                        onRefreshDacDevices = { viewModel.refreshDacDevices() },
                        dspPreset = dspPreset,
                        skipSilence = skipSilence,
                        playbackSpeed = speed,
                        loudnessBoostDb = loudnessBoostDb,
                        seekStepSec = seekStepSec,
                        smartRewindEnabled = smartRewindEnabled,
                        onSelectDspPreset = { viewModel.setDspPreset(it) },
                        onToggleSkipSilence = { viewModel.setSkipSilence(it) },
                        onSelectPlaybackSpeed = { viewModel.setPlaybackSpeed(it) },
                        onSelectLoudnessBoostDb = { viewModel.setLoudnessBoostDb(it) },
                        onSelectSeekStepSec = { viewModel.setSeekStepSec(it) },
                        onToggleSmartRewind = { viewModel.setSmartRewindEnabled(it) }
                    )
                }
            }

            playlistPickerTrack?.let { pickedTrack ->
                AlertDialog(
                    onDismissRequest = {
                        playlistPickerTrack = null
                        quickNewPlaylistName = ""
                    },
                    title = {
                        Text(
                            text = "Добавить в плейлист",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .heightIn(max = 360.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = pickedTrack.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            playlists.forEach { pl ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            viewModel.addTrackToPlaylist(pl.id, pickedTrack)
                                            playlistPickerTrack = null
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                                    ) {
                                        Text(
                                            text = pl.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "${pl.items.size} аудио",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            OutlinedTextField(
                                value = quickNewPlaylistName,
                                onValueChange = { quickNewPlaylistName = it },
                                label = { Text("Новый плейлист") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            TextButton(
                                enabled = quickNewPlaylistName.isNotBlank(),
                                onClick = {
                                    viewModel.createPlaylist(quickNewPlaylistName, pickedTrack)
                                    quickNewPlaylistName = ""
                                    playlistPickerTrack = null
                                }
                            ) { Text("Создать и добавить") }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            playlistPickerTrack = null
                            quickNewPlaylistName = ""
                        }) { Text("Закрыть") }
                    }
                )
            }
        }
    }
}

private fun decodeSampledPaletteBitmap(path: String, maxDim: Int): Bitmap? = runCatching {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, opts)
    var sample = 1
    val largest = maxOf(opts.outWidth, opts.outHeight)
    while (largest > 0 && largest / sample > maxDim) {
        sample *= 2
    }
    BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.RGB_565
    }.let { BitmapFactory.decodeFile(path, it) }
}.getOrNull()
