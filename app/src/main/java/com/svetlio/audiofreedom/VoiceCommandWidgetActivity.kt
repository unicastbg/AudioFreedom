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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class WidgetVoicePhase {
    Preparing,
    Listening,
    Transcribing,
    Interpreting,
    Review,
    Applied,
    Information,
    Error;

    val canStartSession: Boolean
        get() = when (this) {
            Review, Applied, Information, Error -> true
            Preparing, Listening, Transcribing, Interpreting -> false
        }

    fun microphoneEnabled(sessionBusy: Boolean): Boolean =
        this == Listening || (canStartSession && !sessionBusy)
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
    private var sessionBusy by mutableStateOf(false)
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
            AudioFreedomTheme(
                preferences.theme,
                preferences.appTheme,
                controlSystemBars = false,
            ) {
                VoiceCommandWidgetPanel(
                    phase = phase,
                    microphoneLevel = microphoneLevel,
                    headline = headline,
                    detail = detail,
                    sessionBusy = sessionBusy,
                    onVoiceCircleClick = {
                        if (phase == WidgetVoicePhase.Listening) recorder.stop()
                        else if (phase.microphoneEnabled(sessionBusy)) requestVoiceSession()
                    },
                    onApply = { pendingProposal?.let(::applyProposal) },
                    undoAvailable = undoAvailable,
                    onUndo = ::undoLastChange,
                    onClose = ::finish,
                )
            }
        }

        requestVoiceSession()
    }

    private fun requestVoiceSession() {
        if (sessionBusy || isFinishing || isDestroyed) return
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
        if (sessionBusy || isFinishing || isDestroyed) return
        sessionBusy = true
        pendingProposal = null
        microphoneLevel = 0F
        phase = WidgetVoicePhase.Preparing
        headline = "Preparing voice command"
        detail = null
        activityScope.launch {
            try {
                val preferences = AppPreferencesStore.load(this@VoiceCommandWidgetActivity)
                when {
                    !preferences.assistantEnabled -> {
                        showError("Enable the local assistant in AudioFreedom settings")
                        return@launch
                    }
                    !preferences.assistantVoiceEnabled ||
                        !AssistantRuntimeManager.voiceModelInfo().installed -> {
                        showError("Install and enable the voice language pack first")
                        return@launch
                    }
                }
                phase = WidgetVoicePhase.Listening
                headline = "Listening"
                detail = "Speak now. Tap the circle to stop."
                audioSession.beginManualSession()
                if (preferences.assistantPauseMediaForVoice) {
                    audioSession.requestPlaybackPause()
                }
                when (
                    val recording = withContext(Dispatchers.IO) {
                        recorder.record(
                            autoStopAfterSilence = true,
                            maximumSeconds = 20,
                            silenceMilliseconds = preferences.assistantSilenceMilliseconds,
                            waitForSpeechSeconds = preferences.assistantSpeechWaitSeconds,
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
            } finally {
                microphoneLevel = 0F
                audioSession.restorePlayback()
                sessionBusy = false
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
        val transcript = AssistantVoiceRuntimeManager.transcribe(
            samples,
            AssistantLanguage.English,
        )
        if (transcript.isBlank()) {
            showError("No speech was recognized")
            return
        }

        val musicReply = MusicBridge.handleIfMusicCommand(
            this, transcript, audioSession,
            enabled = AppPreferencesStore.load(this).assistantForwardMusicCommands,
            speakReply = preferences.assistantSpeakVoiceConfirmation,
        )
        if (musicReply != null) {
            showMusicResult(musicReply)
            return
        }
        val control = AssistantConversationCommands.recognize(transcript)
        if (control != null) {
            val language = HandsFreeVoicePolicy.replyLanguage(preferences.assistantLanguage, transcript)
            pendingProposal = null
            headline = if (control == AssistantConversationControl.Cancel) "Command cancelled" else "Assistant help"
            detail = AssistantConversationCommands.response(control, language,
                preferences.assistantForwardMusicCommands && MusicBridge.isInstalled(this))
            phase = WidgetVoicePhase.Information
            try {
                if (preferences.assistantSpeakVoiceConfirmation) {
                    when (audioSession.speak(detail.orEmpty(), language, 30_000L)) {
                        SpokenConfirmationResult.NoSpeechEngine -> detail += " No Android text-to-speech engine is installed or enabled."
                        SpokenConfirmationResult.NoOfflineVoice -> detail += " No offline ${language.label} speech voice is installed."
                        SpokenConfirmationResult.Failed -> detail += " Android could not play the spoken reply."
                        SpokenConfirmationResult.Spoken -> Unit
                    }
                }
            } finally {
                audioSession.restorePlayback()
            }
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
            AssistantPlanResult.Undo -> restoreLastChange(
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
        var speechWarning: String? = null
        if (preferences.assistantSpeakVoiceConfirmation) {
            when (
                audioSession.speakPlannedChange(
                    change = proposal.title,
                    applying = applyAutomatically,
                )
            ) {
                SpokenConfirmationResult.Spoken -> Unit
                SpokenConfirmationResult.NoSpeechEngine ->
                    speechWarning = "No Android text-to-speech engine is installed or enabled. Open Local assistant > Spoken replies."
                SpokenConfirmationResult.NoOfflineVoice ->
                    speechWarning = "No offline speech voice is installed for this reply. Open Local assistant > Spoken replies."
                SpokenConfirmationResult.Failed ->
                    speechWarning = "Android could not play the spoken confirmation. Check Android speech settings."
            }
        }
        if (applyAutomatically) {
            applyProposal(proposal)
        } else {
            phase = WidgetVoicePhase.Review
            audioSession.restorePlayback()
        }
        speechWarning?.let { detail = "${detail.orEmpty()} $it" }
    }

    private fun applyProposal(proposal: AssistantProposal) {
        if (AudioFreedomSettingsStore.load(this) != proposal.baseSettings) {
            showError("DSP settings changed. Say the command again.")
            return
        }
        pendingProposal = null
        AssistantUndoStore.save(this, proposal.baseSettings, proposal.settings)
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
        if (sessionBusy || isFinishing || isDestroyed) return
        sessionBusy = true
        activityScope.launch {
            try {
                restoreLastChange(speakConfirmation)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                showError(error.message ?: "Could not undo the last change")
            } finally {
                audioSession.restorePlayback()
                sessionBusy = false
            }
        }
    }

    private suspend fun restoreLastChange(speakConfirmation: Boolean) {
        val previous = AssistantUndoStore.load(this)
        if (previous == null) {
            showError("There is no assistant change to undo")
            return
        }
        val spokenResult = if (speakConfirmation) {
            audioSession.speakPlannedChange(
                change = "Restore previous settings",
                applying = true,
            )
        } else null
        AudioFreedomService.updateSettings(this, previous)
        AssistantUndoStore.clear(this)
        pendingProposal = null
        undoAvailable = false
        phase = WidgetVoicePhase.Applied
        headline = "Last change undone"
        detail = when (spokenResult) {
            SpokenConfirmationResult.NoSpeechEngine ->
                "Previous settings restored. No Android text-to-speech engine is installed or enabled. Open Local assistant > Spoken replies."
            SpokenConfirmationResult.NoOfflineVoice ->
                "Previous settings restored. No offline speech voice is installed for this reply. Open Local assistant > Spoken replies."
            SpokenConfirmationResult.Failed ->
                "Previous settings restored, but Android could not play the spoken confirmation."
            else -> "Previous settings restored"
        }
        audioSession.restorePlayback()
    }

    private fun showError(message: String) {
        phase = WidgetVoicePhase.Error
        headline = "Voice command unavailable"
        detail = message
        microphoneLevel = 0F
        audioSession.restorePlayback()
    }

    /**
     * A command the music player took. Information rather than Applied, because nothing
     * in the DSP changed and there is no undo snapshot to offer — the panel should not
     * present an Apply or Undo button for something another app did.
     *
     * Does not touch audio focus: it has already been released before forwarding, and
     * grabbing it back here would interrupt the playback we just asked for.
     */
    private fun showMusicResult(message: String) {
        phase = WidgetVoicePhase.Information
        headline = "Music"
        detail = message.ifBlank { "Done" }
        microphoneLevel = 0F
    }
}

@Composable
private fun VoiceCommandWidgetPanel(
    phase: WidgetVoicePhase,
    microphoneLevel: Float,
    headline: String,
    detail: String?,
    sessionBusy: Boolean,
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
                sessionBusy = sessionBusy,
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
                    TextButton(onClick = onApply, enabled = !sessionBusy) {
                        Icon(Icons.Rounded.Check, contentDescription = null)
                        Text("Apply")
                    }
                }
                WidgetVoicePhase.Applied -> Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (undoAvailable) {
                        TextButton(onClick = onUndo, enabled = !sessionBusy) {
                            Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null)
                            Text("Undo")
                        }
                    }
                    TextButton(onClick = onClose) { Text("Done") }
                }
                WidgetVoicePhase.Error, WidgetVoicePhase.Information ->
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
    sessionBusy: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (phase == WidgetVoicePhase.Listening) 1F + level * 0.22F else 1F,
        label = "voice-level",
    )
    val active = phase.microphoneEnabled(sessionBusy)
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
                else -> Icon(
                    Icons.Rounded.Mic,
                    contentDescription = "Start another voice command",
                    modifier = Modifier.size(34.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
