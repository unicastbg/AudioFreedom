package com.svetlio.audiofreedom

import android.media.AudioManager
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit physical-device acceptance test; skipped when no driver/music is present. */
class Los24RecoveryDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun await(message: String, timeout: Long = 15000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        fail("$message; health=${AudioFreedomService.currentHealth}; " +
            "metrics=${AudioFreedomService.currentOutputMetrics}; " +
            "storedPreamp=${AudioFreedomSettingsStore.load(context).preampMillibels}; " +
            "stream=${AudioFreedomService.currentStreamFrame}")
    }

    @Test fun repeatedTogglesRecoverWithoutRestartingThePlayerAndApplyLiveGain() {
        assumeTrue("A real driver is required", probeDriver() == DriverState.Detected)
        val audioManager = context.getSystemService(AudioManager::class.java)
        assumeTrue("Keep music playing during this acceptance test", audioManager.isMusicActive)
        val original = AudioFreedomSettingsStore.load(context)
        val wasEnabled = AudioFreedomService.isRequestedEnabled(context)
        try {
            context.startActivity(android.content.Intent(context, MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            Thread.sleep(1000)
            repeat(3) {
                instrumentation.runOnMainSync { AudioFreedomService.setEnabled(context, false) }
                await("Service did not release effects after turning processing off") {
                    AudioFreedomService.currentState != DriverState.Attached &&
                        AudioFreedomService.currentStreamFrame == null
                }
                Thread.sleep(1500)
                assertTrue("The music player must keep playing", audioManager.isMusicActive)
                instrumentation.runOnMainSync { AudioFreedomService.setEnabled(context, true) }
                await("No fresh PCM after toggle $it: ${AudioFreedomService.currentHealth}") {
                    AudioFreedomService.currentStreamFrame?.isFresh(SystemClock.elapsedRealtime()) == true &&
                        AudioFreedomService.currentHealth in setOf(ProcessingHealth.Global, ProcessingHealth.Sessions)
                }
                assertTrue(audioManager.isMusicActive)
            }
            val gainTest = original.copy(equalizerEnabled = true, preampMillibels = -1200,
                bandGainsMillibels = List(10) { 0 }, dynamicBassEnabled = false,
                detailRecoveryEnabled = false, immersiveFieldEnabled = false, reverbEnabled = false,
                limiterEnabled = false, volumeLevelerEnabled = false)
            instrumentation.runOnMainSync { AudioFreedomService.updateSettings(context, gainTest) }
            await("The -12 dB live change did not reach the active effect") {
                AudioFreedomService.currentOutputMetrics?.let {
                    it.inputPeakMillibels > -7000 && kotlin.math.abs(
                        it.inputPeakMillibels - it.outputPeakMillibels - 1200) <= 5
                } == true
            }
            instrumentation.runOnMainSync {
                AudioFreedomService.updateSettings(context, gainTest.copy(preampMillibels = -600))
            }
            await("The subsequent -6 dB live change did not reach the active effect") {
                AudioFreedomService.currentOutputMetrics?.let {
                    it.inputPeakMillibels > -7000 && kotlin.math.abs(
                        it.inputPeakMillibels - it.outputPeakMillibels - 600) <= 5
                } == true
            }
        } finally {
            instrumentation.runOnMainSync {
                AudioFreedomService.updateSettings(context, original)
                AudioFreedomService.setEnabled(context, wasEnabled)
            }
        }
    }
}
