package com.nexusstream.app.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusstream.app.model.LyricLine
import com.nexusstream.app.model.LyricWord
import com.nexusstream.app.ui.theme.LocalGlassColors

/**
 * High-performance Synchronized Lyrics Engine with karaoke-style fluid visual flows.
 * Smoothly interpolates active syllable sweeps and softly blurs inactive lines
 * into ambient liquid background space.
 */
@Composable
fun LyricsView(
    lyrics: List<LyricLine>,
    currentPositionMs: Long,
    onSeekToTimestamp: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val glassColors = LocalGlassColors.current

    // Locate the active lyric index based on current playback timestamp
    val activeIndex = remember(lyrics, currentPositionMs) {
        val idx = lyrics.indexOfLast { it.startTimeMs <= currentPositionMs }
        if (idx != -1 && currentPositionMs <= lyrics[idx].endTimeMs + 1000) idx else idx
    }

    // Organic auto-scroll targeting the focal active lyric line
    LaunchedEffect(activeIndex) {
        if (activeIndex in lyrics.indices) {
            val targetScrollIndex = (activeIndex - 1).coerceAtLeast(0)
            listState.animateScrollToItem(
                index = targetScrollIndex,
                scrollOffset = -120
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Ambient liquid gradient edge fade for top & bottom bounds
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.15f to Color.Black,
                        0.85f to Color.Black,
                        1f to Color.Transparent
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 160.dp, bottom = 240.dp, start = 24.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(lyrics, key = { index, line -> "${index}_${line.startTimeMs}" }) { index, line ->
                val isActive = index == activeIndex
                val isPast = index < activeIndex

                KaraokeLyricRow(
                    line = line,
                    isActive = isActive,
                    isPast = isPast,
                    currentPositionMs = currentPositionMs,
                    highlightColor = glassColors.primaryTint,
                    onClick = { onSeekToTimestamp(line.startTimeMs) }
                )
            }
        }
    }
}

@Composable
private fun KaraokeLyricRow(
    line: LyricLine,
    isActive: Boolean,
    isPast: Boolean,
    currentPositionMs: Long,
    highlightColor: Color,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.06f else 0.95f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "LyricScale"
    )

    val alpha by animateFloatAsState(
        targetValue = when {
            isActive -> 1f
            isPast -> 0.40f
            else -> 0.22f
        },
        animationSpec = tween(durationMillis = 350),
        label = "LyricAlpha"
    )

    // Calculate line-level progression fraction for continuous sweep effect
    val lineDuration = (line.endTimeMs - line.startTimeMs).coerceAtLeast(1L)
    val lineProgress = if (isActive) {
        ((currentPositionMs - line.startTimeMs).toFloat() / lineDuration.toFloat()).coerceIn(0f, 1f)
    } else if (isPast) {
        1f
    } else {
        0f
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .alpha(alpha)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        if (line.words.isNotEmpty() && isActive) {
            // Word-level granular karaoke rendering
            KaraokeWordsFlow(
                words = line.words,
                currentPositionMs = currentPositionMs,
                highlightColor = highlightColor
            )
        } else {
            // Smooth gradient sweep along entire sentence
            Text(
                text = line.text,
                fontSize = if (isActive) 24.sp else 21.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = Color.White,
                lineHeight = 32.sp,
                modifier = Modifier.drawWithContent {
                    drawContent()
                    if (isActive && lineProgress > 0f) {
                        // Draw sweeping highlight sheen over active sentence
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0f to highlightColor.copy(alpha = 0.95f),
                                lineProgress to highlightColor.copy(alpha = 0.95f),
                                (lineProgress + 0.05f).coerceAtMost(1f) to Color.Transparent,
                                1f to Color.Transparent
                            ),
                            blendMode = BlendMode.SrcAtop
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun KaraokeWordsFlow(
    words: List<LyricWord>,
    currentPositionMs: Long,
    highlightColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        words.forEach { word ->
            val wordDuration = (word.endTimeMs - word.startTimeMs).coerceAtLeast(1L)
            val isWordActive = currentPositionMs in word.startTimeMs..word.endTimeMs
            val isWordCompleted = currentPositionMs > word.endTimeMs

            val wordProgress = when {
                isWordCompleted -> 1f
                isWordActive -> ((currentPositionMs - word.startTimeMs).toFloat() / wordDuration.toFloat()).coerceIn(0f, 1f)
                else -> 0f
            }

            val animatedWordColor by animateColorAsState(
                targetValue = if (isWordCompleted || isWordActive) highlightColor else Color.White.copy(alpha = 0.5f),
                animationSpec = tween(150),
                label = "WordColor"
            )

            Text(
                text = "${word.word} ",
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                color = animatedWordColor,
                modifier = Modifier.drawWithContent {
                    drawContent()
                    if (isWordActive) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0f to Color.White,
                                wordProgress to Color.White,
                                (wordProgress + 0.08f).coerceAtMost(1f) to Color.Transparent
                            ),
                            blendMode = BlendMode.SrcAtop
                        )
                    }
                }
            )
        }
    }
}
