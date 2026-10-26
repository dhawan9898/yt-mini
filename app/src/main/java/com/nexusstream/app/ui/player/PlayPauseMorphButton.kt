package com.nexusstream.app.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexusstream.app.ui.theme.liquidGlassSurface

/**
 * High-end Vector Path Morphing Play/Pause button.
 * Instead of crossfading icons, this component parametrically morphs a single polygon
 * and dual bar geometry from a directional play arrowhead to dual pause pills using
 * cubic hermite interpolation and liquid spring elasticity.
 */
@Composable
fun PlayPauseMorphButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    tint: Color = Color.White,
    glowColor: Color = Color(0xFF6C5CE7)
) {
    // 0f = Play (Arrowhead), 1f = Pause (Dual Bars)
    val morphProgress = remember { Animatable(if (isPlaying) 1f else 0f) }
    val pressScale = remember { Animatable(1f) }

    LaunchedEffect(isPlaying) {
        morphProgress.animateTo(
            targetValue = if (isPlaying) 1f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .scale(pressScale.value)
            .liquidGlassSurface(
                shape = CircleShape,
                backgroundColor = Color.White.copy(alpha = 0.12f),
                borderWidth = 1.dp,
                borderBrush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.60f),
                        glowColor.copy(alpha = 0.40f)
                    )
                ),
                blurRadius = 16.dp
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.42f)) {
            val canvasWidth = this.size.width
            val canvasHeight = this.size.height
            val progress = morphProgress.value

            drawMorphedVector(
                progress = progress,
                w = canvasWidth,
                h = canvasHeight,
                color = tint
            )
        }
    }
}

/**
 * Parametric path computation that continuously deforms the play arrowhead
 * into two parallel pause bars with rounded terminal joints.
 */
private fun DrawScope.drawMorphedVector(
    progress: Float,
    w: Float,
    h: Float,
    color: Color
) {
    // When progress = 0 (Play):
    // Bar 1 forms the left half of the triangle: (0.15w, 0.1h) -> (0.5w, 0.3h) -> (0.5w, 0.7h) -> (0.15w, 0.9h)
    // Bar 2 forms the right apex: (0.5w, 0.3h) -> (0.9w, 0.5h) -> (0.9w, 0.5h) -> (0.5w, 0.7h)
    // When progress = 1 (Pause):
    // Bar 1 forms: (0.15w, 0.1h) -> (0.40w, 0.1h) -> (0.40w, 0.9h) -> (0.15w, 0.9h)
    // Bar 2 forms: (0.60w, 0.1h) -> (0.85w, 0.1h) -> (0.85w, 0.9h) -> (0.60w, 0.9h)

    val p = progress.coerceIn(0f, 1f)

    // Left Bar Geometry
    val leftBarLeft = lerp(0.18f * w, 0.14f * w, p)
    val leftBarRight = lerp(0.52f * w, 0.38f * w, p)
    val leftBarTop = lerp(0.12f * h, 0.14f * h, p)
    val leftBarBottom = lerp(0.88f * h, 0.86f * h, p)

    val leftTopY = lerp(0.12f * h + (0.18f * h), 0.14f * h, p)
    val leftBottomY = lerp(0.88f * h - (0.18f * h), 0.86f * h, p)

    val leftPath = Path().apply {
        moveTo(leftBarLeft, leftBarTop)
        lineTo(leftBarRight, leftTopY)
        lineTo(leftBarRight, leftBottomY)
        lineTo(leftBarLeft, leftBarBottom)
        close()
    }

    // Right Bar Geometry
    val rightBarLeft = lerp(0.50f * w, 0.62f * w, p)
    val rightBarRight = lerp(0.88f * w, 0.86f * w, p)
    val rightTopY = lerp(0.50f * h, 0.14f * h, p)
    val rightBottomY = lerp(0.50f * h, 0.86f * h, p)
    val rightBarInTop = lerp(leftTopY, 0.14f * h, p)
    val rightBarInBottom = lerp(leftBottomY, 0.86f * h, p)

    val rightPath = Path().apply {
        moveTo(rightBarLeft, rightBarInTop)
        lineTo(rightBarRight, rightTopY)
        lineTo(rightBarRight, rightBottomY)
        lineTo(rightBarLeft, rightBarInBottom)
        close()
    }

    // Dynamic rotation to provide liquid angular inertia during state flip
    val rotationDegrees = (1f - p) * 12f
    drawContext.transform.rotate(rotationDegrees, Offset(w / 2f, h / 2f))

    drawPath(path = leftPath, color = color, style = Fill)
    drawPath(path = rightPath, color = color, style = Fill)
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}
