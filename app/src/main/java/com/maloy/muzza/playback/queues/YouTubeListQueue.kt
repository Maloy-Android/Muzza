package com.maloy.muzza.playback.queues

import com.maloy.innertube.YouTube
import com.maloy.innertube.models.SongItem
import com.maloy.innertube.utils.completed
import com.maloy.muzza.extensions.toMediaItemWithPlaylist
import com.maloy.muzza.playback.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ListQueuePlaylist(
    private val playlistId: String,
    private val playlistTitle: String,
    private val coroutineScope: CoroutineScope,
    private val playerConnection: PlayerConnection,
    private val pageSize: Int = 25,
    private val addDelay: Long = 50L
) {
    fun play() {
        coroutineScope.launch {
            val initialSongs = loadSongs(playlistId, pageSize)

            if (initialSongs.isNotEmpty()) {
                val queue = createQueue(playlistTitle, initialSongs)
                playerConnection.playQueue(queue)

                coroutineScope.launch {
                    val allSongs = loadAllSongs(playlistId)
                    if (allSongs.size > pageSize) {
                        val remainingSongs = allSongs.drop(pageSize)
                        addSongsToQueue(remainingSongs, playlistId)
                    }
                }
            }
        }
    }

    fun playShuffled() {
        coroutineScope.launch {
            val initialSongs = loadSongs(playlistId, pageSize).shuffled()

            if (initialSongs.isNotEmpty()) {
                val queue = createQueue(playlistTitle, initialSongs)
                playerConnection.playQueue(queue)

                coroutineScope.launch {
                    val allSongs = loadAllSongs(playlistId).shuffled()
                    if (allSongs.size > pageSize) {
                        val remainingSongs = allSongs.drop(pageSize)
                        addSongsToQueue(remainingSongs, playlistId)
                    }
                }
            }
        }
    }

    fun playNext() {
        coroutineScope.launch {
            val initialSongs = loadSongs(playlistId, pageSize)
            if (initialSongs.isNotEmpty()) {
                addSongsToQueueNext(initialSongs, playlistId)
                coroutineScope.launch {
                    val allSongs = loadAllSongs(playlistId)
                    if (allSongs.size > pageSize) {
                        val remainingSongs = allSongs.drop(pageSize)
                        addSongsToQueue(remainingSongs, playlistId)
                    }
                }
            }
        }
    }

    fun addToQueue() {
        coroutineScope.launch {
            val initialSongs = loadSongs(playlistId, pageSize)
            if (initialSongs.isNotEmpty()) {
                addSongsToQueue(initialSongs, playlistId)
                coroutineScope.launch {
                    val allSongs = loadAllSongs(playlistId)
                    if (allSongs.size > pageSize) {
                        val remainingSongs = allSongs.drop(pageSize)
                        addSongsToQueue(remainingSongs, playlistId)
                    }
                }
            }
        }
    }

    private suspend fun loadSongs(playlistId: String, limit: Int): List<SongItem> {
        return withContext(Dispatchers.IO) {
            YouTube.playlist(playlistId)
                .completed()
                .getOrNull()
                ?.songs
                ?.take(limit)
                .orEmpty()
        }
    }

    private suspend fun loadAllSongs(playlistId: String): List<SongItem> {
        return withContext(Dispatchers.IO) {
            YouTube.playlist(playlistId)
                .completed()
                .getOrNull()
                ?.songs
                .orEmpty()
        }
    }

    private fun createQueue(title: String, songs: List<SongItem>): ListQueue {
        return ListQueue(
            title = title,
            items = songs.map { it.toMediaItemWithPlaylist(playlistId) }
        )
    }

    private suspend fun addSongsToQueue(songs: List<SongItem>, playlistId: String) {
        songs.forEach { song ->
            playerConnection.addToQueue(
                song.toMediaItemWithPlaylist(playlistId)
            )
            delay(addDelay)
        }
    }

    private suspend fun addSongsToQueueNext(songs: List<SongItem>, playlistId: String) {
        songs.reversed().forEach { song ->
            playerConnection.playNext(
                song.toMediaItemWithPlaylist(playlistId)
            )
            delay(addDelay)
        }
    }
}