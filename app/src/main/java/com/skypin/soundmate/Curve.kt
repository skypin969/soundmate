package com.skypin.soundmate

import kotlin.math.ln
import kotlin.math.sqrt

/** Menghitung kurva suara dari equalizer, bass, dan penjernih dialog. */
object Curve {
    // Tambahan dB per batang saat penggeser bass berada di 100%.
    // Dorongan utama di 62 dan 125 Hz, bagian yang terdengar sebagai gelegar.
    // 31 Hz dibuat kecil: speaker dan headset kecil hampir tidak memutarnya,
    // dan energinya membuat pembatas menurunkan volume seluruh lagu.
    private val BASS = doubleArrayOf(3.0, 9.0, 9.0, 2.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

    // Tambahan dB per batang saat penjernih dialog berada di 100%.
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
            g[i] = v.coerceIn(-12.0, 12.0)
        }
        return g
    }

    /** Penguat volume untuk mesin biasa, dalam millibel (0 sampai 6 dB). */
    fun loudMb(p: Prefs): Int = p.volume * 6

    /** Nilai dB kurva pada frekuensi tertentu, dihitung dari batang terdekat. */
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
