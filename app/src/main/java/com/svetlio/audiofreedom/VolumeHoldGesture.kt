package com.svetlio.audiofreedom

internal enum class VolumeRelease { Ignore, Tap, Activate, Consume }

internal class VolumeHoldGesture {
    private var key: Int? = null
    private var device = 0
    private var deadline = 0L
    private var activated = false
    private var cancelled = false

    fun tracks(keyCode: Int, deviceId: Int): Boolean = key == keyCode && device == deviceId

    fun begin(keyCode: Int, deviceId: Int, now: Long, holdMilliseconds: Int): Boolean {
        if (key != null) return false
        key = keyCode
        device = deviceId
        deadline = now + holdMilliseconds.coerceIn(500, 3000)
        activated = false
        cancelled = false
        return true
    }

    fun activate(now: Long): Boolean {
        if (key == null || activated || cancelled || now < deadline) return false
        activated = true
        return true
    }

    fun cancel() { cancelled = true }

    fun beginCancellation(keyCode: Int, deviceId: Int): Boolean {
        if (!begin(keyCode, deviceId, 0L, 500)) return false
        cancel()
        return true
    }

    fun release(keyCode: Int, deviceId: Int, now: Long): VolumeRelease {
        if (!tracks(keyCode, deviceId)) return VolumeRelease.Ignore
        val result = when {
            cancelled || activated -> VolumeRelease.Consume
            now >= deadline -> VolumeRelease.Activate
            else -> VolumeRelease.Tap
        }
        key = null
        return result
    }
}
