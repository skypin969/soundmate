package com.skypin.soundmate

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    companion object {
        private const val ADB_COMMAND =
            "adb shell pm grant com.skypin.soundmate android.permission.DUMP"
    }

    private lateinit var prefs: Prefs
    private lateinit var am: AudioManager

    private lateinit var iconBox: View
    private lateinit var imgBt: ImageView
    private lateinit var txtBtState: TextView
    private lateinit var txtBtName: TextView
    private lateinit var txtFxTitle: TextView
    private lateinit var txtFxSub: TextView
    private lateinit var txtInfo: TextView
    private lateinit var swMaster: Switch
    private lateinit var sbBass: SeekBar
    private lateinit var sbSurround: SeekBar
    private lateinit var sbDialog: SeekBar
    private lateinit var sbVolume: SeekBar
    private lateinit var txtBass: TextView
    private lateinit var txtSurround: TextView
    private lateinit var txtDialog: TextView
    private lateinit var txtVolume: TextView

    private val presetButtons = ArrayList<Button>()
    private val eqSliders = ArrayList<VerticalSlider>()
    private val eqValues = ArrayList<TextView>()
    private var updating = false

    private val handler = Handler(Looper.getMainLooper())
    private val infoTick = object : Runnable {
        override fun run() {
            updateInfo()
            handler.postDelayed(this, 2000L)
        }
    }

    private val deviceCb = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshStatus()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = Prefs(this)
        am = getSystemService(AudioManager::class.java)

        iconBox = findViewById(R.id.iconBox)
        imgBt = findViewById(R.id.imgBt)
        txtBtState = findViewById(R.id.txtBtState)
        txtBtName = findViewById(R.id.txtBtName)
        txtFxTitle = findViewById(R.id.txtFxTitle)
        txtFxSub = findViewById(R.id.txtFxSub)
        txtInfo = findViewById(R.id.txtInfo)
        swMaster = findViewById(R.id.swMaster)
        sbBass = findViewById(R.id.sbBass)
        sbSurround = findViewById(R.id.sbSurround)
        sbDialog = findViewById(R.id.sbDialog)
        sbVolume = findViewById(R.id.sbVolume)
        txtBass = findViewById(R.id.txtBass)
        txtSurround = findViewById(R.id.txtSurround)
        txtDialog = findViewById(R.id.txtDialog)
        txtVolume = findViewById(R.id.txtVolume)

        val ids = intArrayOf(
            R.id.btnP0, R.id.btnP1, R.id.btnP2, R.id.btnP3,
            R.id.btnP4, R.id.btnP5, R.id.btnP6, R.id.btnP7
        )
        for (i in ids.indices) {
            val b: Button = findViewById(ids[i])
            presetButtons.add(b)
            b.setOnClickListener { onPresetClick(i) }
            if (i >= Presets.ALL.size) {
                b.setOnLongClickListener {
                    val n = i - Presets.ALL.size
                    prefs.saveCustom(n)
                    showPreset()
                    Toast.makeText(this, "Tersimpan di Kustom " + (n + 1), Toast.LENGTH_SHORT).show()
                    true
                }
            }
        }

        buildEqualizer(findViewById(R.id.eqRow))

        bindSeek(sbBass, txtBass) { prefs.bass = it }
        bindSeek(sbSurround, txtSurround) { prefs.surround = it }
        bindSeek(sbDialog, txtDialog) { prefs.dialog = it }
        bindSeek(sbVolume, txtVolume) { prefs.volume = it }

        swMaster.setOnCheckedChangeListener { _, checked ->
            if (!updating) {
                prefs.enabled = checked
                if (checked) startEffectService() else stopService(Intent(this, EffectService::class.java))
                refreshStatus()
            }
        }

        findViewById<View>(R.id.btnMore).setOnClickListener { showMenu(it) }

        loadValues()

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        if (prefs.enabled) startEffectService()
    }

    override fun onStart() {
        super.onStart()
        am.registerAudioDeviceCallback(deviceCb, handler)
        refreshStatus()
        handler.post(infoTick)
    }

    override fun onStop() {
        handler.removeCallbacks(infoTick)
        am.unregisterAudioDeviceCallback(deviceCb)
        super.onStop()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun hasDump(): Boolean =
        checkSelfPermission(EffectService.DUMP_PERMISSION) == PackageManager.PERMISSION_GRANTED

    private fun onPresetClick(i: Int) {
        if (i < Presets.ALL.size) {
            prefs.applyPreset(i)
        } else {
            val n = i - Presets.ALL.size
            if (!prefs.loadCustom(n)) {
                Toast.makeText(this, "Masih kosong. Tekan lama untuk menyimpan pengaturan sekarang.", Toast.LENGTH_LONG).show()
                return
            }
        }
        loadValues()
        pushApply()
    }

    /** Membuat sepuluh batang equalizer. */
    private fun buildEqualizer(row: LinearLayout) {
        for (i in 0 until Presets.BANDS) {
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER_HORIZONTAL

            val value = TextView(this)
            value.textSize = 11f
            value.setTextColor(getColor(R.color.text))
            value.setTypeface(value.typeface, Typeface.BOLD)
            value.gravity = Gravity.CENTER

            val slider = VerticalSlider(this)
            slider.contentDescription = "Equalizer " + Presets.LABELS[i] + " Hz"
            slider.onChange = { v ->
                prefs.setBand(i, v)
                prefs.preset = -1
                value.text = signed(v)
                showPreset()
                pushApply()
            }

            val freq = TextView(this)
            freq.textSize = 10f
            freq.setTextColor(getColor(R.color.text2))
            freq.text = Presets.LABELS[i]
            freq.gravity = Gravity.CENTER

            col.addView(value, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(150))
            lp.topMargin = dp(6)
            lp.bottomMargin = dp(6)
            col.addView(slider, lp)
            col.addView(freq, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

            row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            eqSliders.add(slider)
            eqValues.add(value)
        }
    }

    private fun bindSeek(sb: SeekBar, label: TextView, save: (Int) -> Unit) {
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                label.text = "$progress%"
                if (fromUser) {
                    save(progress)
                    prefs.preset = -1
                    showPreset()
                    pushApply()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun signed(v: Int): String = if (v > 0) "+$v" else "$v"

    /** Menampilkan nilai tersimpan ke layar. */
    private fun loadValues() {
        for (i in eqSliders.indices) {
            val v = prefs.band(i)
            eqSliders[i].value = v
            eqValues[i].text = signed(v)
        }
        sbBass.progress = prefs.bass
        sbSurround.progress = prefs.surround
        sbDialog.progress = prefs.dialog
        sbVolume.progress = prefs.volume
        txtBass.text = "${prefs.bass}%"
        txtSurround.text = "${prefs.surround}%"
        txtDialog.text = "${prefs.dialog}%"
        txtVolume.text = "${prefs.volume}%"
        showPreset()
    }

    private fun showPreset() {
        val sel = prefs.preset
        for (i in presetButtons.indices) {
            val b = presetButtons[i]
            b.isSelected = i == sel
            b.setTypeface(Typeface.create(b.typeface, if (i == sel) Typeface.BOLD else Typeface.NORMAL))
        }
    }

    /** Memperbarui kartu status sesuai keadaan Bluetooth dan tombol utama. */
    private fun refreshStatus() {
        val dev = Bt.find(am)
        val connected = dev != null
        val on = prefs.enabled
        val active = connected && on

        if (dev != null) {
            val name = Bt.name(dev)
            // Tiap perangkat Bluetooth punya pengaturan sendiri.
            if (prefs.profile != name) {
                prefs.profile = name
                loadValues()
            }
            txtBtState.text = "Bluetooth tersambung"
            txtBtName.text = name
            imgBt.setImageResource(R.drawable.ic_bt)
        } else {
            txtBtState.text = "Bluetooth tidak tersambung"
            txtBtName.text = "Menunggu perangkat"
            imgBt.setImageResource(R.drawable.ic_bt_off)
        }
        iconBox.setBackgroundResource(if (active) R.drawable.bg_icon_on else R.drawable.bg_icon_off)
        imgBt.imageTintList = ColorStateList.valueOf(getColor(if (active) R.color.bg else R.color.text2))

        if (!on) {
            txtFxTitle.text = "Soundmate dimatikan"
            txtFxSub.text = "Efek tidak akan menyala"
        } else if (connected) {
            txtFxTitle.text = "Efek suara aktif"
            txtFxSub.text = "Menyala otomatis saat Bluetooth tersambung"
        } else {
            txtFxTitle.text = "Efek suara mati"
            txtFxSub.text = "Speaker HP dan headset kabel tetap normal"
        }

        updating = true
        swMaster.isChecked = on
        updating = false

        for (b in presetButtons) b.isEnabled = active
        for (s in eqSliders) s.isEnabled = active
        val valueColor = getColor(if (active) R.color.text else R.color.dim)
        for (t in eqValues) t.setTextColor(valueColor)
        sbBass.isEnabled = active
        sbSurround.isEnabled = active
        sbDialog.isEnabled = active
        sbVolume.isEnabled = active

        updateInfo()
    }

    /** Keterangan kecil di bawah layar, berguna untuk memeriksa apakah efek berjalan. */
    private fun updateInfo() {
        if (!prefs.enabled) {
            txtInfo.text = ""
            return
        }
        val count = prefs.stSessions
        val engine = prefs.stEngine
        val line1 = if (count == 0) "Sesi audio: belum ada. Putar lagu setelah Soundmate aktif."
        else "Sesi audio: $count, mesin suara: $engine"
        val line2 = if (hasDump()) "Mode semua aplikasi: aktif"
        else "Mode semua aplikasi: belum diberi izin (lihat menu titik tiga)"
        txtInfo.text = line1 + "\n" + line2
    }

    private fun startEffectService() {
        try {
            startForegroundService(Intent(this, EffectService::class.java))
        } catch (t: Throwable) {
        }
    }

    /** Meminta layanan memakai pengaturan terbaru. */
    private fun pushApply() {
        if (!prefs.enabled) return
        try {
            startService(Intent(this, EffectService::class.java).setAction(EffectService.ACTION_APPLY))
        } catch (t: Throwable) {
        }
    }

    private fun showMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, 1, 0, "Mode semua aplikasi (izin ADB)")
        menu.menu.add(0, 2, 1, "Mode global (percobaan)").setCheckable(true).setChecked(prefs.global)
        menu.menu.add(0, 3, 2, "Tentang")
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> showAdbHelp()
                2 -> {
                    prefs.global = !prefs.global
                    if (prefs.enabled) {
                        try {
                            startService(Intent(this, EffectService::class.java).setAction(EffectService.ACTION_RELOAD))
                        } catch (t: Throwable) {
                        }
                    }
                }
                3 -> showAbout()
            }
            true
        }
        menu.show()
    }

    private fun showAdbHelp() {
        val status = if (hasDump()) "Status: izin SUDAH diberikan." else "Status: izin BELUM diberikan."
        AlertDialog.Builder(this)
            .setTitle("Mode semua aplikasi")
            .setMessage(
                status + "\n\n" +
                    "Dengan izin ini, efek dipasang ke semua aplikasi yang mengeluarkan suara " +
                    "(YouTube, game, pemutar video), bukan hanya aplikasi musik tertentu.\n\n" +
                    "Izin hanya bisa diberikan satu kali lewat ADB, tanpa root:\n\n" +
                    ADB_COMMAND + "\n\n" +
                    "Setelah itu matikan lalu nyalakan lagi tombol Soundmate."
            )
            .setPositiveButton("Salin perintah") { _, _ ->
                val cm = getSystemService(ClipboardManager::class.java)
                cm.setPrimaryClip(ClipData.newPlainText("adb", ADB_COMMAND))
                Toast.makeText(this, "Perintah disalin", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle("Soundmate 2.5")
            .setMessage(
                "Efek suara menyala otomatis saat headset atau speaker Bluetooth tersambung.\n\n" +
                    "Tiap perangkat Bluetooth menyimpan pengaturannya sendiri.\n\n" +
                    "Kustom 1 dan Kustom 2: tekan lama untuk menyimpan pengaturan sekarang, " +
                    "ketuk untuk memakainya lagi.\n\n" +
                    "Kalau efek belum terasa, hentikan lagu lalu putar lagi setelah Soundmate aktif.\n\n" +
                    "Mode global mencoba memasang efek dasar ke seluruh suara HP. " +
                    "Mode ini tidak berjalan di semua HP."
            )
            .setPositiveButton("Tutup", null)
            .show()
    }
}
