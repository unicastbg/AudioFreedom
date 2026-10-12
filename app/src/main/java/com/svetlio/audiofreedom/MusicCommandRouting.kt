package com.svetlio.audiofreedom

import java.util.Locale

internal object MusicCommandRouting {
    private val transport = setOf(
        "play", "resume", "continue", "unpause", "pause", "pause music", "pause playback",
        "stop music", "stop playing", "stop playback", "next", "skip", "next track",
        "next song", "previous", "back", "previous track", "previous song", "go back",
    )
    private val playbackPrefixes = listOf("play ", "shuffle ", "put on ", "start ")

    fun isMusicCommand(command: String): Boolean {
        val text = transcript(command).lowercase(Locale.ROOT)
        // Bare "stop" remains assistant cancellation, never a playback request.
        return text in transport || playbackPrefixes.any(text::startsWith)
    }

    fun transcript(command: String): String = command.trim().trimEnd('.', '!', '?')
        .replace(Regex("(?i)^please\\s+|\\s+please$"), "").trim()
}

internal sealed interface MusicBridgeResult {
    data class Handled(val reply: String) : MusicBridgeResult
    data object Unsupported : MusicBridgeResult
    data object Unavailable : MusicBridgeResult
    data object Untrusted : MusicBridgeResult
    data object NeedsPlayerUpdate : MusicBridgeResult
    data object Expired : MusicBridgeResult
    data object Duplicate : MusicBridgeResult
    data object TimedOut : MusicBridgeResult
    data object Failed : MusicBridgeResult
}

internal fun MusicBridgeResult.message(): String = when (this) {
    is MusicBridgeResult.Handled -> reply.ifBlank { "Music command completed." }
    MusicBridgeResult.Unsupported -> "Folder Player does not support that music command."
    MusicBridgeResult.Unavailable -> "Folder Player is unavailable. Install or enable it and try again."
    MusicBridgeResult.Untrusted -> "AudioFreedom and Folder Player must use the same trusted signing certificate."
    MusicBridgeResult.NeedsPlayerUpdate -> "Update Folder Player to use the music command bridge."
    MusicBridgeResult.Expired -> "The music request expired before execution. No playback change was made."
    MusicBridgeResult.Duplicate -> "That music request was already submitted. Check playback before trying again."
    MusicBridgeResult.TimedOut -> "Folder Player did not confirm the request in time. Check playback before trying again."
    MusicBridgeResult.Failed -> "Folder Player could not complete the music request."
}
