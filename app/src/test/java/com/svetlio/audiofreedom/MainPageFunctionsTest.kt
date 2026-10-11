package com.svetlio.audiofreedom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainPageFunctionsTest {
    private val allEnabled = AudioFreedomSettings(
        equalizerEnabled = true,
        immersiveFieldEnabled = true,
        reverbEnabled = true,
        volumeLevelerEnabled = true,
        detailRecoveryEnabled = true,
        dynamicBassEnabled = true,
        limiterEnabled = true,
        preampMillibels = -600,
        bandGainsMillibels = List(EqualizerBandCount) { 100 * it },
        reverbAmountPercent = 73,
    )

    @Test fun everyFunctionIsVisibleByDefault() {
        val preferences = AppPreferences()
        assertTrue(MainPageFunction.entries.all { preferences.isVisible(it) })
        assertEquals(allEnabled, allEnabled.withVisibleEffects(preferences))
    }

    @Test fun hidingReverbDisablesOnlyReverbAndKeepsItsValues() {
        val preferences = AppPreferences().withVisibility(MainPageFunction.Reverb, false)
        assertFalse(preferences.isVisible(MainPageFunction.Reverb))
        assertEquals(allEnabled.copy(reverbEnabled = false), allEnabled.withVisibleEffects(preferences))
    }

    @Test fun allHiddenEffectsAreDisabledWithoutResettingTheirParameters() {
        val preferences = AppPreferences(hiddenMainPageFunctions = MainPageFunction.entries.toSet())
        assertEquals(allEnabled.copy(
            equalizerEnabled = false, immersiveFieldEnabled = false, reverbEnabled = false,
            volumeLevelerEnabled = false, detailRecoveryEnabled = false,
            dynamicBassEnabled = false, limiterEnabled = false,
        ), allEnabled.withVisibleEffects(preferences))
    }

    @Test fun showingAnEffectAgainDoesNotEnableIt() {
        val hidden = AppPreferences().withVisibility(MainPageFunction.Reverb, false)
        val disabled = allEnabled.withVisibleEffects(hidden)
        val shown = hidden.withVisibility(MainPageFunction.Reverb, true)
        assertTrue(shown.isVisible(MainPageFunction.Reverb))
        assertFalse(disabled.withVisibleEffects(shown).reverbEnabled)
    }

    @Test fun profileAndComparisonSnapshotsCannotEnableHiddenEffects() {
        val preferences = AppPreferences().withVisibility(MainPageFunction.Equalizer, false)
            .withVisibility(MainPageFunction.BassFoundation, false)
        val snapshot = SoundComparison().capture(ComparisonSlot.B, allEnabled).settings(ComparisonSlot.B)!!
        val applied = snapshot.withVisibleEffects(preferences)
        assertFalse(applied.equalizerEnabled)
        assertFalse(applied.dynamicBassEnabled)
        assertEquals(0, java.nio.ByteBuffer.wrap(AudioFreedomProtocol.equalizerConfiguration(applied))
            .order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt(16))
        assertEquals(allEnabled, snapshot)
    }

    @Test fun hidingReadOnlyToolsDoesNotChangeDspOrOtherPreferences() {
        val preferences = AppPreferences(theme = ThemePreference.Dark, assistantEnabled = true)
            .withVisibility(MainPageFunction.PlaybackAnalysis, false)
            .withVisibility(MainPageFunction.SoundComparison, false)
        assertEquals(allEnabled, allEnabled.withVisibleEffects(preferences))
        assertEquals(ThemePreference.Dark, preferences.theme)
        assertTrue(preferences.assistantEnabled)
    }

    @Test fun savedKeysAreStableUniqueAndIgnoreUnknownFutureFunctions() {
        val keys = MainPageFunction.entries.map { it.storageKey }.toSet()
        assertEquals(MainPageFunction.entries.size, keys.size)
        assertEquals(MainPageFunction.entries.toSet(), hiddenMainPageFunctions(keys))
        assertEquals(setOf(MainPageFunction.Reverb), hiddenMainPageFunctions(setOf("reverb", "future")))
        assertTrue(hiddenMainPageFunctions(emptySet()).isEmpty())
    }
}
