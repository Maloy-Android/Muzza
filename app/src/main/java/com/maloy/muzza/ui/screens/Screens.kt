package com.maloy.muzza.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.maloy.muzza.R

@Immutable
sealed class Screens(
    @StringRes val titleId: Int,
    @DrawableRes val iconId: Int,
    val route: String,
) {
    object Home : Screens(R.string.home, R.drawable.home, "home")
    object Library : Screens(R.string.filter_library, R.drawable.library_music, "library")

    companion object {
        val MainScreens = listOf(Home, Library)
    }
}
