package com.sokoul.desktop.data

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryFlag
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView

/**
 * Dossier des données de Sokoul sur le PC (`%APPDATA%\Sokoul`) : base, réglages, journaux.
 * Les identifiants de playlist et les clés y sont stockés ; l'accès est réservé au compte Windows courant.
 */
object AppDirs {
    val data: File by lazy {
        val base = System.getenv("APPDATA")?.let(::File) ?: File(System.getProperty("user.home"), ".sokoul")
        File(base, "Sokoul").also { prepare(it) }
    }

    /** Crée le dossier et, sur Windows, ne laisse que le propriétaire (héritage vers les fichiers créés ensuite). */
    fun prepare(dir: File) {
        dir.mkdirs()
        restrictToOwner(dir)
    }

    fun restrictToOwner(dir: File) {
        val path = dir.toPath()
        val view = Files.getFileAttributeView(path, AclFileAttributeView::class.java) ?: return // hors Windows : rien
        runCatching {
            val owner = view.owner
            val entry = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(AclEntryPermission.entries.toSet())
                .setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT)
                .build()
            view.acl = listOf(entry)
        }
    }
}
