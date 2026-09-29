package com.svetlio.audiofreedom

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun AssistantPanel(
    expanded: Boolean,
    command: String,
    proposal: AssistantProposal?,
    message: String?,
    busy: Boolean,
    canUndo: Boolean,
    voiceEnabled: Boolean,
    voiceState: AssistantVoiceState,
    feedbackCommand: String?,
    onExpandedChange: (Boolean) -> Unit,
    onCommandChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onVoice: () -> Unit,
    onApply: () -> Unit,
    onDiscard: () -> Unit,
    onUndo: () -> Unit,
    onFeedback: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1F)) {
                Text("Assistant", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = when {
                        voiceState == AssistantVoiceState.Listening -> "Listening. Tap stop when finished"
                        voiceState == AssistantVoiceState.Transcribing -> "Transcribing on this phone"
                        proposal != null -> "Review proposed changes"
                        canUndo -> "Change applied"
                        else -> "Ready for a sound command"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (canUndo) {
                IconButton(onClick = onUndo) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Undo,
                        contentDescription = "Undo assistant change",
                    )
                }
            }
            IconButton(onClick = { onExpandedChange(!expanded) }) {
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse assistant" else "Expand assistant",
                )
            }
        }
        if (expanded) {
            OutlinedTextField(
                value = command,
                onValueChange = onCommandChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Describe the sound you want") },
                singleLine = true,
                trailingIcon = {
                    if (busy || voiceState == AssistantVoiceState.Transcribing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (voiceEnabled) {
                                IconButton(onClick = onVoice) {
                                    Icon(
                                        if (voiceState == AssistantVoiceState.Listening) {
                                            Icons.Rounded.StopCircle
                                        } else {
                                            Icons.Rounded.Mic
                                        },
                                        contentDescription = if (
                                            voiceState == AssistantVoiceState.Listening
                                        ) {
                                            "Stop listening"
                                        } else {
                                            "Speak command"
                                        },
                                    )
                                }
                            }
                            IconButton(
                                onClick = onSubmit,
                                enabled = command.isNotBlank() &&
                                    voiceState == AssistantVoiceState.Idle,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "Send command",
                                )
                            }
                        }
                    }
                },
                enabled = !busy && voiceState == AssistantVoiceState.Idle,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "More bass",
                    "Clearer vocals",
                    "Less harshness",
                    "Wider stage",
                ).forEach { suggestion ->
                    AssistChip(
                        onClick = { onCommandChange(suggestion) },
                        label = { Text(suggestion) },
                        enabled = !busy,
                    )
                }
            }
            proposal?.let {
                Text(
                    it.title,
                    modifier = Modifier.padding(top = 14.dp),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    it.explanation,
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                it.changes.forEach { change ->
                    Text(
                        "- $change",
                        modifier = Modifier.padding(top = 3.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDiscard) {
                        Icon(Icons.Rounded.Close, contentDescription = null)
                        Text("Discard")
                    }
                    TextButton(onClick = onApply) {
                        Icon(Icons.Rounded.Check, contentDescription = null)
                        Text("Apply")
                    }
                }
            }
            if (proposal == null && message != null) {
                Text(
                    message,
                    modifier = Modifier.padding(top = 10.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (proposal == null && feedbackCommand != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Did that sound right?",
                        modifier = Modifier.weight(1F),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    IconButton(onClick = { onFeedback(true) }) {
                        Icon(Icons.Rounded.ThumbUp, contentDescription = "Good result")
                    }
                    IconButton(onClick = { onFeedback(false) }) {
                        Icon(Icons.Rounded.ThumbDown, contentDescription = "Poor result")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
