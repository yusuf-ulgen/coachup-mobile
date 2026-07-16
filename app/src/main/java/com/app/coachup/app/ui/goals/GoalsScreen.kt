package com.app.coachup.app.ui.goals

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
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.app.coachup.app.models.UserGoal
import com.app.coachup.app.services.AuthService
import com.app.coachup.app.services.GoalService
import com.app.coachup.app.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.Instant
import java.time.format.DateTimeFormatter

// ---------------------------------------------------------------------------
// Icon mapping helper
// ---------------------------------------------------------------------------

private fun goalTypeIcon(type: String): ImageVector = when (type) {
    "weight_loss"  -> Icons.Default.TrendingDown
    "weight_gain"  -> Icons.Default.TrendingUp
    "muscle_gain"  -> Icons.Default.FitnessCenter
    "endurance"    -> Icons.Default.DirectionsRun
    "flexibility"  -> Icons.Default.SelfImprovement
    "attendance"   -> Icons.Default.CalendarMonth
    else           -> Icons.Default.Star
}

private fun goalStatusFilter(goal: UserGoal, tabIndex: Int): Boolean {
    val normalized = when (goal.status) {
        "active" -> "in_progress"
        else -> goal.status
    }
    return if (tabIndex == 0) normalized == "in_progress" else normalized == "completed"
}

private fun datePickerMillisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

private fun goalTypeLabel(type: String): String = when (type) {
    "weight_loss"  -> "Kilo Verme"
    "weight_gain"  -> "Kilo Alma"
    "muscle_gain"  -> "Kas Geliştirme"
    "endurance"    -> "Dayanıklılık"
    "flexibility"  -> "Esneklik"
    "attendance"   -> "Devam"
    else           -> "Özel"
}

// ---------------------------------------------------------------------------
// GoalsScreen
// ---------------------------------------------------------------------------

@Composable
fun GoalsScreen(navController: NavController) {
    var selectedTab by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var goals by remember { mutableStateOf<List<UserGoal>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val tabs = listOf("Aktif", "Tamamlanan")

    suspend fun loadGoals() {
        val userId = AuthService.getCurrentUserId() ?: return
        isLoading = true
        errorMessage = null
        try {
            goals = GoalService.fetchGoals(userId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMessage = "Hedefler yüklenemedi"
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadGoals()
    }

    if (showAddDialog) {
        AddGoalDialog(
            onDismiss = { showAddDialog = false },
            onSave = {
                showAddDialog = false
                coroutineScope.launch { loadGoals() }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Spacing.xl)
                .padding(top = Spacing.sm, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Geri",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(
                text = "Hedeflerim",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Hedef Ekle", tint = Primary)
            }
        }

        // Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = Primary,
            indicator = { tabPositions ->
                if (selectedTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = Primary
                    )
                }
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            title,
                            fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selectedTab == index) Primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            isLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = Primary) }

            errorMessage != null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = errorMessage!!,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                    TextButton(onClick = { coroutineScope.launch { loadGoals() } }) {
                        Text("Tekrar Dene", color = Primary)
                    }
                }
            }

            else -> {
                val filteredGoals = goals.filter { goalStatusFilter(it, selectedTab) }

                if (filteredGoals.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                if (selectedTab == 0) Icons.Default.Flag else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                                modifier = Modifier.size(64.dp)
                            )
                            Text(
                                text = if (selectedTab == 0) "Aktif hedef yok" else "Tamamlanan hedef yok",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                            if (selectedTab == 0) {
                                TextButton(onClick = { showAddDialog = true }) {
                                    Text("Hedef Ekle", color = Primary)
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = Spacing.xl),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredGoals) { goal ->
                            GoalCard(
                                goal = goal,
                                onComplete = {
                                    coroutineScope.launch {
                                        val userId = AuthService.getCurrentUserId() ?: return@launch
                                        try {
                                            GoalService.completeGoal(goal.id, userId)
                                            loadGoals()
                                        } catch (_: Exception) {}
                                    }
                                }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// GoalCard
// ---------------------------------------------------------------------------

@Composable
private fun GoalCard(goal: UserGoal, onComplete: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = goalTypeIcon(goal.type),
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = goal.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = goalTypeLabel(goal.type),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
            if (goal.status == "in_progress" || goal.status == "active") {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(PrimaryLight)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(text = "Aktif", fontSize = 11.sp, color = Primary)
                }
            }
        }

        // Progress bar
        val progress = (goal.progressPercentage / 100f).coerceIn(0f, 1f)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                goal.currentValue?.let { current ->
                    goal.unit?.let { unit ->
                        Text(
                            text = "$current $unit",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                }
                goal.targetValue?.let { target ->
                    goal.unit?.let { unit ->
                        Text(
                            text = "Hedef: $target $unit",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Primary
                        )
                    }
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (goal.status == "completed") SuccessGreen else Primary,
                trackColor = PrimaryLight
            )
            Text(
                text = "${goal.progressPercentage}% tamamlandı",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }

        // Target date
        goal.targetDate?.let { targetDate ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Hedef tarihi: ${targetDate.take(10)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                )
            }
        }

        // Complete button shown when progress is 100% and still in_progress
        if ((goal.status == "in_progress" || goal.status == "active") && goal.progressPercentage >= 100) {
            Button(
                onClick = onComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                shape = RoundedCornerShape(Radius.pill)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Tamamlandı Olarak İşaretle", fontSize = 13.sp, color = Color.White)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// AddGoalDialog
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddGoalDialog(onDismiss: () -> Unit, onSave: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("weight_loss") }
    var targetValue by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("kg") }
    var targetDate by remember { mutableStateOf<LocalDate?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var typeDropdownExpanded by remember { mutableStateOf(false) }

    val goalTypes = listOf(
        "weight_loss" to "Kilo Verme",
        "weight_gain" to "Kilo Alma",
        "muscle_gain" to "Kas Geliştirme",
        "endurance" to "Dayanıklılık",
        "flexibility" to "Esneklik",
        "attendance" to "Devam",
        "custom" to "Özel"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Yeni Hedef", fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // Goal type dropdown
                ExposedDropdownMenuBox(
                    expanded = typeDropdownExpanded,
                    onExpandedChange = { typeDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = goalTypes.firstOrNull { it.first == selectedType }?.second ?: selectedType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Hedef Türü") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeDropdownExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = typeDropdownExpanded,
                        onDismissRequest = { typeDropdownExpanded = false }
                    ) {
                        goalTypes.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedType = value
                                    typeDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Hedef Adı") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = targetValue,
                        onValueChange = { targetValue = it },
                        label = { Text("Hedef Değer") },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Birim") },
                        modifier = Modifier.width(80.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = targetDate?.format(DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale("tr"))) ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Hedef Tarihi") },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Tarih Seç", tint = Primary)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary)
                )

                if (showDatePicker) {
                    val datePickerState = rememberDatePickerState(
                        initialSelectedDateMillis = targetDate?.atStartOfDay(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                    )
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                datePickerState.selectedDateMillis?.let { millis ->
                                    targetDate = datePickerMillisToLocalDate(millis)
                                }
                                showDatePicker = false
                            }) { Text("Tamam", color = Primary) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDatePicker = false }) { Text("İptal") }
                        }
                    ) {
                        DatePicker(state = datePickerState)
                    }
                }

                saveError?.let { err ->
                    Text(text = err, fontSize = 12.sp, color = ErrorRed)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isBlank()) {
                        saveError = "Lütfen hedef adı girin"
                        return@TextButton
                    }
                    isSaving = true
                    coroutineScope.launch {
                        try {
                            val userId = AuthService.getCurrentUserId()
                            if (userId == null) {
                                saveError = "Kullanıcı bulunamadı"
                                isSaving = false
                                return@launch
                            }
                            val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
                            val selectedTargetDate = targetDate?.format(DateTimeFormatter.ISO_DATE)
                            GoalService.createGoal(
                                userId = userId,
                                type = selectedType,
                                title = title,
                                targetValue = targetValue.toDoubleOrNull(),
                                unit = unit.ifBlank { null },
                                startDate = today,
                                targetDate = selectedTargetDate
                            )
                            isSaving = false
                            onSave()
                        } catch (e: Exception) {
                            saveError = e.message ?: "Hedef kaydedilemedi"
                            isSaving = false
                        }
                    }
                },
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Primary, strokeWidth = 2.dp)
                } else {
                    Text("Kaydet", color = Primary)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        }
    )
}
