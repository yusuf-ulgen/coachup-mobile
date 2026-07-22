package com.app.coachup.app.ui.training

import android.Manifest
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.PermissionController
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.app.coachup.app.models.*
import com.app.coachup.app.services.AuthService
import com.app.coachup.app.services.HealthConnectService
import com.app.coachup.app.services.LocationTrackingService
import com.app.coachup.app.services.StreakService
import com.app.coachup.app.services.WorkoutService
import androidx.compose.ui.platform.LocalContext
import com.app.coachup.app.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import com.app.coachup.app.services.ActiveWorkoutManager
import com.app.coachup.app.config.SupabaseConfig
import io.github.jan.supabase.postgrest.from
import java.time.Instant

// ---------------------------------------------------------------------------
// Summary data — replaces the old Triple so GPS fields can be passed through
// ---------------------------------------------------------------------------

data class WorkoutCompletionData(
    val durationSeconds: Int,
    val completedSets: Int,
    val totalSets: Int,
    val distanceKm: Double = 0.0,
    val avgPaceMinPerKm: Double = 0.0,
    val avgSpeedKmh: Double = 0.0,
    val altitudeGainM: Double = 0.0,
    val splits: List<KmSplit> = emptyList(),
    val routePoints: List<Pair<Double, Double>> = emptyList(),
    // Health Connect metrics — null when no wearable
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val calories: Int? = null,
    // Post-workout feeling
    val perceivedEffort: String? = null
)

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class ActiveWorkoutViewModel : ViewModel() {

    private val _programExercises = MutableStateFlow<List<ProgramExercise>>(emptyList())
    val programExercises: StateFlow<List<ProgramExercise>> = _programExercises.asStateFlow()

    private val _workoutSets = MutableStateFlow<List<WorkoutSet>>(emptyList())
    val workoutSets: StateFlow<List<WorkoutSet>> = _workoutSets.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    private val _showRestTimer = MutableStateFlow(false)
    val showRestTimer: StateFlow<Boolean> = _showRestTimer.asStateFlow()

    private val _restSecondsRemaining = MutableStateFlow(0)
    val restSecondsRemaining: StateFlow<Int> = _restSecondsRemaining.asStateFlow()

    private val _currentExerciseIndex = MutableStateFlow(0)
    val currentExerciseIndex: StateFlow<Int> = _currentExerciseIndex.asStateFlow()

    private val _editingSetId = MutableStateFlow<String?>(null)
    val editingSetId: StateFlow<String?> = _editingSetId.asStateFlow()

    val editReps = MutableStateFlow("")
    val editWeight = MutableStateFlow("")

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _completed = MutableStateFlow(false)
    val completed: StateFlow<Boolean> = _completed.asStateFlow()

    private var restTimerJob: Job? = null

    val completedSetsCount get() = _workoutSets.value.count { it.isCompleted }
    val totalSetsCount get() = _workoutSets.value.size

    val overallProgress: Double
        get() = if (totalSetsCount == 0) 0.0 else completedSetsCount.toDouble() / totalSetsCount

    fun currentSets(): List<WorkoutSet> {
        val exercises = _programExercises.value
        val idx = _currentExerciseIndex.value
        if (idx >= exercises.size) return emptyList()
        val exerciseId = exercises[idx].exerciseId
        return _workoutSets.value
            .filter { it.exerciseId == exerciseId }
            .sortedBy { it.setNumber }
    }

    fun currentExercise(): ProgramExercise? {
        val exercises = _programExercises.value
        val idx = _currentExerciseIndex.value
        return if (idx < exercises.size) exercises[idx] else null
    }

    private suspend fun checkAndInsertMissingExercises(userId: String, programId: String) {
        val client = SupabaseConfig.client
        // 1. Check if "Warm-up - Light Cardio" exists
        val existingCardio = client.from("exercises").select {
            filter { eq("name", "Warm-up - Light Cardio") }
        }.decodeList<Exercise>()

        val cardioId = if (existingCardio.isEmpty()) {
            val newId = java.util.UUID.randomUUID().toString()
            val newCardio = Exercise(
                id = newId,
                name = "Warm-up - Light Cardio",
                category = "cardio",
                equipment = "Treadmill",
                description = "5 minutes light cardio to warm up."
            )
            client.from("exercises").insert(newCardio)
            newId
        } else {
            existingCardio.first().id
        }

        val existingCooldown = client.from("exercises").select {
            filter { eq("name", "Cool-down Stretch") }
        }.decodeList<Exercise>()

        val cooldownId = if (existingCooldown.isEmpty()) {
            val newId = java.util.UUID.randomUUID().toString()
            val newCooldown = Exercise(
                id = newId,
                name = "Cool-down Stretch",
                category = "stretching",
                equipment = "Bodyweight",
                description = "Static stretches for chest, shoulders, back, and legs."
            )
            client.from("exercises").insert(newCooldown)
            newId
        } else {
            existingCooldown.first().id
        }

        // 2. Fetch all program exercises for the active program
        val currentPE = WorkoutService.fetchExercises(programId)
        val currentOrderIndexes = currentPE.map { it.orderIndex }

        val targetInserts = mutableListOf<ProgramExercise>()

        // Helper to check and add
        fun addIfMissing(orderIndex: Int, exerciseId: String, sets: Int, reps: Int, restSeconds: Int, notes: String) {
            if (orderIndex !in currentOrderIndexes) {
                targetInserts.add(
                    ProgramExercise(
                        id = java.util.UUID.randomUUID().toString(),
                        programId = programId,
                        exerciseId = exerciseId,
                        sets = sets,
                        reps = reps,
                        restSeconds = restSeconds,
                        orderIndex = orderIndex,
                        notes = notes,
                        createdAt = java.time.Instant.now().toString()
                    )
                )
            }
        }

        if (programId == "faa92b2c-2a26-41e1-a763-4f5402242888") {
            // Ahmet Balaban Antrenman Programı
            val yogaId = "73fff0b1-340c-4d4e-92e4-01a85137ae9f"
            val kurekId = "2a93bb71-9ae5-479a-9f72-20a719907388"
            val foamId = "50586c31-4428-4b8f-88c7-edada71d460b"
            val bisikletId = "e24e77f3-4eed-43ab-8072-f6925ed97f35"

            // Day 1
            addIfMissing(101, cardioId, 1, 1, 0, "Day 1 | Warm-up - Light Cardio (5 min) — 5 minutes light treadmill or bike to elevate heart rate and prepare joints.")
            addIfMissing(102, yogaId, 1, 5, 0, "Day 1 | Yoga Sun Salutation (5 reps) — Dynamic mobility for shoulders and back. Prepare upper body for heavy pulling movements.")
            addIfMissing(105, kurekId, 4, 8, 90, "Day 1 | Kürek Çekme (Barbell Row) (4x8-10 · rest 90s) — Drive elbows back, retract scapula fully. Keep bar close to body, chest up throughout.")
            addIfMissing(106, foamId, 1, 1, 0, "Day 1 | Foam Roll - Sırt (Back) (2 min) — Release tension in lats and thoracic spine. Improves mobility for next session.")

            // Day 2
            addIfMissing(200, cardioId, 1, 1, 0, "Day 2 | Warm-up - Light Cardio (5 min) — 5 minutes light treadmill or bike to warm up entire body.")
            addIfMissing(204, yogaId, 1, 3, 0, "Day 2 | Yoga Sun Salutation (3 reps) — Cool-down with dynamic stretching for chest and shoulders. Promote recovery.")

            // Day 3
            addIfMissing(300, cardioId, 1, 1, 0, "Day 3 | Warm-up - Light Cardio (5 min) — 5 minutes bike or treadmill to prepare lower body joints.")
            addIfMissing(305, bisikletId, 1, 1, 0, "Day 3 | Bisiklet Fartlek (Bike Intervals) (1x10 min total · rest -) — 3 min easy, then 6x(30s hard/30s easy). Active recovery finisher targeting cardiovascular endurance.")
        } else if (programId == "4e5ee1cb-c9ee-44ae-96d7-fc00a7b25e5a" || programId == "a9502421-fb90-4526-8089-ace1bd670bf4") {
            // Ali Öztürk Antrenman Programı
            val benchId = "4448257e-00bc-47dc-ad4d-a8077635be8c"
            val shoulderId = "f582aff8-d031-4d63-9c30-121a96680fb1"
            val kurekId = "2a93bb71-9ae5-479a-9f72-20a719907388"
            val plankId = "60fbac8c-433d-4484-ac1c-069a05129f69"
            val pullupId = "7fc786a1-49e1-4aee-a6b4-57c06b7f1cb1"
            val deadliftId = "702deb94-327b-48c4-8888-005b6befb68e"
            val rdlId = "011de9f1-ef1e-410c-bb38-6c16f5f405db"
            val foamId = "50586c31-4428-4b8f-88c7-edada71d460b"
            val squatId = "f91ed3ca-7520-4475-8c95-eced766cfe20"
            val boxjumpId = "bcb90587-bff7-45f1-9e78-4e60cb63af03"
            val battleropeId = "80fbe411-6355-4ee3-bea1-a8589ac60da4"

            // Day 1
            addIfMissing(101, cardioId, 1, 1, 0, "Day 1 | Warm-up - Light Cardio (5 min)")
            addIfMissing(102, benchId, 4, 8, 90, "Day 1 | Bench Press (4x8-10 · rest 90s)")
            addIfMissing(103, shoulderId, 3, 10, 75, "Day 1 | Shoulder Press (3x10-12 · rest 75s)")
            addIfMissing(104, kurekId, 3, 8, 90, "Day 1 | Kürek Çekme (Barbell Row) (3x8-10 · rest 90s)")
            addIfMissing(105, plankId, 3, 30, 60, "Day 1 | Plank (3x30-45s · rest 60s)")
            addIfMissing(106, cooldownId, 1, 1, 0, "Day 1 | Cool-down Stretch (5 min)")

            // Day 2
            addIfMissing(201, cardioId, 1, 1, 0, "Day 2 | Warm-up - Light Cardio (5 min)")
            addIfMissing(202, pullupId, 4, 6, 90, "Day 2 | Pull-Up (4x6-10 · rest 90s)")
            addIfMissing(203, deadliftId, 3, 6, 120, "Day 2 | Deadlift (3x6-8 · rest 120s)")
            addIfMissing(204, rdlId, 3, 10, 75, "Day 2 | Romanian Deadlift (3x10-12 · rest 75s)")
            addIfMissing(205, foamId, 1, 1, 0, "Day 2 | Foam Roll - Sırt (Back) (1x2 min)")
            addIfMissing(206, cooldownId, 1, 1, 0, "Day 2 | Cool-down Stretch (5 min)")

            // Day 3
            addIfMissing(301, cardioId, 1, 1, 0, "Day 3 | Warm-up - Light Cardio (5 min)")
            addIfMissing(302, squatId, 4, 8, 90, "Day 3 | Squat (4x8-10 · rest 90s)")
            addIfMissing(303, boxjumpId, 3, 6, 90, "Day 3 | Box Jump (3x6-8 · rest 90s)")
            addIfMissing(304, battleropeId, 3, 30, 90, "Day 3 | Battle Rope (3x30s · rest 90s)")
            addIfMissing(305, plankId, 3, 30, 60, "Day 3 | Plank (3x30-45s · rest 60s)")
            addIfMissing(306, cooldownId, 1, 1, 0, "Day 3 | Cool-down Stretch (5 min)")
        }

        if (targetInserts.isNotEmpty()) {
            for (pe in targetInserts) {
                client.from("program_exercises").insert(pe)
            }
        }
    }

    fun loadWorkout(sessionId: String, programId: String, userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val migratePrograms = listOf(
                    "faa92b2c-2a26-41e1-a763-4f5402242888",
                    "4e5ee1cb-c9ee-44ae-96d7-fc00a7b25e5a",
                    "a9502421-fb90-4526-8089-ace1bd670bf4"
                )
                if (programId in migratePrograms) {
                    runCatching { checkAndInsertMissingExercises(userId, programId) }
                }

                if (programId.startsWith("builtin_")) {
                    _programExercises.value = emptyList()
                    _workoutSets.value = emptyList()
                    _isLoading.value = false
                    return@launch
                }

                val exercises = WorkoutService.fetchExercises(programId)
                if (exercises.isEmpty()) {
                    _programExercises.value = emptyList()
                    _workoutSets.value = emptyList()
                    _isLoading.value = false
                    return@launch
                }

                val existing = WorkoutService.fetchWorkoutSets(sessionId)
                if (existing.isNotEmpty()) {
                    val existingExerciseIds = existing.map { it.exerciseId }.distinct()
                    _programExercises.value = exercises.filter { it.exerciseId in existingExerciseIds }
                    _workoutSets.value = existing
                } else {
                    val days = exercises.map { it.orderIndex / 100 }.filter { it > 0 }.distinct()
                    if (days.size > 1) {
                        _programExercises.value = exercises
                        _workoutSets.value = emptyList()
                    } else {
                        _programExercises.value = exercises
                        WorkoutService.createWorkoutSet(sessionId, userId, exercises)
                        _workoutSets.value = WorkoutService.fetchWorkoutSets(sessionId)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "Antrenman yüklenemedi"
            }
            _isLoading.value = false
        }
    }

    suspend fun startWorkoutForDay(sessionId: String, userId: String, dayNumber: Int) {
        _isLoading.value = true
        try {
            val allExercises = _programExercises.value
            val filtered = allExercises.filter { (it.orderIndex / 100) == dayNumber }
            if (filtered.isNotEmpty()) {
                WorkoutService.createWorkoutSet(sessionId, userId, filtered)
                _programExercises.value = filtered
                _workoutSets.value = WorkoutService.fetchWorkoutSets(sessionId)
            }
        } catch (_: Exception) {
            _error.value = "Antrenman başlatılamadı"
        }
        _isLoading.value = false
    }

    fun initSession(training: Training, sessionId: String) {
        val session = ActiveWorkoutManager.activeSession.value
        if (session != null && session.sessionId == sessionId) {
            viewModelScope.launch {
                session.elapsedSeconds.collect { _elapsedSeconds.value = it }
            }
            viewModelScope.launch {
                session.isPaused.collect { _isPaused.value = it }
            }
        } else {
            ActiveWorkoutManager.startSession(training, sessionId)
            val newSession = ActiveWorkoutManager.activeSession.value
            if (newSession != null) {
                viewModelScope.launch {
                    newSession.elapsedSeconds.collect { _elapsedSeconds.value = it }
                }
                viewModelScope.launch {
                    newSession.isPaused.collect { _isPaused.value = it }
                }
            }
        }
    }

    fun startTimer() {
        ActiveWorkoutManager.startTimer()
    }

    fun stopTimer() {
        ActiveWorkoutManager.pauseTimer()
    }

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    fun pauseTimer() {
        ActiveWorkoutManager.pauseTimer()
    }

    fun resumeTimer() {
        ActiveWorkoutManager.resumeTimer()
    }

    /** Mirrors iOS togglePause(): pauses the elapsed timer. */
    fun togglePause() {
        ActiveWorkoutManager.togglePause()
    }

    /** Mirrors iOS "Set Tamamla" → triggerRestTimer() for the HR-only strength view. */
    fun triggerRest(seconds: Int = 60) {
        startRestTimer(seconds)
    }

    fun beginEditing(set: WorkoutSet) {
        _editingSetId.value = set.id
        editReps.value = set.reps.toString()
        editWeight.value = set.weight?.let { String.format("%.1f", it) } ?: ""
    }

    fun completeSet(set: WorkoutSet, sessionId: String) {
        val reps = editReps.value.toIntOrNull() ?: set.reps
        val weight = editWeight.value.toDoubleOrNull()
        _editingSetId.value = null

        viewModelScope.launch {
            try {
                WorkoutService.completeWorkoutSet(set.id, reps, weight)
                _workoutSets.value = _workoutSets.value.map { s ->
                    if (s.id == set.id) s.copy(reps = reps, weight = weight, isCompleted = true)
                    else s
                }
                // Trigger rest timer if more sets remain in this exercise
                val remaining = currentSets().filter { !it.isCompleted && it.id != set.id }
                if (remaining.isNotEmpty()) {
                    val restSecs = currentExercise()?.restSeconds ?: 60
                    startRestTimer(restSecs)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "Set tamamlanamadı"
            }
        }
    }

    fun completeWorkout(
        sessionId: String,
        userId: String,
        activityType: String = "workout",
        durationMinutes: Int? = null,
        completionNotes: String? = null,
        durationSeconds: Int? = null,
        distanceKm: Double? = null,
        avgHeartRate: Int? = null,
        maxHeartRate: Int? = null,
        calories: Int? = null,
        avgPace: Double? = null,
        avgSpeed: Double? = null,
        altitudeGain: Double? = null,
        perceivedEffort: String? = null,
        onDone: (durationSeconds: Int, completedSets: Int, totalSets: Int) -> Unit
    ) {
        onDone(_elapsedSeconds.value, completedSetsCount, totalSetsCount)
        viewModelScope.launch {
            try {
                WorkoutService.completeSession(
                    sessionId = sessionId,
                    notes = completionNotes,
                    durationSeconds = durationSeconds,
                    distanceKm = distanceKm,
                    avgHeartRate = avgHeartRate,
                    maxHeartRate = maxHeartRate,
                    calories = calories,
                    avgPace = avgPace,
                    avgSpeed = avgSpeed,
                    altitudeGain = altitudeGain,
                    perceivedEffort = perceivedEffort
                )
                try {
                    StreakService.recordActivityAndSync(
                        userId = userId,
                        activityType = activityType,
                        durationMinutes = durationMinutes
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {}
                _completed.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "Antrenman tamamlanamadı"
            }
        }
    }

    fun setCurrentExerciseIndex(index: Int) {
        _currentExerciseIndex.value = index
        _editingSetId.value = null
    }

    fun dismissRestTimer() {
        restTimerJob?.cancel()
        _showRestTimer.value = false
    }

    private fun startRestTimer(seconds: Int) {
        restTimerJob?.cancel()
        _restSecondsRemaining.value = seconds
        _showRestTimer.value = true
        restTimerJob = viewModelScope.launch {
            while (_restSecondsRemaining.value > 0) {
                delay(1000)
                _restSecondsRemaining.value--
            }
            _showRestTimer.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        restTimerJob?.cancel()
    }
}

// ---------------------------------------------------------------------------
// Helper extensions
// ---------------------------------------------------------------------------

private fun WorkoutSet.copy(
    reps: Int = this.reps,
    weight: Double? = this.weight,
    isCompleted: Boolean = this.isCompleted
) = WorkoutSet(
    id = this.id,
    sessionId = this.sessionId,
    exerciseId = this.exerciseId,
    userId = this.userId,
    setNumber = this.setNumber,
    reps = reps,
    weight = weight,
    isCompleted = isCompleted,
    restSeconds = this.restSeconds,
    notes = this.notes,
    completedAt = this.completedAt,
    createdAt = this.createdAt
)

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ActiveWorkoutScreen(
    training: Training,
    sessionId: String,
    onNavigateBack: () -> Unit,
    vm: ActiveWorkoutViewModel = viewModel()
) {
    val isOutdoor = training.category.isOutdoor
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Sync state with ActiveWorkoutManager
    val isSessionAlreadyActive = remember(sessionId) {
        ActiveWorkoutManager.activeSession.value?.sessionId == sessionId
    }
    LaunchedEffect(sessionId) {
        vm.initSession(training, sessionId)
    }

    // ── Workout goal sheet (outdoor only) ─────────────────────────────────────
    var showGoalSheet by remember { mutableStateOf(isOutdoor && !isSessionAlreadyActive) }
    var workoutSessionStarted by remember { mutableStateOf(isSessionAlreadyActive) }

    // ── Salon / AI program önizlemesi — sayaç başlamadan hareket listesi ───────
    val needsProgramPreview = !training.isBuiltIn
    var showProgramPreview by remember { mutableStateOf(needsProgramPreview && !isSessionAlreadyActive) }
    var timerStarted by remember { mutableStateOf(isSessionAlreadyActive) }
    var workoutGoal by remember { mutableStateOf<WorkoutGoal>(WorkoutGoal.None) }

    // ── Summary data — includes GPS fields ────────────────────────────────────
    var summaryData by remember { mutableStateOf<WorkoutCompletionData?>(null) }

    // ── Location permission ────────────────────────────────────────────────────
    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    // ── GPS state (always collected — safe to read even when not tracking) ────
    val gpsDistance by LocationTrackingService.distanceKm.collectAsState()
    val gpsPace by LocationTrackingService.paceMinPerKm.collectAsState()
    val gpsSpeed by LocationTrackingService.averageSpeedKmh.collectAsState()
    val gpsAltitude by LocationTrackingService.altitudeGainM.collectAsState()
    val gpsSignalWeak by LocationTrackingService.isGPSSignalWeak.collectAsState()
    val gpsIsPaused by LocationTrackingService.isPaused.collectAsState()
    val gpsIsAutoPaused by LocationTrackingService.isAutoPaused.collectAsState()
    val gpsRoutePoints by LocationTrackingService.routePoints.collectAsState()
    val gpsCurrentSpeed by LocationTrackingService.currentSpeedKmh.collectAsState()
    val gpsSplits by LocationTrackingService.splits.collectAsState()
    var showGoalSheetDuringWorkout by remember { mutableStateOf(false) }
    var showFinishCelebration by remember { mutableStateOf(false) }
    var pendingSummaryData by remember { mutableStateOf<WorkoutCompletionData?>(null) }

    val handleClose = {
        if (workoutSessionStarted && !showFinishCelebration) {
            ActiveWorkoutManager.showOverlay()
            onNavigateBack()
        } else {
            ActiveWorkoutManager.stopSession()
            onNavigateBack()
        }
    }

    BackHandler {
        handleClose()
    }

    // ── Show summary when complete ─────────────────────────────────────────────
    summaryData?.let { data ->
        WorkoutSummaryScreen(
            training = training,
            durationSeconds = data.durationSeconds,
            avgHeartRate = data.avgHeartRate,
            maxHeartRate = data.maxHeartRate,
            calories = data.calories,
            distanceKm = data.distanceKm,
            avgPaceMinPerKm = data.avgPaceMinPerKm,
            avgSpeedKmh = data.avgSpeedKmh,
            altitudeGainM = data.altitudeGainM,
            splits = data.splits,
            routePoints = data.routePoints,
            completedSets = data.completedSets,
            totalSets = data.totalSets,
            perceivedEffort = data.perceivedEffort,
            onDismiss = {
                summaryData = null
                onNavigateBack()
            }
        )
        return
    }

    val isAvailable by HealthConnectService.isAvailable.collectAsState()
    val liveHR by HealthConnectService.currentHeartRate.collectAsState()
    val avgHR by HealthConnectService.averageHeartRate.collectAsState()
    val maxHR by HealthConnectService.maxHeartRate.collectAsState()

    val hcPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { /* İzin verilmezse nabız 0 kalır */ }

    LaunchedEffect(isAvailable) {
        if (!isAvailable) return@LaunchedEffect
        if (!HealthConnectService.hasAllPermissions()) {
            hcPermissionLauncher.launch(HealthConnectService.permissions)
        }
    }

    LaunchedEffect(isAvailable) {
        if (!isAvailable) return@LaunchedEffect
        while (true) {
            HealthConnectService.refreshHeartRate()
            delay(12_000)
        }
    }

    val userId by AuthService.currentUser.collectAsState(initial = null)
    val programExercises by vm.programExercises.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val elapsedSeconds by vm.elapsedSeconds.collectAsState()
    val isPaused by vm.isPaused.collectAsState()
    val showRestTimer by vm.showRestTimer.collectAsState()
    val restSecondsRemaining by vm.restSecondsRemaining.collectAsState()
    val currentIndex by vm.currentExerciseIndex.collectAsState()
    val editingSetId by vm.editingSetId.collectAsState()
    val editReps by vm.editReps.collectAsState()
    val editWeight by vm.editWeight.collectAsState()

    var showCompleteDialog by remember { mutableStateOf(false) }
    var showEffortDialog by remember { mutableStateOf(false) }

    fun confirmFinishWorkout(effort: String? = null) {
        showCompleteDialog = false
        showEffortDialog = false
        val duration = elapsedSeconds
        val dist = if (isOutdoor) LocationTrackingService.distanceKm.value else 0.0
        val completionNotes = SessionWorkoutMeta.encodeCompletionNotes(
            categoryDbValue = if (training.isBuiltIn) training.category.dbValue else null,
            durationSeconds = duration,
            distanceKm = dist
        )
        // Capture Health Connect values — null if unavailable or no wearable (<= 0)
        val hcAvail = HealthConnectService.isAvailable.value
        val hcAvgHR = if (hcAvail) HealthConnectService.averageHeartRate.value.takeIf { it > 0 } else null
        val hcMaxHR = if (hcAvail) HealthConnectService.maxHeartRate.value.takeIf { it > 0 } else null

        if (isOutdoor) LocationTrackingService.stopForegroundTracking(context)

        coroutineScope.launch {
            var hcCalories: Int? = null
            if (hcAvail) {
                val endTime = Instant.now()
                val startTime = endTime.minusSeconds(duration.toLong())
                hcCalories = HealthConnectService.fetchCaloriesBurned(startTime, endTime)
            }
            val finalCalories = hcCalories?.takeIf { it > 0 } ?: ((duration / 60) * 7).coerceAtLeast(1)

            pendingSummaryData = WorkoutCompletionData(
                durationSeconds = duration,
                completedSets = vm.completedSetsCount,
                totalSets = vm.totalSetsCount,
                distanceKm = dist,
                avgPaceMinPerKm = if (isOutdoor) LocationTrackingService.paceMinPerKm.value else 0.0,
                avgSpeedKmh = if (isOutdoor) LocationTrackingService.averageSpeedKmh.value else 0.0,
                altitudeGainM = if (isOutdoor) LocationTrackingService.altitudeGainM.value else 0.0,
                splits = if (isOutdoor) LocationTrackingService.splits.value else emptyList(),
                routePoints = if (isOutdoor) LocationTrackingService.routePoints.value else emptyList(),
                avgHeartRate = hcAvgHR,
                maxHeartRate = hcMaxHR,
                calories = finalCalories,
                perceivedEffort = effort
            )
            showFinishCelebration = true

            val uid = userId?.id ?: return@launch
            vm.completeWorkout(
                sessionId = sessionId,
                userId = uid,
                activityType = training.category.dbValue,
                durationMinutes = (duration / 60).coerceAtLeast(1),
                completionNotes = completionNotes,
                durationSeconds = duration,
                distanceKm = if (isOutdoor) dist else null,
                avgHeartRate = hcAvgHR,
                maxHeartRate = hcMaxHR,
                calories = finalCalories,
                avgPace = if (isOutdoor) LocationTrackingService.paceMinPerKm.value.takeIf { it > 0.0 } else null,
                avgSpeed = if (isOutdoor) LocationTrackingService.averageSpeedKmh.value.takeIf { it > 0.0 } else null,
                altitudeGain = if (isOutdoor) LocationTrackingService.altitudeGainM.value.takeIf { it > 0.0 } else null,
                perceivedEffort = effort
            ) { _, _, _ -> }
        }
    }

    if (showFinishCelebration) {
        WorkoutFinishCelebration(
            title = training.title,
            durationSeconds = pendingSummaryData?.durationSeconds ?: elapsedSeconds,
            onFinished = {
                summaryData = pendingSummaryData
                pendingSummaryData = null
                showFinishCelebration = false
            }
        )
        return
    }

    val progress by animateFloatAsState(
        targetValue = vm.overallProgress.toFloat(),
        animationSpec = tween(300),
        label = "progress"
    )

    // ── Load workout details exactly once when screen opens / sessionId changes ────
    LaunchedEffect(sessionId, userId?.id) {
        val uid = userId?.id ?: return@LaunchedEffect
        vm.loadWorkout(sessionId, training.id, uid)
    }

    // ── Start timer after goal / preview sheets are dismissed ─────────────────
    // NOTE: For outdoor (GPS) workouts we do NOT start the timer here —
    // it starts only when the user taps "Başlat" in onStartWorkout (see below).
    // For indoor workouts this block handles timer start normally.
    LaunchedEffect(showGoalSheet, showProgramPreview, workoutSessionStarted) {
        if (showGoalSheet || showProgramPreview) return@LaunchedEffect
        if (!workoutSessionStarted) return@LaunchedEffect
        if (!timerStarted && !isOutdoor) {
            vm.startTimer()
            timerStarted = true
        }
    }

    // Konum izni verildikten sonra — yalnızca kullanıcı Başlat dediyse
    LaunchedEffect(locationPermission.status.isGranted, workoutSessionStarted) {
        if (!isOutdoor || !workoutSessionStarted || showGoalSheet) return@LaunchedEffect
        if (locationPermission.status.isGranted && !LocationTrackingService.isTracking.value) {
            LocationTrackingService.startForegroundTracking(context, enableAutoPause = true)
        }
    }

    DisposableEffect(isOutdoor, showFinishCelebration) {
        onDispose {
            if (!showFinishCelebration) {
                if (!ActiveWorkoutManager.showFloatingOverlay.value) {
                    vm.stopTimer()
                    ActiveWorkoutManager.stopSession()
                    if (isOutdoor) LocationTrackingService.stopForegroundTracking(context)
                }
            }
        }
    }

    // ── Goal progress (derived) ────────────────────────────────────────────────
    val goalProgress = remember(workoutGoal, gpsDistance, elapsedSeconds) {
        workoutGoal.progress(gpsDistance, elapsedSeconds)
    }

    // ── Program preview (salon / AI) — sayaç başlamadan önce ───────────────────
    if (showProgramPreview) {
        ProgramPreviewOverlay(
            training = training,
            exercises = programExercises,
            fallbackNames = training.exerciseNames,
            isLoading = isLoading,
            onStart = { selectedDay ->
                coroutineScope.launch {
                    val uid = userId?.id
                    if (uid != null && vm.workoutSets.value.isEmpty()) {
                        val days = programExercises.map { it.orderIndex / 100 }.filter { it > 0 }.distinct()
                        if (days.size > 1) {
                            vm.startWorkoutForDay(sessionId, uid, selectedDay)
                        }
                    }
                    showProgramPreview = false
                    workoutSessionStarted = true
                }
            },
            onBack = onNavigateBack
        )
        return
    }

    // Start Prompt Overlay for Indoor built-in activities
    if (!isOutdoor && !showProgramPreview && !workoutSessionStarted) {
        WorkoutStartPromptOverlay(
            training = training,
            onStart = { workoutSessionStarted = true },
            onBack = {
                ActiveWorkoutManager.stopSession()
                onNavigateBack()
            }
        )
        return
    }

    // ── Main UI — GPS (outdoor) öncelikli, sonra minimal, sonra salon programı ──
    val useMinimalLayout = training.isBuiltIn || programExercises.isEmpty()

    if (isOutdoor && !showGoalSheet) {
        Box(modifier = Modifier.fillMaxSize()) {
            GPSWorkoutLayout(
                training = training,
                elapsedSeconds = elapsedSeconds,
                gpsDistance = gpsDistance,
                gpsPace = gpsPace,
                gpsSpeed = gpsSpeed,
                gpsCurrentSpeed = gpsCurrentSpeed,
                gpsAltitude = gpsAltitude,
                gpsSignalWeak = gpsSignalWeak,
                gpsIsPaused = gpsIsPaused,
                gpsIsAutoPaused = gpsIsAutoPaused,
                awaitingStart = !workoutSessionStarted,
                routePoints = gpsRoutePoints,
                gpsSplits = gpsSplits,
                goal = workoutGoal,
                goalProgress = goalProgress,
                onClose = handleClose,
                onGoalTap = { showGoalSheetDuringWorkout = true },
                onStartWorkout = {
                    workoutSessionStarted = true
                    if (locationPermission.status.isGranted) {
                        // Start the Foreground Service so GPS works when screen is locked
                        LocationTrackingService.startForegroundTracking(context, enableAutoPause = true)
                        if (!timerStarted) {
                            vm.startTimer()
                            timerStarted = true
                        }
                    } else {
                        locationPermission.launchPermissionRequest()
                    }
                },
                onPauseToggle = {
                    if (gpsIsPaused) {
                        LocationTrackingService.resumeTracking()
                        vm.resumeTimer()
                    } else {
                        LocationTrackingService.pauseTracking(manual = true)
                        vm.pauseTimer()
                    }
                },
                onFinishWorkout = { showCompleteDialog = true }
            )
        }
    } else if (useMinimalLayout && !showGoalSheet) {
        val primaryMetric = when {
            (elapsedSeconds / 60) > 0 -> "${(elapsedSeconds / 60) * 8}"
            else -> "--"
        }
        val secondaryMetric = "--"

        Box(modifier = Modifier.fillMaxSize()) {
            MinimalWorkoutLayout(
                elapsedSeconds = elapsedSeconds,
                primaryMetric = primaryMetric,
                secondaryMetric = secondaryMetric,
                isPaused = isPaused,
                onClose = handleClose,
                onPauseToggle = { vm.togglePause() }
            )

            if (showRestTimer) {
                RestTimerOverlay(
                    secondsRemaining = restSecondsRemaining,
                    totalSeconds = vm.currentExercise()?.restSeconds ?: 60,
                    onSkip = { vm.dismissRestTimer() }
                )
            }

            if (isPaused) {
                WorkoutPausedOverlay(
                    onResume = { vm.resumeTimer() },
                    onFinish = { showCompleteDialog = true }
                )
            }
        }
    } else {
        // Strength / non-outdoor layout
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Purple100.copy(alpha = 0.05f), MaterialTheme.colorScheme.background),
                            startY = 0f,
                            endY = 800f
                        )
                    )
            ) {
                // Header
                WorkoutHeader(
                    title = training.title,
                    completedSets = vm.completedSetsCount,
                    totalSets = vm.totalSetsCount,
                    elapsedSeconds = elapsedSeconds,
                    isOutdoor = false,
                    onClose = handleClose
                )

                when {
                    !training.programText.isNullOrBlank() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = Spacing.xl),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            MinifiedStatsHeader(
                                elapsedSeconds = elapsedSeconds,
                                liveHR = liveHR,
                                avgHR = avgHR,
                                maxHR = maxHR,
                                isAvailable = isAvailable
                            )

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(Radius.card),
                                shadowElevation = 2.dp,
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text = "Antrenman Detayları",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    Text(
                                        text = training.programText,
                                        fontSize = 15.sp,
                                        lineHeight = 22.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { vm.togglePause() },
                                    modifier = Modifier.weight(1f).height(50.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isPaused) Color(0xFF4CAF50) else Color(0xFFFF9800)
                                    ),
                                    shape = RoundedCornerShape(Radius.pill)
                                ) {
                                    Text(if (isPaused) "Devam Et" else "Duraklat", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                }

                                Button(
                                    onClick = { showCompleteDialog = true },
                                    modifier = Modifier.weight(1.5f).height(50.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                    shape = RoundedCornerShape(Radius.pill)
                                ) {
                                    Text("Antrenmanı Tamamla", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                }
                            }
                        }
                    }

                    isLoading -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = Primary)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Antrenman hazırlanıyor...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    programExercises.isEmpty() -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Spacer(Modifier.weight(1f))
                            StrengthCenter(
                                bpm = if (isAvailable && liveHR > 0) liveHR else 0,
                                isLive = isAvailable && liveHR > 0,
                                estimatedCalories = 0,
                                exerciseProgress = "—",
                                avgBpm = if (isAvailable && avgHR > 0) avgHR else 0
                            )
                            Spacer(Modifier.weight(1f))
                            StravaStyleWorkoutControls(
                                isPaused = isPaused,
                                onPauseToggle = { vm.togglePause() }
                            )
                        }
                    }

                    else -> {
                        if (training.category.isProgramBased) {
                            // GYM / AI PROGRAM FULL LIST LAYOUT
                            MinifiedStatsHeader(
                                elapsedSeconds = elapsedSeconds,
                                liveHR = liveHR,
                                avgHR = avgHR,
                                maxHR = maxHR,
                                isAvailable = isAvailable
                            )

                            Spacer(Modifier.height(8.dp))

                            LazyColumn(
                                contentPadding = PaddingValues(
                                    start = Spacing.xl,
                                    end = Spacing.xl,
                                    top = 0.dp,
                                    bottom = 120.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(programExercises) { pe ->
                                    val exerciseSets = vm.workoutSets.collectAsState().value.filter { it.exerciseId == pe.exerciseId }
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .shadow(2.dp, RoundedCornerShape(Radius.card))
                                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(Radius.card))
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = pe.exercise?.name ?: "Egzersiz",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (!pe.notes.isNullOrBlank()) {
                                            Text(
                                                text = pe.notes,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)))
                                        SetsCard(
                                            sets = exerciseSets,
                                            editingSetId = editingSetId,
                                            editReps = editReps,
                                            editWeight = editWeight,
                                            onEditRepsChange = { vm.editReps.value = it },
                                            onEditWeightChange = { vm.editWeight.value = it },
                                            onBeginEdit = { vm.beginEditing(it) },
                                            onCompleteSet = { vm.completeSet(it, sessionId) }
                                        )
                                    }
                                }
                            }

                            StravaStyleWorkoutControls(
                                isPaused = isPaused,
                                onPauseToggle = { vm.togglePause() }
                            )

                            Button(
                                onClick = { showCompleteDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.xl, vertical = 12.dp)
                                    .height(52.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                shape = RoundedCornerShape(Radius.pill)
                            ) {
                                Text("Antrenmanı Tamamla", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        } else {
                            // ORIGINAL PAGING LAYOUT
                            if (programExercises.isNotEmpty()) {
                                ExerciseDots(
                                    exercises = programExercises,
                                    currentIndex = currentIndex,
                                    workoutSets = vm.workoutSets.collectAsState().value,
                                    onDotClick = { vm.setCurrentExerciseIndex(it) },
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                            }

                            val currentExercise = vm.currentExercise()
                            val currentSets = vm.currentSets()

                            LazyColumn(
                                contentPadding = PaddingValues(
                                    start = Spacing.xl,
                                    end = Spacing.xl,
                                    top = 0.dp,
                                    bottom = 120.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (programExercises.isNotEmpty()) {
                                    item {
                                        currentExercise?.let {
                                            ExerciseMediaCard(
                                                exercise = it,
                                                exerciseNumber = currentIndex + 1,
                                                totalExercises = programExercises.size
                                            )
                                        }
                                    }
                                    item {
                                        currentExercise?.let {
                                            ExerciseInfoCard(exercise = it)
                                        }
                                    }
                                    item {
                                        SetsCard(
                                            sets = currentSets,
                                            editingSetId = editingSetId,
                                            editReps = editReps,
                                            editWeight = editWeight,
                                            onEditRepsChange = { vm.editReps.value = it },
                                            onEditWeightChange = { vm.editWeight.value = it },
                                            onBeginEdit = { vm.beginEditing(it) },
                                            onCompleteSet = { vm.completeSet(it, sessionId) }
                                        )
                                    }
                                }
                            }

                            StravaStyleWorkoutControls(
                                isPaused = isPaused,
                                onPauseToggle = { vm.togglePause() }
                            )

                            BottomNavigationBar(
                                currentIndex = currentIndex,
                                totalExercises = programExercises.size,
                                completedSets = vm.completedSetsCount,
                                totalSets = vm.totalSetsCount,
                                isOutdoor = false,
                                onPrevious = { vm.setCurrentExerciseIndex(currentIndex - 1) },
                                onNext = { vm.setCurrentExerciseIndex(currentIndex + 1) },
                                onComplete = { showCompleteDialog = true }
                            )
                        }
                    }
                }
            }

            if (showRestTimer) {
                RestTimerOverlay(
                    secondsRemaining = restSecondsRemaining,
                    totalSeconds = vm.currentExercise()?.restSeconds ?: 60,
                    onSkip = { vm.dismissRestTimer() }
                )
            }

            if (isPaused) {
                WorkoutPausedOverlay(
                    onResume = { vm.togglePause() },
                    onFinish = { showCompleteDialog = true }
                )
            }
        }
    }

    // ── Goal sheet overlays ────────────────────────────────────────────────────
    if (showGoalSheet) {
        WorkoutGoalSheet(
            onGoalSelected = { goal -> workoutGoal = goal },
            onDismiss = { showGoalSheet = false }
        )
    }

    if (showGoalSheetDuringWorkout) {
        WorkoutGoalSheet(
            onGoalSelected = { goal -> workoutGoal = goal },
            onDismiss = { showGoalSheetDuringWorkout = false }
        )
    }

    // ── Complete dialog ────────────────────────────────────────────────────────
    if (showCompleteDialog) {
        AlertDialog(
            onDismissRequest = { showCompleteDialog = false },
            title = { Text("Antrenman Tamamla") },
            text = {
                if (isOutdoor && gpsDistance > 0.01) {
                    Text("%.2f km tamamlandı. Antrenmanı bitirmek istiyor musunuz?".format(gpsDistance))
                } else {
                    Text("Antrenmanı bitirmek istiyor musunuz?")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showCompleteDialog = false
                    showEffortDialog = true
                }) { Text("Tamamla", color = Primary) }
            },
            dismissButton = {
                TextButton(onClick = { showCompleteDialog = false }) {
                    Text("İptal", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // ── Perceived Effort dialog ──────────────────────────────────────────────────
    if (showEffortDialog) {
        AlertDialog(
            onDismissRequest = { showEffortDialog = false },
            title = {
                Text(
                    text = "Nasıl Hissediyorsun?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Antrenmanı nasıl tamamladın? Hissettiğin zorluk derecesini seç:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    PerceivedEffort.entries.forEach { effort ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .clickable {
                                    showEffortDialog = false
                                    confirmFinishWorkout(effort.dbValue)
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(text = effort.emoji, fontSize = 24.sp)
                            Text(
                                text = effort.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { showEffortDialog = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("İptal", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Header — iOS style: [←]  BIG TIMER  [🏁]
// ---------------------------------------------------------------------------

@Composable
private fun WorkoutHeader(
    title: String,
    completedSets: Int,
    totalSets: Int,
    elapsedSeconds: Int,
    isOutdoor: Boolean,
    onClose: () -> Unit
) {
    // iOS topBar: plain chevron · big monospaced timer · checkered finish flag.
    // No subtitle, no progress bar, no circular button backgrounds (birebir iOS).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = Spacing.sm, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = "Kapat",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(28.dp)
                .clickable { onClose() }
        )

        Text(
            text = formatTime(elapsedSeconds),
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.width(28.dp))
    }
}

// ---------------------------------------------------------------------------
// GPS stats panel — shows during outdoor workouts
// ---------------------------------------------------------------------------

@Composable
private fun GPSStatsPanel(
    distanceKm: Double,
    paceMinPerKm: Double,
    speedKmh: Double,
    altitudeGainM: Double,
    signalWeak: Boolean,
    autoPaused: Boolean,
    goal: WorkoutGoal,
    goalProgress: Float
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl)
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Signal / auto-pause banners
        if (signalWeak) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFFF3CD))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.GpsNotFixed, contentDescription = null, tint = Color(0xFFB8860B), modifier = Modifier.size(14.dp))
                Text("GPS sinyali zayıf", fontSize = 12.sp, color = Color(0xFFB8860B), fontWeight = FontWeight.Medium)
            }
        }
        if (autoPaused) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Primary.copy(alpha = 0.08f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Pause, contentDescription = null, tint = Primary, modifier = Modifier.size(14.dp))
                Text("Otomatik duraklama", fontSize = 12.sp, color = Primary, fontWeight = FontWeight.Medium)
            }
        }

        // Primary stats row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            GPSStat(
                value = "%.2f".format(distanceKm),
                unit = "km",
                label = "Mesafe"
            )
            GPSStatDivider()
            GPSStat(
                value = formatPace(paceMinPerKm),
                unit = "/km",
                label = "Tempo"
            )
            GPSStatDivider()
            GPSStat(
                value = "%.1f".format(speedKmh),
                unit = "km/s",
                label = "Hız"
            )
            GPSStatDivider()
            GPSStat(
                value = "%.0f".format(altitudeGainM),
                unit = "m",
                label = "Yükseklik"
            )
        }

        // Goal progress bar
        if (goal !is WorkoutGoal.None) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(goal.label(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("%d%%".format((goalProgress * 100).toInt()), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Primary)
                }
                LinearProgressIndicator(
                    progress = { goalProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (goalProgress >= 1f) Color(0xFF4CAF50) else Primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
private fun GPSStat(value: String, unit: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(unit, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
        }
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GPSStatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(32.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    )
}

// ---------------------------------------------------------------------------
// Outdoor focus card — shown when outdoor workout has no program exercises
// ---------------------------------------------------------------------------

@Composable
private fun OutdoorFocusCard(distanceKm: Double, elapsedSeconds: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(listOf(Primary.copy(alpha = 0.08f), MaterialTheme.colorScheme.surface)),
                RoundedCornerShape(20.dp)
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.DirectionsRun,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = "Aktif Seans",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "GPS takip aktif. Antrenmanı tamamlamak için\naşağıdaki butonu kullanın.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

// ---------------------------------------------------------------------------
// Exercise dots
// ---------------------------------------------------------------------------

@Composable
private fun ExerciseDots(
    exercises: List<ProgramExercise>,
    currentIndex: Int,
    workoutSets: List<WorkoutSet>,
    onDotClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        exercises.forEachIndexed { index, pe ->
            val exerciseSets = workoutSets.filter { it.exerciseId == pe.exerciseId }
            val allDone = exerciseSets.isNotEmpty() && exerciseSets.all { it.isCompleted }

            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .then(
                        if (index == currentIndex) {
                            Modifier
                                .width(24.dp)
                                .height(8.dp)
                                .background(Primary, RoundedCornerShape(4.dp))
                        } else {
                            Modifier
                                .size(8.dp)
                                .background(
                                    if (allDone) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    CircleShape
                                )
                        }
                    )
                    .clickable { onDotClick(index) }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Exercise media card
// ---------------------------------------------------------------------------

@Composable
private fun ExerciseMediaCard(
    exercise: ProgramExercise,
    exerciseNumber: Int,
    totalExercises: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(Radius.card))
    ) {
        val imageUrl = exercise.exercise?.imageUrl
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(colors = listOf(Purple100, Purple100.copy(alpha = 0.8f)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(52.dp)
                    )
                    Text(
                        text = exercise.exercise?.name ?: "Egzersiz",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    exercise.exercise?.equipment?.let { equip ->
                        if (equip.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(Radius.pill))
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Build,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = equip,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .background(
                    Color.Black.copy(alpha = 0.4f),
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text = "$exerciseNumber/$totalExercises",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Exercise info card
// ---------------------------------------------------------------------------

@Composable
private fun ExerciseInfoCard(exercise: ProgramExercise) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = exercise.exercise?.name ?: "Egzersiz",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .background(Purple100.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = null,
                        tint = Purple100,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = "${exercise.sets}x${exercise.reps}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Purple100
                    )
                }
                Row(
                    modifier = Modifier
                        .background(Primary.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = "${exercise.restSeconds}s",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                }
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            exercise.exercise?.equipment?.let { equip ->
                if (equip.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(9.dp)
                            )
                            Text(equip, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            items(exercise.exercise?.muscleGroups ?: emptyList()) { muscle ->
                Text(
                    text = muscle.replaceFirstChar { it.uppercase() },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Primary,
                    modifier = Modifier
                        .background(Primary.copy(alpha = 0.06f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        exercise.exercise?.instructions?.let { instructions ->
            if (instructions.isNotEmpty()) {
                Text(
                    text = instructions,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Sets card
// ---------------------------------------------------------------------------

@Composable
private fun SetsCard(
    sets: List<WorkoutSet>,
    editingSetId: String?,
    editReps: String,
    editWeight: String,
    onEditRepsChange: (String) -> Unit,
    onEditWeightChange: (String) -> Unit,
    onBeginEdit: (WorkoutSet) -> Unit,
    onCompleteSet: (WorkoutSet) -> Unit
) {
    val doneCount = sets.count { it.isCompleted }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.FormatListNumbered, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                Text("Setler", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                text = "$doneCount/${sets.size}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (doneCount == sets.size && sets.isNotEmpty()) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("SET", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.width(36.dp))
            Text("TEKRAR", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text("KG", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(modifier = Modifier.width(40.dp))
        }

        sets.forEach { set ->
            SetRow(
                set = set,
                isEditing = editingSetId == set.id,
                editReps = if (editingSetId == set.id) editReps else "",
                editWeight = if (editingSetId == set.id) editWeight else "",
                onEditRepsChange = onEditRepsChange,
                onEditWeightChange = onEditWeightChange,
                onBeginEdit = { onBeginEdit(set) },
                onComplete = { onCompleteSet(set) }
            )
        }
    }
}

@Composable
private fun SetRow(
    set: WorkoutSet,
    isEditing: Boolean,
    editReps: String,
    editWeight: String,
    onEditRepsChange: (String) -> Unit,
    onEditWeightChange: (String) -> Unit,
    onBeginEdit: () -> Unit,
    onComplete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    if (set.isCompleted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (set.isCompleted) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
            } else {
                Text(text = "${set.setNumber}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        if (isEditing) {
            BasicTextField(
                value = editReps,
                onValueChange = onEditRepsChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                ),
                modifier = Modifier
                    .weight(1f)
                    .background(Primary.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
                    .border(1.5.dp, Primary, RoundedCornerShape(10.dp))
                    .padding(vertical = 10.dp, horizontal = 8.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (set.isCompleted) Color(0xFF4CAF50).copy(alpha = 0.06f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f),
                        RoundedCornerShape(10.dp)
                    )
                    .clickable(enabled = !set.isCompleted) { onBeginEdit() }
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${set.reps}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (set.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (isEditing) {
            BasicTextField(
                value = editWeight,
                onValueChange = onEditWeightChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                ),
                modifier = Modifier
                    .weight(1f)
                    .background(Primary.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
                    .border(1.5.dp, Primary, RoundedCornerShape(10.dp))
                    .padding(vertical = 10.dp, horizontal = 8.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (set.isCompleted) Color(0xFF4CAF50).copy(alpha = 0.06f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f),
                        RoundedCornerShape(10.dp)
                    )
                    .clickable(enabled = !set.isCompleted) { onBeginEdit() }
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = set.weight?.let { String.format("%.1f", it) } ?: "-",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (set.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    when {
                        set.isCompleted -> Color(0xFF4CAF50)
                        isEditing -> Primary
                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    },
                    CircleShape
                )
                .clickable(enabled = !set.isCompleted) {
                    if (isEditing) onComplete() else onBeginEdit()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when {
                    set.isCompleted -> Icons.Default.Check
                    isEditing -> Icons.Default.Check
                    else -> Icons.Default.Edit
                },
                contentDescription = null,
                tint = when {
                    set.isCompleted || isEditing -> Color.White
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Bottom navigation bar
// ---------------------------------------------------------------------------

@Composable
private fun BottomNavigationBar(
    currentIndex: Int,
    totalExercises: Int,
    completedSets: Int,
    totalSets: Int,
    isOutdoor: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onComplete: () -> Unit
) {
    val allCompleted = completedSets == totalSets && totalSets > 0
    val showNext = !isOutdoor && currentIndex < totalExercises - 1
    val showPrevious = !isOutdoor && currentIndex > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 4.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.xl, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showPrevious) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .shadow(2.dp, CircleShape)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .clickable { onPrevious() },
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "Önceki", tint = MaterialTheme.colorScheme.onSurface)
            }
        }

        if (showNext) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .background(Primary, RoundedCornerShape(16.dp))
                    .clickable { onNext() }
                    .padding(vertical = 15.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sonraki Egzersiz",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(Modifier.width(8.dp))
                Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        } else {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (allCompleted || isOutdoor)
                            Brush.horizontalGradient(listOf(Color(0xFF4CAF50), Color(0xFF388E3C)))
                        else
                            Brush.horizontalGradient(listOf(Primary, Primary.copy(alpha = 0.8f))),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable { onComplete() }
                    .padding(vertical = 15.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.Flag, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Antrenmanı Tamamla",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Rest timer overlay
// ---------------------------------------------------------------------------

@Composable
private fun RestTimerOverlay(
    secondsRemaining: Int,
    totalSeconds: Int,
    onSkip: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Dinlenme",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Box(contentAlignment = Alignment.Center) {
                val sweep = if (totalSeconds > 0) (secondsRemaining.toFloat() / totalSeconds) * 360f else 0f
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.size(160.dp)
                ) {
                    drawArc(
                        color = Color.White.copy(alpha = 0.15f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(Primary, Primary.copy(alpha = 0.6f))
                        ),
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$secondsRemaining",
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "saniye",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            Text(
                text = "Sonraki set için hazır olun",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.5f)
            )

            Row(
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(Radius.pill))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(Radius.pill))
                    .clickable { onSkip() }
                    .padding(horizontal = 40.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.FastForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                Text(text = "Atla", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Heart rate zone — mirrors iOS HeartRateZone enum
// ---------------------------------------------------------------------------

private enum class HRZoneStrength(
    val label: String,
    val color: Color,
    val rangeLabel: String
) {
    REST("Dinlenme", Color(0xFF8E8E93), "< 50%"),
    WARMUP("Isınma", Color(0xFF34C759), "50–60%"),
    FAT_BURN("Yağ Yakımı", Color(0xFFFF9500), "60–70%"),
    CARDIO("Kardiyo", Color(0xFFFF6047), "70–80%"),
    PEAK("Zirve", Color(0xFFFF3B30), "> 80%");

    companion object {
        fun from(bpm: Int, age: Int = 30): HRZoneStrength {
            val maxHR = (220 - age.coerceIn(10, 90)).toDouble()
            return when {
                bpm < maxHR * 0.50 -> REST
                bpm < maxHR * 0.60 -> WARMUP
                bpm < maxHR * 0.70 -> FAT_BURN
                bpm < maxHR * 0.80 -> CARDIO
                else               -> PEAK
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Strength center — mirrors iOS strengthCenter
// Shows estimated BPM (TAHMİNİ) when no live HealthKit data
// ---------------------------------------------------------------------------

/**
 * iOS strengthCenter — birebir: heart · BPM · zone badge (TAHMİNİ / ⌚) · KCAL | EGZERSİZ | ORT BPM.
 * No guidance card (iOS doesn't have one). Wrap-content so it can be Spacer-centered.
 */
@Composable
private fun StrengthCenter(
    bpm: Int,
    isLive: Boolean,
    estimatedCalories: Int,
    exerciseProgress: String,
    avgBpm: Int
) {
    val zone = HRZoneStrength.from(bpm)

    // Heartbeat pulse — mirrors iOS hrPulse
    val pulse = rememberInfiniteTransition(label = "hr")
    val heartScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "hrScale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Heart icon with zone colour — mirrors iOS heart.fill (28pt)
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = null,
            tint = zone.color,
            modifier = Modifier
                .size(28.dp)
                .scale(heartScale)
        )

        Spacer(Modifier.height(8.dp))

        // Big BPM number — mirrors iOS 56pt bold rounded
        Text(
            text = "$bpm",
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "BPM",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 3.sp
        )

        Spacer(Modifier.height(8.dp))

        // Zone chip: "TAHMİNİ" when estimated, ⌚ when live — mirrors iOS zone row
        Row(
            modifier = Modifier
                .background(zone.color.copy(alpha = 0.1f), RoundedCornerShape(Radius.pill))
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = zone.label.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = zone.color,
                letterSpacing = 2.sp
            )
            if (isLive) {
                Icon(
                    imageVector = Icons.Default.Watch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "TAHMİNİ",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        // Stats row: KCAL | EGZERSİZ | ORT BPM — mirrors iOS WorkoutStatColumn row
        Row(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WorkoutStatColumn(value = "$estimatedCalories", label = "KCAL", color = Primary)
            StatDivider()
            WorkoutStatColumn(value = exerciseProgress, label = "EGZERSİZ", color = MaterialTheme.colorScheme.onSurface)
            StatDivider()
            WorkoutStatColumn(
                value = if (avgBpm > 0) "$avgBpm" else "--",
                label = "ORT BPM",
                color = Color(0xFFFF9500)
            )
        }
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(32.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    )
}

@Composable
private fun WorkoutStatColumn(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 2.sp
        )
    }
}

// ---------------------------------------------------------------------------
// GPS Workout Layout — Athlete HUD (dark/light dashboard + mini map)
// ---------------------------------------------------------------------------

private data class HudColors(
    val bgBrush: Brush,
    val surface: Color,
    val surfaceBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textHint: Color,
    val divider: Color,
    val iconBg: Color,
    val iconBorder: Color,
    val mapPlaceholder: Color,
    val pausePlayBg: Color,
    val pausePlayIcon: Color
)

private fun darkHud() = HudColors(
    bgBrush        = Brush.verticalGradient(listOf(Color(0xFF0A0A0F), Color(0xFF13131F))),
    surface        = Color.White.copy(alpha = 0.05f),
    surfaceBorder  = Color.White.copy(alpha = 0.07f),
    textPrimary    = Color.White,
    textSecondary  = Color.White.copy(alpha = 0.85f),
    textHint       = Color.White.copy(alpha = 0.3f),
    divider        = Color.White.copy(alpha = 0.07f),
    iconBg         = Color.White.copy(alpha = 0.07f),
    iconBorder     = Color.White.copy(alpha = 0.1f),
    mapPlaceholder = Color(0xFF191926),
    pausePlayBg    = Color.White,
    pausePlayIcon  = Color.Black
)

private fun lightHud() = HudColors(
    bgBrush        = Brush.verticalGradient(listOf(Color(0xFFF2F2F7), Color(0xFFE8E8F0))),
    surface        = Color.Black.copy(alpha = 0.04f),
    surfaceBorder  = Color.Black.copy(alpha = 0.07f),
    textPrimary    = Color(0xFF0D0D0D),
    textSecondary  = Color(0xFF1A1A1A),
    textHint       = Color.Black.copy(alpha = 0.35f),
    divider        = Color.Black.copy(alpha = 0.07f),
    iconBg         = Color.Black.copy(alpha = 0.05f),
    iconBorder     = Color.Black.copy(alpha = 0.08f),
    mapPlaceholder = Color(0xFFDDDDE8),
    pausePlayBg    = Color(0xFF1A1A1A),
    pausePlayIcon  = Color.White
)

@Composable
private fun GPSWorkoutLayout(
    training: Training,
    elapsedSeconds: Int,
    gpsDistance: Double,
    gpsPace: Double,
    gpsSpeed: Double,
    gpsCurrentSpeed: Double,
    gpsAltitude: Double,
    gpsSignalWeak: Boolean,
    gpsIsPaused: Boolean,
    gpsIsAutoPaused: Boolean,
    awaitingStart: Boolean,
    routePoints: List<Pair<Double, Double>>,
    gpsSplits: List<KmSplit>,
    goal: WorkoutGoal,
    goalProgress: Float,
    onClose: () -> Unit,
    onGoalTap: () -> Unit,
    onStartWorkout: () -> Unit,
    onPauseToggle: () -> Unit,
    onFinishWorkout: () -> Unit
) {
    val hcAvailable    by HealthConnectService.isAvailable.collectAsState()
    val liveHR         by HealthConnectService.currentHeartRate.collectAsState()
    val displayBpm     = if (hcAvailable && liveHR > 0) liveHR else 0
    val zone           = HRZoneStrength.from(displayBpm)
    val latLngs        = remember(routePoints) { routePoints.map { LatLng(it.first, it.second) } }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(39.9208, 32.8541), 14f)
    }

    var heroMetric    by remember { mutableIntStateOf(0) }
    var isDark        by remember { mutableStateOf(true) }
    var cameraLocked  by remember { mutableStateOf(true) }
    val c             = if (isDark) darkHud() else lightHud()
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    // Kamera kilidi açıkken harita sürüklendiğinde kilidi otomatik kaldır
    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving && cameraLocked) {
            cameraLocked = false
        }
    }

    // Kamera kilitliyken yeni GPS noktası gelince takip et
    LaunchedEffect(latLngs) {
        if (latLngs.isEmpty() || !cameraLocked) return@LaunchedEffect
        try {
            cameraPositionState.animate(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.fromLatLngZoom(latLngs.last(), 16f)
                ),
                durationMs = 800
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}
    }

    Box(modifier = Modifier.fillMaxSize().background(c.bgBrush)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {

            // ── Top bar ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HudIconButton(c, icon = Icons.Default.KeyboardArrowDown, onClick = onClose)

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatTime(elapsedSeconds),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = c.textPrimary
                    )
                    Text("SÜRE", fontSize = 9.sp, color = c.textHint, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                }

                // Sağ üstte: tema toggle + bitir
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HudIconButton(
                        c,
                        icon = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                        onClick = { isDark = !isDark }
                    )
                    HudIconButton(c, icon = Icons.Default.Flag, tint = Primary, onClick = onGoalTap)
                }
            }

            // ── Banners ───────────────────────────────────────────────────────
            if (gpsSignalWeak && latLngs.size >= 2) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .background(Color(0xFF8B6914).copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.GpsNotFixed, null, tint = Color.White, modifier = Modifier.size(13.dp))
                    Text("GPS sinyali zayıf", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
            }

            if (gpsIsAutoPaused && !awaitingStart) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .background(Primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .border(1.dp, Primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Pause, null, tint = Primary, modifier = Modifier.size(13.dp))
                    Text("Otomatik duraklama aktif", fontSize = 12.sp, color = Primary, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(4.dp))

            // ── Hero metric card ──────────────────────────────────────────────
            val heroLabel = when (heroMetric) { 0 -> "MESAFE"; 1 -> "TEMPO"; else -> "HIZ" }
            val heroValue = when (heroMetric) { 0 -> "%.2f".format(gpsDistance); 1 -> formatPace(gpsPace); else -> "%.1f".format(gpsSpeed) }
            val heroUnit  = when (heroMetric) { 0 -> "km"; 1 -> "/km"; else -> "km/s" }
            val secLeft   = when (heroMetric) { 0 -> Pair(formatPace(gpsPace), "TEMPO"); 1 -> Pair("%.2f".format(gpsDistance), "KM"); else -> Pair(formatPace(gpsPace), "TEMPO") }
            val secRight  = when (heroMetric) { 0 -> Pair("%.1f".format(gpsSpeed), "HIZ"); else -> Pair("%.2f".format(gpsDistance), "KM") }

            Box(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .background(c.surface, RoundedCornerShape(24.dp))
                    .border(1.dp, c.surfaceBorder, RoundedCornerShape(24.dp))
                    .clickable { heroMetric = (heroMetric + 1) % 3 }
                    .padding(horizontal = 22.dp, vertical = 18.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(heroLabel, fontSize = 10.sp, color = Primary, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            repeat(3) { i ->
                                Box(
                                    modifier = Modifier.then(
                                        if (i == heroMetric)
                                            Modifier.width(14.dp).height(4.dp).background(Primary, RoundedCornerShape(2.dp))
                                        else
                                            Modifier.size(4.dp).background(c.textHint, CircleShape)
                                    )
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(2.dp))

                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = heroValue,
                            fontSize = 68.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = c.textPrimary,
                            lineHeight = 68.sp
                        )
                        Text(heroUnit, fontSize = 20.sp, color = c.textHint, modifier = Modifier.padding(bottom = 10.dp))
                    }

                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HudSecondStat(c, secLeft.first, secLeft.second)
                        Box(Modifier.width(1.dp).height(26.dp).background(c.divider))
                        HudSecondStat(c, secRight.first, secRight.second)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Stat grid — saat/HC varsa 3 kart (BPM dahil), yoksa 2 kart ───
            Row(
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HudStatCard(c, Modifier.weight(1f), Icons.Default.TrendingUp, "+%.0f".format(gpsAltitude), "m", "YÜKSEKLİK")
                if (hcAvailable) {
                    HudStatCard(
                        c, Modifier.weight(1f), Icons.Default.Favorite,
                        if (liveHR > 0) "$liveHR" else "--",
                        "", "BPM", valueColor = zone.color
                    )
                }
                HudStatCard(c, Modifier.weight(1f), Icons.Default.LocalFireDepartment, if (hcAvailable) "${(elapsedSeconds / 60) * 7}" else "—", "", "KCAL")
            }

            Spacer(Modifier.height(10.dp))

            // ── Harita kartı (kalan alanı doldurur) ──────────────────────────
            Box(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, c.surfaceBorder, RoundedCornerShape(20.dp))
            ) {
                if (latLngs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(c.mapPlaceholder),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.GpsFixed, null, tint = Primary.copy(alpha = 0.4f), modifier = Modifier.size(28.dp))
                            Text(
                                text = if (gpsSignalWeak) "GPS sinyali aranıyor..." else "GPS konumu bekleniyor...",
                                fontSize = 12.sp, color = c.textHint
                            )
                            Text("Açık bir alanda durun", fontSize = 11.sp, color = c.textHint.copy(alpha = 0.6f))
                        }
                    }
                } else {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        properties = MapProperties(
                            isMyLocationEnabled = true,
                            mapType = com.google.maps.android.compose.MapType.NORMAL
                        ),
                        uiSettings = MapUiSettings(
                            zoomControlsEnabled = false,
                            mapToolbarEnabled = false,
                            myLocationButtonEnabled = false,
                            scrollGesturesEnabled = true,
                            rotationGesturesEnabled = false,
                            zoomGesturesEnabled = true,
                            tiltGesturesEnabled = false
                        )
                    ) {
                        if (latLngs.size >= 2) {
                            Polyline(points = latLngs, color = Primary, width = 14f)
                        }
                        if (latLngs.isNotEmpty()) {
                            Marker(state = MarkerState(position = latLngs.first()), title = "Başlangıç")
                        }
                        if (latLngs.size >= 2) {
                            Circle(
                                center      = latLngs.last(),
                                radius      = 7.0,
                                fillColor   = Primary,
                                strokeColor = Color.White,
                                strokeWidth = 4f
                            )
                        }
                    }

                    // Konuma kilitle butonu — kamera serbest olduğunda görünür
                    if (!cameraLocked) {
                        Surface(
                            onClick = {
                                cameraLocked = true
                                if (latLngs.isNotEmpty()) {
                                    coroutineScope.launch {
                                        try {
                                            cameraPositionState.animate(
                                                CameraUpdateFactory.newCameraPosition(
                                                    CameraPosition.fromLatLngZoom(latLngs.last(), 16f)
                                                ),
                                                durationMs = 600
                                            )
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (_: Exception) {}
                                    }
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp)
                                .size(40.dp),
                            shape = CircleShape,
                            color = Color.White,
                            shadowElevation = 4.dp,
                            tonalElevation = 0.dp
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MyLocation,
                                    contentDescription = "Konuma kilitle",
                                    tint = Primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Hedef progress ────────────────────────────────────────────────
            if (goal !is WorkoutGoal.None) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(goal.label(), fontSize = 11.sp, color = c.textHint)
                        Text(
                            "%d%%".format((goalProgress * 100).toInt()),
                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = if (goalProgress >= 1f) Color(0xFF4CAF50) else Primary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { goalProgress },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = if (goalProgress >= 1f) Color(0xFF4CAF50) else Primary,
                        trackColor = c.divider
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            // ── Alt butonlar (yalnızca aktif antrenman) ───────────────────────
            if (!awaitingStart && !gpsIsPaused) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(c.pausePlayBg, CircleShape)
                            .clickable { onPauseToggle() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = "Duraklat",
                            tint = c.pausePlayIcon,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }

        if (awaitingStart) {
            WorkoutStartOverlay(onStart = onStartWorkout)
        } else if (gpsIsPaused) {
            WorkoutPausedOverlay(
                onResume = onPauseToggle,
                onFinish = onFinishWorkout
            )
        }
    }
}

// ── HUD helper composables ─────────────────────────────────────────────────────

@Composable
private fun HudIconButton(
    c: HudColors,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    val iconTint = if (tint == Color.Unspecified) c.textPrimary else tint
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(c.iconBg, CircleShape)
            .border(1.dp, c.iconBorder, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun HudStatCard(
    c: HudColors,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    unit: String,
    label: String,
    valueColor: Color = Color.Unspecified
) {
    val vColor = if (valueColor == Color.Unspecified) c.textPrimary else valueColor
    Column(
        modifier = modifier
            .background(c.surface, RoundedCornerShape(16.dp))
            .border(1.dp, c.surfaceBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icon, null, tint = vColor.copy(alpha = 0.55f), modifier = Modifier.size(13.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = vColor)
            if (unit.isNotEmpty())
                Text(unit, fontSize = 9.sp, color = c.textHint, modifier = Modifier.padding(bottom = 2.dp))
        }
        Text(label, fontSize = 8.sp, color = c.textHint, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HudSecondStat(c: HudColors, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = c.textSecondary)
        Text(label, fontSize = 9.sp, color = c.textHint, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HudActionButton(
    c: HudColors,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    val iconTint = if (tint == Color.Unspecified) c.textPrimary else tint
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .background(c.iconBg, CircleShape)
                .border(1.dp, c.iconBorder, CircleShape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Text(label, fontSize = 11.sp, color = c.textHint)
    }
}

// ---------------------------------------------------------------------------
// Program preview — salon / AI hareket listesi (sayaç öncesi)
// ---------------------------------------------------------------------------

@Composable
private fun ProgramPreviewOverlay(
    training: Training,
    exercises: List<ProgramExercise>,
    fallbackNames: List<String>,
    isLoading: Boolean,
    onStart: (Int) -> Unit,
    onBack: () -> Unit
) {
    val availableDays = remember(exercises) {
        exercises.map { it.orderIndex / 100 }.filter { it > 0 }.distinct().sorted()
    }
    var selectedDay by remember(availableDays) {
        mutableStateOf(availableDays.firstOrNull() ?: 1)
    }

    val items = remember(exercises, fallbackNames, selectedDay, availableDays) {
        val filtered = if (availableDays.size > 1) {
            exercises.filter { (it.orderIndex / 100) == selectedDay }
        } else {
            exercises
        }
        if (filtered.isNotEmpty()) {
            filtered.sortedBy { it.orderIndex }.map { pe ->
                val name = pe.exercise?.name ?: "Hareket"
                val detail = buildString {
                    append("${pe.sets} set × ${pe.reps} tekrar")
                    pe.weightSuggestion?.takeIf { it > 0 }?.let { append(" · ${it.toInt()} kg") }
                }
                name to detail
            }
        } else {
            fallbackNames.map { it to "" }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    Text("İptal", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text = training.title,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(56.dp))
            }

            Text(
                text = "Program hareketleri",
                modifier = Modifier.padding(horizontal = 20.dp),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Başlamadan önce içeriği inceleyin",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (availableDays.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableDays.forEach { day ->
                        val isSelected = selectedDay == day
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clickable { selectedDay = day },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) Primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$day. Gün",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (isLoading && items.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            } else if (items.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "Bu program için hareket listesi bulunamadı.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items.size) { index ->
                        val (name, detail) = items[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}.",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Primary,
                                modifier = Modifier.width(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (detail.isNotBlank()) {
                                    Text(
                                        detail,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { onStart(selectedDay) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text("Antrenmana Başla", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MinimalWorkoutLayout(
    elapsedSeconds: Int,
    primaryMetric: String,
    secondaryMetric: String,
    isPaused: Boolean,
    onClose: () -> Unit,
    onPauseToggle: () -> Unit
) {
    val pulse = rememberInfiniteTransition(label = "minimalHr")
    val heartScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "heartScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Kapat",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .padding(start = 12.dp, top = 8.dp)
                    .size(32.dp)
                    .clickable { onClose() }
            )

            Text(
                text = formatTimeFull(elapsedSeconds),
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = primaryMetric,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    Text(
                        text = secondaryMetric,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }

                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color(0xFFFF3B30),
                    modifier = Modifier
                        .size(22.dp)
                        .scale(heartScale)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color(0xFF1C1C1E),
                        RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
                    )
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 18.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                )

                Button(
                    onClick = onPauseToggle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (isPaused) "Devam Et" else "Duraklat",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun StravaStyleWorkoutControls(
    isPaused: Boolean,
    onPauseToggle: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(
                    if (isPaused) Primary else MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape
                )
                .clickable { onPauseToggle() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = if (isPaused) "Devam" else "Duraklat",
                tint = if (isPaused) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun WorkoutStartOverlay(onStart: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .widthIn(max = 320.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                "Hazır mısın?",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                "Başlat'a bastığında süre ve GPS takibi başlar.",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.72f),
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text("Başlat", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun WorkoutPausedOverlay(
    onResume: () -> Unit,
    onFinish: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .widthIn(max = 320.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Duraklatıldı",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onResume,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text("Devam Et", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onFinish,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF3B30)),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFF3B30))
            ) {
                Text("Bitir", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}

private fun formatTimeFull(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return String.format("%02d:%02d:%02d", h, m, s)
}

private fun formatPace(paceMinPerKm: Double): String {
    if (paceMinPerKm <= 0) return "--'--"
    val min = paceMinPerKm.toInt()
    val sec = ((paceMinPerKm - min) * 60).toInt()
    return "%d'%02d\"".format(min, sec)
}

@Composable
private fun MinifiedStatsHeader(
    elapsedSeconds: Int,
    liveHR: Int,
    avgHR: Int,
    maxHR: Int,
    isAvailable: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Süre
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatTime(elapsedSeconds),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text("SÜRE", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
            // Ort. Nabız
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isAvailable && avgHR > 0) "$avgHR" else "—",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text("ORT BPM", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
            // Maks. Nabız
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isAvailable && maxHR > 0) "$maxHR" else "—",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text("MAKS BPM", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
            // Kalori
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isAvailable) "${(elapsedSeconds / 60) * 7}" else "—",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text("KCAL", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun WorkoutStartPromptOverlay(
    training: Training,
    onStart: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .widthIn(max = 320.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = training.category.emoji,
                fontSize = 64.sp
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = training.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Antrenmana başlamak ister misiniz?",
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Başla", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            TextButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Vazgeç", fontSize = 15.sp, color = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}
