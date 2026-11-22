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
import com.maloy.innertube.models.getContinuation
import com.maloy.innertube.pages.ArtistItemsPage
import com.maloy.innertube.pages.HomePage
import com.maloy.innertube.pages.NextResult
import com.maloy.innertube.models.response.BrowseResponse
import com.metrolist.innertubex.models.YouTubeClient.Companion.WEB_REMIX
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
data class IosArtistMetadata(
    val id: String?,
    val name: String,
    val thumbnail: String? = null,
    val isProfile: Boolean = false,
)

@Serializable
data class IosAlbumMetadata(
    val id: String,
    val title: String,
)

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
    val artists: List<IosArtistMetadata> = emptyList(),
    val album: IosAlbumMetadata? = null,
    val playlistId: String? = null,
    val playlistTitle: String? = null,
    val isEditable: Boolean = false,
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

internal suspend fun iosResponse(block: suspend () -> IosBridgeResponse): IosBridgeResponse =
    iosRequestResponse(block)

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
    val song = this as? SongItem
    return IosMediaItem(
        id = id,
        title = title,
        subtitle = subtitle,
        thumbnail = thumbnail.toIosArtworkUrl(),
        kind = kind,
        explicit = explicit,
        duration = song?.duration,
        artists = when (this) {
            is SongItem -> artists.map { it.toIosArtistMetadata() }
            is AlbumItem -> artists.orEmpty().map { it.toIosArtistMetadata() }
            is PlaylistItem -> listOfNotNull(author?.toIosArtistMetadata())
            is ArtistItem -> listOf(IosArtistMetadata(id, title, thumbnail.toIosArtworkUrl(), isProfile))
        },
        album = song?.album?.let { IosAlbumMetadata(it.id, it.name) }
            ?: (this as? AlbumItem)?.let { IosAlbumMetadata(it.id, it.title) },
        isEditable = (this as? PlaylistItem)?.isEditable ?: false,
    )
}

private fun com.maloy.innertube.models.Artist.toIosArtistMetadata() =
    IosArtistMetadata(id = id, name = name, isProfile = isProfile)

private val iosGoogleArtworkSize =
    Regex("^(https://(?:lh3|yt3)\\.googleusercontent\\.com/[^?]*?)=w\\d+-h\\d+[^?]*(\\?.*)?$")
private val iosGgphtArtworkSize =
    Regex("^(https://yt3\\.ggpht\\.com/[^?=]+)=(?:s\\d+|w\\d+-h\\d+)[^?]*(\\?.*)?$")

internal fun String.toIosArtworkUrl(): String {
    iosGoogleArtworkSize.matchEntire(this)?.groupValues?.let {
        return "${it[1]}=w1080-h1080-p-l90-rj${it[2]}"
    }
    iosGgphtArtworkSize.matchEntire(this)?.groupValues?.let {
        return "${it[1]}=w1080-h1080-p-l90-rj${it[2]}"
    }
    return this
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
        thumbnail = thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl().orEmpty().toIosArtworkUrl(),
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
    val artistRuns = flexColumns.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer
        ?.text?.runs.orEmpty()
    val artists = artistRuns.joinToString("") { it.text }
    val artistMetadata = artistRuns.mapNotNull { run ->
        run.text.takeIf(String::isNotBlank)?.let { name ->
            IosArtistMetadata(
                id = run.navigationEndpoint?.browseEndpoint?.browseId,
                name = name,
            )
        }
    }
    val albumRun = flexColumns.getOrNull(2)?.musicResponsiveListItemFlexColumnRenderer
        ?.text?.runs?.firstOrNull()
    return IosMediaItem(
        id = id,
        title = title,
        subtitle = artists.ifBlank { if (kind == "song") "Song" else kind.replaceFirstChar(Char::uppercase) },
        thumbnail = thumbnail?.musicThumbnailRenderer?.getThumbnailUrl().orEmpty().toIosArtworkUrl(),
        kind = kind,
        explicit = badges?.any {
            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
        } == true,
        artists = artistMetadata,
        album = albumRun?.navigationEndpoint?.browseEndpoint?.browseId?.let { albumId ->
            IosAlbumMetadata(albumId, albumRun.text)
        },
    )
}

private fun SectionListRenderer.Content.toIosMediaSection(hideExplicit: Boolean): IosMediaSection? {
    val title: String
    val items: List<IosMediaItem>
    val endpoint: BrowseEndpoint?
    when {
        musicCarouselShelfRenderer != null -> {
            val renderer = musicCarouselShelfRenderer ?: return null
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
            val renderer = musicShelfRenderer ?: return null
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
            val renderer = gridRenderer ?: return null
            title = renderer.header?.gridHeaderRenderer?.title?.runs?.firstOrNull()?.text ?: return null
            endpoint = renderer.header?.gridHeaderRenderer?.title?.runs?.firstNotNullOfOrNull {
                it.navigationEndpoint?.browseEndpoint
            }
            items = renderer.items.mapNotNull { it.musicTwoRowItemRenderer?.toIosMediaItemOrNull() }
        }
        musicCardShelfRenderer != null -> {
            val renderer = musicCardShelfRenderer ?: return null
            title = renderer.title.runs?.firstOrNull()?.text ?: return null
            endpoint = renderer.onTap.browseEndpoint
            items = renderer.contents.orEmpty().mapNotNull {
                it.musicResponsiveListItemRenderer?.toIosMediaItemOrNull()
            }
        }
        itemSectionRenderer != null -> {
            val renderer = itemSectionRenderer ?: return null
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
        .orEmpty()
    val continuationContents = response.continuationContents
    val gridContinuation = continuationContents?.gridContinuation
    val musicShelfContinuation = continuationContents?.musicShelfContinuation
    val sectionListContinuation = continuationContents?.sectionListContinuation
    val appendItems = response.onResponseReceivedActions?.flatMap {
        it.appendContinuationItemsAction?.continuationItems.orEmpty()
    }.orEmpty()
    val items = when {
        gridContinuation != null ->
            gridContinuation.items.toIosLibraryItems(hideExplicit)
        musicShelfContinuation != null ->
            musicShelfContinuation.contents.orEmpty()
                .mapNotNull { it.musicResponsiveListItemRenderer }
                .toIosLibraryItems(hideExplicit)
        sectionListContinuation != null -> sectionListContinuation.contents.orEmpty().flatMap { content ->
            val grid = content.gridRenderer
            val shelf = content.musicShelfRenderer
            when {
                grid != null -> grid.items.toIosLibraryItems(hideExplicit)
                shelf != null -> shelf.contents.orEmpty()
                    .mapNotNull { it.musicResponsiveListItemRenderer }
                    .toIosLibraryItems(hideExplicit)
                else -> emptyList()
            }
        }.distinctBy { "${it.kind}:${it.id}" }
        sectionContents.isNotEmpty() -> sectionContents.flatMap { content ->
            val grid = content.gridRenderer
            val shelf = content.musicShelfRenderer
            when {
                grid != null -> grid.items.toIosLibraryItems(hideExplicit)
                shelf != null -> shelf.contents.orEmpty()
                    .mapNotNull { it.musicResponsiveListItemRenderer }
                    .toIosLibraryItems(hideExplicit)
                content.musicCardShelfRenderer != null -> content.musicCardShelfRenderer
                    ?.contents.orEmpty()
                    .mapNotNull { it.musicResponsiveListItemRenderer?.toIosMediaItemOrNull() }
                else -> emptyList()
            }
        }.distinctBy { "${it.kind}:${it.id}" }
        else -> emptyList()
    }
    val nextContinuation = continuationContents?.gridContinuation?.continuations?.getContinuation()
        ?: continuationContents?.musicShelfContinuation?.continuations?.getContinuation()
        ?: continuationContents?.sectionListContinuation?.continuations?.getContinuation()
        ?: appendItems.getContinuation()
        ?: sectionContents?.firstNotNullOfOrNull { content ->
            content.gridRenderer?.continuations?.getContinuation()
                ?: content.musicShelfRenderer?.continuations?.getContinuation()
        }
    return IosBridgeResponse(items = items, continuation = nextContinuation)
}

internal suspend fun fetchIosPlaylistSongs(
    playlistId: String,
    continuation: String?,
    setLogin: Boolean,
    hideExplicit: Boolean,
): IosBridgeResponse {
    val sharedTransport = YouTube.extractionTransport().innerTube
    val innerTube = if (setLogin) sharedTransport else sharedTransport.createIsolatedSession(includeAccount = false)
    val response = try {
        if (continuation == null) {
            innerTube.browse(
                client = WEB_REMIX,
                browseId = "VL$playlistId",
                setLogin = setLogin,
            ).body<BrowseResponse>()
        } else {
            innerTube.browse(
                client = WEB_REMIX,
                continuation = continuation,
                browseId = "",
                setLogin = setLogin,
            ).body<BrowseResponse>()
        }
    } finally {
        if (!setLogin) innerTube.close()
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
        response.onResponseReceivedActions?.flatMap {
            it.appendContinuationItemsAction?.continuationItems.orEmpty()
        }.orEmpty()
    val items = rows.mapNotNull { it.musicResponsiveListItemRenderer?.toIosMediaItemOrNull() }
        .filter { !hideExplicit || !it.explicit }
        .distinctBy { "${it.kind}:${it.id}" }
    val nextContinuation = response.continuationContents?.sectionListContinuation?.continuations?.getContinuation()
        ?: response.continuationContents?.musicPlaylistShelfContinuation?.continuations?.getContinuation()
        ?: response.continuationContents?.musicShelfContinuation?.continuations?.getContinuation()
        ?: response.onResponseReceivedActions?.asSequence()
            ?.mapNotNull { it.appendContinuationItemsAction?.continuationItems?.getContinuation() }
            ?.firstOrNull()
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
    val continuedSections = response.continuationContents?.sectionListContinuation?.contents
        .toIosMediaSections(hideExplicit)
    val continuedItems = when {
        response.continuationContents?.gridContinuation != null ->
            response.continuationContents?.gridContinuation?.items?.toIosLibraryItems(hideExplicit).orEmpty()
        response.continuationContents?.musicShelfContinuation != null ->
            response.continuationContents?.musicShelfContinuation?.contents.orEmpty()
                .mapNotNull { it.musicResponsiveListItemRenderer?.toIosMediaItemOrNull() }
                .filter { !hideExplicit || !it.explicit }
        else -> emptyList()
    }
    val continuedTitle = response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text ?: "More"
    val sections = sectionList?.contents.toIosMediaSections(hideExplicit).ifEmpty {
        continuedSections.ifEmpty {
            continuedItems.takeIf { it.isNotEmpty() }?.let {
                listOf(IosMediaSection(continuedTitle, it))
            }.orEmpty()
        }
    }
    val nextContinuation = sectionList?.continuations?.getContinuation()
        ?: response.continuationContents?.sectionListContinuation?.continuations?.getContinuation()
        ?: response.continuationContents?.musicShelfContinuation?.continuations?.getContinuation()
        ?: response.continuationContents?.gridContinuation?.continuations?.getContinuation()
        ?: response.onResponseReceivedActions?.asSequence()
            ?.mapNotNull { it.appendContinuationItemsAction?.continuationItems?.getContinuation() }
            ?.firstOrNull()
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
    val newMixes = YouTube.newReleasesMixesPlaylists().getOrThrow().newMixesPlaylists
    val moodsAndGenres = YouTube.moodAndGenres().getOrThrow()
        .flatMap { it.items }
        .distinctBy { it.endpoint.browseId to it.endpoint.params }
    IosBridgeResponse(
        sections = buildList {
            newMixes.iosItems(hideExplicit).takeIf { it.isNotEmpty() }?.let {
                add(IosMediaSection("New mixes", it))
            }
            page?.newReleaseAlbums?.iosItems(hideExplicit)?.takeIf { it.isNotEmpty() }?.let {
                add(
                    IosMediaSection(
                        "New releases",
                        it,
                        IosMediaItem(
                            id = "FEmusic_new_releases_albums",
                            title = "New releases",
                            subtitle = "",
                            thumbnail = "",
                            kind = "genre",
                        ),
                    ),
                )
            }
            page?.newClips?.iosItems(hideExplicit)?.takeIf { it.isNotEmpty() }?.let {
                add(
                    IosMediaSection(
                        "New clips",
                        it,
                        IosMediaItem(
                            id = "FEmusic_new_releases_videos",
                            title = "New clips",
                            subtitle = "",
                            thumbnail = "",
                            kind = "genre",
                        ),
                    ),
                )
            }
            moodsAndGenres.takeIf { it.isNotEmpty() }?.let { genres ->
                add(
                    IosMediaSection(
                        title = "Moods and genres",
                        items = genres.map {
                            IosMediaItem(
                                id = it.endpoint.browseId,
                                title = it.title,
                                subtitle = "",
                                thumbnail = "",
                                kind = "genre",
                                params = it.endpoint.params,
                            )
                        },
                        moreItem = IosMediaItem(
                            id = "moodAndGenres",
                            title = "Moods and genres",
                            subtitle = "",
                            thumbnail = "",
                            kind = "moodAndGenres",
                        ),
                    ),
                )
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
        "profiles" -> YouTube.SearchFilter.FILTER_PROFILE
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

private suspend fun fetchIosGenrePage(
    id: String,
    params: String?,
    continuation: String?,
    hideExplicit: Boolean,
): IosBridgeResponse {
    val innerTube = YouTube.extractionTransport().innerTube
    val response = if (continuation == null) {
        innerTube.browse(WEB_REMIX, browseId = id, params = params).body<BrowseResponse>()
    } else {
        innerTube.browse(WEB_REMIX, continuation = continuation).body<BrowseResponse>()
    }
    val sectionList = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
        ?.tabRenderer?.content?.sectionListRenderer
    val continuedSections = response.continuationContents?.sectionListContinuation?.contents
        .toIosMediaSections(hideExplicit)
    val continuedItems = when {
        response.continuationContents?.gridContinuation != null ->
            response.continuationContents?.gridContinuation?.items?.toIosLibraryItems(hideExplicit).orEmpty()
        response.continuationContents?.musicShelfContinuation != null ->
            response.continuationContents?.musicShelfContinuation?.contents.orEmpty()
                .mapNotNull { it.musicResponsiveListItemRenderer?.toIosMediaItemOrNull() }
                .filter { !hideExplicit || !it.explicit }
        else -> emptyList()
    }
    val title = response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text
        ?: if (id == "FEmusic_new_releases_videos") "New clips" else "Explore"
    val sections = when {
        continuation == null -> sectionList?.contents.toIosMediaSections(hideExplicit)
        continuedSections.isNotEmpty() -> continuedSections
        continuedItems.isNotEmpty() -> listOf(IosMediaSection(title, continuedItems))
        else -> emptyList()
    }
    val nextContinuation = sectionList?.continuations?.getContinuation()
        ?: response.continuationContents?.sectionListContinuation?.continuations?.getContinuation()
        ?: response.continuationContents?.gridContinuation?.continuations?.getContinuation()
        ?: response.continuationContents?.musicShelfContinuation?.continuations?.getContinuation()
        ?: response.onResponseReceivedActions?.asSequence()
            ?.mapNotNull { it.appendContinuationItemsAction?.continuationItems?.getContinuation() }
            ?.firstOrNull()
    return IosBridgeResponse(sections = sections, continuation = nextContinuation)
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
            fetchIosPlaylistSongs(id.removePrefix("VL"), continuation, useLogin, hideExplicit).let { result ->
                result.copy(
                    items = result.items.map { song ->
                        song.copy(playlistId = id.removePrefix("VL"))
                    },
                )
            }
        }
        kind == "album" -> {
            val page = YouTube.album(id, withSongs = false)?.getOrThrow()
                ?: throw IllegalStateException("Album details are unavailable.")
            fetchIosPlaylistSongs(
                page.album.playlistId,
                continuation,
                setLogin = false,
                hideExplicit = hideExplicit,
            ).let { result ->
                result.copy(
                    items = result.items.map { song ->
                        song.copy(
                            album = IosAlbumMetadata(page.album.id, page.album.title),
                            artists = song.artists.ifEmpty {
                                page.album.artists.orEmpty().map { it.toIosArtistMetadata() }
                            },
                        )
                    },
                )
            }
        }
        kind == "artist" -> {
            val page = YouTube.artist(id).getOrThrow()
            IosBridgeResponse(sections = page.sections.mapNotNull { section ->
                val initialItems = section.items.iosItems(hideExplicit)
                val items = if (section.moreEndpoint != null) {
                    val endpoint = section.moreEndpoint!!
                    val allItems = mutableListOf<IosMediaItem>()
                    allItems.addAll(initialItems)
                    val initialPage = YouTube.artistItems(
                        BrowseEndpoint(endpoint.browseId, endpoint.params)
                        ).getOrThrow()
                        allItems.addAll(initialPage.items.iosItems(hideExplicit))
                        var token = initialPage.continuation
                        val seenTokens = mutableSetOf<String>()
                        while (!token.isNullOrBlank()) {
                            check(seenTokens.add(token)) { "YouTube returned a repeated artist continuation." }
                            val continuationPage = YouTube.artistItemsContinuation(token).getOrThrow()
                            allItems.addAll(continuationPage.items.iosItems(hideExplicit))
                            token = continuationPage.continuation
                    }
                    allItems.distinctBy { "${it.kind}:${it.id}" }
                } else {
                    initialItems
                }
                items.takeIf { it.isNotEmpty() }?.let {
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
            fetchIosGenrePage(id, params, continuation, hideExplicit)
        }
        kind == "moodAndGenres" -> {
            val sections = YouTube.moodAndGenres().getOrThrow().mapNotNull { group ->
                group.items.map { item ->
                    IosMediaItem(
                        id = item.endpoint.browseId,
                        title = item.title,
                        subtitle = "",
                        thumbnail = "",
                        kind = "genre",
                        params = item.endpoint.params,
                    )
                }.takeIf { it.isNotEmpty() }?.let { IosMediaSection(group.title, it) }
            }
            IosBridgeResponse(sections = sections)
        }
        else -> throw IllegalArgumentException("Unsupported collection type.")
    }
}

internal suspend fun fetchIosYouTubeLibraryPage(
    category: String,
    hideExplicit: Boolean,
    continuation: String?,
): IosBridgeResponse {
    check(!YouTube.cookie.isNullOrBlank() && YouTube.useLoginForBrowse) { "Sign in to YouTube Music first."     }
    val page = if (category == "songs") {
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
    return page
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
