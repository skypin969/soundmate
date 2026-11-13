package com.skypin.soundmate

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Build
import kotlin.math.roundToInt

/**
 * Satu set efek suara untuk satu sesi audio.
 * Memakai mesin DynamicsProcessing kalau HP mendukung, kalau tidak memakai efek dasar.
 */
class FxSet(session: Int, allowDp: Boolean) {
    private var dp: DynamicsProcessing? = null
    private var eq: Equalizer? = null
    private var loud: LoudnessEnhancer? = null
    private var virt: Virtualizer? = null

    val usesDp: Boolean
        get() = dp != null

    init {
        if (allowDp && Build.VERSION.SDK_INT >= 28) {
            dp = try { makeDp(session) } catch (t: Throwable) { null }
        }
        if (dp == null) {
            eq = try { Equalizer(0, session) } catch (t: Throwable) { null }
            loud = try { LoudnessEnhancer(session) } catch (t: Throwable) { null }
        }
        virt = try { Virtualizer(0, session) } catch (t: Throwable) { null }
    }

    private fun makeDp(session: Int): DynamicsProcessing {
        val cfg = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            2,
            true, Presets.BANDS,
            false, 0,
            false, 0,
            true
        ).build()
        return DynamicsProcessing(0, session, cfg)
    }

    fun apply(p: Prefs, active: Boolean) {
        val g = Curve.gains(p)

        val d = dp
        if (d != null) {
            try {
                for (i in 0 until Presets.BANDS) {
                    d.setPreEqBandAllChannelsTo(
                        i, DynamicsProcessing.EqBand(true, Curve.CUTOFF[i], g[i].toFloat())
                    )
                }
                d.setInputGainAllChannelsTo(Curve.preampDb(p, g))
                // Pembatas supaya suara tidak pecah saat bass dan volume dinaikkan.
                d.setLimiterAllChannelsTo(
                    DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -2f, 0f)
                )
                d.setEnabled(active)
            } catch (t: Throwable) {
            }
        }

        val e = eq
        if (e != null) {
            try {
                val range = e.bandLevelRange
                val lo = range[0].toInt()
                val hi = range[1].toInt()
                val n = e.numberOfBands.toInt()
                for (b in 0 until n) {
                    val hz = e.getCenterFreq(b.toShort()) / 1000.0
                    val mb = (Curve.at(g, hz) * 100.0).roundToInt().coerceIn(lo, hi)
                    e.setBandLevel(b.toShort(), mb.toShort())
                }
                e.setEnabled(active)
            } catch (t: Throwable) {
            }
        }

        val l = loud
        if (l != null) {
            try {
                l.setTargetGain(p.volume * 9)
                l.setEnabled(active && p.volume > 0)
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
    }

    fun release() {
        try { dp?.release() } catch (t: Throwable) {}
        try { eq?.release() } catch (t: Throwable) {}
        try { loud?.release() } catch (t: Throwable) {}
        try { virt?.release() } catch (t: Throwable) {}
    }
}
