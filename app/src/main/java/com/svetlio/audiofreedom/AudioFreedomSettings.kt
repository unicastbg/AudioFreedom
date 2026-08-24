package com.svetlio.audiofreedom

import android.content.Context

internal const val EqualizerBandCount = 10

internal val EqualizerBandLabels = listOf(
    "31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k",
)

internal data class AudioFreedomSettings(
    val equalizerEnabled: Boolean = false,
    val preampMillibels: Int = 0,
    val bandGainsMillibels: List<Int> = List(EqualizerBandCount) { 0 },
    val dynamicBassEnabled: Boolean = false,
    val bassBoostMillibels: Int = 600,
    val bassCutoffHz: Int = 95,
    val bassDynamicsPercent: Int = 20,
    val detailRecoveryEnabled: Boolean = false,
    val detailAmountPercent: Int = 55,
    val detailFocusHz: Int = 6000,
    val detailTransientsPercent: Int = 75,
    val immersiveFieldEnabled: Boolean = false,
    val immersiveAmountPercent: Int = 55,
    val immersiveWidthPercent: Int = 60,
    val immersiveCenterPercent: Int = 60,
    val immersiveRoomPercent: Int = 25,
    val limiterEnabled: Boolean = true,
    val limiterThresholdMillibels: Int = -100,
    val limiterReleaseMilliseconds: Int = 120,
) {
    init {
        require(bandGainsMillibels.size == EqualizerBandCount)
    }
}

internal fun EqualizerPreset.applyTo(current: AudioFreedomSettings): AudioFreedomSettings =
    settings.copy(
        dynamicBassEnabled = current.dynamicBassEnabled,
        bassBoostMillibels = current.bassBoostMillibels,
        bassCutoffHz = current.bassCutoffHz,
        bassDynamicsPercent = current.bassDynamicsPercent,
        detailRecoveryEnabled = current.detailRecoveryEnabled,
        detailAmountPercent = current.detailAmountPercent,
        detailFocusHz = current.detailFocusHz,
        detailTransientsPercent = current.detailTransientsPercent,
        immersiveFieldEnabled = current.immersiveFieldEnabled,
        immersiveAmountPercent = current.immersiveAmountPercent,
        immersiveWidthPercent = current.immersiveWidthPercent,
        immersiveCenterPercent = current.immersiveCenterPercent,
        immersiveRoomPercent = current.immersiveRoomPercent,
        limiterEnabled = current.limiterEnabled,
        limiterThresholdMillibels = current.limiterThresholdMillibels,
        limiterReleaseMilliseconds = current.limiterReleaseMilliseconds,
    )

internal enum class BassFoundationPreset(
    val label: String,
    val cutoffHz: Int,
    val smallDriverSupportPercent: Int,
) {
    DeepHeadphones("Deep-capable headphones", 70, 10),
    BalancedHeadphones("Balanced headphones", 95, 20),
    CompactHeadphones("Compact headphones", 110, 40),
    Earbuds("Earbuds", 130, 65),
    PhoneSpeaker("Phone speaker", 150, 90),
    ;

    fun applyTo(settings: AudioFreedomSettings): AudioFreedomSettings = settings.copy(
        dynamicBassEnabled = true,
        bassCutoffHz = cutoffHz,
        bassDynamicsPercent = smallDriverSupportPercent,
    )

    companion object {
        fun matching(settings: AudioFreedomSettings): BassFoundationPreset? =
            entries.firstOrNull {
                it.cutoffHz == settings.bassCutoffHz &&
                    it.smallDriverSupportPercent == settings.bassDynamicsPercent
            }
    }
}

internal enum class ImmersiveFieldPreset(
    val label: String,
    val amountPercent: Int,
    val widthPercent: Int,
    val centerPercent: Int,
    val roomPercent: Int,
) {
    Natural("Natural stage", 65, 60, 70, 30),
    WideMusic("Wide music", 90, 95, 55, 45),
    Cinema("Cinema", 100, 90, 75, 80),
    FrontStage("Front stage", 75, 65, 90, 45),
    ;

    fun applyTo(settings: AudioFreedomSettings): AudioFreedomSettings = settings.copy(
        immersiveFieldEnabled = true,
        immersiveAmountPercent = amountPercent,
        immersiveWidthPercent = widthPercent,
        immersiveCenterPercent = centerPercent,
        immersiveRoomPercent = roomPercent,
    )

    companion object {
        fun matching(settings: AudioFreedomSettings): ImmersiveFieldPreset? =
            entries.firstOrNull {
                it.amountPercent == settings.immersiveAmountPercent &&
                    it.widthPercent == settings.immersiveWidthPercent &&
                    it.centerPercent == settings.immersiveCenterPercent &&
                    it.roomPercent == settings.immersiveRoomPercent
            }
    }
}

internal enum class DetailRecoveryPreset(
    val label: String,
    val amountPercent: Int,
    val focusHz: Int,
    val transientsPercent: Int,
) {
    Gentle("Gentle", 40, 7500, 50),
    Clear("Clear", 65, 6500, 70),
    Crisp("Crisp", 90, 5500, 85),
    SoftRecordings("Soft recordings", 75, 8000, 40),
    ;

    fun applyTo(settings: AudioFreedomSettings): AudioFreedomSettings = settings.copy(
        detailRecoveryEnabled = true,
        detailAmountPercent = amountPercent,
        detailFocusHz = focusHz,
        detailTransientsPercent = transientsPercent,
    )

    companion object {
        fun matching(settings: AudioFreedomSettings): DetailRecoveryPreset? =
            entries.firstOrNull {
                it.amountPercent == settings.detailAmountPercent &&
                    it.focusHz == settings.detailFocusHz &&
                    it.transientsPercent == settings.detailTransientsPercent
            }
    }
}

internal enum class EqualizerPreset(
    val label: String,
    val settings: AudioFreedomSettings,
) {
    Flat(
        "Flat",
        AudioFreedomSettings(equalizerEnabled = false, preampMillibels = 0),
    ),
    Balanced(
        "Balanced",
        AudioFreedomSettings(
            equalizerEnabled = true,
            preampMillibels = -200,
            bandGainsMillibels = listOf(0, 100, 100, 0, -100, 0, 100, 100, 100, 0),
        ),
    ),
    DeepBass(
        "Deep bass",
        AudioFreedomSettings(
            equalizerEnabled = true,
            preampMillibels = -700,
            bandGainsMillibels = listOf(450, 600, 400, 100, 0, -100, 0, 100, 100, 0),
        ),
    ),
    HeadphoneEnergy(
        "Headphone energy",
        AudioFreedomSettings(
            equalizerEnabled = true,
            preampMillibels = -600,
            bandGainsMillibels = listOf(350, 500, 300, 0, -100, -100, 100, 200, 200, 100),
        ),
    ),
    ;

    companion object {
        fun matching(settings: AudioFreedomSettings): EqualizerPreset? =
            entries.firstOrNull {
                it.settings.equalizerEnabled == settings.equalizerEnabled &&
                    it.settings.preampMillibels == settings.preampMillibels &&
                    it.settings.bandGainsMillibels == settings.bandGainsMillibels
            }
    }
}

internal object AudioFreedomSettingsStore {
    private const val Preferences = "audiofreedom"
    private const val KeyEqualizerEnabled = "equalizer_enabled"
    private const val KeyPreamp = "preamp_millibels"
    private const val KeyBandPrefix = "equalizer_band_"
    private const val KeyDynamicBassEnabled = "dynamic_bass_enabled"
    private const val KeyBassBoost = "bass_boost_millibels"
    private const val KeyBassCutoff = "bass_cutoff_hz"
    private const val KeyBassDynamics = "bass_dynamics_percent"
    private const val KeyDetailRecoveryEnabled = "detail_recovery_enabled"
    private const val KeyDetailAmount = "detail_amount_percent"
    private const val KeyDetailFocus = "detail_focus_hz"
    private const val KeyDetailTransients = "detail_transients_percent"
    private const val KeyImmersiveFieldEnabled = "immersive_field_enabled"
    private const val KeyImmersiveAmount = "immersive_amount_percent"
    private const val KeyImmersiveWidth = "immersive_width_percent"
    private const val KeyImmersiveCenter = "immersive_center_percent"
    private const val KeyImmersiveRoom = "immersive_room_percent"
    private const val KeyLimiterEnabled = "limiter_enabled"
    private const val KeyLimiterThreshold = "limiter_threshold_millibels"
    private const val KeyLimiterRelease = "limiter_release_milliseconds"

    fun load(context: Context): AudioFreedomSettings {
        val preferences = context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
        return AudioFreedomSettings(
            equalizerEnabled = preferences.getBoolean(KeyEqualizerEnabled, false),
            preampMillibels = preferences.getInt(KeyPreamp, 0).coerceIn(-2400, 0),
            bandGainsMillibels = List(EqualizerBandCount) { band ->
                preferences.getInt("$KeyBandPrefix$band", 0).coerceIn(-1200, 1200)
            },
            dynamicBassEnabled = preferences.getBoolean(KeyDynamicBassEnabled, false),
            bassBoostMillibels = preferences.getInt(KeyBassBoost, 600).coerceIn(0, 1200),
            bassCutoffHz = preferences.getInt(KeyBassCutoff, 95).coerceIn(40, 160),
            bassDynamicsPercent = preferences.getInt(KeyBassDynamics, 20).coerceIn(0, 100),
            detailRecoveryEnabled = preferences.getBoolean(KeyDetailRecoveryEnabled, false),
            detailAmountPercent = preferences.getInt(KeyDetailAmount, 55).coerceIn(0, 100),
            detailFocusHz = preferences.getInt(KeyDetailFocus, 6000).coerceIn(3000, 10000),
            detailTransientsPercent =
                preferences.getInt(KeyDetailTransients, 75).coerceIn(0, 100),
            immersiveFieldEnabled = preferences.getBoolean(KeyImmersiveFieldEnabled, false),
            immersiveAmountPercent =
                preferences.getInt(KeyImmersiveAmount, 55).coerceIn(0, 100),
            immersiveWidthPercent =
                preferences.getInt(KeyImmersiveWidth, 60).coerceIn(0, 100),
            immersiveCenterPercent =
                preferences.getInt(KeyImmersiveCenter, 60).coerceIn(0, 100),
            immersiveRoomPercent =
                preferences.getInt(KeyImmersiveRoom, 25).coerceIn(0, 100),
            limiterEnabled = preferences.getBoolean(KeyLimiterEnabled, true),
            limiterThresholdMillibels =
                preferences.getInt(KeyLimiterThreshold, -100).coerceIn(-600, 0),
            limiterReleaseMilliseconds =
                preferences.getInt(KeyLimiterRelease, 120).coerceIn(20, 1000),
        )
    }

    fun save(context: Context, settings: AudioFreedomSettings) {
        val editor = context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KeyEqualizerEnabled, settings.equalizerEnabled)
            .putInt(KeyPreamp, settings.preampMillibels.coerceIn(-2400, 0))
            .putBoolean(KeyDynamicBassEnabled, settings.dynamicBassEnabled)
            .putInt(KeyBassBoost, settings.bassBoostMillibels.coerceIn(0, 1200))
            .putInt(KeyBassCutoff, settings.bassCutoffHz.coerceIn(40, 160))
            .putInt(KeyBassDynamics, settings.bassDynamicsPercent.coerceIn(0, 100))
            .putBoolean(KeyDetailRecoveryEnabled, settings.detailRecoveryEnabled)
            .putInt(KeyDetailAmount, settings.detailAmountPercent.coerceIn(0, 100))
            .putInt(KeyDetailFocus, settings.detailFocusHz.coerceIn(3000, 10000))
            .putInt(KeyDetailTransients, settings.detailTransientsPercent.coerceIn(0, 100))
            .putBoolean(KeyImmersiveFieldEnabled, settings.immersiveFieldEnabled)
            .putInt(KeyImmersiveAmount, settings.immersiveAmountPercent.coerceIn(0, 100))
            .putInt(KeyImmersiveWidth, settings.immersiveWidthPercent.coerceIn(0, 100))
            .putInt(KeyImmersiveCenter, settings.immersiveCenterPercent.coerceIn(0, 100))
            .putInt(KeyImmersiveRoom, settings.immersiveRoomPercent.coerceIn(0, 100))
            .putBoolean(KeyLimiterEnabled, settings.limiterEnabled)
            .putInt(KeyLimiterThreshold, settings.limiterThresholdMillibels.coerceIn(-600, 0))
            .putInt(KeyLimiterRelease, settings.limiterReleaseMilliseconds.coerceIn(20, 1000))
        settings.bandGainsMillibels.forEachIndexed { band, gain ->
            editor.putInt("$KeyBandPrefix$band", gain.coerceIn(-1200, 1200))
        }
        editor.apply()
    }
}
