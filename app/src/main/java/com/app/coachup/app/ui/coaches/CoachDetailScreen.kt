package com.app.coachup.app.ui.coaches

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.app.coachup.app.models.*
import com.app.coachup.app.services.AuthService
import com.app.coachup.app.services.CoachService
import com.app.coachup.app.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class CoachDetailViewModel : ViewModel() {

    private val _lessonSlots = MutableStateFlow<List<LessonSlot>>(emptyList())
    val lessonSlots: StateFlow<List<LessonSlot>> = _lessonSlots.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _bookingSlotId = MutableStateFlow<String?>(null)
    val bookingSlotId: StateFlow<String?> = _bookingSlotId.asStateFlow()

    private val _bookingSuccess = MutableStateFlow<String?>(null)
    val bookingSuccess: StateFlow<String?> = _bookingSuccess.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadSchedules(coachId: String, dayOfWeek: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val schedules = CoachService.fetchCoachScheduleForDay(coachId, dayOfWeek)
                _lessonSlots.value = schedules.map { LessonSlot.from(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _lessonSlots.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun bookSession(userId: String, coachId: String, slot: LessonSlot, dayOfWeek: Int) {
        val scheduleId = slot.scheduleId ?: return
        _bookingSlotId.value = slot.id

        viewModelScope.launch {
            try {
                // Calculate booking date for current week
                val today = LocalDate.now()
                val currentDayOfWeek = today.dayOfWeek.value - 1 // 0 = Mon
                val daysToAdd = dayOfWeek - currentDayOfWeek
                val bookingDate = today.plusDays(daysToAdd.toLong())
                val dateStr = bookingDate.atStartOfDay().toString() // ISO-8601 date-time

                CoachService.bookSession(
                    userId = userId,
                    coachId = coachId,
                    scheduleId = scheduleId,
                    scheduledAt = dateStr
                )
                _bookingSuccess.value = "Seans başarıyla rezerve edildi!"
                // Refresh slots
                loadSchedules(coachId, dayOfWeek)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.message ?: "Rezervasyon başarısız"
            } finally {
                _bookingSlotId.value = null
            }
        }
    }

    fun clearMessages() {
        _bookingSuccess.value = null
        _error.value = null
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@Composable
fun CoachDetailScreen(
    coach: LocalCoach,
    onNavigateBack: () -> Unit,
    onNavigateToChat: (LocalCoach) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        DetailHeader(
            coach = coach,
            onBack = onNavigateBack,
            modifier = Modifier
                .padding(horizontal = Spacing.xl)
                .padding(top = Spacing.sm, bottom = 24.dp)
        )

        LazyColumn(
            contentPadding = PaddingValues(bottom = 120.dp),
            modifier = Modifier.weight(1f)
        ) {
            item {
                CoachProfileSection(
                    coach = coach,
                    modifier = Modifier.padding(horizontal = Spacing.xl, vertical = 8.dp)
                )
            }

            if (!coach.bio.isNullOrEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = Spacing.xl)
                            .padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Hakkında", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(coach.bio, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xl, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CoachStatChip(
                        icon = Icons.Default.Star,
                        text = String.format("%.1f", coach.rating),
                        color = Color(0xFFFFB300),
                        bgColor = Color(0xFFFFB300).copy(alpha = 0.2f)
                    )
                    CoachStatChip(
                        icon = Icons.Default.WorkHistory,
                        text = "${coach.experienceYears} yıl",
                        color = Color(0xFF64B5F6),
                        bgColor = Color(0xFF64B5F6).copy(alpha = 0.2f)
                    )
                }
            }

            if (coach.specializations.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = Spacing.xl)
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Uzmanlık Alanları", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        OptFlowRow(items = coach.specializations, color = Primary)
                    }
                }
            }

            if (coach.certifications.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = Spacing.xl)
                            .padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Sertifikalar", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        OptFlowRow(items = coach.certifications, color = Color(0xFF81C784))
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.xl, vertical = 16.dp)
                .clip(RoundedCornerShape(Radius.pill))
                .background(Primary)
                .clickable { onNavigateToChat(coach) }
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Text("Mesaj Gönder", style = AppNormalMediumStyle, color = Color.White)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun OptFlowRow(items: List<String>, color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        items.forEach { item ->
            Box(
                modifier = Modifier
                    .background(color.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = item,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = color
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Detail header
// ---------------------------------------------------------------------------

@Composable
private fun DetailHeader(
    coach: LocalCoach,
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
                .size(40.dp)
                .shadow(2.dp, CircleShape)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
        }

        Text(
            text = "Koç",
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        )

        // Gender symbol (High contrast badge)
        val genderColor = if (coach.gender == CoachGender.MALE) Color(0xFF64B5F6) else Color(0xFFFF4081)
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(genderColor.copy(alpha = 0.2f), CircleShape)
                .border(1.5.dp, genderColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (coach.gender == CoachGender.MALE) "♂" else "♀",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = genderColor
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Profile section
// ---------------------------------------------------------------------------

@Composable
private fun CoachProfileSection(
    coach: LocalCoach,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(Primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (coach.profileImageUrl != null) {
                AsyncImage(
                    model = coach.profileImageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(24.dp))
            }
        }

        // Name
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(coach.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(coach.surname, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }

        Spacer(Modifier.weight(1f))

        // Gender symbol (large high contrast badge)
        val genderColor = if (coach.gender == CoachGender.MALE) Color(0xFF64B5F6) else Color(0xFFFF4081)
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(genderColor.copy(alpha = 0.2f), CircleShape)
                .border(1.5.dp, genderColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (coach.gender == CoachGender.MALE) "♂" else "♀",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = genderColor
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Stat chip
// ---------------------------------------------------------------------------

@Composable
private fun CoachStatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color,
    bgColor: Color = color.copy(alpha = 0.15f)
) {
    Row(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(text = text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}
