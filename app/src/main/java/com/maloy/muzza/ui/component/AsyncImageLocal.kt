package com.maloy.muzza.ui.component

import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage


@Composable
fun AsyncLocalImage(
    image: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    contentDescription: String? = null,
) {
    if (image.isNullOrEmpty()) {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = modifier
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(16.dp))
        )
    } else {
        AsyncImage(
            model = image,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    }
}