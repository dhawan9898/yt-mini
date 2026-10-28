package com.nexusstream.app.ui.components

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.nexusstream.app.R

/** Width and height, in pixels, of a video frame laid out inside a container. */
data class VideoFrame(val width: Float, val height: Float)

/**
 * The size of the video picture inside a [containerWidth] x [containerHeight] box. The picture is
 * always centered. With [fill] it covers the whole box (the overflow is cropped, no bars); without,
 * the whole picture is visible (bars appear on the sides or top / bottom as needed).
 */
fun fitVideoFrame(containerWidth: Float, containerHeight: Float, aspect: Float, fill: Boolean): VideoFrame {
    if (containerWidth <= 0f || containerHeight <= 0f || aspect <= 0f) return VideoFrame(containerWidth, containerHeight)
    val containerAspect = containerWidth / containerHeight
    val widthLimited = if (fill) aspect < containerAspect else aspect > containerAspect
    return if (widthLimited) VideoFrame(containerWidth, containerWidth / aspect)
    else VideoFrame(containerHeight * aspect, containerHeight)
}

/** Display aspect ratio of a decoded video (0 while the size is still unknown). */
fun videoAspect(size: VideoSize): Float {
    if (size.width <= 0 || size.height <= 0) return 0f
    val rotated = size.unappliedRotationDegrees == 90 || size.unappliedRotationDegrees == 270
    val w = if (rotated) size.height.toFloat() else size.width * size.pixelWidthHeightRatio
    val h = if (rotated) size.width * size.pixelWidthHeightRatio else size.height.toFloat()
    return w / h
}

/**
 * One player can draw into only one surface. Several [VideoSurface]s can be on screen at once (the
 * watch page fading in while the home screen's floating player fades out, the fullscreen layout
 * over the page), so the most recently shown one owns the picture and hands it back when it leaves.
 */
private object SurfaceOwners {
    val stack = mutableStateListOf<Any>()
}

/**
 * The picture of [player], laid out by this composable and not by the platform: it is sized from the
 * video's real aspect ratio, centered in its box and clipped to it. [fill] crops to cover the box;
 * otherwise the whole picture is shown. Until the first frame's size is known it assumes 16:9.
 * A TextureView surface is used, so Compose controls drawn above always stay above the picture.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(
    player: Player,
    fill: Boolean,
    modifier: Modifier = Modifier
) {
    var size by remember(player) { mutableStateOf(player.videoSize) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                size = videoSize
            }
        }
        player.addListener(listener)
        size = player.videoSize
        onDispose { player.removeListener(listener) }
    }
    val token = remember { Any() }
    DisposableEffect(token) {
        SurfaceOwners.stack.add(token)
        onDispose { SurfaceOwners.stack.remove(token) }
    }
    val owner by remember { derivedStateOf { SurfaceOwners.stack.lastOrNull() === token } }

    BoxWithConstraints(modifier = modifier.clipToBounds().background(Color.Black)) {
        val density = LocalDensity.current
        val containerWidth = with(density) { maxWidth.toPx() }
        val containerHeight = with(density) { maxHeight.toPx() }
        val aspect = videoAspect(size).takeIf { it > 0f } ?: (16f / 9f)
        val frame = fitVideoFrame(containerWidth, containerHeight, aspect, fill)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .requiredSize(
                    with(density) { frame.width.toDp() },
                    with(density) { frame.height.toDp() }
                )
        ) {
            AndroidView(
                factory = { ctx ->
                    (LayoutInflater.from(ctx).inflate(R.layout.view_player_texture, null, false) as PlayerView).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        // The box is already the exact shape of the picture, so the view just fills it.
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                },
                update = { view ->
                    // Re-attaching forces the player to render into this view's texture again.
                    if (owner) {
                        if (view.player !== player) view.player = player
                    } else if (view.player != null) {
                        view.player = null
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
