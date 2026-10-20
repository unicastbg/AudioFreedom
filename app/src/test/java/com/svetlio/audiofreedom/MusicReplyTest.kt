package com.svetlio.audiofreedom

import org.junit.Assert.*
import org.junit.Test

class MusicReplyTest {
    @Test fun musicRepliesAreBriefByDefault() {
        assertFalse(AppPreferences().assistantDetailedMusicReplies)
        for (reply in listOf("Playing The Prodigy, 31 tracks", "Playing trance (genre), 143 tracks",
            "Playing Teardrop, 1 track", "Playing Drum, Bass, and Everything, 2 tracks.")) {
            assertEquals(reply, "Playing", MusicBridgeResult.Handled(reply).spokenMessage(false))
        }
    }

    @Test fun detailedOptionRetainsTheOriginalReply() {
        val reply = "Playing The Prodigy, 31 tracks"
        assertEquals(reply, MusicBridgeResult.Handled(reply).spokenMessage(true))
    }

    @Test fun screenKeepsDetailsEvenWhenSpeechIsBrief() {
        val result = MusicBridgeResult.Handled("Playing The Prodigy, 31 tracks")
        assertEquals("Playing", result.spokenMessage(false))
        assertEquals("Playing The Prodigy, 31 tracks", result.message())
    }

    @Test fun handledLookupFailuresAreNeverCalledPlaying() {
        for (reply in listOf("I couldn't find The Prodigy", "The music library hasn't been indexed yet",
            "There is nothing queued to play", "That was the last track", "That was the first track")) {
            assertEquals(reply, MusicBridgeResult.Handled(reply).spokenMessage(false))
        }
    }

    @Test fun transportRepliesStayInformative() {
        for (reply in listOf("Playing", "Paused", "Stopped", "Next track", "Previous track")) {
            assertEquals(reply, MusicBridgeResult.Handled(reply).spokenMessage(false))
        }
    }

    @Test fun bridgeErrorsAreUnchangedInEitherMode() {
        for (result in listOf(MusicBridgeResult.Unsupported, MusicBridgeResult.Unavailable,
            MusicBridgeResult.Untrusted, MusicBridgeResult.NeedsPlayerUpdate, MusicBridgeResult.Expired,
            MusicBridgeResult.Duplicate, MusicBridgeResult.TimedOut, MusicBridgeResult.Failed)) {
            assertEquals(result.message(), result.spokenMessage(false))
            assertEquals(result.message(), result.spokenMessage(true))
        }
    }

    @Test fun unknownPlayingTextAndEmptyRepliesAreNotMisclassified() {
        for (reply in listOf("Playing is unavailable", "Playing The Prodigy, 0 tracks", "")) {
            assertEquals(MusicBridgeResult.Handled(reply).message(), MusicBridgeResult.Handled(reply).spokenMessage(false))
        }
    }
}
