package com.svetlio.audiofreedom

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class DspStreamDisplayUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun everyHeroShowsExplicitDspFormatAndClearsStaleRates() {
        val theme = mutableStateOf(AppTheme.Mocha)
        val label = mutableStateOf<String?>("DSP: 44.1 kHz · 32-bit float · 2 ch")
        compose.setContent { AudioFreedomTheme(ThemePreference.Dark, theme.value, controlSystemBars = false) {
            ProcessingHeroCard(DriverState.Attached, RootAccessState.NotChecked, true, true,
                "Headphones", 4, 7, -1800, 0, null, true, {}, streamLabel = label.value)
        } }
        for (value in AppTheme.entries.filter { it != AppTheme.Classic }) {
            compose.runOnIdle { theme.value = value; label.value = "DSP: 44.1 kHz · 32-bit float · 2 ch" }
            compose.onNodeWithText("DSP: 44.1 kHz · 32-bit float · 2 ch").assertIsDisplayed()
            compose.runOnIdle { label.value = null }
            compose.onNodeWithText("DSP: 44.1 kHz · 32-bit float · 2 ch").assertDoesNotExist()
            compose.onNodeWithText("DSP format unavailable / idle").assertIsDisplayed()
        }
    }
}
