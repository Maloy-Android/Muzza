package com.maloy.muzza.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.maloy.muzza.LocalDatabase
import com.maloy.muzza.LocalPlayerAwareWindowInsets
import com.maloy.muzza.LocalPlayerConnection
import com.maloy.muzza.R
import com.maloy.muzza.db.entities.EventWithSong
import com.maloy.muzza.extensions.togglePlayPause
import com.maloy.muzza.models.toMediaMetadata
import com.maloy.muzza.playback.queues.YouTubeQueue
import com.maloy.muzza.ui.component.IconButton
import com.maloy.muzza.ui.component.LocalMenuState
import com.maloy.muzza.ui.component.NavigationTitle
import com.maloy.muzza.ui.component.SongListItem
import com.maloy.muzza.ui.menu.SongMenu
import com.maloy.muzza.ui.menu.SongSelectionMenu
import com.maloy.muzza.ui.utils.backToMain
import com.maloy.muzza.viewmodels.DateAgo
import com.maloy.muzza.viewmodels.HistoryViewModel
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    navController: NavController,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val events by viewModel.events.collectAsState()
    val eventIndex: Map<Long, EventWithSong> by remember(events) {
        derivedStateOf {
            events.flatMap { it.value }.associateBy { it.event.id }
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

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom).asPaddingValues(),
        modifier = Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top))
    ) {
        events.forEach { (dateAgo, events) ->
            stickyHeader {
                NavigationTitle(
                    title = when (dateAgo) {
                        DateAgo.Today -> stringResource(R.string.today)
                        DateAgo.Yesterday -> stringResource(R.string.yesterday)
                        DateAgo.ThisWeek -> stringResource(R.string.this_week)
                        DateAgo.LastWeek -> stringResource(R.string.last_week)
                        is DateAgo.Other -> dateAgo.date.format(DateTimeFormatter.ofPattern("yyyy/MM"))
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

    TopAppBar(
        title = {
            if (inSelectMode) {
                Text(pluralStringResource(R.plurals.n_selected, selection.size, selection.size))
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
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain
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
                    checked = selection.size == eventIndex.size && selection.isNotEmpty(),
                    onCheckedChange = {
                        if (selection.size == eventIndex.size) {
                            selection.clear()
                        } else {
                            selection.clear()
                            selection.addAll(events.flatMap { group ->
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
                                selection = selection.mapNotNull { eventId ->
                                    eventIndex[eventId]?.song
                                },
                                onDismiss = menuState::dismiss,
                                onRemoveFromHistory = {
                                    val sel = selection.mapNotNull { eventId ->
                                        eventIndex[eventId]?.event
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
            }
        }
    )
}