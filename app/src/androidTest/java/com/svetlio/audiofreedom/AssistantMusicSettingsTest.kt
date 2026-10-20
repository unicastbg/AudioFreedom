package com.svetlio.audiofreedom

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AssistantMusicSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun musicRepliesCanBeChangedInVolumeButtonMode() {
        checkMusicSettings(AssistantVoiceMode.VolumeButton)
    }

    @Test fun musicRepliesCanBeChangedInPushToTalkMode() {
        checkMusicSettings(AssistantVoiceMode.PushToTalk)
    }

    private fun checkMusicSettings(mode: AssistantVoiceMode) {
        val preferences = mutableStateOf(AppPreferences(
            assistantVoiceMode = mode,
            assistantForwardMusicCommands = true,
            assistantSpeakVoiceConfirmation = true,
        ))
        compose.setContent {
            AudioFreedomTheme(ThemePreference.Light, controlSystemBars = false) {
                AssistantSettingsScreen(
                    preferences = preferences.value,
                    languageModelInfo = AssistantModelPackInfo(installed = false),
                    voiceModelInfo = AssistantModelPackInfo(installed = true),
                    activeModelPack = null,
                    modelDownloadProgress = null,
                    modelMessage = null,
                    voiceTestMessage = null,
                    voiceTestRunning = false,
                    widgetMessage = null,
                    onPreferencesChanged = { preferences.value = it },
                    onAddVoiceWidget = {},
                    onInstallLanguageModel = {},
                    onImportLanguageModel = {},
                    onRemoveLanguageModel = {},
                    onInstallVoiceModel = {},
                    onRemoveVoiceModel = {},
                    onTestVoice = {},
                )
            }
        }
        compose.onNodeWithText("Pass music commands to Media Player")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Detailed music replies")
            .performScrollTo().assertIsDisplayed()
        val replySwitch = compose.onNodeWithContentDescription("Detailed music replies")
        replySwitch.performScrollTo().assertIsDisplayed().assertIsOff().performClick()
        replySwitch.assertIsOn()
        compose.runOnIdle { assertTrue(preferences.value.assistantDetailedMusicReplies) }
    }
}
