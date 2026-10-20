package com.svetlio.audiofreedom

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class CassetteDeckUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun narrowDeckRendersAndPowerControlStillWorks() = checkDeck(320)
    @Test fun standardDeckRendersAndPowerControlStillWorks() = checkDeck(360)

    private fun checkDeck(width: Int) {
        val processing = mutableStateOf(true)
        compose.setContent {
            AudioFreedomTheme(ThemePreference.Light, AppTheme.CassetteDeck, controlSystemBars = false) {
                Box(Modifier.width(width.dp)) {
                    CassetteHero("Driver attached", true, "Root access available", processing.value,
                        true, "Bluetooth headphones with a long device name", 4, 7, -650,
                        listOf(-4500, -3000, -2000, -2800, -3200, -3800, -2900, -4000, -4800, -5500),
                        { processing.value = it })
                }
            }
        }
        compose.mainClock.advanceTimeBy(600)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir, "cassette-deck-$width.png").outputStream().use {
            compose.onNodeWithContentDescription("Cassette deck panel")
                .captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val power = compose.onNodeWithContentDescription("Processing")
        power.assertIsOn().performClick().assertIsOff()
        compose.onNodeWithContentDescription("Cassette deck, idle").assertExists()
    }

    @Test fun reelsAnimateOnlyWhenThereIsSignal() {
        val active = mutableStateOf(false)
        compose.setContent {
            Box(Modifier.width(320.dp)) { CassetteWindow(active.value) }
        }
        val idle = compose.onNodeWithContentDescription("Cassette deck, idle")
            .captureToImage().asAndroidBitmap()
        compose.runOnIdle { active.value = true }
        compose.mainClock.advanceTimeBy(1000)
        val moving = compose.onNodeWithContentDescription("Cassette deck, audio signal active")
            .captureToImage().asAndroidBitmap()
        assertFalse(idle.sameAs(moving))
        compose.runOnIdle { active.value = false }
        compose.onNodeWithContentDescription("Cassette deck, idle").assertExists()
    }
}
