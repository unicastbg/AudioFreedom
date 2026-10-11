package com.svetlio.audiofreedom

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeLevelerTest {
    @Test
    fun oldSettingsDefaultToLevelerOff() {
        assertFalse(AudioFreedomSettings().volumeLevelerEnabled)
    }

    @Test
    fun presetsPreserveOtherEffectsAndProtectionPreferences() {
        val before = AudioFreedomSettings(equalizerEnabled = true, preampMillibels = -400,
            reverbEnabled = true, limiterEnabled = false, limiterThresholdMillibels = -300)
        for (preset in VolumeLevelerPreset.entries) {
            val after = preset.applyTo(before)
            assertTrue(after.volumeLevelerEnabled)
            assertEquals(preset, VolumeLevelerPreset.matching(after))
            assertEquals(before, after.copy(volumeLevelerEnabled = before.volumeLevelerEnabled,
                levelerTargetMillibels = before.levelerTargetMillibels,
                levelerStrengthPercent = before.levelerStrengthPercent,
                levelerMaximumBoostMillibels = before.levelerMaximumBoostMillibels,
                levelerResponseMilliseconds = before.levelerResponseMilliseconds))
        }
    }

    @Test
    fun eqPresetPreservesLevelerConfiguration() {
        val before = VolumeLevelerPreset.Night.applyTo(AudioFreedomSettings())
        val after = EqualizerPreset.DeepBass.applyTo(before)
        assertTrue(after.volumeLevelerEnabled)
        assertEquals(VolumeLevelerPreset.Night, VolumeLevelerPreset.matching(after))
    }

    @Test
    fun configurationHasExpectedWireLayout() {
        val before = VolumeLevelerPreset.Night.applyTo(AudioFreedomSettings())
        val bytes = AudioFreedomProtocol.volumeLevelerConfiguration(before)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(40, bytes.size)
        assertEquals(8, buffer.getShort(6).toInt())
        assertEquals(0x1700, buffer.getInt(8))
        assertEquals(20, buffer.getInt(12))
        assertEquals(1, bytes[16].toInt())
        assertEquals(-1800, buffer.getInt(20))
        assertEquals(100, buffer.getInt(24))
        assertEquals(1200, buffer.getInt(28))
        assertEquals(500, buffer.getInt(32))
    }

    @Test
    fun voiceToggleOnlyChangesLevelerEnabled() {
        val before = AudioFreedomSettings(preampMillibels = -400)
        val result = AssistantCommandPlanner.plan("Turn on volume leveler", before, emptyList(), 150)
        assertTrue(result is AssistantPlanResult.Proposed)
        val proposal = (result as AssistantPlanResult.Proposed).proposal
        assertEquals(before.copy(volumeLevelerEnabled = true), proposal.settings)
        assertTrue(proposal.changes.any { it.contains("Volume leveler") })
        assertEquals(AssistantConversationControl.Help(AssistantHelpTopic.Leveler),
            AssistantConversationCommands.recognize("Volume leveler help"))
    }
}
