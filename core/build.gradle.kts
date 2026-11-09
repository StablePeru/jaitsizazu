// Pure domain code: no I/O, no UI. Everything lives in commonMain, so the JVM
// (desktop) and Android targets share it as it is. Its tests run on the JVM.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    jvmToolchain(21)

    jvm()

    android {
        namespace = "io.github.stableperu.jaitsizazu.core"
        compileSdk = 37
        minSdk = 26
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
