package com.svetlio.audiofreedom

import java.util.Locale

internal data class DspStreamInfo(
    val sequence: Int, val sampleRateHz: Int, val channels: Int, val ioFormat: Int,
) {
    fun label(): String = String.format(Locale.US, "DSP: %s kHz · 32-bit float · %d ch",
        if (sampleRateHz % 1000 == 0) (sampleRateHz / 1000).toString()
        else String.format(Locale.US, "%.1f", sampleRateHz / 1000.0), channels)
}

internal data class DspStreamFrame(val value: DspStreamInfo, val observedAt: Long) {
    fun isFresh(now: Long): Boolean = now >= observedAt && now - observedAt < 1500L
}

internal fun stereoRoutingSummary(settings: AudioFreedomSettings, stream: DspStreamInfo?): String = when {
    !settings.stereoRoutingEnabled -> "Off"
    stream != null && stream.channels != 2 -> "Bypassed · ${stream.channels}-channel stream"
    else -> settings.stereoRoutingMode.label
}

internal class DspStreamTracker {
    private var lastSequence: Int? = null
    private var frame: DspStreamFrame? = null
    fun update(value: DspStreamInfo?, now: Long): DspStreamFrame? {
        // Baseline first so a stale publication from an earlier route or service
        // attachment cannot be presented as newly observed playback.
        if (value != null) {
            if (lastSequence != null && lastSequence != value.sequence) frame = DspStreamFrame(value, now)
            lastSequence = value.sequence
        }
        return frame?.takeIf { it.isFresh(now) }
    }
    fun reset() { lastSequence = null; frame = null }
}
