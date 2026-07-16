package com.app.coachup.app.ui.nutrition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.app.coachup.app.models.NutritionLog
import com.app.coachup.app.models.NutritionMeal
import com.app.coachup.app.models.NutritionPlan
import com.app.coachup.app.models.UserNutritionPlan
import androidx.compose.foundation.clickable
import com.app.coachup.app.services.AuthService
import com.app.coachup.app.services.FatSecretFood
import com.app.coachup.app.services.FatSecretService
import com.app.coachup.app.services.NutritionService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.app.coachup.app.theme.*
import kotlinx.coroutines.launch

@Composable
fun NutritionScreen(navController: NavController) {
    var isLoading by remember { mutableStateOf(true) }
    var activePlan by remember { mutableStateOf<UserNutritionPlan?>(null) }
    var planDetails by remember { mutableStateOf<NutritionPlan?>(null) }
    var meals by remember { mutableStateOf<List<NutritionMeal>>(emptyList()) }
    var todayLogs by remember { mutableStateOf<List<NutritionLog>>(emptyList()) }
    var showLogDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val todayCalories = todayLogs.sumOf { it.calories }
    val todayProtein = todayLogs.sumOf { it.protein }
    val todayCarbs = todayLogs.sumOf { it.carbs }
    val todayFat = todayLogs.sumOf { it.fat }
    val targetCalories = planDetails?.targetCalories ?: 2000

    suspend fun loadData() {
        val userId = AuthService.getCurrentUserId() ?: return
        isLoading = true
        try {
            activePlan = NutritionService.fetchActivePlan(userId)
            activePlan?.planId?.let { planId ->
                planDetails = NutritionService.fetchPlanDetails(planId)
                meals = NutritionService.fetchMeals(planId)
            }
            todayLogs = NutritionService.fetchTodayLogs(userId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadData() }

    if (showLogDialog) {
        LogNutritionDialog(
            planId = activePlan?.planId,
            onDismiss = { showLogDialog = false },
            onSaved = {
                showLogDialog = false
                coroutineScope.launch { loadData() }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
                modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(text = "Beslenme", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            IconButton(onClick = { showLogDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Kayıt Ekle", tint = Primary)
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    CalorieRingSection(todayCalories = todayCalories, targetCalories = targetCalories, planName = planDetails?.name)
                }
                item {
                    MacroBreakdownRow(protein = todayProtein, carbs = todayCarbs, fat = todayFat)
                }
                if (meals.isNotEmpty()) {
                    item { MealPlanSection(meals = meals, planName = planDetails?.name) }
                }
                item { TodayLogsSection(logs = todayLogs) }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun CalorieRingSection(todayCalories: Int, targetCalories: Int, planName: String?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
            val progress = if (targetCalories > 0) (todayCalories.toFloat() / targetCalories).coerceIn(0f, 1f) else 0f
            Canvas(modifier = Modifier.size(160.dp)) {
                val strokeWidth = 20f
                val radius = (size.minDimension - strokeWidth) / 2
                drawCircle(
                    color = Color(0xFFE8E8E8),
                    radius = radius,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                drawArc(
                    color = Color(0xFFFF6047),
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "$todayCalories", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Text(text = "/ $targetCalories kcal", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            }
        }
        planName?.let { Text(text = it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)) }
    }
}

@Composable
private fun MacroBreakdownRow(protein: Double, carbs: Double, fat: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MacroItem(label = "Protein", value = protein, color = Color(0xFF4A90E2))
        Box(modifier = Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)))
        MacroItem(label = "Karb.", value = carbs, color = Color(0xFFF5A623))
        Box(modifier = Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)))
        MacroItem(label = "Yağ", value = fat, color = Color(0xFF7ED321))
    }
}

@Composable
private fun MacroItem(label: String, value: Double, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = String.format("%.1fg", value), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
    }
}

@Composable
private fun MealPlanSection(meals: List<NutritionMeal>, planName: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Restaurant, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
            Text(text = "Öğün Planı", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            planName?.let { Text(text = it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)) }
        }
        meals.forEach { meal ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.card))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = meal.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                    meal.description?.takeIf { it.isNotEmpty() }?.let {
                        Text(text = it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), maxLines = 1)
                    }
                }
                Text(text = "${meal.calories} kcal", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Primary)
            }
        }
    }
}

@Composable
private fun TodayLogsSection(logs: List<NutritionLog>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.ListAlt, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
            Text(text = "Bugünün Kayıtları", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            Text(text = "${logs.size} kayıt", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
        }
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(MaterialTheme.colorScheme.surface).padding(24.dp),
                contentAlignment = Alignment.Center
            ) { Text(text = "Henüz kayıt yok", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)) }
        } else {
            logs.forEach { log ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.card))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = log.notes?.takeIf { it.isNotBlank() } ?: mealTypeName(log.mealType), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Primary.copy(alpha = 0.15f)).padding(horizontal = 7.dp, vertical = 3.dp)) {
                                Text(text = mealTypeName(log.mealType), fontSize = 11.sp, color = Primary, fontWeight = FontWeight.Medium)
                            }
                            Text(text = "P:${log.protein.toInt()}g · K:${log.carbs.toInt()}g · Y:${log.fat.toInt()}g", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "${log.calories}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Primary)
                        Text(text = "kcal", fontSize = 10.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}

private fun mealTypeName(type: String): String = when (type) {
    "kahvalti" -> "Kahvaltı"
    "ogle" -> "Öğle"
    "aksam" -> "Akşam"
    "ara_ogun" -> "Ara Öğün"
    else -> type.replaceFirstChar { it.uppercase() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogNutritionDialog(planId: String?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var selectedMealType by remember { mutableStateOf("kahvalti") }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var mealTypeExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<FatSecretFood>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val mealTypes = listOf("kahvalti" to "Kahvaltı", "ogle" to "Öğle", "aksam" to "Akşam", "ara_ogun" to "Ara Öğün")

    fun selectFood(food: FatSecretFood) {
        calories = "${food.calories}"
        protein = "%.1f".format(food.protein)
        carbs = "%.1f".format(food.carbs)
        fat = "%.1f".format(food.fat)
        notes = food.name
        searchResults = emptyList()
        searchQuery = ""
    }

    fun triggerSearch(query: String) {
        searchJob?.cancel()
        if (query.length < 2) { searchResults = emptyList(); return }
        searchJob = coroutineScope.launch {
            delay(500)
            isSearching = true
            try {
                searchResults = FatSecretService.searchFoods(query)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                isSearching = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yemek Kaydı", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Food search
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it; triggerSearch(it) },
                    label = { Text("Yemek Ara (FatSecret)") },
                    trailingIcon = {
                        if (isSearching) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Primary, strokeWidth = 2.dp)
                        else Icon(Icons.Default.Search, contentDescription = null)
                    },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (searchResults.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        searchResults.take(5).forEach { food ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .clickable { selectFood(food) }
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = food.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                                    Text(text = "P:%.0fg  K:%.0fg  Y:%.0fg".format(food.protein, food.carbs, food.fat), fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                                }
                                Text(text = "${food.calories} kcal", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Primary)
                            }
                        }
                    }
                }

                ExposedDropdownMenuBox(expanded = mealTypeExpanded, onExpandedChange = { mealTypeExpanded = it }) {
                    OutlinedTextField(
                        value = mealTypes.find { it.first == selectedMealType }?.second ?: "Kahvaltı",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Öğün") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mealTypeExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = mealTypeExpanded, onDismissRequest = { mealTypeExpanded = false }) {
                        mealTypes.forEach { (key, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { selectedMealType = key; mealTypeExpanded = false })
                        }
                    }
                }
                OutlinedTextField(value = calories, onValueChange = { calories = it }, label = { Text("Kalori (kcal)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary), singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = protein, onValueChange = { protein = it }, label = { Text("Protein (g)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary), singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = carbs, onValueChange = { carbs = it }, label = { Text("Karb. (g)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary), singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = fat, onValueChange = { fat = it }, label = { Text("Yağ (g)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary), singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Not") }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary), singleLine = true, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cal = calories.toIntOrNull() ?: return@TextButton
                    coroutineScope.launch {
                        isSaving = true
                        try {
                            val userId = AuthService.getCurrentUserId() ?: return@launch
                            NutritionService.logNutrition(userId = userId, planId = planId, mealType = selectedMealType, calories = cal, protein = protein.toDoubleOrNull() ?: 0.0, carbs = carbs.toDoubleOrNull() ?: 0.0, fat = fat.toDoubleOrNull() ?: 0.0, notes = notes.takeIf { it.isNotBlank() })
                            onSaved()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                        } finally {
                            isSaving = false
                        }
                    }
                },
                enabled = calories.isNotBlank() && !isSaving
            ) {
                if (isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Primary, strokeWidth = 2.dp)
                else Text("Kaydet", color = Primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}
