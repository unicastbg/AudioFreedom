package com.svetlio.audiofreedom

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

internal val EqualizerFrequenciesHz = listOf(31.25, 62.5, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)

internal fun estimatedEqPeakBoost(gains: List<Int>, qValues: List<Int>): Int {
    val frequencies = List(513) { 20.0 * 1000.0.pow(it / 512.0) } + EqualizerFrequenciesHz
    return kotlin.math.ceil(frequencies.maxOf { equalizerResponseDb(gains, qValues, it) }
        .coerceAtLeast(0f) * 100.0).toInt()
}

// RBJ peaking filters, matching Engine::refresh_equalizer; display reference is 48 kHz.
internal fun equalizerResponseDb(gains: List<Int>, qValues: List<Int>, frequency: Double,
                                sampleRate: Double = 48000.0): Float {
    val w = 2 * PI * frequency / sampleRate
    var db = 0.0
    gains.forEachIndexed { band, gain ->
        val center = EqualizerFrequenciesHz[band]
        if (gain == 0 || center >= sampleRate * 0.49) return@forEachIndexed
        val a = 10.0.pow(gain / 4000.0)
        val omega = 2 * PI * center / sampleRate
        val alpha = sin(omega) / (2 * qValues[band] / 100.0)
        val b0 = 1 + alpha * a
        val b1 = -2 * cos(omega)
        val b2 = 1 - alpha * a
        val a0 = 1 + alpha / a
        val a1 = b1
        val a2 = 1 - alpha / a
        fun power(c0: Double, c1: Double, c2: Double): Double {
            val re = c0 + c1 * cos(w) + c2 * cos(2 * w)
            val im = -c1 * sin(w) - c2 * sin(2 * w)
            return re * re + im * im
        }
        db += 10 * log10((power(b0, b1, b2) / power(a0, a1, a2)).coerceAtLeast(1e-20))
    }
    return db.toFloat()
}
