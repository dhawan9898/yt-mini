package com.nexusstream.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.delay
import com.nexusstream.app.ui.youtube.NotificationsScreen
import com.nexusstream.app.ui.youtube.NotificationSettingsScreen
import com.nexusstream.app.notifications.NotificationHelper
import com.nexusstream.app.notifications.BackgroundJobs
import com.nexusstream.app.data.YouTubeRepository
import com.nexusstream.app.LaunchRequests
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import com.nexusstream.app.ui.youtube.SearchScreen
import com.nexusstream.app.ui.youtube.UploadScreen
import com.nexusstream.app.ui.youtube.TimeWatchedScreen
import com.nexusstream.app.ui.youtube.AccountFeedScreen
import com.nexusstream.app.ui.youtube.PlaylistScreen
import com.nexusstream.app.ui.youtube.ChannelScreen
import com.nexusstream.app.ui.youtube.AllSubscriptionsScreen
import com.nexusstream.app.ui.youtube.DownloadsScreen
import com.nexusstream.app.ui.youtube.HistoryScreen
import com.nexusstream.app.ui.youtube.LikedVideosScreen
import com.nexusstream.app.ui.youtube.LocalPlaylistScreen
import com.nexusstream.app.ui.youtube.PlaylistsScreen
import com.nexusstream.app.ui.youtube.SettingsScreen
import com.nexusstream.app.ui.youtube.GoogleSignInScreen
import com.nexusstream.app.ui.youtube.WatchLaterScreen
import com.nexusstream.app.ui.ytmusic.MusicBrowseScreen
import com.nexusstream.app.ui.components.MiniPlayerHooks
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.key
import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusstream.app.data.AppSettings
import com.nexusstream.app.data.DownloadRepository
import com.nexusstream.app.data.LibraryRepository
import com.nexusstream.app.media.MediaPlaybackService
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.model.PlaybackMode
import com.nexusstream.app.ui.components.OriginalColors
import com.nexusstream.app.ui.player.MusicPlayerScreen
import com.nexusstream.app.ui.theme.NexusTheme
import com.nexusstream.app.ui.theme.liquidGlassSurface
import com.nexusstream.app.ui.youtube.YouTubeHomeScreen
import com.nexusstream.app.ui.youtube.VideoWatchScreen
import com.nexusstream.app.ui.ytmusic.YTMusicHomeScreen

enum class AppMode {
    YOUTUBE_VIDEO,
    YT_MUSIC
}

enum class AppScreen {
    HOME,
    WATCH,
    PLAYER
}

/**
 * Root NexusStream App Shell:
 * - Tap the YouTube Premium / Music logo at the top to switch between YouTube and YT Music.
 * - Complete removal of Shorts and Games from feeds.
 * - Unlocked Premium features: Background playback, PiP, Ad-free, Lossless audio.
 */
@Composable
fun NexusApp() {
    val context = LocalContext.current
    var playbackService by remember { mutableStateOf<MediaPlaybackService?>(null) }
    
    var currentMode by remember { mutableStateOf(AppMode.YOUTUBE_VIDEO) }
    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }

    // Pages (search, channel, playlist...) stacked above the YouTube home screen.
    val backStack = remember { mutableStateListOf<Route>() }
    val nav = remember {
        fun push(route: Route) {
            // Leave the watch / player screen so the page is visible (playback continues).
            currentScreen = AppScreen.HOME
            if (backStack.lastOrNull() != route) backStack.add(route)
        }
        YouTubeNav(
            openSearch = { push(Route.Search) },
            openChannel = { url, name, avatar -> push(Route.Channel(url, name, avatar)) },
            openPlaylist = { url, title -> push(Route.Playlist(url, title)) },
            openAllSubscriptions = { push(Route.AllSubscriptions) },
            openMusicChannel = { url, name, avatar ->
                val fallback = Route.Channel(url, name, avatar, music = true)
                push(musicRouteFor(url, name)?.copy(fallback = fallback) ?: fallback)
            },
            openMusicPlaylist = { url, title ->
                val fallback = playlistFallbackFor(url, title)
                push(musicRouteFor(url, title)?.copy(fallback = fallback) ?: fallback ?: Route.Playlist(url, title, music = true))
            },
            open = { route -> push(route) },
            back = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex) }
        )
    }

    // Service binding
    DisposableEffect(Unit) {
        val serviceIntent = Intent(context, MediaPlaybackService::class.java)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as? MediaPlaybackService.LocalBinder
                playbackService = binder?.getService()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                playbackService = null
            }
        }

        try {
            androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
            context.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            android.util.Log.e("NexusApp", "Failed to start or bind MediaPlaybackService: ${e.message}")
        }

        onDispose {
            try {
                context.unbindService(connection)
            } catch (e: Exception) {
                android.util.Log.w("NexusApp", "Unbind service failed: ${e.message}")
            }
        }
    }

    LaunchedEffect(Unit) {
        LibraryRepository.init(context)
        com.nexusstream.app.data.TvLink.restore()
        com.nexusstream.app.data.GoogleAccount.init(context)
        DownloadRepository.init(context)
        NotificationHelper.ensureChannels(context)
    }

    // The signed-in account's library (likes, Watch later, playlists, subscriptions) replaces the
    // on-device copy; changes YouTube refuses are rolled back and reported here.
    val signedIn by com.nexusstream.app.data.GoogleAccount.signedIn.collectAsState()
    LaunchedEffect(signedIn) {
        if (signedIn) LibraryRepository.syncAccount(force = true)
    }
    LaunchedEffect(Unit) {
        LibraryRepository.syncErrors.collect { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Background work follows the notification settings.
    val notificationsEnabled by LibraryRepository.notificationsEnabled.collectAsState()
    val bedtimeMinute by LibraryRepository.bedtimeMinute.collectAsState()
    val breakMinutes by LibraryRepository.breakMinutes.collectAsState()
    val country by AppSettings.contentCountry.collectAsState()
    LaunchedEffect(country) {
        YouTubeRepository.warmUp()
        YouTubeRepository.applyRegion(country)
    }
    LaunchedEffect(notificationsEnabled) {
        if (notificationsEnabled) BackgroundJobs.scheduleUploadChecks(context) else BackgroundJobs.cancelUploadChecks(context)
    }
    val smartDownloads by AppSettings.smartDownloads.collectAsState()
    LaunchedEffect(smartDownloads) {
        if (smartDownloads > 0) BackgroundJobs.scheduleSmartDownloads(context) else BackgroundJobs.cancelSmartDownloads(context)
    }
    val appLanguage by AppSettings.appLanguage.collectAsState()
    LaunchedEffect(appLanguage) {
        YouTubeRepository.applyRegion(AppSettings.contentCountry.value)
        // Android 13+: the per-app language in system settings follows this choice.
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            runCatching {
                context.getSystemService(android.app.LocaleManager::class.java)?.applicationLocales =
                    if (appLanguage.isBlank()) android.os.LocaleList.getEmptyLocaleList() else android.os.LocaleList.forLanguageTags(appLanguage)
            }
        }
    }
    LaunchedEffect(bedtimeMinute) {
        if (bedtimeMinute >= 0) BackgroundJobs.scheduleBedtime(context, bedtimeMinute) else BackgroundJobs.cancelBedtime(context)
    }

    val activeService = playbackService
    val playbackState = activeService?.playbackState?.collectAsState()?.value
    val currentItem = playbackState?.currentItem

    // Feeds "Listen again" and the You tab's history.
    LaunchedEffect(currentItem?.id) {
        currentItem?.takeIf { !it.isShort }?.let { LibraryRepository.recordPlayed(it) }
    }

    BackHandler(enabled = backStack.isNotEmpty() && currentScreen == AppScreen.HOME) { nav.back() }

    val playVideo: (MediaItemModel, List<MediaItemModel>) -> Unit = { item, queue ->
        // Playlists play in order; everywhere else the video plays alone and then continues
        // with related videos (autoplay), like the original app.
        val inPlaylist = backStack.lastOrNull() is Route.Playlist
        val playQueue = if (inPlaylist) queue else listOf(item)
        val idx = playQueue.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
        activeService?.playQueue(playQueue, idx, startInVideoMode = true)
        currentScreen = AppScreen.WATCH
    }
    val closePlayer: () -> Unit = {
        activeService?.closePlayback()
        currentScreen = AppScreen.HOME
    }
    val expandPlayer: () -> Unit = {
        currentScreen = if (playbackState?.playbackMode == PlaybackMode.VIDEO) AppScreen.WATCH else AppScreen.PLAYER
    }

    val miniPlayer = MiniPlayerHooks(
        item = currentItem,
        isPlaying = playbackState?.isPlaying == true,
        onExpand = expandPlayer,
        onTogglePlay = { activeService?.togglePlayPause() },
        onClose = closePlayer
    )
    // Music tracks (albums, artist pages) play as audio in the now-playing screen.
    val playTrack: (MediaItemModel, List<MediaItemModel>) -> Unit = { item, queue ->
        val idx = queue.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
        activeService?.playQueue(queue, idx, startInVideoMode = false)
        currentScreen = AppScreen.PLAYER
    }

    // A video opened from a notification, a YouTube link or shared text.
    val launchRequest by LaunchRequests.video.collectAsState()
    LaunchedEffect(launchRequest, activeService) {
        val request = launchRequest ?: return@LaunchedEffect
        if (activeService == null) return@LaunchedEffect
        LaunchRequests.consumeVideo()
        val basic = LaunchRequests.minimalItem(request)
        val item = if (request.title.isNotBlank()) {
            basic
        } else {
            YouTubeRepository.videoFromUrl(basic.sourceUrl).getOrNull() ?: basic
        }
        playVideo(item, listOf(item))
    }

    // App shortcuts and widgets.
    val destination by LaunchRequests.destination.collectAsState()
    LaunchedEffect(destination) {
        val target = destination ?: return@LaunchedEffect
        LaunchRequests.consumeDestination()
        when (target) {
            "search" -> { currentMode = AppMode.YOUTUBE_VIDEO; nav.openSearch() }
            "shorts" -> { currentMode = AppMode.YOUTUBE_VIDEO; backStack.clear(); currentScreen = AppScreen.HOME; LaunchRequests.homeTab.value = 1 }
            "subscriptions" -> { currentMode = AppMode.YOUTUBE_VIDEO; backStack.clear(); currentScreen = AppScreen.HOME; LaunchRequests.homeTab.value = 2 }
            "music" -> { backStack.clear(); currentScreen = AppScreen.HOME; currentMode = AppMode.YT_MUSIC }
        }
    }

    // "Take a break": after N minutes of continuous playback.
    var showBreakDialog by remember { mutableStateOf(false) }
    val isPlayingNow = playbackState?.isPlaying == true
    LaunchedEffect(breakMinutes, isPlayingNow) {
        if (breakMinutes > 0 && isPlayingNow) {
            delay(breakMinutes * 60_000L)
            showBreakDialog = true
        }
    }

    CompositionLocalProvider(LocalPlaybackService provides activeService, LocalNav provides nav) {
    NexusTheme(
        dominantColor = currentItem?.dominantColor ?: Color(0xFF6C5CE7),
        secondaryColor = currentItem?.secondaryColor ?: Color(0xFFFF7675)
    ) {
        val shellBackground by animateColorAsState(
            targetValue = if (currentMode == AppMode.YT_MUSIC) OriginalColors.MusicBackground else OriginalColors.YouTubeBackground,
            animationSpec = tween(300),
            label = "ShellBackground"
        )
        Box(modifier = Modifier.fillMaxSize().background(shellBackground)) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Screen Switcher
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AnimatedContent(
                        targetState = Pair(currentMode, currentScreen),
                        transitionSpec = {
                            fadeIn(tween(220)) togetherWith fadeOut(tween(160))
                        },
                        label = "AppScreenMorph"
                    ) { (mode, screen) ->
                        when {
                            screen == AppScreen.WATCH -> {
                                if (activeService != null) {
                                    VideoWatchScreen(
                                        playbackService = activeService,
                                        onCollapse = { currentScreen = AppScreen.HOME },
                                        onSelectVideo = { item ->
                                            activeService.playMediaItem(item, startInVideoMode = true)
                                        },
                                        onOpenChannel = { item -> nav.openChannelOf(item) },
                                        onClose = closePlayer
                                    )
                                }
                            }
                            screen == AppScreen.PLAYER -> {
                                if (activeService != null) {
                                    MusicPlayerScreen(
                                        playbackService = activeService,
                                        onCollapse = { currentScreen = AppScreen.HOME },
                                        onOpenPlaylist = nav.openMusicPlaylist,
                                        onOpenArtist = nav.openMusicChannel
                                    )
                                }
                            }
                            mode == AppMode.YOUTUBE_VIDEO -> {
                                // Home stays composed underneath the pages so its tab and scroll state survive.
                                YouTubeHomeScreen(
                                    currentPlayingItem = currentItem,
                                    isPlaying = playbackState?.isPlaying == true,
                                    onSelectVideo = playVideo,
                                    onExpandPlayer = expandPlayer,
                                    onTogglePlayPause = { activeService?.togglePlayPause() },
                                    onClosePlayer = closePlayer,
                                    nav = nav,
                                    playbackService = activeService,
                                    onSwitchApp = { currentMode = AppMode.YT_MUSIC }
                                )
                            }
                            mode == AppMode.YT_MUSIC -> {
                                YTMusicHomeScreen(
                                    currentPlayingItem = currentItem,
                                    isPlaying = playbackState?.isPlaying == true,
                                    onSelectTrack = { item, queue ->
                                        val idx = queue.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                                        activeService?.playQueue(queue, idx, startInVideoMode = false)
                                        currentScreen = AppScreen.PLAYER
                                    },
                                    onExpandPlayer = { currentScreen = AppScreen.PLAYER },
                                    onTogglePlayPause = { activeService?.togglePlayPause() },
                                    onClosePlayer = closePlayer,
                                    nav = nav,
                                    onSwitchApp = { currentMode = AppMode.YOUTUBE_VIDEO }
                                )
                            }
                        }
                    }

                    // Pages (search, channel, album...) stack above either home screen.
                    if (currentScreen == AppScreen.HOME) {
                        backStack.forEachIndexed { index, route ->
                            key(index, route) {
                                when (route) {
                                    is Route.Search -> SearchScreen(nav, playVideo, miniPlayer)
                                    is Route.Channel -> ChannelScreen(route, nav, if (route.music) playTrack else playVideo, miniPlayer)
                                    is Route.Playlist -> PlaylistScreen(route, nav, if (route.music) playTrack else playVideo, miniPlayer)
                                    is Route.AllSubscriptions -> AllSubscriptionsScreen(nav, miniPlayer)
                                    is Route.History -> HistoryScreen(nav, playVideo, miniPlayer)
                                    is Route.WatchLater -> WatchLaterScreen(nav, playVideo, miniPlayer)
                                    is Route.Liked -> LikedVideosScreen(nav, playVideo, miniPlayer)
                                    is Route.Downloads -> DownloadsScreen(nav, playVideo, miniPlayer)
                                    is Route.Playlists -> PlaylistsScreen(nav, miniPlayer)
                                    is Route.LocalPlaylist -> LocalPlaylistScreen(route, nav, playVideo, miniPlayer)
                                    is Route.Notifications -> NotificationsScreen(nav, playVideo, miniPlayer)
                                    is Route.NotificationSettings -> NotificationSettingsScreen(nav, miniPlayer)
                                    is Route.Settings -> SettingsScreen(nav, miniPlayer)
                                    is Route.SignIn -> GoogleSignInScreen(nav)
                                    is Route.Upload -> UploadScreen(nav, miniPlayer)
                                    is Route.TimeWatched -> TimeWatchedScreen(nav, miniPlayer)
                                    is Route.AccountFeed -> AccountFeedScreen(route, nav, playVideo, miniPlayer)
                                    is Route.MusicBrowse -> MusicBrowseScreen(route, nav, playTrack, miniPlayer) { fallback ->
                                        when (fallback) {
                                            is Route.Channel -> ChannelScreen(fallback, nav, playTrack, miniPlayer)
                                            is Route.Playlist -> PlaylistScreen(fallback, nav, playTrack, miniPlayer)
                                            else -> Unit
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showBreakDialog) {
            AlertDialog(
                onDismissRequest = { showBreakDialog = false },
                containerColor = OriginalColors.Sheet,
                title = { Text("Time for a break", color = OriginalColors.TextPrimary) },
                text = {
                    Text(
                        "You've been watching for $breakMinutes minutes.",
                        color = OriginalColors.TextSecondary
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (playbackState?.isPlaying == true) activeService?.togglePlayPause()
                        showBreakDialog = false
                    }) { Text("Pause", color = Color(0xFF3EA6FF)) }
                },
                dismissButton = {
                    TextButton(onClick = { showBreakDialog = false }) {
                        Text("Keep watching", color = OriginalColors.TextSecondary)
                    }
                }
            )
        }
    }
    }
}
