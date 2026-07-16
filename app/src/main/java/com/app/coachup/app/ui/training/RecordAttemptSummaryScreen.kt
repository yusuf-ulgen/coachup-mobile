package com.app.coachup.app.ui.training

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FlagCircle
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.coachup.app.models.Exercise
import com.app.coachup.app.services.AttemptSetType
import com.app.coachup.app.services.PlateCalculator
import com.app.coachup.app.services.RecordAttempt
import com.app.coachup.app.services.RecordAttemptSet
import com.app.coachup.app.services.SummaryResult
import com.app.coachup.app.theme.Primary
import com.app.coachup.app.theme.Purple100
import com.app.coachup.app.ui.training.components.ConfettiView

@Composable
fun RecordAttemptSummaryScreen(
    attempt: RecordAttempt,
    exercise: Exercise,
    sets: List<RecordAttemptSet>,
    result: SummaryResult,
    onDismiss: () -> Unit
) {
    val mainSets = sets.filter { it.setType == AttemptSetType.MAIN }
    val rpeValues = mainSets.mapNotNull { it.rpe }
    val estimated1RM = PlateCalculator.epley1RM(attempt.targetWeight, attempt.targetReps)

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(exercise.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(40.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                // Hero card
                HeroCard(attempt = attempt, result = result)

                if (result.success) {
                    // Stats row
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile(
                            modifier = Modifier.weight(1f),
                            label = "Tahmini 1RM",
                            value = "${formatWeight(estimated1RM)} kg",
                            icon = Icons.Filled.ShowChart
                        )
                        StatTile(
                            modifier = Modifier.weight(1f),
                            label = "RPE",
                            value = if (rpeValues.isEmpty()) "—" else "%.1f".format(rpeValues.average()),
                            icon = Icons.Filled.Speed
                        )
                    }

                    // RPE sparkline (if available)
                    if (rpeValues.size > 1) {
                        RPESparklineCard(rpeValues = rpeValues)
                    }
                } else {
                    // Failed sets list
                    FailedSetsCard(mainSets = mainSets)
                }

                Spacer(Modifier.height(120.dp))
            }
        }

        // CTA
        Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp)) {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text(
                    text = if (result.success) "Tamamla" else "Kapat",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // Confetti on new PR
        if (result.success && result.newPersonalRecord) {
            ConfettiView(modifier = Modifier.fillMaxSize())
        }
    }
}

// ---------------------------------------------------------------------------
// Hero Card
// ---------------------------------------------------------------------------

@Composable
private fun HeroCard(attempt: RecordAttempt, result: SummaryResult) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Purple100),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(vertical = 24.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (result.success) {
                Icon(Icons.Filled.EmojiEvents, null, tint = Color.White, modifier = Modifier.size(48.dp))
                Text(
                    text = if (result.newPersonalRecord) "Yeni Rekor!" else "Set Başarılı",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            } else {
                Icon(Icons.Filled.FlagCircle, null, tint = Color.White, modifier = Modifier.size(44.dp))
                Text("Rekor Kırılmadı", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Bir sonraki sefere", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    formatWeight(attempt.targetWeight),
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text("kg", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.7f))
                Text("× ${attempt.targetReps}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.9f))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Stat Tile
// ---------------------------------------------------------------------------

@Composable
private fun StatTile(modifier: Modifier, label: String, value: String, icon: ImageVector) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                Text(label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.5.sp)
            }
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// ---------------------------------------------------------------------------
// RPE Sparkline Card
// ---------------------------------------------------------------------------

@Composable
private fun RPESparklineCard(rpeValues: List<Int>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("RPE", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.5.sp)
            // Simple bar sparkline
            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rpeValues.forEach { rpe ->
                    val fraction = rpe / 10f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(
                                when {
                                    rpe <= 4 -> Color(0xFF4CAF50)
                                    rpe <= 7 -> Color(0xFFFF9800)
                                    else     -> Color(0xFFE53935)
                                }
                            )
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                rpeValues.forEachIndexed { idx, rpe ->
                    Text(
                        "$rpe",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Failed Sets Card
// ---------------------------------------------------------------------------

@Composable
private fun FailedSetsCard(mainSets: List<RecordAttemptSet>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("ANA SETLER", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.5.sp)
        mainSets.forEach { s ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                shadowElevation = 1.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${formatWeight(s.prescribedWeight ?: 0.0)} kg × ${s.prescribedReps ?: 1}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (s.actualReps != null) {
                            Text(
                                "Gerçek: ${formatWeight(s.actualWeight ?: 0.0)} × ${s.actualReps}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    s.rpe?.let { rpe ->
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Primary.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("RPE $rpe", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Primary)
                        }
                    }
                }
            }
        }
    }
}
