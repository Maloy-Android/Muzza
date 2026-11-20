package com.maloy.muzza.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSliderTrack(
    sliderState: SliderState,
    modifier: Modifier = Modifier,
    colors: SliderColors = SliderDefaults.colors(),
    trackHeight: Dp = 12.dp
) {
    val inactiveTrackColor = colors.inactiveTrackColor.copy(alpha = 0.3f)
    val activeTrackColor = colors.activeTrackColor
    val valueRange = sliderState.valueRange

    Canvas(
        modifier
            .fillMaxWidth()
            .height(28.dp)
    ) {
        drawTrack(
            calcFraction(
                valueRange.start,
                valueRange.endInclusive,
                sliderState.value.coerceIn(valueRange.start, valueRange.endInclusive)
            ),
            inactiveTrackColor,
            activeTrackColor,
            trackHeight
        )
    }
}

private fun DrawScope.drawTrack(
    activeRangeEnd: Float,
    inactiveTrackColor: Color,
    activeTrackColor: Color,
    trackHeight: Dp = 12.dp
) {
    val trackStrokeWidth = trackHeight.toPx()
    val trackCornerRadius = trackStrokeWidth / 2
    val centerY = center.y

    drawRoundRect(
        color = inactiveTrackColor,
        topLeft = Offset(0f, centerY - trackStrokeWidth / 2),
        size = Size(size.width, trackStrokeWidth),
        cornerRadius = CornerRadius(trackCornerRadius, trackCornerRadius)
    )

    val activeTrackWidth = size.width * activeRangeEnd

    if (activeTrackWidth > 0) {
        clipRect(
            left = 0f,
            top = centerY - trackStrokeWidth / 2,
            right = activeTrackWidth,
            bottom = centerY + trackStrokeWidth / 2
        ) {
            drawRoundRect(
                color = activeTrackColor,
                topLeft = Offset(0f, centerY - trackStrokeWidth / 2),
                size = Size(size.width, trackStrokeWidth),
                cornerRadius = CornerRadius(trackCornerRadius, trackCornerRadius)
            )
        }

        drawCircle(
            color = activeTrackColor,
            center = Offset(activeTrackWidth, centerY),
            radius = trackStrokeWidth / 2
        )
    }

    val thumbSize = 18.dp.toPx()
    val thumbStrokeWidth = 2.dp.toPx()

    drawCircle(
        color = Color.White,
        center = Offset(activeTrackWidth, centerY),
        radius = thumbSize / 2
    )

    drawCircle(
        color = activeTrackColor,
        center = Offset(activeTrackWidth, centerY),
        radius = thumbSize / 2 - thumbStrokeWidth / 2,
        style = Stroke(width = thumbStrokeWidth)
    )

    drawCircle(
        color = activeTrackColor,
        center = Offset(activeTrackWidth, centerY),
        radius = thumbSize / 4
    )
}

private fun calcFraction(a: Float, b: Float, pos: Float) =
    (if (b - a == 0f) 0f else (pos - a) / (b - a)).coerceIn(0f, 1f)