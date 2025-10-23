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
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Padding
import androidx.compose.material.icons.rounded.QueryStats
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
import com.maloy.innertube.utils.parseCookieString
import com.maloy.muzza.LocalPlayerAwareWindowInsets
import com.maloy.muzza.R
import com.maloy.muzza.constants.DarkMode
import com.maloy.muzza.constants.DarkModeKey
import com.maloy.muzza.constants.DefaultOpenTabKey
import com.maloy.muzza.constants.DynamicThemeKey
import com.maloy.muzza.constants.GridCellSize
import com.maloy.muzza.constants.GridCellSizeKey
import com.maloy.muzza.constants.InnerTubeCookieKey
import com.maloy.muzza.constants.MiniPlayerStyle
import com.maloy.muzza.constants.MiniPlayerStyleKey
import com.maloy.muzza.constants.NavigationTab
import com.maloy.muzza.constants.NowPlayingEnableKey
import com.maloy.muzza.constants.NowPlayingPaddingKey
import com.maloy.muzza.constants.PlayerBackgroundStyle
import com.maloy.muzza.constants.PlayerBackgroundStyleKey
import com.maloy.muzza.constants.PlayerStyle
import com.maloy.muzza.constants.PlayerStyleKey
import com.maloy.muzza.constants.PureBlackKey
import com.maloy.muzza.constants.ShowContentFilterKey
import com.maloy.muzza.constants.ShowRecentActivityKey
import com.maloy.muzza.constants.SliderStyle
import com.maloy.muzza.constants.SliderStyleKey
import com.maloy.muzza.constants.SlimNavBarKey
import com.maloy.muzza.constants.SwipeSongToDismissKey
import com.maloy.muzza.constants.SwipeThumbnailKey
import com.maloy.muzza.constants.ThumbnailCornerRadiusV2Key
import com.maloy.muzza.constants.TwoLineSongItemLabelKey
import com.maloy.muzza.ui.component.CounterDialog
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
    val (swipeSongToDismiss, onSwipeSongToDismissChange) = rememberPreference(SwipeSongToDismissKey, defaultValue = true)
    val (sliderStyle, onSliderStyleChange) = rememberEnumPreference(SliderStyleKey, defaultValue = SliderStyle.DEFAULT)
    val (defaultOpenTab, onDefaultOpenTabChange) = rememberEnumPreference(DefaultOpenTabKey, defaultValue = NavigationTab.HOME)
    val (gridCellSize, onGridCellSizeChange) = rememberEnumPreference(GridCellSizeKey, defaultValue = GridCellSize.SMALL)
    val (swipeThumbnail, onSwipeThumbnailChange) = rememberPreference(SwipeThumbnailKey, defaultValue = true)
    val (slimNav, onSlimNavChange) = rememberPreference(SlimNavBarKey, defaultValue = true)
    val (twoLineSongItemLabel, onTwoLineSongItemLabelChange) = rememberPreference(TwoLineSongItemLabelKey, defaultValue = false)
    val (thumbnailCornerRadius, onThumbnailCornerRadius) = rememberPreference (ThumbnailCornerRadiusV2Key , defaultValue = 3)
    val (nowPlayingEnable,onNowPlayingEnableChange) = rememberPreference(NowPlayingEnableKey, defaultValue = true)
    val (nowPlayingPadding,onNowPlayingPadding) = rememberPreference(NowPlayingPaddingKey, defaultValue = 35)
    val (showContentFilter, onShowContentFilterChange) = rememberPreference(ShowContentFilterKey, defaultValue = true)
    val (showRecentActivity,onShowRecentActivityChange) = rememberPreference(ShowRecentActivityKey, defaultValue = true)
    val (playerStyle, onPlayerStyle) = rememberEnumPreference (PlayerStyleKey , defaultValue = PlayerStyle.NEW)
    val (miniPlayerStyle, onMiniPlayerStyle) = rememberEnumPreference(MiniPlayerStyleKey, defaultValue = MiniPlayerStyle.NEW)
    val (innerTubeCookie) = rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }

    val isSystemInDarkTheme = isSystemInDarkTheme()
    val useDarkTheme = remember(darkMode, isSystemInDarkTheme) {
        if (darkMode == DarkMode.AUTO) isSystemInDarkTheme else darkMode == DarkMode.ON
    }

    val (playerBackground, onPlayerBackgroundChange) =
        rememberEnumPreference(
            PlayerBackgroundStyleKey,
            defaultValue = PlayerBackgroundStyle.DEFAULT,
        )


    var showCornerRadiusDialog by remember {
        mutableStateOf(false)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (showCornerRadiusDialog) {
            CounterDialog(
                title = stringResource(R.string.thumbnail_corner_radius),
                onDismiss = { showCornerRadiusDialog = false },
                icon = { Icon(Icons.Rounded.Image, null) },
                initialValue = thumbnailCornerRadius,
                upperBound = 10,
                lowerBound = 0,
                resetValue = 3,
                unitDisplay = if (thumbnailCornerRadius.toFloat() != 0.toFloat()) "0%" else "%",
                onConfirm = {
                    showCornerRadiusDialog = false
                    onThumbnailCornerRadius(it)
                },
                onCancel = {
                    showCornerRadiusDialog = false
                },
                onReset = { onThumbnailCornerRadius(6) },
            )
        }
    }

    var showNowPlayingPaddingDialog by remember {
        mutableStateOf(false)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (showNowPlayingPaddingDialog ) {
            CounterDialog(
                title = stringResource(R.string.now_playing_padding),
                onDismiss = { showNowPlayingPaddingDialog  = false },
                icon = { Icon(Icons.Rounded.Padding, null) },
                initialValue = nowPlayingPadding,
                upperBound = 100,
                lowerBound = 0,
                resetValue = 35,
                onConfirm = {
                    showNowPlayingPaddingDialog  = false
                    onNowPlayingPadding(it)
                },
                onCancel = {
                    showNowPlayingPaddingDialog  = false
                },
                onReset = { onNowPlayingPadding(35) },
            )
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
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top
                )
            )
        )

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

        AnimatedVisibility(useDarkTheme) {
            SwitchPreference(
                title = { Text(stringResource(R.string.pure_black)) },
                icon = { Icon(painterResource(R.drawable.contrast), null) },
                checked = pureBlack,
                onCheckedChange = { checked ->
                    onPureBlackChange(checked)
                }
            )
        }

        PreferenceEntry(
            title = { Text(stringResource(R.string.slider_style)) },
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
            title = stringResource(R.string.home)
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.show_content_filter)) },
            icon = { Icon(Icons.Rounded.FilterList, null) },
            checked = showContentFilter,
            onCheckedChange = onShowContentFilterChange
        )

        if (isLoggedIn) {
            SwitchPreference(
                title = { Text(stringResource(R.string.recent_activity)) },
                icon = { Icon(Icons.Rounded.QueryStats, null) },
                checked = showRecentActivity,
                onCheckedChange = onShowRecentActivityChange
            )
        }

        PreferenceGroupTitle(
            title = stringResource(R.string.player)
        )

        ListPreference(
            title = { Text(stringResource(R.string.player_style)) },
            icon = { Icon(painterResource(R.drawable.play), null) },
            selectedValue = playerStyle,
            values = listOf(PlayerStyle.OLD, PlayerStyle.NEW),
            valueText = {
                when (it) {
                    PlayerStyle.OLD -> stringResource(R.string.player_style_old)
                    PlayerStyle.NEW -> stringResource(R.string.player_style_new)
                }
            },
            onValueSelected = onPlayerStyle
        )

        ListPreference(
            title = { Text(stringResource(R.string.mini_player_style)) },
            icon = { Icon(painterResource(R.drawable.play), null) },
            selectedValue = miniPlayerStyle,
            values = listOf(MiniPlayerStyle.OLD, MiniPlayerStyle.NEW),
            valueText = {
                when (it) {
                    MiniPlayerStyle.OLD -> stringResource(R.string.player_style_old)
                    MiniPlayerStyle.NEW -> stringResource(R.string.player_style_new)
                }
            },
            onValueSelected = onMiniPlayerStyle
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
                    PlayerBackgroundStyle.BLURMOV -> stringResource(R.string.blurmv)
                    PlayerBackgroundStyle.BLUR -> stringResource(R.string.blur)
                }
            }
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.enable_swipe_thumbnail)) },
            icon = { Icon(painterResource(R.drawable.swipe), null) },
            checked = swipeThumbnail,
            onCheckedChange = onSwipeThumbnailChange,
        )

        PreferenceEntry(
            title = { Text(stringResource(R.string.thumbnail_corner_radius)) },
            description = "$thumbnailCornerRadius" + if (thumbnailCornerRadius.toFloat() != 0.toFloat()) "0%" else "%",
            icon = { Icon(Icons.Rounded.Image, null) },
            onClick = { showCornerRadiusDialog = true }
        )

        PreferenceGroupTitle(
            title = stringResource(R.string.now_playing)
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.now_playing)) },
            icon = { Icon(painterResource(R.drawable.playlist_play), null) },
            checked = nowPlayingEnable,
            onCheckedChange = onNowPlayingEnableChange
        )

        AnimatedVisibility(nowPlayingEnable) {
            PreferenceEntry(
                title = { Text(stringResource(R.string.now_playing_padding)) },
                description = "$nowPlayingPadding",
                icon = { Icon(painterResource(R.drawable.arrow_downward), null) },
                onClick = { showNowPlayingPaddingDialog = true }
            )
        }

        PreferenceGroupTitle(
            title = stringResource(R.string.misc)
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.swipe_song_to_dismiss)) },
            icon = { Icon(painterResource(R.drawable.queue_music), null) },
            checked = swipeSongToDismiss,
            onCheckedChange = onSwipeSongToDismissChange
        )

        EnumListPreference(
            title = { Text(stringResource(R.string.default_open_tab)) },
            icon = { Icon(painterResource(R.drawable.tab), null) },
            selectedValue = defaultOpenTab,
            onValueSelected = onDefaultOpenTabChange,
            valueText = {
                when (it) {
                    NavigationTab.HOME -> stringResource(R.string.home)
                    NavigationTab.EXPLORE -> stringResource(R.string.explore)
                    NavigationTab.LIBRARY -> stringResource(R.string.filter_library)
                }
            }
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.slim_navbar)) },
            icon = { Icon(painterResource(R.drawable.nav_bar), null) },
            checked = slimNav,
            onCheckedChange = onSlimNavChange
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

        SwitchPreference(
            title = { Text(stringResource(R.string.two_line_song_item_label)) },
            icon = { Icon(painterResource(R.drawable.two_line_text), null) },
            checked = twoLineSongItemLabel,
            onCheckedChange = onTwoLineSongItemLabelChange
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