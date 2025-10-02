package com.maloy.muzza.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.maloy.muzza.LocalPlayerAwareWindowInsets
import com.maloy.muzza.R
import com.maloy.muzza.constants.AudioNormalizationKey
import com.maloy.muzza.constants.AudioOffload
import com.maloy.muzza.constants.AudioQuality
import com.maloy.muzza.constants.AudioQualityKey
import com.maloy.muzza.constants.AutoLoadMoreKey
import com.maloy.muzza.constants.AutoPlaySongWhenBluetoothDeviceConnectedKey
import com.maloy.muzza.constants.AutoSkipNextOnErrorKey
import com.maloy.muzza.constants.PersistentQueueKey
import com.maloy.muzza.constants.SkipSilenceKey
import com.maloy.muzza.constants.SongDurationTimeSkip
import com.maloy.muzza.constants.SongDurationTimeSkipKey
import com.maloy.muzza.constants.StopMusicOnTaskClearKey
import com.maloy.muzza.constants.StopPlayingSongWhenMinimumVolumeKey
import com.maloy.muzza.constants.minPlaybackDurKey
import com.maloy.muzza.ui.component.CounterDialog
import com.maloy.muzza.ui.component.EnumListPreference
import com.maloy.muzza.ui.component.IconButton
import com.maloy.muzza.ui.component.PreferenceEntry
import com.maloy.muzza.ui.component.PreferenceGroupTitle
import com.maloy.muzza.ui.component.SwitchPreference
import com.maloy.muzza.ui.utils.backToMain
import com.maloy.muzza.utils.rememberEnumPreference
import com.maloy.muzza.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (audioQuality, onAudioQualityChange) = rememberEnumPreference(AudioQualityKey, defaultValue = AudioQuality.AUTO)
    val (persistentQueue, onPersistentQueueChange) = rememberPreference(PersistentQueueKey, defaultValue = true)
    val (skipSilence, onSkipSilenceChange) = rememberPreference(SkipSilenceKey, defaultValue = false)
    val (audioNormalization, onAudioNormalizationChange) = rememberPreference(AudioNormalizationKey, defaultValue = true)
    val (songDurationTimeSkip, onSongDurationTimeSkipChange) = rememberEnumPreference(
        SongDurationTimeSkipKey, defaultValue = SongDurationTimeSkip.FIVE)
    val (autoPlaySongWhenBluetoothDeviceConnected,onAutoPlaySongWhenBluetoothDeviceConnectedChange) = rememberPreference(
        AutoPlaySongWhenBluetoothDeviceConnectedKey,defaultValue = true)
    val (stopPlayingSongWhenMinimumVolume,onStopPlayingSongWhenMinimumVolumeChange) = rememberPreference(
        StopPlayingSongWhenMinimumVolumeKey,defaultValue = true)
    val (autoSkipNextOnError, onAutoSkipNextOnErrorChange) = rememberPreference(AutoSkipNextOnErrorKey, defaultValue = false)
    val (stopMusicOnTaskClear, onStopMusicOnTaskClearChange) = rememberPreference(StopMusicOnTaskClearKey, defaultValue = false)
    val (autoLoadMore, onAutoLoadMoreChange) = rememberPreference(AutoLoadMoreKey, defaultValue = true)
    val (minPlaybackDur, onMinPlaybackDurChange) = rememberPreference(minPlaybackDurKey, defaultValue = 30)
    val (audioOffload, onAudioOffloadChange) = rememberPreference(key = AudioOffload, defaultValue = false)

    var showMinPlaybackDur by remember {
        mutableStateOf(false)
    }

    if (showMinPlaybackDur) {
        CounterDialog(
            title = stringResource(R.string.minimum_playback_duration),
            description = stringResource(R.string.minimum_playback_duration_info),
            icon = { Icon(Icons.Rounded.Sync,null) },
            initialValue = minPlaybackDur,
            upperBound = 100,
            lowerBound = 0,
            resetValue = 30,
            unitDisplay = "%",
            onDismiss = { showMinPlaybackDur = false },
            onConfirm = {
                showMinPlaybackDur = false
                onMinPlaybackDurChange(it)
            },
            onCancel = {
                showMinPlaybackDur = false
            },
            onReset = { onMinPlaybackDurChange(30) },
        )
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))

        PreferenceGroupTitle(
            title = stringResource(R.string.player)
        )

        EnumListPreference(
            title = { Text(stringResource(R.string.audio_quality)) },
            icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
            selectedValue = audioQuality,
            onValueSelected = onAudioQualityChange,
            valueText = {
                when (it) {
                    AudioQuality.AUTO -> stringResource(R.string.audio_quality_auto)
                    AudioQuality.MAX -> stringResource(R.string.audio_quality_max)
                    AudioQuality.HIGH -> stringResource(R.string.audio_quality_high)
                    AudioQuality.LOW -> stringResource(R.string.audio_quality_low)
                }
            }
        )
        PreferenceEntry(
            title = { Text(stringResource(R.string.lyrics_settings_title)) },
            icon = { Icon(painterResource(R.drawable.lyrics), null) },
            onClick = { navController.navigate("settings/player/lyrics") }
        )

        PreferenceEntry(
            title = { Text(stringResource(R.string.local_player_settings_title)) },
            icon = { Icon(Icons.Rounded.SdCard, null) },
            onClick = { navController.navigate("player/local") }
        )

        PreferenceEntry(
            title = { Text(stringResource(R.string.minimum_playback_duration)) },
            description = "$minPlaybackDur %",
            icon = { Icon(Icons.Rounded.Sync, null) },
            onClick = { showMinPlaybackDur = true }
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.skip_silence)) },
            icon = { Icon(painterResource(R.drawable.fast_forward), null) },
            checked = skipSilence,
            onCheckedChange = onSkipSilenceChange
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.audio_normalization)) },
            icon = { Icon(Icons.AutoMirrored.Rounded.VolumeUp, null) },
            checked = audioNormalization,
            onCheckedChange = onAudioNormalizationChange
        )

        EnumListPreference(
            title = { Text(stringResource(R.string.seek_value)) },
            icon = { Icon(painterResource(R.drawable.fast_forward), null) },
            selectedValue = songDurationTimeSkip,
            onValueSelected = onSongDurationTimeSkipChange,
            valueText = {
                when (it) {
                    SongDurationTimeSkip.FIVE -> stringResource(R.string.seek_value_5)
                    SongDurationTimeSkip.TEN -> stringResource(R.string.seek_value_10)
                    SongDurationTimeSkip.FIFTEEN -> stringResource(R.string.seek_value_15)
                    SongDurationTimeSkip.TWENTY -> stringResource(R.string.seek_value_20)
                    SongDurationTimeSkip.TWENTYFIVE -> stringResource(R.string.seek_value_25)
                    SongDurationTimeSkip.THIRTY -> stringResource(R.string.seek_value_30)
                }
            }
        )


        SwitchPreference(
            title = { Text(stringResource(R.string.play_song_when_bluetooth_device_connected)) },
            icon = { Icon(Icons.Rounded.BluetoothConnected, null) },
            checked = autoPlaySongWhenBluetoothDeviceConnected,
            onCheckedChange = onAutoPlaySongWhenBluetoothDeviceConnectedChange,
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.stop_playing_when_song_sound_minimum_volume)) },
            icon = { Icon(Icons.AutoMirrored.Rounded.VolumeOff, null) },
            checked = stopPlayingSongWhenMinimumVolume,
            onCheckedChange = onStopPlayingSongWhenMinimumVolumeChange
        )

        PreferenceGroupTitle(
            title = stringResource(R.string.queue)
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.persistent_queue)) },
            description = stringResource(R.string.persistent_queue_desc),
            icon = { Icon(painterResource(R.drawable.queue_music), null) },
            checked = persistentQueue,
            onCheckedChange = onPersistentQueueChange
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.auto_load_more)) },
            description = stringResource(R.string.auto_load_more_desc),
            icon = { Icon(painterResource(R.drawable.playlist_add), null) },
            checked = autoLoadMore,
            onCheckedChange = onAutoLoadMoreChange
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.auto_skip_next_on_error)) },
            description = stringResource(R.string.auto_skip_next_on_error_desc),
            icon = { Icon(painterResource(R.drawable.skip_next), null) },
            checked = autoSkipNextOnError,
            onCheckedChange = onAutoSkipNextOnErrorChange
        )

        PreferenceGroupTitle(
            title = stringResource(R.string.misc)
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.audio_offload)) },
            description = stringResource(R.string.audio_offload_description),
            icon = { Icon(Icons.Rounded.Bolt, null) },
            checked = audioOffload,
            onCheckedChange = onAudioOffloadChange
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.stop_music_on_task_clear)) },
            icon = { Icon(painterResource(R.drawable.clear_all), null) },
            checked = stopMusicOnTaskClear,
            onCheckedChange = onStopMusicOnTaskClearChange
        )
    }

    TopAppBar(
        title = { Text(stringResource(R.string.player_and_audio)) },
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
