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
    val onInverse: Color
)

val DarkPalette = AppPalette(
    isDark = true,
    background = Color(0xFF0F0F0F),
    musicBackground = Color(0xFF030303),
    chip = Color(0xFF272727),
    musicChip = Color(0xFF1F1F1F),
    surface = Color(0xFF212121),
    divider = Color(0xFF303030),
    text = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFAAAAAA),
    onInverse = Color(0xFF000000)
)

val LightPalette = AppPalette(
    isDark = false,
    background = Color(0xFFFFFFFF),
    musicBackground = Color(0xFFFFFFFF),
    chip = Color(0xFFF2F2F2),
    musicChip = Color(0xFFF2F2F2),
    surface = Color(0xFFF2F2F2),
    divider = Color(0xFFE5E5E5),
    text = Color(0xFF0F0F0F),
    textSecondary = Color(0xFF606060),
    onInverse = Color(0xFFFFFFFF)
)

/** The palette in use. Compose state, so anything that reads a colour recomposes on a theme change. */
object ThemeState {
    var palette: AppPalette by mutableStateOf(DarkPalette)
}

fun paletteFor(mode: ThemeMode, systemDark: Boolean): AppPalette = when (mode) {
    ThemeMode.LIGHT -> LightPalette
    ThemeMode.DARK -> DarkPalette
    ThemeMode.SYSTEM -> if (systemDark) DarkPalette else LightPalette
}
