package com.nexusstream.app.ui.youtube

import android.content.Context
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.nexusstream.app.data.WatchPositions
import com.nexusstream.app.data.YouTubeRepository
import com.nexusstream.app.media.AdBlockMediaPipeline
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.ui.components.OriginalColors
import com.nexusstream.app.ui.components.fitVideoFrame
import com.nexusstream.app.ui.components.videoAspect
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Muted inline playback in the Home feed, like the YouTube app: the card that comes to rest in
 * view plays silently in place of its thumbnail, with a scrubber you can drag. One small player
 * for the whole app, separate from the main one, playing a low-resolution video-only stream from
 * the shared playback cache (so the bytes are reused when the video is then opened).
 */
@OptIn(UnstableApi::class)
object InlinePreview {
    private const val PREVIEW_MAX_HEIGHT = 360

    private var exo: ExoPlayer? = null
    private var pipeline: AdBlockMediaPipeline? = null

    private val _playingId = MutableStateFlow<String?>(null)
    /** The video whose first frame is on screen (null while loading or stopped). */
    val playingId: StateFlow<String?> = _playingId.asStateFlow()

    private val _muted = MutableStateFlow(true)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private var loadingId: String? = null

    fun player(context: Context): ExoPlayer = exo ?: ExoPlayer.Builder(context.applicationContext)
        .build()
        .apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_OFF
            setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
            addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    _playingId.value = loadingId
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) stop()
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    stop()
                }
            })
        }.also { exo = it }

    /** Starts the silent preview of [item] (stops any other). Resolving happens in the caller's scope. */
    suspend fun play(context: Context, item: MediaItemModel) {
        if (loadingId == item.id) return
        stop()
        if (item.sourceUrl.isBlank() || item.isLive) return
        loadingId = item.id
        // Resolved through the prefetch slot: opening the video afterwards starts at once.
        YouTubeRepository.prefetchStream(item.sourceUrl)
        val resolved = YouTubeRepository.prefetchedStream(item.sourceUrl) ?: return
        if (loadingId != item.id) return
        // The combined stream (usually 360p) carries sound for "unmute"; else a small video-only one.
        val url = resolved.muxedUrl ?: resolved.pickVideo(PREVIEW_MAX_HEIGHT)?.url ?: return
        val factory = (pipeline ?: AdBlockMediaPipeline(context.applicationContext).also { pipeline = it })
            .createStreamingDataSourceFactory()
        val source = ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(url))
        val player = player(context)
        player.volume = if (_muted.value) 0f else 1f
        player.setMediaSource(source, WatchPositions.resumeAt(item.id))
        player.prepare()
        player.playWhenReady = true
    }

    /** Stops the preview (scrolling away, opening a video, leaving the screen). */
    fun stop() {
        loadingId = null
        _playingId.value = null
        exo?.let {
            it.stop()
            it.clearMediaItems()
        }
    }

    /** Frees the decoder when the feed goes away for good. */
    fun release() {
        stop()
        exo?.release()
        exo = null
    }

    fun toggleMute() {
        _muted.value = !_muted.value
        exo?.volume = if (_muted.value) 0f else 1f
    }

    /**
     * Called as [item] is opened from its card: if the preview was watched (or scrubbed) far enough,
     * the video opens where the preview was, as on YouTube. Then the preview stops.
     */
    fun handOff(item: MediaItemModel) {
        val player = exo
        if (player != null && _playingId.value == item.id) {
            val duration = player.duration.takeIf { it > 0 } ?: item.durationMs
            WatchPositions.save(item.id, player.currentPosition, duration)
        }
        stop()
    }

    internal fun positionMs(): Long = exo?.currentPosition ?: 0L
    internal fun durationMs(): Long = exo?.duration?.takeIf { it > 0 } ?: 0L
    internal fun seekTo(ms: Long) { exo?.seekTo(ms) }
}

/**
 * The moving picture over a feed thumbnail while [item] previews: fades in on its first frame,
 * with a thin draggable progress bar, the time left and a mute button. Draws nothing otherwise.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun InlinePreviewLayer(item: MediaItemModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember { InlinePreview.player(context) }
    val playingId by InlinePreview.playingId.collectAsState()
    val muted by InlinePreview.muted.collectAsState()
    val visible = playingId == item.id
    val alpha by animateFloatAsState(if (visible) 1f else 0f, label = "previewFade")

    var videoSize by remember { mutableStateOf(player.videoSize) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(newSize: VideoSize) { videoSize = newSize }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(item.durationMs) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(visible) {
        while (visible) {
            if (dragFraction == null) position = InlinePreview.positionMs()
            InlinePreview.durationMs().takeIf { it > 0 }?.let { duration = it }
            delay(250)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }.clipToBounds()) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val frame = fitVideoFrame(w, h, videoAspect(videoSize).takeIf { it > 0f } ?: (16f / 9f), fill = true)
        AndroidView(
            factory = { ctx -> TextureView(ctx) },
            update = { view -> player.setVideoTextureView(view) },
            onRelease = { view -> player.clearVideoTextureView(view) },
            modifier = Modifier
                .align(Alignment.Center)
                .requiredSize(with(density) { frame.width.toDp() }, with(density) { frame.height.toDp() })
        )

        if (!visible) return@BoxWithConstraints
        // Mute / unmute, top right.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .pointerInput(Unit) { detectTapGestures { InlinePreview.toggleMute() } },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = if (muted) "Unmute" else "Mute",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }

        val trackWidth = maxWidth
        val fraction = dragFraction ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
        // Time left, bottom right (where the duration badge was).
        if (duration > 0) {
            val shownPosition = dragFraction?.let { (it * duration).toLong() } ?: position
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 14.dp)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = formatVideoDuration((duration - shownPosition).coerceAtLeast(0L)),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }

        // Scrubber: a thin red bar along the bottom; drag anywhere along it to seek.
        val dragging = dragFraction != null
        val barHeight by animateFloatAsState(if (dragging) 5f else 3f, label = "scrubHeight")
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(duration) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> dragFraction = (offset.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            dragFraction?.let { f ->
                                val target = (f * duration).toLong()
                                InlinePreview.seekTo(target)
                                position = target
                            }
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        }
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(barHeight.dp)
                    .background(Color.White.copy(alpha = 0.35f))
            ) {
                Box(modifier = Modifier.fillMaxWidth(fraction).fillMaxHeight().background(OriginalColors.Red))
            }
            if (dragging) {
                val knob = 12.dp
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = trackWidth * fraction - knob / 2, y = -(knob - barHeight.dp) / 2)
                        .size(knob)
                        .clip(CircleShape)
                        .background(OriginalColors.Red)
                )
            }
        }
    }
}
