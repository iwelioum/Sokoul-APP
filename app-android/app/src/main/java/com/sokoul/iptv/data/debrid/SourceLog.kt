package com.sokoul.iptv.data.debrid

import android.util.Log
import com.sokoul.iptv.data.net.HttpModule
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Journal des sources : recherche, résolution, lecture, échecs. Les 50 derniers événements, masqués
 * (jamais de clé ni d'identifiant : [HttpModule.redact]), gardés en mémoire et sur disque, et écrits dans logcat
 * (tag `SokoulSrc`) même en version release : on peut comprendre une panne sur la box sans version de débogage.
 */
object SourceLog {
    const val TAG = "SokoulSrc"
    const val MAX = 50

    data class Entry(val atMs: Long, val text: String)

    private val entries = ArrayDeque<Entry>()
    private var file: File? = null
    /** Remplaçable dans les tests (pas d'android.util.Log sur la JVM). Log direct : Timber n'écrit rien en release. */
    @android.annotation.SuppressLint("LogNotTimber")
    internal var logcat: (String) -> Unit = { runCatching { Log.i(TAG, it) } }

    /** Fichier de sauvegarde ; relit les événements précédents. */
    @Synchronized fun attach(f: File) {
        file = f
        runCatching {
            if (f.exists()) f.readLines().takeLast(MAX).forEach { line ->
                val at = line.substringBefore('\t').toLongOrNull() ?: return@forEach
                entries.addLast(Entry(at, line.substringAfter('\t')))
            }
        }
        while (entries.size > MAX) entries.removeFirst()
    }

    /** Ajoute un événement masqué. */
    @Synchronized fun add(text: String, now: Long = System.currentTimeMillis()) {
        val clean = HttpModule.redact(text).replace('\n', ' ').replace('\t', ' ').take(400)
        entries.addLast(Entry(now, clean))
        while (entries.size > MAX) entries.removeFirst()
        logcat(clean)
        file?.let { f -> runCatching { f.writeText(entries.joinToString("\n") { "${it.atMs}\t${it.text}" }) } }
    }

    /** Du plus récent au plus ancien. */
    @Synchronized fun recent(): List<Entry> = entries.reversed()

    @Synchronized fun clear() {
        entries.clear()
        file?.let { runCatching { it.delete() } }
    }

    fun format(e: Entry): String = SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE).format(Date(e.atMs)) + "  " + e.text

    /** Raison courte et lisible d'une erreur (code HTTP compris), pour le panneau et le journal. */
    fun reason(e: Throwable): String {
        val code = when (e) {
            is RdException -> e.code
            is com.sokoul.iptv.data.net.HttpException -> e.code
            else -> null
        }
        val base = HttpModule.redact(e.message ?: e.javaClass.simpleName)
        return when (code) {
            null -> base
            401, 403 -> if (e is RdException) base else "accès refusé ($code)"
            429 -> "limite atteinte (429)"
            else -> if (base.contains(code.toString())) base else "$base ($code)"
        }
    }
}
