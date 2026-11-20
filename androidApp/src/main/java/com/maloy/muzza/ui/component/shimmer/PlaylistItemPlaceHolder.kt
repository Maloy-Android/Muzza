package com.maloy.muzza.ui.component.shimmer

import androidx.compose.foundation.background

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.maloy.muzza.constants.AlbumThumbnailSize
import com.maloy.muzza.constants.ThumbnailCornerRadius

@Composable
fun PlaylistAlbumItemPlaceHolder(
    modifier: Modifier = Modifier,
    thumbnailShape: Shape = RoundedCornerShape(ThumbnailCornerRadius)
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Spacer(
            modifier = Modifier
                .size(AlbumThumbnailSize)
                .clip(thumbnailShape)
                .background(MaterialTheme.colorScheme.onSurface)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextPlaceholder(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(20.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            TextPlaceholder(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            TextPlaceholder(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(16.dp)
            )
        }
    }
}