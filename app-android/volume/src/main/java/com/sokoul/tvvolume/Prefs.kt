package com.sokoul.tvvolume

import android.content.Context

/** Réglages gardés sur la box seulement : adresse de la TV et, en option, son adresse MAC (réveil réseau). */
class Prefs(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("volume_tcl", Context.MODE_PRIVATE)

    var tvIp: String
        get() = p.getString(KEY_IP, "").orEmpty()
        set(v) { p.edit().putString(KEY_IP, v.trim()).apply() }

    var tvMac: String
        get() = p.getString(KEY_MAC, "").orEmpty()
        set(v) { p.edit().putString(KEY_MAC, v.trim()).apply() }

    private companion object {
        const val KEY_IP = "tv_ip"
        const val KEY_MAC = "tv_mac"
    }
}
