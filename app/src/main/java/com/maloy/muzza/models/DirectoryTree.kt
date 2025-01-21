package com.maloy.muzza.models

import com.maloy.muzza.db.entities.Song
import com.maloy.muzza.ui.utils.SCANNER_DEBUG
import timber.log.Timber

/**
 * A tree representation of local audio files
 *
 * @param path root directory start
 */
class DirectoryTree(path: String) {
    companion object {
        const val TAG = "DirectoryTree"
        var directoryUID = 0
    }

    /**
     * Directory name
     */
    private var currentDir = path // file name

    /**
     * Full parent directory path
     */
    private var parent: String = ""

    // folder contents
    private var subdirs = ArrayList<DirectoryTree>()
    private var files = ArrayList<Song>()

    init {
        // increment uid
        directoryUID++
    }

    fun insert(path: String, song: Song) {
//        println("curr path =" + path)

        // add a file
        if (path.indexOf('/') == -1) {
            files.add(song)
            if (SCANNER_DEBUG)
                Timber.tag(TAG).d("Adding song with path: $path")
            return
        }

        // there is still subdirs to process
        var tmpPath = path
        if (path[path.length - 1] == '/') {
            tmpPath = path.substring(0, path.length - 1)
        }

        // the first directory before the .
        val subdirPath = tmpPath.substringBefore('/')

        // create subdirs if they do not exist, then insert
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
            tree.insert(tmpPath.substringAfter('/'), song)
            subdirs.add(tree)

        } else {
            existingSubdir!!.insert(tmpPath.substringAfter('/'), song)
        }
    }


    /**
     * Retrieve a list of all the songs
     */
    fun toList(): List<Song> {
        val songs = ArrayList<Song>()

        fun traverseTree(tree: DirectoryTree, result: ArrayList<Song>) {
            result.addAll(tree.files)
            tree.subdirs.forEach { traverseTree(it, result) }
        }

        traverseTree(this, songs)
        return songs
    }
}