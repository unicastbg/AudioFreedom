package com.svetlio.audiofreedom

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
internal fun ProfileGroupSelector(group: String, groups: List<String>, onChange: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    Box {
        OutlinedTextField(group, { onChange(it.take(100)) },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            label = { Text("Group") }, placeholder = { Text("Ungrouped") }, singleLine = true,
            trailingIcon = { IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.ExpandMore, "Choose group")
            } })
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Ungrouped") }, onClick = { onChange(""); menu = false })
            groups.forEach { existing ->
                DropdownMenuItem(text = { Text(existing) }, onClick = { onChange(existing); menu = false })
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text("New group") }, leadingIcon = { Icon(Icons.Rounded.Add, null) },
                onClick = { onChange(""); menu = false; focus.requestFocus(); keyboard?.show() })
        }
    }
}

@Composable
internal fun ProfileManagerScreen(
    profiles: List<AudioFreedomProfile>,
    activeId: String?,
    onAction: (ProfileAction) -> String?,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var collapsed by remember { mutableStateOf(emptySet<String>()) }
    var editing by remember { mutableStateOf<ProfileAction?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val currentAction by rememberUpdatedState(onAction)
    LaunchedEffect(profiles.map { it.id }) { selected = selected.intersect(profiles.map { it.id }.toSet()) }
    fun perform(action: ProfileAction): Boolean {
        val error = currentAction(action)
        message = error
        return error == null
    }
    val groups = groupedProfiles(profiles, query)
    val visible = groups.flatMap { it.second }.map { it.id }.toSet()
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 12.dp),
            label = { Text("Search profiles or groups") }, singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, null) })
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = visible.isNotEmpty() && selected.containsAll(visible),
                onCheckedChange = { checked -> selected = if (checked) selected + visible else selected - visible },
                enabled = visible.isNotEmpty(),
                modifier = Modifier.semantics { contentDescription = "Select all results" })
            Text(if (selected.isEmpty()) profileCount(profiles.size) else "${selected.size} selected",
                Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            if (selected.isNotEmpty()) IconButton(onClick = { selected = emptySet() }) {
                Icon(Icons.Rounded.Close, "Clear selection")
            }
        }
        if (selected.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            TextButton(onClick = { message = null; editing = ProfileAction.Move(selected, "") }) {
                Icon(Icons.AutoMirrored.Rounded.DriveFileMove, null); Text("Move to group")
            }
            TextButton(onClick = { message = null; editing = ProfileAction.Delete(selected) }) {
                Icon(Icons.Rounded.Delete, null); Text("Delete")
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        LazyColumn(Modifier.weight(1f).testTag("profile-manager-list"), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (groups.isEmpty()) item { Text(if (profiles.isEmpty()) "No saved profiles" else "No matching profiles",
                Modifier.padding(vertical = 24.dp)) }
            groups.forEach { (group, values) ->
                val key = group.lowercase(Locale.ROOT)
                item(key = "group:$key") {
                    var menu by remember { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).clickable {
                            collapsed = if (key in collapsed) collapsed - key else collapsed + key
                        }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (key in collapsed && query.isBlank()) Icons.Rounded.ChevronRight else Icons.Rounded.ExpandMore, null)
                            Text(group.ifEmpty { "Ungrouped" }, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            Text(values.size.toString(), style = MaterialTheme.typography.labelMedium)
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Group actions: ${group.ifEmpty { "Ungrouped" }}") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("Select group") }, onClick = {
                                    selected = selected + values.map { it.id }; menu = false
                                })
                                if (group.isNotBlank()) {
                                    DropdownMenuItem(text = { Text("Rename group") }, onClick = {
                                        message = null; editing = ProfileAction.RenameGroup(group, group); menu = false
                                    })
                                    DropdownMenuItem(text = { Text("Remove group") }, onClick = {
                                        message = null; editing = ProfileAction.RemoveGroup(group); menu = false
                                    })
                                }
                            }
                        }
                    }
                }
                if (key !in collapsed || query.isNotBlank()) items(values, key = { it.id }) { profile ->
                    var menu by remember { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth().clickable {
                        selected = if (profile.id in selected) selected - profile.id else selected + profile.id
                    }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(profile.id in selected, { checked ->
                            selected = if (checked) selected + profile.id else selected - profile.id
                        }, Modifier.semantics { contentDescription = "Select ${profile.displayName}" })
                        Column(Modifier.weight(1f)) {
                            Text(profile.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (profile.id == activeId) Text("Active profile", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Profile actions: ${profile.displayName}") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("Rename") }, onClick = {
                                    message = null; editing = ProfileAction.Rename(profile.id, profile.name); menu = false
                                })
                                DropdownMenuItem(text = { Text("Move to group") }, onClick = {
                                    message = null; editing = ProfileAction.Move(setOf(profile.id), profile.deviceGroup); menu = false
                                })
                                DropdownMenuItem(text = { Text("Duplicate") }, onClick = {
                                    perform(ProfileAction.Duplicate(profile.id)); menu = false
                                })
                                DropdownMenuItem(text = { Text("Delete") }, onClick = {
                                    message = null; editing = ProfileAction.Delete(setOf(profile.id)); menu = false
                                })
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
    editing?.let { action ->
        var value by remember(action) { mutableStateOf(when (action) {
            is ProfileAction.Rename -> action.name
            is ProfileAction.RenameGroup -> action.name
            is ProfileAction.Move -> action.group
            else -> ""
        }) }
        val deleting = action is ProfileAction.Delete
        val removing = action is ProfileAction.RemoveGroup
        AlertDialog(onDismissRequest = { editing = null; message = null },
            title = { Text(when (action) {
                is ProfileAction.Move -> "Move ${profileCount(action.ids.size)}"
                is ProfileAction.Rename -> "Rename profile"
                is ProfileAction.RenameGroup -> "Rename group"
                is ProfileAction.RemoveGroup -> "Remove group?"
                is ProfileAction.Delete -> "Delete ${profileCount(action.ids.size)}?"
                else -> "Profile"
            }) },
            text = { Column {
                when {
                    action is ProfileAction.Move -> ProfileGroupSelector(value, profileGroups(profiles)) { value = it; message = null }
                    removing -> Text("Profiles will be moved to Ungrouped. Their sound settings and device assignments are kept.")
                    deleting -> Text("The selected saved profiles and their device assignments will be removed. Current sound settings are kept.")
                    else -> OutlinedTextField(value, { value = it.take(100); message = null },
                        label = { Text("Name") }, singleLine = true)
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            } },
            confirmButton = { TextButton(enabled = deleting || removing || action is ProfileAction.Move || value.isNotBlank(),
                onClick = {
                    val request = when (action) {
                        is ProfileAction.Move -> action.copy(group = value)
                        is ProfileAction.Rename -> action.copy(name = value)
                        is ProfileAction.RenameGroup -> action.copy(name = value)
                        else -> action
                    }
                    if (perform(request)) { editing = null; if (deleting) selected = emptySet() }
                }) { Text(if (deleting) "Delete" else if (removing) "Remove group" else if (action is ProfileAction.Move) "Move" else "Save") } },
            dismissButton = { TextButton(onClick = { editing = null; message = null }) { Text("Cancel") } })
    }
}
