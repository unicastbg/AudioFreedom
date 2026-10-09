package com.svetlio.audiofreedom

internal class VoiceEndpointDetector(silenceMilliseconds: Int) {
    private val silenceLimit = 16_000 * silenceMilliseconds.coerceIn(500, 3000) / 1000
    private var voiceSamples = 0
    private var silenceSamples = 0
    var hasSpeech: Boolean = false
        private set

    fun accept(probability: Float, samples: Int): Boolean {
        require(samples > 0)
        if (probability >= 0.5F) {
            voiceSamples += samples
            if (voiceSamples >= 1600) hasSpeech = true
            silenceSamples = 0
        } else if (hasSpeech && probability < 0.35F) {
            silenceSamples += samples
        } else {
            silenceSamples = 0
            if (!hasSpeech) voiceSamples = 0
        }
        return hasSpeech && silenceSamples >= silenceLimit
    }
}
