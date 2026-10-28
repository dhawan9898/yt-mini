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
import androidx.compose.runtime.collectAsState
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
 * otherwise the whole picture is shown. Until the first frame's size is known it assumes
 * [placeholderAspect] (16:9 unless the caller knows better, e.g. 9:16 for Shorts).
 * A TextureView surface is used, so Compose controls drawn above always stay above the picture.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(
    player: Player,
    fill: Boolean,
    modifier: Modifier = Modifier,
    placeholderAspect: Float = 16f / 9f
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
    val captionScale by com.nexusstream.app.data.AppSettings.captionScale.collectAsState()
    val captionBackground by com.nexusstream.app.data.AppSettings.captionBackground.collectAsState()

    BoxWithConstraints(modifier = modifier.clipToBounds().background(Color.Black)) {
        val density = LocalDensity.current
        val containerWidth = with(density) { maxWidth.toPx() }
        val containerHeight = with(density) { maxHeight.toPx() }
        val aspect = videoAspect(size).takeIf { it > 0f } ?: placeholderAspect
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
                    view.subtitleView?.let { subs ->
                        subs.setFractionalTextSize(androidx.media3.ui.SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * captionScale)
                        subs.setApplyEmbeddedStyles(false)
                        subs.setStyle(captionStyle(captionBackground))
                    }
                    if (owner) FrameGrabber.view = view.videoSurfaceView as? android.view.TextureView
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

/** Caption look from the settings: white text on nothing (outlined), a translucent or an opaque box. */
@OptIn(UnstableApi::class)
private fun captionStyle(background: Int): androidx.media3.ui.CaptionStyleCompat {
    val box = when (background) {
        0 -> android.graphics.Color.TRANSPARENT
        2 -> android.graphics.Color.BLACK
        else -> android.graphics.Color.argb(0xBF, 0, 0, 0)
    }
    return androidx.media3.ui.CaptionStyleCompat(
        android.graphics.Color.WHITE,
        box,
        android.graphics.Color.TRANSPARENT,
        if (background == 0) androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE else androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE,
        android.graphics.Color.BLACK,
        null
    )
}

/**
 * The picture currently on screen, for ambient mode: it samples a tiny copy of the frame
 * (TextureView keeps the last frame, so this is cheap) to tint the glow around the player.
 */
object FrameGrabber {
    @Volatile
    var view: android.view.TextureView? = null

    /** The last sampled colour (ARGB), shared by the glow and the watch page background. */
    val ambient = kotlinx.coroutines.flow.MutableStateFlow<Int?>(null)

    /** The average colour of the current frame, or null when nothing is showing. */
    fun averageColor(): Int? {
        val v = view ?: return null
        if (!v.isAvailable) return null
        val bmp = runCatching { v.getBitmap(16, 9) }.getOrNull() ?: return null
        var r = 0L; var g = 0L; var b = 0L
        var n = 0
        for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
            val c = bmp.getPixel(x, y)
            r += android.graphics.Color.red(c); g += android.graphics.Color.green(c); b += android.graphics.Color.blue(c)
            n++
        }
        bmp.recycle()
        if (n == 0) return null
        return android.graphics.Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }
}
