package com.nexusstream.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusstream.app.media.MediaPlaybackService
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.model.PlaybackMode
import com.nexusstream.app.ui.player.PlayerScreen
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

    val activeService = playbackService
    val playbackState = activeService?.playbackState?.collectAsState()?.value
    val currentItem = playbackState?.currentItem

    NexusTheme(
        dominantColor = currentItem?.dominantColor ?: Color(0xFF6C5CE7),
        secondaryColor = currentItem?.secondaryColor ?: Color(0xFFFF7675)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Dual-Engine Mode Switcher Bar
                if (currentScreen == AppScreen.HOME) {
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
                            (scaleIn(
                                initialScale = 0.90f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            ) + fadeIn(tween(350))) togetherWith (scaleOut(
                                targetScale = 1.10f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            ) + fadeOut(tween(200)))
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
                                        }
                                    )
                                }
                            }
                            screen == AppScreen.PLAYER -> {
                                if (activeService != null) {
                                    PlayerScreen(
                                        playbackService = activeService,
                                        onCollapseSheet = { currentScreen = AppScreen.HOME }
                                    )
                                }
                            }
                            mode == AppMode.YOUTUBE_VIDEO -> {
                                YouTubeHomeScreen(
                                    currentPlayingItem = currentItem,
                                    isPlaying = playbackState?.isPlaying == true,
                                    onSelectVideo = { item, queue ->
                                        val idx = queue.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                                        activeService?.playQueue(queue, idx, startInVideoMode = true)
                                        currentScreen = AppScreen.WATCH
                                    },
                                    onExpandPlayer = {
                                        if (playbackState?.playbackMode == PlaybackMode.VIDEO) {
                                            currentScreen = AppScreen.WATCH
                                        } else {
                                            currentScreen = AppScreen.PLAYER
                                        }
                                    },
                                    onTogglePlayPause = { activeService?.togglePlayPause() }
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
 * Top Dual Engine Switcher: "YouTube Video" <-> "YouTube Music"
 */
@Composable
private fun DualEngineModeSwitcherBar(
    currentMode: AppMode,
    onModeSelect: (AppMode) -> Unit
) {
    val isMusic = currentMode == AppMode.YT_MUSIC

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(300.dp)
                .height(44.dp)
                .liquidGlassSurface(
                    shape = RoundedCornerShape(22.dp),
                    backgroundColor = Color.Black.copy(alpha = 0.50f),
                    borderWidth = 1.dp
                )
                .padding(3.dp)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val tabWidth = maxWidth / 2

                val offsetFraction = if (isMusic) 1f else 0f

                Box(
                    modifier = Modifier
                        .width(tabWidth)
                        .fillMaxSize()
                        .offset(x = tabWidth * offsetFraction)
                        .liquidGlassSurface(
                            shape = RoundedCornerShape(20.dp),
                            backgroundColor = Color.White.copy(alpha = 0.25f),
                            borderWidth = 1.dp
                        )
                )

                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onModeSelect(AppMode.YOUTUBE_VIDEO) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.OndemandVideo,
                                contentDescription = null,
                                tint = if (!isMusic) Color.White else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "YouTube",
                                fontSize = 13.sp,
                                fontWeight = if (!isMusic) FontWeight.Bold else FontWeight.Medium,
                                color = if (!isMusic) Color.White else Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onModeSelect(AppMode.YT_MUSIC) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = if (isMusic) Color.White else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "YT Music",
                                fontSize = 13.sp,
                                fontWeight = if (isMusic) FontWeight.Bold else FontWeight.Medium,
                                color = if (isMusic) Color.White else Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}
