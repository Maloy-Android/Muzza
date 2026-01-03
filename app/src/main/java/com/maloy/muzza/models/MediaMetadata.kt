package com.maloy.muzza.models

import androidx.compose.runtime.Immutable
import com.maloy.innertube.models.SongItem
import com.maloy.muzza.db.entities.*
import com.maloy.muzza.ui.utils.resize
import java.io.Serializable

@Immutable
data class MediaMetadata(
    val id: String,
    val title: String,
    val artists: List<Artist>,
    val duration: Int,
    val thumbnailUrl: String? = null,
    val album: Album? = null,
    val playlist: Playlist? = null,
    val setVideoId: String? = null,
    val isLocal: Boolean = false,
    val localPath: String? = null,
    val explicit: Boolean = false,
    val blurSync: Boolean = false,
    val blurThumbnail: String? = null,
) : Serializable {
    data class Artist(
        val id: String?,
        val name: String,
    ) : Serializable

    data class Album(
        val id: String,
        val title: String,
    ) : Serializable

    data class Playlist(
        val id: String,
    ) : Serializable

    fun toSongEntity() = SongEntity(
        id = id,
        title = title,
        duration = duration,
        thumbnailUrl = thumbnailUrl,
        albumId = album?.id,
        albumName = album?.title,
        isLocal = isLocal,
        localPath = localPath
    )
}

fun Song.toMediaMetadata() = MediaMetadata(
    id = song.id,
    title = song.title,
    artists = artists.map {
        MediaMetadata.Artist(
            id = it.id,
            name = it.name,
        )
    },
    duration = song.duration,
    thumbnailUrl = song.thumbnailUrl,
    album = album?.let {
        MediaMetadata.Album(
            id = it.id,
            title = it.title,
        )
    } ?: song.albumId?.let { albumId ->
        MediaMetadata.Album(
            id = albumId,
            title = song.albumName.orEmpty()
        )
    },
    isLocal = song.isLocal,
    localPath = song.localPath
)

fun SongItem.toMediaMetadata() = MediaMetadata(
    id = id,
    title = title,
    artists = artists.map {
        MediaMetadata.Artist(
            id = it.id,
            name = it.name
        )
    },
    duration = duration ?: -1,
    thumbnailUrl = thumbnail.resize(544, 544),
    album = album?.let {
        MediaMetadata.Album(
            id = it.id,
            title = it.name
        )
    },
    explicit = explicit,
    setVideoId = setVideoId
)


fun Playlist.toMediaMetadata() = MediaMetadata(
    id = playlist.id,
    title = playlist.name,
    artists = emptyList(),
    duration = 0,
    thumbnailUrl = playlist.thumbnailUrl,
    playlist = MediaMetadata.Playlist(
        id = playlist.id,
    )
)