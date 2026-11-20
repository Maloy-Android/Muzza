package com.maloy.innertube

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

actual fun createInnerTubeHttpClient(
    proxyHost: String?,
    proxyPort: Int,
    proxyAuth: String?,
): HttpClient = HttpClient(Darwin) {
    expectSuccess = true

    install(ContentNegotiation) {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }
        json(json)
        json(json, ContentType.Application.Json)
    }

    install(ContentEncoding) {
        gzip(0.9F)
        deflate(0.8F)
    }
    // Proxy not supported on iOS Darwin engine
}
