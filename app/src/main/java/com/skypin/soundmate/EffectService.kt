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
import android.content.pm.ServiceInfo
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * Layanan latar belakang: memantau Bluetooth dan memasang efek suara.
 * Efek hanya menyala saat headset atau speaker Bluetooth tersambung.
 */
class EffectService : Service() {

    companion object {
        const val ACTION_APPLY = "com.skypin.soundmate.APPLY"
        const val ACTION_RELOAD = "com.skypin.soundmate.RELOAD"
        private const val CHANNEL = "soundmate"
        private const val NOTIF_ID = 1
    }

    private lateinit var prefs: Prefs
    private lateinit var am: AudioManager
    private val sessions = HashMap<Int, FxSet>()
    private var btName: String? = null

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
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> openSession(id)
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> closeSession(id)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        am = getSystemService(AudioManager::class.java)

        createChannel()
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }

        val filter = IntentFilter()
        filter.addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
        filter.addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(sessionRx, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(sessionRx, filter)
        }

        am.registerAudioDeviceCallback(deviceCb, Handler(Looper.getMainLooper()))

        if (prefs.global) openSession(0)
        onBtChanged()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RELOAD) {
            if (prefs.global) openSession(0) else closeSession(0)
        }
        applyAll()
        return START_STICKY
    }

    override fun onDestroy() {
        try { unregisterReceiver(sessionRx) } catch (t: Throwable) {}
        try { am.unregisterAudioDeviceCallback(deviceCb) } catch (t: Throwable) {}
        for (fx in sessions.values) fx.release()
        sessions.clear()
        super.onDestroy()
    }

    private fun openSession(id: Int) {
        if (sessions.containsKey(id)) return
        val fx = FxSet(id)
        sessions[id] = fx
        fx.apply(prefs, btName != null)
    }

    private fun closeSession(id: Int) {
        sessions.remove(id)?.release()
    }

    private fun applyAll() {
        val active = btName != null
        for (fx in sessions.values) fx.apply(prefs, active)
    }

    private fun onBtChanged() {
        val dev = Bt.find(am)
        btName = if (dev != null) Bt.name(dev) else null
        applyAll()
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL, "Status Soundmate", NotificationManager.IMPORTANCE_LOW)
        ch.setShowBadge(false)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val name = btName
        val text = if (name != null) "Efek aktif: $name" else "Menunggu Bluetooth tersambung"
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Soundmate")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }
}
