import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(21)
}

// The OS whose Skiko native runtime goes on the runtime classpath. Unset means
// the build machine's OS. -Pjaitsizazu.desktopTarget=windows-x64 builds for
// Windows from a Linux container (see scripts/package-windows.sh).
val desktopTarget: String? = providers.gradleProperty("jaitsizazu.desktopTarget").orNull

dependencies {
    implementation(project(":core"))
    implementation(project(":ui"))
    implementation(project(":engine-desktop"))
    implementation(
        when (desktopTarget) {
            null -> compose.desktop.currentOs
            "windows-x64" -> compose.desktop.windows_x64
            else -> error("Unsupported jaitsizazu.desktopTarget: $desktopTarget (expected windows-x64)")
        },
    )

    testImplementation(kotlin("test"))
}

// The app JAR plus every runtime dependency JAR in one folder, ready for
// `java -cp "<folder>/*" jaitsizazu.desktop.MainKt`. Separate JARs avoid
// merging META-INF/services files, which a single fat JAR would need.
tasks.register<Sync>("stageAppJars") {
    group = "distribution"
    description = "Copies the app JAR and its runtime dependencies into build/app-jars/<target>."
    from(tasks.jar)
    from(configurations.runtimeClasspath)
    into(layout.buildDirectory.dir("app-jars/${desktopTarget ?: "current-os"}"))
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
