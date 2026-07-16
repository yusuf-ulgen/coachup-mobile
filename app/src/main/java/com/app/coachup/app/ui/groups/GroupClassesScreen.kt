package com.app.coachup.app.ui.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.app.coachup.app.models.ClassBooking
import com.app.coachup.app.models.GroupClass
import com.app.coachup.app.services.AuthService
import com.app.coachup.app.services.GroupClassService
import com.app.coachup.app.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Calendar

@Composable
fun GroupClassesScreen(navController: NavController) {
    val today = Calendar.getInstance().let { cal ->
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        // Sunday=1 → 6, Monday=2 → 0, ..., Saturday=7 → 5
        if (dayOfWeek == 1) 6 else dayOfWeek - 2
    }

    var selectedDay by remember { mutableIntStateOf(today) }
    var isLoading by remember { mutableStateOf(true) }
    var classes by remember { mutableStateOf<List<GroupClass>>(emptyList()) }
    var myBookings by remember { mutableStateOf<List<ClassBooking>>(emptyList()) }
    var seatCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val weekDays = listOf("Pzt", "Sal", "Çar", "Per", "Cm", "Cts", "Paz")

    fun dateForSelectedDay(): LocalDate {
        val todayDate = LocalDate.now()
        val todayDow = todayDate.dayOfWeek.value - 1 // Mon=0
        val diff = selectedDay - todayDow
        return todayDate.plusDays(diff.toLong())
    }

    fun isClassPast(groupClass: GroupClass): Boolean {
        val classDate = dateForSelectedDay()
        val todayDate = LocalDate.now()
        if (classDate.isBefore(todayDate)) return true
        if (classDate.isAfter(todayDate)) return false
        return try {
            val start = java.time.LocalTime.parse(groupClass.startTime.take(5))
            start.isBefore(java.time.LocalTime.now())
        } catch (_: Exception) {
            false
        }
    }

    suspend fun loadData() {
        val userId = AuthService.getCurrentUserId() ?: return
        isLoading = true
        try {
            classes = GroupClassService.fetchClassesByDay(selectedDay)
            myBookings = GroupClassService.fetchMyBookings(userId)
            val bookingDate = dateForSelectedDay().toString()
            seatCounts = GroupClassService.countActiveSeatsForClasses(classes.map { it.id }, bookingDate)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(selectedDay) { loadData() }

    LaunchedEffect(actionMessage) {
        actionMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            actionMessage = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Spacing.xl).padding(top = Spacing.sm, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(text = "Grup Dersleri", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        }

        // Day selector
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xl).padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            weekDays.forEachIndexed { index, day ->
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                        .background(if (selectedDay == index) Color(0xFF6C5DD3) else Color.Transparent)
                        .clickable { selectedDay = index },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = day,
                        fontSize = 12.sp,
                        fontWeight = if (selectedDay == index) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selectedDay == index) Color.White else MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary) }
        } else if (classes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                    Text(text = "Bu gün için ders bulunamadı", fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(classes) { groupClass ->
                    val bookingDate = dateForSelectedDay().toString()
                    val booking = myBookings.find {
                        it.classId == groupClass.id &&
                            it.bookingDate == bookingDate &&
                            GroupClassService.isJoinedStatus(it.status)
                    }
                    val isBooked = booking != null
                    val isWaiting = booking != null && GroupClassService.isWaitingStatus(booking.status)
                    val isPast = isClassPast(groupClass)
                    val currentSeats = seatCounts[groupClass.id]
                        ?: groupClass.currentParticipants
                    GroupClassCard(
                        groupClass = groupClass,
                        currentSeats = currentSeats,
                        isBooked = isBooked,
                        isWaiting = isWaiting,
                        isPast = isPast,
                        onJoin = {
                            coroutineScope.launch {
                                try {
                                    val userId = AuthService.getCurrentUserId() ?: return@launch
                                    val result = GroupClassService.joinClass(
                                        userId = userId,
                                        classId = groupClass.id,
                                        date = bookingDate
                                    )
                                    loadData()
                                    actionMessage = when (result) {
                                        GroupClassService.JoinResult.WAITING -> "Bekleme listesine eklendiniz."
                                        GroupClassService.JoinResult.ALREADY_JOINED -> "Zaten kayıtlısınız."
                                        GroupClassService.JoinResult.CONFIRMED -> "Derse katıldınız."
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    actionMessage = "Derse katılım başarısız oldu. Lütfen tekrar deneyin."
                                }
                            }
                        },
                        onLeave = {
                            coroutineScope.launch {
                                try {
                                    val userId = AuthService.getCurrentUserId() ?: return@launch
                                    val b = booking ?: return@launch
                                    GroupClassService.leaveClass(bookingId = b.id, userId = userId)
                                    loadData()
                                    actionMessage = if (isWaiting) "Bekleme listesinden çıktınız." else "Dersten ayrıldınız."
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    actionMessage = "Dersten ayrılma başarısız oldu. Lütfen tekrar deneyin."
                                }
                            }
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 24.dp)
    )
    }
}

@Composable
private fun GroupClassCard(
    groupClass: GroupClass,
    currentSeats: Int,
    isBooked: Boolean,
    isWaiting: Boolean,
    isPast: Boolean,
    onJoin: () -> Unit,
    onLeave: () -> Unit
) {
    val isFull = currentSeats >= groupClass.capacity

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = groupClass.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                groupClass.instructorName?.let { Text(text = it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)) }
            }
            when {
                isWaiting -> Box(
                    modifier = Modifier.clip(RoundedCornerShape(Radius.pill)).background(Color(0xFFFF9800).copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 4.dp)
                ) { Text(text = "Yedekte", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFFFF9800)) }
                isBooked -> Box(
                    modifier = Modifier.clip(RoundedCornerShape(Radius.pill)).background(Color(0xFF4CAF50).copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 4.dp)
                ) { Text(text = "Katıldın", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF4CAF50)) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                Text(text = "${groupClass.startTime} - ${groupClass.endTime}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            }
            groupClass.location?.let { loc ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                    Text(text = loc, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Group, contentDescription = null, tint = if (isFull) Color.Red else Primary, modifier = Modifier.size(14.dp))
                Text(
                    text = "$currentSeats/${groupClass.capacity}",
                    fontSize = 13.sp,
                    color = if (isFull) Color.Red else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                if (isFull) Text(text = " · Dolu", fontSize = 13.sp, color = Color.Red)
            }
            when {
                isPast -> Box(
                    modifier = Modifier.clip(RoundedCornerShape(Radius.pill)).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)).padding(horizontal = 16.dp, vertical = 8.dp)
                ) { Text("Bitti", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)) }
                isBooked -> OutlinedButton(
                    onClick = onLeave,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                    border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) { Text("Ayrıl", fontSize = 13.sp) }
                isFull -> Button(
                    onClick = onJoin,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                    shape = RoundedCornerShape(Radius.pill),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) { Text("Bekleme Listesi", fontSize = 12.sp) }
                else -> Button(
                    onClick = onJoin,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(Radius.pill),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) { Text("Katıl", fontSize = 13.sp) }
            }
        }
    }
}
