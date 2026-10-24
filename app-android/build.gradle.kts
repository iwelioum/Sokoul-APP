plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}

// Le projet vit dans OneDrive : on sort les dossiers build/ de la synchro
// pour éviter les fichiers verrouillés et les crashs du daemon Kotlin.
val externalBuildRoot = System.getenv("LOCALAPPDATA")?.let { File(it, "SokoulBuild") }
if (externalBuildRoot != null) {
    allprojects {
        val name = if (path == ":") "root" else path.removePrefix(":").replace(':', '_')
        layout.buildDirectory.set(File(externalBuildRoot, name))
    }
}
