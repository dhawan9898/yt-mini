package com.nexusstream.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The playback position, advanced every frame between the player's updates (which come a few
 * times a second), so seek bars and lyrics glide instead of stepping. Jumps straight to a new
 * position after a seek, and holds still while paused. [stepMs] limits how often the value
 * changes, for readers that recompose on every change.
 */
@Composable
fun rememberSmoothPosition(positionMs: Long, isPlaying: Boolean, speed: Float = 1f, stepMs: Long = 0L): State<Long> {
    val shown = remember { mutableLongStateOf(positionMs) }
    LaunchedEffect(positionMs, isPlaying, speed) {
        shown.longValue = positionMs
        if (!isPlaying) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) {
            withFrameMillis { now ->
                val next = positionMs + ((now - start) * speed).toLong()
                if (stepMs <= 0L || next - shown.longValue >= stepMs) shown.longValue = next
            }
        }
    }
    return shown
}

/**
 * YT Music style seek bar, made liquid: a thin line that, while the song plays, ripples as a soft
 * wave; touch it and it swells into a thick bar with a knob, follows the finger, and stretches a
 * little when pulled past either end before springing back. Tap anywhere to jump there.
 *
 * [position] is read only while drawing, so a smoothly moving position redraws the bar without
 * recomposing anything around it.
 */
@Composable
fun LiquidSeekBar(
    position: () -> Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    buffered: () -> Float = { 0f },
    activeColor: Color = Color.White,
    trackColor: Color = Color.White.copy(alpha = 0.22f),
    /** The fraction under the finger while scrubbing (null when not), for a live time label. */
    onScrub: (Float?) -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var overshoot by remember { mutableFloatStateOf(0f) }
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnScrub by rememberUpdatedState(onScrub)

    val thickness by animateDpAsState(
        targetValue = if (pressed) 10.dp else 4.dp,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 520f),
        label = "SeekThickness"
    )
    val knob by animateDpAsState(
        targetValue = if (pressed) 18.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 560f),
        label = "SeekKnob"
    )
    // The wave calms down while paused or scrubbing.
    val amplitude by animateFloatAsState(
        targetValue = if (isPlaying && !pressed) 1f else 0f,
        animationSpec = tween(450),
        label = "SeekWave"
    )
    val stretch by animateFloatAsState(
        targetValue = if (pressed) overshoot else 0f,
        animationSpec = if (pressed) spring<Float>(stiffness = 2000f) else spring<Float>(dampingRatio = 0.4f, stiffness = 380f),
        label = "SeekStretch"
    )
    val phase = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            phase.snapTo(0f)
            phase.animateTo((2 * PI).toFloat(), tween(1600, easing = LinearEasing))
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun update(x: Float) {
                        val raw = x / size.width
                        dragFraction = raw.coerceIn(0f, 1f)
                        // Rubber band past the ends: grows slower the further it is pulled.
                        val past = when {
                            raw < 0f -> raw
                            raw > 1f -> raw - 1f
                            else -> 0f
                        }
                        overshoot = if (past == 0f) 0f else (if (past < 0) -1f else 1f) * sqrt(abs(past)) * 0.06f
                        currentOnScrub(dragFraction)
                    }
                    pressed = true
                    update(down.position.x)
                    down.consume()
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        update(change.position.x)
                        change.consume()
                    }
                    currentOnSeek(dragFraction)
                    currentOnScrub(null)
                    pressed = false
                    overshoot = 0f
                }
            }
    ) {
        val w = size.width
        val cy = size.height / 2f
        val t = thickness.toPx()
        // Stretch: the bar grows toward the side it is pulled, anchored at the other end.
        val extra = stretch * w
        val left = if (extra < 0f) extra else 0f
        val right = w + if (extra > 0f) extra else 0f
        val span = right - left
        val f = if (pressed) dragFraction else position().coerceIn(0f, 1f)
        val playedX = left + span * f
        val bufferedX = left + span * buffered().coerceIn(0f, 1f)

        drawLine(trackColor, Offset(left, cy), Offset(right, cy), t, StrokeCap.Round)
        if (bufferedX > playedX) {
            drawLine(trackColor.copy(alpha = trackColor.alpha * 1.6f), Offset(playedX, cy), Offset(bufferedX, cy), t, StrokeCap.Round)
        }

        val amp = 3.dp.toPx() * amplitude
        if (amp > 0.2f && playedX - left > t) {
            val wave = Path()
            val wavelength = 26.dp.toPx()
            val step = 2.dp.toPx()
            var x = left
            wave.moveTo(x, cy)
            while (x < playedX) {
                x = minOf(x + step, playedX)
                // The wave fades out right at the play head so it meets the knob cleanly.
                val fade = ((playedX - x) / (wavelength / 2f)).coerceIn(0f, 1f)
                wave.lineTo(x, cy + amp * fade * sin(2f * PI.toFloat() * (x - left) / wavelength - phase.value))
            }
            drawPath(wave, activeColor, style = Stroke(width = t, cap = StrokeCap.Round))
        } else if (playedX > left) {
            drawLine(activeColor, Offset(left, cy), Offset(playedX, cy), t, StrokeCap.Round)
        }
        val k = knob.toPx()
        if (k > 0.5f) drawCircle(activeColor, k / 2f, Offset(playedX, cy))
    }
}
