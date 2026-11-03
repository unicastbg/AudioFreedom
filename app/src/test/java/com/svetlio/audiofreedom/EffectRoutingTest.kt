package com.svetlio.audiofreedom

import org.junit.Assert.*
import org.junit.Test

class EffectRoutingTest {
    private class FakeEffect : EffectClient {
        override var isAttached = false
        override var frameworkEnabled = true
        override var driverEnabled: Boolean? = true
        var current = AudioFreedomSettings()
        var released = 0
        var sequence: Int? = null
        override fun attach(value: AudioFreedomSettings): DriverState {
            current = value; isAttached = true; return DriverState.Attached
        }
        override fun release() { isAttached = false; released++ }
        override fun setFrameworkEnabled(enabled: Boolean): Boolean { frameworkEnabled = enabled; return true }
        override fun setProcessingEnabled(enabled: Boolean): Boolean { driverEnabled = enabled; return true }
        override fun applySettings(value: AudioFreedomSettings): Boolean { current = value; return true }
        override fun queryOutputMetrics() = AudioOutputMetrics(-1000, -800, 0)
        override fun queryActiveStream() = sequence?.let { DspStreamInfo(it, 48000, 2, 1) }
        override fun queryPlaybackAnalysis(): PlaybackAnalysis? = null
        override fun setAnalysisEnabled(enabled: Boolean) = true
    }
    private class Fixture {
        val clients = mutableMapOf<Int, FakeEffect>()
        var now = 10000L
        var playing = true
        var starts = 0
        var stops = 0
        lateinit var emit: (Set<Int>) -> Unit
        lateinit var fail: () -> Unit
        val controller = EffectRoutingController({ id -> FakeEffect().also { clients[id] = it } },
            { playing }, { now }, { onSessions, onUnavailable ->
                emit = onSessions; fail = onUnavailable
                object : SessionDiscovery {
                    override fun start() { starts++ }
                    override fun stop() { stops++ }
                }
            })
        val global get() = clients.getValue(0)
        fun suspended() {
            global.driverEnabled = false
            controller.checkHealth()
            now += 2000
            controller.checkHealth()
        }
    }

    @Test fun healthyGlobalPathNeverStartsRootDiscovery() {
        val f = Fixture()
        assertEquals(DriverState.Attached, f.controller.attach(AudioFreedomSettings()))
        repeat(5) { f.now += 2000; f.controller.checkHealth() }
        assertEquals(0, f.starts)
        assertEquals(setOf(0), f.clients.keys)
        assertEquals(ProcessingHealth.Global, f.controller.health)
    }

    @Test fun cachedFrameworkEnabledDoesNotHideNativeSuspensionAndOnlyOnePathProcesses() {
        val f = Fixture()
        f.controller.attach(AudioFreedomSettings())
        f.suspended()
        assertTrue(f.global.isAttached)
        assertFalse(f.global.frameworkEnabled)
        assertEquals(1, f.starts)
        f.emit(setOf(113))
        assertTrue(f.clients.getValue(113).isAttached)
        assertEquals(ProcessingHealth.Sessions, f.controller.health)
        f.controller.checkHealth()
        assertEquals(1, f.starts)
    }

    @Test fun manualSettingsReachEveryActiveSessionAndFutureSessions() {
        val f = Fixture()
        f.controller.attach(AudioFreedomSettings())
        f.suspended()
        f.emit(setOf(1, 2))
        val settings = AudioFreedomSettings(preampMillibels = -1200, equalizerEnabled = true)
        assertTrue(f.controller.applySettings(settings))
        assertEquals(settings, f.clients.getValue(1).current)
        assertEquals(settings, f.clients.getValue(2).current)
        f.emit(setOf(2, 3))
        assertFalse(f.clients.getValue(1).isAttached)
        assertEquals(settings, f.clients.getValue(3).current)
        assertFalse(f.global.frameworkEnabled)
    }

    @Test fun callsAndProcessingOffReleaseEverythingAndIgnoreLateDiscovery() {
        val f = Fixture()
        f.controller.attach(AudioFreedomSettings())
        f.suspended(); f.emit(setOf(1, 2))
        f.controller.release()
        f.emit(setOf(3))
        assertTrue(f.clients.values.none { it.isAttached })
        assertFalse(3 in f.clients)
        assertEquals(0, f.controller.telemetrySource)
    }

    @Test fun idleAndTransientDisableDoNotTriggerRecovery() {
        val f = Fixture()
        f.controller.attach(AudioFreedomSettings())
        f.global.driverEnabled = false
        f.playing = false
        f.controller.checkHealth(); f.now += 5000; f.controller.checkHealth()
        assertEquals(0, f.starts)
        f.global.driverEnabled = true
        f.playing = true
        f.controller.checkHealth()
        assertEquals(0, f.starts)
    }

    @Test fun anotherEnginesAdvancingTelemetryCannotHideSuspendedControlInstance() {
        val f = Fixture()
        f.controller.attach(AudioFreedomSettings())
        f.global.driverEnabled = false
        f.controller.checkHealth()
        repeat(4) { f.global.sequence = it + 1; f.now += 500; f.controller.checkHealth() }
        assertEquals(1, f.starts)
        assertFalse(f.global.frameworkEnabled)
        assertEquals(true, f.global.driverEnabled)
    }

    @Test fun rootFailureAndIdleAreReportedWithoutPretendingToProcess() {
        val f = Fixture()
        f.controller.attach(AudioFreedomSettings())
        f.suspended()
        f.emit(emptySet())
        assertEquals(ProcessingHealth.Waiting, f.controller.health)
        assertNull(f.controller.queryOutputMetrics())
        f.fail()
        assertEquals(ProcessingHealth.Blocked, f.controller.health)
        assertNull(f.controller.queryActiveStream())
    }

    @Test fun discoveryProtocolOnlyAcceptsBoundedPositiveSessionIds() {
        assertEquals(setOf(12, 13), parseMediaSessions("SESSIONS:12,13,12"))
        assertEquals(emptySet<Int>(), parseMediaSessions("SESSIONS:"))
        for (line in listOf("ROOT:true", "SESSIONS:0", "SESSIONS:-1", "SESSIONS:hello", "SESSIONS:12;reboot",
            "SESSIONS:" + (1..65).joinToString(","))) assertNull(parseMediaSessions(line))
    }
}
