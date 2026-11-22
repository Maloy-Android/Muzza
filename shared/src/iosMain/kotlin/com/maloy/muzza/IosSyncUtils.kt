package com.maloy.muzza

import com.maloy.innertube.YouTube
import com.maloy.muzza.data.db.toDb
import com.maloy.muzza.data.db.toModel
import com.maloy.muzza.db.MuzzaDatabase
import com.maloy.muzza.domain.model.AlbumEntity
import com.maloy.muzza.domain.model.ArtistEntity
import com.maloy.muzza.domain.model.PlaylistEntity

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
            val next = page.continuation?.takeIf(String::isNotBlank) ?: break
            check(seenContinuations.add(next)) { "YouTube returned a repeated liked songs continuation." }
            continuation = next
        }
    }.distinctBy { it.id }.reversed()

    withIosDatabase { db ->
        db.transaction {
            val remoteIDs = remoteItems.mapTo(mutableSetOf()) { it.id }
            db.songQueries.selectLiked().executeAsList().forEach { existing ->
                if (existing.id !in remoteIDs) {
                    db.saveSong(db.songToIosMediaItem(existing.toModel()), liked = false)
                }
            }
            remoteItems.forEach { item -> db.saveSong(item, liked = true) }
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
    val syncedItems = mutableMapOf<String, List<IosMediaItem>>()

    for ((category, kind) in categories) {
        val items = buildList {
            var continuation: String? = null
            val seenContinuations = mutableSetOf<String>()
            while (true) {
                val page = fetchIosYouTubeLibraryPage(category, hideExplicit = false, continuation = continuation)
                addAll(page.items.filter { it.kind == kind && (category != "playlists" || it.id !in setOf("LM", "SE")) })
                val next = page.continuation?.takeIf(String::isNotBlank) ?: break
                check(seenContinuations.add(next)) { "YouTube returned a repeated $category continuation." }
                continuation = next
            }
        }.distinctBy { it.id }.reversed()
        syncedItems[category] = items
    }

    withIosDatabase { db ->
        db.transaction {
            categories.forEach { (category, _) ->
                syncedItems.getValue(category).forEach { item ->
                    db.saveIosLibraryCollection(item, category)
                }
            }
            val time = now()
            val syncedAlbums = syncedItems.getValue("albums").mapTo(mutableSetOf()) { it.id }
            db.albumQueries.selectAll().executeAsList()
                .map { it.toModel() }
                .filter { it.bookmarkedAt != null && it.id !in syncedAlbums }
                .forEach { db.albumQueries.upsert(it.copy(bookmarkedAt = null, lastUpdateTime = time).toDb()) }

            val syncedArtists = syncedItems.getValue("artists").mapTo(mutableSetOf()) { it.id }
            db.artistQueries.selectAll().executeAsList()
                .map { it.toModel() }
                .filter { it.bookmarkedAt != null && it.id !in syncedArtists }
                .forEach { db.artistQueries.upsert(it.copy(bookmarkedAt = null, lastUpdateTime = time).toDb()) }

            val syncedPlaylists = syncedItems.getValue("playlists").mapTo(mutableSetOf()) { it.id }
            db.playlistQueries.selectAll().executeAsList()
                .map { it.toModel() }
                .filter { !it.isLocal && it.bookmarkedAt != null && it.id !in syncedPlaylists }
                .forEach { db.playlistQueries.upsert(it.copy(bookmarkedAt = null, lastUpdateTime = time).toDb()) }
        }
        db.libraryResponse()
    }
}

private fun MuzzaDatabase.saveIosLibraryCollection(item: IosMediaItem, category: String) {
    val time = now()
    when (category) {
        "albums" -> {
            val existing = albumQueries.selectById(item.id).executeAsOneOrNull()?.toModel()
            val album = (existing ?: AlbumEntity(
                id = item.id,
                title = item.title,
                songCount = 0,
                duration = 0,
                lastUpdateTime = time,
            )).copy(
                title = item.title,
                thumbnailUrl = item.thumbnail.takeIf(String::isNotBlank) ?: existing?.thumbnailUrl,
                lastUpdateTime = time,
                bookmarkedAt = time,
            )
            albumQueries.upsert(album.toDb())
        }
        "artists" -> {
            val existing = artistQueries.selectById(item.id).executeAsOneOrNull()?.toModel()
            val artist = (existing ?: ArtistEntity(
                id = item.id,
                name = item.title,
                lastUpdateTime = time,
            )).copy(
                name = item.title,
                thumbnailUrl = item.thumbnail.takeIf(String::isNotBlank) ?: existing?.thumbnailUrl,
                lastUpdateTime = time,
                bookmarkedAt = time,
                isProfile = item.artists.firstOrNull()?.isProfile == true,
            )
            artistQueries.upsert(artist.toDb())
        }
        "playlists" -> {
            val existing = playlistQueries.selectById(item.id).executeAsOneOrNull()?.toModel()
            val playlist = (existing ?: PlaylistEntity(
                id = item.id,
                name = item.title,
                browseId = item.id,
                isEditable = item.isEditable,
                isLocal = false,
            )).copy(
                name = item.title,
                thumbnailUrl = item.thumbnail.takeIf(String::isNotBlank) ?: existing?.thumbnailUrl,
                lastUpdateTime = time,
                bookmarkedAt = time,
                isEditable = item.isEditable,
            )
            playlistQueries.upsert(playlist.toDb())
        }
        else -> throw IllegalArgumentException("Unsupported library category.")
    }
}
