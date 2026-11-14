package com.skypin.soundmate

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Build
import kotlin.math.roundToInt

/** Batas pita kompresor (Hz): bass di bawah 150, tengah 150 sampai 2,5 kHz, tinggi di atasnya. */
private val MBC_CUT = floatArrayOf(150f, 2500f, 20000f)

/**
 * Satu set efek suara untuk satu sesi audio.
 * Di HP yang mendukung: EQ 10 pita, lalu kompresor 3 pita (bass padat dan volume naik), lalu pembatas.
 * Kalau tidak didukung, memakai equalizer biasa dan penguat loudness.
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
        }
        loud = try { LoudnessEnhancer(session) } catch (t: Throwable) { null }
        virt = try { Virtualizer(0, session) } catch (t: Throwable) { null }
    }

    private fun makeDp(session: Int): DynamicsProcessing {
        val cfg = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            2,
            true, Presets.BANDS,
            true, MBC_CUT.size,
            false, 0,
            true
        ).build()
        return DynamicsProcessing(0, session, cfg)
    }

    fun apply(p: Prefs, active: Boolean) {
        val g = Curve.gains(p)
        // Datar: tidak ada yang diubah, jadi efek dimatikan supaya suaranya benar-benar asli.
        val flat = g.all { it == 0.0 } && p.bass == 0 && p.dialog == 0 && p.volume == 0

        val d = dp
        if (d != null) {
            for (i in 0 until Presets.BANDS) {
                try {
                    d.setPreEqBandAllChannelsTo(
                        i, DynamicsProcessing.EqBand(true, Curve.CUTOFF[i], g[i].toFloat())
                    )
                } catch (t: Throwable) {
                }
            }
            for (i in MBC_CUT.indices) {
                try {
                    d.setMbcBandAllChannelsTo(i, mbcBand(i, p))
                } catch (t: Throwable) {
                }
            }
            try {
                d.setLimiterAllChannelsTo(
                    DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -1f, 0f)
                )
            } catch (t: Throwable) {
            }
            try {
                d.setEnabled(active && !flat)
            } catch (t: Throwable) {
            }
        }

        val e = eq
        if (e != null) {
            try {
                val range = e.bandLevelRange
                val lo = range[0].toInt()
                val hi = range[1].toInt()
                for (b in 0 until e.numberOfBands.toInt()) {
                    val hz = e.getCenterFreq(b.toShort()) / 1000.0
                    val mb = (Curve.at(g, hz) * 100.0).roundToInt().coerceIn(lo, hi)
                    e.setBandLevel(b.toShort(), mb.toShort())
                }
                e.setEnabled(active && !flat)
            } catch (t: Throwable) {
            }
        }

        val l = loud
        if (l != null) {
            try {
                l.setTargetGain(Curve.loudMb(p))
                // Di mode DynamicsProcessing, volume sudah diatur lewat kompresor.
                l.setEnabled(active && !usesDp && p.volume > 0)
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

    /**
     * Kompresor per pita. Pita bass dikompres 4:1 supaya puncaknya tidak melewati batas.
     * Penguat keluar pita bass ikut penggeser Bass (paling besar +3 dB), jadi bass lebih tebal
     * tanpa menaikkan vokal. Pita tengah dan tinggi hanya mengikuti penggeser volume.
     */
    private fun mbcBand(i: Int, p: Prefs): DynamicsProcessing.MbcBand {
        val vol = p.volume / 100.0
        val bassUp = p.bass / 100.0 * 3.0
        return when (i) {
            0 -> DynamicsProcessing.MbcBand(
                true, MBC_CUT[0], 20f, 200f, 4f, -10f, 6f, -90f, 1f, 0f, (0.5 + vol * 5.0 + bassUp).toFloat()
            )
            1 -> DynamicsProcessing.MbcBand(
                true, MBC_CUT[1], 15f, 150f, 1.5f, -14f, 6f, -90f, 1f, 0f, (1.5 + vol * 5.0).toFloat()
            )
            else -> DynamicsProcessing.MbcBand(
                true, MBC_CUT[2], 10f, 100f, 1.5f, -14f, 6f, -90f, 1f, 0f, (1.0 + vol * 4.0).toFloat()
            )
        }
    }

    fun release() {
        try { dp?.release() } catch (t: Throwable) {}
        try { eq?.release() } catch (t: Throwable) {}
        try { loud?.release() } catch (t: Throwable) {}
        try { virt?.release() } catch (t: Throwable) {}
    }
}
