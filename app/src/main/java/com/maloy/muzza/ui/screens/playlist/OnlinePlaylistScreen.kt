package com.maloy.muzza.ui.screens.playlist

import android.annotation.SuppressLint
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastSumBy
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.maloy.innertube.models.SongItem
import com.maloy.muzza.LocalDatabase
import com.maloy.muzza.LocalPlayerAwareWindowInsets
import com.maloy.muzza.LocalPlayerConnection
import com.maloy.muzza.LocalSyncUtils
import com.maloy.muzza.R
import com.maloy.muzza.constants.AccountImageUrlKey
import com.maloy.muzza.constants.AccountNameKey
import com.maloy.muzza.constants.AlbumThumbnailSize
import com.maloy.muzza.constants.HideExplicitKey
import com.maloy.muzza.constants.ListItemHeight
import com.maloy.muzza.constants.SongSortDescendingKey
import com.maloy.muzza.constants.SongSortType
import com.maloy.muzza.constants.SongSortTypeKey
import com.maloy.muzza.constants.ThumbnailCornerRadius
import com.maloy.muzza.db.entities.PlaylistEntity
import com.maloy.muzza.db.entities.PlaylistSongMap
import com.maloy.muzza.db.entities.Song
import com.maloy.muzza.extensions.move
import com.maloy.muzza.extensions.toMediaItemWithPlaylist
import com.maloy.muzza.extensions.togglePlayPause
import com.maloy.muzza.models.toMediaMetadata
import com.maloy.muzza.playback.ExoDownloadService
import com.maloy.muzza.playback.queues.ListQueue
import com.maloy.muzza.playback.queues.YouTubePlaylistQueue
import com.maloy.muzza.ui.component.AutoResizeText
import com.maloy.muzza.ui.component.DefaultDialog
import com.maloy.muzza.ui.component.EmptyPlaceholder
import com.maloy.muzza.ui.component.FontSizeRange
import com.maloy.muzza.ui.component.HideOnScrollFAB
import com.maloy.muzza.ui.component.IconButton
import com.maloy.muzza.ui.component.LazyColumnScrollbar
import com.maloy.muzza.ui.component.LocalMenuState
import com.maloy.muzza.ui.component.SortHeader
import com.maloy.muzza.ui.component.YouTubeListItem
import com.maloy.muzza.ui.component.shimmer.ButtonRowPlaceHolder
import com.maloy.muzza.ui.component.shimmer.ListItemPlaceHolder
import com.maloy.muzza.ui.component.shimmer.PlaylistAlbumItemPlaceHolder
import com.maloy.muzza.ui.component.shimmer.ShimmerHost
import com.maloy.muzza.ui.component.shimmer.TextPlaceholder
import com.maloy.muzza.ui.menu.YouTubePlaylistMenu
import com.maloy.muzza.ui.menu.YouTubePlaylistMenuInPlaylistScreen
import com.maloy.muzza.ui.menu.YouTubeSongMenu
import com.maloy.muzza.ui.menu.YouTubeSongSelectionMenu
import com.maloy.muzza.ui.utils.backToMain
import com.maloy.muzza.utils.isInternetAvailable
import com.maloy.muzza.utils.makeTimeString
import com.maloy.muzza.utils.rememberEnumPreference
import com.maloy.muzza.utils.rememberPreference
import com.maloy.muzza.utils.rememberVoiceInput
import com.maloy.muzza.viewmodels.OnlinePlaylistViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, FlowPreview::class)
@Composable
fun OnlinePlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: OnlinePlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val playlist by viewModel.playlist.collectAsState()
    val authors = viewModel.authors?: return
    val songs by viewModel.playlistSongs.collectAsState()
    val dbPlaylist by viewModel.dbPlaylist.collectAsState()

    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val syncUtils = LocalSyncUtils.current

    val isLoading by viewModel.isLoading.collectAsState()

    val accountName by rememberPreference(AccountNameKey, "")
    val accountImageUrl by rememberPreference(AccountImageUrlKey, "")

    val (sortType, onSortTypeChange) = rememberEnumPreference(
        SongSortTypeKey,
        SongSortType.CREATE_DATE
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(SongSortDescendingKey, true)

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    val startVoiceInput = rememberVoiceInput(
        onResult = { recognizedText ->
            query = TextFieldValue(recognizedText)
        }
    )
    val filteredSongs = remember(songs, query, hideExplicit) {
        songs.mapIndexed { index, song -> index to song }
            .filter { (_, song) ->
                (!hideExplicit || !song.explicit) && (query.text.isEmpty() ||
                        song.title.contains(query.text, ignoreCase = true) ||
                        song.artists.any { it.name.contains(query.text, ignoreCase = true) })
            }
    }
    val songsLength = remember(songs) {
        songs.fastSumBy { it.duration!! }
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.debounce { 100L }
            .collectLatest { lastVisibleIndex ->
                if (lastVisibleIndex != null && lastVisibleIndex >= songs.size - 5) {
                    viewModel.loadMoreSongs()
                }
            }
    }

    val lazyChecker by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0
        }
    }

    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    val mutableSongs = remember {
        mutableStateListOf<Song>()
    }
    val reorderableState = rememberReorderableLazyListState(
        onMove = { from, to ->
            mutableSongs.move(from.index - 2, to.index - 2)
        },
        lazyListState = lazyListState,
        scrollThresholdPadding = WindowInsets.systemBars.add(
            WindowInsets(
                top = ListItemHeight,
                bottom = ListItemHeight
            )
        ).asPaddingValues()
    )

    val downloadState by remember {
        mutableIntStateOf(Download.STATE_STOPPED)
    }

    var showRemoveDownloadDialog by remember {
        mutableStateOf(false)
    }

    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            icon = { Icon(Icons.Rounded.CloudOff, null) },
            content = {
                Text(
                    text = stringResource(
                        R.string.remove_download_playlist_confirm,
                        playlist?.title!!
                    ),
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
                        database.transaction {
                            dbPlaylist?.id?.let { clearPlaylist(it) }
                        }

                        songs.forEach { song ->
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                song.id,
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

    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection = rememberSaveable(
        saver = listSaver<MutableList<Int>, Int>(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf() }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
    }
    val onExitSearchingMode = {
        isSearching = false
        query = TextFieldValue("")
    }
    if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    } else if (isSearching) {
        BackHandler(onBack = onExitSearchingMode)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pullToRefresh(
                state = pullRefreshState,
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh
            ),
        contentAlignment = Alignment.TopStart
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime)
                .asPaddingValues()
        ) {
            if (filteredSongs.isEmpty() && isSearching) {
                item {
                    EmptyPlaceholder(
                        icon = R.drawable.search,
                        text = stringResource(R.string.no_results_found)
                    )
                }
            }
            playlist.let { playlist ->
                if (playlist != null) {
                    if (!isSearching) {
                        item {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(12.dp)
                            ) {
                                AsyncImage(
                                    model = playlist.thumbnail,
                                    contentScale = ContentScale.Crop,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(AlbumThumbnailSize)
                                        .clip(RoundedCornerShape(ThumbnailCornerRadius))
                                        .align(alignment = Alignment.CenterHorizontally)
                                        .aspectRatio(1f)
                                )

                                Spacer(Modifier.width(12.dp))

                                Column(
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    AutoResizeText(
                                        text = playlist.title,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSizeRange = FontSizeRange(16.sp, 22.sp),
                                        modifier = Modifier
                                            .fillMaxWidth(0.8f)
                                            .combinedClickable(
                                                onClick = {
                                                    menuState.show {
                                                        YouTubePlaylistMenu(
                                                            navController = navController,
                                                            playlist = playlist,
                                                            songs = songs,
                                                            coroutineScope = coroutineScope,
                                                            onDismiss = menuState::dismiss
                                                        )
                                                    }
                                                }
                                            )
                                    )

                                    Spacer(Modifier.height(8.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (playlist.id == "LM") {
                                            if (accountImageUrl.isNotEmpty()) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(RoundedCornerShape(16.dp))
                                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                                ) {
                                                    AsyncImage(
                                                        model = accountImageUrl,
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .clip(RoundedCornerShape(16.dp))
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                            }

                                            if (accountName.isNotEmpty()) {
                                                Text(
                                                    text = accountName,
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.Normal,
                                                        color = MaterialTheme.colorScheme.onBackground
                                                    )
                                                )
                                            }
                                        } else if (playlist.id != "LM" && authors.isNotEmpty()) {
                                            Text(
                                                text = authors,
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Normal,
                                                    color = MaterialTheme.colorScheme.onBackground
                                                )
                                            )
                                        }
                                    }

                                    Text(
                                        text = makeTimeString(songsLength * 1000L),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Normal
                                    )

                                    Spacer(Modifier.height(12.dp))

                                    Row {
                                        if (playlist.id != "LM") {
                                            Button(
                                                onClick = {
                                                    if (dbPlaylist?.playlist == null) {
                                                        database.transaction {
                                                            val playlistEntity = PlaylistEntity(
                                                                name = playlist.title,
                                                                playlistAuthors = authors,
                                                                browseId = playlist.id,
                                                                thumbnailUrl = playlist.thumbnail,
                                                                isEditable = true,
                                                                remoteSongCount = playlist.songCountText?.let {
                                                                    Regex(
                                                                        """\d+"""
                                                                    ).find(it)?.value?.toIntOrNull()
                                                                },
                                                                playEndpointParams = playlist.playEndpoint?.params,
                                                                shuffleEndpointParams = playlist.shuffleEndpoint?.params,
                                                                radioEndpointParams = playlist.radioEndpoint?.params
                                                            ).toggleLike()
                                                            insert(playlistEntity)
                                                            songs.map(SongItem::toMediaMetadata)
                                                                .onEach(::insert)
                                                                .mapIndexed { index, song ->
                                                                    PlaylistSongMap(
                                                                        songId = song.id,
                                                                        playlistId = playlistEntity.id,
                                                                        position = index
                                                                    )
                                                                }
                                                                .forEach(::insert)
                                                        }
                                                    } else {
                                                        database.transaction {
                                                            update(dbPlaylist!!.playlist.toggleLike())
                                                            update(dbPlaylist!!.playlist.localToggleLike())
                                                        }
                                                    }
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(4.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                            ) {
                                                Icon(
                                                    painter = painterResource(
                                                        if (dbPlaylist?.playlist?.bookmarkedAt != null) R.drawable.favorite else R.drawable.favorite_border
                                                    ),
                                                    contentDescription = null
                                                )
                                            }
                                        }

                                        if (dbPlaylist != null) {
                                            when (downloadState) {
                                                Download.STATE_COMPLETED -> {
                                                    Button(
                                                        onClick = {
                                                            showRemoveDownloadDialog = true
                                                        },
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .padding(4.dp)
                                                            .clip(RoundedCornerShape(12.dp))
                                                    ) {
                                                        Icon(
                                                            painterResource(R.drawable.offline),
                                                            contentDescription = null
                                                        )
                                                    }
                                                }

                                                Download.STATE_DOWNLOADING -> {
                                                    Button(
                                                        onClick = {
                                                            songs.forEach { song ->
                                                                DownloadService.sendRemoveDownload(
                                                                    context,
                                                                    ExoDownloadService::class.java,
                                                                    song.id,
                                                                    false
                                                                )
                                                            }
                                                        },
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .padding(4.dp)
                                                            .clip(RoundedCornerShape(12.dp))
                                                    ) {
                                                        CircularProgressIndicator(
                                                            strokeWidth = 2.dp,
                                                            modifier = Modifier.size(24.dp),
                                                            color = MaterialTheme.colorScheme.surfaceContainer
                                                        )
                                                    }
                                                }

                                                else -> {
                                                    Button(
                                                        onClick = {
                                                            viewModel.viewModelScope.launch(
                                                                Dispatchers.IO
                                                            ) {
                                                                syncUtils.syncPlaylist(
                                                                    playlist.id,
                                                                    dbPlaylist!!.id
                                                                )
                                                            }

                                                            songs.forEach { song ->
                                                                val downloadRequest =
                                                                    DownloadRequest.Builder(
                                                                        song.id,
                                                                        song.id.toUri()
                                                                    )
                                                                        .setCustomCacheKey(song.id)
                                                                        .setData(song.title.toByteArray())
                                                                        .build()
                                                                DownloadService.sendAddDownload(
                                                                    context,
                                                                    ExoDownloadService::class.java,
                                                                    downloadRequest,
                                                                    false
                                                                )
                                                            }
                                                        },
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .padding(4.dp)
                                                            .clip(RoundedCornerShape(12.dp))
                                                    ) {
                                                        Icon(
                                                            painterResource(R.drawable.download),
                                                            contentDescription = null
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        if (playlist.id == "LM") {
                                            playlist.radioEndpoint?.let { radioEndpoint ->
                                                Button(
                                                    onClick = {
                                                        playerConnection.playQueue(
                                                            YouTubePlaylistQueue(
                                                                radioEndpoint,
                                                                playlistId = playlist.id
                                                            )
                                                        )
                                                    },
                                                    enabled = !isLoading,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .padding(4.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.radio),
                                                        contentDescription = null
                                                    )
                                                }
                                            }
                                        } else {
                                            Button(
                                                onClick = {
                                                    playerConnection.addToQueue(songs.map {
                                                        it.toMediaItemWithPlaylist(
                                                            playlist.id
                                                        )
                                                    })
                                                },
                                                enabled = !isLoading,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(4.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.queue_music),
                                                    contentDescription = null
                                                )
                                            }
                                        }
                                        if (playlist.id == "LM") {
                                            Button(
                                                onClick = {
                                                    playerConnection.addToQueue(songs.map {
                                                        it.toMediaItemWithPlaylist(
                                                            playlist.id
                                                        )
                                                    })
                                                },
                                                enabled = !isLoading,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(4.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                            ) {
                                                Icon(
                                                    painterResource(R.drawable.queue_music),
                                                    contentDescription = null
                                                )
                                            }
                                        }

                                        if (playlist.id != "LM") {
                                            Button(
                                                onClick = {
                                                    val intent = Intent().apply {
                                                        action = Intent.ACTION_SEND
                                                        type = "text/plain"
                                                        putExtra(
                                                            Intent.EXTRA_TEXT,
                                                            playlist.shareLink
                                                        )
                                                    }
                                                    context.startActivity(
                                                        Intent.createChooser(
                                                            intent,
                                                            null
                                                        )
                                                    )
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(4.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.share),
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
                                                    title = playlist.title,
                                                    items = songs.map {
                                                        it.toMediaItemWithPlaylist(
                                                            playlist.id
                                                        )
                                                    }
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
                                    Button(
                                        onClick = {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = playlist.title,
                                                    items = songs.shuffled()
                                                        .map { it.toMediaItemWithPlaylist(playlist.id) }
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
                    }

                    if (filteredSongs.size > 1) {
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
                                            SongSortType.CREATE_DATE -> R.string.sort_by_create_date
                                            SongSortType.NAME -> R.string.sort_by_name
                                            SongSortType.ARTIST -> R.string.sort_by_artist
                                            SongSortType.PLAY_TIME -> R.string.sort_by_length
                                        }
                                    }
                                )
                            }
                        }
                    }

                    items(
                        items = filteredSongs,
                        key = { (index, _) -> index }
                    ) { (index, song) ->
                        val onCheckedChange: (Boolean) -> Unit = {
                            if (it) {
                                selection.add(index)
                            } else {
                                selection.remove(index)
                            }
                        }
                        if (index == 0) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = pluralStringResource(
                                        R.plurals.n_song,
                                        filteredSongs.size,
                                        filteredSongs.size
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }

                        ReorderableItem(
                            state = reorderableState,
                            key = filteredSongs
                        ) {
                            YouTubeListItem(
                                item = song,
                                isActive = mediaMetadata?.id == song.id,
                                isPlaying = isPlaying,
                                trailingContent = {
                                    if (inSelectMode) {
                                        Checkbox(
                                            checked = index in selection,
                                            onCheckedChange = onCheckedChange
                                        )
                                    } else {
                                        IconButton(
                                            onClick = {
                                                menuState.show {
                                                    YouTubeSongMenu(
                                                        song = song,
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
                                    }
                                },
                                modifier = Modifier
                                    .combinedClickable(
                                        enabled = !hideExplicit || !song.explicit,
                                        onClick = {
                                            if (inSelectMode) {
                                                onCheckedChange(index !in selection)
                                            } else if (song.id == mediaMetadata?.id) {
                                                playerConnection.player.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    ListQueue(
                                                        title = playlist.title,
                                                        items = songs.map {
                                                            it.toMediaItemWithPlaylist(
                                                                playlist.id
                                                            )
                                                        },
                                                        startIndex = songs.indexOf(song)
                                                    )
                                                )
                                            }
                                        },
                                        onLongClick = {
                                            if (!inSelectMode) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                inSelectMode = true
                                                onCheckedChange(true)
                                            }
                                        }
                                    )
                                    .alpha(if (hideExplicit && song.explicit) 0.3f else 1f)
                                    .animateItem()
                            )
                        }
                    }
                    if (viewModel.continuation != null && songs.isNotEmpty()) {
                        item {
                            ShimmerHost {
                                repeat(2) {
                                    ListItemPlaceHolder()
                                }
                            }
                        }
                    }
                } else {
                    item {
                        ShimmerHost {
                            Column(Modifier.padding(12.dp)) {
                                PlaylistAlbumItemPlaceHolder()

                                Spacer(Modifier.padding(8.dp))

                                ButtonRowPlaceHolder(buttonCount = if (playlist?.id == "LM") 2 else 3)

                                Spacer(Modifier.padding(8.dp))

                                ButtonRowPlaceHolder(buttonCount = 2)

                                Spacer(Modifier.padding(8.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextPlaceholder(
                                        modifier = Modifier
                                            .fillMaxWidth(0.4f)
                                            .height(20.dp)
                                    )
                                }
                            }
                            repeat(6) {
                                ListItemPlaceHolder()
                            }
                        }
                    }
                }
            }
        }
        if (filteredSongs.isNotEmpty() && isInternetAvailable(context) && !isSearching) {
            Indicator(
                isRefreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
            )
        }
        LazyColumnScrollbar(
            visible = lazyChecker,
            state = lazyListState
        )
        HideOnScrollFAB(
            visible = lazyChecker && !isSearching && !inSelectMode,
            lazyListState = lazyListState,
            icon = R.drawable.play,
            onClick = {
                playerConnection.playQueue(
                    ListQueue(
                        title = playlist!!.title,
                        items = songs.map {
                            it.toMediaItemWithPlaylist(
                                playlist!!.id
                            )
                        }
                    )
                )
            }
        )
        CenterAlignedTopAppBar(
            title = {
                if (inSelectMode) {
                    Text(pluralStringResource(R.plurals.n_selected, selection.size, selection.size))
                } else if (isSearching) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.search),
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        trailingIcon = {
                            if (query.text.isEmpty()) {
                                IconButton(
                                    onClick = startVoiceInput
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.mic),
                                        contentDescription = null
                                    )
                                }
                            }
                            if (query.text.isNotEmpty()) {
                                IconButton(
                                    onClick = { query = TextFieldValue("") }
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.close),
                                        contentDescription = null
                                    )
                                }
                            }
                        }
                    )
                } else {
                    if (lazyChecker) Text(playlist?.title.orEmpty())
                }
            },
            navigationIcon = {
                if (inSelectMode) {
                    IconButton(onClick = onExitSelectionMode) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = null,
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            if (isSearching) {
                                isSearching = false
                                query = TextFieldValue()
                            } else {
                                navController.navigateUp()
                            }
                        },
                        onLongClick = {
                            if (!isSearching) {
                                navController.backToMain()
                            }
                        }
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null
                        )
                    }
                }
            },
            actions = {
                if (inSelectMode) {
                    Checkbox(
                        checked = selection.size == filteredSongs.size,
                        onCheckedChange = {
                            if (selection.size == filteredSongs.size) {
                                selection.clear()
                                if (selection.size == songs.size) {
                                    selection.clear()
                                }
                            } else {
                                selection.clear()
                                selection.addAll(filteredSongs.map { it.first })
                            }
                        }
                    )
                    IconButton(
                        enabled = selection.isNotEmpty(),
                        onClick = {
                            menuState.show {
                                YouTubeSongSelectionMenu(
                                    navController = navController,
                                    selection = selection.mapNotNull { songs.getOrNull(it) },
                                    onDismiss = menuState::dismiss,
                                    onExitSelectionMode = onExitSelectionMode
                                )
                            }
                        }
                    ) {
                        Icon(
                            painterResource(R.drawable.more_vert),
                            contentDescription = null
                        )
                    }
                } else if (!isSearching && !isLoading && songs.isNotEmpty()) {
                    IconButton(
                        onClick = { isSearching = true }
                    ) {
                        Icon(
                            painterResource(R.drawable.search),
                            contentDescription = null
                        )
                    }
                }
                if (!isSearching && !inSelectMode && !isLoading && songs.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            menuState.show {
                                YouTubePlaylistMenuInPlaylistScreen(
                                    navController = navController,
                                    playlist = playlist!!,
                                    songs = songs,
                                    coroutineScope = coroutineScope,
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
                }
            },
            scrollBehavior = scrollBehavior
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime))
                .align(Alignment.BottomCenter)
        )
    }
}
