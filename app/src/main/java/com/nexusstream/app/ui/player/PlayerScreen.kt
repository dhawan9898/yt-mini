package com.nexusstream.app.ui.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.nexusstream.app.media.MediaPlaybackService
import com.nexusstream.app.media.PlaybackState
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.model.PlaybackMode
import com.nexusstream.app.model.StreamRepeatMode
import com.nexusstream.app.ui.theme.DynamicMeshBackground
import com.nexusstream.app.ui.theme.LiquidPhysics
import com.nexusstream.app.ui.theme.LocalGlassColors
import com.nexusstream.app.ui.theme.liquidGlassSurface
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The Master Now-Playing Screen:
 * - Fluid capsule selector (Song vs Video)
 * - Rotating vinyl record with Coil artwork or full-screen video viewport
 * - Dynamic Vector morphing Play/Pause
 * - Liquid canvas sheet drag stretch distortion
 * - Integrated synchronized karaoke lyrics layer
 * - Up-Next queue modal glass sheet
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    playbackService: MediaPlaybackService,
    onCollapseSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playbackState by playbackService.playbackState.collectAsState()
    val mediaItem = playbackState.currentItem ?: return

    val glassColors = LocalGlassColors.current
    var showLyrics by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }

    // Liquid Drag Elasticity & Stretch distortion state
    val dragOffsetY = remember { Animatable(0f) }
    val liquidStretch = remember { Animatable(1f) }
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {
        DynamicMeshBackground(
            dominantColor = mediaItem.dominantColor,
            secondaryColor = mediaItem.secondaryColor,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val newOffset = (dragOffsetY.value + dragAmount.y).coerceAtLeast(0f)
                            coroutineScope.launch {
                                dragOffsetY.snapTo(newOffset)
                                val stretchRatio = 1f + (newOffset / 1800f).coerceIn(0f, 0.28f)
                                liquidStretch.snapTo(stretchRatio)
                            }
                        },
                        onDragEnd = {
                            coroutineScope.launch {
                                if (dragOffsetY.value > 250f) {
                                    onCollapseSheet()
                                }
                                dragOffsetY.animateTo(0f, LiquidPhysics.LowBouncyFluidSpring)
                                liquidStretch.animateTo(1f, LiquidPhysics.LowBouncyFluidSpring)
                            }
                        }
                    )
                }
                .offset { IntOffset(0, dragOffsetY.value.roundToInt()) }
                .graphicsLayer {
                    scaleY = liquidStretch.value
                    scaleX = 1f / (liquidStretch.value * 0.95f)
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // TOP BAR: Down chevron, Elastic Fluid Capsule Selector, Lyrics & Queue buttons
                TopControlBar(
                    currentMode = playbackState.playbackMode,
                    onModeSelected = { playbackService.togglePlaybackMode(it) },
                    onCollapse = onCollapseSheet,
                    onToggleLyrics = { showLyrics = !showLyrics },
                    isLyricsActive = showLyrics,
                    onToggleQueue = { showQueueSheet = !showQueueSheet }
                )

                // MIDDLE SECTION: Rotating Vinyl Disc, Sleek Video Canvas, or Synchronized Lyrics
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = if (showLyrics) "LYRICS" else playbackState.playbackMode.name,
                        transitionSpec = {
                            (scaleIn(
                                initialScale = 0.88f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            ) + fadeIn(tween(400))) togetherWith (scaleOut(
                                targetScale = 1.10f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            ) + fadeOut(tween(250)))
                        },
                        label = "MediaContentTransition"
                    ) { stateTag ->
                        when (stateTag) {
                            "LYRICS" -> {
                                LyricsView(
                                    lyrics = mediaItem.lyrics,
                                    currentPositionMs = playbackState.currentPositionMs,
                                    onSeekToTimestamp = { playbackService.seekTo(it) }
                                )
                            }
                            PlaybackMode.VIDEO.name -> {
                                VideoContainer(
                                    playbackService = playbackService,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                )
                            }
                            else -> {
                                RotatingVinylArt(
                                    isPlaying = playbackState.isPlaying,
                                    dominantColor = mediaItem.dominantColor,
                                    artworkUrl = mediaItem.artworkUrl,
                                    title = mediaItem.title
                                )
                            }
                        }
                    }
                }

                // BOTTOM PANEL: Title/Artist, Fluid Seek Bar, Morphing Controls
                FluidFloatingControlsPanel(
                    mediaItem = mediaItem,
                    playbackState = playbackState,
                    onPlayPause = { playbackService.togglePlayPause() },
                    onSeek = { playbackService.seekTo(it) },
                    onNext = { playbackService.skipToNext() },
                    onPrev = { playbackService.skipToPrevious() },
                    onToggleShuffle = { playbackService.toggleShuffle() },
                    onToggleRepeat = { playbackService.toggleRepeatMode() }
                )
            }
        }

        // Queue Up-Next Glass Sheet Overlay
        AnimatedVisibility(
            visible = showQueueSheet,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
            ) + fadeIn(tween(300)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(250)
            ) + fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            QueueSheet(
                queue = playbackState.queue,
                currentIndex = playbackState.queueIndex,
                dominantColor = mediaItem.dominantColor,
                onTrackSelected = { index ->
                    playbackService.playQueue(playbackState.queue, index, playbackState.playbackMode == PlaybackMode.VIDEO)
                    showQueueSheet = false
                },
                onDismiss = { showQueueSheet = false }
            )
        }
    }
}

/**
 * Top Control Header with fluid pill capsule for Song vs Video switching.
 */
@Composable
private fun TopControlBar(
    currentMode: PlaybackMode,
    onModeSelected: (PlaybackMode) -> Unit,
    onCollapse: () -> Unit,
    onToggleLyrics: () -> Unit,
    isLyricsActive: Boolean,
    onToggleQueue: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onCollapse,
            modifier = Modifier
                .size(44.dp)
                .liquidGlassSurface(
                    shape = CircleShape,
                    backgroundColor = Color.White.copy(alpha = 0.08f),
                    borderWidth = 0.8.dp
                )
        ) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Collapse Player",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        // Elastic Fluid Capsule Selector: "Song" <-> "Video"
        ElasticCapsuleSelector(
            currentMode = currentMode,
            onModeSelected = onModeSelected
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Queue Toggle Button
            IconButton(
                onClick = onToggleQueue,
                modifier = Modifier
                    .size(44.dp)
                    .liquidGlassSurface(
                        shape = CircleShape,
                        backgroundColor = Color.White.copy(alpha = 0.08f),
                        borderWidth = 0.8.dp
                    )
            ) {
                Icon(
                    imageVector = Icons.Rounded.QueueMusic,
                    contentDescription = "Queue",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(22.dp)
                )
            }

            // Lyrics Toggle Button
            IconButton(
                onClick = onToggleLyrics,
                modifier = Modifier
                    .size(44.dp)
                    .liquidGlassSurface(
                        shape = CircleShape,
                        backgroundColor = if (isLyricsActive) Color(0xFF6C5CE7).copy(alpha = 0.45f) else Color.White.copy(alpha = 0.08f),
                        borderWidth = 0.8.dp
                    )
            ) {
                Icon(
                    imageVector = Icons.Rounded.FormatQuote,
                    contentDescription = "Lyrics",
                    tint = if (isLyricsActive) Color.White else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Elastic capsule toggle for switching between Audio and Video streams.
 */
@Composable
private fun ElasticCapsuleSelector(
    currentMode: PlaybackMode,
    onModeSelected: (PlaybackMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val isVideo = currentMode == PlaybackMode.VIDEO

    val pillOffsetFraction by animateFloatAsState(
        targetValue = if (isVideo) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "CapsulePillOffset"
    )

    Box(
        modifier = modifier
            .width(168.dp)
            .height(44.dp)
            .liquidGlassSurface(
                shape = RoundedCornerShape(22.dp),
                backgroundColor = Color.Black.copy(alpha = 0.45f),
                borderWidth = 1.dp
            )
            .padding(4.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val pillWidth = maxWidth / 2

            Box(
                modifier = Modifier
                    .width(pillWidth)
                    .height(maxHeight)
                    .offset(x = pillWidth * pillOffsetFraction)
                    .liquidGlassSurface(
                        shape = RoundedCornerShape(18.dp),
                        backgroundColor = Color.White.copy(alpha = 0.22f),
                        borderWidth = 1.dp,
                        borderBrush = Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.1f))
                        )
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
                        ) { onModeSelected(PlaybackMode.AUDIO_ONLY) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Song",
                        fontSize = 13.sp,
                        fontWeight = if (!isVideo) FontWeight.Bold else FontWeight.Normal,
                        color = if (!isVideo) Color.White else Color.White.copy(alpha = 0.55f)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onModeSelected(PlaybackMode.VIDEO) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Video",
                        fontSize = 13.sp,
                        fontWeight = if (isVideo) FontWeight.Bold else FontWeight.Normal,
                        color = if (isVideo) Color.White else Color.White.copy(alpha = 0.55f)
                    )
                }
            }
        }
    }
}

/**
 * Sleek Media3 PlayerView container embedded via AndroidView.
 */
@OptIn(UnstableApi::class)
@Composable
private fun VideoContainer(
    playbackService: MediaPlaybackService,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .liquidGlassSurface(
                shape = RoundedCornerShape(28.dp),
                backgroundColor = Color.Black,
                borderWidth = 1.dp
            )
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = false
                    player = playbackService.getPlayer()
                }
            },
            update = { playerView ->
                playerView.player = playbackService.getPlayer()
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Circular Rotating Vinyl Disc with procedural groove rendering and Coil album art medallion.
 */
@Composable
private fun RotatingVinylArt(
    isPlaying: Boolean,
    dominantColor: Color,
    artworkUrl: String,
    title: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "VinylRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RotationAngle"
    )

    val currentRotation = if (isPlaying) rotation else 0f

    Box(
        modifier = modifier
            .size(310.dp)
            .shadow(
                elevation = 32.dp,
                shape = CircleShape,
                ambientColor = dominantColor,
                spotColor = dominantColor
            ),
        contentAlignment = Alignment.Center
    ) {
        // Procedural Vinyl Canvas with groove rings
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .rotate(currentRotation)
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = size.width / 2f

            // Outer Vinyl Body
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1E1E24),
                        Color(0xFF111116),
                        Color(0xFF09090D)
                    ),
                    center = center,
                    radius = outerRadius
                ),
                radius = outerRadius,
                center = center
            )

            // Groove Reflections
            for (i in 1..7) {
                val grooveRadius = outerRadius * (0.42f + (i * 0.075f))
                drawCircle(
                    color = Color.White.copy(alpha = 0.035f),
                    radius = grooveRadius,
                    center = center,
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }

            // Radial specular sheen streaks across vinyl
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.08f),
                        Color.Transparent,
                        Color.White.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center
                ),
                radius = outerRadius,
                center = center
            )
        }

        // Center Album Art Medallion with Coil Image Loader
        Box(
            modifier = Modifier
                .size(136.dp)
                .rotate(currentRotation)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(dominantColor, dominantColor.copy(alpha = 0.7f), Color.Black)
                    )
                )
                .border(3.dp, Color.White.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (artworkUrl.isNotEmpty()) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Headphones,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(40.dp)
                )
            }

            // Spindle Center Hole
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF09090D))
                    .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
            )
        }
    }
}

/**
 * Fluid Floating Controls Panel with interactive seek-bar, song metadata,
 * and vector path morphing play/pause.
 */
@Composable
private fun FluidFloatingControlsPanel(
    mediaItem: MediaItemModel,
    playbackState: PlaybackState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit
) {
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderTempPosition by remember { mutableFloatStateOf(0f) }

    val currentMs = if (isDraggingSlider) sliderTempPosition.toLong() else playbackState.currentPositionMs
    val totalMs = playbackState.durationMs.coerceAtLeast(1L)
    val sliderProgress = (currentMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                shape = RoundedCornerShape(32.dp),
                backgroundColor = Color(0xFF14141B).copy(alpha = 0.68f),
                borderWidth = 1.dp,
                blurRadius = 32.dp
            )
            .padding(horizontal = 22.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Song Title & Artist Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mediaItem.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = mediaItem.artistOrChannel,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = { /* Additional actions */ }) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Options",
                    tint = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Liquid Floating Seek Bar
        Slider(
            value = sliderProgress,
            onValueChange = { frac ->
                isDraggingSlider = true
                sliderTempPosition = frac * totalMs
            },
            onValueChangeFinished = {
                isDraggingSlider = false
                onSeek(sliderTempPosition.toLong())
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = mediaItem.dominantColor,
                inactiveTrackColor = Color.White.copy(alpha = 0.12f)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Timestamps (Current vs Total Duration)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(currentMs),
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.5f)
            )
            Text(
                text = formatDuration(totalMs),
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.5f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Transport Controls: Shuffle, Prev, Morphing Play/Pause, Next, Repeat
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle
            IconButton(onClick = onToggleShuffle) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (playbackState.shuffleEnabled) mediaItem.dominantColor else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp)
                )
            }

            // Previous
            IconButton(onClick = onPrev) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }

            // Vector Morphing Play/Pause Core
            PlayPauseMorphButton(
                isPlaying = playbackState.isPlaying,
                onClick = onPlayPause,
                size = 68.dp,
                glowColor = mediaItem.dominantColor
            )

            // Next
            IconButton(onClick = onNext) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }

            // Repeat Mode
            IconButton(onClick = onToggleRepeat) {
                val repeatIcon = if (playbackState.repeatMode == StreamRepeatMode.ONE) {
                    Icons.Rounded.RepeatOne
                } else {
                    Icons.Rounded.Repeat
                }
                val repeatTint = if (playbackState.repeatMode != StreamRepeatMode.OFF) {
                    mediaItem.dominantColor
                } else {
                    Color.White.copy(alpha = 0.6f)
                }

                Icon(
                    imageVector = repeatIcon,
                    contentDescription = "Repeat",
                    tint = repeatTint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Glassmorphic Up-Next Queue sheet.
 */
@Composable
private fun QueueSheet(
    queue: List<MediaItemModel>,
    currentIndex: Int,
    dominantColor: Color,
    onTrackSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp)
                .clickable(enabled = false) {}
                .liquidGlassSurface(
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                    backgroundColor = Color(0xFF14141B).copy(alpha = 0.88f),
                    borderWidth = 1.dp,
                    blurRadius = 40.dp
                )
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Up Next in Queue",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${queue.size} streams in continuous queue",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(queue) { index, item ->
                    val isCurrent = index == currentIndex

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlassSurface(
                                shape = RoundedCornerShape(16.dp),
                                backgroundColor = if (isCurrent) dominantColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f),
                                borderWidth = if (isCurrent) 1.2.dp else 0.5.dp,
                                borderBrush = if (isCurrent) {
                                    Brush.linearGradient(listOf(dominantColor, Color.White.copy(alpha = 0.8f)))
                                } else {
                                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.2f), Color.Transparent))
                                }
                            )
                            .clickable { onTrackSelected(index) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Artwork or Icon thumbnail
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(item.dominantColor),
                            contentAlignment = Alignment.Center
                        ) {
                            if (item.artworkUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = item.artworkUrl,
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Headphones,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                fontSize = 14.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.artistOrChannel,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.55f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (isCurrent) {
                            Text(
                                text = "NOW PLAYING",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = dominantColor,
                                modifier = Modifier
                                    .background(dominantColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        } else {
                            Text(
                                text = formatDuration(item.durationMs),
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.45f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Formats milliseconds into mm:ss time display.
 */
private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
