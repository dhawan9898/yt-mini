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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nexusstream.app.ui.youtube.SearchScreen
import com.nexusstream.app.ui.youtube.PlaylistScreen
import com.nexusstream.app.ui.youtube.ChannelScreen
import com.nexusstream.app.ui.youtube.AllSubscriptionsScreen
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
 * - Fluid Glass dual-mode switcher (YouTube Video vs YT Music).
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

    LaunchedEffect(Unit) { LibraryRepository.init(context) }

    val activeService = playbackService
    val playbackState = activeService?.playbackState?.collectAsState()?.value
    val currentItem = playbackState?.currentItem

    // Feeds "Listen again" and the You tab's history.
    LaunchedEffect(currentItem?.id) {
        currentItem?.let { LibraryRepository.recordPlayed(it) }
    }

    BackHandler(enabled = backStack.isNotEmpty() && currentScreen == AppScreen.HOME) { nav.back() }

    val playVideo: (MediaItemModel, List<MediaItemModel>) -> Unit = { item, queue ->
        val idx = queue.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
        activeService?.playQueue(queue, idx, startInVideoMode = true)
        currentScreen = AppScreen.WATCH
    }
    val expandPlayer: () -> Unit = {
        currentScreen = if (playbackState?.playbackMode == PlaybackMode.VIDEO) AppScreen.WATCH else AppScreen.PLAYER
    }

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
                // Top Dual-Engine Mode Switcher Bar
                if (currentScreen == AppScreen.HOME && backStack.isEmpty()) {
                    DualEngineModeSwitcherBar(
                        currentMode = currentMode,
                        onModeSelect = { mode ->
                            currentMode = mode
                        }
                    )
                }

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
                                        onOpenChannel = { item -> nav.openChannelOf(item) }
                                    )
                                }
                            }
                            screen == AppScreen.PLAYER -> {
                                if (activeService != null) {
                                    MusicPlayerScreen(
                                        playbackService = activeService,
                                        onCollapse = { currentScreen = AppScreen.HOME }
                                    )
                                }
                            }
                            mode == AppMode.YOUTUBE_VIDEO -> {
                                val miniPlayer = MiniPlayerHooks(
                                    item = currentItem,
                                    isPlaying = playbackState?.isPlaying == true,
                                    onExpand = expandPlayer,
                                    onTogglePlay = { activeService?.togglePlayPause() }
                                )
                                Box(modifier = Modifier.fillMaxSize()) {
                                    // Home stays composed underneath so its tab and scroll state survive.
                                    YouTubeHomeScreen(
                                        currentPlayingItem = currentItem,
                                        isPlaying = playbackState?.isPlaying == true,
                                        onSelectVideo = playVideo,
                                        onExpandPlayer = expandPlayer,
                                        onTogglePlayPause = { activeService?.togglePlayPause() },
                                        nav = nav
                                    )
                                    backStack.forEachIndexed { index, route ->
                                        key(index, route) {
                                            when (route) {
                                                is Route.Search -> SearchScreen(nav, playVideo, miniPlayer)
                                                is Route.Channel -> ChannelScreen(route, nav, playVideo, miniPlayer)
                                                is Route.Playlist -> PlaylistScreen(route, nav, playVideo, miniPlayer)
                                                is Route.AllSubscriptions -> AllSubscriptionsScreen(nav, miniPlayer)
                                            }
                                        }
                                    }
                                }
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
                                    onTogglePlayPause = { activeService?.togglePlayPause() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Top mode pill: "YouTube" <-> "YT Music". Flat dark track with a white sliding thumb.
 */
@Composable
private fun DualEngineModeSwitcherBar(
    currentMode: AppMode,
    onModeSelect: (AppMode) -> Unit
) {
    val isMusic = currentMode == AppMode.YT_MUSIC
    val thumbFraction by animateFloatAsState(
        targetValue = if (isMusic) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
        label = "PillThumb"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 8.dp, bottom = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .width(220.dp)
                .height(36.dp)
                .clip(CircleShape)
                .background(OriginalColors.Chip)
                .padding(3.dp)
        ) {
            val tabWidth = maxWidth / 2

            Box(
                modifier = Modifier
                    .width(tabWidth)
                    .fillMaxHeight()
                    .offset(x = tabWidth * thumbFraction)
                    .clip(CircleShape)
                    .background(Color.White)
            )

            Row(modifier = Modifier.fillMaxSize()) {
                PillTab(
                    label = "YouTube",
                    icon = Icons.Rounded.PlayArrow,
                    selected = !isMusic,
                    onClick = { onModeSelect(AppMode.YOUTUBE_VIDEO) },
                    modifier = Modifier.weight(1f)
                )
                PillTab(
                    label = "YT Music",
                    icon = Icons.Rounded.MusicNote,
                    selected = isMusic,
                    onClick = { onModeSelect(AppMode.YT_MUSIC) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PillTab(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) Color.Black else Color.White.copy(alpha = 0.7f),
        label = "PillTabColor"
    )
    Row(
        modifier = modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
    }
}
