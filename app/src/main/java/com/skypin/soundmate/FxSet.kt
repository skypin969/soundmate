package com.skypin.soundmate

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import kotlin.math.ln
import kotlin.math.roundToInt

/** Satu set efek suara untuk satu sesi audio. */
class FxSet(session: Int) {
    private val eq: Equalizer? = try { Equalizer(0, session) } catch (e: Throwable) { null }
    private val bass: BassBoost? = try { BassBoost(0, session) } catch (e: Throwable) { null }
    private val virt: Virtualizer? = try { Virtualizer(0, session) } catch (e: Throwable) { null }
    private val loud: LoudnessEnhancer? = try { LoudnessEnhancer(session) } catch (e: Throwable) { null }

    fun apply(p: Prefs, active: Boolean) {
        val e = eq
        if (e != null) {
            try {
                val range = e.bandLevelRange
                val lo = range[0].toInt()
                val hi = range[1].toInt()
                val n = e.numberOfBands.toInt()
                for (b in 0 until n) {
                    val hz = e.getCenterFreq(b.toShort()) / 1000.0
                    val mb = (gainAt(p, hz) * 100.0).roundToInt().coerceIn(lo, hi)
                    e.setBandLevel(b.toShort(), mb.toShort())
                }
                e.setEnabled(active)
            } catch (t: Throwable) {
            }
        }

        val b = bass
        if (b != null) {
            try {
                if (b.strengthSupported) b.setStrength((p.bass * 10).toShort())
                b.setEnabled(active && p.bass > 0)
            } catch (t: Throwable) {
            }
        }

        val v = virt
        if (v != null) {
            try {
                if (v.strengthSupported) v.setStrength((p.surround * 10).toShort())
                v.setEnabled(active && p.surround > 0)
            } catch (t: Throwable) {
            }
        }

        val l = loud
        if (l != null) {
            try {
                l.setTargetGain(p.volume * 10)
                l.setEnabled(active && p.volume > 0)
            } catch (t: Throwable) {
            }
        }
    }

    fun release() {
        try { eq?.release() } catch (t: Throwable) {}
        try { bass?.release() } catch (t: Throwable) {}
        try { virt?.release() } catch (t: Throwable) {}
        try { loud?.release() } catch (t: Throwable) {}
    }

    /** Nilai dB untuk frekuensi tertentu, dihitung dari lima batang di layar. */
    private fun gainAt(p: Prefs, hz: Double): Double {
        val f = Presets.FREQ
        if (hz <= f[0]) return p.band(0).toDouble()
        val last = f.size - 1
        if (hz >= f[last]) return p.band(last).toDouble()
        for (i in 0 until last) {
            if (hz <= f[i + 1]) {
                val t = (ln(hz) - ln(f[i].toDouble())) / (ln(f[i + 1].toDouble()) - ln(f[i].toDouble()))
                return p.band(i) + t * (p.band(i + 1) - p.band(i))
            }
        }
        return 0.0
    }
}
