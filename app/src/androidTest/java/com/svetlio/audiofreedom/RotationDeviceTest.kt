package com.svetlio.audiofreedom

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

class RotationDeviceTest {
    @Test fun recreationDoesNotReapplyLinkedProfileOrResetRouting() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyRotation") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val original = AudioFreedomSettingsStore.load(context)
        val route = AudioRouteDetector.current(context)
        val originalAssignment = AppPreferencesStore.profileForRoute(context, route.id)
        val originalPreferences = AppPreferencesStore.load(context)
        val originalEnabled = AudioFreedomService.isRequestedEnabled(context)
        var temporaryId: String? = null
        try {
            val profile = AudioFreedomProfileStore.save(context, "Rotation check ${UUID.randomUUID()}",
                original.copy(stereoRoutingEnabled = false))
            temporaryId = profile.id
            AppPreferencesStore.assignProfile(context, route.id, profile.id)
            AppPreferencesStore.save(context, originalPreferences.copy(automaticDeviceProfiles = true))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val edited = original.copy(stereoRoutingEnabled = true)
                instrumentation.runOnMainSync { AudioFreedomService.updateSettings(context, edited) }
                Thread.sleep(500)
                scenario.recreate()
                Thread.sleep(1500)
                assertEquals("Activity recreation reapplied the linked profile", edited,
                    AudioFreedomSettingsStore.load(context))
                assertEquals(originalEnabled, AudioFreedomService.isRequestedEnabled(context))
            }
        } finally {
            AppPreferencesStore.assignProfile(context, route.id, originalAssignment)
            temporaryId?.let { AudioFreedomProfileStore.delete(context, it) }
            AppPreferencesStore.save(context, originalPreferences)
            instrumentation.runOnMainSync {
                AudioFreedomService.updateSettings(context, original)
                if (AudioFreedomService.isRequestedEnabled(context) != originalEnabled)
                    AudioFreedomService.setEnabled(context, originalEnabled)
            }
        }
    }
}
