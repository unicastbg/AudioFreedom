package com.svetlio.audiofreedom

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

internal enum class SpatialLeaseState {
    Idle, Starting, Active, Restoring, Recovering, Overridden, Error, RestoreFailed, Unsupported,
}

internal val AudioFreedomSettings.needsStereoPath: Boolean
    get() = stereoRoutingEnabled &&
        (stereoRoutingMode != StereoRoutingMode.Normal || stereoBalancePercent != 0)

internal fun shouldRequestSpatialConsent(
    settings: AudioFreedomSettings, processing: Boolean, channels: Int?, spatialEnabled: Boolean?,
    state: SpatialLeaseState,
): Boolean = settings.needsStereoPath && processing && channels != null && channels > 2 &&
    spatialEnabled == true && state == SpatialLeaseState.Idle

internal fun systemSpatialAudioEnabled(context: Context): Boolean? = runCatching {
    context.getSystemService(AudioManager::class.java).spatializer.isEnabled
}.getOrNull()

internal fun clearsSpatialConsent(previous: SpatialLeaseState, next: SpatialLeaseState): Boolean =
    (next == SpatialLeaseState.Idle && previous != SpatialLeaseState.Recovering) ||
        next in setOf(SpatialLeaseState.Error, SpatialLeaseState.RestoreFailed, SpatialLeaseState.Overridden)

/** A private, heartbeat-bounded root helper owns and restores the global change. */
internal class SpatialAudioLease(
    private val context: Context,
    private val onState: (SpatialLeaseState) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val generation = AtomicInteger()
    private val lock = Any()
    private var child: Process? = null
    private var workerRunning = false
    @Volatile var state: SpatialLeaseState = SpatialLeaseState.Idle
        private set

    private fun publish(value: SpatialLeaseState) { state = value; onState(value) }
    private val heartbeat = object : Runnable {
        override fun run() {
            val process = synchronized(lock) { child }
            if (process != null) runCatching {
                process.outputStream.write("PING\n".toByteArray())
                process.outputStream.flush()
            }
            if (workerRunning) handler.postDelayed(this, 2000)
        }
    }

    private fun command(mode: String): List<String> {
        val bytes = context.assets.open("audiofreedom-spatial-lease.jar").use { it.readBytes() }
        fun digest(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value)
            .joinToString("") { "%02x".format(it) }
        val hash = digest(bytes)
        val jar = File(context.codeCacheDir, "spatial-lease-$hash.jar")
        synchronized(assetLock) {
            val valid = runCatching { jar.isFile && digest(jar.readBytes()) == hash }.getOrDefault(false)
            if (!valid) {
                check(!jar.exists() || jar.delete())
                jar.outputStream().use { it.write(bytes) }
            }
            check(jar.setReadOnly())
        }
        fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
        val directory = File(context.noBackupFilesDir, "spatial-audio")
        return listOf("su", "-c", "CLASSPATH=${quote(jar.absolutePath)} app_process /system/bin " +
            "com.svetlio.audiofreedom.spatial.AudioFreedomSpatialLease $mode ${quote(directory.absolutePath)}")
    }

    fun recover() {
        if (workerRunning) return
        if (!File(context.noBackupFilesDir, "spatial-audio").exists()) {
            publish(SpatialLeaseState.Recovering)
            publish(SpatialLeaseState.Idle)
            return
        }
        run("recover")
    }

    fun start() {
        if (workerRunning) return
        run("lease")
    }

    private fun run(mode: String) {
        workerRunning = true
        val token = generation.incrementAndGet()
        publish(if (mode == "lease") SpatialLeaseState.Starting else SpatialLeaseState.Recovering)
        handler.post(heartbeat)
        thread(name = "AudioFreedom-spatial-lease", isDaemon = true) {
            var process: Process? = null
            var restored = false
            var terminal = SpatialLeaseState.Idle
            try {
                process = ProcessBuilder(command(mode)).redirectErrorStream(true).start()
                synchronized(lock) {
                    if (generation.get() == token) child = process
                    else process.outputStream.close()
                }
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        when (line) {
                            "ACTIVE" -> handler.post {
                                if (generation.get() == token) publish(SpatialLeaseState.Active)
                            }
                            "RESTORED", "RECOVERED", "UNCHANGED" -> restored = true
                            "OVERRIDDEN" -> { restored = true; terminal = SpatialLeaseState.Overridden }
                            "ERROR", "BUSY" -> terminal = SpatialLeaseState.Error
                            "RESTORE_FAILED" -> terminal = SpatialLeaseState.RestoreFailed
                        }
                    }
                }
                process.waitFor()
                if (!restored && terminal == SpatialLeaseState.Idle) terminal = SpatialLeaseState.Error
            } catch (_: Exception) {
                terminal = SpatialLeaseState.Error
            } finally {
                runCatching { process?.outputStream?.close() }
                synchronized(lock) { if (child === process) child = null }
                // Do not kill the helper before it can restore. EOF and its timeout
                // independently end the lease if this process is killed.
                handler.post {
                    workerRunning = false
                    handler.removeCallbacks(heartbeat)
                    publish(terminal)
                    if (mode == "lease" && !restored &&
                        terminal in setOf(SpatialLeaseState.Error, SpatialLeaseState.RestoreFailed)) recover()
                }
            }
        }
    }

    fun stop() {
        if (!workerRunning) return
        generation.incrementAndGet()
        publish(SpatialLeaseState.Restoring)
        handler.removeCallbacks(heartbeat)
        synchronized(lock) { runCatching { child?.outputStream?.close() } }
    }

    private companion object {
        val assetLock = Any()
    }
}
