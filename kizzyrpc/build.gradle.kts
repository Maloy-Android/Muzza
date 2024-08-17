plugins {
    kotlin("jvm")
    @Suppress("DSL_SCOPE_VIOLATION")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation (libs.coroutines.android)
    implementation (libs.bundles.network.ktor)
    implementation (libs.ktor.websockets)
    testImplementation (libs.junit)
}