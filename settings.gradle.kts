rootProject.name = "jaitsizazu"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
}

include(":core")
include(":ui")
include(":engine-desktop")
include(":desktopApp")
include(":engine-android")
include(":androidApp")
