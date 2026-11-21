package com.maloy.innertube

import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HttpClientFactoryTest {
    @Test
    fun relativeRequestsUseYouTubeMusicApi() = runBlocking {
        val client = createInnerTubeHttpClient()
        var requestedUrl: String? = null
        client.plugin(HttpSend).intercept { request ->
            requestedUrl = request.url.buildString()
            throw RequestCaptured()
        }

        try {
            for (endpoint in listOf("browse", "search", "player")) {
                assertFailsWith<RequestCaptured> { client.get(endpoint) }
                assertEquals("https://music.youtube.com/youtubei/v1/$endpoint", requestedUrl)
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun absoluteRequestsKeepTheirOwnHost() = runBlocking {
        val client = createInnerTubeHttpClient()
        var requestedUrl: String? = null
        client.plugin(HttpSend).intercept { request ->
            requestedUrl = request.url.buildString()
            throw RequestCaptured()
        }

        try {
            assertFailsWith<RequestCaptured> { client.get("https://example.org/test") }
            assertEquals("https://example.org/test", requestedUrl)
        } finally {
            client.close()
        }
    }

    private class RequestCaptured : RuntimeException()
}
