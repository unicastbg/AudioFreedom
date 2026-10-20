package com.svetlio.audiofreedom

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ThemeGalleryScreen(
    preferences: AppPreferences,
    onPreferencesChanged: (AppPreferences) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("theme-mode-controls"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Mode", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemePreference.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = preferences.theme == mode,
                        onClick = { onPreferencesChanged(preferences.copy(theme = mode)) },
                        enabled = preferences.appTheme.modes == ThemeModes.Both,
                        shape = SegmentedButtonDefaults.itemShape(index, ThemePreference.entries.size),
                    ) { Text(mode.label) }
                }
            }
            Text(when (preferences.appTheme.modes) {
                ThemeModes.Both -> preferences.appTheme.label
                ThemeModes.DarkOnly -> "${preferences.appTheme.label} is always dark"
                ThemeModes.LightOnly -> "${preferences.appTheme.label} is always light"
            }, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(148.dp),
            modifier = Modifier.weight(1F).fillMaxWidth().testTag("theme-gallery"),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(AppTheme.entries, key = { it.name }) { theme ->
                val selected = theme == preferences.appTheme
                Surface(shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(if (selected) 2.dp else 1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                    Box {
                        Column {
                            ThemeThumbnail(theme, preferences.theme)
                            Row(Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(theme.label, Modifier.weight(1F), style = MaterialTheme.typography.labelLarge)
                                if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Box(Modifier.matchParentSize().selectable(selected = selected, role = Role.RadioButton,
                            onClick = { onPreferencesChanged(preferences.copy(appTheme = theme)) })
                            .semantics { contentDescription = "Apply ${theme.label} theme" })
                    }
                }
            }
        }
    }
}

// Render real theme components at a fixed phone size, then scale the read-only preview.
@Composable
private fun ThemeThumbnail(theme: AppTheme, mode: ThemePreference) {
    Layout(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
        .clearAndSetSemantics { }, content = {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            AudioFreedomTheme(mode, theme, controlSystemBars = false) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground) {
                    ThemePreviewContent(theme)
                }
            }
        }
    }) { children, constraints ->
        val sourceWidth = 360.dp.roundToPx()
        val sourceHeight = 720.dp.roundToPx()
        val preview = children.single().measure(Constraints.fixed(sourceWidth, sourceHeight))
        val scale = constraints.maxWidth / sourceWidth.toFloat()
        layout(constraints.maxWidth, (sourceHeight * scale).roundToInt()) {
            preview.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0F, 0F)
            }
        }
    }
}

@Composable
private fun ThemePreviewContent(theme: AppTheme) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .verticalScroll(rememberScrollState(), enabled = false).padding(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("AudioFreedom", Modifier.weight(1F), style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            Icon(Icons.Rounded.Mic, null, modifier = Modifier.padding(end = 12.dp))
            Icon(Icons.Rounded.Settings, null)
        }
        if (theme == AppTheme.Classic) {
            Text("Driver attached", color = MaterialTheme.colorScheme.primary)
            Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Processing enabled", Modifier.weight(1F))
                Switch(true, {})
            }
        } else {
            ProcessingHeroCard(DriverState.Attached, RootAccessState.NotChecked, true, true,
                "Headphones", 4, 7, -650, 0,
                listOf(-4500, -3000, -2000, -2800, -3200, -3800, -2900, -4000, -4800, -5500),
                true, {}, outputRmsMillibels = -2000)
        }
        ProfileChipsRow(listOf(AudioFreedomProfile("preview", "Headphones", AudioFreedomSettings())),
            "preview", false, {}, {}, {}, {})
        EffectGroup {
            for ((title, summary) in listOf("Equalizer" to "Custom - 10 bands", "Bass foundation" to "Deep bass",
                "Immersive field" to "Wide stage", "Detail recovery" to "Clear transients")) {
                ModernEffectSection(title, summary, false, {}, true, {}, null, {})
            }
        }
    }
}
