package com.nexusstream.app

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexusstream.app.ui.components.OriginalEmptyState
import com.nexusstream.app.ui.components.OriginalFilterChips
import com.nexusstream.app.ui.theme.DarkPalette
import com.nexusstream.app.ui.theme.LightPalette
import com.nexusstream.app.ui.theme.ThemeState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented UI tests for shared components. They need a device or emulator and are not part
 * of the CI build: run them with `./gradlew connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class ComponentsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun filterChipsReportTheTappedLabel() {
        var selected by mutableStateOf("All")
        compose.setContent {
            OriginalFilterChips(
                labels = listOf("All", "Music", "News"),
                selected = selected,
                onSelect = { selected = it },
                unselectedColor = Color.DarkGray
            )
        }
        compose.onNodeWithText("Music").performClick()
        assertEquals("Music", selected)
    }

    @Test
    fun emptyStateShowsItsMessage() {
        compose.setContent { OriginalEmptyState(title = "Nothing here", message = "Try again later") }
        compose.onNodeWithText("Nothing here").assertIsDisplayed()
        compose.onNodeWithText("Try again later").assertIsDisplayed()
    }

    @Test
    fun themeStateSwitchesThePalette() {
        compose.setContent {
            var light by remember { mutableStateOf(false) }
            ThemeState.palette = if (light) LightPalette else DarkPalette
            Text("palette", color = ThemeState.palette.text)
            // Flip once so the test also covers recomposition after a theme change.
            if (!light) light = true
        }
        compose.waitForIdle()
        assertEquals(LightPalette, ThemeState.palette)
    }
}
