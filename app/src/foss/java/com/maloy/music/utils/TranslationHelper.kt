package com.maloy.music.utils

import com.maloy.music.db.entities.LyricsEntity

object TranslationHelper {
    suspend fun translate(lyrics: LyricsEntity): LyricsEntity = lyrics
    suspend fun clearModels() {}
}