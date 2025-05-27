package com.maloy.muzza.viewmodels

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import com.maloy.muzza.MainActivity
import com.maloy.muzza.R
import com.maloy.muzza.constants.ScannerSensitivity
import com.maloy.muzza.db.InternalDatabase
import com.maloy.muzza.db.MusicDatabase
import com.maloy.muzza.db.entities.ArtistEntity
import com.maloy.muzza.db.entities.Song
import com.maloy.muzza.db.entities.SongEntity
import com.maloy.muzza.extensions.div
import com.maloy.muzza.extensions.tryOrNull
import com.maloy.muzza.extensions.zipInputStream
import com.maloy.muzza.extensions.zipOutputStream
import com.maloy.muzza.playback.MusicService
import com.maloy.muzza.playback.MusicService.Companion.PERSISTENT_QUEUE_FILE
import com.maloy.muzza.ui.utils.compareSong
import com.maloy.muzza.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
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
                    FileInputStream(database.openHelper.writableDatabase.path).use { inputStream ->
                        outputStream.putNextEntry(ZipEntry(InternalDatabase.DB_NAME))
                        inputStream.copyTo(outputStream)
                    }
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
            context.applicationContext.contentResolver.openInputStream(uri)?.use {
                it.zipInputStream().use { inputStream ->
                    var entry = tryOrNull { inputStream.nextEntry }
                    while (entry != null) {
                        when (entry.name) {
                            SETTINGS_FILENAME -> {
                                (context.filesDir / "datastore" / SETTINGS_FILENAME).outputStream().use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }

                            InternalDatabase.DB_NAME -> {
                                runBlocking(Dispatchers.IO) {
                                    database.checkpoint()
                                }
                                database.close()
                                FileOutputStream(database.openHelper.writableDatabase.path).use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                        }
                        entry = tryOrNull { inputStream.nextEntry }
                    }
                }
            }
            context.stopService(Intent(context, MusicService::class.java))
            context.filesDir.resolve(PERSISTENT_QUEUE_FILE).delete()
            context.startActivity(Intent(context, MainActivity::class.java))
            exitProcess(0)
        }.onFailure {
            reportException(it)
            Toast.makeText(context, R.string.restore_failed, Toast.LENGTH_SHORT).show()
        }
    }

    fun loadM3u(
        context: Context,
        uri: Uri,
        matchStrength: ScannerSensitivity = ScannerSensitivity.LEVEL_2
    ): Pair<ArrayList<Song>, ArrayList<String>> {
        val songs = ArrayList<Song>()
        val rejectedSongs = ArrayList<String>()

        runCatching {
            context.applicationContext.contentResolver.openInputStream(uri)?.use { stream ->
                val lines = stream.readLines()
                if (lines.first().startsWith("#EXTM3U")) {
                    lines.forEachIndexed { index, rawLine ->
                        if (rawLine.startsWith("#EXTINF:")) {
                            val artists =
                                rawLine.substringAfter("#EXTINF:").substringAfter(',').substringBefore(" - ").split(';')
                            val title = rawLine.substringAfter("#EXTINF:").substringAfter(',').substringAfter(" - ")

                            val mockSong = Song(
                                song = SongEntity(
                                    id = "",
                                    title = title,
                                    isLocal = true,
                                    localPath = if (index + 1 < lines.size) lines[index + 1] else ""
                                ),
                                artists = artists.map { ArtistEntity("", it) },
                            )
                            val matches = database.searchSongs(title)
                            val oldSize = songs.size
                            runBlocking {
                                matches.first().forEach {
                                    if (compareSong(mockSong, it, matchStrength = matchStrength)) {
                                        songs.add(it)
                                    }
                                }
                            }

                            if (oldSize == songs.size) {
                                rejectedSongs.add(rawLine)
                            }
                        }
                    }
                }
            }
        }.onFailure {
            reportException(it)
            Toast.makeText(context, R.string.m3u_import_failed, Toast.LENGTH_SHORT).show()
        }
        return Pair(songs, rejectedSongs)
    }
    private fun InputStream.readLines(): List<String> {
        return this.bufferedReader().useLines { it.toList() }
    }

    companion object {
        const val SETTINGS_FILENAME = "settings.preferences_pb"
    }
}
