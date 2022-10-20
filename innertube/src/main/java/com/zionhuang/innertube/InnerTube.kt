package com.zionhuang.innertube

import com.zionhuang.innertube.encoder.brotli
import com.zionhuang.innertube.models.YouTubeClient
import com.zionhuang.innertube.models.YouTubeLocale
import com.zionhuang.innertube.models.body.*
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.compression.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.net.Proxy
import java.util.*

/**
 * Provide access to InnerTube endpoints.
 * For making HTTP requests, not parsing response.
 */
class InnerTube {
    private var httpClient = createClient()

    var locale = YouTubeLocale(
        gl = Locale.getDefault().country,
        hl = Locale.getDefault().toLanguageTag()
    )
    private var _proxy: Proxy? = null
    var proxy: Proxy?
        get() = _proxy
        set(value) {
            _proxy = value
            httpClient.close()
            httpClient = createClient()
        }
    var visitorData: String = "CgtsZG1ySnZiQWtSbyiMjuGSBg%3D%3D"

    @OptIn(ExperimentalSerializationApi::class)
    private fun createClient() = HttpClient(OkHttp) {
        expectSuccess = true

        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            })
        }

        install(ContentEncoding) {
            brotli(1.0F)
            gzip(0.9F)
            deflate(0.8F)
        }

        if (_proxy != null) {
            engine {
                this.proxy = _proxy
            }
        }

        defaultRequest {
            url("https://music.youtube.com/youtubei/v1/")
        }
    }

    private fun HttpRequestBuilder.configYTClient(client: YouTubeClient) {
        contentType(ContentType.Application.Json)
        headers {
            append("X-Goog-Api-Format-Version", "1")
            append("X-YouTube-Client-Name", client.clientName)
            append("X-YouTube-Client-Version", client.clientVersion)
            if (client.referer != null) {
                append("Referer", client.referer)
            }
        }
        userAgent(client.userAgent)
        parameter("key", client.api_key)
        parameter("prettyPrint", false)
    }

    suspend fun search(
        client: YouTubeClient,
        query: String? = null,
        params: String? = null,
        continuation: String? = null,
    ) = httpClient.post("search") {
        configYTClient(client)
        setBody(SearchBody(
            context = client.toContext(locale, visitorData),
            query = query,
            params = params
        ))
        parameter("continuation", continuation)
        parameter("ctoken", continuation)
    }

    suspend fun player(
        client: YouTubeClient,
        videoId: String,
        playlistId: String?,
    ) = httpClient.post("player") {
        configYTClient(client)
        setBody(PlayerBody(
            context = client.toContext(locale, visitorData),
            videoId = videoId,
            playlistId = playlistId
        ))
    }

    suspend fun browse(
        client: YouTubeClient,
        browseId: String? = null,
        params: String? = null,
        continuation: String? = null,
    ) = httpClient.post("browse") {
        configYTClient(client)
        setBody(BrowseBody(
            context = client.toContext(locale, visitorData),
            browseId = browseId,
            params = params
        ))
        parameter("continuation", continuation)
        parameter("ctoken", continuation)
        if (continuation != null) {
            parameter("type", "next")
        }
    }

    suspend fun next(
        client: YouTubeClient,
        videoId: String?,
        playlistId: String?,
        playlistSetVideoId: String?,
        index: Int?,
        params: String?,
        continuation: String? = null,
    ) = httpClient.post("next") {
        configYTClient(client)
        setBody(NextBody(
            context = client.toContext(locale, visitorData),
            videoId = videoId,
            playlistId = playlistId,
            playlistSetVideoId = playlistSetVideoId,
            index = index,
            params = params,
            continuation = continuation
        ))
    }

    suspend fun getSearchSuggestions(
        client: YouTubeClient,
        input: String,
    ) = httpClient.post("music/get_search_suggestions") {
        configYTClient(client)
        setBody(GetSearchSuggestionsBody(
            context = client.toContext(locale, visitorData),
            input = input
        ))
    }

    suspend fun getQueue(
        client: YouTubeClient,
        videoIds: List<String>?,
        playlistId: String?,
    ) = httpClient.post("music/get_queue") {
        configYTClient(client)
        setBody(GetQueueBody(
            context = client.toContext(locale, visitorData),
            videoIds = videoIds,
            playlistId = playlistId
        ))
    }
}