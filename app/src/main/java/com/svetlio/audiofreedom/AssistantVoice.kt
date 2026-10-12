package com.svetlio.audiofreedom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import com.svetlio.audiofreedom.voice.runtime.LocalWhisperRuntime
import com.svetlio.audiofreedom.voice.runtime.LocalVoiceActivityDetector
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sqrt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

internal enum class AssistantVoiceState {
    Idle,
    Listening,
    Transcribing,
}

internal sealed interface VoiceRecordingResult {
    data class Audio(val samples: ShortArray) : VoiceRecordingResult
    data object CallActive : VoiceRecordingResult
    data object TooShort : VoiceRecordingResult
}

internal enum class SpokenConfirmationResult {
    Spoken,
    NoSpeechEngine,
    NoOfflineVoice,
    Failed,
}

internal object VoiceCaptureOwnership {
    val manualSessions = AtomicInteger(0)
    val microphoneLock = Any()
}

internal class VoiceCommandRecorder(private val context: Context) {
    private val stopGeneration = AtomicInteger(0)
    @Volatile var lastCaptureDetail: String = ""
        private set

    fun stop() {
        stopGeneration.incrementAndGet()
    }

    fun record(
        autoStopAfterSilence: Boolean = false,
        maximumSeconds: Int = MaximumSeconds,
        silenceMilliseconds: Int = 1000,
        waitForSpeechSeconds: Int = 8,
        handsFreeSession: Boolean = false,
        onLevel: (Float) -> Unit = {},
    ): VoiceRecordingResult {
        val generation = stopGeneration.get()
        if (!handsFreeSession) {
            VoiceCaptureOwnership.manualSessions.incrementAndGet()
            AssistantHandsFreeService.interruptConversation()
        }
        try {
            return synchronized(VoiceCaptureOwnership.microphoneLock) {
                capture(autoStopAfterSilence, maximumSeconds, silenceMilliseconds,
                    waitForSpeechSeconds, generation, onLevel)
            }
        } finally {
            if (!handsFreeSession) VoiceCaptureOwnership.manualSessions.decrementAndGet()
        }
    }

    private fun capture(
        autoStopAfterSilence: Boolean,
        maximumSeconds: Int,
        silenceMilliseconds: Int,
        waitForSpeechSeconds: Int,
        generation: Int,
        onLevel: (Float) -> Unit,
    ): VoiceRecordingResult {
        lastCaptureDetail = ""
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            error("Microphone permission is required")
        }
        if (isCallActive()) return VoiceRecordingResult.CallActive

        val minimumBuffer = AudioRecord.getMinBufferSize(
            SampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minimumBuffer > 0) { "Microphone recording is unavailable" }
        val bufferSamples = 512
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minimumBuffer, bufferSamples * 2),
        )
        check(recorder.state == AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            "Microphone recording could not be initialized"
        }

        val bytes = ByteArrayOutputStream()
        val buffer = ShortArray(bufferSamples)
        var callInterrupted = false
        val endpoint = VoiceEndpointDetector(silenceMilliseconds)
        val maximumBytes = SampleRate * maximumSeconds.coerceIn(1, MaximumSeconds) * 2
        var totalSamples = 0L
        var maximumLevel = 0F
        var maximumProbability = 0F
        val noiseSuppressor = if (NoiseSuppressor.isAvailable()) {
            runCatching { NoiseSuppressor.create(recorder.audioSessionId)?.apply { enabled = true } }
                .getOrNull()
        } else null
        var detector: LocalVoiceActivityDetector? = null
        try {
            if (autoStopAfterSilence) {
                detector = LocalVoiceActivityDetector(BundledSpeechDetector.path(context))
            }
            if (generation != stopGeneration.get()) return VoiceRecordingResult.TooShort
            recorder.startRecording()
            val startedAt = SystemClock.elapsedRealtime()
            while (generation == stopGeneration.get() && bytes.size() < maximumBytes) {
                if (isCallActive()) {
                    callInterrupted = true
                    break
                }
                val count = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (count < 0) error("Microphone read failed: $count")
                var squareSum = 0.0
                for (index in 0 until count) {
                    val normalized = buffer[index] / 32768.0
                    squareSum += normalized * normalized
                }
                val level = if (count == 0) 0F else sqrt(squareSum / count).toFloat()
                totalSamples += count
                maximumLevel = maxOf(maximumLevel, level)
                onLevel((level / VoiceLevelReference).coerceIn(0F, 1F))
                val ended = count > 0 && detector?.let {
                    val probability = it.probability(buffer, count)
                    check(probability.isFinite()) { "Speech detector returned an invalid probability" }
                    maximumProbability = maxOf(maximumProbability, probability)
                    endpoint.accept(probability, count)
                } == true
                val chunk = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
                for (index in 0 until count) chunk.putShort(buffer[index])
                bytes.write(chunk.array())
                if (ended) break
                // Keep a short lead-in, rather than feed long waiting silence to Whisper.
                if (autoStopAfterSilence && !endpoint.hasSpeech && bytes.size() > SampleRate) {
                    val raw = bytes.toByteArray()
                    val leadIn = raw.copyOfRange(raw.size - SampleRate, raw.size)
                    bytes.reset()
                    bytes.write(leadIn)
                }
                if (autoStopAfterSilence && !endpoint.hasSpeech &&
                    SystemClock.elapsedRealtime() - startedAt >= waitForSpeechSeconds * 1000L) break
                if (SystemClock.elapsedRealtime() - startedAt >= maximumSeconds * 1000L) break
            }
        } finally {
            lastCaptureDetail = String.format(Locale.ROOT,
                "Captured %.1f s | microphone peak %.4f | speech confidence %.2f | %s",
                totalSamples / SampleRate.toDouble(), maximumLevel, maximumProbability,
                if (endpoint.hasSpeech) "speech detected" else "no speech detected")
            onLevel(0F)
            runCatching { recorder.stop() }
            detector?.close()
            noiseSuppressor?.release()
            recorder.release()
        }
        if (callInterrupted) return VoiceRecordingResult.CallActive
        if (autoStopAfterSilence && !endpoint.hasSpeech) return VoiceRecordingResult.TooShort

        val raw = bytes.toByteArray()
        if (raw.size < MinimumBytes) return VoiceRecordingResult.TooShort
        val shorts = ShortArray(raw.size / 2)
        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        return VoiceRecordingResult.Audio(shorts)
    }

    private fun isCallActive(): Boolean {
        val mode = context.getSystemService(AudioManager::class.java).mode
        return mode == AudioManager.MODE_IN_CALL ||
            mode == AudioManager.MODE_IN_COMMUNICATION ||
            mode == AudioManager.MODE_RINGTONE ||
            mode == AudioManager.MODE_CALL_SCREENING
    }

    private companion object {
        const val SampleRate = 16_000
        const val MaximumSeconds = 20
        const val MinimumMilliseconds = 350
        const val MinimumBytes = SampleRate * MinimumMilliseconds / 1000 * 2
        const val VoiceLevelReference = 0.12F
    }
}

internal class VoiceCommandAudioSession(private val context: Context, private val handsFree: Boolean = false) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val focusRequest = AudioFocusRequest.Builder(
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE,
    )
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener { }
        .build()
    private var speechReady = CompletableDeferred<Boolean>()
    private val pendingSpeech = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private var enginePackage: String? = null
    private var textToSpeech = createSpeechEngine()

    private fun createSpeechEngine(): TextToSpeech {
        val ready = CompletableDeferred<Boolean>()
        speechReady = ready
        return TextToSpeech(context.applicationContext) { status ->
            ready.complete(status == TextToSpeech.SUCCESS)
        }.apply {
            setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(utteranceId: String?) {
                        utteranceId?.let { pendingSpeech.remove(it)?.complete(true) }
                    }

                    @Deprecated("Deprecated by Android")
                    override fun onError(utteranceId: String?) {
                        utteranceId?.let { pendingSpeech.remove(it)?.complete(false) }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        onError(utteranceId)
                    }
                },
            )
        }
    }
    private var holdsAudioFocus = false
    private var manualSession = false
    private val replyFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setOnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                stopSpeaking()
            }
        }
        .build()

    suspend fun speakMusicReply(text: String): SpokenConfirmationResult {
        releasePlaybackFocus()
        if (audioManager.mode != AudioManager.MODE_NORMAL) return SpokenConfirmationResult.Failed
        if (audioManager.requestAudioFocus(replyFocusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return SpokenConfirmationResult.Failed
        }
        return try {
            speak(text, AssistantLanguage.English)
        } finally {
            audioManager.abandonAudioFocusRequest(replyFocusRequest)
        }
    }

    @Synchronized
    fun beginManualSession() {
        if (!handsFree && !manualSession) {
            manualSession = true
            VoiceCaptureOwnership.manualSessions.incrementAndGet()
            AssistantHandsFreeService.interruptConversation()
        }
    }

    @Synchronized
    fun requestPlaybackPause(): Boolean {
        beginManualSession()
        if (holdsAudioFocus) return true
        holdsAudioFocus =
            audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return holdsAudioFocus
    }

    @Synchronized
    fun releasePlaybackFocus() {
        if (holdsAudioFocus) {
            audioManager.abandonAudioFocusRequest(focusRequest)
            holdsAudioFocus = false
        }
    }

    @Synchronized
    fun restorePlayback() {
        releasePlaybackFocus()
        if (manualSession) {
            manualSession = false
            VoiceCaptureOwnership.manualSessions.decrementAndGet()
        }
    }

    suspend fun speakPlannedChange(
        change: String,
        applying: Boolean,
    ): SpokenConfirmationResult {
        val prefix = if (applying) "Applying" else "Understood"
        return speak("$prefix: $change", AssistantLanguage.English)
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun speak(text: String, language: AssistantLanguage,
        maximumSpeechMilliseconds: Long = SpokenConfirmationTimeoutMilliseconds): SpokenConfirmationResult {
        if (!hasInstalledSpeechEngine(context)) return SpokenConfirmationResult.NoSpeechEngine
        val voices = availableSpeechVoices() ?: return SpokenConfirmationResult.Failed
        val requestedLocale = Locale.ENGLISH
        val preferences = AppPreferencesStore.load(context)
        val preferred = preferences.assistantEnglishSpeechVoice
        val selected = OfflineSpeechVoicePolicy.choose(voices, requestedLocale.language, requestedLocale.country, preferred)
            ?: return SpokenConfirmationResult.NoOfflineVoice
        val offlineVoice = textToSpeech.voices?.firstOrNull { it.name == selected.name }
            ?: return SpokenConfirmationResult.NoOfflineVoice
        if (textToSpeech.setVoice(offlineVoice) != TextToSpeech.SUCCESS) return SpokenConfirmationResult.Failed
        val utteranceId = UUID.randomUUID().toString()
        val completion = CompletableDeferred<Boolean>()
        pendingSpeech[utteranceId] = completion
        val result = textToSpeech.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId,
        )
        if (result == TextToSpeech.ERROR) {
            pendingSpeech.remove(utteranceId)
            return SpokenConfirmationResult.Failed
        }
        var completed = false
        try {
            completed = withTimeoutOrNull(maximumSpeechMilliseconds.coerceIn(1000L, 30_000L)) {
                completion.await()
            } ?: false
        } finally {
            pendingSpeech.remove(utteranceId)
            if (!completed) textToSpeech.stop()
        }
        return if (completed) {
            SpokenConfirmationResult.Spoken
        } else {
            SpokenConfirmationResult.Failed
        }
    }

    suspend fun availableSpeechVoices(): List<OfflineSpeechVoice>? {
        // A return from Android speech settings can change the default engine.
        if (enginePackage != null && enginePackage != textToSpeech.defaultEngine) {
            stopSpeaking()
            textToSpeech.shutdown()
            textToSpeech = createSpeechEngine()
            enginePackage = null
        }
        val initialized = withTimeoutOrNull(SpeechInitializationTimeoutMilliseconds) { speechReady.await() } ?: false
        if (!initialized) return null
        enginePackage = textToSpeech.defaultEngine
        return textToSpeech.voices.orEmpty().map { voice ->
            OfflineSpeechVoice("$enginePackage:${voice.name}", voice.name, voice.locale.language,
                voice.locale.country, voice.quality, voice.latency, voice.isNetworkConnectionRequired,
                TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in voice.features.orEmpty())
        }
    }

    fun shutdown() {
        stopSpeaking()
        textToSpeech.shutdown()
        restorePlayback()
    }

    fun stopSpeaking() {
        pendingSpeech.values.forEach { it.complete(false) }
        pendingSpeech.clear()
        textToSpeech.stop()
    }

    private companion object {
        const val SpokenConfirmationTimeoutMilliseconds = 15_000L
        const val SpeechInitializationTimeoutMilliseconds = 5_000L

    }
}

internal object AssistantVoiceRuntimeManager {
    private val mutex = Mutex()
    private var runtime: LocalWhisperRuntime? = null
    private var loadedPath: String? = null

    @Suppress("UNUSED_PARAMETER")
    suspend fun transcribe(
        samples: ShortArray,
        language: AssistantLanguage,
        commandHints: Boolean = true,
    ): String =
        mutex.withLock {
            val modelPath = AssistantRuntimeManager.voiceModelPath()
                ?: error("Install the voice language pack first")
            val active = runtime ?: LocalWhisperRuntime().also { runtime = it }
            if (!active.isModelLoaded || loadedPath != modelPath) {
                active.loadModel(modelPath)
                loadedPath = modelPath
            }
            active.transcribe(
                samples,
                "en",
                commandHints,
            )
        }
}

internal fun chooseAutomaticVoiceTranscript(
    primary: String,
    bulgarian: String,
    isSupported: (String) -> Boolean,
): String = when {
    isSupported(primary) -> primary
    isSupported(bulgarian) -> bulgarian
    primary.isNotBlank() -> primary
    else -> bulgarian
}

internal fun isSupportedAssistantCommand(
    command: String,
    current: AudioFreedomSettings,
    profiles: List<AudioFreedomProfile>,
    preferences: AppPreferences,
): Boolean = AssistantConversationCommands.recognize(command) != null || AssistantCommandPlanner.plan(
    command = command,
    current = current,
    profiles = profiles,
    maximumBandDeltaMillibels =
        preferences.assistantAdjustmentStrength.maximumBandDeltaMillibels,
) !is AssistantPlanResult.NotUnderstood
