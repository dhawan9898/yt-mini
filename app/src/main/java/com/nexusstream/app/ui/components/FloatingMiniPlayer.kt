package com.nexusstream.app.ui.components

import android.view.LayoutInflater
import com.nexusstream.app.R
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.nexusstream.app.media.MediaPlaybackService
import com.nexusstream.app.media.PlaybackState
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.model.PlaybackMode
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The minimized video: a small floating window with the live picture, a play / pause button
 * (top left), a close button (top right) and a thin progress line. Tap to expand; drag to move it,
 * and throw it far sideways to dismiss (which stops playback).
 */
@OptIn(UnstableApi::class)
@Composable
fun FloatingMiniPlayer(
    item: MediaItemModel,
    state: PlaybackState,
    playbackService: MediaPlaybackService,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val dragX = remember { Animatable(0f) }
    val dragY = remember { Animatable(0f) }
    val shape = RoundedCornerShape(12.dp)
    val playerWidth = 168.dp

    Box(
        modifier = modifier
            .offset { IntOffset(dragX.value.roundToInt(), dragY.value.roundToInt()) }
    ) {
    // Soft blurred glow under the window only: the sides stay clear, and it fades out downwards.
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .offset(y = 14.dp)
            .width(playerWidth - 8.dp)
            .height(28.dp)
            .blur(14.dp)
            .background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)),
                RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
            )
    )
    Box(
        modifier = Modifier
            .width(playerWidth)
            .aspectRatio(16f / 9f)
            .clip(shape)
            .background(Color.Black)
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (abs(dragX.value) > 120.dp.toPx()) {
                                onClose()
                            } else if (dragY.value < -48.dp.toPx()) {
                                // Swipe up maximizes.
                                onExpand()
                            }
                            // Settle back (springy) unless it was dismissed.
                            launch { dragX.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)) }
                            launch { dragY.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)) }
                        }
                    }
                ) { change, amount ->
                    change.consume()
                    scope.launch {
                        dragX.snapTo(dragX.value + amount.x)
                        dragY.snapTo((dragY.value + amount.y).coerceIn(-300f, 120f))
                    }
                }
            }
            .clickable(onClick = onExpand)
    ) {
        if (state.playbackMode == PlaybackMode.VIDEO) {
            AndroidView(
                factory = { ctx ->
                    (LayoutInflater.from(ctx).inflate(R.layout.view_player_texture, null, false) as PlayerView).apply {
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        player = playbackService.getPlayer()
                    }
                },
                update = { it.player = playbackService.getPlayer() },
                modifier = Modifier.fillMaxSize()
            )
        } else if (item.artworkUrl.isNotEmpty()) {
            AsyncImage(
                model = item.artworkUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Play / pause, top left.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .size(30.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable { playbackService.togglePlayPause() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (state.isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        // Close, top right.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(30.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Close, "Close player", tint = Color.White, modifier = Modifier.size(18.dp))
        }

        // Progress line.
        val fraction = if (state.durationMs > 0) (state.currentPositionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(3.dp)
                .background(Color.White.copy(alpha = 0.25f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(3.dp)
                    .background(OriginalColors.Red)
            )
        }
    }
    }
}
