package com.nexusstream.app.ui.theme

import android.os.Build
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.nexusstream.app.data.AppSettings
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.lightColorScheme
import android.content.ContextWrapper
import android.content.Context
import android.app.Activity
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.nexusstream.app.R
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Global fluid spring physics for liquid animations.
 */
object LiquidPhysics {
    val LowBouncyFluidSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    )

    val ElasticDragSpring = spring<Float>(
        dampingRatio = 0.65f,
        stiffness = 250f
    )

    fun <T> organicSpring(): AnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    )
}

/**
 * Glassmorphic color palette configuration.
 */
@Stable
data class GlassColors(
    val primaryTint: Color = Color(0xFF6C5CE7),
    val secondaryTint: Color = Color(0xFFFF7675),
    val surfaceGlass: Color = Color(0xFF1E1E24).copy(alpha = 0.55f),
    val surfaceGlassBorderStart: Color = Color.White.copy(alpha = 0.35f),
    val surfaceGlassBorderEnd: Color = Color.White.copy(alpha = 0.05f),
    val specularHighlight: Color = Color.White.copy(alpha = 0.18f),
    val backgroundDeep: Color = Color(0xFF0B0B0F),
    val textPrimary: Color = Color(0xFFFBFBFD),
    val textSecondary: Color = Color(0xFFA0A0AB)
)

val LocalGlassColors = compositionLocalOf { GlassColors() }

/**
 * Modifier creating an authentic liquid glass surface:
 * 1. Blur backing (with fallback)
 * 2. Translucent glass tint
 * 3. Specular directional gradient border (0.5dp to 1dp)
 * 4. Micro specular top highlight simulating ambient light refraction
 */
fun Modifier.liquidGlassSurface(
    shape: Shape = RoundedCornerShape(24.dp),
    backgroundColor: Color = Color(0xFF1B1B22).copy(alpha = 0.52f),
    borderWidth: Dp = 1.dp,
    borderBrush: Brush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.38f),
            Color.White.copy(alpha = 0.06f),
            Color(0xFF6C5CE7).copy(alpha = 0.22f)
        ),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    ),
    blurRadius: Dp = 28.dp,
    highlightColor: Color = Color.White.copy(alpha = 0.12f)
): Modifier = this
    // NOTE: Modifier.blur() blurs this element's own content (text, icons), not what is behind it,
    // so it is intentionally not applied here. blurRadius is kept for API compatibility.
    .clip(shape)
    .background(backgroundColor, shape)
    .border(borderWidth, borderBrush, shape)
    .drawBehind {
        drawSpecularGlassSheen(highlightColor)
    }

/**
 * Draws a subtle top-angled specular reflection sheen on the glass layer.
 */
private fun DrawScope.drawSpecularGlassSheen(highlightColor: Color) {
    val sheenHeight = size.height * 0.4f
    val brush = Brush.verticalGradient(
        colors = listOf(highlightColor, Color.Transparent),
        startY = 0f,
        endY = sheenHeight
    )
    drawRect(
        brush = brush,
        topLeft = Offset.Zero,
        size = androidx.compose.ui.geometry.Size(size.width, sheenHeight)
    )
}

/** Roboto (bundled variable font) - the typeface the official YouTube apps use. */
@OptIn(ExperimentalTextApi::class)
val RobotoFamily = FontFamily(
    listOf(
        FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold,
        FontWeight.Bold, FontWeight.ExtraBold, FontWeight.Black
    ).map { weight ->
        Font(
            resId = R.font.roboto_variable,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
        )
    }
)

private fun robotoTypography(): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = RobotoFamily),
        displayMedium = base.displayMedium.copy(fontFamily = RobotoFamily),
        displaySmall = base.displaySmall.copy(fontFamily = RobotoFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = RobotoFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = RobotoFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = RobotoFamily),
        titleLarge = base.titleLarge.copy(fontFamily = RobotoFamily),
        titleMedium = base.titleMedium.copy(fontFamily = RobotoFamily),
        titleSmall = base.titleSmall.copy(fontFamily = RobotoFamily),
        // bodyLarge is the default text style: no extra tracking or fixed line height.
        bodyLarge = base.bodyLarge.copy(fontFamily = RobotoFamily, letterSpacing = 0.sp, lineHeight = TextUnit.Unspecified),
        bodyMedium = base.bodyMedium.copy(fontFamily = RobotoFamily),
        bodySmall = base.bodySmall.copy(fontFamily = RobotoFamily),
        labelLarge = base.labelLarge.copy(fontFamily = RobotoFamily),
        labelMedium = base.labelMedium.copy(fontFamily = RobotoFamily),
        labelSmall = base.labelSmall.copy(fontFamily = RobotoFamily)
    )
}

/**
 * Root theme for NexusStream.
 */
@Composable
fun NexusTheme(
    dominantColor: Color = Color(0xFF6C5CE7),
    secondaryColor: Color = Color(0xFFFF7675),
    content: @Composable () -> Unit
) {
    // Light / dark / follow the system, from Settings.
    val mode by AppSettings.themeMode.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val palette = paletteFor(mode, systemDark)
    SideEffect { ThemeState.palette = palette }
    SystemBarIcons(lightIcons = palette.isDark)

    val glassColors = GlassColors(
        primaryTint = dominantColor,
        secondaryTint = secondaryColor,
        surfaceGlass = Color(0xFF16161D).copy(alpha = 0.58f),
        surfaceGlassBorderStart = Color.White.copy(alpha = 0.32f),
        surfaceGlassBorderEnd = dominantColor.copy(alpha = 0.20f),
        specularHighlight = Color.White.copy(alpha = 0.16f),
        backgroundDeep = Color(0xFF09090D),
        textPrimary = Color(0xFFF9F9FB),
        textSecondary = Color(0xFFA1A1AA)
    )

    val view = LocalView.current
    SideEffect { ThemeState.windowBlur = windowBlurSupported(view) }

    // Monochrome Material colours: controls (switches, sliders, fields, menus) take the text and
    // surface greys instead of a stock hue, so artwork and thumbnails carry the colour.
    val colorScheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.text,
            onPrimary = palette.onInverse,
            primaryContainer = palette.chip,
            onPrimaryContainer = palette.text,
            secondary = palette.textSecondary,
            onSecondary = palette.onInverse,
            secondaryContainer = palette.chip,
            onSecondaryContainer = palette.text,
            tertiary = palette.accent,
            background = palette.background,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = palette.chip,
            onSurfaceVariant = palette.textSecondary,
            surfaceContainerLowest = palette.background,
            surfaceContainerLow = palette.surface,
            surfaceContainer = palette.surface,
            surfaceContainerHigh = palette.chip,
            surfaceContainerHighest = palette.chip,
            outline = palette.divider,
            outlineVariant = palette.divider,
            error = palette.danger
        )
    } else {
        lightColorScheme(
            primary = palette.text,
            onPrimary = palette.onInverse,
            primaryContainer = palette.chip,
            onPrimaryContainer = palette.text,
            secondary = palette.textSecondary,
            onSecondary = palette.onInverse,
            secondaryContainer = palette.chip,
            onSecondaryContainer = palette.text,
            tertiary = palette.accent,
            background = palette.background,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = palette.chip,
            onSurfaceVariant = palette.textSecondary,
            surfaceContainerLowest = palette.background,
            surfaceContainerLow = palette.surface,
            surfaceContainer = palette.surface,
            surfaceContainerHigh = palette.chip,
            surfaceContainerHighest = palette.chip,
            outline = palette.divider,
            outlineVariant = palette.divider,
            error = palette.danger
        )
    }

    CompositionLocalProvider(
        LocalGlassColors provides glassColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = robotoTypography(),
            content = content
        )
    }
}

/**
 * Status / navigation bar icons: light icons for dark surfaces, dark icons for light ones.
 * Screens that are always dark (video, now playing) can call this too; it restores the theme's
 * setting when they leave.
 */
@Composable
fun SystemBarIcons(lightIcons: Boolean, includeNavigationBar: Boolean = true) {
    val view = LocalView.current
    val themeLightIcons = ThemeState.palette.isDark
    DisposableEffect(lightIcons) {
        val window = view.context.findThemeActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = !lightIcons
        if (includeNavigationBar) controller?.isAppearanceLightNavigationBars = !lightIcons
        onDispose {
            controller?.isAppearanceLightStatusBars = !themeLightIcons
            if (includeNavigationBar) controller?.isAppearanceLightNavigationBars = !themeLightIcons
        }
    }
}

private tailrec fun Context.findThemeActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findThemeActivity()
    else -> null
}

object NexusThemeTokens {
    val glass: GlassColors
        @Composable
        @ReadOnlyComposable
        get() = LocalGlassColors.current
}
