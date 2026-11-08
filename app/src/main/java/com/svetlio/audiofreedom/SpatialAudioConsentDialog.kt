package com.svetlio.audiofreedom

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

@Composable
internal fun SpatialAudioConsentDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(onDismissRequest = onCancel,
        title = { Text("Use stereo routing?") },
        text = { Text("System spatial audio must be turned off for this playback path. This affects other apps too. " +
            "AudioFreedom will restore the previous setting when routing stops, processing stops, or the output changes. " +
            "Root access is required. Your routing setting will stay on if you cancel, but this path will remain bypassed.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Turn off spatial audio") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } })
}

@Composable
internal fun SpatialAudioRoutingStatus(state: SpatialLeaseState, enabled: Boolean?, blocked: Boolean,
                                      onRequest: () -> Unit, onRestore: () -> Unit) {
    val message = when (state) {
        SpatialLeaseState.Starting -> "Preparing stereo playback"
        SpatialLeaseState.Active -> "System spatial audio temporarily off"
        SpatialLeaseState.Restoring, SpatialLeaseState.Recovering -> "Restoring system spatial audio"
        SpatialLeaseState.Overridden -> "System spatial audio changed outside AudioFreedom"
        SpatialLeaseState.Error -> "Spatial audio control failed. Check root access; any pending restoration will be retried when processing restarts."
        SpatialLeaseState.RestoreFailed -> "System spatial audio could not be restored. Restore it below or in the phone's sound settings."
        SpatialLeaseState.Unsupported -> "No stereo playback was detected. System spatial audio has been restored."
        SpatialLeaseState.Idle -> null
    }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    if (state == SpatialLeaseState.Error || state == SpatialLeaseState.RestoreFailed)
        TextButton(onClick = onRestore) { Text("Restore spatial audio") }
    if (blocked && enabled == true && state in setOf(SpatialLeaseState.Idle, SpatialLeaseState.Error,
            SpatialLeaseState.Overridden, SpatialLeaseState.Unsupported)) {
        TextButton(onClick = onRequest) { Text("Use stereo playback") }
    }
}
