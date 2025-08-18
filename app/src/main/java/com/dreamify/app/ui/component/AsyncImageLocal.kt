package com.dreamify.app.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext

const val MAX_IMAGE_JOBS = 4
@OptIn(DelicateCoroutinesApi::class)
val imageSession = newFixedThreadPoolContext(MAX_IMAGE_JOBS , "ImageExtractor")

/**
 * Non-blocking image
 */
@Composable
fun AsyncLocalImage(
    image: () -> Bitmap?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    contentDescription: String? = null,
) {
    var imageBitmapState by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(image) {
        CoroutineScope(imageSession).launch {
            try {
                imageBitmapState = image.invoke()?.asImageBitmap()
            } catch (e: Exception) { }
        }
    }

    imageBitmapState.let { imageBitmap ->
        if (imageBitmap == null) {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = modifier
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(16.dp))
            )
        } else {
            Image(
                bitmap = imageBitmap,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = modifier,
            )
        }
    }
}