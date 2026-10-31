package com.sokoul.desktop.player

/** Rectangle de dessin de la vidéo selon le format d'image choisi (logique pure, testée). */
object VideoFit {
    data class Rect(val x: Float, val y: Float, val w: Float, val h: Float)

    /**
     * [sourceAspect] = largeur / hauteur affichées de la vidéo (pixels non carrés compris).
     * Ajusté : toute l'image, bandes noires ; Remplir : tout l'écran, image rognée ; 16:9 et 4:3 : rapport forcé.
     */
    fun rect(boxW: Float, boxH: Float, sourceAspect: Float, mode: VideoAspect): Rect {
        val a = mode.ratio ?: sourceAspect
        val fill = mode == VideoAspect.FILL
        val byWidth = (boxW / a <= boxH) != fill
        val w = if (byWidth) boxW else boxH * a
        val h = if (byWidth) boxW / a else boxH
        return Rect((boxW - w) / 2, (boxH - h) / 2, w, h)
    }
}
