package com.maloy.muzza.ui.screens.playlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastSumBy
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.maloy.muzza.LocalDownloadUtil
import com.maloy.muzza.LocalPlayerAwareWindowInsets
import com.maloy.muzza.LocalPlayerConnection
import com.maloy.muzza.R
import com.maloy.muzza.constants.AlbumThumbnailSize
import com.maloy.muzza.constants.PlaylistSongSortDescendingKey
import com.maloy.muzza.constants.PlaylistSongSortType
import com.maloy.muzza.constants.PlaylistSongSortTypeKey
import com.maloy.muzza.constants.ThumbnailCornerRadius
import com.maloy.muzza.db.entities.Song
import com.maloy.muzza.extensions.toMediaItem
import com.maloy.muzza.extensions.togglePlayPause
import com.maloy.muzza.playback.ExoDownloadService
import com.maloy.muzza.playback.queues.ListQueue
import com.maloy.muzza.ui.component.AutoResizeText
import com.maloy.muzza.ui.component.DefaultDialog
import com.maloy.muzza.ui.component.EmptyPlaceholder
import com.maloy.muzza.ui.component.FontSizeRange
import com.maloy.muzza.ui.component.LocalMenuState
import com.maloy.muzza.ui.component.SongListItem
import com.maloy.muzza.ui.component.SortHeader
import com.maloy.muzza.ui.menu.SongMenu
import com.maloy.muzza.ui.utils.backToMain
import com.maloy.muzza.utils.makeTimeString
import com.maloy.muzza.utils.rememberEnumPreference
import com.maloy.muzza.utils.rememberPreference
import com.maloy.muzza.viewmodels.AutoPlaylistViewModel
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AutoPlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: AutoPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val playlist = if (viewModel.playlist == "liked") "Liked " else "Offline "
    val songs by viewModel.likedSongs.collectAsState(null)
    val mutableSongs = remember {
        mutableStateListOf<Song>()
    }
    val likeLength = remember(songs) {
        songs?.fastSumBy { it.song.duration } ?: 0
    }
    val (sortType, onSortTypeChange) = rememberEnumPreference(PlaylistSongSortTypeKey, PlaylistSongSortType.CUSTOM)
    val (sortDescending, onSortDescendingChange) = rememberPreference(PlaylistSongSortDescendingKey, true)
    val downloadUtil = LocalDownloadUtil.current
    var downloadState by remember {
        mutableStateOf(Download.STATE_STOPPED)
    }
    LaunchedEffect(songs) {
        mutableSongs.apply {
            clear()
            songs?.let { addAll(it) }
        }
        if (songs?.isEmpty() == true) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songs?.all { downloads[it.song.id]?.state == Download.STATE_COMPLETED } == true)
                    Download.STATE_COMPLETED
                else if (songs?.all {
                        downloads[it.song.id]?.state == Download.STATE_QUEUED
                                || downloads[it.song.id]?.state == Download.STATE_DOWNLOADING
                                || downloads[it.song.id]?.state == Download.STATE_COMPLETED
                    } == true)
                    Download.STATE_DOWNLOADING
                else
                    Download.STATE_STOPPED
        }
    }
    var showRemoveDownloadDialog by remember {
        mutableStateOf(false)
    }
    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.remove_download_playlist_confirm, playlist + "Playlist"),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            },
            buttons = {
                TextButton(
                    onClick = { showRemoveDownloadDialog = false }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        songs!!.forEach { song ->
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                song.song.id,
                                false
                            )
                        }
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        )
    }
    val state = rememberLazyListState()
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = state,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            if (songs != null) {
                if (songs!!.isEmpty()) {
                    item {
                        EmptyPlaceholder(
                            icon = R.drawable.music_note,
                            text = stringResource(R.string.playlist_is_empty)
                        )
                    }
                } else {
                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = songs!![0].song.thumbnailUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(AlbumThumbnailSize)
                                        .clip(RoundedCornerShape(ThumbnailCornerRadius))
                                )
                                Column(
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    AutoResizeText(
                                        text = playlist,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSizeRange = FontSizeRange(16.sp, 22.sp)
                                    )
                                    Text(
                                        text = pluralStringResource(
                                            R.plurals.n_song,
                                            songs!!.size,
                                            songs!!.size
                                        ),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Normal
                                    )
                                    Text(
                                        text = makeTimeString(likeLength * 1000L),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Normal
                                    )
                                    Row {
                                        when (downloadState) {
                                            Download.STATE_COMPLETED -> {
                                                IconButton(
                                                    onClick = {
                                                        showRemoveDownloadDialog = true
                                                    }
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.offline),
                                                        contentDescription = null
                                                    )
                                                }
                                            }
                                            Download.STATE_DOWNLOADING -> {
                                                IconButton(
                                                    onClick = {
                                                        songs!!.forEach { song ->
                                                            DownloadService.sendRemoveDownload(
                                                                context,
                                                                ExoDownloadService::class.java,
                                                                song.song.id,
                                                                false
                                                            )
                                                        }
                                                    }
                                                ) {
                                                    CircularProgressIndicator(
                                                        strokeWidth = 2.dp,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }
                                            else -> {
                                                IconButton(
                                                    onClick = {
                                                        songs!!.forEach { song ->
                                                            val downloadRequest =
                                                                DownloadRequest.Builder(
                                                                    song.song.id,
                                                                    song.song.id.toUri()
                                                                )
                                                                    .setCustomCacheKey(song.song.id)
                                                                    .setData(song.song.title.toByteArray())
                                                                    .build()
                                                            DownloadService.sendAddDownload(
                                                                context,
                                                                ExoDownloadService::class.java,
                                                                downloadRequest,
                                                                false
                                                            )
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.download),
                                                        contentDescription = null
                                                    )
                                                }
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                playerConnection.addToQueue(
                                                    items = songs!!.map { it.toMediaItem() },
                                                )
                                            }
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.queue_music),
                                                contentDescription = null
                                            )
                                        }
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = "Auto Playlist",
                                                items = songs!!.map { it.toMediaItem() }
                                            )
                                        )
                                    },
                                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.play),
                                        contentDescription = null,
                                        modifier = Modifier.size(ButtonDefaults.IconSize)
                                    )
                                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                    Text(stringResource(R.string.play))
                                }
                                OutlinedButton(
                                    onClick = {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = playlist + "Playlist",
                                                items = songs!!.shuffled().map { it.toMediaItem() }
                                            )
                                        )
                                    },
                                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.shuffle),
                                        contentDescription = null,
                                        modifier = Modifier.size(ButtonDefaults.IconSize)
                                    )
                                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                    Text(stringResource(R.string.shuffle))
                                }
                            }
                        }
                    }
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 16.dp)
                        ) {
                            SortHeader(
                                sortType = sortType,
                                sortDescending = sortDescending,
                                onSortTypeChange = onSortTypeChange,
                                onSortDescendingChange = onSortDescendingChange,
                                sortTypeText = { sortType ->
                                    when (sortType) {
                                        PlaylistSongSortType.CUSTOM -> R.string.sort_by_custom
                                        PlaylistSongSortType.CREATE_DATE -> R.string.sort_by_create_date
                                        PlaylistSongSortType.NAME -> R.string.sort_by_name
                                        PlaylistSongSortType.ARTIST -> R.string.sort_by_artist
                                        PlaylistSongSortType.PLAY_TIME -> R.string.sort_by_play_time
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                itemsIndexed(
                    items = mutableSongs,
                    key = { _, song -> song.id }
                ) { index, song ->
                    SongListItem(
                        song = song,
                        isActive = song.song.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        showInLibraryIcon = true,
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    menuState.show {
                                        SongMenu(
                                            originalSong = song,
                                            navController = navController,
                                            onDismiss = menuState::dismiss
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.more_vert),
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable (
                                onClick = {
                                    if (song.song.id == mediaMetadata?.id) {
                                        playerConnection.player.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = playlist + "Playlist",
                                                items = songs!!.map { it.toMediaItem() },
                                                startIndex = index
                                            )
                                        )
                                    }
                                },
                                onLongClick = {
                                    menuState.show {
                                        SongMenu(
                                            originalSong = song,
                                            navController = navController,
                                            onDismiss = menuState::dismiss
                                        )
                                    }
                                }
                            )
                    )
                }
            }
        }
        TopAppBar(
            title = { playlist + "Playlist" },
            navigationIcon = {
                com.maloy.muzza.ui.component.IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain
                ) {
                    Icon(
                        painterResource(R.drawable.arrow_back),
                        contentDescription = null
                    )
                }
            },
            scrollBehavior = scrollBehavior
        )
    }
}