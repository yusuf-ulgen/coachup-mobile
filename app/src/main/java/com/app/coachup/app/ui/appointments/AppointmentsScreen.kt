package com.app.coachup.app.ui.appointments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.app.coachup.app.models.Appointment
import com.app.coachup.app.models.Coach
import com.app.coachup.app.services.AppointmentService
import com.app.coachup.app.services.AuthService
import com.app.coachup.app.services.CoachService
import com.app.coachup.app.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun AppointmentsScreen(navController: NavController) {
    var selectedTab by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var appointments by remember { mutableStateOf<List<Appointment>>(emptyList()) }
    var appointmentToCancel by remember { mutableStateOf<Appointment?>(null) }
    var showBooking by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val tabs = listOf("Yaklaşan", "Geçmiş")

    suspend fun loadAppointments() {
        val userId = AuthService.getCurrentUserId() ?: return
        isLoading = true
        try {
            appointments = AppointmentService.fetchAppointments(userId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadAppointments() }

    if (appointmentToCancel != null) {
        AlertDialog(
            onDismissRequest = { appointmentToCancel = null },
            title = { Text("Randevu İptali") },
            text = { Text("Bu randevuyu iptal etmek istediğinizden emin misiniz?") },
            confirmButton = {
                TextButton(onClick = {
                    val a = appointmentToCancel ?: return@TextButton
                    coroutineScope.launch {
                        try {
                            val userId = AuthService.getCurrentUserId() ?: return@launch
                            AppointmentService.cancelAppointment(a.id, userId)
                            loadAppointments()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {}
                        appointmentToCancel = null
                    }
                }) { Text("İptal Et", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { appointmentToCancel = null }) { Text("Geri", color = Primary) } }
        )
    }

    if (showBooking) {
        BookAppointmentDialog(
            onDismiss = { showBooking = false },
            onBooked = {
                showBooking = false
                coroutineScope.launch { loadAppointments() }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Spacing.xl).padding(top = Spacing.sm, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(text = "Randevularım", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            IconButton(onClick = { showBooking = true }) {
                Icon(Icons.Default.Add, contentDescription = "Randevu Al", tint = Primary)
            }
        }

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
                        Text(title, fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selectedTab == index) Primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary) }
        } else {
            val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
            val filtered = if (selectedTab == 0) {
                appointments.filter { it.date >= today && it.status != "cancelled" }
            } else {
                appointments.filter { it.date < today || it.status == "cancelled" || it.status == "completed" }
            }

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                        Text(text = if (selectedTab == 0) "Yaklaşan randevu yok" else "Geçmiş randevu bulunamadı", fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filtered) { appointment ->
                        AppointmentCard(
                            appointment = appointment,
                            showCancelOption = selectedTab == 0,
                            onCancel = { appointmentToCancel = appointment }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun AppointmentCard(appointment: Appointment, showCancelOption: Boolean, onCancel: () -> Unit) {
    val (statusLabel, statusColor) = when (appointment.status) {
        "confirmed" -> "Onaylandı" to Color(0xFF4CAF50)
        "pending" -> "Bekliyor" to Color(0xFFFF9800)
        "cancelled" -> "İptal" to Color.Red
        "completed" -> "Tamamlandı" to Color(0xFF6C5DD3)
        else -> appointment.status to MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
    }
    val typeLabel = when (appointment.type) {
        "personal_training" -> "Kişisel Antrenman"
        "consultation" -> "Danışmanlık"
        "assessment" -> "Değerlendirme"
        else -> appointment.type
    }

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = typeLabel, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Text(text = appointment.date, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            }
            Box(modifier = Modifier.clip(RoundedCornerShape(Radius.pill)).background(statusColor.copy(alpha = 0.1f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(text = statusLabel, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = statusColor)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.AccessTime, contentDescription = null, tint = Primary, modifier = Modifier.size(14.dp))
            Text(text = "${appointment.startTime} - ${appointment.endTime}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        }
        appointment.notes?.takeIf { it.isNotEmpty() }?.let {
            Text(text = it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
        }
        if (showCancelOption && (appointment.status == "pending" || appointment.status == "confirmed")) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookAppointmentDialog(onDismiss: () -> Unit, onBooked: () -> Unit) {
    var coaches by remember { mutableStateOf<List<Coach>>(emptyList()) }
    var selectedCoach by remember { mutableStateOf<Coach?>(null) }
    var selectedDate by remember { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_DATE)) }
    var startTime by remember { mutableStateOf("10:00") }
    var endTime by remember { mutableStateOf("11:00") }
    var selectedType by remember { mutableStateOf("personal_training") }
    var notes by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }
    var startExpanded by remember { mutableStateOf(false) }
    var endExpanded by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val types = listOf("personal_training" to "Kişisel Antrenman", "consultation" to "Danışmanlık", "assessment" to "Değerlendirme")
    val timeSlots = (8..20).map { h -> String.format("%02d:00", h) }

    LaunchedEffect(Unit) {
        try { coaches = CoachService.fetchCoaches() } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni Randevu", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Coach selection
                if (coaches.isNotEmpty()) {
                    Text(text = "Koç Seçin", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        coaches.forEach { coach ->
                            val sel = selectedCoach?.id == coach.id
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .background(if (sel) Primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface)
                                    .clickable(onClick = { selectedCoach = coach })
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(if (sel) Primary else MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = if (sel) Color.White else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                                }
                                Text(text = coach.fullName, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                                if (sel) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Appointment type
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                    OutlinedTextField(value = types.find { it.first == selectedType }?.second ?: "", onValueChange = {}, readOnly = true, label = { Text("Randevu Türü") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary), modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        types.forEach { (k, l) -> DropdownMenuItem(text = { Text(l) }, onClick = { selectedType = k; typeExpanded = false }) }
                    }
                }

                OutlinedTextField(value = selectedDate, onValueChange = { selectedDate = it }, label = { Text("Tarih (YYYY-MM-DD)") }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary), singleLine = true, modifier = Modifier.fillMaxWidth())

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExposedDropdownMenuBox(expanded = startExpanded, onExpandedChange = { startExpanded = it }, modifier = Modifier.weight(1f)) {
                        OutlinedTextField(value = startTime, onValueChange = {}, readOnly = true, label = { Text("Başlangıç") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = startExpanded) }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary), modifier = Modifier.menuAnchor())
                        ExposedDropdownMenu(expanded = startExpanded, onDismissRequest = { startExpanded = false }) {
                            timeSlots.forEach { t -> DropdownMenuItem(text = { Text(t) }, onClick = { startTime = t; startExpanded = false }) }
                        }
                    }
                    ExposedDropdownMenuBox(expanded = endExpanded, onExpandedChange = { endExpanded = it }, modifier = Modifier.weight(1f)) {
                        OutlinedTextField(value = endTime, onValueChange = {}, readOnly = true, label = { Text("Bitiş") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = endExpanded) }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary), modifier = Modifier.menuAnchor())
                        ExposedDropdownMenu(expanded = endExpanded, onDismissRequest = { endExpanded = false }) {
                            timeSlots.forEach { t -> DropdownMenuItem(text = { Text(t) }, onClick = { endTime = t; endExpanded = false }) }
                        }
                    }
                }

                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Not (isteğe bağlı)") }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    coroutineScope.launch {
                        isSaving = true
                        try {
                            val userId = AuthService.getCurrentUserId() ?: return@launch
                            AppointmentService.bookAppointment(
                                userId = userId,
                                coachId = selectedCoach?.id,
                                date = selectedDate,
                                startTime = startTime,
                                endTime = endTime,
                                type = selectedType,
                                notes = notes.takeIf { it.isNotBlank() }
                            )
                            onBooked()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                        } finally { isSaving = false }
                    }
                },
                enabled = !isSaving
            ) {
                if (isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Primary, strokeWidth = 2.dp)
                else Text("Randevu Al", color = Primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}
