package com.zionhuang.music.ui.player

import android.content.Intent
import android.media.audiofx.AudioEffect
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DismissValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SwipeToDismiss
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDismissState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.navigation.NavController
import com.zionhuang.music.LocalDatabase
import com.zionhuang.music.LocalDownloadUtil
import com.zionhuang.music.LocalPlayerConnection
import com.zionhuang.music.R
import com.zionhuang.music.constants.ListItemHeight
import com.zionhuang.music.constants.ShowLyricsKey
import com.zionhuang.music.db.entities.PlaylistSongMap
import com.zionhuang.music.extensions.metadata
import com.zionhuang.music.extensions.move
import com.zionhuang.music.extensions.togglePlayPause
import com.zionhuang.music.models.MediaMetadata
import com.zionhuang.music.playback.ExoDownloadService
import com.zionhuang.music.playback.PlayerConnection
import com.zionhuang.music.ui.component.BigSeekBar
import com.zionhuang.music.ui.component.BottomSheet
import com.zionhuang.music.ui.component.BottomSheetState
import com.zionhuang.music.ui.component.DownloadGridMenu
import com.zionhuang.music.ui.component.GridMenu
import com.zionhuang.music.ui.component.GridMenuItem
import com.zionhuang.music.ui.component.ListDialog
import com.zionhuang.music.ui.component.LocalMenuState
import com.zionhuang.music.ui.component.MediaMetadataListItem
import com.zionhuang.music.ui.menu.AddToPlaylistDialog
import com.zionhuang.music.ui.utils.reordering.ReorderingLazyColumn
import com.zionhuang.music.ui.utils.reordering.draggedItem
import com.zionhuang.music.ui.utils.reordering.rememberReorderingState
import com.zionhuang.music.ui.utils.reordering.reorder
import com.zionhuang.music.utils.makeTimeString
import com.zionhuang.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun Queue(
    state: BottomSheetState,
    playerBottomSheetState: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val menuState = LocalMenuState.current

    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()

    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val currentFormat by playerConnection.currentFormat.collectAsState(initial = null)

    var showLyrics by rememberPreference(ShowLyricsKey, defaultValue = false)

    val sleepTimerEnabled = remember(playerConnection.service.sleepTimerTriggerTime, playerConnection.service.pauseWhenSongEnd) {
        playerConnection.service.sleepTimerTriggerTime != -1L || playerConnection.service.pauseWhenSongEnd
    }

    var sleepTimerTimeLeft by remember {
        mutableStateOf(0L)
    }

    LaunchedEffect(sleepTimerEnabled) {
        if (sleepTimerEnabled) {
            while (isActive) {
                sleepTimerTimeLeft = if (playerConnection.service.pauseWhenSongEnd) {
                    playerConnection.player.duration - playerConnection.player.currentPosition
                } else {
                    playerConnection.service.sleepTimerTriggerTime - System.currentTimeMillis()
                }
                delay(1000L)
            }
        }
    }

    var showSleepTimerDialog by remember {
        mutableStateOf(false)
    }

    var sleepTimerValue by remember {
        mutableStateOf(30f)
    }
    if (showSleepTimerDialog) {
        AlertDialog(
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = { showSleepTimerDialog = false },
            icon = { Icon(painter = painterResource(R.drawable.bedtime), contentDescription = null) },
            title = { Text(stringResource(R.string.sleep_timer)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSleepTimerDialog = false
                        playerConnection.service.setSleepTimer(sleepTimerValue.roundToInt())
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSleepTimerDialog = false }
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = pluralStringResource(R.plurals.minute, sleepTimerValue.roundToInt(), sleepTimerValue.roundToInt()),
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Slider(
                        value = sleepTimerValue,
                        onValueChange = { sleepTimerValue = it },
                        valueRange = 5f..120f,
                        steps = (120 - 5) / 5 - 1,
                    )

                    OutlinedButton(
                        onClick = {
                            showSleepTimerDialog = false
                            playerConnection.service.setSleepTimer(-1)
                        }
                    ) {
                        Text(stringResource(R.string.end_of_song))
                    }
                }
            }
        )
    }

    var showDetailsDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showDetailsDialog) {
        AlertDialog(
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = { showDetailsDialog = false },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.info),
                    contentDescription = null
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showDetailsDialog = false }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .sizeIn(minWidth = 280.dp, maxWidth = 560.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    listOf(
                        stringResource(R.string.song_title) to mediaMetadata?.title,
                        stringResource(R.string.song_artists) to mediaMetadata?.artists?.joinToString { it.name },
                        stringResource(R.string.media_id) to mediaMetadata?.id,
                        "Itag" to currentFormat?.itag?.toString(),
                        stringResource(R.string.mime_type) to currentFormat?.mimeType,
                        stringResource(R.string.codecs) to currentFormat?.codecs,
                        stringResource(R.string.bitrate) to currentFormat?.bitrate?.let { "${it / 1000} Kbps" },
                        stringResource(R.string.sample_rate) to currentFormat?.sampleRate?.let { "$it Hz" },
                        stringResource(R.string.loudness) to currentFormat?.loudnessDb?.let { "$it dB" },
                        stringResource(R.string.volume) to "${(playerConnection.player.volume * 100).toInt()}%",
                        stringResource(R.string.file_size) to currentFormat?.contentLength?.let { Formatter.formatShortFileSize(context, it) }
                    ).forEach { (label, text) ->
                        val displayText = text ?: stringResource(R.string.unknown)
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(displayText))
                                    Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                                }
                            )
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        )
    }

    BottomSheet(
        state = state,
        backgroundColor = MaterialTheme.colorScheme.surfaceColorAtElevation(NavigationBarDefaults.Elevation),
        collapsedColor = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier,
        collapsedContent = {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.systemBars
                            .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                    )
            ) {
                IconButton(onClick = { state.expandSoft() }) {
                    Icon(
                        painter = painterResource(R.drawable.queue_music),
                        contentDescription = null
                    )
                }
                IconButton(onClick = { showLyrics = !showLyrics }) {
                    Icon(
                        painter = painterResource(R.drawable.lyrics),
                        contentDescription = null,
                        modifier = Modifier.alpha(if (showLyrics) 1f else 0.5f)
                    )
                }
                AnimatedContent(
                    targetState = sleepTimerEnabled
                ) { sleepTimerEnabled ->
                    if (sleepTimerEnabled) {
                        Text(
                            text = makeTimeString(sleepTimerTimeLeft),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable(onClick = playerConnection.service::clearSleepTimer)
                                .padding(8.dp)
                        )
                    } else {
                        IconButton(onClick = { showSleepTimerDialog = true }) {
                            Icon(
                                painter = painterResource(R.drawable.bedtime),
                                contentDescription = null
                            )
                        }
                    }
                }
                IconButton(onClick = playerConnection::toggleLibrary) {
                    Icon(
                        painter = painterResource(if (currentSong?.song?.inLibrary != null) R.drawable.library_add_check else R.drawable.library_add),
                        contentDescription = null
                    )
                }
                IconButton(
                    onClick = {
                        menuState.show {
                            PlayerMenu(
                                mediaMetadata = mediaMetadata,
                                navController = navController,
                                playerBottomSheetState = playerBottomSheetState,
                                playerConnection = playerConnection,
                                onShowDetailsDialog = { showDetailsDialog = true },
                                onDismiss = menuState::dismiss
                            )
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_horiz),
                        contentDescription = null
                    )
                }
            }
        }
    ) {
        val queueTitle by playerConnection.queueTitle.collectAsState()
        val queueWindows by playerConnection.queueWindows.collectAsState()
        val queueLength = remember(queueWindows) {
            queueWindows.sumOf { it.mediaItem.metadata!!.duration }
        }

        val coroutineScope = rememberCoroutineScope()
        val reorderingState = rememberReorderingState(
            lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = currentWindowIndex),
            key = queueWindows,
            onDragEnd = { currentIndex, newIndex ->
                if (!playerConnection.player.shuffleModeEnabled) {
                    playerConnection.player.moveMediaItem(currentIndex, newIndex)
                } else {
                    playerConnection.player.setShuffleOrder(
                        DefaultShuffleOrder(
                            queueWindows.map { it.firstPeriodIndex }.toMutableList().move(currentIndex, newIndex).toIntArray(),
                            System.currentTimeMillis()
                        )
                    )
                }
            },
            extraItemCount = 0
        )

        ReorderingLazyColumn(
            reorderingState = reorderingState,
            contentPadding = WindowInsets.systemBars
                .add(
                    WindowInsets(
                        top = ListItemHeight,
                        bottom = ListItemHeight
                    )
                )
                .asPaddingValues(),
            modifier = Modifier.nestedScroll(state.preUpPostDownNestedScrollConnection)
        ) {
            itemsIndexed(
                items = queueWindows,
                key = { _, item -> item.uid.hashCode() }
            ) { index, window ->
                val currentItem by rememberUpdatedState(window)
                val dismissState = rememberDismissState(
                    positionalThreshold = { totalDistance ->
                        totalDistance
                    },
                    confirmValueChange = { dismissValue ->
                        if (dismissValue == DismissValue.DismissedToEnd || dismissValue == DismissValue.DismissedToStart) {
                            playerConnection.player.removeMediaItem(currentItem.firstPeriodIndex)
                        }
                        true
                    }
                )
                SwipeToDismiss(
                    state = dismissState,
                    background = {},
                    dismissContent = {
                        MediaMetadataListItem(
                            mediaMetadata = window.mediaItem.metadata!!,
                            isActive = index == currentWindowIndex,
                            isPlaying = isPlaying,
                            trailingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.drag_handle),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .reorder(
                                            reorderingState = reorderingState,
                                            index = index
                                        )
                                        .clickable(
                                            enabled = false,
                                            onClick = {}
                                        )
                                        .padding(8.dp)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch(Dispatchers.Main) {
                                        if (index == currentWindowIndex) {
                                            playerConnection.player.togglePlayPause()
                                        } else {
                                            playerConnection.player.seekToDefaultPosition(window.firstPeriodIndex)
                                            playerConnection.player.playWhenReady = true
                                        }
                                    }
                                }
                                .draggedItem(
                                    reorderingState = reorderingState,
                                    index = index
                                )
                        )
                    }
                )
            }
        }

        Box(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme
                        .surfaceColorAtElevation(NavigationBarDefaults.Elevation)
                        .copy(alpha = 0.95f)
                )
                .windowInsetsPadding(
                    WindowInsets.systemBars
                        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                )
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Text(
                    text = queueTitle.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = pluralStringResource(R.plurals.n_song, queueWindows.size, queueWindows.size),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = makeTimeString(queueLength * 1000L),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsState()

        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f))
                .fillMaxWidth()
                .height(
                    ListItemHeight +
                            WindowInsets.systemBars
                                .asPaddingValues()
                                .calculateBottomPadding()
                )
                .align(Alignment.BottomCenter)
                .clickable {
                    state.collapseSoft()
                }
                .windowInsetsPadding(
                    WindowInsets.systemBars
                        .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                )
                .padding(12.dp)
        ) {
            IconButton(
                modifier = Modifier.align(Alignment.CenterStart),
                onClick = {
                    reorderingState.coroutineScope.launch {
                        reorderingState.lazyListState.animateScrollToItem(
                            if (playerConnection.player.shuffleModeEnabled) playerConnection.player.currentMediaItemIndex else 0
                        )
                    }.invokeOnCompletion {
                        playerConnection.player.shuffleModeEnabled = !playerConnection.player.shuffleModeEnabled
                    }
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.shuffle),
                    contentDescription = null,
                    modifier = Modifier.alpha(if (shuffleModeEnabled) 1f else 0.5f)
                )
            }

            Icon(
                painter = painterResource(R.drawable.expand_more),
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
fun PlayerMenu(
    mediaMetadata: MediaMetadata?,
    navController: NavController,
    playerBottomSheetState: BottomSheetState,
    playerConnection: PlayerConnection,
    onShowDetailsDialog: () -> Unit,
    onDismiss: () -> Unit,
) {
    mediaMetadata ?: return
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerVolume = playerConnection.service.playerVolume.collectAsState()
    val activityResultLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    val download by LocalDownloadUtil.current.getDownload(mediaMetadata.id).collectAsState(initial = null)

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onAdd = { playlist ->
            database.transaction {
                insert(mediaMetadata)
                insert(
                    PlaylistSongMap(
                        songId = mediaMetadata.id,
                        playlistId = playlist.id,
                        position = playlist.songCount
                    )
                )
            }
        },
        onDismiss = {
            showChoosePlaylistDialog = false
        }
    )

    var showSelectArtistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showSelectArtistDialog) {
        ListDialog(
            onDismiss = { showSelectArtistDialog = false }
        ) {
            items(mediaMetadata.artists) { artist ->
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .height(ListItemHeight)
                        .clickable {
                            navController.navigate("artist/${artist.id}")
                            showSelectArtistDialog = false
                            playerBottomSheetState.collapseSoft()
                            onDismiss()
                        }
                        .padding(horizontal = 24.dp),
                ) {
                    Text(
                        text = artist.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 12.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.volume_up),
            contentDescription = null,
            modifier = Modifier.size(28.dp)
        )

        BigSeekBar(
            progressProvider = playerVolume::value,
            onProgressChange = { playerConnection.service.playerVolume.value = it },
            modifier = Modifier.weight(1f)
        )
    }

    GridMenu(
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
        )
    ) {
        GridMenuItem(
            icon = R.drawable.radio,
            title = R.string.start_radio
        ) {
            playerConnection.service.startRadioSeamlessly()
            onDismiss()
        }
        GridMenuItem(
            icon = R.drawable.playlist_add,
            title = R.string.add_to_playlist
        ) {
            showChoosePlaylistDialog = true
        }
        DownloadGridMenu(
            state = download?.state,
            onDownload = {
                database.transaction {
                    insert(mediaMetadata)
                }
                val downloadRequest = DownloadRequest.Builder(mediaMetadata.id, mediaMetadata.id.toUri())
                    .setCustomCacheKey(mediaMetadata.id)
                    .setData(mediaMetadata.title.toByteArray())
                    .build()
                DownloadService.sendAddDownload(
                    context,
                    ExoDownloadService::class.java,
                    downloadRequest,
                    false
                )
            },
            onRemoveDownload = {
                DownloadService.sendRemoveDownload(
                    context,
                    ExoDownloadService::class.java,
                    mediaMetadata.id,
                    false
                )
            }
        )
        GridMenuItem(
            icon = R.drawable.artist,
            title = R.string.view_artist
        ) {
            if (mediaMetadata.artists.size == 1) {
                navController.navigate("artist/${mediaMetadata.artists[0].id}")
                playerBottomSheetState.collapseSoft()
                onDismiss()
            } else {
                showSelectArtistDialog = true
            }
        }
        if (mediaMetadata.album != null) {
            GridMenuItem(
                icon = R.drawable.album,
                title = R.string.view_album
            ) {
                navController.navigate("album/${mediaMetadata.album.id}")
                playerBottomSheetState.collapseSoft()
                onDismiss()
            }
        }
        GridMenuItem(
            icon = R.drawable.share,
            title = R.string.share
        ) {
            val intent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${mediaMetadata.id}")
            }
            context.startActivity(Intent.createChooser(intent, null))
            onDismiss()
        }
        GridMenuItem(
            icon = R.drawable.info,
            title = R.string.details
        ) {
            onShowDetailsDialog()
            onDismiss()
        }
        GridMenuItem(
            icon = R.drawable.equalizer,
            title = R.string.equalizer
        ) {
            val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, playerConnection.player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                activityResultLauncher.launch(intent)
            }
            onDismiss()
        }
    }
}
