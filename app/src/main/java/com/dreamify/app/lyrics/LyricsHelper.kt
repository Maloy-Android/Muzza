package com.dreamify.app.lyrics

import android.content.Context
import android.os.Build
import android.util.LruCache
import com.dreamify.app.constants.PreferredLyricsProvider
import com.dreamify.app.constants.PreferredLyricsProviderKey
import com.dreamify.app.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.dreamify.app.extensions.toEnum
import com.dreamify.app.models.MediaMetadata
import com.dreamify.app.utils.dataStore
import com.dreamify.app.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class LyricsHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val PREFER_LOCAL_LYRIC = true
    private var lyricsProviders = listOf(LrcLibLyricsProvider,KuGouLyricsProvider, YouTubeSubtitleLyricsProvider, YouTubeLyricsProvider)
    val preferred = context.dataStore.data.map {
        it[PreferredLyricsProviderKey].toEnum(PreferredLyricsProvider.LRCLIB)
    }.distinctUntilChanged()
        .map {
            lyricsProviders = if (it == PreferredLyricsProvider.LRCLIB) {
                listOf(LrcLibLyricsProvider,KuGouLyricsProvider, YouTubeSubtitleLyricsProvider, YouTubeLyricsProvider)
            } else {
                listOf(KuGouLyricsProvider, LrcLibLyricsProvider, YouTubeSubtitleLyricsProvider, YouTubeLyricsProvider)
            }
        }
    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)

    suspend fun getLyrics(mediaMetadata: MediaMetadata): String {
        val cached = cache.get(mediaMetadata.id)?.firstOrNull()
        if (cached != null) {
            return cached.lyrics
        }
        val localLyrics = getLocalLyrics(mediaMetadata)
        var remoteLyrics: String?

        // fallback to secondary provider when primary is unavailable
        if (PREFER_LOCAL_LYRIC) {
            if (localLyrics != null) {
                return localLyrics
            }

            // "lazy eval" the remote lyrics cuz it is laughably slow
            remoteLyrics= getRemoteLyrics(mediaMetadata)
            if (remoteLyrics != null) {
                return remoteLyrics
            }
        } else {
            remoteLyrics= getRemoteLyrics(mediaMetadata)
            if (remoteLyrics != null) {
                return remoteLyrics
            } else if (localLyrics != null) {
                return localLyrics
            }

        }

        return LYRICS_NOT_FOUND
    }

    /**
     * Lookup lyrics from remote providers
     */
    private suspend fun getRemoteLyrics(mediaMetadata: MediaMetadata): String? {
        lyricsProviders.forEach { provider ->
            if (provider.isEnabled(context)) {
                provider.getLyrics(
                    mediaMetadata.id,
                    mediaMetadata.title,
                    mediaMetadata.artists.joinToString { it.name },
                    mediaMetadata.duration
                ).onSuccess { lyrics ->
                    return lyrics
                }.onFailure {
                    reportException(it)
                }
            }
        }
        return null
    }

    /**
     * Lookup lyrics from local disk (.lrc) file
     */
    private suspend fun getLocalLyrics(mediaMetadata: MediaMetadata): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            throw Exception("Local lyrics are not supported below SDK 26 (Oreo)")
        }
        if (LocalLyricsProvider.isEnabled(context)) {
            LocalLyricsProvider.getLyrics(
                mediaMetadata.id,
                "" + mediaMetadata.localPath, // title used as path
                mediaMetadata.artists.joinToString { it.name },
                mediaMetadata.duration
            ).onSuccess { lyrics ->
                return lyrics
            }.onFailure {
                reportException(it)
            }
        }
        return null
    }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        callback: (LyricsResult) -> Unit,
    ) {
        val cacheKey = "$songArtists-$songTitle".replace(" ", "")
        cache.get(cacheKey)?.let { results ->
            results.forEach {
                callback(it)
            }
            return
        }
        val allResult = mutableListOf<LyricsResult>()
        lyricsProviders.forEach { provider ->
            if (provider.isEnabled(context)) {
                provider.getAllLyrics(mediaId, songTitle, songArtists, duration) { lyrics ->
                    val result = LyricsResult(provider.name, lyrics)
                    allResult += result
                    callback(result)
                }
            }
        }
        cache.put(cacheKey, allResult)
    }

    companion object {
        private const val MAX_CACHE_SIZE = 3
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)
