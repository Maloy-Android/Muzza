package com.maloy.muzza.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.maloy.muzza.ui.screens.Screens

@Composable
actual fun NavIcon(screen: Screens) {
    val icon = when (screen) {
        Screens.Home    -> Icons.Filled.Home
        Screens.Explore -> Icons.Filled.Explore
        Screens.Library -> Icons.Filled.LibraryMusic
    }
    Icon(imageVector = icon, contentDescription = null)
}

@Composable
actual fun NavLabel(screen: Screens) {
    val label = when (screen) {
        Screens.Home    -> "Home"
        Screens.Explore -> "Explore"
        Screens.Library -> "Library"
    }
    Text(label)
}
