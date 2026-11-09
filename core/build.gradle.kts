// Pure domain code: no I/O, no UI.
// Only the JVM target for now. An Android target is added here later
// without moving code, since everything lives in commonMain.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(21)

    jvm()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
