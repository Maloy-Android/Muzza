package com.maloy.muzza.playback.queues

import androidx.media3.common.MediaItem
import com.maloy.innertube.YouTube
import com.maloy.innertube.models.WatchEndpoint
import com.maloy.muzza.extensions.toMediaItemWithPlaylist
import com.maloy.muzza.models.MediaMetadata
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

class YouTubePlaylistRadio(
    private val playlistId: String,
    override val preloadItem: MediaMetadata? = null,
) : Queue {
    private var continuation: String? = null

    override suspend fun getInitialStatus(): Queue.Status {
        val playlist = YouTube.playlist(playlistId).getOrThrow()
        val radioEndpoint: WatchEndpoint = playlist.playlist.radioEndpoint!!
        val title = YouTube.next(radioEndpoint, null).getOrThrow().title
        val radioSongs = run {
            val result = YouTube.next(radioEndpoint, null).getOrThrow()
            continuation = result.continuation
            result.items
        }
        return Queue.Status(
            title = title,
            items = radioSongs.map { it.toMediaItemWithPlaylist(playlistId = playlistId) },
            mediaItemIndex = 0
        )
    }

    override fun hasNextPage(): Boolean = continuation != null

    override suspend fun nextPage(): List<MediaItem> {
        val playlist = YouTube.playlist(playlistId).getOrThrow()
        val radioEndpoint: WatchEndpoint = playlist.playlist.radioEndpoint!!
        val nextResult = withContext(IO) {
            YouTube.next(radioEndpoint, continuation).getOrThrow()
        }
        continuation = nextResult.continuation
        return nextResult.items.map { it.toMediaItemWithPlaylist(playlistId = playlistId) }
    }
}
