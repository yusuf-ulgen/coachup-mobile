package com.app.coachup.app.ui.guardian

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import com.app.coachup.app.services.ChildHealthMetrics
import com.app.coachup.app.services.GuardianChildInfo
import com.app.coachup.app.services.GuardianService
import com.app.coachup.app.services.MemberGuardian
import com.app.coachup.app.theme.Primary

// ---------------------------------------------------------------------------
// GuardianChildDetailScreen — mirrors iOS GuardianChildDetailView.swift
// ---------------------------------------------------------------------------

private enum class DetailSection(val label: String) {
    HEALTH("Sağlık"),
    ATTENDANCE("Giriş/Çıkış"),
    PROGRESS("İlerleme")
}

@Composable
fun GuardianChildDetailScreen(
    relation: MemberGuardian,
    onBack: () -> Unit
) {
    val member = relation.member ?: return
    val childHealthData by GuardianService.childHealthData.collectAsState()
    val childEntries by GuardianService.childEntries.collectAsState()
    val metrics = childHealthData[member.id] ?: ChildHealthMetrics()
    val entries = childEntries[member.id] ?: emptyList()

    var selectedSection by remember { mutableStateOf(DetailSection.HEALTH) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F8F8)),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        // Header
        item {
            Column(modifier = Modifier.statusBarsPadding()) {
                // Back button
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Geri",
                        tint = Color(0xFF1A1A1A)
                    )
                }

                // Avatar + name + status
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = member.initials,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                    }

                    Text(
                        text = member.fullName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (metrics.isLive) Color(0xFF4CAF50) else Color(0xFFBDBDBD))
                        )
                        Text(
                            text = if (metrics.isLive) "Salonda aktif" else "Çevrimdışı",
                            fontSize = 13.sp,
                            color = if (metrics.isLive) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                }
            }
        }

        // Section Picker
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailSection.values().forEach { section ->
                    val selected = section == selectedSection
                    Button(
                        onClick = { selectedSection = section },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) Primary else MaterialTheme.colorScheme.surface,
                            contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = if (selected) 0.dp else 2.dp)
                    ) {
                        Text(section.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Content based on section
        when (selectedSection) {
            DetailSection.HEALTH -> {
                if (relation.canViewHealth) {
                    item { HealthSection(metrics = metrics) }
                } else {
                    item { LockedSection("Sağlık verilerini görme izniniz yok") }
                }
            }

            DetailSection.ATTENDANCE -> {
                if (relation.canViewAttendance) {
                    if (entries.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Filled.DoorFront, null, tint = Color(0xFF9E9E9E).copy(alpha = 0.4f), modifier = Modifier.size(40.dp))
                                Text("Henüz giriş kaydı yok", fontSize = 13.sp, color = Color(0xFF9E9E9E))
                            }
                        }
                    } else {
                        items(entries) { entry ->
                            val isEntry = entry.entryType == "entry"
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 4.dp)
                                    .shadow(1.dp, RoundedCornerShape(12.dp)),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isEntry) Color(0xFF4CAF50).copy(0.12f)
                                                else Primary.copy(0.12f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isEntry) Icons.Filled.Login else Icons.Filled.Logout,
                                            contentDescription = null,
                                            tint = if (isEntry) Color(0xFF4CAF50) else Primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            if (isEntry) "Giriş" else "Çıkış",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF1A1A1A)
                                        )
                                        Text(entry.entryDate ?: "", fontSize = 11.sp, color = Color(0xFF9E9E9E))
                                    }
                                    Text(
                                        entry.entryTime?.take(5) ?: "",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF1A1A1A)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    item { LockedSection("Giriş/çıkış verilerini görme izniniz yok") }
                }
            }

            DetailSection.PROGRESS -> {
                if (relation.canViewProgress) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.TrendingUp, null, tint = Primary.copy(alpha = 0.5f), modifier = Modifier.size(44.dp))
                            Text("İlerleme verileri yakında", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF9E9E9E))
                        }
                    }
                } else {
                    item { LockedSection("İlerleme verilerini görme izniniz yok") }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Health Section
// ---------------------------------------------------------------------------

@Composable
private fun HealthSection(metrics: ChildHealthMetrics) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Heart rate
        HealthDetailCard(
            title = "Nabız",
            icon = Icons.Filled.Favorite,
            value = if (metrics.heartRate > 0) "${metrics.heartRate}" else "--",
            unit = "bpm",
            color = Color(0xFFF44336),
            subtitle = if (metrics.heartRate > 0) heartRateZone(metrics.heartRate) else null
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HealthDetailCard(
                title = "SpO2",
                icon = Icons.Filled.Air,
                value = if (metrics.spo2 > 0) "${metrics.spo2}" else "--",
                unit = "%",
                color = Color(0xFF00BCD4),
                subtitle = if (metrics.spo2 >= 95) "Normal" else if (metrics.spo2 > 0) "Düşük" else null,
                modifier = Modifier.weight(1f)
            )
            HealthDetailCard(
                title = "Stres",
                icon = Icons.Filled.Psychology,
                value = if (metrics.stressLevel > 0) "${metrics.stressLevel}" else "--",
                unit = "skor",
                color = stressColor(metrics.stressLevel),
                subtitle = stressLabel(metrics.stressLevel),
                modifier = Modifier.weight(1f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HealthDetailCard(
                title = "Kalori",
                icon = Icons.Filled.LocalFireDepartment,
                value = if (metrics.calories > 0) "${metrics.calories}" else "--",
                unit = "kcal",
                color = Color(0xFFFF9800),
                subtitle = null,
                modifier = Modifier.weight(1f)
            )
            HealthDetailCard(
                title = "Adım",
                icon = Icons.Filled.DirectionsWalk,
                value = if (metrics.steps > 0) "${metrics.steps}" else "--",
                unit = "",
                color = Color(0xFF4CAF50),
                subtitle = null,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun HealthDetailCard(
    title: String,
    icon: ImageVector,
    value: String,
    unit: String,
    color: Color,
    subtitle: String?,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    Surface(
        modifier = modifier.shadow(2.dp, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                Text(title, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF9E9E9E))
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                if (unit.isNotEmpty()) {
                    Text(unit, fontSize = 12.sp, color = Color(0xFF9E9E9E), modifier = Modifier.padding(bottom = 3.dp))
                }
            }
            if (subtitle != null) {
                Text(subtitle, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Locked Section
// ---------------------------------------------------------------------------

@Composable
private fun LockedSection(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = Color(0xFF9E9E9E).copy(alpha = 0.4f),
            modifier = Modifier.size(36.dp)
        )
        Text(message, fontSize = 13.sp, color = Color(0xFF9E9E9E))
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun heartRateZone(hr: Int) = when {
    hr < 60 -> "Dinlenme"
    hr < 100 -> "Normal"
    hr < 140 -> "Yüksek"
    else -> "Çok yüksek"
}

private fun stressColor(level: Int) = when {
    level < 30 -> Color(0xFF4CAF50)
    level < 60 -> Color(0xFFFF9800)
    else -> Color(0xFFF44336)
}

private fun stressLabel(level: Int): String? = when {
    level == 0 -> null
    level < 30 -> "Düşük"
    level < 60 -> "Orta"
    else -> "Yüksek"
}
