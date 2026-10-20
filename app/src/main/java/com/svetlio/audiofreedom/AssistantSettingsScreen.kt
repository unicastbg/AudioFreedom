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
import androidx.compose.material3.Slider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.roundToInt

@Composable
internal fun AssistantSettingsScreen(
    preferences: AppPreferences,
    languageModelInfo: AssistantModelPackInfo,
    voiceModelInfo: AssistantModelPackInfo,
    activeModelPack: AssistantModelPackType?,
    modelDownloadProgress: AssistantModelDownloadProgress?,
    modelMessage: String?,
    voiceTestMessage: String?,
    voiceTestRunning: Boolean,
    widgetMessage: String?,
    onPreferencesChanged: (AppPreferences) -> Unit,
    onAddVoiceWidget: () -> Unit,
    onInstallLanguageModel: () -> Unit,
    onImportLanguageModel: () -> Unit,
    onRemoveLanguageModel: () -> Unit,
    onInstallVoiceModel: () -> Unit,
    onRemoveVoiceModel: () -> Unit,
    onTestVoice: (AssistantLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var shortcutEnabled by remember { mutableStateOf(VolumeButtonAccessibilityService.enabled(context)) }
    LaunchedEffect(context) {
        while (true) {
            shortcutEnabled = VolumeButtonAccessibilityService.enabled(context)
            delay(1000)
        }
    }
    var commandGuideExpanded by rememberSaveable { mutableStateOf(false) }
    var advancedModelOptionsExpanded by rememberSaveable { mutableStateOf(false) }
    var experimentalExpanded by rememberSaveable { mutableStateOf(false) }
    var tuningGuideExpanded by rememberSaveable { mutableStateOf(false) }
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

        AssistantSettingsSectionTitle("Voice commands")
        Text(
            "English only",
            modifier = Modifier.padding(bottom = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "Recognition, spoken replies, and supported assistant commands use English. Other languages are not supported in this version.",
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
                Text("Voice input", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (voiceModelInfo.installed) {
                        "Offline voice input for push-to-talk and volume-button commands"
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
                    enabled = mode == AssistantVoiceMode.PushToTalk ||
                        (preferences.assistantEnabled && preferences.assistantVoiceEnabled && voiceModelInfo.installed),
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
            "The microphone stays off between commands. Button-triggered changes require spoken confirmation.",
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!preferences.assistantEnabled || !preferences.assistantVoiceEnabled || !voiceModelInfo.installed) {
            Text(
                "To select Volume button, enable the assistant and Voice input, and install the voice language pack.",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (preferences.assistantVoiceMode == AssistantVoiceMode.VolumeButton) {
            AssistantSettingsSectionTitle("Volume button shortcut")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                AssistantVolumeButton.entries.forEachIndexed { index, button ->
                    SegmentedButton(
                        selected = preferences.assistantVolumeButton == button,
                        onClick = { onPreferencesChanged(preferences.copy(assistantVolumeButton = button)) },
                        shape = SegmentedButtonDefaults.itemShape(index, AssistantVolumeButton.entries.size),
                    ) { Text(button.label) }
                }
            }
            VoiceTimingSlider("Hold to activate",
                "${preferences.assistantButtonHoldMilliseconds / 1000F} seconds",
                preferences.assistantButtonHoldMilliseconds.toFloat(), 500F..3000F, 9) {
                onPreferencesChanged(preferences.copy(assistantButtonHoldMilliseconds = it.roundToInt()))
            }
            Text(if (shortcutEnabled) "Button shortcut enabled" else "Button shortcut needs Accessibility permission",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { VolumeButtonAccessibilityService.openSettings(context) }) {
                Text(if (shortcutEnabled) "Accessibility settings" else "Enable button shortcut")
            }
            Text("Press the selected volume button again to cancel listening or a spoken reply.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(AssistantHandsFreeService.status,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (AssistantHandsFreeService.captureDetail.isNotBlank()) {
                Text(AssistantHandsFreeService.captureDetail,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (AssistantHandsFreeService.recognizedCommand.isNotBlank()) {
                Text("Last heard: ${AssistantHandsFreeService.recognizedCommand}",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AssistantSettingsSectionTitle("Voice timing")
            VoiceTimingSlider("End-of-command pause",
                "${preferences.assistantSilenceMilliseconds / 1000F} seconds",
                preferences.assistantSilenceMilliseconds.toFloat(), 500F..3000F, 9) {
                onPreferencesChanged(preferences.copy(assistantSilenceMilliseconds = it.roundToInt()))
            }
            VoiceTimingSlider("Wait for speech", "${preferences.assistantSpeechWaitSeconds} seconds",
                preferences.assistantSpeechWaitSeconds.toFloat(), 3F..15F, 11) {
                onPreferencesChanged(preferences.copy(assistantSpeechWaitSeconds = it.roundToInt()))
            }
            VoiceTimingSlider("Wait for confirmation", "${preferences.assistantConfirmationWaitSeconds} seconds",
                preferences.assistantConfirmationWaitSeconds.toFloat(), 3F..15F, 11) {
                onPreferencesChanged(preferences.copy(assistantConfirmationWaitSeconds = it.roundToInt()))
            }
        if (preferences.assistantVoiceMode == AssistantVoiceMode.PushToTalk) {
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
        }
        AssistantSettingsSectionTitle("Music commands")
        AssistantSettingsSwitchRow(
            title = "Pass music commands to Media Player",
            summary = "Send play, shuffle and playback commands directly to Media Player. " +
                "Music names bypass DSP interpretation. Requires compatible versions " +
                "of both apps signed with the same certificate.",
            checked = preferences.assistantForwardMusicCommands,
            enabled = MusicBridge.isInstalled(context),
            onCheckedChange = {
                onPreferencesChanged(preferences.copy(assistantForwardMusicCommands = it))
            },
        )
        AssistantSettingsSwitchRow(
            title = "Detailed music replies",
            summary = "Read artist or category names and track counts. Off: just Playing. " +
                "Errors and playback warnings are always explained.",
            checked = preferences.assistantDetailedMusicReplies,
            enabled = preferences.assistantForwardMusicCommands && preferences.assistantSpeakVoiceConfirmation,
            onCheckedChange = {
                onPreferencesChanged(preferences.copy(assistantDetailedMusicReplies = it))
            },
        )
        AssistantSpeechSettings(preferences, onPreferencesChanged, voiceTestRunning, voiceTestMessage, onTestVoice)
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
        AssistantModelRow(
            title = "Voice language pack",
            summary = modelPackSummary(voiceModelInfo, AssistantModelCatalog.voice),
            busy = activeModelPack == AssistantModelPackType.Voice,
            actionsEnabled = activeModelPack == null,
            progress = modelDownloadProgress.takeIf { activeModelPack == AssistantModelPackType.Voice },
            primaryAction = when {
                !voiceModelInfo.installed -> "Install"
                voiceModelInfo.hasUpdate(AssistantModelCatalog.voice) -> "Update"
                else -> null
            },
            onPrimaryAction = onInstallVoiceModel,
            secondaryAction = "Remove".takeIf { voiceModelInfo.installed },
            onSecondaryAction = onRemoveVoiceModel,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(modifier = Modifier.fillMaxWidth().clickable { experimentalExpanded = !experimentalExpanded }
            .padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Experimental features", modifier = Modifier.weight(1F), style = MaterialTheme.typography.titleMedium)
            Icon(if (experimentalExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (experimentalExpanded) "Collapse experimental features" else "Expand experimental features")
        }
        if (experimentalExpanded) {
        AssistantSettingsSwitchRow(
            title = "Conversational tuning",
            summary = "Optional local LLM for descriptive requests; every model proposal requires review",
            checked = preferences.assistantConversationalTuning,
            enabled = preferences.assistantEnabled && languageModelInfo.installed,
            onCheckedChange = { onPreferencesChanged(preferences.copy(assistantConversationalTuning = it)) },
        )
        Row(modifier = Modifier.fillMaxWidth().clickable { tuningGuideExpanded = !tuningGuideExpanded }
            .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Tuning examples and limits", modifier = Modifier.weight(1F), style = MaterialTheme.typography.bodyLarge)
            Icon(if (tuningGuideExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
        }
        if (tuningGuideExpanded) {
            Text("Standard commands work without a language model. Combine up to six simple commands; if any part is unclear, nothing is applied.",
                style = MaterialTheme.typography.bodyMedium)
            AssistantCommandExamples("Combined commands", "Higher bass and lower highs\nAdd bass and turn on reverb\nIncrease bass and treble")
            AssistantCommandExamples("Conversational tuning", "Bring the vocals forward, soften the cymbals, and keep the bass unchanged\nMore detail without more brightness\nUndo half of the last bass change")
            Text("The model proposes small changes to supported effects. Review the actual changes before applying. Preserved groups lock DSP controls, not a guarantee of identical perceived sound. Partial bass undo is available only while settings still match the last assistant change.",
                modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
            Text("Not available yet: automatic profile creation, A/B audition controls, spectrum-based diagnosis, or autonomous per-device tuning. The model cannot hear your headphones, fix physical distortion, restore missing information, or calibrate your ears. English requests only; it may misunderstand complex requests. Reject the proposal or use standard commands.",
                modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
        }
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
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun VoiceTimingSlider(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChanged: (Float) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1F), style = MaterialTheme.typography.bodyMedium)
        Text(valueLabel, style = MaterialTheme.typography.bodyMedium)
    }
    Slider(value = value.coerceIn(range), onValueChange = onChanged, valueRange = range, steps = steps)
}

@Composable
private fun AssistantCommandGuide() {
    Text(
        "Supported language: English. These standard commands work without the LLM. Use Add, Increase, Boost, Reduce, or Lower followed by a target.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
    AssistantCommandExamples(
        title = "Sound",
        examples = "Add bass\nReduce bass\nIncrease mids\nLower treble\nClearer vocals\nMore detail\nWider stage\nAdd echo\nReduce reverb",
    )
    AssistantCommandExamples(
        title = "Combined changes",
        examples = "Increase bass and treble\nIncrease bass and lower treble\nAdd bass and turn on reverb",
    )
    Text("Small, bounded steps. If any part of a combined standard command is unclear, nothing is applied.",
        modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    AssistantCommandExamples(
        title = "Effects",
        examples = "Turn on equalizer\nTurn off bass foundation\nTurn on detail recovery\nTurn on immersive field\nTurn on reverb\nTurn on volume leveler\nTurn off volume leveler\nTurn off output protection",
    )
    AssistantCommandExamples(
        title = "Profiles and history",
        examples = "Switch profile to <saved profile name>\nLoad profile <saved profile name>\nUndo",
    )
    Text("Profiles must already exist; use the exact saved name. English names are recommended for voice commands. Profile changes require review. Undo restores the last assistant change; Cancel only ends the current request.",
        modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    AssistantCommandExamples(
        title = "Help and cancellation",
        examples = "Cancel\nStop listening\nNever mind\nWhat can you do?\nHelp\nBass help\nEqualizer help\nProfiles help\nEffects help\nReverb help\nImmersive field help\nDetail recovery help\nUndo help",
    )
    Text("Volume-button sessions ask for Yes or No before changing settings. After help, they briefly wait for another command. Press the configured volume button again to interrupt listening or a spoken reply.",
        modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    AssistantCommandExamples(title = "Experimental conversational tuning",
        examples = "Bring the vocals forward, but keep the bass unchanged\nMore detail without more brightness\nUndo half of the last bass change")
    Text("These descriptive requests require the optional language model and Conversational tuning under Experimental features. Model proposals always require review. Half-undo requires an unchanged snapshot of the last assistant sound change.",
        modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    AssistantCommandExamples(title = "Media Player music commands",
        examples = "Play trance\nPlay The Prodigy\nShuffle drum and bass\nNext track\nPause music\nStop music\nMusic help")
    Text("Requires Media Player and music forwarding enabled. Music requests execute immediately, without DSP changes or a Yes/No question. Only AudioFreedom initiates this one-way handoff. Say Cancel or Stop listening to cancel listening; Stop music stops playback.",
        modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Not supported: other languages, radio or other music-player controls, creating profiles by voice, automatic headphone calibration, or guaranteed distortion removal.",
        modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            modifier = Modifier.semantics { contentDescription = title },
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
