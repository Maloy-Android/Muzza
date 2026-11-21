package com.maloy.muzza

import com.maloy.muzza.data.db.DatabaseDriverFactory
import com.maloy.muzza.data.db.toDb
import com.maloy.muzza.data.db.toModel
import app.cash.sqldelight.db.SqlDriver
import com.maloy.muzza.db.MuzzaDatabase
import com.maloy.muzza.domain.model.AlbumEntity
import com.maloy.muzza.domain.model.ArtistEntity
import com.maloy.muzza.domain.model.PlaylistEntity
import com.maloy.muzza.domain.model.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.decodeFromString
import platform.Foundation.NSUUID
import kotlin.time.Clock

private val iosDriver by lazy { DatabaseDriverFactory().createDriver() }
private val iosDatabase by lazy { MuzzaDatabase(iosDriver) }
private val iosDatabaseMutex = Mutex()

internal class IosDuplicateSongException : IllegalStateException("This song is already in the playlist.")

internal suspend fun <T> withIosDatabase(block: (MuzzaDatabase) -> T): T =
    withContext(Dispatchers.Default) {
        iosDatabaseMutex.withLock { block(iosDatabase) }
    }

private fun now() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

private fun SongEntity.toIosMediaItem() = IosMediaItem(
    id = id,
    title = title,
    subtitle = artistName.orEmpty().ifBlank { "Song" },
    thumbnail = thumbnailUrl.orEmpty(),
    kind = "song",
    explicit = explicit,
    duration = duration.takeIf { it >= 0 },
)

internal fun MuzzaDatabase.saveSong(item: IosMediaItem, liked: Boolean? = null) {
    require(item.kind == "song" && item.id.isNotBlank() && item.title.isNotBlank()) {
        "Invalid song data."
    }
    val existing = songQueries.selectById(item.id).executeAsOneOrNull()?.toModel()
    val song = (existing ?: SongEntity(id = item.id, title = item.title)).copy(
        title = item.title,
        duration = item.duration ?: existing?.duration ?: -1,
        thumbnailUrl = item.thumbnail.takeIf(String::isNotBlank) ?: existing?.thumbnailUrl,
        artistName = item.subtitle.takeIf { it.isNotBlank() && it != "Song" } ?: existing?.artistName,
        liked = liked ?: existing?.liked ?: false,
        inLibrary = if (liked == true) now() else existing?.inLibrary,
        explicit = item.explicit || existing?.explicit == true,
    )
    songQueries.upsert(song.toDb())
}

internal fun MuzzaDatabase.libraryResponse(): IosBridgeResponse {
    val liked = songQueries.selectLiked().executeAsList().map { it.toModel().toIosMediaItem() }
    val playlists = playlistQueries.selectAll().executeAsList()
        .filter { it.isLocal != 0L || it.bookmarkedAt != null }.map { playlist ->
        val count = playlistSongMapQueries.countByPlaylist(playlist.id).executeAsOne()
        IosMediaItem(
            id = if (playlist.isLocal != 0L) playlist.id else playlist.browseId ?: playlist.id,
            title = playlist.name,
            subtitle = if (playlist.isLocal != 0L) "$count ${if (count == 1L) "song" else "songs"}" else "Playlist",
            thumbnail = playlist.thumbnailUrl.orEmpty(),
            kind = if (playlist.isLocal != 0L) "localPlaylist" else "playlist",
        )
    }
    val albums = albumQueries.selectAll().executeAsList().filter { it.bookmarkedAt != null }.map { album ->
        IosMediaItem(album.id, album.title, "Album", album.thumbnailUrl.orEmpty(), "album")
    }
    val artists = artistQueries.selectAll().executeAsList().filter { it.bookmarkedAt != null }.map { artist ->
        IosMediaItem(artist.id, artist.name, "Artist", artist.thumbnailUrl.orEmpty(), "artist")
    }
    val recent = eventQueries.selectRecent(100).executeAsList()
        .distinctBy { it.songId }
        .mapNotNull { event -> songQueries.selectById(event.songId).executeAsOneOrNull()?.toModel()?.toIosMediaItem() }
    return IosBridgeResponse(
        items = liked,
        playlists = playlists,
        albums = albums,
        artists = artists,
        recent = recent,
    )
}

internal fun MuzzaDatabase.localPlaylistResponse(id: String): IosBridgeResponse {
    val playlist = playlistQueries.selectById(id).executeAsOneOrNull()
        ?: throw IllegalArgumentException("Playlist not found.")
    require(playlist.isLocal != 0L) { "This playlist is not stored on this device." }
    val songs = playlistSongMapQueries.selectByPlaylist(id).executeAsList().mapNotNull { entry ->
        songQueries.selectById(entry.songId).executeAsOneOrNull()?.toModel()?.toIosMediaItem()?.copy(entryId = entry.id)
    }
    return IosBridgeResponse(items = songs)
}

fun fetchIosLibrary(completion: (String) -> Unit) = iosRequest(completion) {
    withIosDatabase { it.libraryResponse() }
}

fun importIosLikes(json: String, completion: (String) -> Unit) = iosRequest(completion) {
    val items = iosJson.decodeFromString<List<IosMediaItem>>(json).distinctBy { it.id }
    withIosDatabase { db ->
        db.transaction {
            items.forEach { db.saveSong(it, liked = true) }
        }
        db.libraryResponse()
    }
}

fun setIosSongLiked(json: String, liked: Boolean, completion: (String) -> Unit) = iosRequest(completion) {
    val item = iosJson.decodeFromString<IosMediaItem>(json)
    withIosDatabase { db ->
        db.transaction { db.saveSong(item, liked) }
        db.libraryResponse()
    }
}

fun setIosCollectionSaved(json: String, saved: Boolean, completion: (String) -> Unit) = iosRequest(completion) {
    val item = iosJson.decodeFromString<IosMediaItem>(json)
    require(item.id.isNotBlank() && item.title.isNotBlank()) { "Invalid collection data." }
    withIosDatabase { db ->
        val time = now()
        when (item.kind) {
            "album" -> {
                val existing = db.albumQueries.selectById(item.id).executeAsOneOrNull()?.toModel()
                require(saved || existing != null) { "Album not found in the library." }
                val album = (existing ?: AlbumEntity(
                    id = item.id, title = item.title, songCount = 0, duration = 0, lastUpdateTime = time,
                )).copy(
                    title = item.title,
                    thumbnailUrl = item.thumbnail.takeIf(String::isNotBlank) ?: existing?.thumbnailUrl,
                    lastUpdateTime = time,
                    bookmarkedAt = if (saved) time else null,
                )
                db.albumQueries.upsert(album.toDb())
            }
            "artist" -> {
                val existing = db.artistQueries.selectById(item.id).executeAsOneOrNull()?.toModel()
                require(saved || existing != null) { "Artist not found in the library." }
                val artist = (existing ?: ArtistEntity(
                    id = item.id, name = item.title, lastUpdateTime = time,
                )).copy(
                    name = item.title,
                    thumbnailUrl = item.thumbnail.takeIf(String::isNotBlank) ?: existing?.thumbnailUrl,
                    lastUpdateTime = time,
                    bookmarkedAt = if (saved) time else null,
                )
                db.artistQueries.upsert(artist.toDb())
            }
            "playlist" -> {
                val existing = db.playlistQueries.selectById(item.id).executeAsOneOrNull()?.toModel()
                require(saved || existing != null) { "Playlist not found in the library." }
                val playlist = (existing ?: PlaylistEntity(
                    id = item.id, name = item.title, browseId = item.id, isEditable = false, isLocal = false,
                )).copy(
                    name = item.title,
                    thumbnailUrl = item.thumbnail.takeIf(String::isNotBlank) ?: existing?.thumbnailUrl,
                    lastUpdateTime = time,
                    bookmarkedAt = if (saved) time else null,
                )
                db.playlistQueries.upsert(playlist.toDb())
            }
            else -> throw IllegalArgumentException("This collection cannot be saved.")
        }
        db.libraryResponse()
    }
}

fun recordIosPlay(json: String, completion: (String) -> Unit) = iosRequest(completion) {
    val item = iosJson.decodeFromString<IosMediaItem>(json)
    withIosDatabase { db ->
        db.transaction {
            db.saveSong(item)
            db.eventQueries.insert(item.id, now().toString(), 0)
        }
        db.libraryResponse()
    }
}

fun fetchIosLocalPlaylist(id: String, completion: (String) -> Unit) = iosRequest(completion) {
    withIosDatabase { it.localPlaylistResponse(id) }
}

fun createIosPlaylist(name: String, completion: (String) -> Unit) = iosRequest(completion) {
    val title = name.trim()
    require(title.isNotEmpty() && title.length <= 120) { "Enter a playlist name of up to 120 characters." }
    withIosDatabase { db ->
        val time = now()
        val playlist = PlaylistEntity(
            id = "LP_${NSUUID().UUIDString}",
            name = title,
            createdAt = time,
            lastUpdateTime = time,
            isLocal = true,
        )
        db.playlistQueries.upsert(playlist.toDb())
        db.libraryResponse()
    }
}

fun addIosSongToPlaylist(
    id: String,
    json: String,
    allowDuplicate: Boolean,
    completion: (String) -> Unit,
) = iosRequest(completion) {
    val item = iosJson.decodeFromString<IosMediaItem>(json)
    withIosDatabase { db ->
        db.transaction {
            val playlist = db.playlistQueries.selectById(id).executeAsOneOrNull()
                ?: throw IllegalArgumentException("Playlist not found.")
            require(playlist.isLocal != 0L && playlist.isEditable != 0L) { "Playlist cannot be edited." }
            val songs = db.playlistSongMapQueries.selectByPlaylist(id).executeAsList()
            if (!allowDuplicate && songs.any { it.songId == item.id }) throw IosDuplicateSongException()
            db.saveSong(item)
            val position = (songs.maxOfOrNull { it.position } ?: -1L) + 1
            db.playlistSongMapQueries.insert(id, item.id, position)
        }
        db.libraryResponse()
    }
}

internal fun MuzzaDatabase.removeLocalPlaylistEntry(driver: SqlDriver, id: String, entryId: Long) {
    transaction {
        val playlist = playlistQueries.selectById(id).executeAsOneOrNull()
            ?: throw IllegalArgumentException("Playlist not found.")
        require(playlist.isLocal != 0L && playlist.isEditable != 0L) { "Playlist cannot be edited." }
        require(playlistSongMapQueries.selectByPlaylist(id).executeAsList().any { it.id == entryId }) {
            "Song not found in this playlist."
        }
        driver.execute(null, "DELETE FROM playlistSongMap WHERE id = ? AND playlistId = ?", 2) {
            bindLong(0, entryId)
            bindString(1, id)
        }
    }
    driver.notifyListeners("playlistSongMap")
}

fun removeIosSongFromPlaylist(id: String, entryId: Long, completion: (String) -> Unit) = iosRequest(completion) {
    withIosDatabase { db ->
        db.removeLocalPlaylistEntry(iosDriver, id, entryId)
        db.localPlaylistResponse(id)
    }
}

fun deleteIosPlaylist(id: String, completion: (String) -> Unit) = iosRequest(completion) {
    withIosDatabase { db ->
        db.transaction {
            val playlist = db.playlistQueries.selectById(id).executeAsOneOrNull()
                ?: throw IllegalArgumentException("Playlist not found.")
            require(playlist.isLocal != 0L && playlist.isEditable != 0L) { "Playlist cannot be deleted." }
            db.playlistSongMapQueries.deleteByPlaylist(id)
            db.playlistQueries.delete(id)
        }
        db.libraryResponse()
    }
}
