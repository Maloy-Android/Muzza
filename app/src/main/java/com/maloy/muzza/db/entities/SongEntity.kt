package com.maloy.muzza.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.maloy.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@Immutable
@Entity(
    tableName = "song",
    indices = [
        Index(
            value = ["albumId"]
        )
    ]
)
data class SongEntity(
    @PrimaryKey val id: String,
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
    @ColumnInfo(name = "isLocal", defaultValue = "false") val isLocal: Boolean = false,
    val localPath: String?,
    @ColumnInfo(name = "contentUri") 
    val contentUri: String? = null,
    @ColumnInfo(name = "isVideoSong", defaultValue = "false")
    val isVideoSong: Boolean = false,
) {
    fun localToggleLike() = copy(
        liked = !liked
    ).also {
        CoroutineScope(Dispatchers.IO).launch() {
            YouTube.likeVideo(id, !liked)
            this.cancel()
        }
    }

    fun toggleLike() = localToggleLike().also {
        CoroutineScope(Dispatchers.IO).launch() {
            YouTube.likeVideo(id, !liked)
        }
    }

    fun toggleLibrary() = copy(
        inLibrary = if (inLibrary == null) LocalDateTime.now() else null,
    ).also {
        CoroutineScope(Dispatchers.IO).launch {
            YouTube.addVideoToLibrary(id,inLibrary == null)
        }
    }
}