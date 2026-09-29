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
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import com.svetlio.audiofreedom.voice.runtime.LocalWhisperRuntime
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
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
    NoOfflineVoice,
    Failed,
}

internal class VoiceCommandRecorder(private val context: Context) {
    private val stopRequested = AtomicBoolean(false)

    fun stop() {
        stopRequested.set(true)
    }

    fun record(
        autoStopAfterSilence: Boolean = false,
        maximumSeconds: Int = MaximumSeconds,
        onLevel: (Float) -> Unit = {},
    ): VoiceRecordingResult {
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
        val bufferSamples = maxOf(minimumBuffer / 2, 2048)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSamples * 2,
        )
        check(recorder.state == AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            "Microphone recording could not be initialized"
        }

        stopRequested.set(false)
        val bytes = ByteArrayOutputStream()
        val buffer = ShortArray(bufferSamples)
        var callInterrupted = false
        var speechDetected = false
        var silentSamples = 0
        val maximumBytes = SampleRate * maximumSeconds.coerceIn(1, MaximumSeconds) * 2
        try {
            recorder.startRecording()
            while (!stopRequested.get() && bytes.size() < maximumBytes) {
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
                onLevel((level / VoiceLevelReference).coerceIn(0F, 1F))
                if (autoStopAfterSilence) {
                    if (level >= SpeechStartLevel) {
                        speechDetected = true
                        silentSamples = 0
                    } else if (speechDetected && level <= SpeechStopLevel) {
                        silentSamples += count
                    } else if (speechDetected) {
                        silentSamples = 0
                    }
                }
                val chunk = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
                for (index in 0 until count) chunk.putShort(buffer[index])
                bytes.write(chunk.array())
                if (
                    autoStopAfterSilence && speechDetected &&
                    silentSamples >= SampleRate * SilenceAfterSpeechMilliseconds / 1000
                ) {
                    break
                }
            }
        } finally {
            onLevel(0F)
            runCatching { recorder.stop() }
            recorder.release()
        }
        if (callInterrupted) return VoiceRecordingResult.CallActive

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
        const val SilenceAfterSpeechMilliseconds = 1_100
        const val SpeechStartLevel = 0.025F
        const val SpeechStopLevel = 0.015F
        const val VoiceLevelReference = 0.12F
    }
}

internal class VoiceCommandAudioSession(context: Context) {
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
    private val speechReady = CompletableDeferred<Boolean>()
    private val pendingSpeech = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val textToSpeech = TextToSpeech(context.applicationContext) { status ->
        speechReady.complete(status == TextToSpeech.SUCCESS)
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
    private var holdsAudioFocus = false

    fun requestPlaybackPause(): Boolean {
        if (holdsAudioFocus) return true
        holdsAudioFocus =
            audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return holdsAudioFocus
    }

    fun restorePlayback() {
        if (!holdsAudioFocus) return
        audioManager.abandonAudioFocusRequest(focusRequest)
        holdsAudioFocus = false
    }

    suspend fun speakPlannedChange(
        change: String,
        applying: Boolean,
    ): SpokenConfirmationResult {
        val initialized = withTimeoutOrNull(SpeechInitializationTimeoutMilliseconds) {
            speechReady.await()
        } ?: false
        if (!initialized) return SpokenConfirmationResult.Failed
        val requestedLocale = if (change.any { it in '\u0400'..'\u04ff' }) {
            bulgarianLocale()
        } else {
            Locale.ENGLISH
        }
        val offlineVoice = textToSpeech.voices
            ?.filterNot { it.isNetworkConnectionRequired }
            ?.firstOrNull { it.locale.language == requestedLocale.language }
            ?: return SpokenConfirmationResult.NoOfflineVoice
        textToSpeech.voice = offlineVoice
        val prefix = when {
            requestedLocale.language == "bg" && applying -> "Прилагам"
            requestedLocale.language == "bg" -> "Разбрах"
            applying -> "Applying"
            else -> "Understood"
        }
        val utteranceId = UUID.randomUUID().toString()
        val completion = CompletableDeferred<Boolean>()
        pendingSpeech[utteranceId] = completion
        val result = textToSpeech.speak(
            "$prefix: $change",
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId,
        )
        if (result == TextToSpeech.ERROR) {
            pendingSpeech.remove(utteranceId)
            return SpokenConfirmationResult.Failed
        }
        val completed = withTimeoutOrNull(SpokenConfirmationTimeoutMilliseconds) {
            completion.await()
        } ?: false
        return if (completed) {
            SpokenConfirmationResult.Spoken
        } else {
            SpokenConfirmationResult.Failed
        }
    }

    fun shutdown() {
        pendingSpeech.values.forEach { it.complete(false) }
        pendingSpeech.clear()
        textToSpeech.stop()
        textToSpeech.shutdown()
        restorePlayback()
    }

    private companion object {
        const val SpokenConfirmationTimeoutMilliseconds = 15_000L
        const val SpeechInitializationTimeoutMilliseconds = 5_000L

        fun bulgarianLocale(): Locale = Locale.Builder()
            .setLanguage("bg")
            .setRegion("BG")
            .build()
    }
}

internal object AssistantVoiceRuntimeManager {
    private val mutex = Mutex()
    private var runtime: LocalWhisperRuntime? = null
    private var loadedPath: String? = null

    suspend fun transcribe(samples: ShortArray, language: AssistantLanguage): String =
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
                when (language) {
                    AssistantLanguage.Automatic -> "auto"
                    AssistantLanguage.English -> "en"
                    AssistantLanguage.Bulgarian -> "bg"
                },
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
): Boolean = AssistantCommandPlanner.plan(
    command = command,
    current = current,
    profiles = profiles,
    maximumBandDeltaMillibels =
        preferences.assistantAdjustmentStrength.maximumBandDeltaMillibels,
) !is AssistantPlanResult.NotUnderstood
