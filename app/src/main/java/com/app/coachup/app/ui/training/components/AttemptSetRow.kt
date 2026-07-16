package com.app.coachup.app.ui.training.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.coachup.app.models.RecordMeasureType
import com.app.coachup.app.services.PlannedSet
import com.app.coachup.app.theme.Primary
import com.app.coachup.app.ui.training.formatDuration
import kotlin.math.roundToInt

@Composable
fun AttemptSetRow(
    index: Int,
    set: PlannedSet,
    isCurrent: Boolean = false,
    measureType: RecordMeasureType = RecordMeasureType.WEIGHT,
    onClick: (() -> Unit)? = null
) {
    val isMain = set.isMain
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val rowBackground = when {
        isCurrent -> Primary.copy(alpha = 0.12f)
        set.isCompleted -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(rowBackground)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    when {
                        set.isCompleted -> Color(0xFF4CAF50)
                        isMain -> Primary
                        else -> muted.copy(alpha = 0.55f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (set.isCompleted) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
            } else {
                Text(
                    text = "${index + 1}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = when {
                        isMain && measureType != RecordMeasureType.WEIGHT -> "Ana Deneme"
                        isMain -> "Ana Set"
                        else -> "Isınma"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isMain) Primary else muted
                )
                if (!isMain && measureType == RecordMeasureType.WEIGHT) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(muted.copy(alpha = 0.15f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "%${(set.percentOf1RM * 100).toInt()}",
                            fontSize = 9.sp,
                            color = muted
                        )
                    }
                }
            }

            val value = set.actualWeight ?: set.plannedWeight
            val reps = set.actualReps ?: set.plannedReps
            Text(
                text = formatSetLine(measureType, value, reps),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        set.rpe?.let { rpe ->
            Text(
                text = "RPE $rpe",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = muted
            )
        }

        if (isCurrent && !set.isCompleted) {
            Icon(Icons.Filled.Edit, null, tint = Primary, modifier = Modifier.size(16.dp))
        }
    }
}

private fun formatSetLine(type: RecordMeasureType, value: Double, reps: Int): String = when (type) {
    RecordMeasureType.WEIGHT -> {
        val w = if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
        "$w kg × $reps"
    }
    RecordMeasureType.REPS -> "${reps.coerceAtLeast(value.roundToInt())} tekrar"
    RecordMeasureType.TIME -> formatDuration(value.roundToInt())
    RecordMeasureType.CALORIES -> "${value.roundToInt()} cal"
    RecordMeasureType.DISTANCE -> if (value >= 1.0) "%.1f km".format(value) else "${(value * 1000).roundToInt()} m"
}
