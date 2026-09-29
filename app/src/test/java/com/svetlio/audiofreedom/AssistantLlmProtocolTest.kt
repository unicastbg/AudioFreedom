package com.svetlio.audiofreedom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantLlmProtocolTest {
    @Test
    fun constrainedActionsUseTheExistingSafetyPlanner() {
        val request = request(maximumDelta = 150)

        val result = AssistantLlmProtocol.parse(
            """{"actions":["more_bass","clearer_vocals"],"profile":-1,"undo":false}""",
            request,
        ) as AssistantPlanResult.Proposed

        assertEquals(150, result.proposal.settings.bandGainsMillibels[0])
        assertTrue(result.proposal.settings.bandGainsMillibels.all { it in -150..150 })
        assertEquals(0, result.proposal.settings.preampMillibels)
    }

    @Test
    fun modelCanOnlyLoadAListedProfileByIndex() {
        val profile = AudioFreedomProfile(
            id = "car",
            name = "Car",
            settings = AudioFreedomSettings(preampMillibels = -300),
        )
        val request = request(profiles = listOf(profile)).copy(command = "Load my Car profile")

        val result = AssistantLlmProtocol.parse(
            """{"actions":[],"profile":0,"undo":false}""",
            request,
        ) as AssistantPlanResult.Proposed

        assertEquals(profile.id, result.proposal.selectedProfileId)
        assertEquals(profile.settings, result.proposal.settings)
    }

    @Test
    fun hallucinatedProfileIndexIsRejectedForTonalCommand() {
        val profile = AudioFreedomProfile(
            id = "bt",
            name = "BT",
            settings = AudioFreedomSettings(preampMillibels = -500),
        )
        val request = request(profiles = listOf(profile)).copy(command = "More bass")

        val result = AssistantLlmProtocol.parse(
            """{"actions":[],"profile":0,"undo":false}""",
            request,
        )

        assertTrue(result is AssistantPlanResult.NotUnderstood)
    }

    @Test
    fun constrainedActionsCanToggleAudioFreedomEffects() {
        val request = request().copy(
            command = "Turn off EQ and turn on immersive field",
            current = AudioFreedomSettings(
                equalizerEnabled = true,
                immersiveFieldEnabled = false,
            ),
        )

        val result = AssistantLlmProtocol.parse(
            """{"actions":["disable_equalizer","enable_immersive"],"profile":-1,"undo":false}""",
            request,
        ) as AssistantPlanResult.Proposed

        assertTrue(!result.proposal.settings.equalizerEnabled)
        assertTrue(result.proposal.settings.immersiveFieldEnabled)
    }

    @Test
    fun promptGroundsTheModelInCurrentControlsWithoutExposingProfileIndexes() {
        val profile = AudioFreedomProfile(
            id = "night",
            name = "Night Listening",
            settings = AudioFreedomSettings(),
        )
        val prompt = AssistantLlmProtocol.userPrompt(
            request(profiles = listOf(profile)).copy(
                current = AudioFreedomSettings(
                    equalizerEnabled = true,
                    immersiveFieldEnabled = true,
                ),
            ),
        )

        assertTrue(prompt.contains("equalizer=true"))
        assertTrue(prompt.contains("immersive=true"))
        assertFalse(prompt.contains("Night Listening"))
    }

    @Test
    fun promptIncludesOnlyExplicitLocalPersonalizationExamples() {
        val prompt = AssistantLlmProtocol.userPrompt(
            request(),
            listOf(
                AssistantFeedbackExample(
                    command = "Give the voices more focus",
                    changes = listOf("2k Hz +1.5 dB"),
                    accepted = true,
                ),
            ),
        )

        assertTrue(prompt.contains("Local personalization history"))
        assertTrue(prompt.contains("accepted phrase=Give the voices more focus"))
        assertTrue(prompt.contains("result=2k Hz +1.5 dB"))
    }

    @Test
    fun ambiguousUndoIsRejected() {
        val result = AssistantLlmProtocol.parse(
            """{"actions":["more_bass"],"profile":-1,"undo":true}""",
            request(),
        )

        assertTrue(result is AssistantPlanResult.NotUnderstood)
    }

    @Test
    fun unknownActionIsRejected() {
        val result = AssistantLlmProtocol.parse(
            """{"actions":["set_volume_very_high"],"profile":-1,"undo":false}""",
            request(),
        )

        assertTrue(result is AssistantPlanResult.NotUnderstood)
    }

    @Test
    fun malformedOutputIsRejected() {
        val result = AssistantLlmProtocol.parse("not json", request())

        assertTrue(result is AssistantPlanResult.NotUnderstood)
    }

    private fun request(
        maximumDelta: Int = 300,
        profiles: List<AudioFreedomProfile> = emptyList(),
    ) = AssistantRequest(
        command = "Make the bass stronger and bring the vocals forward",
        current = AudioFreedomSettings(),
        profiles = profiles,
        maximumBandDeltaMillibels = maximumDelta,
    )
}
