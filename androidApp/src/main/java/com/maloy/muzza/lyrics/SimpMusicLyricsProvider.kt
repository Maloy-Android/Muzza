package com.maloy.muzza.lyrics

import android.content.Context
import com.maloy.muzza.constants.EnableSimpMusicKey
import com.maloy.muzza.lyrics.simpmusic.SimpMusicLyrics
import com.maloy.muzza.utils.dataStore
import com.maloy.muzza.utils.get

object SimpMusicLyricsProvider : LyricsProvider {
    override val name = "SimpMusic"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableSimpMusicKey] ?: true
    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int
    ): Result<String> = SimpMusicLyrics.getLyrics(id,duration)

    suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = SimpMusicLyrics.getLyrics(id, duration)

    suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit,
    ) {
        SimpMusicLyrics.getAllLyrics(id, duration, callback)
    }
}