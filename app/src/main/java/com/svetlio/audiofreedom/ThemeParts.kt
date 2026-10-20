package com.svetlio.audiofreedom

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// The parts that make some themes more than a palette: Valve's VU meter, Phosphor's
// scope trace, Neon's glowing outline, the Cassette Deck's LCD and piano keys.
// Which one a theme uses is decided by its ThemeLook / LayoutStyle (Themes.kt).

/** The 10 measured spectrum bands as 0..1, eased so meters move smoothly. */
@Composable
internal fun animatedBandLevels(valuesMillibels: List<Int>?, active: Boolean): List<Float> =
    (0 until EqualizerBandLabels.size).map { index ->
        val target = valuesMillibels?.getOrNull(index)
            ?.let { (it + 6000).coerceIn(0, 6000) / 6000F } ?: 0F
        animateFloatAsState(
            targetValue = if (active) target else 0F,
            animationSpec = tween(200),
            label = "band$index",
        ).value
    }

// ------------------------------------------------------------------ Valve: VU meter

private val VuFace = Color(0xFFEFE3C8)
private val VuInk = Color(0xFF2A1C13)
private val VuRed = Color(0xFFB23A2A)
private val VuNeedle = Color(0xFF1E140E)
private val VuLampOn = Color(0xFFFF4A3A)
private val VuLampOff = Color(0xFF8A6A5A)

/** Scale marks: VU value to position along the arc (0 = left end, 1 = right end). */
private val VuMarks = listOf(
    -30F to 0F, -20F to 0.16F, -10F to 0.34F, -7F to 0.44F, -5F to 0.54F,
    -3F to 0.64F, -2F to 0.71F, -1F to 0.78F, 0F to 0.86F, 1F to 0.93F, 3F to 1F,
)
private val VuLabels = listOf(-30F, -20F, -10F, -5F, -3F, -1F, 0F, 3F)

internal fun vuPosition(vu: Float): Float {
    if (vu <= VuMarks.first().first) return 0F
    if (vu >= VuMarks.last().first) return 1F
    for (i in 1 until VuMarks.size) {
        val (v0, p0) = VuMarks[i - 1]
        val (v1, p1) = VuMarks[i]
        if (vu <= v1) return p0 + (vu - v0) / (v1 - v0) * (p1 - p0)
    }
    return 1F
}

/**
 * An analog VU meter driven by the output RMS.
 *
 * A VU meter shows average level, which is exactly what RMS is, so it reads the
 * analysis RMS rather than the peak. 0 VU is aligned to -18 dBFS, the usual digital
 * reference. The needle is sprung, so it swings and settles like a real movement; the
 * PEAK lamp lights when the output peak passes -1 dBFS. The driver reports one output
 * level, so this is one meter rather than a fake left/right pair.
 */
@Composable
internal fun VuMeter(rmsMillibels: Int?, peakMillibels: Int?) {
    val target = rmsMillibels?.let { vuPosition(it / 100F + 18F) } ?: 0F
    val position by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.55F, stiffness = 60F),
        label = "vuNeedle",
    )
    val clipping = peakMillibels != null && peakMillibels >= -100
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = VuInk, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(124.dp)
            .clip(MaterialTheme.shapes.medium)
            .semantics { contentDescription = "Output level VU meter" },
    ) {
        drawRect(VuFace)
        val pivot = Offset(size.width / 2, size.height * 1.12F)
        val radius = size.height * 0.98F
        val scaleRadius = radius * 0.86F
        fun at(f: Float, r: Float): Offset {
            val angle = Math.toRadians(-48.0 + f * 96.0)
            return Offset(pivot.x + r * sin(angle).toFloat(), pivot.y - r * cos(angle).toFloat())
        }
        val arcTopLeft = Offset(pivot.x - scaleRadius, pivot.y - scaleRadius)
        val arcSize = Size(scaleRadius * 2, scaleRadius * 2)
        drawArc(
            VuInk, startAngle = -138F, sweepAngle = 96F * 0.86F, useCenter = false,
            topLeft = arcTopLeft, size = arcSize, style = Stroke(1.4.dp.toPx()),
        )
        drawArc(
            VuRed, startAngle = -138F + 96F * 0.86F, sweepAngle = 96F * 0.14F, useCenter = false,
            topLeft = arcTopLeft, size = arcSize, style = Stroke(5.dp.toPx()),
        )
        VuMarks.forEach { (vu, f) ->
            drawLine(
                if (vu > 0F) VuRed else VuInk,
                at(f, scaleRadius),
                at(f, scaleRadius + 9.dp.toPx()),
                1.4.dp.toPx(),
            )
        }
        VuLabels.forEach { vu ->
            val text = if (vu > 0F) "+${vu.toInt()}" else vu.toInt().toString().replace('-', '−')
            val layout = measurer.measure(
                AnnotatedString(text),
                labelStyle.copy(color = if (vu > 0F) VuRed else VuInk),
            )
            val p = at(vuPosition(vu), scaleRadius + 20.dp.toPx())
            drawText(layout, topLeft = Offset(p.x - layout.size.width / 2, p.y - layout.size.height / 2))
        }
        val caption = measurer.measure(AnnotatedString("VU"), labelStyle.copy(fontSize = 14.sp))
        drawText(
            caption,
            topLeft = Offset(size.width / 2 - caption.size.width / 2, size.height * 0.5F),
        )
        drawLine(VuNeedle, pivot, at(position, radius * 1.02F), 2.dp.toPx(), cap = StrokeCap.Round)

        val lamp = Offset(size.width - 20.dp.toPx(), 16.dp.toPx())
        if (clipping) drawCircle(VuLampOn.copy(alpha = 0.35F), 9.dp.toPx(), lamp)
        drawCircle(if (clipping) VuLampOn else VuLampOff, 5.dp.toPx(), lamp)
        val peak = measurer.measure(AnnotatedString("PEAK"), labelStyle.copy(fontSize = 9.sp))
        drawText(peak, topLeft = Offset(lamp.x - peak.size.width / 2, lamp.y + 8.dp.toPx()))
    }
}

/** Valve's pilot lamp, in place of the icon badge on each effect. */
@Composable
internal fun PilotLamp(on: Boolean) {
    val lit = MaterialTheme.colorScheme.primary
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(12.dp)
                .then(
                    if (on) Modifier.shadow(10.dp, CircleShape, ambientColor = lit, spotColor = lit)
                    else Modifier,
                )
                .background(if (on) lit else MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
    }
}

// ------------------------------------------------------------------ Phosphor: scope trace

@Composable
internal fun TraceScope(valuesMillibels: List<Int>?, active: Boolean) {
    val levels = animatedBandLevels(valuesMillibels, active)
    val colors = MaterialTheme.colorScheme
    val trace = colors.primary
    val grid = colors.outlineVariant
    val shape = MaterialTheme.shapes.small
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(shape)
            .background(colors.surfaceContainerLowest)
            .border(1.dp, grid, shape)
            .semantics { contentDescription = "Output spectrum trace" },
    ) {
        val pad = 8.dp.toPx()
        for (i in 1 until 12) {
            val x = size.width * i / 12
            drawLine(grid.copy(alpha = 0.6F), Offset(x, 0F), Offset(x, size.height), 1F)
        }
        for (i in 1 until 4) {
            val y = size.height * i / 4
            drawLine(grid.copy(alpha = 0.6F), Offset(0F, y), Offset(size.width, y), 1F)
        }
        val points = 48
        val path = Path()
        for (i in 0 until points) {
            val x = size.width * i / (points - 1)
            val y = size.height - pad - bandLevelAt(levels, i, points) * (size.height - 2 * pad)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, trace.copy(alpha = 0.2F), style = Stroke(6.dp.toPx(), join = StrokeJoin.Round))
        drawPath(path, trace, style = Stroke(1.8.dp.toPx(), join = StrokeJoin.Round))
    }
}

// ------------------------------------------------------------------ Neon: glowing outline

@Composable
internal fun GlowSpectrum(valuesMillibels: List<Int>?, active: Boolean) {
    val levels = animatedBandLevels(valuesMillibels, active)
    val colors = MaterialTheme.colorScheme
    val glow = colors.primary
    val floor = colors.secondary
    val core = lerp(colors.primary, Color.White, 0.7F)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(128.dp)
            .semantics { contentDescription = "Output spectrum" },
    ) {
        val w = size.width
        val base = size.height * 0.66F
        val depth = size.height - base

        // Retro perspective floor.
        val grid = Path().apply {
            moveTo(0F, base); lineTo(w, base)
            for (k in 1..3) {
                val y = base + k * k * depth / 10F
                moveTo(0F, y); lineTo(w, y)
            }
            for (x in -4..4) {
                moveTo(w / 2 + x * w / 14F, base)
                lineTo(w / 2 + x * w / 4.5F, size.height)
            }
        }
        drawPath(grid, floor.copy(alpha = 0.22F), style = Stroke(1F))

        val n = 32
        val top = 6.dp.toPx()
        val points = (0 until n).map { i ->
            Offset(w * i / (n - 1), base - bandLevelAt(levels, i, n) * (base - top))
        }
        fun pathOf(list: List<Offset>) = Path().apply {
            list.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        }
        val area = pathOf(points).apply { lineTo(w, base); lineTo(0F, base); close() }
        drawPath(area, glow.copy(alpha = 0.12F))
        val reflection = pathOf(points.map { Offset(it.x, base + (base - it.y) * 0.45F) })
        drawPath(reflection, floor.copy(alpha = 0.35F), style = Stroke(1.5.dp.toPx(), join = StrokeJoin.Round))
        val outline = pathOf(points)
        drawPath(outline, glow.copy(alpha = 0.35F), style = Stroke(7.dp.toPx(), join = StrokeJoin.Round))
        drawPath(outline, core, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
    }
}

// ------------------------------------------------------------------ Cassette Deck: LCD

private val LcdBezel = Color(0xFF2A2620)
private val LcdWindow = Color(0xFF1D1A14)
private val LcdAmber = Color(0xFFFF9A2E)
private val LcdDim = Color(0xFFB87422)
private val LcdUnlit = Color(0xFF3A2F1E)
private val LedRed = Color(0xFFFF4A3A)
private val KeyLabel = Color(0xFFE8DFCC)

/** The Cassette Deck's processing card: an amber LCD window and a POWER key. */
@Composable
internal fun CassetteHero(
    statusLabel: String,
    statusOk: Boolean,
    rootLine: String?,
    processing: Boolean,
    driverAvailable: Boolean,
    routeLabel: String,
    activeEffects: Int,
    totalEffects: Int,
    peakMillibels: Int?,
    spectrumMillibels: List<Int>?,
    onProcessingChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val levels = animatedBandLevels(spectrumMillibels, processing)
    val shownPeak = peakMillibels?.takeIf { processing }
    val peak by animateFloatAsState(
        targetValue = shownPeak?.let { (it + 4800).coerceIn(0, 4800) / 4800F } ?: 0F,
        animationSpec = tween(150),
        label = "lcdPeak",
    )
    val lcd = TextStyle(fontFamily = FontFamily.Monospace, color = LcdAmber, fontSize = 18.sp)
    val tapeMoving = cassetteHasSignal(processing, spectrumMillibels)

    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(Color(0xFFC9C8B8))
            .border(1.dp, Color(0xFF939688), MaterialTheme.shapes.medium)
            .semantics { contentDescription = "Cassette deck panel" }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1F)) {
                Text("AUDIOFREEDOM", color = Color(0xFF262A25),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                Text("DIGITAL AUDIO PROCESSOR", color = Color(0xFF50574C),
                    style = MaterialTheme.typography.labelSmall)
            }
            Text("CASSETTE\nDECK", color = Color(0xFF50574C),
                style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
        }
        CassetteWindow(signalActive = tapeMoving)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(LcdBezel)
                .padding(6.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(LcdWindow)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (processing) "PROCESSING ON" else "STANDBY",
                        style = lcd.copy(fontSize = 14.sp),
                        maxLines = 1,
                        modifier = Modifier.weight(1.2F),
                    )
                    Text(
                        routeLabel.uppercase(Locale.US),
                        style = lcd.copy(color = LcdDim, fontSize = 12.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1F).padding(start = 8.dp),
                    )
                }
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .semantics { contentDescription = "Output spectrum" },
                ) {
                    val cols = 20
                    val rows = 8
                    val gap = 3.dp.toPx()
                    val rowGap = 2.dp.toPx()
                    val cellWidth = (size.width - gap * (cols - 1)) / cols
                    val cellHeight = (size.height - rowGap * (rows - 1)) / rows
                    for (c in 0 until cols) {
                        val lit = (bandLevelAt(levels, c, cols) * rows).roundToInt()
                        for (r in 0 until rows) {
                            val y = size.height - (r + 1) * cellHeight - r * rowGap
                            drawRect(
                                if (r < lit) LcdAmber else LcdUnlit,
                                Offset(c * (cellWidth + gap), y),
                                Size(cellWidth, cellHeight),
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("OUT", style = lcd.copy(fontSize = 15.sp), modifier = Modifier.width(40.dp))
                    Canvas(
                        Modifier
                            .weight(1F)
                            .height(10.dp)
                            .semantics { contentDescription = "Output peak meter" },
                    ) {
                        val segments = 24
                        val gap = 2.dp.toPx()
                        val width = (size.width - gap * (segments - 1)) / segments
                        val lit = (peak * segments).roundToInt()
                        for (s in 0 until segments) {
                            val color = when {
                                s >= lit -> LcdUnlit
                                s >= segments - 3 -> LedRed
                                else -> LcdAmber
                            }
                            drawRect(color, Offset(s * (width + gap), 0F), Size(width, size.height))
                        }
                    }
                    Text(
                        shownPeak?.let { String.format(Locale.US, "%.1fdB", it / 100F) } ?: "--",
                        style = lcd.copy(fontSize = 15.sp),
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(72.dp),
                    )
                }
                Row {
                    Text(
                        "$activeEffects/$totalEffects FX",
                        style = lcd.copy(fontSize = 15.sp, color = LcdDim),
                        modifier = Modifier.weight(1F),
                    )
                    Text(
                        when {
                            !statusOk -> "NO DRIVER"
                            !tapeMoving -> "NO SIGNAL"
                            else -> "DRV OK"
                        },
                        style = lcd.copy(fontSize = 15.sp, color = LcdDim),
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 92.dp, height = 64.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(LcdBezel)
                    .toggleable(
                        value = processing,
                        enabled = driverAvailable,
                        role = Role.Switch,
                        onValueChange = onProcessingChange,
                    )
                    .semantics { contentDescription = "Processing" },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .then(
                                if (processing) {
                                    Modifier.shadow(6.dp, CircleShape, ambientColor = LedRed, spotColor = LedRed)
                                } else {
                                    Modifier
                                },
                            )
                            .background(if (processing) LedRed else Color(0xFF5A3A34), CircleShape),
                    )
                    Text(
                        "POWER",
                        color = KeyLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1F)) {
                Text(
                    statusLabel,
                    color = if (statusOk) Color(0xFF262A25) else colors.error,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                rootLine?.let {
                    Text(it, color = Color(0xFF50574C), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Cassette Deck: keys

/**
 * One effect as a chunky deck key: tap the key to engage or release it, tap the small
 * adjust button for the controls. An engaged key sits lower, like a latched key.
 */
@Composable
internal fun EffectKey(
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
    val shape = MaterialTheme.shapes.small
    val face = if (enabled) colors.surfaceContainerHigh else colors.surfaceContainerLowest
    val lip = colors.surfaceContainerHighest
    Box(
        modifier = (if (tileWidth != Dp.Unspecified) Modifier.width(tileWidth) else Modifier.fillMaxWidth())
            .heightIn(min = 112.dp)
            .offset(y = if (enabled) 3.dp else 0.dp)
            .clip(shape)
            .background(face)
            .drawBehind {
                if (!enabled) {
                    val lipHeight = 5.dp.toPx()
                    drawRect(lip, Offset(0F, size.height - lipHeight), Size(size.width, lipHeight))
                }
            }
            .toggleable(value = enabled, role = Role.Switch, onValueChange = onEnabledChange)
            .semantics { contentDescription = title }
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(9.dp)
                        .then(
                            if (enabled) Modifier.shadow(6.dp, CircleShape, ambientColor = LedRed, spotColor = LedRed)
                            else Modifier,
                        )
                        .background(if (enabled) LedRed else colors.outline, CircleShape),
                )
                Spacer(Modifier.weight(1F))
                IconButton(onClick = { onExpandedChange(true) }) {
                    Icon(Icons.Rounded.Tune, contentDescription = "Adjust $title", tint = colors.onSurfaceVariant)
                }
            }
            Text(
                title.uppercase(Locale.US),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp,
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
        EffectSheet(
            title = title,
            summary = summary,
            enabled = enabled,
            onEnabledChange = onEnabledChange,
            onDismiss = { onExpandedChange(false) },
            content = content,
        )
    }
}

/** An effect's controls in a bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EffectSheet(
    title: String,
    summary: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                Column(Modifier.weight(1F)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(summary, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}
