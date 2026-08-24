package com.svetlio.audiofreedom

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioFreedomProtocolTest {
    @Test
    fun parameterKeyIsNonEmptyWireMagic() {
        assertArrayEquals(
            byteArrayOf(0x41, 0x46, 0x58, 0x31),
            AudioFreedomProtocol.parameterKey(),
        )
    }

    @Test
    fun preampMessageUsesLittleEndianWireFormat() {
        val message = AudioFreedomProtocol.preamp(-1200)

        assertEquals(40, message.size)
        assertArrayEquals(
            byteArrayOf(0x41, 0x46, 0x58, 0x31),
            message.copyOfRange(0, 4),
        )
        assertArrayEquals(byteArrayOf(1, 0, 5, 0), message.copyOfRange(4, 8))
        assertArrayEquals(byteArrayOf(1, 0x10, 0, 0), message.copyOfRange(8, 12))
        assertArrayEquals(byteArrayOf(0x50, 0xFB.toByte(), 0xFF.toByte(), 0xFF.toByte()),
            message.copyOfRange(16, 20))
    }

    @Test
    fun equalizerBandMessageCarriesBandAndSignedGain() {
        val message = AudioFreedomProtocol.equalizerBand(7, -350)

        assertArrayEquals(byteArrayOf(1, 0x11, 0, 0), message.copyOfRange(8, 12))
        assertArrayEquals(byteArrayOf(8, 0, 0, 0), message.copyOfRange(12, 16))
        assertArrayEquals(byteArrayOf(7, 0, 0, 0), message.copyOfRange(16, 20))
        assertArrayEquals(byteArrayOf(0xA2.toByte(), 0xFE.toByte(), 0xFF.toByte(), 0xFF.toByte()),
            message.copyOfRange(20, 24))
    }

    @Test
    fun equalizerConfigurationFillsSingleWirePayload() {
        val settings = EqualizerPreset.HeadphoneEnergy.settings
        val message = AudioFreedomProtocol.equalizerConfiguration(settings)

        assertArrayEquals(byteArrayOf(2, 0x11, 0, 0), message.copyOfRange(8, 12))
        assertArrayEquals(byteArrayOf(24, 0, 0, 0), message.copyOfRange(12, 16))
        assertArrayEquals(
            byteArrayOf(0xA8.toByte(), 0xFD.toByte(), 1, 0),
            message.copyOfRange(16, 20),
        )
        assertArrayEquals(
            byteArrayOf(0x5E, 0x01, 0xF4.toByte(), 0x01),
            message.copyOfRange(20, 24),
        )
    }

    @Test
    fun disabledEqualizerBypassesPresetPreamp() {
        val settings = EqualizerPreset.DeepBass.settings.copy(equalizerEnabled = false)
        val message = AudioFreedomProtocol.equalizerConfiguration(settings)

        assertArrayEquals(byteArrayOf(0, 0, 0, 0), message.copyOfRange(16, 20))
        assertEquals(-700, settings.preampMillibels)
    }

    @Test
    fun limiterChangesDoNotBreakEqualizerPresetMatching() {
        val settings = EqualizerPreset.HeadphoneEnergy.settings.copy(
            limiterEnabled = false,
            limiterThresholdMillibels = -600,
        )

        assertEquals(EqualizerPreset.HeadphoneEnergy, EqualizerPreset.matching(settings))
    }

    @Test
    fun limiterConfigurationCarriesAllSettingsAtomically() {
        val settings = AudioFreedomSettings(
            limiterEnabled = true,
            limiterThresholdMillibels = -150,
            limiterReleaseMilliseconds = 240,
        )
        val message = AudioFreedomProtocol.limiterConfiguration(settings)

        assertArrayEquals(byteArrayOf(0, 0x12, 0, 0), message.copyOfRange(8, 12))
        assertArrayEquals(byteArrayOf(12, 0, 0, 0), message.copyOfRange(12, 16))
        assertEquals(1, message[16].toInt())
        assertArrayEquals(
            byteArrayOf(0x6A, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
            message.copyOfRange(20, 24),
        )
        assertArrayEquals(
            byteArrayOf(0xF0.toByte(), 0, 0, 0),
            message.copyOfRange(24, 28),
        )
    }

    @Test
    fun dynamicBassConfigurationCarriesAllSettingsAtomically() {
        val settings = AudioFreedomSettings(
            dynamicBassEnabled = true,
            bassBoostMillibels = 750,
            bassCutoffHz = 65,
            bassDynamicsPercent = 80,
        )
        val message = AudioFreedomProtocol.dynamicBassConfiguration(settings)

        assertArrayEquals(byteArrayOf(0, 0x13, 0, 0), message.copyOfRange(8, 12))
        assertArrayEquals(byteArrayOf(16, 0, 0, 0), message.copyOfRange(12, 16))
        assertEquals(1, message[16].toInt())
        assertArrayEquals(byteArrayOf(0xEE.toByte(), 2, 0, 0), message.copyOfRange(20, 24))
        assertArrayEquals(byteArrayOf(65, 0, 0, 0), message.copyOfRange(24, 28))
        assertArrayEquals(byteArrayOf(80, 0, 0, 0), message.copyOfRange(28, 32))
    }

    @Test
    fun detailRecoveryConfigurationCarriesAllSettingsAtomically() {
        val settings = AudioFreedomSettings(
            detailRecoveryEnabled = true,
            detailAmountPercent = 65,
            detailFocusHz = 7000,
            detailTransientsPercent = 85,
        )
        val message = AudioFreedomProtocol.detailRecoveryConfiguration(settings)

        assertArrayEquals(byteArrayOf(0, 0x14, 0, 0), message.copyOfRange(8, 12))
        assertArrayEquals(byteArrayOf(16, 0, 0, 0), message.copyOfRange(12, 16))
        assertEquals(1, message[16].toInt())
        assertArrayEquals(byteArrayOf(65, 0, 0, 0), message.copyOfRange(20, 24))
        assertArrayEquals(
            byteArrayOf(0x58, 0x1B, 0, 0),
            message.copyOfRange(24, 28),
        )
        assertArrayEquals(byteArrayOf(85, 0, 0, 0), message.copyOfRange(28, 32))
    }

    @Test
    fun immersiveFieldConfigurationCarriesAllSettingsAtomically() {
        val settings = AudioFreedomSettings(
            immersiveFieldEnabled = true,
            immersiveAmountPercent = 70,
            immersiveWidthPercent = 80,
            immersiveCenterPercent = 65,
            immersiveRoomPercent = 35,
        )
        val message = AudioFreedomProtocol.immersiveFieldConfiguration(settings)

        assertArrayEquals(byteArrayOf(0, 0x15, 0, 0), message.copyOfRange(8, 12))
        assertArrayEquals(byteArrayOf(20, 0, 0, 0), message.copyOfRange(12, 16))
        assertEquals(1, message[16].toInt())
        assertArrayEquals(byteArrayOf(70, 0, 0, 0), message.copyOfRange(20, 24))
        assertArrayEquals(byteArrayOf(80, 0, 0, 0), message.copyOfRange(24, 28))
        assertArrayEquals(byteArrayOf(65, 0, 0, 0), message.copyOfRange(28, 32))
        assertArrayEquals(byteArrayOf(35, 0, 0, 0), message.copyOfRange(32, 36))
    }

    @Test
    fun outputMetricsRoundTripParsesSignedValues() {
        val response = AudioFreedomProtocol.outputMetricsQuery()
        response[12] = 12
        writeInt(response, 16, 320)
        writeInt(response, 20, -100)
        writeInt(response, 24, 420)

        assertEquals(
            AudioOutputMetrics(320, -100, 420),
            AudioFreedomProtocol.readOutputMetrics(response),
        )
    }

    @Test
    fun malformedOutputMetricsAreRejected() {
        assertEquals(null, AudioFreedomProtocol.readOutputMetrics(ByteArray(39)))
    }

    private fun writeInt(target: ByteArray, offset: Int, value: Int) {
        repeat(4) { index ->
            target[offset + index] = (value ushr (index * 8)).toByte()
        }
    }
}
