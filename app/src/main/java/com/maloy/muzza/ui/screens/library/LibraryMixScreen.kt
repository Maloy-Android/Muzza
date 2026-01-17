package com.maloy.muzza.ui.screens.library

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil.compose.AsyncImage
import com.maloy.innertube.YouTube
import com.maloy.innertube.utils.parseCookieString
import com.maloy.muzza.LocalDatabase
import com.maloy.muzza.LocalPlayerAwareWindowInsets
import com.maloy.muzza.LocalPlayerConnection
import com.maloy.muzza.R
import com.maloy.muzza.constants.InnerTubeCookieKey
import com.maloy.muzza.constants.ListItemHeight
import com.maloy.muzza.constants.YtmSyncKey
import com.maloy.muzza.constants.likedMusicThumbnailKey
import com.maloy.muzza.constants.likedMusicTitleKey
import com.maloy.muzza.db.entities.Playlist
import com.maloy.muzza.db.entities.PlaylistEntity
import com.maloy.muzza.extensions.toMediaItem
import com.maloy.muzza.extensions.togglePlayPause
import com.maloy.muzza.models.toMediaMetadata
import com.maloy.muzza.playback.queues.ListQueue
import com.maloy.muzza.ui.component.ArtistGridItem
import com.maloy.muzza.ui.component.HideOnScrollFAB
import com.maloy.muzza.ui.component.LocalMenuState
import com.maloy.muzza.ui.component.SongListItem
import com.maloy.muzza.ui.menu.ArtistMenu
import com.maloy.muzza.ui.menu.AutoPlaylistMenu
import com.maloy.muzza.ui.menu.SongMenu
import com.maloy.muzza.ui.utils.SnapLayoutInfoProvider
import com.maloy.muzza.utils.scanLocal
import com.maloy.muzza.utils.syncDB
import com.maloy.muzza.utils.isInternetAvailable
import com.maloy.muzza.utils.rememberPreference
import com.maloy.muzza.viewmodels.LibraryMixViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryMixScreen(
    navController: NavController,
    viewModel: LibraryMixViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val (ytmSync) = rememberPreference(YtmSyncKey, true)
    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn =
        remember(innerTubeCookie) {
            "SAPISID" in parseCookieString(innerTubeCookie)
        }

    val likedSongs by viewModel.likedSongs.collectAsState()
    val downloadSongs by viewModel.downloadSongs.collectAsState(initial = null)
    val topSongs by viewModel.topSongs.collectAsState(initial = null)
    val localSongs by viewModel.localSongs.collectAsState()
    val cachedSongs by viewModel.cachedSongs.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val librarySongs by viewModel.librarySongs.collectAsState(initial = null)
    val libraryLikedSongs by viewModel.libraryLikedLibrarySongs.collectAsState(initial = null)

    val (likedMusicThumbnail) = rememberPreference(likedMusicThumbnailKey, defaultValue = "")
    val (likedMusicTitle) = rememberPreference(likedMusicTitleKey, defaultValue = "")

    val artists by viewModel.allArtists.collectAsState()

    val playlistsPlaylist = Playlist(
        playlist = PlaylistEntity(
            id = "user_playlists",
            name = stringResource(R.string.playlists)
        ),
        songCount = playlists.size,
        songThumbnails = emptyList()
    )

    val playlistAlbums = Playlist(
        playlist = PlaylistEntity(
            id = "albums",
            name = stringResource(R.string.albums)
        ),
        songCount = albums.size,
        songThumbnails = emptyList()
    )

    val topSize = 50

    val downloadPlaylist = Playlist(
        playlist = PlaylistEntity(
            id = "downloaded",
            name = stringResource(R.string.offline)
        ),
        songCount = downloadSongs?.size ?: 0,
        songThumbnails = emptyList()
    )

    val topPlaylist = Playlist(
        playlist = PlaylistEntity(
            id = "top",
            name = stringResource(R.string.my_top)
        ),
        songCount = topSongs?.let { minOf(it.size, topSize) } ?: 0,
        songThumbnails = emptyList()
    )

    val localPlaylist = Playlist(
        playlist = PlaylistEntity(
            id = "local",
            name = stringResource(R.string.local)
        ),
        songCount = localSongs?.size ?: 0,
        songThumbnails = emptyList()
    )

    val likedMusicPlaylist = Playlist(
        playlist = PlaylistEntity(
            id = "likedMusic",
            name =  if (isLoggedIn && likedMusicTitle.isNotEmpty()) likedMusicTitle else {
                stringResource(R.string.liked)
            }
        ),
        songCount = likedSongs?.size ?: 0,
        songThumbnails = emptyList()
    )

    val libraryMusicPlaylist = Playlist(
        playlist = PlaylistEntity(
            id = "libraryMusic",
            name = stringResource(R.string.songs_from_library)
        ),
        songCount = librarySongs?.size ?: 0,
        songThumbnails = emptyList()
    )

    val cachedPlaylist = Playlist(
        playlist = PlaylistEntity(
            id = "cached",
            name = stringResource(R.string.cached)
        ),
        songCount = cachedSongs.size,
        songThumbnails = emptyList()
    )

    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    var isScannerActive by remember { mutableStateOf(false) }
    val mediaPermissionLevel =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    val lazyListState = rememberLazyListState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    val view = LocalView.current
    val density = LocalDensity.current
    val screenWidth = with(density) { view.width.toDp() }

    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    val horizontalLazyGridItemWidthFactor = if (screenWidth * 0.475f >= 320.dp) 0.475f else 0.9f
    val horizontalLazyGridItemWidth = screenWidth * horizontalLazyGridItemWidthFactor
    val quickPicksLazyGridState = rememberLazyGridState()
    val quickPicksSnapLayoutInfoProvider = remember(quickPicksLazyGridState) {
        SnapLayoutInfoProvider(
            lazyGridState = quickPicksLazyGridState,
            positionInLayout = { layoutSize, itemSize ->
                (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
            }
        )
    }

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazyListState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    LaunchedEffect(libraryLikedSongs) {
        libraryLikedSongs?.let { songs ->
            if (songs.isNotEmpty() && isInternetAvailable(context)) {
                coroutineScope.launch(Dispatchers.IO) {
                    val songIds = songs.map { it.song.id }
                    YouTube.queue(songIds).onSuccess { updatedSongs ->
                        database.transaction {
                            updatedSongs.forEach { newSong ->
                                val songWrapper =
                                    songs.find { it.song.id == newSong.id }
                                if (songWrapper != null) {
                                    update(songWrapper, newSong.toMediaMetadata())
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (ytmSync && isLoggedIn && isInternetAvailable(context)) {
            withContext(Dispatchers.IO) {
                viewModel.syncAllLibrary()
            }
        }
    }

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
            return@LaunchedEffect
        }
        isScannerActive = true
        coroutineScope.launch(Dispatchers.IO) {
            val directoryStructure = scanLocal(context).value
            syncDB(database, directoryStructure.toList())
            isScannerActive = false
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
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .combinedClickable(
                            onClick = {
                                navController.navigate("auto_playlist/liked")
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    AutoPlaylistMenu(
                                        playlist = likedMusicPlaylist,
                                        navController = navController,
                                        thumbnail = likedMusicThumbnail,
                                        iconThumbnail = Icons.Rounded.Favorite,
                                        songs = likedSongs,
                                        coroutineScope = coroutineScope,
                                        onDismiss = menuState::dismiss,
                                        showSyncLikedSongsButton = true,
                                        syncUtils = null
                                    )
                                }
                            }
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isLoggedIn && likedMusicThumbnail.isNotEmpty()) {
                                AsyncImage(
                                    model = likedMusicThumbnail,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Favorite,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (isLoggedIn && likedMusicTitle.isNotEmpty()) likedMusicTitle else {
                                    stringResource(R.string.liked)
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(1.dp))
                            Icon(
                                painter = painterResource(R.drawable.arrow_forward),
                                contentDescription = null
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = pluralStringResource(
                                R.plurals.n_song,
                                likedSongs?.size ?: 0,
                                likedSongs?.size ?: 0
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            likedSongs?.let { songs ->
                if (songs.size > 3) {
                    item {
                        Text(
                            text = stringResource(R.string.sort_by_create_date),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                        )
                    }

                    item {
                        LazyHorizontalGrid(
                            state = quickPicksLazyGridState,
                            rows = GridCells.Fixed(4),
                            flingBehavior = rememberSnapFlingBehavior(
                                quickPicksSnapLayoutInfoProvider
                            ),
                            contentPadding = WindowInsets.systemBars
                                .only(WindowInsetsSides.Horizontal)
                                .asPaddingValues(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ListItemHeight * 4)
                        ) {
                            likedSongs?.let { songs ->
                                items(
                                    items = songs.take(16),
                                    key = { song -> song.id }
                                ) { songWrapper ->
                                    SongListItem(
                                        song = songWrapper,
                                        isActive = songWrapper.song.id == mediaMetadata?.id,
                                        showInLibraryIcon = true,
                                        isSwipeable = false,
                                        isPlaying = isPlaying,
                                        trailingContent = {
                                            IconButton(
                                                onClick = {
                                                    menuState.show {
                                                        SongMenu(
                                                            originalSong = songWrapper,
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
                                            .width(horizontalLazyGridItemWidth)
                                            .combinedClickable(
                                                onClick = {
                                                    if (songWrapper.id == mediaMetadata?.id) {
                                                        playerConnection.player.togglePlayPause()
                                                    } else {
                                                        likedSongs?.let { songs ->
                                                            playerConnection.playQueue(
                                                                ListQueue(
                                                                    title = if (isLoggedIn && likedMusicTitle.isNotEmpty()) likedMusicTitle else {
                                                                        context.getString(R.string.liked)
                                                                    },
                                                                    items = songs.map { it.toMediaItem() },
                                                                    startIndex = songs.indexOfFirst { it.song.id == songWrapper.id }
                                                                )
                                                            )
                                                        }
                                                    }
                                                },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    menuState.show {
                                                        SongMenu(
                                                            originalSong = songWrapper,
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
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.also_in_your_library),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                )
            }

            item {
                val autoPlaylists = listOfNotNull(
                    downloadPlaylist to Icons.Rounded.CloudDownload,
                    topPlaylist to Icons.AutoMirrored.Rounded.TrendingUp,
                    cachedPlaylist to Icons.Rounded.Cached,
                    localPlaylist to Icons.Rounded.MusicNote,
                    playlistsPlaylist to Icons.AutoMirrored.Rounded.PlaylistPlay,
                    playlistAlbums to Icons.Rounded.Album
                )

                val rows = autoPlaylists.chunked(2)
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    navController.navigate("AutoPlaylistLibrary")
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.show {
                                        AutoPlaylistMenu(
                                            playlist = libraryMusicPlaylist,
                                            navController = navController,
                                            thumbnail = null,
                                            iconThumbnail = Icons.Rounded.LibraryMusic,
                                            songs = librarySongs,
                                            coroutineScope = coroutineScope,
                                            onDismiss = menuState::dismiss,
                                            syncUtils = null
                                        )
                                    }
                                }
                            )
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.LibraryMusic,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = stringResource(R.string.songs_from_library),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = pluralStringResource(
                                            R.plurals.n_song,
                                            librarySongs?.size ?: 0,
                                            librarySongs?.size ?: 0
                                        ),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    rows.forEach { rowItems ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rowItems.forEach { (playlist, icon) ->
                                val countText = when (playlist.id) {
                                    "albums" -> pluralStringResource(
                                        R.plurals.n_album,
                                        playlist.songCount,
                                        playlist.songCount
                                    )

                                    "user_playlists" -> pluralStringResource(
                                        R.plurals.n_playlist,
                                        playlist.songCount,
                                        playlist.songCount
                                    )

                                    else -> pluralStringResource(
                                        R.plurals.n_song,
                                        playlist.songCount,
                                        playlist.songCount
                                    )
                                }
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(80.dp)
                                        .combinedClickable(
                                            onClick = {
                                                when (playlist.id) {
                                                    "downloaded" -> navController.navigate("auto_playlist/downloaded")
                                                    "top" -> navController.navigate("top_playlist/$topSize")
                                                    "cached" -> navController.navigate("CachedPlaylist")
                                                    "local" -> navController.navigate("AutoPlaylistLocal")
                                                    "user_playlists" -> navController.navigate("library_playlists")
                                                    "albums" -> navController.navigate("library_albums")
                                                }
                                            },
                                            onLongClick = {
                                                when (playlist.id) {
                                                    "downloaded" -> {
                                                        haptic.performHapticFeedback(
                                                            HapticFeedbackType.LongPress
                                                        )
                                                        menuState.show {
                                                            AutoPlaylistMenu(
                                                                playlist = downloadPlaylist,
                                                                navController = navController,
                                                                thumbnail = null,
                                                                iconThumbnail = Icons.Rounded.CloudDownload,
                                                                songs = downloadSongs,
                                                                coroutineScope = coroutineScope,
                                                                onDismiss = menuState::dismiss,
                                                                syncUtils = null
                                                            )
                                                        }
                                                    }

                                                    "top" -> {
                                                        haptic.performHapticFeedback(
                                                            HapticFeedbackType.LongPress
                                                        )
                                                        menuState.show {
                                                            AutoPlaylistMenu(
                                                                playlist = topPlaylist,
                                                                navController = navController,
                                                                thumbnail = null,
                                                                iconThumbnail = Icons.AutoMirrored.Rounded.TrendingUp,
                                                                songs = topSongs,
                                                                coroutineScope = coroutineScope,
                                                                onDismiss = menuState::dismiss,
                                                                syncUtils = null
                                                            )
                                                        }
                                                    }

                                                    "cached" -> {
                                                        haptic.performHapticFeedback(
                                                            HapticFeedbackType.LongPress
                                                        )
                                                        menuState.show {
                                                            AutoPlaylistMenu(
                                                                playlist = cachedPlaylist,
                                                                navController = navController,
                                                                thumbnail = null,
                                                                iconThumbnail = Icons.Rounded.Cached,
                                                                songs = cachedSongs,
                                                                coroutineScope = coroutineScope,
                                                                onDismiss = menuState::dismiss,
                                                                showRemoveFromCacheButton = true,
                                                                syncUtils = null
                                                            )
                                                        }
                                                    }

                                                    "local" -> {
                                                        haptic.performHapticFeedback(
                                                            HapticFeedbackType.LongPress
                                                        )
                                                        menuState.show {
                                                            AutoPlaylistMenu(
                                                                playlist = localPlaylist,
                                                                navController = navController,
                                                                thumbnail = null,
                                                                iconThumbnail = Icons.Rounded.MusicNote,
                                                                songs = localSongs,
                                                                coroutineScope = coroutineScope,
                                                                onDismiss = menuState::dismiss,
                                                                showSyncLocalSongsButton = true,
                                                                showM3UBackupButton = false,
                                                                syncUtils = null
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 16.dp)
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = playlist.playlist.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = countText,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            artists?.let { allArtists ->
                if (allArtists.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 16.dp)
                                .clickable { navController.navigate("library_artists") }
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.your_artists),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(1.dp))
                            Icon(
                                painter = painterResource(R.drawable.arrow_forward),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    item {
                        LazyRow(
                            modifier = Modifier.animateItem()
                        ) {
                            items(
                                items = allArtists.take(8),
                                key = { it.id }
                            ) { artist ->
                                ArtistGridItem(
                                    artist = artist,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                navController.navigate("artist/${artist.id}")
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show {
                                                    ArtistMenu(
                                                        originalArtist = artist,
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
                }
            }
        }
        if (librarySongs?.isEmpty() != true) {
            HideOnScrollFAB(
                lazyListState = lazyListState,
                icon = R.drawable.library_music,
                onClick = {
                    librarySongs?.let { songs ->
                        playerConnection.playQueue(
                            ListQueue(
                                title = context.getString(R.string.filter_library),
                                items = songs.take(100).shuffled().map { it.toMediaItem() }
                            )
                        )
                    }
                }
            )
        }
        if (ytmSync && isLoggedIn && isInternetAvailable(context)) {
            Indicator(
                isRefreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
            )
        }
    }
}