package com.app.coachup.app.ui.training.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// RPESlider — mirrors iOS RPESlider.swift
//
// Range 1-10, gradient track green→yellow→orange→red
// ---------------------------------------------------------------------------

private fun rpeDescriptor(rpe: Int) = when (rpe) {
    in 1..3 -> "Çok Kolay"
    in 4..5 -> "Orta"
    in 6..7 -> "Zor"
    in 8..9 -> "Çok Zor"
    10 -> "Maksimum"
    else -> ""
}

private val trackGradient = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF4CAF50),
        Color(0xFFCDDC39),
        Color(0xFFFF9800),
        Color(0xFFF44336)
    )
)

@Composable
fun RPESlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Descriptor label
        Text(
            text = rpeDescriptor(value),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF1A1A1A)
        )

        // RPE value
        Text(
            text = "RPE $value",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A1A1A)
        )

        // Slider track
        var sliderWidth by remember { mutableStateOf(0f) }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        if (sliderWidth > 0) {
                            val fraction = (change.position.x / sliderWidth).coerceIn(0f, 1f)
                            val newVal = (fraction * 9 + 1).roundToInt().coerceIn(1, 10)
                            onValueChange(newVal)
                        }
                    }
                }
        ) {
            sliderWidth = size.width
            val trackHeight = 8.dp.toPx()
            val trackY = (size.height - trackHeight) / 2

            // Background track gradient
            drawRoundRect(
                brush = trackGradient,
                topLeft = Offset(0f, trackY),
                size = Size(size.width, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2),
                style = Fill
            )

            // Thumb
            val fraction = (value - 1) / 9f
            val thumbX = fraction * size.width
            val thumbRadius = 14.dp.toPx()

            drawCircle(color = Color.White, radius = thumbRadius, center = Offset(thumbX, size.height / 2))
            drawCircle(
                color = Color(0xFF1A1A1A),
                radius = thumbRadius,
                center = Offset(thumbX, size.height / 2),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Number row 1-10
        Row(modifier = Modifier.fillMaxWidth()) {
            for (i in 1..10) {
                Text(
                    text = "$i",
                    modifier = Modifier.weight(1f),
                    fontSize = if (i == value) 14.sp else 12.sp,
                    fontWeight = if (i == value) FontWeight.Bold else FontWeight.Normal,
                    color = if (i == value) Color(0xFF1A1A1A) else Color(0xFF9E9E9E),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
