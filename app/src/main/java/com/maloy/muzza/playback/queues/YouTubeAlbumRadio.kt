package com.maloy.muzza.playback.queues

import androidx.media3.common.MediaItem
import com.maloy.innertube.YouTube
import com.maloy.innertube.models.WatchEndpoint
import com.maloy.muzza.extensions.toMediaItem
import com.maloy.muzza.models.MediaMetadata
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext


class YouTubeAlbumRadio(
    private val playlistId: String,
) : Queue {
    override val preloadItem: MediaMetadata? = null
    private var continuation: String? = null

    override suspend fun getInitialStatus(): Queue.Status = withContext(IO) {
        val album = YouTube.album(playlistId)?.getOrThrow()
        val radioEndpoint: WatchEndpoint = album?.album?.radioEndpoint!!
        val title = YouTube.next(radioEndpoint, null).getOrThrow().title
        val radioSongs = run {
            val result = YouTube.next(radioEndpoint, null).getOrThrow()
            continuation = result.continuation
            result.items
        }
        Queue.Status(
            title = title,
            items = radioSongs.map { it.toMediaItem() },
            mediaItemIndex = 0
        )
    }

    override fun hasNextPage(): Boolean = continuation != null

    override suspend fun nextPage(): List<MediaItem> {
        val album = YouTube.album(playlistId)?.getOrThrow()
        val radioEndpoint = album?.album?.radioEndpoint
        val nextResult = withContext(IO) {
            YouTube.next(radioEndpoint!!, continuation).getOrThrow()
        }
        continuation = nextResult.continuation
        return nextResult.items.map { it.toMediaItem() }
    }
}
