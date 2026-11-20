package com.maloy.muzza.domain.model

import kotlinx.datetime.LocalDateTime

data class ArtistEntity(
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val channelId: String? = null,
    val lastUpdateTime: LocalDateTime,
    val bookmarkedAt: LocalDateTime? = null,
    val isProfile: Boolean = false,
) {
    val isYouTubeArtist: Boolean get() = id.startsWith("UC")
}
