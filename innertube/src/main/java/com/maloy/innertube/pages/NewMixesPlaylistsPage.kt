package com.maloy.innertube.pages

import com.maloy.innertube.models.Artist
import com.maloy.innertube.models.MusicTwoRowItemRenderer
import com.maloy.innertube.models.PlaylistItem

object NewMixesPlaylistsPage{
    fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): PlaylistItem? {
        return PlaylistItem(
            id = renderer.navigationEndpoint.browseEndpoint?.browseId?.removePrefix(
                "VL"
            ) ?: return null,
            title = renderer.title.runs?.firstOrNull()?.text ?: return null,
            author = renderer.subtitle?.runs?.firstOrNull()?.let { runs ->
                Artist(
                    name = renderer.subtitle.runs.joinToString(separator = "") { it.text },
                    id = runs.navigationEndpoint?.browseEndpoint?.browseId
                )
            },
            songCountText = null,
            thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl()
                ?: return null,
            playEndpoint = renderer.thumbnailOverlay
                ?.musicItemThumbnailOverlayRenderer?.content
                ?.musicPlayButtonRenderer?.playNavigationEndpoint
                ?.watchPlaylistEndpoint ?: return null,
            shuffleEndpoint = renderer.menu?.menuRenderer?.items?.find {
                it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE"
            }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint
                ?: return null,
            radioEndpoint = renderer.menu.menuRenderer.items.find {
                it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
            }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint
        )
    }
}