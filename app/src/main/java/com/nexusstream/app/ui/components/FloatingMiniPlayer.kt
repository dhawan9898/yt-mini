package com.nexusstream.app.ui.components

import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.nexusstream.app.media.MediaPlaybackService
import com.nexusstream.app.media.PlaybackState
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.model.PlaybackMode
import kotlin.math.min
import kotlin.math.roundToInt

/** Where the floating player sits and how wide it is; kept across screens while the app runs. */
private object FloatingWindowState {
    /** Offset from the bottom-end corner, in pixels (both values are 0 or negative). */
    var offsetX by mutableFloatStateOf(0f)
    var offsetY by mutableFloatStateOf(0f)
    var widthDp by mutableFloatStateOf(DEFAULT_WIDTH_DP)
}

private const val DEFAULT_WIDTH_DP = 180f
private const val MIN_WIDTH_DP = 128f
private const val MAX_WIDTH_DP = 360f

/** [widthDp] limited to the allowed sizes, and never wider than [maxDp] (the area's width). */
private fun clampWidth(widthDp: Float, maxDp: Float): Float =
    widthDp.coerceAtLeast(MIN_WIDTH_DP).coerceAtMost(maxDp)

/**
 * The minimized video: a small floating window with the live picture, a play / pause button
 * (top left), a close button (top right) and a thin progress line. Tap to expand. Drag with one
 * finger to move it anywhere in the area it is given; pinch with two fingers to make it bigger or
 * smaller. It stays where it is let go.
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
    val shape = RoundedCornerShape(12.dp)
    val window = FloatingWindowState

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val areaWidth = constraints.maxWidth.toFloat()
        val areaHeight = constraints.maxHeight.toFloat()
        val maxWidthDp = min(MAX_WIDTH_DP, with(density) { areaWidth.toDp().value })
        val widthDp = clampWidth(window.widthDp, maxWidthDp)
        val widthPx = with(density) { widthDp.dp.toPx() }
        val heightPx = widthPx * 9f / 16f
        // Keep the window fully inside the area, also after a resize or a rotation.
        val minX = -(areaWidth - widthPx).coerceAtLeast(0f)
        val minY = -(areaHeight - heightPx).coerceAtLeast(0f)
        val x = window.offsetX.coerceIn(minX, 0f)
        val y = window.offsetY.coerceIn(minY, 0f)
        val currentArea by rememberUpdatedState(Size(areaWidth, areaHeight))

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
        ) {
            // Soft blurred glow under the window only: the sides stay clear, and it fades out downwards.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 14.dp)
                    .width(widthDp.dp - 8.dp)
                    .height(28.dp)
                    .blur(14.dp)
                    .background(
                        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)),
                        RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                    )
            )
            Box(
                modifier = Modifier
                    .width(widthDp.dp)
                    .aspectRatio(16f / 9f)
                    .clip(shape)
                    .background(Color.Black)
                    .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            // Pinch resizes around the bottom-end corner; one finger moves the window.
                            val area = currentArea
                            val maxW = min(MAX_WIDTH_DP, area.width.toDp().value)
                            val newWidth = clampWidth(clampWidth(window.widthDp, maxW) * zoom, maxW)
                            window.widthDp = newWidth
                            val w = newWidth.dp.toPx()
                            val lowX = -(area.width - w).coerceAtLeast(0f)
                            val lowY = -(area.height - w * 9f / 16f).coerceAtLeast(0f)
                            window.offsetX = (window.offsetX + pan.x).coerceIn(lowX, 0f)
                            window.offsetY = (window.offsetY + pan.y).coerceIn(lowY, 0f)
                        }
                    }
                    .clickable(onClick = onExpand)
            ) {
                if (state.playbackMode == PlaybackMode.VIDEO) {
                    VideoSurface(
                        player = playbackService.getPlayer(),
                        fill = true,
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
}
