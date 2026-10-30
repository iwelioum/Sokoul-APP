// Sokoul pour Windows : application de bureau Compose (souris et clavier), lecture par libVLC (vlcj).
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

// Clés API (sokoul.key.* de local.properties, ignoré par Git) → classe générée dans build/, jamais commitée.
// Même principe que le BuildConfig de l'app Android : l'app installée fonctionne sans fichier de configuration.
val keysDir = layout.buildDirectory.dir("generated/keys")
val generateDesktopKeys by tasks.registering {
    val props = rootProject.file("local.properties")
    inputs.files(listOf(props).filter { it.exists() })
    outputs.dir(keysDir)
    doLast {
        val p = Properties()
        if (props.isFile) props.inputStream().use { p.load(it) }
        val keys = p.stringPropertyNames().filter { it.startsWith("sokoul.key.") }.sorted()
            .associate { it.removePrefix("sokoul.key.") to p.getProperty(it).trim() }
        fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")
        val body = keys.entries.joinToString(",\n") { (k, v) -> "        \"${esc(k)}\" to \"${esc(v)}\"" }
        val out = keysDir.get().file("com/sokoul/desktop/data/DesktopKeys.kt").asFile
        out.parentFile.mkdirs()
        out.writeText(
            "package com.sokoul.desktop.data\n\n" +
                "/** Généré au build depuis local.properties : ne pas commiter, ne jamais journaliser. */\n" +
                "internal object DesktopKeys {\n    val all: Map<String, String> = mapOf(\n$body\n    )\n}\n",
        )
    }
}
sourceSets["main"].kotlin.srcDir(keysDir)
tasks.named("compileKotlin") { dependsOn(generateDesktopKeys) }

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.vlcj)
    implementation(libs.sqlite.jdbc)
    // XmlPullParser (guide XMLTV de :parser) : fourni par Android, à embarquer sur PC
    implementation(libs.kxml2)

    testImplementation(libs.junit)
    // tests d'interface sans fenêtre (runComposeUiTest) : navigation, Échap, recherche
    @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
    testImplementation(compose.uiTest)
}

compose.desktop {
    application {
        mainClass = "com.sokoul.desktop.MainKt"
        jvmArgs += listOf("-Xmx1g", "-Dfile.encoding=UTF-8")
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Sokoul"
            packageVersion = "1.0.0"
            description = "Sokoul TV pour Windows"
            vendor = "Sokoul"
            // libVLC (VLC 3.0.21 win64) copié dans les ressources de l'image : rien à installer dans Windows
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
            // jdk.localedata : formats français (virgule décimale, jours et mois) dans l'app installée
            modules("java.instrument", "java.sql", "java.naming", "jdk.crypto.ec", "jdk.unsupported", "jdk.localedata")
            windows {
                menu = true
                shortcut = true
                menuGroup = "Sokoul"
                upgradeUuid = "6c1f2f2e-6a2b-4f0e-9a4e-5d3b8f1c2a77"
                iconFile.set(project.layout.projectDirectory.file("icon/sokoul.ico"))
            }
        }
    }
}
