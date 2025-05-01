package com.maloy.muzza.ui.screens.playlist

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.maloy.muzza.LocalDatabase
import com.maloy.muzza.LocalPlayerAwareWindowInsets
import com.maloy.muzza.LocalPlayerConnection
import com.maloy.muzza.R
import com.maloy.muzza.constants.*
import com.maloy.muzza.extensions.toMediaItem
import com.maloy.muzza.extensions.togglePlayPause
import com.maloy.muzza.playback.queues.ListQueue
import com.maloy.muzza.ui.component.AutoResizeText
import com.maloy.muzza.ui.component.FontSizeRange
import com.maloy.muzza.ui.component.LocalMenuState
import com.maloy.muzza.ui.component.SongListItem
import com.maloy.muzza.ui.menu.SongMenu
import com.maloy.muzza.ui.screens.Screens
import com.maloy.muzza.ui.utils.getDirectorytree
import com.maloy.muzza.ui.component.SongFolderItem
import com.maloy.muzza.ui.utils.scanLocal
import com.maloy.muzza.ui.utils.syncDB
import com.maloy.muzza.utils.rememberEnumPreference
import com.maloy.muzza.utils.rememberPreference
import com.maloy.muzza.viewmodels.LibrarySongsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@SuppressLint("StateFlowValueCalledInComposition")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoPlaylistLocalScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: LibrarySongsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val folderStack = remember { viewModel.folderPositionStack }
    val coroutineScope = rememberCoroutineScope()
    var isScannerActive by remember { mutableStateOf(false) }
    var isScanFinished by remember { mutableStateOf(false) }

    val (scannerSensitivity) = rememberEnumPreference(
        key = ScannerSensitivityKey,
        defaultValue = ScannerSensitivity.LEVEL_2
    )
    val (strictExtensions) = rememberPreference(ScannerStrictExtKey, defaultValue = false)

    val lazyListState = rememberLazyListState()
    if (folderStack.isEmpty()) {
        val cachedTree = getDirectorytree()
        if (cachedTree == null) {
            viewModel.getLocalSongs(context, viewModel.databseLink)
        }

        folderStack.push(viewModel.localSongDirectoryTree.value)
    }
    var currDir by remember { mutableStateOf(folderStack.peek()) }

    LaunchedEffect(Unit) {
        if (isScannerActive) {
            return@LaunchedEffect
        }
        isScanFinished = false
        isScannerActive = true
        coroutineScope.launch(Dispatchers.IO) {
            val directoryStructure = scanLocal(context, database).value
            syncDB(database, directoryStructure.toList(), scannerSensitivity, strictExtensions)

            isScannerActive = false
            isScanFinished = true
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
        ) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(12.dp)
                ) {
                    val libcarditem = 25.dp
                    Box(
                        modifier = Modifier
                            .size(AlbumThumbnailSize)
                            .clip(RoundedCornerShape(libcarditem))
                            .align(alignment = Alignment.CenterHorizontally)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(ThumbnailCornerRadius)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = LocalContentColor.current.copy(alpha = 0.8f),
                            modifier = Modifier
                                .size(110.dp)
                                .clip(RoundedCornerShape(ThumbnailCornerRadius))
                                .align(Alignment.Center)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AutoResizeText(
                            text = stringResource(R.string.local),
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontSizeRange = FontSizeRange(16.sp, 22.sp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = {
                                    playerConnection.playQueue(ListQueue(title = context.getString(R.string.queue_all_songs),
                                        items = currDir
                                            .toList()
                                            .map { it.toMediaItem() }))
                                },
                                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.play),
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize)
                                )
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text(stringResource(R.string.play))
                            }
                            Button(
                                onClick = {
                                    if (isScannerActive) {
                                        return@Button
                                    }
                                    isScanFinished = false
                                    isScannerActive = true

                                    Toast.makeText(
                                        context,
                                        "Starting full library scan this may take a while...",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val directoryStructure = scanLocal(context, database).value
                                        syncDB(database, directoryStructure.toList(), scannerSensitivity, strictExtensions)

                                        isScannerActive = false
                                        isScanFinished = true
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.sync),
                                    contentDescription = null
                                )
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text(stringResource(R.string.sync))
                            }
                        }
                    }
                }
            }
            itemsIndexed(
                items = currDir.subdirs,
                key = { _, item -> item.uid },
                contentType = { _, _ -> CONTENT_TYPE_SONG }
            ) { _, song ->
                SongFolderItem(
                    folderTitle = song.currentDir,
                    modifier = Modifier
                        .combinedClickable {
                            currDir = folderStack.push(song)
                        }
                        .animateItem()
                )
            }
            if (currDir.subdirs.size > 0 && currDir.files.size > 0) {
                item(
                    key = "folder_songs_divider",
                    ) {
                    HorizontalDivider(
                        thickness = DividerDefaults.Thickness,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
            itemsIndexed(
                items = currDir.files,
                key = { _, item -> item.id },
                contentType = { _, _ -> CONTENT_TYPE_SONG }
            ) { index, song ->
                if (index == 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.n_song,
                                R.plurals.n_song, currDir.toList().size, currDir.toList().size
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
                SongListItem(
                    song = song,
                    isActive = song.id == mediaMetadata?.id,
                    isPlaying = isPlaying,
                    trailingContent = {
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
                                painterResource(R.drawable.more_vert),
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
                                        ListQueue(
                                            title = context.getString(R.string.queue_all_songs),
                                            items = currDir
                                                .toList()
                                                .map { it.toMediaItem() },
                                            startIndex = index
                                        )
                                    )
                                }
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    SongMenu(
                                        originalSong = song,
                                        navController = navController,
                                        onDismiss = menuState::dismiss
                                    )
                                }
                            }
                        )
                        .animateItem()
                )
            }
        }
        TopAppBar(
            title = {
                if (viewModel.folderPositionStack.size > 1) Text(currDir.currentDir)
                else Text("Internal Storage")
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        if (folderStack.size == 0) {
                            navController.navigate(Screens.Library.route)
                            return@IconButton
                        }
                        currDir = folderStack.pop()
                    }

                ) {
                    Icon(
                        painterResource(R.drawable.arrow_back),
                        contentDescription = null
                    )
                }
            },
            scrollBehavior = scrollBehavior
        )
    }
}