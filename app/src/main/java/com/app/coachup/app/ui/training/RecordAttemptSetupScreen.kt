package com.app.coachup.app.ui.training

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.coachup.app.models.Exercise
import com.app.coachup.app.models.RecordAttemptCategories
import com.app.coachup.app.models.RecordCategory
import com.app.coachup.app.models.RecordExercise
import com.app.coachup.app.models.RecordMeasureType
import com.app.coachup.app.services.PlannedSet
import com.app.coachup.app.services.PlateCalculator
import com.app.coachup.app.services.RecordAttempt
import com.app.coachup.app.services.RecordAttemptService
import com.app.coachup.app.services.RecordAttemptSet
import com.app.coachup.app.theme.Primary
import com.app.coachup.app.ui.training.components.AttemptSetRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ─── Navigation steps ────────────────────────────────────────────────────────
private enum class SetupStep { CATEGORY, EXERCISE, CONFIG }

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RecordAttemptSetupScreen(
    userId: String,
    onNavigateBack: () -> Unit,
    onNavigateToSession: (
        RecordAttempt,
        List<RecordAttemptSet>,
        Exercise,
        RecordMeasureType
    ) -> Unit
) {
    var step by remember { mutableStateOf(SetupStep.CATEGORY) }
    var selectedCategory by remember { mutableStateOf<RecordCategory?>(null) }
    var selectedCatalogExercise by remember { mutableStateOf<RecordExercise?>(null) }
    var dbExercises by remember { mutableStateOf<List<Exercise>>(emptyList()) }

    var targetValue by remember { mutableDoubleStateOf(100.0) }
    var targetReps by remember { mutableIntStateOf(1) }
    var includeWarmup by remember { mutableStateOf(false) }
    var plan by remember { mutableStateOf(PlateCalculator.generateSimpleMainPlan(100.0, 1)) }

    var isStarting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val measureType = selectedCatalogExercise?.measureType ?: RecordMeasureType.WEIGHT

    fun regeneratePlan() {
        plan = when {
            measureType != RecordMeasureType.WEIGHT ->
                PlateCalculator.generateSimpleMainPlan(targetValue, targetReps)
            includeWarmup ->
                PlateCalculator.generateWarmupPlan(targetValue, targetReps)
            else ->
                PlateCalculator.generateSimpleMainPlan(targetValue, targetReps)
        }
    }

    fun applyDefaults(exercise: RecordExercise) {
        if (exercise.measureType == RecordMeasureType.WEIGHT) {
            includeWarmup = false
        }
        targetReps = exercise.defaultReps
        targetValue = when (exercise.measureType) {
            RecordMeasureType.WEIGHT -> 100.0
            RecordMeasureType.REPS -> exercise.defaultReps.toDouble()
            else -> exercise.defaultTarget ?: 600.0
        }
        regeneratePlan()
    }

    fun launchSession() {
        val catalogExercise = selectedCatalogExercise ?: return
        if (isStarting) return
        isStarting = true
        scope.launch {
            try {
                val resolved = RecordAttemptService.resolveOrCreateExercise(
                    catalog = catalogExercise,
                    categoryId = selectedCategory?.id,
                    knownExercises = dbExercises
                )
                if (dbExercises.none { it.id == resolved.id }) {
                    dbExercises = dbExercises + resolved
                }
                val attempt = RecordAttemptService.startAttempt(
                    userId = userId,
                    exerciseId = resolved.id,
                    targetWeight = targetValue,
                    targetReps = targetReps
                )
                val sets = RecordAttemptService.insertPlannedSets(
                    attemptId = attempt.id,
                    userId = userId,
                    plan = plan
                )
                onNavigateToSession(attempt, sets, resolved, catalogExercise.measureType)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = friendlyRecordAttemptError(e)
            } finally {
                isStarting = false
            }
        }
    }

    fun adjustTarget(positive: Boolean) {
        val step = when (measureType) {
            RecordMeasureType.WEIGHT -> 2.5
            RecordMeasureType.REPS -> 1.0
            RecordMeasureType.CALORIES -> 5.0
            RecordMeasureType.TIME -> 15.0
            RecordMeasureType.DISTANCE -> 0.5
        }
        targetValue = maxOf(0.0, targetValue + if (positive) step else -step)
        if (measureType == RecordMeasureType.REPS) {
            targetReps = targetValue.roundToInt().coerceAtLeast(1)
        }
        regeneratePlan()
    }

    fun adjustReps(positive: Boolean) {
        targetReps = maxOf(1, targetReps + if (positive) 1 else -1)
        regeneratePlan()
    }

    LaunchedEffect(Unit) {
        try {
            dbExercises = RecordAttemptService.fetchExercises()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("RecordAttemptSetup", "fetchExercises error", e)
        }
    }

    fun handleBack() {
        when (step) {
            SetupStep.CATEGORY -> onNavigateBack()
            SetupStep.EXERCISE -> {
                selectedCategory = null
                step = SetupStep.CATEGORY
            }
            SetupStep.CONFIG -> {
                selectedCatalogExercise = null
                step = SetupStep.EXERCISE
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ── Header ────────────────────────────────────────────────────────
            SetupHeader(
                title = when (step) {
                    SetupStep.CATEGORY -> "Rekor Denemesi"
                    SetupStep.EXERCISE -> selectedCategory?.name ?: "Hareket Seç"
                    SetupStep.CONFIG -> selectedCatalogExercise?.name ?: "Hazırlık"
                },
                subtitle = when (step) {
                    SetupStep.CATEGORY -> "Kategori seçin"
                    SetupStep.EXERCISE -> "${selectedCategory?.exerciseCount ?: 0} hareket"
                    SetupStep.CONFIG -> measureLabel(measureType)
                },
                onBack = { handleBack() }
            )

            // ── Content ───────────────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (step) {
                    // ── Step 1: Category list ─────────────────────────────────
                    SetupStep.CATEGORY -> {
                        items(RecordAttemptCategories.all) { category ->
                            CategoryCard(
                                category = category,
                                onClick = {
                                    selectedCategory = category
                                    selectedCatalogExercise = null
                                    step = SetupStep.EXERCISE
                                }
                            )
                        }
                    }

                    // ── Step 2: Exercise list ─────────────────────────────────
                    SetupStep.EXERCISE -> {
                        val exercises = selectedCategory?.exercises ?: emptyList()
                        items(exercises) { exercise ->
                            ExerciseCard(
                                exercise = exercise,
                                onClick = {
                                    selectedCatalogExercise = exercise
                                    applyDefaults(exercise)
                                    if (exercise.measureType == RecordMeasureType.WEIGHT) {
                                        step = SetupStep.CONFIG
                                    } else {
                                        launchSession()
                                    }
                                }
                            )
                        }
                    }

                    // ── Step 3: Config ────────────────────────────────────────
                    SetupStep.CONFIG -> {
                        item {
                            SelectedExerciseBanner(
                                exercise = selectedCatalogExercise!!,
                                categoryName = selectedCategory?.name.orEmpty(),
                                onChangeTap = {
                                    selectedCatalogExercise = null
                                    step = SetupStep.EXERCISE
                                }
                            )
                        }
                        item {
                            when (measureType) {
                                RecordMeasureType.WEIGHT -> {
                                    TargetNumericCard(
                                        label = "HEDEF AĞIRLIK",
                                        valueText = "${formatWeight(targetValue)} kg",
                                        onDecrease = { adjustTarget(false) },
                                        onIncrease = { adjustTarget(true) }
                                    )
                                }
                                RecordMeasureType.REPS ->
                                    TargetNumericCard(
                                        label = "HEDEF TEKRAR",
                                        valueText = "$targetReps tekrar",
                                        onDecrease = { adjustTarget(false) },
                                        onIncrease = { adjustTarget(true) }
                                    )
                                RecordMeasureType.TIME ->
                                    TargetNumericCard(
                                        label = "HEDEF SÜRE",
                                        valueText = formatDuration(targetValue.roundToInt()),
                                        onDecrease = { adjustTarget(false) },
                                        onIncrease = { adjustTarget(true) }
                                    )
                                RecordMeasureType.CALORIES ->
                                    TargetNumericCard(
                                        label = "HEDEF KALORİ",
                                        valueText = "${targetValue.roundToInt()} cal",
                                        onDecrease = { adjustTarget(false) },
                                        onIncrease = { adjustTarget(true) }
                                    )
                                RecordMeasureType.DISTANCE ->
                                    TargetNumericCard(
                                        label = "HEDEF MESAFE",
                                        valueText = if (targetValue >= 1.0)
                                            "%.1f km".format(targetValue)
                                        else
                                            "${(targetValue * 1000).roundToInt()} m",
                                        onDecrease = { adjustTarget(false) },
                                        onIncrease = { adjustTarget(true) }
                                    )
                            }
                        }
                        if (measureType == RecordMeasureType.WEIGHT) {
                            item {
                                TargetNumericCard(
                                    label = "HEDEF TEKRAR",
                                    valueText = "$targetReps tekrar",
                                    onDecrease = { adjustReps(false) },
                                    onIncrease = { adjustReps(true) }
                                )
                            }
                            item {
                                WarmupToggleCard(
                                    includeWarmup = includeWarmup,
                                    onIncludeWarmupChange = {
                                        includeWarmup = it
                                        regeneratePlan()
                                    }
                                )
                            }
                        }
                        item {
                            PlannedSetsSection(plan = plan, measureType = measureType)
                        }
                        item { Spacer(Modifier.height(100.dp)) }
                    }
                }

                if (step != SetupStep.CONFIG) {
                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }

        // ── CTA: only on CONFIG step ──────────────────────────────────────────
        if (step == SetupStep.CONFIG) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.background.copy(alpha = 0f),
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                )
                Button(
                    onClick = { launchSession() },
                    enabled = selectedCatalogExercise != null && !isStarting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 20.dp)
                        .align(Alignment.BottomCenter)
                        .offset(y = (-20).dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary,
                        disabledContainerColor = Primary.copy(alpha = 0.4f)
                    )
                ) {
                    if (isStarting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.LocalFireDepartment, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text("Başla", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        if (isStarting && step != SetupStep.CONFIG) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Primary)
            }
        }
    }

    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Hata") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { errorMessage = null }) { Text("Tamam") } }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SetupHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CategoryCard(category: RecordCategory, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon(category.id),
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${category.exerciseCount} hareket",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExerciseCard(exercise: RecordExercise, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = measureLabel(exercise.measureType),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SelectedExerciseBanner(
    exercise: RecordExercise,
    categoryName: String,
    onChangeTap: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Primary.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onChangeTap)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Filled.FitnessCenter, null, tint = Primary, modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = categoryName,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("Değiştir", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Primary)
        }
    }
}

@Composable
private fun TargetNumericCard(
    label: String,
    valueText: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HoldRepeatButton(
                    onClick = onDecrease,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "−", fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = valueText,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                HoldRepeatButton(
                    onClick = onIncrease,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun WarmupToggleCard(
    includeWarmup: Boolean,
    onIncludeWarmupChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Isınma",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                WarmupOptionChip(
                    label = "Isınma setleriyle",
                    selected = includeWarmup,
                    onClick = { onIncludeWarmupChange(true) },
                    modifier = Modifier.weight(1f)
                )
                WarmupOptionChip(
                    label = "Direkt rekor",
                    selected = !includeWarmup,
                    onClick = { onIncludeWarmupChange(false) },
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = if (includeWarmup) {
                    "Önerilen ısınma setleri plana eklenir."
                } else {
                    "Doğrudan hedef ağırlıkla rekor denemesine geçilir."
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun WarmupOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.background,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) Primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Text(
            text = label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) Primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PlannedSetsSection(plan: List<PlannedSet>, measureType: RecordMeasureType) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (measureType == RecordMeasureType.WEIGHT) "Planlanan Setler" else "Deneme Planı",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${plan.size}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        plan.forEachIndexed { index, set ->
            AttemptSetRow(index = index, set = set, measureType = measureType)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hold-to-repeat button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HoldRepeatButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    var isHeld by remember { mutableStateOf(false) }
    LaunchedEffect(isHeld) {
        if (isHeld) {
            delay(450L)
            while (isHeld) { onClick(); delay(80L) }
        }
    }
    Box(
        modifier = modifier.pointerInput(onClick) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                onClick()
                isHeld = true
                waitForUpOrCancellation()
                isHeld = false
            }
        },
        contentAlignment = contentAlignment,
        content = content
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Pure helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun categoryIcon(id: String): ImageVector = when (id) {
    "strength"  -> Icons.Default.FitnessCenter
    "bodyweight" -> Icons.Default.AccessibilityNew
    "running"   -> Icons.AutoMirrored.Filled.DirectionsRun
    "cardio"    -> Icons.Default.MonitorHeart
    "benchmark" -> Icons.Default.EmojiEvents
    else        -> Icons.Default.Category
}

private fun measureLabel(type: RecordMeasureType): String = when (type) {
    RecordMeasureType.WEIGHT   -> "Ağırlık rekoru (kg)"
    RecordMeasureType.REPS     -> "Maksimum tekrar"
    RecordMeasureType.TIME     -> "En iyi süre"
    RecordMeasureType.DISTANCE -> "Mesafe rekoru"
    RecordMeasureType.CALORIES -> "Kalori hedefi"
}

private fun friendlyRecordAttemptError(e: Exception): String {
    val raw = buildString {
        append(e.message.orEmpty())
        e.cause?.message?.let { append(' ').append(it) }
    }
    return when {
        raw.contains("invalid input syntax for type uuid", ignoreCase = true) ->
            "Hareket kaydı oluşturulamadı. Lütfen tekrar deneyin."
        raw.contains("JWT", ignoreCase = true) || raw.contains("401") ->
            "Oturum süresi dolmuş olabilir. Tekrar giriş yapın."
        raw.contains("permission", ignoreCase = true) || raw.contains("42501") ->
            "Rekor denemesi için yetki yok."
        raw.length > 160 || raw.contains("URL:") || raw.contains("Headers:") ->
            "Rekor denemesi başlatılamadı. Lütfen tekrar deneyin."
        else -> raw.ifBlank { "Rekor denemesi başlatılamadı." }
    }
}

internal fun formatWeight(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v)
