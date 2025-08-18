package com.dreamify.innertube.pages

import com.dreamify.innertube.models.SongItem

data class PlaylistContinuationPage(
    val songs: List<SongItem>,
    val continuation: String?,
)
