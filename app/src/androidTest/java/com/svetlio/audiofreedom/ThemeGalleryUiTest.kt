package com.svetlio.audiofreedom

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ThemeGalleryUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun everyThemeHasAPreviewAndCanBeAppliedWithoutLeavingTheGallery() {
        val preferences = mutableStateOf(AppPreferences())
        compose.setContent {
            AudioFreedomTheme(preferences.value.theme, preferences.value.appTheme, controlSystemBars = false) {
                ThemeGalleryScreen(preferences.value, { preferences.value = it })
            }
        }
        val gallery = compose.onNodeWithTag("theme-gallery")
        val modeControls = compose.onNodeWithTag("theme-mode-controls")
        val modeBounds = modeControls.fetchSemanticsNode().boundsInRoot
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir, "theme-gallery-first.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        for (theme in AppTheme.entries) {
            val description = "Apply ${theme.label} theme"
            gallery.performScrollToNode(hasContentDescription(description))
            compose.onNodeWithContentDescription(description).performClick().assertIsSelected()
            compose.runOnIdle { assertEquals(theme, preferences.value.appTheme) }
            modeControls.assertIsDisplayed()
            assertEquals(modeBounds, modeControls.fetchSemanticsNode().boundsInRoot)
        }
        File(context.cacheDir, "theme-gallery-last.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        gallery.performScrollToNode(hasContentDescription("Apply Mocha theme"))
        compose.onNodeWithContentDescription("Apply Mocha theme").performClick()
        compose.onNodeWithText("Dark").assertIsDisplayed().performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(ThemePreference.Dark, preferences.value.theme) }
        compose.onNodeWithText("Light").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(ThemePreference.Light, preferences.value.theme) }
    }
}
