package com.nexusstream.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.nexusstream.app.data.ThemeMode

/** The app's surface and text colours for one theme. */
@Immutable
class AppPalette(
    val isDark: Boolean,
    val background: Color,
    val musicBackground: Color,
    val chip: Color,
    val musicChip: Color,
    /** Mini player, sheets and dialogs. */
    val surface: Color,
    val divider: Color,
    val text: Color,
    val textSecondary: Color,
    /** Text on a filled button drawn in [text] colour (white pill with black text, and inverted). */
    val onInverse: Color,
    /** The one accent: links, "new" dots and dialog actions. */
    val accent: Color,
    /** Errors and failed checks. */
    val danger: Color,
    /** Tint laid over the blurred backdrop of glass surfaces. */
    val glassTint: Color,
    /** Glass without blur (Android 11 and older): nearly opaque so text stays readable. */
    val glassFallback: Color,
    /** The bright edge of glass where light catches it. */
    val glassRim: Color,
    /** The faint top sheen of glass. */
    val glassHighlight: Color
)

// Minimalist palettes: neutral greys with a faint cool cast, one muted accent, and colour left to
// the thumbnails and artwork.
val DarkPalette = AppPalette(
    isDark = true,
    background = Color(0xFF0B0B0D),
    musicBackground = Color(0xFF060607),
    chip = Color(0xFF1C1C1F),
    musicChip = Color(0xFF19191C),
    surface = Color(0xFF17171A),
    divider = Color(0xFF26262A),
    text = Color(0xFFF2F2F4),
    textSecondary = Color(0xFF8E8E95),
    onInverse = Color(0xFF0B0B0D),
    accent = Color(0xFF9DB4FF),
    danger = Color(0xFFFF8A80),
    glassTint = Color(0xFF16161A).copy(alpha = 0.58f),
    glassFallback = Color(0xFF18181C).copy(alpha = 0.94f),
    glassRim = Color.White.copy(alpha = 0.16f),
    glassHighlight = Color.White.copy(alpha = 0.07f)
)

val LightPalette = AppPalette(
    isDark = false,
    background = Color(0xFFFAFAFB),
    musicBackground = Color(0xFFF6F6F7),
    chip = Color(0xFFEEEEF0),
    musicChip = Color(0xFFECECEE),
    surface = Color(0xFFF3F3F5),
    divider = Color(0xFFE3E3E6),
    text = Color(0xFF111114),
    textSecondary = Color(0xFF6C6C72),
    onInverse = Color(0xFFFFFFFF),
    accent = Color(0xFF3A57C9),
    danger = Color(0xFFD23F3A),
    glassTint = Color(0xFFFFFFFF).copy(alpha = 0.62f),
    glassFallback = Color(0xFFFCFCFD).copy(alpha = 0.96f),
    glassRim = Color.White.copy(alpha = 0.85f),
    glassHighlight = Color.White.copy(alpha = 0.45f)
)

/** The palette in use. Compose state, so anything that reads a colour recomposes on a theme change. */
object ThemeState {
    var palette: AppPalette by mutableStateOf(DarkPalette)
    /** Sheets and dialogs can blur the app behind them (Android 12+ with cross-window blur on). */
    var windowBlur: Boolean by mutableStateOf(false)
}

fun paletteFor(mode: ThemeMode, systemDark: Boolean): AppPalette = when (mode) {
    ThemeMode.LIGHT -> LightPalette
    ThemeMode.DARK -> DarkPalette
    ThemeMode.SYSTEM -> if (systemDark) DarkPalette else LightPalette
}
