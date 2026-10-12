package com.svetlio.audiofreedom

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One-way transcript handoff. Only Folder Player interprets music library names. */
internal object MusicBridge {
    const val PLAYER_PACKAGE = "com.svetlio.mediaplayer"
    private const val PLAYER_SERVICE = "com.svetlio.mediaplayer.playback.PlaybackService"
    private const val COMMAND_ID = "com.svetlio.mediaplayer.VOICE_COMMAND"
    private const val CANCEL_COMMAND_ID = "com.svetlio.mediaplayer.CANCEL_VOICE_COMMAND"
    private const val PROTOCOL_VERSION = 1
    private const val KEY_PROTOCOL = "protocol_version"
    private const val KEY_REQUEST_ID = "request_id"
    private const val KEY_DEADLINE = "deadline_elapsed_ms"
    private const val KEY_ERROR = "error"
    private const val KEY_TEXT = "text"
    private const val KEY_HANDLED = "handled"
    private const val KEY_REPLY = "reply"
    private const val DEADLINE_MS = 5_000L
    private const val RESPONSE_TIMEOUT_MS = 6_000L

    fun isInstalled(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage(PLAYER_PACKAGE) != null

    private fun isTrusted(context: Context): Boolean =
        context.packageManager.checkSignatures(context.packageName, PLAYER_PACKAGE) ==
            PackageManager.SIGNATURE_MATCH

    /** Returns null only for a non-music command, which may continue to the DSP planner. */
    suspend fun handleIfMusicCommand(
        context: Context,
        transcript: String,
        audioSession: VoiceCommandAudioSession,
        enabled: Boolean,
        speakReply: Boolean,
    ): String? {
        if (!MusicCommandRouting.isMusicCommand(transcript)) return null
        // Return focus without releasing the manual voice-session ownership guard.
        audioSession.releasePlaybackFocus()
        val message = if (enabled) send(context, MusicCommandRouting.transcript(transcript)).message()
            else "Music command forwarding is turned off in AudioFreedom settings."
        if (!speakReply) return message
        return message + when (audioSession.speakMusicReply(message)) {
            SpokenConfirmationResult.Spoken -> ""
            SpokenConfirmationResult.NoSpeechEngine -> " No Android text-to-speech engine is installed or enabled."
            SpokenConfirmationResult.NoOfflineVoice -> " No offline English speech voice is installed."
            SpokenConfirmationResult.Failed -> " Android could not play the spoken reply."
        }
    }

    suspend fun send(context: Context, transcript: String): MusicBridgeResult {
        if (!isInstalled(context)) return MusicBridgeResult.Unavailable
        if (!isTrusted(context)) return MusicBridgeResult.Untrusted
        val requestId = UUID.randomUUID().toString()
        val deadline = SystemClock.elapsedRealtime() + DEADLINE_MS
        return withTimeoutOrNull(RESPONSE_TIMEOUT_MS) {
            withContext(Dispatchers.Main) {
                var controllerFuture: ListenableFuture<MediaController>? = null
                var controller: MediaController? = null
                var commandSent = false
                var replyReceived = false
                try {
                    val token = SessionToken(context, ComponentName(PLAYER_PACKAGE, PLAYER_SERVICE))
                    val future = MediaController.Builder(context, token).buildAsync()
                    controllerFuture = future
                    val connected = future.awaitOnMain(context)
                    controller = connected
                    val offered = connected.availableSessionCommands.commands.firstOrNull {
                        it.customAction == COMMAND_ID
                    } ?: return@withContext MusicBridgeResult.NeedsPlayerUpdate
                    if (offered.customExtras.getInt(KEY_PROTOCOL) != PROTOCOL_VERSION) {
                        return@withContext MusicBridgeResult.NeedsPlayerUpdate
                    }
                    if (SystemClock.elapsedRealtime() >= deadline) return@withContext MusicBridgeResult.Expired
                    val args = Bundle().apply {
                        putString(KEY_TEXT, transcript)
                        putInt(KEY_PROTOCOL, PROTOCOL_VERSION)
                        putString(KEY_REQUEST_ID, requestId)
                        putLong(KEY_DEADLINE, deadline)
                    }
                    commandSent = true
                    val result = connected.sendCustomCommand(SessionCommand(COMMAND_ID, Bundle.EMPTY), args)
                        .awaitOnMain(context)
                    replyReceived = true
                    val extras = result.extras
                    if (extras.getString(KEY_REQUEST_ID) != requestId) return@withContext MusicBridgeResult.Failed
                    when {
                        extras.getString(KEY_ERROR) == "expired" -> MusicBridgeResult.Expired
                        extras.getString(KEY_ERROR) == "duplicate" -> MusicBridgeResult.Duplicate
                        result.resultCode != SessionResult.RESULT_SUCCESS -> MusicBridgeResult.Failed
                        !extras.getBoolean(KEY_HANDLED) -> MusicBridgeResult.Unsupported
                        else -> MusicBridgeResult.Handled(extras.getString(KEY_REPLY).orEmpty())
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (commandSent) MusicBridgeResult.TimedOut else MusicBridgeResult.Unavailable
                } finally {
                    if (commandSent && !replyReceived) {
                        controller?.let { connected ->
                            runCatching {
                                connected.sendCustomCommand(SessionCommand(CANCEL_COMMAND_ID, Bundle.EMPTY),
                                    Bundle().apply { putString(KEY_REQUEST_ID, requestId) })
                            }
                        }
                    }
                    // Cancellation may win after construction but before assignment.
                    controllerFuture?.let { MediaController.releaseFuture(it) }
                }
            }
        } ?: MusicBridgeResult.TimedOut
    }

    private suspend fun <T> ListenableFuture<T>.awaitOnMain(context: Context): T =
        suspendCancellableCoroutine { continuation ->
            addListener({
                if (continuation.isActive) {
                    runCatching { get() }
                        .onSuccess { continuation.resume(it) }
                        .onFailure { continuation.resumeWithException(it) }
                }
            }, ContextCompat.getMainExecutor(context))
            continuation.invokeOnCancellation { cancel(false) }
        }
}
