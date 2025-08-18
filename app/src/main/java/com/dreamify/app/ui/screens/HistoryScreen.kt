package com.dreamify.app.ui.screens

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
import com.dreamify.innertube.utils.parseCookieString
import com.dreamify.app.LocalDatabase
import com.dreamify.app.LocalPlayerAwareWindowInsets
import com.dreamify.app.LocalPlayerConnection
import com.dreamify.app.R
import com.dreamify.app.constants.HistorySource
import com.dreamify.app.constants.InnerTubeCookieKey
import com.dreamify.app.constants.YtmSyncKey
import com.dreamify.app.db.entities.EventWithSong
import com.dreamify.app.extensions.toMediaItem
import com.dreamify.app.extensions.togglePlayPause
import com.dreamify.app.models.toMediaMetadata
import com.dreamify.app.playback.queues.ListQueue
import com.dreamify.app.playback.queues.YouTubeQueue
import com.dreamify.app.ui.component.ChipsRow
import com.dreamify.app.ui.component.EmptyPlaceholder
import com.dreamify.app.ui.component.HideOnScrollFAB
import com.dreamify.app.ui.component.IconButton
import com.dreamify.app.ui.component.LocalMenuState
import com.dreamify.app.ui.component.NavigationTitle
import com.dreamify.app.ui.component.SongListItem
import com.dreamify.app.ui.component.YouTubeListItem
import com.dreamify.app.ui.menu.SongMenu
import com.dreamify.app.ui.menu.SongSelectionMenu
import com.dreamify.app.ui.menu.YouTubeSongMenu
import com.dreamify.app.ui.utils.backToMain
import com.dreamify.app.utils.isInternetAvailable
import com.dreamify.app.utils.rememberPreference
import com.dreamify.app.viewmodels.DateAgo
import com.dreamify.app.viewmodels.HistoryViewModel
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
        saver = listSaver<MutableList<Long>, Long>(
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
    val filteredEventIndex: Map<Long, EventWithSong> by remember(filteredEventsMap) {
        derivedStateOf {
            filteredEventsMap.flatMap { it.value }.associateBy { it.event.id }
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
                    if (ytmSync && isLoggedIn && isInternetAvailable(context)) {
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
                            YouTubeListItem(
                                item = song,
                                isActive = song.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                trailingContent = {
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
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (song.id == mediaMetadata?.id) {
                                                playerConnection.player.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    (YouTubeQueue.radio(song.toMediaMetadata()))
                                                )
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(
                                                HapticFeedbackType.LongPress,
                                            )
                                            menuState.show {
                                                YouTubeSongMenu(
                                                    song = song,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )
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
                            key = { it.event.id }
                        ) { event ->
                            val onCheckedChange: (Boolean) -> Unit = {
                                if (it) {
                                    selection.add(event.event.id)
                                } else {
                                    selection.remove(event.event.id)
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
                                                checked = event.event.id in selection,
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
                                                onCheckedChange(event.event.id !in selection)
                                            } else if (event.song.id == mediaMetadata?.id) {
                                                playerConnection.player.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    YouTubeQueue.radio(event.song.toMediaMetadata())
                                                )
                                            }
                                        },onLongClick = {
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
            if (inSelectMode) {
                Checkbox(
                    checked = selection.size == filteredEventIndex.size && selection.isNotEmpty(),
                    onCheckedChange = {
                        if (selection.size == filteredEventIndex.size) {
                            selection.clear()
                        } else {
                            selection.clear()
                            selection.addAll(filteredEventsMap.flatMap { group ->
                                group.value.map { it.event.id }
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