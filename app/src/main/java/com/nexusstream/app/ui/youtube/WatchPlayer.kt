package com.nexusstream.app.ui.youtube

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.media.AudioManager
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.runtime.rememberUpdatedState
import android.view.LayoutInflater
import com.nexusstream.app.R
import kotlin.math.abs
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.nexusstream.app.data.AppSettings
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.nexusstream.app.data.LibraryRepository
import com.nexusstream.app.data.Storyboard
import com.nexusstream.app.data.watchUrl
import com.nexusstream.app.media.MediaPlaybackService
import com.nexusstream.app.media.PlaybackState
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.ui.components.OriginalColors
import kotlinx.coroutines.delay
import java.util.Locale

private const val SEEK_STEP_MS = 10_000L

/**
 * The video with YouTube-style controls drawn on top: title and channel (fullscreen), autoplay
 * switch, cast / captions / settings (top), previous - play - next (centre), time, seek bar and a
 * fullscreen button (bottom). Gestures: tap shows / hides the controls, double-tap the left or
 * right third seeks 10 s, hold plays at 2x, swipe up / down enters / leaves fullscreen or minimizes
 * (and in fullscreen, the left half of the screen is brightness and the right half is volume).
 */
@OptIn(UnstableApi::class)
@Composable
fun WatchPlayerViewport(
    playbackService: MediaPlaybackService,
    state: PlaybackState,
    item: MediaItemModel,
    fullscreen: Boolean,
    controlsEnabled: Boolean,
    onCollapse: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    storyboard: Storyboard? = null,
    /** Extra buttons in the top row (cast, picture-in-picture, close...). */
    topActions: @Composable RowScope.() -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val ambientMode by AppSettings.ambientMode.collectAsState()
    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var hud by remember { mutableStateOf<String?>(null) }
    var boosting by remember { mutableStateOf(false) }
    var speedBeforeBoost by remember { mutableFloatStateOf(1f) }
    // Pinch out fills the screen (crops the edges); pinch in fits the whole picture again.
    var fillScreen by remember { mutableStateOf(false) }
    val currentSwipeUp by rememberUpdatedState(onSwipeUp)
    val currentSwipeDown by rememberUpdatedState(onSwipeDown)

    val glowColor = remember(item.dominantColor) {
        if (item.dominantColor == Color.Unspecified || item.dominantColor == Color.Transparent || item.dominantColor == Color.Black) {
            Color(0xFF383838)
        } else {
            item.dominantColor
        }
    }

    // Controls fade out a few seconds after the last touch while playing.
    LaunchedEffect(controlsVisible, interaction, state.isPlaying) {
        if (controlsVisible && state.isPlaying) {
            delay(3500)
            controlsVisible = false
        }
    }
    LaunchedEffect(hud) {
        if (hud != null && !boosting) {
            delay(700)
            hud = null
        }
    }

    fun seekBy(deltaMs: Long) {
        val player = playbackService.getPlayer()
        val duration = player.duration
        val target = player.currentPosition + deltaMs
        player.seekTo(if (duration > 0) target.coerceIn(0L, duration) else target.coerceAtLeast(0L))
        hud = if (deltaMs > 0) "+10 seconds" else "-10 seconds"
    }

    fun adjustBrightness(fraction: Float) {
        val window = context.hostActivity()?.window ?: return
        val attributes = window.attributes
        val current = if (attributes.screenBrightness < 0f) 0.5f else attributes.screenBrightness
        val next = (current + fraction * 1.2f).coerceIn(0.02f, 1f)
        attributes.screenBrightness = next
        window.attributes = attributes
        hud = "Brightness ${(next * 100).toInt()}%"
    }

    var volumeRemainder by remember { mutableFloatStateOf(0f) }
    fun adjustVolume(fraction: Float) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        volumeRemainder += fraction * max * 1.5f
        val steps = volumeRemainder.toInt()
        if (steps != 0) {
            volumeRemainder -= steps
            val level = (audio.getStreamVolume(AudioManager.STREAM_MUSIC) + steps).coerceIn(0, max)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0)
        }
        hud = "Volume ${audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max}%"
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .then(
                if (ambientMode && !fullscreen) {
                    Modifier.drawBehind {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    glowColor.copy(alpha = 0.55f),
                                    glowColor.copy(alpha = 0.18f),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = size.width * 0.90f
                            )
                        )
                    }
                } else Modifier
            )
    ) {
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.view_player_texture, null, false) as PlayerView).apply {
                    layoutParams = android.widget.FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    player = playbackService.getPlayer()
                }
            },
            update = {
                it.player = playbackService.getPlayer()
                it.resizeMode = if (fillScreen) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
            },
            modifier = Modifier.fillMaxSize()
        )

        if (controlsEnabled) {
            // Gesture layer: sits above the video, below the visible controls.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(fullscreen) {
                        detectTapGestures(
                            onPress = {
                                tryAwaitRelease()
                                if (boosting) {
                                    playbackService.getPlayer().setPlaybackSpeed(speedBeforeBoost)
                                    boosting = false
                                    hud = null
                                }
                            },
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val player = playbackService.getPlayer()
                                speedBeforeBoost = player.playbackParameters.speed
                                boosting = true
                                player.setPlaybackSpeed(2f)
                                hud = "2x speed \u25B6\u25B6"
                            },
                            onDoubleTap = { offset ->
                                val third = size.width / 3f
                                when {
                                    offset.x < third -> seekBy(-SEEK_STEP_MS)
                                    offset.x > 2 * third -> seekBy(SEEK_STEP_MS)
                                    else -> playbackService.togglePlayPause()
                                }
                                interaction++
                            },
                            onTap = {
                                controlsVisible = !controlsVisible
                                interaction++
                            }
                        )
                    }
                    .pointerInput(fullscreen) {
                        var startX = 0f
                        var total = 0f
                        val threshold = 60.dp.toPx()
                        detectVerticalSwipeAndPinch(
                            onDragStart = { offset ->
                                startX = offset.x
                                total = 0f
                            },
                            onDrag = { dy ->
                                total += dy
                                if (fullscreen) {
                                    val fraction = -dy / size.height
                                    if (startX < size.width / 2f) adjustBrightness(fraction) else adjustVolume(fraction)
                                }
                            },
                            onDragEnd = {
                                if (!fullscreen) {
                                    if (total < -threshold) currentSwipeUp() else if (total > threshold) currentSwipeDown()
                                }
                            },
                            onPinch = { zoom ->
                                if (zoom > 1.12f && !fillScreen) {
                                    fillScreen = true
                                    hud = "Zoom to fill"
                                } else if (zoom < 0.88f && fillScreen) {
                                    fillScreen = false
                                    hud = "Original"
                                }
                            }
                        )
                    }
            )
        }

        if (state.isResolving || (state.isBuffering && !controlsVisible)) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
            )
        }

        if (controlsEnabled) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                WatchControls(
                    playbackService = playbackService,
                    state = state,
                    title = item.title,
                    channel = item.artistOrChannel,
                    fullscreen = fullscreen,
                    onTouched = { interaction++ },
                    onCollapse = onCollapse,
                    onToggleFullscreen = onToggleFullscreen,
                    onOpenSettings = onOpenSettings,
                    storyboard = storyboard,
                    topActions = topActions
                )
            }
        }

        hud?.let { text ->
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (fullscreen) 56.dp else 40.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun WatchControls(
    playbackService: MediaPlaybackService,
    state: PlaybackState,
    title: String,
    channel: String,
    fullscreen: Boolean,
    onTouched: () -> Unit,
    onCollapse: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onOpenSettings: () -> Unit,
    storyboard: Storyboard?,
    topActions: @Composable RowScope.() -> Unit
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1L)
    val fraction = dragFraction ?: (state.currentPositionMs.toFloat() / duration).coerceIn(0f, 1f)
    val shownMs = dragFraction?.let { (it * duration).toLong() } ?: state.currentPositionMs
    val captionsOn = state.selectedSubtitleTag != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.38f))
    ) {
        // ---- top row ----
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onTouched(); onCollapse() }) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Minimize", tint = Color.White, modifier = Modifier.size(30.dp))
            }
            if (fullscreen) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(text = channel, fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            AutoplayPill(on = state.autoplayNext) {
                onTouched()
                playbackService.setAutoplayNext(!state.autoplayNext)
            }
            topActions()
            if (state.availableSubtitles.isNotEmpty()) {
                CaptionsButton(on = captionsOn) {
                    onTouched()
                    if (captionsOn) {
                        playbackService.setSubtitle(null)
                    } else {
                        val language = Locale.getDefault().language
                        val pick = state.availableSubtitles.firstOrNull { it.languageTag.startsWith(language) }
                            ?: state.availableSubtitles.first()
                        playbackService.setSubtitle(pick.languageTag)
                    }
                }
            }
            IconButton(onClick = { onTouched(); onOpenSettings() }) {
                Icon(Icons.Outlined.Settings, "Settings", tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }

        // ---- centre: previous, play / pause, next ----
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(if (fullscreen) 64.dp else 36.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassCircle(size = 48.dp, onClick = { onTouched(); playbackService.skipToPrevious() }) {
                Icon(Icons.Rounded.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(28.dp))
            }
            GlassCircle(size = 68.dp, onClick = { onTouched(); playbackService.togglePlayPause() }) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }
            GlassCircle(size = 48.dp, onClick = { onTouched(); playbackService.skipToNext() }) {
                Icon(Icons.Rounded.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }

        // ---- bottom: time, fullscreen, seek bar ----
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${formatVideoDuration(shownMs)} / ${formatVideoDuration(state.durationMs)}",
                    fontSize = 12.sp,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { onTouched(); onToggleFullscreen() }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                        contentDescription = if (fullscreen) "Exit fullscreen" else "Fullscreen",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            ScrubPreview(
                visible = dragFraction != null,
                fraction = fraction,
                timeMs = shownMs,
                storyboard = storyboard
            )
            YouTubeSeekBar(
                fraction = fraction,
                bufferedFraction = (state.bufferedPositionMs.toFloat() / duration).coerceIn(0f, 1f),
                onScrub = {
                    onTouched()
                    dragFraction = it
                },
                onScrubEnd = {
                    dragFraction?.let { playbackService.seekTo((it * duration).toLong()) }
                    dragFraction = null
                },
                modifier = Modifier.fillMaxWidth().height(28.dp)
            )
        }
    }
}

@Composable
private fun AutoplayPill(on: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .width(52.dp)
            .height(28.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.28f))
            .clickable(onClickLabel = "Autoplay", onClick = onClick)
            .padding(3.dp)
    ) {
        Box(
            modifier = Modifier
                .align(if (on) Alignment.CenterEnd else Alignment.CenterStart)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (on) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                contentDescription = if (on) "Autoplay on" else "Autoplay off",
                tint = Color.Black,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun CaptionsButton(on: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .size(width = 34.dp, height = 24.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(if (on) Color.White else Color.Transparent)
            .border(1.5.dp, Color.White, RoundedCornerShape(5.dp))
            .clickable(onClickLabel = "Captions", onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("CC", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (on) Color.Black else Color.White)
    }
}

/**
 * Fullscreen: the video fills most of the screen, with a row of actions underneath (like, dislike,
 * comments, save, share, more) and a "More videos" shortcut that goes back to the page.
 */
@OptIn(UnstableApi::class)
@Composable
fun FullscreenWatchLayout(
    playbackService: MediaPlaybackService,
    state: PlaybackState,
    item: MediaItemModel,
    nextVideo: MediaItemModel?,
    onExit: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenComments: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    storyboard: Storyboard? = null
) {
    val context = LocalContext.current
    val liked by LibraryRepository.liked.collectAsState()
    val disliked by LibraryRepository.disliked.collectAsState()
    val isLiked = liked.any { it.id == item.id }
    val isDisliked = item.id in disliked

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .displayCutoutPadding()
    ) {
        WatchPlayerViewport(
            playbackService = playbackService,
            state = state,
            item = item,
            fullscreen = true,
            controlsEnabled = true,
            onCollapse = onExit,
            onToggleFullscreen = onExit,
            onSwipeUp = {},
            onSwipeDown = {},
            onOpenSettings = onOpenSettings,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            storyboard = storyboard
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { LibraryRepository.toggleLike(item) }) {
                Icon(if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, "Like", tint = Color.White)
            }
            IconButton(onClick = { LibraryRepository.toggleDislike(item) }) {
                Icon(if (isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown, "Dislike", tint = Color.White)
            }
            IconButton(onClick = onOpenComments) {
                Icon(Icons.Outlined.Comment, "Comments", tint = Color.White)
            }
            IconButton(onClick = onSave) {
                Icon(Icons.Outlined.BookmarkBorder, "Save", tint = Color.White)
            }
            IconButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, item.watchUrl)
                }
                context.startActivity(Intent.createChooser(send, "Share"))
            }) {
                Icon(Icons.Outlined.Share, "Share", tint = Color.White)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.MoreHoriz, "More", tint = Color.White)
            }
            Spacer(modifier = Modifier.weight(1f))
            if (nextVideo != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onExit)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("More videos", fontSize = 14.sp, color = Color.White)
                    Spacer(modifier = Modifier.width(10.dp))
                    AsyncImage(
                        model = nextVideo.artworkUrl,
                        contentDescription = nextVideo.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 64.dp, height = 40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    )
                }
            }
        }
    }
}

internal tailrec fun Context.hostActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is ContextWrapper -> baseContext.hostActivity()
    else -> null
}

/** Frosted-glass round button: translucent fill, bright rim, and a springy squash while pressed. */
@Composable
private fun GlassCircle(size: androidx.compose.ui.unit.Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 380f),
        label = "glassPress"
    )
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.08f))
                )
            )
            .border(
                1.dp,
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.3f))
                ),
                CircleShape
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** YouTube's seek bar: thin track, grey buffered part, red progress and a dot that grows on touch. */
@Composable
private fun YouTubeSeekBar(
    fraction: Float,
    bufferedFraction: Float,
    onScrub: (Float) -> Unit,
    onScrubEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scrubbing by remember { mutableStateOf(false) }
    val thumb by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (scrubbing) 16.dp else 12.dp,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "thumb"
    )
    androidx.compose.foundation.Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset -> onScrub((offset.x / size.width).coerceIn(0f, 1f)); onScrubEnd() }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset -> scrubbing = true; onScrub((offset.x / size.width).coerceIn(0f, 1f)) },
                    onDragEnd = { scrubbing = false; onScrubEnd() },
                    onDragCancel = { scrubbing = false; onScrubEnd() },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        onScrub((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                )
            }
    ) {
        val cy = size.height / 2f
        val trackHeight = (if (scrubbing) 4.dp else 3.dp).toPx()
        val radius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f)
        val top = androidx.compose.ui.geometry.Offset(0f, cy - trackHeight / 2f)
        drawRoundRect(Color.White.copy(alpha = 0.3f), top, androidx.compose.ui.geometry.Size(size.width, trackHeight), radius)
        drawRoundRect(Color.White.copy(alpha = 0.5f), top, androidx.compose.ui.geometry.Size(size.width * bufferedFraction, trackHeight), radius)
        drawRoundRect(OriginalColors.Red, top, androidx.compose.ui.geometry.Size(size.width * fraction, trackHeight), radius)
        drawCircle(OriginalColors.Red, thumb.toPx() / 2f, androidx.compose.ui.geometry.Offset(size.width * fraction, cy))
    }
}

/** Time bubble (with a storyboard frame when YouTube provides one) that follows the scrub thumb. */
@Composable
private fun ScrubPreview(visible: Boolean, fraction: Float, timeMs: Long, storyboard: Storyboard?) {
    val context = LocalContext.current
    val sheets = remember(storyboard) { mutableMapOf<Int, androidx.compose.ui.graphics.ImageBitmap>() }
    var loaded by remember(storyboard) { mutableIntStateOf(0) }
    val bounds = storyboard?.boundsAt(timeMs)
    val page = bounds?.get(0)
    LaunchedEffect(storyboard, page) {
        if (storyboard != null && page != null && page !in sheets) {
            val request = coil.request.ImageRequest.Builder(context)
                .data(storyboard.urls[page])
                .allowHardware(false)
                .build()
            val drawable = coil.Coil.imageLoader(context).execute(request).drawable
            (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.let {
                sheets[page] = it.asImageBitmap()
                loaded++
            }
        }
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.85f),
        exit = fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.85f)
    ) {
        androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val previewWidth = if (bounds != null) 150.dp else 64.dp
            val x = (maxWidth * fraction - previewWidth / 2).coerceIn(0.dp, (maxWidth - previewWidth).coerceAtLeast(0.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(start = x, bottom = 4.dp)
                    .width(previewWidth)
            ) {
                val sheet = page?.let { sheets[it] }
                if (bounds != null && sheet != null) {
                    @Suppress("UNUSED_EXPRESSION") loaded
                    val w = bounds[3] - bounds[1]
                    val h = bounds[4] - bounds[2]
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(w.toFloat() / h.coerceAtLeast(1))
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.5.dp, Color.White, RoundedCornerShape(8.dp))
                    ) {
                        drawImage(
                            image = sheet,
                            srcOffset = androidx.compose.ui.unit.IntOffset(bounds[1], bounds[2]),
                            srcSize = androidx.compose.ui.unit.IntSize(w, h),
                            dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
                        )
                    }
                }
                Text(
                    text = formatVideoDuration(timeMs),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/**
 * One detector for both single-finger vertical swipes and two-finger pinches, so they never fight
 * over the same touches. A swipe is claimed only once the finger has moved mostly vertically.
 */
private suspend fun PointerInputScope.detectVerticalSwipeAndPinch(
    onDragStart: (Offset) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onPinch: (Float) -> Unit
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    val slop = viewConfiguration.touchSlop
    var pinching = false
    var dragging = false
    var startDistance = 0f
    var zoom = 1f
    var totalX = 0f
    var totalY = 0f
    do {
        val event = awaitPointerEvent()
        val pressed = event.changes.filter { it.pressed }
        if (pressed.size >= 2) {
            val distance = (pressed[0].position - pressed[1].position).getDistance()
            if (!pinching) {
                pinching = true
                startDistance = distance.coerceAtLeast(1f)
            }
            zoom = distance / startDistance
            event.changes.forEach { it.consume() }
        } else if (!pinching && pressed.size == 1) {
            val change = pressed[0]
            val delta = change.positionChange()
            totalX += delta.x
            totalY += delta.y
            if (!dragging && abs(totalY) > slop && abs(totalY) > abs(totalX)) {
                dragging = true
                onDragStart(down.position)
            }
            if (dragging) {
                onDrag(delta.y)
                change.consume()
            }
        }
    } while (event.changes.any { it.pressed })
    if (pinching) onPinch(zoom) else if (dragging) onDragEnd()
}
