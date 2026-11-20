package com.maloy.muzza.data.repository

import com.maloy.muzza.domain.model.AlbumEntity
import com.maloy.muzza.domain.model.ArtistEntity
import com.maloy.muzza.domain.model.EventEntity
import com.maloy.muzza.domain.model.FormatEntity
import com.maloy.muzza.domain.model.LyricsEntity
import com.maloy.muzza.domain.model.PlaylistEntity
import com.maloy.muzza.domain.model.PlaylistSongMap
import com.maloy.muzza.domain.model.SearchHistoryEntry
import com.maloy.muzza.domain.model.SongAlbumMap
import com.maloy.muzza.domain.model.SongArtistMap
import com.maloy.muzza.domain.model.SongEntity
import kotlinx.coroutines.flow.Flow

interface MusicRepository {

    // ── Songs ──────────────────────────────────────────────────────────────
    fun getAllSongs(): Flow<List<SongEntity>>
    fun getLikedSongs(): Flow<List<SongEntity>>
    fun getLocalSongs(): Flow<List<SongEntity>>
    fun getSongById(id: String): Flow<SongEntity?>
    suspend fun upsertSong(song: SongEntity)
    suspend fun deleteSong(id: String)
    suspend fun toggleLike(id: String, liked: Boolean)
    suspend fun incrementPlayTime(id: String, ms: Long)
    suspend fun setInLibrary(id: String, timestamp: String?)
    suspend fun quickPicks(): List<SongEntity>
    suspend fun forgottenFavorites(): List<SongEntity>

    // ── Albums ─────────────────────────────────────────────────────────────
    fun getAllAlbums(): Flow<List<AlbumEntity>>
    fun getBookmarkedAlbums(): Flow<List<AlbumEntity>>
    fun getAlbumById(id: String): Flow<AlbumEntity?>
    suspend fun upsertAlbum(album: AlbumEntity)
    suspend fun deleteAlbum(id: String)
    suspend fun toggleAlbumBookmark(id: String, bookmarkedAt: String?)

    // ── Artists ────────────────────────────────────────────────────────────
    fun getAllArtists(): Flow<List<ArtistEntity>>
    fun getBookmarkedArtists(): Flow<List<ArtistEntity>>
    fun getArtistById(id: String): Flow<ArtistEntity?>
    suspend fun upsertArtist(artist: ArtistEntity)
    suspend fun deleteArtist(id: String)
    suspend fun toggleArtistBookmark(id: String, bookmarkedAt: String?)

    // ── Playlists ──────────────────────────────────────────────────────────
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>
    fun getBookmarkedPlaylists(): Flow<List<PlaylistEntity>>
    fun getPlaylistById(id: String): Flow<PlaylistEntity?>
    suspend fun upsertPlaylist(playlist: PlaylistEntity)
    suspend fun deletePlaylist(id: String)
    suspend fun togglePlaylistBookmark(id: String, bookmarkedAt: String?)

    // ── Playlist–Song mapping ──────────────────────────────────────────────
    fun getPlaylistSongs(playlistId: String): Flow<List<PlaylistSongMap>>
    suspend fun addSongToPlaylist(playlistId: String, songId: String, position: Int)
    suspend fun removeSongFromPlaylist(playlistId: String, songId: String)
    suspend fun clearPlaylist(playlistId: String)
    fun getSongCountForPlaylist(playlistId: String): Flow<Long>

    // ── Song–Album mapping ─────────────────────────────────────────────────
    fun getSongsByAlbum(albumId: String): Flow<List<SongAlbumMap>>
    suspend fun upsertSongAlbumMap(map: SongAlbumMap)

    // ── Song–Artist mapping ────────────────────────────────────────────────
    fun getArtistsBySong(songId: String): Flow<List<SongArtistMap>>
    suspend fun upsertSongArtistMap(map: SongArtistMap)

    // ── Events ────────────────────────────────────────────────────────────
    suspend fun insertEvent(event: EventEntity)
    fun getRecentEvents(limit: Long): Flow<List<EventEntity>>
    suspend fun deleteOldEvents(cutoffTimestamp: String)

    // ── Lyrics ────────────────────────────────────────────────────────────
    fun getLyrics(id: String): Flow<LyricsEntity?>
    suspend fun upsertLyrics(lyrics: LyricsEntity)
    suspend fun deleteLyrics(id: String)

    // ── Format ────────────────────────────────────────────────────────────
    fun getFormat(id: String): Flow<FormatEntity?>
    suspend fun upsertFormat(format: FormatEntity)
    suspend fun deleteExpiredFormats(now: Long)

    // ── Search History ─────────────────────────────────────────────────────
    fun getSearchHistory(): Flow<List<SearchHistoryEntry>>
    suspend fun addSearchHistory(query: String)
    suspend fun deleteSearchHistory(id: Long)
    suspend fun clearSearchHistory()
}
