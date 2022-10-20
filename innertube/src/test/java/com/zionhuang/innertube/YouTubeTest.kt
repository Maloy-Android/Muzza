package com.zionhuang.innertube

import com.zionhuang.innertube.YouTube.EXPLORE_BROWSE_ID
import com.zionhuang.innertube.YouTube.HOME_BROWSE_ID
import com.zionhuang.innertube.YouTube.SearchFilter.Companion.FILTER_ALBUM
import com.zionhuang.innertube.YouTube.SearchFilter.Companion.FILTER_ARTIST
import com.zionhuang.innertube.YouTube.SearchFilter.Companion.FILTER_COMMUNITY_PLAYLIST
import com.zionhuang.innertube.YouTube.SearchFilter.Companion.FILTER_FEATURED_PLAYLIST
import com.zionhuang.innertube.YouTube.SearchFilter.Companion.FILTER_SONG
import com.zionhuang.innertube.YouTube.SearchFilter.Companion.FILTER_VIDEO
import com.zionhuang.innertube.models.BrowseEndpoint
import com.zionhuang.innertube.models.WatchEndpoint
import com.zionhuang.innertube.models.YTItem
import com.zionhuang.innertube.models.YouTubeClient
import com.zionhuang.innertube.utils.browseAll
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test

@Ignore("IDK Why GitHub Action always runs the test with error")
class YouTubeTest {
    private val youTube = YouTube

    @Test
    fun `Check 'player' endpoint`() = runBlocking {
        VIDEO_IDS.forEach { videoId ->
            val playerResponse = youTube.player(videoId).getOrThrow()
            assertEquals(videoId, playerResponse.videoDetails.videoId)
        }
    }

    @Test
    fun `Check playable stream`() = runBlocking {
        VIDEO_IDS.forEach { videoId ->
            val playerResponse = youTube.player(videoId).getOrThrow()
            val format = playerResponse.streamingData!!.adaptiveFormats[0]
            val url = format.url
            println(url)
            val response = HttpClient(OkHttp).get(url) {
                headers {
                    append("Range", "bytes=0-10")
                }
            }
            assertTrue(response.status.isSuccess())
        }
    }

    @Test
    fun `Check 'search' endpoint`() = runBlocking {
        // Top result with radio link
        val searchAllTypeResult = youTube.searchAllType("musi").getOrThrow()
        assertTrue(searchAllTypeResult.items.size > 1)
        for (filter in listOf(
            FILTER_SONG,
            FILTER_VIDEO,
            FILTER_ALBUM,
            FILTER_ARTIST,
            FILTER_FEATURED_PLAYLIST,
            FILTER_COMMUNITY_PLAYLIST
        )) {
            val searchResult = youTube.search(SEARCH_QUERY, filter).getOrThrow()
            assertTrue(searchResult.items.isNotEmpty())
        }
    }

    @Test
    fun `Check search continuation`() = runBlocking {
        var count = 5
        var searchResult = youTube.search(SEARCH_QUERY, FILTER_SONG).getOrThrow()
        while (searchResult.continuations != null && count > 0) {
            searchResult.items.forEach {
                if (it is YTItem) println(it.title)
            }
            searchResult = youTube.search(searchResult.continuations!![0]).getOrThrow()
            count -= 1
        }
        searchResult.items.forEach {
            if (it is YTItem) println(it.title)
        }
        // audio book
        searchResult = youTube.search("tomori kusunoki", FILTER_ALBUM).getOrThrow()
    }

    @Test
    fun `Check 'get_search_suggestion' endpoint`() = runBlocking {
        val suggestions = youTube.getSearchSuggestions(SEARCH_QUERY).getOrThrow()
        assertTrue(suggestions.isNotEmpty())
    }

    @Test
    fun `Check 'browse' endpoint`() = runBlocking {
        var artist = youTube.browse(BrowseEndpoint("UCI6B8NkZKqlFWoiC_xE-hzA")).getOrThrow()
        assertTrue(artist.items.isNotEmpty())
        artist = youTube.browse(BrowseEndpoint("UCy2RKLxIOMOfGld_yBYEBLw")).getOrThrow() // Artist that contains audiobook
        assertTrue(artist.items.isNotEmpty())
        val album = youTube.browse(BrowseEndpoint("MPREb_oNAdr9eUOfS")).getOrThrow()
        assertTrue(album.items.isNotEmpty())
        val playlist = youTube.browse(BrowseEndpoint("VLRDCLAK5uy_mHAEb33pqvgdtuxsemicZNu-5w6rLRweo")).getOrThrow()
        assertTrue(playlist.items.isNotEmpty())
        listOf(HOME_BROWSE_ID, EXPLORE_BROWSE_ID).forEach { browseId ->
            val result = youTube.browse(BrowseEndpoint(browseId)).getOrThrow()
            assertTrue(result.items.isNotEmpty())
        }
    }

    @Test
    fun `Check 'browse' continuation`() = runBlocking {
        val result = youTube.browseAll(BrowseEndpoint(HOME_BROWSE_ID)).getOrThrow()
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `Check 'next' endpoint`() = runBlocking {
        var nextResult = youTube.next(WatchEndpoint(videoId = "qivRUhepWVA", playlistId = "RDEMQWAKLFUHzBCn9nEsPHDYAw")).getOrThrow()
        assertTrue(nextResult.items.isNotEmpty())
        nextResult = youTube.next(WatchEndpoint(videoId = "jF4KKOsoyDs", playlistId = "PLaHh1PiehjvqOXm1J7b2QGy2iAvN84Azb")).getOrThrow()
        assertTrue(nextResult.items.isNotEmpty())
        val playlistSongInfo = youTube.getPlaylistSongInfo(videoId = VIDEO_IDS.random()).getOrThrow()
        assertNotNull(playlistSongInfo.lyricsEndpoint)
    }

    @Test
    fun `Check 'next' continuation`() = runBlocking {
        val videoId = "qivRUhepWVA"
        val playlistId = "RDEMQWAKLFUHzBCn9nEsPHDYAw"
        var count = 5
        var nextResult = youTube.next(WatchEndpoint(videoId = videoId, playlistId = playlistId)).getOrThrow()
        while (nextResult.continuation != null && count > 0) {
            nextResult.items.forEach {
                println(it.title)
            }
            nextResult = youTube.next(WatchEndpoint(videoId = videoId, playlistId = playlistId), nextResult.continuation).getOrThrow()
            count -= 1
        }
        nextResult.items.forEach {
            println(it.title)
        }
    }

    @Test
    fun `Check 'get_queue' endpoint`() = runBlocking {
        var queue = youTube.getQueue(videoIds = VIDEO_IDS).getOrThrow()
        assertTrue(queue[0].navigationEndpoint.watchEndpoint!!.videoId == VIDEO_IDS[0])
        queue = youTube.getQueue(playlistId = PLAYLIST_ID).getOrThrow()
        assertTrue(queue.isNotEmpty())
    }

    @Test
    fun `Browse playlist`() = runBlocking {
        // This playlist has 2900 songs
        val browseId = "VLPLtAw-mgfCzRwduBTjBHknz5U4_ZM4n6qm"
        var count = 5
        var result = YouTube.browse(BrowseEndpoint(browseId)).getOrThrow()
        while (result.continuations != null && count > 0) {
            result.items.forEach {
                println(it.id)
            }
            result = YouTube.browse(result.continuations!!).getOrThrow()
            count -= 1
        }
        result.items.forEach {
            println(it.id)
        }
    }

    @Test
    fun lyrics() = runBlocking {
        val nextResult = YouTube.next(WatchEndpoint(videoId = "NCC6lI0GGy0")).getOrThrow()
        val browseResult = YouTube.browse(nextResult.lyricsEndpoint!!).getOrThrow()
        assertTrue(browseResult.lyrics != null)
    }

    @Test
    fun visitorData() {
        assertEquals("CgtsZG1ySnZiQWtSbyiMjuGSBg%3D%3D", YouTubeClient.generateVisitorData("ldmrJvbAkRo", 1649952524))
        assertEquals("CgtrLTlNQlQ2SWs1OCjZlu-ZBg%3D%3D", YouTubeClient.generateVisitorData("k-9MBT6Ik58", 1664863065))
        assertEquals("Cgs1bFZLWXJWVk5MRSisnO-ZBg%3D%3D", YouTubeClient.generateVisitorData("5lVKYrVVNLE", 1664863788))
        assertEquals("CgtrLTlNQlQ2SWs1OCjN_fSZBg%3D%3D", YouTubeClient.generateVisitorData("k-9MBT6Ik58", 1664958157))
        assertEquals("CgtXSHBibzJXSm1layj-ifWZBg%3D%3D", YouTubeClient.generateVisitorData("WHpbo2WJmek", 1664959742))
        assertEquals("CgtlcWs4cjFPYUpyZyj_ifWZBg%3D%3D", YouTubeClient.generateVisitorData("eqk8r1OaJrg", 1664959743))
        assertEquals("Cgs2eHNHQ3FTVkJDbyj-i_WZBg%3D%3D", YouTubeClient.generateVisitorData("6xsGCqSVBCo", 1664959998))
    }

    companion object {
        private val VIDEO_IDS = listOf(
            "4H-N260cPCg",
            "jF4KKOsoyDs"
//            "x8VYWazR5mE" Login required
        )

        private const val PLAYLIST_ID = "RDAMVM_WVXrDmm-P0"

        private const val SEARCH_QUERY = "YOASOBI"
    }
}