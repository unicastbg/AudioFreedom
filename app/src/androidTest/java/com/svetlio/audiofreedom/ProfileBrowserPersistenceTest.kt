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
        val one = AudioFreedomProfileStore.save(context, "Clear", AudioFreedomSettings(),
            deviceId = "bluetooth-sony", deviceLabel = "Sony Amp")
        val two = AudioFreedomProfileStore.save(context, "Clear", AudioFreedomSettings(),
            deviceId = "bluetooth-buds", deviceLabel = "Buds")
        assertNotEquals(one.id, two.id)
        AudioFreedomProfileStore.save(context, "Clear", one.settings.copy(preampMillibels = -350), one.id)
        val profiles = AudioFreedomProfileStore.list(context)
        assertEquals(2, profiles.size)
        assertEquals("bluetooth-sony", profiles.first { it.id == one.id }.deviceId)
        assertEquals("Sony Amp", profiles.first { it.id == one.id }.deviceLabel)
        assertEquals(two, profiles.first { it.id == two.id })
    }

    @Test fun renamingAnOutputGroupPreservesItsProfilesAndRememberedAssignment() = isolated { context ->
        val routeId = "bluetooth-sony"
        val saved = AudioFreedomProfileStore.save(context, "Clear", AudioFreedomSettings(),
            deviceId = routeId, deviceLabel = "Sony Amp")
        AppPreferencesStore.assignProfile(context, routeId, saved.id)
        AppPreferencesStore.setOutputGroupName(context, routeId, " Home HiFi ")
        AppPreferencesStore.setOutputGroupName(context, "", "Not a device")
        assertEquals(mapOf(routeId to "Home HiFi"), AppPreferencesStore.outputGroupNames(context))
        assertEquals(mapOf(routeId to saved.id), AppPreferencesStore.routeAssignments(context))
        assertEquals(saved, AudioFreedomProfileStore.list(context).single())
        AppPreferencesStore.setOutputGroupName(context, routeId, "")
        assertTrue(AppPreferencesStore.outputGroupNames(context).isEmpty())
        assertEquals(saved.id, AppPreferencesStore.profileForRoute(context, routeId))
    }

    @Test fun bandwidthAndRoutingSurviveSettingsProfilesAndSnapshots() = isolated { context ->
        val settings = AudioFreedomSettings(bandQHundredths = List(10) { 30 + it * 50 },
            stereoRoutingEnabled = true, stereoRoutingMode = StereoRoutingMode.SingleOutput,
            stereoOutputRight = true, stereoBalancePercent = 55)
        AudioFreedomSettingsStore.save(context, settings)
        assertEquals(settings, AudioFreedomSettingsStore.load(context))
        val profile = AudioFreedomProfileStore.save(context, "Custom Q", settings)
        assertEquals(settings, AudioFreedomProfileStore.list(context).single().settings)
        assertEquals(settings, SettingsSnapshotCodec.decode(SettingsSnapshotCodec.encode(settings)))
        AudioFreedomProfileStore.delete(context, profile.id)
        assertTrue(AudioFreedomProfileStore.list(context).isEmpty())
    }

    @Test fun managerMovesFiveProfilesWithoutSavingLiveSoundOrChangingDeviceLinks() = isolated { context ->
        val saved = (1..5).map { AudioFreedomProfileStore.save(context, "Sound $it",
            AudioFreedomSettings(bandQHundredths = List(10) { 200 + it })) }
        val live = AudioFreedomSettings(preampMillibels = -450)
        AudioFreedomSettingsStore.save(context, live)
        AppPreferencesStore.assignProfile(context, "bluetooth", saved.first().id)
        AudioFreedomProfileStore.manage(context, ProfileAction.Move(saved.map { it.id }.toSet(), "Sony Amp"))
        assertEquals(List(5) { "Sony Amp" }, AudioFreedomProfileStore.list(context).map { it.deviceGroup })
        assertEquals(saved.map { it.settings }, AudioFreedomProfileStore.list(context).map { it.settings })
        AudioFreedomProfileStore.manage(context, ProfileAction.RenameGroup("Sony Amp", "HiFi"))
        AudioFreedomProfileStore.manage(context, ProfileAction.Rename(saved.first().id, "Clear"))
        assertEquals(live, AudioFreedomSettingsStore.load(context))
        assertEquals(saved.first().id, AppPreferencesStore.profileForRoute(context, "bluetooth"))
        AudioFreedomProfileStore.manage(context, ProfileAction.Duplicate(saved.first().id))
        assertEquals(6, AudioFreedomProfileStore.list(context).size)
        assertEquals(saved.first().settings, AudioFreedomProfileStore.list(context).first { it.name == "Clear copy" }.settings)
        AudioFreedomProfileStore.manage(context, ProfileAction.RemoveGroup("HiFi"))
        assertTrue(AudioFreedomProfileStore.list(context).all { it.deviceGroup.isEmpty() })
        assertEquals(live, AudioFreedomSettingsStore.load(context))
        AudioFreedomProfileStore.manage(context, ProfileAction.Delete(setOf(saved.first().id)))
        assertNull(AppPreferencesStore.profileForRoute(context, "bluetooth"))
        assertEquals(live, AudioFreedomSettingsStore.load(context))
    }

    @Test fun failedMovesAreAtomicAndCopiesPreserveRawExtendedEq() = isolated { context ->
        val one = AudioFreedomProfileStore.save(context, "Clear", AudioFreedomSettings(), deviceGroup = "A")
        val two = AudioFreedomProfileStore.save(context, "Clear", AudioFreedomSettings(),
            profileId = "legacy-b", deviceGroup = "B")
        assertThrows(IllegalArgumentException::class.java) {
            AudioFreedomProfileStore.manage(context, ProfileAction.Move(setOf(one.id), "B"))
        }
        assertEquals("A", AudioFreedomProfileStore.list(context).first { it.id == one.id }.deviceGroup)
        assertEquals("B", AudioFreedomProfileStore.list(context).first { it.id == two.id }.deviceGroup)
        val preferences = context.getSharedPreferences("audiofreedom_profiles", Context.MODE_PRIVATE)
        preferences.edit().putInt("profile_${one.id}_band_0", 1800).apply()
        AudioFreedomProfileStore.manage(context, ProfileAction.Duplicate(one.id))
        val copy = AudioFreedomProfileStore.list(context).first { it.name == "Clear copy" }
        assertEquals(1800, preferences.getInt("profile_${copy.id}_band_0", 0))
        AudioFreedomProfileStore.manage(context, ProfileAction.Move(setOf(one.id), "C"))
        assertEquals(1800, preferences.getInt("profile_${one.id}_band_0", 0))
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
