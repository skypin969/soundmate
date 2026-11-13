package com.skypin.soundmate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Menyalakan lagi Soundmate setelah HP dihidupkan ulang. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs(context).enabled) return
        try {
            context.startForegroundService(Intent(context, EffectService::class.java))
        } catch (t: Throwable) {
        }
    }
}
