package com.skypin.soundmate

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Saat Bluetooth tersambung, layanan ini membuka pengaturan suara lalu memilih "Asli".
 *
 * Yang dibaca hanya layar Pengaturan. Soundmate tidak punya izin internet,
 * jadi isi layar tidak pernah dikirim ke mana pun.
 */
class SoundAccessibilityService : AccessibilityService() {

    companion object {
        /** Dipakai layar utama dan notifikasi untuk memeriksa apakah layanan sedang hidup. */
        @Volatile
        var instance: SoundAccessibilityService? = null

        private const val SETTINGS_PKG = "com.android.settings"
        private const val TICK_MS = 600L
        private const val TIMEOUT_MS = 25_000L
        private const val MAX_SCROLLS = 6
        private const val MAX_DEPTH = 40

        // Teks yang dicari di layar Pengaturan. Hanya baris pertama teks yang dibandingkan, tanpa membedakan huruf besar.
        private val SOUND_LABELS = listOf(
            "Suara & getaran", "Suara dan getaran", "Suara & getar", "Suara dan getar",
            "Sound & vibration", "Sound and vibration"
        )
        private val EFFECT_LABELS = listOf(
            "Efek suara", "Pilihan suara", "Sound effect", "Sound effects", "Efek audio", "Audio effects"
        )
        private val ASLI_LABELS = listOf("Asli")
    }

    private val handler = Handler(Looper.getMainLooper())
    private val am: AudioManager by lazy { getSystemService(AudioManager::class.java) }

    private var lastDevice: String? = null
    private var pendingDevice: String? = null

    // Keadaan pekerjaan yang sedang berjalan.
    private var busy = false
    private var targetDevice = ""
    private var clickedSound = false
    private var clickedEffect = false
    private var clickedAsli = false
    private var verifyTicks = 0
    private var scrolls = 0
    private var lastPkg = ""
    private var lastTexts = ""

    private val deviceCb = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            checkBluetooth()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            checkBluetooth()
        }
    }

    /** Layar dibuka lagi setelah terkunci: lanjutkan pekerjaan yang tertunda. */
    private val unlockRx = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val d = pendingDevice ?: return
            pendingDevice = null
            handler.postDelayed({
                if (Bt.find(am) != null) requestAsli(d)
            }, 1500L)
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            if (busy) handler.postDelayed(this, TICK_MS)
        }
    }

    private val timeout = Runnable { endJob(false, "waktu habis") }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        am.registerAudioDeviceCallback(deviceCb, handler)
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(unlockRx, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(unlockRx, filter)
        }
        checkBluetooth()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!busy || event == null) return
        if (event.packageName?.toString() != SETTINGS_PKG) return
        // Layar Pengaturan berubah: periksa lagi sebentar lagi.
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, 200L)
    }

    override fun onInterrupt() {
        endJob(false, "layanan dihentikan sistem")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        if (instance === this) instance = null
        busy = false
        handler.removeCallbacksAndMessages(null)
        try { am.unregisterAudioDeviceCallback(deviceCb) } catch (t: Throwable) {}
        try { unregisterReceiver(unlockRx) } catch (t: Throwable) {}
    }

    // ---- Bluetooth ----

    /** Memeriksa perangkat Bluetooth. Saat perangkat baru tersambung, pilihan Asli diatur. */
    private fun checkBluetooth() {
        val dev = Bt.find(am)
        val name = if (dev != null) Bt.name(dev) else null
        val before = lastDevice
        lastDevice = name
        if (name == null || name == before) return
        if (Prefs(this).enabled) requestAsli(name)
    }

    /** Menjalankan pemilihan Asli, atau menunggu kalau layar sedang terkunci. */
    private fun requestAsli(name: String) {
        if (busy) return
        val locked = !getSystemService(PowerManager::class.java).isInteractive ||
            getSystemService(KeyguardManager::class.java).isKeyguardLocked
        if (locked) {
            pendingDevice = name
            setStatus("Asli untuk $name: menunggu layar dibuka")
            return
        }
        startJob(name)
    }

    // ---- Pekerjaan memilih Asli ----

    private fun startJob(name: String) {
        busy = true
        targetDevice = name
        clickedSound = false
        clickedEffect = false
        clickedAsli = false
        verifyTicks = 0
        scrolls = 0
        lastPkg = ""
        lastTexts = ""
        setStatus("Asli untuk $name: membuka Pengaturan")

        val opened = try {
            startActivity(Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (t: Throwable) {
            false
        }
        if (!opened) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (t: Throwable) {
                endJob(false, "Pengaturan tidak bisa dibuka")
                return
            }
        }
        handler.postDelayed(ticker, 1200L)
        handler.postDelayed(timeout, TIMEOUT_MS)
    }

    /** Satu kali pemeriksaan layar. Dijalankan berulang sampai pekerjaan selesai. */
    private fun tick() {
        if (!busy) return
        val root = rootInActiveWindow ?: return
        try {
            lastPkg = root.packageName?.toString().orEmpty()
            if (lastPkg != SETTINGS_PKG) return
            lastTexts = visibleTexts(root)

            // 1. Pilihan Asli sudah kelihatan: pilih kalau belum terpilih.
            var found = false
            var checked = false
            walk(root, 0) { n ->
                if (labelMatches(n, ASLI_LABELS)) {
                    found = true
                    checked = n.isChecked || n.isSelected
                    if (!checked && !clickedAsli) clickedAsli = clickNode(n)
                    true
                } else {
                    false
                }
            }
            if (found) {
                if (checked) {
                    endJob(true, if (clickedAsli) "terpilih" else "sudah terpilih sebelumnya")
                } else if (clickedAsli) {
                    verifyTicks++
                    if (verifyTicks >= 3) endJob(true, "diklik, tapi tanda pilih belum terbaca di layar")
                }
                return
            }
            if (clickedAsli) {
                // Pilihan Asli hilang setelah diklik, biasanya karena halaman sudah tertutup.
                endJob(true, "diklik")
                return
            }

            // 2. Belum kelihatan: buka menu efek suara, lalu menu suara.
            var acted = false
            if (!clickedEffect) {
                walk(root, 0) { n ->
                    if (labelMatches(n, EFFECT_LABELS) && clickNode(n)) {
                        clickedEffect = true
                        acted = true
                        true
                    } else {
                        false
                    }
                }
            }
            if (!acted && !clickedSound) {
                walk(root, 0) { n ->
                    if (labelMatches(n, SOUND_LABELS) && clickNode(n)) {
                        clickedSound = true
                        acted = true
                        true
                    } else {
                        false
                    }
                }
            }
            if (acted) return

            // 3. Masih belum kelihatan: gulir daftar ke bawah.
            if (scrolls < MAX_SCROLLS) {
                walk(root, 0) { n ->
                    if (n.isScrollable && n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                        scrolls++
                        true
                    } else {
                        false
                    }
                }
            }
        } finally {
            root.recycle()
        }
    }

    /**
     * Menelusuri pohon layar dari [node]. Berhenti kalau [visit] mengembalikan true.
     * Node anak dikembalikan ke sistem setelah ditelusuri. Node [node] sendiri tidak.
     */
    @Suppress("DEPRECATION")
    private fun walk(node: AccessibilityNodeInfo, depth: Int, visit: (AccessibilityNodeInfo) -> Boolean): Boolean {
        if (visit(node)) return true
        if (depth >= MAX_DEPTH) return false
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val done = walk(child, depth + 1, visit)
            child.recycle()
            if (done) return true
        }
        return false
    }

    /** Mengklik node, atau induknya yang bisa diklik. Mengembalikan true kalau klik berhasil. */
    @Suppress("DEPRECATION")
    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        var p: AccessibilityNodeInfo? = node.parent
        var level = 0
        while (p != null && level < 6) {
            if (p.isClickable) {
                val ok = p.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                p.recycle()
                return ok
            }
            val next = p.parent
            p.recycle()
            p = next
            level++
        }
        p?.recycle()
        return false
    }

    private fun labelMatches(n: AccessibilityNodeInfo, labels: List<String>): Boolean {
        if (!n.isVisibleToUser) return false
        val a = firstLine(n.text)
        val b = firstLine(n.contentDescription)
        return labels.any { l ->
            (a != null && a.equals(l, ignoreCase = true)) || (b != null && b.equals(l, ignoreCase = true))
        }
    }

    private fun firstLine(text: CharSequence?): String? =
        text?.toString()?.lineSequence()?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    /** Teks yang terlihat di layar, untuk keterangan kalau pilihan Asli tidak ketemu. */
    private fun visibleTexts(root: AccessibilityNodeInfo): String {
        val out = ArrayList<String>()
        walk(root, 0) { n ->
            if (n.isVisibleToUser) {
                val t = firstLine(n.text)
                if (t != null && t.length <= 40 && t !in out) out.add(t)
            }
            out.size >= 12
        }
        return out.joinToString(" | ")
    }

    /** Mengakhiri pekerjaan, mencatat hasilnya, lalu keluar dari Pengaturan. */
    private fun endJob(ok: Boolean, detail: String) {
        if (!busy) return
        busy = false
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(timeout)
        val text = if (ok) {
            "Asli untuk $targetDevice: $detail"
        } else {
            "Asli untuk $targetDevice belum berhasil ($detail). " +
                "Layar: ${lastPkg.ifEmpty { "tidak terbaca" }}. ${lastTexts.take(300)}"
        }
        setStatus(text)
        leaveSettings(4)
    }

    /** Menekan Kembali sampai keluar dari Pengaturan, supaya layar kembali ke aplikasi sebelumnya. */
    private fun leaveSettings(left: Int) {
        handler.postDelayed({
            if (busy) return@postDelayed
            val root = rootInActiveWindow
            val inSettings = root?.packageName?.toString() == SETTINGS_PKG
            root?.recycle()
            if (inSettings && left > 0) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                leaveSettings(left - 1)
            }
        }, 500L)
    }

    /** Menyimpan status dan memperbarui notifikasi. */
    private fun setStatus(text: String) {
        Prefs(this).stAsli = text
        handler.post { EffectService.instance?.updateNotification() }
    }
}
