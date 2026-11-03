package com.svetlio.audiofreedom

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProfilePolicyTest {
    @Test fun stableRouteRefreshNeverOverwritesManualTweaks() {
        for (automatic in listOf(false, true)) for (requested in listOf(false, true))
            assertFalse(shouldApplyDeviceProfile(false, false, requested, automatic))
    }

    @Test fun routeChangesAndExplicitStartupRequireOptIn() {
        for (changed in listOf(false, true)) for (force in listOf(false, true))
            for (requested in listOf(false, true)) for (automatic in listOf(false, true)) {
                val expected = (changed || force) && requested && automatic
                org.junit.Assert.assertEquals(expected,
                    shouldApplyDeviceProfile(changed, force, requested, automatic))
            }
    }

    @Test fun callsVoipAndRedirectedAudioAlwaysBypassMediaDsp() {
        assertTrue(isMediaProcessingMode(0))
        for (mode in -2..8) if (mode != 0) assertFalse(isMediaProcessingMode(mode))
    }
    @Test
    fun initialServiceStartAppliesAssignedProfile() {
        assertTrue(shouldApplyAssignedProfileOnServiceStart(null))
    }

    @Test
    fun explicitServiceActionsPreserveManualSettings() {
        assertFalse(
            shouldApplyAssignedProfileOnServiceStart(
                "com.svetlio.audiofreedom.action.APPLY_SETTINGS",
            ),
        )
        assertFalse(
            shouldApplyAssignedProfileOnServiceStart(
                "com.svetlio.audiofreedom.action.REFRESH_STATUS",
            ),
        )
    }
}
