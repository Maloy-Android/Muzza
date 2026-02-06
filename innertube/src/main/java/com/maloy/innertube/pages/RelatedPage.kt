package com.maloy.innertube.pages

import com.maloy.innertube.models.Album
import com.maloy.innertube.models.AlbumItem
import com.maloy.innertube.models.Artist
import com.maloy.innertube.models.ArtistItem
import com.maloy.innertube.models.MusicResponsiveListItemRenderer
import com.maloy.innertube.models.MusicTwoRowItemRenderer
import com.maloy.innertube.models.PlaylistItem
import com.maloy.innertube.models.SongItem
import com.maloy.innertube.models.YTItem
import com.maloy.innertube.models.oddElements

data class RelatedPage(
    val songs: List<SongItem>,
    val albums: List<AlbumItem>,
    val artists: List<ArtistItem>,
    val playlists: List<PlaylistItem>,
) {
    companion object {
        fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): SongItem? {
            return SongItem(
                id = renderer.playlistItemData?.videoId ?: return null,
                title = renderer.flexColumns.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text
                    ?: return null,
                artists = renderer.flexColumns.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.oddElements()
                    ?.map {
                        Artist(
                            name = it.text,
                            id = it.navigationEndpoint?.browseEndpoint?.browseId,
                        )
                    } ?: return null,
                album = renderer.flexColumns.getOrNull(2)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()
                    ?.let {
                        Album(
                            name = it.text,
                            id = it.navigationEndpoint?.browseEndpoint?.browseId ?: return null
                        )
                    },
                duration = null,
                thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl()
                    ?: return null,
                musicVideoType = renderer.musicVideoType,
                explicit = renderer.badges?.find {
                    it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                } != null)
        }

        fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): YTItem? {
            return when {
                renderer.isAlbum -> AlbumItem(
                    browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                    playlistId = renderer.thumbnailOverlay?.musicItemThumbnailOverlayRenderer?.content
                        ?.musicPlayButtonRenderer?.playNavigationEndpoint
                        ?.watchPlaylistEndpoint?.playlistId ?: return null,
                    title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                    artists = renderer.subtitle?.runs?.oddElements()?.drop(0)?.map {
                        Artist(
                            name = it.text,
                            id = it.navigationEndpoint?.browseEndpoint?.browseId
                        )
                    }?:return null,
                    year = null,
                    thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl()
                        ?: return null,
                    explicit = renderer.subtitleBadges?.find {
                        it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                    } != null,
                )

                renderer.isPlaylist -> PlaylistItem(
                    id = renderer.navigationEndpoint.browseEndpoint?.browseId?.removePrefix("VL")
                        ?: return null,
                    title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                    author = Artist(
                        name = renderer.subtitle?.runs?.lastOrNull()?.text ?: return null, id = null
                    ),
                    songCountText = renderer.subtitle.runs.getOrNull(4)?.text,
                    thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl()
                        ?: return null,
                    playEndpoint = renderer.thumbnailOverlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchPlaylistEndpoint
                        ?: return null,
                    shuffleEndpoint = renderer.menu?.menuRenderer?.items?.find {
                            it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE"
                        }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint
                        ?: return null,
                    radioEndpoint = renderer.menu.menuRenderer.items.find {
                            it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
                        }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint
                )

                renderer.isArtist -> {
                    ArtistItem(
                        id = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                        title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                        subscriptions = renderer.subtitle?.runs?.firstOrNull()?.text,
                        thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl()
                            ?: return null,
                        shuffleEndpoint = renderer.menu?.menuRenderer?.items?.find {
                                it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE"
                            }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint
                            ?: return null,
                        radioEndpoint = renderer.menu.menuRenderer.items.find {
                                it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
                            }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint
                            ?: return null,
                    )
                }

                else -> null
            }
        }
    }
}
