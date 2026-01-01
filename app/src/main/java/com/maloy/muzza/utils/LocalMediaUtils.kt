@file:Suppress("NAME_SHADOWING")

package com.maloy.muzza.utils

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore
import com.maloy.innertube.YouTube
import com.maloy.innertube.models.SongItem
import com.maloy.muzza.db.MusicDatabase
import com.maloy.muzza.db.entities.ArtistEntity
import com.maloy.muzza.db.entities.Song
import com.maloy.muzza.db.entities.SongEntity
import com.maloy.muzza.models.toMediaMetadata
import com.maloy.muzza.constants.ScannerSensitivity
import com.maloy.muzza.models.MediaMetadata
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import java.util.Locale

const val sdcardRoot = "/storage/emulated/0/"
val testScanPaths = arrayListOf("Music")
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
            println(path)
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
    scanLocal(context, testScanPaths)


@OptIn(ExperimentalCoroutinesApi::class)
fun scanLocal(
    context: Context,
    scanPaths: ArrayList<String>
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

    val scannerJobs = ArrayList<Deferred<Song>>()
    runBlocking {
        cursor?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val artistIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIDColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val title = cursor.getString(titleColumn)
                val duration = cursor.getInt(durationColumn)
                val artist = cursor.getString(artistColumn)
                val artistID = cursor.getString(artistIdColumn)
                val albumID = cursor.getString(albumIDColumn)
                val album = cursor.getString(albumColumn)
                val path = cursor.getString(pathColumn)
                fun advancedScan(): Song {
                    val artists = ArrayList<ArtistEntity>()
                    artists.add(ArtistEntity(artistID, artist))
                    return Song(
                        SongEntity(
                            id.toString(),
                            title,
                            (duration / 1000),
                            albumId = albumID,
                            albumName = album,
                            isLocal = true,
                            liked = false,
                            inLibrary = LocalDateTime.now(),
                            localPath = "$sdcardRoot$path$name"
                        ),
                        artists
                    )
                }

                scannerJobs.add(async(Dispatchers.IO) { advancedScan() })
            }
        }

        scannerJobs.awaitAll()
    }
    scannerJobs.forEach { it ->
        val song = it.getCompleted()
        song.song.localPath?.let {
            newDirectoryStructure.insert(
                it.substringAfter(sdcardRoot), song
            )
        }
    }

    cachedDirectoryTree = newDirectoryStructure
    return MutableStateFlow(newDirectoryStructure)
}

fun syncDB(
    database: MusicDatabase,
    directoryStructure: List<Song>,
    matchStrength: ScannerSensitivity,
    strictFileNames: Boolean
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
                    totalPlayTime = existingSong.song.totalPlayTime
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

fun youtubeSongLookup(query: String, songUrl: String?): List<MediaMetadata> {
    val ytmResult = ArrayList<MediaMetadata>()
    runBlocking(Dispatchers.IO) {
        var exactSong: SongItem? = null
        if (songUrl != null) {
            runBlocking(Dispatchers.IO) {
                runCatching {
                    YouTube.queue(listOf(songUrl.substringAfter("/watch?v=").substringBefore("&")))
                }.onSuccess {
                    exactSong = it.getOrNull()?.firstOrNull()
                }.onFailure {
                    reportException(it)
                }
            }
        }
        if (exactSong != null) {
            ytmResult.add(exactSong.toMediaMetadata())
            return@runBlocking
        }
        YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).onSuccess { result ->
            val foundSong = result.items.filter {
                it is SongItem
            }
            ytmResult.addAll(foundSong.map { (it as SongItem).toMediaMetadata() })
        }
    }
    return ytmResult
}

fun compareArtist(a: List<ArtistEntity>, b: List<ArtistEntity>): Boolean {
    if (a.isEmpty() && b.isEmpty()) {
        return true
    } else if (a.isEmpty() || b.isEmpty()) {
        return false
    }
    if (a.size != b.size) {
        return false
    }
    val matchingArtists = a.filter { artist ->
        b.any { it.name.lowercase(Locale.getDefault()) == artist.name.lowercase(Locale.getDefault()) }
    }
    return matchingArtists.size == a.size
}
fun compareSong(
    a: Song,
    b: Song,
    matchStrength: ScannerSensitivity = ScannerSensitivity.LEVEL_2,
    strictFileNames: Boolean = false
): Boolean {
    if (strictFileNames &&
        (a.song.localPath?.substringAfterLast('/') !=
                b.song.localPath?.substringAfterLast('/'))
    ) {
        return false
    }
    fun closeEnough(): Boolean {
        return a.song.localPath == b.song.localPath
    }
    return when (matchStrength) {
        ScannerSensitivity.LEVEL_1 -> a.song.title == b.song.title
        ScannerSensitivity.LEVEL_2 -> closeEnough() || (a.song.title == b.song.title &&
                compareArtist(a.artists, b.artists))

        ScannerSensitivity.LEVEL_3 -> closeEnough() || (a.song.title == b.song.title &&
                compareArtist(a.artists, b.artists))
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