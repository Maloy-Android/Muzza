@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@file:Suppress("FunctionName", "unused")

package com.maloy.muzza

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.maloy.muzza.crash.IosCrashRecovery
import com.maloy.muzza.di.initKoinIos
import com.maloy.muzza.ui.theme.MuzzaTheme
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    IosCrashRecovery.install()
    runCatching { initKoinIos() }.onFailure(IosCrashRecovery::record)

    return ComposeUIViewController {
        MuzzaTheme {
            var selectedTab by rememberSaveable { mutableIntStateOf(0) }

            Scaffold(
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = { Text("H") },
                            label = { Text("Home") },
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = { Text("L") },
                            label = { Text("Library") },
                        )
                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            icon = { Text("S") },
                            label = { Text("Settings") },
                        )
                    }
                },
            ) { padding ->
                when (selectedTab) {
                    0 -> HomeScreen(Modifier.fillMaxSize().padding(padding))
                    1 -> LibraryScreen(Modifier.fillMaxSize().padding(padding))
                    else -> SettingsScreen(Modifier.fillMaxSize().padding(padding))
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun HomeScreen(modifier: Modifier) {
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Muzza",
            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
        )
        Text("Good to see you")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Home")
                Text("Your music and recommendations will appear here.")
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Quick picks")
                Text("Loading music is isolated to keep the app available.")
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun LibraryScreen(modifier: Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Library")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Your collection")
                Text("Albums, artists and playlists are ready for the next data layer.")
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun SettingsScreen(modifier: Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Muzza")
                Text("Home, Library and Settings are available.")
            }
        }
    }
}
