package com.echogram.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.echogram.app.data.model.AppTab
import com.echogram.app.data.model.AuthState
import com.echogram.app.data.telegram.TelegramClientManager
import com.echogram.app.ui.MainViewModel
import com.echogram.app.ui.components.FullPlayerSheet
import com.echogram.app.ui.components.MiniPlayerBar
import com.echogram.app.ui.screens.AuthScreen
import com.echogram.app.ui.screens.ChatDetailScreen
import com.echogram.app.ui.screens.ChatsScreen
import com.echogram.app.ui.screens.LibraryScreen
import com.echogram.app.ui.screens.SearchScreen
import com.echogram.app.ui.screens.SettingsScreen
import com.echogram.app.ui.theme.EchoGramTheme

class MainActivity : ComponentActivity() {

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or declined */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationsIfNeeded()
        setContent { EchoGramRoot() }
    }

    override fun onStart() {
        super.onStart()
        TelegramClientManager.getInstance(applicationContext).setAppOnline(true)
    }

    override fun onStop() {
        super.onStop()
        TelegramClientManager.getInstance(applicationContext).setAppOnline(false)
    }

    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
private fun EchoGramRoot(vm: MainViewModel = viewModel()) {
    val authState by vm.authState.collectAsState()
    val authError by vm.authError.collectAsState()
    val isAuthBusy by vm.isAuthBusy.collectAsState()
    val connectionStatus by vm.connectionStatus.collectAsState()
    val themeMode by vm.themeMode.collectAsState()
    val proxies by vm.proxies.collectAsState()
    val uiMessage by vm.uiMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiMessage) {
        val msg = uiMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        vm.dismissUiMessage()
    }

    EchoGramTheme(themeMode = themeMode) {
        if (authState !is AuthState.Authorized) {
            AuthScreen(
                authState = authState,
                authError = authError,
                isBusy = isAuthBusy,
                connectionStatus = connectionStatus,
                proxies = proxies,
                onConfigureApi = { id, hash -> vm.telegram.configureAndStart(id, hash, saveAsCustom = true) },
                onSubmitPhone = { vm.telegram.submitPhoneNumber(it) },
                onRequestQr = { vm.telegram.requestQrLogin() },
                onSubmitCode = { vm.telegram.submitCode(it) },
                onResendCode = { vm.telegram.resendCode() },
                onSubmitEmail = { vm.telegram.submitEmailAddress(it) },
                onSubmitEmailCode = { vm.telegram.submitEmailCode(it) },
                onSubmitRegistration = { f, l -> vm.telegram.submitRegistration(f, l) },
                onSubmitPassword = { vm.telegram.submitPassword(it) },
                onReturnToPhone = { vm.telegram.returnToPhoneInput() },
                onClearError = { vm.telegram.clearAuthError() },
                onAddProxy = { t, s, p, u, pw, sec -> vm.addProxy(t, s, p, u, pw, sec) },
                onEnableProxy = { vm.enableProxy(it) },
                onDisableProxies = { vm.disableProxies() },
                onRemoveProxy = { vm.removeProxy(it) }
            )
            return@EchoGramTheme
        }

        val chats by vm.chats.collectAsState()
        val isLoadingChats by vm.isLoadingChats.collectAsState()
        val activeChat by vm.activeChat.collectAsState()
        val allTracks by vm.allLibraryTracks.collectAsState()
        val series by vm.bookSeries.collectAsState()
        val favorites by vm.favorites.collectAsState()
        val completedIds by vm.completedIds.collectAsState()
        val bookmarks by vm.bookmarks.collectAsState()
        val offlineTracks by vm.downloadedTracks.collectAsState()
        val offlineIds by vm.downloadedTrackIds.collectAsState()
        val downloadingProgress by vm.downloadingProgress.collectAsState()
        val globalSearchResults by vm.globalSearchResults.collectAsState()
        val isSearchingGlobal by vm.isSearchingGlobal.collectAsState()

        val currentTrack by vm.currentTrack.collectAsState()
        val isPlaying by vm.isPlaying.collectAsState()
        val isBuffering by vm.isBuffering.collectAsState()
        val speed by vm.playbackSpeed.collectAsState()
        val skipSilence by vm.skipSilence.collectAsState()
        val seekStepSec by vm.seekStepSec.collectAsState()
        val smartRewind by vm.smartRewind.collectAsState()
        val sleepTimer by vm.sleepTimer.collectAsState()
        val dspPreset by vm.dspPreset.collectAsState()
        val loudnessBoostDb by vm.loudnessBoostDb.collectAsState()
        val dacMode by vm.dacMode.collectAsState()
        val dacDevices by vm.dacDevices.collectAsState()
        val dacSummary by vm.dacRoutingSummary.collectAsState()
        val streamFormat by vm.streamFormat.collectAsState()
        val cacheStats by vm.cacheStats.collectAsState()
        val chaptersState by vm.embeddedChapters.collectAsState()

        val favoriteIds = remember(favorites) { favorites.map { it.trackId }.toSet() }
        var currentTab by rememberSaveable { mutableStateOf(AppTab.CHATS) }
        var showFullPlayer by remember { mutableStateOf(false) }

        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    Column {
                        currentTrack?.let { cur ->
                            MiniPlayerBar(
                                track = cur,
                                isPlaying = isPlaying,
                                isBuffering = isBuffering,
                                positionMsFlow = vm.positionMs,
                                durationMsFlow = vm.durationMs,
                                onExpand = { showFullPlayer = true },
                                onTogglePlayPause = { vm.togglePlayPause() },
                                onNext = { vm.playNext() }
                            )
                        }
                        NavigationBar {
                            AppTab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = currentTab == tab,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            when (tab) {
                                                AppTab.CHATS -> Icons.Default.Forum
                                                AppTab.LIBRARY -> Icons.Default.LibraryMusic
                                                AppTab.SEARCH -> Icons.Default.Search
                                                AppTab.SETTINGS -> Icons.Default.Settings
                                            },
                                            contentDescription = tab.title
                                        )
                                    },
                                    label = { Text(tab.title) }
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    when (currentTab) {
                        AppTab.CHATS -> ChatsScreen(
                            chats = chats,
                            isLoading = isLoadingChats,
                            connectionStatus = connectionStatus,
                            onRefresh = { vm.refreshChats() },
                            onOpenChat = { vm.openChat(it) },
                            onToggleStarred = { vm.toggleStarredSource(it) }
                        )

                        AppTab.LIBRARY -> LibraryScreen(
                            allTracks = allTracks,
                            series = series,
                            offlineTracks = offlineTracks,
                            favorites = favorites,
                            bookmarks = bookmarks,
                            currentTrack = currentTrack,
                            isPlaying = isPlaying,
                            favoriteIds = favoriteIds,
                            completedIds = completedIds,
                            offlineIds = offlineIds,
                            downloadingProgress = downloadingProgress,
                            onPlayTrack = { t, q -> vm.playTrack(t, q) },
                            onTogglePlayPause = { vm.togglePlayPause() },
                            onToggleFavorite = { vm.toggleFavorite(it) },
                            onDownloadOffline = { vm.downloadOffline(it) },
                            onRemoveOffline = { vm.removeOffline(it) },
                            onSeekToBookmark = { bm ->
                                val match = allTracks.firstOrNull { it.trackId == bm.trackId } ?: currentTrack
                                if (match != null) {
                                    if (currentTrack?.trackId != match.trackId) vm.playTrack(match)
                                    vm.seekTo(bm.positionSec * 1000L)
                                    showFullPlayer = true
                                }
                            },
                            onDeleteBookmark = { vm.deleteBookmark(it) },
                            onOpenFullPlayer = { showFullPlayer = true }
                        )

                        AppTab.SEARCH -> SearchScreen(
                            allTracks = allTracks,
                            globalChats = globalSearchResults,
                            isSearchingGlobal = isSearchingGlobal,
                            currentTrack = currentTrack,
                            isPlaying = isPlaying,
                            favoriteIds = favoriteIds,
                            completedIds = completedIds,
                            offlineIds = offlineIds,
                            downloadingProgress = downloadingProgress,
                            onQueryGlobalChats = { vm.searchPublicChannels(it) },
                            onJoinAndOpenChat = { vm.joinAndOpenChat(it) },
                            onPlayTrack = { t, q -> vm.playTrack(t, q) },
                            onTogglePlayPause = { vm.togglePlayPause() },
                            onToggleFavorite = { vm.toggleFavorite(it) },
                            onDownloadOffline = { vm.downloadOffline(it) }
                        )

                        AppTab.SETTINGS -> SettingsScreen(
                            authState = authState,
                            connectionStatus = connectionStatus,
                            themeMode = themeMode,
                            playbackSpeed = speed,
                            skipSilence = skipSilence,
                            seekStepSec = seekStepSec,
                            smartRewind = smartRewind,
                            dspPreset = dspPreset,
                            loudnessBoostDb = loudnessBoostDb,
                            dacMode = dacMode,
                            dacDevices = dacDevices,
                            dacRoutingSummary = dacSummary,
                            streamFormat = streamFormat,
                            proxies = proxies,
                            cacheStats = cacheStats,
                            onThemeModeChange = { vm.setThemeMode(it) },
                            onSpeedChange = { vm.setPlaybackSpeed(it) },
                            onSkipSilenceChange = { vm.setSkipSilence(it) },
                            onSeekStepChange = { vm.setSeekStepSec(it) },
                            onSmartRewindChange = { vm.setSmartRewind(it) },
                            onDspPresetChange = { vm.setDspPreset(it) },
                            onLoudnessBoostChange = { vm.setLoudnessBoostDb(it) },
                            onDacModeChange = { vm.setDacMode(it) },
                            onRefreshDac = { vm.refreshDacDevices() },
                            onAddProxy = { t, s, p, u, pw, sec -> vm.addProxy(t, s, p, u, pw, sec) },
                            onEnableProxy = { vm.enableProxy(it) },
                            onDisableProxies = { vm.disableProxies() },
                            onRemoveProxy = { vm.removeProxy(it) },
                            onPingProxy = { vm.pingProxy(it) },
                            onClearCache = { vm.clearStreamingCache() },
                            onLogout = { vm.logout() }
                        )
                    }
                }
            }

            if (activeChat.chat != null) {
                ChatDetailScreen(
                    state = activeChat,
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    favoriteIds = favoriteIds,
                    completedIds = completedIds,
                    offlineIds = offlineIds,
                    downloadingProgress = downloadingProgress,
                    onBack = { vm.closeActiveChat() },
                    onToggleStar = { activeChat.chat?.let { vm.toggleStarredSource(it) } },
                    onLoadOlder = { vm.loadOlderMessages() },
                    onSendMessage = { vm.sendChatMessage(it) },
                    onPlayTrack = { t, q -> vm.playTrack(t, q) },
                    onTogglePlayPause = { vm.togglePlayPause() },
                    onToggleFavorite = { vm.toggleFavorite(it) },
                    onDownloadOffline = { vm.downloadOffline(it) }
                )
            }

            if (showFullPlayer && currentTrack != null) {
                val cur = currentTrack!!
                FullPlayerSheet(
                    track = cur,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    positionMsFlow = vm.positionMs,
                    durationMsFlow = vm.durationMs,
                    playbackSpeed = speed,
                    seekStepSec = seekStepSec,
                    sleepTimer = sleepTimer,
                    chapters = if (chaptersState.first == cur.trackId) chaptersState.second else emptyList(),
                    onDismiss = { showFullPlayer = false },
                    onTogglePlayPause = { vm.togglePlayPause() },
                    onSeekTo = { vm.seekTo(it) },
                    onSkipSeconds = { vm.skipBySeconds(it) },
                    onNext = { vm.playNext() },
                    onPrevious = { vm.playPrevious() },
                    onSelectSpeed = { vm.setPlaybackSpeed(it) },
                    onSelectSleepTimer = { vm.setSleepTimerMinutes(it) },
                    onAddBookmark = { posSec, note -> vm.addBookmark(cur, posSec, note) }
                )
            }
        }
    }
}
