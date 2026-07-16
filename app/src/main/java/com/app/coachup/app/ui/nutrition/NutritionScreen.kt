package com.app.coachup.app.ui.nutrition

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.app.coachup.app.models.NutritionFood
import com.app.coachup.app.models.NutritionLog
import com.app.coachup.app.models.NutritionMeal
import com.app.coachup.app.models.NutritionPlan
import com.app.coachup.app.models.UserNutritionPlan
import com.app.coachup.app.services.AuthService
import com.app.coachup.app.services.FatSecretFood
import com.app.coachup.app.services.FatSecretService
import com.app.coachup.app.services.NutritionService
import com.app.coachup.app.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Meal visual meta (aligned with web SalonAINutrition)
// ---------------------------------------------------------------------------

private data class MealMeta(
    val emoji: String,
    val color: Color,
    val label: String
)

private val MealMetaBreakfast = MealMeta("🌅", Color(0xFFFAAD14), "Kahvaltı")
private val MealMetaLunch = MealMeta("☀️", Color(0xFFFF6047), "Öğle Yemeği")
private val MealMetaDinner = MealMeta("🌙", Color(0xFF722ED1), "Akşam Yemeği")
private val MealMetaSnack = MealMeta("🍎", Color(0xFF52C41A), "Ara Öğün")
private val MealMetaPre = MealMeta("⚡", Color(0xFF4FACFE), "Antrenman Öncesi")
private val MealMetaPost = MealMeta("💪", Color(0xFF13C2C2), "Antrenman Sonrası")
private val MealMetaDefault = MealMeta("🍽️", Color(0xFFFF6047), "Öğün")

private fun mealMeta(type: String?, name: String?): MealMeta {
    val key = (type ?: "").lowercase().replace(Regex("[\\s-]+"), "_")
    when (key) {
        "kahvalti", "breakfast" -> return MealMetaBreakfast
        "ogle", "öğle", "lunch" -> return MealMetaLunch
        "aksam", "akşam", "dinner", "supper" -> return MealMetaDinner
        "ara_ogun", "ara_öğün", "snack" -> return MealMetaSnack
        "pre_workout", "antrenman_oncesi" -> return MealMetaPre
        "post_workout", "antrenman_sonrasi" -> return MealMetaPost
    }
    val n = (name ?: "").lowercase()
    return when {
        n.contains("kahvalt") || n.contains("breakfast") -> MealMetaBreakfast
        n.contains("öğle") || n.contains("ogle") || n.contains("lunch") -> MealMetaLunch
        n.contains("akşam") || n.contains("aksam") || n.contains("dinner") -> MealMetaDinner
        n.contains("öncesi") || n.contains("oncesi") || n.contains("pre") -> MealMetaPre
        n.contains("sonrası") || n.contains("sonrasi") || n.contains("post") -> MealMetaPost
        n.contains("ara") || n.contains("snack") || n.contains("atış") -> MealMetaSnack
        else -> MealMetaDefault
    }
}

private fun mealTypeName(type: String?): String = when (type?.lowercase()) {
    "kahvalti", "breakfast" -> "Kahvaltı"
    "ogle", "öğle", "lunch" -> "Öğle"
    "aksam", "akşam", "dinner" -> "Akşam"
    "ara_ogun", "snack" -> "Ara Öğün"
    "pre_workout" -> "Ant. Öncesi"
    "post_workout" -> "Ant. Sonrası"
    else -> type?.replaceFirstChar { it.uppercase() } ?: "Öğün"
}

/** Parse free-text description into food-like lines when structured foods are missing. */
private fun parseDescriptionLines(description: String?): List<String> {
    if (description.isNullOrBlank()) return emptyList()
    return description
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { line ->
            line
                .removePrefix("-")
                .removePrefix("•")
                .removePrefix("*")
                .trim()
                .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
                .replace(Regex("__(.+?)__"), "$1")
        }
        .filter { it.isNotEmpty() && !it.startsWith("|") && it != "---" }
}

@Composable
fun NutritionScreen(navController: NavController) {
    var isLoading by remember { mutableStateOf(true) }
    var activePlan by remember { mutableStateOf<UserNutritionPlan?>(null) }
    var planDetails by remember { mutableStateOf<NutritionPlan?>(null) }
    var mealsWithFoods by remember { mutableStateOf<List<NutritionService.MealWithFoods>>(emptyList()) }
    var todayLogs by remember { mutableStateOf<List<NutritionLog>>(emptyList()) }
    var showLogDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val todayCalories = todayLogs.sumOf { it.calories }
    val todayProtein = todayLogs.sumOf { it.protein }
    val todayCarbs = todayLogs.sumOf { it.carbs }
    val todayFat = todayLogs.sumOf { it.fat }
    val targetCalories = planDetails?.targetCalories?.takeIf { it > 0 } ?: 2000

    val planProtein = mealsWithFoods.sumOf { it.meal.protein }
    val planCarbs = mealsWithFoods.sumOf { it.meal.carbs }
    val planFat = mealsWithFoods.sumOf { it.meal.fat }
    val planMealCalories = mealsWithFoods.sumOf {
        val fromFoods = it.foods.sumOf { f -> f.calories }
        if (fromFoods > 0) fromFoods else it.meal.calories
    }

    suspend fun loadData() {
        val userId = AuthService.getCurrentUserId() ?: return
        isLoading = true
        try {
            activePlan = NutritionService.fetchActivePlan(userId)
            activePlan?.planId?.let { planId ->
                planDetails = NutritionService.fetchPlanDetails(planId)
                mealsWithFoods = NutritionService.fetchMealsWithFoods(planId)
            } ?: run {
                planDetails = null
                mealsWithFoods = emptyList()
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
            Text(
                text = "Beslenme",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
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
                    CalorieRingSection(
                        todayCalories = todayCalories,
                        targetCalories = targetCalories,
                        planName = planDetails?.name
                    )
                }
                item {
                    MacroBreakdownRow(protein = todayProtein, carbs = todayCarbs, fat = todayFat)
                }
                if (mealsWithFoods.isNotEmpty() || planDetails != null) {
                    item {
                        PlanOverviewCard(
                            plan = planDetails,
                            mealCount = mealsWithFoods.size,
                            totalCalories = planMealCalories.takeIf { it > 0 } ?: targetCalories,
                            protein = planProtein,
                            carbs = planCarbs,
                            fat = planFat
                        )
                    }
                }
                if (mealsWithFoods.isNotEmpty()) {
                    item {
                        MealPlanSection(meals = mealsWithFoods)
                    }
                } else if (activePlan == null) {
                    item { EmptyPlanCard() }
                }
                item { TodayLogsSection(logs = todayLogs) }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Header / macros / empty
// ---------------------------------------------------------------------------

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
        planName?.let {
            Text(
                text = it,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
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
        Text(
            text = String.format("%.1fg", value),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
    }
}

@Composable
private fun PlanOverviewCard(
    plan: NutritionPlan?,
    mealCount: Int,
    totalCalories: Int,
    protein: Double,
    carbs: Double,
    fat: Double
) {
    val onBg = MaterialTheme.colorScheme.onBackground
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Star, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
            Text(
                text = plan?.name ?: "Beslenme Planı",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = onBg,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Surface(
                color = Primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "$mealCount öğün",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        plan?.description?.takeIf { it.isNotBlank() }?.let { desc ->
            Text(
                text = desc,
                fontSize = 13.sp,
                color = onBg.copy(alpha = 0.55f),
                lineHeight = 18.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlanStatChip(
                label = "Kalori",
                value = "$totalCalories",
                unit = "kcal",
                color = Primary,
                modifier = Modifier.weight(1f)
            )
            PlanStatChip(
                label = "Protein",
                value = if (protein > 0) protein.toInt().toString() else "—",
                unit = "g",
                color = Color(0xFF4FACFE),
                modifier = Modifier.weight(1f)
            )
            PlanStatChip(
                label = "Karb",
                value = if (carbs > 0) carbs.toInt().toString() else "—",
                unit = "g",
                color = Color(0xFF52C41A),
                modifier = Modifier.weight(1f)
            )
            PlanStatChip(
                label = "Yağ",
                value = if (fat > 0) fat.toInt().toString() else "—",
                unit = "g",
                color = Color(0xFF722ED1),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PlanStatChip(
    label: String,
    value: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(text = value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        Text(text = unit, fontSize = 10.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f))
    }
}

@Composable
private fun EmptyPlanCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surface)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "🥗", fontSize = 36.sp)
        Text(
            text = "Aktif beslenme planın yok",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Salonun sana bir plan atadığında burada modern kartlar halinde görünecek.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            lineHeight = 18.sp
        )
    }
}

// ---------------------------------------------------------------------------
// Meal plan cards
// ---------------------------------------------------------------------------

@Composable
private fun MealPlanSection(meals: List<NutritionService.MealWithFoods>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Restaurant, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
            Text(
                text = "Öğün Planı",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            Surface(
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "${meals.size} öğün",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        meals.forEach { item ->
            ModernMealCard(meal = item.meal, foods = item.foods)
        }
    }
}

@Composable
private fun ModernMealCard(meal: NutritionMeal, foods: List<NutritionFood>) {
    var expanded by remember(meal.id) { mutableStateOf(true) }
    val meta = mealMeta(meal.mealType, meal.name)
    val onBg = MaterialTheme.colorScheme.onBackground
    val surface = MaterialTheme.colorScheme.surface

    val foodLines = remember(meal.id, foods, meal.description) {
        if (foods.isNotEmpty()) emptyList() else parseDescriptionLines(meal.description)
    }
    val hasDetails = foods.isNotEmpty() || foodLines.isNotEmpty() ||
        meal.protein > 0 || meal.carbs > 0 || meal.fat > 0 || !meal.description.isNullOrBlank()

    val displayCalories = when {
        meal.calories > 0 -> meal.calories
        foods.isNotEmpty() -> foods.sumOf { it.calories }
        else -> 0
    }
    val displayProtein = if (meal.protein > 0) meal.protein else foods.sumOf { it.protein }
    val displayCarbs = if (meal.carbs > 0) meal.carbs else foods.sumOf { it.carbs }
    val displayFat = if (meal.fat > 0) meal.fat else foods.sumOf { it.fat }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(surface)
            .border(1.dp, onBg.copy(alpha = 0.06f), RoundedCornerShape(Radius.card))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(meta.color.copy(alpha = 0.14f), Color.Transparent)
                    )
                )
                .clickable(enabled = hasDetails) { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(meta.color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = meta.emoji, fontSize = 20.sp)
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = meal.name.ifBlank { meta.label },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = onBg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    meal.time?.takeIf { it.isNotBlank() && it != "00:00" }?.let { time ->
                        Text(
                            text = time,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = onBg.copy(alpha = 0.5f)
                        )
                    }
                }
                Text(
                    text = mealTypeName(meal.mealType).takeIf { meal.mealType != null } ?: meta.label,
                    fontSize = 12.sp,
                    color = onBg.copy(alpha = 0.5f)
                )
            }

            if (displayCalories > 0) {
                Surface(
                    color = meta.color.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Whatshot, contentDescription = null, tint = meta.color, modifier = Modifier.size(14.dp))
                        Text(
                            text = "~$displayCalories",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = meta.color
                        )
                    }
                }
            }

            if (hasDetails) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Daralt" else "Genişlet",
                    tint = onBg.copy(alpha = 0.4f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = expanded && hasDetails,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 12.dp, top = 2.dp)
            ) {
                HorizontalDivider(color = onBg.copy(alpha = 0.06f))

                if (foods.isNotEmpty()) {
                    foods.forEachIndexed { index, food ->
                        FoodRow(food = food, showDivider = index < foods.lastIndex)
                    }
                } else if (foodLines.isNotEmpty()) {
                    foodLines.forEachIndexed { index, line ->
                        DescriptionFoodLine(line = line, showDivider = index < foodLines.lastIndex)
                    }
                } else if (!meal.description.isNullOrBlank()) {
                    Text(
                        text = meal.description,
                        fontSize = 13.sp,
                        color = onBg.copy(alpha = 0.7f),
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }

                if (displayProtein > 0 || displayCarbs > 0 || displayFat > 0) {
                    HorizontalDivider(
                        color = onBg.copy(alpha = 0.06f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = "Öğün makroları",
                        fontSize = 11.sp,
                        color = onBg.copy(alpha = 0.45f),
                        modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (displayProtein > 0) {
                            MacroTag(text = "Protein ${displayProtein.toInt()}g", color = Color(0xFF4FACFE))
                        }
                        if (displayCarbs > 0) {
                            MacroTag(text = "Karb ${displayCarbs.toInt()}g", color = Color(0xFF52C41A))
                        }
                        if (displayFat > 0) {
                            MacroTag(text = "Yağ ${displayFat.toInt()}g", color = Color(0xFF722ED1))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FoodRow(food: NutritionFood, showDivider: Boolean) {
    val onBg = MaterialTheme.colorScheme.onBackground
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                Text(
                    text = listOfNotNull(food.emoji?.takeIf { it.isNotBlank() }, food.name)
                        .joinToString(" "),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = onBg,
                    lineHeight = 18.sp
                )
                food.portion?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        fontSize = 12.sp,
                        color = onBg.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (food.calories > 0) {
                    MacroTag(text = "${food.calories} kcal", color = Color(0xFFFA8C16), compact = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (food.protein > 0) MacroTag(text = "P ${food.protein.toInt()}g", color = Color(0xFF4FACFE), compact = true)
                    if (food.carbs > 0) MacroTag(text = "K ${food.carbs.toInt()}g", color = Color(0xFF52C41A), compact = true)
                    if (food.fat > 0) MacroTag(text = "Y ${food.fat.toInt()}g", color = Color(0xFF722ED1), compact = true)
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(color = onBg.copy(alpha = 0.05f))
        }
    }
}

@Composable
private fun DescriptionFoodLine(line: String, showDivider: Boolean) {
    val onBg = MaterialTheme.colorScheme.onBackground
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Primary.copy(alpha = 0.7f))
            )
            Text(
                text = line,
                fontSize = 13.sp,
                color = onBg.copy(alpha = 0.85f),
                lineHeight = 18.sp,
                modifier = Modifier.weight(1f)
            )
        }
        if (showDivider) {
            HorizontalDivider(color = onBg.copy(alpha = 0.05f))
        }
    }
}

@Composable
private fun MacroTag(text: String, color: Color, compact: Boolean = false) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(if (compact) 6.dp else 8.dp)
    ) {
        Text(
            text = text,
            fontSize = if (compact) 10.sp else 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(
                horizontal = if (compact) 6.dp else 8.dp,
                vertical = if (compact) 3.dp else 4.dp
            )
        )
    }
}

// ---------------------------------------------------------------------------
// Today logs
// ---------------------------------------------------------------------------

@Composable
private fun TodayLogsSection(logs: List<NutritionLog>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.List, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
            Text(
                text = "Bugünün Kayıtları",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${logs.size} kayıt",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.card))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Henüz kayıt yok — + ile ekleyebilirsin",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        } else {
            logs.forEach { log ->
                val meta = mealMeta(log.mealType, log.notes)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.card))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f), RoundedCornerShape(Radius.card))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(meta.color.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = meta.emoji, fontSize = 16.sp)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = log.notes?.takeIf { it.isNotBlank() } ?: mealTypeName(log.mealType),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = meta.color.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text(
                                        text = mealTypeName(log.mealType),
                                        fontSize = 11.sp,
                                        color = meta.color,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                                Text(
                                    text = "P:${log.protein.toInt()}g · K:${log.carbs.toInt()}g · Y:${log.fat.toInt()}g",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                )
                            }
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

// ---------------------------------------------------------------------------
// Log dialog
// ---------------------------------------------------------------------------

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

    val mealTypes = listOf(
        "kahvalti" to "Kahvaltı",
        "ogle" to "Öğle",
        "aksam" to "Akşam",
        "ara_ogun" to "Ara Öğün"
    )

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
        if (query.length < 2) {
            searchResults = emptyList()
            return
        }
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
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        triggerSearch(it)
                    },
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .clickable { selectFood(food) }
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = food.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = "P:%.0fg  K:%.0fg  Y:%.0fg".format(food.protein, food.carbs, food.fat),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                    )
                                }
                                Text(
                                    text = "${food.calories} kcal",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Primary
                                )
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
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedMealType = key
                                    mealTypeExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = calories,
                    onValueChange = { calories = it },
                    label = { Text("Kalori (kcal)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = protein,
                        onValueChange = { protein = it },
                        label = { Text("Protein (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = carbs,
                        onValueChange = { carbs = it },
                        label = { Text("Karb. (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = fat,
                        onValueChange = { fat = it },
                        label = { Text("Yağ (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Not") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
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
                            NutritionService.logNutrition(
                                userId = userId,
                                planId = planId,
                                mealType = selectedMealType,
                                calories = cal,
                                protein = protein.toDoubleOrNull() ?: 0.0,
                                carbs = carbs.toDoubleOrNull() ?: 0.0,
                                fat = fat.toDoubleOrNull() ?: 0.0,
                                notes = notes.takeIf { it.isNotBlank() }
                            )
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
