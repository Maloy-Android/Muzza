package com.maloy.muzza.ui.screens.library

import android.Manifest
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil.compose.AsyncImage
import com.maloy.innertube.utils.parseCookieString
import com.maloy.muzza.LocalDatabase
import com.maloy.muzza.LocalPlayerAwareWindowInsets
import com.maloy.muzza.LocalPlayerConnection
import com.maloy.muzza.R
import com.maloy.muzza.constants.AutoSyncLocalSongsKey
import com.maloy.muzza.constants.InnerTubeCookieKey
import com.maloy.muzza.constants.ListItemHeight
import com.maloy.muzza.constants.ScannerSensitivity
import com.maloy.muzza.constants.ScannerSensitivityKey
import com.maloy.muzza.constants.ScannerStrictExtKey
import com.maloy.muzza.constants.YtmSyncKey
import com.maloy.muzza.constants.likedMusicThumbnailKey
import com.maloy.muzza.constants.likedMusicTitleKey
import com.maloy.muzza.db.entities.Playlist
import com.maloy.muzza.db.entities.PlaylistEntity
import com.maloy.muzza.extensions.toMediaItem
import com.maloy.muzza.extensions.togglePlayPause
import com.maloy.muzza.playback.queues.ListQueue
import com.maloy.muzza.ui.component.ArtistGridItem
import com.maloy.muzza.ui.component.LocalMenuState
import com.maloy.muzza.ui.component.SongListItem
import com.maloy.muzza.ui.menu.ArtistMenu
import com.maloy.muzza.ui.menu.SongMenu
import com.maloy.muzza.ui.utils.SnapLayoutInfoProvider
import com.maloy.muzza.ui.utils.scanLocal
import com.maloy.muzza.ui.utils.syncDB
import com.maloy.muzza.utils.isInternetAvailable
import com.maloy.muzza.utils.rememberEnumPreference
import com.maloy.muzza.utils.rememberPreference
import com.maloy.muzza.viewmodels.LibraryMixViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val localSongsCount by viewModel.localSongsCount.collectAsState()
    val cachedSongs by viewModel.cachedSongs.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val playlists by viewModel.playlists.collectAsState()

    val artists by viewModel.artists.collectAsState()

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
        songCount = localSongsCount,
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
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    val view = LocalView.current
    val density = LocalDensity.current
    val screenWidth = with(density) { view.width.toDp() }

    val (likedMusicThumbnail) = rememberPreference(likedMusicThumbnailKey, defaultValue = "")
    val (likedMusicTitle) = rememberPreference(likedMusicTitleKey, defaultValue = "")

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

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            modifier = Modifier.fillMaxSize()
        ) {
            if (!likedSongs.isNullOrEmpty()) {
                item {
                    Card(
                        onClick = {
                            navController.navigate("auto_playlist/liked")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
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
                                        stringResource(R.string.liked_songs)
                                    } + "    ->",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = pluralStringResource(
                                    R.plurals.n_song,
                                    likedSongs!!.size,
                                    likedSongs!!.size
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

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
                                                                title = context.getString(R.string.liked),
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

            item {
                Text(
                    text = stringResource(R.string.also_in_your_collection),
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
                    rows.forEach { rowItems ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rowItems.forEach { (playlist, icon) ->
                                val countText = when (playlist.id) {
                                    "albums" -> pluralStringResource(R.plurals.n_album,playlist.songCount,playlist.songCount)
                                    "user_playlists" -> pluralStringResource(R.plurals.n_playlist,playlist.songCount,playlist.songCount)
                                    else -> pluralStringResource(R.plurals.n_song,playlist.songCount,playlist.songCount)
                                }
                                Card(
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
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(80.dp)
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
            }
            item {
                Text(
                    text = stringResource(R.string.liked_artists) + "    ->",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                        .clickable { navController.navigate("library_artists") }
                )
            }

            item {
                LazyHorizontalGrid(
                    rows = GridCells.Fixed(1),
                    contentPadding = WindowInsets.systemBars
                        .only(WindowInsetsSides.Horizontal)
                        .asPaddingValues(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
                    items(
                        items = artists.take(8),
                        key = { it.id }
                    ) { artist ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(100.dp)
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
                        ) {
                            ArtistGridItem(
                                artist = artist,
                                modifier = Modifier.size(100.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = artist.artist.name,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}