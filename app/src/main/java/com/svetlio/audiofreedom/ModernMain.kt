package com.svetlio.audiofreedom

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

// The redesigned main screen used by the Mocha, Daylight and Studio themes.
// Classic never reaches anything in this file. Colours, corner shapes and fonts come
// from the theme; LocalLayoutStyle only decides how pieces are arranged.

/** Width of one effect tile when the Tiles style lays effects out in two columns. */
internal val LocalTileWidth = staticCompositionLocalOf { Dp.Unspecified }


// ------------------------------------------------------------------ Processing card

@Composable
internal fun ProcessingHeroCard(
    driverState: DriverState,
    rootAccessState: RootAccessState,
    processing: Boolean,
    driverAvailable: Boolean,
    routeLabel: String,
    activeEffects: Int,
    totalEffects: Int,
    outputPeakMillibels: Int?,
    gainReductionMillibels: Int?,
    spectrumMillibels: List<Int>?,
    spectrumSupported: Boolean?,
    onProcessingChange: (Boolean) -> Unit,
    outputRmsMillibels: Int? = null,
) {
    val style = LocalLayoutStyle.current
    val look = LocalThemeLook.current
    val colors = MaterialTheme.colorScheme
    val rack = style == LayoutStyle.Rack
    val (statusLabel, statusOk) = when (driverState) {
        DriverState.Attached -> "Driver attached" to true
        DriverState.Detected -> "Driver detected" to true
        DriverState.ParameterError -> "DSP control unavailable" to false
        DriverState.NotInstalled -> "Driver not installed" to false
        DriverState.ProbeFailed -> "Driver check failed" to false
    }
    val rootLine = when (rootAccessState) {
        RootAccessState.Checking -> "Checking root access"
        RootAccessState.Available -> "Root access available"
        RootAccessState.Unavailable -> "Root denied or unavailable"
        RootAccessState.NotChecked -> null
    }
    if (look.meter == MeterLook.Lcd) {
        CassetteHero(
            statusLabel = statusLabel,
            statusOk = statusOk,
            rootLine = rootLine,
            processing = processing,
            driverAvailable = driverAvailable,
            routeLabel = routeLabel,
            activeEffects = activeEffects,
            totalEffects = totalEffects,
            peakMillibels = outputPeakMillibels,
            spectrumMillibels = spectrumMillibels,
            onProcessingChange = onProcessingChange,
        )
        return
    }
    val okColor = if (style == LayoutStyle.Cards) colors.primary else colors.secondary
    val statusColor = if (statusOk) okColor else colors.error
    // The card layout fills the processing card with the accent's container colour;
    // flat, glowing and lamp-lit looks use a plain panel (or nothing) instead.
    val tintedCard = style == LayoutStyle.Cards && !look.flatHero && !look.glow && !look.lamps
    val subtleText = if (tintedCard) colors.onPrimaryContainer else colors.onSurfaceVariant

    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = if (rack) MaterialTheme.shapes.large else MaterialTheme.shapes.extraLarge,
        color = when {
            look.flatHero -> Color.Transparent
            tintedCard -> colors.primaryContainer
            style == LayoutStyle.Rack -> colors.surfaceContainer
            else -> colors.surfaceContainerLow
        },
        border = if (tintedCard || look.flatHero) null else BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(
            Modifier.padding(if (look.flatHero) 4.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1F), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(statusColor, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (rack) statusLabel.uppercase(Locale.US) else statusLabel,
                            color = statusColor,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Text(
                        when {
                            rack && processing -> "ENGAGED"
                            rack -> "BYPASSED"
                            processing -> "Processing on"
                            else -> "Processing off"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = if (rack) 1.5.sp else 0.sp,
                    )
                    Text(
                        "$routeLabel · $activeEffects of $totalEffects effects on",
                        color = subtleText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    when (rootAccessState) {
                        RootAccessState.Checking -> Text(
                            "Checking root access",
                            color = subtleText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        RootAccessState.Available -> Text(
                            "Root access available",
                            color = subtleText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        RootAccessState.Unavailable -> Text(
                            "Root denied or unavailable",
                            color = colors.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        RootAccessState.NotChecked -> Unit
                    }
                }
                Spacer(Modifier.width(12.dp))
                PowerButton(on = processing, available = driverAvailable, onChange = onProcessingChange)
            }

            when (look.meter) {
                MeterLook.Trace -> TraceScope(spectrumMillibels, processing)
                MeterLook.Glow -> GlowSpectrum(spectrumMillibels, processing)
                MeterLook.Vu -> VuMeter(
                    rmsMillibels = outputRmsMillibels.takeIf { processing },
                    peakMillibels = outputPeakMillibels.takeIf { processing },
                )
                else -> SpectrumBars(valuesMillibels = spectrumMillibels, active = processing)
            }
            if (spectrumMillibels == null) {
                Text(
                    when {
                        !processing -> "Turn processing on to see the live meters"
                        spectrumSupported == false -> "This driver doesn't report a spectrum"
                        else -> "The meters come alive while audio is playing"
                    },
                    color = subtleText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            // The VU meter has its own PEAK lamp.
            if (look.meter != MeterLook.Vu) {
                PeakMeter(peakMillibels = outputPeakMillibels.takeIf { processing })
            }
            val reduction = gainReductionMillibels?.takeIf { processing && kotlin.math.abs(it) >= 10 }
            if (reduction != null) {
                Text(
                    String.format(
                        Locale.US,
                        "Output protection is limiting by %.1f dB",
                        kotlin.math.abs(reduction) / 100F,
                    ),
                    color = if (rack) colors.primary else colors.tertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun PowerButton(on: Boolean, available: Boolean, onChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val look = LocalThemeLook.current
    val rack = LocalLayoutStyle.current == LayoutStyle.Rack
    val ring = look.ringPower
    val shape = if (rack) MaterialTheme.shapes.medium else CircleShape
    val background = when {
        ring -> Color.Transparent
        !available -> colors.surfaceVariant
        on && rack -> colors.primaryContainer
        on -> colors.primary
        else -> colors.surfaceContainerHighest
    }
    val foreground = when {
        !available -> colors.onSurfaceVariant.copy(alpha = 0.6F)
        on && (rack || ring) -> colors.primary
        on -> colors.onPrimary
        else -> colors.onSurfaceVariant
    }
    val glowing = on && available && (look.glow || look.lamps)
    Box(
        modifier = Modifier
            .size(72.dp)
            .then(
                if (glowing) {
                    Modifier.shadow(16.dp, shape, ambientColor = colors.primary, spotColor = colors.primary)
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(background)
            .then(
                if (rack || ring) Modifier.border(2.dp, if (on) colors.primary else colors.outline, shape)
                else Modifier,
            )
            .toggleable(value = on, enabled = available, role = Role.Switch, onValueChange = onChange)
            .semantics { contentDescription = "Processing" },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.PowerSettingsNew,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(30.dp),
            )
            if (rack) {
                Text(
                    if (on) "ON" else "BYP",
                    color = foreground,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun SpectrumBars(valuesMillibels: List<Int>?, active: Boolean) {
    val colors = MaterialTheme.colorScheme
    val rack = LocalLayoutStyle.current == LayoutStyle.Rack
    val count = EqualizerBandLabels.size
    val levels = (0 until count).map { index ->
        val target = valuesMillibels?.getOrNull(index)
            ?.let { (it + 6000).coerceIn(0, 6000) / 6000F } ?: 0F
        animateFloatAsState(
            targetValue = if (active) target else 0F,
            animationSpec = tween(200),
            label = "spectrum$index",
        ).value
    }
    val bar = colors.primary
    val low = colors.secondary
    val high = colors.error
    val idle = colors.onSurfaceVariant.copy(alpha = 0.16F)
    val style = LocalLayoutStyle.current
    // The driver measures 10 bands. Mocha and Daylight draw more, thinner bars, as in the
    // mockups, with a smooth curve through the measured bands filling the bars in between.
    val barCount = when (style) {
        LayoutStyle.Tiles -> 28
        LayoutStyle.Cards -> 36
        else -> count
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(if (style == LayoutStyle.Cards) 48.dp else 56.dp)
            .semantics { contentDescription = "Output spectrum" },
    ) {
        if (!rack) {
            val gap = 3.dp.toPx()
            val width = (size.width - gap * (barCount - 1)) / barCount
            val minimum = 3.dp.toPx()
            val radius = 2.dp.toPx()
            for (index in 0 until barCount) {
                val level = bandLevelAt(levels, index, barCount)
                val x = index * (width + gap)
                val height = (level * size.height).coerceAtLeast(minimum)
                val top = size.height - height
                val color = when {
                    level <= 0F -> idle
                    // Mocha's bars fade slightly towards the treble end.
                    style == LayoutStyle.Tiles -> bar.copy(alpha = (1F - index * 0.02F).coerceAtLeast(0.45F))
                    else -> bar
                }
                drawRoundRect(color, Offset(x, top), Size(width, height), CornerRadius(radius))
                if (style == LayoutStyle.Cards) {
                    // Daylight: round only the top corners so the bars stand on a flat base.
                    val square = minOf(height, radius)
                    drawRect(color, Offset(x, size.height - square), Size(width, square))
                }
            }
            return@Canvas
        }
        val gap = 4.dp.toPx()
        val width = (size.width - gap * (count - 1)) / count
        levels.forEachIndexed { index, level ->
            val x = index * (width + gap)
            if (rack) {
                val segments = 12
                val segmentGap = 2.dp.toPx()
                val segmentHeight = (size.height - segmentGap * (segments - 1)) / segments
                val lit = (level * segments).roundToInt()
                for (segment in 0 until segments) {
                    val y = size.height - (segment + 1) * segmentHeight - segment * segmentGap
                    val color = when {
                        segment >= lit -> idle
                        segment >= segments - 2 -> high
                        segment >= segments - 4 -> bar
                        else -> low
                    }
                    drawRect(color, Offset(x, y), Size(width, segmentHeight))
                }
            } else {
                val minimum = 3.dp.toPx()
                val height = (level * size.height).coerceAtLeast(minimum)
                drawRoundRect(
                    color = if (level > 0F) bar else idle,
                    topLeft = Offset(x, size.height - height),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
        }
    }
}

@Composable
private fun PeakMeter(peakMillibels: Int?) {
    val colors = MaterialTheme.colorScheme
    val style = LocalLayoutStyle.current
    val rack = style == LayoutStyle.Rack
    val level by animateFloatAsState(
        targetValue = peakMillibels?.let { (it + 4800).coerceIn(0, 4800) / 4800F } ?: 0F,
        animationSpec = tween(150),
        label = "peak",
    )
    val ok = if (style == LayoutStyle.Cards) colors.primary else colors.secondary
    val warn = if (rack) colors.primary else colors.tertiary
    val clip = colors.error
    val idle = colors.onSurfaceVariant.copy(alpha = 0.16F)
    val fill = when {
        peakMillibels == null -> idle
        peakMillibels >= -100 -> clip
        peakMillibels >= -300 -> warn
        else -> ok
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (rack) "OUT" else "Peak",
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(44.dp),
        )
        Canvas(
            Modifier
                .weight(1F)
                .height(if (rack) 12.dp else 8.dp)
                .semantics { contentDescription = "Output peak meter" },
        ) {
            if (rack) {
                val segments = 28
                val gap = 2.dp.toPx()
                val width = (size.width - gap * (segments - 1)) / segments
                val lit = (level * segments).roundToInt()
                for (segment in 0 until segments) {
                    val color = when {
                        segment >= lit -> idle
                        segment >= segments - 3 -> clip
                        segment >= segments - 8 -> warn
                        else -> ok
                    }
                    drawRect(color, Offset(segment * (width + gap), 0F), Size(width, size.height))
                }
            } else {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(idle, size = size, cornerRadius = radius)
                drawRoundRect(fill, size = Size(size.width * level, size.height), cornerRadius = radius)
            }
        }
        Text(
            peakMillibels?.let { String.format(Locale.US, "%.1f dB", it / 100F) } ?: "--",
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.width(68.dp),
        )
    }
}

// ------------------------------------------------------------------ Profiles

@Composable
internal fun ProfileChipsRow(
    profiles: List<AudioFreedomProfile>,
    selectedProfileId: String?,
    profileModified: Boolean,
    onProfileSelected: (AudioFreedomProfile) -> Unit,
    onAdd: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val rack = LocalLayoutStyle.current == LayoutStyle.Rack
    val selected = profiles.firstOrNull { it.id == selectedProfileId }
    var menuExpanded by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (rack) "PROFILE SLOT" else "Profile",
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1F),
            )
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Profile actions")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(selected?.let { "Save changes to ${it.name}" } ?: "Save changes") },
                        enabled = selected != null,
                        onClick = {
                            menuExpanded = false
                            onSave()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Save as new profile…") },
                        onClick = {
                            menuExpanded = false
                            onAdd()
                        },
                    )
                    HorizontalDivider()
                    // Delete lives in the menu, away from Save, so it can't be hit by accident.
                    DropdownMenuItem(
                        text = {
                            Text(
                                selected?.let { "Delete ${it.name}" } ?: "Delete profile",
                                color = if (selected != null) colors.error else Color.Unspecified,
                            )
                        },
                        enabled = selected != null,
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (profiles.isEmpty()) {
                Text(
                    "No saved profiles yet",
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            profiles.forEachIndexed { index, profile ->
                val isSelected = profile.id == selectedProfileId
                ChoiceChip(
                    label = profile.name + if (isSelected && profileModified) " •" else "",
                    slot = if (rack && index < 26) ('A' + index).toString() else null,
                    selected = isSelected,
                    onClick = { onProfileSelected(profile) },
                )
            }
            Box(
                modifier = Modifier
                    .size(if (rack) 52.dp else 40.dp)
                    .clip(if (rack) MaterialTheme.shapes.small else CircleShape)
                    .border(
                        1.dp,
                        colors.outlineVariant,
                        if (rack) MaterialTheme.shapes.small else CircleShape,
                    )
                    .clickable(role = Role.Button, onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = "Save as new profile",
                    tint = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    slot: String? = null,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val rack = LocalLayoutStyle.current == LayoutStyle.Rack
    val shape = if (rack) MaterialTheme.shapes.small else CircleShape
    val background = when {
        !selected -> Color.Transparent
        rack -> colors.primaryContainer
        else -> colors.primary
    }
    val foreground = when {
        !selected -> colors.onSurface
        rack -> colors.onPrimaryContainer
        else -> colors.onPrimary
    }
    val borderColor = when {
        !selected -> colors.outlineVariant
        rack -> colors.primary
        else -> colors.primary
    }
    Box(
        modifier = Modifier
            .heightIn(min = if (slot != null) 52.dp else 40.dp)
            .widthIn(min = if (slot != null) 76.dp else 0.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (slot != null) {
                Text(
                    slot,
                    color = if (selected) colors.primary else colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text(
                if (rack) label.uppercase(Locale.US) else label,
                color = if (enabled) foreground else foreground.copy(alpha = 0.5F),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ------------------------------------------------------------------ Effect sections

/**
 * Level of bar [index] out of [bars], read off a Catmull-Rom curve through the measured
 * bands so the in-between bars follow the music smoothly instead of stepping in blocks.
 */
internal fun bandLevelAt(levels: List<Float>, index: Int, bars: Int): Float {
    if (levels.size < 2 || bars < 2) return levels.firstOrNull() ?: 0F
    val position = index.toFloat() / (bars - 1) * (levels.size - 1)
    val i = position.toInt().coerceIn(0, levels.size - 2)
    val t = position - i
    val p0 = levels[(i - 1).coerceAtLeast(0)]
    val p1 = levels[i]
    val p2 = levels[i + 1]
    val p3 = levels[(i + 2).coerceAtMost(levels.size - 1)]
    val t2 = t * t
    val t3 = t2 * t
    val value = 0.5F * (
        2F * p1 +
            (-p0 + p2) * t +
            (2F * p0 - 5F * p1 + 4F * p2 - p3) * t2 +
            (-p0 + 3F * p1 - 3F * p2 + p3) * t3
        )
    return value.coerceIn(0F, 1F)
}

/** Spacing between effect sections; replaces Classic's divider lines. */
@Composable
internal fun SectionGap() {
    when (LocalLayoutStyle.current) {
        LayoutStyle.Classic -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LayoutStyle.Tiles, LayoutStyle.Keys -> Unit
        LayoutStyle.Cards -> Spacer(Modifier.height(10.dp))
        LayoutStyle.Rack -> Spacer(Modifier.height(6.dp))
    }
}

/** Holds the effect sections: two columns of tiles for Tiles, a plain column otherwise. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EffectGroup(content: @Composable () -> Unit) {
    val style = LocalLayoutStyle.current
    if (style == LayoutStyle.Tiles || style == LayoutStyle.Keys) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            val gap = 10.dp
            // FlowRow rounds spacing upward when deciding whether another item fits.
            val tileWidth = with(LocalDensity.current) {
                val gapPixels = kotlin.math.ceil(gap.toPx()).toInt()
                ((constraints.maxWidth - gapPixels) / 2).coerceAtLeast(0).toDp()
            }
            CompositionLocalProvider(LocalTileWidth provides tileWidth) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    content()
                }
            }
        }
    } else {
        Column(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
internal fun ModernEffectSection(
    title: String,
    summary: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    preview: (@Composable () -> Unit)?,
    content: @Composable () -> Unit,
) {
    when (LocalLayoutStyle.current) {
        LayoutStyle.Tiles -> EffectTile(
            title, summary, expanded, onExpandedChange, enabled, onEnabledChange, preview, content,
        )
        LayoutStyle.Rack -> RackRow(
            title, summary, expanded, onExpandedChange, enabled, onEnabledChange, content,
        )
        LayoutStyle.Keys -> EffectKey(
            title, summary, expanded, onExpandedChange, enabled, onEnabledChange, preview, content,
        )
        else -> EffectCard(
            title, summary, expanded, onExpandedChange, enabled, onEnabledChange, content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EffectTile(
    title: String,
    summary: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    preview: (@Composable () -> Unit)?,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val tileWidth = LocalTileWidth.current
    Surface(
        onClick = { onExpandedChange(true) },
        modifier = if (tileWidth != Dp.Unspecified) Modifier.width(tileWidth) else Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerHighest,
    ) {
        Column(
            Modifier.heightIn(min = 112.dp).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    effectIcon(title),
                    contentDescription = null,
                    tint = if (enabled) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.weight(1F))
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
            Text(
                summary,
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            preview?.invoke()
        }
    }
    if (expanded) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { onExpandedChange(false) },
            sheetState = sheetState,
            containerColor = colors.surfaceContainerLow,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(effectIcon(title), contentDescription = null, tint = colors.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1F)) {
                        Text(title, style = MaterialTheme.typography.titleLarge)
                        Text(
                            summary,
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = onEnabledChange)
                }
                Spacer(Modifier.height(16.dp))
                content()
            }
        }
    }
}

@Composable
private fun EffectCard(
    title: String,
    summary: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val look = LocalThemeLook.current
    val glowing = look.glow && enabled
    val shape = MaterialTheme.shapes.large
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (glowing) {
                    Modifier.shadow(10.dp, shape, ambientColor = colors.secondary, spotColor = colors.secondary)
                } else {
                    Modifier
                },
            ),
        shape = shape,
        color = colors.surfaceContainerLowest,
        border = BorderStroke(
            1.dp,
            if (glowing) colors.secondary.copy(alpha = 0.6F) else colors.outlineVariant,
        ),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!expanded) }
                    .heightIn(min = 72.dp)
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (look.lamps) {
                    PilotLamp(on = enabled)
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(if (enabled) colors.primaryContainer else colors.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            effectIcon(title),
                            contentDescription = null,
                            tint = if (enabled) colors.primary else colors.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1F)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        summary,
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
                IconButton(onClick = { onExpandedChange(!expanded) }) {
                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    )
                }
            }
            if (expanded) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    HorizontalDivider(color = colors.outlineVariant)
                    Spacer(Modifier.height(12.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun RackRow(
    title: String,
    summary: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = if (enabled) colors.surfaceContainer else colors.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!expanded) }
                    .heightIn(min = 60.dp)
                    .padding(start = 14.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (enabled) colors.secondary else colors.surfaceVariant)
                        .border(1.dp, if (enabled) colors.secondary else colors.outline, CircleShape),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    title.uppercase(Locale.US),
                    color = if (enabled) colors.onSurface else colors.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1F),
                )
                Text(
                    summary.uppercase(Locale.US),
                    color = if (enabled) colors.primary else colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 110.dp),
                )
                Spacer(Modifier.width(8.dp))
                RackToggle(on = enabled, title = title, onChange = onEnabledChange)
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (expanded) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                    HorizontalDivider(color = colors.outlineVariant)
                    Spacer(Modifier.height(12.dp))
                    content()
                }
            }
        }
    }
}

/** Studio's IN / BYP switch, styled like a lit hardware button. */
@Composable
private fun RackToggle(on: Boolean, title: String, onChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.extraSmall
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .height(30.dp)
            .widthIn(min = 46.dp)
            .clip(shape)
            .background(if (on) colors.secondary.copy(alpha = 0.14F) else Color.Transparent)
            .border(1.dp, if (on) colors.secondary else colors.outline, shape)
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange)
            .semantics { contentDescription = title }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (on) "IN" else "BYP",
            color = if (on) colors.secondary else colors.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

private fun effectIcon(title: String): ImageVector = when (title) {
    "Equalizer" -> Icons.Rounded.Equalizer
    "Immersive field" -> Icons.Rounded.SurroundSound
    "Reverb" -> Icons.Rounded.Waves
    "Volume leveler" -> Icons.Rounded.Compress
    "Detail recovery" -> Icons.Rounded.AutoAwesome
    "Bass foundation" -> Icons.Rounded.Speaker
    "Output protection" -> Icons.Rounded.Shield
    else -> Icons.Rounded.Tune
}

// ------------------------------------------------------------------ Equalizer

/** Small read-only curve shown on the Equalizer tile. */
@Composable
internal fun EqualizerMiniCurve(gains: List<Int>, enabled: Boolean, gainLimit: Int = StandardEqualizerGainLimit) {
    val colors = MaterialTheme.colorScheme
    val line = if (enabled) colors.primary else colors.onSurfaceVariant
    val zero = colors.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(40.dp).padding(top = 6.dp)) {
        val pad = 4.dp.toPx()
        drawLine(zero, Offset(0F, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
        drawPath(
            curvePath(gains, pad, inset = 0F, gainLimit = gainLimit),
            line,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

@Composable
internal fun ModernEqualizerEditor(
    bandGains: List<Int>,
    preampMillibels: Int,
    enabled: Boolean,
    gainLimit: Int = StandardEqualizerGainLimit,
    selectedPreset: EqualizerPreset?,
    onPresetSelected: (EqualizerPreset) -> Unit,
    onBandChange: (band: Int, gain: Int) -> Unit,
    onPreampChange: (Int) -> Unit,
    onCommit: () -> Unit,
) {
    var selectedBand by rememberSaveable { mutableIntStateOf(2) }
    val band = selectedBand.coerceIn(0, bandGains.lastIndex)
    val rack = LocalLayoutStyle.current == LayoutStyle.Rack
    val colors = MaterialTheme.colorScheme

    Column(
        Modifier.fillMaxWidth().padding(bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (selectedPreset == null) {
                ChoiceChip(label = "Custom", selected = true, enabled = enabled, onClick = {})
            }
            EqualizerPreset.entries.forEach { preset ->
                ChoiceChip(
                    label = preset.label,
                    selected = preset == selectedPreset,
                    enabled = enabled,
                    onClick = { onPresetSelected(preset) },
                )
            }
        }

        ResponseCurve(
            gains = bandGains,
            selected = band,
            enabled = enabled,
            gainLimit = gainLimit,
            onSelect = { selectedBand = it },
            onBandChange = onBandChange,
            onCommit = onCommit,
        )

        Row(Modifier.fillMaxWidth()) {
            EqualizerBandLabels.forEachIndexed { index, label ->
                BandFader(
                    label = label,
                    gain = bandGains[index],
                    gainLimit = gainLimit,
                    enabled = enabled,
                    selected = index == band,
                    onSelect = { selectedBand = index },
                    onChange = { onBandChange(index, it) },
                    onCommit = onCommit,
                    modifier = Modifier.weight(1F),
                )
            }
        }

        val label = EqualizerBandLabels[band]
        val gain = bandGains[band]
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(
                onClick = {
                    onBandChange(band, (gain - 50).coerceAtLeast(-gainLimit))
                    onCommit()
                },
                enabled = enabled,
            ) {
                Icon(Icons.Rounded.Remove, contentDescription = "Lower ${hertz(label)} by 0.5 dB")
            }
            Column(Modifier.weight(1F), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    dbText(gain),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (rack) colors.primary else Color.Unspecified,
                )
                Text(
                    "at ${hertz(label)}",
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            FilledIconButton(
                onClick = {
                    onBandChange(band, (gain + 50).coerceAtMost(gainLimit))
                    onCommit()
                },
                enabled = enabled,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Raise ${hertz(label)} by 0.5 dB")
            }
        }

        PreampControl(
            preampMillibels = preampMillibels,
            maxBoostMillibels = bandGains.maxOrNull()?.coerceAtLeast(0) ?: 0,
            enabled = enabled,
            onPreampChange = onPreampChange,
            onCommit = onCommit,
        )
    }
}

@Composable
private fun ResponseCurve(
    gains: List<Int>,
    selected: Int,
    enabled: Boolean,
    gainLimit: Int,
    onSelect: (Int) -> Unit,
    onBandChange: (Int, Int) -> Unit,
    onCommit: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val rack = LocalLayoutStyle.current == LayoutStyle.Rack
    val curve = if (enabled) colors.primary else colors.onSurfaceVariant
    val grid = colors.outlineVariant
    val zero = colors.outline
    val handleFill = colors.surfaceContainerLow
    val currentGains by rememberUpdatedState(gains)
    val select by rememberUpdatedState(onSelect)
    val change by rememberUpdatedState(onBandChange)
    val commit by rememberUpdatedState(onCommit)
    val density = LocalDensity.current
    val pad = with(density) { 12.dp.toPx() }
    val inset = with(density) { 14.dp.toPx() }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row {
                Text(
                    if (rack) "RESPONSE" else "Response",
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1F),
                )
                Text("±${gainLimit / 100} dB", color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            }
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .padding(top = 6.dp)
                    .semantics {
                        contentDescription =
                            "Equalizer response curve. Drag a point up or down to change that band."
                    }
                    .pointerInput(enabled, gainLimit) {
                        if (!enabled) return@pointerInput
                        var active = 0
                        detectDragGestures(
                            onDragStart = { offset ->
                                active = bandIndexAt(offset.x, size.width.toFloat(), inset, currentGains.size)
                                select(active)
                            },
                            onDragEnd = { commit() },
                            onDragCancel = { commit() },
                            onDrag = { pointer, _ ->
                                pointer.consume()
                                change(active, gainAtY(pointer.position.y, size.height.toFloat(), pad, gainLimit))
                            },
                        )
                    }
                    .pointerInput(enabled) {
                        if (!enabled) return@pointerInput
                        detectTapGestures { offset ->
                            select(bandIndexAt(offset.x, size.width.toFloat(), inset, currentGains.size))
                        }
                    },
            ) {
                listOf(-gainLimit, -gainLimit / 2, gainLimit / 2, gainLimit).forEach { level ->
                    val y = gainY(level, pad, gainLimit)
                    drawLine(grid, Offset(0F, y), Offset(size.width, y), 1.dp.toPx())
                }
                drawLine(zero, Offset(0F, size.height / 2), Offset(size.width, size.height / 2), 1.5.dp.toPx())
                val x = bandX(selected, gains.size, inset)
                drawLine(
                    curve.copy(alpha = 0.35F),
                    Offset(x, 0F),
                    Offset(x, size.height),
                    1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6F, 8F)),
                )
                val path = curvePath(gains, pad, inset, gainLimit)
                val area = curvePath(gains, pad, inset, gainLimit).apply {
                    lineTo(size.width, size.height)
                    lineTo(0F, size.height)
                    close()
                }
                drawPath(area, curve.copy(alpha = 0.12F))
                drawPath(path, curve, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                gains.forEachIndexed { index, gain ->
                    val center = Offset(bandX(index, gains.size, inset), gainY(gain, pad, gainLimit))
                    if (index == selected) {
                        drawCircle(curve, 9.dp.toPx(), center)
                        drawCircle(handleFill, 9.dp.toPx(), center, style = Stroke(3.dp.toPx()))
                    } else {
                        drawCircle(handleFill, 5.dp.toPx(), center)
                        drawCircle(curve, 5.dp.toPx(), center, style = Stroke(2.dp.toPx()))
                    }
                }
            }
        }
    }
}

@Composable
private fun BandFader(
    label: String,
    gain: Int,
    gainLimit: Int,
    enabled: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    onChange: (Int) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val rack = LocalLayoutStyle.current == LayoutStyle.Rack
    val select by rememberUpdatedState(onSelect)
    val change by rememberUpdatedState(onChange)
    val commit by rememberUpdatedState(onCommit)
    val pad = with(LocalDensity.current) { 10.dp.toPx() }
    val accent = if (enabled) colors.primary else colors.onSurfaceVariant
    val track = if (rack) colors.outlineVariant else colors.surfaceContainerHighest
    val thumb = if (selected) accent else colors.onSurface
    val ring = colors.surface
    val cap = colors.surfaceBright
    val capBorder = colors.outline
    val tick = colors.outline

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            shortDb(gain),
            color = when {
                selected -> accent
                gain == 0 -> colors.onSurfaceVariant
                else -> colors.onSurface
            },
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(168.dp)
                .semantics {
                    contentDescription = "${hertz(label)} band"
                    stateDescription = dbText(gain)
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        gain.toFloat(),
                        -gainLimit.toFloat()..gainLimit.toFloat(),
                        gainLimit * 2 / 50 - 1,
                    )
                    if (enabled) {
                        setProgress { target ->
                            change(snapGain(target, -gainLimit, gainLimit))
                            commit()
                            true
                        }
                    }
                }
                .pointerInput(enabled, gainLimit) {
                    if (!enabled) return@pointerInput
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            select()
                            change(faderGainAt(offset.y, size.height.toFloat(), pad, gainLimit))
                        },
                        onDragEnd = { commit() },
                        onDragCancel = { commit() },
                        onVerticalDrag = { pointer, _ ->
                            pointer.consume()
                            change(faderGainAt(pointer.position.y, size.height.toFloat(), pad, gainLimit))
                        },
                    )
                }
                .pointerInput(Unit) { detectTapGestures { select() } },
        ) {
            val cx = size.width / 2
            val usable = size.height - 2 * pad
            fun yOf(value: Int) = pad + (1F - (value + gainLimit) / (2F * gainLimit)) * usable
            val zeroY = yOf(0)
            val valueY = yOf(gain)
            val trackWidth = if (rack) 3.dp.toPx() else 6.dp.toPx()
            drawRoundRect(
                track,
                topLeft = Offset(cx - trackWidth / 2, pad),
                size = Size(trackWidth, usable),
                cornerRadius = CornerRadius(trackWidth / 2),
            )
            drawLine(tick, Offset(cx - 7.dp.toPx(), zeroY), Offset(cx + 7.dp.toPx(), zeroY), 1.dp.toPx())
            if (!rack && gain != 0) {
                drawRoundRect(
                    accent,
                    topLeft = Offset(cx - trackWidth / 2, minOf(zeroY, valueY)),
                    size = Size(trackWidth, kotlin.math.abs(zeroY - valueY)),
                    cornerRadius = CornerRadius(trackWidth / 2),
                )
            }
            if (rack) {
                val capWidth = minOf(26.dp.toPx(), size.width - 2.dp.toPx())
                val capHeight = 14.dp.toPx()
                val topLeft = Offset(cx - capWidth / 2, valueY - capHeight / 2)
                drawRoundRect(cap, topLeft, Size(capWidth, capHeight), CornerRadius(2.dp.toPx()))
                drawRoundRect(
                    if (selected) accent else capBorder,
                    topLeft,
                    Size(capWidth, capHeight),
                    CornerRadius(2.dp.toPx()),
                    style = Stroke(1.dp.toPx()),
                )
                drawLine(
                    accent,
                    Offset(cx - capWidth / 2 + 4.dp.toPx(), valueY),
                    Offset(cx + capWidth / 2 - 4.dp.toPx(), valueY),
                    2.dp.toPx(),
                )
            } else {
                drawCircle(ring, 11.dp.toPx(), Offset(cx, valueY))
                drawCircle(thumb, 8.dp.toPx(), Offset(cx, valueY))
            }
        }
        Text(
            label,
            color = if (selected) accent else colors.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun PreampControl(
    preampMillibels: Int,
    maxBoostMillibels: Int,
    enabled: Boolean,
    onPreampChange: (Int) -> Unit,
    onCommit: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val style = LocalLayoutStyle.current
    val rack = style == LayoutStyle.Rack
    // How far the loudest boosted band can push a full-scale signal above 0 dBFS.
    val overshoot = preampMillibels + maxBoostMillibels
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (rack) "PREAMP" else "Preamp",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1F),
            )
            Text(
                dbText(preampMillibels),
                color = if (enabled) colors.primary else colors.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Slider(
            value = preampMillibels.toFloat(),
            onValueChange = { onPreampChange(snapGain(it, -2400, 0)) },
            onValueChangeFinished = onCommit,
            valueRange = -2400F..0F,
            steps = 47,
            enabled = enabled,
        )
        if (overshoot > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    String.format(
                        Locale.US,
                        "Boosts can push peaks %.1f dB over full scale before the limiter",
                        overshoot / 100F,
                    ),
                    color = if (rack) colors.primary else colors.tertiary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1F),
                )
                TextButton(
                    onClick = {
                        onPreampChange((-maxBoostMillibels).coerceIn(-2400, 0))
                        onCommit()
                    },
                    enabled = enabled,
                ) {
                    Text("Match preamp")
                }
            }
        } else {
            Text(
                "Headroom OK: the boosts can't clip",
                color = if (style == LayoutStyle.Cards) colors.primary else colors.secondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

// ------------------------------------------------------------------ Helpers

private fun snapGain(value: Float, minimum: Int, maximum: Int): Int =
    ((value / 50F).roundToInt() * 50).coerceIn(minimum, maximum)

private fun dbText(millibels: Int): String =
    String.format(Locale.US, "%+.1f dB", millibels / 100F)

private fun shortDb(millibels: Int): String =
    if (millibels == 0) "0.0" else String.format(Locale.US, "%+.1f", millibels / 100F)

private fun hertz(label: String): String =
    if (label.endsWith("k")) "${label.dropLast(1)} kHz" else "$label Hz"

private fun bandIndexAt(x: Float, width: Float, inset: Float, count: Int): Int {
    val step = (width - 2 * inset) / (count - 1)
    return ((x - inset) / step).roundToInt().coerceIn(0, count - 1)
}

private fun gainAtY(y: Float, height: Float, pad: Float, gainLimit: Int): Int {
    val half = height / 2F
    return snapGain((half - y) / (half - pad) * gainLimit, -gainLimit, gainLimit)
}

private fun faderGainAt(y: Float, height: Float, pad: Float, gainLimit: Int): Int {
    val usable = height - 2 * pad
    val fraction = 1F - ((y - pad) / usable).coerceIn(0F, 1F)
    return snapGain(fraction * 2 * gainLimit - gainLimit, -gainLimit, gainLimit)
}

private fun DrawScope.gainY(gain: Int, pad: Float, gainLimit: Int): Float =
    size.height / 2F - gain.toFloat() / gainLimit * (size.height / 2F - pad)

private fun DrawScope.bandX(index: Int, count: Int, inset: Float): Float =
    inset + index * (size.width - 2 * inset) / (count - 1)

/** Smooth curve through the band points, extended flat to both edges. */
private fun DrawScope.curvePath(gains: List<Int>, pad: Float, inset: Float, gainLimit: Int): Path {
    val points = gains.mapIndexed { index, gain -> Offset(bandX(index, gains.size, inset), gainY(gain, pad, gainLimit)) }
    return Path().apply {
        moveTo(0F, points.first().y)
        lineTo(points.first().x, points.first().y)
        for (index in 1 until points.size) {
            val a = points[index - 1]
            val b = points[index]
            val midX = (a.x + b.x) / 2
            cubicTo(midX, a.y, midX, b.y, b.x, b.y)
        }
        lineTo(size.width, points.last().y)
    }
}
