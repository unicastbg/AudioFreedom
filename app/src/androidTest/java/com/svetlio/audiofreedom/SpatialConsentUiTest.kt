package com.svetlio.audiofreedom

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SpatialConsentUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun consentIsExplicitAndCancelDoesNotAuthorizeAnything() {
        val open = mutableStateOf(true)
        var approved = false
        compose.setContent { AudioFreedomTheme(ThemePreference.Light, controlSystemBars = false) {
            if (open.value) SpatialAudioConsentDialog({ approved = true; open.value = false }, { open.value = false })
        } }
        compose.onNodeWithText("Use stereo routing?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertFalse(approved); open.value = true }
        compose.onNodeWithText("Turn off spatial audio").performClick()
        compose.runOnIdle { assertTrue(approved) }
    }
}
