package com.maloy.muzza.domain.model

import kotlinx.datetime.LocalDateTime

data class PlaylistEntity(
    val id: String,
    val name: String,
    val browseId: String? = null,
    val playlistAuthorsId: String? = null,
    val playlistAuthorName: String? = null,
    val playlistAuthorAvatarUrl: String? = null,
    val createdAt: LocalDateTime? = null,
    val lastUpdateTime: LocalDateTime? = null,
    val isEditable: Boolean = true,
    val bookmarkedAt: LocalDateTime? = null,
    val remoteSongCount: Int? = null,
    val playEndpointParams: String? = null,
    val thumbnailUrl: String? = null,
    val shuffleEndpointParams: String? = null,
    val radioEndpointParams: String? = null,
    val description: String? = null,
    val isLocal: Boolean = false,
) {
    companion object {
        const val LIKED_PLAYLIST_ID = "LP_LIKED"
        const val DOWNLOADED_PLAYLIST_ID = "LP_DOWNLOADED"
    }
}
