package com.sokoul.iptv.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun formatter(pattern: String) = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue() = SimpleDateFormat(pattern, Locale.FRANCE)
}

private val clock = formatter("HH:mm")
private val day = formatter("EEE d MMM")
private val full = formatter("EEE d MMM 'à' HH:mm")

fun formatClock(t: Long): String = clock.get()!!.format(Date(t))
fun formatRange(start: Long, end: Long) = "${formatClock(start)} – ${formatClock(end)}"
private val fullYear = formatter("EEE d MMM yyyy")
fun formatDateTime(t: Long): String = full.get()!!.format(Date(t))
/** Date d'échéance : avec l'année, sinon « 2 juin » est ambigu pour un abonnement d'un an. */
fun formatDate(t: Long): String = fullYear.get()!!.format(Date(t))

fun dayLabel(t: Long, now: Long = System.currentTimeMillis()): String {
    val a = Calendar.getInstance().apply { timeInMillis = t }
    val b = Calendar.getInstance().apply { timeInMillis = now }
    val diff = ((startOfDay(a) - startOfDay(b)) / (24 * 3600_000L)).toInt()
    return when (diff) {
        0 -> "Aujourd'hui"
        -1 -> "Hier"
        1 -> "Demain"
        else -> day.get()!!.format(Date(t)).replaceFirstChar { it.uppercase() }
    }
}

private fun startOfDay(c: Calendar): Long = (c.clone() as Calendar).apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

fun formatMinutes(min: Int): String = if (min >= 60) "${min / 60} h ${"%02d".format(min % 60)}" else "$min min"

fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f Go".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.0f Mo".format(bytes / (1L shl 20).toDouble())
    else -> "${bytes / 1024} Ko"
}

/** Heure courante rafraîchie toutes les 30 s (horloge, barres de progression EPG). */
@Composable
fun rememberNow(periodMs: Long = 30_000): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            delay(periodMs - System.currentTimeMillis() % periodMs)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** « Finit à 23h28 » : heure de fin si l'on lance maintenant [minutes] de programme. */
fun endsAt(minutes: Int, nowMs: Long = System.currentTimeMillis()): String {
    val c = Calendar.getInstance().apply { timeInMillis = nowMs + minutes * 60_000L }
    return "Finit à %02dh%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
}
