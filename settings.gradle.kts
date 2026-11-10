pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "QOwnNotesMobile"
include(
    ":app",
    ":core",
    ":data",
    ":backend-nextcloud",
    ":backend-localfolder",
    ":markdown-android",
    ":notefolder-sqlite"
)
