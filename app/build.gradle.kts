@file:Suppress("DEPRECATION")

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.net.URL
import javax.inject.Inject

val isFullBuild: Boolean by rootProject.extra

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

if (isFullBuild && System.getenv("PULL_REQUEST") == null) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
    apply(plugin = "com.google.firebase.firebase-perf")
}

abstract class GenerateProtoTask : DefaultTask() {
    @get:Input
    abstract val protocUrl: Property<String>

    @get:InputFile
    abstract val protoSourceFile: RegularFileProperty

    @get:Internal
    abstract val generatedSourcesDir: DirectoryProperty

    @get:Internal
    abstract val protocExecutable: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun generate() {
        val protoFile = protoSourceFile.get().asFile
        val outputDir = generatedSourcesDir.get().asFile
        val protocFile = protocExecutable.get().asFile

        outputDir.mkdirs()

        if (!protocFile.exists() || protocFile.length() == 0L) {
            val url = protocUrl.get()
            logger.lifecycle("Downloading protoc ${url.substringAfterLast('/')} from $url")
            protocFile.parentFile.mkdirs()
            URL(url).openStream().use { input ->
                protocFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            protocFile.setExecutable(true)
        }

        logger.lifecycle("Generating protobuf files in $outputDir")
        execOperations.exec {
            executable = protocFile.absolutePath
            args(
                "--java_out=lite:$outputDir",
                "--kotlin_out=$outputDir",
                "-I=${protoFile.parentFile}",
                protoFile.absolutePath,
            )
        }
        logger.lifecycle("Protobuf files generated successfully")
    }
}

android {
    namespace = "com.maloy.muzza"
    compileSdk = 36
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "com.maloy.muzza"
        minSdk = 24
        targetSdk = 36
        versionCode = 48
        versionName = "0.7.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }
    flavorDimensions += "version"
    productFlavors {
        create("full") {
            dimension = "version"
        }
        create("foss") {
            dimension = "version"
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }
    
    signingConfigs {
        getByName("debug") {
            if (System.getenv("MUSIC_DEBUG_SIGNING_STORE_PASSWORD") != null) {
                storeFile = file(System.getenv("MUSIC_DEBUG_KEYSTORE_FILE"))
                storePassword = System.getenv("MUSIC_DEBUG_SIGNING_STORE_PASSWORD")
                keyAlias = "debug"
                keyPassword = System.getenv("MUSIC_DEBUG_SIGNING_KEY_PASSWORD")
            }
        }
    }
    buildFeatures {
        buildConfig = true
        compose = true
        viewBinding = true
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        jvmToolchain(21)
    }
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + "-Xcontext-receivers"
        jvmTarget = "21"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
    lint {
        disable += "MissingTranslation"
        disable += "MissingQuantity"
        disable += "ImpliedQuantity"
    }
    // avoid DEPENDENCY_INFO_BLOCK for IzzyOnDroid
    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }
    androidResources{
        generateLocaleConfig = true
    }
}

val protocVersion = libs.versions.protobuf.get()

fun getProtocUrl(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()

    val osName = when {
        os.contains("linux") -> "linux"
        os.contains("mac") || os.contains("darwin") -> "osx"
        os.contains("windows") -> "windows"
        else -> "linux"
    }

    val archName = when {
        arch.contains("x86_64") || arch.contains("amd64") -> "x86_64"
        arch.contains("aarch64") || arch.contains("arm64") -> "aarch_64"
        arch.contains("x86") -> "x86_32"
        else -> "x86_64"
    }

    return "https://repo1.maven.org/maven2/com/google/protobuf/protoc/$protocVersion/protoc-$protocVersion-$osName-$archName.exe"
}

val protoDir = rootProject.file("metroproto")
val protoFile = protoDir.resolve("listentogether.proto")

val generateProto = if (protoFile.exists()) {
    val protocUrl = getProtocUrl()
    val protocFileName = URL(protocUrl).path.substringAfterLast('/')

    tasks.register<GenerateProtoTask>("generateProto") {
        group = "build"
        description = "Generate Kotlin protobuf files"
        protoSourceFile.set(protoFile)
        generatedSourcesDir.set(file("src/main/java"))
        this.protocUrl.set(protocUrl)
        protocExecutable.set(layout.buildDirectory.file("protoc/$protocFileName"))
    }
} else {
    logger.error("✗ Proto file not found at $protoFile")
    null
}

tasks.configureEach {
    if (name.startsWith("compile") || name.startsWith("kapt") || name.startsWith("ksp")) {
        generateProto?.let {
            dependsOn(it)
        }
    }
}

tasks.named("clean") {
    doLast {
        val generatedDir = file("src/main/java/com/maloyt/muzza/listentogether/proto")
        if (generatedDir.exists()) {
            generatedDir.deleteRecursively()
            println("✓ Deleted generated proto files")
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.guava)
    implementation(libs.coroutines.guava)
    implementation(libs.concurrent.futures)

    implementation(libs.activity)
    implementation(libs.navigation)
    implementation(libs.hilt.navigation)
    implementation(libs.datastore)

    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.ui.tooling)
    implementation(libs.compose.reorderable)

    implementation(libs.viewmodel)
    implementation(libs.viewmodel.compose)

    implementation(libs.material3)
    implementation(libs.palette)
    implementation(libs.material.kolor)
    implementation(libs.squigglyslider)
    implementation(libs.compose.icons.extended)

    implementation(libs.lazycolumnscrollbar)

    implementation(libs.coil)

    implementation(libs.shimmer)

    implementation(libs.media3)
    implementation(libs.media3.session)
    implementation(libs.media3.okhttp)

    implementation(libs.room.runtime)
    implementation(libs.cardview)
    implementation(libs.animation.core)
    implementation(libs.material)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)

    implementation(libs.apache.lang3)

    implementation(libs.hilt)
    kapt(libs.hilt.compiler)

    implementation(projects.innertube)
    implementation(projects.kugou)
    implementation(projects.lrclib)
    implementation(projects.simpmusic)
    implementation(projects.kizzy)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)

    coreLibraryDesugaring(libs.desugaring)

    "fullImplementation"(platform(libs.firebase.bom))
    "fullImplementation"(libs.firebase.analytics)
    "fullImplementation"(libs.firebase.crashlytics)
    "fullImplementation"(libs.firebase.config)
    "fullImplementation"(libs.firebase.perf)
    "fullImplementation"(libs.mlkit.language.id)
    "fullImplementation"(libs.mlkit.translate)
    "fullImplementation"(libs.opencc4j)

    implementation(libs.timber)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)

    implementation(libs.lifecycle.process)
    implementation(libs.protobuf.javalite)
    implementation(libs.protobuf.kotlin.lite)
}
configurations.all {
    resolutionStrategy {
        force("com.google.protobuf:protobuf-javalite:${libs.versions.protobuf.get()}")
    }
    exclude(group = "com.google.protobuf", module = "protobuf-java")
}

subprojects {
    configurations.all {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }
}