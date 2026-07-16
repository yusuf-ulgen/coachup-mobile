package com.app.coachup.app.ui.streak

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.app.coachup.app.services.AuthService
import com.app.coachup.app.services.StreakActivityItem
import com.app.coachup.app.services.StreakService
import com.app.coachup.app.services.UserService
import com.app.coachup.app.theme.Primary
import kotlinx.coroutines.CancellationException
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreakScreen(navController: NavController) {
    val profile by UserService.currentProfile.collectAsState()
    var recentActivities by remember { mutableStateOf<List<StreakActivityItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var hasActivityToday by remember { mutableStateOf(false) }

    val streak = profile?.currentStreak ?: 0
    val longest = profile?.longestStreak ?: 0
    val fireTint = streakFireColor(streak)

    LaunchedEffect(Unit) {
        val userId = AuthService.getCurrentUserId() ?: run {
            isLoading = false
            return@LaunchedEffect
        }
        try {
            StreakService.syncUserStreak(userId)
            recentActivities = StreakService.fetchRecentActivities(userId)
            hasActivityToday = StreakService.hasActivityOnDate(userId, LocalDate.now())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aktif Seri") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Primary)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocalFireDepartment,
                        contentDescription = null,
                        tint = fireTint,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$streak Gün",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$streak Günlük Streak",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (longest > streak) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "En uzun seri: $longest gün",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                StreakMilestonesRow(currentStreak = streak)
            }

            item {
                StreakDayCalendar(recentActivities = recentActivities)
            }

            item {
                Text(
                    text = "Son aktiviteler",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (recentActivities.isEmpty()) {
                item {
                    Text(
                        text = "Henüz tamamlanmış aktivite yok.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column {
                            recentActivities.forEachIndexed { index, activity ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = activity.label,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = StreakService.formatActivityDate(activity.date),
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (index < recentActivities.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Primary.copy(alpha = 0.12f)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = motivationalMessage(streak, hasActivityToday),
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun StreakDayCalendar(recentActivities: List<StreakActivityItem>) {
    val activeDates = remember(recentActivities) { recentActivities.map { it.date }.toSet() }
    val days = remember {
        val today = LocalDate.now()
        (6 downTo 0).map { today.minusDays(it.toLong()) }
    }
    val dayLabels = listOf("Pzt", "Sal", "Çar", "Per", "Cm", "Cts", "Pz")

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Son 7 gün",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            days.forEach { date ->
                StreakDayCell(
                    modifier = Modifier.weight(1f),
                    date = date,
                    dayLabel = dayLabels[date.dayOfWeek.value - 1],
                    active = date in activeDates,
                    isToday = date == LocalDate.now()
                )
            }
        }
    }
}

@Composable
private fun StreakDayCell(
    modifier: Modifier = Modifier,
    date: LocalDate,
    dayLabel: String,
    active: Boolean,
    isToday: Boolean
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = dayLabel,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    when {
                        active -> Primary.copy(alpha = 0.18f)
                        isToday -> MaterialTheme.colorScheme.surfaceVariant
                        else -> Color.Transparent
                    },
                    RoundedCornerShape(8.dp)
                )
                .then(
                    if (isToday) Modifier.border(
                        1.dp,
                        Primary.copy(alpha = 0.5f),
                        RoundedCornerShape(8.dp)
                    ) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${date.dayOfMonth}",
                fontSize = 11.sp,
                maxLines = 1,
                fontWeight = if (active || isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (active) Primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun StreakMilestonesRow(currentStreak: Int) {
    val milestones = StreakService.milestoneDays
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        milestones.forEach { day ->
            val unlocked = currentStreak >= day
            Column(
                modifier = Modifier.widthIn(min = 44.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    tint = if (unlocked) streakFireColor(day) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                    modifier = Modifier.size(if (day >= 30) 28.dp else 22.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$day",
                    fontSize = 11.sp,
                    maxLines = 1,
                    fontWeight = if (unlocked) FontWeight.Bold else FontWeight.Normal,
                    color = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun streakFireColor(streak: Int): Color = when {
    streak >= 100 -> Color(0xFFFFD700)
    streak >= 50 -> Color(0xFFFF5722)
    streak >= 30 -> Color(0xFFFF9800)
    streak >= 14 -> Color(0xFFFF7043)
    streak >= 7 -> Color(0xFFFF6047)
    streak >= 3 -> Color(0xFFFF8A65)
    else -> Primary
}

private fun motivationalMessage(streak: Int, hasActivityToday: Boolean): String = when {
    streak == 0 ->
        "Streak'ini başlatmak için bugün en az bir antrenman veya spor aktivitesi tamamla!"
    hasActivityToday ->
        "Yarın da bir antrenman yaparak streak'ini devam ettir!"
    else ->
        "🔥 $streak günlük streak'in devam ediyor! Bugünkü antrenmanını kaydetmeyi unutma."
}
