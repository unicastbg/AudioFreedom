package com.svetlio.audiofreedom.voice.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LocalWhisperRuntime {
    private val mutex = Mutex()
    private var modelPath: String? = null

    val isModelLoaded: Boolean
        get() = modelPath != null

    suspend fun loadModel(path: String) {
        require(path.isNotBlank()) { "Model path cannot be blank" }
        mutex.withLock {
            withContext(Dispatchers.IO) { nativeLoadModel(path) }
            modelPath = path
        }
    }

    suspend fun transcribe(pcm16: ShortArray, language: String): String {
        require(pcm16.isNotEmpty()) { "Recording is empty" }
        return mutex.withLock {
            check(isModelLoaded) { "No Whisper model is loaded" }
            withContext(Dispatchers.Default) {
                nativeTranscribe(pcm16, language).trim()
            }
        }
    }

    suspend fun unload() {
        mutex.withLock {
            withContext(Dispatchers.IO) { nativeUnload() }
            modelPath = null
        }
    }

    private external fun nativeLoadModel(path: String)
    private external fun nativeTranscribe(pcm16: ShortArray, language: String): String
    private external fun nativeUnload()

    private companion object {
        init {
            System.loadLibrary("audiofreedom-voice")
        }
    }
}
