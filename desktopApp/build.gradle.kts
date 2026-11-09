import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "jaitsizazu.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "jaitsizazu"
            packageVersion = "0.1.0"
            description = "Download and convert audio and video."
            licenseFile.set(rootProject.file("LICENSE"))
        }
    }
}
