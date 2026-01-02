package com.maloy.muzza.ui.screens.settings


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.maloy.muzza.LocalPlayerAwareWindowInsets
import com.maloy.muzza.R
import com.maloy.muzza.constants.AutoSyncLocalSongsKey
import com.maloy.muzza.constants.ScannerSensitivity
import com.maloy.muzza.constants.ScannerSensitivityKey
import com.maloy.muzza.constants.ScannerStrictExtKey
import com.maloy.muzza.ui.component.EnumListPreference
import com.maloy.muzza.ui.component.IconButton
import com.maloy.muzza.ui.component.PreferenceGroupTitle
import com.maloy.muzza.ui.component.SwitchPreference
import com.maloy.muzza.ui.utils.backToMain
import com.maloy.muzza.utils.rememberEnumPreference
import com.maloy.muzza.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalPlayerSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (autoSyncLocalSongs, onAutoSyncLocalSongs) = rememberPreference(
        key = AutoSyncLocalSongsKey,
        defaultValue = true
    )
    val (scannerSensitivity, onScannerSensitivityChange) = rememberEnumPreference(
        key = ScannerSensitivityKey,
        defaultValue = ScannerSensitivity.LEVEL_2
    )
    val (strictExtensions, onStrictExtensionsChange) = rememberPreference(
        ScannerStrictExtKey,
        defaultValue = false
    )

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState())
    ) {
        PreferenceGroupTitle(
            title = stringResource(R.string.scanner_settings_title)
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.auto_scanner_title)) },
            description = stringResource(R.string.auto_scanner_description),
            icon = { Icon(painterResource(R.drawable.sync), null) },
            checked = autoSyncLocalSongs,
            onCheckedChange = onAutoSyncLocalSongs
        )

        // scanner sensitivity
        EnumListPreference(
            title = { Text(stringResource(R.string.scanner_sensitivity_title)) },
            icon = { Icon(Icons.Rounded.GraphicEq, null) },
            selectedValue = scannerSensitivity,
            onValueSelected = onScannerSensitivityChange,
            valueText = {
                when (it) {
                    ScannerSensitivity.LEVEL_1 -> stringResource(R.string.scanner_sensitivity_L1)
                    ScannerSensitivity.LEVEL_2 -> stringResource(R.string.scanner_sensitivity_L2)
                    ScannerSensitivity.LEVEL_3 -> stringResource(R.string.scanner_sensitivity_L3)
                }
            }
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.scanner_strict_file_name_title)) },
            description = stringResource(R.string.scanner_strict_file_name_description),
            icon = { Icon(Icons.Rounded.TextFields, null) },
            checked = strictExtensions,
            onCheckedChange = onStrictExtensionsChange
        )
        VerticalDivider(
            thickness = DividerDefaults.Thickness,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 10.dp)
        )
    }
    CenterAlignedTopAppBar(
        title = { Text(stringResource(R.string.local_player_settings_title)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}