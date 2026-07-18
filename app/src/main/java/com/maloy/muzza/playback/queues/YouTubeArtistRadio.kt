package com.maloy.muzza.playback.queues

import android.content.Context
import androidx.media3.common.MediaItem
import com.maloy.innertube.YouTube
import com.maloy.innertube.models.WatchEndpoint
import com.maloy.muzza.R
import com.maloy.muzza.extensions.toMediaItem
import com.maloy.muzza.models.MediaMetadata
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

class YouTubeArtistRadio(
    private val artistId: String,
    private val context: Context,
    private val shuffleEndpointEnabled: Boolean = true,
    override val preloadItem: MediaMetadata? = null,
) : Queue {
    private var continuation: String? = null

    override suspend fun getInitialStatus(): Queue.Status {
        val artist = YouTube.artist(artistId).getOrThrow()
        val radioEndpoint: WatchEndpoint? = artist.artist.radioEndpoint
        val shuffleEndpoint: WatchEndpoint? = artist.artist.shuffleEndpoint
        val title = artist.artist.title
        val radioSongs = run {
            val result = YouTube.next((if (shuffleEndpointEnabled) shuffleEndpoint else radioEndpoint)!!, null).getOrThrow()
            continuation = result.continuation
            result.items
        }
        return Queue.Status(
            title = if (!shuffleEndpointEnabled) context.getString(R.string.radio_queue_title, title) else title,
            items = radioSongs.map { it.toMediaItem() },
            mediaItemIndex = 0
        )
    }

    override fun hasNextPage(): Boolean = continuation != null

    override suspend fun nextPage(): List<MediaItem> {
        val artist = YouTube.artist(artistId).getOrThrow()
        val radioEndpoint: WatchEndpoint? = artist.artist.radioEndpoint
        val shuffleEndpoint: WatchEndpoint? = artist.artist.shuffleEndpoint
        val nextResult = withContext(IO) {
            YouTube.next((if (shuffleEndpointEnabled) shuffleEndpoint else radioEndpoint)!!, null).getOrThrow()
        }
        continuation = nextResult.continuation
        return nextResult.items.map { it.toMediaItem() }
    }
}
