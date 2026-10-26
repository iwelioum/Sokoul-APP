import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "com.sokoul.iptv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sokoul.iptv"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "2.0.0"
        vectorDrawables.useSupportLibrary = true

        // Clés des services de données (TheTVDB, fanart.tv…) : local.properties, jamais versionné.
        // Absentes → chaîne vide → le service reste simplement inactif. Modifiables ensuite dans Réglages.
        val localProps = Properties().also { p ->
            rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { p.load(it) }
        }
        listOf("tvdb", "fanart", "motn", "watchmode", "tastedive", "omdb", "opensubtitles", "subdl", "realdebrid", "trakt", "traktsecret").forEach { k ->
            val v = localProps.getProperty("sokoul.key.$k").orEmpty().trim().filter { it.isLetterOrDigit() || it in "._-" }
            buildConfigField("String", "KEY_${k.uppercase()}", "\"$v\"")
        }
    }

    // Signature release : keystore.properties (non versionné) à la racine d'app-android :
    // storeFile=…, storePassword=…, keyAlias=…, keyPassword=…
    val keystoreProps = rootProject.file("keystore.properties").takeIf { it.isFile }?.let { f ->
        Properties().also { p -> f.inputStream().use { p.load(it) } }
    }
    signingConfigs {
        if (keystoreProps != null) create("release") {
            storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sans keystore.properties : signature debug (installation locale uniquement, à ne pas diffuser).
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time (épisodes, sous-titres, TVmaze) sur Android 7 (minSdk 24)
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=androidx.media3.common.util.UnstableApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.maxHeapSize = "2g" }
    }
    // schémas Room exportés, lus par le test de migration (Robolectric lit les assets de la variante debug ; absents du release)
    sourceSets["debug"].assets.srcDir("$projectDir/schemas")
    lint {
        abortOnError = false
        // l'opt-in Media3 est déclaré globalement au compilateur (freeCompilerArgs), lint ne le voit pas
        disable += listOf("UnsafeOptInUsageError", "GradleDependency", "AndroidGradlePluginVersion")
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":parser"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.palette)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)
    implementation(libs.work.runtime)
    implementation(libs.tvprovider)
    implementation(libs.datastore.preferences)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.ffmpeg)

    implementation(libs.coil.compose)
    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.media3.test.utils)
    testImplementation(libs.media3.test.utils.robolectric)
    // tests d'interface Compose sous Robolectric (focus D-pad, écrans d'erreur, navigation)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
