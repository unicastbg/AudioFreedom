package com.svetlio.audiofreedom

import android.content.Context
import android.content.pm.PackageManager
import android.content.ComponentName
import android.os.Bundle
import android.os.SystemClock
import android.media.AudioManager
import android.media.AudioFocusRequest
import android.media.AudioAttributes
import androidx.test.core.app.ActivityScenario
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Run once without Media Player and once with a same-signed bridge-capable APK installed. */
@RunWith(AndroidJUnit4::class)
class MusicBridgeIntegrationTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun missingPlayerIsReportedExplicitly() = runBlocking {
        assumeFalse(MusicBridge.isInstalled(context))
        assertEquals(MusicBridgeResult.Unavailable, MusicBridge.send(context, "play trance"))
    }

    @Test fun testApksHaveMatchingSignatures() {
        assumeTrue(MusicBridge.isInstalled(context))
        assertEquals(PackageManager.SIGNATURE_MATCH,
            context.packageManager.checkSignatures(context.packageName, MusicBridge.PLAYER_PACKAGE))
    }

    @Test fun coldServiceAcceptsTransportCommands() = runBlocking {
        assumeTrue(MusicBridge.isInstalled(context))
        val result = MusicBridge.send(context, "pause music")
        assertEquals(MusicBridgeResult.Handled("Paused"), result)
    }

    @Test fun namesContainingDspWordsAreHandledByPlayer() = runBlocking {
        assumeTrue(MusicBridge.isInstalled(context))
        for (command in listOf("play artist Muddy Waters", "play song Surround", "play The Prodigy")) {
            assertTrue(command, MusicBridge.send(context, command) is MusicBridgeResult.Handled)
        }
    }

    @Test fun unsupportedCommandIsNotConfusedWithAnUnavailablePlayer() = runBlocking {
        assumeTrue(MusicBridge.isInstalled(context))
        assertEquals(MusicBridgeResult.Unsupported, MusicBridge.send(context, "unrecognized request"))
    }

    @Test fun cancellationDoesNotPoisonTheNextConnection() = runBlocking {
        assumeTrue(MusicBridge.isInstalled(context))
        repeat(5) {
            val job = launch(Dispatchers.Default) { MusicBridge.send(context, "pause music") }
            delay(1)
            job.cancelAndJoin()
        }
        assertEquals(MusicBridgeResult.Handled("Paused"), MusicBridge.send(context, "pause music"))
    }

    @Test fun disabledForwardingDoesNotChangeDspSettings() = runBlocking {
        val before = AudioFreedomSettingsStore.load(context)
        withContext(Dispatchers.Main) {
            val audioSession = VoiceCommandAudioSession(context)
            try {
                val result = MusicBridge.handleIfMusicCommand(context, "play artist Muddy Waters", audioSession,
                    enabled = false, speakReply = false)
                assertTrue(result!!.contains("turned off"))
                assertEquals(before, AudioFreedomSettingsStore.load(context))
            } finally {
                audioSession.shutdown()
            }
        }
    }

    @Test fun expiredRequestIsRejectedByTheRealReceiver() = runBlocking {
        assumeTrue(MusicBridge.isInstalled(context))
        withController { controller ->
            val result = controller.sendCustomCommand(SessionCommand(CommandId, Bundle.EMPTY),
                request("pause music", UUID.randomUUID().toString(), SystemClock.elapsedRealtime() - 1)).await()
            assertEquals("expired", result.extras.getString("error"))
            assertNotEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
        }
    }

    @Test fun duplicateIdCannotExecuteTwiceOnTheRealReceiver() = runBlocking {
        assumeTrue(MusicBridge.isInstalled(context))
        withController { controller ->
            val args = request("pause music", UUID.randomUUID().toString(), SystemClock.elapsedRealtime() + 5000)
            val command = SessionCommand(CommandId, Bundle.EMPTY)
            val first = controller.sendCustomCommand(command, args).await()
            assertEquals(SessionResult.RESULT_SUCCESS, first.resultCode)
            val duplicate = controller.sendCustomCommand(command, args).await()
            assertEquals("duplicate", duplicate.extras.getString("error"))
        }
    }

    @Test fun missingProtocolCannotExecuteOnTheRealReceiver() = runBlocking {
        assumeTrue(MusicBridge.isInstalled(context))
        withController { controller ->
            val args = Bundle().apply { putString("text", "play trance") }
            val result = controller.sendCustomCommand(SessionCommand(CommandId, Bundle.EMPTY), args).await()
            assertEquals("invalid", result.extras.getString("error"))
        }
    }

    @Test fun forwardingReturnsExclusiveFocusButKeepsVoiceOwnershipUntilCompletion() = runBlocking {
        assumeTrue(MusicBridge.isInstalled(context))
        ActivityScenario.launch(MainActivity::class.java).use {
            withContext(Dispatchers.Main) {
                val manager = context.getSystemService(AudioManager::class.java)
                val regained = CompletableDeferred<Unit>()
                val referenceFocus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setOnAudioFocusChangeListener { change ->
                        if (change == AudioManager.AUDIOFOCUS_GAIN) regained.complete(Unit)
                    }.build()
                assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, manager.requestAudioFocus(referenceFocus))
                val audioSession = VoiceCommandAudioSession(context)
                val before = VoiceCaptureOwnership.manualSessions.get()
                try {
                    assertTrue(audioSession.requestPlaybackPause())
                    assertEquals(before + 1, VoiceCaptureOwnership.manualSessions.get())
                    val result = MusicBridge.handleIfMusicCommand(context, "pause music", audioSession,
                        enabled = true, speakReply = false)
                    assertEquals("Paused", result)
                    withTimeout(5000) { regained.await() }
                    assertEquals(before + 1, VoiceCaptureOwnership.manualSessions.get())
                } finally {
                    audioSession.shutdown()
                    manager.abandonAudioFocusRequest(referenceFocus)
                }
                assertEquals(before, VoiceCaptureOwnership.manualSessions.get())
            }
        }
    }

    private fun request(text: String, id: String, deadline: Long) = Bundle().apply {
        putString("text", text)
        putInt("protocol_version", 1)
        putString("request_id", id)
        putLong("deadline_elapsed_ms", deadline)
    }

    private suspend fun withController(action: suspend (MediaController) -> Unit) = withContext(Dispatchers.Main) {
        val token = SessionToken(context, ComponentName(MusicBridge.PLAYER_PACKAGE,
            "com.svetlio.mediaplayer.playback.PlaybackService"))
        val future = MediaController.Builder(context, token).buildAsync()
        try { action(future.await()) } finally { MediaController.releaseFuture(future) }
    }

    private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addListener({
            if (continuation.isActive) runCatching { get() }
                .onSuccess { continuation.resume(it) }.onFailure { continuation.resumeWithException(it) }
        }, ContextCompat.getMainExecutor(context))
        continuation.invokeOnCancellation { cancel(false) }
    }

    private companion object { const val CommandId = "com.svetlio.mediaplayer.VOICE_COMMAND" }
}
