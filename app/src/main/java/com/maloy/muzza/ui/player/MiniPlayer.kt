package com.maloy.muzza.ui.player

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.maloy.muzza.LocalPlayerConnection
import com.maloy.muzza.R
import com.maloy.muzza.constants.MiniPlayerHeight
import com.maloy.muzza.constants.MiniPlayerStyle
import com.maloy.muzza.constants.MiniPlayerStyleKey
import com.maloy.muzza.constants.ThumbnailCornerRadius
import com.maloy.muzza.extensions.togglePlayPause
import com.maloy.muzza.models.MediaMetadata
import com.maloy.muzza.ui.component.AsyncLocalImage
import com.maloy.muzza.ui.utils.imageCache
import com.maloy.muzza.utils.rememberEnumPreference
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@SuppressLint("UseOfNonLambdaOffsetOverload")
@Composable
fun MiniPlayer(
    position: Long,
    duration: Long,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val playbackState by playerConnection.playbackState.collectAsState()
    val error by playerConnection.error.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()

    val currentView = LocalView.current
    val layoutDirection = LocalLayoutDirection.current
    var offsetX by remember { mutableFloatStateOf(0f) }

    val (miniPlayerStyle) = rememberEnumPreference(
        MiniPlayerStyleKey,
        defaultValue = MiniPlayerStyle.NEW
    )

    if (miniPlayerStyle == MiniPlayerStyle.NEW) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(MiniPlayerHeight)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {},
                        onDragCancel = {
                            offsetX = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            val adjustedDragAmount =
                                if (layoutDirection == LayoutDirection.Rtl) -dragAmount else dragAmount
                            offsetX += adjustedDragAmount
                        },
                        onDragEnd = {
                            val threshold = 0.15f * currentView.width

                            when {
                                offsetX > threshold && canSkipPrevious -> {
                                    playerConnection.player.seekToPreviousMediaItem()
                                }

                                offsetX < -threshold && canSkipNext -> {
                                    playerConnection.player.seekToNext()
                                }
                            }
                            offsetX = 0f
                        }
                    )
                }
        ) {
            LinearProgressIndicator(
                progress = { (position.toFloat() / duration).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.BottomCenter),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(offsetX.roundToInt(), 0) }
                    .padding(end = 12.dp)
            ) {
                Box(Modifier.weight(1f)) {
                    mediaMetadata?.let {
                        MiniMediaInfo(
                            mediaMetadata = it,
                            error = error,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }
                }
                IconButton(
                    onClick = {
                        if (playbackState == Player.STATE_ENDED) {
                            playerConnection.player.seekTo(0, 0)
                            playerConnection.player.playWhenReady = true
                        } else {
                            playerConnection.player.togglePlayPause()
                        }
                    },
                ) {
                    Icon(
                        painter = painterResource(
                            if (playbackState == Player.STATE_ENDED) {
                                R.drawable.replay
                            } else if (isPlaying) {
                                R.drawable.pause
                            } else {
                                R.drawable.play
                            },
                        ),
                        contentDescription = null,
                    )
                }
            }
            if (offsetX.absoluteValue > 50f) {
                Box(
                    modifier = Modifier
                        .align(if (offsetX > 0) Alignment.CenterStart else Alignment.CenterEnd)
                        .padding(horizontal = 16.dp)
                ) {
                    Icon(
                        painter = painterResource(
                            if (offsetX > 0) R.drawable.skip_previous else R.drawable.skip_next
                        ),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(
                            alpha = (offsetX.absoluteValue / 200f).coerceIn(0f, 1f)
                        ),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(MiniPlayerHeight)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
        ) {
            LinearProgressIndicator(
                progress = { (position.toFloat() / duration).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.BottomCenter),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = modifier
                    .fillMaxSize()
                    .padding(end = 6.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    mediaMetadata?.let {
                        MiniMediaInfo(
                            mediaMetadata = it,
                            error = error,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }
                }

                IconButton(
                    enabled = canSkipNext,
                    onClick = playerConnection::seekToPrevious
                ) {
                    Icon(
                        painter = painterResource(R.drawable.skip_previous),
                        contentDescription = null,
                        modifier = Modifier
                            .size(26.dp)
                    )
                }

                IconButton(
                    onClick = {
                        if (playbackState == Player.STATE_ENDED) {
                            playerConnection.player.seekTo(0, 0)
                            playerConnection.player.playWhenReady = true
                        } else {
                            playerConnection.player.togglePlayPause()
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(if (playbackState == Player.STATE_ENDED) R.drawable.replay else if (isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = null
                    )
                }

                IconButton(
                    enabled = canSkipNext,
                    onClick = playerConnection::seekToNext
                ) {
                    Icon(
                        painter = painterResource(R.drawable.skip_next),
                        contentDescription = null,
                        modifier = Modifier
                            .size(26.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MiniMediaInfo(
    mediaMetadata: MediaMetadata,
    error: PlaybackException?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Box(modifier = Modifier.padding(6.dp)) {
            if (mediaMetadata.isLocal) {
                mediaMetadata.let {
                    AsyncLocalImage(
                        image = { imageCache.getLocalThumbnail(it.localPath, false) },
                        contentDescription = null,
                        contentScale = contentScale,
                        modifier = Modifier
                            .size(48.dp)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(ThumbnailCornerRadius))
                    )
                }
            } else {
                AsyncImage(
                    model = mediaMetadata.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(ThumbnailCornerRadius))
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = error != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(ThumbnailCornerRadius)
                        )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.info),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.Center)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
        ) {
            AnimatedContent(
                targetState = mediaMetadata.title,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "",
            ) { title ->
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(),
                )
            }
            AnimatedContent(
                targetState = mediaMetadata.artists.joinToString { it.name },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "",
            ) { artists ->
                Text(
                    text = artists,
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(),
                )
            }
        }
    }
}
