package com.svetlio.audiofreedom

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs

/** Kept for the profile manager screen's search. */
internal fun groupedProfiles(
    profiles: List<AudioFreedomProfile>, query: String,
): List<Pair<String, List<AudioFreedomProfile>>> {
    val needle = query.trim().lowercase(Locale.ROOT)
    return profiles.filter {
        needle.isEmpty() || it.name.lowercase(Locale.ROOT).contains(needle) ||
            it.deviceGroup.lowercase(Locale.ROOT).contains(needle)
    }.groupBy { it.deviceGroup.trim().lowercase(Locale.ROOT) }
        .entries.sortedWith(compareBy<Map.Entry<String, List<AudioFreedomProfile>>> { it.key.isEmpty() }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.key })
        .map { (_, values) -> values.first().deviceGroup.trim() to values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }) }
}

/** One output's profiles, for the picker. [deviceId] empty = "Any device". */
private data class DeviceSection(
    val deviceId: String,
    val label: String,
    val connected: Boolean,
    val profiles: List<AudioFreedomProfile>,
)

private fun deviceSections(
    profiles: List<AudioFreedomProfile>, route: AudioRoute, query: String, groupNames: Map<String, String>,
): List<DeviceSection> {
    val needle = query.trim().lowercase(Locale.ROOT)
    val matching = profiles.filter {
        needle.isEmpty() || it.name.lowercase(Locale.ROOT).contains(needle) ||
            it.deviceLabel.lowercase(Locale.ROOT).contains(needle) ||
            groupNames[it.deviceId]?.lowercase(Locale.ROOT)?.contains(needle) == true ||
            (it.deviceId == route.id && route.label.lowercase(Locale.ROOT).contains(needle)) ||
            it.deviceGroup.lowercase(Locale.ROOT).contains(needle)
    }
    val byDevice = matching.groupBy { it.deviceId }
    val sorted: (List<AudioFreedomProfile>) -> List<AudioFreedomProfile> =
        { list -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }) }
    return buildList {
        // The connected output first, always shown so there is somewhere to save to.
        add(DeviceSection(route.id, groupNames[route.id] ?: route.label, true, sorted(byDevice[route.id].orEmpty())))
        byDevice.filterKeys { it.isNotEmpty() && it != route.id }
            .map { (id, list) -> DeviceSection(id, groupNames[id]
                ?: list.first().deviceLabel.ifBlank { "Audio output" }, false, sorted(list)) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .forEach { add(it) }
        byDevice[""]?.let { add(DeviceSection("", "Any device", false, sorted(it))) }
    }.filter { it.connected || it.profiles.isNotEmpty() }
}

/** Every output a profile can be moved to: the connected one, ones profiles know, or none. */
private fun moveTargets(profiles: List<AudioFreedomProfile>, route: AudioRoute): List<Pair<String, String>> =
    buildList {
        add(route.id to route.label)
        profiles.filter { it.deviceId.isNotEmpty() && it.deviceId != route.id }
            .distinctBy { it.deviceId }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.deviceLabel })
            .forEach { add(it.deviceId to it.deviceLabel.ifBlank { "Audio output" }) }
        add("" to "Any device")
    }

/**
 * The profile area of the main screen.
 *
 * The connected output's profiles sit right here as chips, so switching between, say,
 * "Bass heavy" and "Bright" on the same headphones is one tap. Whatever you pick is
 * remembered for the output and comes back when it reconnects ([onSelect] records it).
 * Everything else (other outputs, rename, move, delete) lives in the picker sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeviceProfilesPanel(
    profiles: List<AudioFreedomProfile>,
    settings: AudioFreedomSettings,
    selectedProfileId: String?,
    route: AudioRoute,
    /** The profile this output will come back to. */
    rememberedProfileId: String?,
    onSelect: (AudioFreedomProfile) -> Unit,
    /** The store changed; reload, and select [selectId] when it's non-null. */
    onProfilesChanged: (selectId: String?) -> Unit,
) {
    val context = LocalContext.current
    var groupNames by remember(context) { mutableStateOf(AppPreferencesStore.outputGroupNames(context)) }
    val routeGroupName = groupNames[route.id] ?: route.label
    val selected = profiles.firstOrNull { it.id == selectedProfileId }
    val modified = selected != null && selected.settings != settings
    var pickerOpen by remember { mutableStateOf(false) }
    var saveMenuOpen by remember { mutableStateOf(false) }
    var savingNew by remember { mutableStateOf(false) }
    var pendingSwitch by remember { mutableStateOf<AudioFreedomProfile?>(null) }

    fun choose(profile: AudioFreedomProfile) {
        if (profile.id == selectedProfileId && !modified) return
        if (modified) pendingSwitch = profile else onSelect(profile)
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable { pickerOpen = true }.padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AudioRouteIcon(route, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(routeGroupName, style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(selected?.name ?: "Unsaved settings", modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium)
                    Icon(Icons.Rounded.ExpandMore, "All profiles")
                }
                if (modified) Text("Modified", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            // One button, only when there is something to save.
            if (modified || selected == null) Box {
                FilledTonalButton(onClick = {
                    if (selected == null) savingNew = true else saveMenuOpen = true
                }) {
                    Icon(Icons.Rounded.Save, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save")
                }
                DropdownMenu(expanded = saveMenuOpen, onDismissRequest = { saveMenuOpen = false }) {
                    if (selected != null) DropdownMenuItem(
                        text = { Text("Update “${selected.name}”") },
                        leadingIcon = { Icon(Icons.Rounded.Save, null) },
                        onClick = {
                            saveMenuOpen = false
                            val saved = AudioFreedomProfileStore.save(context, selected.name, settings,
                                profileId = selected.id)
                            onProfilesChanged(saved.id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Save as new…") },
                        leadingIcon = { Icon(Icons.Rounded.Add, null) },
                        onClick = { saveMenuOpen = false; savingNew = true },
                    )
                    if (selected != null) DropdownMenuItem(
                        text = { Text("Revert changes") },
                        leadingIcon = { Icon(Icons.Rounded.Undo, null) },
                        onClick = { saveMenuOpen = false; onSelect(selected) },
                    )
                }
            }
        }

        // Quick switch: this output's profiles, then the ones meant for any device.
        val chips = profiles.filter { it.deviceId == route.id }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }) +
            profiles.filter { it.deviceId.isEmpty() }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            chips.forEach { profile ->
                FilterChip(
                    selected = profile.id == selectedProfileId,
                    onClick = { choose(profile) },
                    label = { Text(profile.name, maxLines = 1) },
                    leadingIcon = if (profile.id == selectedProfileId) {
                        { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                    } else null,
                )
            }
            AssistChip(
                onClick = { savingNew = true },
                label = { Text(if (chips.isEmpty()) "Save for $routeGroupName" else "New",
                    maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(16.dp)) },
            )
        }
    }

    if (pickerOpen) ProfilePickerSheet(
        profiles = profiles,
        selectedProfileId = selectedProfileId,
        route = route,
        rememberedProfileId = rememberedProfileId,
        groupNames = groupNames,
        onRenameGroup = { id, name ->
            AppPreferencesStore.setOutputGroupName(context, id, name)
            groupNames = AppPreferencesStore.outputGroupNames(context)
        },
        onDismiss = { pickerOpen = false },
        onChoose = { pickerOpen = false; choose(it) },
        onProfilesChanged = onProfilesChanged,
    )

    if (savingNew) SaveNewProfileDialog(
        profiles = profiles,
        route = route,
        routeGroupName = routeGroupName,
        initialName = "",
        onDismiss = { savingNew = false },
        onSave = { name, deviceId, deviceLabel ->
            savingNew = false
            val saved = AudioFreedomProfileStore.save(context, name, settings,
                deviceId = deviceId, deviceLabel = deviceLabel)
            onProfilesChanged(saved.id)
        },
    )

    pendingSwitch?.let { profile -> AlertDialog(onDismissRequest = { pendingSwitch = null },
        title = { Text("Discard unsaved changes?") },
        text = { Text("Switch to ${profile.name}? Your changes to ${selected?.name ?: "this profile"} haven't been saved.") },
        confirmButton = { TextButton(onClick = { pendingSwitch = null; onSelect(profile) }) { Text("Switch") } },
        dismissButton = { TextButton(onClick = { pendingSwitch = null }) { Text("Keep editing") } }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfilePickerSheet(
    profiles: List<AudioFreedomProfile>,
    selectedProfileId: String?,
    route: AudioRoute,
    rememberedProfileId: String?,
    groupNames: Map<String, String>,
    onRenameGroup: (id: String, name: String) -> Unit,
    onDismiss: () -> Unit,
    onChoose: (AudioFreedomProfile) -> Unit,
    onProfilesChanged: (String?) -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    // Other outputs start folded away; the connected one and "Any device" start open.
    var expanded by remember { mutableStateOf(setOf(route.id, "")) }
    var renaming by remember { mutableStateOf<AudioFreedomProfile?>(null) }
    var renamingGroup by remember { mutableStateOf<DeviceSection?>(null) }
    var deleting by remember { mutableStateOf<AudioFreedomProfile?>(null) }
    val targets = moveTargets(profiles, route)

    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("Profiles", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 12.dp),
                label = { Text("Search profiles or devices") }, singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) })
            val sections = deviceSections(profiles, route, query, groupNames)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                if (sections.all { it.profiles.isEmpty() }) item {
                    Text(if (query.isBlank()) "No profiles yet. Tap Save on the main screen to make one."
                        else "No matching profiles", Modifier.padding(vertical = 20.dp))
                }
                sections.forEach { section ->
                    val open = section.deviceId in expanded || query.isNotBlank()
                    item(key = "device:${section.deviceId}") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1f).clickable {
                                expanded = if (section.deviceId in expanded) expanded - section.deviceId
                                    else expanded + section.deviceId
                            }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (open) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight, null)
                                Text(
                                    section.label + if (section.connected) "  ·  connected" else "",
                                    Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Text(section.profiles.size.toString(), style = MaterialTheme.typography.labelSmall)
                            }
                            if (section.deviceId.isNotEmpty()) IconButton(onClick = { renamingGroup = section }) {
                                Icon(Icons.Rounded.Edit, "Rename group: ${section.label}")
                            }
                        }
                    }
                    if (open && section.connected && section.profiles.isEmpty()) item(key = "empty:${section.deviceId}") {
                        Text("Nothing saved for this output yet.",
                            Modifier.padding(start = 24.dp, top = 4.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (open) items(section.profiles, key = { it.id }) { profile ->
                        ProfileRow(
                            profile = profile,
                            active = profile.id == selectedProfileId,
                            remembered = profile.id == rememberedProfileId,
                            routeLabel = groupNames[route.id] ?: route.label,
                            targets = targets,
                            groupNames = groupNames,
                            onClick = { onChoose(profile) },
                            onRename = { renaming = profile },
                            onMove = { id, label ->
                                AudioFreedomProfileStore.move(context, profile.id, id, label)
                                onProfilesChanged(null)
                            },
                            onDuplicate = {
                                AudioFreedomProfileStore.manage(context, ProfileAction.Duplicate(profile.id))
                                onProfilesChanged(null)
                            },
                            onDelete = { deleting = profile },
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    renamingGroup?.let { section ->
        var name by remember(section.deviceId) { mutableStateOf(section.label) }
        AlertDialog(onDismissRequest = { renamingGroup = null },
            title = { Text("Rename group") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Group name") }) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    onRenameGroup(section.deviceId, name)
                    renamingGroup = null
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (section.deviceId in groupNames) TextButton(onClick = {
                        onRenameGroup(section.deviceId, "")
                        renamingGroup = null
                    }) { Text("Use device name") }
                    TextButton(onClick = { renamingGroup = null }) { Text("Cancel") }
                }
            })
    }

    renaming?.let { profile ->
        var name by remember(profile.id) { mutableStateOf(profile.name) }
        AlertDialog(onDismissRequest = { renaming = null },
            title = { Text("Rename profile") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    AudioFreedomProfileStore.rename(context, profile.id, name)
                    renaming = null
                    onProfilesChanged(null)
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } })
    }

    deleting?.let { profile ->
        AlertDialog(onDismissRequest = { deleting = null },
            title = { Text("Delete ${profile.name}?") },
            text = { Text("The profile is removed for good. Your current sound stays as it is.") },
            confirmButton = {
                TextButton(onClick = {
                    AudioFreedomProfileStore.delete(context, profile.id)
                    deleting = null
                    onProfilesChanged(null)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
    }
}

@Composable
private fun ProfileRow(
    profile: AudioFreedomProfile,
    active: Boolean,
    remembered: Boolean,
    routeLabel: String,
    targets: List<Pair<String, String>>,
    groupNames: Map<String, String>,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onMove: (String, String) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var moveOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        EqCurve(profile.settings, Modifier.size(width = 52.dp, height = 28.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(profile.name, style = MaterialTheme.typography.bodyLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            val details = buildList {
                if (remembered) add("Comes back on $routeLabel")
                addAll(effectBadges(profile.settings))
                if (profile.deviceGroup.isNotBlank() && profile.deviceId.isEmpty()) add("was “${profile.deviceGroup}”")
            }
            if (details.isNotEmpty()) Text(details.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (active) Icon(Icons.Rounded.Check, "Active profile", tint = MaterialTheme.colorScheme.primary)
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, "Profile options") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Rename…") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                    onClick = { menuOpen = false; onRename() })
                DropdownMenuItem(text = { Text("Move to…") }, leadingIcon = { Icon(Icons.Rounded.DevicesOther, null) },
                    onClick = { menuOpen = false; moveOpen = true })
                DropdownMenuItem(text = { Text("Duplicate") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                    onClick = { menuOpen = false; onDuplicate() })
                DropdownMenuItem(text = { Text("Delete…") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                    onClick = { menuOpen = false; onDelete() })
            }
            DropdownMenu(expanded = moveOpen, onDismissRequest = { moveOpen = false }) {
                targets.forEach { (id, label) ->
                    DropdownMenuItem(
                        text = { Text(groupNames[id] ?: label) },
                        trailingIcon = if (id == profile.deviceId) {
                            { Icon(Icons.Rounded.Check, null) }
                        } else null,
                        onClick = { moveOpen = false; if (id != profile.deviceId) onMove(id, label) },
                    )
                }
            }
        }
    }
}

/** Short names of the effects a profile switches on, so profiles are recognisable. */
private fun effectBadges(s: AudioFreedomSettings): List<String> = buildList {
    if (!s.equalizerEnabled) add("EQ off")
    if (s.dynamicBassEnabled) add("Bass")
    if (s.detailRecoveryEnabled) add("Detail")
    if (s.immersiveFieldEnabled) add("Immersive")
    if (s.reverbEnabled) add("Reverb")
    if (s.volumeLevelerEnabled) add("Leveler")
    if (s.stereoRoutingEnabled) add("Routing")
}

/** A tiny drawing of the profile's EQ, flat line when the EQ is off. */
@Composable
private fun EqCurve(settings: AudioFreedomSettings, modifier: Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.outlineVariant
    val gains = if (settings.equalizerEnabled) settings.bandGainsMillibels else List(EqualizerBandCount) { 0 }
    val scale = maxOf(1200, gains.maxOfOrNull { abs(it) } ?: 0).toFloat()
    Canvas(modifier) {
        val mid = size.height / 2f
        drawLine(axis, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.dp.toPx())
        if (gains.isEmpty()) return@Canvas
        val path = Path()
        gains.forEachIndexed { i, g ->
            val x = if (gains.size == 1) size.width / 2f else size.width * i / (gains.size - 1)
            val y = mid - (g / scale) * (size.height / 2f - 2.dp.toPx())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun SaveNewProfileDialog(
    profiles: List<AudioFreedomProfile>,
    route: AudioRoute,
    routeGroupName: String,
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (name: String, deviceId: String, deviceLabel: String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var forThisDevice by remember { mutableStateOf(true) }
    val deviceId = if (forThisDevice) route.id else ""
    val clash = profiles.firstOrNull {
        it.deviceId == deviceId && it.name.equals(name.trim(), ignoreCase = true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save as new profile") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, singleLine = true,
                    label = { Text("Name") }, placeholder = { Text("e.g. Bass heavy") })
                Spacer(Modifier.height(12.dp))
                Text("For", style = MaterialTheme.typography.labelLarge)
                listOf(true to routeGroupName, false to "Any device").forEach { (thisDevice, label) ->
                    Row(Modifier.fillMaxWidth().selectable(selected = forThisDevice == thisDevice,
                        role = Role.RadioButton, onClick = { forThisDevice = thisDevice }),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = forThisDevice == thisDevice, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (clash != null) Text("“${clash.name}” already exists there; saving updates it.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave(name.trim(), deviceId, if (forThisDevice) route.label else "")
            }) { Text(if (clash != null) "Update" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
