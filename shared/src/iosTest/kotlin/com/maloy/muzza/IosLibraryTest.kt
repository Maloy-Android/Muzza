@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.maloy.muzza

import app.cash.sqldelight.driver.native.inMemoryDriver
import com.maloy.muzza.data.db.createIosDatabaseDriver
import com.maloy.muzza.data.db.toDb
import com.maloy.muzza.db.MuzzaDatabase
import com.maloy.muzza.domain.model.PlaylistEntity
import com.maloy.muzza.domain.model.SongEntity
import co.touchlab.sqliter.DatabaseFileContext
import co.touchlab.sqliter.databaseFileExists
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosLibraryTest {
    private val item = IosMediaItem(
        id = "song-1",
        title = "Song",
        subtitle = "Artist",
        thumbnail = "https://example.org/cover",
        kind = "song",
    )

    @Test
    fun likesPersistAndKeepExistingSongData() {
        val name = "muzza-library-test-${NSUUID().UUIDString}.db"
        try {
            val first = createIosDatabaseDriver(name)
            try {
                val db = MuzzaDatabase(first)
                db.songQueries.upsert(SongEntity(id = item.id, title = "Old title", totalPlayTime = 42).toDb())
                db.saveSong(item, liked = true)
            } finally {
                first.close()
            }

            val second = createIosDatabaseDriver(name)
            try {
                val db = MuzzaDatabase(second)
                val saved = db.songQueries.selectById(item.id).executeAsOne()
                assertEquals(1L, saved.liked)
                assertEquals(42L, saved.totalPlayTime)
                assertEquals("Artist", saved.artistName)
                assertEquals(listOf(item.id), db.libraryResponse().items.map { it.id })
                db.saveSong(item, liked = false)
                assertEquals(0L, db.songQueries.selectById(item.id).executeAsOne().liked)
                assertTrue(db.libraryResponse().items.isEmpty())
            } finally {
                second.close()
            }
        } finally {
            DatabaseFileContext.deleteDatabase(name)
            check(!DatabaseFileContext.databaseFileExists(name)) { "Could not remove the test database." }
        }
    }

    @Test
    fun failedImportRollsBackAllSongs() {
        val driver = inMemoryDriver(MuzzaDatabase.Schema)
        try {
            val db = MuzzaDatabase(driver)
            assertFailsWith<IllegalArgumentException> {
                db.transaction {
                    db.saveSong(item, liked = true)
                    db.saveSong(item.copy(id = ""), liked = true)
                }
            }
            assertNull(db.songQueries.selectById(item.id).executeAsOneOrNull())
        } finally {
            driver.close()
        }
    }

    @Test
    fun localPlaylistReturnsItsStoredSongs() {
        val driver = inMemoryDriver(MuzzaDatabase.Schema)
        try {
            val db = MuzzaDatabase(driver)
            db.playlistQueries.upsert(PlaylistEntity(id = "LP_test", name = "Test", isLocal = true).toDb())
            db.saveSong(item)
            db.playlistSongMapQueries.insert("LP_test", item.id, 0)
            db.playlistSongMapQueries.insert("LP_test", item.id, 1)
            val entries = db.localPlaylistResponse("LP_test").items
            assertEquals(listOf(item.id, item.id), entries.map { it.id })
            val firstEntry = requireNotNull(entries.first().entryId)
            val secondEntry = requireNotNull(entries.last().entryId)
            db.removeLocalPlaylistEntry(driver, "LP_test", firstEntry)
            assertEquals(listOf(secondEntry), db.localPlaylistResponse("LP_test").items.map { it.entryId })
            assertEquals(listOf("LP_test"), db.libraryResponse().playlists.map { it.id })
        } finally {
            driver.close()
        }
    }
}
