// Shared Compose UI: plain state holders plus stateless screens, and every
// user-facing text as Compose resources: Basque in values/ (the default) and
// English in values-en/.
// The JVM (desktop) and Android targets share everything in commonMain. Its
// tests run on the JVM.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.compiler)
    // Generates the Res class from src/commonMain/composeResources.
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(21)

    jvm()

    android {
        namespace = "io.github.stableperu.jaitsizazu.ui"
        compileSdk = 37
        minSdk = 26
        // Packs the Compose resources (the texts) into the Android library.
        // Without it, Res.string is missing at run time on Android.
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            api(libs.kotlinx.coroutines.core)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material)
            api(libs.compose.components.resources)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            // Reading the string resources outside a window asks Skiko for the system
            // theme, so the JVM tests need the Skiko runtime of the build machine.
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.resources {
    // desktopApp reads the texts it shows itself: tabs, dialogs and the crash message.
    publicResClass = true
    packageOfResClass = "jaitsizazu.ui.resources"
}
