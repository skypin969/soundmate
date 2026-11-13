package com.skypin.soundmate

import android.media.AudioDeviceInfo
import android.media.AudioManager

/** Mencari keluaran suara Bluetooth (headset atau speaker). */
object Bt {
    // A2DP, BLE headset (26), BLE speaker (27), BLE broadcast (30)
    private val TYPES = intArrayOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, 26, 27, 30)

    fun find(am: AudioManager): AudioDeviceInfo? {
        val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        for (d in devices) {
            if (TYPES.contains(d.type)) return d
        }
        return null
    }

    fun name(d: AudioDeviceInfo): String {
        val n = d.productName?.toString()?.trim()
        return if (n.isNullOrEmpty()) "Perangkat Bluetooth" else n
    }
}
