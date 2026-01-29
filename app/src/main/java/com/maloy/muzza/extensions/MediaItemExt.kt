package com.maloy.muzza.extensions

import android.content.Context
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC
import com.maloy.innertube.models.SongItem
import com.maloy.muzza.db.entities.Song
import com.maloy.muzza.db.entities.SongEntity
import com.maloy.muzza.models.MediaMetadata
import com.maloy.muzza.models.toMediaMetadata
import com.maloy.muzza.ui.utils.resize
import com.maloy.muzza.utils.getPlaybackUriForLocalSong

val MediaItem.metadata: MediaMetadata?
    get() = localConfiguration?.tag as? MediaMetadata

fun Song.toMediaItem(context: Context? = null) = MediaItem.Builder()
    .setMediaId(song.id)
    .setUri(
        if (song.isLocal && context != null) {
            val uri = getPlaybackUriForLocalSong(context, song)
            uri ?: "file://${song.localPath}".toUri()
        } else {
            song.id.toUri()
        }
    )
    .setCustomCacheKey(song.id)
    .setTag(toMediaMetadata())
    .setMediaMetadata(
        androidx.media3.common.MediaMetadata.Builder()
            .setTitle(song.title)
            .setSubtitle(artists.joinToString { it.name })
            .setArtist(artists.joinToString { it.name })
            .setArtworkUri(song.thumbnailUrl?.resize(544, 544)?.toUri())
            .setAlbumTitle(song.albumName)
            .setMediaType(MEDIA_TYPE_MUSIC)
            .build()
    )
    .build()

fun Song.toMediaItemWithPlaylist(playlistId: String, context: Context? = null) = MediaItem.Builder()
    .setMediaId(song.id)
    .setUri(
        if (song.isLocal && context != null) {
            val uri = getPlaybackUriForLocalSong(context, song)
            uri ?: "file://${song.localPath}".toUri()
        } else {
            song.id.toUri()
        }
    )
    .setCustomCacheKey(song.id)
    .setTag(
        toMediaMetadata().copy(
            playlist = MediaMetadata.Playlist(
                id = playlistId,
                author = null
            )
        )
    )
    .setMediaMetadata(
        androidx.media3.common.MediaMetadata.Builder()
            .setTitle(song.title)
            .setSubtitle(artists.joinToString { it.name })
            .setArtist(artists.joinToString { it.name })
            .setArtworkUri(song.thumbnailUrl?.resize(544, 544)?.toUri())
            .setAlbumTitle(song.albumName)
            .setMediaType(MEDIA_TYPE_MUSIC)
            .setExtras(
                Bundle().apply {
                    putBoolean("isVideoSong", song.isVideoSong)
                }
            )
            .build()
    )
    .build()

fun SongItem.toMediaItem() = MediaItem.Builder()
    .setMediaId(id)
    .setUri(id)
    .setCustomCacheKey(id)
    .setTag(toMediaMetadata())
    .setMediaMetadata(
        androidx.media3.common.MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(artists.joinToString { it.name })
            .setArtist(artists.joinToString { it.name })
            .setArtworkUri(thumbnail.toUri())
            .setAlbumTitle(album?.name)
            .setMediaType(MEDIA_TYPE_MUSIC)
            .build()
    )
    .build()

fun SongItem.toMediaItemWithPlaylist(playlistId: String) = MediaItem.Builder()
    .setMediaId(id)
    .setUri(id)
    .setCustomCacheKey(id)
    .setTag(
        toMediaMetadata().copy(
            playlist = MediaMetadata.Playlist(
                id = playlistId,
                author = null
            )
        )
    )
    .setMediaMetadata(
        androidx.media3.common.MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(artists.joinToString { it.name })
            .setArtist(artists.joinToString { it.name })
            .setArtworkUri(thumbnail.toUri())
            .setAlbumTitle(album?.name)
            .setMediaType(MEDIA_TYPE_MUSIC)
            .build()
    )
    .build()

fun MediaMetadata.toMediaItem(context: Context? = null) = MediaItem.Builder()
    .setMediaId(id)
    .setUri(
        if (isLocal && context != null && localPath != null) {
            val songEntity = SongEntity(
                id = id,
                title = title,
                duration = duration,
                artistName = artists.firstOrNull()?.name,
                albumName = null,
                isLocal = true,
                localPath = localPath,
                contentUri = null
            )
            val uri = getPlaybackUriForLocalSong(context, songEntity)
            uri ?: "file://$localPath".toUri()
        } else {
            id.toUri()
        }
    )
    .setCustomCacheKey(id)
    .setTag(this)
    .setMediaMetadata(
        androidx.media3.common.MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(artists.joinToString { it.name })
            .setArtist(artists.joinToString { it.name })
            .setArtworkUri(thumbnailUrl?.toUri())
            .setAlbumTitle(album?.title)
            .setMediaType(MEDIA_TYPE_MUSIC)
            .build()
    )
    .build()