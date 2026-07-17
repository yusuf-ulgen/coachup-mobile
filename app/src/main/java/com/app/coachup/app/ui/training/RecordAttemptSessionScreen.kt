package com.app.coachup.app.ui.training

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.app.coachup.app.models.Exercise
import com.app.coachup.app.models.RecordMeasureType
import com.app.coachup.app.services.AttemptSetType
import com.app.coachup.app.services.PlateCalculator
import com.app.coachup.app.services.RecordAttempt
import com.app.coachup.app.services.RecordAttemptService
import com.app.coachup.app.services.RecordAttemptSet
import com.app.coachup.app.services.ResultsService
import com.app.coachup.app.services.SummaryResult
import com.app.coachup.app.theme.Primary
import com.app.coachup.app.theme.Purple100
import com.app.coachup.app.ui.training.components.CircularRestTimer
import com.app.coachup.app.ui.training.components.PlateVisualView
import com.app.coachup.app.ui.training.components.RPESlider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordAttemptSessionScreen(
    attempt: RecordAttempt,
    initialSets: List<RecordAttemptSet>,
    exercise: Exercise,
    measureType: RecordMeasureType = RecordMeasureType.WEIGHT,
    catalogId: String? = null,
    categoryId: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToSummary: (SummaryResult, List<RecordAttemptSet>) -> Unit
) {
    // Non-strength modes: bodyweight stopwatch, cardio timer, running GPS
    val useTimedMode = measureType != RecordMeasureType.WEIGHT
    if (useTimedMode) {
        RecordTimedAttemptSession(
            attempt = attempt,
            initialSets = initialSets,
            exercise = exercise,
            measureType = measureType,
            catalogId = catalogId,
            categoryId = categoryId,
            onNavigateBack = onNavigateBack,
            onNavigateToSummary = onNavigateToSummary
        )
        return
    }

    var sets by remember { mutableStateOf(initialSets.sortedBy { it.setIndex }.toMutableList()) }
    var currentIndex by remember {
        mutableIntStateOf(initialSets.sortedBy { it.setIndex }.indexOfFirst { !it.isCompleted }.coerceAtLeast(0))
    }
    var savedWeights by remember { mutableStateOf<Map<Int, Double>>(emptyMap()) }

    var showRestTimer by remember { mutableStateOf(false) }
    var restTotal by remember { mutableIntStateOf(60) }

    var showRPESheet by remember { mutableStateOf(false) }
    var pendingRPE by remember { mutableIntStateOf(7) }
    var pendingActualWeight by remember { mutableDoubleStateOf(0.0) }
    var pendingActualReps by remember { mutableIntStateOf(1) }
    var pendingNotes by remember { mutableStateOf("") }

    var showReadyCheck by remember { mutableStateOf(false) }
    var readyCheckIsMain by remember { mutableStateOf(false) }
    var readyCheckCount by remember { mutableIntStateOf(0) }
    var showAbandonAlert by remember { mutableStateOf(false) }
    var isFinishingMain by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val currentSet = sets.getOrNull(currentIndex)
    val isOnMainSet = currentSet?.setType == AttemptSetType.MAIN
    val isBarbell = measureType == RecordMeasureType.WEIGHT &&
        (exercise.equipment ?: "").lowercase().contains("bar")
    val usesWarmupFlow = sets.any { it.setType == AttemptSetType.WARMUP }

    // Flame pulse animation
    val flamePulse = rememberInfiniteTransition(label = "flame")
    val flameScale by flamePulse.animateFloat(
        initialValue = 0.94f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "flameScale"
    )

    // Background color
    val rootBg = MaterialTheme.colorScheme.background
    val bgColor by animateColorAsState(
        targetValue = if (isOnMainSet) Color(0xFF120500) else rootBg,
        animationSpec = tween(500), label = "bg"
    )

    fun prefillFromCurrent() {
        currentSet?.let { cs ->
            pendingActualWeight = cs.prescribedWeight ?: 0.0
            pendingActualReps = cs.prescribedReps ?: 1
            pendingRPE = if (cs.setType == AttemptSetType.WARMUP) 5 else 8
            pendingNotes = ""
        }
    }

    fun adjustRemainingWeights(delta: Double) {
        val step = 2.5
        val updated = sets.toMutableList()
        for (i in (currentIndex + 1) until updated.size) {
            if (updated[i].isCompleted) continue
            val current = updated[i].prescribedWeight ?: 0.0
            val adjusted = maxOf(0.0, (Math.round((current + delta) / step) * step))
            updated[i] = updated[i].copy(prescribedWeight = adjusted)
        }
        sets = updated
    }

    fun skipToMainSet() {
        val mainIndex = sets.indexOfFirst { it.setType == AttemptSetType.MAIN }
        if (mainIndex < 0) return
        currentIndex = mainIndex
        showRestTimer = false
        showReadyCheck = false
        showRPESheet = false
        prefillFromCurrent()
    }

    fun applyRPEAdjustment(rpe: Int, actualWeight: Double) {
        val cs = sets.getOrNull(currentIndex) ?: return
        val updated = sets.toMutableList()

        if (cs.setType == AttemptSetType.WARMUP) {
            if (rpe <= 3 && currentIndex + 1 < updated.size && !updated[currentIndex + 1].isCompleted) {
                val nextPlanned = updated[currentIndex + 1].prescribedWeight ?: 0.0
                val mid = (actualWeight + nextPlanned) / 2.0
                val rounded = PlateCalculator.roundWeight(mid)
                updated[currentIndex + 1] = updated[currentIndex + 1].copy(
                    prescribedWeight = maxOf(rounded, actualWeight)
                )
            }
        } else {
            val isFrozen = savedWeights.isNotEmpty()
            if (rpe >= 7) {
                val newSaved = if (savedWeights.isEmpty()) {
                    val m = mutableMapOf<Int, Double>()
                    for (i in (currentIndex + 1) until updated.size) {
                        if (!updated[i].isCompleted) m[updated[i].setIndex] = updated[i].prescribedWeight ?: 0.0
                    }
                    m
                } else savedWeights
                savedWeights = newSaved
                for (i in (currentIndex + 1) until updated.size) {
                    if (!updated[i].isCompleted) updated[i] = updated[i].copy(prescribedWeight = actualWeight)
                }
            } else if (isFrozen && rpe <= 3) {
                for (i in updated.indices) {
                    if (!updated[i].isCompleted) {
                        val orig = savedWeights[updated[i].setIndex]
                        if (orig != null) updated[i] = updated[i].copy(prescribedWeight = orig)
                    }
                }
                savedWeights = emptyMap()
            }
        }
        sets = updated
    }

    fun finishAttempt(explicitSuccess: Boolean?, localSets: List<RecordAttemptSet> = sets) {
        val success = explicitSuccess ?: run {
            val mains = localSets.filter { it.setType == AttemptSetType.MAIN }
            mains.isNotEmpty() && mains.all { s -> (s.actualReps ?: 0) >= (s.prescribedReps ?: 1) }
        }
        val completedMain = localSets.firstOrNull { it.setType == AttemptSetType.MAIN && it.isCompleted }
        val recordWeight = completedMain?.actualWeight ?: attempt.targetWeight
        val recordReps = completedMain?.actualReps ?: attempt.targetReps

        scope.launch {
            var newPR = false
            try {
                // Run completeAttempt and PR updates concurrently to speed up response time
                val completeJob = async {
                    RecordAttemptService.completeAttempt(
                        attemptId = attempt.id,
                        success = success,
                        notes = null,
                        userId = attempt.userId
                    )
                }

                val prJob = if (success) {
                    async {
                        try {
                            ResultsService.upsertExerciseResult(
                                userId = attempt.userId,
                                exerciseId = attempt.exerciseId,
                                weight = recordWeight,
                                reps = recordReps
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {}

                        try {
                            RecordAttemptService.updatePersonalRecordIfNeeded(
                                userId = attempt.userId,
                                exerciseId = attempt.exerciseId,
                                weight = recordWeight,
                                reps = recordReps,
                                notes = null,
                                measureType = measureType
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            false
                        }
                    }
                } else null

                completeJob.await()
                newPR = prJob?.await() ?: false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("RecordAttemptSession", "finishAttempt error", e)
            } finally {
                isFinishingMain = false
            }
            onNavigateToSummary(SummaryResult(success = success, newPersonalRecord = newPR), localSets)
        }
    }

    fun advanceOrFinish(restSecs: Int) {
        if (currentIndex >= sets.size - 1) {
            finishAttempt(null)
            return
        }
        currentIndex += 1
        readyCheckCount = 0
        prefillFromCurrent()
        val nextSet = sets.getOrNull(currentIndex)
        if (nextSet?.setType == AttemptSetType.MAIN) {
            readyCheckIsMain = true
            showReadyCheck = true
        } else {
            restTotal = restSecs
            showRestTimer = true
        }
    }

    fun completeCurrentSet() {
        val cs = currentSet ?: return
        val restSecs = if (cs.setType == AttemptSetType.WARMUP) 60 else 180
        val capturedWeight = pendingActualWeight
        val capturedReps = pendingActualReps
        val capturedRPE = pendingRPE
        val capturedNotes = pendingNotes.ifEmpty { null }

        scope.launch {
            try {
                RecordAttemptService.saveSet(
                    setId = cs.id,
                    actualWeight = capturedWeight,
                    actualReps = capturedReps,
                    rpe = capturedRPE,
                    restSeconds = restSecs,
                    notes = capturedNotes
                )
                val updated = sets.toMutableList()
                val idx = updated.indexOfFirst { it.id == cs.id }
                if (idx >= 0) {
                    updated[idx] = updated[idx].copy(
                        actualWeight = capturedWeight,
                        actualReps = capturedReps,
                        rpe = capturedRPE,
                        restSeconds = restSecs,
                        isCompleted = true,
                        notes = capturedNotes
                    )
                }
                sets = updated
                applyRPEAdjustment(capturedRPE, capturedWeight)
                showRPESheet = false
                advanceOrFinish(restSecs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("RecordAttemptSession", "saveSet error", e)
            }
        }
    }

    fun completeMainAttempt(success: Boolean) {
        if (isFinishingMain) return
        val cs = currentSet ?: return
        isFinishingMain = true
        val w = cs.prescribedWeight ?: attempt.targetWeight
        val r = if (success) (cs.prescribedReps ?: 1) else 0

        // Optimistic local update so UI feels instant
        val updated = sets.toMutableList()
        val idx = updated.indexOfFirst { it.id == cs.id }
        if (idx >= 0) {
            updated[idx] = updated[idx].copy(
                actualWeight = w,
                actualReps = r,
                isCompleted = true
            )
        }
        sets = updated

        scope.launch {
            try {
                RecordAttemptService.saveSet(
                    setId = cs.id,
                    actualWeight = w,
                    actualReps = r,
                    rpe = null,
                    restSeconds = 0,
                    notes = null
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("RecordAttemptSession", "completeMainAttempt save error", e)
            }
            finishAttempt(success, updated)
        }
    }

    // ----- Composable UI -----

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {

        // Floating flame decorations on main set
        if (isOnMainSet) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopEnd
            ) {
                Icon(
                    Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.06f),
                    modifier = Modifier
                        .size((180 * flameScale).dp)
                        .offset(x = 40.dp, y = (-20).dp)
                )
            }
        }

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(top = 8.dp, bottom = 12.dp)
            ) {
                val iconBg = if (isOnMainSet) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                val iconFg = if (isOnMainSet) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurface
                val titleColor = if (isOnMainSet) Color.White else MaterialTheme.colorScheme.onSurface

                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(iconBg)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = iconFg)
                }
                Text(
                    text = exercise.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 52.dp)
                        .fillMaxWidth()
                )
                IconButton(
                    onClick = { showAbandonAlert = true },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(iconBg)
                ) {
                    Icon(Icons.Filled.Close, null, tint = iconFg)
                }
            }

            // Progress strip
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                sets.forEachIndexed { idx, s ->
                    val color = when {
                        s.isCompleted -> Primary
                        idx == currentIndex -> Purple100
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(color)
                    )
                    // Divider between warmup and main
                    if (idx < sets.size - 1 && s.setType == AttemptSetType.WARMUP && sets[idx + 1].setType == AttemptSetType.MAIN) {
                        Box(modifier = Modifier.width(2.dp).height(10.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)))
                    }
                }
            }

            if (!isOnMainSet && usesWarmupFlow) {
                TextButton(
                    onClick = { skipToMainSet() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Text("Isınmayı atla, rekora geç", color = Primary, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hero card
                if (isOnMainSet) {
                    MainHeroCard(
                        currentSet = currentSet,
                        attempt = attempt,
                        measureType = measureType,
                        flameScale = flameScale,
                        currentPosition = currentPosition(sets, currentIndex)
                    )
                } else {
                    WarmupHeroCard(
                        currentSet = currentSet,
                        attempt = attempt,
                        measureType = measureType,
                        currentPosition = currentPosition(sets, currentIndex)
                    )
                }

                // Plate card
                if (isBarbell && currentSet != null) {
                    PlateCard(weight = currentSet.prescribedWeight ?: 0.0)
                }

                // Next up strip (warmup only)
                if (!isOnMainSet && usesWarmupFlow) {
                    NextUpStrip(
                        sets = sets,
                        currentIndex = currentIndex,
                        onAdjust = { adjustRemainingWeights(it) }
                    )
                }

                Spacer(Modifier.height(120.dp))
            }
        }

        // CTA buttons
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            val ctaBg = MaterialTheme.colorScheme.background
            val gradColors = if (isOnMainSet)
                listOf(Color.Black.copy(alpha = 0f), Color(0xFF140800))
            else
                listOf(ctaBg.copy(alpha = 0f), ctaBg)

            Box(
                modifier = Modifier.fillMaxWidth().height(140.dp)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(gradColors))
            )

            if (isOnMainSet) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .offset(y = (-20).dp)
                        .align(Alignment.BottomCenter),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { completeMainAttempt(false) },
                        enabled = !isFinishingMain,
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.85f))
                    ) {
                        if (isFinishingMain) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Yapamadım", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    Button(
                        onClick = { completeMainAttempt(true) },
                        enabled = !isFinishingMain,
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        if (isFinishingMain) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.LocalFireDepartment, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Yaptım!", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            } else {
                Button(
                    onClick = {
                        prefillFromCurrent()
                        showRPESheet = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 20.dp)
                        .offset(y = (-20).dp)
                        .align(Alignment.BottomCenter),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Nasıl Geçti?", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // Rest timer overlay
        if (showRestTimer) {
            CircularRestTimer(
                totalSeconds = restTotal,
                onFinish = {
                    showRestTimer = false
                    readyCheckIsMain = false
                    readyCheckCount += 1
                    showReadyCheck = true
                },
                onSkip = {
                    showRestTimer = false
                    readyCheckIsMain = false
                    readyCheckCount += 1
                    showReadyCheck = true
                }
            )
        }

        // Ready check overlay
        if (showReadyCheck) {
            ReadyCheckOverlay(
                isMain = readyCheckIsMain,
                attempt = attempt,
                flameScale = flameScale,
                readyCheckCount = readyCheckCount,
                onReady = { showReadyCheck = false },
                onMoreRest = {
                    showReadyCheck = false
                    restTotal = 30
                    showRestTimer = true
                    readyCheckCount += 1
                }
            )
        }
    }

    // Abandon alert
    if (showAbandonAlert) {
        AlertDialog(
            onDismissRequest = { showAbandonAlert = false },
            title = { Text("Denemeyi Bırak") },
            text = { Text("Bu denemeyi bırakmak istediğine emin misin?") },
            confirmButton = {
                TextButton(onClick = {
                    showAbandonAlert = false
                    scope.launch {
                        try { RecordAttemptService.abandonAttempt(attempt.id) } catch (_: Exception) {}
                        onNavigateBack()
                    }
                }) { Text("Denemeyi Bırak", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showAbandonAlert = false }) { Text("İptal") }
            }
        )
    }

    // RPE modal dialog
    if (showRPESheet) {
        Dialog(
            onDismissRequest = { showRPESheet = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                RPESheetContent(
                    rpe = pendingRPE,
                    onRpeChange = { pendingRPE = it },
                    actualWeight = pendingActualWeight,
                    onWeightChange = { pendingActualWeight = it },
                    actualReps = pendingActualReps,
                    onRepsChange = { pendingActualReps = it },
                    notes = pendingNotes,
                    onNotesChange = { pendingNotes = it },
                    onComplete = { completeCurrentSet() }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Warmup Hero Card
// ---------------------------------------------------------------------------

@Composable
private fun WarmupHeroCard(
    currentSet: RecordAttemptSet?,
    attempt: RecordAttempt,
    measureType: RecordMeasureType,
    currentPosition: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Purple100)
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                currentPosition.uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.6f),
                letterSpacing = 0.8.sp
            )
            if (currentSet != null) {
                val (primary, unit) = heroPrimaryValue(
                    measureType,
                    currentSet.prescribedWeight ?: 0.0,
                    currentSet.prescribedReps ?: 1
                )
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        primary,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (unit.isNotBlank()) {
                        Text(
                            unit,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    if (measureType == RecordMeasureType.WEIGHT) {
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "× ${currentSet.prescribedReps ?: 1}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
                if (measureType == RecordMeasureType.WEIGHT) {
                    val pct = (currentSet.prescribedWeight ?: 0.0) / maxOf(attempt.targetWeight, 0.01)
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Primary.copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "%${(pct * 100).toInt()} hedeften",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Primary
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Main Hero Card (fire theme)
// ---------------------------------------------------------------------------

@Composable
private fun MainHeroCard(
    currentSet: RecordAttemptSet?,
    attempt: RecordAttempt,
    measureType: RecordMeasureType,
    flameScale: Float,
    currentPosition: String
) {
    val (primary, unit) = heroPrimaryValue(
        measureType,
        currentSet?.prescribedWeight ?: attempt.targetWeight,
        currentSet?.prescribedReps ?: attempt.targetReps
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Flame cluster
        Row(
            horizontalArrangement = Arrangement.spacedBy((-4).dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Icon(Icons.Filled.LocalFireDepartment, null,
                tint = Color(0xFFFF9800).copy(alpha = 0.6f),
                modifier = Modifier.size((24 * (2f - flameScale)).dp))
            Icon(Icons.Filled.LocalFireDepartment, null,
                tint = Color(0xFFFFFFFF),
                modifier = Modifier.size((48 * flameScale).dp))
            Icon(Icons.Filled.LocalFireDepartment, null,
                tint = Color(0xFFFF9800).copy(alpha = 0.6f),
                modifier = Modifier.size((24 * (2f - flameScale)).dp))
        }

        // Weight circle
        Box(contentAlignment = Alignment.Center) {
            // Outer ring
            Box(
                modifier = Modifier
                    .size((186 + (flameScale - 1f) * 20).dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0x33FF8000), Color(0x22FF0000))
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .size(172.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFF331400), Color(0xFF0D0300))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        primary,
                        fontSize = if (primary.length > 6) 40.sp else 52.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    if (unit.isNotBlank()) {
                        Text(unit, fontSize = 14.sp, color = Color.White.copy(alpha = 0.35f))
                    }
                }
            }
        }

        // Label
        Text(
            currentPosition.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = Color.White.copy(alpha = 0.5f),
            letterSpacing = 2.sp
        )
    }
}

// ---------------------------------------------------------------------------
// Plate Card
// ---------------------------------------------------------------------------

@Composable
private fun PlateCard(weight: Double) {
    val load = PlateCalculator.calculate(weight)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.FitnessCenter, null, tint = Primary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Halter (20kg)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text("${formatWeight(load.totalWeight)} kg", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            PlateVisualView(load = load, modifier = Modifier.fillMaxWidth())
            if (load.perSide.isNotEmpty()) {
                Text(
                    "Her tarafa: " + load.perSide.sortedByDescending { it.weight }
                        .joinToString(" + ") { "${it.count}×${formatWeight(it.weight)}" },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Next Up Strip
// ---------------------------------------------------------------------------

@Composable
private fun NextUpStrip(
    sets: List<RecordAttemptSet>,
    currentIndex: Int,
    onAdjust: (Double) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Primary.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                val next = sets.getOrNull(currentIndex + 1)
                Text(
                    text = if (next != null)
                        "Sonraki: ${formatWeight(next.prescribedWeight ?: 0.0)}kg × ${next.prescribedReps ?: 1}"
                    else
                        "Son set",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (next != null) MaterialTheme.colorScheme.onSurfaceVariant else Primary
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Kalan setleri ayarla", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                for ((label, delta, col) in listOf(
                    Triple("-5", -5.0, Color.Red),
                    Triple("-2.5", -2.5, Color(0xFFFF9800)),
                    Triple("+2.5", 2.5, Primary),
                    Triple("+5", 5.0, Primary)
                )) {
                    Surface(
                        onClick = { onAdjust(delta) },
                        shape = RoundedCornerShape(8.dp),
                        color = col.copy(alpha = 0.08f)
                    ) {
                        Text(
                            label,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = col
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Ready Check Overlay
// ---------------------------------------------------------------------------

@Composable
private fun ReadyCheckOverlay(
    isMain: Boolean,
    attempt: RecordAttempt,
    flameScale: Float,
    readyCheckCount: Int,
    onReady: () -> Unit,
    onMoreRest: () -> Unit
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isMain)
                        Brush.verticalGradient(listOf(Color(0xFF120500), Color(0xFF220A00), Color(0xFF000000)))
                    else
                        Brush.verticalGradient(listOf(Color(0xF5000000), Color(0xEF000000)))
                )
        ) {
            if (isMain) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(Modifier.height(60.dp))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy((-4).dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Icon(Icons.Filled.LocalFireDepartment, null,
                                tint = Color(0xFFFF9800).copy(alpha = 0.6f),
                                modifier = Modifier.size((36 * (2f - flameScale)).dp))
                            Icon(Icons.Filled.LocalFireDepartment, null,
                                tint = Color.White,
                                modifier = Modifier.size((64 * flameScale).dp))
                            Icon(Icons.Filled.LocalFireDepartment, null,
                                tint = Color(0xFFFF9800).copy(alpha = 0.6f),
                                modifier = Modifier.size((36 * (2f - flameScale)).dp))
                        }
                        Box(
                            modifier = Modifier
                                .size(220.dp)
                                .clip(CircleShape)
                                .background(Brush.radialGradient(listOf(Color(0xFF331400), Color(0xFF050000)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    formatWeight(attempt.targetWeight),
                                    fontSize = 64.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                                Text("kg", fontSize = 17.sp, color = Color.White.copy(alpha = 0.35f))
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Text("ANA DENEME", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.4f), letterSpacing = 4.sp)
                        Text("Bu anı bekliyordun.", fontSize = 15.sp, color = Color.White.copy(alpha = 0.38f))
                    }

                    Spacer(Modifier.height(1.dp))

                    Button(
                        onClick = onReady,
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(listOf(Color(0xFFFF8C00), Color(0xFFCC0000))),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Evet, Hazırım!", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    Spacer(Modifier.height(40.dp))
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(Modifier.height(1.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Filled.Bolt, null,
                        tint = Primary,
                        modifier = Modifier.size((56 * flameScale).dp))
                        Text("Hazır mısın?", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Button(
                            onClick = onReady,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = Primary)
                        ) {
                            Text("Evet, Hazırım!", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        if (readyCheckCount < 2) {
                            TextButton(
                                onClick = onMoreRest,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "30 Saniye Daha",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                        Spacer(Modifier.height(40.dp))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// RPE Sheet Content
// ---------------------------------------------------------------------------

@Composable
private fun RPESheetContent(
    rpe: Int,
    onRpeChange: (Int) -> Unit,
    actualWeight: Double,
    onWeightChange: (Double) -> Unit,
    actualReps: Int,
    onRepsChange: (Int) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    onComplete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Seti nasıl hissettin?", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

        RPESlider(value = rpe, onValueChange = onRpeChange)

        val fieldColors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedBorderColor = Primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            cursorColor = Primary
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("GERÇEK AĞIRLIK", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.5.sp)
                OutlinedTextField(
                    value = if (actualWeight == 0.0) "" else formatWeight(actualWeight),
                    onValueChange = { v ->
                        v.toDoubleOrNull()?.let { onWeightChange(maxOf(0.0, it)) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                    suffix = { Text("kg", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("GERÇEK TEKRAR", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.5.sp)
                OutlinedTextField(
                    value = if (actualReps == 0) "" else actualReps.toString(),
                    onValueChange = { v ->
                        v.toIntOrNull()?.let { onRepsChange(maxOf(0, it)) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                    suffix = { Text("tekrar", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("NOT", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.5.sp)
            OutlinedTextField(
                value = notes,
                onValueChange = onNotesChange,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
                placeholder = { Text("İsteğe bağlı...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                shape = RoundedCornerShape(10.dp)
            )
        }

        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) {
            Text("Tamamla", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun heroPrimaryValue(
    type: RecordMeasureType,
    value: Double,
    reps: Int
): Pair<String, String> = when (type) {
    RecordMeasureType.WEIGHT -> Pair(formatWeight(value), "kg")
    RecordMeasureType.REPS -> Pair("$reps", "tekrar")
    RecordMeasureType.TIME -> Pair(formatDuration(value.toInt()), "")
    RecordMeasureType.CALORIES -> Pair("${value.toInt()}", "cal")
    RecordMeasureType.DISTANCE -> {
        if (value >= 1.0) Pair("%.1f".format(value), "km")
        else Pair("${(value * 1000).toInt()}", "m")
    }
}

private fun currentPosition(sets: List<RecordAttemptSet>, currentIndex: Int): String {
    val cs = sets.getOrNull(currentIndex) ?: return ""
    return if (cs.setType == AttemptSetType.WARMUP) {
        val warmups = sets.filter { it.setType == AttemptSetType.WARMUP }
        val idx = warmups.indexOfFirst { it.id == cs.id }
        "Isınma ${idx + 1}/${warmups.size}"
    } else {
        val mains = sets.filter { it.setType == AttemptSetType.MAIN }
        val idx = mains.indexOfFirst { it.id == cs.id }
        "Ana Deneme ${idx + 1}/${mains.size}"
    }
}
