package com.svetlio.audiofreedom

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class SettingsReleaseNotesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tappingInstalledAppNameReopensTheSameReleaseNotes() {
        compose.setContent { AudioFreedomTheme(ThemePreference.Light, controlSystemBars = false) {
            SettingsScreen(
                route = AudioRoute("speaker", "Phone speaker", AudioRouteKind.PhoneSpeaker),
                profiles = emptyList(), preferences = AppPreferences(), assignedProfileId = null,
                onPreferencesChanged = {}, onProfileAssigned = {},
                onOpenAssistantSettings = {}, onOpenMainPageSettings = {})
        } }
        compose.onNodeWithText("AudioFreedom ${BuildConfig.VERSION_NAME}").performScrollTo().performClick()
        compose.onNodeWithText("What's new in ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
        installedReleaseHighlights(BuildConfig.VERSION_NAME).forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.onNodeWithText("Got it").performClick()
        compose.onNodeWithText("AudioFreedom ${BuildConfig.VERSION_NAME}").performScrollTo().performClick()
        compose.onNodeWithText("What's new in ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
    }
}
