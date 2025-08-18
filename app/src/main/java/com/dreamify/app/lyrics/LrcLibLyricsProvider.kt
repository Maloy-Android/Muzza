package com.dreamify.app.lyrics

import android.content.Context
import com.dreamify.lrclib.LrcLib
import com.dreamify.app.constants.EnableLrcLibKey
import com.dreamify.app.utils.dataStore
import com.dreamify.app.utils.get

/**
 * Source: https://github.com/Malopieds/Dreamify
 */
object LrcLibLyricsProvider : LyricsProvider {
    override val name = "LrcLib"

    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableLrcLibKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = LrcLib.getLyrics(title, artist, duration)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        LrcLib.getAllLyrics(title, artist, duration, null, callback)
    }
}
