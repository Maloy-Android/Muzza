package com.maloy.muzza.ui.screens.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.maloy.muzza.LocalPlayerAwareWindowInsets
import com.maloy.muzza.R
import com.maloy.muzza.constants.StreamSourceAndroidCreatorKey
import com.maloy.muzza.constants.StreamSourceAndroidVRKey
import com.maloy.muzza.constants.StreamSourceIOSKey
import com.maloy.muzza.constants.StreamSourceTVHTML5Key
import com.maloy.muzza.constants.StreamSourceVisionOSKey
import com.maloy.muzza.constants.StreamSourceWebCreatorKey
import com.maloy.muzza.constants.StreamSourceWebRemixKey
import com.maloy.muzza.ui.component.IconButton
import com.maloy.muzza.ui.component.PreferenceGroupTitle
import com.maloy.muzza.ui.component.SwitchPreference
import com.maloy.muzza.ui.utils.backToMain
import com.maloy.muzza.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamSourcesSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (webRemix, onWebRemixChange) = rememberPreference(StreamSourceWebRemixKey, defaultValue = true)
    val (tvhtml5, onTvhtml5Change) = rememberPreference(StreamSourceTVHTML5Key, defaultValue = true)
    val (visionOS, onVisionOSChange) = rememberPreference(StreamSourceVisionOSKey, defaultValue = true)
    val (androidVR, onAndroidVRChange) = rememberPreference(StreamSourceAndroidVRKey, defaultValue = true)
    val (ios, onIosChange) = rememberPreference(StreamSourceIOSKey, defaultValue = false)
    val (webCreator, onWebCreatorChange) = rememberPreference(StreamSourceWebCreatorKey, defaultValue = true)
    val (androidCreator, onAndroidCreatorChange) = rememberPreference(StreamSourceAndroidCreatorKey, defaultValue = false)

    val streamOrder = listOf(
        stringResource(R.string.stream_source_web_remix) to webRemix,
        stringResource(R.string.stream_source_visionos) to visionOS,
        stringResource(R.string.stream_source_web_creator) to webCreator,
        stringResource(R.string.stream_source_tvhtml5) to tvhtml5,
        stringResource(R.string.stream_source_android_vr) to androidVR,
        stringResource(R.string.stream_source_ios) to ios,
        stringResource(R.string.stream_source_android_creator) to androidCreator,
    ).filter { it.second }.map { it.first }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))

        Text(
            text = stringResource(R.string.stream_source_order),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            streamOrder.forEachIndexed { index, name ->
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = "${index + 1}. $name",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        PreferenceGroupTitle(
            title = stringResource(R.string.stream_source_web_clients)
        )

        SwitchPreference(
            icon = { Icon(painterResource(R.drawable.play), null) },
            title = { Text(stringResource(R.string.stream_source_web_remix)) },
            description = stringResource(R.string.stream_source_web_remix_desc),
            checked = webRemix,
            onCheckedChange = onWebRemixChange
        )

        SwitchPreference(
            icon = { Icon(painterResource(R.drawable.play), null) },
            title = { Text(stringResource(R.string.stream_source_tvhtml5)) },
            description = stringResource(R.string.stream_source_tvhtml5_desc),
            checked = tvhtml5,
            onCheckedChange = onTvhtml5Change
        )

        PreferenceGroupTitle(
            title = stringResource(R.string.stream_source_native_clients)
        )

        SwitchPreference(
            icon = { Icon(painterResource(R.drawable.play), null) },
            title = { Text(stringResource(R.string.stream_source_visionos)) },
            description = stringResource(R.string.stream_source_visionos_desc),
            checked = visionOS,
            onCheckedChange = onVisionOSChange
        )

        SwitchPreference(
            icon = { Icon(painterResource(R.drawable.play), null) },
            title = { Text(stringResource(R.string.stream_source_android_vr)) },
            description = stringResource(R.string.stream_source_android_vr_desc),
            checked = androidVR,
            onCheckedChange = onAndroidVRChange
        )

        SwitchPreference(
            icon = { Icon(painterResource(R.drawable.play), null) },
            title = { Text(stringResource(R.string.stream_source_ios)) },
            description = stringResource(R.string.stream_source_ios_desc),
            checked = ios,
            onCheckedChange = onIosChange
        )

        PreferenceGroupTitle(
            title = stringResource(R.string.stream_source_creator_clients)
        )

        SwitchPreference(
            icon = { Icon(painterResource(R.drawable.play), null) },
            title = { Text(stringResource(R.string.stream_source_web_creator)) },
            description = stringResource(R.string.stream_source_web_creator_desc),
            checked = webCreator,
            onCheckedChange = onWebCreatorChange
        )


        SwitchPreference(
            icon = { Icon(painterResource(R.drawable.play), null) },
            title = { Text(stringResource(R.string.stream_source_android_creator)) },
            description = stringResource(R.string.stream_source_android_creator_desc),
            checked = androidCreator,
            onCheckedChange = onAndroidCreatorChange
        )
    }
    CenterAlignedTopAppBar(
        title = { Text(stringResource(R.string.stream_sources)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}