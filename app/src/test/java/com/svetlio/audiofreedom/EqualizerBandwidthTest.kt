package com.svetlio.audiofreedom

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class EqualizerBandwidthTest {
    @Test fun defaultBandwidthAndOldSnapshotsKeepOriginalSound() {
        assertEquals(List(10) { 140 }, AudioFreedomSettings().bandQHundredths)
        val old = SettingsSnapshotCodec.decode("""{"equalizerEnabled":true,"preampMillibels":-200}""")!!
        assertEquals(List(10) { 140 }, old.bandQHundredths)
        assertFalse(old.stereoRoutingEnabled)
        assertEquals(StereoRoutingMode.Normal, old.stereoRoutingMode)
    }

    @Test fun snapshotsRoundTripNewSettingsAndClampInvalidValues() {
        val settings = AudioFreedomSettings(bandQHundredths = listOf(30,50,75,100,140,200,300,400,450,500),
            stereoRoutingEnabled = true, stereoRoutingMode = StereoRoutingMode.SingleOutput,
            stereoOutputRight = true, stereoBalancePercent = -40)
        assertEquals(settings, SettingsSnapshotCodec.decode(SettingsSnapshotCodec.encode(settings)))
        val bad = SettingsSnapshotCodec.decode("""{"bandQHundredths":[-1,9999],"stereoRoutingMode":99,"stereoBalancePercent":500}""")!!
        assertEquals(30, bad.bandQHundredths[0])
        assertEquals(500, bad.bandQHundredths[1])
        assertEquals(140, bad.bandQHundredths[2])
        assertEquals(100, bad.stereoBalancePercent)
        assertEquals(StereoRoutingMode.Normal, bad.stereoRoutingMode)
    }

    @Test fun wireUsesBoundedIntegerQAndNeutralRoutingWhenDisabled() {
        val settings = AudioFreedomSettings(bandQHundredths = List(10) { 500 },
            stereoRoutingMode = StereoRoutingMode.SingleOutput, stereoOutputRight = true,
            stereoBalancePercent = -25)
        val q = AudioFreedomProtocol.equalizerQ(settings)
        assertEquals(40, q.size)
        assertArrayEquals(byteArrayOf(3, 17, 0, 0), q.copyOfRange(8,12))
        assertArrayEquals(byteArrayOf(-12, 1), q.copyOfRange(16,18))
        assertTrue(AudioFreedomProtocol.stereoRouting(settings).slice(16..23).all { it == 0.toByte() })
        val right = AudioFreedomProtocol.stereoRouting(settings.copy(stereoRoutingEnabled = true))
        assertEquals(4, right[16].toInt())
        assertEquals(-25, java.nio.ByteBuffer.wrap(right, 20, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int)
    }

    @Test fun bandwidthChangesResponseButNotCenterGain() {
        val gains = List(10) { if (it == 5) 600 else 0 }
        val broad = List(10) { 30 }
        val narrow = List(10) { 500 }
        assertTrue(equalizerResponseDb(gains, broad, 1800.0) > equalizerResponseDb(gains, narrow, 1800.0) + 2)
        for (q in listOf(broad, narrow)) assertTrue(abs(equalizerResponseDb(gains, q, 1000.0) - 6f) < .01f)
        assertEquals(0f, equalizerResponseDb(List(10) { 0 }, broad, 1000.0), 0f)
        val adjacent = List(10) { if (it in 4..6) 600 else 0 }
        assertTrue(estimatedEqPeakBoost(adjacent, broad) > 1000)
    }

    @Test fun eqPresetDoesNotResetRoutingAndCustomQIsNotMislabeled() {
        val settings = AudioFreedomSettings(stereoRoutingEnabled = true, stereoRoutingMode = StereoRoutingMode.Swap)
        assertEquals(StereoRoutingMode.Swap, EqualizerPreset.Flat.applyTo(settings).stereoRoutingMode)
        assertTrue(EqualizerPreset.Flat.applyTo(settings).stereoRoutingEnabled)
        assertNull(EqualizerPreset.matching(EqualizerPreset.Flat.settings.copy(bandQHundredths = List(10) { 200 })))
        assertFalse(settings.withVisibleEffects(AppPreferences(hiddenMainPageFunctions = setOf(MainPageFunction.StereoRouting))).stereoRoutingEnabled)
    }
}
