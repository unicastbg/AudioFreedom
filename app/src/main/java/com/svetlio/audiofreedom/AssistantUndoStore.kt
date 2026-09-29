package com.svetlio.audiofreedom

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal object AssistantUndoStore {
    private const val Preferences = "audiofreedom_assistant_undo"
    private const val KeySnapshot = "settings_snapshot"

    fun save(context: Context, settings: AudioFreedomSettings) {
        context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
            .edit()
            .putString(KeySnapshot, settings.toJson().toString())
            .apply()
    }

    fun load(context: Context): AudioFreedomSettings? {
        val raw = context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
            .getString(KeySnapshot, null)
            ?: return null
        return runCatching { JSONObject(raw).toAudioFreedomSettings() }.getOrNull()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
            .edit()
            .remove(KeySnapshot)
            .apply()
    }

    private fun AudioFreedomSettings.toJson() = JSONObject()
        .put("equalizerEnabled", equalizerEnabled)
        .put("preampMillibels", preampMillibels)
        .put("bandGainsMillibels", JSONArray(bandGainsMillibels))
        .put("dynamicBassEnabled", dynamicBassEnabled)
        .put("bassBoostMillibels", bassBoostMillibels)
        .put("bassCutoffHz", bassCutoffHz)
        .put("bassDynamicsPercent", bassDynamicsPercent)
        .put("detailRecoveryEnabled", detailRecoveryEnabled)
        .put("detailAmountPercent", detailAmountPercent)
        .put("detailFocusHz", detailFocusHz)
        .put("detailTransientsPercent", detailTransientsPercent)
        .put("immersiveFieldEnabled", immersiveFieldEnabled)
        .put("immersiveAmountPercent", immersiveAmountPercent)
        .put("immersiveWidthPercent", immersiveWidthPercent)
        .put("immersiveCenterPercent", immersiveCenterPercent)
        .put("immersiveRoomPercent", immersiveRoomPercent)
        .put("reverbEnabled", reverbEnabled)
        .put("reverbAmountPercent", reverbAmountPercent)
        .put("reverbSpacePercent", reverbSpacePercent)
        .put("reverbDampingPercent", reverbDampingPercent)
        .put("reverbDecayMilliseconds", reverbDecayMilliseconds)
        .put("limiterEnabled", limiterEnabled)
        .put("limiterThresholdMillibels", limiterThresholdMillibels)
        .put("limiterReleaseMilliseconds", limiterReleaseMilliseconds)

    private fun JSONObject.toAudioFreedomSettings(): AudioFreedomSettings {
        val defaults = AudioFreedomSettings()
        val bands = optJSONArray("bandGainsMillibels")
        return AudioFreedomSettings(
            equalizerEnabled = optBoolean("equalizerEnabled", defaults.equalizerEnabled),
            preampMillibels = optInt("preampMillibels", defaults.preampMillibels)
                .coerceIn(-2400, 0),
            bandGainsMillibels = List(EqualizerBandCount) { index ->
                bands?.optInt(index, 0)?.coerceIn(-1200, 1200) ?: 0
            },
            dynamicBassEnabled = optBoolean(
                "dynamicBassEnabled",
                defaults.dynamicBassEnabled,
            ),
            bassBoostMillibels = optInt("bassBoostMillibels", defaults.bassBoostMillibels)
                .coerceIn(0, 1200),
            bassCutoffHz = optInt("bassCutoffHz", defaults.bassCutoffHz).coerceIn(40, 160),
            bassDynamicsPercent = optInt(
                "bassDynamicsPercent",
                defaults.bassDynamicsPercent,
            ).coerceIn(0, 100),
            detailRecoveryEnabled = optBoolean(
                "detailRecoveryEnabled",
                defaults.detailRecoveryEnabled,
            ),
            detailAmountPercent = optInt(
                "detailAmountPercent",
                defaults.detailAmountPercent,
            ).coerceIn(0, 100),
            detailFocusHz = optInt("detailFocusHz", defaults.detailFocusHz)
                .coerceIn(3000, 10000),
            detailTransientsPercent = optInt(
                "detailTransientsPercent",
                defaults.detailTransientsPercent,
            ).coerceIn(0, 100),
            immersiveFieldEnabled = optBoolean(
                "immersiveFieldEnabled",
                defaults.immersiveFieldEnabled,
            ),
            immersiveAmountPercent = optInt(
                "immersiveAmountPercent",
                defaults.immersiveAmountPercent,
            ).coerceIn(0, 100),
            immersiveWidthPercent = optInt(
                "immersiveWidthPercent",
                defaults.immersiveWidthPercent,
            ).coerceIn(0, 100),
            immersiveCenterPercent = optInt(
                "immersiveCenterPercent",
                defaults.immersiveCenterPercent,
            ).coerceIn(0, 100),
            immersiveRoomPercent = optInt(
                "immersiveRoomPercent",
                defaults.immersiveRoomPercent,
            ).coerceIn(0, 100),
            reverbEnabled = optBoolean("reverbEnabled", defaults.reverbEnabled),
            reverbAmountPercent = optInt(
                "reverbAmountPercent",
                defaults.reverbAmountPercent,
            ).coerceIn(0, 100),
            reverbSpacePercent = optInt(
                "reverbSpacePercent",
                defaults.reverbSpacePercent,
            ).coerceIn(0, 100),
            reverbDampingPercent = optInt(
                "reverbDampingPercent",
                defaults.reverbDampingPercent,
            ).coerceIn(0, 100),
            reverbDecayMilliseconds = optInt(
                "reverbDecayMilliseconds",
                defaults.reverbDecayMilliseconds,
            ).coerceIn(300, 5000),
            limiterEnabled = optBoolean("limiterEnabled", defaults.limiterEnabled),
            limiterThresholdMillibels = optInt(
                "limiterThresholdMillibels",
                defaults.limiterThresholdMillibels,
            ).coerceIn(-600, 0),
            limiterReleaseMilliseconds = optInt(
                "limiterReleaseMilliseconds",
                defaults.limiterReleaseMilliseconds,
            ).coerceIn(20, 1000),
        )
    }
}
