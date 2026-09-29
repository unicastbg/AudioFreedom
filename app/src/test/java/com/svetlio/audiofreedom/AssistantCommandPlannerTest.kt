package com.svetlio.audiofreedom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantCommandPlannerTest {
    @Test
    fun bassAdjustmentIsBoundedAndPreservesPreamp() {
        val current = AudioFreedomSettings(preampMillibels = -275)

        val proposal = proposed(
            AssistantCommandPlanner.plan(
                command = "Make the bass deeper and stronger",
                current = current,
                profiles = emptyList(),
                maximumBandDeltaMillibels = 150,
            ),
        )

        assertTrue(proposal.settings.dynamicBassEnabled)
        assertEquals(150, proposal.settings.bandGainsMillibels[0])
        assertEquals(150, proposal.settings.bandGainsMillibels[1])
        assertEquals(-275, proposal.settings.preampMillibels)
        assertEquals(750, proposal.settings.bassBoostMillibels)
        assertEquals(85, proposal.settings.bassCutoffHz)
    }

    @Test
    fun combinedRequestNeverExceedsPerCommandBandLimit() {
        val proposal = proposed(
            AssistantCommandPlanner.plan(
                command = "Make it warmer, less muddy, and give it more bass",
                current = AudioFreedomSettings(),
                profiles = emptyList(),
                maximumBandDeltaMillibels = 300,
            ),
        )

        proposal.settings.bandGainsMillibels.forEach { gain ->
            assertTrue(gain in -300..300)
        }
    }

    @Test
    fun harshnessRequestReducesPresenceAndDetail() {
        val current = AudioFreedomSettings(
            equalizerEnabled = true,
            detailRecoveryEnabled = true,
            detailAmountPercent = 60,
        )

        val proposal = proposed(
            AssistantCommandPlanner.plan(
                command = "The vocals sound harsh and sharp",
                current = current,
                profiles = emptyList(),
                maximumBandDeltaMillibels = 150,
            ),
        )

        assertEquals(-150, proposal.settings.bandGainsMillibels[7])
        assertEquals(-100, proposal.settings.bandGainsMillibels[8])
        assertEquals(50, proposal.settings.detailAmountPercent)
    }

    @Test
    fun profileChangesAlwaysRequirePreview() {
        val profile = AudioFreedomProfile(
            id = "car",
            name = "Car",
            settings = AudioFreedomSettings(equalizerEnabled = true, preampMillibels = -300),
        )

        val proposal = proposed(
            AssistantCommandPlanner.plan(
                command = "Load my Car profile",
                current = AudioFreedomSettings(),
                profiles = listOf(profile),
                maximumBandDeltaMillibels = 150,
            ),
        )

        assertEquals("car", proposal.selectedProfileId)
        assertFalse(proposal.canApplyAutomatically)
        assertEquals(profile.settings, proposal.settings)
    }

    @Test
    fun exactSavedProfileNameLoadsWithoutExtraKeywords() {
        val profile = AudioFreedomProfile(
            id = "headphones",
            name = "Deep Headphones",
            settings = AudioFreedomSettings(dynamicBassEnabled = true),
        )

        val proposal = proposed(
            AssistantCommandPlanner.plan(
                command = "Deep Headphones",
                current = AudioFreedomSettings(),
                profiles = listOf(profile),
                maximumBandDeltaMillibels = 150,
            ),
        )

        assertEquals(profile.id, proposal.selectedProfileId)
        assertEquals(profile.settings, proposal.settings)
    }

    @Test
    fun tonalCommandsNeverSelectAnUnmentionedProfile() {
        val btProfile = AudioFreedomProfile(
            id = "bt",
            name = "BT",
            settings = AudioFreedomSettings(preampMillibels = -600),
        )

        listOf("More bass", "Clearer vocals").forEach { command ->
            val proposal = proposed(
                AssistantCommandPlanner.plan(
                    command = command,
                    current = AudioFreedomSettings(),
                    profiles = listOf(btProfile),
                    maximumBandDeltaMillibels = 150,
                ),
            )

            assertEquals(null, proposal.selectedProfileId)
            assertTrue(proposal.settings != btProfile.settings)
        }
    }

    @Test
    fun voiceFriendlyVocalClarityPhrasesAreDeterministic() {
        listOf(
            "Clear vocals.",
            "Make vocals clear",
            "Vocal clarity",
            "Изчисти вокалите",
        ).forEach { command ->
            val proposal = proposed(
                AssistantCommandPlanner.plan(
                    command = command,
                    current = AudioFreedomSettings(),
                    profiles = emptyList(),
                    maximumBandDeltaMillibels = 150,
                ),
            )

            assertTrue(proposal.settings.equalizerEnabled)
            assertTrue(proposal.settings.bandGainsMillibels[6] > 0)
        }
    }

    @Test
    fun automaticVoiceSelectionUsesSupportedBulgarianRetry() {
        val selected = chooseAutomaticVoiceTranscript(
            primary = "po yasni vokali",
            bulgarian = "По-ясни вокали",
        ) { transcript ->
            AssistantCommandPlanner.plan(
                command = transcript,
                current = AudioFreedomSettings(),
                profiles = emptyList(),
                maximumBandDeltaMillibels = 150,
            ) !is AssistantPlanResult.NotUnderstood
        }

        assertEquals("По-ясни вокали", selected)
    }

    @Test
    fun standardIncreaseCommandsCoverEchoMidsBassAndTreble() {
        val commands = listOf(
            "Add echo" to { settings: AudioFreedomSettings ->
                settings.reverbEnabled && settings.reverbAmountPercent > 25
            },
            "Increase mids" to { settings: AudioFreedomSettings ->
                settings.bandGainsMillibels[5] > 0
            },
            "Add bass" to { settings: AudioFreedomSettings ->
                settings.dynamicBassEnabled && settings.bandGainsMillibels[0] > 0
            },
            "Add treble" to { settings: AudioFreedomSettings ->
                settings.bandGainsMillibels[8] > 0
            },
        )

        commands.forEach { (command, assertion) ->
            val proposal = proposed(
                AssistantCommandPlanner.plan(
                    command = command,
                    current = AudioFreedomSettings(),
                    profiles = emptyList(),
                    maximumBandDeltaMillibels = 150,
                ),
            )
            assertTrue("Failed command: $command", assertion(proposal.settings))
        }
    }

    @Test
    fun echoCanAlsoToggleReverbDirectly() {
        val proposal = proposed(
            AssistantCommandPlanner.plan(
                command = "Turn on echo",
                current = AudioFreedomSettings(reverbEnabled = false),
                profiles = emptyList(),
                maximumBandDeltaMillibels = 150,
            ),
        )

        assertTrue(proposal.settings.reverbEnabled)
    }

    @Test
    fun explicitEffectCommandsToggleTheirOwnControls() {
        val current = AudioFreedomSettings(
            equalizerEnabled = true,
            immersiveFieldEnabled = false,
        )

        val proposal = proposed(
            AssistantCommandPlanner.plan(
                command = "Turn off equalizer and turn on Immersive field",
                current = current,
                profiles = emptyList(),
                maximumBandDeltaMillibels = 150,
            ),
        )

        assertFalse(proposal.settings.equalizerEnabled)
        assertTrue(proposal.settings.immersiveFieldEnabled)
        assertTrue(proposal.changes.contains("Equalizer off"))
        assertTrue(proposal.changes.contains("Immersive field on"))
    }

    @Test
    fun directControlUnderstandsAllEffectFamilies() {
        val current = AudioFreedomSettings(limiterEnabled = true)
        val proposal = proposed(
            AssistantCommandPlanner.planDirectControl(
                command =
                    "Enable bass foundation, enable detail recovery, enable reverb, then disable limiter",
                current = current,
                profiles = emptyList(),
            )!!,
        )

        assertTrue(proposal.settings.dynamicBassEnabled)
        assertTrue(proposal.settings.detailRecoveryEnabled)
        assertTrue(proposal.settings.reverbEnabled)
        assertFalse(proposal.settings.limiterEnabled)
    }

    @Test
    fun undoIsAConstrainedAction() {
        assertEquals(
            AssistantPlanResult.Undo,
            AssistantCommandPlanner.plan(
                command = "Restore previous settings",
                current = AudioFreedomSettings(),
                profiles = emptyList(),
                maximumBandDeltaMillibels = 150,
            ),
        )
    }

    @Test
    fun unknownRequestDoesNotModifySettings() {
        assertTrue(
            AssistantCommandPlanner.plan(
                command = "Play the next song",
                current = AudioFreedomSettings(),
                profiles = emptyList(),
                maximumBandDeltaMillibels = 150,
            ) is AssistantPlanResult.NotUnderstood,
        )
    }

    @Test
    fun bulgarianBassRequestUsesTheSameSafetyLimits() {
        val proposal = proposed(
            AssistantCommandPlanner.plan(
                command = "Искам повече и по-дълбок бас",
                current = AudioFreedomSettings(),
                profiles = emptyList(),
                maximumBandDeltaMillibels = 150,
            ),
        )

        assertEquals(150, proposal.settings.bandGainsMillibels[0])
        assertEquals(0, proposal.settings.preampMillibels)
        assertEquals(85, proposal.settings.bassCutoffHz)
    }

    @Test
    fun bulgarianProfileRequestFindsSavedProfile() {
        val profile = AudioFreedomProfile(
            id = "evening",
            name = "Вечер",
            settings = AudioFreedomSettings(preampMillibels = -200),
        )

        val proposal = proposed(
            AssistantCommandPlanner.plan(
                command = "Зареди профил Вечер",
                current = AudioFreedomSettings(),
                profiles = listOf(profile),
                maximumBandDeltaMillibels = 150,
            ),
        )

        assertEquals(profile.id, proposal.selectedProfileId)
        assertEquals(profile.settings, proposal.settings)
    }

    @Test
    fun bulgarianEffectToggleIsUnderstood() {
        val proposal = proposed(
            AssistantCommandPlanner.plan(
                command = "Включи еквалайзера",
                current = AudioFreedomSettings(equalizerEnabled = false),
                profiles = emptyList(),
                maximumBandDeltaMillibels = 150,
            ),
        )

        assertTrue(proposal.settings.equalizerEnabled)
    }

    private fun proposed(result: AssistantPlanResult): AssistantProposal =
        (result as AssistantPlanResult.Proposed).proposal
}
