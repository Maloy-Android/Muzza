package com.maloy.muzza.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.maloy.muzza.data.db.DatabaseDriverFactory
import com.maloy.muzza.data.db.createDatabase
import com.maloy.muzza.db.MuzzaDatabase
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
import com.maloy.muzza.util.ioDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MusicRepositoryImpl(driverFactory: DatabaseDriverFactory) : MusicRepository {

    private val db: MuzzaDatabase = createDatabase(driverFactory)
    private val songQ get() = db.songQueries
    private val albumQ get() = db.albumQueries
    private val artistQ get() = db.artistQueries
    private val playlistQ get() = db.playlistQueries
    private val psmQ get() = db.playlistSongMapQueries
    private val samQ get() = db.songAlbumMapQueries
    private val sartQ get() = db.songArtistMapQueries
    private val eventQ get() = db.eventQueries
    private val lyricsQ get() = db.lyricsQueries
    private val formatQ get() = db.formatQueries
    private val searchQ get() = db.searchHistoryQueries

    // ── Songs ──────────────────────────────────────────────────────────────

    override fun getAllSongs(): Flow<List<SongEntity>> =
        songQ.selectAll().asFlow().mapToList(ioDispatcher).map { rows ->
            rows.map { it.toModel() }
        }

    override fun getLikedSongs(): Flow<List<SongEntity>> =
        songQ.selectLiked().asFlow().mapToList(ioDispatcher).map { rows ->
            rows.map { it.toModel() }
        }

    override fun getLocalSongs(): Flow<List<SongEntity>> =
        songQ.selectLocal().asFlow().mapToList(ioDispatcher).map { rows ->
            rows.map { it.toModel() }
        }

    override fun getSongById(id: String): Flow<SongEntity?> =
        songQ.selectById(id).asFlow().mapToOneOrNull(ioDispatcher).map { it?.toModel() }

    override suspend fun upsertSong(song: SongEntity) = withContext(ioDispatcher) {
        songQ.upsert(song.toDb())
    }

    override suspend fun deleteSong(id: String) = withContext(ioDispatcher) {
        songQ.delete(id)
    }

    override suspend fun toggleLike(id: String, liked: Boolean) = withContext(ioDispatcher) {
        songQ.updateLiked(if (liked) 1 else 0, id)
    }

    override suspend fun incrementPlayTime(id: String, ms: Long) = withContext(ioDispatcher) {
        songQ.updateTotalPlayTime(ms, id)
    }

    override suspend fun setInLibrary(id: String, timestamp: String?) = withContext(ioDispatcher) {
        songQ.updateInLibrary(timestamp, id)
    }

    override suspend fun quickPicks(): List<SongEntity> = withContext(ioDispatcher) {
        songQ.selectQuickPicks().executeAsList().map { it.toModel() }
    }

    override suspend fun forgottenFavorites(): List<SongEntity> = withContext(ioDispatcher) {
        songQ.selectForgottenFavorites().executeAsList().map { it.toModel() }
    }

    // ── Albums ─────────────────────────────────────────────────────────────

    override fun getAllAlbums(): Flow<List<AlbumEntity>> =
        albumQ.selectAll().asFlow().mapToList(ioDispatcher).map { rows -> rows.map { it.toModel() } }

    override fun getBookmarkedAlbums(): Flow<List<AlbumEntity>> =
        albumQ.selectBookmarked().asFlow().mapToList(ioDispatcher).map { rows -> rows.map { it.toModel() } }

    override fun getAlbumById(id: String): Flow<AlbumEntity?> =
        albumQ.selectById(id).asFlow().mapToOneOrNull(ioDispatcher).map { it?.toModel() }

    override suspend fun upsertAlbum(album: AlbumEntity) = withContext(ioDispatcher) {
        albumQ.upsert(album.toDb())
    }

    override suspend fun deleteAlbum(id: String) = withContext(ioDispatcher) { albumQ.delete(id) }

    override suspend fun toggleAlbumBookmark(id: String, bookmarkedAt: String?) = withContext(ioDispatcher) {
        albumQ.updateBookmarkedAt(bookmarkedAt, id)
    }

    // ── Artists ────────────────────────────────────────────────────────────

    override fun getAllArtists(): Flow<List<ArtistEntity>> =
        artistQ.selectAll().asFlow().mapToList(ioDispatcher).map { rows -> rows.map { it.toModel() } }

    override fun getBookmarkedArtists(): Flow<List<ArtistEntity>> =
        artistQ.selectBookmarked().asFlow().mapToList(ioDispatcher).map { rows -> rows.map { it.toModel() } }

    override fun getArtistById(id: String): Flow<ArtistEntity?> =
        artistQ.selectById(id).asFlow().mapToOneOrNull(ioDispatcher).map { it?.toModel() }

    override suspend fun upsertArtist(artist: ArtistEntity) = withContext(ioDispatcher) {
        artistQ.upsert(artist.toDb())
    }

    override suspend fun deleteArtist(id: String) = withContext(ioDispatcher) { artistQ.delete(id) }

    override suspend fun toggleArtistBookmark(id: String, bookmarkedAt: String?) = withContext(ioDispatcher) {
        artistQ.updateBookmarkedAt(bookmarkedAt, id)
    }

    // ── Playlists ──────────────────────────────────────────────────────────

    override fun getAllPlaylists(): Flow<List<PlaylistEntity>> =
        playlistQ.selectAll().asFlow().mapToList(ioDispatcher).map { rows -> rows.map { it.toModel() } }

    override fun getBookmarkedPlaylists(): Flow<List<PlaylistEntity>> =
        playlistQ.selectBookmarked().asFlow().mapToList(ioDispatcher).map { rows -> rows.map { it.toModel() } }

    override fun getPlaylistById(id: String): Flow<PlaylistEntity?> =
        playlistQ.selectById(id).asFlow().mapToOneOrNull(ioDispatcher).map { it?.toModel() }

    override suspend fun upsertPlaylist(playlist: PlaylistEntity) = withContext(ioDispatcher) {
        playlistQ.upsert(playlist.toDb())
    }

    override suspend fun deletePlaylist(id: String) = withContext(ioDispatcher) { playlistQ.delete(id) }

    override suspend fun togglePlaylistBookmark(id: String, bookmarkedAt: String?) = withContext(ioDispatcher) {
        playlistQ.updateBookmarkedAt(bookmarkedAt, id)
    }

    // ── Playlist–Song mapping ──────────────────────────────────────────────

    override fun getPlaylistSongs(playlistId: String): Flow<List<PlaylistSongMap>> =
        psmQ.selectByPlaylist(playlistId).asFlow().mapToList(ioDispatcher).map { rows ->
            rows.map { PlaylistSongMap(it.playlistId, it.songId, it.position.toInt()) }
        }

    override suspend fun addSongToPlaylist(playlistId: String, songId: String, position: Int) =
        withContext(ioDispatcher) { psmQ.insert(playlistId, songId, position.toLong()) }

    override suspend fun removeSongFromPlaylist(playlistId: String, songId: String) =
        withContext(ioDispatcher) { psmQ.deleteByPlaylistAndSong(playlistId, songId) }

    override suspend fun clearPlaylist(playlistId: String) =
        withContext(ioDispatcher) { psmQ.deleteByPlaylist(playlistId) }

    override fun getSongCountForPlaylist(playlistId: String): Flow<Long> =
        psmQ.countByPlaylist(playlistId).asFlow().mapToOneOrNull(ioDispatcher).map { it ?: 0L }

    // ── Song–Album mapping ─────────────────────────────────────────────────

    override fun getSongsByAlbum(albumId: String): Flow<List<SongAlbumMap>> =
        samQ.selectByAlbum(albumId).asFlow().mapToList(ioDispatcher).map { rows ->
            rows.map { SongAlbumMap(it.songId, it.albumId, it.idx?.toInt()) }
        }

    override suspend fun upsertSongAlbumMap(map: SongAlbumMap) = withContext(ioDispatcher) {
        samQ.insert(map.songId, map.albumId, map.index?.toLong())
    }

    // ── Song–Artist mapping ────────────────────────────────────────────────

    override fun getArtistsBySong(songId: String): Flow<List<SongArtistMap>> =
        sartQ.selectArtistsBySong(songId).asFlow().mapToList(ioDispatcher).map { rows ->
            rows.map { SongArtistMap(it.songId, it.artistId) }
        }

    override suspend fun upsertSongArtistMap(map: SongArtistMap) = withContext(ioDispatcher) {
        sartQ.insert(map.songId, map.artistId)
    }

    // ── Events ────────────────────────────────────────────────────────────

    override suspend fun insertEvent(event: EventEntity) = withContext(ioDispatcher) {
        eventQ.insert(event.songId, event.timestamp.toString(), event.playTime)
    }

    override fun getRecentEvents(limit: Long): Flow<List<EventEntity>> =
        eventQ.selectRecent(limit).asFlow().mapToList(ioDispatcher).map { rows ->
            rows.map { EventEntity(it.id, it.songId, kotlinx.datetime.LocalDateTime.parse(it.timestamp), it.playTime) }
        }

    override suspend fun deleteOldEvents(cutoffTimestamp: String) = withContext(ioDispatcher) {
        eventQ.deleteOlderThan(cutoffTimestamp)
    }

    // ── Lyrics ────────────────────────────────────────────────────────────

    override fun getLyrics(id: String): Flow<LyricsEntity?> =
        lyricsQ.selectById(id).asFlow().mapToOneOrNull(ioDispatcher).map { it?.let { r -> LyricsEntity(r.id, r.lyrics, r.syncedLyrics) } }

    override suspend fun upsertLyrics(lyrics: LyricsEntity) = withContext(ioDispatcher) {
        lyricsQ.upsert(com.maloy.muzza.db.Lyrics(lyrics.id, lyrics.lyrics, lyrics.syncedLyrics))
    }

    override suspend fun deleteLyrics(id: String) = withContext(ioDispatcher) { lyricsQ.delete(id) }

    // ── Format ────────────────────────────────────────────────────────────

    override fun getFormat(id: String): Flow<FormatEntity?> =
        formatQ.selectById(id).asFlow().mapToOneOrNull(ioDispatcher).map { it?.toModel() }

    override suspend fun upsertFormat(format: FormatEntity) = withContext(ioDispatcher) {
        formatQ.upsert(format.toDb())
    }

    override suspend fun deleteExpiredFormats(now: Long) = withContext(ioDispatcher) {
        formatQ.deleteExpired(now)
    }

    // ── Search History ─────────────────────────────────────────────────────

    override fun getSearchHistory(): Flow<List<SearchHistoryEntry>> =
        searchQ.selectAll().asFlow().mapToList(ioDispatcher).map { rows ->
            rows.map { SearchHistoryEntry(it.id.toInt(), it.query) }
        }

    override suspend fun addSearchHistory(query: String) = withContext(ioDispatcher) {
        searchQ.insert(query)
    }

    override suspend fun deleteSearchHistory(id: Long) = withContext(ioDispatcher) {
        searchQ.delete(id)
    }

    override suspend fun clearSearchHistory() = withContext(ioDispatcher) {
        searchQ.deleteAll()
    }
}
