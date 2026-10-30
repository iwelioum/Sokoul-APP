package com.sokoul.iptv.data.debrid

import java.io.IOException

// Modèles Real-Debrid, communs à l'app Android (`RealDebridClient`) et à l'app PC.

data class RdUser(val username: String, val premium: Boolean, val expiresAtMs: Long) {
    fun daysLeft(now: Long): Int = if (!premium) 0 else ((expiresAtMs - now) / 86_400_000L).toInt().coerceAtLeast(0)
}

data class RdFile(val id: Int, val path: String, val bytes: Long, val selected: Boolean) {
    val name get() = path.substringAfterLast('/')
}

data class RdTorrent(
    val id: String, val filename: String, val hash: String = "", val status: String, val progress: Int,
    val bytes: Long, val files: List<RdFile>, val links: List<String>, val addedAt: Long,
) {
    val ready get() = status == "downloaded"
}

data class RdLink(val url: String, val filename: String, val bytes: Long, val mime: String?)

data class RdDownload(val id: String, val filename: String, val bytes: Long, val url: String, val generatedAt: Long)

data class RdDeviceCode(val deviceCode: String, val userCode: String, val verificationUrl: String, val intervalS: Int, val expiresAtMs: Long)

class RdException(val code: Int, message: String) : IOException(message)
