package com.app.coachup.app.ui.training.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.coachup.app.services.LoadedPlate
import com.app.coachup.app.services.PlateCalculator
import com.app.coachup.app.services.PlateLoad

// ---------------------------------------------------------------------------
// PlateVisualView — mirrors iOS PlateVisualView.swift
// ---------------------------------------------------------------------------

private data class PlateStyle(val color: Color, val heightDp: Dp)

private fun plateStyle(weight: Double): PlateStyle = when (weight) {
    25.0 -> PlateStyle(Color(0xFFF44336), 72.dp)
    20.0 -> PlateStyle(Color(0xFF2196F3), 66.dp)
    15.0 -> PlateStyle(Color(0xFFFFEB3B), 60.dp)
    10.0 -> PlateStyle(Color(0xFF4CAF50), 52.dp)
    5.0  -> PlateStyle(Color(0xFFFFFFFF), 40.dp)
    2.5  -> PlateStyle(Color(0xFF333333), 32.dp)
    1.25 -> PlateStyle(Color(0xFFC0C0C0), 26.dp)
    else -> PlateStyle(Color(0xFF9E9E9E), 30.dp)
}

@Composable
fun PlateVisualView(
    load: PlateLoad,
    modifier: Modifier = Modifier
) {
    // Expand repeated plates: e.g. 2×25kg → [25, 25]
    val expandedPlates = load.perSide.flatMap { lp -> List(lp.count) { lp.weight } }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side (reversed)
        expandedPlates.reversed().forEach { weight ->
            PlateBlock(weight = weight)
        }

        // Left collar
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(28.dp)
                .background(Color(0xFFAAAAAA))
        )

        // Bar
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(8.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFFBBBBBB), Color(0xFF888888), Color(0xFFBBBBBB))
                    )
                )
        )

        // Right collar
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(28.dp)
                .background(Color(0xFFAAAAAA))
        )

        // Right side
        expandedPlates.forEach { weight ->
            PlateBlock(weight = weight)
        }
    }
}

@Composable
private fun PlateBlock(weight: Double) {
    val style = plateStyle(weight)
    val label = when {
        weight >= 1.0 -> "${weight.toInt()}"
        else -> "$weight"
    }

    Box(
        modifier = Modifier
            .width(14.dp)
            .height(style.heightDp)
            .clip(RoundedCornerShape(3.dp))
            .background(style.color),
        contentAlignment = Alignment.Center
    ) {
        // Weight label (only for plates ≥ 5kg for readability)
        if (weight >= 5.0) {
            Text(
                text = label,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                color = if (weight == 5.0) Color(0xFF1A1A1A) else Color.White,
                maxLines = 1
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Convenience overload — calculate from target weight
// ---------------------------------------------------------------------------

@Composable
fun PlateVisualView(
    targetKg: Double,
    modifier: Modifier = Modifier
) {
    val load = PlateCalculator.calculate(target = targetKg)
    PlateVisualView(load = load, modifier = modifier)
}
