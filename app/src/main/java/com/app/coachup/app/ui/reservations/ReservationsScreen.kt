package com.app.coachup.app.ui.reservations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.app.coachup.app.models.AreaReservation
import com.app.coachup.app.models.GymArea
import com.app.coachup.app.services.AuthService
import com.app.coachup.app.services.ReservationService
import com.app.coachup.app.services.UserService
import com.app.coachup.app.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ReservationsScreen(navController: NavController) {
    val currentProfile by UserService.currentProfile.collectAsState()
    val gymId = currentProfile?.gymId
    val gymName = currentProfile?.gymName

    var isLoading by remember { mutableStateOf(true) }
    var areas by remember { mutableStateOf<List<GymArea>>(emptyList()) }
    var myReservations by remember { mutableStateOf<List<AreaReservation>>(emptyList()) }
    var showBookingDialog by remember { mutableStateOf(false) }
    var selectedArea by remember { mutableStateOf<GymArea?>(null) }
    var reservationToCancel by remember { mutableStateOf<AreaReservation?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val activeReservations = remember(myReservations) {
        myReservations.filter { it.status != "cancelled" }
            .sortedBy { it.reservationDate }
    }

    suspend fun loadData() {
        val userId = currentProfile?.id ?: AuthService.getCurrentUserId() ?: return
        isLoading = true
        errorMessage = null
        try {
            areas = ReservationService.fetchAreas(gymId)
            myReservations = ReservationService.fetchMyReservations(userId, gymId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMessage = friendlyReservationError(e)
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(currentProfile?.id, gymId) {
        currentProfile?.id?.let {
            loadData()
        } ?: run {
            val uid = AuthService.getCurrentUserId()
            if (uid != null) {
                try {
                    UserService.fetchProfile(uid)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    isLoading = false
                }
            } else {
                isLoading = false
            }
        }
    }

    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Hata") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text("Tamam", color = Primary)
                }
            }
        )
    }

    successMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { successMessage = null },
            title = { Text("Rezervasyon") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { successMessage = null }) {
                    Text("Tamam", color = Primary)
                }
            }
        )
    }

    if (reservationToCancel != null) {
        AlertDialog(
            onDismissRequest = { reservationToCancel = null },
            title = { Text("Rezervasyon İptali") },
            text = { Text("Bu rezervasyonu iptal etmek istediğinizden emin misiniz?") },
            confirmButton = {
                TextButton(onClick = {
                    val r = reservationToCancel ?: return@TextButton
                    coroutineScope.launch {
                        try {
                            val userId = currentProfile?.id ?: AuthService.getCurrentUserId() ?: return@launch
                            ReservationService.cancelReservation(r.id, userId)
                            loadData()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            errorMessage = friendlyReservationError(e)
                        }
                        reservationToCancel = null
                    }
                }) { Text("İptal Et", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { reservationToCancel = null }) { Text("Geri", color = Primary) } }
        )
    }

    if (showBookingDialog && selectedArea != null) {
        BookReservationDialog(
            area = selectedArea!!,
            gymId = gymId,
            onDismiss = { showBookingDialog = false; selectedArea = null },
            onBooked = {
                showBookingDialog = false
                selectedArea = null
                successMessage = "Rezervasyon talebiniz alındı."
                coroutineScope.launch { loadData() }
            },
            onError = { errorMessage = it }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Spacing.xl)
                .padding(top = Spacing.sm, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Rezervasyon",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (!gymName.isNullOrBlank()) {
                    Text(
                        text = gymName,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                    )
                }
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    InfoCard(
                        text = "Salon alanlarını (stüdyo, kort, havuz vb.) rezerve edin. " +
                            "Onaylanan rezervasyonlarınız aşağıda listelenir."
                    )
                }

                if (activeReservations.isNotEmpty()) {
                    items(activeReservations, key = { it.id }) { reservation ->
                        ReservationCard(
                            reservation = reservation,
                            onCancel = { reservationToCancel = reservation }
                        )
                    }
                } else {
                    item {
                        EmptySectionCard(
                            message = "Yaklaşan rezervasyonunuz yok",
                            subtitle = "Aşağıdan bir alan seçerek rezervasyon yapabilirsiniz"
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Rezerve edilebilir alanlar",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                if (areas.isEmpty()) {
                    item {
                        EmptySectionCard(
                            message = "Henüz alan tanımlanmamış",
                            subtitle = if (!gymName.isNullOrBlank()) {
                                "$gymName tarafından alan eklendiğinde burada görünecek"
                            } else {
                                "Salon yöneticisi alan tanımladığında burada listelenecek"
                            }
                        )
                    }
                } else {
                    items(areas, key = { it.id }) { area ->
                        AreaCard(
                            area = area,
                            onBook = {
                                selectedArea = area
                                showBookingDialog = true
                            }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun InfoCard(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(Primary.copy(alpha = 0.08f))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
        Text(text = text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f), lineHeight = 18.sp)
    }
}

@Composable
private fun EmptySectionCard(message: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(Radius.card)
            )
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = message,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun AreaCard(area: GymArea, onBook: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(areaIcon(area.areaType), contentDescription = null, tint = Primary, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = area.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    text = "${areaTypeLabel(area.areaType)} · Kapasite ${area.capacity}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        }
        area.description?.takeIf { it.isNotEmpty() }?.let {
            Text(text = it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), maxLines = 2)
        }
        Button(
            onClick = onBook,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            shape = RoundedCornerShape(Radius.pill)
        ) {
            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Rezervasyon Yap")
        }
    }
}

@Composable
private fun ReservationCard(reservation: AreaReservation, onCancel: () -> Unit) {
    val (statusLabel, statusColor) = when (reservation.status) {
        "confirmed" -> "Onaylandı" to Color(0xFF4CAF50)
        "pending" -> "Bekliyor" to Color(0xFFFF9800)
        "cancelled" -> "İptal" to Color.Red
        else -> reservation.status to MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
    }
    val areaName = reservation.area?.name ?: "Salon alanı"
    val formattedDate = formatReservationDate(reservation.reservationDate)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = areaName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Text(text = formattedDate, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            }
            Box(modifier = Modifier.clip(RoundedCornerShape(Radius.pill)).background(statusColor.copy(alpha = 0.1f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(text = statusLabel, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = statusColor)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Default.AccessTime, contentDescription = null, tint = Primary, modifier = Modifier.size(14.dp))
            Text(
                text = "${reservation.startTime.take(5)} - ${reservation.endTime.take(5)}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
        if (reservation.status == "pending" || reservation.status == "confirmed") {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
            ) {
                Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("İptal Et", fontSize = 13.sp)
            }
        }
    }
}

private fun areaIcon(areaType: String) = when (areaType) {
    "studio" -> Icons.Default.MusicNote
    "pool" -> Icons.Default.Pool
    "sauna" -> Icons.Default.HotTub
    "court" -> Icons.Default.SportsTennis
    else -> Icons.Default.Business
}

private fun areaTypeLabel(areaType: String) = when (areaType) {
    "studio" -> "Stüdyo"
    "pool" -> "Havuz"
    "sauna" -> "Sauna"
    "court" -> "Kort"
    else -> "Alan"
}

private fun formatReservationDate(isoDate: String): String = runCatching {
    val date = LocalDate.parse(isoDate.take(10))
    date.format(DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", Locale("tr")))
}.getOrElse { isoDate.take(10) }

private fun friendlyReservationError(e: Exception): String {
    val raw = buildString {
        append(e.message.orEmpty())
        e.cause?.message?.let { append(' ').append(it) }
    }
    return when {
        raw.contains("JWT", ignoreCase = true) || raw.contains("401") ->
            "Oturum süresi dolmuş olabilir. Tekrar giriş yapın."
        raw.contains("permission", ignoreCase = true) || raw.contains("42501") ->
            "Rezervasyon verilerine erişim izni yok."
        raw.length > 140 || raw.contains("URL:") ->
            "Rezervasyon işlemi başarısız. Lütfen tekrar deneyin."
        else -> raw.ifBlank { "Rezervasyon işlemi başarısız." }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookReservationDialog(
    area: GymArea,
    gymId: String?,
    onDismiss: () -> Unit,
    onBooked: () -> Unit,
    onError: (String) -> Unit
) {
    var selectedDate by remember { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_DATE)) }
    var startTime by remember { mutableStateOf("09:00") }
    var endTime by remember { mutableStateOf("10:00") }
    var notes by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var startExpanded by remember { mutableStateOf(false) }
    var endExpanded by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val currentProfile by UserService.currentProfile.collectAsState()
    val timeSlots = (8..20).map { h -> String.format("%02d:00", h) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(area.name, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Talebiniz salona iletilecek; onay sonrası rezervasyonunuz aktif olur.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = selectedDate,
                    onValueChange = { selectedDate = it },
                    label = { Text("Tarih (YYYY-MM-DD)") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExposedDropdownMenuBox(expanded = startExpanded, onExpandedChange = { startExpanded = it }, modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = startTime,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Başlangıç") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = startExpanded) },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = startExpanded, onDismissRequest = { startExpanded = false }) {
                            timeSlots.forEach { t ->
                                DropdownMenuItem(text = { Text(t) }, onClick = { startTime = t; startExpanded = false })
                            }
                        }
                    }
                    ExposedDropdownMenuBox(expanded = endExpanded, onExpandedChange = { endExpanded = it }, modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = endTime,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Bitiş") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = endExpanded) },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = endExpanded, onDismissRequest = { endExpanded = false }) {
                            timeSlots.forEach { t ->
                                DropdownMenuItem(text = { Text(t) }, onClick = { endTime = t; endExpanded = false })
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Not (isteğe bağlı)") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    coroutineScope.launch {
                        isSaving = true
                        try {
                            val userId = currentProfile?.id ?: AuthService.getCurrentUserId() ?: return@launch
                            ReservationService.bookReservation(
                                userId = userId,
                                areaId = area.id,
                                gymId = gymId,
                                date = selectedDate,
                                startTime = startTime,
                                endTime = endTime,
                                notes = notes.takeIf { it.isNotBlank() }
                            )
                            onBooked()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            onError(friendlyReservationError(e))
                        } finally {
                            isSaving = false
                        }
                    }
                },
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Primary, strokeWidth = 2.dp)
                } else {
                    Text("Rezervasyon Yap", color = Primary)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}
