package com.svetlio.audiofreedom

import org.junit.Assert.*
import org.junit.Test

class MusicCommandRoutingTest {
    @Test fun musicNamesCannotReachDspInterpretation() {
        for (command in listOf("Play trance", "Play The Prodigy", "Play artist Muddy Waters",
            "Play song Surround", "Play song Clearer", "Shuffle drum and bass", "Play album More Bass")) {
            assertTrue(command, MusicCommandRouting.isMusicCommand(command))
            assertTrue(command, AssistantCommandPlanner.plan(command, AudioFreedomSettings(), emptyList(), 200)
                is AssistantPlanResult.NotUnderstood)
        }
    }

    @Test fun transportAndPoliteCommandsRouteToPlayer() {
        for (command in listOf("Play", "Next track!", "Pause music", "Stop music", "Stop playback",
            "Please play The Prodigy.", "play trance please", "Resume", "Previous")) {
            assertTrue(command, MusicCommandRouting.isMusicCommand(command))
        }
        assertEquals("play The Prodigy", MusicCommandRouting.transcript("Please play The Prodigy."))
    }

    @Test fun assistantCommandsStayLocal() {
        for (command in listOf("stop", "cancel", "stop listening", "help", "more bass",
            "turn off equalizer", "turn on reverb", "switch profile to Car", "clearer vocals", "undo")) {
            assertFalse(command, MusicCommandRouting.isMusicCommand(command))
        }
    }

    @Test fun disabledForwardingCannotTurnMusicNamesIntoDspRequests() {
        // Routing is independent of the forwarding preference. The caller reports disabled
        // instead of falling through to either planner.
        assertTrue(MusicCommandRouting.isMusicCommand("Play artist Muddy Waters"))
    }

    @Test fun timeoutDoesNotClaimThatPlaybackCouldNotHaveChanged() {
        assertTrue(MusicBridgeResult.TimedOut.message().contains("Check playback"))
        assertFalse(MusicBridgeResult.TimedOut.message().contains("No playback change"))
        assertTrue(MusicBridgeResult.Expired.message().contains("No playback change"))
    }

    @Test fun unavailableUnsupportedAndSigningErrorsAreDistinct() {
        val messages = listOf(MusicBridgeResult.Unavailable, MusicBridgeResult.Unsupported,
            MusicBridgeResult.Untrusted, MusicBridgeResult.NeedsPlayerUpdate, MusicBridgeResult.Failed)
            .map { it.message() }
        assertEquals(messages.size, messages.toSet().size)
    }

    @Test fun musicHelpDescribesOneWayCommandsOnlyWhenEnabled() {
        val control = AssistantConversationCommands.recognize("Music help")!!
        assertTrue(AssistantConversationCommands.response(control, AssistantLanguage.English, true).contains("stop music"))
        assertTrue(AssistantConversationCommands.response(control, AssistantLanguage.English, false).contains("enable music forwarding"))
    }
}
