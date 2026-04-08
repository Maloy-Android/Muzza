package com.maloy.muzza.models.data

import com.maloy.innertube.models.PlaylistItem
import com.maloy.innertube.models.SongItem

data class CommunityPlaylistItem(
    val playlist: PlaylistItem,
    val songs: List<SongItem>
)