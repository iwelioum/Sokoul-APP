package com.sokoul.tvvolume

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/** Touches envoyées à la TV (noms du protocole TCL « nScreen »). */
enum class TvKey(val code: String, val label: String) {
    VOL_UP("TR_KEY_VOL_UP", "Volume +"),
    VOL_DOWN("TR_KEY_VOL_DOWN", "Volume −"),
    MUTE("TR_KEY_MUTE", "Muet"),
    POWER("TR_KEY_POWER", "Veille"),
}

/**
 * Protocole des TV TCL non Android (celui de Magic Connect / nScreen) : une connexion TCP sur le port 4123 et un
 * message XML `setKey` par touche, sans réponse attendue.
 */
object TclProtocol {
    const val PORT = 4123

    fun message(key: TvKey): ByteArray =
        ("<?xml version=\"1.0\" encoding=\"utf-8\"?><root><action name=\"setKey\" eventAction=\"${key.code}\" keyCode=\"${key.code}\" /></root>")
            .toByteArray(Charsets.UTF_8)

    /** Paquet « magique » Wake-on-LAN : 6 × 0xFF puis 16 fois l'adresse MAC. Null si l'adresse est mal écrite. */
    fun magicPacket(mac: String): ByteArray? {
        val hex = mac.filter { it.isLetterOrDigit() }
        if (hex.length != 12 || !hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        val bytes = ByteArray(6) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        return ByteArray(6) { 0xFF.toByte() } + ByteArray(16 * 6) { bytes[it % 6] }
    }

    /** Adresse privée IPv4 (192.168.x.x, 10.x.x.x, 172.16-31.x.x) : la TV n'est jamais cherchée ailleurs. */
    fun isPrivateIpv4(host: String): Boolean {
        val p = host.trim().split('.').map { it.toIntOrNull() ?: return false }
        if (p.size != 4 || p.any { it !in 0..255 }) return false
        return p[0] == 10 || (p[0] == 192 && p[1] == 168) || (p[0] == 172 && p[1] in 16..31)
    }
}

/** Envoi des touches à la TV. Bloquant : toujours appelé hors du fil principal. */
class TclRemote(private val port: Int = TclProtocol.PORT, private val timeoutMs: Int = 1_500) {

    /** @return false si la TV ne répond pas (éteinte, autre réseau, mauvaise adresse). */
    fun send(host: String, key: TvKey): Boolean = runCatching {
        Socket().use { s ->
            s.tcpNoDelay = true
            s.connect(InetSocketAddress(host, port), timeoutMs)
            s.soTimeout = timeoutMs
            s.getOutputStream().apply { write(TclProtocol.message(key)); flush() }
            // la TV lit le message après la connexion : on la laisse le recevoir avant de fermer
            s.shutdownOutput()
        }
        true
    }.getOrDefault(false)

    /** La TV écoute-t-elle sur son port de commande ? */
    fun reachable(host: String, timeout: Int = timeoutMs): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress(host, port), timeout) }
        true
    }.getOrDefault(false)

    /** Réveil réseau (si la TV a une « veille réseau ») : envoyé en diffusion sur le réseau local. */
    fun wake(mac: String, broadcast: String? = null): Boolean {
        val packet = TclProtocol.magicPacket(mac) ?: return false
        return runCatching {
            DatagramSocket().use { s ->
                s.broadcast = true
                listOfNotNull("255.255.255.255", broadcast).distinct().forEach { addr ->
                    for (p in listOf(9, 7)) s.send(DatagramPacket(packet, packet.size, InetAddress.getByName(addr), p))
                }
            }
            true
        }.getOrDefault(false)
    }
}
