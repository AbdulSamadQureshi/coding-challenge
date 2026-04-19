package com.bonial.core.ui.extensions

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize

private const val SHIMMER_OFFSET_MULTIPLIER = 2f
private const val SHIMMER_DURATION = 1200
private const val SHIMMER_COLOR_LIGHT = 0xFFEBEBEB
private const val SHIMMER_COLOR_DARK = 0xFFD6D6D6

fun Modifier.shimmerEffect(): Modifier =
    composed {
        var size by remember { mutableStateOf(IntSize.Zero) }
        val transition = rememberInfiniteTransition(label = "shimmer")
        val startOffsetX by transition.animateFloat(
            initialValue = -SHIMMER_OFFSET_MULTIPLIER * size.width.toFloat(),
            targetValue = SHIMMER_OFFSET_MULTIPLIER * size.width.toFloat(),
            animationSpec =
                infiniteRepeatable(
                    animation = tween(SHIMMER_DURATION, easing = LinearEasing),
                ),
            label = "shimmer",
        )

        background(
            brush =
                Brush.linearGradient(
                    colors =
                        listOf(
                            Color(SHIMMER_COLOR_LIGHT),
                            Color(SHIMMER_COLOR_DARK),
                            Color(SHIMMER_COLOR_LIGHT),
                        ),
                    start = Offset(startOffsetX, 0f),
                    end = Offset(startOffsetX + size.width.toFloat(), size.height.toFloat()),
                ),
        ).onGloballyPositioned {
            size = it.size
        }
    }
