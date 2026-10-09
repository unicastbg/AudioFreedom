package com.svetlio.audiofreedom

import org.junit.Assert.assertEquals
import org.junit.Test

class HandsFreeVoicePolicyTest {
    @Test fun legacyBulgarianSelectionNowRepliesInEnglish() {
        assertEquals(AssistantLanguage.English,
            HandsFreeVoicePolicy.replyLanguage(AssistantLanguage.Bulgarian, "Add bass"))
        assertEquals(AssistantLanguage.English,
            HandsFreeVoicePolicy.replyLanguage(AssistantLanguage.English, "Добави бас"))
    }

    @Test fun legacyAutomaticSelectionNowRepliesInEnglish() {
        assertEquals(AssistantLanguage.English,
            HandsFreeVoicePolicy.replyLanguage(AssistantLanguage.Automatic, "Добави бас"))
        assertEquals(AssistantLanguage.English,
            HandsFreeVoicePolicy.replyLanguage(AssistantLanguage.Automatic, "Add bass"))
    }

    @Test fun allLegacySelectionsAndTranscriptsUseEnglish() {
        assertEquals(AssistantLanguage.English, AppPreferences().assistantLanguage)
        for (selected in AssistantLanguage.entries) {
            for (transcript in listOf("Add bass", "Добави бас", "Añade bajos", "Добавь бас", "")) {
                assertEquals(AssistantLanguage.English, HandsFreeVoicePolicy.replyLanguage(selected, transcript))
            }
        }
    }

    @Test fun advertisedEnglishGuideCommandsRemainAvailable() {
        val settings = AudioFreedomSettings(dynamicBassEnabled = true,
            bandGainsMillibels = List(EqualizerBandCount) { 100 })
        val profiles = listOf(AudioFreedomProfile("car", "Car", AudioFreedomSettings()))
        for (command in listOf("Add bass", "Reduce bass", "Increase mids", "Lower treble", "Clearer vocals",
            "More detail", "Wider stage", "Add echo", "Reduce reverb", "Increase bass and treble",
            "Increase bass and lower treble", "Add bass and turn on reverb", "Turn on equalizer",
            "Turn off bass foundation", "Turn on detail recovery", "Turn on immersive field", "Turn on reverb",
            "Turn off output protection", "Switch profile to Car", "Load profile Car", "Undo")) {
            org.junit.Assert.assertTrue(command,
                AssistantCommandPlanner.plan(command, settings, profiles, 150) !is AssistantPlanResult.NotUnderstood)
        }
    }

    @Test fun bulgarianSummaryUsesActualChangesNotModelProse() {
        val before = AudioFreedomSettings()
        val proposal = AssistantProposal("English title", "English explanation", before,
            before.copy(reverbEnabled = true, reverbAmountPercent = 40), emptyList())
        assertEquals("Включвам реверберацията. Променям настройките на реверберацията",
            HandsFreeVoicePolicy.bulgarianSummary(proposal, emptyList()))
    }

    @Test fun profileNameIsPreservedInBulgarianReadback() {
        val before = AudioFreedomSettings()
        val profile = AudioFreedomProfile("car", "My Car", before)
        val proposal = AssistantProposal("Load My Car", "", before, before, emptyList(), "car")
        assertEquals("Зареждам профил My Car",
            HandsFreeVoicePolicy.bulgarianSummary(proposal, listOf(profile)))
    }

    @Test
    fun confirmationRejectsNegationAndAmbiguity() {
        assertEquals(VoiceAnswer.Yes, HandsFreeVoicePolicy.confirmation("Yes, please."))
        assertEquals(VoiceAnswer.Yes, HandsFreeVoicePolicy.confirmation("Да!"))
        assertEquals(VoiceAnswer.No, HandsFreeVoicePolicy.confirmation("Cancel"))
        assertEquals(VoiceAnswer.No, HandsFreeVoicePolicy.confirmation("Не"))
        for (answer in listOf("", "maybe", "not yes", "yes no", "yes but less bass", "yesterday", "не да")) {
            assertEquals(answer, VoiceAnswer.Unknown, HandsFreeVoicePolicy.confirmation(answer))
        }
    }
}
