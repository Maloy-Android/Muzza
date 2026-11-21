package com.maloy.muzza

import com.maloy.innertube.YouTube
import com.maloy.innertube.models.AlbumItem
import com.maloy.innertube.models.ArtistItem
import com.maloy.innertube.models.PlaylistItem
import com.maloy.innertube.models.SongItem
import com.maloy.innertube.models.WatchEndpoint
import com.maloy.innertube.models.YTItem
import com.maloy.innertube.pages.NextResult
import com.metrolist.innertubex.models.YouTubeLocale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSLocale

@Serializable
data class IosMediaItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val thumbnail: String,
    val kind: String,
    val params: String? = null,
    val explicit: Boolean = false,
    val duration: Int? = null,
    val entryId: Long? = null,
    val downloadPath: String? = null,
    val remoteLiked: Boolean? = null,
)

@Serializable
data class IosMediaSection(
    val title: String,
    val items: List<IosMediaItem>,
)

@Serializable
data class IosHomeChip(
    val title: String,
    val params: String,
)

@Serializable
data class IosAccountProfile(
    val name: String,
    val email: String? = null,
    val channelHandle: String? = null,
    val thumbnailUrl: String? = null,
)

@Serializable
data class IosBridgeResponse(
    val sections: List<IosMediaSection> = emptyList(),
    val items: List<IosMediaItem> = emptyList(),
    val playlists: List<IosMediaItem> = emptyList(),
    val albums: List<IosMediaItem> = emptyList(),
    val artists: List<IosMediaItem> = emptyList(),
    val downloads: List<IosMediaItem> = emptyList(),
    val recent: List<IosMediaItem> = emptyList(),
    val chips: List<IosHomeChip> = emptyList(),
    val continuation: String? = null,
    val audioUrl: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val contentLength: Long? = null,
    val lyrics: String? = null,
    val account: IosAccountProfile? = null,
    val radioEndpoint: WatchEndpoint? = null,
    val currentIndex: Int? = null,
    val errorCode: String? = null,
    val error: String? = null,
)

private val iosBridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
internal val iosJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

internal fun iosRequest(completion: (String) -> Unit, block: suspend () -> IosBridgeResponse) {
    iosBridgeScope.launch {
        val locale = NSLocale.currentLocale
        YouTube.locale = YouTubeLocale(
            gl = locale.countryCode?.takeIf(String::isNotBlank) ?: "US",
            hl = locale.languageCode?.takeIf(String::isNotBlank) ?: "en",
        )
        if (YouTube.visitorData.isNullOrBlank()) {
            YouTube.visitorData = YouTube.visitorData().getOrNull()
                ?: YouTube.DEFAULT_VISITOR_DATA
        }
        completion(iosJson.encodeToString(iosResponse(block)))
    }
}

internal suspend fun iosResponse(block: suspend () -> IosBridgeResponse): IosBridgeResponse =
    try {
        block()
    } catch (error: CancellationException) {
        IosBridgeResponse(error = "The request was cancelled.")
    } catch (error: Exception) {
        IosBridgeResponse(
            errorCode = if (error is IosDuplicateSongException) "duplicate_song" else null,
            error = error.message?.takeIf(String::isNotBlank) ?: error::class.simpleName ?: "Unknown error",
        )
    }

internal fun YTItem.toIosMediaItem(): IosMediaItem {
    val subtitle = when (this) {
        is SongItem -> artists.joinToString { it.name }.ifBlank { "Song" }
        is AlbumItem -> artists?.joinToString { it.name }.orEmpty().ifBlank { "Album" }
        is PlaylistItem -> author?.name?.ifBlank { "Playlist" } ?: "Playlist"
        is ArtistItem -> subscriptions?.ifBlank { "Artist" } ?: "Artist"
    }
    val kind = when (this) {
        is SongItem -> "song"
        is AlbumItem -> "album"
        is PlaylistItem -> "playlist"
        is ArtistItem -> "artist"
    }
    return IosMediaItem(
        id = id,
        title = title,
        subtitle = subtitle,
        thumbnail = thumbnail,
        kind = kind,
        explicit = explicit,
        duration = (this as? SongItem)?.duration,
    )
}

private fun List<YTItem>.iosItems(hideExplicit: Boolean): List<IosMediaItem> =
    asSequence()
        .filter { !hideExplicit || !it.explicit }
        .map(YTItem::toIosMediaItem)
        .toList()

fun fetchIosHome(
    hideExplicit: Boolean,
    params: String?,
    continuation: String?,
    completion: (String) -> Unit,
) = iosRequest(completion) {
    val page = YouTube.home(continuation = continuation, params = params).getOrThrow()
    IosBridgeResponse(
        sections = page.sections.mapNotNull { section ->
            section.items.iosItems(hideExplicit).takeIf { it.isNotEmpty() }
                ?.let { IosMediaSection(section.title, it) }
        },
        chips = page.chips.orEmpty().mapNotNull { chip ->
            val endpoint = chip.endpoint
            endpoint?.params?.takeIf { endpoint.browseId == "FEmusic_home" }
                ?.let { IosHomeChip(chip.title, it) }
        },
        continuation = page.continuation,
    )
}

fun fetchIosExplore(hideExplicit: Boolean, completion: (String) -> Unit) = iosRequest(completion) {
    val page = YouTube.explore().getOrThrow()
    IosBridgeResponse(
        sections = buildList {
            page.newReleaseAlbums.iosItems(hideExplicit).takeIf { it.isNotEmpty() }?.let {
                add(IosMediaSection("New releases", it))
            }
            page.moodAndGenres.takeIf { it.isNotEmpty() }?.let { genres ->
                add(IosMediaSection("Moods and genres", genres.map {
                    IosMediaItem(
                        id = it.endpoint.browseId,
                        title = it.title,
                        subtitle = "",
                        thumbnail = "",
                        kind = "genre",
                        params = it.endpoint.params,
                    )
                }))
            }
            page.newClips.iosItems(hideExplicit).takeIf { it.isNotEmpty() }?.let {
                add(IosMediaSection("New clips", it))
            }
        },
    )
}

fun searchIosMusic(
    query: String,
    category: String,
    hideExplicit: Boolean,
    continuation: String?,
    completion: (String) -> Unit,
) = iosRequest(completion) {
    require(query.isNotBlank()) { "Enter a search term." }
    val filter = when (category) {
        "songs" -> YouTube.SearchFilter.FILTER_SONG
        "videos" -> YouTube.SearchFilter.FILTER_VIDEO
        "albums" -> YouTube.SearchFilter.FILTER_ALBUM
        "artists" -> YouTube.SearchFilter.FILTER_ARTIST
        "playlists" -> YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST
        "community" -> YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST
        else -> throw IllegalArgumentException("Unsupported search category.")
    }
    val page = if (continuation == null) {
        YouTube.search(query.trim(), filter).getOrThrow()
    } else {
        YouTube.searchContinuation(continuation).getOrThrow()
    }
    IosBridgeResponse(items = page.items.iosItems(hideExplicit), continuation = page.continuation)
}

fun fetchIosCollection(
    kind: String,
    id: String,
    params: String?,
    hideExplicit: Boolean,
    continuation: String?,
    completion: (String) -> Unit,
) = iosRequest(completion) {
    require(id.isNotBlank()) { "Missing collection identifier." }
    val useLogin = !YouTube.cookie.isNullOrBlank() && YouTube.useLoginForBrowse
    when {
        kind == "playlist" && continuation != null -> {
            val page = YouTube.playlistContinuation(continuation, setLogin = useLogin).getOrThrow()
            IosBridgeResponse(
                items = page.songs.iosItems(hideExplicit),
                continuation = page.continuation,
            )
        }
        kind == "playlist" -> {
            val page = YouTube.playlist(id.removePrefix("VL"), setLogin = useLogin).getOrThrow()
            IosBridgeResponse(
                items = page.songs.iosItems(hideExplicit),
                continuation = page.songsContinuation ?: page.continuation,
            )
        }
        kind == "album" -> {
            val page = YouTube.album(id)?.getOrThrow()
                ?: throw IllegalStateException("Album details are unavailable.")
            IosBridgeResponse(items = page.songs.iosItems(hideExplicit))
        }
        kind == "artist" -> {
            val page = YouTube.artist(id).getOrThrow()
            IosBridgeResponse(sections = page.sections.mapNotNull { section ->
                section.items.iosItems(hideExplicit).takeIf { it.isNotEmpty() }
                    ?.let { IosMediaSection(section.title, it) }
            })
        }
        kind == "genre" -> {
            val page = YouTube.browse(id, params).getOrThrow()
            IosBridgeResponse(sections = page.items.mapNotNull { section ->
                section.items.iosItems(hideExplicit).takeIf { it.isNotEmpty() }
                    ?.let { IosMediaSection(section.title.orEmpty(), it) }
            })
        }
        else -> throw IllegalArgumentException("Unsupported collection type.")
    }
}

fun fetchIosYouTubeLibrary(
    category: String,
    hideExplicit: Boolean,
    continuation: String?,
    completion: (String) -> Unit,
) = iosRequest(completion) {
    check(!YouTube.cookie.isNullOrBlank() && YouTube.useLoginForBrowse) { "Sign in to YouTube Music first." }
    if (category == "songs") {
        if (continuation == null) {
            val page = YouTube.playlist("LM", setLogin = true).getOrThrow()
            IosBridgeResponse(
                items = page.songs.iosItems(hideExplicit).map { it.copy(remoteLiked = true) },
                continuation = page.songsContinuation ?: page.continuation,
            )
        } else {
            val page = YouTube.playlistContinuation(continuation, setLogin = true).getOrThrow()
            IosBridgeResponse(
                items = page.songs.iosItems(hideExplicit).map { it.copy(remoteLiked = true) },
                continuation = page.continuation,
            )
        }
    } else if (continuation != null) {
        val page = YouTube.libraryContinuation(continuation).getOrThrow()
        IosBridgeResponse(items = page.items.iosItems(hideExplicit), continuation = page.continuation)
    } else {
        val browseId = when (category) {
            "albums" -> "FEmusic_liked_albums"
            "artists" -> "FEmusic_library_corpus_artists"
            "playlists" -> "FEmusic_liked_playlists"
            else -> throw IllegalArgumentException("Unsupported library category.")
        }
        val page = YouTube.library(browseId).getOrThrow()
        IosBridgeResponse(items = page.items.iosItems(hideExplicit), continuation = page.continuation)
    }
}

internal fun NextResult.toIosRadioResponse(hideExplicit: Boolean): IosBridgeResponse {
    val visible = items.withIndex().filter { (index, item) ->
        !hideExplicit || !item.explicit || index == currentIndex
    }
    val selected = currentIndex?.let { index -> visible.indexOfFirst { it.index == index }.takeIf { it >= 0 } }
    return IosBridgeResponse(
        items = visible.map { it.value.toIosMediaItem() },
        continuation = continuation,
        radioEndpoint = endpoint,
        currentIndex = selected,
    )
}

fun fetchIosRadio(
    endpointJson: String,
    continuation: String?,
    hideExplicit: Boolean,
    completion: (String) -> Unit,
) = iosRequest(completion) {
    val endpoint = iosJson.decodeFromString<WatchEndpoint>(endpointJson)
    require(!endpoint.videoId.isNullOrBlank() || !endpoint.playlistId.isNullOrBlank()) {
        "Missing radio endpoint."
    }
    YouTube.next(endpoint, continuation).getOrThrow().toIosRadioResponse(hideExplicit)
}
