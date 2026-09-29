package com.svetlio.audiofreedom.assistant.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LocalLlmRuntime {
    private val mutex = Mutex()
    private var loadedModelPath: String? = null

    init {
        nativeInitialize()
    }

    val isModelLoaded: Boolean
        get() = loadedModelPath != null

    suspend fun loadModel(path: String, contextSize: Int = DefaultContextSize) {
        require(path.isNotBlank()) { "Model path cannot be blank" }
        require(contextSize in 1024..4096) { "Context size must be between 1024 and 4096" }
        mutex.withLock {
            withContext(Dispatchers.IO) {
                nativeLoadModel(path, contextSize)
            }
            loadedModelPath = path
        }
    }

    suspend fun generate(
        systemPrompt: String,
        userPrompt: String,
        grammar: String,
        maximumTokens: Int = DefaultMaximumTokens,
    ): String {
        require(systemPrompt.isNotBlank()) { "System prompt cannot be blank" }
        require(userPrompt.isNotBlank()) { "User prompt cannot be blank" }
        require(grammar.isNotBlank()) { "Grammar cannot be blank" }
        require(maximumTokens in 16..512) { "Maximum tokens must be between 16 and 512" }
        return mutex.withLock {
            check(isModelLoaded) { "No local model is loaded" }
            withContext(Dispatchers.IO) {
                nativeGenerate(systemPrompt, userPrompt, grammar, maximumTokens).decodeToString()
            }
        }
    }

    suspend fun unload() {
        mutex.withLock {
            withContext(Dispatchers.IO) { nativeUnload() }
            loadedModelPath = null
        }
    }

    private external fun nativeInitialize()
    private external fun nativeLoadModel(path: String, contextSize: Int)
    private external fun nativeGenerate(
        systemPrompt: String,
        userPrompt: String,
        grammar: String,
        maximumTokens: Int,
    ): ByteArray
    private external fun nativeUnload()

    private companion object {
        const val DefaultContextSize = 2048
        const val DefaultMaximumTokens = 192

        init {
            System.loadLibrary("audiofreedom-assistant")
        }
    }
}
