package com.maloy.muzza.utils

import com.maloy.innertube.YouTube
import com.maloy.innertube.models.AlbumItem
import com.maloy.innertube.models.ArtistItem
import com.maloy.innertube.models.LikedMusicPlaylistFragments
import com.maloy.innertube.models.PlaylistItem
import com.maloy.innertube.models.PlaylistsFragments
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
                likedMusicTitle = fragment.playlist.title,
                likedMusicAuthorId = fragment.playlist.author?.id,
                likedMusicAuthorName = fragment.playlist.author?.name,
                likedMusicAuthorAvatarImage = fragment.playlist.authorAvatarUrl,
                likedMusicDescription = fragment.playlist.description
            )
        }.getOrNull()
    }

    suspend fun getPlaylistFragments(playlistId: String): Result<PlaylistsFragments> {
        return YouTube.playlist(playlistId).completed().map { fragment ->
            PlaylistsFragments(
                id = fragment.playlist.id,
                name = fragment.playlist.title,
                playlistAuthorsId = fragment.playlist.author?.id,
                playlistAuthorName = fragment.playlist.author?.name,
                playlistAuthorAvatarUrl = fragment.playlist.authorAvatarUrl,
                thumbnailUrl = fragment.playlist.thumbnail,
                isEditable = true,
                bookmarkedAt = LocalDateTime.now(),
                remoteSongCount = fragment.playlist.songCountText?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() },
                playEndpointParams = fragment.playlist.playEndpoint?.params,
                shuffleEndpointParams = fragment.playlist.shuffleEndpoint?.params,
                radioEndpointParams = fragment.playlist.radioEndpoint?.params,
                description = fragment.playlist.description
            )
        }
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
                YouTube.album(album.browseId)?.onSuccess { albumPage ->
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
                .filterNot { it.artist.bookmarkedAt != null }
                .filter {
                    !it.artist.isProfile && it.id !in artists.map(ArtistItem::id)
                }
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
                                    bookmarkedAt = LocalDateTime.now(),
                                    isProfile = artist.isProfile
                                )
                            )
                        }
                        else -> if (dbArtist.artist.bookmarkedAt == null && !artist.isProfile)
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

            dbPlaylists.filter { dbPlaylist ->
                dbPlaylist.playlist.browseId !in playlistList.map(PlaylistItem::id) &&
                        dbPlaylist.playlist.bookmarkedAt != null &&
                        !dbPlaylist.playlist.isLocal
            }.forEach { database.update(it.playlist.localToggleLike()) }

            playlistList.forEach { playlistItem ->
                val fragments = getPlaylistFragments(playlistItem.id).getOrNull() ?: return@forEach

                var playlist = dbPlaylists.find { it.playlist.browseId == playlistItem.id }?.playlist
                    ?: database.getPlaylistByBrowseId(playlistItem.id)

                if (playlist == null) {
                    playlist = PlaylistEntity(
                        name = fragments.name!!,
                        browseId = playlistItem.id,
                        thumbnailUrl = fragments.thumbnailUrl,
                        playlistAuthorsId = fragments.playlistAuthorsId,
                        playlistAuthorName = fragments.playlistAuthorName,
                        playlistAuthorAvatarUrl = fragments.playlistAuthorAvatarUrl,
                        bookmarkedAt = LocalDateTime.now(),
                        remoteSongCount = fragments.remoteSongCount,
                        playEndpointParams = fragments.playEndpointParams,
                        shuffleEndpointParams = fragments.shuffleEndpointParams,
                        radioEndpointParams = fragments.radioEndpointParams,
                        description = fragments.description,
                        isEditable = true
                    )
                    database.insert(playlist)
                    playlist = database.getPlaylistByBrowseId(playlistItem.id) ?: return@forEach
                }

                syncPlaylist(playlistItem.id, playlist.id)
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