package com.maloy.muzza.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.maloy.innertube.models.Album
import com.maloy.innertube.models.AlbumItem
import com.maloy.innertube.models.Artist
import com.maloy.innertube.models.ArtistItem
import com.maloy.innertube.models.SongItem
import com.maloy.innertube.models.YTItem

@Entity(tableName = "speed_dial_item")
data class SpeedDialItem(
    @PrimaryKey
    val id: String,
    val secondaryId: String? = null,
    val title: String,
    val subtitle: String? = null,
    val channelId: String?,
    val isProfile: Boolean = false,
    val thumbnailUrl: String? = null,
    val album: String? = null,
    val type: String,
    val explicit: Boolean = false,
    val createDate: Long = System.currentTimeMillis()
) {
    fun toYTItem(): YTItem {
        return when (type) {
            "SONG" -> SongItem(
                id = id,
                title = title,
                artists = subtitle?.split(", ")?.map { Artist(name = it, id = null) } ?: emptyList(),
                album = album?.let { Album(name = it, id = "") },
                thumbnail = thumbnailUrl ?: "",
                explicit = explicit
            )
            "ALBUM" -> AlbumItem(
                browseId = id,
                playlistId = secondaryId ?: "",
                title = title,
                artists = subtitle?.split(", ")?.map { Artist(name = it, id = null) },
                thumbnail = thumbnailUrl ?: "",
                explicit = explicit
            )
            "ARTIST", "USER_PROFILE" -> ArtistItem(
                id = id,
                title = title,
                subscriptions = null,
                thumbnail = thumbnailUrl ?: "",
                channelId = channelId,
                shuffleEndpoint = null,
                radioEndpoint = null,
                isProfile = isProfile,
            )
            else -> throw IllegalArgumentException("Unknown type: $type")
        }
    }
}