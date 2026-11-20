package com.maloy.muzza.domain.model

data class LyricsEntity(
    val id: String,
    val lyrics: String? = null,
    val syncedLyrics: String? = null,
)
