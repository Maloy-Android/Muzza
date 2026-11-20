package com.maloy.muzza.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.maloy.muzza.R
import com.maloy.muzza.ui.screens.Screens

@Composable
actual fun NavIcon(screen: Screens) {
    when (screen) {
        Screens.Home -> Icon(
            painter = painterResource(R.drawable.home_outlined),
            contentDescription = null
        )
        Screens.Explore -> Icon(
            painter = painterResource(R.drawable.explore_outlined),
            contentDescription = null
        )
        Screens.Library -> Icon(
            painter = painterResource(R.drawable.library_music_outlined),
            contentDescription = null
        )
    }
}

@Composable
actual fun NavLabel(screen: Screens) {
    when (screen) {
        Screens.Home    -> Text(stringResource(R.string.home))
        Screens.Explore -> Text(stringResource(R.string.explore))
        Screens.Library -> Text(stringResource(R.string.filter_library))
    }
}
