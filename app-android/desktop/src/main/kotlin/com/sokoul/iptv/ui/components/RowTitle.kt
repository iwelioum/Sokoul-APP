package com.sokoul.iptv.ui.components

/** Titre de rangée : la partie [emphasis] (nom d'une œuvre) passe en italique. */
fun rowTitle(title: String, emphasis: String?): androidx.compose.ui.text.AnnotatedString {
    val i = emphasis?.takeIf { it.isNotBlank() }?.let { title.indexOf(it) } ?: -1
    return androidx.compose.ui.text.buildAnnotatedString {
        if (i < 0) { append(title); return@buildAnnotatedString }
        append(title.substring(0, i))
        pushStyle(androidx.compose.ui.text.SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
        append(emphasis!!)
        pop()
        append(title.substring(i + emphasis.length))
    }
}
