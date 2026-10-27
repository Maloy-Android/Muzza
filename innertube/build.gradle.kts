plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.encoding)
    implementation(libs.brotli)
    testImplementation(libs.junit)
    if (providers.gradleProperty("useMavenLocalInnerTubeX").isPresent) {
        api("com.github.MetrolistGroup:innertubex:${libs.versions.innertubex.get()}")
    } else {
        api(libs.innertubex)
    }
}