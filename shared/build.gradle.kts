plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    android {
        namespace = "com.maloy.muzza.shared"
        compileSdk = 37
        minSdk = 24
    }

    jvmToolchain(21)
    applyDefaultHierarchyTemplate()

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Compose Multiplatform
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)

            // Lifecycle / ViewModel KMP
            implementation(libs.viewmodel)
            implementation(libs.viewmodel.compose)
            implementation(libs.lifecycle.process)

            // Koin KMP
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            // Ktor KMP (core + serialization — engines are platform-specific)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.ktor.client.encoding)

            // SQLDelight
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.sqldelight.primitive.adapters)

            // Settings (replaces DataStore)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.coroutines)

            // Coil 3 KMP
            implementation(libs.coil3.compose)
            implementation(libs.coil3.network.ktor)

            // Coroutines
            implementation(libs.coroutines.guava)

            // DateTime
            implementation(libs.kotlinx.datetime)

            // Serialization
            implementation(libs.ktor.serialization.json)

            // Inner modules
            implementation(projects.innertube)
            implementation(projects.kugou)
            implementation(projects.lrclib)
        }

        androidMain.dependencies {
            // Android Ktor engine
            implementation(libs.ktor.client.okhttp)

            // SQLDelight Android driver
            implementation(libs.sqldelight.android.driver)

            // Android-specific Koin
            implementation(libs.koin.android)

            // Activity Compose (needed for LocalContext, etc.)
            implementation(libs.activity)

            // Media3 (playback — Android only)
            implementation(libs.media3)
            implementation(libs.media3.session)
            implementation(libs.media3.okhttp)

            // DataStore (used by existing code during migration)
            implementation(libs.datastore)

            // Palette (album art colors)
            implementation(libs.palette)
        }

        iosMain.dependencies {
            // iOS Ktor engine
            implementation(libs.ktor.client.darwin)

            // SQLDelight iOS driver
            implementation(libs.sqldelight.native.driver)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

sqldelight {
    databases {
        create("MuzzaDatabase") {
            packageName.set("com.maloy.muzza.db")
        }
    }
    linkSqlite.set(true)
}
