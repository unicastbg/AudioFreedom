package com.svetlio.audiofreedom

import org.junit.Assert.*
import org.junit.Test

class AssistantCompoundCommandsTest {
    private fun plan(command: String) = AssistantCommandPlanner.plan(command, AudioFreedomSettings(), emptyList(), 150)

    @Test fun bassAndTrebleBothApply() {
        val proposal = (plan("higher bass and lower highs") as AssistantPlanResult.Proposed).proposal
        assertTrue(proposal.settings.bandGainsMillibels[0] > 0)
        assertTrue(proposal.settings.bandGainsMillibels[8] < 0)
    }
    @Test fun tonalAndToggleBothApply() {
        val proposal = (plan("higher bass and turn on reverb") as AssistantPlanResult.Proposed).proposal
        assertTrue(proposal.settings.dynamicBassEnabled)
        assertTrue(proposal.settings.reverbEnabled)
    }
    @Test fun unknownClauseRejectsWholeRequest() {
        assertTrue(plan("add bass and order coffee") is AssistantPlanResult.NotUnderstood)
    }
    @Test fun opposingDirectionsAreRejected() {
        assertTrue(plan("more bass and less bass") is AssistantPlanResult.NotUnderstood)
    }
    @Test fun repeatedBoostIsBounded() {
        val proposal = (plan("add bass and increase bass and boost bass") as AssistantPlanResult.Proposed).proposal
        assertTrue(proposal.settings.bandGainsMillibels.all { it in -150..150 })
        assertTrue(proposal.settings.bassBoostMillibels <= 750)
    }
    @Test fun bulgarianCompoundAppliesBothChanges() {
        val proposal = (plan("Добави бас и намали високите") as AssistantPlanResult.Proposed).proposal
        assertTrue(proposal.settings.bandGainsMillibels[0] > 0)
        assertTrue(proposal.settings.bandGainsMillibels[8] < 0)
    }
    @Test fun reportedBulgarianPhraseAndRecognitionVariantsApplyBothChanges() {
        for (command in listOf(
            "Увеличи бас и намали високите",
            "Увеличи баса и намали високите честоти.",
            "Увеличи баса. Намали високите.",
            "Увеличи баса! Намали високите тонове!",
            "Увеличете баса и намалете високите честоти",
            "Увеличи  баса и намали   високите",
            "Увеличи\u00a0баса и намали\u00a0високите",
        )) {
            val result = plan(command)
            assertTrue("$command: $result", result is AssistantPlanResult.Proposed)
            val settings = (result as AssistantPlanResult.Proposed).proposal.settings
            assertTrue(command, settings.bandGainsMillibels[0] > 0)
            assertTrue(command, settings.bandGainsMillibels[8] < 0)
        }
    }
    @Test fun sentencePunctuationDoesNotAllowUnknownCommandsToBeIgnored() {
        assertTrue(plan("Увеличи баса. Поръчай кафе.") is AssistantPlanResult.NotUnderstood)
    }
    @Test fun shorthandSharesTheDirectionAcrossKnownTargets() {
        for (command in listOf("Increase bass and treble", "Увеличи баса и високите", "Increase bass, mids and treble")) {
            val result = plan(command) as AssistantPlanResult.Proposed
            assertTrue(command, result.proposal.settings.bandGainsMillibels[0] > 0)
            assertTrue(command, result.proposal.settings.bandGainsMillibels[8] > 0)
        }
    }
    @Test fun shorthandUsesTheMostRecentExplicitDirection() {
        val result = plan("Increase bass and lower mids and treble") as AssistantPlanResult.Proposed
        assertTrue(result.proposal.settings.bandGainsMillibels[0] > 0)
        assertTrue(result.proposal.settings.bandGainsMillibels[5] < 0)
        assertTrue(result.proposal.settings.bandGainsMillibels[8] < 0)
    }
    @Test fun shorthandNeverGuessesUnknownTargets() {
        assertTrue(plan("Increase bass and battery life") is AssistantPlanResult.NotUnderstood)
    }
}
