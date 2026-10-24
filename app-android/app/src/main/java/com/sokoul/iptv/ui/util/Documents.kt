package com.sokoul.iptv.ui.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Le sélecteur de documents système est-il réellement disponible ?
 * Sur Android TV / Google TV, l'action existe mais pointe vers un composant vide (« frameworkpackagestubs »).
 */
fun Context.hasDocumentPicker(): Boolean {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
    val info = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) ?: return false
    return !info.activityInfo.packageName.contains("frameworkpackagestubs")
}
