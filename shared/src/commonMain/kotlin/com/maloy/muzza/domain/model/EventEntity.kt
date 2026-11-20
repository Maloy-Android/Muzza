package com.maloy.muzza.domain.model

import kotlinx.datetime.LocalDateTime

data class EventEntity(
    val id: Long = 0,
    val songId: String,
    val timestamp: LocalDateTime,
    val playTime: Long,
)
