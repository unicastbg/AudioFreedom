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
    private val profiles = (0 until 80).map {
        AudioFreedomProfile("$it", "Sound $it", AudioFreedomSettings(), if (it % 2 == 0) "Sony Amp" else "Buds")
    }

    @Test fun pickerRemainsUsableAcrossAllThemes() {
        val theme = mutableStateOf(AppTheme.Classic)
        compose.setContent { AudioFreedomTheme(ThemePreference.Dark, theme.value, controlSystemBars = false) {
            ProfileBrowserRow(profiles, "0", false, {}, {}, {}, {})
        } }
        for (value in AppTheme.entries) {
            compose.runOnIdle { theme.value = value }
            compose.onNodeWithContentDescription("Choose profile").assertIsDisplayed().performClick()
            compose.onNodeWithText("Search profiles or groups").performTextInput("79")
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

    @Test fun searchSelectAndDeviceDefaultWorkWithManyProfiles() {
        val selected = mutableStateOf("0")
        val assigned = mutableStateOf<String?>(null)
        compose.setContent { AudioFreedomTheme(ThemePreference.System, controlSystemBars = false) {
            ProfileBrowserRow(profiles, selected.value, false, { selected.value = it.id }, {}, {}, {},
                "Sony Amp", assigned.value, { assigned.value = it })
        } }
        compose.onNodeWithContentDescription("Choose profile").performClick()
        compose.onNodeWithText("Search profiles or groups").performTextInput("79")
        compose.onNodeWithText("Sound 79").assertIsDisplayed()
        compose.onNodeWithText("Sound 78").assertDoesNotExist()
        compose.onNodeWithContentDescription("Set as device default").performClick()
        compose.runOnIdle { assertEquals("79", assigned.value); assertEquals("0", selected.value) }
        compose.onNodeWithContentDescription("Remove device default").assertIsDisplayed()
        compose.onNodeWithText("Sound 79").performClick()
        compose.runOnIdle { assertEquals("79", selected.value) }
    }

    @Test fun unsavedChangesCanBeKeptOrExplicitlyDiscarded() {
        var selected: String? = "0"
        compose.setContent { AudioFreedomTheme(ThemePreference.System, controlSystemBars = false) {
            ProfileBrowserRow(profiles, "0", true, { selected = it.id }, {}, {}, {})
        } }
        fun choose() {
            compose.onNodeWithContentDescription("Choose profile").performClick()
            compose.onNodeWithText("Search profiles or groups").performTextInput("79")
            compose.onNodeWithText("Sound 79").performClick()
        }
        choose()
        compose.onNodeWithText("Keep editing").performClick()
        compose.runOnIdle { assertEquals("0", selected) }
        choose()
        compose.onNodeWithText("Switch").performClick()
        compose.runOnIdle { assertEquals("79", selected) }
    }
}
