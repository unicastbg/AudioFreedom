package com.svetlio.audiofreedom

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight

@Composable
internal fun AssistantSettingsScreen(
    preferences: AppPreferences,
    languageModelInfo: AssistantModelPackInfo,
    voiceModelInfo: AssistantModelPackInfo,
    activeModelPack: AssistantModelPackType?,
    modelDownloadProgress: AssistantModelDownloadProgress?,
    modelMessage: String?,
    voiceTestMessage: String?,
    widgetMessage: String?,
    onPreferencesChanged: (AppPreferences) -> Unit,
    onAddVoiceWidget: () -> Unit,
    onInstallLanguageModel: () -> Unit,
    onImportLanguageModel: () -> Unit,
    onRemoveLanguageModel: () -> Unit,
    onInstallVoiceModel: () -> Unit,
    onRemoveVoiceModel: () -> Unit,
    onTestVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var commandGuideExpanded by rememberSaveable { mutableStateOf(false) }
    var advancedModelOptionsExpanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        AssistantSettingsSectionTitle("Local assistant")
        AssistantSettingsSwitchRow(
            title = "Enable assistant",
            summary = "Accept typed sound and profile commands",
            checked = preferences.assistantEnabled,
            onCheckedChange = {
                onPreferencesChanged(preferences.copy(assistantEnabled = it))
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null)
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text("On-device only", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Commands and DSP settings stay on this phone",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        AssistantSettingsSectionTitle("Home screen")
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Mic, contentDescription = null)
            Column(modifier = Modifier.padding(start = 16.dp).weight(1F)) {
                Text("Voice widget", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Open the compact voice-command panel from your home screen",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            TextButton(onClick = onAddVoiceWidget) {
                Text("Add")
            }
        }
        widgetMessage?.let { message ->
            Text(
                message,
                modifier = Modifier.padding(bottom = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        AssistantSettingsSectionTitle("Command guide")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { commandGuideExpanded = !commandGuideExpanded }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1F)) {
                Text("What can I say?", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Standard sound, effect, profile, and undo commands",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Icon(
                if (commandGuideExpanded) {
                    Icons.Rounded.ExpandLess
                } else {
                    Icons.Rounded.ExpandMore
                },
                contentDescription = if (commandGuideExpanded) {
                    "Collapse command guide"
                } else {
                    "Expand command guide"
                },
            )
        }
        if (commandGuideExpanded) {
            AssistantCommandGuide()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        AssistantSettingsSectionTitle("Language and voice")
        Text(
            "Command language",
            modifier = Modifier.padding(bottom = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            AssistantLanguage.entries.forEachIndexed { index, language ->
                SegmentedButton(
                    selected = preferences.assistantLanguage == language,
                    onClick = {
                        onPreferencesChanged(preferences.copy(assistantLanguage = language))
                    },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = AssistantLanguage.entries.size,
                    ),
                ) {
                    Text(language.label)
                }
            }
        }
        Text(
            "Automatic retries short unrecognized commands as Bulgarian. Select Bulgarian for the most consistent Cyrillic transcription.",
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Mic, contentDescription = null)
            Column(modifier = Modifier.padding(start = 16.dp).weight(1F)) {
                Text("Push-to-talk", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (voiceModelInfo.installed) {
                        "Transcribe spoken commands locally with Whisper"
                    } else {
                        "Turn on to install the optional 75 MB voice language pack"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(
                checked = preferences.assistantVoiceEnabled,
                onCheckedChange = {
                    if (it && !voiceModelInfo.installed) {
                        onInstallVoiceModel()
                    } else {
                        onPreferencesChanged(preferences.copy(assistantVoiceEnabled = it))
                    }
                },
                enabled = activeModelPack == null,
            )
        }
        Text(
            "Listening mode",
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            AssistantVoiceMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = preferences.assistantVoiceMode == mode,
                    onClick = {
                        onPreferencesChanged(preferences.copy(assistantVoiceMode = mode))
                    },
                    enabled = mode == AssistantVoiceMode.PushToTalk,
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = AssistantVoiceMode.entries.size,
                    ),
                ) {
                    Text(mode.label)
                }
            }
        }
        Text(
            "The planned Audio Freedom wake phrase needs a separate low-power keyword pack. It is disabled until that runtime is ready.",
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        AssistantSettingsSwitchRow(
            title = "Apply after recognition",
            summary = "Run a recognized command without reviewing it on screen",
            checked = preferences.assistantAutoSubmitVoice,
            enabled = preferences.assistantVoiceEnabled && voiceModelInfo.installed,
            onCheckedChange = {
                onPreferencesChanged(preferences.copy(assistantAutoSubmitVoice = it))
            },
        )
        AssistantSettingsSwitchRow(
            title = "Pause media while listening",
            summary = "Ask the active player to pause while a voice command is captured",
            checked = preferences.assistantPauseMediaForVoice,
            enabled = preferences.assistantVoiceEnabled && voiceModelInfo.installed,
            onCheckedChange = {
                onPreferencesChanged(preferences.copy(assistantPauseMediaForVoice = it))
            },
        )
        AssistantSettingsSwitchRow(
            title = "Speak recognized command",
            summary = "Read back the validated interpretation; uses an installed offline system voice",
            checked = preferences.assistantSpeakVoiceConfirmation,
            enabled = voiceModelInfo.installed,
            onCheckedChange = {
                onPreferencesChanged(preferences.copy(assistantSpeakVoiceConfirmation = it))
            },
        )
        TextButton(
            onClick = onTestVoice,
            enabled = voiceModelInfo.installed,
        ) {
            Text("Test offline voice")
        }
        voiceTestMessage?.let { message ->
            Text(
                message,
                modifier = Modifier.padding(bottom = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        AssistantSettingsSectionTitle("Safety")
        Text("Apply behavior", style = MaterialTheme.typography.bodyLarge)
        AssistantApplyMode.entries.forEach { mode ->
            AssistantChoiceRow(
                title = mode.label,
                summary = mode.summary,
                selected = preferences.assistantApplyMode == mode,
                onClick = {
                    onPreferencesChanged(preferences.copy(assistantApplyMode = mode))
                },
            )
        }
        Text(
            "Adjustment strength",
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            AssistantAdjustmentStrength.entries.forEachIndexed { index, strength ->
                SegmentedButton(
                    selected = preferences.assistantAdjustmentStrength == strength,
                    onClick = {
                        onPreferencesChanged(
                            preferences.copy(assistantAdjustmentStrength = strength),
                        )
                    },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = AssistantAdjustmentStrength.entries.size,
                    ),
                ) {
                    Text(strength.label)
                }
            }
        }
        Text(
            text = "Maximum ${preferences.assistantAdjustmentStrength.maximumBandDeltaMillibels / 100F} dB per EQ band and command",
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        AssistantSettingsSwitchRow(
            title = "On-device personalization",
            summary = "Use private thumbs up or down results to improve future assistant decisions",
            checked = preferences.assistantAskForFeedback,
            onCheckedChange = {
                onPreferencesChanged(preferences.copy(assistantAskForFeedback = it))
            },
        )
        Text(
            "Commands, ratings, and learned preferences stay on this phone and are never sent over the internet. Personalization guides the local model; it does not rewrite its model weights.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 20.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        AssistantSettingsSectionTitle("Model packs")
        AssistantModelRow(
            title = "Language model",
            summary = modelPackSummary(languageModelInfo, AssistantModelCatalog.language),
            busy = activeModelPack == AssistantModelPackType.Language,
            actionsEnabled = activeModelPack == null,
            progress = modelDownloadProgress
                .takeIf { activeModelPack == AssistantModelPackType.Language },
            primaryAction = when {
                !languageModelInfo.installed -> "Install"
                languageModelInfo.hasUpdate(AssistantModelCatalog.language) -> "Update"
                else -> null
            },
            onPrimaryAction = onInstallLanguageModel,
            secondaryAction = "Remove".takeIf { languageModelInfo.installed },
            onSecondaryAction = onRemoveLanguageModel,
        )
        AssistantModelRow(
            title = "Voice language pack",
            summary = modelPackSummary(voiceModelInfo, AssistantModelCatalog.voice),
            busy = activeModelPack == AssistantModelPackType.Voice,
            actionsEnabled = activeModelPack == null,
            progress = modelDownloadProgress
                .takeIf { activeModelPack == AssistantModelPackType.Voice },
            primaryAction = when {
                !voiceModelInfo.installed -> "Install"
                voiceModelInfo.hasUpdate(AssistantModelCatalog.voice) -> "Update"
                else -> null
            },
            onPrimaryAction = onInstallVoiceModel,
            secondaryAction = "Remove".takeIf { voiceModelInfo.installed },
            onSecondaryAction = onRemoveVoiceModel,
        )
        AssistantSettingsSwitchRow(
            title = "Wi-Fi downloads only",
            summary = "Do not download model packs over metered connections",
            checked = preferences.assistantWifiOnlyDownloads,
            onCheckedChange = {
                onPreferencesChanged(preferences.copy(assistantWifiOnlyDownloads = it))
            },
        )
        Text(
            modelMessage
                ?: "The built-in safe command engine remains available without a model pack.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(
            onClick = { advancedModelOptionsExpanded = !advancedModelOptionsExpanded },
            enabled = activeModelPack == null,
        ) {
            Text("Advanced")
            Icon(
                if (advancedModelOptionsExpanded) {
                    Icons.Rounded.ExpandLess
                } else {
                    Icons.Rounded.ExpandMore
                },
                contentDescription = null,
            )
        }
        if (advancedModelOptionsExpanded) {
            Text(
                "Use a compatible GGUF file instead of the recommended language model.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = onImportLanguageModel,
                enabled = activeModelPack == null,
            ) {
                Text("Import GGUF file")
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 20.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        AssistantSettingsSectionTitle("Performance")
        Text(
            "Keep model loaded",
            modifier = Modifier.padding(bottom = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            AssistantKeepWarm.entries.forEachIndexed { index, keepWarm ->
                SegmentedButton(
                    selected = preferences.assistantKeepWarm == keepWarm,
                    onClick = {
                        onPreferencesChanged(preferences.copy(assistantKeepWarm = keepWarm))
                    },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = AssistantKeepWarm.entries.size,
                    ),
                ) {
                    Text(keepWarm.label)
                }
            }
        }
        Text(
            "Keep ready avoids repeated model loading. Timed options release memory after inactivity.",
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun AssistantCommandGuide() {
    Text(
        "Use Add, Increase, Boost, Reduce, Lower, or Remove followed by a target.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
    AssistantCommandExamples(
        title = "Sound",
        examples = "Add bass\nReduce mids\nIncrease treble\nClearer vocals\nAdd echo",
    )
    AssistantCommandExamples(
        title = "Effects",
        examples = "Turn on equalizer\nTurn off bass foundation\nTurn on immersive field\nTurn on reverb",
    )
    AssistantCommandExamples(
        title = "Profiles and history",
        examples = "Switch profile to <profile name>\nUndo",
    )
    AssistantCommandExamples(
        title = "Bulgarian examples",
        examples = "Добави бас\nНамали средите\nУвеличи високите\nДобави ехо\nВключи реверберацията",
    )
    Text(
        "Commands are case-insensitive. Reverb and echo refer to the same effect.",
        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun AssistantCommandExamples(title: String, examples: String) {
    Text(
        title,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        examples,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun AssistantModelRow(
    title: String,
    summary: String,
    busy: Boolean = false,
    actionsEnabled: Boolean = true,
    progress: AssistantModelDownloadProgress? = null,
    primaryAction: String? = null,
    onPrimaryAction: () -> Unit = {},
    secondaryAction: String? = null,
    onSecondaryAction: () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Memory, contentDescription = null)
        Column(modifier = Modifier.padding(start = 16.dp).weight(1F)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (busy && progress != null) {
                    "Downloading ${progress.percent}% | ${formatModelSize(progress.downloadedBytes)} of ${formatModelSize(progress.totalBytes)}"
                } else {
                    summary
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.padding(start = 12.dp).size(28.dp),
                strokeWidth = 3.dp,
            )
        } else {
            secondaryAction?.let { label ->
                TextButton(onClick = onSecondaryAction, enabled = actionsEnabled) { Text(label) }
            }
            primaryAction?.let { label ->
                TextButton(onClick = onPrimaryAction, enabled = actionsEnabled) { Text(label) }
            }
        }
    }
}

private fun modelPackSummary(
    info: AssistantModelPackInfo,
    pack: AssistantModelPack,
): String = when {
    !info.installed -> "${pack.displayName} | ${formatModelSize(pack.sizeBytes)} | Not installed"
    info.isCurrent(pack) -> "${pack.displayName} | ${formatModelSize(info.sizeBytes)} | Up to date"
    info.installedPackId == null -> "Custom model | ${formatModelSize(info.sizeBytes)} | Installed"
    else -> "${pack.displayName} | ${formatModelSize(info.sizeBytes)} | Update available"
}

private fun formatModelSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.0f MB".format(bytes / (1024.0 * 1024.0))
    else -> "${bytes / 1024L} KB"
}

@Composable
private fun AssistantChoiceRow(
    title: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                summary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun AssistantSettingsSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1F)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                summary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun AssistantSettingsSectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.labelLarge,
    )
}
