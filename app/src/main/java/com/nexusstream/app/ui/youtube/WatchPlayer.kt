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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
    /** Extra buttons in the top row (cast, picture-in-picture, close...). */
    topActions: @Composable RowScope.() -> Unit = {}
) {
    val context = LocalContext.current
    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var hud by remember { mutableStateOf<String?>(null) }
    var boosting by remember { mutableStateOf(false) }
    var speedBeforeBoost by remember { mutableFloatStateOf(1f) }

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

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    player = playbackService.getPlayer()
                }
            },
            update = { it.player = playbackService.getPlayer() },
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
                                val player = playbackService.getPlayer()
                                speedBeforeBoost = player.playbackParameters.speed
                                boosting = true
                                player.setPlaybackSpeed(2f)
                                hud = "2x speed"
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
                        val threshold = 80.dp.toPx()
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                startX = offset.x
                                total = 0f
                            },
                            onDragEnd = {
                                if (!fullscreen) {
                                    if (total < -threshold) onSwipeUp() else if (total > threshold) onSwipeDown()
                                }
                            },
                            onVerticalDrag = { change, dy ->
                                change.consume()
                                total += dy
                                if (fullscreen) {
                                    val fraction = -dy / size.height
                                    if (startX < size.width / 2f) adjustBrightness(fraction) else adjustVolume(fraction)
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
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable { onTouched(); playbackService.skipToPrevious() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { onTouched(); playbackService.togglePlayPause() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable { onTouched(); playbackService.skipToNext() },
                contentAlignment = Alignment.Center
            ) {
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
            Slider(
                value = fraction,
                onValueChange = {
                    onTouched()
                    dragFraction = it
                },
                onValueChangeFinished = {
                    dragFraction?.let { playbackService.seekTo((it * duration).toLong()) }
                    dragFraction = null
                },
                colors = SliderDefaults.colors(
                    thumbColor = OriginalColors.Red,
                    activeTrackColor = OriginalColors.Red,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                ),
                modifier = Modifier.height(24.dp)
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
    modifier: Modifier = Modifier
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
            modifier = Modifier.weight(1f).fillMaxWidth()
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
