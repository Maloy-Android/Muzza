package com.maloy.muzza

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.maloy.muzza.ui.MuzzaNavHost
import com.maloy.muzza.ui.theme.MuzzaTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MuzzaTheme {
                MuzzaNavHost()
            }
        }
    }
}
