package com.svetlio.audiofreedom

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
internal fun AssistantSpeechSettings(
    preferences: AppPreferences,
    onPreferencesChanged: (AppPreferences) -> Unit,
    testing: Boolean,
    testMessage: String?,
    onTest: (AssistantLanguage) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val language = AssistantLanguage.English
    val code = "en"
    val country = ""
    val preferred = preferences.assistantEnglishSpeechVoice
    var voices by remember { mutableStateOf<List<OfflineSpeechVoice>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(refresh) {
        loading = true
        if (!hasInstalledSpeechEngine(context)) {
            voices = emptyList()
            message = "No Android text-to-speech engine is installed or enabled. Install an offline-capable engine in Android speech settings."
            loading = false
            return@LaunchedEffect
        }
        val session = VoiceCommandAudioSession(context.applicationContext)
        try {
            val available = session.availableSpeechVoices()
            voices = available.orEmpty()
            message = if (available == null) "Android could not initialize the speech engine" else null
        } finally {
            session.shutdown()
            loading = false
        }
    }
    val available = OfflineSpeechVoicePolicy.available(voices, code, country)
    val selected = OfflineSpeechVoicePolicy.choose(voices, code, country, preferred)
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text("Spoken replies (English)", style = MaterialTheme.typography.titleSmall)
        Box(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { menuExpanded = true }, enabled = !loading && !testing) {
                Text(if (preferred.isEmpty()) "Automatic voice" else "Selected voice")
                Icon(Icons.Rounded.ExpandMore, contentDescription = null)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false },
                modifier = Modifier.widthIn(max = 360.dp)) {
                fun select(id: String) {
                    onPreferencesChanged(preferences.copy(assistantEnglishSpeechVoice = id))
                    menuExpanded = false
                }
                DropdownMenuItem(text = { Text("Automatic (best available offline voice)") }, onClick = { select("") })
                available.forEach { voice ->
                    DropdownMenuItem(text = {
                        Text(voice.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }, onClick = { select(voice.id) })
                }
            }
        }
        Text(when {
            loading -> "Loading offline voices..."
            selected == null -> "No installed offline ${language.label} voice. Check Android speech settings."
            preferred.isNotEmpty() && selected.id != preferred -> "Saved voice unavailable. Using ${selected.name}."
            else -> "Voice: ${selected.name}"
        }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (available.size == 1) {
            Text("Only one offline ${language.label} voice is available in this engine.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onTest(language) }, modifier = Modifier.weight(1F),
                enabled = !loading && !testing && selected != null) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Text(if (testing) "Speaking..." else "Test voice")
            }
            TextButton(onClick = { refresh++ }, modifier = Modifier.weight(1F), enabled = !loading && !testing) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Text("Refresh voices")
            }
        }
        TextButton(onClick = {
            val opened = runCatching { context.startActivity(Intent("com.android.settings.TTS_SETTINGS")) }.isSuccess
            if (!opened) {
                val fallback = runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }.isSuccess
                message = if (fallback) "Search Android settings for Text-to-speech output"
                    else "Android speech settings could not be opened"
            }
        }, enabled = !testing) {
            Icon(Icons.Rounded.Settings, contentDescription = null)
            Text("Android speech settings")
        }
        (message ?: testMessage)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
