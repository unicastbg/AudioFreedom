package com.svetlio.audiofreedom

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class PlaybackToolsTest {
    private fun metrics(sequence: Int = 42): ByteArray =
        AudioFreedomProtocol.playbackAnalysisQuery().also {
            ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(12, 24); putInt(16, sequence); putInt(20, -1900); putInt(24, -1800)
                putInt(28, 800); putInt(32, -500); putInt(36, 120)
            }
        }
    private fun spectrum(sequence: Int = 42): ByteArray =
        AudioFreedomProtocol.playbackAnalysisQuery(true).also {
            ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(12, 24); putInt(16, sequence)
                repeat(10) { band -> putShort(20 + 2 * band, (-2000 - band * 100).toShort()) }
            }
        }

    @Test fun telemetryDecodesSignedValuesAndMatchingWindow() {
        val value = AudioFreedomProtocol.readPlaybackAnalysis(metrics(), spectrum())!!
        assertEquals(-1900, value.inputRmsMillibels)
        assertEquals(-500, value.correlationPermille)
        assertEquals(-2900, value.bandsMillibels.last())
        assertNull(AudioFreedomProtocol.readPlaybackAnalysis(metrics(), spectrum(43)))
        assertNull(AudioFreedomProtocol.readPlaybackAnalysis(metrics(0), spectrum(0)))
    }
    @Test fun telemetryRejectsMalformedMessagesAndOutOfRangeValues() {
        assertNull(AudioFreedomProtocol.readPlaybackAnalysis(ByteArray(4), spectrum()))
        val bad = metrics()
        ByteBuffer.wrap(bad).order(ByteOrder.LITTLE_ENDIAN).putInt(36, -1)
        assertNull(AudioFreedomProtocol.readPlaybackAnalysis(bad, spectrum()))
        val wrong = spectrum().also { it[8] = 0 }
        assertNull(AudioFreedomProtocol.readPlaybackAnalysis(metrics(), wrong))
    }
    @Test fun unavailableCorrelationRemainsNull() {
        val mono = metrics()
        ByteBuffer.wrap(mono).order(ByteOrder.LITTLE_ENDIAN).putInt(32, 2000)
        assertNull(AudioFreedomProtocol.readPlaybackAnalysis(mono, spectrum())!!.correlationPermille)
    }
    @Test fun repeatedQueriesDoNotKeepStoppedPlaybackFresh() {
        val tracker = PlaybackAnalysisTracker()
        val values = AudioFreedomProtocol.readPlaybackAnalysis(metrics(), spectrum())!!
        tracker.update(values, 100)
        tracker.update(values, 1400)
        assertTrue(tracker.frame!!.isFresh(1500))
        assertFalse(tracker.frame!!.isFresh(1600))
        tracker.update(null, 2000)
        assertFalse(tracker.frame!!.isFresh(2000))
        tracker.update(values.copy(sequence = 43), 2100)
        assertTrue(tracker.frame!!.isFresh(2200))
        tracker.reset(); assertNull(tracker.frame)
    }
    @Test fun comparisonCapturesIndependentlyWithoutMutatingCurrentSettings() {
        val bands = MutableList(10) { 100 }
        val original = AudioFreedomSettings(bandGainsMillibels = bands)
        val modified = original.copy(reverbEnabled = true, volumeLevelerEnabled = true)
        val comparison = SoundComparison().capture(ComparisonSlot.A, original)
            .capture(ComparisonSlot.B, modified)
        bands[0] = 500
        assertEquals(100, comparison.a!!.bandGainsMillibels.first())
        assertFalse(comparison.a!!.reverbEnabled)
        assertTrue(comparison.b!!.reverbEnabled)
        assertEquals(ComparisonSlot.B, comparison.matching(modified.copy(bandGainsMillibels = List(10) { 100 })))
        assertNull(comparison.matching(AudioFreedomSettings()))
        assertNull(SoundComparison().settings(ComparisonSlot.A))
    }
    @Test fun sharedSnapshotCodecPreservesAllEffectsAndDefaultsForOldSnapshots() {
        val settings = AudioFreedomSettings(equalizerEnabled = true, preampMillibels = -600,
            reverbEnabled = true, volumeLevelerEnabled = true, levelerStrengthPercent = 82,
            immersiveFieldEnabled = true, dynamicBassEnabled = true, detailRecoveryEnabled = true)
        assertEquals(settings, SettingsSnapshotCodec.decode(SettingsSnapshotCodec.encode(settings)))
        assertEquals(AudioFreedomSettings(), SettingsSnapshotCodec.decode("{}"))
        assertNull(SettingsSnapshotCodec.decode("invalid"))
    }
    @Test fun selectingEmptyBCapturesChangesAndRetainsA() {
        val original = AudioFreedomSettings()
        val edited = original.copy(dynamicBassEnabled = true, bassBoostMillibels = 900)
        val comparison = SoundComparison().capture(ComparisonSlot.A, original)
            .activate(ComparisonSlot.B, edited)
        assertEquals(original, comparison.a)
        assertEquals(edited, comparison.b)
        assertEquals(ComparisonSlot.B, comparison.matching(edited))
        val switched = comparison.activate(ComparisonSlot.A, edited)
        assertEquals(original, switched.a)
        assertEquals(edited, switched.b)
        assertEquals(ComparisonSlot.A, switched.matching(original))
    }
    @Test fun identicalSnapshotsStillShowTheSelectedSlot() {
        val settings = AudioFreedomSettings()
        val comparison = SoundComparison().capture(ComparisonSlot.A, settings)
            .capture(ComparisonSlot.B, settings)
        assertEquals(ComparisonSlot.B, comparison.matching(settings))
        assertEquals(ComparisonSlot.A,
            comparison.activate(ComparisonSlot.A, settings).matching(settings))
        assertEquals(ComparisonSlot.B,
            comparison.activate(ComparisonSlot.B, settings).matching(settings))
        assertNull(comparison.matching(settings.copy(reverbEnabled = true)))
    }
    @Test fun replacingBCopiesCurrentSettingsWithoutOverwritingA() {
        val original = AudioFreedomSettings(preampMillibels = -200)
        val firstB = original.copy(reverbEnabled = true)
        val nextB = original.copy(volumeLevelerEnabled = true)
        val comparison = SoundComparison().capture(ComparisonSlot.A, original)
            .capture(ComparisonSlot.B, firstB).capture(ComparisonSlot.B, nextB)
        assertEquals(original, comparison.a)
        assertEquals(nextB, comparison.b)
        assertEquals(ComparisonSlot.B, comparison.preferredSlot)
    }
    @Test fun levelerPresetDescriptionsExplainDynamicsAndLowVolumeUse() {
        assertTrue(VolumeLevelerPreset.Gentle.description.contains("preserves"))
        assertTrue(VolumeLevelerPreset.Balanced.description.contains("Moderate"))
        assertTrue(VolumeLevelerPreset.Night.description.contains("quiet and loud"))
        assertTrue(VolumeLevelerPreset.Night.description.contains("low device volume"))
    }
    @Test fun analysisWireToggleUsesNewParameterWithoutChangingSettings() {
        val enabled = ByteBuffer.wrap(AudioFreedomProtocol.analysisEnabled(true)).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x1800, enabled.getInt(8))
        assertEquals(1, enabled.getInt(12))
        assertEquals(1, enabled.get(16).toInt())
        assertEquals(0, AudioFreedomProtocol.analysisEnabled(false)[16].toInt())
    }
}
