package com.svetlio.audiofreedom

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class EqRoutingUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bandwidthAndRoutingWorkAcrossThemes() {
        val theme = mutableStateOf(AppTheme.Classic)
        val settings = mutableStateOf(AudioFreedomSettings(equalizerEnabled = true,
            stereoRoutingEnabled = true, bandGainsMillibels = List(10) { if (it == 5) 600 else 0 }))
        val selected = mutableStateOf(5)
        compose.setContent {
            AudioFreedomTheme(ThemePreference.Dark, theme.value, controlSystemBars = false) {
                Surface {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        if (LocalLayoutStyle.current.modern) {
                            ModernEqualizerEditor(
                                bandGains = settings.value.bandGainsMillibels,
                                qValues = settings.value.bandQHundredths,
                                onQChange = { band, q -> settings.value = settings.value.copy(
                                    bandQHundredths = settings.value.bandQHundredths.toMutableList().also { it[band] = q }) },
                                onResetQ = { settings.value = settings.value.copy(bandQHundredths = List(10) { 140 }) },
                                preampMillibels = 0, enabled = true, selectedPreset = null,
                                onPresetSelected = {}, onBandChange = { _, _ -> }, onPreampChange = {}, onCommit = {})
                        } else {
                            EqualizerBandwidthControls(settings.value.bandQHundredths, selected.value, { selected.value = it }, true,
                                { band, q -> settings.value = settings.value.copy(
                                    bandQHundredths = settings.value.bandQHundredths.toMutableList().also { it[band] = q }) },
                                {}, { settings.value = settings.value.copy(bandQHundredths = List(10) { 140 }) })
                        }
                        StereoRoutingControls(settings.value, { settings.value = it }, {})
                    }
                }
            }
        }
        for (value in AppTheme.entries) {
            compose.runOnIdle { theme.value = value }
            compose.onNodeWithText("Q 1.40").performScrollTo().performClick()
            compose.onNode(hasSetTextAction()).performTextReplacement("2.50")
            compose.onNodeWithText("Apply").performClick()
            compose.runOnIdle { assertTrue(settings.value.bandQHundredths.contains(250)) }
            compose.onNodeWithContentDescription("Reset all bandwidths").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(List(10) { 140 }, settings.value.bandQHundredths) }
            compose.onNodeWithContentDescription("Select stereo routing mode").performScrollTo().performClick()
            compose.onNodeWithText("Single output").performClick()
            compose.onNodeWithText("Right", substring = false).performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(StereoRoutingMode.SingleOutput, settings.value.stereoRoutingMode)
                assertTrue(settings.value.stereoOutputRight)
            }
            compose.onNodeWithContentDescription("Select stereo routing mode").performScrollTo().performClick()
            compose.onNodeWithText("Normal", substring = false).performClick()
            if (value == AppTheme.Mocha) {
                compose.onNodeWithText("Q 1.40").performScrollTo()
                val i = InstrumentationRegistry.getInstrumentation()
                File(i.targetContext.cacheDir, "eq-routing-mocha.png").outputStream().use {
                    i.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
    }
}
