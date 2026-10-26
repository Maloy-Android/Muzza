package com.maloy.muzza.ui.screens

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.maloy.muzza.LocalPlayerAwareWindowInsets
import com.maloy.muzza.LocalPlayerConnection
import com.maloy.muzza.R
import com.maloy.muzza.constants.LibraryViewType
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.util.fastForEachReversed
import androidx.navigation.compose.currentBackStackEntryAsState
import com.maloy.innertube.models.WatchEndpoint
import com.maloy.muzza.constants.ClipViewTypeKey
import com.maloy.muzza.extensions.toMediaItem
import com.maloy.muzza.models.toMediaMetadata
import com.maloy.muzza.playback.queues.ListQueue
import com.maloy.muzza.playback.queues.YouTubeQueue
import com.maloy.muzza.ui.component.HideOnScrollFAB
import com.maloy.muzza.ui.component.IconButton
import com.maloy.muzza.ui.component.LazyColumnScrollbar
import com.maloy.muzza.ui.component.LazyVerticalGridScrollbar
import com.maloy.muzza.ui.component.LocalMenuState
import com.maloy.muzza.ui.component.YouTubeGridItem
import com.maloy.muzza.ui.component.YouTubeListItem
import com.maloy.muzza.ui.component.shimmer.GridItemPlaceHolder
import com.maloy.muzza.ui.component.shimmer.ListItemPlaceHolder
import com.maloy.muzza.ui.component.shimmer.ShimmerHost
import com.maloy.muzza.ui.menu.YouTubeSongMenu
import com.maloy.muzza.ui.menu.YouTubeSongSelectionMenu
import com.maloy.muzza.ui.utils.backToMain
import com.maloy.muzza.utils.rememberEnumPreference
import com.maloy.muzza.viewmodels.NewClipsViewModel
import kotlin.collections.isNotEmpty
import kotlin.collections.map

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NewClipScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: NewClipsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val lazyGridState = rememberLazyGridState()
    val gridChecker by remember {
        derivedStateOf {
            lazyGridState.firstVisibleItemIndex > 0
        }
    }

    val lazyListState = rememberLazyListState()

    val backStackEntry by navController.currentBackStackEntryAsState()

    val lazyChecker by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0
        }
    }

    var viewType by rememberEnumPreference(ClipViewTypeKey, LibraryViewType.GRID)

    val newClips by viewModel.newClips.collectAsState()

    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection = rememberSaveable(
        saver = listSaver<MutableList<String>, String>(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf() }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
    }
    if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    LaunchedEffect(inSelectMode) {
        backStackEntry?.savedStateHandle?.set("inSelectMode", inSelectMode)
    }
    LaunchedEffect(newClips) {
        selection.fastForEachReversed { songId ->
            if (newClips.find { it.id == songId } == null) {
                selection.remove(songId)
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
        when (viewType) {
            LibraryViewType.LIST -> {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
                ) {
                    items(
                        items = newClips,
                        key = { it.id }
                    ) { clip ->
                        val onCheckedChange: (Boolean) -> Unit = {
                            if (it) {
                                selection.add(clip.id)
                            } else {
                                selection.remove(clip.id)
                            }
                        }
                        YouTubeListItem(
                            item = clip,
                            isActive = mediaMetadata?.id == clip.id,
                            isSwipeable = !inSelectMode,
                            isPlaying = isPlaying,
                            trailingContent = {
                                if (inSelectMode) {
                                    Checkbox(
                                        checked = clip.id in selection,
                                        onCheckedChange = onCheckedChange
                                    )
                                } else {
                                    androidx.compose.material3.IconButton(
                                        onClick = {
                                            menuState.show {
                                                YouTubeSongMenu(
                                                    song = clip,
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
                                    onClick = {
                                        if (inSelectMode) {
                                            onCheckedChange(clip.id !in selection)
                                        } else if (mediaMetadata?.id == clip.id) {
                                            playerConnection.togglePlayPause()
                                        } else {
                                            playerConnection.playQueue(
                                                YouTubeQueue(
                                                    title = clip.title,
                                                    endpoint = clip.endpoint
                                                        ?: WatchEndpoint(
                                                            videoId = clip.id,
                                                        ),
                                                    preloadItem = clip.toMediaMetadata(),
                                                    context = context
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
                        )
                    }
                    if (newClips.isEmpty()) {
                        item(key = "loading") {
                            ShimmerHost {
                                repeat(8) {
                                    ListItemPlaceHolder()
                                }
                            }
                        }
                    }
                }
                LazyColumnScrollbar(
                    visible = lazyChecker,
                    state = lazyListState
                )
                newClips.let { songs ->
                    HideOnScrollFAB(
                        visible = songs.isNotEmpty() && !inSelectMode,
                        lazyListState = lazyListState,
                        icon = R.drawable.play,
                        onClick = {
                            playerConnection.playQueue(
                                ListQueue(
                                    title = context.getString(R.string.new_clips),
                                    items = songs.map { it.toMediaItem() }
                                )
                            )
                        }
                    )
                }
            }

            LibraryViewType.GRID -> {
                LazyVerticalGrid(
                    state = lazyGridState,
                    columns = GridCells.Fixed(1),
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
                ) {
                    items(
                        items = newClips,
                        key = { it.id }
                    ) { clip ->
                        YouTubeGridItem(
                            item = clip,
                            isActive = mediaMetadata?.id == clip.id,
                            isPlaying = isPlaying,
                            videoThumbnailSize = true,
                            fillMaxWidth = true,
                            navController = navController,
                            modifier = Modifier
                                .combinedClickable(
                                    onClick = {
                                        if (mediaMetadata?.id == clip.id) {
                                            playerConnection.togglePlayPause()
                                        } else {
                                            playerConnection.playQueue(
                                                YouTubeQueue(
                                                    title = clip.title,
                                                    endpoint = clip.endpoint
                                                        ?: WatchEndpoint(
                                                            videoId = clip.id,
                                                        ),
                                                    preloadItem = clip.toMediaMetadata(),
                                                    context = context
                                                )
                                            )
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.show {
                                            YouTubeSongMenu(
                                                song = clip,
                                                navController = navController,
                                                onDismiss = menuState::dismiss
                                            )
                                        }
                                    }
                                )
                        )
                    }
                    if (newClips.isEmpty()) {
                        items(8) {
                            ShimmerHost {
                                GridItemPlaceHolder(videoThumbnail = true)
                            }
                        }
                    }
                }
                LazyVerticalGridScrollbar(
                    visible = gridChecker,
                    state = lazyGridState
                )
                newClips.let { songs ->
                    HideOnScrollFAB(
                        visible = songs.isNotEmpty(),
                        lazyListState = lazyGridState,
                        icon = R.drawable.play,
                        onClick = {
                            playerConnection.playQueue(
                                ListQueue(
                                    title = context.getString(R.string.new_clips),
                                    items = songs.map { it.toMediaItem() }
                                )
                            )
                        }
                    )
                }
            }
        }

        if (newClips.isNotEmpty()) {
            Indicator(
                isRefreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
            )
        }
    }

    if (inSelectMode) {
        CenterAlignedTopAppBar(
            title = {
                Text(pluralStringResource(R.plurals.n_selected, selection.size, selection.size))
            },
            navigationIcon = {
                androidx.compose.material3.IconButton(onClick = onExitSelectionMode) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = null,
                    )
                }
            },
            actions = {
                Checkbox(
                    checked = selection.size == newClips.size && selection.isNotEmpty(),
                    onCheckedChange = {
                        if (selection.size == newClips.size) {
                            selection.clear()
                        } else {
                            selection.clear()
                            selection.addAll(newClips.map { it.id })
                        }
                    }
                )
                androidx.compose.material3.IconButton(
                    enabled = selection.isNotEmpty(),
                    onClick = {
                        menuState.show {
                            YouTubeSongSelectionMenu(
                                navController = navController,
                                selection = selection.mapNotNull { songId ->
                                    newClips.find { it.id == songId }
                                },
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
            }
        )
    } else {
        CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.new_clips)) },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain
                ) {
                    Icon(
                        painterResource(R.drawable.arrow_back),
                        contentDescription = null
                    )
                }
            },
            actions = {
                if (newClips.isNotEmpty()) {
                    androidx.compose.material3.IconButton(
                        onClick = {
                            viewType = viewType.toggle()
                        }
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
            },
            scrollBehavior = scrollBehavior
        )
    }
}