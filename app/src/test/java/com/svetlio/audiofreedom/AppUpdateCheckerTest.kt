package com.svetlio.audiofreedom

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {
    @Test
    fun detectsNewerBetaRelease() {
        assertTrue(isVersionNewer("0.9.3-beta", "0.9.2-beta"))
    }

    @Test
    fun stableReleaseSupersedesMatchingBeta() {
        assertTrue(isVersionNewer("0.9.2", "0.9.2-beta"))
    }

    @Test
    fun numericPreReleaseIdentifiersUseNumericOrdering() {
        assertTrue(isVersionNewer("1.0.0-beta.10", "1.0.0-beta.2"))
    }

    @Test
    fun equalOrOlderReleaseIsNotAnUpdate() {
        assertFalse(isVersionNewer("v0.9.2-beta", "0.9.2-beta"))
        assertFalse(isVersionNewer("0.9.1", "0.9.2-beta"))
    }

    @Test
    fun malformedVersionIsNotTreatedAsAnUpdate() {
        assertFalse(isVersionNewer("latest", "0.9.2-beta"))
        assertFalse(isVersionNewer("0.9.3", "development"))
    }
}
