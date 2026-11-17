package com.svetlio.audiofreedom

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

internal data class AudioFreedomProfile(
    val id: String,
    val name: String,
    val settings: AudioFreedomSettings,
    /** Old free-text group. No longer groups anything; shown as a small tag if set. */
    val deviceGroup: String = "",
    /** The output this profile belongs to ([AudioRoute.id]); empty = any device. */
    val deviceId: String = "",
    /** That output's name, for when it isn't connected. */
    val deviceLabel: String = "",
) {
    val displayName: String get() =
        listOf(deviceLabel.ifBlank { deviceGroup }, name).filter(String::isNotBlank).joinToString(" / ")
}

internal object AudioFreedomProfileStore {
    private const val Preferences = "audiofreedom_profiles"
    private const val KeyProfileIds = "profile_ids"
    private const val KeyDevicesMigrated = "devices_migrated_v1"

    fun list(context: Context): List<AudioFreedomProfile> {
        val preferences = context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
        val extended = AppPreferencesStore.load(context).extendedEqualizerRange
        return preferences.getStringSet(KeyProfileIds, emptySet())
            .orEmpty()
            .mapNotNull { id -> readProfile(preferences, id) }
            .map { it.copy(settings = it.settings.withEqualizerRange(extended)) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    fun save(
        context: Context,
        name: String,
        settings: AudioFreedomSettings,
        profileId: String? = null,
        deviceGroup: String? = null,
        /** Null keeps an existing profile's device (or "any device" for a new one). */
        deviceId: String? = null,
        deviceLabel: String? = null,
    ): AudioFreedomProfile {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty())
        val settings = settings.withEqualizerRange(AppPreferencesStore.load(context).extendedEqualizerRange)

        val preferences = context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
        val existingProfiles = preferences.getStringSet(KeyProfileIds, emptySet()).orEmpty()
        val group = (deviceGroup ?: profileId?.let {
            preferences.getString(key(it, "device_group"), "")
        }).orEmpty().trim().take(100)
        val device = (deviceId ?: profileId?.let {
            preferences.getString(key(it, "device_id"), "")
        }).orEmpty()
        val label = (deviceLabel ?: profileId?.let {
            preferences.getString(key(it, "device_label"), "")
        }).orEmpty().trim().take(100)
        // Saving a new profile under a name the same device already has updates that one.
        val id = profileId ?: existingProfiles.firstOrNull { existingId ->
            preferences.getString(key(existingId, "name"), null)
                ?.equals(normalizedName, ignoreCase = true) == true &&
                preferences.getString(key(existingId, "device_id"), "").orEmpty() == device
        } ?: UUID.randomUUID().toString()
        val ids = existingProfiles.toMutableSet().apply { add(id) }

        preferences.edit()
            .putStringSet(KeyProfileIds, ids)
            .putString(key(id, "name"), normalizedName)
            .putString(key(id, "device_group"), group)
            .putString(key(id, "device_id"), device)
            .putString(key(id, "device_label"), label)
            .putSettings(id, settings)
            .apply()

        return AudioFreedomProfile(id, normalizedName, settings, group, device, label)
    }

    fun rename(context: Context, profileId: String, name: String) {
        val normalized = name.trim()
        require(normalized.isNotEmpty())
        context.getSharedPreferences(Preferences, Context.MODE_PRIVATE).edit()
            .putString(key(profileId, "name"), normalized.take(100))
            .apply()
    }

    /** Move a profile to another output; an empty [deviceId] means any device. */
    fun move(context: Context, profileId: String, deviceId: String, deviceLabel: String) {
        context.getSharedPreferences(Preferences, Context.MODE_PRIVATE).edit()
            .putString(key(profileId, "device_id"), deviceId)
            .putString(key(profileId, "device_label"), if (deviceId.isEmpty()) "" else deviceLabel)
            .apply()
    }

    /**
     * One-time move from typed groups to real devices. A profile already set as an
     * output's profile belongs to that output; one whose group text is the connected
     * output's name belongs to it; everything else becomes "any device" and keeps its
     * old group as a tag, so nothing is lost and it can be moved by hand.
     */
    fun migrateToDevices(context: Context, current: AudioRoute) {
        val preferences = context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
        if (preferences.getBoolean(KeyDevicesMigrated, false)) return
        val assignments = AppPreferencesStore.routeAssignments(context)   // routeId -> profileId
        val editor = preferences.edit()
        preferences.getStringSet(KeyProfileIds, emptySet()).orEmpty().forEach { id ->
            if (preferences.contains(key(id, "device_id"))) return@forEach
            val group = preferences.getString(key(id, "device_group"), "").orEmpty().trim()
            val assignedRoute = assignments.entries.firstOrNull { it.value == id }?.key
            val (deviceId, label) = when {
                assignedRoute == current.id -> current.id to current.label
                assignedRoute != null -> assignedRoute to group.ifBlank { routeLabelFromId(assignedRoute) }
                group.isNotEmpty() && group.equals(current.label, ignoreCase = true) ->
                    current.id to current.label
                else -> "" to ""
            }
            editor.putString(key(id, "device_id"), deviceId).putString(key(id, "device_label"), label)
        }
        editor.putBoolean(KeyDevicesMigrated, true).apply()
    }

    /** "bluetooth-sony-wh-1000xm5" -> "Sony Wh 1000xm5": only for outputs we never saw named. */
    private fun routeLabelFromId(routeId: String): String =
        routeId.substringAfter('-').split('-').filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
            .ifBlank { "Audio output" }

    fun delete(context: Context, profileId: String) {
        val preferences = context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
        val ids = preferences.getStringSet(KeyProfileIds, emptySet()).orEmpty().toMutableSet()
        ids.remove(profileId)

        val editor = preferences.edit().putStringSet(KeyProfileIds, ids)
        editor.remove(key(profileId, "device_group"))
        editor.remove(key(profileId, "device_id"))
        editor.remove(key(profileId, "device_label"))
        profileFields.forEach { field -> editor.remove(key(profileId, field)) }
        repeat(EqualizerBandCount) { band -> editor.remove(key(profileId, "band_$band")) }
        repeat(EqualizerBandCount) { band -> editor.remove(key(profileId, "q_$band")) }
        listOf("routing_enabled", "routing_mode", "routing_right", "routing_balance").forEach {
            editor.remove(key(profileId, it))
        }
        editor.apply()
        AppPreferencesStore.removeProfileAssignments(context, profileId)
    }

    fun manage(context: Context, action: ProfileAction) {
        val preferences = context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
        val ids = preferences.getStringSet(KeyProfileIds, emptySet()).orEmpty().toSet()
        // Read raw profile settings, not the range-limited live view, when duplicating.
        val profiles = ids.mapNotNull { readProfile(preferences, it) }
        when (action) {
            is ProfileAction.Delete -> action.ids.intersect(ids).forEach { delete(context, it) }
            is ProfileAction.Duplicate -> {
                val source = profiles.firstOrNull { it.id == action.id } ?: return
                val newId = UUID.randomUUID().toString()
                preferences.edit().putStringSet(KeyProfileIds, ids + newId)
                    .putString(key(newId, "name"), duplicateProfileName(source, profiles))
                    .putString(key(newId, "device_group"), source.deviceGroup)
                    .putString(key(newId, "device_id"), source.deviceId)
                    .putString(key(newId, "device_label"), source.deviceLabel)
                    .putSettings(newId, source.settings).apply()
            }
            else -> {
                val planned = planProfileMetadata(profiles, action)
                val editor = preferences.edit()
                planned.filterIndexed { index, profile -> profile != profiles[index] }.forEach { profile ->
                    editor.putString(key(profile.id, "name"), profile.name)
                        .putString(key(profile.id, "device_group"), profile.deviceGroup)
                }
                editor.apply()
            }
        }
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
            deviceGroup = preferences.getString(key(id, "device_group"), "").orEmpty(),
            deviceId = preferences.getString(key(id, "device_id"), "").orEmpty(),
            deviceLabel = preferences.getString(key(id, "device_label"), "").orEmpty(),
            settings = AudioFreedomSettings(
                equalizerEnabled = preferences.getBoolean(
                    key(id, "equalizer_enabled"), defaults.equalizerEnabled,
                ),
                preampMillibels = preferences.getInt(
                    key(id, "preamp"), defaults.preampMillibels,
                ).coerceIn(-2400, 0),
                bandGainsMillibels = List(EqualizerBandCount) { band ->
                    preferences.getInt(key(id, "band_$band"), 0)
                        .coerceIn(-ExtendedEqualizerGainLimit, ExtendedEqualizerGainLimit)
                },
                bandQHundredths = List(EqualizerBandCount) { band ->
                    preferences.getInt(key(id, "q_$band"), DefaultEqualizerQ).coerceIn(30, 500)
                },
                stereoRoutingEnabled = preferences.getBoolean(key(id, "routing_enabled"), false),
                stereoRoutingMode = StereoRoutingMode.entries.getOrElse(preferences.getInt(key(id, "routing_mode"), 0)) { StereoRoutingMode.Normal },
                stereoOutputRight = preferences.getBoolean(key(id, "routing_right"), false),
                stereoBalancePercent = preferences.getInt(key(id, "routing_balance"), 0).coerceIn(-100, 100),
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
                reverbEnabled = preferences.getBoolean(
                    key(id, "reverb_enabled"), defaults.reverbEnabled,
                ),
                reverbAmountPercent = preferences.getInt(
                    key(id, "reverb_amount"), defaults.reverbAmountPercent,
                ).coerceIn(0, 100),
                reverbSpacePercent = preferences.getInt(
                    key(id, "reverb_space"), defaults.reverbSpacePercent,
                ).coerceIn(0, 100),
                reverbDampingPercent = preferences.getInt(
                    key(id, "reverb_damping"), defaults.reverbDampingPercent,
                ).coerceIn(0, 100),
                reverbDecayMilliseconds = preferences.getInt(
                    key(id, "reverb_decay"), defaults.reverbDecayMilliseconds,
                ).coerceIn(300, 5000),
                limiterEnabled = preferences.getBoolean(
                    key(id, "limiter_enabled"), defaults.limiterEnabled,
                ),
                volumeLevelerEnabled = preferences.getBoolean(key(id, "leveler_enabled"), false),
                levelerTargetMillibels = preferences.getInt(key(id, "leveler_target"), -1800).coerceIn(-3000, -1200),
                levelerStrengthPercent = preferences.getInt(key(id, "leveler_strength"), 50).coerceIn(0, 100),
                levelerMaximumBoostMillibels = preferences.getInt(key(id, "leveler_maximum_boost"), 600).coerceIn(0, 1200),
                levelerResponseMilliseconds = preferences.getInt(key(id, "leveler_response"), 1500).coerceIn(200, 5000),
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
        settings.bandQHundredths.forEachIndexed { band, q -> putInt(key(id, "q_$band"), q) }
        putBoolean(key(id, "routing_enabled"), settings.stereoRoutingEnabled)
        putInt(key(id, "routing_mode"), settings.stereoRoutingMode.ordinal)
        putBoolean(key(id, "routing_right"), settings.stereoOutputRight)
        putInt(key(id, "routing_balance"), settings.stereoBalancePercent)
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
        putBoolean(key(id, "reverb_enabled"), settings.reverbEnabled)
        putInt(key(id, "reverb_amount"), settings.reverbAmountPercent)
        putInt(key(id, "reverb_space"), settings.reverbSpacePercent)
        putInt(key(id, "reverb_damping"), settings.reverbDampingPercent)
        putInt(key(id, "reverb_decay"), settings.reverbDecayMilliseconds)
        putBoolean(key(id, "limiter_enabled"), settings.limiterEnabled)
        putBoolean(key(id, "leveler_enabled"), settings.volumeLevelerEnabled)
        putInt(key(id, "leveler_target"), settings.levelerTargetMillibels)
        putInt(key(id, "leveler_strength"), settings.levelerStrengthPercent)
        putInt(key(id, "leveler_maximum_boost"), settings.levelerMaximumBoostMillibels)
        putInt(key(id, "leveler_response"), settings.levelerResponseMilliseconds)
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
        "reverb_enabled",
        "reverb_amount",
        "reverb_space",
        "reverb_damping",
        "reverb_decay",
        "limiter_enabled",
        "leveler_enabled", "leveler_target", "leveler_strength", "leveler_maximum_boost", "leveler_response",
        "limiter_threshold",
        "limiter_release",
    )
}
