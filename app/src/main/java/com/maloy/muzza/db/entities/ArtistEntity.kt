package com.maloy.muzza.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.apache.commons.lang3.RandomStringUtils
import java.time.LocalDateTime

@Suppress("DEPRECATION")
@Immutable
@Entity(tableName = "artist")
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val lastUpdateTime: LocalDateTime = LocalDateTime.now(),
    val bookmarkedAt: LocalDateTime? = null,
) {

    val isYouTubeArtist: Boolean
        get() = id.startsWith("UC")

    fun toggleLike() = copy(
        bookmarkedAt = if (bookmarkedAt != null) null else LocalDateTime.now(),
    )

    companion object {
        fun generateArtistId() = "LA" + RandomStringUtils.random(8, true, false)
    }
}
