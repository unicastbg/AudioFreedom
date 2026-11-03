package com.svetlio.audiofreedom

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.util.Locale

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileBrowserRow(
    profiles: List<AudioFreedomProfile>, selectedProfileId: String?, profileModified: Boolean,
    onProfileSelected: (AudioFreedomProfile) -> Unit, onAdd: () -> Unit,
    onSave: () -> Unit, onDelete: () -> Unit,
    routeLabel: String? = null, assignedProfileId: String? = null,
    onAssignDefault: ((String?) -> Unit)? = null,
) {
    val selected = profiles.firstOrNull { it.id == selectedProfileId }
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var pendingSwitch by remember { mutableStateOf<AudioFreedomProfile?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clickable { query = ""; open = true }.padding(vertical = 8.dp)) {
            Text("Profile", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(selected?.name ?: "Current settings", modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                Icon(Icons.Rounded.ExpandMore, "Choose profile")
            }
            if (profileModified) Text("Modified", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
            else if (!selected?.deviceGroup.isNullOrEmpty()) Text(selected!!.deviceGroup,
                style = MaterialTheme.typography.labelSmall)
        }
        IconButton(onClick = onAdd) { Icon(Icons.Rounded.Add, "Save as new profile") }
        IconButton(onClick = onSave) { Icon(Icons.Rounded.Save, "Save current settings") }
        IconButton(onClick = { confirmDelete = true }, enabled = selected != null) {
            Icon(Icons.Rounded.Delete, "Delete selected profile")
        }
    }
    if (open) ModalBottomSheet(onDismissRequest = { open = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("Profiles", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 12.dp),
                label = { Text("Search profiles or devices") }, singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) })
            val groups = groupedProfiles(profiles, query)
            if (onAssignDefault != null && routeLabel != null) Text("Automatic default for $routeLabel",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                if (groups.isEmpty()) item { Text("No matching profiles", Modifier.padding(vertical = 20.dp)) }
                groups.forEach { (group, values) ->
                    item(key = "group:$group") {
                        Text(group.ifEmpty { "Ungrouped" }, Modifier.padding(top = 16.dp, bottom = 4.dp),
                            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                    items(values, key = { it.id }) { profile ->
                        Row(Modifier.fillMaxWidth().clickable {
                            open = false
                            if (profileModified) pendingSwitch = profile
                            else onProfileSelected(profile)
                        }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(profile.name, style = MaterialTheme.typography.bodyLarge)
                                if (profile.id == assignedProfileId && routeLabel != null)
                                    Text("Default for $routeLabel", style = MaterialTheme.typography.labelSmall)
                            }
                            if (profile.id == selectedProfileId) Icon(Icons.Rounded.Check, "Active profile")
                            if (onAssignDefault != null) Checkbox(
                                checked = profile.id == assignedProfileId,
                                onCheckedChange = { checked -> onAssignDefault(if (checked) profile.id else null) },
                                modifier = Modifier.semantics { contentDescription =
                                    if (profile.id == assignedProfileId) "Remove device default" else "Set as device default" },
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
    pendingSwitch?.let { profile -> AlertDialog(onDismissRequest = { pendingSwitch = null },
        title = { Text("Discard unsaved changes?") },
        text = { Text("Switch to ${profile.name}? Your current adjustments have not been saved to a profile.") },
        confirmButton = { TextButton(onClick = { pendingSwitch = null; onProfileSelected(profile) }) { Text("Switch") } },
        dismissButton = { TextButton(onClick = { pendingSwitch = null }) { Text("Keep editing") } }) }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("Delete ${selected?.name.orEmpty()}?") },
        text = { Text("The saved profile and its automatic device assignments will be removed. Current sound settings are kept.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
}
