@file:Suppress("NAME_SHADOWING")

package com.maloy.muzza.utils

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.maloy.muzza.db.MusicDatabase
import com.maloy.muzza.db.entities.ArtistEntity
import com.maloy.muzza.db.entities.Song
import com.maloy.muzza.db.entities.SongEntity
import com.maloy.muzza.models.toMediaMetadata
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDateTime
import androidx.core.net.toUri

const val sdcardRoot = "/storage/emulated/0/"
var directoryUID = 0
var cachedDirectoryTree: DirectoryTree? = null
var imageCache: LmImageCacheMgr = LmImageCacheMgr()

val projection = arrayOf(
    MediaStore.Audio.Media._ID,
    MediaStore.Audio.Media.DISPLAY_NAME,
    MediaStore.Audio.Media.TITLE,
    MediaStore.Audio.Media.DURATION,
    MediaStore.Audio.Media.ARTIST,
    MediaStore.Audio.Media.ARTIST_ID,
    MediaStore.Audio.Media.ALBUM,
    MediaStore.Audio.Media.ALBUM_ID,
    MediaStore.Audio.Media.RELATIVE_PATH,
    MediaStore.Audio.Media.DATA
)

class DirectoryTree(path: String) {
    var currentDir = path
    var parent: String = ""
    var subdirs = ArrayList<DirectoryTree>()
    var files = ArrayList<Song>()

    init {
        directoryUID++
    }

    constructor(path: String, files: ArrayList<Song>) : this(path) {
        this.files = files
    }

    fun insert(path: String, song: Song) {
        if (path.indexOf('/') == -1) {
            files.add(song)
            return
        }
        var tmppath = path
        if (path[path.length - 1] == '/') {
            tmppath = path.substring(0, path.length - 1)
        }
        val subdirPath = tmppath.substringBefore('/')
        var existingSubdir: DirectoryTree? = null
        subdirs.forEach { subdir ->
            if (subdir.currentDir == subdirPath) {
                existingSubdir = subdir
                return@forEach
            }
        }
        if (existingSubdir == null) {
            val tree = DirectoryTree(subdirPath)
            tree.parent = "$parent/$currentDir"
            tree.insert(tmppath.substringAfter('/'), song)
            subdirs.add(tree)

        } else {
            existingSubdir.insert(tmppath.substringAfter('/'), song)
        }
    }

    fun toList(): List<Song> {
        val songs = ArrayList<Song>()
        fun traverseHELPME(tree: DirectoryTree, result: ArrayList<Song>) {
            result.addAll(tree.files)
            tree.subdirs.forEach { traverseHELPME(it, result) }
        }
        traverseHELPME(this, songs)
        return songs
    }

    private fun getSubdirsRecursive(it: DirectoryTree, result: ArrayList<DirectoryTree>) {
        if (it.files.isNotEmpty()) {
            result.add(DirectoryTree(it.currentDir, it.files))
        }

        if (it.subdirs.isNotEmpty()) {
            it.subdirs.forEach { getSubdirsRecursive(it, result) }
        }
    }

    fun copy(
        currentDir: String = this.currentDir,
        subdirs: List<DirectoryTree> = this.subdirs,
        files: List<Song> = this.files
    ): DirectoryTree {
        return DirectoryTree(currentDir).apply {
            this.subdirs = subdirs.toMutableList() as ArrayList<DirectoryTree>
            this.files = files.toMutableList() as ArrayList<Song>
        }
    }
}

fun scanLocal(context: Context) =
    scanLocal(context, arrayListOf(""))


@OptIn(ExperimentalCoroutinesApi::class)
fun scanLocal(
    context: Context,
    scanPaths: ArrayList<String> = arrayListOf("")
): MutableStateFlow<DirectoryTree> {
    val newDirectoryStructure = DirectoryTree(sdcardRoot)

    val contentResolver: ContentResolver = context.contentResolver
    val cursor = contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DATA} LIKE ?",
        scanPaths.map { "$sdcardRoot$it%" }.toTypedArray(),
        null
    )

    val scannerJobs = ArrayList<Deferred<Song?>>()
    runBlocking {
        cursor?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn)
                val duration = cursor.getInt(durationColumn)
                val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                val data = cursor.getString(dataColumn) ?: ""

                scannerJobs.add(async(Dispatchers.IO) {
                    extractSongMetadata(id, title, artist, duration, data)
                })
            }
        }

        scannerJobs.awaitAll()
    }
    scannerJobs.forEach { it ->
        val song = it.getCompleted()
        song?.let {
            it.song.localPath?.let { path ->
                newDirectoryStructure.insert(
                    path.substringAfter(sdcardRoot), it
                )
            }
        }
    }

    cachedDirectoryTree = newDirectoryStructure
    return MutableStateFlow(newDirectoryStructure)
}

private fun extractSongMetadata(
    mediaStoreId: Long,
    title: String,
    artist: String,
    duration: Int,
    filePath: String
): Song? {
    return try {
        val fileId = "LOCAL_$mediaStoreId"
        val contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            .buildUpon()
            .appendPath(mediaStoreId.toString())
            .build()

        val artistId = "ARTIST_${(artist).hashCode()}"
        val artists = listOf(ArtistEntity(artistId, artist))

        val songEntity = SongEntity(
            id = fileId,
            title = title,
            duration = (duration / 1000),
            artistName = artist,
            albumId = null,
            albumName = null,
            isLocal = true,
            liked = false,
            inLibrary = LocalDateTime.now(),
            localPath = filePath,
            contentUri = contentUri.toString()
        )

        Song(songEntity, artists)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun syncDB(
    database: MusicDatabase,
    directoryStructure: List<Song>,
) {
    runBlocking(Dispatchers.IO) {
        val existingSongs = database.allLocalSongsData().first()
            .associateBy { it.song.localPath }
        directoryStructure.forEach { scannedSong ->
            val localPath = scannedSong.song.localPath ?: return@forEach
            val existingSong = existingSongs[localPath]
            if (existingSong != null) {
                val updatedSong = scannedSong.song.copy(
                    id = existingSong.song.id,
                    liked = existingSong.song.liked,
                    inLibrary = existingSong.song.inLibrary,
                    totalPlayTime = existingSong.song.totalPlayTime,
                    contentUri = scannedSong.song.contentUri ?: existingSong.song.contentUri
                )
                database.update(updatedSong)
            } else {
                database.insert(scannedSong.toMediaMetadata())
            }
        }
        val scannedPaths = directoryStructure.mapNotNull { it.song.localPath }.toSet()
        existingSongs.keys.forEach { path ->
            if (path != null && path !in scannedPaths) {
                database.delete(existingSongs[path]!!.song)
            }
        }
    }
}

fun getPlaybackUriForLocalSong(context: Context, songEntity: SongEntity): Uri? {
    return try {
        if (!songEntity.contentUri.isNullOrEmpty()) {
            return songEntity.contentUri.toUri()
        }
        val localPath = songEntity.localPath ?: return null
        val contentResolver = context.contentResolver
        val projection = arrayOf(MediaStore.Audio.Media._ID)
        val selection = "${MediaStore.Audio.Media.DATA} = ?"
        val selectionArgs = arrayOf(localPath)
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val id = cursor.getLong(idColumn)
                return Uri.withAppendedPath(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )
            }
        }
        try {
            val authority = "${context.packageName}.fileprovider"
            FileProvider.getUriForFile(
                context,
                authority,
                File(localPath)
            )
        } catch (_: Exception) {
            Uri.fromFile(File(localPath))
        }

    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

object CachedBitmap {
    var path: String? = null
    var image: Bitmap? = null
    fun cache(path: String, image: Bitmap?) {
        if (image == null) {
            return
        }

        CachedBitmap.path = path
        CachedBitmap.image = image
        bitmapCache.add(this)
    }
}

var bitmapCache = ArrayList<CachedBitmap>()