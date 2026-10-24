plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    systemProperty("realWorld", System.getProperty("realWorld") ?: "false")
    testLogging { showStandardStreams = true }
}

dependencies {
    // XmlPullParser est fourni par Android ; on ne compile que contre l'interface.
    compileOnly(libs.xmlpull)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kxml2)
}
