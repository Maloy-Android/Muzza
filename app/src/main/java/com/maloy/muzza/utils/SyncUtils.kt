package com.maloy.muzza.utils

import com.maloy.innertube.YouTube
import com.maloy.innertube.models.AlbumItem
import com.maloy.innertube.models.ArtistItem
import com.maloy.innertube.models.LikedMusicPlaylistFragments
import com.maloy.innertube.models.PlaylistItem
import com.maloy.innertube.models.SongItem
import com.maloy.innertube.utils.completed
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
    suspend fun getLikedMusicPlaylistFragments(): LikedMusicPlaylistFragments? {
        return YouTube.playlist("LM").completed().map { fragment ->
            LikedMusicPlaylistFragments(
                likedMusicThumbnail = fragment.playlist.thumbnail,
                likedMusicTitle = fragment.playlist.title
            )
        }.getOrNull()
    }
    suspend fun syncLikedSongs() {
        YouTube.playlist("LM").completed().onSuccess { page ->
            val songs = page.songs.reversed()
            database.likedSongsByNameAsc().first()
                .filter {
                    !it.song.isLocal && it.id !in songs.map(SongItem::id)
                }
                .filterNot { it.song.liked }
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
        YouTube.library("FEmusic_liked_albums").completed().onSuccess { page ->
            val albums = page.items.filterIsInstance<AlbumItem>().reversed()

            database.albumsLikedByNameAsc().first()
                .filterNot { it.album.bookmarkedAt != null && it.id in albums.map(AlbumItem::id) }
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
        YouTube.library("FEmusic_library_corpus_artists").completed().onSuccess { page ->
            val artists = page.items.filterIsInstance<ArtistItem>()

            database.artistsBookmarkedByNameAsc().first()
                .filterNot { it.artist.bookmarkedAt != null && it.id in artists.map(ArtistItem::id) }
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
        YouTube.library("FEmusic_liked_playlists").completed().onSuccess { page ->
            val playlistList = page.items.filterIsInstance<PlaylistItem>()
                .filterNot { it.id == "LM" || it.id == "SE" }
                .reversed()
            val dbPlaylists = database.playlistsByNameAsc().first()

            dbPlaylists.filterNot { it.playlist.browseId in playlistList.map(PlaylistItem::id) }
                .filterNot { it.playlist.bookmarkedAt != null && it.playlist.browseId == null }
                .filterNot { it.playlist.isLocal }
                .forEach { database.update(it.playlist.localToggleLike()) }

            playlistList.onEach { playlist ->
                var playlistEntity =
                    dbPlaylists.find { playlist.id == it.playlist.browseId }?.playlist
                if (playlistEntity == null) {
                    playlistEntity = PlaylistEntity(
                        name = playlist.title,
                        playlistAuthors = playlist.author?.name,
                        browseId = playlist.id,
                        thumbnailUrl = playlist.thumbnail,
                        isEditable = true,
                        bookmarkedAt = LocalDateTime.now(),
                        remoteSongCount = playlist.songCountText?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() },
                        playEndpointParams = playlist.playEndpoint?.params,
                        shuffleEndpointParams = playlist.shuffleEndpoint?.params,
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
}