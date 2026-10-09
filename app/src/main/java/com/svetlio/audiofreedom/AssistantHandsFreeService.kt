package com.svetlio.audiofreedom

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.IBinder
import android.os.ResultReceiver
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

class AssistantHandsFreeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var recorder: VoiceCommandRecorder
    private lateinit var audioSession: VoiceCommandAudioSession
    private lateinit var audioManager: AudioManager
    @Volatile private var conversationActive = false
    private val interruptionGeneration = AtomicInteger(0)
    private var conversationGeneration = 0
    private val modeListener = AudioManager.OnModeChangedListener { mode ->
        if (mode != AudioManager.MODE_NORMAL) {
            interruptionGeneration.incrementAndGet()
            recorder.stop()
            audioSession.stopSpeaking()
            audioSession.restorePlayback()
        }
    }

    override fun onCreate() {
        super.onCreate()
        recorder = VoiceCommandRecorder(this)
        audioSession = VoiceCommandAudioSession(this, handsFree = true)
        audioManager = getSystemService(AudioManager::class.java)
        audioManager.addOnModeChangedListener(mainExecutor, modeListener)
        AssistantRuntimeManager.initialize(this)
        activeService = this
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == StopAction) {
            cancelSession()
            return START_NOT_STICKY
        }
        val ready = intent?.getParcelableExtra(ReadyReceiver, ResultReceiver::class.java)
        val preferences = AppPreferencesStore.load(this)
        if (intent?.action != StartAction || !canUseShortcut(preferences) ||
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            audioManager.mode != AudioManager.MODE_NORMAL) {
            status = "Enable voice input and microphone permission; shortcuts are unavailable during calls"
            ready?.send(1, null)
            stopSelf()
            return START_NOT_STICKY
        }
        if (conversationActive) {
            ready?.send(0, null)
            return START_NOT_STICKY
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(Channel, "Hands-free voice", NotificationManager.IMPORTANCE_LOW),
        )
        val openApp = PendingIntent.getActivity(this, 41, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 42,
            Intent(this, AssistantHandsFreeService::class.java).setAction(StopAction),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, Channel)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("AudioFreedom voice command")
            .setContentText("Microphone active for this command only")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, "Cancel command", stop)
            .build()
        startForeground(NotificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        ready?.send(0, null)
        conversationActive = true
        captureDetail = ""
        recognizedCommand = ""
        conversationGeneration = interruptionGeneration.get()
        scope.launch { runConversation(preferences) }
        return START_NOT_STICKY
    }

    private suspend fun runConversation(preferences: AppPreferences) {
        try {
            if (withTimeoutOrNull(120_000) { converse(preferences); true } == null) {
                status = "Voice command timed out; cancelled"
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            status = error.message ?: "Voice command failed"
        } finally {
            conversationActive = false
            recorder.stop()
            audioSession.stopSpeaking()
            audioSession.restorePlayback()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun capture(preferences: AppPreferences): VoiceRecordingResult =
        withContext(Dispatchers.IO) {
            recorder.record(autoStopAfterSilence = true,
                maximumSeconds = 20,
                silenceMilliseconds = preferences.assistantSilenceMilliseconds,
                waitForSpeechSeconds = preferences.assistantSpeechWaitSeconds,
                handsFreeSession = true).also {
                    withContext(Dispatchers.Main) { captureDetail = recorder.lastCaptureDetail }
                }
        }

    private fun ensureAvailable() {
        check(scope.isActive && audioManager.mode == AudioManager.MODE_NORMAL &&
            conversationGeneration == interruptionGeneration.get() &&
            VoiceCaptureOwnership.manualSessions.get() == 0 &&
            canUseShortcut(AppPreferencesStore.load(this))) { "Voice command cancelled" }
    }

    private suspend fun say(text: String, language: AssistantLanguage, maximumSpeechMilliseconds: Long = 15_000L): Boolean {
        ensureAvailable()
        status = text
        val result = audioSession.speak(text, language, maximumSpeechMilliseconds)
        val spoken = result == SpokenConfirmationResult.Spoken
        if (!spoken) {
            status = when (result) {
                SpokenConfirmationResult.NoSpeechEngine ->
                    "No Android text-to-speech engine is installed or enabled. Command cancelled. Check Android speech settings."
                SpokenConfirmationResult.NoOfflineVoice ->
                    "No installed offline ${language.label} speech voice. Command cancelled. Open Local assistant > Spoken replies."
                else -> "Android could not play the speech voice. Command cancelled. Check Android speech settings."
            }
            Toast.makeText(this, status, Toast.LENGTH_LONG).show()
        }
        delay(200)
        return spoken
    }

    private fun cancelSession() {
        status = "Voice command cancelled"
        interruptionGeneration.incrementAndGet()
        recorder.stop()
        audioSession.stopSpeaking()
        audioSession.restorePlayback()
        scope.cancel()
        stopSelf()
    }

    private suspend fun listenForCommand(preferences: AppPreferences): String? {
        ensureAvailable()
        status = "Listening for your command"
        val recording = capture(preferences) as? VoiceRecordingResult.Audio
        ensureAvailable()
        if (recording == null) return null
        status = "Understanding your command"
        val transcript = AssistantVoiceRuntimeManager.transcribe(recording.samples, AssistantLanguage.English)
        ensureAvailable()
        recognizedCommand = transcript
        return transcript
    }

    private suspend fun converse(preferences: AppPreferences) {
        var replyLanguage = HandsFreeVoicePolicy.replyLanguage(preferences.assistantLanguage)
        suspend fun reply(english: String, bulgarian: String): Boolean =
            say(if (replyLanguage == AssistantLanguage.Bulgarian) bulgarian else english, replyLanguage)
        ensureAvailable()
        if (!audioSession.requestPlaybackPause()) {
            status = "Could not pause playback; command cancelled"
            return
        }
        if (!reply("I'm listening", "Слушам")) return
        var transcript: String
        var helpReplies = 0
        while (true) {
            transcript = listenForCommand(preferences) ?: run {
                reply("No command heard. Cancelled.", "Не чух команда. Отказвам.")
                return
            }
            replyLanguage = HandsFreeVoicePolicy.replyLanguage(preferences.assistantLanguage, transcript)
            when (val control = AssistantConversationCommands.recognize(transcript)) {
                AssistantConversationControl.Cancel -> {
                    say(AssistantConversationCommands.response(control, replyLanguage), replyLanguage)
                    return
                }
                is AssistantConversationControl.Help -> {
                    if (!say(AssistantConversationCommands.response(control, replyLanguage), replyLanguage, 30_000L)) return
                    helpReplies++
                    if (helpReplies >= AssistantConversationCommands.MaximumHelpReplies) {
                        reply("Help finished. Activate again when ready.", "Помощта приключи. Активирайте отново, когато сте готови.")
                        return
                    }
                    if (!reply("Say a command, or cancel.", "Кажете команда, или отказ.")) return
                }
                null -> break
            }
        }
        ensureAvailable()
        val base = AudioFreedomSettingsStore.load(this)
        val profiles = AudioFreedomProfileStore.list(this)
        val result = AssistantRuntimeManager.plan(AssistantRequest(transcript, base, profiles,
            preferences.assistantAdjustmentStrength.maximumBandDeltaMillibels,
            usePersonalization = preferences.assistantAskForFeedback))
        val target: AudioFreedomSettings
        val description: String
        val undo: Boolean
        when (result) {
            is AssistantPlanResult.Proposed -> {
                target = result.proposal.settings
                description = if (replyLanguage == AssistantLanguage.Bulgarian)
                    HandsFreeVoicePolicy.bulgarianSummary(result.proposal, profiles)
                    else if (result.proposal.selectedProfileId != null) result.proposal.title
                    else result.proposal.explanation.ifBlank { result.proposal.title }
                undo = false
            }
            AssistantPlanResult.Undo -> {
                target = AssistantUndoStore.load(this) ?: run {
                    reply("There is no change to undo.", "Няма промяна за отмяна.")
                    return
                }
                description = if (replyLanguage == AssistantLanguage.Bulgarian)
                    "Връщам настройките преди последната промяна" else "Restore the settings before the last assistant change"
                undo = true
            }
            is AssistantPlanResult.NotUnderstood -> {
                reply("I could not understand a supported sound command. Cancelled.", "Не разбрах поддържана команда за звук. Отказвам.")
                return
            }
        }
        if (!reply("$description. Do you confirm?", "$description. Потвърждавате ли?")) return
        ensureAvailable()
        status = "Waiting for yes or no"
        val answer = capture(preferences.copy(assistantSpeechWaitSeconds =
            preferences.assistantConfirmationWaitSeconds)) as? VoiceRecordingResult.Audio
        ensureAvailable()
        val response = if (answer != null) {
            HandsFreeVoicePolicy.confirmation(AssistantVoiceRuntimeManager.transcribe(answer.samples,
                replyLanguage, commandHints = false))
        } else VoiceAnswer.Unknown
        ensureAvailable()
        if (response != VoiceAnswer.Yes) {
            if (response == VoiceAnswer.No) reply("Cancelled.", "Отказвам.")
            else reply("Confirmation was unclear. Cancelled.", "Потвърждението беше неясно. Отказвам.")
            return
        }
        if (AudioFreedomSettingsStore.load(this) != base) {
            reply("Settings changed while listening. Please try again.", "Настройките се промениха по време на слушане. Опитайте отново.")
            return
        }
        if (undo && AssistantUndoStore.load(this) != target) {
            reply("Undo history changed. Please try again.", "Историята за отмяна се промени. Опитайте отново.")
            return
        }
        if (undo) AssistantUndoStore.clear(this) else AssistantUndoStore.save(this, base, target)
        AudioFreedomService.updateSettings(this, target)
        if (AudioFreedomService.isRequestedEnabled(this)) reply("Applied.", "Приложено.")
        else reply("Saved. Processing is off.", "Запазено. Обработката е изключена.")
    }

    override fun onDestroy() {
        recorder.stop()
        scope.cancel()
        audioManager.removeOnModeChangedListener(modeListener)
        audioSession.shutdown()
        if (activeService === this) activeService = null
        if (status.startsWith("Listening") || status == "Waiting for yes or no") status = "Voice command stopped"
        super.onDestroy()
    }

    companion object {
        private const val Channel = "audiofreedom_hands_free"
        private const val NotificationId = 43
        private const val StopAction = "com.svetlio.audiofreedom.STOP_HANDS_FREE"
        private const val StartAction = "com.svetlio.audiofreedom.START_BUTTON_VOICE"
        private const val ReadyReceiver = "ready_receiver"
        @Volatile private var activeService: AssistantHandsFreeService? = null
        internal var status by mutableStateOf("Microphone off")
            private set
        internal var captureDetail by mutableStateOf("")
            private set
        internal var recognizedCommand by mutableStateOf("")
            private set

        internal fun canUseShortcut(preferences: AppPreferences): Boolean =
            preferences.assistantEnabled && preferences.assistantVoiceEnabled &&
                preferences.assistantVoiceMode == AssistantVoiceMode.VolumeButton &&
                AssistantRuntimeManager.voiceModelInfo().installed

        internal fun sync(context: Context) {
            if (!canUseShortcut(AppPreferencesStore.load(context))) {
                context.stopService(Intent(context, AssistantHandsFreeService::class.java))
            }
        }

        internal fun startCommand(context: Context, ready: ResultReceiver) {
            try {
                ContextCompat.startForegroundService(context,
                    Intent(context, AssistantHandsFreeService::class.java)
                        .setAction(StartAction).putExtra(ReadyReceiver, ready))
            } catch (error: Exception) {
                status = error.message ?: "Voice command could not start"
                ready.send(1, null)
            }
        }

        internal fun activationFailed(message: String) { status = message }

        internal val isRunning: Boolean get() = activeService?.conversationActive == true

        internal fun interruptConversation() {
            activeService?.let {
                if (it.conversationActive) it.cancelSession()
            }
        }
    }
}
