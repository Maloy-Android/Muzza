package com.maloy.muzza

import com.maloy.innertube.models.SongItem
import com.maloy.innertube.models.Artist
import com.maloy.innertube.models.Album
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

    @Test
    fun playerMetadataPreservesInnerTubeAlbumAndArtistNavigation() {
        val item = SongItem(
            id = "song",
            title = "Song",
            artists = listOf(Artist(name = "Artist", id = "UCartist")),
            album = Album(name = "Album", id = "MPREalbum"),
            thumbnail = "https://lh3.googleusercontent.com/image=w544-h544-l90-rj",
        ).toIosMediaItem()

        assertEquals("MPREalbum", item.album?.id)
        assertEquals("Album", item.album?.title)
        assertEquals("UCartist", item.artists.single().id)
        assertEquals("Artist", item.artists.single().name)
        assertEquals("https://lh3.googleusercontent.com/image=w1080-h1080-p-l90-rj", item.thumbnail)
    }

    @Test
    fun googleArtworkResizingPreservesQueryParameters() {
        assertEquals(
            "https://yt3.ggpht.com/image=w1080-h1080-p-l90-rj?auth=token",
            "https://yt3.ggpht.com/image=s544-c-k-c0x00ffffff-no-rj?auth=token".toIosArtworkUrl(),
        )
    }
}
