package com.app.coachup.app.ui.training.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------
// CircularRestTimer — mirrors iOS CircularRestTimer.swift
// ---------------------------------------------------------------------------

@Composable
fun CircularRestTimer(
    totalSeconds: Int,
    onFinish: () -> Unit,
    onSkip: () -> Unit
) {
    var remaining by remember { mutableStateOf(totalSeconds) }

    LaunchedEffect(totalSeconds) {
        remaining = totalSeconds
        while (remaining > 0) {
            delay(1000L)
            remaining--
        }
        onFinish()
    }

    val progress = remaining.toFloat() / totalSeconds.toFloat()

    // Full-screen dark overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000).copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Dinlenme",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.7f)
            )

            // Circular progress
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(160.dp)) {
                    val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                    val inset = stroke.width / 2
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    val topLeft = Offset(inset, inset)

                    // Background track
                    drawArc(
                        color = Color.White.copy(alpha = 0.15f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = stroke
                    )

                    // Progress arc (gradient-like: purple → blue)
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(Color(0xFF7C3AED), Color(0xFF3B82F6), Color(0xFF7C3AED))
                        ),
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = stroke
                    )
                }

                // Countdown number
                Text(
                    text = "$remaining",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Skip button
            Button(
                onClick = {
                    remaining = 0
                    onSkip()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.12f),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Atla", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
