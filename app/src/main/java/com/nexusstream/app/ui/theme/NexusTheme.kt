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
import androidx.compose.ui.unit.Dp
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
    .then(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.blur(blurRadius)
        } else {
            Modifier
        }
    )
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

/**
 * Dynamic mesh gradient canvas background that organically morphs and flows
 * based on the dominant and secondary colors extracted from media artwork.
 */
@Composable
fun DynamicMeshBackground(
    dominantColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "LiquidMeshTransition")
    
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2831853f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PhaseAnimation"
    )

    val scaleMorph by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ScaleMorph"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07070A))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Center points oscillating with harmonic motion
            val x1 = width * (0.35f + 0.18f * cos(phase.toDouble()).toFloat())
            val y1 = height * (0.28f + 0.15f * sin(phase.toDouble()).toFloat())

            val x2 = width * (0.68f + 0.14f * sin(phase * 0.8).toFloat())
            val y2 = height * (0.65f + 0.18f * cos(phase * 0.8).toFloat())

            val x3 = width * (0.50f + 0.20f * cos(phase * 1.2).toFloat())
            val y3 = height * (0.42f + 0.20f * sin(phase * 1.2).toFloat())

            val radius1 = width * 0.75f * scaleMorph
            val radius2 = width * 0.85f / scaleMorph
            val radius3 = width * 0.60f

            // Layer 1: Dominant color orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        dominantColor.copy(alpha = 0.55f),
                        dominantColor.copy(alpha = 0.22f),
                        Color.Transparent
                    ),
                    center = Offset(x1, y1),
                    radius = radius1
                ),
                center = Offset(x1, y1),
                radius = radius1
            )

            // Layer 2: Secondary accent orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        secondaryColor.copy(alpha = 0.48f),
                        secondaryColor.copy(alpha = 0.16f),
                        Color.Transparent
                    ),
                    center = Offset(x2, y2),
                    radius = radius2
                ),
                center = Offset(x2, y2),
                radius = radius2
            )

            // Layer 3: Central harmonic blend orb
            val blendedColor = Color(
                red = (dominantColor.red + secondaryColor.red) * 0.5f,
                green = (dominantColor.green + secondaryColor.green) * 0.5f,
                blue = (dominantColor.blue + secondaryColor.blue) * 0.5f,
                alpha = 0.35f
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        blendedColor,
                        blendedColor.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(x3, y3),
                    radius = radius3
                ),
                center = Offset(x3, y3),
                radius = radius3
            )

            // Darkening vignette to maintain legibility
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.35f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.85f)
                    ),
                    startY = 0f,
                    endY = height
                )
            )
        }

        content()
    }
}

/**
 * Root theme for NexusStream.
 */
@Composable
fun NexusTheme(
    dominantColor: Color = Color(0xFF6C5CE7),
    secondaryColor: Color = Color(0xFFFF7675),
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
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

    val colorScheme = darkColorScheme(
        primary = dominantColor,
        secondary = secondaryColor,
        background = glassColors.backgroundDeep,
        surface = Color(0xFF141419),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = glassColors.textPrimary,
        onSurface = glassColors.textPrimary
    )

    CompositionLocalProvider(
        LocalGlassColors provides glassColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

object NexusThemeTokens {
    val glass: GlassColors
        @Composable
        @ReadOnlyComposable
        get() = LocalGlassColors.current
}
