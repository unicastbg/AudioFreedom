package com.svetlio.audiofreedom

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceEndpointDetectorTest {
    @Test
    fun silenceBeforeSpeechDoesNotCompleteACommand() {
        val detector = VoiceEndpointDetector(1000)
        repeat(500) { assertFalse(detector.accept(0.1F, 512)) }
        assertFalse(detector.hasSpeech)
    }

    @Test
    fun pauseTimerStartsAfterSpeechAndResetsWhenSpeechReturns() {
        val detector = VoiceEndpointDetector(1000)
        repeat(8) { assertFalse(detector.accept(0.9F, 512)) }
        assertTrue(detector.hasSpeech)
        repeat(30) { assertFalse(detector.accept(0.1F, 512)) }
        assertFalse(detector.accept(0.8F, 512))
        repeat(31) { assertFalse(detector.accept(0.1F, 512)) }
        assertTrue(detector.accept(0.1F, 512))
    }

    @Test
    fun briefNoiseSpikeDoesNotCountAsSpeech() {
        val detector = VoiceEndpointDetector(500)
        assertFalse(detector.accept(0.8F, 512))
        repeat(100) { assertFalse(detector.accept(0.05F, 512)) }
        assertFalse(detector.hasSpeech)
    }

    @Test
    fun longerConfiguredPauseToleratesHesitation() {
        val detector = VoiceEndpointDetector(2000)
        repeat(8) { detector.accept(0.8F, 512) }
        repeat(62) { assertFalse(detector.accept(0.05F, 512)) }
        assertTrue(detector.accept(0.05F, 512))
    }
}
