package com.svetlio.audiofreedom

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class DspStreamInfoTest {
    private fun info(sequence: Int, rate: Int = 48000) = DspStreamInfo(sequence, rate, 2, 1)
    @Test fun onlyAdvancingCallbacksBecomeLiveAndExpireWhenPlaybackStops() {
        val tracker = DspStreamTracker()
        assertNull(tracker.update(info(12), 100))
        assertNull(tracker.update(info(12), 200))
        assertEquals(48000, tracker.update(info(14), 300)?.value?.sampleRateHz)
        assertNotNull(tracker.update(info(14), 1799))
        assertNull(tracker.update(info(14), 1800))
        assertEquals(44100, tracker.update(info(16, 44100), 1900)?.value?.sampleRateHz)
    }
    @Test fun routeCallAndServiceResetRequireFreshCallbacksAgain() {
        val tracker = DspStreamTracker()
        tracker.update(info(1), 0)
        tracker.update(info(2), 250)
        tracker.reset()
        assertNull(tracker.update(info(2), 500))
        assertNotNull(tracker.update(info(3), 750))
        assertNull(tracker.update(null, 2250))
    }
    @Test fun sequenceWrapAndInternalFormatAreNotSourceQualityClaims() {
        val tracker = DspStreamTracker()
        tracker.update(info(-2), 0)
        assertNotNull(tracker.update(info(2), 250))
        assertEquals("DSP: 44.1 kHz · 32-bit float · 2 ch", info(1, 44100).label())
        assertEquals("DSP: 96 kHz · 32-bit float · 2 ch", info(1, 96000).label())
    }
    @Test fun validatesStreamWireContractAndRejectsUnsupportedValues() {
        val bytes = AudioFreedomProtocol.activeStreamQuery()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(12, 20); buffer.putInt(16, 42); buffer.putInt(20, 48000)
        buffer.putInt(24, 6); buffer.putInt(28, 1); buffer.putInt(32, 2)
        assertEquals(DspStreamInfo(42, 48000, 6, 2), AudioFreedomProtocol.readActiveStream(bytes))
        for ((offset, invalid) in listOf(12 to 24, 16 to 0, 20 to 0, 24 to 33, 28 to 2, 32 to 99)) {
            val copy = bytes.copyOf()
            ByteBuffer.wrap(copy).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, invalid)
            assertNull(AudioFreedomProtocol.readActiveStream(copy))
        }
        assertNull(AudioFreedomProtocol.readActiveStream(ByteArray(39)))
    }
}
