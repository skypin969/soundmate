package com.skypin.soundmate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Layanan latar belakang: memantau Bluetooth dan memasang efek suara.
 * Efek hanya menyala saat headset atau speaker Bluetooth tersambung.
 */
class EffectService : Service() {

    companion object {
        /** Layanan yang sedang berjalan. Dipakai layanan Aksesibilitas untuk memperbarui notifikasi. */
        @Volatile
        var instance: EffectService? = null

        const val ACTION_APPLY = "com.skypin.soundmate.APPLY"
        const val ACTION_RELOAD = "com.skypin.soundmate.RELOAD"
        const val DUMP_PERMISSION = "android.permission.DUMP"
        private const val CHANNEL = "soundmate"
        private const val NOTIF_ID = 1
        private const val POLL_MS = 3000L
    }

    private class Entry(val fx: FxSet, var fromBroadcast: Boolean, var fromPoll: Boolean)

    private lateinit var prefs: Prefs
    private lateinit var am: AudioManager
    private val sessions = HashMap<Int, Entry>()
    private var btName: String? = null

    private val handler = Handler(Looper.getMainLooper())
    private val exec: ExecutorService = Executors.newSingleThreadExecutor()
    private var polling = false
    private var destroyed = false

    private val deviceCb = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            onBtChanged()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            onBtChanged()
        }
    }

    /** Aplikasi musik memberi tahu lewat siaran ini saat mulai dan berhenti memutar. */
    private val sessionRx = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
            if (id <= 0) return
            when (intent.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> openFromBroadcast(id)
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> closeFromBroadcast(id)
            }
        }
    }

    /** Mode semua aplikasi: memeriksa daftar pemutar suara tiap beberapa detik. */
    private val pollTask = object : Runnable {
        override fun run() {
            if (destroyed) return
            if (btName != null && hasDump()) pollOnce()
            handler.postDelayed(this, POLL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        am = getSystemService(AudioManager::class.java)
        instance = this

        createChannel()
        goForeground()

        val filter = IntentFilter()
        filter.addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
        filter.addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(sessionRx, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(sessionRx, filter)
        }

        am.registerAudioDeviceCallback(deviceCb, handler)

        setGlobal(prefs.global)
        onBtChanged()
        handler.postDelayed(pollTask, 500L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Dipanggil ulang setiap kali aplikasi atau BtReceiver membangunkan layanan, jadi harus tetap di latar depan.
        goForeground()
        if (intent?.action == ACTION_RELOAD) setGlobal(prefs.global)
        applyAll()
        return START_STICKY
    }

    override fun onDestroy() {
        instance = null
        destroyed = true
        handler.removeCallbacks(pollTask)
        exec.shutdownNow()
        try { unregisterReceiver(sessionRx) } catch (t: Throwable) {}
        try { am.unregisterAudioDeviceCallback(deviceCb) } catch (t: Throwable) {}
        for (e in sessions.values) e.fx.release()
        sessions.clear()
        prefs.stSessions = 0
        prefs.stEngine = ""
        super.onDestroy()
    }

    private fun hasDump(): Boolean =
        checkSelfPermission(DUMP_PERMISSION) == PackageManager.PERMISSION_GRANTED

    // ---- Sesi audio ----

    private fun addSession(id: Int, broadcast: Boolean, poll: Boolean) {
        val old = sessions[id]
        if (old != null) {
            if (broadcast) old.fromBroadcast = true
            if (poll) old.fromPoll = true
            return
        }
        // Sesi 0 (seluruh suara HP) hanya bisa memakai efek dasar.
        val fx = FxSet(id, id != 0)
        sessions[id] = Entry(fx, broadcast, poll)
        fx.apply(prefs, btName != null)
        reportStatus()
    }

    private fun removeSession(id: Int) {
        val e = sessions.remove(id)
        if (e != null) {
            e.fx.release()
            reportStatus()
        }
    }

    private fun openFromBroadcast(id: Int) {
        addSession(id, true, false)
    }

    private fun closeFromBroadcast(id: Int) {
        val e = sessions[id] ?: return
        e.fromBroadcast = false
        if (!e.fromPoll) removeSession(id)
    }

    private fun setGlobal(on: Boolean) {
        if (on) addSession(0, true, false) else removeSession(0)
    }

    private fun applyAll() {
        val active = btName != null
        for (e in sessions.values) e.fx.apply(prefs, active)
    }

    private fun onBtChanged() {
        val dev = Bt.find(am)
        val name = if (dev != null) Bt.name(dev) else null
        btName = name
        // Tiap perangkat Bluetooth punya pengaturan sendiri.
        if (name != null && prefs.profile != name) prefs.profile = name
        applyAll()
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    private fun reportStatus() {
        var dp = false
        for (e in sessions.values) if (e.fx.usesDp) dp = true
        prefs.stSessions = sessions.size
        prefs.stEngine = if (sessions.isEmpty()) "" else if (dp) "DynamicsProcessing" else "efek dasar"
    }

    // ---- Mode semua aplikasi (izin DUMP lewat ADB) ----

    private fun pollOnce() {
        if (polling) return
        polling = true
        try {
            exec.execute {
                val ids = readSessions()
                handler.post {
                    polling = false
                    if (!destroyed && ids != null) reconcile(ids)
                }
            }
        } catch (t: Throwable) {
            polling = false
        }
    }

    /** Membaca nomor sesi semua pemutar suara yang sedang aktif. */
    private fun readSessions(): Set<Int>? {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("dumpsys", "audio"))
            val text = proc.inputStream.bufferedReader().use { it.readText() }
            proc.waitFor()
            if (text.contains("Permission Denial")) return null
            val reSession = Regex("sessionId:(\\d+)")
            val reState = Regex("state:(\\w+)")
            val out = HashSet<Int>()
            for (line in text.lineSequence()) {
                if (!line.contains("u/pid:")) continue
                val state = reState.find(line)?.groupValues?.get(1) ?: continue
                if (state != "started" && state != "paused") continue
                val id = reSession.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: continue
                if (id > 0) out.add(id)
            }
            out
        } catch (t: Throwable) {
            null
        }
    }

    private fun reconcile(ids: Set<Int>) {
        for (id in ids) addSession(id, false, true)
        var changed = false
        val it = sessions.entries.iterator()
        while (it.hasNext()) {
            val item = it.next()
            val e = item.value
            if (e.fromPoll && !ids.contains(item.key)) {
                e.fromPoll = false
                if (!e.fromBroadcast) {
                    e.fx.release()
                    it.remove()
                    changed = true
                }
            }
        }
        if (changed) reportStatus()
    }

    // ---- Notifikasi ----

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL, "Status Soundmate", NotificationManager.IMPORTANCE_LOW)
        ch.setShowBadge(false)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    /** Memperbarui teks notifikasi, misalnya setelah hasil pemilihan Asli berubah. */
    fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    /** Masuk ke mode latar depan dengan notifikasi terbaru. */
    private fun goForeground() {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun buildNotification(): Notification {
        val name = btName
        val base = if (name != null) "Efek aktif: $name" else "Menunggu Bluetooth tersambung"
        val asli = prefs.stAsli
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Soundmate")
            .setContentText(base)
            .setContentIntent(open)
            .setOngoing(true)
        if (asli.isNotEmpty()) {
            b.setStyle(Notification.BigTextStyle().bigText(base + "\n" + asli))
        }
        return b.build()
    }
}
