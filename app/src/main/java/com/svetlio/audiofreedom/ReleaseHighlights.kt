package com.svetlio.audiofreedom

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

internal val VersionOneHighlights = listOf(
    "LOS 24 compatibility: automatic recovery when Android suspends global audio processing.",
    "Searchable profiles grouped by device, with optional automatic defaults.",
    "Live DSP format information and clearer driver-update status.",
)

internal val VersionElevenHighlights = listOf(
    "Per-band EQ bandwidth and stereo routing, with optional temporary system spatial-audio control.",
    "A profile manager with multi-select group moves, rename, duplicate and clearer saving.",
    "DSP edits survive screen rotation; routing now explains when a playback path is bypassed.",
)

internal fun installedReleaseHighlights(version: String): List<String> = when (version) {
    "1.0.0" -> VersionOneHighlights
    "1.1.0" -> VersionElevenHighlights
    else -> emptyList()
}

@Composable
internal fun InstalledReleaseNotesDialog(onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("What's new in ${BuildConfig.VERSION_NAME}") },
        text = { ReleaseHighlightList(installedReleaseHighlights(BuildConfig.VERSION_NAME)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
        dismissButton = { TextButton(onClick = {
            uri.openUri("https://github.com/unicastbg/AudioFreedom/releases/tag/v${BuildConfig.VERSION_NAME}")
        }) { Text("View release") } })
}

@Composable
internal fun ReleaseHighlightList(highlights: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        highlights.forEach { highlight ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("\u2022")
                Text(highlight, modifier = Modifier.weight(1F))
            }
        }
    }
}

@Composable
internal fun ReleaseHighlightsNotice(): Boolean {
    if (installedReleaseHighlights(BuildConfig.VERSION_NAME).isEmpty()) return false
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("release_highlights", Context.MODE_PRIVATE) }
    var visible by remember { mutableStateOf(preferences.getInt("seen_version", 0) < BuildConfig.VERSION_CODE) }
    if (visible) InstalledReleaseNotesDialog(onDismiss = {
            preferences.edit().putInt("seen_version", BuildConfig.VERSION_CODE).apply()
            visible = false
        })
    return visible
}
