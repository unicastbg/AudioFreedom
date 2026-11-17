package com.svetlio.audiofreedom

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProfileBrowserUiTest {
    @get:Rule val compose = createComposeRule()
    private val route = AudioRoute("profile-test-sony", "Sony Amp", AudioRouteKind.Bluetooth)
    private val profiles = (0 until 80).map {
        AudioFreedomProfile("$it", "Sound $it", AudioFreedomSettings(),
            deviceId = if (it % 2 == 0) route.id else "profile-test-buds",
            deviceLabel = if (it % 2 == 0) route.label else "Buds")
    }

    @Test fun pickerRemainsUsableAcrossAllThemes() {
        val theme = mutableStateOf(AppTheme.Classic)
        compose.setContent { AudioFreedomTheme(ThemePreference.Dark, theme.value, controlSystemBars = false) {
            DeviceProfilesPanel(profiles, AudioFreedomSettings(), "0", route, null, {}, {})
        } }
        for (value in AppTheme.entries) {
            compose.runOnIdle { theme.value = value }
            openPicker()
            compose.onNodeWithText("Search profiles or devices").performTextInput("79")
            compose.onNodeWithText("Sound 79").assertIsDisplayed()
            if (value == AppTheme.Mocha) {
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                File(instrumentation.targetContext.cacheDir, "profile-browser.png").outputStream().use {
                    instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            compose.onNodeWithText("Sound 79").performClick()
        }
    }

    @Test fun searchSelectAndRememberedLabelWorkWithManyProfiles() {
        val selected = mutableStateOf("0")
        val assigned = mutableStateOf("0")
        compose.setContent { AudioFreedomTheme(ThemePreference.System, controlSystemBars = false) {
            DeviceProfilesPanel(profiles, AudioFreedomSettings(), selected.value, route, assigned.value,
                { selected.value = it.id; assigned.value = it.id }, {})
        } }
        openPicker()
        compose.onNodeWithText("Comes back on Sony Amp", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Search profiles or devices").performTextInput("79")
        compose.onNodeWithText("Sound 79").assertIsDisplayed()
        compose.onNodeWithText("Sound 78").assertIsNotDisplayed()
        compose.onNodeWithText("Sound 79").performClick()
        compose.runOnIdle { assertEquals("79", selected.value); assertEquals("79", assigned.value) }
    }

    @Test fun unsavedChangesCanBeKeptOrExplicitlyDiscarded() {
        var selected: String? = "0"
        compose.setContent { AudioFreedomTheme(ThemePreference.System, controlSystemBars = false) {
            DeviceProfilesPanel(profiles, AudioFreedomSettings(preampMillibels = -300), "0", route, null,
                { selected = it.id }, {})
        } }
        fun choose() {
            openPicker()
            compose.onNodeWithText("Search profiles or devices").performTextInput("79")
            compose.onNodeWithText("Sound 79").performClick()
        }
        choose()
        compose.onNodeWithText("Keep editing").performClick()
        compose.runOnIdle { assertEquals("0", selected) }
        choose()
        compose.onNodeWithText("Switch").performClick()
        compose.runOnIdle { assertEquals("79", selected) }
    }

    @Test fun deviceGroupCanBeRenamedAndReset() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val device = route.copy(id = "profile-test-alias", label = "Test amplifier")
        AppPreferencesStore.setOutputGroupName(context, device.id, "")
        try {
            val profile = profiles.first().copy(deviceId = device.id, deviceLabel = device.label)
            compose.setContent {
                DeviceProfilesPanel(listOf(profile), profile.settings, profile.id, device, profile.id, {}, {})
            }
            openPicker()
            compose.onNodeWithContentDescription("Rename group: Test amplifier").performClick()
            compose.onNodeWithText("Group name").performTextReplacement("Home HiFi")
            compose.onNodeWithText("Save").performClick()
            compose.onNodeWithContentDescription("Rename group: Home HiFi").assertIsDisplayed()
            compose.onNodeWithText("Comes back on Home HiFi", substring = true).assertIsDisplayed()
            assertEquals("Home HiFi", AppPreferencesStore.outputGroupNames(context)[device.id])
            compose.onNodeWithContentDescription("Rename group: Home HiFi").performClick()
            compose.onNodeWithText("Use device name").performClick()
            compose.onNodeWithContentDescription("Rename group: Test amplifier").assertIsDisplayed()
            assertNull(AppPreferencesStore.outputGroupNames(context)[device.id])
        } finally {
            AppPreferencesStore.setOutputGroupName(context, device.id, "")
        }
    }

    private fun openPicker() {
        compose.onNode(hasContentDescription("All profiles") and hasClickAction())
            .assertIsDisplayed().performClick()
    }
}
