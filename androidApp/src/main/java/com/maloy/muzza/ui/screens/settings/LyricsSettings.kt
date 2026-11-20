package com.maloy.muzza.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.toLowerCase
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.maloy.muzza.LocalPlayerAwareWindowInsets
import com.maloy.muzza.R
import com.maloy.muzza.constants.EnableKugouKey
import com.maloy.muzza.constants.EnableLrcLibKey
import com.maloy.muzza.constants.EnableSimpMusicKey
import com.maloy.muzza.constants.ListItemHeight
import com.maloy.muzza.constants.LyricFontSizeKey
import com.maloy.muzza.constants.LyricTrimKey
import com.maloy.muzza.constants.LyricsPosition
import com.maloy.muzza.constants.LyricsTextPositionKey
import com.maloy.muzza.constants.MultilineLrcKey
import com.maloy.muzza.constants.PreferredLyricsProvider
import com.maloy.muzza.constants.PreferredLyricsProviderKey
import com.maloy.muzza.ui.component.CounterDialog
import com.maloy.muzza.ui.component.EnumListPreference
import com.maloy.muzza.ui.component.IconButton
import com.maloy.muzza.ui.component.ListDialog
import com.maloy.muzza.ui.component.ListPreference
import com.maloy.muzza.ui.component.PreferenceEntry
import com.maloy.muzza.ui.component.PreferenceGroupTitle
import com.maloy.muzza.ui.component.SwitchPreference
import com.maloy.muzza.ui.utils.backToMain
import com.maloy.muzza.utils.rememberEnumPreference
import com.maloy.muzza.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (enableKugou, onEnableKugouChange) = rememberPreference(key = EnableKugouKey, defaultValue = true)
    val (lyricsPosition, onLyricsPositionChange) = rememberEnumPreference(LyricsTextPositionKey, defaultValue = LyricsPosition.CENTER)
    val (enableSimpMusic, onEnableSimpMusicChange) = rememberPreference(key = EnableSimpMusicKey, defaultValue = true)
    val (enableLrcLib, onEnableLrcLibChange) = rememberPreference(key = EnableLrcLibKey, defaultValue = true)
    val (preferredProvider, onPreferredProviderChange) = rememberEnumPreference(key = PreferredLyricsProviderKey, defaultValue = PreferredLyricsProvider.LRCLIB)
    val (multilineLrc, onMultilineLrcChange) = rememberPreference(MultilineLrcKey, defaultValue = true)
    val (lyricTrim, onLyricTrimChange) = rememberPreference(LyricTrimKey, defaultValue = false)
    val (lyricFontSize, onLyricFontSizeChange) = rememberPreference(LyricFontSizeKey, defaultValue = 20)

    var showFontSizeDialog by remember {
        mutableStateOf(false)
    }
    if (showFontSizeDialog) {
        CounterDialog(
            title = stringResource(R.string.lyrics_font_size),
            icon = { Icon(Icons.Rounded.FormatSize,null) },
            initialValue = lyricFontSize,
            upperBound = 28,
            lowerBound = 10,
            resetValue = 20,
            unitDisplay = " sp",
            onDismiss = { showFontSizeDialog = false },
            onConfirm = {
                onLyricFontSizeChange(it)
                showFontSizeDialog = false
            },
            onCancel = { showFontSizeDialog = false },
            onReset = { onLyricFontSizeChange(20) },
        )
    }

    var lyricsEnablerPreferenceDialog by remember {
        mutableStateOf(false)
    }

    if (lyricsEnablerPreferenceDialog) {
        ListDialog(
            onDismiss = { lyricsEnablerPreferenceDialog = false }
        ) {
            item {
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ListItemHeight)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SwitchPreference(
                            title = { Text("SimpMusic")},
                            icon = { Icon(painterResource(R.drawable.lyrics),null) },
                            checked = enableSimpMusic,
                            onCheckedChange = onEnableSimpMusicChange
                        )
                    }
                }
            }
            item {
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ListItemHeight)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SwitchPreference(
                            title = { Text("KuGou") },
                            icon = { Icon(painterResource(R.drawable.lyrics), null) },
                            checked = enableKugou,
                            onCheckedChange = onEnableKugouChange
                        )
                    }
                }
            }
            item {
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ListItemHeight)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SwitchPreference(
                            title = { Text("LrcLib") },
                            icon = { Icon(painterResource(R.drawable.lyrics), null) },
                            checked = enableLrcLib,
                            onCheckedChange = onEnableLrcLibChange
                        )
                    }
                }
            }
        }
    }
    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState())
    ) {
        PreferenceGroupTitle(
            title = stringResource(R.string.main)
        )

        PreferenceEntry(
            title = { Text(stringResource(R.string.lyrics_providers)) },
            icon = { Icon(painterResource(R.drawable.lyrics), null) },
            onClick = { lyricsEnablerPreferenceDialog = true }
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.lyrics_multiline_title)) },
            description = stringResource(R.string.lyrics_multiline_description),
            icon = { Icon(Icons.AutoMirrored.Rounded.Sort, null) },
            checked = multilineLrc,
            onCheckedChange = onMultilineLrcChange
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.lyrics_trim_title)) },
            icon = { Icon(Icons.Rounded.ContentCut, null) },
            checked = lyricTrim,
            onCheckedChange = onLyricTrimChange
        )

        EnumListPreference(
            title = { Text(stringResource(R.string.lyrics_text_position)) },
            icon = { Icon(painterResource(R.drawable.lyrics), null) },
            selectedValue = lyricsPosition,
            onValueSelected = onLyricsPositionChange,
            valueText = {
                when (it) {
                    LyricsPosition.LEFT -> stringResource(R.string.left)
                    LyricsPosition.CENTER -> stringResource(R.string.center)
                    LyricsPosition.RIGHT -> stringResource(R.string.right)
                }
            }
        )
        PreferenceEntry(
            title = { Text( stringResource(R.string.lyrics_font_size)) },
            description = "$lyricFontSize sp",
            icon = { Icon(Icons.Rounded.FormatSize, null) },
            onClick = { showFontSizeDialog = true }
        )

        PreferenceGroupTitle(
            title = stringResource(R.string.misc)
        )

        ListPreference(
            title = { Text(stringResource(R.string.default_lyrics_provider)) },
            icon = { Icon(painterResource(R.drawable.lyrics), null) },
            selectedValue = preferredProvider,
            values = listOf(PreferredLyricsProvider.KUGOU, PreferredLyricsProvider.LRCLIB, PreferredLyricsProvider.SIMPMUSIC),
            valueText = {
                it.name.toLowerCase(androidx.compose.ui.text.intl.Locale.current)
                    .capitalize(androidx.compose.ui.text.intl.Locale.current)
            },
            onValueSelected = onPreferredProviderChange
        )
    }
    CenterAlignedTopAppBar(
        title = { Text(stringResource(R.string.lyrics_settings_title)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}