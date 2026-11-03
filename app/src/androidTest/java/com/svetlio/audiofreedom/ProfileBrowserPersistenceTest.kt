package com.svetlio.audiofreedom

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class ProfileBrowserPersistenceTest {
    private fun isolated(block: (Context) -> Unit) {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = UUID.randomUUID().toString()
        val names = mutableSetOf<String>()
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
                val isolated = "profile_test_${suffix}_$name"
                names.add(isolated)
                return base.getSharedPreferences(isolated, mode)
            }
        }
        try { block(context) } finally { names.forEach { base.deleteSharedPreferences(it) } }
    }

    @Test fun legacyProfilesAndDeviceLinksSurviveGroupingWithoutTouchingLiveEdits() = isolated { context ->
        val old = AudioFreedomProfileStore.save(context, "Clear", AudioFreedomSettings())
        AppPreferencesStore.assignProfile(context, "bluetooth-sony", old.id)
        val manual = AudioFreedomSettings(bandGainsMillibels = listOf(300) + List(9) { 0 })
        AudioFreedomSettingsStore.save(context, manual)
        assertEquals("", AudioFreedomProfileStore.list(context).single().deviceGroup)
        AudioFreedomProfileStore.save(context, "Clear", old.settings, old.id, "Sony Amp")
        assertEquals("Sony Amp", AudioFreedomProfileStore.list(context).single().deviceGroup)
        assertEquals(old.id, AppPreferencesStore.profileForRoute(context, "bluetooth-sony"))
        assertEquals(manual, AudioFreedomSettingsStore.load(context))
    }

    @Test fun groupsCanReuseNamesAndUpdatesPreserveMetadataAndOtherProfiles() = isolated { context ->
        val one = AudioFreedomProfileStore.save(context, "Clear", AudioFreedomSettings(), deviceGroup = "Sony Amp")
        val two = AudioFreedomProfileStore.save(context, "Clear", AudioFreedomSettings(), deviceGroup = "Buds")
        assertNotEquals(one.id, two.id)
        AudioFreedomProfileStore.save(context, "Clear", one.settings.copy(preampMillibels = -350), one.id)
        val profiles = AudioFreedomProfileStore.list(context)
        assertEquals(2, profiles.size)
        assertEquals("Sony Amp", profiles.first { it.id == one.id }.deviceGroup)
        assertEquals(two, profiles.first { it.id == two.id })
    }

    @Test fun deletingProfileClearsAllOfItsAssignmentsOnly() = isolated { context ->
        val one = AudioFreedomProfileStore.save(context, "One", AudioFreedomSettings())
        val two = AudioFreedomProfileStore.save(context, "Two", AudioFreedomSettings())
        for (route in listOf("speaker", "usb", "bluetooth")) AppPreferencesStore.assignProfile(context, route, one.id)
        AppPreferencesStore.assignProfile(context, "wired", two.id)
        AudioFreedomProfileStore.delete(context, one.id)
        for (route in listOf("speaker", "usb", "bluetooth")) assertNull(AppPreferencesStore.profileForRoute(context, route))
        assertEquals(two.id, AppPreferencesStore.profileForRoute(context, "wired"))
        assertEquals(listOf(two), AudioFreedomProfileStore.list(context))
    }
}
