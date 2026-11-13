package com.skypin.soundmate

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
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

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var am: AudioManager

    private lateinit var iconBox: View
    private lateinit var imgBt: ImageView
    private lateinit var txtBtState: TextView
    private lateinit var txtBtName: TextView
    private lateinit var txtFxTitle: TextView
    private lateinit var txtFxSub: TextView
    private lateinit var swMaster: Switch
    private lateinit var sbBass: SeekBar
    private lateinit var sbSurround: SeekBar
    private lateinit var sbVolume: SeekBar
    private lateinit var txtBass: TextView
    private lateinit var txtSurround: TextView
    private lateinit var txtVolume: TextView

    private val presetButtons = ArrayList<Button>()
    private val eqSliders = ArrayList<VerticalSlider>()
    private val eqValues = ArrayList<TextView>()
    private var updating = false

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
        swMaster = findViewById(R.id.swMaster)
        sbBass = findViewById(R.id.sbBass)
        sbSurround = findViewById(R.id.sbSurround)
        sbVolume = findViewById(R.id.sbVolume)
        txtBass = findViewById(R.id.txtBass)
        txtSurround = findViewById(R.id.txtSurround)
        txtVolume = findViewById(R.id.txtVolume)

        presetButtons.add(findViewById(R.id.btnP0))
        presetButtons.add(findViewById(R.id.btnP1))
        presetButtons.add(findViewById(R.id.btnP2))
        presetButtons.add(findViewById(R.id.btnP3))
        for (i in presetButtons.indices) {
            presetButtons[i].setOnClickListener {
                prefs.applyPreset(i)
                loadValues()
                pushApply()
            }
        }

        buildEqualizer(findViewById(R.id.eqRow))

        bindSeek(sbBass, txtBass) { prefs.bass = it }
        bindSeek(sbSurround, txtSurround) { prefs.surround = it }
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
        am.registerAudioDeviceCallback(deviceCb, Handler(Looper.getMainLooper()))
        refreshStatus()
    }

    override fun onStop() {
        am.unregisterAudioDeviceCallback(deviceCb)
        super.onStop()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /** Membuat lima batang equalizer. */
    private fun buildEqualizer(row: LinearLayout) {
        for (i in Presets.FREQ.indices) {
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER_HORIZONTAL

            val value = TextView(this)
            value.textSize = 13f
            value.setTextColor(getColor(R.color.text))
            value.setTypeface(value.typeface, Typeface.BOLD)
            value.gravity = Gravity.CENTER

            val slider = VerticalSlider(this)
            slider.contentDescription = "Equalizer " + Presets.LABELS[i]
            slider.onChange = { v ->
                prefs.setBand(i, v)
                prefs.preset = -1
                value.text = signed(v)
                showPreset()
                pushApply()
            }

            val freq = TextView(this)
            freq.textSize = 12f
            freq.setTextColor(getColor(R.color.text2))
            freq.text = Presets.LABELS[i]
            freq.gravity = Gravity.CENTER

            col.addView(value, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            val lp = LinearLayout.LayoutParams(dp(44), dp(150))
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
        sbVolume.progress = prefs.volume
        txtBass.text = "${prefs.bass}%"
        txtSurround.text = "${prefs.surround}%"
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
            txtBtState.text = "Bluetooth tersambung"
            txtBtName.text = Bt.name(dev)
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
        sbVolume.isEnabled = active
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
        menu.menu.add(0, 1, 0, "Mode semua aplikasi (percobaan)").setCheckable(true).setChecked(prefs.global)
        menu.menu.add(0, 2, 1, "Tentang")
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    prefs.global = !prefs.global
                    if (prefs.enabled) {
                        try {
                            startService(Intent(this, EffectService::class.java).setAction(EffectService.ACTION_RELOAD))
                        } catch (t: Throwable) {
                        }
                    }
                }
                2 -> showAbout()
            }
            true
        }
        menu.show()
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle("Soundmate 1.0")
            .setMessage(
                "Efek suara menyala otomatis saat headset atau speaker Bluetooth tersambung.\n\n" +
                    "Kalau efek belum terasa, hentikan lagu lalu putar lagi setelah Soundmate aktif.\n\n" +
                    "Mode semua aplikasi mencoba memasang efek ke seluruh suara HP. " +
                    "Mode ini tidak berjalan di semua HP."
            )
            .setPositiveButton("Tutup", null)
            .show()
    }
}
