package com.maloy.muzza

import androidx.compose.ui.window.ComposeUIViewController
import com.maloy.muzza.ui.MuzzaNavHost
import com.maloy.muzza.ui.theme.MuzzaTheme

@Suppress("FunctionName", "unused")
fun MainViewController() = ComposeUIViewController {
    MuzzaTheme {
        MuzzaNavHost()
    }
}
