package com.svetlio.audiofreedom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicNamingTest {
    @Test fun helpRecognizesCurrentPlayerName() {
        for (command in listOf("Media Player help", "Help with Media Player", "Help for Media Player")) {
            assertEquals(AssistantConversationControl.Help(AssistantHelpTopic.Music),
                AssistantConversationCommands.recognize(command))
        }
    }

    @Test fun playerErrorsUseCurrentName() {
        for (result in listOf(MusicBridgeResult.Unsupported, MusicBridgeResult.Unavailable,
            MusicBridgeResult.Untrusted, MusicBridgeResult.NeedsPlayerUpdate,
            MusicBridgeResult.TimedOut, MusicBridgeResult.Failed)) {
            assertTrue(result.message(), result.message().contains("Media Player"))
        }
    }

    @Test fun musicHelpUsesCurrentNameInBothLanguages() {
        for (language in listOf(AssistantLanguage.English, AssistantLanguage.Bulgarian)) {
            for (enabled in listOf(false, true)) {
                val reply = AssistantConversationCommands.response(
                    AssistantConversationControl.Help(AssistantHelpTopic.Music), language, enabled,
                )
                assertTrue(reply, reply.contains("Media Player"))
            }
        }
    }
}
