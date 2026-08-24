package com.svetlio.audiofreedom

import java.util.concurrent.TimeUnit

internal enum class RootAccessState {
    NotChecked,
    Checking,
    Available,
    Unavailable,
}

internal fun probeRootAccess(): RootAccessState {
    val process = try {
        ProcessBuilder("su", "-c", "id")
            .redirectErrorStream(true)
            .start()
    } catch (_: Exception) {
        return RootAccessState.Unavailable
    }

    return try {
        if (!process.waitFor(ROOT_PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            RootAccessState.Unavailable
        } else {
            val output = process.inputStream.bufferedReader().use { it.readText() }
            if (process.exitValue() == 0 && output.contains("uid=0")) {
                RootAccessState.Available
            } else {
                RootAccessState.Unavailable
            }
        }
    } catch (_: Exception) {
        process.destroyForcibly()
        RootAccessState.Unavailable
    }
}

private const val ROOT_PROBE_TIMEOUT_SECONDS = 4L
