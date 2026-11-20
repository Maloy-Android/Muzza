@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { setUrl("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
        maven { setUrl("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
    }
}

rootProject.name = "Muzza"

// KMP shared logic
include(":shared")

// New Android app shell (KMP migration target)
include(":androidApp")

// Legacy Android app — kept during migration for reference, will be removed in Phase 6
include(":app")

// Network / API modules (migrated to KMP multiplatform)
include(":innertube")
include(":kugou")
include(":lrclib")
include(":kizzy")
include(":simpmusic")
