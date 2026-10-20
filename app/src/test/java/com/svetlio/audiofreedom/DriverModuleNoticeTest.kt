package com.svetlio.audiofreedom

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverModuleNoticeTest {
    private val bundled = DriverModuleInfo("0.9.8-beta", 43)
    private fun installed(code: Int) =
        DriverModuleProbeResult.Installed(DriverModuleInfo("test", code))

    @Test fun olderInstalledModuleNeedsNotice() {
        assertTrue(needsDriverUpdateNotice(installed(36), bundled, 0, false))
        assertTrue(needsDriverUpdateNotice(installed(36), bundled, 42, false))
    }

    @Test fun currentAndNewerModulesDoNotNeedNotice() {
        assertFalse(needsDriverUpdateNotice(installed(43), bundled, 0, false))
        assertFalse(needsDriverUpdateNotice(installed(44), bundled, 0, false))
    }

    @Test fun acknowledgedModuleDoesNotNotifyAgainForAppOnlyUpdates() {
        assertFalse(needsDriverUpdateNotice(installed(36), bundled, 43, false))
        assertFalse(needsDriverUpdateNotice(installed(36), bundled, 44, false))
    }

    @Test fun pendingRestartSuppressesUpdateNotice() {
        assertFalse(needsDriverUpdateNotice(installed(36), bundled, 0, true))
    }

    @Test fun missingUnknownAndRootDeniedAreNotReportedAsOutdated() {
        for (result in listOf(DriverModuleProbeResult.Missing,
            DriverModuleProbeResult.RootUnavailable, DriverModuleProbeResult.Failed("failed"))) {
            assertFalse(needsDriverUpdateNotice(result, bundled, 0, false))
        }
    }

    @Test fun restartStateSurvivesAppRestartButNotPhoneRestart() {
        assertTrue(isDriverRestartPending(12, 12))
        assertFalse(isDriverRestartPending(12, 13))
        assertFalse(isDriverRestartPending(-1, -1))
        assertFalse(isDriverRestartPending(12, -1))
    }

    @Test fun extendedEqWarningOnlyAppliesToConfirmedOldModules() {
        assertTrue(needsExtendedEqualizerDriverUpdate(installed(36)))
        assertFalse(needsExtendedEqualizerDriverUpdate(installed(43)))
        assertFalse(needsExtendedEqualizerDriverUpdate(installed(44)))
        assertFalse(needsExtendedEqualizerDriverUpdate(DriverModuleProbeResult.Missing))
        assertFalse(needsExtendedEqualizerDriverUpdate(DriverModuleProbeResult.RootUnavailable))
        assertFalse(needsExtendedEqualizerDriverUpdate(DriverModuleProbeResult.Failed("unknown")))
    }
}
