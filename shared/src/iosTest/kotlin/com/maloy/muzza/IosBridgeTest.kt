package com.maloy.muzza

import com.maloy.innertube.models.SongItem
import com.maloy.innertube.models.WatchEndpoint
import com.maloy.innertube.pages.NextResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosBridgeTest {
    @Test
    fun cancelledRequestsProduceAnExplicitResponse() = runBlocking {
        val response = iosResponse { throw CancellationException() }
        assertEquals("The request was cancelled.", response.error)
    }

    @Test
    fun radioKeepsTheCurrentSongWhenFilteringExplicitTracks() {
        val songs = listOf(
            SongItem("hidden", "Hidden", emptyList(), thumbnail = "", explicit = true),
            SongItem("current", "Current", emptyList(), thumbnail = "", explicit = true),
            SongItem("next", "Next", emptyList(), thumbnail = ""),
        )
        val result = NextResult(
            items = songs,
            currentIndex = 1,
            continuation = "next-page",
            endpoint = WatchEndpoint(videoId = "current"),
        ).toIosRadioResponse(hideExplicit = true)
        assertEquals(listOf("current", "next"), result.items.map { it.id })
        assertEquals(0, result.currentIndex)
        assertTrue(result.radioEndpoint?.videoId == "current")
        assertEquals("next-page", result.continuation)
    }
}
