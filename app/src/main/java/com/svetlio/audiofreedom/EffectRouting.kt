package com.svetlio.audiofreedom

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock

internal enum class ProcessingHealth(val title: String, val detail: String?) {
    Global("Processing on", null),
    Recovering("Restoring processing", "Android suspended the global effect"),
    Sessions("Processing on", "Media-session compatibility mode"),
    Waiting("Waiting for playback", "Media-session compatibility mode"),
    Blocked("Processing suspended", "Session recovery unavailable; check root access and refresh"),
    Call("Paused during call", null),
}

internal fun needsSessionRecovery(attached: Boolean, frameworkEnabled: Boolean): Boolean =
    attached && !frameworkEnabled

internal interface EffectClient {
    val isAttached: Boolean
    val frameworkEnabled: Boolean
    val driverEnabled: Boolean?
    fun attach(value: AudioFreedomSettings): DriverState
    fun release()
    fun setFrameworkEnabled(enabled: Boolean): Boolean
    fun setProcessingEnabled(enabled: Boolean): Boolean
    fun applySettings(value: AudioFreedomSettings): Boolean
    fun queryOutputMetrics(): AudioOutputMetrics?
    fun queryActiveStream(): DspStreamInfo?
    fun queryPlaybackAnalysis(): PlaybackAnalysis?
    fun setAnalysisEnabled(enabled: Boolean): Boolean
}

internal interface SessionDiscovery {
    fun start()
    fun stop()
}

/** One processing path at a time. Per-session inserts avoid global-mix suspension. */
internal class EffectRoutingController(
    private val create: (Int) -> EffectClient,
    private val musicActive: () -> Boolean,
    private val clock: () -> Long,
    discoveryFactory: ((Set<Int>) -> Unit, () -> Unit) -> SessionDiscovery,
) {
    constructor(context: Context) : this(
        { PlatformEffectController(it) },
        { context.getSystemService(AudioManager::class.java).isMusicActive },
        { SystemClock.elapsedRealtime() },
        { onSessions, onUnavailable -> MediaSessionDiscovery(context, onSessions, onUnavailable) },
    )
    private val global = create(0)
    private val sessions = linkedMapOf<Int, EffectClient>()
    private var settings = AudioFreedomSettings()
    private var fallback = false
    private var analysisEnabled = false
    private var inactiveSince: Long? = null
    private var recoveryFailed = false
    private var lastSessions: Set<Int> = emptySet()
    var health: ProcessingHealth = ProcessingHealth.Global
        private set
    var telemetrySource: Int = 0
        private set
    private val discovery = discoveryFactory(::updateSessions) {
        health = if (sessions.isEmpty()) ProcessingHealth.Blocked else ProcessingHealth.Sessions
    }

    val isAttached: Boolean get() = global.isAttached

    fun attach(value: AudioFreedomSettings): DriverState {
        release()
        settings = value
        val state = global.attach(value)
        if (state == DriverState.Attached) checkHealth()
        return state
    }

    fun checkHealth() {
        if (fallback || recoveryFailed) return
        val now = clock()
        val actuallyEnabled = global.frameworkEnabled && global.driverEnabled != false
        if (!actuallyEnabled) {
            if (inactiveSince == null) inactiveSince = now
        } else inactiveSince = null
        if (needsSessionRecovery(global.isAttached, actuallyEnabled) && musicActive() &&
            now - (inactiveSince ?: now) >= 1000L) {
            // Disabling the controlling global handle prevents double processing if
            // Android later clears its suspension while session inserts are active.
            if (!global.setFrameworkEnabled(false)) {
                health = ProcessingHealth.Blocked
                recoveryFailed = true
                return
            }
            // AIDL's control-session disable also bypasses shared DSP state. Restore
            // that state without re-enabling the global framework processing path.
            if (!global.setProcessingEnabled(true)) {
                health = ProcessingHealth.Blocked
                recoveryFailed = true
                return
            }
            fallback = true
            health = ProcessingHealth.Recovering
            discovery.start()
        }
    }

    private fun updateSessions(ids: Set<Int>) {
        if (!fallback) return
        lastSessions = ids
        sessions.keys.filter { it !in ids }.forEach { sessions.remove(it)?.release() }
        for (id in ids) if (id !in sessions) {
            val controller = create(id)
            if (controller.attach(settings) == DriverState.Attached) {
                controller.setAnalysisEnabled(analysisEnabled)
                sessions[id] = controller
            } else controller.release()
        }
        telemetrySource = sessions.keys.firstOrNull() ?: -1
        health = when {
            ids.isEmpty() -> ProcessingHealth.Waiting
            sessions.size == ids.size && sessions.values.all { it.frameworkEnabled } -> ProcessingHealth.Sessions
            else -> ProcessingHealth.Blocked
        }
    }

    fun retryRecovery() {
        recoveryFailed = false
        if (fallback) {
            sessions.keys.filter { sessions[it]?.frameworkEnabled != true }.forEach {
                sessions.remove(it)?.release()
            }
            updateSessions(lastSessions)
            discovery.start()
        } else checkHealth()
    }

    fun applySettings(value: AudioFreedomSettings): Boolean {
        settings = value
        val globalApplied = global.applySettings(value)
        val sessionResults = sessions.values.map { it.applySettings(value) }
        return globalApplied && sessionResults.all { it }
    }

    fun queryOutputMetrics(): AudioOutputMetrics? {
        if (!fallback) return global.queryOutputMetrics().takeIf { global.frameworkEnabled }
        val values = sessions.values.filter { it.frameworkEnabled }.mapNotNull { it.queryOutputMetrics() }
        if (values.isEmpty()) return null
        return AudioOutputMetrics(values.maxOf { it.inputPeakMillibels }, values.maxOf { it.outputPeakMillibels },
            values.maxBy { kotlin.math.abs(it.gainReductionMillibels) }.gainReductionMillibels)
    }

    fun queryActiveStream(): DspStreamInfo? = telemetryController()?.queryActiveStream()
    fun queryPlaybackAnalysis(): PlaybackAnalysis? = telemetryController()?.queryPlaybackAnalysis()
    private fun telemetryController(): EffectClient? =
        if (fallback) sessions.values.firstOrNull { it.frameworkEnabled } else global.takeIf { it.frameworkEnabled }

    fun setAnalysisEnabled(enabled: Boolean): Boolean {
        analysisEnabled = enabled
        if (!fallback) return global.setAnalysisEnabled(enabled)
        global.setAnalysisEnabled(false)
        val results = sessions.values.map { it.setAnalysisEnabled(enabled) }
        return results.isNotEmpty() && results.all { it }
    }

    fun release() {
        discovery.stop()
        sessions.values.forEach { it.release() }
        sessions.clear()
        global.release()
        fallback = false
        inactiveSince = null
        recoveryFailed = false
        lastSessions = emptySet()
        telemetrySource = 0
        analysisEnabled = false
        health = ProcessingHealth.Global
    }
}
