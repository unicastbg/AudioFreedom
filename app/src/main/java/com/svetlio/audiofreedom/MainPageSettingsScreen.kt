package com.svetlio.audiofreedom

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun MainPageSettingsScreen(
    preferences: AppPreferences,
    onPreferencesChanged: (AppPreferences) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Text(
            "Hidden effects are disabled, including when you load a profile or use voice commands. " +
                "Their slider values stay saved. Showing an effect again leaves it off.",
            modifier = Modifier.padding(vertical = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MainPageFunction.entries.forEach { function ->
            val visible = preferences.isVisible(function)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1F).padding(end = 12.dp)) {
                    Text(function.label, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (visible) "Shown on main page"
                        else if (function.isEffect) "Hidden and disabled" else "Hidden",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = visible,
                    onCheckedChange = { onPreferencesChanged(preferences.withVisibility(function, it)) },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        Spacer(Modifier.height(24.dp))
    }
}
