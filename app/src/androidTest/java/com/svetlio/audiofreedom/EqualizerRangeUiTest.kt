package com.svetlio.audiofreedom

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class EqualizerRangeUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun classicRangeUnlockAndClamp() = checkTheme(AppTheme.Classic)
    @Test fun mochaRangeUnlockAndClamp() = checkTheme(AppTheme.Mocha)
    @Test fun daylightRangeUnlockAndClamp() = checkTheme(AppTheme.Daylight)
    @Test fun studioRangeUnlockAndClamp() = checkTheme(AppTheme.Studio)

    private fun checkTheme(theme: AppTheme) {
        val preferences = mutableStateOf(AppPreferences())
        val settings = mutableStateOf(AudioFreedomSettings(equalizerEnabled = true,
            preampMillibels = -350, bandGainsMillibels = listOf(1100, -1100) + List(8) { 0 }))
        compose.setContent {
            AudioFreedomTheme(ThemePreference.Light, theme, controlSystemBars = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    EqualizerRangeControl(preferences.value) { updated ->
                        preferences.value = updated
                        settings.value = settings.value.withEqualizerRange(updated.extendedEqualizerRange)
                    }
                    if (theme == AppTheme.Classic) {
                        EqualizerBandLabels.forEachIndexed { index, label ->
                            EqualizerBandRow(label, settings.value.bandGainsMillibels[index],
                                settings.value.equalizerGainLimit, true,
                                { gain -> settings.value = settings.value.copy(bandGainsMillibels =
                                    settings.value.bandGainsMillibels.mapIndexed { band, old -> if (band == index) gain else old }) }, {})
                        }
                    } else {
                        ModernEqualizerEditor(bandGains = settings.value.bandGainsMillibels,
                            preampMillibels = settings.value.preampMillibels, enabled = true,
                            gainLimit = settings.value.equalizerGainLimit, selectedPreset = null,
                            onPresetSelected = {}, onBandChange = { index, gain ->
                                settings.value = settings.value.copy(bandGainsMillibels =
                                    settings.value.bandGainsMillibels.mapIndexed { band, old -> if (band == index) gain else old })
                            }, onPreampChange = {}, onCommit = {})
                    }
                }
                }
            }
        }
        val toggle = compose.onNodeWithContentDescription("Extended EQ range")
        toggle.assertIsOff().performClick()
        compose.onNodeWithText("Unlock extended EQ range?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        toggle.assertIsOff().performClick()
        compose.onNodeWithText("Unlock").performClick()
        toggle.assertIsOn()
        compose.runOnIdle {
            assertTrue(preferences.value.extendedEqualizerRangeAcknowledged)
            assertEquals(1100, settings.value.bandGainsMillibels[0])
        }
        if (theme != AppTheme.Classic) {
            for ((name, gain) in listOf("125 Hz band" to 1800F, "250 Hz band" to -1600F)) {
                val fader = compose.onNodeWithContentDescription(name).performScrollTo()
                assertEquals(-2000F..2000F, fader.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].range)
                fader.performSemanticsAction(SemanticsActions.SetProgress) { it(gain) }
            }
        } else {
            // The standard theme uses Material sliders; their range is checked independently.
            val sliders = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).fetchSemanticsNodes()
            assertEquals(10, sliders.size)
            sliders.forEach {
                assertEquals(-2000F..2000F, it.config[SemanticsProperties.ProgressBarRangeInfo].range)
            }
            compose.runOnIdle {
                settings.value = settings.value.copy(bandGainsMillibels =
                    listOf(1100, -1100, 1800, -1600) + List(6) { 0 })
            }
        }
        toggle.performScrollTo()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir, "eq-range-${theme.name.lowercase()}.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        toggle.performClick().assertIsOff()
        compose.runOnIdle {
            assertEquals(listOf(1100, -1100, 1200, -1200), settings.value.bandGainsMillibels.take(4))
            assertEquals(-350, settings.value.preampMillibels)
        }
        toggle.performClick().assertIsOn()
        compose.onNodeWithText("Unlock extended EQ range?").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1200, settings.value.bandGainsMillibels[2]) }
    }
}
