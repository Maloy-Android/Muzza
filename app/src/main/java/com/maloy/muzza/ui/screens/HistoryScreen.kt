package com.maloy.muzza.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
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
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEachReversed
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.maloy.innertube.utils.parseCookieString
import com.maloy.muzza.LocalDatabase
import com.maloy.muzza.LocalPlayerAwareWindowInsets
import com.maloy.muzza.LocalPlayerConnection
import com.maloy.muzza.R
import com.maloy.muzza.constants.HistorySource
import com.maloy.muzza.constants.InnerTubeCookieKey
import com.maloy.muzza.constants.YtmSyncKey
import com.maloy.muzza.db.entities.EventWithSong
import com.maloy.muzza.extensions.toMediaItem
import com.maloy.muzza.extensions.togglePlayPause
import com.maloy.muzza.playback.queues.ListQueue
import com.maloy.muzza.ui.component.ChipsRow
import com.maloy.muzza.ui.component.EmptyPlaceholder
import com.maloy.muzza.ui.component.HideOnScrollFAB
import com.maloy.muzza.ui.component.IconButton
import com.maloy.muzza.ui.component.LazyColumnScrollbar
import com.maloy.muzza.ui.component.LocalMenuState
import com.maloy.muzza.ui.component.NavigationTitle
import com.maloy.muzza.ui.component.SongListItem
import com.maloy.muzza.ui.component.YouTubeListItem
import com.maloy.muzza.ui.menu.SongMenu
import com.maloy.muzza.ui.menu.SongSelectionMenu
import com.maloy.muzza.ui.menu.YouTubeSongMenu
import com.maloy.muzza.ui.menu.YouTubeSongSelectionMenu
import com.maloy.muzza.ui.utils.backToMain
import com.maloy.muzza.utils.isInternetAvailable
import com.maloy.muzza.utils.rememberPreference
import com.maloy.muzza.viewmodels.DateAgo
import com.maloy.muzza.viewmodels.HistoryViewModel
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    navController: NavController,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val historyPage by viewModel.historyPage
    val historySource by viewModel.historySource.collectAsState()

    val (ytmSync) = rememberPreference(YtmSyncKey, true)

    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn =
        remember(innerTubeCookie) {
            "SAPISID" in parseCookieString(innerTubeCookie)
        }

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }
    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    }

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

    val eventsMap by viewModel.events.collectAsState()
    val filteredEventsMap = remember(eventsMap, query) {
        if (query.text.isEmpty()) eventsMap
        else eventsMap
            .mapValues { (_, songs) ->
                songs.filter { song ->
                    song.song.title.contains(query.text, ignoreCase = true) ||
                            song.song.artists.fastAny { it.name.contains(query.text, ignoreCase = true) }
                }
            }
            .filterValues { it.isNotEmpty() }
    }
    val filteredEventIndex: Map<String, EventWithSong> by remember(filteredEventsMap) {
        derivedStateOf {
            filteredEventsMap.flatMap { it.value }.associateBy { it.event.id.toString() }
        }
    }

    val filteredRemoteContent = remember(historyPage, query) {
        if (query.text.isEmpty()) {
            historyPage?.sections
        } else {
            historyPage?.sections?.map { section ->
                section.copy(
                    songs = section.songs.filter { song ->
                        song.title.contains(query.text, ignoreCase = true) ||
                                song.artists.any { it.name.contains(query.text, ignoreCase = true) }
                    }
                )
            }?.filter { it.songs.isNotEmpty() }
        }
    }

    LaunchedEffect(filteredEventsMap) {
        selection.fastForEachReversed { eventId ->
            if (filteredEventIndex[eventId] == null) {
                selection.remove(eventId)
            }
        }
    }

    val lazyListState = rememberLazyListState()
    val lazyChecker by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                .union(WindowInsets.ime)
                .asPaddingValues(),
            modifier = Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Top)
            )
        ) {
            if (historySource == HistorySource.REMOTE && filteredRemoteContent.isNullOrEmpty() && isSearching ||
                historySource == HistorySource.LOCAL && filteredEventsMap.isEmpty() && isSearching
            ) {
                item {
                    EmptyPlaceholder(
                        icon = R.drawable.history,
                        text = stringResource(R.string.no_results_found),
                        modifier = Modifier
                            .fillParentMaxSize()
                            .animateItem()
                    )
                }
            }
            if (historySource == HistorySource.REMOTE && filteredRemoteContent.isNullOrEmpty() ||
                historySource == HistorySource.LOCAL && filteredEventsMap.isEmpty()
            ) {
                item {
                    EmptyPlaceholder(
                        icon = R.drawable.history,
                        text = stringResource(R.string.history_empty),
                        modifier = Modifier
                            .fillParentMaxSize()
                            .animateItem()
                    )
                }
            } else if (historySource == HistorySource.REMOTE && filteredRemoteContent?.isNotEmpty() == true ||
                historySource == HistorySource.LOCAL && filteredEventsMap.isNotEmpty()) {
                item {
                    if (ytmSync && isLoggedIn && isInternetAvailable(context) && !isSearching && !inSelectMode) {
                        ChipsRow(
                            chips = listOf(
                                HistorySource.LOCAL to stringResource(R.string.local_history),
                                HistorySource.REMOTE to stringResource(R.string.remote_history),
                            ),
                            currentValue = historySource,
                            onValueUpdate = { viewModel.historySource.value = it }
                        )
                    }
                }
                if (historySource == HistorySource.REMOTE) {
                    filteredRemoteContent?.forEach { section ->
                        stickyHeader {
                            NavigationTitle(
                                title = section.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                            )
                        }

                        items(
                            items = section.songs,
                            key = { it.id }
                        ) { song ->
                            val onCheckedChange: (Boolean) -> Unit = { checked ->
                                if (checked) {
                                    selection.add(song.id)
                                } else {
                                    selection.remove(song.id)
                                }
                            }

                            YouTubeListItem(
                                item = song,
                                isActive = song.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                trailingContent = {
                                    if (inSelectMode) {
                                        Checkbox(
                                            checked = song.id in selection,
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
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (song.id == mediaMetadata?.id) {
                                                playerConnection.player.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    ListQueue(
                                                        title = context.getString(R.string.history_queue_title_online),
                                                        items = filteredRemoteContent.map { it.songs }.flatten().map { it.toMediaItem() },
                                                        startIndex = filteredRemoteContent.map { it.songs }.flatten().indexOfFirst { it.id == song.id }
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
                } else {
                    filteredEventsMap.forEach { (dateAgo, events) ->
                        stickyHeader {
                            NavigationTitle(
                                title = when (dateAgo) {
                                    DateAgo.Today -> stringResource(R.string.today)
                                    DateAgo.Yesterday -> stringResource(R.string.yesterday)
                                    DateAgo.ThisWeek -> stringResource(R.string.this_week)
                                    DateAgo.LastWeek -> stringResource(R.string.last_week)
                                    is DateAgo.Other -> dateAgo.date.format(
                                        DateTimeFormatter.ofPattern(
                                            "yyyy/MM"
                                        )
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                            )
                        }

                        items(
                            items = events,
                            key = { it.event.id.toString() }
                        ) { event ->
                            val onCheckedChange: (Boolean) -> Unit = { checked ->
                                if (checked) {
                                    selection.add(event.event.id.toString())
                                } else {
                                    selection.remove(event.event.id.toString())
                                }
                            }

                            SongListItem(
                                song = event.song,
                                isActive = event.song.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                showInLibraryIcon = true,
                                trailingContent = {
                                    if (inSelectMode) {
                                        Checkbox(
                                            checked = event.event.id.toString() in selection,
                                            onCheckedChange = onCheckedChange
                                        )
                                    } else {
                                        IconButton(
                                            onClick = {
                                                menuState.show {
                                                    SongMenu(
                                                        originalSong = event.song,
                                                        event = event.event,
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
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (inSelectMode) {
                                                onCheckedChange(event.event.id.toString() !in selection)
                                            } else if (event.song.id == mediaMetadata?.id) {
                                                playerConnection.player.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    ListQueue(
                                                        title = context.getString(R.string.history_queue_title_local),
                                                        items = filteredEventIndex.values.map { it.song.toMediaItem() },
                                                        startIndex = filteredEventIndex.values.map { it.song }.indexOfFirst { it.id == event.song.id }
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
                }
            }
        }

        if (historySource == HistorySource.REMOTE) {
            filteredRemoteContent?.let { it ->
                HideOnScrollFAB(
                    visible = it.isNotEmpty(),
                    lazyListState = lazyListState,
                    icon = R.drawable.shuffle,
                    onClick = {
                        val songs = filteredRemoteContent.flatMap { it.songs }
                        playerConnection.playQueue(
                            ListQueue(
                                title = context.getString(R.string.history_queue_title_online),
                                items = songs.map { it.toMediaItem() }.shuffled()
                            )
                        )
                    }
                )
            }
        } else {
            HideOnScrollFAB(
                visible = filteredEventsMap.isNotEmpty(),
                lazyListState = lazyListState,
                icon = R.drawable.shuffle,
                onClick = {
                    playerConnection.playQueue(
                        ListQueue(
                            title = context.getString(R.string.history_queue_title_local),
                            items = filteredEventIndex.values.map { it.song.toMediaItem() }
                                .shuffled()
                        )
                    )
                }
            )
        }
    }
    if (lazyChecker) {
        LazyColumnScrollbar(
            state = lazyListState
        )
    }
    TopAppBar(
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
                        if (query.text.isNotEmpty()) {
                            IconButton(
                                onClick = { query= TextFieldValue("") }
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
                Text(stringResource(R.string.history))
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
            if (inSelectMode && historySource == HistorySource.LOCAL) {
                Checkbox(
                    checked = selection.size == filteredEventIndex.size && selection.isNotEmpty(),
                    onCheckedChange = {
                        if (selection.size == filteredEventIndex.size) {
                            selection.clear()
                        } else {
                            selection.clear()
                            selection.addAll(filteredEventsMap.flatMap { (_, events) ->
                                events.map { it.event.id.toString() }
                            })
                        }
                    }
                )
                IconButton(
                    enabled = selection.isNotEmpty(),
                    onClick = {
                        menuState.show {
                            SongSelectionMenu(
                                navController = navController,
                                selection = selection.mapNotNull { eventId ->
                                    filteredEventIndex[eventId]?.song
                                },
                                onDismiss = menuState::dismiss,
                                onRemoveFromHistory = {
                                    val sel = selection.mapNotNull { eventId ->
                                        filteredEventIndex[eventId]?.event
                                    }
                                    database.query {
                                        sel.forEach {
                                            delete(it)
                                        }
                                    }
                                    onExitSelectionMode()
                                },
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
            } else if (inSelectMode && historySource == HistorySource.REMOTE) {
                val allRemoteSongIds = filteredRemoteContent?.flatMap { section -> section.songs.map { it.id } } ?: emptyList()
                Checkbox(
                    checked = selection.size == allRemoteSongIds.size && selection.isNotEmpty(),
                    onCheckedChange = { checked ->
                        if (checked) {
                            selection.clear()
                            selection.addAll(allRemoteSongIds)
                        } else {
                            selection.clear()
                        }
                    }
                )
                IconButton(
                    enabled = selection.isNotEmpty(),
                    onClick = {
                        menuState.show {
                            YouTubeSongSelectionMenu(
                                navController = navController,
                                showPlayNextButton = false,
                                selection = selection.mapNotNull { songId ->
                                    filteredRemoteContent?.flatMap { it.songs }?.find { it.id == songId }
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
            } else if (!isSearching) {
                IconButton(
                    onClick = { isSearching = true }
                ) {
                    Icon(
                        painterResource(R.drawable.search),
                        contentDescription = null
                    )
                }
            }
        }
    )
}