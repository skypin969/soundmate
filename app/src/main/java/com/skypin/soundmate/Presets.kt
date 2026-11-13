package com.skypin.soundmate

class Preset(
    val name: String,
    val bands: IntArray,
    val bass: Int,
    val surround: Int,
    val volume: Int
)

object Presets {
    /** Frekuensi tengah lima batang equalizer (Hz). */
    val FREQ = intArrayOf(60, 230, 910, 3600, 14000)
    val LABELS = arrayOf("60 Hz", "230 Hz", "910 Hz", "3,6 kHz", "14 kHz")

    val ALL = listOf(
        Preset("Musik", intArrayOf(4, 2, 0, 2, 3), 60, 70, 20),
        Preset("Film", intArrayOf(5, 2, -1, 3, 4), 70, 90, 30),
        Preset("Game", intArrayOf(3, 0, 1, 4, 5), 40, 100, 30),
        Preset("Suara", intArrayOf(-3, -1, 3, 4, 1), 0, 20, 40)
    )
}
