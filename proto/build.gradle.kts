plugins {
    alias(libs.plugins.kotlin.serialization)
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.protobuf.kotlin.lite)
}
