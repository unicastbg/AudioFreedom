package com.svetlio.audiofreedom

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetVoicePhaseTest {
    @Test
    fun completedAndReviewStatesAllowAnotherCommand() {
        for (phase in listOf(WidgetVoicePhase.Review, WidgetVoicePhase.Applied,
                WidgetVoicePhase.Information, WidgetVoicePhase.Error)) {
            assertTrue(phase.canStartSession)
            assertTrue(phase.microphoneEnabled(sessionBusy = false))
        }
    }

    @Test
    fun readbackAndUndoMustFinishBeforeRestarting() {
        for (phase in WidgetVoicePhase.entries) {
            if (phase != WidgetVoicePhase.Listening) {
                assertFalse(phase.microphoneEnabled(sessionBusy = true))
            }
        }
    }

    @Test
    fun inFlightProcessingCannotStartAnotherCapture() {
        for (phase in listOf(WidgetVoicePhase.Preparing, WidgetVoicePhase.Transcribing,
                WidgetVoicePhase.Interpreting)) {
            assertFalse(phase.canStartSession)
            assertFalse(phase.microphoneEnabled(sessionBusy = false))
        }
    }

    @Test
    fun listeningKeepsStopButtonAvailable() {
        assertFalse(WidgetVoicePhase.Listening.canStartSession)
        assertTrue(WidgetVoicePhase.Listening.microphoneEnabled(sessionBusy = true))
    }
}
