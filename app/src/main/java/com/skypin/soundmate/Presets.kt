package com.skypin.soundmate

class Preset(
    val name: String,
    val bands: IntArray,
    val bass: Int,
    val surround: Int,
    val dialog: Int,
    val volume: Int
)

object Presets {
    const val BANDS = 10
    const val CUSTOM_COUNT = 2

    /** Frekuensi tengah sepuluh batang equalizer (Hz). */
    val FREQ = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    val LABELS = arrayOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

    val ALL = listOf(
        // Musik: bass sedikit padat, vokal hangat, kejernihan cukup.
        Preset("Musik", intArrayOf(2, 3, 2, 0, -1, 1, 2, 2, 2, 1), 45, 30, 15, 30),
        Preset("Film", intArrayOf(3, 3, 2, 0, -1, 1, 3, 3, 2, 1), 60, 70, 50, 35),
        Preset("Game", intArrayOf(2, 3, 3, 0, -2, 0, 2, 3, 3, 2), 50, 80, 20, 35),
        Preset("Suara", intArrayOf(-3, -2, -1, 0, 1, 3, 4, 3, 1, 0), 0, 20, 80, 40),
        // Bass: gelegar di 62 dan 125 Hz, 500 Hz dipotong supaya tidak mendem, kejernihan di 2k sampai 8k.
        Preset("Bass", intArrayOf(0, 0, 0, -1, -2, 0, 2, 2, 2, 1), 90, 25, 10, 55),
        // Datar: tidak ada yang diubah, efek dimatikan.
        Preset("Datar", IntArray(BANDS), 0, 0, 0, 0)
    )
}
