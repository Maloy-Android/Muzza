package com.maloy.muzza.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.maloy.innertube.YouTube
import com.maloy.innertube.utils.parseCookieString
import com.maloy.muzza.LocalDatabase
import com.maloy.muzza.LocalPlayerAwareWindowInsets
import com.maloy.muzza.LocalPlayerConnection
import com.maloy.muzza.R
import com.maloy.muzza.constants.CONTENT_TYPE_HEADER
import com.maloy.muzza.constants.CONTENT_TYPE_PLAYLIST
import com.maloy.muzza.constants.GridCellSize
import com.maloy.muzza.constants.GridCellSizeKey
import com.maloy.muzza.constants.GridThumbnailHeight
import com.maloy.muzza.constants.InnerTubeCookieKey
import com.maloy.muzza.constants.LibraryViewType
import com.maloy.muzza.constants.PlaylistSortDescendingKey
import com.maloy.muzza.constants.PlaylistSortType
import com.maloy.muzza.constants.PlaylistSortTypeKey
import com.maloy.muzza.constants.PlaylistViewTypeKey
import com.maloy.muzza.constants.SmallGridThumbnailHeight
import com.maloy.muzza.constants.YtmSyncKey
import com.maloy.muzza.db.entities.PlaylistEntity
import com.maloy.muzza.ui.component.EmptyPlaceholder
import com.maloy.muzza.ui.component.HideOnScrollFAB
import com.maloy.muzza.ui.component.LazyColumnScrollbar
import com.maloy.muzza.ui.component.LazyVerticalGridScrollbar
import com.maloy.muzza.ui.component.LocalMenuState
import com.maloy.muzza.ui.component.PlaylistGridItem
import com.maloy.muzza.ui.component.PlaylistListItem
import com.maloy.muzza.ui.component.SortHeader
import com.maloy.muzza.ui.component.TextFieldDialog
import com.maloy.muzza.ui.menu.PlaylistMenu
import com.maloy.muzza.ui.utils.backToMain
import com.maloy.muzza.utils.isInternetAvailable
import com.maloy.muzza.utils.rememberEnumPreference
import com.maloy.muzza.utils.rememberPreference
import com.maloy.muzza.viewmodels.LibraryPlaylistsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryPlaylistsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: LibraryPlaylistsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current

    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    val gridCellSize by rememberEnumPreference(GridCellSizeKey, GridCellSize.BIG)
    var viewType by rememberEnumPreference(PlaylistViewTypeKey, LibraryViewType.GRID)
    val (sortType, onSortTypeChange) = rememberEnumPreference(
        PlaylistSortTypeKey,
        PlaylistSortType.CREATE_DATE
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(
        PlaylistSortDescendingKey,
        true
    )

    val playlists by viewModel.allPlaylists.collectAsState()


    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()
    val lazyChecker by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0
        }
    }
    val gridChecker by remember {
        derivedStateOf{
            lazyGridState.firstVisibleItemIndex > 0
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()
    val (ytmSync) = rememberPreference(YtmSyncKey, true)

    val (innerTubeCookie) = rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }

    LaunchedEffect(Unit) {
        if (ytmSync && isLoggedIn && isInternetAvailable(context)) {
            viewModel.sync()
        }
    }

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            when (viewType) {
                LibraryViewType.LIST -> lazyListState.animateScrollToItem(0)
                LibraryViewType.GRID -> lazyGridState.animateScrollToItem(0)
            }
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    var showAddPlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var syncedPlaylist: Boolean by remember {
        mutableStateOf(false)
    }

    if (showAddPlaylistDialog) {
        TextFieldDialog(
            icon = { Icon(imageVector = Icons.Rounded.Add, contentDescription = null) },
            title = { Text(text = stringResource(R.string.create_playlist)) },
            onDismiss = { showAddPlaylistDialog = false },
            onDone = { playlistName ->
                viewModel.viewModelScope.launch(Dispatchers.IO) {
                    val browseId = if (syncedPlaylist)
                        YouTube.createPlaylist(playlistName)
                    else null

                    database.query {
                        insert(
                            PlaylistEntity(
                                name = playlistName,
                                browseId = browseId,
                                bookmarkedAt = LocalDateTime.now(),
                                isLocal = !syncedPlaylist,
                                isEditable = true
                            )
                        )
                    }
                }
            },
            extraContent = {
                if (isLoggedIn && isInternetAvailable(context)) {
                    Row(
                        modifier = Modifier.padding(vertical = 16.dp, horizontal = 40.dp)
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.sync_playlist),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = stringResource(R.string.allows_for_sync_witch_youtube),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth(0.7f)
                            )
                        }
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Switch(
                                checked = syncedPlaylist,
                                onCheckedChange = {
                                    syncedPlaylist = !syncedPlaylist
                                },
                            )
                        }
                    }
                }
            }
        )
    }

    val headerContent = @Composable {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp)
        ) {
            if (playlists?.size != 0) {
                SortHeader(
                    sortType = sortType,
                    sortDescending = sortDescending,
                    onSortTypeChange = onSortTypeChange,
                    onSortDescendingChange = onSortDescendingChange,
                    sortTypeText = { sortType ->
                        when (sortType) {
                            PlaylistSortType.CREATE_DATE -> R.string.sort_by_create_date
                            PlaylistSortType.NAME -> R.string.sort_by_name
                            PlaylistSortType.SONG_COUNT -> R.string.sort_by_song_count
                        }
                    }
                )
            }

            Spacer(Modifier.weight(1f))

            if (playlists?.size != 0) {
                playlists?.let { playlists ->
                    Text(
                        text = pluralStringResource(
                            R.plurals.n_playlist,
                            playlists.size,
                            playlists.size
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            IconButton(
                onClick = {
                    viewType = viewType.toggle()
                },
                modifier = Modifier.padding(start = 6.dp, end = 6.dp)
            ) {
                Icon(
                    painter = painterResource(
                        when (viewType) {
                            LibraryViewType.LIST -> R.drawable.list
                            LibraryViewType.GRID -> R.drawable.grid_view
                        }
                    ),
                    contentDescription = null
                )
            }
        }
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
        playlists?.let { playlists ->
            when (viewType) {
                LibraryViewType.LIST -> {
                    if (playlists.isEmpty()) {
                        LazyVerticalGrid(
                            state = lazyGridState,
                            columns = GridCells.Fixed(1),
                            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
                        ) {
                            item {
                                EmptyPlaceholder(
                                    icon = R.drawable.queue_music,
                                    text = stringResource(R.string.library_playlist_empty)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = lazyListState,
                            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
                        ) {
                            item(
                                key = "header",
                                contentType = CONTENT_TYPE_HEADER
                            ) {
                                headerContent()
                            }
                            items(
                                items = playlists,
                                key = { it.id },
                                contentType = { CONTENT_TYPE_PLAYLIST }
                            ) { playlist ->
                                PlaylistListItem(
                                    playlist = playlist,
                                    isActive = playlist.id == mediaMetadata?.playlist?.id,
                                    isPlaying = isPlaying,
                                    thumbnail = Icons.AutoMirrored.Rounded.QueueMusic,
                                    trailingContent = {
                                        IconButton(
                                            onClick = {
                                                menuState.show {
                                                    PlaylistMenu(
                                                        playlist = playlist,
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
                                    },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = {
                                                    navController.navigate("local_playlist/${playlist.id}")
                                                },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    menuState.show {
                                                        PlaylistMenu(
                                                            playlist = playlist,
                                                            coroutineScope = coroutineScope,
                                                            onDismiss = menuState::dismiss,
                                                        )
                                                    }
                                                },
                                            )
                                            .animateItem(),
                                )
                            }
                        }
                        if (lazyChecker) {
                            LazyColumnScrollbar(
                                state = lazyListState
                            )
                        }
                    }
                    HideOnScrollFAB(
                        lazyListState = lazyListState,
                        icon = R.drawable.add,
                        onClick = {
                            showAddPlaylistDialog = true
                        }
                    )
                }

                LibraryViewType.GRID -> {
                    if (playlists.isEmpty()) {
                        LazyVerticalGrid(
                            state = lazyGridState,
                            columns = GridCells.Fixed(1),
                            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
                        ) {
                            item {
                                EmptyPlaceholder(
                                    icon = R.drawable.queue_music,
                                    text = stringResource(R.string.library_playlist_empty)
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            state = lazyGridState,
                            columns = GridCells.Adaptive(
                                minSize = when (gridCellSize) {
                                    GridCellSize.SMALL -> SmallGridThumbnailHeight
                                    GridCellSize.BIG -> GridThumbnailHeight
                                } + 24.dp
                            ),
                            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
                        ) {
                            item(
                                key = "header",
                                span = { GridItemSpan(maxLineSpan) },
                                contentType = CONTENT_TYPE_HEADER
                            ) {
                                headerContent()
                            }
                            items(
                                items = playlists,
                                key = { it.id },
                                contentType = { CONTENT_TYPE_PLAYLIST }
                            ) { playlist ->
                                PlaylistGridItem(
                                    playlist = playlist,
                                    thumbnail = Icons.AutoMirrored.Rounded.QueueMusic,
                                    isActive = playlist.id == mediaMetadata?.playlist?.id,
                                    isPlaying = isPlaying,
                                    fillMaxWidth = true,
                                    coroutineScope = coroutineScope,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                navController.navigate("local_playlist/${playlist.id}")
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show {
                                                    PlaylistMenu(
                                                        playlist = playlist,
                                                        coroutineScope = coroutineScope,
                                                        onDismiss = menuState::dismiss
                                                    )
                                                }
                                            }
                                        )
                                        .animateItem()
                                )
                            }
                        }
                        if (gridChecker) {
                            LazyVerticalGridScrollbar(
                                state = lazyGridState
                            )
                        }
                    }
                    HideOnScrollFAB(
                        lazyListState = lazyGridState,
                        icon = R.drawable.add,
                        onClick = {
                            showAddPlaylistDialog = true
                        }
                    )
                }
            }
        }
        if (playlists != null && ytmSync && isLoggedIn && isInternetAvailable(context)) {
            Indicator(
                isRefreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
            )
        }
        CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.playlists)) },
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
