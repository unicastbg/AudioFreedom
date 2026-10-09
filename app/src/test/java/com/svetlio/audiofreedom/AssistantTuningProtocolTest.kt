package com.svetlio.audiofreedom

import org.junit.Assert.*
import org.junit.Test

class AssistantTuningProtocolTest {
    private fun request(command: String = "Clearer vocals but keep bass unchanged") = AssistantRequest(
        command, AudioFreedomSettings(equalizerEnabled = true), emptyList(), 150)
    private fun output(goals: String, locks: String = "\"bass\"") = """{"goals":[$goals],"preserve":[$locks]}"""

    @Test fun preservedBassIsBitForBitUnchanged() {
        val result = AssistantTuningProtocol.parse(output("""{"target":"vocal_presence","amount":0.5}"""), request()) as AssistantPlanResult.Proposed
        assertEquals(listOf(0, 0, 0), result.proposal.settings.bandGainsMillibels.take(3))
        assertEquals(600, result.proposal.settings.bassBoostMillibels)
        assertEquals(0, result.proposal.settings.preampMillibels)
        assertFalse(result.proposal.canApplyAutomatically)
    }
    @Test fun omittedHumanConstraintIsRejected() {
        assertTrue(AssistantTuningProtocol.parse(output("""{"target":"bass_weight","amount":0.5}""", ""), request()) is AssistantPlanResult.NotUnderstood)
    }
    @Test fun conflictingGoalAndPreserveAreRejected() {
        assertTrue(AssistantTuningProtocol.parse(output("""{"target":"bass_weight","amount":0.5}"""), request()) is AssistantPlanResult.NotUnderstood)
    }
    @Test fun halfUndoRequiresVerifiedHistory() {
        val json = output("""{"target":"undo_half_bass","amount":0.5}""", "")
        assertTrue(AssistantTuningProtocol.parse(json, request("Undo half of bass")) is AssistantPlanResult.NotUnderstood)
        val current = AudioFreedomSettings(equalizerEnabled = true, bandGainsMillibels = listOf(150, 150, 100, 0, 0, 0, 0, 0, 0, 0), bassBoostMillibels = 750)
        val result = AssistantTuningProtocol.parse(json, request("Undo half of bass").copy(current = current,
            previous = AudioFreedomSettings())) as AssistantPlanResult.Proposed
        assertEquals(75, result.proposal.settings.bandGainsMillibels[0])
        assertEquals(675, result.proposal.settings.bassBoostMillibels)
    }
    @Test fun excessiveAmountAndUnknownGoalsAreRejected() {
        for (goal in listOf("""{"target":"bass_weight","amount":25}""", """{"target":"hardware_distortion","amount":0.5}""")) {
            assertTrue(AssistantTuningProtocol.parse(output(goal, ""), request("More bass")) is AssistantPlanResult.NotUnderstood)
        }
    }
    @Test fun confidenceScoreIsNotAnAuthorization() {
        assertTrue(AssistantTuningProtocol.parse("""{"goals":[{"target":"bass_weight","amount":0.5}],"preserve":[],"confidence":0.99}""",
            request("More bass")) is AssistantPlanResult.NotUnderstood)
    }
    @Test fun englishAndBulgarianPreservationAreDetected() {
        assertTrue("bass" in AssistantTuningProtocol.explicitLocks("Keep bass unchanged"))
        assertTrue("bass" in AssistantTuningProtocol.explicitLocks("Изчисти вокалите, но запази баса"))
        assertTrue("detail" in AssistantTuningProtocol.explicitLocks("Намали остротата, без да загубя детайла"))
        assertTrue("bass" in AssistantTuningProtocol.explicitLocks("Do not increase bass"))
    }
    @Test fun detailAndStereoLocksSurviveOtherGoals() {
        val before = AudioFreedomSettings(equalizerEnabled = true, detailAmountPercent = 66,
            immersiveFieldEnabled = true, immersiveWidthPercent = 71)
        val json = output("""{"target":"harshness","amount":-0.5}""", "\"detail\",\"stereo\"")
        val result = AssistantTuningProtocol.parse(json, request("Softer highs").copy(current = before)) as AssistantPlanResult.Proposed
        assertEquals(66, result.proposal.settings.detailAmountPercent)
        assertEquals(71, result.proposal.settings.immersiveWidthPercent)
        assertTrue(result.proposal.settings.bandGainsMillibels[7] < 0)
    }
    @Test fun bassPunchIsNotJustMoreSubBass() {
        val result = AssistantTuningProtocol.parse(output("""{"target":"bass_punch","amount":0.5}""", ""), request("More punch")) as AssistantPlanResult.Proposed
        assertTrue(result.proposal.settings.bandGainsMillibels[0] < 0)
        assertTrue(result.proposal.settings.bandGainsMillibels[1] > 0)
        assertEquals(600, result.proposal.settings.bassBoostMillibels)
    }
    @Test fun overlappingGoalsRespectTotalCommandBudget() {
        val result = AssistantTuningProtocol.parse(output("""{"target":"brightness","amount":1.0},{"target":"vocal_presence","amount":1.0}""", ""),
            request("More treble and vocals")) as AssistantPlanResult.Proposed
        assertTrue(result.proposal.settings.bandGainsMillibels.all { it in -150..150 })
    }
}
