package com.maloy.muzza.ui.screens.library

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.maloy.innertube.YouTube
import com.maloy.innertube.utils.parseCookieString
import com.maloy.muzza.LocalDatabase
import com.maloy.muzza.LocalPlayerAwareWindowInsets
import com.maloy.muzza.R
import com.maloy.muzza.constants.AppDesignVariantKey
import com.maloy.muzza.constants.AppDesignVariantType
import com.maloy.muzza.constants.AutoPlaylistCachedPlaylistShowKey
import com.maloy.muzza.constants.AutoPlaylistDownloadShowKey
import com.maloy.muzza.constants.AutoPlaylistLikedShowKey
import com.maloy.muzza.constants.AutoPlaylistLocalPlaylistShowKey
import com.maloy.muzza.constants.AutoPlaylistTopPlaylistShowKey
import com.maloy.muzza.constants.AutoSyncLocalSongsKey
import com.maloy.muzza.constants.CONTENT_TYPE_HEADER
import com.maloy.muzza.constants.CONTENT_TYPE_PLAYLIST
import com.maloy.muzza.constants.ChipSortTypeKey
import com.maloy.muzza.constants.GridCellSize
import com.maloy.muzza.constants.GridCellSizeKey
import com.maloy.muzza.constants.GridThumbnailHeight
import com.maloy.muzza.constants.InnerTubeCookieKey
import com.maloy.muzza.constants.LibraryFilter
import com.maloy.muzza.constants.LibraryViewType
import com.maloy.muzza.constants.PlaylistSortDescendingKey
import com.maloy.muzza.constants.PlaylistSortType
import com.maloy.muzza.constants.PlaylistSortTypeKey
import com.maloy.muzza.constants.PlaylistViewTypeKey
import com.maloy.muzza.constants.ScannerSensitivity
import com.maloy.muzza.constants.ScannerSensitivityKey
import com.maloy.muzza.constants.ScannerStrictExtKey
import com.maloy.muzza.constants.SmallGridThumbnailHeight
import com.maloy.muzza.constants.YtmSyncKey
import com.maloy.muzza.db.entities.Playlist
import com.maloy.muzza.db.entities.PlaylistEntity
import com.maloy.muzza.ui.component.ChipsRow
import com.maloy.muzza.ui.component.EmptyPlaceholder
import com.maloy.muzza.ui.component.HideOnScrollFAB
import com.maloy.muzza.ui.component.LocalMenuState
import com.maloy.muzza.ui.component.PlaylistGridItem
import com.maloy.muzza.ui.component.PlaylistListItem
import com.maloy.muzza.ui.component.SortHeader
import com.maloy.muzza.ui.component.TextFieldDialog
import com.maloy.muzza.ui.menu.PlaylistMenu
import com.maloy.muzza.ui.utils.scanLocal
import com.maloy.muzza.ui.utils.syncDB
import com.maloy.muzza.utils.isInternetAvailable
import com.maloy.muzza.utils.rememberEnumPreference
import com.maloy.muzza.utils.rememberPreference
import com.maloy.muzza.viewmodels.LibraryPlaylistsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.UUID

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryPlaylistsScreen(
    navController: NavController,
    viewModel: LibraryPlaylistsViewModel = hiltViewModel(),
) {
    val (appDesignVariant) = rememberEnumPreference(
        AppDesignVariantKey,
        defaultValue = AppDesignVariantType.NEW
    )
    var filterType by rememberEnumPreference(ChipSortTypeKey, LibraryFilter.LIBRARY)
    val filterContent = @Composable {
        if (appDesignVariant == AppDesignVariantType.NEW) {
            Row {
                ChipsRow(
                    chips =
                        listOf(
                            LibraryFilter.PLAYLISTS to stringResource(R.string.filter_playlists),
                            LibraryFilter.SONGS to stringResource(R.string.filter_songs),
                            LibraryFilter.ALBUMS to stringResource(R.string.filter_albums),
                            LibraryFilter.ARTISTS to stringResource(R.string.filter_artists),
                        ),
                    currentValue = filterType,
                    onValueUpdate = {
                        filterType =
                            if (filterType == it) {
                                LibraryFilter.LIBRARY
                            } else {
                                it
                            }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current

    var isScannerActive by remember { mutableStateOf(false) }
    var isScanFinished by remember { mutableStateOf(false) }
    var mediaPermission by remember { mutableStateOf(true) }
    val mediaPermissionLevel =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
    val (autoSyncLocalSongs) = rememberPreference(
        key = AutoSyncLocalSongsKey,
        defaultValue = true
    )
    val (scannerSensitivity) = rememberEnumPreference(
        key = ScannerSensitivityKey,
        defaultValue = ScannerSensitivity.LEVEL_2
    )
    val (strictExtensions) = rememberPreference(ScannerStrictExtKey, defaultValue = false)

    val coroutineScope = rememberCoroutineScope()

    val likedSongs by viewModel.likedSongs.collectAsState()
    val downloadSongs by viewModel.downloadSongs.collectAsState(initial = null)
    val localSongsCount by viewModel.localSongsCount.collectAsState()
    val cachedSongs by viewModel.cachedSongs.collectAsState()
    val topSongs by viewModel.topSongs.collectAsState(initial = null)
    val topSize = 50
    val likedPlaylist = Playlist(
        playlist = PlaylistEntity(
            id = UUID.randomUUID().toString(),
            name = stringResource(R.string.liked)
        ),
        songCount = if (likedSongs != null) likedSongs!!.size else 0,
        songThumbnails = emptyList()
    )
    val downloadPlaylist = Playlist(
        playlist = PlaylistEntity(
            id = UUID.randomUUID().toString(),
            name = stringResource(R.string.offline)
        ),
        songCount = if (downloadSongs != null) downloadSongs!!.size else 0,
        songThumbnails = emptyList()
    )
    val topSizeInt = topSize.toString().toInt()
    if (topSongs != null)
        println(topSongs?.size)

    val topPlaylist = Playlist(
        playlist = PlaylistEntity(
            id = UUID.randomUUID().toString(),
            name = stringResource(R.string.my_top)
        ),
        songCount = topSongs?.let { minOf(it.size, topSizeInt) } ?: 0,
        songThumbnails = emptyList()
    )
    val localPlaylist = Playlist(
        playlist = PlaylistEntity(
            id = UUID.randomUUID().toString(),
            name = stringResource(R.string.local)
        ),
        songCount = localSongsCount,
        songThumbnails = emptyList()
    )

    val cachedPlaylist = Playlist(
        playlist = PlaylistEntity(
            id = UUID.randomUUID().toString(),
            name = stringResource(R.string.cached)
        ),
        songCount = cachedSongs.size,
        songThumbnails = emptyList()
    )

    val gridCellSize by rememberEnumPreference(GridCellSizeKey, GridCellSize.SMALL)
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

    val (autoPlaylistLiked) = rememberPreference(AutoPlaylistLikedShowKey, defaultValue = true)
    val (autoPlaylistDownload) = rememberPreference(
        AutoPlaylistDownloadShowKey, defaultValue = true
    )
    val (autoPlaylistTopPlaylist) = rememberPreference(
        AutoPlaylistTopPlaylistShowKey, defaultValue = true
    )
    val (autoPlaylistCached) = rememberPreference(
        AutoPlaylistCachedPlaylistShowKey, defaultValue = true
    )
    val (autoPlaylistLocal) = rememberPreference(
        AutoPlaylistLocalPlaylistShowKey, defaultValue = true
    )

    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()
    val (ytmSync) = rememberPreference(YtmSyncKey, true)

    val (innerTubeCookie) = rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }

    LaunchedEffect(Unit) {
        if (ytmSync) {
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

    if (autoSyncLocalSongs) {
        LaunchedEffect(Unit) {
            if (isScannerActive) {
                return@LaunchedEffect
            }
            if (context.checkSelfPermission(mediaPermissionLevel)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    context as Activity,
                    arrayOf(mediaPermissionLevel), PackageManager.PERMISSION_GRANTED
                )
                mediaPermission = false
                return@LaunchedEffect
            } else if (context.checkSelfPermission(mediaPermissionLevel)
                == PackageManager.PERMISSION_GRANTED
            ) {
                mediaPermission = true
            }
            isScanFinished = false
            isScannerActive = true
            coroutineScope.launch(Dispatchers.IO) {
                val directoryStructure = scanLocal(context).value
                syncDB(database, directoryStructure.toList(), scannerSensitivity, strictExtensions)
                isScannerActive = false
                isScanFinished = true
                database.localSongsCount()
            }
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
                                bookmarkedAt = LocalDateTime.now()
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

                Spacer(Modifier.weight(1f))

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

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        playlists?.let { playlists ->
            when (viewType) {
                LibraryViewType.LIST -> {
                    if (!autoPlaylistLiked && !autoPlaylistDownload && !autoPlaylistTopPlaylist && !autoPlaylistCached && !autoPlaylistLocal && playlists.isEmpty()) {
                        LazyVerticalGrid(
                            state = lazyGridState,
                            columns = GridCells.Fixed(1),
                            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
                        ) {
                            item(
                                key = "filter",
                                contentType = CONTENT_TYPE_HEADER
                            ) {
                                filterContent()
                            }
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
                                key = "filter",
                                contentType = CONTENT_TYPE_HEADER
                            ) {
                                filterContent()
                            }
                            item(
                                key = "header",
                                contentType = CONTENT_TYPE_HEADER
                            ) {
                                headerContent()
                            }

                            if (autoPlaylistLiked) {
                                item(
                                    key = "likedPlaylist",
                                    contentType = { CONTENT_TYPE_PLAYLIST }
                                ) {
                                    PlaylistListItem(
                                        playlist = likedPlaylist,
                                        thumbnail = Icons.Rounded.Favorite,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                navController.navigate("auto_playlist/liked")
                                            }
                                            .animateItem()
                                    )
                                }
                            }
                            if (autoPlaylistDownload) {
                                item(
                                    key = "downloadedPlaylist",
                                    contentType = { CONTENT_TYPE_PLAYLIST }
                                ) {
                                    PlaylistListItem(
                                        playlist = downloadPlaylist,
                                        thumbnail = Icons.Rounded.CloudDownload,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                navController.navigate("auto_playlist/downloaded")
                                            }
                                            .animateItem()
                                    )
                                }
                            }
                            if (autoPlaylistTopPlaylist) {
                                item(
                                    key = "topPlaylist",
                                    contentType = { CONTENT_TYPE_PLAYLIST }
                                ) {
                                    PlaylistListItem(
                                        playlist = topPlaylist,
                                        thumbnail = Icons.AutoMirrored.Rounded.TrendingUp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = {
                                                    navController.navigate("top_playlist/$topSize")
                                                }
                                            )
                                            .animateItem()
                                    )
                                }
                            }
                            if (autoPlaylistCached) {
                                item(
                                    key = "cachedPlaylist",
                                    contentType = { CONTENT_TYPE_PLAYLIST }
                                ) {
                                    PlaylistListItem(
                                        playlist = cachedPlaylist,
                                        thumbnail = Icons.Rounded.Cached,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = {
                                                    navController.navigate("CachedPlaylist")
                                                }
                                            )
                                            .animateItem()
                                    )
                                }
                            }
                            if (autoPlaylistLocal) {
                                item(
                                    key = "localPlaylist",
                                    contentType = { CONTENT_TYPE_PLAYLIST }
                                ) {
                                    PlaylistListItem(
                                        playlist = localPlaylist,
                                        thumbnail = Icons.Rounded.MusicNote,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = {
                                                    navController.navigate("AutoPlaylistLocal")
                                                }
                                            )
                                            .animateItem()
                                    )
                                }
                            }
                            items(
                                items = playlists,
                                key = { it.id },
                                contentType = { CONTENT_TYPE_PLAYLIST }
                            ) { playlist ->
                                PlaylistListItem(
                                    playlist = playlist,
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
                    if (!autoPlaylistLiked && !autoPlaylistDownload && !autoPlaylistTopPlaylist && !autoPlaylistCached && !autoPlaylistLocal && playlists.isEmpty()) {
                        LazyVerticalGrid(
                            state = lazyGridState,
                            columns = GridCells.Fixed(1),
                            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
                        ) {
                            item(
                                key = "filter",
                                span = { GridItemSpan(maxLineSpan) },
                                contentType = CONTENT_TYPE_HEADER
                            ) {
                                filterContent()
                            }
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
                                key = "filter",
                                span = { GridItemSpan(maxLineSpan) },
                                contentType = CONTENT_TYPE_HEADER
                            ) {
                                filterContent()
                            }
                            item(
                                key = "header",
                                span = { GridItemSpan(maxLineSpan) },
                                contentType = CONTENT_TYPE_HEADER
                            ) {
                                headerContent()
                            }

                            if (autoPlaylistLiked) {
                                item(
                                    key = "likedPlaylist",
                                    contentType = { CONTENT_TYPE_PLAYLIST }
                                ) {
                                    PlaylistGridItem(
                                        playlist = likedPlaylist,
                                        thumbnail = Icons.Rounded.Favorite,
                                        fillMaxWidth = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = {
                                                    navController.navigate("auto_playlist/liked")
                                                },
                                            )
                                            .animateItem()
                                    )
                                }
                            }
                            if (autoPlaylistDownload) {
                                item(
                                    key = "downloadedPlaylist",
                                    contentType = { CONTENT_TYPE_PLAYLIST }
                                ) {
                                    PlaylistGridItem(
                                        playlist = downloadPlaylist,
                                        thumbnail = Icons.Rounded.CloudDownload,
                                        fillMaxWidth = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = {
                                                    navController.navigate("auto_playlist/downloaded")
                                                },
                                            )
                                            .animateItem()
                                    )
                                }
                            }
                            if (autoPlaylistTopPlaylist) {
                                item(
                                    key = "topPlaylist",
                                    contentType = { CONTENT_TYPE_PLAYLIST }
                                ) {
                                    PlaylistGridItem(
                                        playlist = topPlaylist,
                                        thumbnail = Icons.AutoMirrored.Rounded.TrendingUp,
                                        fillMaxWidth = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = {
                                                    navController.navigate("top_playlist/$topSize")
                                                }
                                            )
                                            .animateItem()
                                    )
                                }
                            }
                            if (autoPlaylistCached) {
                                item(
                                    key = "cachedPlaylist",
                                    contentType = { CONTENT_TYPE_PLAYLIST }
                                ) {
                                    PlaylistGridItem(
                                        playlist = cachedPlaylist,
                                        thumbnail = Icons.Rounded.Cached,
                                        fillMaxWidth = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = {
                                                    navController.navigate("CachedPlaylist")
                                                }
                                            )
                                            .animateItem()
                                    )
                                }
                            }
                            if (autoPlaylistLocal) {
                                item(
                                    key = "localPlaylist",
                                    contentType = { CONTENT_TYPE_PLAYLIST }
                                ) {
                                    PlaylistGridItem(
                                        playlist = localPlaylist,
                                        thumbnail = Icons.Rounded.MusicNote,
                                        fillMaxWidth = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = {
                                                    navController.navigate("AutoPlaylistLocal")
                                                }
                                            )
                                            .animateItem()
                                    )
                                }
                            }
                            items(
                                items = playlists,
                                key = { it.id },
                                contentType = { CONTENT_TYPE_PLAYLIST }
                            ) { playlist ->
                                PlaylistGridItem(
                                    playlist = playlist,
                                    thumbnail = Icons.AutoMirrored.Rounded.QueueMusic,
                                    fillMaxWidth = true,
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
    }
}
