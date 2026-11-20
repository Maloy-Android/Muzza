package com.maloy.innertube.pages

import com.maloy.innertube.models.Artist
import com.maloy.innertube.models.MusicTwoRowItemRenderer
import com.maloy.innertube.models.SongItem
import com.maloy.innertube.models.oddElements
import kotlin.collections.ifEmpty

object NewClipsPage {
    fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): SongItem? {
        val subtitleRuns = renderer.subtitle?.runs?.oddElements() ?: return null
        return SongItem(
            id = renderer.navigationEndpoint.watchEndpoint?.videoId ?: return null,
            title = renderer.title.runs?.firstOrNull()?.text ?: return null,
            artists = subtitleRuns.filter { run ->
                run.navigationEndpoint?.browseEndpoint?.browseId?.startsWith("UC") == true || (run.navigationEndpoint?.browseEndpoint != null && !run.navigationEndpoint.browseEndpoint.browseId.startsWith(
                    "MPREb_"
                ))
            }.map { run ->
                Artist(
                    name = run.text,
                    id = run.navigationEndpoint?.browseEndpoint?.browseId
                )
            }.ifEmpty {
                subtitleRuns.firstOrNull()?.let { run ->
                    listOf(Artist(name = run.text, id = null))
                } ?: emptyList()
            },
            album = null,
            duration = null,
            thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
            musicVideoType = renderer.musicVideoType,
            explicit = false
        )
    }
}