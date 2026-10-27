package com.sokoul.tvvolume

import android.content.Context
import android.net.ConnectivityManager
import java.net.Inet4Address
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Cherche la TV sur le réseau local : les adresses du même réseau qui écoutent sur le port 4123. */
object TvFinder {
    /** Adresse IPv4 de la box et longueur du préfixe réseau (« 192.168.1.20 », 24). */
    fun localNetwork(context: Context): Pair<String, Int>? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val lp = cm.getLinkProperties(cm.activeNetwork) ?: return null
        return lp.linkAddresses.firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress }
            ?.let { it.address.hostAddress.orEmpty() to it.prefixLength }
    }

    /** Adresses voisines à essayer : le /24 de la box (sans elle-même). */
    fun candidates(selfIp: String): List<String> {
        val parts = selfIp.split('.')
        if (parts.size != 4 || !TclProtocol.isPrivateIpv4(selfIp)) return emptyList()
        val base = parts.take(3).joinToString(".")
        return (1..254).map { "$base.$it" }.filter { it != selfIp }
    }

    suspend fun scan(selfIp: String, remote: TclRemote = TclRemote(), timeoutMs: Int = 400): List<String> = withContext(Dispatchers.IO) {
        val gate = Semaphore(48)
        coroutineScope {
            candidates(selfIp).map { ip -> async { gate.withPermit { ip.takeIf { remote.reachable(it, timeoutMs) } } } }.awaitAll().filterNotNull()
        }
    }
}
