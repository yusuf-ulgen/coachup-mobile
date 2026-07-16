package com.app.coachup.app.ui.training.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

// ---------------------------------------------------------------------------
// ConfettiView — mirrors iOS ConfettiView.swift
// ---------------------------------------------------------------------------

private val confettiPalette = listOf(
    Color(0xFFFF6047),
    Color(0xFFFFD166),
    Color(0xFF06D6A0),
    Color(0xFF118AB2),
    Color(0xFFEF476F)
)

private data class ConfettiPiece(
    val startX: Float,
    val color: Color,
    val rotation: Float,   // 180..720
    val delay: Long        // ms stagger
)

@Composable
fun ConfettiView(
    modifier: Modifier = Modifier,
    pieces: Int = 24,
    durationMs: Int = 2000
) {
    val rng = remember { Random(System.currentTimeMillis()) }

    val confetti = remember {
        List(pieces) {
            ConfettiPiece(
                startX = rng.nextFloat(),
                color = confettiPalette[it % confettiPalette.size],
                rotation = (180 + rng.nextFloat() * 540),
                delay = (rng.nextFloat() * 400).toLong()
            )
        }
    }

    val progresses = confetti.map { piece ->
        val anim = remember { Animatable(0f) }
        LaunchedEffect(piece) {
            delay(piece.delay)
            anim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = durationMs, easing = EaseIn)
            )
        }
        anim.value
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
    ) {
        confetti.forEachIndexed { i, piece ->
            val progress = progresses.getOrElse(i) { 0f }
            if (progress <= 0f) return@forEachIndexed

            val x = piece.startX * size.width
            val y = progress * (size.height + 40)
            val rot = piece.rotation * progress
            val alpha = 1f - maxOf(0f, (progress - 0.7f) / 0.3f) // fade out at end

            rotate(degrees = rot, pivot = Offset(x, y)) {
                drawRoundRect(
                    color = piece.color.copy(alpha = alpha),
                    topLeft = Offset(x - 4f, y - 6f),
                    size = Size(8f, 12f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f)
                )
            }
        }
    }
}
