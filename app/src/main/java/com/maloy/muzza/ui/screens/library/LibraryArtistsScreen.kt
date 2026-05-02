package com.maloy.muzza.ui.screens.library

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.maloy.innertube.utils.parseCookieString
import com.maloy.muzza.LocalPlayerAwareWindowInsets
import com.maloy.muzza.R
import com.maloy.muzza.constants.ArtistFilter
import com.maloy.muzza.constants.ArtistFilterKey
import com.maloy.muzza.constants.ArtistSortDescendingKey
import com.maloy.muzza.constants.ArtistSortType
import com.maloy.muzza.constants.ArtistSortTypeKey
import com.maloy.muzza.constants.ArtistViewTypeKey
import com.maloy.muzza.constants.CONTENT_TYPE_ARTIST
import com.maloy.muzza.constants.CONTENT_TYPE_HEADER
import com.maloy.muzza.constants.GridCellSize
import com.maloy.muzza.constants.GridCellSizeKey
import com.maloy.muzza.constants.GridThumbnailHeight
import com.maloy.muzza.constants.InnerTubeCookieKey
import com.maloy.muzza.constants.LibraryViewType
import com.maloy.muzza.constants.SmallGridThumbnailHeight
import com.maloy.muzza.constants.YtmSyncKey
import com.maloy.muzza.ui.component.ArtistGridItem
import com.maloy.muzza.ui.component.ArtistListItem
import com.maloy.muzza.ui.component.ChipsRow
import com.maloy.muzza.ui.component.EmptyPlaceholder
import com.maloy.muzza.ui.component.LazyColumnScrollbar
import com.maloy.muzza.ui.component.LazyVerticalGridScrollbar
import com.maloy.muzza.ui.component.LocalMenuState
import com.maloy.muzza.ui.component.SortHeader
import com.maloy.muzza.ui.menu.ArtistMenu
import com.maloy.muzza.ui.utils.backToMain
import com.maloy.muzza.utils.isInternetAvailable
import com.maloy.muzza.utils.rememberEnumPreference
import com.maloy.muzza.utils.rememberPreference
import com.maloy.muzza.utils.rememberVoiceInput
import com.maloy.muzza.viewmodels.LibraryArtistsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryArtistsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: LibraryArtistsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current

    var filter by rememberEnumPreference(ArtistFilterKey, ArtistFilter.LIKED)
    val gridCellSize by rememberEnumPreference(GridCellSizeKey, GridCellSize.BIG)
    var viewType by rememberEnumPreference(ArtistViewTypeKey, LibraryViewType.GRID)
    val (sortType, onSortTypeChange) = rememberEnumPreference(ArtistSortTypeKey, ArtistSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(ArtistSortDescendingKey, true)
    val (ytmSync) = rememberPreference(YtmSyncKey, true)
    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn =
        remember(innerTubeCookie) {
            "SAPISID" in parseCookieString(innerTubeCookie)
        }

    val filterContent = @Composable {
        Row {
            ChipsRow(
                chips =
                listOf(
                    ArtistFilter.LIKED to stringResource(R.string.filter_liked),
                    ArtistFilter.LIBRARY to stringResource(R.string.filter_library),
                ),
                currentValue = filter,
                onValueUpdate = {
                    filter = it
                },
                modifier = Modifier.weight(1f),
            )
        }
    }

    LaunchedEffect(Unit) {
        if (ytmSync && isLoggedIn && isInternetAvailable(context))
            withContext(Dispatchers.IO) {
                viewModel.sync()
        }
    }

    val artists by viewModel.allArtists.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop = backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()
    val lazyChecker by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0
        }
    }
    val gridChecker by remember {
        derivedStateOf {
            lazyGridState.firstVisibleItemIndex > 0
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

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }
    val (startVoiceInput,isVoiceInputAvailable) = rememberVoiceInput(
        onResult = { recognizedText ->
            searchQuery = TextFieldValue(recognizedText)
        }
    )
    val focusRequester = remember { FocusRequester() }
    val searchQueryStr = searchQuery.text.trim()
    val filteredArtists = if (searchQueryStr.isEmpty()) {
        artists
    } else {
        artists?.filter { artists ->
            artists.title.contains(searchQueryStr, ignoreCase = true)
        }
    }

    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    val onExitSearchingMode = {
        isSearching = false
        searchQuery = TextFieldValue("")
    }

    if (isSearching) {
        BackHandler(onBack = onExitSearchingMode)
    }

    val headerContent = @Composable {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            if (filteredArtists?.isEmpty() != true) {
                SortHeader(
                    sortType = sortType,
                    sortDescending = sortDescending,
                    onSortTypeChange = onSortTypeChange,
                    onSortDescendingChange = onSortDescendingChange,
                    sortTypeText = { sortType ->
                        when (sortType) {
                            ArtistSortType.CREATE_DATE -> R.string.sort_by_create_date
                            ArtistSortType.NAME -> R.string.sort_by_name
                            ArtistSortType.SONG_COUNT -> R.string.sort_by_song_count
                            ArtistSortType.PLAY_TIME -> R.string.sort_by_play_time
                        }
                    }
                )

                Spacer(Modifier.weight(1f))

                filteredArtists?.let { artists ->
                    Text(
                        text = pluralStringResource(R.plurals.n_artist, artists.size, artists.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                IconButton(
                    onClick = {
                        viewType = viewType.toggle()
                    },
                    modifier = Modifier.padding(start = 6.dp)
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

                    filteredArtists?.let { artists ->
                        if (artists.isEmpty()) {
                            if (isSearching) {
                                item {
                                    EmptyPlaceholder(
                                        icon = R.drawable.search,
                                        text = stringResource(R.string.no_results_found),
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            } else {
                                item {
                                    EmptyPlaceholder(
                                        icon = R.drawable.artist,
                                        text = stringResource(R.string.library_artist_empty),
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }

                        items(
                            items = artists,
                            key = { it.id },
                            contentType = { CONTENT_TYPE_ARTIST }
                        ) { artist ->
                            ArtistListItem(
                                artist = artist,
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            menuState.show {
                                                ArtistMenu(
                                                    originalArtist = artist,
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
                                    .animateItem(),
                            )
                        }
                    }
                }
                LazyColumnScrollbar(
                    visible = lazyChecker,
                    state = lazyListState
                )
            }

            LibraryViewType.GRID -> {
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

                    filteredArtists?.let { artists ->
                        if (artists.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                if (!isSearching) {
                                    EmptyPlaceholder(
                                        icon = R.drawable.search,
                                        text = stringResource(R.string.no_results_found),
                                        modifier = Modifier.animateItem()
                                    )
                                } else {
                                    EmptyPlaceholder(
                                        icon = R.drawable.artist,
                                        text = stringResource(R.string.library_artist_empty),
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }

                        items(
                            items = artists,
                            key = { it.id },
                            contentType = { CONTENT_TYPE_ARTIST }
                        ) { artist ->
                            ArtistGridItem(
                                artist = artist,
                                fillMaxWidth = true,
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
                LazyVerticalGridScrollbar(
                    visible = gridChecker,
                    state = lazyGridState
                )
            }
        }
        if (artists != null && ytmSync && isLoggedIn && isInternetAvailable(context)) {
            Indicator(
                isRefreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
            )
        }
        CenterAlignedTopAppBar(
            title = {
                if (isSearching) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
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
                            if (searchQuery.text.isEmpty() && isVoiceInputAvailable) {
                                IconButton(
                                    onClick = startVoiceInput
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.mic),
                                        contentDescription = null
                                    )
                                }
                            }
                            if (searchQuery.text.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = TextFieldValue("") }
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.close),
                                        contentDescription = null
                                    )
                                }
                            }
                        }
                    )
                } else Text(stringResource(R.string.your_artists)) },
            navigationIcon = {
                com.maloy.muzza.ui.component.IconButton(
                    onClick = {
                        if (isSearching) {
                            isSearching = false
                            searchQuery = TextFieldValue()
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
                        painter = painterResource(
                            R.drawable.arrow_back
                        ),
                        contentDescription = null
                    )
                }
            },
            actions = {
                filteredArtists?.let { artists ->
                    if (artists.isNotEmpty() && !isSearching) {
                        IconButton(
                            onClick = { isSearching = true }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.search),
                                contentDescription = null
                            )
                        }
                    }
                }
            },
            scrollBehavior = if (!isSearching) scrollBehavior else null
        )
    }
}
