package com.skypin.soundmate

import android.content.Context

/**
 * Semua pengaturan Soundmate disimpan di sini.
 * Pengaturan suara disimpan terpisah untuk tiap perangkat Bluetooth (profil).
 */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("soundmate", Context.MODE_PRIVATE)
    /** Preset yang dipakai saat pertama kali dipasang: Bass. */
    private val defPreset = Presets.ALL.indexOfFirst { it.name == "Bass" }.coerceAtLeast(0)
    private val def = Presets.ALL[defPreset]

    var enabled: Boolean
        get() = sp.getBoolean("enabled", true)
        set(v) { sp.edit().putBoolean("enabled", v).apply() }

    var global: Boolean
        get() = sp.getBoolean("global", false)
        set(v) { sp.edit().putBoolean("global", v).apply() }

    /** Nama perangkat Bluetooth yang pengaturannya sedang dipakai. */
    var profile: String
        get() = sp.getString("profile", "") ?: ""
        set(v) { sp.edit().putString("profile", v).apply() }

    private fun k(name: String): String = "p/" + profile + "/" + name

    /** Nomor preset yang dipilih, -1 kalau diatur manual. */
    var preset: Int
        get() = sp.getInt(k("preset"), defPreset)
        set(v) { sp.edit().putInt(k("preset"), v).apply() }

    var bass: Int
        get() = sp.getInt(k("bass"), def.bass)
        set(v) { sp.edit().putInt(k("bass"), v).apply() }

    var surround: Int
        get() = sp.getInt(k("surround"), def.surround)
        set(v) { sp.edit().putInt(k("surround"), v).apply() }

    var dialog: Int
        get() = sp.getInt(k("dialog"), def.dialog)
        set(v) { sp.edit().putInt(k("dialog"), v).apply() }

    var volume: Int
        get() = sp.getInt(k("volume"), def.volume)
        set(v) { sp.edit().putInt(k("volume"), v).apply() }

    fun band(i: Int): Int = sp.getInt(k("b$i"), def.bands[i])

    fun setBand(i: Int, v: Int) {
        sp.edit().putInt(k("b$i"), v).apply()
    }

    fun applyPreset(idx: Int) {
        val p = Presets.ALL[idx]
        val e = sp.edit()
        e.putInt(k("preset"), idx)
        for (i in 0 until Presets.BANDS) e.putInt(k("b$i"), p.bands[i])
        e.putInt(k("bass"), p.bass)
        e.putInt(k("surround"), p.surround)
        e.putInt(k("dialog"), p.dialog)
        e.putInt(k("volume"), p.volume)
        e.apply()
    }

    fun customSaved(n: Int): Boolean = sp.getBoolean("c$n/saved", false)

    /** Menyimpan pengaturan sekarang ke slot Kustom. */
    fun saveCustom(n: Int) {
        val e = sp.edit()
        e.putBoolean("c$n/saved", true)
        for (i in 0 until Presets.BANDS) e.putInt("c$n/b$i", band(i))
        e.putInt("c$n/bass", bass)
        e.putInt("c$n/surround", surround)
        e.putInt("c$n/dialog", dialog)
        e.putInt("c$n/volume", volume)
        e.putInt(k("preset"), Presets.ALL.size + n)
        e.apply()
    }

    /** Memuat slot Kustom. Mengembalikan false kalau slot masih kosong. */
    fun loadCustom(n: Int): Boolean {
        if (!customSaved(n)) return false
        val e = sp.edit()
        for (i in 0 until Presets.BANDS) e.putInt(k("b$i"), sp.getInt("c$n/b$i", 0))
        e.putInt(k("bass"), sp.getInt("c$n/bass", 0))
        e.putInt(k("surround"), sp.getInt("c$n/surround", 0))
        e.putInt(k("dialog"), sp.getInt("c$n/dialog", 0))
        e.putInt(k("volume"), sp.getInt("c$n/volume", 0))
        e.putInt(k("preset"), Presets.ALL.size + n)
        e.apply()
        return true
    }

    // Keterangan dari layanan, ditampilkan di bagian bawah layar.
    var stEngine: String
        get() = sp.getString("st_engine", "") ?: ""
        set(v) { sp.edit().putString("st_engine", v).apply() }

    var stSessions: Int
        get() = sp.getInt("st_sessions", 0)
        set(v) { sp.edit().putInt("st_sessions", v).apply() }

    /** Hasil pemilihan suara Asli terakhir, ditampilkan di layar utama dan notifikasi. */
    var stAsli: String
        get() = sp.getString("st_asli", "") ?: ""
        set(v) { sp.edit().putString("st_asli", v).apply() }
}
