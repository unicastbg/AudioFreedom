package com.svetlio.audiofreedom

internal data class PlaybackAnalysis(
    val sequence: Int,
    val inputRmsMillibels: Int,
    val outputRmsMillibels: Int,
    val crestMillibels: Int,
    val correlationPermille: Int?,
    val nearFullScalePpm: Int,
    val bandsMillibels: List<Int>,
)

internal data class PlaybackAnalysisFrame(val values: PlaybackAnalysis, val updatedAt: Long) {
    fun isFresh(now: Long): Boolean = now >= updatedAt && now - updatedAt < 1500L
}

internal class PlaybackAnalysisTracker {
    var frame: PlaybackAnalysisFrame? = null
        private set
    fun update(value: PlaybackAnalysis?, now: Long): PlaybackAnalysisFrame? {
        if (value != null && value.sequence != frame?.values?.sequence)
            frame = PlaybackAnalysisFrame(value, now)
        return frame
    }
    fun reset() { frame = null }
}
