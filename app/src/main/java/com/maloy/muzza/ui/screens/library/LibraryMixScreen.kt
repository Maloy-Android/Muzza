package com.maloy.muzza.ui.screens.library

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.maloy.innertube.utils.parseCookieString
import com.maloy.muzza.LocalDatabase
import com.maloy.muzza.LocalPlayerAwareWindowInsets
import com.maloy.muzza.LocalPlayerConnection
import com.maloy.muzza.R
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
import com.maloy.muzza.constants.MixSortDescendingKey
import com.maloy.muzza.constants.MixSortType
import com.maloy.muzza.constants.MixSortTypeKey
import com.maloy.muzza.constants.MixViewType
import com.maloy.muzza.constants.MixViewTypeKey
import com.maloy.muzza.constants.ScannerSensitivity
import com.maloy.muzza.constants.ScannerSensitivityKey
import com.maloy.muzza.constants.ScannerStrictExtKey
import com.maloy.muzza.constants.SmallGridThumbnailHeight
import com.maloy.muzza.constants.YtmSyncKey
import com.maloy.muzza.db.entities.Album
import com.maloy.muzza.db.entities.Artist
import com.maloy.muzza.db.entities.Playlist
import com.maloy.muzza.db.entities.PlaylistEntity
import com.maloy.muzza.extensions.reversed
import com.maloy.muzza.ui.component.AlbumGridItem
import com.maloy.muzza.ui.component.AlbumListItem
import com.maloy.muzza.ui.component.ArtistGridItem
import com.maloy.muzza.ui.component.ArtistListItem
import com.maloy.muzza.ui.component.ChipsRow
import com.maloy.muzza.ui.component.EmptyPlaceholder
import com.maloy.muzza.ui.component.LazyVerticalGridScrollbar
import com.maloy.muzza.ui.component.LocalMenuState
import com.maloy.muzza.ui.component.PlaylistGridItem
import com.maloy.muzza.ui.component.PlaylistListItem
import com.maloy.muzza.ui.component.SortHeader
import com.maloy.muzza.ui.menu.AlbumMenu
import com.maloy.muzza.ui.menu.ArtistMenu
import com.maloy.muzza.ui.menu.PlaylistMenu
import com.maloy.muzza.ui.utils.scanLocal
import com.maloy.muzza.ui.utils.syncDB
import com.maloy.muzza.utils.isInternetAvailable
import com.maloy.muzza.utils.rememberEnumPreference
import com.maloy.muzza.utils.rememberPreference
import com.maloy.muzza.viewmodels.LibraryMixViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.time.LocalDateTime
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryMixScreen(
    navController: NavController,
    viewModel: LibraryMixViewModel = hiltViewModel(),
) {
    val gridCellSize by rememberEnumPreference(GridCellSizeKey, GridCellSize.SMALL)
    var filterType by rememberEnumPreference(ChipSortTypeKey, LibraryFilter.LIBRARY)
    val filterContent = @Composable {
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
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val (sortType, onSortTypeChange) = rememberEnumPreference(
        MixSortTypeKey,
        MixSortType.DEFAULT
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(MixSortDescendingKey, true)
    var viewType by rememberEnumPreference(MixViewTypeKey, MixViewType.GRID)

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

    val (ytmSync) = rememberPreference(YtmSyncKey, true)
    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn =
        remember(innerTubeCookie) {
            "SAPISID" in parseCookieString(innerTubeCookie)
        }

    val likedSongs by viewModel.likedSongs.collectAsState()
    val downloadSongs by viewModel.downloadSongs.collectAsState(initial = null)
    val topSongs by viewModel.topSongs.collectAsState(initial = null)
    val localSongsCount by viewModel.localSongsCount.collectAsState()
    val cachedSongs by viewModel.cachedSongs.collectAsState()
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

    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
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

    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()
    val gridChecker by remember {
        derivedStateOf{
            lazyGridState.firstVisibleItemIndex > 0
        }
    }

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            when (viewType) {
                MixViewType.LIST -> lazyListState.animateScrollToItem(0)
                MixViewType.GRID -> lazyGridState.animateScrollToItem(0)
            }
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    LaunchedEffect(Unit) {
        if (ytmSync && isLoggedIn && isInternetAvailable(context)) {
            withContext(Dispatchers.IO) {
                viewModel.syncAllLibrary()
            }
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

    val albums = viewModel.albums.collectAsState()
    val artist = viewModel.artists.collectAsState()
    val playlist = viewModel.playlists.collectAsState()

    var allItems = playlist.value + albums.value + artist.value
    val collator = Collator.getInstance(Locale.getDefault())
    collator.strength = Collator.PRIMARY
    allItems = when (sortType) {
        MixSortType.DEFAULT -> allItems.reversed()
        MixSortType.CREATE_DATE -> allItems.sortedBy { item ->
            when (item) {
                is Album -> item.album.bookmarkedAt
                is Artist -> item.artist.bookmarkedAt
                is Playlist -> item.playlist.createdAt
                else -> LocalDateTime.now()
            }
        }

        MixSortType.LAST_UPDATED -> allItems.sortedBy { item ->
            when (item) {
                is Album -> item.album.lastUpdateTime
                is Artist -> item.artist.lastUpdateTime
                is Playlist -> item.playlist.lastUpdateTime
                else -> LocalDateTime.now()
            }
        }

        MixSortType.NAME -> allItems.sortedWith(
            compareBy(collator) { item ->
                when (item) {
                    is Album -> item.album.title
                    is Artist -> item.artist.name
                    is Playlist -> item.playlist.name
                    else -> ""
                }
            },
        )
    }.reversed(sortDescending)

    val headerContent = @Composable {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp),
        ) {
            if (allItems.isNotEmpty()) {
                SortHeader(
                    sortType = sortType,
                    sortDescending = sortDescending,
                    onSortTypeChange = onSortTypeChange,
                    onSortDescendingChange = onSortDescendingChange,
                    sortTypeText = { sortType ->
                        when (sortType) {
                            MixSortType.DEFAULT -> R.string.default_
                            MixSortType.CREATE_DATE -> R.string.sort_by_create_date
                            MixSortType.LAST_UPDATED -> R.string.sort_by_last_updated
                            MixSortType.NAME -> R.string.sort_by_name
                        }
                    },
                )
            }

            Spacer(Modifier.weight(1f))

            IconButton(
                onClick = {
                    viewType = viewType.toggle()
                },
                modifier = Modifier.padding(end = 6.dp)
            ) {
                Icon(
                    painter = painterResource(
                        when (viewType) {
                            MixViewType.LIST -> R.drawable.list
                            MixViewType.GRID -> R.drawable.grid_view
                        }
                    ),
                    contentDescription = null
                )
            }
        }
    }
    if (viewType == MixViewType.LIST) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            if (!autoPlaylistLiked && !autoPlaylistDownload && !autoPlaylistTopPlaylist && !autoPlaylistCached && !autoPlaylistLocal && allItems.isEmpty()) {
                LazyVerticalGrid(
                    state = lazyGridState,
                    columns = GridCells.Fixed(1),
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
                ) {
                    item(
                        key = "filter",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        filterContent()
                    }
                    item {
                        EmptyPlaceholder(
                            icon = R.drawable.library_music_outlined,
                            text = stringResource(R.string.library_empty),
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    state = lazyGridState,
                    columns = GridCells.Fixed(1),
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                ) {
                    item(
                        key = "filter",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        filterContent()
                    }

                    item(
                        key = "header",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        headerContent()
                    }

                    if (autoPlaylistLiked) {
                        item(
                            key = "likedPlaylist",
                            contentType = { CONTENT_TYPE_PLAYLIST },
                        ) {
                            PlaylistListItem(
                                playlist = likedPlaylist,
                                thumbnail = Icons.Rounded.Favorite,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            navController.navigate("auto_playlist/liked")
                                        })
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
                                    .combinedClickable(
                                        onClick = {
                                            navController.navigate("auto_playlist/downloaded")
                                        })
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
                                        })
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
                                        })
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
                                        })
                                    .animateItem()
                            )
                        }
                    }
                    items(
                        allItems,
                        key = { it.id },
                        contentType = { CONTENT_TYPE_PLAYLIST }
                    ) { item ->
                        when (item) {
                            is Playlist -> {
                                PlaylistListItem(
                                    playlist = item,
                                    thumbnail = Icons.AutoMirrored.Rounded.QueueMusic,
                                    trailingContent = {
                                        IconButton(
                                            onClick = {
                                                menuState.show {
                                                    PlaylistMenu(
                                                        playlist = item,
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
                                            .combinedClickable(onClick = {
                                                navController.navigate("local_playlist/${item.id}")
                                            }, onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show {
                                                    PlaylistMenu(
                                                        playlist = item,
                                                        coroutineScope = coroutineScope,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            })
                                            .animateItem()
                                )
                            }

                            is Artist -> {
                                ArtistListItem(
                                    artist = item,
                                    trailingContent = {
                                        IconButton(
                                            onClick = {
                                                menuState.show {
                                                    ArtistMenu(
                                                        originalArtist = item,
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
                                            .combinedClickable(onClick = {
                                                navController.navigate("artist/${item.id}")
                                            }, onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show {
                                                    ArtistMenu(
                                                        originalArtist = item,
                                                        coroutineScope = coroutineScope,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            })
                                            .animateItem()
                                )
                            }

                            is Album -> {
                                AlbumListItem(
                                    album = item,
                                    isActive = item.id == mediaMetadata?.album?.id,
                                    isPlaying = isPlaying,
                                    trailingContent = {
                                        IconButton(
                                            onClick = {
                                                menuState.show {
                                                    AlbumMenu(
                                                        originalAlbum = item,
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
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(onClick = {
                                                navController.navigate("album/${item.id}")
                                            }, onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show {
                                                    AlbumMenu(
                                                        originalAlbum = item,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            })
                                            .animateItem()
                                )
                            }

                            else -> {}
                        }
                    }
                }
            }
            if (gridChecker) {
                LazyVerticalGridScrollbar(
                    state = lazyGridState
                )
            }
        }
    } else {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            if (!autoPlaylistLiked && !autoPlaylistDownload && !autoPlaylistTopPlaylist && !autoPlaylistCached && !autoPlaylistLocal && allItems.isEmpty()) {
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
                            icon = R.drawable.library_music_outlined,
                            text = stringResource(R.string.library_empty)
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
                        contentType = CONTENT_TYPE_HEADER,
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
                                        })
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
                                        })
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
                                        })
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
                                        })
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
                                        })
                                    .animateItem()
                            )
                        }
                    }
                    items(
                        allItems,
                        key = { it.id },
                        contentType = { CONTENT_TYPE_PLAYLIST },
                    ) { item ->
                        when (item) {
                            is Playlist -> {
                                PlaylistGridItem(
                                    playlist = item,
                                    thumbnail = Icons.AutoMirrored.Rounded.QueueMusic,
                                    fillMaxWidth = true,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(onClick = {
                                                navController.navigate("local_playlist/${item.id}")
                                            }, onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show {
                                                    PlaylistMenu(
                                                        playlist = item,
                                                        coroutineScope = coroutineScope,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            })
                                            .animateItem()
                                )
                            }

                            is Artist -> {
                                ArtistGridItem(
                                    artist = item,
                                    fillMaxWidth = true,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(onClick = {
                                                navController.navigate("artist/${item.id}")
                                            }, onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show {
                                                    ArtistMenu(
                                                        originalArtist = item,
                                                        coroutineScope = coroutineScope,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            })
                                            .animateItem()
                                )
                            }

                            is Album -> {
                                AlbumGridItem(
                                    album = item,
                                    isActive = item.id == mediaMetadata?.album?.id,
                                    isPlaying = isPlaying,
                                    coroutineScope = coroutineScope,
                                    fillMaxWidth = true,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = {
                                                    navController.navigate("album/${item.id}")
                                                },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    menuState.show {
                                                        AlbumMenu(
                                                            originalAlbum = item,
                                                            navController = navController,
                                                            onDismiss = menuState::dismiss,
                                                        )
                                                    }
                                                }
                                            )
                                            .animateItem()
                                )
                            }

                            else -> {}
                        }
                    }
                }
            }
            if (gridChecker) {
                LazyVerticalGridScrollbar(
                    state = lazyGridState
                )
            }
        }
    }
}