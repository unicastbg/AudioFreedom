package com.svetlio.audiofreedom

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

internal fun cassetteHasSignal(processing: Boolean, bands: List<Int>?): Boolean =
    processing && bands?.any { it > -9000 } == true

@Composable
internal fun CassetteWindow(signalActive: Boolean) {
    val rotation = if (signalActive && !LocalInspectionMode.current) {
        val motion = rememberInfiniteTransition(label = "cassetteReels")
        val angle by motion.animateFloat(0F, 360F,
            infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
            label = "reelRotation")
        angle
    } else 0F
    val measurer = rememberTextMeasurer()
    Canvas(Modifier.fillMaxWidth().aspectRatio(1.95F).semantics {
        contentDescription = if (signalActive) "Cassette deck, audio signal active" else "Cassette deck, idle"
    }) {
        val u = size.width / 1000F
        val v = size.height / 520F
        fun at(x: Float, y: Float) = Offset(x * u, y * v)
        fun extent(w: Float, h: Float) = Size(w * u, h * v)
        fun rounded(color: Color, x: Float, y: Float, w: Float, h: Float, r: Float) {
            drawRoundRect(color, at(x, y), extent(w, h), CornerRadius(r * u))
        }
        fun label(text: String, x: Float, y: Float, color: Color, fontSize: Int = 10) {
            val layout = measurer.measure(AnnotatedString(text), TextStyle(color = color,
                fontSize = fontSize.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold))
            val p = at(x, y)
            drawText(layout, topLeft = Offset(p.x - layout.size.width / 2, p.y - layout.size.height / 2))
        }
        val ink = Color(0xFF191D1B)
        val metal = Color(0xFFA8A99B)
        val cassette = Color(0xFFB8AD86)
        val ivory = Color(0xFFE8E0C5)
        val amber = Color(0xFFFFAA46)

        // Recessed glass door, with an inset cassette shell and visible tape path.
        rounded(Color(0xFF262A25), 0F, 0F, 1000F, 520F, 12F)
        rounded(metal, 8F, 8F, 984F, 504F, 10F)
        rounded(Color(0xFF101713), 22F, 22F, 956F, 476F, 8F)
        drawRect(Brush.verticalGradient(listOf(Color(0xFF354238), Color(0xFF0E1511))),
            at(34F, 34F), extent(932F, 452F))
        rounded(Color(0xFF000000), 105F, 75F, 800F, 394F, 22F)
        rounded(cassette, 115F, 65F, 770F, 384F, 20F)
        rounded(ivory, 143F, 84F, 714F, 112F, 8F)
        drawRect(Color(0xFFB35335), at(143F, 153F), extent(714F, 15F))
        label("A", 179F, 119F, ink, 13)
        label("AUDIOFREEDOM", 505F, 119F, ink, 12)
        label("PERSONAL SOUND", 505F, 182F, ink, 9)
        rounded(ink, 199F, 209F, 602F, 158F, 30F)
        rounded(Color(0xFF465044), 211F, 221F, 578F, 134F, 24F)
        drawLine(Color(0xFF493528), at(330F, 318F), at(670F, 318F), 6F * u)

        fun reel(cx: Float, tapeRadius: Float, phase: Float) {
            val center = at(cx, 286F)
            drawCircle(Color(0xFF372B20), tapeRadius * u, center)
            for (radius in 57..tapeRadius.toInt() step 7) {
                drawCircle(Color(0xFF75634A).copy(alpha = 0.5F), radius * u, center,
                    style = Stroke(1F * u))
            }
            drawCircle(ivory, 52F * u, center)
            drawCircle(Color(0xFF777C6D), 41F * u, center, style = Stroke(5F * u))
            repeat(6) { index ->
                val angle = Math.toRadians((phase + index * 60F).toDouble())
                val tooth = Offset(center.x + cos(angle).toFloat() * 28F * u,
                    center.y + sin(angle).toFloat() * 28F * u)
                drawCircle(ink, 9F * u, tooth)
            }
            drawCircle(ink, 14F * u, center)
            drawCircle(metal, 5F * u, center)
        }
        reel(329F, 75F, rotation)
        reel(671F, 87F, rotation + 25F)
        label("TAPE", 500F, 280F, ivory, 9)
        val headCover = Path().apply {
            moveTo(319F * u, 387F * v); lineTo(681F * u, 387F * v)
            lineTo(716F * u, 449F * v); lineTo(284F * u, 449F * v); close()
        }
        drawPath(headCover, Color(0xFF8A8368))
        for (x in listOf(355F, 645F)) {
            drawCircle(ink, 16F * u, at(x, 418F))
            drawCircle(metal, 7F * u, at(x, 418F))
        }
        for ((x, y) in listOf(133F to 81F, 867F to 81F, 133F to 430F, 867F to 430F)) {
            drawCircle(Color(0xFF77745F), 7F * u, at(x, y))
            drawLine(ink, at(x - 4F, y), at(x + 4F, y), 2F * u)
        }
        // A narrow glass highlight keeps the tape itself readable.
        drawLine(Color.White.copy(alpha = 0.10F), at(48F, 46F), at(940F, 46F), 5F * u)
        drawLine(Color.White.copy(alpha = 0.05F), at(48F, 54F), at(48F, 466F), 6F * u)
        drawLine(Color(0xFF4C554B), at(65F, 478F), at(935F, 478F), 5F * u, cap = StrokeCap.Round)
        drawCircle(if (signalActive) amber else Color(0xFF615442), 6F * u, at(70F, 70F))
    }
}
