package com.svetlio.audiofreedom

import java.util.Locale

internal sealed interface ProfileAction {
    data class Rename(val id: String, val name: String) : ProfileAction
    data class Move(val ids: Set<String>, val group: String) : ProfileAction
    data class RenameGroup(val group: String, val name: String) : ProfileAction
    data class RemoveGroup(val group: String) : ProfileAction
    data class Duplicate(val id: String) : ProfileAction
    data class Delete(val ids: Set<String>) : ProfileAction
}

internal fun profileGroups(profiles: List<AudioFreedomProfile>): List<String> =
    profiles.map { it.deviceGroup.trim() }.filter(String::isNotEmpty)
        .distinctBy { it.lowercase(Locale.ROOT) }.sortedWith(String.CASE_INSENSITIVE_ORDER)

internal fun planProfileMetadata(profiles: List<AudioFreedomProfile>, action: ProfileAction): List<AudioFreedomProfile> {
    if (action is ProfileAction.RenameGroup) {
        require(profileGroups(profiles).none {
            it.equals(action.name.trim(), true) && !it.equals(action.group.trim(), true)
        }) { "That group already exists. Use Move to group to combine profiles." }
    }
    fun name(value: String): String = value.trim().also {
        require(it.isNotEmpty() && it.length <= 100) { "Enter a name between 1 and 100 characters." }
    }
    val changed = mutableSetOf<String>()
    val result = profiles.map { profile ->
        val updated = when (action) {
            is ProfileAction.Rename -> if (profile.id == action.id) profile.copy(name = name(action.name)) else profile
            is ProfileAction.Move -> if (profile.id in action.ids) {
                val group = action.group.trim()
                require(group.length <= 100) { "Group names can have up to 100 characters." }
                profile.copy(deviceGroup = profileGroups(profiles).firstOrNull { it.equals(group, true) } ?: group)
            } else profile
            is ProfileAction.RenameGroup -> if (profile.deviceGroup.trim().equals(action.group.trim(), true)) {
                require(action.group.isNotBlank()) { "Ungrouped cannot be renamed." }
                profile.copy(deviceGroup = name(action.name))
            } else profile
            is ProfileAction.RemoveGroup -> if (profile.deviceGroup.trim().equals(action.group.trim(), true)) {
                profile.copy(deviceGroup = "")
            } else profile
            else -> profile
        }
        if (updated != profile) changed.add(profile.id)
        updated
    }
    val collision = result.groupBy { it.deviceGroup.trim().lowercase(Locale.ROOT) to it.name.lowercase(Locale.ROOT) }
        .values.firstOrNull { it.size > 1 && it.any { profile -> profile.id in changed } }
    require(collision == null) {
        "A profile named \"${collision!!.first().name}\" already exists in \"${collision.first().deviceGroup.ifEmpty { "Ungrouped" }}\". Rename one and try again."
    }
    return result
}

internal fun profileCount(count: Int): String = "$count " + if (count == 1) "profile" else "profiles"

internal fun duplicateProfileName(source: AudioFreedomProfile, profiles: List<AudioFreedomProfile>): String {
    val base = source.name.take(88) + " copy"
    var candidate = base
    var index = 2
    while (profiles.any { it.name.equals(candidate, true) && it.deviceGroup.equals(source.deviceGroup, true) }) {
        candidate = "$base $index"
        index++
    }
    return candidate
}
