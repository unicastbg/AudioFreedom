package com.svetlio.audiofreedom

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

internal data class AudioFreedomProfile(
    val id: String,
    val name: String,
    val settings: AudioFreedomSettings,
)

internal object AudioFreedomProfileStore {
    private const val Preferences = "audiofreedom_profiles"
    private const val KeyProfileIds = "profile_ids"

    fun list(context: Context): List<AudioFreedomProfile> {
        val preferences = context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
        return preferences.getStringSet(KeyProfileIds, emptySet())
            .orEmpty()
            .mapNotNull { id -> readProfile(preferences, id) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    fun save(
        context: Context,
        name: String,
        settings: AudioFreedomSettings,
        profileId: String? = null,
    ): AudioFreedomProfile {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty())

        val preferences = context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
        val existingProfiles = preferences.getStringSet(KeyProfileIds, emptySet()).orEmpty()
        val id = profileId ?: existingProfiles.firstOrNull { existingId ->
            preferences.getString(key(existingId, "name"), null)
                ?.equals(normalizedName, ignoreCase = true) == true
        } ?: UUID.randomUUID().toString()
        val ids = existingProfiles.toMutableSet().apply { add(id) }

        preferences.edit()
            .putStringSet(KeyProfileIds, ids)
            .putString(key(id, "name"), normalizedName)
            .putSettings(id, settings)
            .apply()

        return AudioFreedomProfile(id, normalizedName, settings)
    }

    fun delete(context: Context, profileId: String) {
        val preferences = context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
        val ids = preferences.getStringSet(KeyProfileIds, emptySet()).orEmpty().toMutableSet()
        ids.remove(profileId)

        val editor = preferences.edit().putStringSet(KeyProfileIds, ids)
        profileFields.forEach { field -> editor.remove(key(profileId, field)) }
        repeat(EqualizerBandCount) { band -> editor.remove(key(profileId, "band_$band")) }
        editor.apply()
    }

    private fun readProfile(
        preferences: SharedPreferences,
        id: String,
    ): AudioFreedomProfile? {
        val name = preferences.getString(key(id, "name"), null)?.trim().orEmpty()
        if (name.isEmpty()) return null

        val defaults = AudioFreedomSettings()
        return AudioFreedomProfile(
            id = id,
            name = name,
            settings = AudioFreedomSettings(
                equalizerEnabled = preferences.getBoolean(
                    key(id, "equalizer_enabled"), defaults.equalizerEnabled,
                ),
                preampMillibels = preferences.getInt(
                    key(id, "preamp"), defaults.preampMillibels,
                ).coerceIn(-2400, 0),
                bandGainsMillibels = List(EqualizerBandCount) { band ->
                    preferences.getInt(key(id, "band_$band"), 0).coerceIn(-1200, 1200)
                },
                dynamicBassEnabled = preferences.getBoolean(
                    key(id, "bass_enabled"), defaults.dynamicBassEnabled,
                ),
                bassBoostMillibels = preferences.getInt(
                    key(id, "bass_boost"), defaults.bassBoostMillibels,
                ).coerceIn(0, 1200),
                bassCutoffHz = preferences.getInt(
                    key(id, "bass_cutoff"), defaults.bassCutoffHz,
                ).coerceIn(40, 160),
                bassDynamicsPercent = preferences.getInt(
                    key(id, "bass_dynamics"), defaults.bassDynamicsPercent,
                ).coerceIn(0, 100),
                detailRecoveryEnabled = preferences.getBoolean(
                    key(id, "detail_enabled"), defaults.detailRecoveryEnabled,
                ),
                detailAmountPercent = preferences.getInt(
                    key(id, "detail_amount"), defaults.detailAmountPercent,
                ).coerceIn(0, 100),
                detailFocusHz = preferences.getInt(
                    key(id, "detail_focus"), defaults.detailFocusHz,
                ).coerceIn(3000, 10000),
                detailTransientsPercent = preferences.getInt(
                    key(id, "detail_transients"), defaults.detailTransientsPercent,
                ).coerceIn(0, 100),
                immersiveFieldEnabled = preferences.getBoolean(
                    key(id, "immersive_enabled"), defaults.immersiveFieldEnabled,
                ),
                immersiveAmountPercent = preferences.getInt(
                    key(id, "immersive_amount"), defaults.immersiveAmountPercent,
                ).coerceIn(0, 100),
                immersiveWidthPercent = preferences.getInt(
                    key(id, "immersive_width"), defaults.immersiveWidthPercent,
                ).coerceIn(0, 100),
                immersiveCenterPercent = preferences.getInt(
                    key(id, "immersive_center"), defaults.immersiveCenterPercent,
                ).coerceIn(0, 100),
                immersiveRoomPercent = preferences.getInt(
                    key(id, "immersive_room"), defaults.immersiveRoomPercent,
                ).coerceIn(0, 100),
                limiterEnabled = preferences.getBoolean(
                    key(id, "limiter_enabled"), defaults.limiterEnabled,
                ),
                limiterThresholdMillibels = preferences.getInt(
                    key(id, "limiter_threshold"), defaults.limiterThresholdMillibels,
                ).coerceIn(-600, 0),
                limiterReleaseMilliseconds = preferences.getInt(
                    key(id, "limiter_release"), defaults.limiterReleaseMilliseconds,
                ).coerceIn(20, 1000),
            ),
        )
    }

    private fun SharedPreferences.Editor.putSettings(
        id: String,
        settings: AudioFreedomSettings,
    ): SharedPreferences.Editor {
        putBoolean(key(id, "equalizer_enabled"), settings.equalizerEnabled)
        putInt(key(id, "preamp"), settings.preampMillibels)
        settings.bandGainsMillibels.forEachIndexed { band, gain ->
            putInt(key(id, "band_$band"), gain)
        }
        putBoolean(key(id, "bass_enabled"), settings.dynamicBassEnabled)
        putInt(key(id, "bass_boost"), settings.bassBoostMillibels)
        putInt(key(id, "bass_cutoff"), settings.bassCutoffHz)
        putInt(key(id, "bass_dynamics"), settings.bassDynamicsPercent)
        putBoolean(key(id, "detail_enabled"), settings.detailRecoveryEnabled)
        putInt(key(id, "detail_amount"), settings.detailAmountPercent)
        putInt(key(id, "detail_focus"), settings.detailFocusHz)
        putInt(key(id, "detail_transients"), settings.detailTransientsPercent)
        putBoolean(key(id, "immersive_enabled"), settings.immersiveFieldEnabled)
        putInt(key(id, "immersive_amount"), settings.immersiveAmountPercent)
        putInt(key(id, "immersive_width"), settings.immersiveWidthPercent)
        putInt(key(id, "immersive_center"), settings.immersiveCenterPercent)
        putInt(key(id, "immersive_room"), settings.immersiveRoomPercent)
        putBoolean(key(id, "limiter_enabled"), settings.limiterEnabled)
        putInt(key(id, "limiter_threshold"), settings.limiterThresholdMillibels)
        putInt(key(id, "limiter_release"), settings.limiterReleaseMilliseconds)
        return this
    }

    private fun key(id: String, field: String) = "profile_${id}_$field"

    private val profileFields = listOf(
        "name",
        "equalizer_enabled",
        "preamp",
        "bass_enabled",
        "bass_boost",
        "bass_cutoff",
        "bass_dynamics",
        "detail_enabled",
        "detail_amount",
        "detail_focus",
        "detail_transients",
        "immersive_enabled",
        "immersive_amount",
        "immersive_width",
        "immersive_center",
        "immersive_room",
        "limiter_enabled",
        "limiter_threshold",
        "limiter_release",
    )
}
