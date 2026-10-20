package com.svetlio.audiofreedom

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EqualizerRangePersistenceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var originalPreferences: AppPreferences
    private lateinit var originalSettings: AudioFreedomSettings
    private val profiles = mutableListOf<String>()
    private val route = "eq-test-${UUID.randomUUID()}"
    private val gains = listOf(1800, -1600, 1100) + List(7) { 0 }

    @Before fun rememberExistingSettings() {
        originalPreferences = AppPreferencesStore.load(context)
        originalSettings = AudioFreedomSettingsStore.load(context)
        AppPreferencesStore.save(context, AppPreferences(extendedEqualizerRange = true,
            extendedEqualizerRangeAcknowledged = true))
    }

    @After fun restoreSettings() {
        profiles.forEach { AudioFreedomProfileStore.delete(context, it) }
        context.getSharedPreferences("audiofreedom_sound_comparison", 0).edit()
            .remove("$route.a").remove("$route.b").remove("$route.selected").apply()
        AppPreferencesStore.save(context, originalPreferences)
        AudioFreedomSettingsStore.save(context, originalSettings)
    }

    @Test fun disablingPersistsCapsWithoutChangingElevenDbBand() {
        AudioFreedomSettingsStore.save(context, AudioFreedomSettings(extendedEqualizerRange = true,
            preampMillibels = -350, bandGainsMillibels = gains))
        assertEquals(gains, AudioFreedomSettingsStore.load(context).bandGainsMillibels)
        val preferences = AppPreferencesStore.load(context)
        AppPreferencesStore.save(context, preferences.copy(extendedEqualizerRange = false))
        assertEquals(listOf(1200, -1200, 1100), AudioFreedomSettingsStore.load(context).bandGainsMillibels.take(3))
        assertEquals(-350, AudioFreedomSettingsStore.load(context).preampMillibels)
        AppPreferencesStore.save(context, preferences)
        assertEquals(listOf(1200, -1200, 1100), AudioFreedomSettingsStore.load(context).bandGainsMillibels.take(3))
    }

    @Test fun profileLoadingCannotBypassTheDeviceUnlock() {
        val profile = AudioFreedomProfileStore.save(context, "Range test ${UUID.randomUUID()}",
            AudioFreedomSettings(extendedEqualizerRange = true, bandGainsMillibels = gains))
        profiles.add(profile.id)
        assertEquals(gains, AudioFreedomProfileStore.list(context).first { it.id == profile.id }.settings.bandGainsMillibels)
        AppPreferencesStore.save(context, AppPreferences())
        val recalled = AudioFreedomProfileStore.list(context).first { it.id == profile.id }.settings
        assertFalse(recalled.extendedEqualizerRange)
        assertEquals(listOf(1200, -1200, 1100), recalled.bandGainsMillibels.take(3))
        AudioFreedomSettingsStore.save(context, profile.settings)
        assertEquals(recalled.bandGainsMillibels, AudioFreedomSettingsStore.load(context).bandGainsMillibels)
        assertFalse(AppPreferencesStore.load(context).extendedEqualizerRange)
    }

    @Test fun comparisonRecallAndMissingAcknowledgementStayWithinStandardRange() {
        val snapshot = AudioFreedomSettings(extendedEqualizerRange = true, bandGainsMillibels = gains)
        SoundComparisonStore.save(context, route, SoundComparison(a = snapshot, b = snapshot))
        AppPreferencesStore.save(context, AppPreferences(extendedEqualizerRange = true))
        assertFalse(AppPreferencesStore.load(context).extendedEqualizerRange)
        val comparison = SoundComparisonStore.load(context, route)
        for (value in listOfNotNull(comparison.a, comparison.b)) {
            assertFalse(value.extendedEqualizerRange)
            assertEquals(listOf(1200, -1200, 1100), value.bandGainsMillibels.take(3))
        }
    }
}
