package com.dreamify.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import coil.compose.AsyncImage
import com.dreamify.app.LocalPlayerConnection
import com.dreamify.app.R
import com.dreamify.app.constants.PlayerHorizontalPadding
import com.dreamify.app.constants.PlayerStyle
import com.dreamify.app.constants.PlayerStyleKey
import com.dreamify.app.constants.ShowLyricsKey
import com.dreamify.app.constants.SwipeThumbnailKey
import com.dreamify.app.constants.ThumbnailCornerRadiusV2Key
import com.dreamify.app.models.MediaMetadata
import com.dreamify.app.ui.component.AsyncLocalImage
import com.dreamify.app.ui.component.Lyrics
import com.dreamify.app.ui.utils.imageCache
import com.dreamify.app.utils.rememberEnumPreference
import com.dreamify.app.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun Thumbnail(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
    showLyricsOnClick: Boolean = false,
    contentScale: ContentScale = ContentScale.Fit,
    customMediaMetadata: MediaMetadata? = null
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val currentView = LocalView.current
    val context = LocalContext.current

    val error: PlaybackException? by playerConnection.error.collectAsState()
    val playerMediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val mediaMetadata = customMediaMetadata ?: playerMediaMetadata

    var showLyrics by rememberPreference(ShowLyricsKey, false)
    val swipeThumbnail by rememberPreference(SwipeThumbnailKey, true)
    val thumbnailCornerRadiusV2 by rememberPreference(ThumbnailCornerRadiusV2Key, 3)
    val (playerStyle) = rememberEnumPreference(PlayerStyleKey, defaultValue = PlayerStyle.NEW)

    val thumbnailAlpha by animateFloatAsState(
        targetValue = if (showLyrics) 0.6f else 1f,
        animationSpec = tween(300),
        label = "thumbnailAlpha"
    )

    val thumbnailScale by animateFloatAsState(
        targetValue = if (showLyrics) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "thumbnailScale"
    )

    var offsetX by remember { mutableFloatStateOf(0f) }

    var currentMediaItem by remember { mutableStateOf<MediaItem?>(null) }
    val layoutDirection = LocalLayoutDirection.current
    var showSeekEffect by remember { mutableStateOf(false) }
    var seekDirection by remember { mutableStateOf("") }

    LaunchedEffect(playerConnection.player.currentMediaItemIndex) {
        currentMediaItem = playerConnection.player.currentMediaItem
    }

    DisposableEffect(showLyrics) {
        currentView.keepScreenOn = showLyrics
        onDispose { currentView.keepScreenOn = false }
    }

    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = !showLyrics && error == null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = PlayerHorizontalPadding)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragCancel = {
                                offsetX = 0f
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                if (swipeThumbnail) {
                                    offsetX += dragAmount
                                }
                            },
                            onDragEnd = {
                                println(offsetX)
                                if (offsetX > 300) {
                                    if (playerConnection.player.previousMediaItemIndex != -1) {
                                        playerConnection.player.seekToPreviousMediaItem()
                                    }
                                } else if (offsetX < -300) {
                                    if (playerConnection.player.nextMediaItemIndex != -1) {
                                        playerConnection.player.seekToNext()
                                    }
                                }
                                offsetX = 0f
                            },
                        )
                    },
            ) {
                if (mediaMetadata?.isLocal == false) {
                    if (playerStyle == PlayerStyle.NEW) {
                        AsyncImage(
                            model = mediaMetadata.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .offset { IntOffset(offsetX.roundToInt(), 0) }
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .graphicsLayer {
                                    translationX = offsetX * 0.5f
                                    alpha = thumbnailAlpha
                                    scaleX = thumbnailScale
                                    scaleY = thumbnailScale
                                }
                                .clip(RoundedCornerShape(thumbnailCornerRadiusV2 * 2))
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onDoubleTap = { offset ->
                                            val currentPosition =
                                                playerConnection.player.currentPosition
                                            if ((layoutDirection == LayoutDirection.Ltr && offset.x < size.width / 2) ||
                                                (layoutDirection == LayoutDirection.Rtl && offset.x > size.width / 2)
                                            ) {
                                                playerConnection.player.seekTo(
                                                    (currentPosition - 5000).coerceAtLeast(
                                                        0
                                                    )
                                                )
                                                seekDirection =
                                                    context.getString(R.string.seek_backward)
                                            } else {
                                                playerConnection.player.seekTo(
                                                    (currentPosition + 5000).coerceAtMost(
                                                        playerConnection.player.duration
                                                    )
                                                )
                                                seekDirection =
                                                    context.getString(R.string.seek_forward)
                                            }
                                            showSeekEffect = true
                                        }
                                    )
                                }
                        )
                    } else {
                        AsyncImage(
                            model = mediaMetadata.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .offset { IntOffset(offsetX.roundToInt(), 0) }
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .graphicsLayer {
                                    translationX = offsetX * 0.5f
                                    alpha = thumbnailAlpha
                                    scaleX = thumbnailScale
                                    scaleY = thumbnailScale
                                }
                                .clip(RoundedCornerShape(thumbnailCornerRadiusV2 * 2))
                                .clickable(enabled = showLyricsOnClick) { showLyrics = !showLyrics }
                        )
                    }
                } else {
                    if (mediaMetadata?.isLocal == true) {
                        if (playerStyle == PlayerStyle.NEW) {
                            mediaMetadata.let {
                                AsyncLocalImage(
                                    image = { imageCache.getLocalThumbnail(it.localPath, false) },
                                    contentDescription = null,
                                    contentScale = contentScale,
                                    modifier = Modifier
                                        .offset { IntOffset(offsetX.roundToInt(), 0) }
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .graphicsLayer {
                                            translationX = offsetX * 0.5f
                                            alpha = thumbnailAlpha
                                            scaleX = thumbnailScale
                                            scaleY = thumbnailScale
                                        }
                                        .clip(RoundedCornerShape(thumbnailCornerRadiusV2 * 2))
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onDoubleTap = { offset ->
                                                    val currentPosition =
                                                        playerConnection.player.currentPosition
                                                    if ((layoutDirection == LayoutDirection.Ltr && offset.x < size.width / 2) ||
                                                        (layoutDirection == LayoutDirection.Rtl && offset.x > size.width / 2)
                                                    ) {
                                                        playerConnection.player.seekTo(
                                                            (currentPosition - 5000).coerceAtLeast(
                                                                0
                                                            )
                                                        )
                                                        seekDirection =
                                                            context.getString(R.string.seek_backward)
                                                    } else {
                                                        playerConnection.player.seekTo(
                                                            (currentPosition + 5000).coerceAtMost(
                                                                playerConnection.player.duration
                                                            )
                                                        )
                                                        seekDirection =
                                                            context.getString(R.string.seek_forward)
                                                    }
                                                    showSeekEffect = true
                                                }
                                            )
                                        }
                                )
                            }
                        } else {
                            mediaMetadata.let {
                                AsyncLocalImage(
                                    image = { imageCache.getLocalThumbnail(it.localPath, false) },
                                    contentDescription = null,
                                    contentScale = contentScale,
                                    modifier = Modifier
                                        .offset { IntOffset(offsetX.roundToInt(), 0) }
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .graphicsLayer {
                                            translationX = offsetX * 0.5f
                                            alpha = thumbnailAlpha
                                            scaleX = thumbnailScale
                                            scaleY = thumbnailScale
                                        }
                                        .clip(RoundedCornerShape(thumbnailCornerRadiusV2 * 2))
                                        .clickable(enabled = showLyricsOnClick) {
                                            showLyrics = !showLyrics
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }

        LaunchedEffect(showSeekEffect) {
            if (showSeekEffect) {
                delay(1000)
                showSeekEffect = false
            }
        }

        AnimatedVisibility(
            visible = showSeekEffect,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Text(
                text = seekDirection,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            )
        }

        AnimatedVisibility(
            visible = showLyrics && error == null,
            enter = fadeIn() + expandHorizontally(expandFrom = Alignment.CenterHorizontally),
            exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxSize()
        ) {
            Lyrics(
                sliderPositionProvider = sliderPositionProvider,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            )
        }

        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .padding(32.dp)
                .align(Alignment.Center)
        ) {
            error?.let { playbackException ->
                PlaybackError(
                    error = playbackException.message ?: context.getString(R.string.error_unknown),
                    retry = { playerConnection.player.prepare() },
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                )
            }
        }
    }
}

@Composable
fun PlaybackError(
    error: String,
    retry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(24.dp)
    ) {
        Text(
            text = stringResource(R.string.error_unknown),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onErrorContainer
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = retry,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Text(text = stringResource(R.string.retry))
        }
    }
}
