package com.svetlio.audiofreedom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SettingsScreen(
    route: AudioRoute,
    profiles: List<AudioFreedomProfile>,
    preferences: AppPreferences,
    assignedProfileId: String?,
    onPreferencesChanged: (AppPreferences) -> Unit,
    onProfileAssigned: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var profileMenuExpanded by remember { mutableStateOf(false) }
    val assignedProfile = profiles.firstOrNull { it.id == assignedProfileId }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        SettingsSectionTitle("Audio output")
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AudioRouteIcon(route, contentDescription = null)
            Column(modifier = Modifier.padding(start = 16.dp).weight(1F)) {
                Text(route.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    route.kind.displayName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SettingsSectionTitle("Device profiles")
        SettingsSwitchRow(
            title = "Automatic profiles",
            summary = "Apply the assigned profile when this output connects",
            checked = preferences.automaticDeviceProfiles,
            onCheckedChange = {
                onPreferencesChanged(preferences.copy(automaticDeviceProfiles = it))
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1F)) {
                Text("Profile for ${route.label}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    assignedProfile?.name ?: "Not assigned",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            androidx.compose.foundation.layout.Box {
                TextButton(onClick = { profileMenuExpanded = true }) {
                    Text("Choose")
                    Icon(Icons.Rounded.ExpandMore, contentDescription = null)
                }
                DropdownMenu(
                    expanded = profileMenuExpanded,
                    onDismissRequest = { profileMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("No profile") },
                        onClick = {
                            profileMenuExpanded = false
                            onProfileAssigned(null)
                        },
                    )
                    profiles.forEach { profile ->
                        DropdownMenuItem(
                            text = { Text(profile.name) },
                            onClick = {
                                profileMenuExpanded = false
                                onProfileAssigned(profile.id)
                            },
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SettingsSectionTitle("Notification")
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null)
            Column(modifier = Modifier.padding(start = 16.dp).weight(1F)) {
                Text("Processing status", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Required while system-wide processing is active",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        SettingsSwitchRow(
            title = "Show connected device",
            summary = route.label,
            checked = preferences.showDeviceInNotification,
            onCheckedChange = {
                onPreferencesChanged(preferences.copy(showDeviceInNotification = it))
            },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SettingsSectionTitle("Appearance")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ThemePreference.entries.forEachIndexed { index, theme ->
                SegmentedButton(
                    selected = preferences.theme == theme,
                    onClick = { onPreferencesChanged(preferences.copy(theme = theme)) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = ThemePreference.entries.size,
                    ),
                ) {
                    Text(theme.label)
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
internal fun AudioRouteIcon(route: AudioRoute, contentDescription: String?) {
    Icon(
        imageVector = route.kind.icon,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.primary,
    )
}

private val AudioRouteKind.icon: ImageVector
    get() = when (this) {
        AudioRouteKind.PhoneSpeaker -> Icons.Rounded.PhoneAndroid
        AudioRouteKind.WiredHeadphones -> Icons.Rounded.Headphones
        AudioRouteKind.Bluetooth -> Icons.Rounded.BluetoothAudio
        AudioRouteKind.Usb -> Icons.Rounded.Usb
        AudioRouteKind.Hdmi -> Icons.Rounded.Tv
        AudioRouteKind.Other -> Icons.Rounded.DevicesOther
    }

private val AudioRouteKind.displayName: String
    get() = when (this) {
        AudioRouteKind.PhoneSpeaker -> "Built-in speaker"
        AudioRouteKind.WiredHeadphones -> "Wired audio"
        AudioRouteKind.Bluetooth -> "Bluetooth audio"
        AudioRouteKind.Usb -> "USB audio"
        AudioRouteKind.Hdmi -> "HDMI audio"
        AudioRouteKind.Other -> "External audio"
    }

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1F)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                summary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
