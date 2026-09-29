package com.svetlio.audiofreedom

import android.content.Context

internal enum class ThemePreference(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark"),
}

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
    WakePhrase("Wake phrase"),
}

internal data class AppPreferences(
    val theme: ThemePreference = ThemePreference.System,
    val automaticDeviceProfiles: Boolean = false,
    val showDeviceInNotification: Boolean = true,
    val assistantEnabled: Boolean = false,
    val assistantLanguage: AssistantLanguage = AssistantLanguage.Automatic,
    val assistantApplyMode: AssistantApplyMode = AssistantApplyMode.Preview,
    val assistantAdjustmentStrength: AssistantAdjustmentStrength =
        AssistantAdjustmentStrength.Conservative,
    val assistantVoiceEnabled: Boolean = false,
    val assistantVoiceMode: AssistantVoiceMode = AssistantVoiceMode.PushToTalk,
    val assistantAutoSubmitVoice: Boolean = false,
    val assistantPauseMediaForVoice: Boolean = true,
    val assistantSpeakVoiceConfirmation: Boolean = true,
    val assistantAskForFeedback: Boolean = true,
    val assistantKeepWarm: AssistantKeepWarm = AssistantKeepWarm.KeepReady,
    val assistantWifiOnlyDownloads: Boolean = true,
)

internal object AppPreferencesStore {
    private const val Preferences = "audiofreedom_app"
    private const val KeyTheme = "theme"
    private const val KeyAutomaticDeviceProfiles = "automatic_device_profiles"
    private const val KeyShowDeviceInNotification = "show_device_in_notification"
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
    private const val KeyRouteProfilePrefix = "route_profile_"

    fun load(context: Context): AppPreferences {
        val preferences = context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
        val theme = runCatching {
            ThemePreference.valueOf(
                preferences.getString(KeyTheme, ThemePreference.System.name).orEmpty(),
            )
        }.getOrDefault(ThemePreference.System)
        return AppPreferences(
            theme = theme,
            automaticDeviceProfiles = preferences.getBoolean(KeyAutomaticDeviceProfiles, false),
            showDeviceInNotification =
                preferences.getBoolean(KeyShowDeviceInNotification, true),
            assistantEnabled = preferences.getBoolean(KeyAssistantEnabled, false),
            assistantLanguage = preferences.enumValue(
                KeyAssistantLanguage,
                AssistantLanguage.Automatic,
            ),
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
            assistantAutoSubmitVoice =
                preferences.getBoolean(KeyAssistantAutoSubmitVoice, false),
            assistantPauseMediaForVoice =
                preferences.getBoolean(KeyAssistantPauseMediaForVoice, true),
            assistantSpeakVoiceConfirmation =
                preferences.getBoolean(KeyAssistantSpeakVoiceConfirmation, true),
            assistantAskForFeedback = preferences.getBoolean(KeyAssistantAskForFeedback, true),
            assistantKeepWarm = preferences.enumValue(
                KeyAssistantKeepWarm,
                AssistantKeepWarm.KeepReady,
            ),
            assistantWifiOnlyDownloads =
                preferences.getBoolean(KeyAssistantWifiOnlyDownloads, true),
        )
    }

    fun save(context: Context, preferences: AppPreferences) {
        context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
            .edit()
            .putString(KeyTheme, preferences.theme.name)
            .putBoolean(KeyAutomaticDeviceProfiles, preferences.automaticDeviceProfiles)
            .putBoolean(KeyShowDeviceInNotification, preferences.showDeviceInNotification)
            .putBoolean(KeyAssistantEnabled, preferences.assistantEnabled)
            .putString(KeyAssistantLanguage, preferences.assistantLanguage.name)
            .putString(KeyAssistantApplyMode, preferences.assistantApplyMode.name)
            .putString(
                KeyAssistantAdjustmentStrength,
                preferences.assistantAdjustmentStrength.name,
            )
            .putBoolean(KeyAssistantVoiceEnabled, preferences.assistantVoiceEnabled)
            .putString(KeyAssistantVoiceMode, preferences.assistantVoiceMode.name)
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
            .putString(KeyAssistantKeepWarm, preferences.assistantKeepWarm.name)
            .putBoolean(
                KeyAssistantWifiOnlyDownloads,
                preferences.assistantWifiOnlyDownloads,
            )
            .apply()
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
