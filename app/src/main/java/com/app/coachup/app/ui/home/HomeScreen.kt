package com.app.coachup.app.ui.home

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.jan.supabase.auth.user.UserInfo
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.app.coachup.app.models.ClassBooking
import com.app.coachup.app.models.GroupClass
import com.app.coachup.app.models.ScheduledProgramDB
import com.app.coachup.app.models.TrainingSession
import com.app.coachup.app.models.UserGoal
import com.app.coachup.app.models.UserMembership
import com.app.coachup.app.models.UserProfile
import com.app.coachup.app.navigation.HomeTabState
import com.app.coachup.app.navigation.Routes
import com.app.coachup.app.services.AuthService
import com.app.coachup.app.services.GoalService
import com.app.coachup.app.services.GroupClassService
import com.app.coachup.app.services.ScheduleService
import com.app.coachup.app.services.TrainingService
import com.app.coachup.app.services.UserService
import com.app.coachup.app.theme.AllWhite
import com.app.coachup.app.theme.Primary
import com.app.coachup.app.theme.coachUpLogoRes
import com.app.coachup.app.theme.Purple100
import com.app.coachup.app.utils.CalendarContentFilter
import com.app.coachup.app.ui.calendar.CalendarScreen
import com.app.coachup.app.ui.qr.QREntryScreen
import com.app.coachup.app.ui.training.TrainingScreen
import com.app.coachup.app.navigation.NavigationStateHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ─────────────────────────────────────────────────────────────────────────────
// Tab model
// ─────────────────────────────────────────────────────────────────────────────

private enum class MainTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME(
        "Anasayfa",
        Icons.Filled.Home,
        Icons.Filled.Home
    ),
    CALENDAR(
        "Takvim",
        Icons.Filled.CalendarMonth,
        Icons.Outlined.CalendarToday
    ),
    TRAINING(
        "Antrenman",
        Icons.Filled.FitnessCenter,
        Icons.Filled.FitnessCenter
    ),
    QR_ENTRY(
        "QR Giriş",
        Icons.Filled.QrCodeScanner,
        Icons.Filled.QrCodeScanner
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Week-day data class
// ─────────────────────────────────────────────────────────────────────────────

private fun defaultScreenToTab(screen: String): MainTab = when (screen) {
    "calendar" -> MainTab.CALENDAR
    "training" -> MainTab.TRAINING
    "qr" -> MainTab.QR_ENTRY
    else -> MainTab.HOME
}

private fun joinedProgramsForDay(programs: List<ScheduledProgramDB>): List<ScheduledProgramDB> =
    CalendarContentFilter.joinedPrograms(programs)

private fun joinedBookingsForDay(bookings: List<ClassBooking>): List<ClassBooking> =
    CalendarContentFilter.joinedBookings(bookings)

private data class WeekDay(
    val name: String,
    val date: LocalDate,
    val isToday: Boolean = date == LocalDate.now()
)

// ─────────────────────────────────────────────────────────────────────────────
// HomeScreen
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Main home screen – Android equivalent of iOS HomeView.
 *
 * Layout:
 *  - [ModalNavigationDrawer] wraps everything; the drawer hosts [SideMenuContent].
 *  - Inside the drawer: top nav bar → scrollable content (welcome, day selector,
 *    daily programs) → bottom tab bar.
 *  - Switching tabs replaces the main content area (no sub-NavHost needed at
 *    this level; full screens are pushed via [navController]).
 */
@Composable
fun HomeScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Ensure the drawer is always closed when this screen becomes visible again
    // (e.g. after returning from a sub-screen where navigation happened mid-animation).
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (drawerState.isOpen) drawerState.close()
        }
    }

    val currentUser: UserInfo? by AuthService.currentUser.collectAsState(initial = null)
    val currentProfile by UserService.currentProfile.collectAsState(initial = null)
    val availableGyms by UserService.availableGyms.collectAsState()
    val availableMemberships by UserService.availableMemberships.collectAsState()
    val isLoadingProfile by UserService.isLoading.collectAsState()

    val isIndividualUser = remember(currentProfile, currentUser, availableMemberships) {
        when {
            currentProfile != null -> UserService.isIndividualUser(currentProfile)
            AuthService.isIndividualAuthUser(currentUser) -> true
            else -> false
        }
    }

    val showMembershipPicker = !isLoadingProfile &&
        !isIndividualUser &&
        UserService.shouldShowMembershipPicker()

    var hasAppliedDefaultTab by rememberSaveable {
        mutableStateOf(HomeTabState.hasAppliedDefaultTab)
    }
    var selectedTabName by rememberSaveable {
        mutableStateOf(HomeTabState.selectedTabName)
    }
    val selectedTab = remember(selectedTabName) {
        MainTab.entries.firstOrNull { it.name == selectedTabName } ?: MainTab.HOME
    }
    var selectedDate by rememberSaveable {
        mutableStateOf(HomeTabState.selectedDate)
    }
    val selectedLocalDate = remember(selectedDate) {
        runCatching { LocalDate.parse(selectedDate) }.getOrDefault(LocalDate.now())
    }
    // Build synchronously so the day strip is visible on the very first frame.
    var weekDays by remember { mutableStateOf(buildWeekDays()) }
    var programs by remember { mutableStateOf<List<ScheduledProgramDB>>(emptyList()) }
    var completedSessions by remember { mutableStateOf<List<TrainingSession>>(emptyList()) }
    var groupClassBookings by remember { mutableStateOf<List<ClassBooking>>(emptyList()) }
    var groupClasses by remember { mutableStateOf<List<GroupClass>>(emptyList()) }
    var goalsForDay by remember { mutableStateOf<List<UserGoal>>(emptyList()) }
    var isLoadingPrograms by remember { mutableStateOf(true) }

    // Fetch profile + sync streak when user is available.
    LaunchedEffect(currentUser?.id) {
        currentUser?.id?.let { userId ->
            try {
                com.app.coachup.app.services.StreakService.syncUserStreak(userId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        if (HomeTabState.hasUserSelectedTab) {
            selectedTabName = HomeTabState.selectedTabName
            selectedDate = HomeTabState.selectedDate
        }
    }

    LaunchedEffect(currentProfile?.defaultScreen) {
        val profile = currentProfile ?: return@LaunchedEffect
        if (!HomeTabState.hasAppliedDefaultTab && !HomeTabState.hasUserSelectedTab && !hasAppliedDefaultTab) {
            val tab = defaultScreenToTab(profile.defaultScreen)
            selectedTabName = tab.name
            HomeTabState.selectedTabName = tab.name
            HomeTabState.hasAppliedDefaultTab = true
            hasAppliedDefaultTab = true
        }
    }

    LaunchedEffect(selectedTabName, selectedDate) {
        HomeTabState.selectedTabName = selectedTabName
        HomeTabState.selectedDate = selectedDate
        HomeTabState.hasAppliedDefaultTab = hasAppliedDefaultTab
    }

    if (showMembershipPicker) {
        AlertDialog(
            onDismissRequest = { /* zorunlu seçim */ },
            title = { Text("Üyelik Seçin") },
            text = {
                Column {
                    availableMemberships.forEach { membership ->
                        val planName = membership.plan?.name ?: "Üyelik"
                        val gymId = membership.plan?.gymId
                        val gym = availableGyms.firstOrNull { it.id == gymId }
                        val subtitle = gym?.name ?: ""
                        TextButton(
                            onClick = {
                                if (gym != null) {
                                    val userId = currentUser?.id ?: return@TextButton
                                    scope.launch {
                                        UserService.selectMembership(userId, membership, gym)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                                Text(planName, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                                if (subtitle.isNotBlank()) {
                                    Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Reload home-tab data only while HOME tab is visible.
    LaunchedEffect(selectedTab, selectedLocalDate, currentUser?.id, isIndividualUser) {
        if (selectedTab != MainTab.HOME) return@LaunchedEffect
        val userId = currentUser?.id ?: return@LaunchedEffect
        isLoadingPrograms = true
        try {
            programs = ScheduleService.fetchScheduledPrograms(userId, selectedLocalDate)
                .let { joinedProgramsForDay(it) }
            completedSessions = TrainingService.fetchCompletedSessionsForDate(userId, selectedLocalDate)
            if (!isIndividualUser) {
                val bookings = GroupClassService.fetchBookingsForDate(userId, selectedLocalDate)
                    .let { joinedBookingsForDay(it) }
                groupClassBookings = bookings
                groupClasses = if (bookings.isNotEmpty()) {
                    GroupClassService.fetchClassesByIds(bookings.map { it.classId }.distinct())
                } else {
                    emptyList()
                }
            } else {
                groupClassBookings = emptyList()
                groupClasses = emptyList()
            }
            goalsForDay = GoalService.fetchGoalsForDate(userId, selectedLocalDate)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            programs = emptyList()
            completedSessions = emptyList()
            groupClassBookings = emptyList()
            groupClasses = emptyList()
            goalsForDay = emptyList()
        } finally {
            isLoadingPrograms = false
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SideMenuContent(
                currentProfile = currentProfile,
                onClose = { scope.launch { drawerState.close() } },
                onNavigate = { route ->
                    scope.launch {
                        drawerState.close()
                        navController.navigate(route)
                    }
                },
                onLogout = {
                    scope.launch {
                        drawerState.close()
                        try {
                            AuthService.signOut()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {}
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    }
                }
            )
        },
        gesturesEnabled = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
        ) {
            // Shared header across all tabs
            val headerBg = if (selectedTab == MainTab.HOME) Color.Transparent else MaterialTheme.colorScheme.surface
            TopNavigationBar(
                onMenuClicked = { scope.launch { drawerState.open() } },
                onNotificationsClicked = { navController.navigate(Routes.NOTIFICATIONS) },
                modifier = Modifier
                    .background(headerBg)
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            )

            // Main content (tab-driven)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (selectedTab) {
                    MainTab.HOME -> HomeTabContent(
                        currentProfile = currentProfile,
                        weekDays = weekDays,
                        selectedDate = selectedLocalDate,
                        onDateSelected = { date ->
                            selectedDate = date.toString()
                            HomeTabState.selectedDate = date.toString()
                        },
                        programs = programs,
                        completedSessions = completedSessions,
                        groupClassBookings = groupClassBookings,
                        groupClasses = groupClasses,
                        goalsForDay = goalsForDay,
                        isLoading = isLoadingPrograms,
                        showGroupClasses = !isIndividualUser,
                        onStreakClicked = { navController.navigate(Routes.STREAK) }
                    )

                    MainTab.CALENDAR -> CalendarScreen()

                    MainTab.TRAINING -> TrainingScreen(
                        onNavigateToActiveWorkout = { training, sessionId ->
                            NavigationStateHolder.pendingTraining = training
                            navController.navigate(Routes.activeWorkout(sessionId, training.id))
                        },
                        onNavigateToPersonalRecords = {
                            navController.navigate(Routes.PERSONAL_RECORDS)
                        },
                        onNavigateToRecordAttempt = {
                            navController.navigate(Routes.RECORD_ATTEMPT_SETUP)
                        }
                    )

                    MainTab.QR_ENTRY -> QREntryScreen(
                        onNavigateToAllHistory = {
                            navController.navigate(Routes.ALL_ENTRY_HISTORY)
                        }
                    )
                }
            }

            // Bottom tab bar
            BottomTabBar(
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    selectedTabName = tab.name
                    HomeTabState.selectedTabName = tab.name
                    HomeTabState.hasUserSelectedTab = true
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Home content (the "Ana Sayfa" tab body)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HomeTabContent(
    currentProfile: UserProfile?,
    weekDays: List<WeekDay>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    programs: List<ScheduledProgramDB>,
    completedSessions: List<TrainingSession>,
    groupClassBookings: List<ClassBooking>,
    groupClasses: List<GroupClass>,
    goalsForDay: List<UserGoal>,
    isLoading: Boolean,
    showGroupClasses: Boolean = true,
    onStreakClicked: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                WelcomeSection(
                    profileName = currentProfile?.name,
                    streak = currentProfile?.currentStreak ?: 0,
                    onStreakClicked = onStreakClicked,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            item {
                // DaySelector tam genişlikte, kendi contentPadding'i var
                DaySelector(
                    weekDays = weekDays,
                    selectedDate = selectedDate,
                    onDateSelected = onDateSelected
                )
            }

            item {
                DailyProgramSection(
                    programs = programs,
                    completedSessions = completedSessions,
                    groupClassBookings = groupClassBookings,
                    groupClasses = groupClasses,
                    goalsForDay = goalsForDay,
                    isLoading = isLoading,
                    showGroupClasses = showGroupClasses,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            item {
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top navigation bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TopNavigationBar(
    onMenuClicked: () -> Unit,
    onNotificationsClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Hamburger menu button
        IconButton(
            onClick = onMenuClicked,
            modifier = Modifier
                .size(44.dp)
                .shadow(elevation = 2.dp, shape = CircleShape)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Outlined.Menu,
                contentDescription = "Menü",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // App logo
        Image(
            painter = painterResource(id = coachUpLogoRes()),
            contentDescription = "CoachUp",
            modifier = Modifier.height(28.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.weight(1f))

        // Notification bell
        IconButton(
            onClick = onNotificationsClicked,
            modifier = Modifier
                .size(44.dp)
                .shadow(elevation = 2.dp, shape = CircleShape)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = "Bildirimler",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Welcome section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WelcomeSection(
    profileName: String?,
    streak: Int,
    onStreakClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (!profileName.isNullOrBlank()) {
                Text(
                    text = "Merhaba, $profileName!",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Hadi Başlayalım!",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Hadi",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Başlayalım!",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Streak counter
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onStreakClicked
                )
                .padding(start = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.LocalFireDepartment,
                contentDescription = "Streak",
                tint = com.app.coachup.app.ui.streak.streakFireColor(streak),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$streak",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "›",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = Primary
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Day selector strip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DaySelector(
    weekDays: List<WeekDay>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    val todayIndex = weekDays.indexOfFirst { it.isToday }.coerceAtLeast(0)
    val listState = rememberLazyListState()

    // Bugüne scroll et (ilk render'da)
    LaunchedEffect(Unit) {
        val scrollIndex = (todayIndex - 2).coerceAtLeast(0)
        listState.scrollToItem(scrollIndex)
    }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 20.dp)
    ) {
        items(weekDays, key = { it.date.toString() }) { day ->
            val isSelected = day.date == selectedDate
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(56.dp)
                    .height(58.dp)
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .then(
                        if (isSelected) Modifier.border(
                            2.dp, Primary, RoundedCornerShape(12.dp)
                        ) else Modifier
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDateSelected(day.date) }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = day.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isSelected) Primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${day.date.dayOfMonth}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) Primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Daily program section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DailyProgramSection(
    programs: List<ScheduledProgramDB>,
    completedSessions: List<TrainingSession>,
    groupClassBookings: List<ClassBooking>,
    groupClasses: List<GroupClass>,
    goalsForDay: List<UserGoal>,
    isLoading: Boolean,
    showGroupClasses: Boolean = true,
    modifier: Modifier = Modifier
) {
    val hasContent = programs.isNotEmpty() ||
        completedSessions.isNotEmpty() ||
        (showGroupClasses && groupClassBookings.isNotEmpty()) ||
        goalsForDay.isNotEmpty()

    Column(modifier = modifier.fillMaxWidth()) {
        // Section header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.FitnessCenter,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Günün Programı",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        when {
            isLoading -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                ) {
                    CircularProgressIndicator(color = Primary, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                }
            }

            !hasContent -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                ) {
                    Icon(imageVector = Icons.Outlined.EventBusy, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "Bu gün için program bulunmuyor", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    goalsForDay.forEach { goal ->
                        GoalProgramCard(goal = goal)
                    }
                    programs.forEach { program ->
                        ProgramCard(program = program)
                    }
                    if (showGroupClasses) {
                        groupClassBookings.forEach { booking ->
                            val groupClass = groupClasses.firstOrNull { it.id == booking.classId }
                            if (groupClass != null) {
                                GroupClassProgramCard(groupClass = groupClass)
                            }
                        }
                    }
                    completedSessions.forEach { session ->
                        CompletedSessionCard(session = session)
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalProgramCard(goal: UserGoal) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Color(0xFFFF9800).copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Flag,
                contentDescription = null,
                tint = Color(0xFFFF9800),
                modifier = Modifier.size(22.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = goal.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Hedef · ${goal.progressPercentage}% tamamlandı",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Program card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProgramCard(program: ScheduledProgramDB) {
    val coachFullName = listOfNotNull(
        program.coach?.name,
        program.coach?.surname
    ).joinToString(" ").ifBlank { "Koç" }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon box on the left
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector = Icons.Filled.FitnessCenter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Content pill (dark purple background – mirrors iOS AppColors.purple100)
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Purple100)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = program.program?.name ?: "Antrenman",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = coachFullName,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }

            // Time
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.CalendarToday,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${program.startTime} / ${program.endTime}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
private fun GroupClassProgramCard(groupClass: GroupClass) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector = Icons.Filled.FitnessCenter,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Purple100)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = groupClass.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                groupClass.instructorName?.let { instructor ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = instructor,
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
            Text(
                text = "${groupClass.startTime.take(5)} - ${groupClass.endTime.take(5)}",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Completed session card — mirrors iOS CompletedSessionCard exactly
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CompletedSessionCard(session: TrainingSession) {
    val programName = session.activityDisplayName
    val activityCategory = session.resolvedActivityCategory
    val successGreen = Color(0xFF4CAF50)

    // Parse completion time → "HH:mm"
    val timeText = session.completedAt?.let {
        try {
            val instant = java.time.Instant.parse(it)
            val localTime = instant.atZone(java.time.ZoneId.systemDefault()).toLocalTime()
            String.format("%02d:%02d", localTime.hour, localTime.minute)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { null }
    }

    // Duration in minutes (completedAt − startedAt)
    val durationText = if (session.completedAt != null && session.startedAt != null) {
        try {
            val completed = java.time.Instant.parse(session.completedAt)
            val started = java.time.Instant.parse(session.startedAt)
            val mins = java.time.Duration.between(started, completed).toMinutes()
            if (mins > 0) "$mins dk" else null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { null }
    } else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Icon box — Primary.opacity(0.1) background, Primary tint (mirrors iOS)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Primary.copy(alpha = 0.10f))
        ) {
            if (activityCategory != null) {
                Text(
                    text = activityCategory.emoji,
                    fontSize = 24.sp
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.FitnessCenter,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Left: program name + "Tamamlandı" row
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = programName,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = successGreen,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "Tamamlandı",
                    fontSize = 12.sp,
                    color = successGreen
                )
            }
        }

        // Right: time + duration (trailing, mirrors iOS VStack alignment .trailing)
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (timeText != null) {
                Text(
                    text = timeText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (durationText != null) {
                Text(
                    text = durationText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom tab bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BottomTabBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
        MainTab.values().forEach { tab ->
            val isSelected = tab == selectedTab
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onTabSelected(tab) }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(50.dp)
                        .then(
                            if (isSelected)
                                Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Primary)
                            else Modifier
                        )
                ) {
                    Icon(
                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.label,
                        tint = if (isSelected) AllWhite else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = tab.label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) Primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helper – build a 60-day strip (30 past + today + 29 future)
// ─────────────────────────────────────────────────────────────────────────────

private fun buildWeekDays(): List<WeekDay> {
    val today = LocalDate.now()
    val dayNames = listOf("Pzt", "Sal", "Çar", "Per", "Cm", "Cts", "Paz")
    val start = today.minusDays(30)
    return (0 until 60).map { i ->
        val date = start.plusDays(i.toLong())
        // dayOfWeek value: 1=Monday … 7=Sunday
        val nameIndex = date.dayOfWeek.value - 1
        WeekDay(name = dayNames[nameIndex], date = date)
    }
}
