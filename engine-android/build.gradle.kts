import com.android.build.api.variant.HostTestBuilder

// Android engine adapter: runs yt-dlp and ffmpeg from youtubedl-android
// through the subprocess adapters of :engine-desktop, and publishes finished
// files to the public Download folder.
// :engine-desktop is really the subprocess engine that both platforms share
// (plain JVM code, API level 26 safe). Renaming it is a later follow-up.
// A pure Android library: AGP 9 compiles its Kotlin, so there is no Kotlin plugin here.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.stableperu.jaitsizazu.engine.android"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }
}

kotlin {
    jvmToolchain(21)
}

// The host tests check plain logic, the same in every build type: run them once, for debug.
androidComponents {
    beforeVariants(selector().withBuildType("release")) { variant ->
        variant.hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = false
    }
}

dependencies {
    implementation(project(":core"))
    api(project(":engine-desktop"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.youtubedl.android.library)
    implementation(libs.youtubedl.android.ffmpeg)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
