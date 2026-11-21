package com.maloy.muzza

import com.maloy.kugou.KuGou
import com.maloy.lrclib.LrcLib
import com.maloy.muzza.db.Lyrics
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.decodeFromString
import kotlin.math.abs

fun fetchIosLyrics(json: String, durationSeconds: Int, completion: (String) -> Unit) = iosRequest(completion) {
    val item = iosJson.decodeFromString<IosMediaItem>(json)
    require(item.kind == "song" && item.id.isNotBlank()) { "Invalid song data." }
    val cached = withIosDatabase { db ->
        db.lyricsQueries.selectById(item.id).executeAsOneOrNull()?.syncedLyrics
    }
    if (!cached.isNullOrBlank()) {
        return@iosRequest IosBridgeResponse(lyrics = cached)
    }

    val artist = item.subtitle.takeIf { it.isNotBlank() && it != "Song" }
        ?: throw IllegalStateException("The artist is required to find lyrics.")
    val duration = durationSeconds.takeIf { it > 0 } ?: item.duration ?: -1
    val lrcResult = LrcLib.lyrics(artist, item.title)
    lrcResult.exceptionOrNull()?.let { if (it is CancellationException) throw it }
    val tracks = lrcResult.getOrNull().orEmpty()
    val candidate = if (duration > 0) {
        tracks.minByOrNull { abs(it.duration - duration) }?.takeIf { abs(it.duration - duration) <= 5 }
    } else {
        tracks.firstOrNull()
    }
    val matchedLyrics = candidate?.syncedLyrics?.takeIf(String::isNotBlank)
    val kugouResult = if (matchedLyrics == null) {
        KuGou.getLyrics(item.title, artist, duration)
    } else {
        null
    }
    kugouResult?.exceptionOrNull()?.let { if (it is CancellationException) throw it }
    val lyrics = (matchedLyrics ?: kugouResult?.getOrNull())?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: throw IllegalStateException(
            listOfNotNull(
                lrcResult.exceptionOrNull()?.message,
                kugouResult?.exceptionOrNull()?.message,
            ).joinToString("; ").ifBlank { "No synchronized lyrics were found." },
        )
    withIosDatabase { db ->
        db.lyricsQueries.upsert(Lyrics(item.id, lyrics = null, syncedLyrics = lyrics))
    }
    IosBridgeResponse(lyrics = lyrics)
}
