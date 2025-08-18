package com.dreamify.app.ui.screens.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
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
import androidx.compose.ui.util.fastForEachReversed
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.dreamify.innertube.utils.parseCookieString
import com.dreamify.app.LocalPlayerAwareWindowInsets
import com.dreamify.app.LocalPlayerConnection
import com.dreamify.app.R
import com.dreamify.app.constants.AppDesignVariantKey
import com.dreamify.app.constants.AppDesignVariantType
import com.dreamify.app.constants.CONTENT_TYPE_HEADER
import com.dreamify.app.constants.CONTENT_TYPE_SONG
import com.dreamify.app.constants.ChipSortTypeKey
import com.dreamify.app.constants.InnerTubeCookieKey
import com.dreamify.app.constants.LibraryFilter
import com.dreamify.app.constants.SongFilter
import com.dreamify.app.constants.SongFilterKey
import com.dreamify.app.constants.SongSortDescendingKey
import com.dreamify.app.constants.SongSortType
import com.dreamify.app.constants.SongSortTypeKey
import com.dreamify.app.constants.YtmSyncKey
import com.dreamify.app.extensions.toMediaItem
import com.dreamify.app.extensions.togglePlayPause
import com.dreamify.app.playback.queues.ListQueue
import com.dreamify.app.ui.component.ChipsRow
import com.dreamify.app.ui.component.EmptyPlaceholder
import com.dreamify.app.ui.component.HideOnScrollFAB
import com.dreamify.app.ui.component.LocalMenuState
import com.dreamify.app.ui.component.SongListItem
import com.dreamify.app.ui.component.SortHeader
import com.dreamify.app.ui.menu.SongMenu
import com.dreamify.app.ui.menu.SongSelectionMenu
import com.dreamify.app.utils.isInternetAvailable
import com.dreamify.app.utils.rememberEnumPreference
import com.dreamify.app.utils.rememberPreference
import com.dreamify.app.viewmodels.LibrarySongsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibrarySongsScreen(
    navController: NavController,
    viewModel: LibrarySongsViewModel = hiltViewModel(),
) {
    var filterType by rememberEnumPreference(ChipSortTypeKey, LibraryFilter.LIBRARY)
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    var filter by rememberEnumPreference(SongFilterKey, SongFilter.LIKED)

    val (sortType, onSortTypeChange) = rememberEnumPreference(
        SongSortTypeKey,
        SongSortType.CREATE_DATE
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(SongSortDescendingKey, true)
    val (ytmSync) = rememberPreference(YtmSyncKey, true)
    val (appDesignVariant) = rememberEnumPreference(
        AppDesignVariantKey,
        defaultValue = AppDesignVariantType.NEW
    )
    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn =
        remember(innerTubeCookie) {
            "SAPISID" in parseCookieString(innerTubeCookie)
        }

    val songs by viewModel.allSongs.collectAsState()

    LaunchedEffect(Unit) {
        if (ytmSync && isLoggedIn && isInternetAvailable(context)) {
            withContext(Dispatchers.IO) {
                viewModel.syncLikedSongs()
            }
        }
    }

    val lazyListState = rememberLazyListState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

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

    val focusRequester = remember { FocusRequester() }
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }
    val searchQueryStr = searchQuery.text.trim()
    val filteredSongs = if (searchQueryStr.isEmpty()) {
        songs
    } else {
        songs.filter { song ->
            song.song.title.contains(searchQueryStr, ignoreCase = true) ||
                    song.artists.joinToString("")
                        .contains(searchQueryStr, ignoreCase = true)
        }
    }

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazyListState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    LaunchedEffect(inSelectMode) {
        backStackEntry?.savedStateHandle?.set("inSelectMode", inSelectMode)
    }

    LaunchedEffect(filteredSongs) {
        selection.fastForEachReversed { songId ->
            if (filteredSongs.find { it.id == songId } == null) {
                selection.remove(songId)
            }
        }
    }

    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(inSelectMode) {
        backStackEntry?.savedStateHandle?.set("inSelectMode", inSelectMode)
    }

    LaunchedEffect(isSearching) {
        backStackEntry?.savedStateHandle?.set("isSearching", isSearching)
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
        ) {
            item(
                key = "filter",
                contentType = CONTENT_TYPE_HEADER
            ) {
                Row {
                    Spacer(Modifier.width(12.dp))
                    if (appDesignVariant == AppDesignVariantType.NEW && !isSearching && !inSelectMode) {
                        FilterChip(
                            label = { Text(stringResource(R.string.songs)) },
                            selected = true,
                            colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface),
                            onClick = { filterType = LibraryFilter.LIBRARY },
                            shape = RoundedCornerShape(16.dp),
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.close),
                                    contentDescription = ""
                                )
                            },
                        )
                    }
                    if (!isSearching && !inSelectMode) {
                        ChipsRow(
                            chips =
                                listOf(
                                    SongFilter.LIKED to stringResource(R.string.filter_liked),
                                    SongFilter.LIBRARY to stringResource(R.string.filter_library),
                                    SongFilter.DOWNLOADED to stringResource(R.string.filter_downloaded),
                                    SongFilter.CACHED to stringResource(R.string.cached)
                                ),
                            currentValue = filter,
                            onValueUpdate = {
                                filter = it
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            item(
                key = "header",
                contentType = CONTENT_TYPE_HEADER
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    if (filteredSongs.isNotEmpty() && filter != SongFilter.CACHED) {
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
                                    SongSortType.PLAY_TIME -> R.string.sort_by_play_time
                                }
                            }
                        )
                        if (filteredSongs.isNotEmpty() && !inSelectMode) {
                            Spacer(Modifier.weight(1f))
                            if (!isSearching) {
                                IconButton(
                                    onClick = {
                                        isSearching = !isSearching
                                        searchQuery = TextFieldValue("")
                                    },
                                    Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.search),
                                        contentDescription = null
                                    )
                                }
                            }
                        }
                    } else {
                        if (filteredSongs.isNotEmpty() && !inSelectMode) {
                            IconButton(
                                onClick = {
                                    isSearching = !isSearching
                                    searchQuery = TextFieldValue("")
                                },
                                Modifier.size(20.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.search),
                                    contentDescription = null
                                )
                            }
                        }
                    }
                    if (filteredSongs.isNotEmpty()) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = pluralStringResource(
                                R.plurals.n_song,
                                filteredSongs.size,
                                filteredSongs.size
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
            if (filteredSongs.isEmpty() && !isSearching) {
                item {
                    EmptyPlaceholder(
                        icon = R.drawable.music_note,
                        text = stringResource(R.string.library_song_empty),
                        modifier = Modifier.animateItem()
                    )
                }
            }
            if (filteredSongs.isEmpty() && isSearching) {
                item {
                    EmptyPlaceholder(
                        icon = R.drawable.search,
                        text = stringResource(R.string.no_results_found)
                    )
                }
            }

            itemsIndexed(
                items = filteredSongs,
                key = { _, item -> item.id },
                contentType = { _, _ -> CONTENT_TYPE_SONG }
            ) { _, song ->
                val onCheckedChange: (Boolean) -> Unit = {
                    if (it) {
                        selection.add(song.id)
                    } else {
                        selection.remove(song.id)
                    }
                }

                SongListItem(
                    song = song,
                    isActive = song.id == mediaMetadata?.id,
                    showInLibraryIcon = true,
                    isPlaying = isPlaying,
                    trailingContent = {
                        if (inSelectMode) {
                            Checkbox(
                                checked = song.id in selection,
                                onCheckedChange = onCheckedChange
                            )
                        } else {
                            if (filter == SongFilter.CACHED) {
                                IconButton(
                                    onClick = {
                                        menuState.show {
                                            SongMenu(
                                                originalSong = song,
                                                navController = navController,
                                                onDismiss = menuState::dismiss,
                                                isFromCache = true
                                            )
                                        }
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.more_vert),
                                        contentDescription = null
                                    )
                                }
                            } else {
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
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                if (inSelectMode) {
                                    onCheckedChange(song.id !in selection)
                                } else if (song.id == mediaMetadata?.id) {
                                    playerConnection.player.togglePlayPause()
                                } else {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = when (filter) {
                                                SongFilter.LIKED -> context.getString(R.string.liked)
                                                SongFilter.LIBRARY -> context.getString(R.string.filter_library)
                                                SongFilter.DOWNLOADED -> context.getString(R.string.downloaded_songs)
                                                SongFilter.CACHED -> context.getString(R.string.cached)
                                            },
                                            items = songs.map { it.toMediaItem() },
                                            startIndex = songs.indexOfFirst { it.song.id == song.id }
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
                        .animateItem()
                )
            }
        }

        HideOnScrollFAB(
            visible = songs.isNotEmpty(),
            lazyListState = lazyListState,
            icon = R.drawable.shuffle,
            onClick = {
                ListQueue(
                    title = context.getString(R.string.queue_all_songs),
                    items = songs.shuffled()
                        .map { it.toMediaItem() }
                )
            }
        )
    }

    if (inSelectMode) {
        TopAppBar(
            title = {
                Text(pluralStringResource(R.plurals.n_selected, selection.size, selection.size))
            },
            navigationIcon = {
                IconButton(onClick = onExitSelectionMode) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = null,
                    )
                }
            },
            actions = {
                Checkbox(
                    checked = selection.size == filteredSongs.size && selection.isNotEmpty(),
                    onCheckedChange = {
                        if (selection.size == filteredSongs.size) {
                            selection.clear()
                        } else {
                            selection.clear()
                            selection.addAll(filteredSongs.map { it.id })
                        }
                    }
                )
                if (filter == SongFilter.CACHED) {
                    IconButton(
                        enabled = selection.isNotEmpty(),
                        onClick = {
                            menuState.show {
                                SongSelectionMenu(
                                    navController = navController,
                                    selection = selection.mapNotNull { songId ->
                                        filteredSongs.find { it.id == songId }
                                    },
                                    isFromCache = true,
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
                } else {
                    IconButton(
                        enabled = selection.isNotEmpty(),
                        onClick = {
                            menuState.show {
                                SongSelectionMenu(
                                    navController = navController,
                                    selection = selection.mapNotNull { songId ->
                                        filteredSongs.find { it.id == songId }
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
            }
        )
    } else {
        if (isSearching) {
            TopAppBar(
                title = {
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
                },
                navigationIcon = {
                    if (isSearching) {
                        com.dreamify.app.ui.component.IconButton(
                            onClick = {
                                isSearching = false
                                searchQuery = TextFieldValue()
                            },
                            onLongClick = {
                                isSearching = false
                                searchQuery = TextFieldValue()
                            }
                        ) {
                            Icon(
                                painter = painterResource(
                                    R.drawable.arrow_back
                                ),
                                contentDescription = null
                            )
                        }
                    }
                }
            )
        }
    }
}
