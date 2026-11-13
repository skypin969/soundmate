package com.skypin.soundmate

import android.content.Context

/** Semua pengaturan Soundmate disimpan di sini. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("soundmate", Context.MODE_PRIVATE)
    private val def = Presets.ALL[0]

    var enabled: Boolean
        get() = sp.getBoolean("enabled", true)
        set(v) { sp.edit().putBoolean("enabled", v).apply() }

    var global: Boolean
        get() = sp.getBoolean("global", false)
        set(v) { sp.edit().putBoolean("global", v).apply() }

    /** Nomor preset yang dipilih, -1 kalau diatur manual. */
    var preset: Int
        get() = sp.getInt("preset", 0)
        set(v) { sp.edit().putInt("preset", v).apply() }

    var bass: Int
        get() = sp.getInt("bass", def.bass)
        set(v) { sp.edit().putInt("bass", v).apply() }

    var surround: Int
        get() = sp.getInt("surround", def.surround)
        set(v) { sp.edit().putInt("surround", v).apply() }

    var volume: Int
        get() = sp.getInt("volume", def.volume)
        set(v) { sp.edit().putInt("volume", v).apply() }

    fun band(i: Int): Int = sp.getInt("b$i", def.bands[i])

    fun setBand(i: Int, v: Int) {
        sp.edit().putInt("b$i", v).apply()
    }

    fun applyPreset(idx: Int) {
        val p = Presets.ALL[idx]
        val e = sp.edit()
        e.putInt("preset", idx)
        for (i in p.bands.indices) e.putInt("b$i", p.bands[i])
        e.putInt("bass", p.bass)
        e.putInt("surround", p.surround)
        e.putInt("volume", p.volume)
        e.apply()
    }
}
