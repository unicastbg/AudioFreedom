package com.svetlio.audiofreedom

import android.content.Context

internal enum class ThemePreference(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark"),
}

internal data class AppPreferences(
    val theme: ThemePreference = ThemePreference.System,
    val automaticDeviceProfiles: Boolean = false,
    val showDeviceInNotification: Boolean = true,
)

internal object AppPreferencesStore {
    private const val Preferences = "audiofreedom_app"
    private const val KeyTheme = "theme"
    private const val KeyAutomaticDeviceProfiles = "automatic_device_profiles"
    private const val KeyShowDeviceInNotification = "show_device_in_notification"
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
        )
    }

    fun save(context: Context, preferences: AppPreferences) {
        context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
            .edit()
            .putString(KeyTheme, preferences.theme.name)
            .putBoolean(KeyAutomaticDeviceProfiles, preferences.automaticDeviceProfiles)
            .putBoolean(KeyShowDeviceInNotification, preferences.showDeviceInNotification)
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
