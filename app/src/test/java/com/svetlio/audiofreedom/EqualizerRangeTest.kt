package com.svetlio.audiofreedom

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class EqualizerRangeTest {
    private fun bands(vararg gains: Int) = (gains.toList() + List(EqualizerBandCount) { 0 }).take(EqualizerBandCount)

    @Test fun defaultRangeIsTwelveDbAndLockedWithoutAcknowledgement() {
        val raw = AudioFreedomSettings(bandGainsMillibels = bands(1800, -1600, 1100))
        assertEquals(1200, raw.equalizerGainLimit)
        assertEquals(bands(1200, -1200, 1100), raw.withVisibleEffects(AppPreferences()).bandGainsMillibels)
        assertFalse(raw.withVisibleEffects(AppPreferences(extendedEqualizerRange = true)).extendedEqualizerRange)
    }

    @Test fun unlockDoesNotChangeExistingGainsOrOtherEffects() {
        val settings = AudioFreedomSettings(preampMillibels = -350, bandGainsMillibels = bands(1100, -450),
            dynamicBassEnabled = true, bassBoostMillibels = 1000, reverbEnabled = true)
        val extended = settings.withEqualizerRange(true)
        assertEquals(2000, extended.equalizerGainLimit)
        assertEquals(settings, extended.copy(extendedEqualizerRange = false))
    }

    @Test fun disablingClampsOnlyOutOfRangeBandsAndDoesNotRestoreThemOnReenable() {
        val extended = AudioFreedomSettings(extendedEqualizerRange = true,
            bandGainsMillibels = bands(1800, -1600, 1100, -1100, 1200, -1200, 2000, -2000))
        val standard = extended.withEqualizerRange(false)
        assertEquals(bands(1200, -1200, 1100, -1100, 1200, -1200, 1200, -1200), standard.bandGainsMillibels)
        assertEquals(standard.bandGainsMillibels, standard.withEqualizerRange(true).bandGainsMillibels)
    }

    @Test fun profilesAndSnapshotsCannotUnlockDevicePreference() {
        val extended = AudioFreedomSettings(extendedEqualizerRange = true, bandGainsMillibels = bands(2000, -2000, 1100))
        val restored = SettingsSnapshotCodec.decode(SettingsSnapshotCodec.encode(extended))!!
        assertEquals(extended, restored)
        assertEquals(bands(1200, -1200, 1100), restored.withVisibleEffects(AppPreferences()).bandGainsMillibels)
        assertFalse(restored.withVisibleEffects(AppPreferences()).extendedEqualizerRange)
        val profile = AudioFreedomProfile("loud", "Loud", extended)
        val result = AssistantCommandPlanner.plan("Load profile Loud", AudioFreedomSettings(), listOf(profile), 150)
            as AssistantPlanResult.Proposed
        assertFalse(result.proposal.settings.extendedEqualizerRange)
        assertEquals(bands(1200, -1200, 1100), result.proposal.settings.bandGainsMillibels)
    }

    @Test fun wireSerializationEnforcesTheSelectedRange() {
        for ((extended, expected) in listOf(false to 1200, true to 2000)) {
            val settings = AudioFreedomSettings(extendedEqualizerRange = extended, bandGainsMillibels = bands(5000, -5000, 1100))
            val data = ByteBuffer.wrap(AudioFreedomProtocol.equalizerConfiguration(settings)).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(expected, data.getShort(20).toInt())
            assertEquals(-expected, data.getShort(22).toInt())
            assertEquals(1100, data.getShort(24).toInt())
        }
    }

    @Test fun assistantRetainsSmallStepsButCanReachTheUnlockedRange() {
        val standard = AudioFreedomSettings(equalizerEnabled = true, bandGainsMillibels = bands(1150, 1150, 1150))
        val unlocked = standard.withEqualizerRange(true)
        val first = AssistantCommandPlanner.plan("Increase bass", standard, emptyList(), 150) as AssistantPlanResult.Proposed
        val second = AssistantCommandPlanner.plan("Increase bass", unlocked, emptyList(), 150) as AssistantPlanResult.Proposed
        assertTrue(first.proposal.settings.bandGainsMillibels.all { it <= 1200 })
        assertTrue(second.proposal.settings.bandGainsMillibels.any { it > 1200 })
        assertTrue(second.proposal.settings.bandGainsMillibels.zip(unlocked.bandGainsMillibels).all { (a, b) -> a - b <= 150 })
    }
}
