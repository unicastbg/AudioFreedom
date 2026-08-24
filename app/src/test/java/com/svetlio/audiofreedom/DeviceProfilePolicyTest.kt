package com.svetlio.audiofreedom

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProfilePolicyTest {
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
