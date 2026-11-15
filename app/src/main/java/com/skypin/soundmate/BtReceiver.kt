package com.skypin.soundmate

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Saat Bluetooth tersambung, membangunkan layanan efek kalau sistem sudah mematikannya,
 * misalnya setelah aplikasi dihapus dari Recents. Siaran ACL_CONNECTED termasuk yang
 * tetap dikirim ke receiver di manifest pada Android 8 ke atas.
 */
class BtReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        if (!Prefs(context).enabled) return
        try {
            context.startForegroundService(Intent(context, EffectService::class.java))
        } catch (t: Throwable) {
        }
    }
}
