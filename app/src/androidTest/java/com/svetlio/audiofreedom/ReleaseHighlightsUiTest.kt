package com.svetlio.audiofreedom

import android.content.ContextWrapper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ReleaseHighlightsUiTest {
    @get:Rule val compose = createComposeRule()
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val context = object : ContextWrapper(target) {
        override fun getSharedPreferences(name: String, mode: Int) =
            super.getSharedPreferences(if (name == "release_highlights") "test_release_highlights" else name, mode)
    }
    @Before fun before() { context.getSharedPreferences("release_highlights", 0).edit().clear().commit() }
    @After fun after() { context.getSharedPreferences("release_highlights", 0).edit().clear().commit() }

    @Test fun firstOpenShowsThreeHighlightsAndAcknowledgementPersists() {
        compose.setContent { CompositionLocalProvider(LocalContext provides context) {
            AudioFreedomTheme(ThemePreference.Light, controlSystemBars = false) { ReleaseHighlightsNotice() }
        } }
        compose.onNodeWithText("What's new in 1.0.0").assertIsDisplayed()
        VersionOneHighlights.forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithText("Got it").performClick()
        compose.onNodeWithText("What's new in 1.0.0").assertDoesNotExist()
        org.junit.Assert.assertEquals(BuildConfig.VERSION_CODE,
            context.getSharedPreferences("release_highlights", 0).getInt("seen_version", 0))
    }
}
