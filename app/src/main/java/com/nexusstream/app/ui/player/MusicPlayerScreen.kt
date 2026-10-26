package com.nexusstream.app.ui.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.nexusstream.app.data.LibraryRepository
import com.nexusstream.app.media.MediaPlaybackService
import com.nexusstream.app.model.PlaybackMode
import com.nexusstream.app.model.StreamRepeatMode
import com.nexusstream.app.ui.theme.DarkPalette
import com.nexusstream.app.ui.theme.SystemBarIcons
import com.nexusstream.app.ui.components.PlayerOptionsSheet

private enum class PlayerPanel { NONE, UP_NEXT, LYRICS }

/**
 * Now-playing screen laid out like the YouTube Music app: Song / Video toggle on top,
 * square album art, title + artist, seek bar, transport controls and an
 * UP NEXT / LYRICS tab row at the bottom.
 */
@OptIn(UnstableApi::class)
@Composable
fun MusicPlayerScreen(
    playbackService: MediaPlaybackService,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by playbackService.playbackState.collectAsState()
    val item = state.currentItem ?: return
    var panel by remember { mutableStateOf(PlayerPanel.NONE) }
    var showOptions by remember { mutableStateOf(false) }
    val savedItems by LibraryRepository.liked.collectAsState()
    val isLiked = savedItems.any { it.id == item.id }

    BackHandler(onBack = onCollapse)
    // The now-playing screen is always dark: keep light status / navigation icons.
    SystemBarIcons(lightIcons = true)

    var dragY by remember { mutableFloatStateOf(0f) }
    val animatedDragY by animateFloatAsState(
        targetValue = dragY,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "PlayerDrag"
    )
    var isDragging by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .offset { IntOffset(0, (if (isDragging) dragY else animatedDragY).roundToInt()) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = {
                        isDragging = false
                        if (dragY > size.height * 0.2f) onCollapse() else dragY = 0f
                    },
                    onDragCancel = {
                        isDragging = false
                        dragY = 0f
                    },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        dragY = (dragY + amount).coerceAtLeast(0f)
                    }
                )
            }
            .background(
                Brush.verticalGradient(
                    listOf(item.dominantColor.copy(alpha = 0.55f), Color.Black, Color.Black)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top bar: minimize, Song | Video toggle, cast + more
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCollapse) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Minimize", tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                SongVideoToggle(
                    isVideo = state.playbackMode == PlaybackMode.VIDEO,
                    onSelect = { playbackService.togglePlaybackMode(if (it) PlaybackMode.VIDEO else PlaybackMode.AUDIO_ONLY) }
                )
            }
            IconButton(onClick = { /* Cast */ }) {
                Icon(Icons.Rounded.Cast, "Cast", tint = Color.White)
            }
            IconButton(onClick = { showOptions = true }) {
                Icon(Icons.Rounded.MoreVert, "Quality and download", tint = Color.White)
            }
        }

        // Main area: art / video, or the Up next / Lyrics panel
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (panel) {
                PlayerPanel.UP_NEXT -> UpNextList(
                    queue = state.queue,
                    currentIndex = state.queueIndex,
                    onSelect = { index ->
                        playbackService.playQueue(
                            state.queue, index,
                            startInVideoMode = state.playbackMode == PlaybackMode.VIDEO
                        )
                    }
                )
                PlayerPanel.LYRICS -> LyricsPanel(
                    lyrics = item.lyrics,
                    currentPositionMs = state.currentPositionMs,
                    onSeek = { playbackService.seekTo(it) }
                )
                PlayerPanel.NONE -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (state.playbackMode == PlaybackMode.VIDEO) {
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
                            update = { it.player = playbackService.getPlayer() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(item.dominantColor)
                        ) {
                            if (item.artworkUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = item.artworkUrl,
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }

        // Title / artist / like
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.artistOrChannel,
                    fontSize = 14.sp,
                    color = DarkPalette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { LibraryRepository.toggleLike(item) }) {
                Icon(
                    Icons.Rounded.ThumbUp, "Like",
                    tint = if (isLiked) Color.White else DarkPalette.textSecondary
                )
            }
        }

        if (state.hasError) {
            Text(
                text = state.errorMessage ?: "Playback error",
                fontSize = 12.sp,
                color = Color(0xFFFF6B6B),
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        SeekBar(
            positionMs = state.currentPositionMs,
            durationMs = state.durationMs,
            onSeek = { playbackService.seekTo(it) },
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        // Transport controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { playbackService.toggleShuffle() }) {
                Icon(
                    Icons.Rounded.Shuffle, "Shuffle",
                    tint = if (state.shuffleEnabled) Color.White else DarkPalette.textSecondary
                )
            }
            IconButton(onClick = { playbackService.skipToPrevious() }, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Rounded.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(38.dp))
            }
            Box(modifier = Modifier.size(68.dp), contentAlignment = Alignment.Center) {
                PlayPauseMorphButton(
                    isPlaying = state.isPlaying,
                    onClick = { playbackService.togglePlayPause() },
                    size = 68.dp,
                    tint = Color.Black,
                    glowColor = Color.Transparent,
                    flatBackground = Color.White
                )
                if (state.isResolving || (state.isBuffering && !state.isPlaying)) {
                    CircularProgressIndicator(
                        color = Color.Black,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
            IconButton(onClick = { playbackService.skipToNext() }, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Rounded.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(38.dp))
            }
            IconButton(onClick = { playbackService.toggleRepeatMode() }) {
                Icon(
                    imageVector = if (state.repeatMode == StreamRepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    contentDescription = "Repeat",
                    tint = if (state.repeatMode == StreamRepeatMode.OFF) DarkPalette.textSecondary else Color.White
                )
            }
        }

        // Bottom tabs: UP NEXT | LYRICS (tap again to return to the art)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PanelTab("UP NEXT", panel == PlayerPanel.UP_NEXT) {
                panel = if (panel == PlayerPanel.UP_NEXT) PlayerPanel.NONE else PlayerPanel.UP_NEXT
            }
            PanelTab("LYRICS", panel == PlayerPanel.LYRICS) {
                panel = if (panel == PlayerPanel.LYRICS) PlayerPanel.NONE else PlayerPanel.LYRICS
            }
        }
    }

    if (showOptions) {
        PlayerOptionsSheet(
            item = item,
            state = state,
            service = playbackService,
            showVideoQuality = state.playbackMode == PlaybackMode.VIDEO,
            onDismiss = { showOptions = false }
        )
    }
}

@Composable
private fun SongVideoToggle(isVideo: Boolean, onSelect: (isVideo: Boolean) -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .width(160.dp)
            .height(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .padding(3.dp)
    ) {
        val half = maxWidth / 2
        Box(
            modifier = Modifier
                .width(half)
                .fillMaxHeight()
                .offset(x = if (isVideo) half else 0.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.9f))
        )
        Row(modifier = Modifier.fillMaxSize()) {
            listOf("Song" to false, "Video" to true).forEach { (label, video) ->
                val selected = isVideo == video
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(video) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (selected) Color.Black else Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val safeDuration = durationMs.coerceAtLeast(1L)
    val fraction = dragFraction ?: (positionMs.toFloat() / safeDuration).coerceIn(0f, 1f)
    val shownMs = dragFraction?.let { (it * safeDuration).toLong() } ?: positionMs

    Column(modifier = modifier) {
        Slider(
            value = fraction,
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                dragFraction?.let { onSeek((it * safeDuration).toLong()) }
                dragFraction = null
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
            )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(shownMs), fontSize = 12.sp, color = DarkPalette.textSecondary)
            Text(formatTime(durationMs), fontSize = 12.sp, color = DarkPalette.textSecondary)
        }
    }
}

@Composable
private fun PanelTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) Color.White else DarkPalette.textSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(2.dp)
                .background(if (selected) Color.White else Color.Transparent)
        )
    }
}

@Composable
private fun UpNextList(
    queue: List<com.nexusstream.app.model.MediaItemModel>,
    currentIndex: Int,
    onSelect: (Int) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(queue, key = { index, q -> "q_${index}_${q.id}" }) { index, q ->
            val isCurrent = index == currentIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isCurrent) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(q.dominantColor)
                ) {
                    if (q.artworkUrl.isNotEmpty()) {
                        AsyncImage(
                            model = q.artworkUrl,
                            contentDescription = q.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(q.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(q.artistOrChannel, fontSize = 12.sp, color = DarkPalette.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(formatTime(q.durationMs), fontSize = 12.sp, color = DarkPalette.textSecondary)
            }
        }
    }
}

@Composable
private fun LyricsPanel(
    lyrics: List<com.nexusstream.app.model.LyricLine>,
    currentPositionMs: Long,
    onSeek: (Long) -> Unit
) {
    if (lyrics.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Lyrics aren't available for this song",
                fontSize = 14.sp,
                color = DarkPalette.textSecondary,
                textAlign = TextAlign.Center
            )
        }
    } else {
        LyricsView(
            lyrics = lyrics,
            currentPositionMs = currentPositionMs,
            onSeekToTimestamp = onSeek
        )
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
