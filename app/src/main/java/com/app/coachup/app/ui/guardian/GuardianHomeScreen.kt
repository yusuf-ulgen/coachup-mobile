package com.app.coachup.app.ui.guardian

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.app.coachup.app.models.QREntry
import com.app.coachup.app.services.*
import com.app.coachup.app.theme.Primary
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------------------------------------------------------------------------
// GuardianHomeScreen — mirrors iOS GuardianHomeView.swift
// ---------------------------------------------------------------------------

@Composable
fun GuardianHomeScreen(navController: NavHostController) {
    val guardian by GuardianService.guardian.collectAsState()
    val children by GuardianService.children.collectAsState()
    val childHealthData by GuardianService.childHealthData.collectAsState()
    val recentEntries by GuardianService.recentEntries.collectAsState()
    val isLoading by GuardianService.isLoading.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F8F8)),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Header
        item {
            GuardianHeader(guardianName = guardian?.name ?: "Veli")
            Spacer(Modifier.height(16.dp))
        }

        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Primary)
                }
            }
        } else if (children.isEmpty()) {
            item { GuardianEmptyState() }
        } else {
            // Summary card
            item {
                GuardianSummaryCard(
                    childCount = children.size,
                    activeCount = children.count { child ->
                        child.member?.let { childHealthData[it.id]?.isLive } ?: false
                    },
                    todayEntryCount = recentEntries.size
                )
                Spacer(Modifier.height(16.dp))
            }

            // Child live cards
            items(children) { relation ->
                relation.member?.let { member ->
                    val metrics = childHealthData[member.id] ?: ChildHealthMetrics()
                    ChildLiveCard(
                        member = member,
                        relation = relation,
                        metrics = metrics,
                        onClick = {
                            navController.navigate("guardian_child_detail/${member.id}")
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            // Recent activity
            if (recentEntries.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    RecentActivitySection(
                        entries = recentEntries.take(5),
                        children = children
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

@Composable
private fun GuardianHeader(guardianName: String) {
    val today = LocalDate.now().format(
        DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("tr"))
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Merhaba, $guardianName",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A)
            )
            Text(
                text = today,
                fontSize = 13.sp,
                color = Color(0xFF9E9E9E)
            )
        }

        // Notification bell
        Box {
            Surface(
                shape = CircleShape,
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = "Bildirimler",
                    tint = Color(0xFF1A1A1A),
                    modifier = Modifier
                        .size(42.dp)
                        .padding(10.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Primary)
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Summary Card
// ---------------------------------------------------------------------------

@Composable
private fun GuardianSummaryCard(
    childCount: Int,
    activeCount: Int,
    todayEntryCount: Int
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(modifier = Modifier.padding(vertical = 14.dp)) {
            SummaryItem(icon = Icons.Filled.People, value = "$childCount", label = "Çocuk", color = Primary)
            SummaryItem(icon = Icons.Filled.Wifi, value = "$activeCount", label = "Aktif", color = Color(0xFF4CAF50))
            SummaryItem(icon = Icons.Filled.Login, value = "$todayEntryCount", label = "Bugün Giriş", color = Color(0xFF3B82F6))
        }
    }
}

@Composable
private fun RowScope.SummaryItem(
    icon: ImageVector,
    value: String,
    label: String,
    color: Color
) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
        Text(label, fontSize = 10.sp, color = Color(0xFF9E9E9E))
    }
}

// ---------------------------------------------------------------------------
// Child Live Card
// ---------------------------------------------------------------------------

@Composable
private fun ChildLiveCard(
    member: GuardianChildInfo,
    relation: MemberGuardian,
    metrics: ChildHealthMetrics,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .shadow(elevation = 3.dp, shape = RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Top row: avatar + name + status + chevron
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = member.initials,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = member.fullName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (metrics.isLive) Color(0xFF4CAF50) else Color(0xFFBDBDBD))
                        )
                        Text(
                            text = if (metrics.isLive) "Salonda aktif" else "Çevrimdışı",
                            fontSize = 12.sp,
                            color = if (metrics.isLive) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFFBDBDBD),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Health metrics (if permitted)
            if (relation.canViewHealth) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricPill(
                        icon = Icons.Filled.Favorite,
                        value = if (metrics.heartRate > 0) "${metrics.heartRate}" else "--",
                        unit = "bpm",
                        color = heartRateColor(metrics.heartRate),
                        modifier = Modifier.weight(1f)
                    )
                    MetricPill(
                        icon = Icons.Filled.Air,
                        value = if (metrics.spo2 > 0) "${metrics.spo2}" else "--",
                        unit = "%",
                        color = Color(0xFF00BCD4),
                        modifier = Modifier.weight(1f)
                    )
                    MetricPill(
                        icon = Icons.Filled.LocalFireDepartment,
                        value = if (metrics.calories > 0) "${metrics.calories}" else "--",
                        unit = "kcal",
                        color = Color(0xFFFF9800),
                        modifier = Modifier.weight(1f)
                    )
                    MetricPill(
                        icon = Icons.Filled.DirectionsWalk,
                        value = if (metrics.steps > 0) "${metrics.steps}" else "--",
                        unit = "",
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun MetricPill(
    icon: ImageVector,
    value: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.07f))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
            if (unit.isNotEmpty()) {
                Text(unit, fontSize = 8.sp, color = Color(0xFF9E9E9E))
            }
        }
    }
}

private fun heartRateColor(hr: Int) = when {
    hr < 60 -> Color(0xFF2196F3)
    hr < 100 -> Color(0xFF4CAF50)
    hr < 140 -> Color(0xFFFF9800)
    else -> Color(0xFFF44336)
}

// ---------------------------------------------------------------------------
// Recent Activity Section
// ---------------------------------------------------------------------------

@Composable
private fun RecentActivitySection(
    entries: List<QREntry>,
    children: List<com.app.coachup.app.services.MemberGuardian>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Son Hareketler",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A1A1A)
        )

        entries.forEach { entry ->
            val childName = children
                .find { it.memberId == entry.userId }
                ?.member?.fullName ?: "Çocuk"
            val isEntry = entry.entryType == "entry"

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(1.dp, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isEntry) Icons.Filled.Login else Icons.Filled.Logout,
                        contentDescription = null,
                        tint = if (isEntry) Color(0xFF4CAF50) else Primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(childName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A))
                        Text(
                            if (isEntry) "Salona giriş yaptı" else "Salondan çıkış yaptı",
                            fontSize = 11.sp,
                            color = Color(0xFF9E9E9E)
                        )
                    }
                    Text(
                        text = entry.entryTime?.take(5) ?: "",
                        fontSize = 11.sp,
                        color = Color(0xFF9E9E9E)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Empty State
// ---------------------------------------------------------------------------

@Composable
private fun GuardianEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.People,
            contentDescription = null,
            tint = Color(0xFF9E9E9E).copy(alpha = 0.5f),
            modifier = Modifier.size(52.dp)
        )
        Text(
            "Henüz bağlı çocuk bulunamadı",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF9E9E9E)
        )
        Text(
            "Salon yöneticinizle iletişime geçin",
            fontSize = 13.sp,
            color = Color(0xFF9E9E9E).copy(alpha = 0.7f)
        )
    }
}
