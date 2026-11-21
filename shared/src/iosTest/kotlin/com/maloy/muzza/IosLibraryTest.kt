@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.maloy.muzza

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.maloy.muzza.data.db.toDb
import com.maloy.muzza.db.MuzzaDatabase
import com.maloy.muzza.domain.model.PlaylistEntity
import com.maloy.muzza.domain.model.SongEntity
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
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
        val path = NSTemporaryDirectory() + "muzza-library-test-${NSUUID().UUIDString}.db"
        try {
            val first = NativeSqliteDriver(MuzzaDatabase.Schema, path)
            try {
                val db = MuzzaDatabase(first)
                db.songQueries.upsert(SongEntity(id = item.id, title = "Old title", totalPlayTime = 42).toDb())
                db.saveSong(item, liked = true)
            } finally {
                first.close()
            }

            val second = NativeSqliteDriver(MuzzaDatabase.Schema, path)
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
            val files = NSFileManager.defaultManager
            listOf(path, "$path-wal", "$path-shm").forEach { file ->
                if (files.fileExistsAtPath(file)) {
                    check(files.removeItemAtPath(file, error = null)) { "Could not remove the test database." }
                }
            }
        }
    }

    @Test
    fun failedImportRollsBackAllSongs() {
        val driver = NativeSqliteDriver(MuzzaDatabase.Schema, ":memory:")
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
        val driver = NativeSqliteDriver(MuzzaDatabase.Schema, ":memory:")
        try {
            val db = MuzzaDatabase(driver)
            db.playlistQueries.upsert(PlaylistEntity(id = "LP_test", name = "Test", isLocal = true).toDb())
            db.saveSong(item)
            db.playlistSongMapQueries.insert("LP_test", item.id, 0)
            assertEquals(listOf(item.id), db.localPlaylistResponse("LP_test").items.map { it.id })
            assertEquals(listOf("LP_test"), db.libraryResponse().playlists.map { it.id })
        } finally {
            driver.close()
        }
    }
}
