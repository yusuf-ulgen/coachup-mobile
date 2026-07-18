package com.app.coachup.app.ui.training

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.coachup.app.models.Exercise
import com.app.coachup.app.models.RecordAttemptCategories
import com.app.coachup.app.models.RecordMeasureType
import com.app.coachup.app.services.LocationTrackingService
import com.app.coachup.app.services.RecordAttempt
import com.app.coachup.app.services.RecordAttemptService
import com.app.coachup.app.services.RecordAttemptSet
import com.app.coachup.app.services.ResultsService
import com.app.coachup.app.services.SummaryResult
import com.app.coachup.app.theme.Primary
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class TimedPhase { IDLE, COUNTDOWN, RUNNING, ENTER_REPS, ENTER_ROUNDS }

/**
 * Bodyweight / cardio / running / CrossFit benchmark session flows.
 *
 * CrossFit:
 * - For Time (Fran, Grace, ...): stopwatch + Bitir
 * - Cindy AMRAP: 20:00 countdown, then enter rounds
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RecordTimedAttemptSession(
    attempt: RecordAttempt,
    initialSets: List<RecordAttemptSet>,
    exercise: Exercise,
    measureType: RecordMeasureType,
    catalogId: String?,
    categoryId: String?,
    onNavigateBack: () -> Unit,
    onNavigateToSummary: (SummaryResult, List<RecordAttemptSet>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isRunningMode = categoryId == "running" || (catalogId?.startsWith("run_") == true)
    val isBodyweight = categoryId == "bodyweight" ||
        (measureType == RecordMeasureType.REPS && categoryId != "benchmark")
    val isBenchmark = categoryId == "benchmark"
    val isCindyAmrap = RecordAttemptCategories.isAmrapCatalog(catalogId) ||
        exercise.name.equals("Cindy", ignoreCase = true)
    val amrapCapSeconds = remember(catalogId) {
        RecordAttemptCategories.amrapCapSeconds(catalogId).takeIf { it > 0 } ?: (20 * 60)
    }
    val amrapCapMs = amrapCapSeconds * 1000L
    val targetKm = remember(catalogId) {
        RecordAttemptService.runningTargetKm(catalogId ?: "")
    }

    var phase by remember { mutableStateOf(TimedPhase.IDLE) }
    var countdown by remember { mutableIntStateOf(3) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var isFinishing by remember { mutableStateOf(false) }
    var hasAutoFinished by remember { mutableStateOf(false) }
    var showAbandon by remember { mutableStateOf(false) }
    var showRepsDialog by remember { mutableStateOf(false) }
    var showRoundsDialog by remember { mutableStateOf(false) }
    var repsInput by remember { mutableStateOf("") }
    var roundsInput by remember { mutableStateOf("") }
    var sets by remember { mutableStateOf(initialSets.sortedBy { it.setIndex }) }

    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    val gpsDistance by LocationTrackingService.distanceKm.collectAsState()
    val gpsPace by LocationTrackingService.paceMinPerKm.collectAsState()
    val gpsSpeed by LocationTrackingService.currentSpeedKmh.collectAsState()

    val remainingMs = (amrapCapMs - elapsedMs).coerceAtLeast(0L)

    // Stopwatch / AMRAP countdown ticker
    LaunchedEffect(phase, isRunningMode, isCindyAmrap) {
        if (phase != TimedPhase.RUNNING || isRunningMode) return@LaunchedEffect
        val start = System.currentTimeMillis() - elapsedMs
        while (phase == TimedPhase.RUNNING) {
            elapsedMs = System.currentTimeMillis() - start
            if (isCindyAmrap && elapsedMs >= amrapCapMs) {
                elapsedMs = amrapCapMs
                break
            }
            delay(50)
        }
        if (isCindyAmrap && elapsedMs >= amrapCapMs && phase == TimedPhase.RUNNING && !hasAutoFinished) {
            hasAutoFinished = true
            phase = TimedPhase.ENTER_ROUNDS
            showRoundsDialog = true
            roundsInput = ""
        }
    }

    // Countdown 3-2-1
    LaunchedEffect(phase) {
        if (phase != TimedPhase.COUNTDOWN) return@LaunchedEffect
        countdown = 3
        while (countdown > 0) {
            delay(1000)
            countdown -= 1
        }
        phase = TimedPhase.RUNNING
        elapsedMs = 0L
        hasAutoFinished = false
        if (isRunningMode) {
            try {
                LocationTrackingService.init(context.applicationContext)
                LocationTrackingService.startTracking(enableAutoPause = true)
            } catch (_: Exception) {
            }
        }
    }

    // Auto-finish run when distance reached
    LaunchedEffect(gpsDistance, phase, isRunningMode, hasAutoFinished, isFinishing) {
        if (!isRunningMode || phase != TimedPhase.RUNNING || hasAutoFinished || isFinishing) return@LaunchedEffect
        if (gpsDistance + 0.008 >= targetKm) {
            hasAutoFinished = true
            val secs = (elapsedMs / 1000.0).roundToInt().coerceAtLeast(1)
            finishTimed(
                success = true,
                elapsedSeconds = secs,
                repsOverride = null,
                attempt = attempt,
                sets = sets,
                measureType = measureType,
                isBodyweight = false,
                isRunningMode = true,
                isAmrap = false,
                onSets = { sets = it },
                onNavigateToSummary = onNavigateToSummary,
                setFinishing = { isFinishing = it },
                stopGps = true
            )
        }
    }

    // Running elapsed ticker
    LaunchedEffect(phase, isRunningMode) {
        if (!isRunningMode || phase != TimedPhase.RUNNING) return@LaunchedEffect
        val start = System.currentTimeMillis()
        while (phase == TimedPhase.RUNNING) {
            elapsedMs = System.currentTimeMillis() - start
            delay(200)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                if (LocationTrackingService.isTracking.value) {
                    LocationTrackingService.stopTracking()
                }
            } catch (_: Exception) {
            }
        }
    }

    fun startFlow() {
        if (isRunningMode && !locationPermission.status.isGranted) {
            locationPermission.launchPermissionRequest()
            return
        }
        phase = TimedPhase.COUNTDOWN
    }

    fun onStopPressed() {
        if (phase != TimedPhase.RUNNING || isFinishing) return
        when {
            isBodyweight -> {
                phase = TimedPhase.ENTER_REPS
                showRepsDialog = true
                repsInput = ""
            }
            isCindyAmrap -> {
                phase = TimedPhase.ENTER_ROUNDS
                showRoundsDialog = true
                roundsInput = ""
            }
            else -> {
                scope.launch {
                    finishTimed(
                        success = true,
                        elapsedSeconds = (elapsedMs / 1000.0).roundToInt().coerceAtLeast(1),
                        repsOverride = null,
                        attempt = attempt,
                        sets = sets,
                        measureType = measureType,
                        isBodyweight = false,
                        isRunningMode = isRunningMode,
                        isAmrap = false,
                        onSets = { sets = it },
                        onNavigateToSummary = onNavigateToSummary,
                        setFinishing = { isFinishing = it },
                        stopGps = isRunningMode
                    )
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (phase == TimedPhase.RUNNING || phase == TimedPhase.COUNTDOWN) {
                            showAbandon = true
                        } else onNavigateBack()
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                }
                Text(
                    exercise.name,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    maxLines = 1
                )
                IconButton(
                    onClick = { showAbandon = true },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Icon(Icons.Default.Close, null)
                }
            }

            when (phase) {
                TimedPhase.IDLE -> IdleStartPane(
                    isRunning = isRunningMode,
                    isBodyweight = isBodyweight,
                    isCindyAmrap = isCindyAmrap,
                    isBenchmark = isBenchmark,
                    targetLabel = when {
                        isRunningMode -> "%.2f km hedef".format(targetKm)
                        isCindyAmrap -> "AMRAP ${amrapCapSeconds / 60} dk — bitince tur gir"
                        isBodyweight -> "Maksimum tekrar"
                        isBenchmark -> "For Time — bitirince Bitir'e bas"
                        measureType == RecordMeasureType.CALORIES ->
                            "Hedef ~${attempt.targetWeight.roundToInt()} cal — sureyi bitir"
                        else -> "Sureyi kendin takip et, bitirince durdur"
                    },
                    needsLocation = isRunningMode && !locationPermission.status.isGranted,
                    onStart = { startFlow() },
                    onRequestLocation = { locationPermission.launchPermissionRequest() }
                )

                TimedPhase.COUNTDOWN -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (countdown > 0) "$countdown" else "BASLA",
                        fontSize = 96.sp,
                        fontWeight = FontWeight.Black,
                        color = Primary
                    )
                }

                TimedPhase.RUNNING, TimedPhase.ENTER_REPS, TimedPhase.ENTER_ROUNDS -> {
                    when {
                        isRunningMode -> RunningActivePane(
                            distanceKm = gpsDistance,
                            targetKm = targetKm,
                            elapsedMs = elapsedMs,
                            pace = gpsPace,
                            speed = gpsSpeed,
                            isFinishing = isFinishing,
                            onFinish = { onStopPressed() }
                        )
                        isCindyAmrap -> AmrapCountdownPane(
                            remainingMs = remainingMs,
                            totalMs = amrapCapMs,
                            isFinishing = isFinishing,
                            onFinishEarly = { onStopPressed() }
                        )
                        else -> TimerActivePane(
                            elapsedMs = elapsedMs,
                            subtitle = when {
                                isBodyweight -> "Tekrarlarını say, bitirince durdur"
                                isBenchmark -> "WOD bitince Bitir'e bas — For Time"
                                else -> "İşin bitince süreyi durdur"
                            },
                            isFinishing = isFinishing,
                            onFinish = { onStopPressed() }
                        )
                    }
                }
            }
        }
    }

    if (showRepsDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Kaç tekrar yaptın?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Süre: ${formatDuration((elapsedMs / 1000.0).roundToInt())}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = repsInput,
                        onValueChange = { repsInput = it.filter { ch -> ch.isDigit() }.take(4) },
                        label = { Text("Tekrar") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val reps = repsInput.toIntOrNull() ?: return@TextButton
                        if (reps <= 0) return@TextButton
                        showRepsDialog = false
                        scope.launch {
                            finishTimed(
                                success = true,
                                elapsedSeconds = (elapsedMs / 1000.0).roundToInt().coerceAtLeast(1),
                                repsOverride = reps,
                                attempt = attempt,
                                sets = sets,
                                measureType = measureType,
                                isBodyweight = true,
                                isRunningMode = false,
                                isAmrap = false,
                                onSets = { sets = it },
                                onNavigateToSummary = onNavigateToSummary,
                                setFinishing = { isFinishing = it },
                                stopGps = false
                            )
                        }
                    },
                    enabled = (repsInput.toIntOrNull() ?: 0) > 0 && !isFinishing
                ) {
                    if (isFinishing) {
                        CircularProgressIndicator(color = Primary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    } else Text("Kaydet", color = Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRepsDialog = false
                    phase = TimedPhase.RUNNING
                }) { Text("Geri") }
            }
        )
    }

    if (showRoundsDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Kaç tur yaptın?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Cindy AMRAP ${amrapCapSeconds / 60} dk. Tamamladığın tur sayısını gir.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                    OutlinedTextField(
                        value = roundsInput,
                        onValueChange = { roundsInput = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text("Tur") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val rounds = roundsInput.toIntOrNull() ?: return@TextButton
                        if (rounds < 0) return@TextButton
                        showRoundsDialog = false
                        scope.launch {
                            finishTimed(
                                success = true,
                                elapsedSeconds = amrapCapSeconds,
                                repsOverride = rounds,
                                attempt = attempt,
                                sets = sets,
                                measureType = RecordMeasureType.REPS,
                                isBodyweight = false,
                                isRunningMode = false,
                                isAmrap = true,
                                onSets = { sets = it },
                                onNavigateToSummary = onNavigateToSummary,
                                setFinishing = { isFinishing = it },
                                stopGps = false
                            )
                        }
                    },
                    enabled = roundsInput.toIntOrNull() != null && !isFinishing
                ) {
                    if (isFinishing) {
                        CircularProgressIndicator(color = Primary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    } else Text("Kaydet", color = Primary)
                }
            }
        )
    }

    if (showAbandon) {
        AlertDialog(
            onDismissRequest = { showAbandon = false },
            title = { Text("Denemeyi Birak") },
            text = { Text("Bu denemeyi birakmak istedigine emin misin?") },
            confirmButton = {
                TextButton(onClick = {
                    showAbandon = false
                    try {
                        if (LocationTrackingService.isTracking.value) LocationTrackingService.stopTracking()
                    } catch (_: Exception) {
                    }
                    scope.launch {
                        try {
                            RecordAttemptService.abandonAttempt(attempt.id)
                        } catch (_: Exception) {
                        }
                        onNavigateBack()
                    }
                }) { Text("Birak", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showAbandon = false }) { Text("Iptal") }
            }
        )
    }
}

@Composable
private fun IdleStartPane(
    isRunning: Boolean,
    isBodyweight: Boolean,
    isCindyAmrap: Boolean = false,
    isBenchmark: Boolean = false,
    targetLabel: String,
    needsLocation: Boolean,
    onStart: () -> Unit,
    onRequestLocation: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            when {
                isRunning -> Icons.Default.Flag
                isCindyAmrap -> Icons.Default.Timer
                else -> Icons.Default.Timer
            },
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            targetLabel,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                 isBodyweight -> "Başlat → 3-2-1 → kronometre. Bitirince tekrar gir."
                 isCindyAmrap -> "Başlat → 3-2-1 → 20:00 geri sayım. Süre bitince tur sayısını gir."
                 isBenchmark -> "Başlat → 3-2-1 → kronometre (For Time). WOD bitince Bitir."
                 isRunning -> "GPS ile mesafe takip edilir. Hedefe ulaşınca otomatik biter."
                 else -> "Kronometre sen bitirene kadar çalışır."
            },
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(28.dp))
        if (needsLocation) {
            Text(
                "Konum izni gerekli",
                color = Color(0xFFFF9800),
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedButton(onClick = onRequestLocation) { Text("İzin Ver") }
            Spacer(Modifier.height(12.dp))
        }
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) {
            Icon(Icons.Default.PlayArrow, null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("Başlat", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 17.sp)
        }
    }
}

@Composable
private fun TimerActivePane(
    elapsedMs: Long,
    subtitle: String,
    isFinishing: Boolean,
    onFinish: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            formatStopwatch(elapsedMs),
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onFinish,
            enabled = !isFinishing,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
        ) {
            if (isFinishing) {
                CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Stop, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Bitir", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 17.sp)
            }
        }
    }
}

@Composable
private fun AmrapCountdownPane(
    remainingMs: Long,
    totalMs: Long,
    isFinishing: Boolean,
    onFinishEarly: () -> Unit
) {
    val progress = if (totalMs > 0) (1f - remainingMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f) else 0f
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "AMRAP",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Primary,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            formatStopwatch(remainingMs),
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            color = if (remainingMs < 60_000) Color(0xFFE53935) else MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp)),
            color = Primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Kalan sure — bitince tur sayisini gireceksin",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(36.dp))
        OutlinedButton(
            onClick = onFinishEarly,
            enabled = !isFinishing,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = CircleShape
        ) {
            Text("Erken Bitir (tur gir)", fontWeight = FontWeight.SemiBold, color = Primary)
        }
    }
}

@Composable
private fun RunningActivePane(
    distanceKm: Double,
    targetKm: Double,
    elapsedMs: Long,
    pace: Double,
    speed: Double,
    isFinishing: Boolean,
    onFinish: () -> Unit
) {
    val progress = (distanceKm / targetKm).toFloat().coerceIn(0f, 1f)
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            formatStopwatch(elapsedMs),
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp)),
            color = Primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text(
            "%.2f / %.2f km".format(distanceKm, targetKm),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatChip("Tempo", if (pace > 0) "%.1f /km".format(pace) else "—")
            StatChip("Hiz", if (speed > 0) "%.1f km/s".format(speed) else "—")
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onFinish,
            enabled = !isFinishing,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
        ) {
            if (isFinishing) {
                CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Stop, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Manuel Bitir", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        Text(
            "Hedefe ulaşınca otomatik biter · pes edersen manuel bitir",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatStopwatch(ms: Long): String {
    val totalSec = (ms / 1000).toInt().coerceAtLeast(0)
    val centi = ((ms % 1000) / 10).toInt()
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d.%02d".format(m, s, centi)
}

private suspend fun finishTimed(
    success: Boolean,
    elapsedSeconds: Int,
    repsOverride: Int?,
    attempt: RecordAttempt,
    sets: List<RecordAttemptSet>,
    measureType: RecordMeasureType,
    isBodyweight: Boolean,
    isRunningMode: Boolean,
    isAmrap: Boolean = false,
    onSets: (List<RecordAttemptSet>) -> Unit,
    onNavigateToSummary: (SummaryResult, List<RecordAttemptSet>) -> Unit,
    setFinishing: (Boolean) -> Unit,
    stopGps: Boolean
) {
    if (stopGps) {
        try {
            LocationTrackingService.stopTracking()
        } catch (_: Exception) {
        }
    }
    setFinishing(true)
    val main = sets.firstOrNull { it.setTypeRaw == "main" } ?: sets.firstOrNull()
    val weight: Double
    val reps: Int
    val prType: RecordMeasureType
    when {
        isAmrap -> {
            // weight = time cap seconds, reps = rounds (higher better)
            weight = elapsedSeconds.toDouble()
            reps = repsOverride ?: 0
            prType = RecordMeasureType.REPS
        }
        isBodyweight -> {
            weight = 0.0
            reps = repsOverride ?: 0
            prType = RecordMeasureType.REPS
        }
        isRunningMode || measureType == RecordMeasureType.TIME -> {
            weight = elapsedSeconds.toDouble()
            reps = 1
            prType = RecordMeasureType.TIME
        }
        measureType == RecordMeasureType.CALORIES -> {
            weight = attempt.targetWeight
            reps = elapsedSeconds
            prType = RecordMeasureType.CALORIES
        }
        else -> {
            weight = attempt.targetWeight
            reps = repsOverride ?: 1
            prType = measureType
        }
    }

    val updated = sets.map { s ->
        if (main != null && s.id == main.id) {
            s.copy(
                actualWeight = weight,
                actualReps = reps,
                restSeconds = elapsedSeconds,
                isCompleted = true
            )
        } else s
    }
    onSets(updated)

    var newPR = false
    try {
        if (main != null) {
            RecordAttemptService.saveSet(
                setId = main.id,
                actualWeight = weight,
                actualReps = reps,
                rpe = null,
                restSeconds = elapsedSeconds,
                notes = if (isAmrap) "AMRAP rounds=$reps" else null
            )
        }
        RecordAttemptService.completeAttempt(
            attemptId = attempt.id,
            success = success,
            notes = null,
            userId = attempt.userId
        )
        if (success) {
            try {
                ResultsService.upsertExerciseResult(
                    userId = attempt.userId,
                    exerciseId = attempt.exerciseId,
                    weight = weight,
                    reps = reps
                )
            } catch (_: Exception) {
            }
            newPR = try {
                RecordAttemptService.updatePersonalRecordIfNeeded(
                    userId = attempt.userId,
                    exerciseId = attempt.exerciseId,
                    weight = weight,
                    reps = reps,
                    notes = if (isAmrap) "AMRAP" else null,
                    measureType = prType
                )
            } catch (_: Exception) {
                false
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.e("RecordTimed", "finish error", e)
    } finally {
        setFinishing(false)
    }
    onNavigateToSummary(
        SummaryResult(
            success = success,
            newPersonalRecord = newPR,
            overrideMeasureType = prType,
            overrideWeight = weight,
            overrideReps = reps,
            overrideDurationSeconds = elapsedSeconds
        ),
        updated
    )
}
