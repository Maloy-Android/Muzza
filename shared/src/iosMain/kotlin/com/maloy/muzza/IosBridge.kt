package com.maloy.muzza

import com.maloy.innertube.YouTube
import com.maloy.innertube.models.AlbumItem
import com.maloy.innertube.models.ArtistItem
import com.maloy.innertube.models.BrowseEndpoint
import com.maloy.innertube.models.GridRenderer
import com.maloy.innertube.models.MusicResponsiveListItemRenderer
import com.maloy.innertube.models.MusicTwoRowItemRenderer
import com.maloy.innertube.models.PlaylistItem
import com.maloy.innertube.models.SectionListRenderer
import com.maloy.innertube.models.SongItem
import com.maloy.innertube.models.WatchEndpoint
import com.maloy.innertube.models.YTItem
import com.maloy.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.maloy.innertube.models.getContinuation
import com.maloy.innertube.models.getItems
import com.maloy.innertube.pages.ArtistItemsPage
import com.maloy.innertube.pages.HomePage
import com.maloy.innertube.pages.NextResult
import com.maloy.innertube.models.response.BrowseResponse
import com.maloy.innertube.models.getThumbnailUrl
import com.metrolist.innertubex.models.YouTubeLocale
import io.ktor.client.call.body
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
    val moreItem: IosMediaItem? = null,
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

private var iosGl = "US"
private var iosHl = "en"

fun configureIosLocale(gl: String, hl: String) {
    iosGl = gl.takeIf(String::isNotBlank) ?: "US"
    iosHl = hl.takeIf(String::isNotBlank) ?: "en"
    YouTube.locale = YouTubeLocale(gl = iosGl, hl = iosHl)
}

internal fun iosRequest(completion: (String) -> Unit, block: suspend () -> IosBridgeResponse) {
    iosBridgeScope.launch {
        YouTube.locale = YouTubeLocale(gl = iosGl, hl = iosHl)
        if (YouTube.visitorData.isNullOrBlank()) {
            YouTube.visitorData = YouTube.visitorData().getOrNull()
                ?: YouTube.DEFAULT_VISITOR_DATA
        }
        completion(iosJson.encodeToString(iosRequestResponse(block)))
    }
}

private suspend fun iosRequestResponse(block: suspend () -> IosBridgeResponse): IosBridgeResponse =
    try {
        block()
    } catch (error: CancellationException) {
        IosBridgeResponse(error = "The request was cancelled.")
    } catch (error: Exception) {
        val message = error.message.orEmpty()
        if ("400" in message || "INVALID_ARGUMENT" in message || "badRequest" in message) {
            YouTube.visitorData = YouTube.visitorData().getOrNull()
                ?.takeIf(String::isNotBlank)
                ?: YouTube.DEFAULT_VISITOR_DATA
            try {
                return block()
            } catch (retryError: CancellationException) {
                return IosBridgeResponse(error = "The request was cancelled.")
            } catch (retryError: Exception) {
                return iosResponseFromError(retryError)
            }
        }
        iosResponseFromError(error)
    }

private fun iosResponseFromError(error: Exception): IosBridgeResponse =
    IosBridgeResponse(
        errorCode = if (error is IosDuplicateSongException) "duplicate_song" else null,
        error = error.message?.takeIf(String::isNotBlank) ?: error::class.simpleName ?: "Unknown error",
    )

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

private fun BrowseEndpoint.toIosBrowseItem(title: String): IosMediaItem =
    IosMediaItem(
        id = browseId,
        title = title,
        subtitle = "",
        thumbnail = "",
        kind = "browse",
        params = params,
    )

private fun MusicTwoRowItemRenderer.toIosMediaItemOrNull(): IosMediaItem? {
    ArtistItemsPage.fromMusicTwoRowItemRenderer(this)?.let { return it.toIosMediaItem() }

    val browseId = navigationEndpoint.browseEndpoint?.browseId
    val videoId = navigationEndpoint.watchEndpoint?.videoId
    val kind = when {
        isSong -> "song"
        isAlbum -> "album"
        isPlaylist -> "playlist"
        isArtist || isUserChannel -> "artist"
        else -> return null
    }
    val id = when (kind) {
        "song" -> videoId
        "playlist" -> browseId?.removePrefix("VL")
        else -> browseId
    }?.takeIf(String::isNotBlank) ?: return null
    val title = title.runs?.firstOrNull()?.text?.takeIf(String::isNotBlank) ?: return null
    return IosMediaItem(
        id = id,
        title = title,
        subtitle = subtitle?.runs?.joinToString("") { it.text }.orEmpty(),
        thumbnail = thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl().orEmpty(),
        kind = kind,
        explicit = subtitleBadges?.any {
            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
        } == true,
    )
}

private fun MusicResponsiveListItemRenderer.toIosMediaItemOrNull(): IosMediaItem? {
    ArtistItemsPage.fromMusicResponsiveListItemRenderer(this)?.let { return it.toIosMediaItem() }
    val titleColumn = flexColumns.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer?.text
    val titleRun = titleColumn?.runs?.firstOrNull()
    val browseEndpoint = navigationEndpoint?.browseEndpoint ?: titleRun?.navigationEndpoint?.browseEndpoint
    val videoId = playlistItemData?.videoId
        ?: navigationEndpoint?.watchEndpoint?.videoId
        ?: titleRun?.navigationEndpoint?.watchEndpoint?.videoId
    val kind = when {
        isSong -> "song"
        isPlaylist -> "playlist"
        isAlbum -> "album"
        isArtist || isUserChannel -> "artist"
        else -> return null
    }
    val id = when (kind) {
        "song" -> videoId
        "playlist" -> browseEndpoint?.browseId?.removePrefix("VL")
        else -> browseEndpoint?.browseId
    }
    if (id.isNullOrBlank()) return null
    val title = titleRun?.text?.takeIf(String::isNotBlank) ?: return null
    val artists = flexColumns.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer
        ?.text?.runs.orEmpty().joinToString("") { it.text }
    return IosMediaItem(
        id = id,
        title = title,
        subtitle = artists.ifBlank { if (kind == "song") "Song" else kind.replaceFirstChar(Char::uppercase) },
        thumbnail = thumbnail?.musicThumbnailRenderer?.getThumbnailUrl().orEmpty(),
        kind = kind,
        explicit = badges?.any {
            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
        } == true,
    )
}

private fun SectionListRenderer.Content.toIosMediaSection(hideExplicit: Boolean): IosMediaSection? {
    val title: String
    val items: List<IosMediaItem>
    val endpoint: BrowseEndpoint?
    when {
        musicCarouselShelfRenderer != null -> {
            val renderer = musicCarouselShelfRenderer
            val header = renderer.header?.musicCarouselShelfBasicHeaderRenderer
            title = header?.title?.runs?.firstOrNull()?.text ?: return null
            endpoint = header?.title?.runs?.firstNotNullOfOrNull {
                it.navigationEndpoint?.browseEndpoint
            } ?: header?.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint
            items = renderer.contents.mapNotNull { content ->
                content.musicTwoRowItemRenderer?.toIosMediaItemOrNull()
                    ?: content.musicResponsiveListItemRenderer?.toIosMediaItemOrNull()
            }
        }
        musicShelfRenderer != null -> {
            val renderer = musicShelfRenderer
            title = renderer.title?.runs?.firstOrNull()?.text
                ?: renderer.moreContentButton?.buttonRenderer?.text?.runs?.firstOrNull()?.text
                ?: return null
            endpoint = renderer.title?.runs?.firstNotNullOfOrNull { it.navigationEndpoint?.browseEndpoint }
                ?: renderer.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint
            items = renderer.contents.orEmpty().mapNotNull {
                it.musicResponsiveListItemRenderer?.toIosMediaItemOrNull()
            }
        }
        gridRenderer != null -> {
            val renderer = gridRenderer
            title = renderer.header?.gridHeaderRenderer?.title?.runs?.firstOrNull()?.text ?: return null
            endpoint = renderer.header?.gridHeaderRenderer?.title?.runs?.firstNotNullOfOrNull {
                it.navigationEndpoint?.browseEndpoint
            }
            items = renderer.items.mapNotNull { it.musicTwoRowItemRenderer?.toIosMediaItemOrNull() }
        }
        musicCardShelfRenderer != null -> {
            val renderer = musicCardShelfRenderer
            title = renderer.title.runs.firstOrNull()?.text ?: return null
            endpoint = renderer.onTap.browseEndpoint
            items = renderer.contents.orEmpty().mapNotNull {
                it.musicResponsiveListItemRenderer?.toIosMediaItemOrNull()
            }
        }
        itemSectionRenderer != null -> {
            val renderer = itemSectionRenderer
            title = renderer.header?.itemSectionTabbedHeaderRenderer?.title?.runs?.firstOrNull()?.text
                ?: return null
            endpoint = renderer.header?.itemSectionTabbedHeaderRenderer?.title?.runs
                ?.firstNotNullOfOrNull { it.navigationEndpoint?.browseEndpoint }
            items = renderer.contents.orEmpty().mapNotNull {
                it.musicResponsiveListItemRenderer?.toIosMediaItemOrNull()
            }
        }
        else -> return null
    }
    val visible = items.filter { !hideExplicit || !it.explicit }
    if (visible.isEmpty()) return null
    return IosMediaSection(title, visible, endpoint?.toIosBrowseItem(title))
}

private fun List<SectionListRenderer.Content>?.toIosMediaSections(hideExplicit: Boolean): List<IosMediaSection> =
    orEmpty().mapNotNull { it.toIosMediaSection(hideExplicit) }

private fun List<GridRenderer.Item>.toIosLibraryItems(hideExplicit: Boolean): List<IosMediaItem> =
    mapNotNull { it.musicTwoRowItemRenderer?.toIosMediaItemOrNull() }
        .filter { !hideExplicit || !it.explicit }
        .distinctBy { "${it.kind}:${it.id}" }

private fun List<MusicResponsiveListItemRenderer>.toIosLibraryItems(
    hideExplicit: Boolean,
): List<IosMediaItem> =
    mapNotNull { it.toIosMediaItemOrNull() }
        .filter { !hideExplicit || !it.explicit }
        .distinctBy { "${it.kind}:${it.id}" }

private suspend fun fetchIosLibraryPage(
    browseId: String?,
    continuation: String?,
    hideExplicit: Boolean,
): IosBridgeResponse {
    val response = if (continuation == null) {
        YouTube.extractionTransport().innerTube.browse(
            client = WEB_REMIX,
            browseId = requireNotNull(browseId),
            setLogin = true,
        ).body<BrowseResponse>()
    } else {
        YouTube.extractionTransport().innerTube.browse(
            client = WEB_REMIX,
            continuation = continuation,
            setLogin = true,
        ).body<BrowseResponse>()
    }

    val sectionContents = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
        ?.tabRenderer?.content?.sectionListRenderer?.contents
    val continuationContents = response.continuationContents
    val items = when {
        continuationContents?.gridContinuation != null ->
            continuationContents.gridContinuation.items.toIosLibraryItems(hideExplicit)
        continuationContents?.musicShelfContinuation != null ->
            continuationContents.musicShelfContinuation.contents
                .mapNotNull { it.musicResponsiveListItemRenderer }
                .toIosLibraryItems(hideExplicit)
        sectionContents != null -> sectionContents.flatMap { content ->
            when {
                content.gridRenderer != null -> content.gridRenderer.items.toIosLibraryItems(hideExplicit)
                content.musicShelfRenderer != null -> content.musicShelfRenderer.contents.orEmpty()
                    .mapNotNull { it.musicResponsiveListItemRenderer }
                    .toIosLibraryItems(hideExplicit)
                else -> emptyList()
            }
        }.distinctBy { "${it.kind}:${it.id}" }
        else -> emptyList()
    }
    val nextContinuation = continuationContents?.gridContinuation?.continuations?.getContinuation()
        ?: continuationContents?.musicShelfContinuation?.continuations?.getContinuation()
        ?: sectionContents?.firstNotNullOfOrNull { content ->
            content.gridRenderer?.continuations?.getContinuation()
                ?: content.musicShelfRenderer?.continuations?.getContinuation()
        }
    return IosBridgeResponse(items = items, continuation = nextContinuation)
}

private suspend fun fetchIosPlaylistSongs(
    playlistId: String,
    continuation: String?,
    setLogin: Boolean,
    hideExplicit: Boolean,
): IosBridgeResponse {
    val innerTube = YouTube.extractionTransport().innerTube
    val response = if (continuation == null) {
        innerTube.browse(
            client = WEB_REMIX,
            browseId = "VL$playlistId",
            setLogin = setLogin,
            useLoginForBrowseEnabled = setLogin,
        ).body<BrowseResponse>()
    } else {
        innerTube.browse(
            client = WEB_REMIX,
            continuation = continuation,
            browseId = "",
            setLogin = setLogin,
            useLoginForBrowseEnabled = setLogin,
        ).body<BrowseResponse>()
    }

    val initialSections = response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents
        ?.sectionListRenderer?.contents.orEmpty() +
        response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents.orEmpty()
    val continuationSections = response.continuationContents?.sectionListContinuation?.contents.orEmpty()
    val rows = initialSections.flatMap { it.musicPlaylistShelfRenderer?.contents.orEmpty() } +
        continuationSections.flatMap {
            it.musicPlaylistShelfRenderer?.contents ?: it.musicShelfRenderer?.contents.orEmpty()
        } +
        response.continuationContents?.musicPlaylistShelfContinuation?.contents.orEmpty() +
        response.continuationContents?.musicShelfContinuation?.contents.orEmpty() +
        response.onResponseReceivedActions?.firstOrNull()
            ?.appendContinuationItemsAction?.continuationItems.orEmpty()
    val items = rows.mapNotNull { it.musicResponsiveListItemRenderer?.toIosMediaItemOrNull() }
        .filter { !hideExplicit || !it.explicit }
    val nextContinuation = response.continuationContents?.sectionListContinuation?.continuations?.getContinuation()
        ?: response.continuationContents?.musicPlaylistShelfContinuation?.continuations?.getContinuation()
        ?: response.continuationContents?.musicShelfContinuation?.continuations?.getContinuation()
        ?: response.onResponseReceivedActions?.firstOrNull()
            ?.appendContinuationItemsAction?.continuationItems?.getContinuation()
        ?: initialSections.firstNotNullOfOrNull { section ->
            section.musicPlaylistShelfRenderer?.let { shelf ->
                shelf.contents.getContinuation() ?: shelf.continuations?.getContinuation()
            }
        }
    return IosBridgeResponse(items = items, continuation = nextContinuation)
}

fun fetchIosHome(
    hideExplicit: Boolean,
    params: String?,
    continuation: String?,
    completion: (String) -> Unit,
) = iosRequest(completion) {
    val response = if (continuation == null) {
        YouTube.extractionTransport().innerTube
            .browse(WEB_REMIX, browseId = "FEmusic_home", params = params)
            .body<BrowseResponse>()
    } else {
        YouTube.extractionTransport().innerTube
            .browse(WEB_REMIX, continuation = continuation)
            .body<BrowseResponse>()
    }
    val sectionList = if (continuation == null) {
        response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer
    } else {
        null
    }
    val sections = sectionList?.contents.toIosMediaSections(hideExplicit)
        .ifEmpty {
            response.continuationContents?.sectionListContinuation?.contents
                .toIosMediaSections(hideExplicit)
        }
    val nextContinuation = sectionList?.continuations?.getContinuation()
        ?: response.continuationContents?.sectionListContinuation?.continuations?.getContinuation()
    IosBridgeResponse(
        sections = sections,
        chips = sectionList?.header?.chipCloudRenderer?.chips.orEmpty().mapNotNull {
            HomePage.Chip.fromChipCloudChipRenderer(it)
        }.mapNotNull { chip ->
            val endpoint = chip.endpoint
            endpoint?.params?.takeIf { endpoint.browseId == "FEmusic_home" }
                ?.let { IosHomeChip(chip.title, it) }
        },
        continuation = nextContinuation,
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
        kind == "playlist" -> {
            fetchIosPlaylistSongs(id.removePrefix("VL"), continuation, useLogin, hideExplicit)
        }
        kind == "album" -> {
            val page = YouTube.album(id, withSongs = false)?.getOrThrow()
                ?: throw IllegalStateException("Album details are unavailable.")
            fetchIosPlaylistSongs(
                page.album.playlistId,
                continuation,
                setLogin = false,
                hideExplicit = hideExplicit,
            )
        }
        kind == "artist" -> {
            val page = YouTube.artist(id).getOrThrow()
            IosBridgeResponse(sections = page.sections.mapNotNull { section ->
                section.items.iosItems(hideExplicit).takeIf { it.isNotEmpty() }
                    ?.let {
                        IosMediaSection(
                            section.title,
                            it,
                            section.moreEndpoint?.toIosBrowseItem(section.title),
                        )
                    }
            })
        }
        kind == "browse" && continuation != null -> {
            val page = YouTube.artistItemsContinuation(continuation).getOrThrow()
            IosBridgeResponse(items = page.items.iosItems(hideExplicit), continuation = page.continuation)
        }
        kind == "browse" -> {
            val page = YouTube.artistItems(BrowseEndpoint(id, params)).getOrThrow()
            IosBridgeResponse(items = page.items.iosItems(hideExplicit), continuation = page.continuation)
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
    check(!YouTube.cookie.isNullOrBlank() && YouTube.useLoginForBrowse) { "Sign in to YouTube Music first."     }
    if (category == "songs") {
        fetchIosPlaylistSongs("LM", continuation, setLogin = true, hideExplicit = hideExplicit)
            .let { page ->
                page.copy(items = page.items.map { it.copy(remoteLiked = true) })
            }
    } else if (continuation != null) {
        fetchIosLibraryPage(null, continuation, hideExplicit)
    } else {
        val browseId = when (category) {
            "albums" -> "FEmusic_liked_albums"
            "artists" -> "FEmusic_library_corpus_artists"
            "playlists" -> "FEmusic_liked_playlists"
            else -> throw IllegalArgumentException("Unsupported library category.")
        }
        fetchIosLibraryPage(browseId, null, hideExplicit)
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
