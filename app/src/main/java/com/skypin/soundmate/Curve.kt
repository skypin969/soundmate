package com.skypin.soundmate

import kotlin.math.ln
import kotlin.math.sqrt

/** Menghitung kurva suara akhir dari equalizer, bass, dan penjernih dialog. */
object Curve {
    // Tambahan dB per batang saat penggeser berada di 100%.
    private val BASS = doubleArrayOf(10.0, 9.0, 6.0, 2.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    private val DIALOG = doubleArrayOf(0.0, 0.0, -1.5, -2.0, 1.0, 3.0, 5.0, 4.0, 1.0, 0.0)

    /** Batas atas tiap batang (Hz), dipakai mesin DynamicsProcessing. */
    val CUTOFF: FloatArray = FloatArray(Presets.BANDS) { i ->
        if (i == Presets.BANDS - 1) 20000f
        else sqrt(Presets.FREQ[i].toDouble() * Presets.FREQ[i + 1].toDouble()).toFloat()
    }

    fun gains(p: Prefs): DoubleArray {
        val g = DoubleArray(Presets.BANDS)
        for (i in 0 until Presets.BANDS) {
            val v = p.band(i) + p.bass / 100.0 * BASS[i] + p.dialog / 100.0 * DIALOG[i]
            g[i] = v.coerceIn(-12.0, 15.0)
        }
        return g
    }

    /** Penguatan awal: naik oleh penguat volume, turun sedikit supaya suara tidak pecah. */
    fun preampDb(p: Prefs, g: DoubleArray): Float {
        var top = 0.0
        for (v in g) if (v > top) top = v
        return (p.volume / 100.0 * 9.0 - 0.4 * top).toFloat()
    }

    /** Nilai dB kurva pada frekuensi tertentu. */
    fun at(g: DoubleArray, hz: Double): Double {
        val f = Presets.FREQ
        if (hz <= f[0]) return g[0]
        val last = f.size - 1
        if (hz >= f[last]) return g[last]
        for (i in 0 until last) {
            if (hz <= f[i + 1]) {
                val a = ln(f[i].toDouble())
                val b = ln(f[i + 1].toDouble())
                val t = (ln(hz) - a) / (b - a)
                return g[i] + t * (g[i + 1] - g[i])
            }
        }
        return 0.0
    }
}
