package com.maloy.muzza

import com.maloy.innertube.YouTube
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.YtConfigParserImpl

private val iosExtractor by lazy {
    val transport = YouTube.extractionTransport()
    val store = RemotePlayerConfigStore(transport.httpClient, PlayerConfigRepository.disabled())
    val cipher = YouTubeCipherService(transport.httpClient, store)
    InnerTubeExtractor(
        configParser = YtConfigParserImpl(transport.httpClient, transport.innerTube, store, cipherService = cipher),
        cipherService = cipher,
        innerTube = transport.innerTube,
    )
}

fun fetchIosPlayerUrl(videoId: String, isExplicit: Boolean, completion: (String) -> Unit) =
    iosRequest(completion) {
        require(videoId.isNotBlank()) { "Missing song identifier." }
        val stream = iosExtractor.extract(
            videoId = videoId,
            hints = ContentHints(isExplicit = isExplicit).withStreamCapabilities(
                allowHls = false,
                allowSabr = false,
                allowBoundedRange = false,
            ),
            audioQuality = AudioQuality.MP4,
        ) ?: throw IllegalStateException("YouTube did not return a playable audio stream.")
        check(!stream.requireBoundedRange) { "This audio stream requires an unsupported transport." }
        IosBridgeResponse(audioUrl = stream.audioUrl, headers = stream.headers, contentLength = stream.contentLengthBytes)
    }
