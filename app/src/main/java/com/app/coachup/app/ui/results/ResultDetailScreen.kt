package com.app.coachup.app.ui.results

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.app.coachup.app.models.ExerciseHistory
import com.app.coachup.app.models.ExerciseResult
import com.app.coachup.app.models.ProgressDataPoint
import com.app.coachup.app.navigation.NavigationStateHolder
import com.app.coachup.app.navigation.Routes
import com.app.coachup.app.services.AuthService
import com.app.coachup.app.services.ResultsService
import com.app.coachup.app.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException

@Composable
fun ResultDetailScreen(
    resultId: String,      // exerciseId
    navController: NavController
) {
    val exerciseName = remember {
        val n = NavigationStateHolder.pendingExerciseName
        NavigationStateHolder.pendingExerciseName = null
        n ?: "Egzersiz"
    }

    var exerciseResult by remember { mutableStateOf<ExerciseResult?>(null) }
    var history by remember { mutableStateOf<List<ExerciseHistory>>(emptyList()) }
    var chartData by remember { mutableStateOf<List<ProgressDataPoint>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    var selectedUnit by remember { mutableStateOf("KG") }

    LaunchedEffect(Unit) {
        val uid = AuthService.getCurrentUserId()
        if (uid.isNullOrEmpty()) {
            isLoading = false
            loadError = true
            return@LaunchedEffect
        }
        isLoading = true
        loadError = false
        try {
            exerciseResult = ResultsService.fetchExerciseResult(uid, resultId)
            history = ResultsService.fetchExerciseHistory(uid, resultId, limit = 50)
            chartData = ResultsService.getProgressData(uid, resultId, days = 90)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("ResultDetailScreen", "load error", e)
            loadError = true
        } finally {
            isLoading = false
        }
    }

    fun display(kg: Double): String {
        val v = if (selectedUnit == "LBS") kg * 2.20462 else kg
        return "%.1f".format(v)
    }

    val oneRepMax = exerciseResult?.oneRepMax ?: 0.0
    val maxWeight = exerciseResult?.maxWeight ?: 0.0
    val maxReps = exerciseResult?.maxReps ?: 0

    // Group history by year
    val historyByYear = remember(history) {
        history
            .groupBy {
                try {
                    Instant.parse(it.recordedAt)
                        .atZone(ZoneId.systemDefault())
                        .year.toString()
                } catch (_: Exception) { "—" }
            }
            .entries
            .sortedByDescending { it.key }
            .map { it.key to it.value.sortedByDescending { e -> e.recordedAt } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { navController.popBackStack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            }

            // KG / LBS toggle
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { selectedUnit = if (selectedUnit == "KG") "LBS" else "KG" }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        selectedUnit,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                }
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
            return@Column
        }

        if (loadError) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Default.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Text("Veriler yüklenemedi", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    TextButton(onClick = { navController.popBackStack() }) {
                        Text("Geri Dön", color = Primary)
                    }
                }
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 60.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Exercise name
            item {
                Text(
                    exerciseName,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            // Heaviest lift section
            item {
                HeaviestLiftCard(
                    maxWeight = maxWeight,
                    maxReps = maxReps,
                    oneRepMax = oneRepMax,
                    display = ::display,
                    unit = selectedUnit,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            // 1RM Percentages
            item {
                OneRmPercentagesSection(
                    oneRepMax = oneRepMax,
                    display = ::display,
                    unit = selectedUnit,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            // Progress chart
            if (chartData.size >= 2) {
                item {
                    ProgressChartSection(
                        chartData = chartData,
                        unit = selectedUnit,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }

            // History by year
            if (history.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.ShowChart, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                        Text("Henüz kayıt yok", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                historyByYear.forEach { (year, entries) ->
                    item(key = "year_$year") {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(year, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            entries.forEach { entry ->
                                ExpandableHistoryRow(entry = entry, unit = selectedUnit)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Heaviest Lift Card
// ---------------------------------------------------------------------------

@Composable
private fun HeaviestLiftCard(
    maxWeight: Double,
    maxReps: Int,
    oneRepMax: Double,
    display: (Double) -> String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "En Ağır Kaldırma",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Primary
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                StatColumn(
                    label = "Max Ağırlık",
                    value = if (maxWeight > 0) "${display(maxWeight)} $unit" else "--",
                    modifier = Modifier.weight(1f)
                )
                StatColumn(
                    label = "Max Tekrar",
                    value = if (maxReps > 0) "$maxReps rep" else "--",
                    modifier = Modifier.weight(1f)
                )
                StatColumn(
                    label = "1RM",
                    value = if (oneRepMax > 0) "${display(oneRepMax)} $unit" else "--",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ---------------------------------------------------------------------------
// 1RM Percentages — 3-column grid
// ---------------------------------------------------------------------------

@Composable
private fun OneRmPercentagesSection(
    oneRepMax: Double,
    display: (Double) -> String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "1RM Yüzdelikler",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Primary
        )

        if (oneRepMax <= 0) {
            Text("Henüz 1RM hesaplanamadı", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }

        val percentages = listOf(100, 90, 80, 70, 60, 50)
        val rows = percentages.chunked(3)

        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { pct ->
                    val weight = oneRepMax * pct / 100.0
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("%$pct", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(display(weight), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text(unit, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                // Fill empty cells if row has < 3 items
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Progress Chart (Canvas line chart)
// ---------------------------------------------------------------------------

@Composable
private fun ProgressChartSection(
    chartData: List<ProgressDataPoint>,
    unit: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "İlerleme Grafiği (90 gün)",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Primary
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            shadowElevation = 3.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val weights = chartData.map { it.weight }
                val minW = weights.minOrNull() ?: 0.0
                val maxW = weights.maxOrNull() ?: 1.0
                val range = if (maxW - minW < 1.0) 1.0 else maxW - minW

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val padH = 12.dp.toPx()
                    val count = chartData.size

                    if (count < 2) return@Canvas

                    val points = chartData.mapIndexed { i, point ->
                        val x = w * i / (count - 1).toFloat()
                        val y = h - padH - ((point.weight - minW) / range * (h - padH * 2)).toFloat()
                        Offset(x, y)
                    }

                    // Draw line
                    val path = Path()
                    path.moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { path.lineTo(it.x, it.y) }
                    drawPath(
                        path = path,
                        color = Primary,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    // Draw points
                    points.forEach { pt ->
                        drawCircle(color = Primary, radius = 4.dp.toPx(), center = pt)
                        drawCircle(color = Color.White, radius = 2.dp.toPx(), center = pt)
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    unit,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Expandable History Row
// ---------------------------------------------------------------------------

@Composable
private fun ExpandableHistoryRow(entry: ExerciseHistory, unit: String) {
    var isExpanded by remember { mutableStateOf(false) }

    val displayWeight = run {
        val w = if (unit == "LBS") entry.weight * 2.20462 else entry.weight
        "%.1f $unit".format(w)
    }
    val formattedDate = try {
        val instant = Instant.parse(entry.recordedAt)
        val local = instant.atZone(ZoneId.systemDefault()).toLocalDate()
        local.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("tr")))
    } catch (_: Exception) { entry.recordedAt.take(10) }

    val topRadius = 12.dp
    val bottomRadius = if (isExpanded && entry.notes?.isNotEmpty() == true) 0.dp else 12.dp

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        topStart = topRadius,
                        topEnd = topRadius,
                        bottomStart = bottomRadius,
                        bottomEnd = bottomRadius
                    )
                )
                .background(Purple100)
                .clickable { if (entry.notes?.isNotEmpty() == true) isExpanded = !isExpanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.FitnessCenter, null, tint = Color.White, modifier = Modifier.size(11.dp))
                Text(
                    "${entry.sets}×${entry.reps} — $displayWeight",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(formattedDate, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                if (entry.notes?.isNotEmpty() == true) {
                    Icon(
                        if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isExpanded && entry.notes?.isNotEmpty() == true,
            enter = expandVertically(animationSpec = tween(200)),
            exit = shrinkVertically(animationSpec = tween(200))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .background(Purple100.copy(alpha = 0.7f))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    entry.notes ?: "",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}
