package com.svetlio.audiofreedom

import org.junit.Assert.*
import org.junit.Test

class AssistantConversationCommandsTest {
    @Test fun cancelRecognizesWholeCommandsInBothLanguages() {
        for (command in listOf("Cancel!", "Please cancel.", "Stop listening", "Never mind", "Отказ", "Спри да слушаш", "Отмени командата")) {
            assertEquals(command, AssistantConversationControl.Cancel, AssistantConversationCommands.recognize(command))
            assertEquals(command, VoiceAnswer.No, HandsFreeVoicePolicy.confirmation(command))
        }
    }

    @Test fun cancelDoesNotStealUndoOrEffectCommands() {
        for (command in listOf("undo", "отмени", "turn off reverb", "спри еквалайзера", "cancel bass", "increase bass and cancel")) {
            assertNull(command, AssistantConversationCommands.recognize(command))
        }
    }

    @Test fun generalHelpIsCaseAndPunctuationInsensitive() {
        for (command in listOf("HELP", "What can you do?", "What can I say?", "Помощ!", "Какво можеш да правиш?")) {
            assertEquals(command, AssistantConversationControl.Help(AssistantHelpTopic.General), AssistantConversationCommands.recognize(command))
        }
    }

    @Test fun topicHelpWorksBeforeOrAfterTheTopic() {
        for ((command, topic) in listOf("Bass help" to AssistantHelpTopic.Bass, "Help with bass" to AssistantHelpTopic.Bass,
            "Equalizer help" to AssistantHelpTopic.Equalizer, "Help profiles" to AssistantHelpTopic.Profiles,
            "Помощ за баса" to AssistantHelpTopic.Bass, "Еквалайзер помощ" to AssistantHelpTopic.Equalizer,
            "Помощ за профилите" to AssistantHelpTopic.Profiles, "Reverb help" to AssistantHelpTopic.Reverb,
            "Immersive field help" to AssistantHelpTopic.Immersive, "Detail recovery help" to AssistantHelpTopic.Detail,
            "Undo help" to AssistantHelpTopic.Undo)) {
            assertEquals(command, AssistantConversationControl.Help(topic), AssistantConversationCommands.recognize(command))
        }
    }

    @Test fun unsupportedTopicDoesNotInventCapabilities() {
        val control = AssistantConversationCommands.recognize("Radio help")!!
        assertEquals(AssistantConversationControl.Help(AssistantHelpTopic.Unsupported), control)
        assertTrue(AssistantConversationCommands.response(control, AssistantLanguage.English).contains("cannot control a radio"))
    }

    @Test fun explicitProfileNamesContainingHelpRemainLoadable() {
        for (command in listOf("Load profile Radio Help", "Switch profile to Help", "Зареди профил Помощ", "Смени профил Помощ")) {
            assertNull(command, AssistantConversationCommands.recognize(command))
        }
    }

    @Test fun everyReplyIsBriefAndLocalized() {
        for (topic in AssistantHelpTopic.entries) {
            val control = AssistantConversationControl.Help(topic)
            val english = AssistantConversationCommands.response(control, AssistantLanguage.English)
            val bulgarian = AssistantConversationCommands.response(control, AssistantLanguage.Bulgarian)
            assertTrue(topic.name, english.split(' ').size <= 40)
            assertTrue(topic.name, bulgarian.split(' ').size <= 40)
            assertFalse(english.any { it in '\u0400'..'\u04ff' })
            assertTrue(bulgarian.any { it in '\u0400'..'\u04ff' })
        }
    }

    @Test fun controlsAreValidRecognitionFallbackCandidates() {
        val settings = AudioFreedomSettings()
        val preferences = AppPreferences()
        for (command in listOf("Cancel", "Help", "Помощ за баса", "Отказ")) {
            assertTrue(command, isSupportedAssistantCommand(command, settings, emptyList(), preferences))
        }
        assertEquals("Помощ за баса", chooseAutomaticVoiceTranscript("unrecognized words", "Помощ за баса") {
            isSupportedAssistantCommand(it, settings, emptyList(), preferences)
        })
    }

    @Test fun advertisedBulgarianEffectCommandsAreRecognized() {
        val settings = AudioFreedomSettings(equalizerEnabled = true)
        for (command in listOf("включи детайлността", "включи обгръщащия звук", "увеличи ширината",
            "намали ширината", "намали ехото", "изключи еквалайзера", "увеличи баса и намали високите")) {
            assertTrue(command, AssistantCommandPlanner.plan(command, settings, emptyList(), 150) is AssistantPlanResult.Proposed)
        }
    }
}
