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

// YouTube's own colours: #0F0F0F / #FFFFFF grounds, #272727 / #F2F2F2 chips, #F1F1F1 / #0F0F0F text,
// #AAAAAA / #606060 secondary text and its link blue. YouTube Music is always dark, on #030303.
val DarkPalette = AppPalette(
    isDark = true,
    background = Color(0xFF0F0F0F),
    musicBackground = Color(0xFF030303),
    chip = Color(0xFF272727),
    musicChip = Color(0xFF212121),
    surface = Color(0xFF212121),
    divider = Color(0xFF3F3F3F),
    text = Color(0xFFF1F1F1),
    textSecondary = Color(0xFFAAAAAA),
    onInverse = Color(0xFF0F0F0F),
    accent = Color(0xFF3EA6FF),
    danger = Color(0xFFFF4E45),
    glassTint = Color(0xFF0F0F0F).copy(alpha = 0.62f),
    glassFallback = Color(0xFF181818).copy(alpha = 0.95f),
    glassRim = Color.White.copy(alpha = 0.14f),
    glassHighlight = Color.White.copy(alpha = 0.06f)
)

val LightPalette = AppPalette(
    isDark = false,
    background = Color(0xFFFFFFFF),
    // YouTube Music has no light theme: this is only used if something reads it on the light side.
    musicBackground = Color(0xFF030303),
    chip = Color(0xFFF2F2F2),
    musicChip = Color(0xFF212121),
    surface = Color(0xFFF2F2F2),
    divider = Color(0xFFE5E5E5),
    text = Color(0xFF0F0F0F),
    textSecondary = Color(0xFF606060),
    onInverse = Color(0xFFFFFFFF),
    accent = Color(0xFF065FD4),
    danger = Color(0xFFCC0000),
    glassTint = Color(0xFFFFFFFF).copy(alpha = 0.66f),
    glassFallback = Color(0xFFFFFFFF).copy(alpha = 0.96f),
    glassRim = Color.White.copy(alpha = 0.85f),
    glassHighlight = Color.White.copy(alpha = 0.45f)
)

/** The palette in use. Compose state, so anything that reads a colour recomposes on a theme change. */
object ThemeState {
    var palette: AppPalette by mutableStateOf(DarkPalette)
    /** Sheets and dialogs can blur the app behind them (Android 12+ with cross-window blur on). */
    var windowBlur: Boolean by mutableStateOf(false)
}

/** YouTube follows the theme setting; YouTube Music is always dark, like the real app ([musicSide]). */
fun paletteFor(mode: ThemeMode, systemDark: Boolean, musicSide: Boolean = false): AppPalette = if (musicSide) DarkPalette else when (mode) {
    ThemeMode.LIGHT -> LightPalette
    ThemeMode.DARK -> DarkPalette
    ThemeMode.SYSTEM -> if (systemDark) DarkPalette else LightPalette
}
