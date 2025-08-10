package com.maloy.muzza.ui.screens.settings

import android.annotation.SuppressLint
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.maloy.muzza.BuildConfig
import com.maloy.muzza.LocalPlayerConnection
import com.maloy.muzza.R
import com.maloy.muzza.constants.MaxImageCacheSizeKey
import com.maloy.muzza.constants.MaxSongCacheSizeKey
import com.maloy.muzza.extensions.tryOrNull
import com.maloy.muzza.playback.ExoDownloadService
import com.maloy.muzza.ui.component.IconButton
import com.maloy.muzza.ui.utils.backToMain
import com.maloy.muzza.ui.utils.formatFileSize
import com.maloy.muzza.utils.TranslationHelper
import com.maloy.muzza.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("PrivateResource")
@OptIn(ExperimentalCoilApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StorageSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val imageDiskCache = context.imageLoader.diskCache ?: return
    val playerCache = LocalPlayerConnection.current?.service?.playerCache ?: return
    val downloadCache = LocalPlayerConnection.current?.service?.downloadCache ?: return

    val coroutineScope = rememberCoroutineScope()
    val (maxImageCacheSize, onMaxImageCacheSizeChange) = rememberPreference(
        key = MaxImageCacheSizeKey,
        defaultValue = 512
    )
    val (maxSongCacheSize, onMaxSongCacheSizeChange) = rememberPreference(
        key = MaxSongCacheSizeKey,
        defaultValue = 1024
    )

    var imageCacheSize by remember { mutableLongStateOf(imageDiskCache.size) }
    var playerCacheSize by remember { mutableLongStateOf(tryOrNull { playerCache.cacheSpace } ?: 0) }
    var downloadCacheSize by remember { mutableLongStateOf(tryOrNull { downloadCache.cacheSpace } ?: 0) }

    LaunchedEffect(maxImageCacheSize) {
        if (maxImageCacheSize == 0) {
            coroutineScope.launch(Dispatchers.IO) {
                imageDiskCache.clear()
            }
        }
    }
    LaunchedEffect(maxSongCacheSize) {
        if (maxSongCacheSize == 0) {
            coroutineScope.launch(Dispatchers.IO) {
                playerCache.keys.forEach { key ->
                    playerCache.removeResource(key)
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        imageCacheSize = imageDiskCache.size
        playerCacheSize = tryOrNull { playerCache.cacheSpace } ?: 0
        downloadCacheSize = tryOrNull { downloadCache.cacheSpace } ?: 0
        delay(500)
    }

    var showClearAllDownloadsDialog by remember {
        mutableStateOf(false)
    }

    var showClearSongCacheDialog by remember {
        mutableStateOf(false)
    }

    var showClearImagesCacheDialog by remember {
        mutableStateOf(false)
    }

    var showClearTranslationModels by remember {
        mutableStateOf(false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.storage)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            item {
                StorageCategoryHeader(
                    title = stringResource(R.string.downloaded_songs),
                    icon = Icons.Outlined.CloudDownload,
                    color = MaterialTheme.colorScheme.surfaceContainer
                )
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = formatFileSize(downloadCacheSize),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = { showClearAllDownloadsDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(text = stringResource(R.string.clear_all_downloads))
                        }
                    }
                }
            }

            item {
                StorageCategoryHeader(
                    title = stringResource(R.string.song_cache),
                    icon = Icons.Rounded.MusicNote,
                    color = MaterialTheme.colorScheme.surfaceContainer
                )
                if (maxSongCacheSize == -1) {
                    Card(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = formatFileSize(playerCacheSize),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FilledTonalButton(
                                onClick = { showClearSongCacheDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text(text = stringResource(R.string.clear_song_cache))
                            }
                        }
                    }
                } else {
                    AnimatedVisibility(visible = maxSongCacheSize != 0) {
                        StorageProgressCard(
                            usedSpace = playerCacheSize,
                            totalSpace = maxSongCacheSize * 1024 * 1024L,
                            onClear = { showClearSongCacheDialog = true }
                        )
                    }
                }
                CacheSizeSelector(
                    selectedValue = maxSongCacheSize,
                    onValueChange = onMaxSongCacheSizeChange
                )
            }

            item {
                StorageCategoryHeader(
                    title = stringResource(R.string.image_cache),
                    icon = Icons.Rounded.Image,
                    color = MaterialTheme.colorScheme.surfaceContainer
                )
                if (maxImageCacheSize == -1) {
                    Card(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = formatFileSize(imageCacheSize),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FilledTonalButton(
                                onClick = { showClearImagesCacheDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text(text = stringResource(R.string.clear_image_cache))
                            }
                        }
                    }
                } else {
                    AnimatedVisibility(visible = maxImageCacheSize != 0) {
                        StorageProgressCard(
                            usedSpace = imageCacheSize,
                            totalSpace = maxImageCacheSize * 1024 * 1024L,
                            onClear = { showClearImagesCacheDialog = true }
                        )
                    }
                }
                CacheSizeSelector(
                    selectedValue = maxImageCacheSize,
                    onValueChange = onMaxImageCacheSizeChange
                )
            }

            if (BuildConfig.FLAVOR != "foss") {
                item {
                    StorageCategoryHeader(
                        title = stringResource(R.string.translation_models),
                        icon = Icons.Rounded.Translate,
                        color = MaterialTheme.colorScheme.surfaceContainer
                    )
                    ModelManagementCard(
                        onClear = { showClearTranslationModels = true }
                    )
                }
            }
        }
    }
    if (showClearAllDownloadsDialog) {
        ConfirmationDialog(
            title = R.string.clear_all_downloads,
            icon = Icons.Outlined.CloudOff,
            onDismiss = { showClearAllDownloadsDialog = false },
            onConfirm = {
                showClearAllDownloadsDialog = false
                coroutineScope.launch(Dispatchers.IO) {
                    downloadCache.keys.forEach { key ->
                        DownloadService.sendRemoveDownload(
                            context,
                            ExoDownloadService::class.java,
                            key,
                            false
                        )
                    }
                    downloadCache.keys.forEach { key ->
                        downloadCache.removeResource(key)
                    }
                }
            }
        )
    }
    if (showClearImagesCacheDialog) {
        ConfirmationDialog(
            title = R.string.clear_image_cache,
            icon = Icons.Rounded.Image,
            onDismiss = { showClearImagesCacheDialog = false },
            onConfirm = {
                showClearImagesCacheDialog = false
                coroutineScope.launch(Dispatchers.IO) {
                    imageDiskCache.clear()
                }
            }
        )
    }
    if (showClearSongCacheDialog) {
        ConfirmationDialog(
            title = R.string.clear_song_cache,
            icon = Icons.Rounded.MusicNote,
            onDismiss = { showClearSongCacheDialog = false },
            onConfirm = {
                showClearSongCacheDialog = false
                coroutineScope.launch(Dispatchers.IO) {
                    playerCache.keys.forEach { key ->
                        playerCache.removeResource(key)
                    }
                }
            }
        )
    }
    if (showClearTranslationModels) {
        ConfirmationDialog(
            title = R.string.clear_translation_models,
            icon = Icons.Rounded.Translate,
            onDismiss = { showClearTranslationModels = false },
            onConfirm = {
                showClearTranslationModels = false
                coroutineScope.launch(Dispatchers.IO) {
                    TranslationHelper.clearModels()
                }
            }
        )
    }
}

@Composable
private fun StorageProgressCard(
    usedSpace: Long,
    totalSpace: Long,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = CenterVertically) {
                CircularProgressIndicator(
                    progress = { calculateProgress(usedSpace, totalSpace) },
                    modifier = Modifier.size(40.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "${(usedSpace.toFloat() / totalSpace * 100).toInt()}%" + stringResource(R.string.size_used),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            FilledTonalButton(
                onClick = onClear,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(text = stringResource(R.string.clear_all_downloads))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CacheSizeSelector(
    selectedValue: Int,
    onValueChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
                .padding(horizontal = 16.dp),
            readOnly = true,
            value = when (selectedValue) {
                0 -> stringResource(R.string.off)
                -1 -> stringResource(R.string.unlimited)
                else -> formatFileSize(selectedValue * 1024 * 1024L)
            },
            onValueChange = {},
            label = { Text(stringResource(R.string.max_cache_size)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            listOf(0, 128, 256, 512, 1024, 2048, 4096, 8192, -1).forEach { size ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (size) {
                                0 -> stringResource(R.string.off)
                                -1 -> stringResource(R.string.unlimited)
                                else -> formatFileSize(size * 1024 * 1024L)
                            }
                        )
                    },
                    onClick = {
                        onValueChange(size)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun StorageCategoryHeader(
    title: String,
    icon: ImageVector,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
        verticalAlignment = CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ModelManagementCard(onClear: () -> Unit) {
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.translation_models),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(
                onClick = onClear,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.clear_translation_models))
            }
        }
    }
}

@Composable
fun ConfirmationDialog(
    @StringRes title: Int,
    icon: ImageVector,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, null) },
        title = { Text(stringResource(title)) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

private fun calculateProgress(used: Long, total: Long): Float {
    return if (total <= 0) 0f else (used.toFloat() / total).coerceIn(0f, 1f)
}