package com.svetlio.audiofreedom

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun EqualizerRangeControl(
    preferences: AppPreferences,
    driverUpdateRequired: Boolean = false,
    onPreferencesChanged: (AppPreferences) -> Unit,
) {
    var showWarning by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1F).padding(end = 12.dp)) {
            Text("Extended EQ range", style = MaterialTheme.typography.bodyLarge)
            Text(
                if (preferences.extendedEqualizerRange) "Unlocked: -20 to +20 dB" else "Standard: -12 to +12 dB",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Turning this off caps existing EQ bands at -12 to +12 dB.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = preferences.extendedEqualizerRange,
            onCheckedChange = { enabled ->
                if (enabled && !preferences.extendedEqualizerRangeAcknowledged) showWarning = true
                else onPreferencesChanged(preferences.copy(extendedEqualizerRange = enabled))
            },
            modifier = Modifier.semantics { contentDescription = "Extended EQ range" },
        )
    }
    if (preferences.extendedEqualizerRange && driverUpdateRequired) {
        Text("Your driver is older than 0.9.8-beta and limits EQ to +/-12 dB. Install the bundled module in Settings > Updates, then restart your phone.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (showWarning) {
        AlertDialog(
            onDismissRequest = { showWarning = false },
            icon = { Icon(Icons.Rounded.WarningAmber, contentDescription = null) },
            title = { Text("Unlock extended EQ range?") },
            text = { Text("Extreme boosts can cause distortion and may damage speakers or headphones at high playback levels. " +
                "More boost does not guarantee deeper bass or better sound. Output limiting cannot guarantee protection against physical damage.\n\n" +
                "Start at a low playback volume. This unlock changes the EQ range only; it does not adjust your existing gains or preamp.",
                modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = {
                TextButton(onClick = {
                    showWarning = false
                    onPreferencesChanged(preferences.copy(extendedEqualizerRange = true,
                        extendedEqualizerRangeAcknowledged = true))
                }) { Text("Unlock") }
            },
            dismissButton = { TextButton(onClick = { showWarning = false }) { Text("Cancel") } },
        )
    }
}
