package com.maloy.muzza.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.maloy.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.apache.commons.lang3.RandomStringUtils
import java.time.LocalDateTime

@Suppress("DEPRECATION")
@Immutable
@Entity(tableName = "playlist")
data class PlaylistEntity(
    @PrimaryKey val id: String = generatePlaylistId(),
    val name: String,
    val browseId: String? = null,
    @ColumnInfo(name = "isEditable", defaultValue = true.toString())
    val isEditable: Boolean = true,
    @ColumnInfo(name = "isLocal", defaultValue = false.toString())
    val isLocal: Boolean = false,
    val bookmarkedAt: LocalDateTime? = null,
) {
    companion object {
        const val LIKED_PLAYLIST_ID = "LP_LIKED"
        const val DOWNLOADED_PLAYLIST_ID = "LP_DOWNLOADED"

        fun generatePlaylistId() = "LP" + RandomStringUtils.random(8, true, false)
    }
    fun localToggleLike() = copy(
        bookmarkedAt = if (bookmarkedAt != null) null else LocalDateTime.now()
    )
    fun toggleLike() = localToggleLike().also {
        CoroutineScope(Dispatchers.IO).launch {
            if (browseId != null)
                YouTube.likePlaylist(browseId, bookmarkedAt == null)
            this.cancel()
        }
    }
}
