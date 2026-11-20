package com.maloy.innertube

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.net.InetSocketAddress
import java.net.Proxy

actual fun createInnerTubeHttpClient(
    proxyHost: String?,
    proxyPort: Int,
    proxyAuth: String?,
): HttpClient = HttpClient(OkHttp) {
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

    engine {
        if (proxyHost != null) {
            proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost, proxyPort))
            proxyAuth?.let { auth ->
                config {
                    proxyAuthenticator { _, response ->
                        response.request.newBuilder()
                            .header("Proxy-Authorization", auth)
                            .build()
                    }
                }
            }
        }
    }
}
