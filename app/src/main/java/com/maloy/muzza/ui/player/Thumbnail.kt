package com.maloy.muzza.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player.STATE_ENDED
import coil.compose.AsyncImage
import com.maloy.muzza.LocalListenTogetherManager
import com.maloy.muzza.LocalPlayerConnection
import com.maloy.muzza.constants.PlayerHorizontalPadding
import com.maloy.muzza.constants.PlayerStyle
import com.maloy.muzza.constants.PlayerStyleKey
import com.maloy.muzza.constants.ShowLyricsKey
import com.maloy.muzza.constants.SwipeThumbnailKey
import com.maloy.muzza.constants.ThumbnailCornerRadiusV2Key
import com.maloy.muzza.extensions.togglePlayPause
import com.maloy.muzza.models.MediaMetadata
import com.maloy.muzza.ui.component.AsyncLocalImage
import com.maloy.muzza.ui.component.Lyrics
import com.maloy.muzza.utils.imageCache
import com.maloy.muzza.utils.rememberEnumPreference
import com.maloy.muzza.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun Thumbnail(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
    mediaMetadata: MediaMetadata,
    showLyricsOnClick: Boolean = false
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val playbackState by playerConnection.playbackState.collectAsState()
    val currentView = LocalView.current
    val listenTogetherManager = LocalListenTogetherManager.current
    val isGuest = listenTogetherManager?.isInRoom == true && !listenTogetherManager.isHost

    val error: PlaybackException? by playerConnection.error.collectAsState()
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
        targetValue = when {
            showLyrics || !isPlaying -> 1.0f
            else -> 1.1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "thumbnailScale"
    )

    var showSeekEffect by remember { mutableStateOf(false) }
    var seekDirection by remember { mutableStateOf("") }

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
            BoxWithConstraints(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = PlayerHorizontalPadding)
            ) {
                val widthPx = constraints.maxWidth.toFloat()
                val spacingPx = with(LocalDensity.current) { 40.dp.toPx() }
                val itemWidth = widthPx + spacingPx

                val offsetX = remember { Animatable(0f) }
                val coroutineScope = rememberCoroutineScope()

                var lockedPrevMetadata by remember { mutableStateOf<MediaMetadata?>(null) }
                var lockedNextMetadata by remember { mutableStateOf<MediaMetadata?>(null) }

                LaunchedEffect(mediaMetadata, offsetX.isRunning) {
                    if (!offsetX.isRunning && offsetX.value == 0f) {
                        val prevIndex = playerConnection.player.previousMediaItemIndex
                        val nextIndex = playerConnection.player.nextMediaItemIndex

                        lockedPrevMetadata = if (prevIndex != -1) {
                            playerConnection.player.getMediaItemAt(prevIndex).localConfiguration?.tag as? MediaMetadata
                        } else null

                        lockedNextMetadata = if (nextIndex != -1) {
                            playerConnection.player.getMediaItemAt(nextIndex).localConfiguration?.tag as? MediaMetadata
                        } else null
                    }
                }

                LaunchedEffect(mediaMetadata) {
                    if (!offsetX.isRunning) offsetX.snapTo(0f)
                }

                val renderCover: @Composable (MediaMetadata, Float) -> Unit = { metadata, xOffset ->
                    val coverModifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .graphicsLayer {
                            translationX = xOffset
                            alpha = thumbnailAlpha
                            scaleX = thumbnailScale
                            scaleY = thumbnailScale
                        }
                        .clip(RoundedCornerShape(thumbnailCornerRadiusV2 * 2))
                        .clickable(onClick = {
                            when {
                                playbackState == STATE_ENDED -> {
                                    playerConnection.player.seekTo(0, 0)
                                    playerConnection.player.playWhenReady = true
                                }
                                playerStyle == PlayerStyle.OLD && showLyricsOnClick -> {
                                    showLyrics = !showLyrics
                                }
                                isGuest -> {
                                    playerConnection.isMuted
                                }
                                else -> {
                                    playerConnection.player.togglePlayPause()
                                }
                            }
                        })

                    if (metadata.isLocal) {
                        AsyncLocalImage(
                            image = { imageCache.getLocalThumbnail(metadata.localPath, false) },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = coverModifier
                        )
                    } else {
                        AsyncImage(
                            model = metadata.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = coverModifier
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(isGuest, swipeThumbnail) {
                            if (isGuest || !swipeThumbnail) return@pointerInput
                            detectHorizontalDragGestures(
                                onDragCancel = {
                                    coroutineScope.launch { offsetX.animateTo(0f, spring()) }
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    coroutineScope.launch {
                                        val newValue = offsetX.value + dragAmount
                                        val limitedValue = when {
                                            newValue > 0 && lockedPrevMetadata == null -> newValue * 0.2f
                                            newValue < 0 && lockedNextMetadata == null -> newValue * 0.2f
                                            else -> newValue
                                        }
                                        offsetX.snapTo(limitedValue)
                                    }
                                },
                                onDragEnd = {
                                    coroutineScope.launch {
                                        val threshold = widthPx / 4
                                        when {
                                            offsetX.value > threshold && lockedPrevMetadata != null -> {
                                                offsetX.animateTo(itemWidth, tween(300))
                                                playerConnection.player.seekToPreviousMediaItem()
                                                offsetX.snapTo(0f)
                                            }
                                            offsetX.value < -threshold && lockedNextMetadata != null -> {
                                                offsetX.animateTo(-itemWidth, tween(300))
                                                playerConnection.player.seekToNext()
                                                offsetX.snapTo(0f)
                                            }
                                            else -> {
                                                offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessLow))
                                            }
                                        }
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (offsetX.value < 0f && lockedNextMetadata != null) {
                        renderCover(lockedNextMetadata!!, offsetX.value + itemWidth)
                    }
                    if (offsetX.value > 0f && lockedPrevMetadata != null) {
                        renderCover(lockedPrevMetadata!!, offsetX.value - itemWidth)
                    }
                    renderCover(mediaMetadata, offsetX.value)
                }
            }
        }

        LaunchedEffect(showSeekEffect) {
            if (showSeekEffect) {
                delay(1000)
                showSeekEffect = false
            }
        }

        var hasHandledError by remember { mutableStateOf(false) }
        LaunchedEffect(error) {
            if (error != null && !hasHandledError) {
                hasHandledError = true
                playerConnection.player.prepare()
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
                    error = playbackException,
                    retry = { playerConnection.player.prepare() },
                )
            }
        }
    }
}