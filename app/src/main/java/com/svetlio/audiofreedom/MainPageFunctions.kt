package com.svetlio.audiofreedom

internal enum class MainPageFunction(val storageKey: String, val label: String, val isEffect: Boolean) {
    Equalizer("equalizer", "Equalizer", true),
    ImmersiveField("immersive_field", "Immersive field", true),
    Reverb("reverb", "Reverb", true),
    VolumeLeveler("volume_leveler", "Volume leveler", true),
    DetailRecovery("detail_recovery", "Detail recovery", true),
    BassFoundation("bass_foundation", "Bass Foundation", true),
    OutputProtection("output_protection", "Output protection", true),
    SoundComparison("sound_comparison", "A/B comparison", false),
    PlaybackAnalysis("playback_analysis", "Playback analysis", false),
}

internal fun AppPreferences.isVisible(function: MainPageFunction): Boolean =
    function !in hiddenMainPageFunctions

internal fun AppPreferences.withVisibility(function: MainPageFunction, visible: Boolean): AppPreferences =
    copy(hiddenMainPageFunctions = if (visible) hiddenMainPageFunctions - function
        else hiddenMainPageFunctions + function)

internal fun AudioFreedomSettings.withVisibleEffects(preferences: AppPreferences): AudioFreedomSettings =
    copy(
        equalizerEnabled = equalizerEnabled && preferences.isVisible(MainPageFunction.Equalizer),
        immersiveFieldEnabled = immersiveFieldEnabled && preferences.isVisible(MainPageFunction.ImmersiveField),
        reverbEnabled = reverbEnabled && preferences.isVisible(MainPageFunction.Reverb),
        volumeLevelerEnabled = volumeLevelerEnabled && preferences.isVisible(MainPageFunction.VolumeLeveler),
        detailRecoveryEnabled = detailRecoveryEnabled && preferences.isVisible(MainPageFunction.DetailRecovery),
        dynamicBassEnabled = dynamicBassEnabled && preferences.isVisible(MainPageFunction.BassFoundation),
        limiterEnabled = limiterEnabled && preferences.isVisible(MainPageFunction.OutputProtection),
    ).withEqualizerRange(preferences.extendedEqualizerRange && preferences.extendedEqualizerRangeAcknowledged)

internal fun hiddenMainPageFunctions(keys: Set<String>): Set<MainPageFunction> =
    MainPageFunction.entries.filterTo(mutableSetOf()) { it.storageKey in keys }
