package com.maloy.muzza

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.maloy.innertube.models.SongItem
import com.maloy.muzza.di.initKoinIos
import com.maloy.muzza.presentation.viewmodel.HomeViewModel
import com.maloy.muzza.presentation.viewmodel.PlayerViewModel
import com.maloy.muzza.ui.theme.MuzzaTheme
import org.koin.compose.viewmodel.koinViewModel
import platform.UIKit.UIViewController

@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController {
    initKoinIos()
    return ComposeUIViewController {
        MuzzaTheme {
            val homeViewModel = koinViewModel<HomeViewModel>()
            val playerViewModel = koinViewModel<PlayerViewModel>()
            val isLoading by homeViewModel.isLoading.collectAsState()
            val homePage by homeViewModel.homePage.collectAsState()
            val quickPicks by homeViewModel.quickPicks.collectAsState()
            val isPlaying by playerViewModel.isPlaying.collectAsState()

            Scaffold(
                bottomBar = {
                    BottomAppBar {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (isPlaying) "Playing" else "Paused")
                            IconButton(onClick = playerViewModel::togglePlayback) {
                                Text(if (isPlaying) "||" else ">")
                            }
                        }
                    }
                },
            ) { padding ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            text = "Muzza",
                            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                        )
                    }

                    if (isLoading) {
                        item {
                            CircularProgressIndicator()
                        }
                    }

                    quickPicks?.let { songs ->
                        item {
                            Text("Quick picks")
                        }
                        items(songs) { song ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { playerViewModel.playSong(song.id) },
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(song.title)
                                    Text(song.artistName.orEmpty())
                                }
                            }
                        }
                    }

                    homePage?.sections?.forEach { section ->
                        item {
                            Text(section.title)
                        }
                        items(section.items.filterIsInstance<SongItem>().take(20)) { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { playerViewModel.playSong(item.id) },
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(item.title)
                                    Text(item.artists.joinToString { it.name })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
