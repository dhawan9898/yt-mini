package com.nexusstream.app.ui.theme

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.Modifier.Node
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/*
 * The app's touch feel, in the spirit of iOS liquid glass: nothing ripples. A pressed surface
 * sinks a little under the finger and lights up with a faint glass sheen; on release it springs
 * back with a touch of overshoot, as if it were a drop of liquid settling.
 */

/** Into the press: quick and fully damped, so the response feels immediate. */
private val PressIn = spring<Float>(dampingRatio = 1f, stiffness = 1400f)

/** Out of the press: a soft spring with a little overshoot (the "liquid" settle). */
private val PressOut = spring<Float>(dampingRatio = 0.5f, stiffness = 320f)

/**
 * Drives [progress] (0 = rest, 1 = fully pressed) from this source's press interactions. A tap
 * that is released before the press-in finished still plays the whole press, so quick taps are
 * visible; a press cancelled by scrolling lets go at once.
 */
internal suspend fun InteractionSource.driveLiquidPress(progress: Animatable<Float, AnimationVector1D>) = coroutineScope {
    var pressJob: Job? = null
    interactions.collect { interaction ->
        when (interaction) {
            is PressInteraction.Press -> {
                pressJob?.cancel()
                pressJob = launch { progress.animateTo(1f, PressIn) }
            }
            is PressInteraction.Release -> {
                val pressing = pressJob
                pressJob = launch {
                    pressing?.join()
                    progress.animateTo(0f, PressOut)
                }
            }
            is PressInteraction.Cancel -> {
                pressJob?.cancel()
                pressJob = launch { progress.animateTo(0f, PressOut) }
            }
        }
    }
}

/**
 * The default indication for every `clickable` in the app (replaces the Material ripple): the
 * pressed element sinks by a few dp (smaller elements a little more, so it reads the same on a
 * chip and on a full-width row) under a faint rounded glass highlight.
 */
object LiquidIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = LiquidIndicationNode(interactionSource)
    override fun equals(other: Any?) = other === this
    override fun hashCode() = System.identityHashCode(this)
}

private class LiquidIndicationNode(private val source: InteractionSource) : Node(), DrawModifierNode {
    private val progress = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch { source.driveLiquidPress(progress) }
    }

    override fun ContentDrawScope.draw() {
        val p = progress.value
        if (p == 0f) {
            drawContent()
            return
        }
        val minSide = size.minDimension.coerceAtLeast(1f)
        // Sinks by about 6dp on the shorter side, capped at 6% for small things.
        val shrink = (6.dp.toPx() / minSide).coerceAtMost(0.06f)
        val scale = 1f - shrink * p
        scale(scale) { this@draw.drawContent() }

        val glow = p.coerceIn(0f, 1f)
        if (glow > 0f) {
            val ink = ThemeState.palette.text
            val corner = minOf(14.dp.toPx(), size.minDimension / 2f)
            scale(scale) {
                drawRoundRect(
                    color = ink.copy(alpha = 0.06f * glow),
                    cornerRadius = CornerRadius(corner)
                )
                // The glass sheen: brighter at the top edge, gone by the middle.
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.07f * glow), Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.55f
                    ),
                    cornerRadius = CornerRadius(corner)
                )
            }
        }
    }
}

/**
 * Drop-in for Material's IconButton: no ripple. While pressed, a glass droplet swells behind the
 * icon and the icon compresses; on release both spring back with a liquid settle.
 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit
) {
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(interaction) { interaction.driveLiquidPress(progress) }
    val tint = LocalContentColor.current

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(40.dp)
            .drawBehind {
                val p = progress.value.coerceIn(0f, 1f)
                if (p <= 0f) return@drawBehind
                val radius = size.minDimension / 2f * (0.62f + 0.38f * p)
                drawCircle(color = tint.copy(alpha = 0.12f * p), radius = radius)
                drawCircle(
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.16f * p), Color.Transparent),
                        startY = center.y - radius,
                        endY = center.y + radius * 0.2f
                    ),
                    radius = radius,
                    center = Offset(center.x, center.y)
                )
            }
            .graphicsLayer {
                val s = 1f - 0.14f * progress.value
                scaleX = s
                scaleY = s
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides if (enabled) tint else tint.copy(alpha = 0.38f)) {
            content()
        }
    }
}

/** Drop-in for Material's TextButton that sinks and springs back like the rest of the app. */
@Composable
fun GlassTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    androidx.compose.material3.TextButton(
        onClick = onClick,
        modifier = modifier.pressScale(interaction, 0.94f),
        enabled = enabled,
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interaction,
        content = content
    )
}

/** Drop-in for Material's Button that sinks and springs back like the rest of the app. */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier.pressScale(interaction, 0.95f),
        enabled = enabled,
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interaction,
        content = content
    )
}

/** A light selection tick, like the iOS picker and tab bar (respects the system haptics setting). */
fun View.selectionTick() {
    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
}
