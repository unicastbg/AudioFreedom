package com.svetlio.audiofreedom

import org.junit.Assert.*
import org.junit.Test

class VolumeHoldGestureTest {
    @Test fun shortPressAdjustsVolume() {
        val gesture = VolumeHoldGesture()
        assertTrue(gesture.begin(24, 1, 0, 1000))
        assertEquals(VolumeRelease.Tap, gesture.release(24, 1, 999))
        assertFalse(gesture.activate(1000))
    }
    @Test fun holdActivatesOnce() {
        val gesture = VolumeHoldGesture()
        gesture.begin(25, 1, 100, 1500)
        assertFalse(gesture.activate(1599))
        assertTrue(gesture.activate(1600))
        assertFalse(gesture.activate(1700))
        assertEquals(VolumeRelease.Consume, gesture.release(25, 1, 1800))
    }
    @Test fun releaseAtDeadlineActivatesWithoutTimer() {
        val gesture = VolumeHoldGesture()
        gesture.begin(24, 1, 0, 500)
        assertEquals(VolumeRelease.Activate, gesture.release(24, 1, 500))
        assertFalse(gesture.activate(501))
    }
    @Test fun cancellationConsumesRelease() {
        val gesture = VolumeHoldGesture()
        gesture.begin(24, 1, 0, 1000)
        gesture.cancel()
        assertFalse(gesture.activate(1000))
        assertEquals(VolumeRelease.Consume, gesture.release(24, 1, 1100))
    }
    @Test fun unrelatedDeviceCannotFinishGesture() {
        val gesture = VolumeHoldGesture()
        gesture.begin(24, 1, 0, 1000)
        assertEquals(VolumeRelease.Ignore, gesture.release(24, 2, 100))
        assertEquals(VolumeRelease.Tap, gesture.release(24, 1, 200))
    }
    @Test fun repeatCannotResetDeadline() {
        val gesture = VolumeHoldGesture()
        gesture.begin(24, 1, 0, 3000)
        assertFalse(gesture.begin(24, 1, 2000, 3000))
        assertTrue(gesture.activate(3000))
    }

    @Test fun interruptionConsumesTheWholePressWithoutVolumeAdjustment() {
        val gesture = VolumeHoldGesture()
        assertTrue(gesture.beginCancellation(24, 1))
        assertTrue(gesture.tracks(24, 1))
        assertFalse(gesture.activate(1000))
        assertEquals(VolumeRelease.Consume, gesture.release(24, 1, 1000))
        assertTrue(gesture.begin(24, 1, 1001, 500))
        assertEquals(VolumeRelease.Tap, gesture.release(24, 1, 1002))
    }

    @Test fun cancellationCannotReplaceAnOriginalHeldKey() {
        val gesture = VolumeHoldGesture()
        gesture.begin(24, 1, 0, 500)
        assertTrue(gesture.activate(500))
        assertFalse(gesture.beginCancellation(24, 2))
        assertEquals(VolumeRelease.Consume, gesture.release(24, 1, 600))
    }
}
