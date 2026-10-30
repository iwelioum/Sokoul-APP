// Sokoul pour Windows : application de bureau Compose (souris et clavier), lecture par libVLC (vlcj).
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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
            modules("java.instrument", "java.sql", "java.naming", "jdk.crypto.ec", "jdk.unsupported")
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
