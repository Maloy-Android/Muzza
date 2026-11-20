package com.maloy.muzza.domain.model

import kotlinx.datetime.LocalDateTime

data class SongEntity(
    val id: String,
    val title: String,
    val duration: Int = -1,
    val thumbnailUrl: String? = null,
    val albumId: String? = null,
    val albumName: String? = null,
    val liked: Boolean = false,
    val totalPlayTime: Long = 0,
    val inLibrary: LocalDateTime? = null,
    val dateDownload: LocalDateTime? = null,
    val artistName: String? = null,
    val isLocal: Boolean = false,
    val localPath: String? = null,
    val contentUri: String? = null,
    val isVideoSong: Boolean = false,
    val explicit: Boolean = false,
    val isFromCache: Boolean = false,
) {
    val isLocalSong: Boolean get() = id.startsWith("LA")
}
