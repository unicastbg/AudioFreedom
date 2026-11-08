package com.svetlio.audiofreedom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun EqualizerBandwidthControls(
    qValues: List<Int>,
    selectedBand: Int,
    onSelect: (Int) -> Unit,
    enabled: Boolean,
    onQChange: (Int, Int) -> Unit,
    onCommit: () -> Unit,
    onReset: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var entry by remember { mutableStateOf<String?>(null) }
    val band = selectedBand.coerceIn(0, 9)
    val q = qValues[band]
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            TextButton(onClick = { menu = true }, enabled = enabled) {
                Text("Bandwidth · ${EqualizerBandLabels[band]} Hz")
                Icon(Icons.Rounded.ExpandMore, contentDescription = "Select bandwidth band")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                EqualizerBandLabels.forEachIndexed { index, label ->
                    DropdownMenuItem(text = { Text("$label Hz") }, onClick = { onSelect(index); menu = false })
                }
            }
        }
        TextButton(onClick = { entry = String.format(Locale.US, "%.2f", q / 100f) }, enabled = enabled) {
            Text(String.format(Locale.US, "Q %.2f", q / 100f))
        }
        IconButton(onClick = onReset, enabled = enabled) {
            Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset all bandwidths")
        }
    }
    Slider(value = q.toFloat(), valueRange = 30f..500f, enabled = enabled,
        onValueChange = { onQChange(band, (it / 5f).roundToInt().times(5).coerceIn(30, 500)) },
        onValueChangeFinished = onCommit)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Broad", style = MaterialTheme.typography.labelSmall)
        Text("Narrow", style = MaterialTheme.typography.labelSmall)
    }
    entry?.let { text ->
        val value = text.replace(',', '.').toDoubleOrNull()
        val valid = value != null && value.isFinite() && value in 0.3..5.0
        AlertDialog(onDismissRequest = { entry = null }, title = { Text("Q · ${EqualizerBandLabels[band]} Hz") },
            text = { OutlinedTextField(value = text, onValueChange = { entry = it.take(12) },
                singleLine = true, label = { Text("Q (0.30–5.00)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = !valid) },
            confirmButton = { TextButton(enabled = valid, onClick = {
                onQChange(band, (value!! * 100).roundToInt()); onCommit(); entry = null
            }) { Text("Apply") } },
            dismissButton = { TextButton(onClick = { entry = null }) { Text("Cancel") } })
    }
}

@Composable
internal fun StereoRoutingControls(settings: AudioFreedomSettings, onChanged: (AudioFreedomSettings) -> Unit,
                                   onCommit: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { menu = true }, enabled = settings.stereoRoutingEnabled) {
            Text(settings.stereoRoutingMode.label)
            Icon(Icons.Rounded.ExpandMore, contentDescription = "Select stereo routing mode")
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            StereoRoutingMode.entries.forEach { mode ->
                DropdownMenuItem(text = { Text(mode.label) }, onClick = {
                    onChanged(settings.copy(stereoRoutingMode = mode)); onCommit(); menu = false
                })
            }
        }
    }
    if (settings.stereoRoutingMode == StereoRoutingMode.SingleOutput) {
        Row {
            listOf(false to "Left", true to "Right").forEach { (right, label) ->
                FilterChip(selected = settings.stereoOutputRight == right, label = { Text(label) },
                    enabled = settings.stereoRoutingEnabled,
                    onClick = { onChanged(settings.copy(stereoOutputRight = right)); onCommit() })
            }
        }
    }
    if (settings.stereoRoutingMode == StereoRoutingMode.Normal || settings.stereoRoutingMode == StereoRoutingMode.Swap) {
        val balance = settings.stereoBalancePercent
        SettingSlider("Balance", when {
            balance < 0 -> "L ${-balance}%"
            balance > 0 -> "R $balance%"
            else -> "Center"
        }, balance.toFloat(), { onChanged(settings.copy(stereoBalancePercent = it.roundToInt().coerceIn(-100, 100))) },
            onCommit, -100f..100f, 199, settings.stereoRoutingEnabled)
    }
}
