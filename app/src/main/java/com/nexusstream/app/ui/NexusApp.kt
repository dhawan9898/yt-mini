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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.nexusstream.app.media.MediaPlaybackService
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.model.PlaybackMode
import com.nexusstream.app.ui.feed.FeedScreen
import com.nexusstream.app.ui.player.PlayerScreen
import com.nexusstream.app.ui.theme.NexusTheme

enum class AppScreen {
    FEED,
    PLAYER
}

/**
 * Root composable managing service binding, state synchronization,
 * and liquid screen morphing transitions.
 */
@Composable
fun NexusApp() {
    val context = LocalContext.current
    var playbackService by remember { mutableStateOf<MediaPlaybackService?>(null) }
    var currentScreen by remember { mutableStateOf(AppScreen.FEED) }

    // Bind to MediaPlaybackService lifecycle
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

        context.startService(serviceIntent)
        context.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)

        onDispose {
            context.unbindService(connection)
        }
    }

    val activeService = playbackService
    val playbackState = activeService?.playbackState?.collectAsState()?.value
    val currentItem = playbackState?.currentItem

    NexusTheme(
        dominantColor = currentItem?.dominantColor ?: androidx.compose.ui.graphics.Color(0xFF6C5CE7),
        secondaryColor = currentItem?.secondaryColor ?: androidx.compose.ui.graphics.Color(0xFFFF7675)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Liquid morph transitions replacing standard slide animations
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    (scaleIn(
                        initialScale = 0.85f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ) + fadeIn(tween(400))) togetherWith (scaleOut(
                        targetScale = 1.15f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) + fadeOut(tween(250)))
                },
                label = "LiquidScreenMorph"
            ) { screen ->
                when (screen) {
                    AppScreen.FEED -> {
                        FeedScreen(
                            currentPlayingItem = currentItem,
                            isPlaying = playbackState?.isPlaying == true,
                            onItemClick = { item, queue, mode ->
                                val index = queue.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                                activeService?.playQueue(queue, index, startInVideoMode = mode == PlaybackMode.VIDEO)
                                currentScreen = AppScreen.PLAYER
                            },
                            onExpandPlayer = { currentScreen = AppScreen.PLAYER },
                            onTogglePlayPause = { activeService?.togglePlayPause() }
                        )
                    }
                    AppScreen.PLAYER -> {
                        if (activeService != null) {
                            PlayerScreen(
                                playbackService = activeService,
                                onCollapseSheet = { currentScreen = AppScreen.FEED }
                            )
                        }
                    }
                }
            }
        }
    }
}
