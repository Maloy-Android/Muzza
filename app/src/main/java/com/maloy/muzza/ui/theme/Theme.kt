package com.maloy.muzza.ui.theme

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme

val DefaultThemeColor = Color(0xFFED5564)

@Composable
fun MuzzaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = remember(darkTheme, pureBlack, themeColor) {
        if (themeColor == DefaultThemeColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (darkTheme) dynamicDarkColorScheme(context).pureBlack(pureBlack)
            else dynamicLightColorScheme(context)
        } else {
            dynamicColorScheme(
                primary = themeColor,
                isDark = darkTheme,
                isAmoled = darkTheme && pureBlack,
                style = PaletteStyle.TonalSpot
            )
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}

fun Bitmap.extractThemeColor(): Color {
    val palette = Palette.from(this)
        .maximumColorCount(8)
        .generate()

    val dominantSwatch = palette.vibrantSwatch
        ?: palette.lightVibrantSwatch
        ?: palette.darkVibrantSwatch
        ?: palette.dominantSwatch

    return if (dominantSwatch != null) {
        Color(dominantSwatch.rgb)
    } else {
        palette.swatches
            .maxByOrNull { it.population }
            ?.let { Color(it.rgb) }
            ?: DefaultThemeColor
    }
}

fun Bitmap.extractGradientColors(): List<Color> {
    val palette = Palette.from(this)
        .maximumColorCount(16)
        .generate()

    val sortedSwatches = palette.swatches
        .asSequence()
        .map { Color(it.rgb) }
        .filter { color ->
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(color.toArgb(), hsv)
            hsv[1] > 0.2f
        }
        .sortedByDescending { it.luminance() }
        .toList()

    return when {
        sortedSwatches.size >= 2 -> listOf(sortedSwatches[0], sortedSwatches[1])
        sortedSwatches.size == 1 -> listOf(sortedSwatches[0], Color(0xFF0D0D0D))
        else -> listOf(Color(0xFF595959), Color(0xFF0D0D0D)) // Fallback gradient
    }
}

fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(
        surface = Color.Black,
        background = Color.Black
    ) else this

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}