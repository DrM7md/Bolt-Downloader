import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release signing reads its secrets from keystore.properties (git-ignored) so they never reach
// the repository. Without that file the release build is simply left unsigned.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { stream -> load(stream) }
}

android {
    namespace = "com.mh.videodownloader"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mh.videodownloader"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "3.1"

        // arm64 for real devices, x86_64 so the app also runs on the emulator.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("personal") {
            storeFile = keystoreProps.getProperty("storeFile")?.let { path -> rootProject.file(path) }
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
            // Personal build: signed locally so the APK installs straight from the file manager.
            if (keystoreProps.getProperty("storeFile")?.let { path -> rootProject.file(path).exists() } == true) {
                signingConfig = signingConfigs.getByName("personal")
            }
        }
    }

    // Splitting keeps each personal APK at ~half the size of the universal one.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // The app speaks Arabic and English only; drop the other ~80 languages that AndroidX and
    // Media3 bring along, which would otherwise sit in resources.arsc unused.
    androidResources {
        localeFilters += listOf("ar", "en")
    }

    // youtubedl-android ships its python/ffmpeg payload as uncompressed .so files.
    packaging {
        jniLibs { useLegacyPackaging = true }
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCY",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/INDEX.LIST",
            )
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)

    implementation(libs.youtubedl.library)
    implementation(libs.youtubedl.ffmpeg)
}
