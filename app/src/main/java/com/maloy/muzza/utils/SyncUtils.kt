package com.maloy.muzza.utils

import com.maloy.innertube.YouTube
import com.maloy.innertube.models.AlbumItem
import com.maloy.innertube.models.ArtistItem
import com.maloy.innertube.models.PlaylistItem
import com.maloy.innertube.models.SongItem
import com.maloy.innertube.utils.completed
import com.maloy.innertube.utils.completedLibraryPage
import com.maloy.muzza.db.MusicDatabase
import com.maloy.muzza.db.entities.ArtistEntity
import com.maloy.muzza.db.entities.PlaylistEntity
import com.maloy.muzza.db.entities.PlaylistSongMap
import com.maloy.muzza.db.entities.SongEntity
import com.maloy.muzza.models.toMediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncUtils @Inject constructor(
    val database: MusicDatabase,
) {
    suspend fun syncLikedSongs() = coroutineScope {
        YouTube.playlist("LM").completed().onSuccess { page ->
            val remoteSongs = page.songs
            val remoteIds = remoteSongs.map { it.id }
            val localSongs = database.likedSongsByNameAsc().first()

            localSongs.filterNot { it.id in remoteIds }
                .forEach { database.update(it.song.localToggleLike()) }

            remoteSongs.forEachIndexed { index, song ->
                launch {
                    val dbSong = database.song(song.id).firstOrNull()
                    val timestamp = LocalDateTime.now().minusSeconds(index.toLong())
                    database.transaction {
                        if (dbSong == null) {
                            insert(song.toMediaMetadata()) { it.copy(liked = true, likedDate = timestamp) }
                        } else if (!dbSong.song.liked || dbSong.song.likedDate != timestamp) {
                            update(dbSong.song.copy(liked = true, likedDate = timestamp))
                        }
                    }
                }
            }
        }
    }

    suspend fun syncLikedAlbums() = coroutineScope {
        YouTube.library("FEmusic_liked_albums").completedLibraryPage().onSuccess { page ->
            val remoteAlbums = page.items.filterIsInstance<AlbumItem>().reversed()
            val remoteIds = remoteAlbums.map { it.id }.toSet()
            val localAlbums = database.albumsLikedByNameAsc().first()

            localAlbums.filterNot { it.id in remoteIds }
                .forEach { database.update(it.album.localToggleLike()) }

            remoteAlbums.forEach { album ->
                launch {
                    val dbAlbum = database.album(album.id).firstOrNull()
                    YouTube.album(album.browseId).onSuccess { albumPage ->
                        if (dbAlbum == null) {
                            database.insert(albumPage)
                            database.album(album.id).firstOrNull()?.let { newDbAlbum ->
                                database.update(newDbAlbum.album.localToggleLike())
                            }
                        } else if (dbAlbum.album.bookmarkedAt == null) {
                            database.update(dbAlbum.album.localToggleLike())
                        }
                    }
                }
            }
        }
    }

    suspend fun syncArtistsSubscriptions() = coroutineScope {
        YouTube.library("FEmusic_library_corpus_artists").completedLibraryPage().onSuccess { page ->
            val remoteArtists = page.items.filterIsInstance<ArtistItem>()
            val remoteIds = remoteArtists.map { it.id }.toSet()
            val localArtists = database.artistsBookmarkedByNameAsc().first()

            localArtists.filterNot { it.id in remoteIds }
                .forEach { database.update(it.artist.localToggleLike()) }

            remoteArtists.forEach { artist ->
                launch {
                    val dbArtist = database.artist(artist.id).firstOrNull()
                    database.transaction {
                        if (dbArtist == null) {
                            insert(
                                ArtistEntity(
                                    id = artist.id,
                                    name = artist.title,
                                    thumbnailUrl = artist.thumbnail,
                                    channelId = artist.channelId,
                                    bookmarkedAt = LocalDateTime.now()
                                )
                            )
                        } else if (dbArtist.artist.bookmarkedAt == null) {
                            update(dbArtist.artist.localToggleLike())
                        }
                    }
                }
            }
        }
    }

    suspend fun syncSavedPlaylists() = coroutineScope {
        YouTube.library("FEmusic_liked_playlists").completedLibraryPage().onSuccess { page ->
            val remotePlaylists = page.items.filterIsInstance<PlaylistItem>()
                .filterNot { it.id == "LM" || it.id == "SE" }
                .reversed()
            val remoteIds = remotePlaylists.map { it.id }.toSet()
            val localPlaylists = database.playlistsByNameAsc().first()

            localPlaylists.filterNot { it.playlist.browseId in remoteIds }
                .filterNot { it.playlist.browseId == null }
                .forEach { database.update(it.playlist.localToggleLike()) }

            remotePlaylists.forEach { playlist ->
                launch {
                    var playlistEntity = localPlaylists.find { it.playlist.browseId == playlist.id }?.playlist
                    if (playlistEntity == null) {
                        playlistEntity = PlaylistEntity(
                            name = playlist.title,
                            browseId = playlist.id,
                            thumbnailUrl = playlist.thumbnail,
                            isEditable = playlist.isEditable,
                            bookmarkedAt = LocalDateTime.now(),
                            remoteSongCount = playlist.songCountText?.let { Regex("""\\d+""").find(it)?.value?.toIntOrNull() },
                            playEndpointParams = playlist.playEndpoint?.params,
                            shuffleEndpointParams = playlist.shuffleEndpoint.params,
                            radioEndpointParams = playlist.radioEndpoint?.params
                        )
                        database.insert(playlistEntity)
                    } else {
                        database.update(playlistEntity, playlist)
                    }
                    syncPlaylist(playlist.id, playlistEntity.id)
                }
            }
        }
    }

    suspend fun syncPlaylist(browseId: String, playlistId: String) = coroutineScope {
        YouTube.playlist(browseId).completed().onSuccess { page ->
            val songs = page.songs.map(SongItem::toMediaMetadata)

            val remoteIds = songs.map { it.id }
            val localIds = database.playlistSongs(playlistId).first()
                .sortedBy { it.map.position }
                .map { it.song.id }

            if (remoteIds == localIds) return@onSuccess

            database.transaction {
                runBlocking {
                    database.clearPlaylist(playlistId)
                    songs.forEachIndexed { idx, song ->
                        if (database.song(song.id).firstOrNull() == null) {
                            database.insert(song)
                        }
                        database.insert(
                            PlaylistSongMap(
                                songId = song.id,
                                playlistId = playlistId,
                                position = idx,
                                setVideoId = song.setVideoId
                            )
                        )
                    }
                }
            }
        }
    }
    suspend fun syncRecentActivity() {
        YouTube.libraryRecentActivity().onSuccess { page ->
            val recentActivity = page.items.take(9).drop(1)
            coroutineScope {
                launch(Dispatchers.IO) {
                    database.clearRecentActivity()

                    recentActivity.reversed().forEach { database.insertRecentActivityItem(it) }
                }
            }
        }
    }

    suspend fun syncUploadedSongs() = coroutineScope {
        YouTube.library("FEmusic_library_privately_owned_tracks").completedLibraryPage().onSuccess { page ->
            val remoteSongs = page.items.filterIsInstance<SongItem>().reversed()
            val remoteIds = remoteSongs.map { it.id }.toSet()
            val localSongs = database.uploadedSongsByNameAsc().first()

            localSongs.filterNot { it.id in remoteIds }
                .forEach { database.update(it.song.toggleUploaded()) }

            remoteSongs.forEach { song ->
                launch {
                    val dbSong = database.song(song.id).firstOrNull()
                    database.transaction {
                        if (dbSong == null) {
                            insert(song.toMediaMetadata()) { it.toggleUploaded() }
                        } else if (!dbSong.song.isUploaded) {
                            update(dbSong.song.toggleUploaded())
                        }
                    }
                }
            }
        }
    }
    suspend fun syncUploadedAlbums() = coroutineScope {
        YouTube.library("FEmusic_library_privately_owned_releases").completedLibraryPage().onSuccess { page ->
            val remoteAlbums = page.items.filterIsInstance<AlbumItem>().reversed()
            val remoteIds = remoteAlbums.map {it.id}.toSet()
            val localAlbums = database.albumsUploadedByNameAsc().first()

            localAlbums.filterNot {it.id in remoteIds}
                .forEach { database.update(it.album.toggleUploaded()) }

            remoteAlbums.forEach { album ->
                launch {
                    val dbAlbum = database.album(album.id).firstOrNull()
                    YouTube.album(album.browseId).onSuccess { albumPage ->
                        if (dbAlbum == null) {
                            database.insert(albumPage)
                            database.album(album.id).firstOrNull()?.let { newDbAlbum ->
                                database.update(newDbAlbum.album.toggleUploaded())
                            }
                        } else if (!dbAlbum.album.isUploaded) {
                            database.update(dbAlbum.album.toggleUploaded())
                        }
                    }.onFailure {
                        reportException(it)
                    }
                }
            }

        }
    }
}