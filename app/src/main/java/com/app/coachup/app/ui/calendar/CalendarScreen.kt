package com.app.coachup.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.coachup.app.models.*
import com.app.coachup.app.services.AuthService
import com.app.coachup.app.services.EventService
import com.app.coachup.app.services.GoalService
import com.app.coachup.app.services.GroupClassService
import com.app.coachup.app.services.GymEventService
import com.app.coachup.app.services.ScheduleService
import com.app.coachup.app.services.UserService
import com.app.coachup.app.theme.*
import com.app.coachup.app.utils.CalendarContentFilter
import com.app.coachup.app.utils.formatTimeHm
import com.app.coachup.app.utils.halfHourTimeSlots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

// ---------------------------------------------------------------------------
// Local ScheduledProgram UI alias (uses ScheduledProgramUi from CalendarModels)
// ---------------------------------------------------------------------------
private typealias UiProgram = ScheduledProgramUi

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class CalendarViewModel : ViewModel() {

    private val _scheduledPrograms = MutableStateFlow<List<UiProgram>>(emptyList())
    val scheduledPrograms: StateFlow<List<UiProgram>> = _scheduledPrograms.asStateFlow()

    private val _userEvents = MutableStateFlow<List<UserEvent>>(emptyList())
    val userEvents: StateFlow<List<UserEvent>> = _userEvents.asStateFlow()

    private val _eventDays = MutableStateFlow<Set<Int>>(emptySet())
    val eventDays: StateFlow<Set<Int>> = _eventDays.asStateFlow()

    private val _availableGroupClasses = MutableStateFlow<List<GroupClass>>(emptyList())
    val availableGroupClasses: StateFlow<List<GroupClass>> = _availableGroupClasses.asStateFlow()

    private val _bookingsForDate = MutableStateFlow<List<ClassBooking>>(emptyList())
    val bookingsForDate: StateFlow<List<ClassBooking>> = _bookingsForDate.asStateFlow()

    private val _gymEvents = MutableStateFlow<List<GymEvent>>(emptyList())
    val gymEvents: StateFlow<List<GymEvent>> = _gymEvents.asStateFlow()

    private val _eventParticipations = MutableStateFlow<List<EventParticipant>>(emptyList())
    val eventParticipations: StateFlow<List<EventParticipant>> = _eventParticipations.asStateFlow()

    private val _classSeatCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val classSeatCounts: StateFlow<Map<String, Int>> = _classSeatCounts.asStateFlow()

    private val _eventSeatCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val eventSeatCounts: StateFlow<Map<String, Int>> = _eventSeatCounts.asStateFlow()

    private val _goalsForDate = MutableStateFlow<List<UserGoal>>(emptyList())
    val goalsForDate: StateFlow<List<UserGoal>> = _goalsForDate.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    fun clearSnackbarMessage() {
        _snackbarMessage.value = null
    }

    fun loadDayContent(
        userId: String,
        date: LocalDate,
        showAllEvents: Boolean,
        showGymContent: Boolean = showAllEvents
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val records = ScheduleService.fetchCalendarProgramsForDate(userId, date)
                val programs = if (showAllEvents) {
                    CalendarContentFilter.allVisiblePrograms(records)
                } else {
                    CalendarContentFilter.joinedPrograms(records)
                }
                _scheduledPrograms.value = programs.map { db ->
                    ScheduledProgramUi(
                        id = db.id,
                        title = db.program?.name ?: "Antrenman",
                        coach = db.coach?.let { "Koç ${it.name}" } ?: "",
                        startTime = db.startTime.take(5),
                        endTime = db.endTime.take(5),
                        iconName = db.program?.iconName ?: "fitness_center",
                        type = ProgramType.fromCategory(db.program?.category)
                    )
                }

                _availableGroupClasses.value = if (showGymContent) {
                    if (showAllEvents) {
                        GroupClassService.fetchClassesForDate(date)
                    } else {
                        val bookings = GroupClassService.fetchBookingsForDate(userId, date)
                            .let { CalendarContentFilter.joinedBookings(it) }
                        val classIds = bookings.map { it.classId }.distinct()
                        GroupClassService.fetchClassesByIds(classIds)
                    }
                } else {
                    emptyList()
                }
                _bookingsForDate.value = if (showGymContent) {
                    GroupClassService.fetchBookingsForDate(userId, date)
                } else {
                    emptyList()
                }
                _classSeatCounts.value = if (showGymContent && _availableGroupClasses.value.isNotEmpty()) {
                    GroupClassService.countActiveSeatsForClasses(
                        _availableGroupClasses.value.map { it.id },
                        date.toString()
                    )
                } else {
                    emptyMap()
                }
                _gymEvents.value = if (showGymContent && showAllEvents) {
                    GymEventService.fetchEventsForDate(date)
                } else {
                    emptyList()
                }
                _eventParticipations.value = if (showGymContent) {
                    GymEventService.fetchParticipationsForDate(userId, date)
                } else {
                    emptyList()
                }
                _eventSeatCounts.value = if (showGymContent && _gymEvents.value.isNotEmpty()) {
                    GymEventService.countRegisteredForEvents(_gymEvents.value.map { it.id })
                } else {
                    emptyMap()
                }
                _goalsForDate.value = if (showAllEvents) {
                    GoalService.fetchGoalsForDate(userId, date)
                } else {
                    emptyList()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _scheduledPrograms.value = emptyList()
                _availableGroupClasses.value = emptyList()
                _bookingsForDate.value = emptyList()
                _classSeatCounts.value = emptyMap()
                _gymEvents.value = emptyList()
                _eventParticipations.value = emptyList()
                _eventSeatCounts.value = emptyMap()
                _goalsForDate.value = emptyList()
            }
            _isLoading.value = false
        }
    }

    /** @deprecated Use [loadDayContent] */
    fun loadScheduledPrograms(userId: String, date: LocalDate) {
        loadDayContent(userId, date, showAllEvents = true)
    }

    fun loadUserEvents(userId: String, date: LocalDate) {
        viewModelScope.launch {
            try {
                _userEvents.value = EventService.fetchEventsForDate(userId, date.toString())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _userEvents.value = emptyList()
            }
        }
    }

    fun loadEventDays(
        userId: String,
        yearMonth: YearMonth,
        showAllEvents: Boolean = true,
        showGymContent: Boolean = showAllEvents
    ) {
        viewModelScope.launch {
            try {
                val allPrograms = ScheduleService.fetchScheduledPrograms(
                    userId,
                    yearMonth.monthValue,
                    yearMonth.year
                )
                val gymPrograms = if (showAllEvents) {
                    ScheduleService.fetchGymPrograms(yearMonth.monthValue, yearMonth.year)
                } else {
                    emptyList()
                }
                val combinedPrograms = allPrograms + gymPrograms
                val scheduleDays = if (showAllEvents) {
                    CalendarContentFilter.allVisiblePrograms(combinedPrograms).mapNotNull { p ->
                        runCatching { LocalDate.parse(p.scheduledDate).dayOfMonth }.getOrNull()
                    }.toSet()
                } else {
                    CalendarContentFilter.joinedPrograms(allPrograms).mapNotNull { p ->
                        runCatching { LocalDate.parse(p.scheduledDate).dayOfMonth }.getOrNull()
                    }.toSet()
                }
                val userEventDays = EventService.fetchEventDays(userId, yearMonth.year, yearMonth.monthValue)
                val goalDays = if (showAllEvents) {
                    GoalService.fetchGoalDays(userId, yearMonth.year, yearMonth.monthValue)
                } else {
                    emptySet()
                }
                val groupClassDays = if (showGymContent) {
                    if (showAllEvents) {
                        GroupClassService.fetchClassDaysInMonth(yearMonth)
                    } else {
                        GroupClassService.fetchBookedDaysInMonth(userId, yearMonth)
                    }
                } else {
                    emptySet()
                }
                val gymEventDays = if (showGymContent && showAllEvents) {
                    GymEventService.fetchEventDaysInMonth(yearMonth)
                } else {
                    emptySet()
                }
                _eventDays.value = scheduleDays + userEventDays + goalDays + groupClassDays + gymEventDays
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _eventDays.value = emptySet()
            }
        }
    }

    fun deleteUserEvent(userId: String, eventId: String, date: LocalDate) {
        viewModelScope.launch {
            try {
                EventService.deleteEvent(eventId)
                loadUserEvents(userId, date)
                loadEventDays(userId, YearMonth.from(date))
                _snackbarMessage.value = "Etkinlik silindi."
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _snackbarMessage.value = "Etkinlik silinemedi. Lütfen tekrar deneyin."
            }
        }
    }

    fun joinGroupClass(
        userId: String,
        classId: String,
        date: LocalDate,
        showAllEvents: Boolean,
        showGymContent: Boolean
    ) {
        viewModelScope.launch {
            try {
                val result = GroupClassService.joinClass(userId, classId, date.toString())
                loadDayContent(userId, date, showAllEvents, showGymContent)
                loadEventDays(userId, YearMonth.from(date), showAllEvents, showGymContent)
                _snackbarMessage.value = when (result) {
                    GroupClassService.JoinResult.WAITING -> "Bekleme listesine eklendiniz."
                    GroupClassService.JoinResult.ALREADY_JOINED -> "Zaten kayıtlısınız."
                    GroupClassService.JoinResult.CONFIRMED -> "Derse katıldınız."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _snackbarMessage.value = "Derse katılım başarısız oldu. Lütfen tekrar deneyin."
            }
        }
    }

    fun leaveGroupClass(
        userId: String,
        bookingId: String,
        date: LocalDate,
        showAllEvents: Boolean,
        showGymContent: Boolean,
        wasWaiting: Boolean = false
    ) {
        viewModelScope.launch {
            try {
                GroupClassService.leaveClass(bookingId, userId)
                loadDayContent(userId, date, showAllEvents, showGymContent)
                loadEventDays(userId, YearMonth.from(date), showAllEvents, showGymContent)
                _snackbarMessage.value = if (wasWaiting) "Bekleme listesinden çıktınız." else "Dersten ayrıldınız."
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _snackbarMessage.value = "Dersten ayrılma başarısız oldu. Lütfen tekrar deneyin."
            }
        }
    }

    fun joinGymEvent(
        userId: String,
        eventId: String,
        date: LocalDate,
        showAllEvents: Boolean,
        showGymContent: Boolean
    ) {
        viewModelScope.launch {
            try {
                val result = GymEventService.joinEvent(userId, eventId)
                loadDayContent(userId, date, showAllEvents, showGymContent)
                loadEventDays(userId, YearMonth.from(date), showAllEvents, showGymContent)
                _snackbarMessage.value = when (result) {
                    GymEventService.JoinResult.WAITING -> "Bekleme listesine eklendiniz."
                    GymEventService.JoinResult.ALREADY_JOINED -> "Zaten kayıtlısınız."
                    GymEventService.JoinResult.CONFIRMED -> "Etkinliğe katıldınız."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _snackbarMessage.value = "Etkinliğe katılım başarısız oldu. Lütfen tekrar deneyin."
            }
        }
    }

    fun leaveGymEvent(
        userId: String,
        participantId: String,
        date: LocalDate,
        showAllEvents: Boolean,
        showGymContent: Boolean,
        wasWaiting: Boolean = false
    ) {
        viewModelScope.launch {
            try {
                GymEventService.leaveEvent(participantId, userId)
                loadDayContent(userId, date, showAllEvents, showGymContent)
                loadEventDays(userId, YearMonth.from(date), showAllEvents, showGymContent)
                _snackbarMessage.value = if (wasWaiting) "Bekleme listesinden çıktınız." else "Etkinlikten ayrıldınız."
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _snackbarMessage.value = "Etkinlikten ayrılma başarısız oldu. Lütfen tekrar deneyin."
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@Composable
fun CalendarScreen(
    navController: NavController? = null,
    showAllEvents: Boolean? = null,
    showGroupClasses: Boolean? = null,
    vm: CalendarViewModel = viewModel()
) {
    val userId by AuthService.currentUser.collectAsState(initial = null)
    val currentProfile by UserService.currentProfile.collectAsState()
    val availableMemberships by UserService.availableMemberships.collectAsState()
    val isIndividualUser = remember(currentProfile, availableMemberships) {
        when {
            currentProfile == null -> true
            else -> UserService.isIndividualUser(currentProfile)
        }
    }
    val effectiveShowAllEvents = showAllEvents ?: !isIndividualUser
    val effectiveShowGymContent = showGroupClasses
        ?: (UserService.resolveActiveGymIdForContent(currentProfile) != null || !isIndividualUser)
    val today = remember { LocalDate.now() }

    var currentYearMonth by remember { mutableStateOf(YearMonth.from(today)) }
    var selectedDay by remember { mutableIntStateOf(today.dayOfMonth) }

    val scheduledPrograms: List<UiProgram> by vm.scheduledPrograms.collectAsState()
    val availableGroupClasses by vm.availableGroupClasses.collectAsState()
    val bookingsForDate by vm.bookingsForDate.collectAsState()
    val classSeatCounts by vm.classSeatCounts.collectAsState()
    val gymEvents by vm.gymEvents.collectAsState()
    val eventParticipations by vm.eventParticipations.collectAsState()
    val eventSeatCounts by vm.eventSeatCounts.collectAsState()
    val goalsForDate by vm.goalsForDate.collectAsState()
    val userEvents: List<UserEvent> by vm.userEvents.collectAsState()
    val eventDays by vm.eventDays.collectAsState()
    val isLoading by vm.isLoading.collectAsState()

    var showAddEventDialog by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<UserEvent?>(null) }
    var eventErrorMessage by remember { mutableStateOf<String?>(null) }
    val snackbarMessage by vm.snackbarMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            vm.clearSnackbarMessage()
        }
    }

    LaunchedEffect(eventErrorMessage) {
        eventErrorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            eventErrorMessage = null
        }
    }

    val weekDayNames = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cts", "Paz")

    val selectedDate = remember(currentYearMonth, selectedDay) {
        LocalDate.of(currentYearMonth.year, currentYearMonth.monthValue, selectedDay)
    }

    // Reload when selection changes
    LaunchedEffect(userId, selectedDay, currentYearMonth, effectiveShowAllEvents, effectiveShowGymContent) {
        val uid = userId?.id ?: return@LaunchedEffect
        vm.loadDayContent(uid, selectedDate, effectiveShowAllEvents, effectiveShowGymContent)
        vm.loadUserEvents(uid, selectedDate)
    }

    LaunchedEffect(userId, currentYearMonth, effectiveShowAllEvents, effectiveShowGymContent) {
        val uid = userId?.id ?: return@LaunchedEffect
        vm.loadEventDays(uid, currentYearMonth, effectiveShowAllEvents, effectiveShowGymContent)
    }

    if (showAddEventDialog || editingEvent != null) {
        AddUserEventDialog(
            selectedDate = selectedDate.toString(),
            editingEvent = editingEvent,
            onDismiss = { showAddEventDialog = false; editingEvent = null },
            onSaved = {
                showAddEventDialog = false
                editingEvent = null
                val uid = userId?.id ?: return@AddUserEventDialog
                vm.loadUserEvents(uid, selectedDate)
                vm.loadEventDays(uid, currentYearMonth)
            },
            onError = { eventErrorMessage = it }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        navController?.let { nav ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { nav.popBackStack() },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Geri",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Takvim",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Month header
        MonthHeader(
            yearMonth = currentYearMonth,
            onPrevious = {
                currentYearMonth = currentYearMonth.minusMonths(1)
                selectedDay = 1
            },
            onNext = {
                currentYearMonth = currentYearMonth.plusMonths(1)
                selectedDay = 1
            },
            modifier = Modifier
                .padding(horizontal = Spacing.xl)
                .padding(top = Spacing.sm, bottom = Spacing.lg)
        )

        // Weekday header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl)
                .padding(bottom = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            weekDayNames.forEach { day ->
                Text(
                    text = day,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Calendar grid
        val calendarDays = remember(currentYearMonth, today, eventDays) {
            generateCalendarDays(currentYearMonth, today, eventDays)
        }
        CalendarGrid(
            days = calendarDays,
            selectedDay = selectedDay,
            onDayClick = { day -> selectedDay = day },
            modifier = Modifier
                .padding(horizontal = Spacing.xl)
                .padding(bottom = 24.dp)
        )

        // Programs list
        LazyColumn(
            contentPadding = PaddingValues(bottom = 100.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (effectiveShowAllEvents) "Günün Programı" else "Katıldığım Etkinlikler",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            when {
                isLoading -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = Primary, strokeWidth = 2.dp) }
                }

                scheduledPrograms.isEmpty() && availableGroupClasses.isEmpty() && gymEvents.isEmpty() && goalsForDate.isEmpty() && userEvents.isEmpty() -> item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "Bu gün için program yok",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    items(scheduledPrograms, key = { it.id }) { program: UiProgram ->
                        ScheduledProgramCard(
                            program = program,
                            modifier = Modifier.padding(horizontal = Spacing.xl, vertical = 6.dp)
                        )
                    }
                    if (effectiveShowGymContent && availableGroupClasses.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.padding(horizontal = Spacing.xl).padding(top = 12.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Groups, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                                Text(
                                    text = if (effectiveShowAllEvents) "Grup Dersleri" else "Katıldığım Grup Dersleri",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        items(availableGroupClasses, key = { it.id }) { groupClass ->
                            val booking = bookingsForDate.find {
                                it.classId == groupClass.id && GroupClassService.isJoinedStatus(it.status)
                            }
                            val isPast = isGroupClassPast(selectedDate, groupClass)
                            val currentSeats = classSeatCounts[groupClass.id] ?: groupClass.currentParticipants
                            val isWaiting = booking != null && GroupClassService.isWaitingStatus(booking.status)
                            GroupClassCalendarCard(
                                groupClass = groupClass,
                                currentSeats = currentSeats,
                                showJoinAction = effectiveShowAllEvents,
                                isPast = isPast,
                                isBooked = booking != null,
                                isWaiting = isWaiting,
                                onJoin = {
                                    val uid = userId?.id ?: return@GroupClassCalendarCard
                                    vm.joinGroupClass(
                                        uid,
                                        groupClass.id,
                                        selectedDate,
                                        effectiveShowAllEvents,
                                        effectiveShowGymContent
                                    )
                                },
                                onLeave = {
                                    val uid = userId?.id ?: return@GroupClassCalendarCard
                                    val bookingId = booking?.id ?: return@GroupClassCalendarCard
                                    vm.leaveGroupClass(
                                        uid,
                                        bookingId,
                                        selectedDate,
                                        effectiveShowAllEvents,
                                        effectiveShowGymContent,
                                        wasWaiting = isWaiting
                                    )
                                },
                                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = 6.dp)
                            )
                        }
                    }
                    if (effectiveShowGymContent && gymEvents.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.padding(horizontal = Spacing.xl).padding(top = 12.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "Salon Etkinlikleri",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        items(gymEvents, key = { it.id }) { gymEvent ->
                            val participation = eventParticipations.find {
                                it.eventId == gymEvent.id && GymEventService.isJoinedStatus(it.status)
                            }
                            val isWaiting = participation != null && GymEventService.isWaitingStatus(participation.status)
                            val currentSeats = eventSeatCounts[gymEvent.id] ?: 0
                            GymEventCalendarCard(
                                gymEvent = gymEvent,
                                currentSeats = currentSeats,
                                isRegistered = participation != null,
                                isWaiting = isWaiting,
                                onJoin = {
                                    val uid = userId?.id ?: return@GymEventCalendarCard
                                    vm.joinGymEvent(uid, gymEvent.id, selectedDate, effectiveShowAllEvents, effectiveShowGymContent)
                                },
                                onLeave = {
                                    val uid = userId?.id ?: return@GymEventCalendarCard
                                    val participantId = participation?.id ?: return@GymEventCalendarCard
                                    vm.leaveGymEvent(
                                        uid,
                                        participantId,
                                        selectedDate,
                                        effectiveShowAllEvents,
                                        effectiveShowGymContent,
                                        wasWaiting = isWaiting
                                    )
                                },
                                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = 6.dp)
                            )
                        }
                    }
                    if (effectiveShowAllEvents && goalsForDate.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.padding(horizontal = Spacing.xl).padding(top = 12.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Flag, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                                Text("Hedefler", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        items(goalsForDate, key = { it.id }) { goal ->
                            GoalCalendarCard(
                                goal = goal,
                                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // User events section
            if (userEvents.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.xl).padding(top = 16.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Event, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                        Text(text = "Etkinliklerim", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                items(userEvents, key = { it.id }) { event ->
                    UserEventCard(
                        event = event,
                        onEdit = { editingEvent = event },
                        onDelete = {
                            val uid = userId?.id ?: return@UserEventCard
                            vm.deleteUserEvent(uid, event.id, selectedDate)
                        },
                        modifier = Modifier.padding(horizontal = Spacing.xl, vertical = 6.dp)
                    )
                }
            }
        }
    }

    // FAB to add event
    FloatingActionButton(
        onClick = { showAddEventDialog = true },
        modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
        containerColor = Primary
    ) {
        Icon(Icons.Default.Add, contentDescription = "Etkinlik Ekle", tint = Color.White)
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 88.dp)
    )
    }
}

// ---------------------------------------------------------------------------
// Month header
// ---------------------------------------------------------------------------

@Composable
private fun MonthHeader(
    yearMonth: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .shadow(1.dp, CircleShape)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .clickable { onPrevious() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = "Önceki ay",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
        }

        val monthLabel = yearMonth.month.getDisplayName(JTextStyle.FULL, Locale("tr"))
            .replaceFirstChar { it.uppercase() }
        Text(
            text = "$monthLabel ${yearMonth.year}",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Box(
            modifier = Modifier
                .size(36.dp)
                .shadow(1.dp, CircleShape)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .clickable { onNext() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Sonraki ay",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Calendar grid
// ---------------------------------------------------------------------------

@Composable
private fun CalendarGrid(
    days: List<CalendarDay>,
    selectedDay: Int,
    onDayClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = days.chunked(7)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { day ->
                    val isSelected = day.day == selectedDay && day.isCurrentMonth
                    CalendarDayCell(
                        day = day,
                        isSelected = isSelected,
                        onClick = { if (day.isCurrentMonth) onDayClick(day.day) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: CalendarDay,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textColor = when {
        isSelected -> Color.White
        !day.isCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        day.isToday -> Primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .height(44.dp)
            .clickable(enabled = day.isCurrentMonth) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // Background circle
        if (isSelected) {
            Box(modifier = Modifier.size(36.dp).background(Primary, CircleShape))
        } else if (day.isToday) {
            Box(modifier = Modifier.size(36.dp).border(2.dp, Primary, CircleShape))
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${day.day}",
                fontSize = 14.sp,
                fontWeight = if (day.isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = textColor
            )
            if (day.hasEvent && day.isCurrentMonth) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(
                            if (isSelected) Color.White else Primary,
                            CircleShape
                        )
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Scheduled program card
// ---------------------------------------------------------------------------

@Composable
private fun ScheduledProgramCard(
    program: UiProgram,
    modifier: Modifier = Modifier
) {
    val typeColor = when (program.type) {
        ProgramType.CARDIO -> Primary
        ProgramType.STRENGTH -> Purple100
        ProgramType.FLEXIBILITY -> Color(0xFF4CAF50)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon box
        Box(
            modifier = Modifier
                .size(50.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.FitnessCenter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }

        // Content
        Row(
            modifier = Modifier
                .weight(1f)
                .background(typeColor.copy(alpha = if (program.type == ProgramType.STRENGTH) 1f else 0.85f), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = program.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                if (program.coach.isNotEmpty()) {
                    Text(
                        text = program.coach,
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "${program.startTime} - ${program.endTime}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
private fun GroupClassCalendarCard(
    groupClass: GroupClass,
    currentSeats: Int = 0,
    showJoinAction: Boolean = true,
    isPast: Boolean = false,
    isBooked: Boolean = false,
    isWaiting: Boolean = false,
    onJoin: () -> Unit = {},
    onLeave: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isFull = currentSeats >= groupClass.capacity
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).background(Primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Groups, contentDescription = null, tint = Primary, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(groupClass.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "${groupClass.startTime.take(5)} - ${groupClass.endTime.take(5)}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$currentSeats/${groupClass.capacity}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isFull) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isFull) {
                    Text("Dolu", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFE53935))
                }
                if (isWaiting) {
                    Text("Yedekte", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFFFF9800))
                }
            }
        }
        when {
            isPast -> Text(
                "Bitti",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            isBooked && showJoinAction -> Text(
                "Ayrıl",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE53935),
                modifier = Modifier.clickable { onLeave() }
            )
            isBooked -> Text(
                if (isWaiting) "Yedekte" else "Katıldın",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isWaiting) Color(0xFFFF9800) else Color(0xFF4CAF50)
            )
            showJoinAction && isFull -> Text(
                "Bekleme Listesi",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFFF9800),
                modifier = Modifier.clickable { onJoin() }
            )
            showJoinAction -> Text(
                "Katıl",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Primary,
                modifier = Modifier.clickable { onJoin() }
            )
        }
    }
}

@Composable
private fun GymEventCalendarCard(
    gymEvent: GymEvent,
    currentSeats: Int = 0,
    isRegistered: Boolean = false,
    isWaiting: Boolean = false,
    onJoin: () -> Unit = {},
    onLeave: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val capacity = gymEvent.capacity
    val isFull = capacity != null && currentSeats >= capacity
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).background(Primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Star, contentDescription = null, tint = Primary, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(gymEvent.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            gymEvent.formattedStartTime?.let { time ->
                Text(time, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            gymEvent.location?.takeIf { it.isNotBlank() }?.let { location ->
                Text(location, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (capacity != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$currentSeats/$capacity",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isFull) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isFull) {
                        Text("Dolu", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFE53935))
                    }
                    if (isWaiting) {
                        Text("Yedekte", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFFFF9800))
                    }
                }
            }
        }
        when {
            isRegistered -> Text(
                "Ayrıl",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE53935),
                modifier = Modifier.clickable { onLeave() }
            )
            isFull -> Text(
                "Bekleme Listesi",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFFF9800),
                modifier = Modifier.clickable { onJoin() }
            )
            else -> Text(
                "Katıl",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Primary,
                modifier = Modifier.clickable { onJoin() }
            )
        }
    }
}

private fun isGroupClassPast(date: LocalDate, groupClass: GroupClass): Boolean {
    val today = LocalDate.now()
    if (date.isBefore(today)) return true
    if (date.isAfter(today)) return false
    return try {
        val start = java.time.LocalTime.parse(groupClass.startTime.take(5))
        start.isBefore(java.time.LocalTime.now())
    } catch (_: Exception) {
        false
    }
}

@Composable
private fun GoalCalendarCard(goal: UserGoal, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).background(Color(0xFFFF9800).copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Flag, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(goal.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text("${goal.progressPercentage}% tamamlandı", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------------------
// Calendar day generation
// ---------------------------------------------------------------------------

private fun generateCalendarDays(
    yearMonth: YearMonth,
    today: LocalDate,
    eventDays: Set<Int>
): List<CalendarDay> {
    val days = mutableListOf<CalendarDay>()
    val firstDay = LocalDate.of(yearMonth.year, yearMonth.monthValue, 1)
    // Monday-based: Monday=0 ... Sunday=6
    var firstWeekday = firstDay.dayOfWeek.value - 1 // 0=Mon ... 6=Sun

    // Previous month fill
    if (firstWeekday > 0) {
        val prevMonth = yearMonth.minusMonths(1)
        val prevLen = prevMonth.lengthOfMonth()
        val startDay = prevLen - firstWeekday + 1
        for (d in startDay..prevLen) {
            days.add(CalendarDay(day = d, isCurrentMonth = false, isToday = false, hasEvent = false))
        }
    }

    // Current month days
    val len = yearMonth.lengthOfMonth()
    for (d in 1..len) {
        val date = LocalDate.of(yearMonth.year, yearMonth.monthValue, d)
        days.add(
            CalendarDay(
                day = d,
                isCurrentMonth = true,
                isToday = date == today,
                hasEvent = eventDays.contains(d)
            )
        )
    }

    // Next month fill (to reach 42 cells = 6 rows)
    val remaining = 42 - days.size
    for (d in 1..remaining) {
        days.add(CalendarDay(day = d, isCurrentMonth = false, isToday = false, hasEvent = false))
    }

    return days
}

// ---------------------------------------------------------------------------
// User event card
// ---------------------------------------------------------------------------

@Composable
private fun UserEventCard(
    event: UserEvent,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconVector = when (event.icon) {
        "run" -> Icons.Default.DirectionsRun
        "dumbbell" -> Icons.Default.FitnessCenter
        "food" -> Icons.Default.Restaurant
        "health" -> Icons.Default.Favorite
        "meeting" -> Icons.Default.People
        "book" -> Icons.Default.MenuBook
        "star" -> Icons.Default.Star
        "bell" -> Icons.Default.Notifications
        "home" -> Icons.Default.Home
        "car" -> Icons.Default.DirectionsCar
        else -> Icons.Default.Event
    }
    val cardColor = when (event.color) {
        "orange" -> Color(0xFFFF6047)
        "green"  -> Color(0xFF4CAF50)
        "purple" -> Color(0xFF8B5CF6)
        "red"    -> Color(0xFFF44336)
        "yellow" -> Color(0xFFFFC107)
        else     -> Color(0xFF3B82F6) // blue
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(cardColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(iconVector, contentDescription = null, tint = cardColor, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = event.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            val timeLabel = if (event.startTime != null && event.endTime != null) {
                "${formatTimeHm(event.startTime)} - ${formatTimeHm(event.endTime)}"
            } else formatTimeHm(event.startTime)
            if (timeLabel.isNotEmpty()) {
                Text(text = timeLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            }
            event.note?.takeIf { it.isNotEmpty() }?.let {
                Text(text = it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), maxLines = 1)
            }
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Edit, contentDescription = "Düzenle", tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Sil", tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Add/Edit User Event Dialog
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddUserEventDialog(
    selectedDate: String,
    editingEvent: UserEvent?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onError: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var title by remember { mutableStateOf(editingEvent?.title ?: "") }
    var note by remember { mutableStateOf(editingEvent?.note ?: "") }
    var startTime by remember(editingEvent?.id) {
        mutableStateOf(formatTimeHm(editingEvent?.startTime).ifBlank { "09:00" })
    }
    var endTime by remember(editingEvent?.id) {
        mutableStateOf(formatTimeHm(editingEvent?.endTime).ifBlank { "10:00" })
    }
    var selectedIcon by remember { mutableStateOf(editingEvent?.icon ?: "calendar") }
    var selectedColor by remember { mutableStateOf(editingEvent?.color ?: "blue") }
    var isSaving by remember { mutableStateOf(false) }
    var startExpanded by remember { mutableStateOf(false) }
    var endExpanded by remember { mutableStateOf(false) }

    val timeSlots = halfHourTimeSlots(fromHour = 6, toHour = 22)
    val colorOptions = listOf("blue" to Color(0xFF3B82F6), "orange" to Color(0xFFFF6047), "green" to Color(0xFF4CAF50), "purple" to Color(0xFF8B5CF6), "red" to Color(0xFFF44336), "yellow" to Color(0xFFFFC107))
    val iconOptions = listOf("calendar", "run", "dumbbell", "food", "health", "meeting", "book", "star", "bell", "home", "car")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editingEvent != null) "Etkinliği Düzenle" else "Etkinlik Ekle", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Başlık") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFF6047)),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Time selectors
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExposedDropdownMenuBox(expanded = startExpanded, onExpandedChange = { startExpanded = it }, modifier = Modifier.weight(1f)) {
                        OutlinedTextField(value = startTime, onValueChange = {}, readOnly = true, label = { Text("Başlangıç") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = startExpanded) }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFF6047)), modifier = Modifier.menuAnchor())
                        ExposedDropdownMenu(expanded = startExpanded, onDismissRequest = { startExpanded = false }) {
                            timeSlots.forEach { t -> DropdownMenuItem(text = { Text(t) }, onClick = { startTime = t; startExpanded = false }) }
                        }
                    }
                    ExposedDropdownMenuBox(expanded = endExpanded, onExpandedChange = { endExpanded = it }, modifier = Modifier.weight(1f)) {
                        OutlinedTextField(value = endTime, onValueChange = {}, readOnly = true, label = { Text("Bitiş") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = endExpanded) }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFF6047)), modifier = Modifier.menuAnchor())
                        ExposedDropdownMenu(expanded = endExpanded, onDismissRequest = { endExpanded = false }) {
                            timeSlots.forEach { t -> DropdownMenuItem(text = { Text(t) }, onClick = { endTime = t; endExpanded = false }) }
                        }
                    }
                }

                // Color picker
                Text(text = "Renk", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colorOptions.forEach { (id, color) ->
                        Box(
                            modifier = Modifier.size(28.dp).clip(CircleShape)
                                .background(color)
                                .then(if (selectedColor == id) Modifier.border(2.dp, MaterialTheme.colorScheme.onBackground, CircleShape) else Modifier)
                                .clickable { selectedColor = id }
                        )
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Not (isteğe bağlı)") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFF6047)),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isBlank()) return@TextButton
                    coroutineScope.launch {
                        isSaving = true
                        try {
                            val userId = AuthService.getCurrentUserId() ?: return@launch
                            if (editingEvent != null) {
                                EventService.updateEvent(
                                    editingEvent.copy(
                                        title = title,
                                        note = note.takeIf { it.isNotBlank() },
                                        startTime = startTime,
                                        endTime = endTime,
                                        icon = selectedIcon,
                                        color = selectedColor
                                    )
                                )
                            } else {
                                EventService.createEvent(
                                    userId = userId,
                                    title = title,
                                    note = note.takeIf { it.isNotBlank() },
                                    eventDate = selectedDate,
                                    startTime = startTime,
                                    endTime = endTime,
                                    icon = selectedIcon,
                                    color = selectedColor
                                )
                            }
                            onSaved()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            onError("Etkinlik kaydedilemedi. Lütfen tekrar deneyin.")
                        } finally { isSaving = false }
                    }
                },
                enabled = !isSaving && title.isNotBlank()
            ) {
                if (isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFFFF6047), strokeWidth = 2.dp)
                else Text("Kaydet", color = Color(0xFFFF6047))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}
