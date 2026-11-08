package com.svetlio.audiofreedom

import org.junit.Assert.*
import org.junit.Test

class SpatialAudioPolicyTest {
    @Test fun startupRecoveryDoesNotRevokeFreshApproval() {
        assertFalse(clearsSpatialConsent(SpatialLeaseState.Recovering, SpatialLeaseState.Idle))
        assertTrue(clearsSpatialConsent(SpatialLeaseState.Restoring, SpatialLeaseState.Idle))
        assertTrue(clearsSpatialConsent(SpatialLeaseState.Active, SpatialLeaseState.Overridden))
        assertTrue(clearsSpatialConsent(SpatialLeaseState.Starting, SpatialLeaseState.Error))
        assertTrue(clearsSpatialConsent(SpatialLeaseState.Recovering, SpatialLeaseState.RestoreFailed))
        assertFalse(clearsSpatialConsent(SpatialLeaseState.Starting, SpatialLeaseState.Active))
    }

    @Test fun onlyBlockedNonNeutralStereoRoutingNeedsConsent() {
        val routed = AudioFreedomSettings(stereoRoutingEnabled = true, stereoRoutingMode = StereoRoutingMode.Mono)
        assertTrue(shouldRequestSpatialConsent(routed, true, 12, true, SpatialLeaseState.Idle))
        assertFalse(shouldRequestSpatialConsent(routed, true, 2, true, SpatialLeaseState.Idle))
        assertFalse(shouldRequestSpatialConsent(routed, true, 1, true, SpatialLeaseState.Idle))
        assertFalse(shouldRequestSpatialConsent(routed, true, null, true, SpatialLeaseState.Idle))
        assertFalse(shouldRequestSpatialConsent(routed, false, 12, true, SpatialLeaseState.Idle))
        assertFalse(shouldRequestSpatialConsent(routed, true, 12, false, SpatialLeaseState.Idle))
        assertFalse(shouldRequestSpatialConsent(routed, true, 12, null, SpatialLeaseState.Idle))
        assertFalse(shouldRequestSpatialConsent(routed.copy(stereoRoutingEnabled = false), true, 12, true, SpatialLeaseState.Idle))
        assertFalse(shouldRequestSpatialConsent(routed.copy(stereoRoutingMode = StereoRoutingMode.Normal), true, 12, true, SpatialLeaseState.Idle))
        assertTrue(shouldRequestSpatialConsent(routed.copy(stereoRoutingMode = StereoRoutingMode.Normal,
            stereoBalancePercent = 5), true, 12, true, SpatialLeaseState.Idle))
        for (state in SpatialLeaseState.entries.filter { it != SpatialLeaseState.Idle })
            assertFalse(shouldRequestSpatialConsent(routed, true, 12, true, state))
    }

    @Test fun installedReleaseNotesUseTheSameBoundedHighlights() {
        assertEquals(VersionElevenHighlights, installedReleaseHighlights("1.1.0"))
        assertEquals(VersionOneHighlights, installedReleaseHighlights("1.0.0"))
        assertTrue(installedReleaseHighlights("future").isEmpty())
        assertEquals(3, VersionElevenHighlights.size)
    }
}
