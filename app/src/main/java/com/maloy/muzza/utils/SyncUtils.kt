package com.maloy.muzza.utils

import com.maloy.innertube.YouTube
import com.maloy.innertube.models.AlbumItem
import com.maloy.innertube.models.ArtistItem
import com.maloy.innertube.models.PlaylistItem
import com.maloy.innertube.models.SongItem
import com.maloy.innertube.utils.completed
import com.maloy.muzza.db.MusicDatabase
import com.maloy.muzza.db.entities.ArtistEntity
import com.maloy.muzza.db.entities.PlaylistEntity
import com.maloy.muzza.db.entities.PlaylistSongMap
import com.maloy.muzza.db.entities.SongEntity
import com.maloy.muzza.models.toMediaMetadata
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
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
                .filterNot { it.id in songs.map(SongItem::id) }
                .forEach { database.update(it.song.localToggleLike()) }
            songs.forEach { song ->
                val dbSong = database.song(song.id).firstOrNull()
                database.transaction {
                    when (dbSong) {
                        null -> insert(song.toMediaMetadata(), SongEntity::localToggleLike)
                        else -> if (!dbSong.song.liked) update(dbSong.song.localToggleLike())
                    }
                }
            }
        }
    }
    suspend fun syncLikedAlbums() {
        YouTube.libraryAlbums().onSuccess { ytAlbums ->
            database.albumsLikedByNameAsc().first()
                .filterNot { it.id in ytAlbums.map(AlbumItem::id) }
                .forEach { database.update(it.album.localToggleLike()) }
            ytAlbums.forEach { album ->
                val dbAlbum = database.album(album.id).firstOrNull()
                YouTube.album(album.browseId).onSuccess { albumPage ->
                    when (dbAlbum) {
                        null -> {
                            database.insert(albumPage)
                            database.album(album.id).firstOrNull()?.let { database.update(it.album) }
                        }
                        else -> if (dbAlbum.album.bookmarkedAt == null)
                            database.update(dbAlbum.album.localToggleLike())
                    }
                }
            }
        }
    }
    suspend fun syncArtistsSubscriptions() {
        YouTube.libraryArtistsSubscriptions().onSuccess { ytArtists ->
            val artists: List<ArtistItem> = ytArtists
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
        YouTube.likedPlaylists().onSuccess { playlistList ->
            val dbPlaylists = database.playlistsByNameAsc().first()

            dbPlaylists.filterNot { it.playlist.browseId in playlistList.map(PlaylistItem::id) }
                .forEach { database.update(it.playlist.localToggleLike()) }

            playlistList.drop(1).forEach { playlist ->
                var playlistEntity = dbPlaylists.find { playlist.id == it.playlist.browseId }?.playlist
                if (playlistEntity == null) {
                    playlistEntity = PlaylistEntity(name = playlist.title, browseId = playlist.id)
                    database.insert(playlistEntity)
                }
                syncPlaylist(playlist.id, playlistEntity.id)
            }
        }
    }
    suspend fun getRecentActivity() {
        println("running get recent activity")
        YouTube.libraryRecentActivity().onSuccess { page ->
            page.items.forEach {
                println(it.title)
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
}