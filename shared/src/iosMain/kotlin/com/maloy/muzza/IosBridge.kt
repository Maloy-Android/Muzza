package com.maloy.muzza

import com.maloy.innertube.YouTube
import com.maloy.innertube.models.SongItem
import com.maloy.innertube.models.YTItem
import com.metrolist.innertubex.models.YouTubeClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class IosMediaItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val thumbnail: String,
    val kind: String,
)

@Serializable
data class IosMediaSection(
    val title: String,
    val items: List<IosMediaItem>,
)

@Serializable
data class IosBridgeResponse(
    val sections: List<IosMediaSection> = emptyList(),
    val items: List<IosMediaItem> = emptyList(),
    val chips: List<String> = emptyList(),
    val error: String? = null,
)

private val iosBridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
private val iosJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

private fun YTItem.toIosMediaItem(): IosMediaItem {
    val subtitle = when (this) {
        is SongItem -> artists.joinToString { it.name }.ifBlank { "Song" }
        is com.maloy.innertube.models.AlbumItem -> artists?.joinToString { it.name }.orEmpty().ifBlank { "Album" }
        is com.maloy.innertube.models.PlaylistItem -> author?.name?.ifBlank { "Playlist" } ?: "Playlist"
        is com.maloy.innertube.models.ArtistItem -> subscriptions?.ifBlank { "Artist" } ?: "Artist"
    }
    val kind = when (this) {
        is SongItem -> "song"
        is com.maloy.innertube.models.AlbumItem -> "album"
        is com.maloy.innertube.models.PlaylistItem -> "playlist"
        is com.maloy.innertube.models.ArtistItem -> "artist"
    }
    return IosMediaItem(id, title, subtitle, thumbnail, kind)
}

fun fetchIosHome(hideExplicit: Boolean, completion: (String) -> Unit) {
    iosBridgeScope.launch {
        val response = YouTube.home()
        val sections = response.getOrNull()?.sections.orEmpty().mapNotNull { section ->
            val items = section.items
                .let { items -> if (hideExplicit) items.filter { !it.explicit } else items }
                .take(20)
                .map(YTItem::toIosMediaItem)
            if (items.isEmpty()) null else IosMediaSection(section.title, items)
        }
        val body = IosBridgeResponse(
            sections = sections,
            chips = response.getOrNull()?.chips.orEmpty().mapNotNull { it.title },
            error = response.exceptionOrNull()?.message,
        )
        completion(iosJson.encodeToString(body))
    }
}

fun fetchIosExplore(completion: (String) -> Unit) {
    iosBridgeScope.launch {
        val response = YouTube.explore()
        val page = response.getOrNull()
        val sections = buildList {
            page?.newReleaseAlbums?.take(20)?.let { albums ->
                if (albums.isNotEmpty()) add(IosMediaSection(
                    "New releases",
                    albums.map(YTItem::toIosMediaItem),
                ))
            }
            page?.newClips?.take(20)?.let { clips ->
                if (clips.isNotEmpty()) add(IosMediaSection(
                    "New clips",
                    clips.map(YTItem::toIosMediaItem),
                ))
            }
            page?.moodAndGenres?.take(20)?.let { moodGenres ->
                if (moodGenres.isNotEmpty()) add(IosMediaSection(
                    "Moods and genres",
                    moodGenres.map {
                        IosMediaItem(it.title, it.title, "Mood or genre", "", "genre")
                    },
                ))
            }
        }
        completion(iosJson.encodeToString(IosBridgeResponse(
            sections = sections,
            error = response.exceptionOrNull()?.message,
        )))
    }
}

fun searchIosMusic(query: String, completion: (String) -> Unit) {
    if (query.isBlank()) {
        completion(iosJson.encodeToString(IosBridgeResponse()))
        return
    }
    iosBridgeScope.launch {
        val response = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
        val items = response.getOrNull()?.items.orEmpty().map(YTItem::toIosMediaItem)
        completion(iosJson.encodeToString(IosBridgeResponse(
            items = items,
            error = response.exceptionOrNull()?.message,
        )))
    }
}

fun fetchIosPlayerUrl(videoId: String, completion: (String) -> Unit) {
    iosBridgeScope.launch {
        val response = YouTube.player(videoId, client = YouTubeClient.WEB_REMIX)
        val url = response.getOrNull()
            ?.streamingData
            ?.adaptiveFormats
            ?.firstOrNull { it.isAudio }
            ?.url
        completion(url.orEmpty())
    }
}
