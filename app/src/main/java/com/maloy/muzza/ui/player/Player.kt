@file:Suppress("NAME_SHADOWING")

package com.maloy.muzza.ui.player

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.BitmapDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Player.STATE_READY
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.maloy.muzza.LocalPlayerConnection
import com.maloy.muzza.R
import com.maloy.muzza.constants.DarkMode
import com.maloy.muzza.constants.DarkModeKey
import com.maloy.muzza.constants.LikedAutoDownloadKey
import com.maloy.muzza.constants.LikedAutodownloadMode
import com.maloy.muzza.constants.NowPlayingEnableKey
import com.maloy.muzza.constants.NowPlayingPaddingKey
import com.maloy.muzza.constants.PlayerBackgroundStyle
import com.maloy.muzza.constants.PlayerBackgroundStyleKey
import com.maloy.muzza.constants.PlayerHorizontalPadding
import com.maloy.muzza.constants.PlayerStyle
import com.maloy.muzza.constants.PlayerStyleKey
import com.maloy.muzza.constants.PureBlackKey
import com.maloy.muzza.constants.QueuePeekHeight
import com.maloy.muzza.constants.ShowFlakeEffectKey
import com.maloy.muzza.constants.ShowLyricsKey
import com.maloy.muzza.constants.SliderStyle
import com.maloy.muzza.constants.SliderStyleKey
import com.maloy.muzza.constants.SongDurationTimeSkip
import com.maloy.muzza.constants.SongDurationTimeSkipKey
import com.maloy.muzza.constants.fullScreenLyricsKey
import com.maloy.muzza.extensions.metadata
import com.maloy.muzza.extensions.togglePlayPause
import com.maloy.muzza.extensions.toggleRepeatMode
import com.maloy.muzza.extensions.toggleShuffleMode
import com.maloy.muzza.models.MediaMetadata
import com.maloy.muzza.playback.ExoDownloadService
import com.maloy.muzza.ui.component.AsyncLocalImage
import com.maloy.muzza.ui.component.BottomSheet
import com.maloy.muzza.ui.component.BottomSheetState
import com.maloy.muzza.ui.component.LocalMenuState
import com.maloy.muzza.ui.component.PlayerSliderTrack
import com.maloy.muzza.ui.component.ResizableIconButton
import com.maloy.muzza.ui.component.SnowfallEffect
import com.maloy.muzza.ui.component.rememberBottomSheetState
import com.maloy.muzza.ui.menu.PlayerMenu
import com.maloy.muzza.ui.theme.extractGradientColors
import com.maloy.muzza.ui.utils.SnapLayoutInfoProvider
import com.maloy.muzza.utils.imageCache
import com.maloy.muzza.utils.makeTimeString
import com.maloy.muzza.utils.rememberEnumPreference
import com.maloy.muzza.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import me.saket.squiggles.SquigglySlider
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BottomSheetPlayer(
    state: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return

    val menuState = LocalMenuState.current
    val isSystemInDarkTheme = isSystemInDarkTheme()
    val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val pureBlack by rememberPreference(PureBlackKey, defaultValue = false)
    val haptic = LocalHapticFeedback.current
    val useBlackBackground = remember(isSystemInDarkTheme, darkTheme, pureBlack) {
        val useDarkTheme =
            if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
        useDarkTheme && pureBlack
    }
    if (useBlackBackground && state.value > state.collapsedBound) {
        lerp(MaterialTheme.colorScheme.surfaceContainer, Color.Black, state.progress)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    val showLyrics by rememberPreference(ShowLyricsKey, defaultValue = false)

    var fullScreenLyrics by rememberPreference(fullScreenLyricsKey, defaultValue = true)

    val sliderStyle by rememberEnumPreference(SliderStyleKey, SliderStyle.DEFAULT)

    val playbackState by playerConnection.playbackState.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)

    val context = LocalContext.current
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()

    val playerBackground by rememberEnumPreference(
        key = PlayerBackgroundStyleKey,
        defaultValue = PlayerBackgroundStyle.DEFAULT
    )

    val (showFlakeEffect) = rememberPreference(ShowFlakeEffectKey, defaultValue = true)

    val (playerStyle) = rememberEnumPreference(PlayerStyleKey, defaultValue = PlayerStyle.NEW)

    val useDarkTheme = remember(darkTheme, isSystemInDarkTheme) {
        if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
    }

    val onBackgroundColor = when (playerBackground) {
        PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.secondary
        else ->
            if (useDarkTheme)
                MaterialTheme.colorScheme.onSurface
            else
                if (pureBlack && darkTheme == DarkMode.ON && isSystemInDarkTheme)
                    Color.White
                else
                    MaterialTheme.colorScheme.onPrimary
    }

    val (nowPlayingEnable) = rememberPreference(NowPlayingEnableKey, defaultValue = true)
    val (nowPlayingPadding) = rememberPreference(NowPlayingPaddingKey, defaultValue = 35)

    var gradientColors by remember {
        mutableStateOf<List<Color>>(emptyList())
    }

    val (likedAutoDownload) = rememberEnumPreference(
        LikedAutoDownloadKey,
        LikedAutodownloadMode.OFF
    )
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val isWifiConnected = remember {
        val capabilities =
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
    }

    LaunchedEffect(mediaMetadata, playerBackground) {
        if (useBlackBackground && playerBackground != PlayerBackgroundStyle.BLUR) {
            gradientColors = listOf(Color.Black, Color.Black)
        }
        if (useBlackBackground && playerBackground != PlayerBackgroundStyle.GRADIENT) {
            gradientColors = listOf(Color.Black, Color.Black)
        } else if (playerBackground == PlayerBackgroundStyle.GRADIENT) {
            withContext(Dispatchers.IO) {
                val result = (ImageLoader(context).execute(
                    ImageRequest.Builder(context).data(mediaMetadata?.thumbnailUrl)
                        .allowHardware(false).build(),
                ).drawable as? BitmapDrawable)?.bitmap?.extractGradientColors()

                result?.let {
                    gradientColors = it
                }
            }
        } else {
            gradientColors = emptyList()
        }
    }

    var position by rememberSaveable(playbackState) {
        mutableLongStateOf(playerConnection.player.currentPosition)
    }
    var duration by rememberSaveable(playbackState) {
        mutableLongStateOf(playerConnection.player.duration)
    }
    val (songDurationTimeSkip) = rememberEnumPreference(
        SongDurationTimeSkipKey,
        defaultValue = SongDurationTimeSkip.FIVE
    )
    var sliderPosition by remember {
        mutableStateOf<Long?>(null)
    }
    var showDetailsDialog by remember { mutableStateOf(false) }
    if (showDetailsDialog) {
        DetailsDialog(
            onDismiss = { showDetailsDialog = false }
        )
    }

    LaunchedEffect(playbackState) {
        if (playbackState == STATE_READY) {
            while (isActive) {
                delay(100)
                position = playerConnection.player.currentPosition
                duration = playerConnection.player.duration
            }
        }
    }

    val thumbnailLazyGridState = rememberLazyGridState()
    val horizontalLazyGridItemWidthFactor = 1f
    val thumbnailSnapLayoutInfoProvider = remember(thumbnailLazyGridState) {
        SnapLayoutInfoProvider(
            lazyGridState = thumbnailLazyGridState,
            positionInLayout = { layoutSize, itemSize ->
                (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
            }
        )
    }
    val previousMediaMetadata =
        if (playerConnection.player.hasPreviousMediaItem()) {
            val previousIndex = playerConnection.player.previousMediaItemIndex
            playerConnection.player.getMediaItemAt(previousIndex).metadata
        } else null

    val nextMediaMetadata = if (playerConnection.player.hasNextMediaItem()) {
        val nextIndex = playerConnection.player.nextMediaItemIndex
        playerConnection.player.getMediaItemAt(nextIndex).metadata
    } else null

    val mediaItems = listOfNotNull(previousMediaMetadata, mediaMetadata, nextMediaMetadata)
    val currentMediaIndex = mediaItems.indexOf(mediaMetadata)

    val currentItem by remember { derivedStateOf { thumbnailLazyGridState.firstVisibleItemIndex } }
    val itemScrollOffset by remember { derivedStateOf { thumbnailLazyGridState.firstVisibleItemScrollOffset } }

    LaunchedEffect(itemScrollOffset) {
        if (!thumbnailLazyGridState.isScrollInProgress || itemScrollOffset != 0) return@LaunchedEffect

        if (currentItem > currentMediaIndex)
            playerConnection.player.seekToNext()
        else if (currentItem < currentMediaIndex)
            playerConnection.player.seekToPreviousMediaItem()
    }


    LaunchedEffect(mediaMetadata, canSkipPrevious, canSkipNext) {
        val index = maxOf(0, currentMediaIndex)
        if (state.isExpanded)
            thumbnailLazyGridState.animateScrollToItem(index)
        else
            thumbnailLazyGridState.scrollToItem(index)
    }

    LaunchedEffect(!showLyrics && !fullScreenLyrics) {
        fullScreenLyrics = true
    }

    LaunchedEffect(!showLyrics) {
        fullScreenLyrics = true
    }

    if (currentSong?.song?.id in listOf("0IuRPqAZBi4", "ZNbSs6Z0lkA")) {
        Toast.makeText(
            context,
            context.getString(R.string.mrzavka),
            Toast.LENGTH_LONG
        ).show()
    }

    val queueSheetState = rememberBottomSheetState(
        dismissedBound = QueuePeekHeight + WindowInsets.systemBars.asPaddingValues()
            .calculateBottomPadding(),
        expandedBound = state.expandedBound,
    )

    BottomSheet(
        state = state,
        modifier = modifier,
        backgroundColor = when {
            pureBlack && (darkTheme == DarkMode.ON || useBlackBackground) -> Color.Black
            useDarkTheme || playerBackground == PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.surfaceContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        collapsedBackgroundColor = MaterialTheme.colorScheme.surfaceContainer,
        onDismiss = {
            playerConnection.player.stop()
            playerConnection.player.clearMediaItems()
        },
        collapsedContent = {
            MiniPlayer(
                position = position,
                duration = duration
            )
        }
    ) {
        if (showFlakeEffect) {
            SnowfallEffect(
                modifier = Modifier.fillMaxSize(),
                isActive = true,
                snowflakeCount = 25,
                speedMultiplier = 0.6f,
                isDarkTheme = useDarkTheme
            )
        }
        val controlsContent: @Composable ColumnScope.(MediaMetadata) -> Unit = { mediaMetadata ->
            if (fullScreenLyrics) {
                Row(
                    horizontalArrangement = Arrangement.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PlayerHorizontalPadding)
                ) {
                    Row {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                        ) {
                            AnimatedContent(
                                targetState = mediaMetadata.title,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "",
                            ) { title ->
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = onBackgroundColor,
                                    modifier =
                                        Modifier
                                            .basicMarquee()
                                            .clickable(enabled = mediaMetadata.album != null && !mediaMetadata.isLocal) {
                                                navController.navigate("album/${mediaMetadata.album!!.id}")
                                                state.collapseSoft()
                                            },
                                )
                            }

                            Row(
                                modifier = Modifier.offset(y = 25.dp)
                            ) {
                                mediaMetadata.artists.fastForEachIndexed { index, artist ->
                                    AnimatedContent(
                                        targetState = artist.name,
                                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                                        label = "",
                                    ) { name ->
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = onBackgroundColor,
                                            maxLines = 1,
                                            modifier =
                                                Modifier.clickable(enabled = artist.id != null && !mediaMetadata.isLocal) {
                                                    navController.navigate("artist/${artist.id}")
                                                    state.collapseSoft()
                                                },
                                        )
                                    }

                                    if (index != mediaMetadata.artists.lastIndex) {
                                        AnimatedContent(
                                            targetState = ", ",
                                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                                            label = "",
                                        ) { comma ->
                                            Text(
                                                text = comma,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = onBackgroundColor,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Box(
                            modifier = Modifier
                                .offset(y = 5.dp)
                                .size(36.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            ResizableIconButton(
                                icon = if (currentSong?.song?.liked == true) R.drawable.favorite else R.drawable.favorite_border,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(24.dp),
                                onClick = {
                                    playerConnection.toggleLike()
                                    currentSong?.song?.localToggleLike()
                                    if (likedAutoDownload == LikedAutodownloadMode.ON && currentSong?.song?.liked == false && currentSong?.song?.dateDownload == null || likedAutoDownload == LikedAutodownloadMode.WIFI_ONLY && currentSong?.song?.liked == false && currentSong?.song?.dateDownload == null && isWifiConnected) {
                                        val downloadRequest = mediaMetadata.let {
                                            DownloadRequest
                                                .Builder(it.id, it.id.toUri())
                                                .setCustomCacheKey(it.id)
                                                .setData(it.title.toByteArray())
                                                .build()
                                        }
                                        DownloadService.sendAddDownload(
                                            context,
                                            ExoDownloadService::class.java,
                                            downloadRequest,
                                            false
                                        )
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.width(7.dp))

                        Box(
                            modifier = Modifier
                                .offset(y = 5.dp)
                                .size(36.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            ResizableIconButton(
                                icon = R.drawable.more_vert,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .size(24.dp)
                                    .align(Alignment.Center),
                                onClick = {
                                    menuState.show {
                                        PlayerMenu(
                                            mediaMetadata = mediaMetadata,
                                            navController = navController,
                                            bottomSheetState = state,
                                            onShowDetailsDialog = { showDetailsDialog = true },
                                            onDismiss = menuState::dismiss
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                when (sliderStyle) {
                    SliderStyle.DEFAULT -> {
                        Slider(
                            value = (sliderPosition ?: position).toFloat(),
                            valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                            onValueChange = {
                                sliderPosition = it.toLong()
                            },
                            onValueChangeFinished = {
                                sliderPosition?.let {
                                    playerConnection.player.seekTo(it)
                                    position = it
                                }
                                sliderPosition = null
                            },
                            thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                            track = { sliderState ->
                                PlayerSliderTrack(
                                    sliderState = sliderState,
                                    colors = SliderDefaults.colors()
                                )
                            },
                            modifier = Modifier.padding(horizontal = PlayerHorizontalPadding)
                        )
                    }

                    SliderStyle.SQUIGGLY -> {
                        SquigglySlider(
                            value = (sliderPosition ?: position).toFloat(),
                            valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                            onValueChange = {
                                sliderPosition = it.toLong()
                            },
                            onValueChangeFinished = {
                                sliderPosition?.let {
                                    playerConnection.player.seekTo(it)
                                    position = it
                                }
                                sliderPosition = null
                            },
                            squigglesSpec = SquigglySlider.SquigglesSpec(
                                amplitude = if (isPlaying) 2.dp else 0.dp,
                                strokeWidth = 4.dp,
                            ),
                            modifier = Modifier.padding(horizontal = PlayerHorizontalPadding),
                        )
                    }

                    SliderStyle.COMPOSE -> {
                        Slider(
                            value = (sliderPosition ?: position).toFloat(),
                            valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                            onValueChange = {
                                sliderPosition = it.toLong()
                            },
                            onValueChangeFinished = {
                                sliderPosition?.let {
                                    playerConnection.player.seekTo(it)
                                    position = it
                                }
                                sliderPosition = null
                            },
                            modifier = Modifier.padding(horizontal = PlayerHorizontalPadding)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PlayerHorizontalPadding + 4.dp)
                ) {
                    Text(
                        text = makeTimeString(sliderPosition ?: position),
                        style = MaterialTheme.typography.labelMedium,
                        color = onBackgroundColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Text(
                        text = if (duration != C.TIME_UNSET) makeTimeString(duration) else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = onBackgroundColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PlayerHorizontalPadding)
                ) {
                    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsState()

                    Box(modifier = Modifier.weight(1f)) {
                        FilledTonalIconButton(
                            onClick = playerConnection.player::toggleShuffleMode,
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (shuffleModeEnabled)
                                    MaterialTheme.colorScheme.secondaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (shuffleModeEnabled)
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            enabled = true
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.shuffle),
                                contentDescription = stringResource(R.string.shuffle),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    if (playerStyle == PlayerStyle.NEW) {
                        Box(modifier = Modifier.weight(1f)) {
                            FilledTonalIconButton(
                                onClick = { playerConnection.player.seekToPrevious() },
                                modifier = Modifier.size(48.dp),
                                enabled = canSkipPrevious
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.skip_previous),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    } else {
                        Box(modifier = Modifier.weight(1f)) {
                            FilledTonalIconButton(
                                onClick = {},
                                modifier = Modifier
                                    .size(48.dp),
                                enabled = canSkipPrevious
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.skip_previous),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .combinedClickable(
                                            onClick = { playerConnection.player.seekToPrevious() },
                                            onLongClick = {
                                                Toast.makeText(
                                                    context, context.getString(
                                                        when (songDurationTimeSkip) {
                                                            SongDurationTimeSkip.FIVE -> R.string.seek_backward_5
                                                            SongDurationTimeSkip.TEN -> R.string.seek_backward_10
                                                            SongDurationTimeSkip.FIFTEEN -> R.string.seek_backward_15
                                                            SongDurationTimeSkip.TWENTY -> R.string.seek_backward_20
                                                            SongDurationTimeSkip.TWENTYFIVE -> R.string.seek_backward_25
                                                            SongDurationTimeSkip.THIRTY -> R.string.seek_backward_30
                                                        }
                                                    ), Toast.LENGTH_LONG
                                                ).show()
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                playerConnection.player.seekTo(
                                                    playerConnection.player.currentPosition - when (songDurationTimeSkip) {
                                                        SongDurationTimeSkip.FIVE -> 5000
                                                        SongDurationTimeSkip.TEN -> 10000
                                                        SongDurationTimeSkip.FIFTEEN -> 15000
                                                        SongDurationTimeSkip.TWENTY -> 20000
                                                        SongDurationTimeSkip.TWENTYFIVE -> 25000
                                                        SongDurationTimeSkip.THIRTY -> 30000
                                                    }
                                                )
                                            }
                                        )
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    FilledTonalIconButton(
                        onClick = {
                            if (playbackState == STATE_ENDED) {
                                playerConnection.player.seekTo(0, 0)
                                playerConnection.player.playWhenReady = true
                            } else {
                                playerConnection.player.togglePlayPause()
                            }
                        },
                        modifier = Modifier.size(72.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            painter = painterResource(
                                if (playbackState == STATE_ENDED) R.drawable.replay
                                else if (isPlaying) R.drawable.pause
                                else R.drawable.play
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    if (playerStyle == PlayerStyle.NEW) {
                        Box(modifier = Modifier.weight(1f)) {
                            FilledTonalIconButton(
                                modifier = Modifier.size(48.dp),
                                onClick = { playerConnection.player.seekToNext() },
                                enabled = canSkipNext
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.skip_next),
                                    contentDescription = stringResource(R.string.next),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    } else {
                        Box(modifier = Modifier.weight(1f)) {
                            FilledTonalIconButton(
                                modifier = Modifier.size(48.dp),
                                onClick = {},
                                enabled = canSkipNext
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.skip_next),
                                    contentDescription = stringResource(R.string.next),
                                    modifier = Modifier
                                        .size(24.dp)
                                        .combinedClickable(
                                            onClick = { playerConnection.player.seekToNext() },
                                            onLongClick = {
                                                Toast.makeText(
                                                    context, context.getString(
                                                        when (songDurationTimeSkip) {
                                                            SongDurationTimeSkip.FIVE -> R.string.seek_forward_5
                                                            SongDurationTimeSkip.TEN -> R.string.seek_forward_10
                                                            SongDurationTimeSkip.FIFTEEN -> R.string.seek_forward_15
                                                            SongDurationTimeSkip.TWENTY -> R.string.seek_forward_20
                                                            SongDurationTimeSkip.TWENTYFIVE -> R.string.seek_forward_25
                                                            SongDurationTimeSkip.THIRTY -> R.string.seek_forward_30
                                                        }
                                                    ), Toast.LENGTH_LONG
                                                ).show()
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                playerConnection.player.seekTo(
                                                    playerConnection.player.currentPosition + when (songDurationTimeSkip) {
                                                        SongDurationTimeSkip.FIVE -> 5000
                                                        SongDurationTimeSkip.TEN -> 10000
                                                        SongDurationTimeSkip.FIFTEEN -> 15000
                                                        SongDurationTimeSkip.TWENTY -> 20000
                                                        SongDurationTimeSkip.TWENTYFIVE -> 25000
                                                        SongDurationTimeSkip.THIRTY -> 30000
                                                    }
                                                )
                                            }
                                        )
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        FilledTonalIconButton(
                            onClick = playerConnection.player::toggleRepeatMode,
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (repeatMode != REPEAT_MODE_OFF)
                                    MaterialTheme.colorScheme.secondaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (repeatMode != REPEAT_MODE_OFF)
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            enabled = true
                        ) {
                            Icon(
                                painter = painterResource(
                                    when (repeatMode) {
                                        REPEAT_MODE_OFF, REPEAT_MODE_ALL -> R.drawable.repeat
                                        REPEAT_MODE_ONE -> R.drawable.repeat_one
                                        else -> throw IllegalStateException()
                                    }
                                ),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
        if (playerBackground == PlayerBackgroundStyle.BLUR) {
            if (mediaMetadata?.isLocal == true) {
                mediaMetadata.let {
                    AsyncLocalImage(
                        image = { imageCache.getLocalThumbnail(it?.localPath) },
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(100.dp)
                    )
                }
            } else {
                AsyncImage(
                    model = mediaMetadata?.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(100.dp)
                )
            }
        } else if (playerBackground == PlayerBackgroundStyle.GRADIENT && gradientColors.size >= 2) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(gradientColors))
            )
        }
        if (playerBackground == PlayerBackgroundStyle.BLURMOV) {
            val infiniteTransition = rememberInfiniteTransition(label = "")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 100000,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Restart
                ), label = ""
            )
            if (mediaMetadata?.isLocal == true) {
                mediaMetadata?.let {
                    AsyncLocalImage(
                        image = { imageCache.getLocalThumbnail(it.localPath) },
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(100.dp)
                            .alpha(0.8f)
                            .background(if (useBlackBackground) Color.Black.copy(alpha = 0.5f) else Color.Transparent)
                            .rotate(rotation)
                    )
                }
            } else {
                val infiniteTransition = rememberInfiniteTransition(label = "")
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 100000,
                            easing = FastOutSlowInEasing
                        ),
                        repeatMode = RepeatMode.Restart
                    ), label = ""
                )
                AsyncImage(
                    model = mediaMetadata?.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(100.dp)
                        .alpha(0.8f)
                        .background(if (useBlackBackground) Color.Black.copy(alpha = 0.5f) else Color.Transparent)
                        .rotate(rotation)
                )
            }

            if (showLyrics) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                )
            }
        }
        if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            val vPadding = max(
                WindowInsets.safeDrawing.getTop(LocalDensity.current),
                WindowInsets.safeDrawing.getBottom(LocalDensity.current)
            )
            val vPaddingDp = with(LocalDensity.current) { vPadding.toDp() }
            val verticalInsets =
                WindowInsets(left = 0.dp, top = vPaddingDp, right = 0.dp, bottom = vPaddingDp)
            Row(
                modifier = Modifier
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                            .add(verticalInsets)
                    )
                    .fillMaxSize()
            ) {
                BoxWithConstraints(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .nestedScroll(state.preUpPostDownNestedScrollConnection)
                ) {
                    val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor

                    LazyHorizontalGrid(
                        state = thumbnailLazyGridState,
                        rows = GridCells.Fixed(1),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        flingBehavior = rememberSnapFlingBehavior(thumbnailSnapLayoutInfoProvider),
                        userScrollEnabled = state.isExpanded && !showLyrics
                    ) {
                        items(
                            items = mediaItems,
                            key = { it.id }
                        ) {
                            Thumbnail(
                                sliderPositionProvider = { sliderPosition },
                                modifier = Modifier
                                    .width(horizontalLazyGridItemWidth)
                                    .animateContentSize(),
                                contentScale = ContentScale.Crop,
                                showLyricsOnClick = true,
                                customMediaMetadata = it
                            )
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(if (showLyrics) 0.65f else 1f, false)
                        .animateContentSize()
                        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                ) {
                    Spacer(Modifier.weight(1f))

                    mediaMetadata?.let {
                        controlsContent(it)
                    }

                    Spacer(Modifier.weight(1f))
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                    .padding(bottom = queueSheetState.collapsedBound)
            ) {
                BoxWithConstraints(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .nestedScroll(state.preUpPostDownNestedScrollConnection)
                ) {
                    val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor

                    LazyHorizontalGrid(
                        state = thumbnailLazyGridState,
                        rows = GridCells.Fixed(1),
                        flingBehavior = rememberSnapFlingBehavior(thumbnailSnapLayoutInfoProvider),
                        userScrollEnabled = state.isExpanded && !showLyrics
                    ) {
                        items(
                            items = mediaItems,
                            key = { it.id }
                        ) {
                            Thumbnail(
                                modifier = Modifier
                                    .width(horizontalLazyGridItemWidth)
                                    .animateContentSize(),
                                contentScale = ContentScale.Crop,
                                sliderPositionProvider = { sliderPosition },
                                showLyricsOnClick = true,
                                customMediaMetadata = it
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                mediaMetadata?.let {
                    controlsContent(it)
                }

                Spacer(Modifier.height(24.dp))
            }
        }

        if (nowPlayingEnable && !showLyrics) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PlayerHorizontalPadding)
                        .padding(top = nowPlayingPadding.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ResizableIconButton(
                        icon = R.drawable.arrow_downward,
                        modifier = Modifier.size(25.dp),
                        color = onBackgroundColor,
                        onClick = {
                            state.collapseSoft()
                        }
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.now_playing),
                            style = MaterialTheme.typography.titleMedium,
                            overflow = TextOverflow.Ellipsis,
                            color = onBackgroundColor,
                            maxLines = 1
                        )
                        if (!queueTitle.isNullOrEmpty()) {
                            Text(
                                text = queueTitle.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                overflow = TextOverflow.Ellipsis,
                                color = onBackgroundColor,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee()
                            )
                        } else {
                            mediaMetadata?.let {
                                Text(
                                    text = it.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    overflow = TextOverflow.Ellipsis,
                                    color = onBackgroundColor,
                                    maxLines = 1,
                                    modifier = Modifier.basicMarquee()
                                )
                            }
                        }
                    }
                    Box(modifier = Modifier.width(25.dp)) {
                        if (mediaMetadata?.isLocal == false) {
                            ResizableIconButton(
                                icon = R.drawable.share,
                                modifier = Modifier.size(25.dp),
                                color = onBackgroundColor,
                                onClick = {
                                    val intent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "https://music.youtube.com/watch?v=${mediaMetadata?.id}"
                                        )
                                    }
                                    context.startActivity(Intent.createChooser(intent, null))
                                }
                            )
                        } else {
                            ResizableIconButton(
                                icon = R.drawable.info,
                                modifier = Modifier.size(25.dp),
                                color = onBackgroundColor,
                                onClick = {
                                    showDetailsDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }

        if (fullScreenLyrics) {
            Queue(
                state = queueSheetState,
                backgroundColor =
                    if (useBlackBackground) {
                        Color.Black
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                navController = navController,
                onBackgroundColor = onBackgroundColor,
            )
        }
    }
}
