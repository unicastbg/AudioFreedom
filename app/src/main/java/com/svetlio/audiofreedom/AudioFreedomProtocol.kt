package com.svetlio.audiofreedom

private const val WireMessageSize = 40
private const val WireHeaderSize = 16
private const val Magic = 0x31584641
private const val ProtocolMajor = 1
private const val ProtocolMinor = 5
private const val ParameterPreamp = 0x1001
private const val ParameterEqualizerEnabled = 0x1100
private const val ParameterEqualizerBand = 0x1101
private const val ParameterEqualizerConfiguration = 0x1102
private const val ParameterLimiterConfiguration = 0x1200
private const val ParameterDynamicBassConfiguration = 0x1300
private const val ParameterDetailRecoveryConfiguration = 0x1400
private const val ParameterImmersiveFieldConfiguration = 0x1500
private const val ParameterOutputMetrics = 0x2001

internal data class AudioOutputMetrics(
    val inputPeakMillibels: Int,
    val outputPeakMillibels: Int,
    val gainReductionMillibels: Int,
)

internal object AudioFreedomProtocol {
    fun parameterKey(): ByteArray = byteArrayOf(0x41, 0x46, 0x58, 0x31)

    fun preamp(millibels: Int): ByteArray = message(ParameterPreamp, 4).also {
        putInt(it, WireHeaderSize, millibels)
    }

    fun equalizerEnabled(enabled: Boolean): ByteArray =
        message(ParameterEqualizerEnabled, 1).also {
            it[WireHeaderSize] = if (enabled) 1 else 0
        }

    fun equalizerBand(band: Int, millibels: Int): ByteArray =
        message(ParameterEqualizerBand, 8).also {
            putInt(it, WireHeaderSize, band)
            putInt(it, WireHeaderSize + 4, millibels)
        }

    fun equalizerConfiguration(settings: AudioFreedomSettings): ByteArray =
        message(ParameterEqualizerConfiguration, 24).also { target ->
            putShort(
                target,
                WireHeaderSize,
                if (settings.equalizerEnabled) settings.preampMillibels else 0,
            )
            target[WireHeaderSize + 2] = if (settings.equalizerEnabled) 1 else 0
            settings.bandGainsMillibels.forEachIndexed { band, gain ->
                putShort(target, WireHeaderSize + 4 + band * 2, gain)
            }
        }

    fun limiterConfiguration(settings: AudioFreedomSettings): ByteArray =
        message(ParameterLimiterConfiguration, 12).also { target ->
            target[WireHeaderSize] = if (settings.limiterEnabled) 1 else 0
            putInt(target, WireHeaderSize + 4, settings.limiterThresholdMillibels)
            putInt(target, WireHeaderSize + 8, settings.limiterReleaseMilliseconds)
        }

    fun dynamicBassConfiguration(settings: AudioFreedomSettings): ByteArray =
        message(ParameterDynamicBassConfiguration, 16).also { target ->
            target[WireHeaderSize] = if (settings.dynamicBassEnabled) 1 else 0
            putInt(target, WireHeaderSize + 4, settings.bassBoostMillibels)
            putInt(target, WireHeaderSize + 8, settings.bassCutoffHz)
            putInt(target, WireHeaderSize + 12, settings.bassDynamicsPercent)
        }

    fun detailRecoveryConfiguration(settings: AudioFreedomSettings): ByteArray =
        message(ParameterDetailRecoveryConfiguration, 16).also { target ->
            target[WireHeaderSize] = if (settings.detailRecoveryEnabled) 1 else 0
            putInt(target, WireHeaderSize + 4, settings.detailAmountPercent)
            putInt(target, WireHeaderSize + 8, settings.detailFocusHz)
            putInt(target, WireHeaderSize + 12, settings.detailTransientsPercent)
        }

    fun immersiveFieldConfiguration(settings: AudioFreedomSettings): ByteArray =
        message(ParameterImmersiveFieldConfiguration, 20).also { target ->
            target[WireHeaderSize] = if (settings.immersiveFieldEnabled) 1 else 0
            putInt(target, WireHeaderSize + 4, settings.immersiveAmountPercent)
            putInt(target, WireHeaderSize + 8, settings.immersiveWidthPercent)
            putInt(target, WireHeaderSize + 12, settings.immersiveCenterPercent)
            putInt(target, WireHeaderSize + 16, settings.immersiveRoomPercent)
        }

    fun outputMetricsQuery(): ByteArray = message(ParameterOutputMetrics, 0)

    fun readOutputMetrics(message: ByteArray): AudioOutputMetrics? {
        if (message.size != WireMessageSize ||
            getInt(message, 0) != Magic ||
            getShort(message, 4) != ProtocolMajor ||
            getInt(message, 8) != ParameterOutputMetrics ||
            getInt(message, 12) != 12
        ) {
            return null
        }
        return AudioOutputMetrics(
            inputPeakMillibels = getInt(message, WireHeaderSize),
            outputPeakMillibels = getInt(message, WireHeaderSize + 4),
            gainReductionMillibels = getInt(message, WireHeaderSize + 8),
        )
    }

    private fun message(parameter: Int, payloadSize: Int): ByteArray =
        ByteArray(WireMessageSize).also {
            putInt(it, 0, Magic)
            putShort(it, 4, ProtocolMajor)
            putShort(it, 6, ProtocolMinor)
            putInt(it, 8, parameter)
            putInt(it, 12, payloadSize)
        }

    private fun putShort(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value ushr 8).toByte()
    }

    private fun putInt(target: ByteArray, offset: Int, value: Int) {
        repeat(4) { index ->
            target[offset + index] = (value ushr (index * 8)).toByte()
        }
    }

    private fun getShort(source: ByteArray, offset: Int): Int =
        (source[offset].toInt() and 0xFF) or
            ((source[offset + 1].toInt() and 0xFF) shl 8)

    private fun getInt(source: ByteArray, offset: Int): Int =
        (source[offset].toInt() and 0xFF) or
            ((source[offset + 1].toInt() and 0xFF) shl 8) or
            ((source[offset + 2].toInt() and 0xFF) shl 16) or
            (source[offset + 3].toInt() shl 24)
}
