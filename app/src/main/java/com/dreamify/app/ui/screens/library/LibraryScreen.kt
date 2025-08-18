package com.dreamify.app.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.dreamify.app.constants.ChipSortTypeKey
import com.dreamify.app.constants.LibraryFilter
import com.dreamify.app.utils.rememberEnumPreference
@Composable
fun LibraryScreen(navController: NavController) {
    val filterType by rememberEnumPreference(ChipSortTypeKey, LibraryFilter.LIBRARY)

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        when (filterType) {
            LibraryFilter.LIBRARY -> LibraryMixScreen(navController)
            LibraryFilter.PLAYLISTS -> LibraryPlaylistsScreen(navController)
            LibraryFilter.SONGS -> LibrarySongsScreen(navController)
            LibraryFilter.ALBUMS -> LibraryAlbumsScreen(navController)
            LibraryFilter.ARTISTS -> LibraryArtistsScreen(navController)
        }
    }
}