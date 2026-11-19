package com.nexusstream.app.ui.theme

import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

/*
 * Liquid glass: translucent surfaces that blur what scrolls underneath them, with a bright rim
 * and a faint top sheen, tinted for the light or dark theme.
 *
 * How it is wired: a screen's scrolling content is marked with [glassBackdrop], and the chrome
 * floating over it (top bar, mini player, navigation bar) uses [liquidGlass] with the same
 * [HazeState]. The two must be siblings, never one inside the other, so the state is always passed
 * explicitly. Real blur needs Android 12L+ (RenderEffect); older versions get a nearly opaque
 * tint instead, which keeps text readable without the blur.
 */

/** How strong a piece of glass is: thin for small controls, thick for big panels. */
enum class GlassWeight(internal val tintScale: Float, internal val blur: Dp) {
    Thin(0.8f, 16.dp),
    Regular(1f, 24.dp),
    Thick(1.2f, 34.dp)
}

/**
 * Space covered by floating glass chrome over a screen's content. Lists that read it add it to
 * their content padding so they scroll underneath the glass; anything else is laid out clear of it.
 */
val LocalChromePadding = compositionLocalOf { PaddingValues(0.dp) }

/** [this] plus extra top and bottom space. */
fun PaddingValues.plus(top: Dp = 0.dp, bottom: Dp = 0.dp, horizontal: Dp? = null): PaddingValues = PaddingValues(
    start = horizontal ?: calculateStartPadding(LayoutDirection.Ltr),
    end = horizontal ?: calculateEndPadding(LayoutDirection.Ltr),
    top = calculateTopPadding() + top,
    bottom = calculateBottomPadding() + bottom
)

/** Marks the content that glass surfaces with the same [state] blur. */
fun Modifier.glassBackdrop(state: HazeState?): Modifier = if (state == null) this else haze(state)

/**
 * A liquid glass surface in the app's theme. With a [backdrop] it blurs that content behind it;
 * without one (or where blur is unavailable) it is a soft translucent tint.
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape,
    backdrop: HazeState? = null,
    weight: GlassWeight = GlassWeight.Regular,
    elevation: Dp = 0.dp,
    rim: Boolean = true,
    /**
     * 0 = the usual tinted glass, 1 = clear glass (blur only, a whisper of tint). Bars use it for
     * the iOS "scroll edge" look: clear at rest, frosting over as content scrolls under them.
     */
    clarity: Float = 0f
): Modifier {
    val p = ThemeState.palette
    // Quantised so a fading bar rebuilds its style a few dozen times, not every frame.
    val clear = (clarity.coerceIn(0f, 1f) * 24).toInt() / 24f
    val style = remember(p, weight, clear) {
        val tintAlpha = (p.glassTint.alpha * weight.tintScale).coerceAtMost(1f) * (1f - 0.8f * clear)
        HazeStyle(
            backgroundColor = p.background,
            tint = HazeTint(p.glassTint.copy(alpha = tintAlpha)),
            blurRadius = weight.blur,
            noiseFactor = if (p.isDark) 0.05f else 0.03f,
            fallbackTint = HazeTint(p.glassFallback)
        )
    }
    val rimBrush = remember(p) {
        Brush.verticalGradient(
            listOf(p.glassRim, p.glassRim.copy(alpha = p.glassRim.alpha * 0.2f), p.glassRim.copy(alpha = p.glassRim.alpha * 0.45f))
        )
    }
    val highlight = p.glassHighlight
    var m = this
    if (elevation > 0.dp) {
        val shadow = Color.Black.copy(alpha = if (p.isDark) 0.45f else 0.16f)
        m = m.shadow(elevation, shape, clip = false, ambientColor = shadow, spotColor = shadow)
    }
    m = m.clip(shape)
    m = if (backdrop != null) m.hazeChild(backdrop, style) else m.background(p.glassFallback.copy(alpha = p.glassFallback.alpha * (1f - clear)))
    m = m.drawBehind { drawSheen(highlight.copy(alpha = highlight.alpha * (1f - clear))) }
    if (rim) m = m.border(0.75.dp, rimBrush, shape)
    return m
}

/**
 * Glass for controls drawn over video or artwork, which is always dark: a smoky translucent disc
 * with a light rim, in both themes.
 */
fun Modifier.frostedGlass(shape: Shape, pressed: Boolean = false, strength: Float = 1f): Modifier = this
    .clip(shape)
    .background(Color.Black.copy(alpha = ((if (pressed) 0.42f else 0.28f) * strength).coerceIn(0f, 1f)))
    .drawBehind { drawSheen(Color.White.copy(alpha = 0.10f * strength)) }
    .border(
        0.75.dp,
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.14f))),
        shape
    )

/**
 * A liquid glass button, chip or tile. Without [fill] it is clear glass: a whisper of tint you can
 * see through, a glossy top sheen and a bright specular rim. With [fill] (a primary button, a
 * selected chip) it is tinted glass: that colour, very slightly see-through, with the same sheen
 * and rim. Press feedback comes from the app's [LiquidIndication].
 */
fun Modifier.glassTile(shape: Shape, fill: Color? = null): Modifier {
    val p = ThemeState.palette
    val dark = p.isDark
    val body = fill?.let { it.copy(alpha = it.alpha * 0.9f) }
        ?: p.text.copy(alpha = if (dark) 0.085f else 0.05f)
    val sheen = when {
        fill != null -> Color.White.copy(alpha = if (dark) 0.22f else 0.16f)
        dark -> Color.White.copy(alpha = 0.07f)
        else -> Color.White.copy(alpha = 0.55f)
    }
    val rim = if (dark) {
        listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.12f))
    } else {
        listOf(Color.White.copy(alpha = 0.95f), Color.Black.copy(alpha = 0.03f), Color.Black.copy(alpha = 0.09f))
    }
    return this
        .clip(shape)
        .background(body)
        .drawBehind { drawSheen(sheen) }
        .border(0.75.dp, Brush.verticalGradient(rim), shape)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSheen(color: Color) {
    if (color.alpha <= 0f) return
    drawRect(
        brush = Brush.verticalGradient(listOf(color, Color.Transparent), startY = 0f, endY = size.height * 0.5f),
        size = Size(size.width, size.height * 0.5f)
    )
}

/** Motion tuned to feel like iOS: critically damped springs, quick to settle, no wobble unless asked. */
object GlassMotion {
    /** Pages, sheets and big surfaces. */
    fun <T> smooth(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 340f)
    /** Small state changes: chips, toggles, colours. */
    fun <T> snappy(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)
    /** Selections that land with a hint of overshoot. */
    fun <T> bouncy(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.72f, stiffness = 420f)
}

/** Shrinks a little while pressed and springs back, like iOS buttons and cells. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource, pressedScale: Float = 0.96f): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 520f),
        label = "pressScale"
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** A click target that gives [pressScale] feedback instead of a ripple. */
@Composable
fun Modifier.bouncyClickable(
    onClickLabel: String? = null,
    role: Role? = null,
    pressedScale: Float = 0.96f,
    onClick: () -> Unit
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return pressScale(interaction, pressedScale)
        .clickable(interactionSource = interaction, indication = null, onClickLabel = onClickLabel, role = role, onClick = onClick)
}

/**
 * Tracks how far the content under a glass bar has scrolled, from the scroll deltas that pass
 * through [connection] (attach it with `Modifier.nestedScroll`). [frost] goes from 0 (content at
 * the top: the bar is clear) to 1 (content under the bar: it frosts and shows its hairline).
 */
class ScrollEdge internal constructor(private val thresholdPx: Float) {
    private var scrolled by androidx.compose.runtime.mutableFloatStateOf(0f)

    /** 0..1, unanimated; read it through [rememberScrollEdgeFrost] for a smooth fade. */
    val rawFrost: Float get() = (scrolled / thresholdPx).coerceIn(0f, 1f)

    val connection = object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
        override fun onPostScroll(
            consumed: androidx.compose.ui.geometry.Offset,
            available: androidx.compose.ui.geometry.Offset,
            source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
        ): androidx.compose.ui.geometry.Offset {
            scrolled = (scrolled - consumed.y).coerceAtLeast(0f)
            // Pulled down with nothing left to scroll: the list is at its top.
            if (available.y > 0f) scrolled = 0f
            return androidx.compose.ui.geometry.Offset.Zero
        }
    }

    /** For content that scrolls by code (e.g. back to the top on refresh). */
    fun reset() { scrolled = 0f }
}

@Composable
fun rememberScrollEdge(threshold: Dp = 28.dp): ScrollEdge {
    val px = with(LocalDensity.current) { threshold.toPx() }
    return remember(px) { ScrollEdge(px) }
}

/** [ScrollEdge.rawFrost], eased with a soft spring. */
@Composable
fun rememberScrollEdgeFrost(edge: ScrollEdge): Float {
    val frost by animateFloatAsState(edge.rawFrost, GlassMotion.smooth(), label = "ScrollEdgeFrost")
    return frost
}

/** A hairline under a bar that fades in with [alpha] (0..1). */
fun Modifier.edgeHairline(alpha: Float): Modifier = drawBehind {
    if (alpha <= 0f) return@drawBehind
    val color = ThemeState.palette.divider
    drawRect(
        color = color.copy(alpha = color.alpha * alpha),
        topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 0.75.dp.toPx()),
        size = Size(size.width, 0.75.dp.toPx())
    )
}

/** True where windows can blur what is behind them (Android 12+ with cross-window blur on). */
fun windowBlurSupported(view: View): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val wm = view.context.getSystemService(WindowManager::class.java) ?: return false
    return runCatching { wm.isCrossWindowBlurEnabled }.getOrDefault(false)
}

/**
 * Call inside a bottom sheet or dialog: on Android 12+ the app behind it is blurred, like an iOS
 * sheet. Does nothing on older versions.
 */
@Composable
fun GlassWindowBlur(radius: Dp = 28.dp) {
    val view = LocalView.current
    val px = with(LocalDensity.current) { radius.roundToPx() }
    DisposableEffect(view, px) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            var v: Any? = view
            var window: android.view.Window? = null
            while (v is View && window == null) {
                window = (v as? DialogWindowProvider)?.window
                v = v.parent
            }
            window?.let { w ->
                runCatching {
                    w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    w.attributes = w.attributes.apply { blurBehindRadius = px }
                }
            }
        }
        onDispose { }
    }
}
