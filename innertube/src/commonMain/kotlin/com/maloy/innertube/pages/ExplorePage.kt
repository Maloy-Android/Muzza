package com.maloy.innertube.pages

import com.maloy.innertube.models.AlbumItem
import com.maloy.innertube.models.SongItem

data class ExplorePage(
    val newReleaseAlbums: List<AlbumItem>,
    val moodAndGenres: List<MoodAndGenres.Item>,
    val newClips: List<SongItem>
)
