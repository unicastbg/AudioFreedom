package com.svetlio.audiofreedom

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
private fun ToolsHeader(title: String, summary: String, expanded: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onToggle) {
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SoundComparisonPanel(
    comparison: SoundComparison,
    current: AudioFreedomSettings,
    expanded: Boolean,
    available: Boolean,
    onToggle: () -> Unit,
    onCapture: (ComparisonSlot) -> Unit,
    onSelect: (ComparisonSlot) -> Unit,
    onClear: () -> Unit,
) {
    val matching = comparison.matching(current)
    ToolsHeader("A/B comparison", matching?.let { "Current: ${it.name}" }
        ?: if (comparison.a == null && comparison.b == null) "No snapshots" else "Modified settings",
        expanded, onToggle)
    if (expanded) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ComparisonSlot.entries.forEachIndexed { index, slot ->
                SegmentedButton(selected = matching == slot, onClick = { onSelect(slot) },
                    enabled = available,
                    shape = SegmentedButtonDefaults.itemShape(index, 2)) {
                    Text(if (comparison.settings(slot) == null) "Store ${slot.name}" else slot.name)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ComparisonSlot.entries.forEach { slot ->
                Box(Modifier.weight(1f)) {
                TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text("Replace ${slot.name} with current DSP settings") } },
                    state = rememberTooltipState(), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { onCapture(slot) }, enabled = available,
                        modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Save, contentDescription = null)
                        Text("Store ${slot.name}", Modifier.padding(start = 6.dp), maxLines = 1)
                    }
                }
                }
            }
            TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text("Clear snapshots without changing the sound") } },
                state = rememberTooltipState()) {
                IconButton(onClick = onClear, enabled = comparison.a != null || comparison.b != null) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Clear comparison snapshots")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
internal fun PlaybackAnalysisPanel(
    expanded: Boolean,
    processing: Boolean,
    supported: Boolean?,
    frame: PlaybackAnalysisFrame?,
    now: Long,
    onToggle: () -> Unit,
) {
    val live = processing && frame?.isFresh(now) == true
    val status = when {
        !processing -> "Processing is off"
        !expanded -> "Paused"
        supported == false -> "Driver update required"
        live -> "Live"
        else -> "Waiting for audio"
    }
    ToolsHeader("Playback analysis", status, expanded, onToggle)
    if (expanded) {
        val values = frame?.values?.takeIf { live }
        MetricRow("Input RMS", values?.inputRmsMillibels?.db("dBFS"))
        MetricRow("Output RMS", values?.outputRmsMillibels?.db("dBFS"))
        MetricRow("Output crest factor", values?.crestMillibels?.db("dB"))
        MetricRow("Stereo correlation", values?.correlationPermille?.let {
            String.format(Locale.US, "%+.2f", it / 1000f)
        })
        MetricRow("Near full scale", values?.nearFullScalePpm?.let {
            String.format(Locale.US, "%.2f%%", it / 10000f)
        })
        Text("Output spectrum", style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
        EqualizerBandLabels.forEachIndexed { index, label ->
            Row(Modifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(0.18f)) {
                    Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                LinearProgressIndicator(
                    progress = { ((values?.bandsMillibels?.get(index) ?: -12000) + 6000)
                        .coerceIn(0, 6000) / 6000f },
                    modifier = Modifier.weight(0.52f).height(4.dp),
                )
                Box(Modifier.weight(0.30f), contentAlignment = Alignment.CenterEnd) {
                    Text(values?.bandsMillibels?.get(index)?.db("") ?: "--",
                        style = MaterialTheme.typography.labelSmall, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun MetricRow(label: String, value: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value ?: "--", style = MaterialTheme.typography.bodyMedium)
    }
}

private fun Int.db(unit: String): String = if (this <= -12000) "--"
    else String.format(Locale.US, "%.1f%s", this / 100f, if (unit.isEmpty()) "" else " $unit")
