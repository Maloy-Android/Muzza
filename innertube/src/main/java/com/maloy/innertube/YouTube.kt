package com.maloy.innertube

import com.maloy.innertube.models.AccountInfo
import com.maloy.innertube.models.AlbumItem
import com.maloy.innertube.models.Artist
import com.maloy.innertube.models.ArtistItem
import com.maloy.innertube.models.BrowseEndpoint
import com.maloy.innertube.models.GridRenderer
import com.maloy.innertube.models.MusicResponsiveListItemRenderer
import com.maloy.innertube.models.MusicShelfRenderer
import com.maloy.innertube.models.MusicTwoRowItemRenderer
import com.maloy.innertube.models.PlaylistItem
import com.maloy.innertube.models.SearchSuggestions
import com.maloy.innertube.models.SongItem
import com.maloy.innertube.models.WatchEndpoint
import com.maloy.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV
import com.maloy.innertube.models.YouTubeClient
import com.maloy.innertube.models.YouTubeClient.Companion.WEB
import com.maloy.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.maloy.innertube.models.YouTubeLocale
import com.maloy.innertube.models.getContinuation
import com.maloy.innertube.models.getItems
import com.maloy.innertube.models.oddElements
import com.maloy.innertube.models.response.AccountMenuResponse
import com.maloy.innertube.models.response.AddItemYouTubePlaylistResponse
import com.maloy.innertube.models.response.BrowseResponse
import com.maloy.innertube.models.response.CreatePlaylistResponse
import com.maloy.innertube.models.response.FeedbackResponse
import com.maloy.innertube.models.response.GetQueueResponse
import com.maloy.innertube.models.response.GetSearchSuggestionsResponse
import com.maloy.innertube.models.response.GetTranscriptResponse
import com.maloy.innertube.models.response.NextResponse
import com.maloy.innertube.models.response.PlayerResponse
import com.maloy.innertube.models.response.SearchResponse
import com.maloy.innertube.pages.AlbumPage
import com.maloy.innertube.pages.ArtistItemsContinuationPage
import com.maloy.innertube.pages.ArtistItemsPage
import com.maloy.innertube.pages.ArtistPage
import com.maloy.innertube.pages.BrowseResult
import com.maloy.innertube.pages.ExplorePage
import com.maloy.innertube.pages.HistoryPage
import com.maloy.innertube.pages.HomePage
import com.maloy.innertube.pages.LibraryContinuationPage
import com.maloy.innertube.pages.LibraryPage
import com.maloy.innertube.pages.MoodAndGenres
import com.maloy.innertube.pages.NewPipeDownloaderImpl
import com.maloy.innertube.pages.NewReleaseAlbumPage
import com.maloy.innertube.pages.NextPage
import com.maloy.innertube.pages.NextResult
import com.maloy.innertube.pages.PlaylistContinuationPage
import com.maloy.innertube.pages.PlaylistPage
import com.maloy.innertube.pages.RelatedPage
import com.maloy.innertube.pages.SearchPage
import com.maloy.innertube.pages.SearchResult
import com.maloy.innertube.pages.SearchSuggestionPage
import com.maloy.innertube.pages.SearchSummary
import com.maloy.innertube.pages.SearchSummaryPage
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.schabi.newpipe.extractor.NewPipe
import java.net.Proxy
import kotlin.random.Random

/**
 * Parse useful data with [InnerTube] sending requests.
 * Modified from [ViMusic](https://github.com/vfsfitvnm/ViMusic)
 */
object YouTube {
    private val innerTube = InnerTube()

    init {
        NewPipe.init(NewPipeDownloaderImpl(proxy))
    }

    var locale: YouTubeLocale
        get() = innerTube.locale
        set(value) {
            innerTube.locale = value
        }
    var visitorData: String?
        get() = innerTube.visitorData
        set(value) {
            innerTube.visitorData = value
        }
    var cookie: String?
        get() = innerTube.cookie
        set(value) {
            innerTube.cookie = value
        }
    var proxy: Proxy?
        get() = innerTube.proxy
        set(value) {
            innerTube.proxy = value
        }
    var useLoginForBrowse: Boolean
        get() = innerTube.useLoginForBrowse
        set(value) {
            innerTube.useLoginForBrowse = value
        }

    suspend fun searchSuggestions(query: String): Result<SearchSuggestions> = runCatching {
        val response = innerTube.getSearchSuggestions(WEB_REMIX, query).body<GetSearchSuggestionsResponse>()
        SearchSuggestions(
            queries = response.contents?.getOrNull(0)?.searchSuggestionsSectionRenderer?.contents?.mapNotNull { content ->
                content.searchSuggestionRenderer?.suggestion?.runs?.joinToString(separator = "") { it.text }
            }.orEmpty(),
            recommendedItems = response.contents?.getOrNull(1)?.searchSuggestionsSectionRenderer?.contents?.mapNotNull {
                it.musicResponsiveListItemRenderer?.let { renderer ->
                    SearchSuggestionPage.fromMusicResponsiveListItemRenderer(renderer)
                }
            }.orEmpty()
        )
    }

    suspend fun searchSummary(query: String): Result<SearchSummaryPage> = runCatching {
        val response = innerTube.search(WEB_REMIX, query).body<SearchResponse>()
        SearchSummaryPage(
            summaries = response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.mapNotNull { it ->
                if (it.musicCardShelfRenderer != null)
                    SearchSummary(
                        title = it.musicCardShelfRenderer.header?.musicCardShelfHeaderBasicRenderer?.title?.runs?.firstOrNull()?.text ?: return@mapNotNull null,
                        items = listOfNotNull(SearchSummaryPage.fromMusicCardShelfRenderer(it.musicCardShelfRenderer))
                            .plus(
                                it.musicCardShelfRenderer.contents
                                    ?.mapNotNull { it.musicResponsiveListItemRenderer }
                                    ?.mapNotNull(SearchSummaryPage.Companion::fromMusicResponsiveListItemRenderer)
                                    .orEmpty()
                            )
                            .distinctBy { it.id }
                            .ifEmpty { null } ?: return@mapNotNull null
                    )
                else
                    SearchSummary(
                        title = it.musicShelfRenderer?.title?.runs?.firstOrNull()?.text ?: return@mapNotNull null,
                        items = it.musicShelfRenderer.contents?.getItems()
                            ?.mapNotNull {
                                SearchSummaryPage.fromMusicResponsiveListItemRenderer(it)
                            }
                            ?.distinctBy { it.id }
                            ?.ifEmpty { null } ?: return@mapNotNull null
                    )
            }!!
        )
    }

    suspend fun search(query: String, filter: SearchFilter): Result<SearchResult> = runCatching {
        val response = innerTube.search(WEB_REMIX, query, filter.value).body<SearchResponse>()
        SearchResult(
            items = response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents?.lastOrNull()
                ?.musicShelfRenderer?.contents?.getItems()?.mapNotNull {
                    SearchPage.toYTItem(it)
                }.orEmpty(),
            continuation = response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents?.lastOrNull()
                ?.musicShelfRenderer?.continuations?.getContinuation()
        )
    }

    suspend fun searchContinuation(continuation: String): Result<SearchResult> = runCatching {
        val response = innerTube.search(WEB_REMIX, continuation = continuation).body<SearchResponse>()
        SearchResult(
            items = response.continuationContents?.musicShelfContinuation?.contents
                ?.mapNotNull {
                    SearchPage.toYTItem(it.musicResponsiveListItemRenderer)
                }!!,
            continuation = response.continuationContents.musicShelfContinuation.continuations?.getContinuation()
        )
    }

    suspend fun album(browseId: String, withSongs: Boolean = true): Result<AlbumPage> = runCatching {
        val response = innerTube.browse(WEB_REMIX, browseId).body<BrowseResponse>()
        if (browseId.contains("FEmusic_library_privately_owned_release_detail")) {
            val playlistId =
                response.header?.musicDetailHeaderRenderer?.menu?.menuRenderer?.topLevelButtons?.firstOrNull()?.buttonRenderer?.navigationEndpoint?.watchPlaylistEndpoint?.playlistId!!
            val albumItem = AlbumItem(
                browseId = browseId,
                playlistId = playlistId,
                title = response.header.musicDetailHeaderRenderer.title.runs?.firstOrNull()?.text!!,
                artists = response.header.musicDetailHeaderRenderer.subtitle.runs?.filter { it.navigationEndpoint != null }?.map {
                    Artist(
                        name = it.text,
                        id = it.navigationEndpoint?.browseEndpoint?.browseId
                    )
                },
                year = response.header.musicDetailHeaderRenderer.subtitle.runs?.lastOrNull()?.text?.toIntOrNull(),
                thumbnail = response.header.musicDetailHeaderRenderer.thumbnail.croppedSquareThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()!!.url,
                explicit = false,
            )
            return@runCatching AlbumPage(
                album = albumItem,
                songs = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.musicShelfRenderer?.contents?.getItems()?.mapNotNull {
                    AlbumPage.getSong(it, albumItem)
                }!!.toMutableList(),
                otherVersions = emptyList()
            )
        } else {
            val playlistId =
                response.microformat?.microformatDataRenderer?.urlCanonical?.substringAfterLast('=')!!
            val albumItem = AlbumItem(
                browseId = browseId,
                playlistId = playlistId,
                title = response.contents?.twoColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.musicResponsiveHeaderRenderer?.title?.runs?.firstOrNull()?.text!!,
                artists = response.contents.twoColumnBrowseResultsRenderer.tabs.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.musicResponsiveHeaderRenderer?.straplineTextOne?.runs?.oddElements()
                    ?.map {
                        Artist(
                            name = it.text,
                            id = it.navigationEndpoint?.browseEndpoint?.browseId
                        )
                    }!!,
                year = response.contents.twoColumnBrowseResultsRenderer.tabs.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.musicResponsiveHeaderRenderer?.subtitle?.runs?.lastOrNull()?.text?.toIntOrNull(),
                thumbnail = response.contents.twoColumnBrowseResultsRenderer.tabs.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.musicResponsiveHeaderRenderer?.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()?.url!!,
                explicit = false,
            )
            return@runCatching AlbumPage(
                album = albumItem,
                songs = if (withSongs) albumSongs(
                    playlistId
                ).getOrThrow() else emptyList(),
                otherVersions = response.contents.twoColumnBrowseResultsRenderer.secondaryContents?.sectionListRenderer?.contents?.getOrNull(
                    1
                )?.musicCarouselShelfRenderer?.contents
                    ?.mapNotNull { it.musicTwoRowItemRenderer }
                    ?.mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer)
                    .orEmpty()
            )
        }
    }

    suspend fun albumSongs(playlistId: String): Result<List<SongItem>> = runCatching {
        var response = innerTube.browse(WEB_REMIX, "VL$playlistId").body<BrowseResponse>()
        val songs = response.contents?.twoColumnBrowseResultsRenderer
            ?.secondaryContents?.sectionListRenderer
            ?.contents?.firstOrNull()
            ?.musicPlaylistShelfRenderer?.contents?.getItems()
            ?.mapNotNull {
                AlbumPage.getSong(it)
            }!!
            .toMutableList()
        var continuation = response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer
            ?.contents?.firstOrNull()?.musicPlaylistShelfRenderer?.contents?.getContinuation()
        while (continuation != null) {
            response = innerTube.browse(
                client = WEB_REMIX,
                continuation = continuation,
            ).body<BrowseResponse>()
            val continuationItems = response.onResponseReceivedActions?.firstOrNull()
                ?.appendContinuationItemsAction?.continuationItems
            if (continuationItems != null) {
                songs += continuationItems.getItems().mapNotNull { AlbumPage.getSong(it) }
            }
            continuation = response.continuationContents?.musicPlaylistShelfContinuation?.continuations?.getContinuation()
        }
        songs
    }

    suspend fun artist(browseId: String): Result<ArtistPage> = runCatching {
val response = innerTube.browse(WEB_REMIX, browseId).body<BrowseResponse>()
        ArtistPage(
            artist = ArtistItem(
                id = browseId,
                channelId = response.header?.musicImmersiveHeaderRenderer?.subscriptionButton?.subscribeButtonRenderer?.channelId,
                title = response.header?.musicImmersiveHeaderRenderer?.title?.runs?.firstOrNull()?.text
                    ?: response.header?.musicVisualHeaderRenderer?.title?.runs?.firstOrNull()?.text!!,
                thumbnail = response.header?.musicImmersiveHeaderRenderer?.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl()
                    ?: response.header?.musicVisualHeaderRenderer?.foregroundThumbnail?.musicThumbnailRenderer?.getThumbnailUrl()!!,
                shuffleEndpoint = response.header?.musicImmersiveHeaderRenderer?.playButton?.buttonRenderer?.navigationEndpoint?.watchEndpoint,
                radioEndpoint = response.header?.musicImmersiveHeaderRenderer?.startRadioButton?.buttonRenderer?.navigationEndpoint?.watchEndpoint
            ),
            sections = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents
                ?.mapNotNull(ArtistPage::fromSectionListRendererContent)!!,
            description = response.header?.musicImmersiveHeaderRenderer?.description?.runs?.firstOrNull()?.text
        )
    }

    suspend fun artistItems(endpoint: BrowseEndpoint): Result<ArtistItemsPage> = runCatching {
val response = innerTube.browse(WEB_REMIX, endpoint.browseId, endpoint.params).body<BrowseResponse>()
        val gridRenderer = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
            ?.gridRenderer
        if (gridRenderer != null) {
            ArtistItemsPage(
                title = gridRenderer.header?.gridHeaderRenderer?.title?.runs?.firstOrNull()?.text.orEmpty(),
                items = gridRenderer.items.mapNotNull {
                    it.musicTwoRowItemRenderer?.let { renderer ->
                        ArtistItemsPage.fromMusicTwoRowItemRenderer(renderer)
                    }
                },
                continuation = gridRenderer.continuations?.getContinuation()
            )
        } else {
            val musicPlaylistShelfRenderer = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
                ?.musicPlaylistShelfRenderer
            ArtistItemsPage(
                title = response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text!!,
                items = musicPlaylistShelfRenderer?.contents?.getItems()?.mapNotNull {
                    ArtistItemsPage.fromMusicResponsiveListItemRenderer(it)
                    }!!,
                continuation = musicPlaylistShelfRenderer.contents.getContinuation()
            )
        }
    }

    suspend fun artistItemsContinuation(continuation: String): Result<ArtistItemsContinuationPage> = runCatching {
val response = innerTube.browse(WEB_REMIX, continuation = continuation).body<BrowseResponse>()
        when {
            response.continuationContents?.gridContinuation != null -> {
                val gridContinuation = response.continuationContents.gridContinuation
                ArtistItemsContinuationPage(
                    items = gridContinuation.items.mapNotNull {
                        it.musicTwoRowItemRenderer?.let { renderer ->
                            ArtistItemsPage.fromMusicTwoRowItemRenderer(renderer)
                        }
                    },
                    continuation = gridContinuation.continuations?.getContinuation()
                )
            }

            response.continuationContents?.musicPlaylistShelfContinuation != null -> {
                val musicPlaylistShelfContinuation = response.continuationContents.musicPlaylistShelfContinuation
                ArtistItemsContinuationPage(
                    items = musicPlaylistShelfContinuation.contents.getItems().mapNotNull {
                        ArtistItemsPage.fromMusicResponsiveListItemRenderer(it)
                    },
                    continuation = musicPlaylistShelfContinuation.continuations?.getContinuation()
                )
            }

            else -> {
                val continuationItems = response.onResponseReceivedActions?.firstOrNull()
                    ?.appendContinuationItemsAction?.continuationItems
                ArtistItemsContinuationPage(
                    items = continuationItems?.getItems()?.mapNotNull {
                        ArtistItemsPage.fromMusicResponsiveListItemRenderer(it)
                    }!!,
                    continuation = continuationItems.getContinuation()
                )
            }
        }
    }

    suspend fun playlist(playlistId: String): Result<PlaylistPage> = runCatching {
        val response = innerTube.browse(
            client = WEB_REMIX,
            browseId = "VL$playlistId",
            setLogin = true
        ).body<BrowseResponse>()
        val base = response.contents?.twoColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
        val header = base?.musicResponsiveHeaderRenderer ?: base?.musicEditablePlaylistDetailHeaderRenderer?.header?.musicResponsiveHeaderRenderer

        val editable = base?.musicEditablePlaylistDetailHeaderRenderer != null

        PlaylistPage(
            playlist = PlaylistItem(
                id = playlistId,
                title = header?.title?.runs?.firstOrNull()?.text!!,
                author = header.straplineTextOne?.runs?.firstOrNull()?.let {
                    Artist(
                        name = it.text,
                        id = it.navigationEndpoint?.browseEndpoint?.browseId
                    )
                },
                songCountText = header.secondSubtitle?.runs?.firstOrNull()?.text,
                thumbnail = header.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()?.url!!,
                playEndpoint = null,
                shuffleEndpoint = header.buttons?.lastOrNull()?.menuRenderer?.items?.firstOrNull()?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint!!,
                radioEndpoint = header.buttons.getOrNull(2)?.menuRenderer?.items?.find {
                    it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
                }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint,
                isEditable = editable
            ),
            songs = response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer
                ?.contents?.firstOrNull()?.musicPlaylistShelfRenderer?.contents?.getItems()?.mapNotNull {
                    PlaylistPage.fromMusicResponsiveListItemRenderer(it)
                }!!,
            songsContinuation = response.contents.twoColumnBrowseResultsRenderer.secondaryContents.sectionListRenderer
                .contents.firstOrNull()?.musicPlaylistShelfRenderer?.contents?.getContinuation(),
            continuation = response.contents.twoColumnBrowseResultsRenderer.secondaryContents.sectionListRenderer
                .continuations?.getContinuation()
        )
    }

    suspend fun playlistContinuation(continuation: String) = runCatching {
        val response = innerTube.browse(
            client = WEB_REMIX,
            continuation = continuation,
            setLogin = true
        ).body<BrowseResponse>()

        val musicPlaylistShelfContinuation = response.continuationContents?.musicPlaylistShelfContinuation
        if (musicPlaylistShelfContinuation != null) {
            PlaylistContinuationPage(
                songs = musicPlaylistShelfContinuation.contents.getItems().mapNotNull {
                    PlaylistPage.fromMusicResponsiveListItemRenderer(it)
                },
                continuation = musicPlaylistShelfContinuation.continuations?.getContinuation()
            )
        } else {
            val continuationItems = response.onResponseReceivedActions?.firstOrNull()
                ?.appendContinuationItemsAction?.continuationItems
            PlaylistContinuationPage(
                songs = continuationItems?.getItems()?.mapNotNull {
                    PlaylistPage.fromMusicResponsiveListItemRenderer(it)
                }!!,
                continuation = continuationItems.getContinuation()
            )
        }
    }

    suspend fun addPlaylistToPlaylist(playlistId: String, addPlaylistId: String) = runCatching {
        innerTube.addPlaylistToPlaylist(WEB_REMIX, playlistId, addPlaylistId)
    }

    suspend fun home(params: String? = null): Result<HomePage> = runCatching {
        var response = innerTube.browse(WEB_REMIX, browseId = "FEmusic_home", params = params).body<BrowseResponse>()
        var continuation = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.continuations?.getContinuation()
        val sectionListRender = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer
        val sections = sectionListRender?.contents!!
            .mapNotNull { it.musicCarouselShelfRenderer }
            .mapNotNull {
                HomePage.Section.fromMusicCarouselShelfRenderer(it)
            }.toMutableList()
        while (continuation != null) {
            response = innerTube.browse(WEB_REMIX, continuation = continuation).body<BrowseResponse>()
            continuation = response.continuationContents?.sectionListContinuation?.continuations?.getContinuation()
            sections += response.continuationContents?.sectionListContinuation?.contents
                ?.mapNotNull { it.musicCarouselShelfRenderer }
                ?.mapNotNull {
                    HomePage.Section.fromMusicCarouselShelfRenderer(it)
                }.orEmpty()
        }
        val chips = sectionListRender.header?.chipCloudRenderer?.chips?.map { HomePage.Chip.fromChipCloudChipRenderer(it) }
        HomePage(chips, sections)
    }

    suspend fun explore(): Result<ExplorePage> = runCatching {
        val response = innerTube.browse(WEB_REMIX, browseId = "FEmusic_explore").body<BrowseResponse>()
        ExplorePage(
            newReleaseAlbums = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.find {
                it.musicCarouselShelfRenderer?.header?.musicCarouselShelfBasicHeaderRenderer?.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint?.browseId == "FEmusic_new_releases_albums"
            }?.musicCarouselShelfRenderer?.contents
                ?.mapNotNull { it.musicTwoRowItemRenderer }
                ?.mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer).orEmpty(),
            moodAndGenres = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.find {
                it.musicCarouselShelfRenderer?.header?.musicCarouselShelfBasicHeaderRenderer?.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint?.browseId == "FEmusic_moods_and_genres"
            }?.musicCarouselShelfRenderer?.contents
                ?.mapNotNull { it.musicNavigationButtonRenderer }
                ?.mapNotNull(MoodAndGenres.Companion::fromMusicNavigationButtonRenderer)
                .orEmpty()
        )
    }

    suspend fun newReleaseAlbums(): Result<List<AlbumItem>> = runCatching {
        val response = innerTube.browse(WEB_REMIX, browseId = "FEmusic_new_releases_albums").body<BrowseResponse>()
        response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.gridRenderer?.items
            ?.mapNotNull { it.musicTwoRowItemRenderer }
            ?.mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer)
            .orEmpty()
    }

    suspend fun moodAndGenres(): Result<List<MoodAndGenres>> = runCatching {
        val response = innerTube.browse(WEB_REMIX, browseId = "FEmusic_moods_and_genres").body<BrowseResponse>()
        response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents!!
            .mapNotNull(MoodAndGenres.Companion::fromSectionListRendererContent)
    }

    suspend fun browse(
        browseId: String,
        params: String?,
    ): Result<BrowseResult> =
        runCatching {
            val response = innerTube.browse(WEB_REMIX, browseId = browseId, params = params)
                .body<BrowseResponse>()
            BrowseResult(
                title =
                response.header
                    ?.musicHeaderRenderer
                    ?.title
                    ?.runs
                    ?.firstOrNull()
                    ?.text,
                items =
                response.contents
                    ?.singleColumnBrowseResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
                    ?.contents
                    ?.mapNotNull { content ->
                        when {
                            content.gridRenderer != null -> {
                                BrowseResult.Item(
                                    title =
                                    content.gridRenderer.header
                                        ?.gridHeaderRenderer
                                        ?.title
                                        ?.runs
                                        ?.firstOrNull()
                                        ?.text,
                                    items =
                                    content.gridRenderer.items
                                        .mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
                                        .mapNotNull(RelatedPage.Companion::fromMusicTwoRowItemRenderer),
                                )
                            }

                            content.musicCarouselShelfRenderer != null -> {
                                BrowseResult.Item(
                                    title =
                                    content.musicCarouselShelfRenderer.header
                                        ?.musicCarouselShelfBasicHeaderRenderer
                                        ?.title
                                        ?.runs
                                        ?.firstOrNull()
                                        ?.text,
                                    items =
                                    content.musicCarouselShelfRenderer.contents
                                        .mapNotNull { content2 ->
                                            val renderer =
                                                content2.musicTwoRowItemRenderer
                                                    ?: content2.musicResponsiveListItemRenderer
                                            renderer?.let {
                                                when (renderer) {
                                                    is MusicTwoRowItemRenderer -> RelatedPage.fromMusicTwoRowItemRenderer(
                                                        renderer
                                                    )

                                                    is MusicResponsiveListItemRenderer ->
                                                        SearchSummaryPage.fromMusicResponsiveListItemRenderer(
                                                            renderer,
                                                        )

                                                    else -> null
                                                }
                                            }
                                        }
                                )
                            }

                            else -> null
                        }
                    }.orEmpty()
            )
        }
    suspend fun library(browseId: String) = runCatching {
        val response = innerTube.browse(
            client = WEB_REMIX,
            browseId = browseId,
            setLogin = true
        ).body<BrowseResponse>()

        val contents = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.
        tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
        when {
            contents?.gridRenderer != null -> {
                LibraryPage(
                    items = contents.gridRenderer.items
                        .mapNotNull (GridRenderer.Item::musicTwoRowItemRenderer)
                        .mapNotNull { LibraryPage.fromMusicTwoRowItemRenderer(it) },
                    continuation = contents.gridRenderer.continuations?.getContinuation()
                )
            }
            else -> {
                LibraryPage(
                    items = contents?.musicShelfRenderer?.contents!!
                        .mapNotNull (MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                        .mapNotNull { LibraryPage.fromMusicResponsiveListItemRenderer(it) },
                    continuation = contents.musicShelfRenderer.continuations?.getContinuation()
                )
            }
        }
    }
    suspend fun libraryContinuation(continuation: String) = runCatching {
        val response = innerTube.browse(
            client = WEB_REMIX,
            continuation = continuation,
            setLogin = true
        ).body<BrowseResponse>()

        val contents = response.continuationContents

        when {
            contents?.gridContinuation != null -> {
                LibraryContinuationPage(
                    items = contents.gridContinuation.items
                        .mapNotNull (GridRenderer.Item::musicTwoRowItemRenderer)
                        .mapNotNull { LibraryPage.fromMusicTwoRowItemRenderer(it) },
                    continuation = contents.gridContinuation.continuations?.getContinuation()
                )
            }

            else -> {
                LibraryContinuationPage(
                    items = contents?.musicShelfContinuation?.contents!!
                        .mapNotNull (MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                        .mapNotNull { LibraryPage.fromMusicResponsiveListItemRenderer(it) },
                    continuation = contents.musicShelfContinuation.continuations?.getContinuation()
                )
            }
        }
    }
    suspend fun likedPlaylists(): Result<List<PlaylistItem>> = runCatching {
        var response = innerTube.browse(
            client = WEB_REMIX,
            browseId = "FEmusic_liked_playlists",
            setLogin = true
        ).body<BrowseResponse>()
        val gridRenderer = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.gridRenderer
        val playlists = gridRenderer?.items!!
            .drop(1)
            .mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
            .mapNotNull {
                ArtistItemsPage.fromMusicTwoRowItemRenderer(it) as? PlaylistItem
            }.toMutableList()
        var continuation = gridRenderer.continuations?.getContinuation()
        while (continuation != null) {
            response = innerTube.browse(
                client = WEB_REMIX,
                continuation = continuation,
                setLogin = true
            ).body<BrowseResponse>()
            val gridContinuation = response.continuationContents?.gridContinuation
            playlists += gridContinuation?.items!!
                .drop(1)
                .mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
                .mapNotNull {
                    ArtistItemsPage.fromMusicTwoRowItemRenderer(it) as? PlaylistItem
                }
            continuation = gridContinuation?.continuations?.getContinuation()
        }
        playlists
    }

    suspend fun subscribeChannel(channelId: String, subscribe: Boolean) = runCatching {
        if (subscribe)
            innerTube.subscribeChannel(WEB_REMIX, channelId)
        else
            innerTube.unsubscribeChannel(WEB_REMIX, channelId)
    }
    suspend fun getChannelId(browseId: String): String {
        artist(browseId).onSuccess {
            return it.artist.channelId!!
        }
        return ""
    }

   fun createPlaylist(title: String) = runBlocking {
        innerTube.createPlaylist(WEB_REMIX, title).body<CreatePlaylistResponse>().playlistId
    }

    suspend fun likeVideo(videoId: String, like: Boolean) = runCatching {
        if (like)
            innerTube.likeVideo(WEB_REMIX, videoId)
        else
            innerTube.unlikeVideo(WEB_REMIX, videoId)
    }

    suspend fun likePlaylist(playlistId: String, like: Boolean) = runCatching {
        if (like)
            innerTube.likePlaylist(WEB_REMIX, playlistId)
        else
            innerTube.unlikePlaylist(WEB_REMIX, playlistId)
    }

    suspend fun musicHistory() = runCatching {
        val response = innerTube.browse(
            client = WEB_REMIX,
            browseId = "FEmusic_history",
            setLogin = true
        ).body<BrowseResponse>()

        HistoryPage(
            sections = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents
                ?.mapNotNull {
                    it.musicShelfRenderer?.let { musicShelfRenderer ->
                        HistoryPage.fromMusicShelfRenderer(musicShelfRenderer)
                    }
                }
        )
    }

    suspend fun player(videoId: String, playlistId: String? = null, client: YouTubeClient, signatureTimestamp: Int? = null): Result<PlayerResponse> = runCatching {
        innerTube.player(client, videoId, playlistId, signatureTimestamp).body<PlayerResponse>()
    }

    suspend fun registerPlayback(playlistId: String? = null, playbackTracking: String) = runCatching {
        val cpn = (1..16).map {
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"[Random.Default.nextInt(
                0,
                64
            )]
        }.joinToString("")

        val playbackUrl = playbackTracking.replace(
            "https://s.youtube.com",
            "https://music.youtube.com",
        )

        innerTube.registerPlayback(
            url = playbackUrl,
            playlistId = playlistId,
            cpn = cpn
        )
    }

    suspend fun libraryRecentActivity(): Result<LibraryPage> = runCatching {
        val continuation =
            "4qmFsgIrEhdGRW11c2ljX2xpYnJhcnlfbGFuZGluZxoQZ2dNR0tnUUlCaEFCb0FZQg%3D%3D"
        val response = innerTube.browse(
            client = WEB_REMIX,
            continuation = continuation,
            setLogin = true
        ).body<BrowseResponse>()

        val items = response.continuationContents?.sectionListContinuation?.contents?.firstOrNull()
            ?.gridRenderer?.items!!.mapNotNull {
                it.musicTwoRowItemRenderer?.let { renderer ->
                    LibraryPage.fromMusicTwoRowItemRenderer(renderer)
                }
            }.toMutableList()
        items.forEachIndexed { index, item ->
            if (item is ArtistItem)
                items[index] =
                    artist(item.id).getOrNull()?.artist!!.copy(thumbnail = item.thumbnail)
        }

        LibraryPage(
            items = items,
            continuation = null
        )
    }

    suspend fun next(endpoint: WatchEndpoint, continuation: String? = null): Result<NextResult> = runCatching {
        val response = innerTube.next(WEB_REMIX, endpoint.videoId, endpoint.playlistId, endpoint.playlistSetVideoId, endpoint.index, endpoint.params, continuation).body<NextResponse>()
        val title = response.contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs[0].tabRenderer.content?.musicQueueRenderer?.header?.musicQueueHeaderRenderer?.subtitle?.runs?.firstOrNull()?.text
        val playlistPanelRenderer = response.continuationContents?.playlistPanelContinuation
            ?: response.contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs[0].tabRenderer.content?.musicQueueRenderer?.content?.playlistPanelRenderer!!
        val items = playlistPanelRenderer.contents.mapNotNull { content ->
            content.playlistPanelVideoRenderer
                ?.let(NextPage::fromPlaylistPanelVideoRenderer)
                ?.let { it to content.playlistPanelVideoRenderer.selected }
        }
        val songs = items.map { it.first }
        val currentIndex = items.indexOfFirst { it.second }.takeIf { it != -1 }
        playlistPanelRenderer.contents.lastOrNull()?.automixPreviewVideoRenderer?.content?.automixPlaylistVideoRenderer?.navigationEndpoint?.watchPlaylistEndpoint?.let { watchPlaylistEndpoint ->
            return@runCatching next(watchPlaylistEndpoint).getOrThrow().let { result ->
                result.copy(
                    title = title,
                    items = songs + result.items,
                    lyricsEndpoint = response.contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs.getOrNull(1)?.tabRenderer?.endpoint?.browseEndpoint,
                    relatedEndpoint = response.contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs.getOrNull(2)?.tabRenderer?.endpoint?.browseEndpoint,
                    currentIndex = currentIndex,
                    endpoint = watchPlaylistEndpoint
                )
            }
        }
        NextResult(
            title = title,
            items = songs,
            currentIndex = currentIndex,
            lyricsEndpoint = response.contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs.getOrNull(1)?.tabRenderer?.endpoint?.browseEndpoint,
            relatedEndpoint = response.contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs.getOrNull(2)?.tabRenderer?.endpoint?.browseEndpoint,
            continuation = playlistPanelRenderer.continuations?.getContinuation(),
            endpoint = endpoint
        )
    }

    suspend fun lyrics(endpoint: BrowseEndpoint): Result<String?> = runCatching {
        val response = innerTube.browse(WEB_REMIX, endpoint.browseId, endpoint.params).body<BrowseResponse>()
        response.contents?.sectionListRenderer?.contents?.firstOrNull()?.musicDescriptionShelfRenderer?.description?.runs?.firstOrNull()?.text
    }

    suspend fun related(endpoint: BrowseEndpoint): Result<RelatedPage> = runCatching {
        val response = innerTube.browse(WEB_REMIX, endpoint.browseId).body<BrowseResponse>()
        val songs = mutableListOf<SongItem>()
        val albums = mutableListOf<AlbumItem>()
        val artists = mutableListOf<ArtistItem>()
        val playlists = mutableListOf<PlaylistItem>()
        response.contents?.sectionListRenderer?.contents?.forEach { sectionContent ->
            sectionContent.musicCarouselShelfRenderer?.contents?.forEach { content ->
                when (val item = content.musicResponsiveListItemRenderer?.let(RelatedPage.Companion::fromMusicResponsiveListItemRenderer)
                    ?: content.musicTwoRowItemRenderer?.let(RelatedPage.Companion::fromMusicTwoRowItemRenderer)) {
                    is SongItem -> if (content.musicResponsiveListItemRenderer?.overlay
                            ?.musicItemThumbnailOverlayRenderer?.content
                            ?.musicPlayButtonRenderer?.playNavigationEndpoint
                            ?.watchEndpoint?.watchEndpointMusicSupportedConfigs
                            ?.watchEndpointMusicConfig?.musicVideoType == MUSIC_VIDEO_TYPE_ATV
                    ) songs.add(item)

                    is AlbumItem -> albums.add(item)
                    is ArtistItem -> artists.add(item)
                    is PlaylistItem -> playlists.add(item)
                    null -> {}
                }
            }
        }
        RelatedPage(songs, albums, artists, playlists)
    }

    suspend fun queue(videoIds: List<String>? = null, playlistId: String? = null): Result<List<SongItem>> = runCatching {
        if (videoIds != null) {
            assert(videoIds.size <= MAX_GET_QUEUE_SIZE)
        }
        innerTube.getQueue(WEB_REMIX, videoIds, playlistId).body<GetQueueResponse>().queueDatas
            .mapNotNull {
                it.content.playlistPanelVideoRenderer?.let { renderer ->
                    NextPage.fromPlaylistPanelVideoRenderer(renderer)
                }
            }
    }

    suspend fun moveSongPlaylist(playlistId: String, setVideoId: String, successorSetVideoId: String) = runCatching {
        innerTube.moveSongPlaylist(WEB_REMIX, playlistId, setVideoId, successorSetVideoId)
    }

    suspend fun renamePlaylist(playlistId: String, name: String) = runCatching {
        innerTube.renamePlaylist(WEB_REMIX, playlistId, name)
    }

    suspend fun deletePlaylist(playlistId: String) = runCatching {
        innerTube.deletePlaylist(WEB_REMIX, playlistId)
    }

    suspend fun transcript(videoId: String): Result<String> = runCatching {
        val response = innerTube.getTranscript(WEB, videoId).body<GetTranscriptResponse>()
        response.actions?.firstOrNull()?.updateEngagementPanelAction?.content?.transcriptRenderer?.body?.transcriptBodyRenderer?.cueGroups?.joinToString(separator = "\n") { group ->
            val time = group.transcriptCueGroupRenderer.cues[0].transcriptCueRenderer.startOffsetMs
            val text = group.transcriptCueGroupRenderer.cues[0].transcriptCueRenderer.cue.simpleText
                .trim('♪')
                .trim(' ')
            "[%02d:%02d.%03d]$text".format(time / 60000, (time / 1000) % 60, time % 1000)
        }!!
    }

    suspend fun visitorData(): Result<String> = runCatching {
        Json.parseToJsonElement(innerTube.getSwJsData().bodyAsText().substring(5))
            .jsonArray[0]
            .jsonArray[2]
            .jsonArray.first {
                (it as? JsonPrimitive)?.contentOrNull?.let { candidate ->
                    VISITOR_DATA_REGEX.containsMatchIn(candidate)
                } ?: false
            }
            .jsonPrimitive.content
    }

    suspend fun accountInfo(): Result<AccountInfo> = runCatching {
        innerTube.accountMenu(WEB_REMIX).body<AccountMenuResponse>()
            .actions[0].openPopupAction.popup.multiPageMenuRenderer
            .header?.activeAccountHeaderRenderer
            ?.toAccountInfo()!!
    }


    suspend fun addToPlaylist(playlistId: String, videoId: String) = runCatching {
        innerTube.addToPlaylist(WEB_REMIX, playlistId, videoId).body<AddItemYouTubePlaylistResponse>()
    }
    suspend fun removeFromPlaylist(playlistId: String, videoId: String, setVideoId: String) = runCatching {
        innerTube.removeFromPlaylist(WEB_REMIX, playlistId, videoId, setVideoId)
    }
    suspend fun feedback(tokens: List<String>): Result<Boolean> = runCatching {
        innerTube.feedback(WEB_REMIX, tokens).body<FeedbackResponse>().feedbackResponses.all { it.isProcessed }
    }
    @JvmInline
    value class SearchFilter(val value: String) {
        companion object {
            val FILTER_SONG = SearchFilter("EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D")
            val FILTER_VIDEO = SearchFilter("EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D")
            val FILTER_ALBUM = SearchFilter("EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D")
            val FILTER_ARTIST = SearchFilter("EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D")
            val FILTER_FEATURED_PLAYLIST = SearchFilter("EgeKAQQoADgBagwQDhAKEAMQBRAJEAQ%3D")
            val FILTER_COMMUNITY_PLAYLIST = SearchFilter("EgeKAQQoAEABagoQAxAEEAoQCRAF")
        }
    }

    private const val MAX_GET_QUEUE_SIZE = 1000

    private val VISITOR_DATA_REGEX = Regex("^Cg[t|s]")

    const val DEFAULT_VISITOR_DATA = "CgtsZG1ySnZiQWtSbyiMjuGSBg%3D%3D"
}
