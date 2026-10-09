package com.svetlio.audiofreedom

internal data class OfflineSpeechVoice(
    val id: String,
    val name: String,
    val language: String,
    val country: String,
    val quality: Int,
    val latency: Int,
    val requiresNetwork: Boolean = false,
    val installed: Boolean = true,
)

internal object OfflineSpeechVoicePolicy {
    fun available(voices: List<OfflineSpeechVoice>, language: String, country: String): List<OfflineSpeechVoice> =
        voices.filter { it.language == language && !it.requiresNetwork && it.installed }
            .sortedWith(compareByDescending<OfflineSpeechVoice> { country.isNotEmpty() && it.country == country }
                .thenByDescending { it.quality }.thenBy { it.latency }.thenBy { it.id })

    fun choose(voices: List<OfflineSpeechVoice>, language: String, country: String, preferredId: String): OfflineSpeechVoice? {
        val available = available(voices, language, country)
        return available.firstOrNull { it.id == preferredId } ?: available.firstOrNull()
    }
}
