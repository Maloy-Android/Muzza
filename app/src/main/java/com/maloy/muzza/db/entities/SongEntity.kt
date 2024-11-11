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
import kotlinx.coroutines.GlobalScope
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
    val duration: Int = -1, // in seconds
    val thumbnailUrl: String? = null,
    val albumId: String? = null,
    val albumName: String? = null,
    val liked: Boolean = false,
    val totalPlayTime: Long = 0, // in milliseconds
    val inLibrary: LocalDateTime? = null,
    @ColumnInfo(name = "isLocal", defaultValue = false.toString())
    val isLocal: Boolean,
    val localPath: String?,
) {
    val isLocalSong: Boolean
        get() = id.startsWith("LA")
        fun localToggleLike() = copy(
            liked = !liked,
            inLibrary = if (!liked) inLibrary ?: LocalDateTime.now() else inLibrary
        ).also {
            CoroutineScope(Dispatchers.IO).launch() {
                YouTube.likeVideo(id, !liked)
                this.cancel()
            }
        }

    fun setLiked() = copy(
        liked = true,
    )

        fun toggleLike() = localToggleLike().also {
            CoroutineScope(Dispatchers.IO).launch() {
                YouTube.likeVideo(id, !liked)
                this.cancel()
            }
        }

    fun toggleLibrary() = copy(
        inLibrary = if (inLibrary == null) LocalDateTime.now() else null,
        liked = if (inLibrary == null) liked else false
    )
}
