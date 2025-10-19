package com.maloy.muzza.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.maloy.muzza.LocalPlayerAwareWindowInsets
import com.maloy.muzza.R
import com.maloy.muzza.ui.component.IconButton
import com.maloy.muzza.ui.component.ImportM3uDialog
import com.maloy.muzza.ui.component.PreferenceEntry
import com.maloy.muzza.ui.component.PreferenceGroupTitle
import com.maloy.muzza.ui.utils.backToMain
import com.maloy.muzza.viewmodels.BackupRestoreViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupAndRestore(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: BackupRestoreViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            viewModel.backup(context, uri)
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.restore(context, uri)
        }
    }
    var showImportM3uDialog by rememberSaveable { mutableStateOf(false) }
    if (showImportM3uDialog) {
        ImportM3uDialog(
            navController = navController,
            onDismiss = { showImportM3uDialog = false }
        )
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top
                )
            )
        )
        PreferenceGroupTitle(
            title = stringResource(R.string.backup_restore)
        )
        PreferenceEntry(
            icon = { Icon(painterResource(R.drawable.backup), null) },
            description = stringResource(R.string.backup_description),
            title = { Text(stringResource(R.string.backup)) },
            onClick = {
                val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                backupLauncher.launch(
                    "${context.getString(R.string.app_name)}_${
                        LocalDateTime.now().format(formatter)
                    }.backup"
                )
            }
        )

        Spacer(Modifier.height(20.dp))

        PreferenceEntry(
            icon = { Icon(painterResource(R.drawable.restore),null) },
            description = stringResource(R.string.restore_description),
            title = { Text(stringResource(R.string.restore)) },
            onClick = { restoreLauncher.launch(arrayOf("application/octet-stream")) }
        )

        Spacer(Modifier.height(20.dp))

        PreferenceGroupTitle(
            title = stringResource(R.string.misc)
        )
        PreferenceEntry(
            icon = { Icon(painterResource(R.drawable.playlist_add),null) },
            description = stringResource(R.string.m3u_import_playlist_description),
            title = { Text(stringResource(R.string.m3u_import_playlist)) },
            onClick = {
                showImportM3uDialog = true
            }
        )
    }

    TopAppBar(
        title = { Text(stringResource(R.string.backup_restore)) },
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
        scrollBehavior = scrollBehavior
    )
}
