package com.maloy.muzza

import app.cash.sqldelight.db.SqlDriver
import com.maloy.innertube.YouTube
import com.maloy.muzza.data.db.DatabaseDriverFactory
import com.maloy.muzza.data.db.toDb
import com.maloy.muzza.data.db.toModel
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
    thumbnail = thumbnailUrl.orEmpty().toIosArtworkUrl(),
    kind = "song",
    explicit = explicit,
    duration = duration.takeIf { it >= 0 },
    downloadPath = localPath.takeIf { dateDownload != null },
)

private fun MuzzaDatabase.songToIosMediaItem(song: SongEntity): IosMediaItem {
    val artists = songArtistMapQueries.selectArtistsBySong(song.id).executeAsList().mapNotNull { mapping ->
        artistQueries.selectById(mapping.artistId).executeAsOneOrNull()?.toModel()?.let { artist ->
            IosArtistMetadata(
                id = artist.id,
                name = artist.name,
                thumbnail = artist.thumbnailUrl,
                isProfile = artist.isProfile,
            )
        }
    }
    val album = song.albumId?.let { id ->
        IosAlbumMetadata(
            id = id,
            title = albumQueries.selectById(id).executeAsOneOrNull()?.title ?: song.albumName.orEmpty(),
        )
    }
    return song.toIosMediaItem().copy(artists = artists, album = album)
}

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
        albumId = item.album?.id ?: existing?.albumId,
        albumName = item.album?.title ?: existing?.albumName,
        liked = liked ?: existing?.liked ?: false,
        inLibrary = if (liked == true) now() else existing?.inLibrary,
        explicit = item.explicit || existing?.explicit == true,
    )
    songQueries.upsert(song.toDb())
    item.album?.let { albumMetadata ->
        val album = albumQueries.selectById(albumMetadata.id).executeAsOneOrNull()?.toModel()
        albumQueries.upsert(
            (album ?: AlbumEntity(
                id = albumMetadata.id,
                title = albumMetadata.title,
                songCount = 0,
                duration = 0,
                lastUpdateTime = now(),
            )).copy(
                title = albumMetadata.title,
                thumbnailUrl = item.thumbnail.takeIf(String::isNotBlank) ?: album?.thumbnailUrl,
                lastUpdateTime = now(),
            ).toDb(),
        )
        songAlbumMapQueries.deleteForSong(item.id)
        songAlbumMapQueries.insert(item.id, albumMetadata.id, 0)
    }
    val artists = item.artists.filter { !it.id.isNullOrBlank() }
    if (artists.isNotEmpty()) {
        songArtistMapQueries.deleteForSong(item.id)
        artists.forEach { artistMetadata ->
            val id = requireNotNull(artistMetadata.id)
            val artist = artistQueries.selectById(id).executeAsOneOrNull()?.toModel()
            artistQueries.upsert(
                (artist ?: ArtistEntity(
                    id = id,
                    name = artistMetadata.name,
                    lastUpdateTime = now(),
                )).copy(
                    name = artistMetadata.name,
                    thumbnailUrl = artistMetadata.thumbnail?.takeIf(String::isNotBlank) ?: artist?.thumbnailUrl,
                    lastUpdateTime = now(),
                    isProfile = artistMetadata.isProfile,
                ).toDb(),
            )
            songArtistMapQueries.insert(item.id, id)
        }
    }
}

internal fun MuzzaDatabase.libraryResponse(): IosBridgeResponse {
    val liked = songQueries.selectLiked().executeAsList().map { songToIosMediaItem(it.toModel()) }
    val playlists = playlistQueries.selectAll().executeAsList()
        .filter { it.isLocal != 0L || it.bookmarkedAt != null }.map { playlist ->
        val count = playlistSongMapQueries.countByPlaylist(playlist.id).executeAsOne()
        IosMediaItem(
            id = if (playlist.isLocal != 0L) playlist.id else playlist.browseId ?: playlist.id,
            title = playlist.name,
            subtitle = if (playlist.isLocal != 0L) "$count ${if (count == 1L) "song" else "songs"}" else "Playlist",
            thumbnail = playlist.thumbnailUrl.orEmpty(),
            kind = if (playlist.isLocal != 0L) "localPlaylist" else "playlist",
            isEditable = playlist.isEditable != 0L,
        )
    }
    val albums = albumQueries.selectAll().executeAsList().filter { it.bookmarkedAt != null }.map { album ->
        IosMediaItem(album.id, album.title, "Album", album.thumbnailUrl.orEmpty(), "album")
    }
    val artists = artistQueries.selectAll().executeAsList().filter { it.bookmarkedAt != null }.map { artist ->
        IosMediaItem(artist.id, artist.name, "Artist", artist.thumbnailUrl.orEmpty(), "artist")
    }
    val downloads = songQueries.selectAll().executeAsList()
        .filter { it.dateDownload != null }
        .map { songToIosMediaItem(it.toModel()) }
    val recent = eventQueries.selectRecent(100).executeAsList()
        .distinctBy { it.songId }
        .mapNotNull { event ->
            songQueries.selectById(event.songId).executeAsOneOrNull()?.toModel()
                ?.let { songToIosMediaItem(it) }
        }
    return IosBridgeResponse(
        items = liked,
        playlists = playlists,
        albums = albums,
        artists = artists,
        downloads = downloads,
        recent = recent,
    )
}

internal fun MuzzaDatabase.localPlaylistResponse(id: String): IosBridgeResponse {
    val playlist = playlistQueries.selectById(id).executeAsOneOrNull()
        ?: throw IllegalArgumentException("Playlist not found.")
    require(playlist.isLocal != 0L) { "This playlist is not stored on this device." }
    val songs = playlistSongMapQueries.selectByPlaylist(id).executeAsList().mapNotNull { entry ->
        songQueries.selectById(entry.songId).executeAsOneOrNull()?.toModel()
            ?.let { songToIosMediaItem(it) }?.copy(entryId = entry.id)
    }
    return IosBridgeResponse(items = songs)
}

fun fetchIosLibrary(completion: (String) -> Unit) = iosRequest(completion) {
    withIosDatabase { it.libraryResponse() }
}

fun syncIosLikedPlaylist(completion: (String) -> Unit) = iosRequest(completion) {
    check(!YouTube.cookie.isNullOrBlank() && YouTube.useLoginForBrowse) {
        "Sign in to YouTube Music first."
    }

    val remoteItems = buildList {
        var continuation: String? = null
        val seenContinuations = mutableSetOf<String>()
        while (true) {
            val page = fetchIosPlaylistSongs(
                playlistId = "LM",
                continuation = continuation,
                setLogin = true,
                hideExplicit = false,
            )
            addAll(page.items.map { it.copy(remoteLiked = true) })
            val next = page.continuation?.takeIf(String::isNotBlank)
            if (next == null || !seenContinuations.add(next)) break
            continuation = next
        }
    }.distinctBy { it.id }

    withIosDatabase { db ->
        db.transaction {
            val remoteIDs = remoteItems.mapTo(mutableSetOf()) { it.id }
            db.songQueries.selectLiked().executeAsList().forEach { existing ->
                if (existing.id !in remoteIDs) {
                    db.saveSong(
                        db.songToIosMediaItem(existing.toModel()),
                        liked = false,
                    )
                }
            }
            remoteItems.forEach { item ->
                db.saveSong(item, liked = true)
            }
        }
        db.libraryResponse()
    }
}

fun syncIosCollections(completion: (String) -> Unit) = iosRequest(completion) {
    val categories = listOf(
        "albums" to "album",
        "artists" to "artist",
        "playlists" to "playlist",
    )
    val syncedIDs = mutableMapOf<String, Set<String>>()

    for ((category, kind) in categories) {
        val ids = mutableSetOf<String>()
        val seenContinuations = mutableSetOf<String>()
        var continuation: String? = null
        while (true) {
            val page = fetchIosYouTubeLibraryPage(category, hideExplicit = false, continuation = continuation)
            page.items.filter { it.kind == kind }.forEach { ids.add(it.id) }
            val next = page.continuation?.takeIf(String::isNotBlank) ?: break
            check(seenContinuations.add(next)) { "YouTube returned a repeated library continuation." }
            continuation = next
        }
        syncedIDs[category] = ids
    }

    withIosDatabase { db ->
        db.transaction {
            val time = now()
            db.albumQueries.selectAll().executeAsList()
                .map { it.toModel() }
                .filter { it.bookmarkedAt != null && it.id !in syncedIDs.getValue("albums") }
                .forEach { db.albumQueries.upsert(it.copy(bookmarkedAt = null, lastUpdateTime = time).toDb()) }
            db.artistQueries.selectAll().executeAsList()
                .map { it.toModel() }
                .filter { it.bookmarkedAt != null && it.id !in syncedIDs.getValue("artists") }
                .forEach { db.artistQueries.upsert(it.copy(bookmarkedAt = null, lastUpdateTime = time).toDb()) }
            db.playlistQueries.selectAll().executeAsList()
                .map { it.toModel() }
                .filter {
                    !it.isLocal && it.bookmarkedAt != null && it.id !in syncedIDs.getValue("playlists")
                }
                .forEach { db.playlistQueries.upsert(it.copy(bookmarkedAt = null, lastUpdateTime = time).toDb()) }
        }
        db.libraryResponse()
    }
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

fun setIosSongLiked(json: String, liked: Boolean, syncRemote: Boolean, completion: (String) -> Unit) = iosRequest(completion) {
    val item = iosJson.decodeFromString<IosMediaItem>(json)
    require(item.kind == "song" && item.id.isNotBlank() && item.title.isNotBlank()) { "Invalid song data." }
    if (syncRemote && !YouTube.cookie.isNullOrBlank() && YouTube.useLoginForBrowse) {
        YouTube.likeVideo(item.id, liked).getOrThrow()
    }
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
                    id = item.id, name = item.title, browseId = item.id,
                    isEditable = item.isEditable, isLocal = false,
                )).copy(
                    name = item.title,
                    thumbnailUrl = item.thumbnail.takeIf(String::isNotBlank) ?: existing?.thumbnailUrl,
                    lastUpdateTime = time,
                    bookmarkedAt = if (saved) time else null,
                    isEditable = item.isEditable,
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

fun clearIosHistory(completion: (String) -> Unit) = iosRequest(completion) {
    withIosDatabase { db ->
        db.transaction { db.eventQueries.clearAll() }
        db.libraryResponse()
    }
}

private val downloadFilename = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.m4a$")

internal fun MuzzaDatabase.markDownload(item: IosMediaItem, filename: String) {
    require(downloadFilename.matches(filename)) { "Invalid download filename." }
    transaction {
        saveSong(item)
        val song = songQueries.selectById(item.id).executeAsOne().toModel()
        require(!song.isLocal) { "A local song cannot be downloaded." }
        require(song.dateDownload == null) { "This song is already downloaded." }
        songQueries.upsert(song.copy(localPath = filename, dateDownload = now()).toDb())
    }
}

internal fun MuzzaDatabase.clearDownload(id: String) {
    transaction {
        val song = songQueries.selectById(id).executeAsOneOrNull()?.toModel()
            ?: throw IllegalArgumentException("Song not found.")
        require(song.dateDownload != null && !song.isLocal) { "Song is not downloaded." }
        songQueries.upsert(song.copy(localPath = null, dateDownload = null).toDb())
    }
}

fun fetchIosDownload(id: String, completion: (String) -> Unit) = iosRequest(completion) {
    require(id.isNotBlank()) { "Missing song identifier." }
    withIosDatabase { db ->
        val song = db.songQueries.selectById(id).executeAsOneOrNull()?.toModel()
        if (song?.dateDownload == null) {
            IosBridgeResponse()
        } else {
            check(!song.localPath.isNullOrBlank()) { "The downloaded audio has no file path." }
            IosBridgeResponse(items = listOf(db.songToIosMediaItem(song)))
        }
    }
}

fun markIosDownload(json: String, filename: String, completion: (String) -> Unit) = iosRequest(completion) {
    val item = iosJson.decodeFromString<IosMediaItem>(json)
    withIosDatabase { db ->
        db.markDownload(item, filename)
        db.libraryResponse()
    }
}

fun clearIosDownload(id: String, completion: (String) -> Unit) = iosRequest(completion) {
    withIosDatabase { db ->
        db.clearDownload(id)
        db.libraryResponse()
    }
}

fun fetchIosLocalPlaylist(id: String, completion: (String) -> Unit) = iosRequest(completion) {
    withIosDatabase { it.localPlaylistResponse(id) }
}

fun fetchIosLikedPlaylist(completion: (String) -> Unit) = iosRequest(completion) {
    withIosDatabase { db ->
        IosBridgeResponse(
            items = db.songQueries.selectLiked().executeAsList()
                .map { db.songToIosMediaItem(it.toModel()) }
        )
    }
}

fun createIosPlaylist(name: String, syncToYouTube: Boolean, completion: (String) -> Unit) = iosRequest(completion) {
    val title = name.trim()
    require(title.isNotEmpty() && title.length <= 120) { "Enter a playlist name of up to 120 characters." }
    val browseId = if (syncToYouTube && !YouTube.cookie.isNullOrBlank() && YouTube.useLoginForBrowse) {
        withContext(Dispatchers.Default) { YouTube.createPlaylist(title) }.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("YouTube did not return a playlist identifier.")
    } else {
        null
    }
    withIosDatabase { db ->
        val time = now()
        val playlist = PlaylistEntity(
            id = browseId ?: "LP_${NSUUID().UUIDString}",
            name = title,
            browseId = browseId,
            createdAt = time,
            lastUpdateTime = time,
            isEditable = true,
            bookmarkedAt = if (browseId != null) time else null,
            isLocal = browseId == null,
        )
        db.playlistQueries.upsert(playlist.toDb())
        db.libraryResponse()
    }
}

internal fun MuzzaDatabase.renameLocalPlaylist(id: String, name: String) {
    val title = name.trim()
    require(title.isNotEmpty() && title.length <= 120) { "Enter a playlist name of up to 120 characters." }
    val playlist = playlistQueries.selectById(id).executeAsOneOrNull()?.toModel()
        ?: throw IllegalArgumentException("Playlist not found.")
    require(playlist.isLocal && playlist.isEditable) { "Playlist cannot be edited." }
    playlistQueries.upsert(playlist.copy(name = title, lastUpdateTime = now()).toDb())
}

fun renameIosPlaylist(id: String, name: String, completion: (String) -> Unit) = iosRequest(completion) {
    withIosDatabase { db ->
        db.renameLocalPlaylist(id, name)
        db.libraryResponse()
    }
}

internal fun MuzzaDatabase.reorderLocalPlaylist(driver: SqlDriver, id: String, entryIds: List<Long>) {
    transaction {
        val playlist = playlistQueries.selectById(id).executeAsOneOrNull()
            ?: throw IllegalArgumentException("Playlist not found.")
        require(playlist.isLocal != 0L && playlist.isEditable != 0L) { "Playlist cannot be edited." }
        val entries = playlistSongMapQueries.selectByPlaylist(id).executeAsList()
        val selected = entryIds.toSet()
        require(selected.size == entryIds.size && entries.map { it.id }.containsAll(selected)) {
            "The playlist order is invalid."
        }
        val ordered = entryIds.iterator()
        entries.map { if (it.id in selected) ordered.next() else it.id }.forEachIndexed { index, entryId ->
            driver.execute(null, "UPDATE playlistSongMap SET position = ? WHERE id = ? AND playlistId = ?", 3) {
                bindLong(0, index.toLong())
                bindLong(1, entryId)
                bindString(2, id)
            }
        }
    }
    driver.notifyListeners("playlistSongMap")
}

fun reorderIosPlaylist(id: String, entriesJson: String, completion: (String) -> Unit) = iosRequest(completion) {
    val entries = iosJson.decodeFromString<List<Long>>(entriesJson)
    withIosDatabase { db ->
        db.reorderLocalPlaylist(iosDriver, id, entries)
        db.localPlaylistResponse(id)
    }
}

fun addIosSongToPlaylist(
    id: String,
    json: String,
    allowDuplicate: Boolean,
    completion: (String) -> Unit,
) = iosRequest(completion) {
    val item = iosJson.decodeFromString<IosMediaItem>(json)
    val playlist = withIosDatabase { db ->
        val playlist = db.playlistQueries.selectById(id).executeAsOneOrNull()?.toModel()
            ?: db.playlistQueries.selectAll().executeAsList().firstOrNull { it.browseId == id }?.toModel()
            ?: throw IllegalArgumentException("Playlist not found.")
        require(playlist.isEditable) { "Playlist cannot be edited." }
        playlist
    }
    if (!playlist.isLocal) {
        check(!YouTube.cookie.isNullOrBlank() && YouTube.useLoginForBrowse) {
            "Sign in to YouTube Music to add songs to this playlist."
        }
        YouTube.addToPlaylist(playlist.browseId ?: playlist.id, item.id).getOrThrow()
        withIosDatabase { it.libraryResponse() }
    } else {
        withIosDatabase { db ->
            db.transaction {
                require(playlist.isLocal) { "Playlist cannot be edited." }
                val songs = db.playlistSongMapQueries.selectByPlaylist(id).executeAsList()
                if (!allowDuplicate && songs.any { it.songId == item.id }) throw IosDuplicateSongException()
                db.saveSong(item)
                val position = (songs.maxOfOrNull { it.position } ?: -1L) + 1
                db.playlistSongMapQueries.insert(id, item.id, position)
            }
            db.libraryResponse()
        }
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
