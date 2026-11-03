package com.svetlio.audiofreedom

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

internal fun parseMediaSessions(line: String): Set<Int>? {
    if (!line.startsWith("SESSIONS:") || line.length > 1024) return null
    val value = line.removePrefix("SESSIONS:")
    if (value.isEmpty()) return emptySet()
    val fields = value.split(',')
    if (fields.size > 64) return null
    val ids = fields.map { it.toIntOrNull()?.takeIf { id -> id > 0 } ?: return null }
    return ids.toSet()
}

internal class MediaSessionDiscovery(
    private val context: Context,
    private val onSessions: (Set<Int>) -> Unit,
    private val onUnavailable: () -> Unit,
) : SessionDiscovery {
    private val generation = AtomicInteger()
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var process: Process? = null
    private var started = false

    override fun start() {
        if (started) return
        started = true
        val token = generation.incrementAndGet()
        thread(name = "AudioFreedom-session-discovery", isDaemon = true) {
            var child: Process? = null
            try {
                val bytes = context.assets.open("audiofreedom-session-monitor.jar").use { it.readBytes() }
                val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { "%02x".format(it) }
                val jar = File(context.codeCacheDir, "audiofreedom-session-$hash.jar")
                if (!jar.exists()) {
                    jar.outputStream().use { it.write(bytes) }
                    check(jar.setReadOnly())
                }
                if (generation.get() != token) return@thread
                val path = jar.absolutePath.replace("'", "'\\''")
                child = ProcessBuilder("su", "-c", "CLASSPATH='$path' app_process /system/bin " +
                    "com.svetlio.audiofreedom.monitor.AudioFreedomSessionMonitor ${context.applicationInfo.uid}")
                    .redirectErrorStream(true).start()
                process = child
                if (generation.get() != token) return@thread
                child.inputStream.bufferedReader().use { reader ->
                    while (generation.get() == token) {
                        val line = reader.readLine() ?: break
                        val sessions = parseMediaSessions(line) ?: continue
                        handler.post { if (generation.get() == token) onSessions(sessions) }
                    }
                }
            } catch (_: Exception) {
                // No raw process output is logged: discovery exposes IDs only.
            } finally {
                runCatching { child?.outputStream?.close() }
                child?.destroy()
                if (process === child) process = null
                handler.post {
                    if (generation.get() == token) {
                        started = false
                        onUnavailable()
                    }
                }
            }
        }
    }

    override fun stop() {
        generation.incrementAndGet()
        started = false
        val child = process
        process = null
        // EOF stops the helper's polling loop even if su has a separate child PID.
        runCatching { child?.outputStream?.close() }
        child?.destroy()
    }
}
