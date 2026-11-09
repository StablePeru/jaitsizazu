// The Android app: a container that wires :engine-android into the shared
// :ui screens, like :desktopApp does for the desktop.
// A pure Android module: AGP 9 compiles its Kotlin, and the Kotlin
// Multiplatform plugin cannot be combined with com.android.application.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "io.github.stableperu.jaitsizazu"
    compileSdk = 37

    defaultConfig {
        // PROVISIONAL: the author picks the final application ID before the
        // first release (task T9). It cannot change once the app is published.
        applicationId = "io.github.stableperu.jaitsizazu"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        // youtubedl-android ships Python, ffmpeg and QuickJS for every ABI, and
        // they are large: keep the phones (64 and 32-bit ARM) and the x86_64 emulator.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    packaging {
        // youtubedl-android runs its binaries from the extracted native
        // library folder, so the libraries must be extracted at install.
        jniLibs.useLegacyPackaging = true
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":ui"))
    implementation(project(":engine-android"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material)
    implementation(libs.kotlinx.coroutines.core)
}
