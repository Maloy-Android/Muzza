package com.maloy.muzza.ui.screens.settings

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.maloy.muzza.LocalPlayerAwareWindowInsets
import com.maloy.muzza.R
import com.maloy.muzza.constants.ChipSortTypeKey
import com.maloy.muzza.constants.DarkModeKey
import com.maloy.muzza.constants.DefaultOpenTabKey
import com.maloy.muzza.constants.DynamicThemeKey
import com.maloy.muzza.constants.GridCellSize
import com.maloy.muzza.constants.GridCellSizeKey
import com.maloy.muzza.constants.LibraryFilter
import com.maloy.muzza.constants.PlayerBackgroundStyle
import com.maloy.muzza.constants.PlayerBackgroundStyleKey
import com.maloy.muzza.constants.PureBlackKey
import com.maloy.muzza.constants.SliderStyle
import com.maloy.muzza.constants.SliderStyleKey
import com.maloy.muzza.constants.SwipeThumbnailKey
import com.maloy.muzza.constants.ThumbnailCornerRadiusV2Key
import com.maloy.muzza.ui.component.ActionPromptDialog
import com.maloy.muzza.ui.component.DefaultDialog
import com.maloy.muzza.ui.component.EnumListPreference
import com.maloy.muzza.ui.component.IconButton
import com.maloy.muzza.ui.component.ListPreference
import com.maloy.muzza.ui.component.PlayerSliderTrack
import com.maloy.muzza.ui.component.PreferenceEntry
import com.maloy.muzza.ui.component.PreferenceGroupTitle
import com.maloy.muzza.ui.component.SwitchPreference
import com.maloy.muzza.ui.utils.backToMain
import com.maloy.muzza.utils.rememberEnumPreference
import com.maloy.muzza.utils.rememberPreference
import me.saket.squiggles.SquigglySlider

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (dynamicTheme, onDynamicThemeChange) = rememberPreference(DynamicThemeKey, defaultValue = true)
    val (darkMode, onDarkModeChange) = rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val (pureBlack, onPureBlackChange) = rememberPreference(PureBlackKey, defaultValue = false)
    val (sliderStyle, onSliderStyleChange) = rememberEnumPreference(SliderStyleKey, defaultValue = SliderStyle.DEFAULT)
    val (defaultOpenTab, onDefaultOpenTabChange) = rememberEnumPreference(DefaultOpenTabKey, defaultValue = NavigationTab.HOME)
    val (gridCellSize, onGridCellSizeChange) = rememberEnumPreference(GridCellSizeKey, defaultValue = GridCellSize.SMALL)
    val (defaultChip, onDefaultChipChange) = rememberEnumPreference(key = ChipSortTypeKey, defaultValue = LibraryFilter.LIBRARY)
    val (swipeThumbnail, onSwipeThumbnailChange) = rememberPreference(SwipeThumbnailKey, defaultValue = true)
    val (cornerRadius, onCornerRadius) = rememberPreference(ThumbnailCornerRadiusV2Key, defaultValue = 6)

    val isSystemInDarkTheme = isSystemInDarkTheme()
    val useDarkTheme = remember(darkMode, isSystemInDarkTheme) {
        if (darkMode == DarkMode.AUTO) isSystemInDarkTheme else darkMode == DarkMode.ON
    }

    val (playerBackground, onPlayerBackgroundChange) =
        rememberEnumPreference(
            PlayerBackgroundStyleKey,
            defaultValue = PlayerBackgroundStyle.DEFAULT,
        )


    var showCornerRadiusDialog by remember { mutableStateOf(false) }
    var thumbnailCornerRadius by remember { mutableStateOf(AppConfig.ThumbnailCornerRadiusV2) }

    if (showCornerRadiusDialog) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ActionPromptDialog(
                title = stringResource(R.string.thumbnail_corner_radius),
                onDismiss = { showCornerRadiusDialog = false },
                onConfirm = {
                    showCornerRadiusDialog = false
                    onCornerRadius(cornerRadius)
                },
                onCancel = {
                    showCornerRadiusDialog = false
                    onCornerRadius(cornerRadius)
                }
            ) {
                if (showCornerRadiusDialog) {
                    ActionPromptDialog(
                        title = stringResource(R.string.thumbnail_corner_radius),
                        onDismiss = { showCornerRadiusDialog = false },
                        onConfirm = {
                            showCornerRadiusDialog = false
                            AppConfig.ThumbnailCornerRadiusV2 = thumbnailCornerRadius
                        },
                        onCancel = {
                            showCornerRadiusDialog = false
                            thumbnailCornerRadius =
                                AppConfig.ThumbnailCornerRadiusV2
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${String.format("%.1f", thumbnailCornerRadius)}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            Slider(
                                value = thumbnailCornerRadius,
                                onValueChange = {
                                    thumbnailCornerRadius = it.coerceIn(0f, 50f)
                                },
                                valueRange = 0f..50f,
                                thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                                track = { sliderState ->
                                    PlayerSliderTrack(
                                        sliderState = sliderState,
                                        colors = SliderDefaults.colors()
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    var showSliderOptionDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showSliderOptionDialog) {
        DefaultDialog(
            buttons = {
                TextButton(
                    onClick = { showSliderOptionDialog = false }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
            onDismiss = {
                showSliderOptionDialog = false
            }
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .aspectRatio(1f)
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            1.dp,
                            if (sliderStyle == SliderStyle.DEFAULT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            onSliderStyleChange(SliderStyle.DEFAULT)
                            showSliderOptionDialog = false
                        }
                        .padding(16.dp)
                ) {
                    var sliderValue by remember {
                        mutableFloatStateOf(0.5f)
                    }
                    Slider(
                        value = sliderValue,
                        valueRange = 0f..1f,
                        onValueChange = {
                            sliderValue = it
                        },
                        thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                        track = { sliderState ->
                            PlayerSliderTrack(
                                sliderState = sliderState,
                                colors = SliderDefaults.colors()
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {}
                                )
                            }
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .aspectRatio(1f)
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            1.dp,
                            if (sliderStyle == SliderStyle.SQUIGGLY) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            onSliderStyleChange(SliderStyle.SQUIGGLY)
                            showSliderOptionDialog = false
                        }
                        .padding(16.dp)
                ) {
                    var sliderValue by remember {
                        mutableFloatStateOf(0.5f)
                    }
                    SquigglySlider(
                        value = sliderValue,
                        valueRange = 0f..1f,
                        onValueChange = {
                            sliderValue = it
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .aspectRatio(1f)
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            1.dp,
                            if (sliderStyle == SliderStyle.COMPOSE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            onSliderStyleChange(SliderStyle.COMPOSE)
                            showSliderOptionDialog = false
                        }
                        .padding(16.dp)
                ) {
                    var sliderValue by remember {
                        mutableFloatStateOf(0.5f)
                    }
                    Slider(
                        value = sliderValue,
                        valueRange = 0f..1f,
                        onValueChange = {
                            sliderValue = it
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))

        PreferenceGroupTitle(
            title = stringResource(R.string.theme)
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.enable_dynamic_theme)) },
            icon = { Icon(painterResource(R.drawable.palette), null) },
            checked = dynamicTheme,
            onCheckedChange = onDynamicThemeChange
        )

        EnumListPreference(
            title = { Text(stringResource(R.string.dark_theme)) },
            icon = { Icon(painterResource(R.drawable.dark_mode), null) },
            selectedValue = darkMode,
            onValueSelected = onDarkModeChange,
            valueText = {
                when (it) {
                    DarkMode.ON -> stringResource(R.string.dark_theme_on)
                    DarkMode.OFF -> stringResource(R.string.dark_theme_off)
                    DarkMode.AUTO -> stringResource(R.string.dark_theme_follow_system)
                }
            }
        )

        EnumListPreference(
            title = { Text(stringResource(R.string.player_background_style)) },
            icon = { Icon(painterResource(R.drawable.gradient), null) },
            selectedValue = playerBackground,
            onValueSelected = onPlayerBackgroundChange,
            valueText = {
                when (it) {
                    PlayerBackgroundStyle.DEFAULT -> stringResource(R.string.follow_theme)
                    PlayerBackgroundStyle.GRADIENT -> stringResource(R.string.gradient)
                    PlayerBackgroundStyle.BLURMOV ->  stringResource(R.string.blurmv)
                    PlayerBackgroundStyle.BLUR -> stringResource(R.string.blur)
                }
            }
        )

        AnimatedVisibility(useDarkTheme) {
            SwitchPreference(
                title = { Text(stringResource(R.string.pure_black)) },
                icon = { Icon(painterResource(R.drawable.contrast), null) },
                checked = pureBlack,
                onCheckedChange = onPureBlackChange
            )
        }

        PreferenceGroupTitle(
            title = stringResource(R.string.player)
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.enable_swipe_thumbnail)) },
            icon = { Icon(painterResource(R.drawable.swipe), null) },
            checked = swipeThumbnail,
            onCheckedChange = onSwipeThumbnailChange,
        )

        PreferenceEntry(
            title = { Text(stringResource(R.string.thumbnail_corner_radius)) },
            icon = { Icon(Icons.Rounded.Image, null) },
            onClick = { showCornerRadiusDialog = true }
        )

        PreferenceEntry(
            title = { Text(stringResource(R.string.player_slider_style)) },
            description = when (sliderStyle) {
                SliderStyle.DEFAULT -> stringResource(R.string.default_)
                SliderStyle.SQUIGGLY -> stringResource(R.string.squiggly)
                SliderStyle.COMPOSE -> stringResource(R.string.compose)
            },
            icon = { Icon(painterResource(R.drawable.sliders), null) },
            onClick = {
                showSliderOptionDialog = true
            }
        )

        PreferenceGroupTitle(
            title = stringResource(R.string.misc)
        )

        EnumListPreference(
            title = { Text(stringResource(R.string.default_open_tab)) },
            icon = { Icon(painterResource(R.drawable.tab), null) },
            selectedValue = defaultOpenTab,
            onValueSelected = onDefaultOpenTabChange,
            valueText = {
                when (it) {
                    NavigationTab.HOME -> stringResource(R.string.home)
                    NavigationTab.LIBRARY -> stringResource(R.string.filter_library)
                }
            }
        )

        EnumListPreference(
            title = { Text(stringResource(R.string.grid_cell_size)) },
            icon = { Icon(painterResource(R.drawable.grid_view), null) },
            selectedValue = gridCellSize,
            onValueSelected = onGridCellSizeChange,
            valueText = {
                when (it) {
                    GridCellSize.SMALL -> stringResource(R.string.small)
                    GridCellSize.BIG -> stringResource(R.string.big)
                }
            },
        )
        ListPreference(
            title = { Text(stringResource(R.string.default_lib_chips)) },
            icon = { Icon(painterResource(R.drawable.list), null) },
            selectedValue = defaultChip,
            values =
            listOf(
                LibraryFilter.LIBRARY,
                LibraryFilter.PLAYLISTS,
                LibraryFilter.SONGS,
                LibraryFilter.ALBUMS,
                LibraryFilter.ARTISTS,
            ),
            valueText = {
                when (it) {
                    LibraryFilter.SONGS -> stringResource(R.string.songs)
                    LibraryFilter.ARTISTS -> stringResource(R.string.artists)
                    LibraryFilter.ALBUMS -> stringResource(R.string.albums)
                    LibraryFilter.PLAYLISTS -> stringResource(R.string.playlists)
                    LibraryFilter.LIBRARY -> stringResource(R.string.filter_library)
                }
            },
            onValueSelected = onDefaultChipChange,
        )
    }

    TopAppBar(
        title = { Text(stringResource(R.string.appearance)) },
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

enum class DarkMode {
    ON, OFF, AUTO
}

enum class NavigationTab {
    HOME, LIBRARY
}

enum class LyricsPosition {
    LEFT, CENTER, RIGHT
}

object AppConfig {
    var ThumbnailCornerRadiusV2: Float = 16f
}