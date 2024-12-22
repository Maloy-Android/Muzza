package com.maloy.muzza.db.entities

import javax.annotation.concurrent.Immutable


@Immutable
data class SongWithStats(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val songCountListened: Int,
    val timeListened: Long?,
)
