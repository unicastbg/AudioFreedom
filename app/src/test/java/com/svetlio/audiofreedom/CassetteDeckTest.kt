package com.svetlio.audiofreedom

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CassetteDeckTest {
    @Test fun reelsOnlyRunWithProcessingAndMeasuredAudio() {
        assertTrue(cassetteHasSignal(true, List(10) { -2500 }))
        assertFalse(cassetteHasSignal(false, List(10) { -2500 }))
        assertFalse(cassetteHasSignal(true, null))
        assertFalse(cassetteHasSignal(true, emptyList()))
        assertFalse(cassetteHasSignal(true, List(10) { -12000 }))
        assertFalse(cassetteHasSignal(true, List(10) { -9000 }))
        assertTrue(cassetteHasSignal(true, listOf(-8999)))
    }
}
