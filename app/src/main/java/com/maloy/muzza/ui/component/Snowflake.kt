package com.maloy.muzza.ui.component

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.maloy.muzza.constants.SnowflakeComplexity
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@SuppressLint("LocalContextResourcesRead")
@Composable
fun SnowfallEffect(
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    snowflakeCount: Int = 40,
    speedMultiplier: Float = 1f,
    complexity: SnowflakeComplexity = SnowflakeComplexity.MEDIUM,
    isDarkTheme: Boolean = false
) {
    if (!isActive) return

    val context = LocalContext.current
    val density = LocalDensity.current
    val screenHeight = context.resources.displayMetrics.heightPixels / density.density
    val screenWidth = context.resources.displayMetrics.widthPixels / density.density

    var snowflakes by remember {
        mutableStateOf(
            List(snowflakeCount) {
                Snowflake.createRandom(
                    maxX = screenWidth,
                    complexity = complexity
                )
            }
        )
    }

    LaunchedEffect(true) {
        while (isActive) {
            snowflakes = snowflakes.map { snowflake ->
                val newY = snowflake.y + snowflake.speed * speedMultiplier
                val time = snowflake.time + 1
                val sway = sin(snowflake.swayFrequency * time) * snowflake.swayAmplitude
                val newX = snowflake.x + sway
                val newRotation = snowflake.rotation + snowflake.rotationSpeed

                if (newY > screenHeight + 60f) {
                    Snowflake.createRandom(
                        maxX = screenWidth,
                        startY = -60f,
                        complexity = complexity
                    )
                } else {
                    snowflake.copy(
                        x = newX,
                        y = newY,
                        rotation = newRotation,
                        time = time
                    )
                }
            }
            delay(16)
        }
    }

    val snowflakeColor = if (isDarkTheme) {
        Color(0xFFE8F4FD)
    } else {
        Color(0xFF2C5282)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        snowflakes.forEach { snowflake ->
            drawSnowflake(
                snowflake = snowflake,
                color = snowflakeColor
            )
        }
    }
}

private fun DrawScope.drawSnowflake(
    snowflake: Snowflake,
    color: Color
) {
    val center = Offset(snowflake.x.dp.toPx(), snowflake.y.dp.toPx())
    val mainColor = color.copy(alpha = snowflake.alpha)
    val mainStrokeWidth = snowflake.size.dp.toPx() * 0.15f

    rotate(degrees = snowflake.rotation, pivot = center) {
        for (i in 0 until 6) {
            val angle = (i * 60).toDouble()
            val rad = Math.toRadians(angle)

            val endX = center.x + cos(rad).toFloat() * snowflake.size.dp.toPx()
            val endY = center.y + sin(rad).toFloat() * snowflake.size.dp.toPx()

            drawLine(
                color = mainColor,
                start = center,
                end = Offset(endX, endY),
                strokeWidth = mainStrokeWidth,
                cap = StrokeCap.Round
            )

            listOf(30.0, -30.0).forEach { sideAngle ->
                val sideRad = Math.toRadians(angle + sideAngle)
                val sideLength = snowflake.size.dp.toPx() * 0.45f
                val sideStartX = center.x + cos(rad).toFloat() * snowflake.size.dp.toPx() * 0.65f
                val sideStartY = center.y + sin(rad).toFloat() * snowflake.size.dp.toPx() * 0.65f
                val sideEndX = sideStartX + cos(sideRad).toFloat() * sideLength
                val sideEndY = sideStartY + sin(sideRad).toFloat() * sideLength

                drawLine(
                    color = mainColor.copy(alpha = snowflake.alpha * 0.9f),
                    start = Offset(sideStartX, sideStartY),
                    end = Offset(sideEndX, sideEndY),
                    strokeWidth = mainStrokeWidth * 0.8f,
                    cap = StrokeCap.Round
                )

                if (snowflake.complexity == SnowflakeComplexity.COMPLEX) {
                    val tinyLength = snowflake.size.dp.toPx() * 0.25f
                    val tinyEndX = sideEndX + cos(sideRad).toFloat() * tinyLength
                    val tinyEndY = sideEndY + sin(sideRad).toFloat() * tinyLength

                    drawLine(
                        color = mainColor.copy(alpha = snowflake.alpha * 0.8f),
                        start = Offset(sideEndX, sideEndY),
                        end = Offset(tinyEndX, tinyEndY),
                        strokeWidth = mainStrokeWidth * 0.6f,
                        cap = StrokeCap.Round
                    )
                }
            }

            drawCircle(
                color = mainColor.copy(alpha = snowflake.alpha * 0.9f),
                radius = mainStrokeWidth * 0.8f,
                center = Offset(endX, endY)
            )
        }

        if (snowflake.complexity != SnowflakeComplexity.SIMPLE) {
            val innerSize = snowflake.size.dp.toPx() * 0.45f
            for (i in 0 until 6) {
                val angle1 = (i * 60 + 30).toDouble()
                val angle2 = ((i + 1) * 60 + 30).toDouble()
                val rad1 = Math.toRadians(angle1)
                val rad2 = Math.toRadians(angle2)

                val startX = center.x + cos(rad1).toFloat() * innerSize
                val startY = center.y + sin(rad1).toFloat() * innerSize
                val endX = center.x + cos(rad2).toFloat() * innerSize
                val endY = center.y + sin(rad2).toFloat() * innerSize

                drawLine(
                    color = mainColor.copy(alpha = snowflake.alpha * 0.85f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = mainStrokeWidth * 0.7f,
                    cap = StrokeCap.Round
                )

                drawCircle(
                    color = mainColor.copy(alpha = snowflake.alpha * 0.9f),
                    radius = mainStrokeWidth * 0.5f,
                    center = Offset(startX, startY)
                )
            }
        }

        drawCircle(
            color = mainColor,
            radius = snowflake.size.dp.toPx() * 0.25f,
            center = center
        )
    }

    val glowColor = if (snowflake.complexity != SnowflakeComplexity.SIMPLE) {
        color.copy(alpha = snowflake.alpha * 0.3f)
    } else {
        color.copy(alpha = snowflake.alpha * 0.2f)
    }

    drawCircle(
        color = glowColor,
        radius = snowflake.size.dp.toPx() * 1.8f,
        center = center,
        blendMode = BlendMode.Screen
    )
}

private data class Snowflake(
    val x: Float,
    val y: Float,
    val size: Float,
    val speed: Float,
    val swayFrequency: Float,
    val swayAmplitude: Float,
    val rotation: Float,
    val rotationSpeed: Float,
    val alpha: Float,
    val complexity: SnowflakeComplexity,
    val time: Int = 0
) {
    companion object {
        fun createRandom(
            maxX: Float,
            startY: Float = -Random.nextFloat() * 200f,
            complexity: SnowflakeComplexity = SnowflakeComplexity.MEDIUM
        ): Snowflake {
            return Snowflake(
                x = Random.nextFloat() * maxX,
                y = startY,
                size = when (complexity) {
                    SnowflakeComplexity.SIMPLE -> Random.nextFloat() * 4f + 2f
                    SnowflakeComplexity.MEDIUM -> Random.nextFloat() * 5f + 3f
                    SnowflakeComplexity.COMPLEX -> Random.nextFloat() * 6f + 4f
                },
                speed = when (complexity) {
                    SnowflakeComplexity.SIMPLE -> Random.nextFloat() * 0.8f + 0.3f
                    SnowflakeComplexity.MEDIUM -> Random.nextFloat() * 1.0f + 0.4f
                    SnowflakeComplexity.COMPLEX -> Random.nextFloat() * 1.2f + 0.5f
                },
                swayFrequency = Random.nextFloat() * 0.02f + 0.01f,
                swayAmplitude = Random.nextFloat() * 0.4f + 0.2f,
                rotation = Random.nextFloat() * 360f,
                rotationSpeed = Random.nextFloat() * 0.4f - 0.2f,
                alpha = when (complexity) {
                    SnowflakeComplexity.SIMPLE -> Random.nextFloat() * 0.3f + 0.4f
                    SnowflakeComplexity.MEDIUM -> Random.nextFloat() * 0.35f + 0.45f
                    SnowflakeComplexity.COMPLEX -> Random.nextFloat() * 0.4f + 0.5f
                },
                complexity = complexity
            )
        }
    }
}