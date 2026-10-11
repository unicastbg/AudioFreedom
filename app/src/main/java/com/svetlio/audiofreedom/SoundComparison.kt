package com.svetlio.audiofreedom

import android.content.Context

internal enum class ComparisonSlot { A, B }

internal data class SoundComparison(
    val a: AudioFreedomSettings? = null,
    val b: AudioFreedomSettings? = null,
    val preferredSlot: ComparisonSlot? = null,
) {
    fun settings(slot: ComparisonSlot): AudioFreedomSettings? =
        if (slot == ComparisonSlot.A) a else b

    fun capture(slot: ComparisonSlot, current: AudioFreedomSettings): SoundComparison {
        val snapshot = current.copy(bandGainsMillibels = current.bandGainsMillibels.toList())
        return if (slot == ComparisonSlot.A) copy(a = snapshot, preferredSlot = slot)
            else copy(b = snapshot, preferredSlot = slot)
    }

    fun activate(slot: ComparisonSlot, current: AudioFreedomSettings): SoundComparison =
        if (settings(slot) == null) capture(slot, current) else copy(preferredSlot = slot)

    fun matching(current: AudioFreedomSettings): ComparisonSlot? {
        preferredSlot?.let { if (settings(it) == current) return it }
        return when (current) {
            a -> ComparisonSlot.A
            b -> ComparisonSlot.B
            else -> null
        }
    }
}

internal object SoundComparisonStore {
    private const val Preferences = "audiofreedom_sound_comparison"

    fun load(context: Context, route: String): SoundComparison {
        val preferences = context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
        fun read(slot: String): AudioFreedomSettings? = preferences.getString("$route.$slot", null)
            ?.let(SettingsSnapshotCodec::decode)
        val preferred = preferences.getString("$route.selected", null)?.let { name ->
            ComparisonSlot.entries.firstOrNull { it.name == name }
        }
        return SoundComparison(read("a"), read("b"), preferred)
    }

    fun save(context: Context, route: String, value: SoundComparison) {
        context.getSharedPreferences(Preferences, Context.MODE_PRIVATE).edit()
            .putString("$route.a", value.a?.let(SettingsSnapshotCodec::encode))
            .putString("$route.b", value.b?.let(SettingsSnapshotCodec::encode))
            .putString("$route.selected", value.preferredSlot?.name)
            .apply()
    }
}
