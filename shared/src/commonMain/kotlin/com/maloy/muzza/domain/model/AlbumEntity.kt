package com.maloy.muzza.domain.model

import kotlinx.datetime.LocalDateTime

data class AlbumEntity(
    val id: String,
    val playlistId: String? = null,
    val title: String,
    val year: Int? = null,
    val thumbnailUrl: String? = null,
    val themeColor: Int? = null,
    val songCount: Int,
    val duration: Int,
    val lastUpdateTime: LocalDateTime,
    val bookmarkedAt: LocalDateTime? = null,
)
