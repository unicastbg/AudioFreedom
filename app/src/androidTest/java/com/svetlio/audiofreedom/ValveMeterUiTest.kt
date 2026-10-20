package com.svetlio.audiofreedom

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class ValveMeterUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun quietAudioMovesTheNeedleAndLabelsFit() {
        val rms = mutableStateOf<Int?>(null)
        compose.setContent {
            AudioFreedomTheme(ThemePreference.Dark, AppTheme.Valve, controlSystemBars = false) {
                Surface {
                    Column(Modifier.padding(16.dp)) { VuMeter(rms.value, -2000) }
                }
            }
        }
        compose.mainClock.advanceTimeBy(2000)
        val idle = compose.onRoot().captureToImage().asAndroidBitmap()
        compose.runOnIdle { rms.value = -4300 }
        compose.mainClock.advanceTimeBy(2000)
        val quiet = compose.onRoot().captureToImage().asAndroidBitmap()
        assertFalse("Quiet audio should move the needle above the new floor", idle.sameAs(quiet))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir, "valve-meter-quiet.png").outputStream().use {
            quiet.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
