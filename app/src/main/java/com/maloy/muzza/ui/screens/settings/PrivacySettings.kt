package com.maloy.muzza.ui.screens.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.maloy.innertube.utils.parseCookieString
import com.maloy.muzza.LocalDatabase
import com.maloy.muzza.LocalPlayerAwareWindowInsets
import com.maloy.muzza.R
import com.maloy.muzza.constants.AddingPlayedSongsToYTMHistoryKey
import com.maloy.muzza.constants.DisableScreenshotKey
import com.maloy.muzza.constants.InnerTubeCookieKey
import com.maloy.muzza.constants.PauseListenHistoryKey
import com.maloy.muzza.constants.PauseSearchHistoryKey
import com.maloy.muzza.ui.component.DefaultDialog
import com.maloy.muzza.ui.component.IconButton
import com.maloy.muzza.ui.component.PreferenceEntry
import com.maloy.muzza.ui.component.PreferenceGroupTitle
import com.maloy.muzza.ui.component.SwitchPreference
import com.maloy.muzza.ui.utils.backToMain
import com.maloy.muzza.utils.rememberPreference

@SuppressLint("BatteryLife")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val (pauseListenHistory, onPauseListenHistoryChange) = rememberPreference(key = PauseListenHistoryKey, defaultValue = false)
    val (pauseSearchHistory, onPauseSearchHistoryChange) = rememberPreference(key = PauseSearchHistoryKey, defaultValue = false)
    val (disableScreenshot, onDisableScreenshotChange) = rememberPreference(key = DisableScreenshotKey, defaultValue = false)

    var showClearListenHistoryDialog by remember { mutableStateOf(false) }

    val (addingPlayedSongsToYtmHistory, onAddingPlayedSongsToYtmHistoryChange) = rememberPreference(
        AddingPlayedSongsToYTMHistoryKey, defaultValue = true)
    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn =
        remember(innerTubeCookie) {
            "SAPISID" in parseCookieString(innerTubeCookie)
        }

    val lifecycleOwner = LocalLifecycleOwner.current
    val isIgnoringOptimizations by produceState(initialValue = false) {
        fun checkOptimizationStatus(): Boolean {
            val powerManager = context.getSystemService<PowerManager>()
            return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        }
        value = checkOptimizationStatus()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                value = checkOptimizationStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        awaitDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showClearListenHistoryDialog) {
        DefaultDialog(
            onDismiss = { showClearListenHistoryDialog = false },
            icon = { Icon(painterResource(R.drawable.delete_history), null)},
            content = {
                Text(
                    text = stringResource(R.string.clear_listen_history_confirm),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            },
            buttons = {
                TextButton(
                    onClick = { showClearListenHistoryDialog = false }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showClearListenHistoryDialog = false
                        database.query {
                            clearListenHistory()
                        }
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        )
    }

    var showClearSearchHistoryDialog by remember { mutableStateOf(false) }

    if (showClearSearchHistoryDialog) {
        DefaultDialog(
            onDismiss = { showClearSearchHistoryDialog = false },
            icon = { Icon(painterResource(R.drawable.delete_history), null)},
            content = {
                Text(
                    text = stringResource(R.string.clear_search_history_confirm),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            },
            buttons = {
                TextButton(
                    onClick = { showClearSearchHistoryDialog = false }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showClearSearchHistoryDialog = false
                        database.query {
                            clearSearchHistory()
                        }
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        )
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))

        PreferenceGroupTitle(
            title = stringResource(R.string.listen_history)
        )

        if (isLoggedIn) {
            SwitchPreference(
                title = { Text(stringResource(R.string.adding_played_songs_to_ytm_history)) },
                icon = { Icon(painterResource(R.drawable.history), null) },
                checked = addingPlayedSongsToYtmHistory,
                onCheckedChange = onAddingPlayedSongsToYtmHistoryChange
            )
        }

        SwitchPreference(
            title = { Text(stringResource(R.string.pause_listen_history)) },
            icon = { Icon(painterResource(R.drawable.history), null) },
            checked = pauseListenHistory,
            onCheckedChange = onPauseListenHistoryChange
        )

        PreferenceEntry(
            title = { Text(stringResource(R.string.clear_listen_history)) },
            icon = { Icon(painterResource(R.drawable.delete_history), null) },
            onClick = { showClearListenHistoryDialog = true }
        )

        PreferenceGroupTitle(
            title = stringResource(R.string.search_history)
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.pause_search_history)) },
            icon = { Icon(painterResource(R.drawable.search_off), null) },
            checked = pauseSearchHistory,
            onCheckedChange = onPauseSearchHistoryChange
        )

        PreferenceEntry(
            title = { Text(stringResource(R.string.clear_search_history)) },
            icon = { Icon(painterResource(R.drawable.clear_all), null) },
            onClick = { showClearSearchHistoryDialog = true }
        )

        PreferenceGroupTitle(
            title = stringResource(R.string.misc)
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.disable_screenshot)) },
            description = stringResource(R.string.disable_screenshot_desc),
            icon = { Icon(painterResource(R.drawable.screenshot), null) },
            checked = disableScreenshot,
            onCheckedChange = onDisableScreenshotChange
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.ignore_battery_optimizations)) },
            description = stringResource(R.string.ignore_battery_optimizations_description),
            icon = { Icon(Icons.Rounded.BatteryChargingFull, null) },
            checked = isIgnoringOptimizations,
            onCheckedChange = { _ ->
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = "package:${context.packageName}".toUri()
                }

                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                } else {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
        )
    }

    CenterAlignedTopAppBar(
        title = { Text(stringResource(R.string.privacy)) },
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
