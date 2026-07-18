package com.app.coachup.app.ui.results

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.app.coachup.app.models.PersonalRecord
import com.app.coachup.app.models.TrainingSession
import com.app.coachup.app.navigation.Routes
import com.app.coachup.app.services.AuthService
import com.app.coachup.app.services.RecordAttempt
import com.app.coachup.app.services.RecordAttemptService
import com.app.coachup.app.services.ResultsService
import com.app.coachup.app.services.StreakService
import com.app.coachup.app.services.TrainingService
import com.app.coachup.app.services.UserService
import com.app.coachup.app.theme.*
import com.app.coachup.app.utils.FormatHelpers
import kotlinx.coroutines.CancellationException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@Composable
fun PersonalRecordsScreen(navController: NavController) {
    val currentProfile by UserService.currentProfile.collectAsState()

    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    var records by remember { mutableStateOf<List<PersonalRecord>>(emptyList()) }
    var workoutSessions by remember { mutableStateOf<List<TrainingSession>>(emptyList()) }
    var recordAttempts by remember { mutableStateOf<List<RecordAttempt>>(emptyList()) }
    var exerciseNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedFilter by remember { mutableStateOf("Tüm zamanlar") }
    var workoutFilter by remember { mutableStateOf("Bu hafta") }
    var expandedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var workoutExpandedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var displayedMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }

    LaunchedEffect(Unit) {
        val uid = AuthService.getCurrentUserId()
        if (uid.isNullOrEmpty()) {
            isLoading = false
            loadError = true
            return@LaunchedEffect
        }
        try {
            records = ResultsService.fetchPersonalRecords(uid)
            workoutSessions = TrainingService.fetchAllCompletedSessions(uid)
            recordAttempts = RecordAttemptService.fetchCompletedAttempts(uid)
            exerciseNames = RecordAttemptService.fetchExercises().associate { it.id to it.name }
            if (currentProfile == null) UserService.fetchProfile(uid)
            StreakService.syncUserStreak(uid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("PersonalRecordsScreen", "Veri yüklenemedi", e)
            loadError = true
        } finally {
            isLoading = false
        }
    }

    val currentStreak = currentProfile?.currentStreak ?: 0
    val today = remember { LocalDate.now() }

    val activityDays = remember(records, workoutSessions, recordAttempts) {
        buildSet {
            records.forEach { r ->
                runCatching { add(LocalDate.parse(r.recordDate.take(10))) }
            }
            workoutSessions.forEach { s ->
                parseActivityDate(s.completedAt)?.let { add(it) }
            }
            recordAttempts.forEach { a ->
                parseActivityDate(a.completedAt)?.let { add(it) }
            }
        }
    }

    val filteredRecords = remember(records, selectedFilter, selectedDay) {
        val base = if (selectedDay != null) {
            records.filter {
                runCatching { LocalDate.parse(it.recordDate.take(10)) == selectedDay }.getOrDefault(false)
            }
        } else when (selectedFilter) {
            "Bu hafta" -> {
                val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                records.filter {
                    runCatching {
                        val d = LocalDate.parse(it.recordDate.take(10))
                        !d.isBefore(monday) && !d.isAfter(today)
                    }.getOrDefault(false)
                }
            }
            "Bu ay" -> records.filter {
                runCatching {
                    val d = LocalDate.parse(it.recordDate.take(10))
                    d.month == today.month && d.year == today.year
                }.getOrDefault(false)
            }
            else -> records
        }
        base.sortedByDescending { it.recordDate }
    }

    val filteredWorkouts = remember(workoutSessions, workoutFilter, selectedDay) {
        val base = if (selectedDay != null) {
            workoutSessions.filter { parseActivityDate(it.completedAt) == selectedDay }
        } else when (workoutFilter) {
            "Bu ay" -> workoutSessions.filter {
                parseActivityDate(it.completedAt)?.let { d ->
                    d.month == today.month && d.year == today.year
                } == true
            }
            else -> {
                val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                workoutSessions.filter {
                    parseActivityDate(it.completedAt)?.let { d ->
                        !d.isBefore(monday) && !d.isAfter(today)
                    } == true
                }
            }
        }
        base.sortedByDescending { it.completedAt.orEmpty() }
    }

    val dayAttempts = remember(recordAttempts, selectedDay) {
        if (selectedDay == null) emptyList()
        else recordAttempts.filter { parseActivityDate(it.completedAt) == selectedDay }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .shadow(2.dp, CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { navController.popBackStack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            }

            Spacer(Modifier.width(8.dp))

            Text(
                "Aktivite Geçmişi",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            // "Rekor Denemesi" button
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Primary)
                    .shadow(elevation = 4.dp, shape = CircleShape)
                    .clickable { navController.navigate(Routes.RECORD_ATTEMPT_SETUP) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.LocalFireDepartment, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text("Rekor Denemesi", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
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


        // ── Scrollable content ───────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 60.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Streak
            item {
                StreakSection(streak = currentStreak)
            }

            // Activity Calendar
            item {
                ActivityCalendarSection(
                    displayedMonth = displayedMonth,
                    activityDays = activityDays,
                    selectedDay = selectedDay,
                    onMonthChange = { displayedMonth = it },
                    onDaySelected = { day ->
                        selectedDay = if (selectedDay == day) null else day
                    },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            if (selectedDay != null) {
                item {
                    SelectedDaySection(
                        date = selectedDay!!,
                        workouts = filteredWorkouts,
                        attempts = dayAttempts,
                        exerciseNames = exerciseNames,
                        expandedWorkoutIds = workoutExpandedIds,
                        onToggleWorkoutExpand = { id ->
                            workoutExpandedIds = if (workoutExpandedIds.contains(id)) {
                                workoutExpandedIds - id
                            } else {
                                workoutExpandedIds + id
                            }
                        },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }

            item {
                WorkoutHistorySection(
                    workouts = filteredWorkouts,
                    selectedFilter = workoutFilter,
                    onFilterChange = { workoutFilter = it },
                    daySelected = selectedDay != null,
                    expandedIds = workoutExpandedIds,
                    onToggleExpand = { id ->
                        workoutExpandedIds = if (workoutExpandedIds.contains(id)) {
                            workoutExpandedIds - id
                        } else {
                            workoutExpandedIds + id
                        }
                    },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            // Personal Records section
            item {
                PersonalRecordsSection(
                    records = filteredRecords,
                    selectedFilter = if (selectedDay != null) {
                        selectedDay!!.format(DateTimeFormatter.ofPattern("d MMM", Locale("tr")))
                    } else selectedFilter,
                    onFilterChange = { if (selectedDay == null) selectedFilter = it },
                    filterEnabled = selectedDay == null,
                    expandedIds = expandedIds,
                    onToggleExpand = { id ->
                        expandedIds = if (expandedIds.contains(id))
                            expandedIds - id else expandedIds + id
                    },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Streak Section
// ---------------------------------------------------------------------------

@Composable
private fun StreakSection(streak: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.LocalFireDepartment, null, tint = Primary, modifier = Modifier.size(32.dp))
        Text(
            "$streak",
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text("Günlük Streak", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------------------
// Activity Calendar
// ---------------------------------------------------------------------------

private data class ActivityDay(
    val date: LocalDate?,
    val day: Int,
    val isActive: Boolean,
    val isToday: Boolean,
    val isSelected: Boolean
)

private fun parseActivityDate(iso: String?): LocalDate? = runCatching {
    Instant.parse(iso).atZone(ZoneOffset.UTC).toLocalDate()
}.getOrNull()

@Composable
private fun ActivityCalendarSection(
    displayedMonth: YearMonth,
    activityDays: Set<LocalDate>,
    selectedDay: LocalDate?,
    onMonthChange: (YearMonth) -> Unit,
    onDaySelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now() }
    val weekDayNames = listOf("Pzt", "Sal", "Çar", "Per", "Cm", "Cts", "Pz")
    val monthTitle = remember(displayedMonth) {
        displayedMonth.month.getDisplayName(TextStyle.FULL, Locale("tr"))
            .replaceFirstChar { it.uppercase() } + " ${displayedMonth.year}"
    }

    val calendarWeeks = remember(displayedMonth, activityDays, selectedDay) {
        val firstOfMonth = displayedMonth.atDay(1)
        val gridStart = firstOfMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val lastOfMonth = displayedMonth.atEndOfMonth()
        val gridEnd = lastOfMonth.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        val days = mutableListOf<ActivityDay>()
        var cursor = gridStart
        while (!cursor.isAfter(gridEnd)) {
            val inMonth = cursor.month == displayedMonth.month
            days.add(
                ActivityDay(
                    date = if (inMonth) cursor else null,
                    day = cursor.dayOfMonth,
                    isActive = cursor in activityDays,
                    isToday = cursor == today,
                    isSelected = cursor == selectedDay
                )
            )
            cursor = cursor.plusDays(1)
        }
        days.chunked(7)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onMonthChange(displayedMonth.minusMonths(1)) }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Önceki ay", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                monthTitle,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = { onMonthChange(displayedMonth.plusMonths(1)) }) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Sonraki ay", tint = MaterialTheme.colorScheme.onSurface)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                weekDayNames.forEach { name ->
                    Text(
                        name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            calendarWeeks.forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        val clickable = day.date != null
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .then(
                                    if (clickable) Modifier.clickable { onDaySelected(day.date!!) }
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                day.isSelected -> {
                                    Box(
                                        modifier = Modifier.size(32.dp).clip(CircleShape).background(Primary)
                                    )
                                }
                                day.isToday -> {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Primary.copy(alpha = 0.25f))
                                    )
                                }
                                day.isActive -> {
                                    Box(
                                        modifier = Modifier.size(32.dp).clip(CircleShape).background(Purple100)
                                    )
                                }
                            }
                            Text(
                                if (day.date != null) "${day.day}" else "",
                                fontSize = 14.sp,
                                fontWeight = if (day.isActive || day.isToday || day.isSelected) FontWeight.Medium else FontWeight.Normal,
                                color = when {
                                    day.isSelected -> Color.White
                                    day.isActive -> Color.White
                                    day.date == null -> Color.Transparent
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Personal Records Section
// ---------------------------------------------------------------------------

@Composable
private fun PersonalRecordsSection(
    records: List<PersonalRecord>,
    selectedFilter: String,
    onFilterChange: (String) -> Unit,
    filterEnabled: Boolean = true,
    expandedIds: Set<String>,
    onToggleExpand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showFilterMenu by remember { mutableStateOf(false) }
    val filters = listOf("Tüm zamanlar", "Bu ay", "Bu hafta")

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title row
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Kişisel Rekor",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            // Filter dropdown
            if (filterEnabled) {
                Box {
                    Row(
                        modifier = Modifier.clickable { showFilterMenu = true },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(selectedFilter, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(
                        expanded = showFilterMenu,
                        onDismissRequest = { showFilterMenu = false }
                    ) {
                        filters.forEach { filter ->
                            DropdownMenuItem(
                                text = { Text(filter, fontSize = 14.sp) },
                                onClick = { onFilterChange(filter); showFilterMenu = false }
                            )
                        }
                    }
                }
            } else {
                Text(selectedFilter, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Records list
        if (records.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Henüz rekor yok", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Rekor denemesi yaparak\nkişisel rekorlarınızı kırın!",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                records.forEach { record ->
                    RecordCard(
                        record = record,
                        isExpanded = expandedIds.contains(record.id),
                        onTap = { onToggleExpand(record.id) }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Workout history + selected day
// ---------------------------------------------------------------------------

@Composable
private fun WorkoutHistorySection(
    workouts: List<TrainingSession>,
    selectedFilter: String,
    onFilterChange: (String) -> Unit,
    daySelected: Boolean,
    expandedIds: Set<String>,
    onToggleExpand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showFilterMenu by remember { mutableStateOf(false) }
    val filters = listOf("Bu hafta", "Bu ay")

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Antrenmanlar",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (!daySelected) {
                Box {
                    Row(
                        modifier = Modifier.clickable { showFilterMenu = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(selectedFilter, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = showFilterMenu, onDismissRequest = { showFilterMenu = false }) {
                        filters.forEach { f ->
                            DropdownMenuItem(text = { Text(f) }, onClick = { onFilterChange(f); showFilterMenu = false })
                        }
                    }
                }
            }
        }

        if (workouts.isEmpty()) {
            Text(
                "Bu dönemde tamamlanan antrenman yok.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                workouts.take(20).forEach { session ->
                    WorkoutSessionCard(
                        session = session,
                        isExpanded = expandedIds.contains(session.id),
                        onTap = { onToggleExpand(session.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedDaySection(
    date: LocalDate,
    workouts: List<TrainingSession>,
    attempts: List<RecordAttempt>,
    exerciseNames: Map<String, String>,
    expandedWorkoutIds: Set<String>,
    onToggleWorkoutExpand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val title = remember(date) {
        date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("tr")))
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        if (workouts.isEmpty() && attempts.isEmpty()) {
            Text("Bu gün için aktivite kaydı yok.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        workouts.forEach { session ->
            WorkoutSessionCard(
                session = session,
                isExpanded = expandedWorkoutIds.contains(session.id),
                onTap = { onToggleWorkoutExpand(session.id) }
            )
        }
        attempts.forEach { attempt ->
            val name = exerciseNames[attempt.exerciseId] ?: "Rekor denemesi"
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(name, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (attempt.success) "Başarılı" else "Deneme",
                    fontSize = 12.sp,
                    color = if (attempt.success) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Workout Session Card — expandable, mirrors RecordCard layout
// ---------------------------------------------------------------------------

private data class WorkoutDetailRow(val label: String, val value: String)

private fun formatWorkoutDateText(completedAt: String?): String {
    val date = parseActivityDate(completedAt) ?: return "—"
    val dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("tr"))
        .replaceFirstChar { it.uppercase() }
    val monthName = date.month.getDisplayName(TextStyle.FULL, Locale("tr"))
        .replaceFirstChar { it.uppercase() }
    return "$dayName, $monthName ${date.dayOfMonth}, ${date.year}"
}

private fun formatCompletionClockTime(completedAt: String?): String? = runCatching {
    Instant.parse(completedAt).atZone(ZoneId.systemDefault()).toLocalTime()
        .format(DateTimeFormatter.ofPattern("HH:mm"))
}.getOrNull()

private fun workoutDetailRows(session: TrainingSession): List<WorkoutDetailRow> = buildList {
    session.recordedDurationSeconds?.let { seconds ->
        add(WorkoutDetailRow("Süre", FormatHelpers.formatDuration(seconds)))
    }
    session.recordedDistanceKm?.takeIf { it > 0.01 }?.let { km ->
        add(WorkoutDetailRow("Mesafe", "%.2f km".format(km)))
        val seconds = session.recordedDurationSeconds ?: 0
        if (seconds > 0) {
            val paceMinPerKm = (seconds / 60.0) / km
            if (paceMinPerKm > 0.1) {
                val min = paceMinPerKm.toInt()
                val sec = ((paceMinPerKm - min) * 60).toInt()
                add(WorkoutDetailRow("Tempo", "%d'%02d\" /km".format(min, sec)))
            }
        }
    }
    val cal = session.calories ?: 0
    add(WorkoutDetailRow("Kalori", "$cal kcal"))

    val hr = session.avgHeartRate ?: 0
    add(WorkoutDetailRow("Ort. Nabız", "$hr bpm"))
    formatCompletionClockTime(session.completedAt)?.let {
        add(WorkoutDetailRow("Bitiş saati", it))
    }
    session.program?.name?.takeIf { session.resolvedActivityCategory == null }?.let {
        add(WorkoutDetailRow("Program", it))
    }
}

@Composable
private fun WorkoutSessionCard(
    session: TrainingSession,
    isExpanded: Boolean,
    onTap: () -> Unit
) {
    val dateText = remember(session.completedAt) { formatWorkoutDateText(session.completedAt) }
    val durationText = session.recordedDurationSeconds?.let { FormatHelpers.formatDuration(it) }
    val distanceText = session.recordedDistanceKm?.takeIf { it > 0.01 }?.let { "%.2f km".format(it) }
    val details = remember(session.id, session.completedAt, session.notes) { workoutDetailRows(session) }

    val headerShape = if (isExpanded) {
        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
    } else {
        RoundedCornerShape(12.dp)
    }
    val bodyShape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(headerShape)
                .background(Purple100)
                .clickable { onTap() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    session.activityDisplayName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1
                )
                Text(dateText, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
            }

            if (!isExpanded) {
                val trailing = durationText ?: distanceText
                if (trailing != null) {
                    Text(
                        trailing,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Primary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            Icon(
                if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(200)),
            exit = shrinkVertically(animationSpec = tween(200))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(bodyShape)
                    .background(PurpleSecondary)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (details.isEmpty()) {
                    Text(
                        "Detay bulunamadı",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                } else {
                    details.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                row.label,
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                            Text(
                                row.value,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Primary
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Record Card — expandable, mirrors iOS RecordCard exactly
// ---------------------------------------------------------------------------

@Composable
private fun RecordCard(
    record: PersonalRecord,
    isExpanded: Boolean,
    onTap: () -> Unit
) {
    val exerciseName = record.exercise?.name ?: "Egzersiz"

    // Format date: "Çar, Mayıs 26, 2026" style
    val dateText = remember(record.recordDate) {
        try {
            val d = LocalDate.parse(record.recordDate.take(10))
            val dayName = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("tr"))
                .replaceFirstChar { it.uppercase() }
            val monthName = d.month.getDisplayName(TextStyle.FULL, Locale("tr"))
                .replaceFirstChar { it.uppercase() }
            "$dayName, $monthName ${d.dayOfMonth}, ${d.year}"
        } catch (_: Exception) {
            record.recordDate.take(10)
        }
    }

    val details = "${record.reps} x 1 / ${record.weight.toInt()} kg"

    val topShape = RoundedCornerShape(12.dp)
    val headerShape = if (isExpanded)
        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
    else
        RoundedCornerShape(12.dp)
    val bodyShape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)

    Column {
        // Header row (Purple100)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(headerShape)
                .background(Purple100)
                .clickable { onTap() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    exerciseName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(dateText, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                Icon(
                    if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Expanded body (PurpleSecondary)
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(200)),
            exit = shrinkVertically(animationSpec = tween(200))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(bodyShape)
                    .background(PurpleSecondary)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(exerciseName, fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f))
                Text(details, fontSize = 13.sp, color = Primary)
            }
        }
    }
}
