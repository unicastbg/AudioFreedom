package com.svetlio.audiofreedom

import android.content.Context

internal enum class ThemePreference(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark"),
}

/**
 * Visual theme. Independent of [ThemePreference] (light/dark mode): every theme except
 * Studio has a light and a dark variant and follows that setting.
 */
internal enum class AppTheme(
    val label: String,
    val summary: String,
    val supportsLight: Boolean = true,
) {
    Classic("Classic", "The original AudioFreedom look"),
    Mocha("Mocha", "Soft pastels, matches Media Player"),
    Daylight("Daylight", "Clean and calm, one coherent green"),
    Studio("Studio", "Hardware rack with amber accents, always dark", supportsLight = false),
}

// Legacy values are retained internally but are no longer offered in settings.
internal enum class AssistantLanguage(val label: String) {
    Automatic("Automatic"),
    English("English"),
    Bulgarian("Bulgarian"),
}

internal enum class AssistantApplyMode(val label: String, val summary: String) {
    Preview(
        "Always preview",
        "Show every proposed DSP change before applying it",
    ),
    SafeAutomatic(
        "Apply small changes",
        "Apply validated tonal changes automatically and keep an undo snapshot",
    ),
}

internal enum class AssistantAdjustmentStrength(
    val label: String,
    val maximumBandDeltaMillibels: Int,
) {
    Conservative("Conservative", 150),
    Balanced("Balanced", 300),
}

internal enum class AssistantKeepWarm(val label: String) {
    KeepReady("Keep ready"),
    FiveMinutes("5 minutes"),
    OneMinute("1 minute"),
}

internal enum class AssistantVoiceMode(val label: String) {
    PushToTalk("Push to talk"),
    VolumeButton("Volume button"),
}

internal enum class AssistantVolumeButton(val label: String, val keyCode: Int) {
    Up("Volume Up", 24),
    Down("Volume Down", 25),
}

internal data class AppPreferences(
    val theme: ThemePreference = ThemePreference.System,
    val appTheme: AppTheme = AppTheme.Classic,
    val automaticDeviceProfiles: Boolean = false,
    val showDeviceInNotification: Boolean = true,
    val hiddenMainPageFunctions: Set<MainPageFunction> = emptySet(),
    val assistantEnabled: Boolean = false,
    val assistantConversationalTuning: Boolean = false,
    val assistantLanguage: AssistantLanguage = AssistantLanguage.English,
    val assistantApplyMode: AssistantApplyMode = AssistantApplyMode.Preview,
    val assistantAdjustmentStrength: AssistantAdjustmentStrength =
        AssistantAdjustmentStrength.Conservative,
    val assistantVoiceEnabled: Boolean = false,
    val assistantVoiceMode: AssistantVoiceMode = AssistantVoiceMode.PushToTalk,
    val assistantVolumeButton: AssistantVolumeButton = AssistantVolumeButton.Up,
    val assistantButtonHoldMilliseconds: Int = 1000,
    val assistantSilenceMilliseconds: Int = 1000,
    val assistantSpeechWaitSeconds: Int = 8,
    val assistantConfirmationWaitSeconds: Int = 8,
    val assistantAutoSubmitVoice: Boolean = false,
    val assistantPauseMediaForVoice: Boolean = true,
    val assistantSpeakVoiceConfirmation: Boolean = true,
    val assistantEnglishSpeechVoice: String = "",
    val assistantAskForFeedback: Boolean = true,
    val assistantKeepWarm: AssistantKeepWarm = AssistantKeepWarm.KeepReady,
    val assistantWifiOnlyDownloads: Boolean = true,
    /**
     * Pass explicit music requests to Media Player before DSP interpretation.
     */
    val assistantForwardMusicCommands: Boolean = true,
)

internal object AppPreferencesStore {
    private const val Preferences = "audiofreedom_app"
    private const val KeyTheme = "theme"
    private const val KeyAppTheme = "app_theme"
    private const val KeyAutomaticDeviceProfiles = "automatic_device_profiles"
    private const val KeyShowDeviceInNotification = "show_device_in_notification"
    private const val KeyHiddenMainPageFunctions = "hidden_main_page_functions"
    private const val KeyAssistantEnabled = "assistant_enabled"
    private const val KeyAssistantLanguage = "assistant_language"
    private const val KeyAssistantApplyMode = "assistant_apply_mode"
    private const val KeyAssistantAdjustmentStrength = "assistant_adjustment_strength"
    private const val KeyAssistantVoiceEnabled = "assistant_voice_enabled"
    private const val KeyAssistantVoiceMode = "assistant_voice_mode"
    private const val KeyAssistantAutoSubmitVoice = "assistant_auto_submit_voice"
    private const val KeyAssistantPauseMediaForVoice = "assistant_pause_media_for_voice"
    private const val KeyAssistantSpeakVoiceConfirmation =
        "assistant_speak_voice_confirmation"
    private const val KeyAssistantAskForFeedback = "assistant_ask_for_feedback"
    private const val KeyAssistantKeepWarm = "assistant_keep_warm"
    private const val KeyAssistantWifiOnlyDownloads = "assistant_wifi_only_downloads"
    private const val KeyAssistantForwardMusicCommands =
        "assistant_forward_music_commands"
    private const val KeyRouteProfilePrefix = "route_profile_"

    fun load(context: Context): AppPreferences {
        val preferences = context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
        if (preferences.getString(KeyAssistantLanguage, null) != AssistantLanguage.English.name ||
            preferences.contains("assistant_bulgarian_speech_voice")) {
            preferences.edit().putString(KeyAssistantLanguage, AssistantLanguage.English.name)
                .remove("assistant_bulgarian_speech_voice").apply()
        }
        val theme = runCatching {
            ThemePreference.valueOf(
                preferences.getString(KeyTheme, ThemePreference.System.name).orEmpty(),
            )
        }.getOrDefault(ThemePreference.System)
        return AppPreferences(
            theme = theme,
            appTheme = preferences.enumValue(KeyAppTheme, AppTheme.Classic),
            automaticDeviceProfiles = preferences.getBoolean(KeyAutomaticDeviceProfiles, false),
            showDeviceInNotification =
                preferences.getBoolean(KeyShowDeviceInNotification, true),
            hiddenMainPageFunctions = hiddenMainPageFunctions(
                preferences.getStringSet(KeyHiddenMainPageFunctions, emptySet()).orEmpty(),
            ),
            assistantEnabled = preferences.getBoolean(KeyAssistantEnabled, false),
            assistantConversationalTuning = preferences.getBoolean("assistant_conversational_tuning", false),
            assistantLanguage = AssistantLanguage.English,
            assistantApplyMode = preferences.enumValue(
                KeyAssistantApplyMode,
                AssistantApplyMode.Preview,
            ),
            assistantAdjustmentStrength = preferences.enumValue(
                KeyAssistantAdjustmentStrength,
                AssistantAdjustmentStrength.Conservative,
            ),
            assistantVoiceEnabled = preferences.getBoolean(KeyAssistantVoiceEnabled, false),
            assistantVoiceMode = preferences.enumValue(
                KeyAssistantVoiceMode,
                AssistantVoiceMode.PushToTalk,
            ),
            assistantVolumeButton = preferences.enumValue("assistant_volume_button", AssistantVolumeButton.Up),
            assistantButtonHoldMilliseconds = preferences.getInt("assistant_button_hold_ms", 1000)
                .coerceIn(500, 3000),
            assistantSilenceMilliseconds = preferences.getInt("assistant_silence_ms", 1000)
                .coerceIn(500, 3000),
            assistantSpeechWaitSeconds = preferences.getInt("assistant_speech_wait_seconds", 8)
                .coerceIn(3, 15),
            assistantConfirmationWaitSeconds = preferences.getInt("assistant_confirm_wait_seconds", 8)
                .coerceIn(3, 15),
            assistantAutoSubmitVoice =
                preferences.getBoolean(KeyAssistantAutoSubmitVoice, false),
            assistantPauseMediaForVoice =
                preferences.getBoolean(KeyAssistantPauseMediaForVoice, true),
            assistantSpeakVoiceConfirmation =
                preferences.getBoolean(KeyAssistantSpeakVoiceConfirmation, true),
            assistantEnglishSpeechVoice = preferences.getString("assistant_english_speech_voice", "").orEmpty(),
            assistantAskForFeedback = preferences.getBoolean(KeyAssistantAskForFeedback, true),
            assistantKeepWarm = preferences.enumValue(
                KeyAssistantKeepWarm,
                AssistantKeepWarm.KeepReady,
            ),
            assistantWifiOnlyDownloads =
                preferences.getBoolean(KeyAssistantWifiOnlyDownloads, true),
            assistantForwardMusicCommands =
                preferences.getBoolean(KeyAssistantForwardMusicCommands, true),
        )
    }

    fun save(context: Context, preferences: AppPreferences) {
        val previousHidden = load(context).hiddenMainPageFunctions
        context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
            .edit()
            .putString(KeyTheme, preferences.theme.name)
            .putString(KeyAppTheme, preferences.appTheme.name)
            .putBoolean(KeyAutomaticDeviceProfiles, preferences.automaticDeviceProfiles)
            .putBoolean(KeyShowDeviceInNotification, preferences.showDeviceInNotification)
            .putStringSet(KeyHiddenMainPageFunctions,
                preferences.hiddenMainPageFunctions.map { it.storageKey }.toSet())
            .putBoolean(KeyAssistantEnabled, preferences.assistantEnabled)
            .putBoolean("assistant_conversational_tuning", preferences.assistantConversationalTuning)
            .putString(KeyAssistantLanguage, AssistantLanguage.English.name)
            .putString(KeyAssistantApplyMode, preferences.assistantApplyMode.name)
            .putString(
                KeyAssistantAdjustmentStrength,
                preferences.assistantAdjustmentStrength.name,
            )
            .putBoolean(KeyAssistantVoiceEnabled, preferences.assistantVoiceEnabled)
            .putString(KeyAssistantVoiceMode, preferences.assistantVoiceMode.name)
            .remove("assistant_wake_phrase")
            .putString("assistant_volume_button", preferences.assistantVolumeButton.name)
            .putInt("assistant_button_hold_ms", preferences.assistantButtonHoldMilliseconds.coerceIn(500, 3000))
            .putInt("assistant_silence_ms", preferences.assistantSilenceMilliseconds.coerceIn(500, 3000))
            .putInt("assistant_speech_wait_seconds", preferences.assistantSpeechWaitSeconds.coerceIn(3, 15))
            .putInt("assistant_confirm_wait_seconds", preferences.assistantConfirmationWaitSeconds.coerceIn(3, 15))
            .putBoolean(KeyAssistantAutoSubmitVoice, preferences.assistantAutoSubmitVoice)
            .putBoolean(
                KeyAssistantPauseMediaForVoice,
                preferences.assistantPauseMediaForVoice,
            )
            .putBoolean(
                KeyAssistantSpeakVoiceConfirmation,
                preferences.assistantSpeakVoiceConfirmation,
            )
            .putBoolean(KeyAssistantAskForFeedback, preferences.assistantAskForFeedback)
            .putString("assistant_english_speech_voice", preferences.assistantEnglishSpeechVoice)
            .remove("assistant_bulgarian_speech_voice")
            .putString(KeyAssistantKeepWarm, preferences.assistantKeepWarm.name)
            .putBoolean(
                KeyAssistantWifiOnlyDownloads,
                preferences.assistantWifiOnlyDownloads,
            )
            .putBoolean(
                KeyAssistantForwardMusicCommands,
                preferences.assistantForwardMusicCommands,
            )
            .apply()
        if (previousHidden != preferences.hiddenMainPageFunctions) {
            // Persist disabled flags so showing an effect later cannot reactivate it.
            AudioFreedomSettingsStore.save(context, AudioFreedomSettingsStore.load(context))
        }
    }

    fun profileForRoute(context: Context, routeId: String): String? =
        context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
            .getString("$KeyRouteProfilePrefix$routeId", null)

    fun assignProfile(context: Context, routeId: String, profileId: String?) {
        val editor = context.getSharedPreferences(Preferences, Context.MODE_PRIVATE).edit()
        if (profileId == null) {
            editor.remove("$KeyRouteProfilePrefix$routeId")
        } else {
            editor.putString("$KeyRouteProfilePrefix$routeId", profileId)
        }
        editor.apply()
    }
}

private inline fun <reified T : Enum<T>> android.content.SharedPreferences.enumValue(
    key: String,
    default: T,
): T = runCatching {
    enumValueOf<T>(getString(key, default.name).orEmpty())
}.getOrDefault(default)
