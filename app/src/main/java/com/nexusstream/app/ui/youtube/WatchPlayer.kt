package com.nexusstream.app.ui.youtube

import com.nexusstream.app.ui.components.rememberSmoothPosition
import com.nexusstream.app.ui.theme.frostedGlass
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import com.nexusstream.app.data.InnerTube
import com.nexusstream.app.data.WatchExtras
import com.nexusstream.app.ui.components.FrameGrabber
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
import com.nexusstream.app.ui.components.VideoSurface
import kotlin.math.abs
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.nexusstream.app.data.AppSettings
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.offset
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
import com.nexusstream.app.data.Chapter
import com.nexusstream.app.data.watchUrl
import com.nexusstream.app.media.MediaPlaybackService
import com.nexusstream.app.media.PlaybackState
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.ui.components.OriginalColors
import kotlinx.coroutines.delay
import java.util.Locale


/**
 * The video with YouTube-style controls drawn on top: title and channel (fullscreen), autoplay
 * switch, cast / captions / settings (top), previous - play - next (centre), time, seek bar and a
 * fullscreen button (bottom). Gestures: tap shows / hides the controls, double-tap the left or
 * right third seeks 10 s, hold plays at 2x, pinch zooms to fill. Swipe up enters fullscreen and
 * swipe down minimizes; in fullscreen, swipe down goes back to the normal view.
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
    topActions: @Composable RowScope.() -> Unit = {},
    /** Optional row under the seek bar (fullscreen actions). */
    bottomBar: (@Composable () -> Unit)? = null,
    /** "Most replayed" graph: drawn over the seek bar while scrubbing, and where Jump ahead skips to. */
    heatmap: List<WatchExtras.HeatPoint> = emptyList(),
    /** Chapters: gaps in the seek bar and the current chapter's title next to the time. */
    chapters: List<Chapter> = emptyList(),
    /** End screen tiles and info cards of this video. */
    extras: InnerTube.PlayerExtras? = null,
    onOpenEndScreen: (WatchExtras.EndScreenElement) -> Unit = {},
    onOpenCard: (WatchExtras.InfoCard) -> Unit = {},
    /**
     * Follows a vertical swipe live: the distance dragged so far (px, down is positive), then once
     * more with released = true when the finger lifts, so the caller can move the page with it.
     */
    onSwipeDrag: ((total: Float, released: Boolean) -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val ambientMode by AppSettings.ambientMode.collectAsState()
    val seekSeconds by AppSettings.doubleTapSeekSec.collectAsState()
    // Lock: the screen ignores touches (pocket, kids) until the unlock button is pressed.
    var locked by remember { mutableStateOf(false) }
    var showUnlock by remember { mutableStateOf(false) }
    var jumpAhead by remember { mutableStateOf<Long?>(null) }
    var cardsOpen by remember(item.id) { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var hud by remember { mutableStateOf<String?>(null) }
    var boosting by remember { mutableStateOf(false) }
    var speedBeforeBoost by remember { mutableFloatStateOf(1f) }
    // Pinch out fills the screen (crops the edges); pinch in fits the whole picture again.
    var fillScreen by remember { mutableStateOf(false) }
    val currentSwipeUp by rememberUpdatedState(onSwipeUp)
    val currentSwipeDown by rememberUpdatedState(onSwipeDown)
    val currentSwipeDrag by rememberUpdatedState(onSwipeDrag)

    // Ambient mode samples the picture itself every couple of seconds, like YouTube's glow.
    val sampled by FrameGrabber.ambient.collectAsState()
    LaunchedEffect(ambientMode, item.id, state.isPlaying, fullscreen) {
        FrameGrabber.ambient.value = null
        while (ambientMode && !fullscreen) {
            FrameGrabber.averageColor()?.let { FrameGrabber.ambient.value = it }
            delay(if (state.isPlaying) 2000 else 5000)
        }
    }
    val glowColor = remember(item.dominantColor, sampled) {
        sampled?.let { Color(it) } ?: if (item.dominantColor == Color.Unspecified || item.dominantColor == Color.Transparent || item.dominantColor == Color.Black) {
            Color(0xFF383838)
        } else {
            item.dominantColor
        }
    }
    LaunchedEffect(showUnlock) {
        if (showUnlock) {
            delay(3000)
            showUnlock = false
        }
    }
    LaunchedEffect(jumpAhead) {
        if (jumpAhead != null) {
            delay(4000)
            jumpAhead = null
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
        hud = if (deltaMs > 0) "+${deltaMs / 1000} seconds" else "${deltaMs / 1000} seconds"
        // After skipping forward, offer to jump to the next part most people rewatch.
        jumpAhead = if (deltaMs > 0) WatchExtras.jumpAheadTarget(heatmap, player.currentPosition) else null
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
        VideoSurface(
            player = playbackService.getPlayer(),
            fill = fillScreen,
            modifier = Modifier.fillMaxSize()
        )

        // End screen tiles over the last seconds of the video, placed where the creator put them.
        val endScreen = extras?.endScreen.orEmpty().filter { state.currentPositionMs in it.startMs..it.endMs }
        if (endScreen.isNotEmpty() && controlsEnabled && !locked) {
            EndScreenOverlay(endScreen, onOpenEndScreen)
        }

        if (controlsEnabled && locked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures(onTap = { showUnlock = true }) }
            ) {
                if (showUnlock) {
                    Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        PlayerCircleButton(size = 56.dp, onClick = { locked = false; showUnlock = false; controlsVisible = true }) {
                            Icon(Icons.Outlined.LockOpen, "Unlock", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Tap to unlock", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }

        if (controlsEnabled && !locked) {
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
                                    offset.x < third -> seekBy(-seekSeconds * 1000L)
                                    offset.x > 2 * third -> seekBy(seekSeconds * 1000L)
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
                        var total = 0f
                        val threshold = 60.dp.toPx()
                        detectVerticalSwipeAndPinch(
                            onDragStart = { total = 0f },
                            onDrag = { dy ->
                                total += dy
                                currentSwipeDrag?.invoke(total, false)
                            },
                            onDragEnd = {
                                currentSwipeDrag?.invoke(total, true)
                                if (total < -threshold) currentSwipeUp() else if (total > threshold) currentSwipeDown()
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

        // While the controls show, the spinner takes the play / pause button's place instead.
        if ((state.isResolving || state.isBuffering) && !(controlsEnabled && controlsVisible)) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
            )
        }

        if (controlsEnabled && !locked) {
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
                    heatmap = heatmap,
                    chapters = chapters,
                    onLock = { locked = true; controlsVisible = false; showUnlock = true },
                    onHud = { hud = it },
                    topActions = topActions,
                    bottomBar = bottomBar
                )
            }
            jumpAhead?.let { target ->
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = if (fullscreen) 96.dp else 40.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .clickable {
                            playbackService.seekTo(target)
                            hud = "Jumped ahead to ${formatVideoDuration(target)}"
                            jumpAhead = null
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.SkipNext, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Jump ahead", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
            // Info cards: the creator's teaser shows for a few seconds, then stays as an "i" button.
            val cards = extras?.cards.orEmpty()
            if (cards.isNotEmpty()) {
                val active = cards.lastOrNull { it.startMs <= state.currentPositionMs }
                val teasing = active != null && state.currentPositionMs - active.startMs < 6000
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = if (fullscreen) 56.dp else 40.dp, end = 8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    if (teasing || controlsVisible || cardsOpen) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .clickable { cardsOpen = !cardsOpen; interaction++ }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (teasing && !cardsOpen) {
                                Text(active!!.teaser.ifBlank { active.title }, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 6.dp).widthIn(max = 200.dp))
                            }
                            Icon(Icons.Outlined.Info, "Cards", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (cardsOpen) {
                        Column(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .widthIn(max = 260.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.8f))
                        ) {
                            cards.filter { it.startMs <= state.currentPositionMs }.ifEmpty { cards }.forEach { card ->
                                Row(
                                    modifier = Modifier
                                        .clickable { cardsOpen = false; onOpenCard(card) }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = card.imageUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(width = 64.dp, height = 36.dp).clip(RoundedCornerShape(4.dp))
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(card.title, color = Color.White, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
            // Thin progress line while the controls are hidden, like YouTube.
            if (!controlsVisible && state.durationMs > 0) {
                val progress = (state.currentPositionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color.White.copy(alpha = 0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(2.dp)
                            .background(OriginalColors.Red)
                    )
                }
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
    heatmap: List<WatchExtras.HeatPoint>,
    chapters: List<Chapter>,
    onLock: () -> Unit,
    onHud: (String?) -> Unit,
    topActions: @Composable RowScope.() -> Unit,
    bottomBar: (@Composable () -> Unit)?
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1L)
    val fraction = dragFraction ?: (state.currentPositionMs.toFloat() / duration).coerceIn(0f, 1f)
    val shownMs = dragFraction?.let { (it * duration).toLong() } ?: state.currentPositionMs
    val captionsOn = state.selectedSubtitleTag != null

    // The dark scrim runs edge to edge; the buttons are inset so the camera cutout and the
    // navigation bar never cover them in fullscreen.
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (fullscreen) Modifier.displayCutoutPadding().navigationBarsPadding() else Modifier)
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
            if (fullscreen) {
                IconButton(onClick = { onTouched(); onLock() }) {
                    Icon(Icons.Outlined.Lock, "Lock screen", tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
            if (state.availableSubtitles.isNotEmpty()) {
                CaptionsButton(on = captionsOn) {
                    onTouched()
                    if (captionsOn) {
                        playbackService.setSubtitle(null)
                    } else {
                        val language = Locale.getDefault().language
                        val pick = state.availableSubtitles.firstOrNull { it.languageTag.startsWith(language) }
                            ?: state.availableSubtitles.firstOrNull()
                        pick?.let { playbackService.setSubtitle(it.languageTag) }
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
            horizontalArrangement = Arrangement.spacedBy(if (fullscreen) 72.dp else 40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerCircleButton(size = 48.dp, onClick = { onTouched(); playbackService.skipToPrevious() }) {
                Icon(Icons.Filled.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(32.dp))
            }
            if (state.isResolving || state.isBuffering) {
                // Loading: YouTube swaps the play / pause button for a spinner.
                Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(48.dp)
                    )
                }
            } else {
                PlayerCircleButton(size = 64.dp, onClick = { onTouched(); playbackService.togglePlayPause() }) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
            PlayerCircleButton(size = 48.dp, onClick = { onTouched(); playbackService.skipToNext() }) {
                Icon(Icons.Filled.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }

        // ---- bottom: time, fullscreen, seek bar ----
        // In the normal view the seek bar runs edge to edge along the bottom of the video, like YouTube.
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = if (fullscreen) 16.dp else 0.dp)
        ) {
            Row(
                modifier = Modifier.padding(start = if (fullscreen) 0.dp else 12.dp, end = if (fullscreen) 0.dp else 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buildAnnotatedString {
                        append(formatVideoDuration(shownMs))
                        withStyle(SpanStyle(color = Color.White.copy(alpha = 0.7f))) {
                            append(" / ${formatVideoDuration(state.durationMs)}")
                        }
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                // The chapter under the playhead (or under the finger while scrubbing), like YouTube.
                chapters.lastOrNull { it.startMs <= shownMs }?.let { chapter ->
                    Text(
                        text = "  •  ${chapter.title}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
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
            if (dragFraction != null && heatmap.isNotEmpty()) {
                HeatmapGraph(heatmap, state.durationMs, Modifier.fillMaxWidth().height(28.dp))
            }
            // Glides between the player's position updates; the scrubbed spot while dragging.
            val smoothPosition = rememberSmoothPosition(state.currentPositionMs, state.isPlaying && !state.isBuffering && dragFraction == null)
            YouTubeSeekBar(
                fraction = { dragFraction ?: (smoothPosition.value.toFloat() / duration).coerceIn(0f, 1f) },
                chapterStarts = remember(chapters, duration) {
                    chapters.map { it.startMs.toFloat() / duration }.filter { it > 0f && it < 1f }
                },
                onPrecise = { precise -> onHud(if (precise) "Precise seeking" else null) },
                bufferedFraction = (state.bufferedPositionMs.toFloat() / duration).coerceIn(0f, 1f),
                onScrub = {
                    onTouched()
                    dragFraction = it
                },
                onScrubEnd = {
                    dragFraction?.let { playbackService.seekTo((it * duration).toLong()) }
                    dragFraction = null
                },
                modifier = Modifier.fillMaxWidth().height(if (fullscreen) 28.dp else 18.dp)
            )
            bottomBar?.invoke()
        }
    }
    }
}

@Composable
private fun AutoplayPill(on: Boolean, onClick: () -> Unit) {
    val knobX by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (on) 24.dp else 0.dp,
        animationSpec = com.nexusstream.app.ui.theme.GlassMotion.bouncy(),
        label = "autoplayKnob"
    )
    Box(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .width(52.dp)
            .height(28.dp)
            .frostedGlass(CircleShape)
            .clickable(onClickLabel = "Autoplay", onClick = onClick)
            .padding(3.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { androidx.compose.ui.unit.IntOffset(knobX.roundToPx(), 0) }
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
    storyboard: Storyboard? = null,
    heatmap: List<WatchExtras.HeatPoint> = emptyList(),
    chapters: List<Chapter> = emptyList(),
    extras: InnerTube.PlayerExtras? = null,
    onOpenEndScreen: (WatchExtras.EndScreenElement) -> Unit = {},
    onOpenCard: (WatchExtras.InfoCard) -> Unit = {},
    onSwipeDrag: ((total: Float, released: Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val liked by LibraryRepository.liked.collectAsState()
    val disliked by LibraryRepository.disliked.collectAsState()
    val isLiked = liked.any { it.id == item.id }
    val isDisliked = item.id in disliked

    // The picture fills the whole screen (edge to edge, under the camera cutout); everything else
    // is a control drawn over it, so nothing takes space away from the video.
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        WatchPlayerViewport(
            playbackService = playbackService,
            state = state,
            item = item,
            fullscreen = true,
            controlsEnabled = true,
            onCollapse = onExit,
            onToggleFullscreen = onExit,
            onSwipeUp = {},
            onSwipeDown = onExit,
            onOpenSettings = onOpenSettings,
            modifier = Modifier.fillMaxSize(),
            storyboard = storyboard,
            heatmap = heatmap,
            chapters = chapters,
            extras = extras,
            onOpenEndScreen = onOpenEndScreen,
            onOpenCard = onOpenCard,
            onSwipeDrag = onSwipeDrag,
            bottomBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
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
        )
    }
}

internal tailrec fun Context.hostActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is ContextWrapper -> baseContext.hostActivity()
    else -> null
}

/** YouTube's round player button: a frosted glass disc behind a white icon, slightly shrinking while pressed. */
@Composable
private fun PlayerCircleButton(size: androidx.compose.ui.unit.Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "playerButtonPress"
    )
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .frostedGlass(CircleShape, pressed)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** YouTube's seek bar: thin track, grey buffered part, red progress and a dot that grows on touch. */
@Composable
private fun YouTubeSeekBar(
    fraction: () -> Float,
    bufferedFraction: Float,
    onScrub: (Float) -> Unit,
    onScrubEnd: () -> Unit,
    modifier: Modifier = Modifier,
    /** Where chapters start (0..1): the bar is split into segments with small gaps there. */
    chapterStarts: List<Float> = emptyList(),
    /** Precise seeking: sliding the finger up off the bar makes it move ten times finer. */
    onPrecise: (Boolean) -> Unit = {}
) {
    var scrubbing by remember { mutableStateOf(false) }
    var precise by remember { mutableStateOf(false) }
    var current by remember { mutableFloatStateOf(0f) }
    val thumb by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (scrubbing) 16.dp else 12.dp,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "thumb"
    )
    // The track swells under the finger with a spring.
    val track by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (scrubbing) 6.dp else 3.dp,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 520f),
        label = "track"
    )
    androidx.compose.foundation.Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset -> onScrub((offset.x / size.width).coerceIn(0f, 1f)); onScrubEnd() }
            }
            .pointerInput(Unit) {
                val preciseLift = 48.dp.toPx()
                fun end() {
                    scrubbing = false
                    if (precise) { precise = false; onPrecise(false) }
                    onScrubEnd()
                }
                detectDragGestures(
                    onDragStart = { offset ->
                        scrubbing = true
                        current = (offset.x / size.width).coerceIn(0f, 1f)
                        onScrub(current)
                    },
                    onDragEnd = { end() },
                    onDragCancel = { end() },
                    onDrag = { change, drag ->
                        change.consume()
                        val nowPrecise = change.position.y < -preciseLift
                        if (nowPrecise != precise) { precise = nowPrecise; onPrecise(nowPrecise) }
                        current = if (precise) (current + drag.x / size.width / 10f).coerceIn(0f, 1f)
                        else (change.position.x / size.width).coerceIn(0f, 1f)
                        onScrub(current)
                    }
                )
            }
    ) {
        val cy = size.height / 2f
        val trackHeight = track.toPx()
        val played = fraction()
        val radius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f)
        val top = androidx.compose.ui.geometry.Offset(0f, cy - trackHeight / 2f)
        // Each layer (track, buffered, played) is drawn segment by segment, leaving a gap at every chapter start.
        val gap = if (chapterStarts.isEmpty()) 0f else 2.dp.toPx()
        val edges = listOf(0f) + chapterStarts + 1f
        fun layer(color: Color, upTo: Float) {
            for (i in 0 until edges.size - 1) {
                val start = edges[i] * size.width + if (i > 0) gap / 2f else 0f
                val segmentEnd = edges[i + 1] * size.width - if (i < edges.size - 2) gap / 2f else 0f
                val end = minOf(segmentEnd, upTo * size.width)
                if (end <= start) break
                drawRoundRect(
                    color,
                    androidx.compose.ui.geometry.Offset(start, top.y),
                    androidx.compose.ui.geometry.Size(end - start, trackHeight),
                    radius
                )
            }
        }
        layer(Color.White.copy(alpha = 0.3f), 1f)
        layer(Color.White.copy(alpha = 0.5f), bufferedFraction)
        layer(OriginalColors.Red, played)
        drawCircle(OriginalColors.Red, thumb.toPx() / 2f, androidx.compose.ui.geometry.Offset(size.width * played, cy))
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

/** YouTube's "Most replayed" curve: taller where more people watch again. */
@Composable
private fun HeatmapGraph(heat: List<WatchExtras.HeatPoint>, durationMs: Long, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val lastPoint = heat.lastOrNull() ?: return@Canvas
        val total = durationMs.takeIf { it > 0 } ?: (lastPoint.startMs + lastPoint.durationMs)
        if (total <= 0) return@Canvas
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, size.height)
            heat.forEach { p ->
                val x = (p.startMs + p.durationMs / 2f) / total * size.width
                lineTo(x, size.height * (1f - p.intensity))
            }
            lineTo(size.width, size.height)
            close()
        }
        drawPath(path, Color.White.copy(alpha = 0.35f))
    }
}

/** End screen: video / playlist / channel / link tiles laid over the frame at the creator's positions. */
@Composable
private fun EndScreenOverlay(elements: List<WatchExtras.EndScreenElement>, onOpen: (WatchExtras.EndScreenElement) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // The tiles are placed in the 16:9 picture, centred in this box.
        val frameW = minOf(maxWidth, maxHeight * 16f / 9f)
        val frameH = frameW * 9f / 16f
        val offsetX = (maxWidth - frameW) / 2
        val offsetY = (maxHeight - frameH) / 2
        elements.forEach { e ->
            val w = frameW * e.width
            val h = if (e.aspectRatio > 0f) w / e.aspectRatio else w
            val round = e.style == "CHANNEL"
            Box(
                modifier = Modifier
                    .padding(start = offsetX + frameW * e.left, top = offsetY + frameH * e.top)
                    .size(w, h)
                    .clip(if (round) CircleShape else RoundedCornerShape(4.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.7f), if (round) CircleShape else RoundedCornerShape(4.dp))
                    .background(Color.Black)
                    .clickable { onOpen(e) }
            ) {
                AsyncImage(model = e.imageUrl, contentDescription = e.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (!round && e.title.isNotBlank()) {
                    Text(
                        e.title,
                        color = Color.White,
                        fontSize = 10.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
