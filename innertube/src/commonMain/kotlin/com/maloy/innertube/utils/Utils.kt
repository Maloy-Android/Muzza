package com.maloy.innertube.utils

import com.maloy.innertube.YouTube
import com.maloy.innertube.pages.LibraryPage
import com.maloy.innertube.pages.PlaylistPage

suspend fun Result<PlaylistPage>.completed(): Result<PlaylistPage> = runCatching {
    val page = getOrThrow()
    val songs = page.songs.toMutableList()
    var continuation = page.songsContinuation
    val seenContinuations = mutableSetOf<String>()
    var requestCount = 0
    val maxRequests = 50
    var consecutiveEmptyResponses = 0

    while (continuation != null && requestCount < maxRequests) {
        if (continuation in seenContinuations) break
        seenContinuations.add(continuation)
        requestCount++
        val continuationPage = YouTube.playlistContinuation(continuation).getOrNull() ?: break
        if (continuationPage.songs.isEmpty()) {
            consecutiveEmptyResponses++
            if (consecutiveEmptyResponses >= 2) break
        } else {
            consecutiveEmptyResponses = 0
            songs += continuationPage.songs
        }
        continuation = continuationPage.continuation
    }
    PlaylistPage(playlist = page.playlist, songs = songs, songsContinuation = null, continuation = page.continuation)
}

suspend fun Result<LibraryPage>.completed(): Result<LibraryPage> = runCatching {
    val page = getOrThrow()
    val items = page.items.toMutableList()
    var continuation = page.continuation
    val seenContinuations = mutableSetOf<String>()
    var requestCount = 0
    val maxRequests = 50
    var consecutiveEmptyResponses = 0

    while (continuation != null && requestCount < maxRequests) {
        if (continuation in seenContinuations) break
        seenContinuations.add(continuation)
        requestCount++
        val continuationPage = YouTube.libraryContinuation(continuation).getOrNull() ?: break
        if (continuationPage.items.isEmpty()) {
            consecutiveEmptyResponses++
            if (consecutiveEmptyResponses >= 2) break
        } else {
            consecutiveEmptyResponses = 0
            items += continuationPage.items
        }
        continuation = continuationPage.continuation
    }
    LibraryPage(items = items, continuation = null)
}

fun ByteArray.toHex(): String =
    joinToString(separator = "") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }

fun sha1(str: String): String {
    val bytes = str.encodeToByteArray()
    val bitLength = bytes.size.toLong() * 8
    val paddedLength = ((bytes.size + 9 + 63) / 64) * 64
    val padded = ByteArray(paddedLength)
    bytes.copyInto(padded)
    padded[bytes.size] = 0x80.toByte()
    for (i in 0 until 8) {
        padded[paddedLength - 1 - i] =
            ((bitLength ushr (i * 8)) and 0xff).toByte()
    }

    var h0 = 0x67452301
    var h1 = 0xefcdab89.toInt()
    var h2 = 0x98badcfe.toInt()
    var h3 = 0x10325476
    var h4 = 0xc3d2e1f0.toInt()
    val words = IntArray(80)

    for (offset in 0 until paddedLength step 64) {
        for (i in 0 until 16) {
            val j = offset + i * 4
            words[i] =
                ((padded[j].toInt() and 0xff) shl 24) or
                    ((padded[j + 1].toInt() and 0xff) shl 16) or
                    ((padded[j + 2].toInt() and 0xff) shl 8) or
                    (padded[j + 3].toInt() and 0xff)
        }
        for (i in 16 until 80) {
            words[i] = (words[i - 3] xor words[i - 8] xor words[i - 14] xor words[i - 16]).rotateLeft(1)
        }

        var a = h0
        var b = h1
        var c = h2
        var d = h3
        var e = h4
        for (i in 0 until 80) {
            val (f, k) = when (i) {
                in 0..19 -> ((b and c) or ((b.inv()) and d)) to 0x5a827999
                in 20..39 -> (b xor c xor d) to 0x6ed9eba1
                in 40..59 -> ((b and c) or (b and d) or (c and d)) to 0x8f1bbcdc.toInt()
                else -> (b xor c xor d) to 0xca62c1d6.toInt()
            }
            val temp = (a.rotateLeft(5) + f + e + k + words[i])
            e = d
            d = c
            c = b.rotateLeft(30)
            b = a
            a = temp
        }
        h0 += a
        h1 += b
        h2 += c
        h3 += d
        h4 += e
    }

    return intArrayOf(h0, h1, h2, h3, h4)
        .joinToString("") { word ->
            (0 until 4).joinToString("") { i ->
                ((word ushr (24 - i * 8)) and 0xff).toString(16).padStart(2, '0')
            }
        }
}

private fun Int.rotateLeft(bits: Int): Int =
    (this shl bits) or (this ushr (32 - bits))

fun parseCookieString(cookie: String): Map<String, String> =
    cookie.split("; ")
        .filter { it.isNotEmpty() }
        .mapNotNull { part ->
            val splitIndex = part.indexOf('=')
            if (splitIndex == -1) null
            else part.take(splitIndex) to part.substring(splitIndex + 1)
        }
        .toMap()

fun String.parseTime(): Int? {
    return try {
        val parts = split(":").map { it.toInt() }
        when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}
