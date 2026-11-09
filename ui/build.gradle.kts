// Shared Compose UI: plain state holders plus stateless screens, and every
// user-facing text as Compose resources: Basque in values/ (the default) and
// English in values-en/.
// Only the JVM (desktop) target for now. The Android target is added here
// later without moving code, since everything lives in commonMain.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    // Generates the Res class from src/commonMain/composeResources.
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(21)

    jvm()

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
