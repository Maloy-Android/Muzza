package com.maloy.muzza.viewmodels

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.room.RoomDatabase
import com.maloy.muzza.MainActivity
import com.maloy.muzza.R
import com.maloy.muzza.db.InternalDatabase
import com.maloy.muzza.db.MusicDatabase
import com.maloy.muzza.extensions.div
import com.maloy.muzza.extensions.tryOrNull
import com.maloy.muzza.extensions.zipInputStream
import com.maloy.muzza.extensions.zipOutputStream
import com.maloy.muzza.playback.MusicService
import com.maloy.muzza.playback.MusicService.Companion.PERSISTENT_QUEUE_FILE
import com.maloy.muzza.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    val database: MusicDatabase,
) : ViewModel() {
    fun backup(context: Context, uri: Uri) {
        runCatching {
            context.applicationContext.contentResolver.openOutputStream(uri)?.use {
                it.buffered().zipOutputStream().use { outputStream ->
                    (context.filesDir / "datastore" / SETTINGS_FILENAME).inputStream().buffered().use { inputStream ->
                        outputStream.putNextEntry(ZipEntry(SETTINGS_FILENAME))
                        inputStream.copyTo(outputStream)
                    }
                    runBlocking(Dispatchers.IO) {
                        database.checkpoint()
                    }
                    val dbVersion = database.openHelper.writableDatabase.version
                    FileInputStream(database.openHelper.writableDatabase.path).use { inputStream ->
                        outputStream.putNextEntry(ZipEntry(InternalDatabase.DB_NAME))
                        inputStream.copyTo(outputStream)
                    }
                    // Self-describing metadata so any future version can tell which schema this
                    // backup was created with, and refuse it gracefully if it is too new.
                    outputStream.putNextEntry(ZipEntry(METADATA_FILENAME))
                    outputStream.write("$KEY_DB_VERSION=$dbVersion\n".toByteArray())
                }
            }
        }.onSuccess {
            Toast.makeText(context, R.string.backup_create_success, Toast.LENGTH_SHORT).show()
        }.onFailure {
            reportException(it)
            Toast.makeText(context, R.string.backup_create_failed, Toast.LENGTH_SHORT).show()
        }
    }

    fun restore(context: Context, uri: Uri) {
        runCatching {
            val tempDbFile = File(context.cacheDir, "restore_${System.currentTimeMillis()}.db")
            var hasDb = false
            var settingsBytes: ByteArray? = null
            var backupDbVersion: Int? = null

            try {
                // 1. Extract the archive to temporary storage without touching live data.
                context.applicationContext.contentResolver.openInputStream(uri)?.use {
                    it.zipInputStream().use { inputStream ->
                        var entry = tryOrNull { inputStream.nextEntry }
                        while (entry != null) {
                            when (entry.name) {
                                SETTINGS_FILENAME -> {
                                    settingsBytes = inputStream.readBytes()
                                }

                                InternalDatabase.DB_NAME -> {
                                    FileOutputStream(tempDbFile).use { output ->
                                        inputStream.copyTo(output)
                                    }
                                    hasDb = true
                                }

                                METADATA_FILENAME -> {
                                    backupDbVersion = parseDbVersion(inputStream.readBytes().decodeToString())
                                }
                            }
                            entry = tryOrNull { inputStream.nextEntry }
                        }
                    }
                }

                // 2. Validate the database can actually be brought up to the current schema
                //    BEFORE overwriting anything. If it can't, we abort and leave the app's
                //    existing data intact instead of crash-looping on next launch.
                if (hasDb) {
                    val currentVersion = database.openHelper.readableDatabase.version
                    if (backupDbVersion != null && backupDbVersion!! > currentVersion) {
                        // A newer backup would require a schema downgrade, which is impossible.
                        throw BackupNewerException()
                    }
                    migrateBackupDatabase(context, tempDbFile)
                }

                // 3. Everything validated -> commit the restore.
                if (settingsBytes != null) {
                    (context.filesDir / "datastore" / SETTINGS_FILENAME).outputStream().use { output ->
                        output.write(settingsBytes)
                    }
                }
                if (hasDb) {
                    val dbPath = database.openHelper.writableDatabase.path!!
                    runBlocking(Dispatchers.IO) {
                        database.checkpoint()
                    }
                    database.close()
                    // Drop stale WAL/SHM so SQLite doesn't replay old journal over the restored file.
                    File("$dbPath-wal").delete()
                    File("$dbPath-shm").delete()
                    FileInputStream(tempDbFile).use { input ->
                        FileOutputStream(dbPath).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            } finally {
                tempDbFile.delete()
                File("${tempDbFile.path}-wal").delete()
                File("${tempDbFile.path}-shm").delete()
                File("${tempDbFile.path}-journal").delete()
            }

            context.stopService(Intent(context, MusicService::class.java))
            context.filesDir.resolve(PERSISTENT_QUEUE_FILE).delete()
            context.startActivity(Intent(context, MainActivity::class.java))
            exitProcess(0)
        }.onFailure { throwable ->
            reportException(
                if (throwable is BackupNewerException)
                    Exception("Restore aborted: backup is from a newer app version", throwable)
                else throwable
            )
            val messageRes = when (throwable) {
                is BackupNewerException -> R.string.restore_backup_newer_version
                else -> R.string.restore_failed
            }
            Toast.makeText(context, messageRes, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Opens the restored database file in an isolated Room instance, forcing Room to run its
     * migrations up to the current schema version. Throws if no valid migration path exists.
     * Uses TRUNCATE journal mode so the whole database stays in a single self-contained file
     * that can then be copied into place.
     */
    private fun migrateBackupDatabase(context: Context, dbFile: File) {
        File("${dbFile.path}-wal").delete()
        File("${dbFile.path}-shm").delete()
        File("${dbFile.path}-journal").delete()
        val validationDb = InternalDatabase.databaseBuilder(context, dbFile.absolutePath)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()
        try {
            // Accessing the writable database triggers open + migration (or throws).
            validationDb.openHelper.writableDatabase
        } finally {
            validationDb.close()
        }
    }

    private fun parseDbVersion(content: String): Int? =
        content.lineSequence()
            .mapNotNull { line ->
                val parts = line.split("=", limit = 2)
                if (parts.size == 2 && parts[0].trim() == KEY_DB_VERSION) {
                    parts[1].trim().toIntOrNull()
                } else null
            }
            .firstOrNull()

    private class BackupNewerException : Exception()

    companion object {
        const val SETTINGS_FILENAME = "settings.preferences_pb"
        const val METADATA_FILENAME = "metadata.txt"
        private const val KEY_DB_VERSION = "db_version"
    }
}
