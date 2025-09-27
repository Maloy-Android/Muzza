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
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncUtils @Inject constructor(
    val database: MusicDatabase,
) {
    suspend fun syncLikedSongs() {
        YouTube.playlist("LM").completed().onSuccess { page ->
            val songs = page.songs.reversed()
            database.likedSongsByNameAsc().first()
                .filter {
                    !it.song.isLocal && it.id !in songs.map(SongItem::id)
                }
                .forEach { database.update(it.song.localToggleLike()) }

            songs.forEach { song ->
                val dbSong = database.song(song.id).firstOrNull()
                database.transaction {
                    when (dbSong) {
                        null -> insert(song.toMediaMetadata(), SongEntity::localToggleLike)
                        else -> if (!dbSong.song.liked && !dbSong.song.isLocal) {
                            update(dbSong.song.localToggleLike())
                        }
                    }
                }
            }
        }
    }
    suspend fun syncLikedAlbums() {
        YouTube.library("FEmusic_liked_albums").completedLibraryPage().onSuccess { page ->
            val albums = page.items.filterIsInstance<AlbumItem>().reversed()

            database.albumsLikedByNameAsc().first()
                .filterNot { it.id in albums.map(AlbumItem::id) }
                .forEach { database.update(it.album.localToggleLike()) }

            albums.forEach { album ->
                val dbAlbum = database.album(album.id).firstOrNull()
                YouTube.album(album.browseId).onSuccess { albumPage ->
                    when (dbAlbum) {
                        null -> {
                            database.insert(albumPage)
                            database.album(album.id).firstOrNull()?.let {
                                database.update(it.album.localToggleLike())
                            }
                        }
                        else -> if (dbAlbum.album.bookmarkedAt == null)
                            database.update(dbAlbum.album.localToggleLike())
                    }
                }
            }
        }
    }
    suspend fun syncArtistsSubscriptions() {
        YouTube.library("FEmusic_library_corpus_artists").completedLibraryPage().onSuccess { page ->
            val artists = page.items.filterIsInstance<ArtistItem>()

            database.artistsBookmarkedByNameAsc().first()
                .filterNot { it.id in artists.map(ArtistItem::id) }
                .forEach { database.update(it.artist.localToggleLike()) }
            artists.forEach { artist ->
                val dbArtist = database.artist(artist.id).firstOrNull()
                database.transaction {
                    when (dbArtist) {
                        null -> {
                            insert(
                                ArtistEntity(
                                    id = artist.id,
                                    name = artist.title,
                                    thumbnailUrl = artist.thumbnail,
                                    channelId = artist.channelId,
                                    bookmarkedAt = LocalDateTime.now()
                                )
                            )
                        }
                        else -> if (dbArtist.artist.bookmarkedAt == null)
                            update(dbArtist.artist.localToggleLike())
                    }
                }
            }
        }
    }
    suspend fun syncSavedPlaylists() {
        YouTube.library("FEmusic_liked_playlists").completedLibraryPage().onSuccess { page ->
            val playlistList = page.items.filterIsInstance<PlaylistItem>()
                .filterNot { it.id == "LM" || it.id == "SE" }
                .reversed()
            val dbPlaylists = database.playlistsByNameAsc().first()

            dbPlaylists.filterNot { it.playlist.browseId in playlistList.map(PlaylistItem::id) }
                .filterNot { it.playlist.browseId == null }
                .filterNot { it.playlist.isLocal }
                .forEach { database.update(it.playlist.localToggleLike()) }

            playlistList.onEach { playlist ->
                var playlistEntity =
                    dbPlaylists.find { playlist.id == it.playlist.browseId }?.playlist
                if (playlistEntity == null) {
                    playlistEntity = PlaylistEntity(
                        name = playlist.title,
                        browseId = playlist.id,
                        thumbnailUrl = playlist.thumbnail,
                        isEditable = playlist.isEditable,
                        bookmarkedAt = LocalDateTime.now(),
                        remoteSongCount = playlist.songCountText?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() },
                        playEndpointParams = playlist.playEndpoint?.params,
                        shuffleEndpointParams = playlist.shuffleEndpoint.params,
                        radioEndpointParams = playlist.radioEndpoint?.params
                    )

                    database.insert(playlistEntity)
                } else database.update(playlistEntity, playlist)

                syncPlaylist(playlist.id, playlistEntity.id)
            }
        }
    }
    suspend fun syncPlaylist(browseId: String, playlistId: String) {
        val playlistPage = YouTube.playlist(browseId).completed().getOrNull() ?: return
        database.transaction {
            clearPlaylist(playlistId)
            playlistPage.songs
                .map(SongItem::toMediaMetadata)
                .onEach(::insert)
                .mapIndexed { position, song ->
                    PlaylistSongMap(
                        songId = song.id,
                        playlistId = playlistId,
                        position = position,
                        setVideoId = song.setVideoId
                    )
                }
                .forEach(::insert)
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