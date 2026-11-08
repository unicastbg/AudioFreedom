package com.svetlio.audiofreedom

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class SpatialAudioLeaseDeviceTest {
    @Test fun disabledSpatialAudioIsLeftDisabled() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifySpatialInitiallyOff") == "true")
        val i = InstrumentationRegistry.getInstrumentation()
        val context = i.targetContext
        assertEquals(false, systemSpatialAudioEnabled(context))
        lateinit var lease: SpatialAudioLease
        i.runOnMainSync { lease = SpatialAudioLease(context) {} }
        try {
            i.runOnMainSync { lease.start() }
            await("An already-disabled spatializer was not left unchanged") {
                lease.state == SpatialLeaseState.Idle
            }
            assertEquals(false, systemSpatialAudioEnabled(context))
        } finally {
            i.runOnMainSync { lease.stop() }
        }
    }

    @Test fun reportSpatialApis() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifySpatialApis") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(android.media.AudioManager::class.java)
        println("SPATIAL_PUBLIC enabled=${manager.spatializer.isEnabled} available=${manager.spatializer.isAvailable}")
        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
            putString("spatial_public", "enabled=${manager.spatializer.isEnabled} available=${manager.spatializer.isAvailable}")
        })
        val get = org.lsposed.hiddenapibypass.HiddenApiBypass.getDeclaredMethod(
            android.media.AudioManager::class.java, "getService").apply { isAccessible = true }
        val service = get.invoke(null)
        val type = Class.forName("android.media.IAudioService")
        val read = org.lsposed.hiddenapibypass.HiddenApiBypass.getDeclaredMethod(type, "isSpatializerEnabled").apply { isAccessible = true }
        println("SPATIAL_BINDER enabled=${read.invoke(service)}")
        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
            putString("spatial_binder", "enabled=${read.invoke(service)}")
        })
    }
    private fun await(message: String, condition: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 20000
        while (!condition() && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(100)
        assertTrue(message, condition())
    }

    @Test fun approvedLeaseDisablesAndRestoresWhenStopped() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifySpatialLease") == "true")
        val i = InstrumentationRegistry.getInstrumentation()
        val context = i.targetContext
        await("Spatial audio must initially be enabled") { systemSpatialAudioEnabled(context) == true }
        lateinit var lease: SpatialAudioLease
        i.runOnMainSync { lease = SpatialAudioLease(context) {} }
        try {
            i.runOnMainSync { lease.start() }
            await("Spatial audio lease did not become active: ${lease.state}") { lease.state == SpatialLeaseState.Active }
            assertEquals(false, systemSpatialAudioEnabled(context))
            i.runOnMainSync { lease.stop() }
            await("Spatial audio was not restored") { lease.state == SpatialLeaseState.Idle && systemSpatialAudioEnabled(context) == true }
        } finally {
            i.runOnMainSync { lease.stop() }
            await("Spatial audio cleanup failed") { systemSpatialAudioEnabled(context) == true }
        }
    }

    @Test fun holdLeaseForExternalProcessLossCheck() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifySpatialCrash") == "true")
        val i = InstrumentationRegistry.getInstrumentation()
        val context = i.targetContext
        await("Spatial audio must initially be enabled") { systemSpatialAudioEnabled(context) == true }
        lateinit var lease: SpatialAudioLease
        i.runOnMainSync { lease = SpatialAudioLease(context) {} }
        try {
            i.runOnMainSync { lease.start() }
            await("Lease did not start") { lease.state == SpatialLeaseState.Active }
            println("SPATIAL_LEASE_READY")
            Thread.sleep(60000)
        } finally {
            i.runOnMainSync { lease.stop() }
        }
    }

    @Test fun approvedRoutingSessionUsesStereoAndRestoresOnDisable() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifySpatialRouting") == "true")
        val i = InstrumentationRegistry.getInstrumentation()
        val context = i.targetContext
        await("Spatial audio must initially be enabled for this check") { systemSpatialAudioEnabled(context) == true }
        val originallyEnabled = AudioFreedomService.isRequestedEnabled(context)
        val original = AudioFreedomSettingsStore.load(context)
        val audio = context.getSystemService(android.media.AudioManager::class.java)
        try {
            i.runOnMainSync {
                AudioFreedomService.setEnabled(context, true)
                AudioFreedomService.updateSettings(context, original)
            }
            await("AudioFreedom service did not attach") { AudioFreedomService.currentState == DriverState.Attached }
            await("No live PCM before consent") {
                AudioFreedomService.currentStreamFrame?.isFresh(android.os.SystemClock.elapsedRealtime()) == true
            }
            await("Recovery did not finish") {
                AudioFreedomService.currentSpatialAudio !in setOf(SpatialLeaseState.Recovering, SpatialLeaseState.Restoring)
            }
            assertTrue("Music must be playing during the speaker-path check", audio.isMusicActive)
            val routed = original.copy(stereoRoutingEnabled = true, stereoRoutingMode = StereoRoutingMode.Mono)
            i.runOnMainSync {
                AudioFreedomService.updateSettings(context, routed)
                AudioFreedomService.allowSpatialStereo(context, AudioRouteDetector.current(context).id)
            }
            await("Approved routing did not acquire spatial control: ${AudioFreedomService.currentSpatialAudio}") {
                AudioFreedomService.currentSpatialAudio == SpatialLeaseState.Active
            }
            assertEquals(false, systemSpatialAudioEnabled(context))
            await("Speaker did not become live stereo") {
                audio.isMusicActive && AudioFreedomService.currentSpatialAudio == SpatialLeaseState.Active &&
                    AudioFreedomService.currentStreamFrame?.takeIf {
                        it.isFresh(android.os.SystemClock.elapsedRealtime())
                    }?.value?.channels == 2
            }
            i.runOnMainSync { AudioFreedomService.updateSettings(context, routed.copy(stereoRoutingEnabled = false)) }
            await("Disabling routing did not restore spatial audio") { systemSpatialAudioEnabled(context) == true }
            await("Routing lease did not finish") { AudioFreedomService.currentSpatialAudio == SpatialLeaseState.Idle }
            i.runOnMainSync {
                AudioFreedomService.updateSettings(context, routed)
                AudioFreedomService.allowSpatialStereo(context, AudioRouteDetector.current(context).id)
            }
            await("Second routing lease did not start") {
                AudioFreedomService.currentSpatialAudio == SpatialLeaseState.Active
            }
            // Simulate an approval belonging to another output without changing paired devices.
            i.runOnMainSync { AudioFreedomService.allowSpatialStereo(context, "test:other-output") }
            await("An output mismatch did not restore spatial audio") {
                AudioFreedomService.currentSpatialAudio == SpatialLeaseState.Idle && systemSpatialAudioEnabled(context) == true
            }
            i.runOnMainSync { AudioFreedomService.allowSpatialStereo(context, AudioRouteDetector.current(context).id) }
            await("Final routing lease did not start") {
                AudioFreedomService.currentSpatialAudio == SpatialLeaseState.Active
            }
            i.runOnMainSync { AudioFreedomService.setEnabled(context, false) }
            await("Stopping processing did not restore spatial audio") { systemSpatialAudioEnabled(context) == true }
        } finally {
            i.runOnMainSync {
                AudioFreedomService.restoreSpatialAudio(context)
                AudioFreedomService.updateSettings(context, original)
                if (!originallyEnabled) AudioFreedomService.setEnabled(context, false)
            }
            await("Routing test cleanup failed") { systemSpatialAudioEnabled(context) == true }
        }
    }
}
