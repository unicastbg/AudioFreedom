package com.svetlio.audiofreedom.voice.runtime

class LocalVoiceActivityDetector(modelPath: String) : AutoCloseable {
    private var handle = nativeCreate(modelPath)

    init { check(handle != 0L) { "Speech detector could not be initialized" } }

    fun probability(samples: ShortArray, count: Int): Float {
        check(handle != 0L) { "Speech detector is closed" }
        require(count in 1..512 && count <= samples.size)
        return nativeProbability(handle, samples, count)
    }

    override fun close() {
        if (handle != 0L) nativeFree(handle)
        handle = 0L
    }

    private external fun nativeCreate(path: String): Long
    private external fun nativeProbability(handle: Long, samples: ShortArray, count: Int): Float
    private external fun nativeFree(handle: Long)

    private companion object {
        init { System.loadLibrary("audiofreedom-voice") }
    }
}
