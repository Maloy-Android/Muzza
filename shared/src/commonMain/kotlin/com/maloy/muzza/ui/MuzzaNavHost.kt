package com.maloy.muzza.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.maloy.muzza.ui.screens.Routes
import com.maloy.muzza.ui.screens.Screens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuzzaNavHost() {
    val navController = rememberNavController()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                Screens.MainScreens.forEach { screen ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { NavIcon(screen) },
                        label = { NavLabel(screen) },
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(tween(200)) + slideInHorizontally { it / 4 } },
            exitTransition = { fadeOut(tween(150)) },
            popEnterTransition = { fadeIn(tween(200)) + slideInHorizontally { -it / 4 } },
            popExitTransition = { fadeOut(tween(150)) + slideOutHorizontally { it / 4 } },
        ) {
            composable(Routes.HOME) {
                com.maloy.muzza.ui.screens.HomeScreen(navController)
            }
            composable(Routes.EXPLORE) {
                com.maloy.muzza.ui.screens.ExploreScreen(navController)
            }
            composable(Routes.LIBRARY) {
                com.maloy.muzza.ui.screens.library.LibraryMixScreen(navController)
            }
            composable(Routes.LIBRARY_ARTISTS) {
                com.maloy.muzza.ui.screens.library.LibraryArtistsScreen(navController, scrollBehavior)
            }
            composable(Routes.LIBRARY_ALBUMS) {
                com.maloy.muzza.ui.screens.library.LibraryAlbumsScreen(navController, scrollBehavior)
            }
            composable(Routes.LIBRARY_PLAYLISTS) {
                com.maloy.muzza.ui.screens.library.LibraryPlaylistsScreen(navController, scrollBehavior)
            }
            composable(Routes.HISTORY) {
                com.maloy.muzza.ui.screens.HistoryScreen(navController)
            }
            composable(Routes.STATS) {
                com.maloy.muzza.ui.screens.StatsScreen(navController)
            }
            composable(Routes.MOOD_AND_GENRES) {
                com.maloy.muzza.ui.screens.MoodAndGenresScreen(navController, scrollBehavior)
            }
            composable(Routes.NEW_RELEASE) {
                com.maloy.muzza.ui.screens.NewReleaseScreen(navController, scrollBehavior)
            }
            composable(Routes.NEW_CLIPS) {
                com.maloy.muzza.ui.screens.NewClipScreen(navController, scrollBehavior)
            }
            composable(
                route = "search/{query}",
                arguments = listOf(navArgument("query") { type = NavType.StringType }),
                enterTransition = { fadeIn(tween(250)) },
                exitTransition = { fadeOut(tween(200)) },
            ) {
                com.maloy.muzza.ui.screens.search.OnlineSearchResult(navController)
            }
            composable(
                route = "album/{albumId}",
                arguments = listOf(navArgument("albumId") { type = NavType.StringType }),
            ) {
                com.maloy.muzza.ui.screens.AlbumScreen(navController, scrollBehavior)
            }
            composable(
                route = "artist/{artistId}",
                arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val artistId = backStackEntry.arguments?.getString("artistId").orEmpty()
                if (artistId.startsWith("LA")) {
                    com.maloy.muzza.ui.screens.artist.ArtistSongsScreen(navController, scrollBehavior)
                } else {
                    com.maloy.muzza.ui.screens.artist.ArtistScreen(navController, scrollBehavior)
                }
            }
            composable(
                route = "artist/{artistId}/songs",
                arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
            ) {
                com.maloy.muzza.ui.screens.artist.ArtistSongsScreen(navController, scrollBehavior)
            }
            composable(
                route = "artist/{artistId}/items?browseId={browseId}&params={params}&title={title}",
                arguments = listOf(
                    navArgument("artistId") { type = NavType.StringType },
                    navArgument("browseId") { type = NavType.StringType; nullable = true },
                    navArgument("params")   { type = NavType.StringType; nullable = true },
                    navArgument("title")    { type = NavType.StringType; nullable = true },
                ),
            ) {
                com.maloy.muzza.ui.screens.artist.ArtistItemsScreen(navController, scrollBehavior)
            }
            composable(
                route = "online_playlist/{playlistId}",
                arguments = listOf(navArgument("playlistId") { type = NavType.StringType }),
            ) {
                com.maloy.muzza.ui.screens.playlist.OnlinePlaylistScreen(navController, scrollBehavior)
            }
            composable(
                route = "local_playlist/{playlistId}",
                arguments = listOf(navArgument("playlistId") { type = NavType.StringType }),
            ) {
                com.maloy.muzza.ui.screens.playlist.LocalPlaylistScreen(navController, scrollBehavior)
            }
            composable(
                route = "youtube_browse/{browseId}?params={params}",
                arguments = listOf(
                    navArgument("browseId") { type = NavType.StringType; nullable = true },
                    navArgument("params")   { type = NavType.StringType; nullable = true },
                ),
            ) {
                com.maloy.muzza.ui.screens.YouTubeBrowseScreen(navController, scrollBehavior)
            }
            composable(
                route = "top_playlist/{top}",
                arguments = listOf(navArgument("top") { type = NavType.StringType }),
            ) {
                com.maloy.muzza.ui.screens.playlist.TopPlaylistScreen(navController, scrollBehavior)
            }
            composable(
                route = "browse/{browseId}?params={params}?title={title}",
                arguments = listOf(
                    navArgument("browseId") { type = NavType.StringType },
                    navArgument("params")   { type = NavType.StringType; nullable = true },
                    navArgument("title")    { type = NavType.StringType; nullable = true },
                ),
            ) {
                com.maloy.muzza.ui.screens.BrowseScreen(
                    navController, scrollBehavior,
                    it.arguments?.getString("browseId")
                )
            }
            composable(Routes.AUTO_PLAYLIST_LIKED)      { com.maloy.muzza.ui.screens.playlist.AutoPlaylistLikedScreen(navController, scrollBehavior) }
            composable(Routes.AUTO_PLAYLIST_DOWNLOADED) { com.maloy.muzza.ui.screens.playlist.AutoPlaylistDownloadedScreen(navController, scrollBehavior) }
            composable(Routes.AUTO_PLAYLIST_LOCAL)      { com.maloy.muzza.ui.screens.playlist.AutoPlaylistLocalScreen(navController, scrollBehavior) }
            composable(Routes.AUTO_PLAYLIST_LIBRARY)    { com.maloy.muzza.ui.screens.playlist.AutoPlaylistLibraryScreen(navController, scrollBehavior) }
            composable(Routes.CACHED_PLAYLIST)          { com.maloy.muzza.ui.screens.playlist.CachePlaylistScreen(navController, scrollBehavior) }
            composable(Routes.SETTINGS)             { com.maloy.muzza.ui.screens.settings.SettingsScreen(navController, scrollBehavior) }
            composable(Routes.SETTINGS_APPEARANCE)  { com.maloy.muzza.ui.screens.settings.AppearanceSettings(navController, scrollBehavior) }
            composable(Routes.SETTINGS_ACCOUNT)     { com.maloy.muzza.ui.screens.settings.AccountSettings(navController, scrollBehavior) }
            composable(Routes.SETTINGS_CONTENT)     { com.maloy.muzza.ui.screens.settings.ContentSettings(navController, scrollBehavior) }
            composable(Routes.SETTINGS_PLAYER)      { com.maloy.muzza.ui.screens.settings.PlayerSettings(navController, scrollBehavior) }
            composable(Routes.SETTINGS_LYRICS)      { com.maloy.muzza.ui.screens.settings.LyricsSettings(navController, scrollBehavior) }
            composable(Routes.SETTINGS_STORAGE)     { com.maloy.muzza.ui.screens.settings.StorageSettings(navController, scrollBehavior) }
            composable(Routes.SETTINGS_PRIVACY)     { com.maloy.muzza.ui.screens.settings.PrivacySettings(navController, scrollBehavior) }
            composable(Routes.SETTINGS_BACKUP)      { com.maloy.muzza.ui.screens.settings.BackupAndRestore(navController, scrollBehavior) }
            composable(Routes.SETTINGS_DISCORD)     { com.maloy.muzza.ui.screens.settings.DiscordSettings(navController, scrollBehavior) }
            composable("settings/discord/login")    { com.maloy.muzza.ui.screens.settings.DiscordLoginScreen(navController) }
            composable(Routes.SETTINGS_ABOUT)       { com.maloy.muzza.ui.screens.settings.AboutScreen(navController, scrollBehavior) }
            composable(Routes.LOGIN)                { com.maloy.muzza.ui.screens.LoginScreen(navController) }
            composable(Routes.SETUP_WIZARD)         { com.maloy.muzza.ui.screens.SetupWizard(navController) }
        }
    }
}

/** Platform-specific bottom nav icon (expect/actual handles string resources vs vector). */
@Composable
expect fun NavIcon(screen: Screens)

/** Platform-specific bottom nav label. */
@Composable
expect fun NavLabel(screen: Screens)
