package com.maloy.innertube.models

import java.time.LocalDateTime

data class PlaylistsFragments (
    val id: String?,
    val name: String?,
    val playlistAuthorsId: String?,
    val playlistAuthorName: String?,
    val playlistAuthorAvatarUrl: String?,
    val thumbnailUrl: String?,
    val isEditable: Boolean = true,
    val bookmarkedAt: LocalDateTime?,
    val remoteSongCount: Int? = null,
    val playEndpointParams: String?,
    val shuffleEndpointParams: String?,
    val radioEndpointParams: String?,
    val description: String?,
)