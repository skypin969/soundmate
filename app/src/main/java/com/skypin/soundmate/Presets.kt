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
        Preset("Musik", intArrayOf(3, 3, 2, 0, -1, 0, 1, 2, 3, 3), 40, 60, 10, 20),
        Preset("Film", intArrayOf(4, 4, 2, 0, -1, 0, 2, 3, 3, 2), 60, 90, 50, 30),
        Preset("Game", intArrayOf(2, 2, 1, 0, 0, 1, 3, 4, 4, 3), 30, 100, 30, 30),
        Preset("Suara", intArrayOf(-4, -3, -2, 0, 1, 3, 4, 3, 1, 0), 0, 20, 80, 40),
        Preset("Bass", intArrayOf(6, 5, 4, 2, 0, 0, 0, 1, 2, 2), 80, 50, 0, 10),
        Preset("Datar", intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0), 0, 0, 0, 0)
    )
}
