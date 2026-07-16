package com.app.coachup.app.ui.training

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.coachup.app.theme.*

// ---------------------------------------------------------------------------
// Domain model — mirrors iOS WorkoutGoal enum
// ---------------------------------------------------------------------------

sealed class WorkoutGoal {
    object None : WorkoutGoal()
    data class Distance(val km: Double) : WorkoutGoal()
    data class Duration(val seconds: Int) : WorkoutGoal()

    /** 0..1 progress towards the goal, given current workout state */
    fun progress(distanceKm: Double, elapsedSeconds: Int): Float = when (this) {
        is None     -> 0f
        is Distance -> (distanceKm / km).toFloat().coerceIn(0f, 1f)
        is Duration -> (elapsedSeconds.toFloat() / seconds).coerceIn(0f, 1f)
    }

    /** Short human-readable label shown in the active workout HUD */
    fun label(): String = when (this) {
        is None     -> ""
        is Distance -> "%.1f km hedef".format(km)
        is Duration -> "Hedef: ${formatGoalDuration(seconds)}"
    }
}

private fun formatGoalDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h} sa ${m} dk" else "${m} dk"
}

// ---------------------------------------------------------------------------
// Bottom-sheet composable — mirrors iOS WorkoutGoalSheet
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutGoalSheet(
    onGoalSelected: (WorkoutGoal) -> Unit,
    onDismiss: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    var selectedGoal by remember { mutableStateOf<WorkoutGoal>(WorkoutGoal.None) }

    val distanceOptions = listOf(1.0, 3.0, 5.0, 10.0, 15.0, 21.1, 42.2)
    val durationOptions = listOf(
        15 * 60 to "15 dk",
        20 * 60 to "20 dk",
        30 * 60 to "30 dk",
        45 * 60 to "45 dk",
        60 * 60 to "1 sa",
        90 * 60 to "1.5 sa",
        120 * 60 to "2 sa"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Hedef Belirle",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Segmented control: Mesafe | Süre
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f))
            ) {
                listOf("Mesafe", "Süre").forEachIndexed { index, label ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (tab == index) Primary else Color.Transparent)
                            .clickable { tab = index }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (tab == index) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (tab == 0) {
                GoalGrid(
                    items = distanceOptions,
                    isSelected = { km -> selectedGoal is WorkoutGoal.Distance && (selectedGoal as WorkoutGoal.Distance).km == km },
                    onSelect = { km -> selectedGoal = WorkoutGoal.Distance(km) },
                    label = { km ->
                        Pair(
                            if (km == km.toInt().toDouble()) "${km.toInt()}" else "%.1f".format(km),
                            "km"
                        )
                    }
                )
            } else {
                GoalGrid(
                    items = durationOptions,
                    isSelected = { (secs, _) -> selectedGoal is WorkoutGoal.Duration && (selectedGoal as WorkoutGoal.Duration).seconds == secs },
                    onSelect = { (secs, _) -> selectedGoal = WorkoutGoal.Duration(secs) },
                    label = { (_, lbl) -> Pair(lbl, null) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f))
                        .clickable {
                            onGoalSelected(WorkoutGoal.None)
                            onDismiss()
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Hedefsiz",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Primary)
                        .clickable {
                            onGoalSelected(selectedGoal)
                            onDismiss()
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Tamam",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Reusable 3-column goal grid
// ---------------------------------------------------------------------------

@Composable
private fun <T> GoalGrid(
    items: List<T>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    label: (T) -> Pair<String, String?>
) {
    val cols = 3
    items.chunked(cols).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            row.forEach { item ->
                val selected = isSelected(item)
                val (main, sub) = label(item)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
                        .clickable { onSelect(item) }
                        .padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = main,
                        fontSize = if (sub != null) 22.sp else 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                    if (sub != null) {
                        Text(
                            text = sub,
                            fontSize = 11.sp,
                            color = if (selected) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            // Pad remaining cells in last row
            repeat(cols - row.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
}
