package com.svetlio.audiofreedom

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class WidgetVoicePhase {
    Preparing,
    Listening,
    Transcribing,
    Interpreting,
    Review,
    Applied,
    Error,
}

class VoiceCommandWidgetActivity : ComponentActivity() {
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var recorder: VoiceCommandRecorder
    private lateinit var audioSession: VoiceCommandAudioSession
    private var phase by mutableStateOf(WidgetVoicePhase.Preparing)
    private var microphoneLevel by mutableFloatStateOf(0F)
    private var headline by mutableStateOf("Preparing voice command")
    private var detail by mutableStateOf<String?>(null)
    private var pendingProposal by mutableStateOf<AssistantProposal?>(null)
    private var undoAvailable by mutableStateOf(false)
    private val sessionStarted = AtomicBoolean(false)
    private val microphonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            beginVoiceSession()
        } else {
            showError("Microphone permission is required")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.attributes = window.attributes.apply { dimAmount = 0.45F }
        setFinishOnTouchOutside(true)

        recorder = VoiceCommandRecorder(applicationContext)
        audioSession = VoiceCommandAudioSession(applicationContext)
        AssistantRuntimeManager.initialize(applicationContext)

        setContent {
            val preferences = AppPreferencesStore.load(this)
            AudioFreedomTheme(preferences.theme) {
                VoiceCommandWidgetPanel(
                    phase = phase,
                    microphoneLevel = microphoneLevel,
                    headline = headline,
                    detail = detail,
                    onVoiceCircleClick = {
                        if (phase == WidgetVoicePhase.Listening) recorder.stop()
                    },
                    onApply = { pendingProposal?.let(::applyProposal) },
                    undoAvailable = undoAvailable,
                    onUndo = ::undoLastChange,
                    onClose = ::finish,
                )
            }
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            beginVoiceSession()
        } else {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onDestroy() {
        recorder.stop()
        audioSession.shutdown()
        activityScope.cancel()
        super.onDestroy()
    }

    private fun beginVoiceSession() {
        if (!sessionStarted.compareAndSet(false, true)) return
        val preferences = AppPreferencesStore.load(this)
        when {
            !preferences.assistantEnabled -> {
                showError("Enable the local assistant in AudioFreedom settings")
                return
            }
            !preferences.assistantVoiceEnabled ||
                !AssistantRuntimeManager.voiceModelInfo().installed -> {
                showError("Install and enable the voice language pack first")
                return
            }
        }

        phase = WidgetVoicePhase.Listening
        headline = "Listening"
        detail = "Speak now. Tap the circle to stop."
        if (preferences.assistantPauseMediaForVoice) {
            audioSession.requestPlaybackPause()
        }
        activityScope.launch {
            try {
                when (
                    val recording = withContext(Dispatchers.IO) {
                        recorder.record(
                            autoStopAfterSilence = true,
                            maximumSeconds = 12,
                            onLevel = { level -> runOnUiThread { microphoneLevel = level } },
                        )
                    }
                ) {
                    VoiceRecordingResult.CallActive ->
                        showError("Voice commands are unavailable during a call")
                    VoiceRecordingResult.TooShort ->
                        showError("No complete voice command was captured")
                    is VoiceRecordingResult.Audio -> transcribeAndPlan(
                        recording.samples,
                        preferences,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                showError(error.message ?: "Voice command failed")
            }
        }
    }

    private suspend fun transcribeAndPlan(
        samples: ShortArray,
        preferences: AppPreferences,
    ) {
        phase = WidgetVoicePhase.Transcribing
        headline = "Transcribing"
        detail = "Speech stays on this phone"
        val settings = AudioFreedomSettingsStore.load(this)
        val profiles = AudioFreedomProfileStore.list(this)
        val primary = AssistantVoiceRuntimeManager.transcribe(
            samples,
            preferences.assistantLanguage,
        )
        val transcript = if (
            preferences.assistantLanguage == AssistantLanguage.Automatic &&
            !isSupportedAssistantCommand(primary, settings, profiles, preferences)
        ) {
            val bulgarian = AssistantVoiceRuntimeManager.transcribe(
                samples,
                AssistantLanguage.Bulgarian,
            )
            chooseAutomaticVoiceTranscript(primary, bulgarian) { candidate ->
                isSupportedAssistantCommand(candidate, settings, profiles, preferences)
            }
        } else {
            primary
        }
        if (transcript.isBlank()) {
            showError("No speech was recognized")
            return
        }

        phase = WidgetVoicePhase.Interpreting
        headline = "Understanding"
        detail = transcript
        when (
            val result = AssistantRuntimeManager.plan(
                AssistantRequest(
                    command = transcript,
                    current = settings,
                    profiles = profiles,
                    maximumBandDeltaMillibels =
                        preferences.assistantAdjustmentStrength.maximumBandDeltaMillibels,
                    usePersonalization = preferences.assistantAskForFeedback,
                ),
            )
        ) {
            is AssistantPlanResult.Proposed -> showProposal(result.proposal, preferences)
            AssistantPlanResult.Undo -> undoLastChange(
                speakConfirmation = preferences.assistantSpeakVoiceConfirmation,
            )
            is AssistantPlanResult.NotUnderstood -> showError(result.message)
        }
    }

    private suspend fun showProposal(
        proposal: AssistantProposal,
        preferences: AppPreferences,
    ) {
        val applyAutomatically =
            preferences.assistantAutoSubmitVoice && proposal.canApplyAutomatically
        pendingProposal = proposal
        headline = proposal.title
        detail = proposal.explanation
        if (preferences.assistantSpeakVoiceConfirmation) {
            when (
                audioSession.speakPlannedChange(
                    change = proposal.title,
                    applying = applyAutomatically,
                )
            ) {
                SpokenConfirmationResult.Spoken -> Unit
                SpokenConfirmationResult.NoOfflineVoice ->
                    detail = "${proposal.explanation} No offline system voice is installed."
                SpokenConfirmationResult.Failed ->
                    detail = "${proposal.explanation} Spoken confirmation failed."
            }
        }
        if (applyAutomatically) {
            applyProposal(proposal)
        } else {
            phase = WidgetVoicePhase.Review
            audioSession.restorePlayback()
        }
    }

    private fun applyProposal(proposal: AssistantProposal) {
        if (AudioFreedomSettingsStore.load(this) != proposal.baseSettings) {
            showError("DSP settings changed. Say the command again.")
            return
        }
        pendingProposal = null
        AssistantUndoStore.save(this, proposal.baseSettings)
        AudioFreedomService.updateSettings(this, proposal.settings)
        phase = WidgetVoicePhase.Applied
        headline = proposal.title
        detail = if (AudioFreedomService.isRequestedEnabled(this)) {
            "Applied"
        } else {
            "Saved. Audio processing is currently off."
        }
        undoAvailable = true
        audioSession.restorePlayback()
    }

    private fun undoLastChange(speakConfirmation: Boolean = false) {
        val previous = AssistantUndoStore.load(this)
        if (previous == null) {
            showError("There is no assistant change to undo")
            return
        }
        activityScope.launch {
            if (speakConfirmation) {
                audioSession.speakPlannedChange(
                    change = "Restore previous settings",
                    applying = true,
                )
            }
            AudioFreedomService.updateSettings(this@VoiceCommandWidgetActivity, previous)
            AssistantUndoStore.clear(this@VoiceCommandWidgetActivity)
            pendingProposal = null
            undoAvailable = false
            phase = WidgetVoicePhase.Applied
            headline = "Last change undone"
            detail = "Previous settings restored"
            audioSession.restorePlayback()
        }
    }

    private fun showError(message: String) {
        phase = WidgetVoicePhase.Error
        headline = "Voice command unavailable"
        detail = message
        microphoneLevel = 0F
        audioSession.restorePlayback()
    }
}

@Composable
private fun VoiceCommandWidgetPanel(
    phase: WidgetVoicePhase,
    microphoneLevel: Float,
    headline: String,
    detail: String?,
    onVoiceCircleClick: () -> Unit,
    onApply: () -> Unit,
    undoAvailable: Boolean,
    onUndo: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        modifier = Modifier.widthIn(min = 280.dp, max = 340.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "AudioFreedom",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(18.dp))
            VoiceAwareCircle(
                phase = phase,
                level = microphoneLevel,
                onClick = onVoiceCircleClick,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                headline,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleSmall,
            )
            detail?.let {
                Text(
                    it,
                    modifier = Modifier.padding(top = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            when (phase) {
                WidgetVoicePhase.Review -> Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = null)
                        Text("Cancel")
                    }
                    TextButton(onClick = onApply) {
                        Icon(Icons.Rounded.Check, contentDescription = null)
                        Text("Apply")
                    }
                }
                WidgetVoicePhase.Applied -> Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (undoAvailable) {
                        TextButton(onClick = onUndo) {
                            Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null)
                            Text("Undo")
                        }
                    }
                    TextButton(onClick = onClose) { Text("Done") }
                }
                WidgetVoicePhase.Error ->
                    TextButton(
                        onClick = onClose,
                        modifier = Modifier.padding(top = 10.dp),
                    ) {
                        Text("Close")
                    }
                else -> Unit
            }
        }
    }
}

@Composable
private fun VoiceAwareCircle(
    phase: WidgetVoicePhase,
    level: Float,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (phase == WidgetVoicePhase.Listening) 1F + level * 0.22F else 1F,
        label = "voice-level",
    )
    val active = phase == WidgetVoicePhase.Listening
    Box(
        modifier = Modifier
            .size(118.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14F))
            .clickable(enabled = active, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(78.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            when (phase) {
                WidgetVoicePhase.Preparing,
                WidgetVoicePhase.Transcribing,
                WidgetVoicePhase.Interpreting -> CircularProgressIndicator(
                    modifier = Modifier.size(34.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 3.dp,
                )
                WidgetVoicePhase.Listening -> Icon(
                    Icons.Rounded.Stop,
                    contentDescription = "Stop listening",
                    modifier = Modifier.size(34.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
                WidgetVoicePhase.Applied -> Icon(
                    Icons.Rounded.Check,
                    contentDescription = "Applied",
                    modifier = Modifier.size(34.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
                else -> Icon(
                    Icons.Rounded.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
