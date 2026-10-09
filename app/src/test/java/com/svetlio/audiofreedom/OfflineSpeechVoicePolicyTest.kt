package com.svetlio.audiofreedom

import org.junit.Assert.*
import org.junit.Test

class OfflineSpeechVoicePolicyTest {
    private fun voice(id: String, country: String = "BG", quality: Int = 300, latency: Int = 300,
        language: String = "bg", network: Boolean = false, installed: Boolean = true) =
        OfflineSpeechVoice(id, id, language, country, quality, latency, network, installed)

    @Test fun automaticPrefersExactLocaleThenQuality() {
        val voices = listOf(voice("generic", country = "", quality = 500), voice("low", quality = 200), voice("high", quality = 400))
        assertEquals("high", OfflineSpeechVoicePolicy.choose(voices, "bg", "BG", "")?.id)
    }

    @Test fun tiesPreferLowerLatencyAndStableName() {
        val voices = listOf(voice("z", latency = 200), voice("slow", latency = 500), voice("a", latency = 200))
        assertEquals(listOf("a", "z", "slow"), OfflineSpeechVoicePolicy.available(voices, "bg", "BG").map { it.id })
    }

    @Test fun explicitChoiceOverridesQualityRanking() {
        val voices = listOf(voice("high", quality = 500), voice("chosen", quality = 200))
        assertEquals("chosen", OfflineSpeechVoicePolicy.choose(voices, "bg", "BG", "chosen")?.id)
    }

    @Test fun networkAndUninstalledVoicesAreNeverSelected() {
        val voices = listOf(voice("network", quality = 500, network = true), voice("missing", quality = 500, installed = false), voice("offline"))
        for (preferred in listOf("network", "missing", "")) {
            assertEquals("offline", OfflineSpeechVoicePolicy.choose(voices, "bg", "BG", preferred)?.id)
        }
    }

    @Test fun staleOrWrongLanguageChoiceFallsBackSafely() {
        val voices = listOf(voice("english", language = "en", country = "US"), voice("bulgarian"))
        for (preferred in listOf("english", "deleted-engine:voice")) {
            assertEquals("bulgarian", OfflineSpeechVoicePolicy.choose(voices, "bg", "BG", preferred)?.id)
        }
    }

    @Test fun missingLanguageDoesNotFallBackToForeignAccent() {
        assertNull(OfflineSpeechVoicePolicy.choose(listOf(voice("english", language = "en")), "bg", "BG", ""))
    }

    @Test fun noAvailableVoicesReturnsNoSelection() {
        assertNull(OfflineSpeechVoicePolicy.choose(emptyList(), "bg", "BG", "previous-voice"))
    }

    @Test fun languageOnlyVoiceIsAcceptedIfRegionUnavailable() {
        assertEquals("generic", OfflineSpeechVoicePolicy.choose(listOf(voice("generic", country = "")), "bg", "BG", "")?.id)
    }

    @Test fun englishHasNoArtificialCountryPreference() {
        val voices = listOf(voice("us", language = "en", country = "US", quality = 200),
            voice("gb", language = "en", country = "GB", quality = 400))
        assertEquals("gb", OfflineSpeechVoicePolicy.choose(voices, "en", "", "")?.id)
    }
}
