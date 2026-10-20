package com.svetlio.audiofreedom

internal const val StandardEqualizerGainLimit = 1200
internal const val ExtendedEqualizerGainLimit = 2000

internal val AudioFreedomSettings.equalizerGainLimit: Int
    get() = if (extendedEqualizerRange) ExtendedEqualizerGainLimit else StandardEqualizerGainLimit

internal fun AudioFreedomSettings.withEqualizerRange(extended: Boolean): AudioFreedomSettings {
    val limit = if (extended) ExtendedEqualizerGainLimit else StandardEqualizerGainLimit
    return copy(extendedEqualizerRange = extended,
        bandGainsMillibels = bandGainsMillibels.map { it.coerceIn(-limit, limit) })
}
