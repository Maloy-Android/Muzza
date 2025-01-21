package com.maloy.muzza.ui.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever

const val SCANNER_DEBUG = false


/**
 * ==========================
 * Various misc helpers
 * ==========================
 */


/**
 * Extract the album art from the audio file. The image is not resized
 * (did you mean to use getLocalThumbnail(path: String?, resize: Boolean)?).
 *
 * @param path Full path of audio file
 */
fun getLocalThumbnail(path: String?): Bitmap? = getLocalThumbnail(path, false)

/**
 * Extract the album art from the audio file
 *
 * @param path Full path of audio file
 * @param resize Whether to resize the Bitmap to a thumbnail size (300x300)
 */
fun getLocalThumbnail(path: String?, resize: Boolean): Bitmap? {
    if (path == null) {
        return null
    }
    // try cache lookup
    val cachedImage = if (resize) {
        retrieveImage(path)?.resizedImage
    } else {
        retrieveImage(path)?.image
    }

    if (cachedImage == null) {
//        Timber.tag(TAG).d("Cache miss on $path")
    } else {
        return cachedImage
    }

    val mData = MediaMetadataRetriever()

    var image: Bitmap = try {
        mData.setDataSource(path)
        val art = mData.embeddedPicture
        BitmapFactory.decodeByteArray(art, 0, art!!.size)
    } catch (e: Exception) {
        cache(path, null, resize)
        null
    } ?: return null

    if (resize) {
        image = Bitmap.createScaledBitmap(image, 100, 100, false)
    }

    cache(path, image, resize)
    return image
}