package com.app.coachup.app.ui.training

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.app.coachup.app.models.Training
import com.app.coachup.app.models.ActivityMetric
import com.app.coachup.app.models.PerceivedEffort
import com.app.coachup.app.theme.*
import kotlinx.coroutines.CancellationException

data class KmSplit(
    val km: Int,
    val paceMinPerKm: Double
) {
    val formattedPace: String get() {
        if (paceMinPerKm <= 0) return "--"
        val min = paceMinPerKm.toInt()
        val sec = ((paceMinPerKm - min) * 60).toInt()
        return "%d:%02d".format(min, sec)
    }
}

@Composable
fun WorkoutSummaryScreen(
    training: Training,
    durationSeconds: Int,
    avgHeartRate: Int? = null,
    maxHeartRate: Int? = null,
    calories: Int? = null,
    distanceKm: Double = 0.0,
    avgPaceMinPerKm: Double = 0.0,
    avgSpeedKmh: Double = 0.0,
    altitudeGainM: Double = 0.0,
    splits: List<KmSplit> = emptyList(),
    routePoints: List<Pair<Double, Double>> = emptyList(),
    completedSets: Int = 0,
    totalSets: Int = 0,
    perceivedEffort: String? = null,
    onDismiss: () -> Unit
) {
    val category = training.category
    val metrics = category.trackedMetrics
    val hasGPSData = distanceKm > 0
    val hasRouteMap = routePoints.size >= 2
    var appear by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appear = true }
    val context = LocalContext.current
    val resolvedEffort = perceivedEffort?.let { PerceivedEffort.fromDbValue(it) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 120.dp)
        ) {
            SummaryHeader(training = training)

            // ── SÜRE (her zaman gösterilir) ──
            SummarySectionLabel("ÖZET", modifier = Modifier.padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryStatCard(
                    icon = Icons.Default.Timer,
                    iconColor = Primary,
                    value = formatDuration(durationSeconds),
                    label = "Süre",
                    modifier = Modifier.weight(1f)
                )
                // Kalori — sadece akıllı saatten, yoksa "—"
                if (ActivityMetric.CALORIES in metrics) {
                    SummaryStatCard(
                        icon = Icons.Default.LocalFireDepartment,
                        iconColor = Color(0xFFFF7043),
                        value = calories?.toString() ?: "—",
                        label = "Kalori",
                        modifier = Modifier.weight(1f)
                    )
                }
            }



            // ── NABIZ — sadece akıllı saatten (veri yoksa "—") ──
            if (ActivityMetric.AVG_HR in metrics || ActivityMetric.MAX_HR in metrics) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (ActivityMetric.AVG_HR in metrics) {
                        SummaryStatCard(
                            icon = Icons.Default.Favorite,
                            iconColor = Color.Red,
                            value = avgHeartRate?.toString() ?: "—",
                            label = "Ort. Nabız (bpm)",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (ActivityMetric.MAX_HR in metrics) {
                        SummaryStatCard(
                            icon = Icons.Default.MonitorHeart,
                            iconColor = Color(0xFFFF6047),
                            value = maxHeartRate?.toString() ?: "—",
                            label = "Maks. Nabız (bpm)",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // HR Zones — sadece gerçek nabız verisi varsa göster
                if ((avgHeartRate ?: 0) > 0 || (maxHeartRate ?: 0) > 0) {
                    Spacer(modifier = Modifier.height(20.dp))
                    SummarySectionLabel("KALBİN ATTIĞI BÖLGELER", modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    HRZonesCard(avgHeartRate = avgHeartRate ?: 0, maxHeartRate = maxHeartRate ?: 0, appear = appear)
                }
            }

            // ── GPS / MESAFE VERİLERİ (aktivite tipine göre) ──
            if (hasGPSData && ActivityMetric.DISTANCE in metrics) {
                if (hasRouteMap) {
                    Spacer(modifier = Modifier.height(20.dp))
                    SummarySectionLabel("GÜZERGAH", modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    RouteMapCard(routePoints = routePoints)
                }

                Spacer(modifier = Modifier.height(20.dp))
                SummarySectionLabel("MESAFE VERİLERİ", modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SummaryStatCard(
                        icon = Icons.Default.DirectionsRun,
                        iconColor = Color(0xFF4CAF50),
                        value = "%.2f km".format(distanceKm),
                        label = "Toplam Mesafe",
                        modifier = Modifier.weight(1f)
                    )
                    if (ActivityMetric.AVG_SPEED in metrics) {
                        SummaryStatCard(
                            icon = Icons.Default.Speed,
                            iconColor = Primary,
                            value = "%.1f km/s".format(avgSpeedKmh),
                            label = "Ort. Hız",
                            modifier = Modifier.weight(1f)
                        )
                    } else if (ActivityMetric.AVG_PACE in metrics && avgPaceMinPerKm > 0) {
                        SummaryStatCard(
                            icon = Icons.Default.Timer,
                            iconColor = Color(0xFF9C27B0),
                            value = formatPaceSummary(avgPaceMinPerKm),
                            label = "Ort. Tempo",
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                if (altitudeGainM > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SummaryStatCard(
                            icon = Icons.Default.Terrain,
                            iconColor = Color(0xFF795548),
                            value = "%.0f m".format(altitudeGainM),
                            label = "Yükseklik Kazanımı",
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                if (splits.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    SummarySectionLabel("KM BAŞI BÖLÜNMELER", modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    SplitsCard(splits = splits, appear = appear)
                }
            }

            // ── NASIL HİSSEDİYORSUN (effort) ──
            if (resolvedEffort != null) {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(Radius.card))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = resolvedEffort.emoji, fontSize = 28.sp)
                    Column {
                        Text(
                            text = "Nasıl hissettin?",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = resolvedEffort.label,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // ── AKILLI SAAT BİLGİ NOTU ──
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(Radius.card))
                    .background(Primary.copy(alpha = 0.06f))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Watch,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Akıllı saat bağlayarak daha detaylı analizlere ulaşabilirsiniz.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Bottom bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { showShareSheet = true },
                    modifier = Modifier.height(52.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Primary),
                    shape = RoundedCornerShape(Radius.pill),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = Primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Paylaş",
                        tint = Primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(Radius.pill)
                ) {
                    Text("Kapat", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }
    // Story-style share sheet
    if (showShareSheet) {
        WorkoutShareSheet(
            training = training,
            durationSeconds = durationSeconds,
            totalCalories = calories ?: 0,
            distanceKm = distanceKm,
            avgPaceMinPerKm = avgPaceMinPerKm,
            routePoints = routePoints,
            onDismiss = { showShareSheet = false }
        )
    }
}

// ---------------------------------------------------------------------------
// Route map card — mirrors iOS SummaryRouteMapView
// ---------------------------------------------------------------------------

@Composable
private fun RouteMapCard(routePoints: List<Pair<Double, Double>>) {
    val latLngs = remember(routePoints) {
        routePoints.map { LatLng(it.first, it.second) }
    }

    val centerLat = remember(latLngs) { latLngs.map { it.latitude }.average() }
    val centerLng = remember(latLngs) { latLngs.map { it.longitude }.average() }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(centerLat, centerLng), 14f)
    }

    // Fit all route points in camera bounds after first composition
    LaunchedEffect(latLngs) {
        if (latLngs.size >= 2) {
            try {
                val boundsBuilder = LatLngBounds.Builder()
                latLngs.forEach { boundsBuilder.include(it) }
                val bounds = boundsBuilder.build()
                cameraPositionState.animate(
                    update = CameraUpdateFactory.newLatLngBounds(bounds, 80),
                    durationMs = 600
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // bounds might fail if all points are identical
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(Radius.card))
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                scrollGesturesEnabled = false,
                rotationGesturesEnabled = false,
                tiltGesturesEnabled = false,
                zoomGesturesEnabled = false,
                mapToolbarEnabled = false
            )
        ) {
            // Route polyline
            Polyline(
                points = latLngs,
                color = Primary,
                width = 8f
            )
            // Start marker (green)
            Marker(
                state = rememberMarkerState(position = latLngs.first()),
                title = "Başlangıç"
            )
            // End marker (primary color — last point)
            Marker(
                state = rememberMarkerState(position = latLngs.last()),
                title = "Bitiş"
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

@Composable
private fun SummaryHeader(training: Training) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Antrenman Özeti",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = training.category.emoji, fontSize = 24.sp)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = training.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(text = java.time.LocalDate.now().toString(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF4CAF50).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(22.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Stat Card
// ---------------------------------------------------------------------------

@Composable
private fun SummaryStatCard(
    icon: ImageVector,
    iconColor: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
        }
        Text(text = value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------------------
// Section label
// ---------------------------------------------------------------------------

@Composable
private fun SummarySectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.5.sp,
        modifier = modifier
    )
}

// ---------------------------------------------------------------------------
// HR Zones Card
// ---------------------------------------------------------------------------

private data class HRZone(val name: String, val range: String, val color: Color, val fraction: Float, val timeLabel: String)

@Composable
private fun HRZonesCard(avgHeartRate: Int, maxHeartRate: Int, appear: Boolean) {
    val zones = remember(avgHeartRate, maxHeartRate) {
        buildHRZones(avgHeartRate, maxHeartRate)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        zones.forEachIndexed { idx, zone ->
            val animatedWidth by animateFloatAsState(
                targetValue = if (appear) zone.fraction else 0f,
                animationSpec = tween(durationMillis = 800, delayMillis = idx * 100),
                label = "zone_$idx"
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(modifier = Modifier.width(6.dp).height(20.dp).clip(RoundedCornerShape(3.dp)).background(zone.color))
                Column(modifier = Modifier.width(90.dp)) {
                    Text(text = zone.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = zone.range, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BoxWithConstraints(modifier = Modifier.weight(1f).height(8.dp)) {
                    Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(zone.color.copy(alpha = 0.15f)))
                    Box(
                        modifier = Modifier
                            .width(maxWidth * animatedWidth)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(zone.color)
                    )
                }
                Text(text = zone.timeLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(50.dp))
            }
        }
    }
}

private fun buildHRZones(avgHR: Int, maxHR: Int): List<HRZone> {
    val hrMax = if (maxHR > 0) maxHR else 180
    return listOf(
        HRZone("Dinlenme", "< 50%", Color(0xFF9E9E9E), if (avgHR < hrMax * 0.5) 0.8f else 0.1f, "~10 dk"),
        HRZone("Hafif", "50–60%", Color(0xFF4CAF50), if (avgHR in (hrMax * 0.5).toInt()..(hrMax * 0.6).toInt()) 0.7f else 0.15f, "~15 dk"),
        HRZone("Aerobik", "60–70%", Color(0xFF2196F3), if (avgHR in (hrMax * 0.6).toInt()..(hrMax * 0.7).toInt()) 0.75f else 0.2f, "~20 dk"),
        HRZone("Anaerobik", "70–85%", Color(0xFFFF9800), if (avgHR in (hrMax * 0.7).toInt()..(hrMax * 0.85).toInt()) 0.8f else 0.1f, "~12 dk"),
        HRZone("Maks.", "85%+", Color(0xFFF44336), if (avgHR > hrMax * 0.85) 0.6f else 0.05f, "~5 dk")
    )
}

// ---------------------------------------------------------------------------
// Splits Card
// ---------------------------------------------------------------------------

@Composable
private fun SplitsCard(splits: List<KmSplit>, appear: Boolean) {
    val bestPace = splits.filter { it.paceMinPerKm > 0 }.minOfOrNull { it.paceMinPerKm } ?: 1.0
    val worstPace = splits.maxOfOrNull { it.paceMinPerKm } ?: 1.0

    val rowTint = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(rowTint)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("KM", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(40.dp))
            Text("TEMPO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(80.dp))
        }
        splits.forEachIndexed { idx, split ->
            val fraction = if (worstPace > bestPace)
                (1.0 - (split.paceMinPerKm - bestPace) / (worstPace - bestPace)).toFloat().coerceIn(0f, 1f)
            else 1f
            val paceColor = paceColor(split.paceMinPerKm, bestPace, worstPace)
            val animatedFraction by animateFloatAsState(
                targetValue = if (appear) fraction else 0f,
                animationSpec = tween(700, delayMillis = idx * 80),
                label = "split_$idx"
            )
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(if (idx % 2 == 0) Color.Transparent else rowTint)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = "${split.km}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(40.dp))
                Text(text = split.formattedPace, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = paceColor, modifier = Modifier.width(60.dp))
                BoxWithConstraints(modifier = Modifier.weight(1f).height(8.dp)) {
                    Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(paceColor.copy(alpha = 0.15f)))
                    Box(
                        modifier = Modifier
                            .width(maxWidth * animatedFraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(paceColor)
                    )
                }
            }
        }
    }
}

private fun paceColor(pace: Double, best: Double, worst: Double): Color {
    if (worst <= best) return Color(0xFF4CAF50)
    val ratio = ((pace - best) / (worst - best)).coerceIn(0.0, 1.0)
    return when {
        ratio < 0.33 -> Color(0xFF4CAF50)
        ratio < 0.66 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }
}

// ---------------------------------------------------------------------------
// Progressive Overload Card
// ---------------------------------------------------------------------------

@Composable
private fun ProgressiveOverloadCard(suggestion: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(Radius.card))
            .background(Color(0xFF4CAF50).copy(alpha = 0.06f))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF4CAF50).copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Aşamalı Yüklenme Önerisi", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "Tüm setleri tamamladın — bir sonraki seansta %.1f kg dene.".format(suggestion),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatPaceSummary(paceMinPerKm: Double): String {
    if (paceMinPerKm <= 0) return "--"
    val min = paceMinPerKm.toInt()
    val sec = ((paceMinPerKm - min) * 60).toInt()
    return "%d'%02d\"".format(min, sec)
}
