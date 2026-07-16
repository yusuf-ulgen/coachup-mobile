package com.app.coachup.app.ui.qr

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.coachup.app.models.EntryFilterType
import com.app.coachup.app.models.EntryHistory
import com.app.coachup.app.models.EntryType
import com.app.coachup.app.services.AuthService
import com.app.coachup.app.services.QRScannerService
import com.app.coachup.app.theme.*
import java.util.Calendar
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@Composable
fun AllEntryHistoryScreen(
    onNavigateBack: () -> Unit
) {
    val userId by AuthService.currentUser.collectAsState(initial = null)
    val entries by QRScannerService.entries.collectAsState()
    val isLoading by QRScannerService.isLoading.collectAsState()
    val loadError by QRScannerService.error.collectAsState()

    val scope = rememberCoroutineScope()
    var selectedFilter by remember { mutableStateOf(EntryFilterType.ALL) }
    var searchText by remember { mutableStateOf("") }

    LaunchedEffect(userId) {
        userId?.id?.let { QRScannerService.fetchEntries(it, limit = 100) }
    }

    val filtered = remember(entries, selectedFilter, searchText) {
        entries
            .filter { entry ->
                when (selectedFilter) {
                    EntryFilterType.ALL -> true
                    EntryFilterType.QR -> entry.type == EntryType.QR
                    EntryFilterType.MANUAL -> entry.type == EntryType.MANUAL
                }
            }
            .filter { entry ->
                if (searchText.isBlank()) true
                else entry.location.contains(searchText, ignoreCase = true) ||
                        entry.time.contains(searchText)
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Header
        AllHistoryHeader(
            count = filtered.size,
            onBack = onNavigateBack,
            modifier = Modifier
                .padding(horizontal = Spacing.xl)
                .padding(top = Spacing.sm, bottom = Spacing.lg)
        )

        // Filter tabs
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(bottom = Spacing.lg)
        ) {
            items(EntryFilterType.values()) { filter ->
                FilterChip(
                    filter = filter,
                    isSelected = selectedFilter == filter,
                    onClick = { selectedFilter = filter }
                )
            }
        }

        // Search bar
        HistorySearchBar(
            text = searchText,
            onTextChange = { searchText = it },
            modifier = Modifier
                .padding(horizontal = Spacing.xl)
                .padding(bottom = Spacing.lg)
        )

        // Stats summary
        StatsSummaryRow(
            entries = entries,
            modifier = Modifier
                .padding(horizontal = Spacing.xl)
                .padding(bottom = Spacing.lg)
        )

        // Entry list
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = Primary, strokeWidth = 2.dp) }
            }

            loadError != null && entries.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(36.dp)
                    )
                    Text(loadError ?: "", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { userId?.id?.let { uid -> scope.launch { QRScannerService.fetchEntries(uid, limit = 100) } } }) {
                        Text("Tekrar Dene", color = Primary)
                    }
                }
            }

            filtered.isEmpty() -> EmptyHistoryState(isSearching = searchText.isNotEmpty())

            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    items(filtered, key = { it.id }) { entry ->
                        EntryHistoryRow(entry = entry)
                        if (entry != filtered.last()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = (Spacing.xl.value + 44 + 12).dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

@Composable
private fun AllHistoryHeader(
    count: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = "Geri",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Giriş Geçmişi",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$count kayıt",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Filter chip
// ---------------------------------------------------------------------------

@Composable
private fun FilterChip(
    filter: EntryFilterType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val icon = when (filter) {
        EntryFilterType.ALL -> Icons.Default.List
        EntryFilterType.QR -> Icons.Default.QrCode
        EntryFilterType.MANUAL -> Icons.Default.Keyboard
    }

    Row(
        modifier = Modifier
            .background(
                if (isSelected) Primary else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(Radius.pill)
            )
            .clickable { onClick() }
            .padding(horizontal = Spacing.lg, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = filter.label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}

// ---------------------------------------------------------------------------
// Search bar
// ---------------------------------------------------------------------------

@Composable
private fun HistorySearchBar(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = Spacing.lg, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (text.isEmpty()) {
                    Text("Ara...", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                inner()
            }
        )
        if (text.isNotEmpty()) {
            Icon(
                imageVector = Icons.Default.Cancel,
                contentDescription = "Temizle",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp).clickable { onTextChange("") }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Stats summary
// ---------------------------------------------------------------------------

@Composable
private fun StatsSummaryRow(
    entries: List<EntryHistory>,
    modifier: Modifier = Modifier
) {
    val now = Calendar.getInstance()
    val thisMonthCount = entries.count { entry ->
        val cal = Calendar.getInstance().apply { time = entry.date }
        cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                cal.get(Calendar.MONTH) == now.get(Calendar.MONTH)
    }
    val qrCount = entries.count { it.type == EntryType.QR }
    val manualCount = entries.count { it.type == EntryType.MANUAL }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            title = "Toplam Giriş",
            value = "${entries.size}",
            icon = Icons.Default.CalendarMonth,
            color = Primary,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Bu Ay",
            value = "$thisMonthCount",
            icon = Icons.Default.Schedule,
            color = Color(0xFF2196F3),
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "QR / Manuel",
            value = "$qrCount/$manualCount",
            icon = Icons.Default.BarChart,
            color = Color(0xFF4CAF50),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
            Text(text = title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ---------------------------------------------------------------------------
// Empty state
// ---------------------------------------------------------------------------

@Composable
private fun EmptyHistoryState(isSearching: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Inbox,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Text("Kayıt Bulunamadı", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = if (isSearching) "Arama kriterlerine uygun kayıt bulunamadı"
            else "Henüz giriş kaydı yok",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
