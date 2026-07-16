package com.app.coachup.app.ui.results

import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import com.app.coachup.app.models.Exercise
import com.app.coachup.app.models.ExerciseResult
import com.app.coachup.app.navigation.NavigationStateHolder
import com.app.coachup.app.navigation.Routes
import com.app.coachup.app.services.AuthService
import com.app.coachup.app.services.ResultsService
import com.app.coachup.app.theme.*
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ResultsScreen(navController: NavController) {
    var myResults by remember { mutableStateOf<List<ExerciseResult>>(emptyList()) }
    var allExercises by remember { mutableStateOf<List<Exercise>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchText by remember { mutableStateOf("") }
    var selectedUnit by remember { mutableStateOf("KG") }
    var refreshKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshKey) {
        val uid = AuthService.getCurrentUserId()
        if (uid.isNullOrEmpty()) {
            isLoading = false
            loadError = true
            return@LaunchedEffect
        }
        isLoading = true
        loadError = false
        try {
            myResults = ResultsService.fetchExerciseResults(uid)
            allExercises = ResultsService.fetchExercises()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("ResultsScreen", "load error", e)
            loadError = true
        } finally {
            isLoading = false
        }
    }

    val myResultsSorted = myResults.sortedByDescending { it.lastUpdated }
    val loggedIds = myResults.map { it.exerciseId }.toSet()
    val filteredExercises = if (searchText.isEmpty()) allExercises
        else allExercises.filter { it.name.contains(searchText, ignoreCase = true) }

    fun weightStr(kg: Double): String {
        val v = if (selectedUnit == "LBS") kg * 2.20462 else kg
        return "%.1f".format(v)
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
                .padding(top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
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
            Spacer(Modifier.width(8.dp))
            Text(
                "Sonuçlar",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            // Refresh
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { refreshKey++ },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            // KG / LBS toggle
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Primary.copy(alpha = 0.1f))
                    .clickable { selectedUnit = if (selectedUnit == "KG") "LBS" else "KG" }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    selectedUnit,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Primary
                )
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(14.dp)
                )
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Default.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Text("Veriler yüklenemedi", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    TextButton(onClick = { refreshKey++ }) {
                        Text("Tekrar Dene", color = Primary)
                    }
                }
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // Summary strip
            item {
                SummaryStrip(
                    exerciseCount = myResults.size,
                    maxWeight = myResults.maxOfOrNull { it.maxWeight } ?: 0.0,
                    maxOneRepMax = myResults.maxOfOrNull { it.oneRepMax } ?: 0.0,
                    weightStr = ::weightStr,
                    unit = selectedUnit,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 14.dp)
                )
            }

            // Tab selector
            item {
                TabSelector(
                    selectedTab = selectedTab,
                    myResultsCount = myResults.size,
                    onTabSelected = { selectedTab = it },
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 12.dp)
                )
            }

            // Search bar — only on Tüm tab
            if (selectedTab == 1) {
                item {
                    SearchBar(
                        text = searchText,
                        onTextChange = { searchText = it },
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 12.dp)
                    )
                }
            }

            if (selectedTab == 0) {
                // My results
                if (myResultsSorted.isEmpty()) {
                    item {
                        EmptyResults(
                            icon = { Icon(Icons.Default.BarChart, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(44.dp)) },
                            title = "Henüz sonuç yok",
                            subtitle = "Antrenman ekranından egzersiz kaydettikçe\nsonuçların burada görünecek."
                        )
                    }
                } else {
                    items(myResultsSorted, key = { it.id }) { result ->
                        ResultCard(
                            result = result,
                            unit = selectedUnit,
                            weightStr = ::weightStr,
                            onClick = {
                                NavigationStateHolder.pendingExerciseName = result.exercise?.name ?: "Egzersiz"
                                navController.navigate(Routes.resultDetail(result.exerciseId))
                            },
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp)
                        )
                    }
                }
            } else {
                // All exercises
                if (filteredExercises.isEmpty()) {
                    item {
                        EmptyResults(
                            icon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(36.dp)) },
                            title = "Egzersiz bulunamadı",
                            subtitle = ""
                        )
                    }
                } else {
                    items(filteredExercises, key = { it.id }) { exercise ->
                        val result = myResults.firstOrNull { it.exerciseId == exercise.id }
                        ExerciseListRow(
                            exercise = exercise,
                            result = result,
                            unit = selectedUnit,
                            weightStr = ::weightStr,
                            onClick = {
                                NavigationStateHolder.pendingExerciseName = exercise.name
                                navController.navigate(Routes.resultDetail(exercise.id))
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Summary Strip
// ---------------------------------------------------------------------------

@Composable
private fun SummaryStrip(
    exerciseCount: Int,
    maxWeight: Double,
    maxOneRepMax: Double,
    weightStr: (Double) -> String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SummaryCell(
            icon = { Icon(Icons.Default.EmojiEvents, null, tint = Primary, modifier = Modifier.size(16.dp)) },
            value = "$exerciseCount",
            label = "Egzersiz",
            modifier = Modifier.weight(1f)
        )
        Box(modifier = Modifier.width(1.dp).height(36.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)))
        SummaryCell(
            icon = { Icon(Icons.Default.LocalFireDepartment, null, tint = Color(0xFFFF9800), modifier = Modifier.size(16.dp)) },
            value = if (exerciseCount == 0) "--" else weightStr(maxWeight),
            label = "En Yüksek",
            modifier = Modifier.weight(1f)
        )
        Box(modifier = Modifier.width(1.dp).height(36.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)))
        SummaryCell(
            icon = { Icon(Icons.Default.ShowChart, null, tint = Color(0xFF2196F3), modifier = Modifier.size(16.dp)) },
            value = if (exerciseCount == 0) "--" else weightStr(maxOneRepMax),
            label = "En Yüksek 1RM",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryCell(
    icon: @Composable () -> Unit,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        icon()
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

// ---------------------------------------------------------------------------
// Tab Selector
// ---------------------------------------------------------------------------

@Composable
private fun TabSelector(
    selectedTab: Int,
    myResultsCount: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(2.dp)
    ) {
        listOf("Sonuçlarım ($myResultsCount)", "Tüm Egzersizler").forEachIndexed { index, title ->
            val isSelected = selectedTab == index
            val bgColor by animateColorAsState(
                if (isSelected) Primary else Color.Transparent,
                animationSpec = tween(200),
                label = "tabBg"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(bgColor)
                    .clickable { onTabSelected(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Search Bar
// ---------------------------------------------------------------------------

@Composable
private fun SearchBar(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (text.isEmpty()) Text("Egzersiz ara...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                inner()
            }
        )
        if (text.isNotEmpty()) {
            Icon(
                Icons.Default.Cancel, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp).clickable { onTextChange("") }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Result Card (Sonuçlarım tab)
// ---------------------------------------------------------------------------

@Composable
private fun ResultCard(
    result: ExerciseResult,
    unit: String,
    weightStr: (Double) -> String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val exerciseName = result.exercise?.name ?: "Egzersiz"
    val dateText = formatResultDate(result.lastUpdated)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(Primary, RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp))
                    .align(Alignment.CenterVertically)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Title + date
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        exerciseName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Text(dateText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Stats row
                Row(modifier = Modifier.fillMaxWidth()) {
                    MiniStat("Max Ağırlık", "${weightStr(result.maxWeight)} $unit", Modifier.weight(1f))
                    MiniStat("Max Tekrar", "${result.maxReps} rep", Modifier.weight(1f))
                    MiniStat("1RM", "${weightStr(result.oneRepMax)} $unit", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------------------
// Exercise List Row (Tüm Egzersizler tab)
// ---------------------------------------------------------------------------

@Composable
private fun ExerciseListRow(
    exercise: Exercise,
    result: ExerciseResult?,
    unit: String,
    weightStr: (Double) -> String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(exercise.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            if (result != null) {
                Text(
                    "Max: ${weightStr(result.maxWeight)} $unit  ·  1RM: ${weightStr(result.oneRepMax)} $unit",
                    fontSize = 12.sp,
                    color = Primary
                )
            } else {
                Text("Kayıt yok", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.Default.ChevronRight, null, tint = Primary, modifier = Modifier.size(16.dp))
    }
}

// ---------------------------------------------------------------------------
// Empty State
// ---------------------------------------------------------------------------

@Composable
private fun EmptyResults(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        icon()
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        if (subtitle.isNotEmpty()) {
            Text(
                subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

internal fun formatResultDate(isoString: String): String {
    return try {
        val instant = Instant.parse(isoString)
        val local = instant.atZone(ZoneId.systemDefault()).toLocalDate()
        val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("tr"))
        local.format(formatter)
    } catch (_: Exception) {
        isoString.take(10)
    }
}
