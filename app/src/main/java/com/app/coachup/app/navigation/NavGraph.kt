package com.app.coachup.app.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.app.coachup.app.models.LocalCoach
import com.app.coachup.app.models.Training
import com.app.coachup.app.models.TrainingCategory
import com.app.coachup.app.services.CoachService
import com.app.coachup.app.services.TrainingService
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.app.coachup.app.ui.appointments.AppointmentsScreen
import com.app.coachup.app.ui.auth.LoginScreen
import com.app.coachup.app.ui.auth.RegisterScreen
import com.app.coachup.app.ui.calendar.CalendarScreen
import com.app.coachup.app.ui.coaches.CoachChatScreen
import com.app.coachup.app.ui.coaches.CoachDetailScreen
import com.app.coachup.app.ui.coaches.CoachesScreen
import com.app.coachup.app.ui.goals.GoalsScreen
import com.app.coachup.app.ui.groups.GroupClassesScreen
import com.app.coachup.app.ui.home.HomeScreen
import com.app.coachup.app.ui.notifications.NotificationsScreen
import com.app.coachup.app.ui.nutrition.NutritionScreen
import com.app.coachup.app.ui.payments.PaymentsScreen
import com.app.coachup.app.ui.placeholder.PlaceholderScreen
import com.app.coachup.app.ui.profile.ProfileScreen
import com.app.coachup.app.ui.progress.ProgressTrackingScreen
import com.app.coachup.app.ui.qr.AllEntryHistoryScreen
import com.app.coachup.app.ui.qr.QREntryScreen
import com.app.coachup.app.ui.reservations.ReservationsScreen
import com.app.coachup.app.ui.results.PersonalRecordsScreen
import com.app.coachup.app.ui.streak.StreakScreen
import com.app.coachup.app.ui.results.ResultDetailScreen
import com.app.coachup.app.ui.results.ResultsScreen
import com.app.coachup.app.ui.settings.AddressScreen
import com.app.coachup.app.ui.settings.AppearanceSettingsScreen
import com.app.coachup.app.ui.settings.DefaultScreenSettingsScreen
import com.app.coachup.app.ui.settings.LanguageSettingsScreen
import com.app.coachup.app.ui.settings.EmergencyContactScreen
import com.app.coachup.app.ui.settings.MembershipScreen
import com.app.coachup.app.ui.settings.PasswordSettingsScreen
import com.app.coachup.app.ui.settings.RequestMembershipScreen
import com.app.coachup.app.ui.settings.SettingsScreen
import com.app.coachup.app.ui.surveys.SurveysScreen
import com.app.coachup.app.services.AuthService
import com.app.coachup.app.services.UserService
import com.app.coachup.app.ui.training.ActiveWorkoutScreen
import com.app.coachup.app.ui.training.RecordAttemptSessionScreen
import com.app.coachup.app.ui.training.RecordAttemptSetupScreen
import com.app.coachup.app.ui.training.RecordAttemptSummaryScreen
import com.app.coachup.app.ui.training.TrainingScreen
import com.app.coachup.app.ui.admin.AdminDashboardScreen
import com.app.coachup.app.ui.admin.AdminTab

/**
 * Root navigation graph.
 *
 * Every route defined in [Routes] is registered here.
 *
 * Non-serialisable UI objects ([Training], [LocalCoach]) are passed between
 * destinations via [NavigationStateHolder], which acts as a typed in-memory
 * transit store. The caller deposits the object immediately before calling
 * [navController.navigate]; the destination reads and clears it on first
 * composition so stale data is never displayed.
 *
 * [startDestination] is driven by [com.app.coachup.app.MainActivity]:
 *   – unauthenticated → [Routes.LOGIN]
 *   – authenticated   → [Routes.HOME]
 */
@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier
) {
    val fadeTween = tween<Float>(220)
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition    = { fadeIn(animationSpec = fadeTween) },
        exitTransition     = { fadeOut(animationSpec = fadeTween) },
        popEnterTransition = { fadeIn(animationSpec = fadeTween) },
        popExitTransition  = { fadeOut(animationSpec = fadeTween) },
        modifier = modifier
    ) {

        // ─── Auth ─────────────────────────────────────────────────────────────
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(Routes.REGISTER)
                }
            )
        }

        composable(Routes.REGISTER) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // ─── Main / Home ──────────────────────────────────────────────────────
        composable(Routes.HOME) {
            HomeScreen(navController = navController)
        }

        composable(Routes.CALENDAR) {
            CalendarScreen(navController = navController)
        }

        composable(Routes.TRAINING) {
            TrainingScreen(
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
        }

        composable(Routes.QR_ENTRY) {
            QREntryScreen(
                onNavigateToAllHistory = {
                    navController.navigate(Routes.ALL_ENTRY_HISTORY)
                }
            )
        }

        // ─── Profile & Notifications ──────────────────────────────────────────
        composable(Routes.PROFILE) {
            ProfileScreen(
                navController = navController
            )
        }

        composable(Routes.NOTIFICATIONS) {
            NotificationsScreen(
                navController = navController
            )
        }

        // ─── Coaches ──────────────────────────────────────────────────────────
        composable(Routes.COACHES) {
            CoachesScreen(
                onNavigateToDetail = { coach ->
                    NavigationStateHolder.pendingCoach = coach
                    navController.navigate(Routes.coachDetail(coach.id))
                },
                onNavigateToChat = { coach ->
                    NavigationStateHolder.pendingChatCoach = coach
                    navController.navigate(Routes.coachChat(coach.id))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.COACH_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { backStack ->
            val coachId = backStack.arguments?.getString("id") ?: ""
            val deposited = remember {
                val c = NavigationStateHolder.pendingCoach
                NavigationStateHolder.pendingCoach = null
                c
            }
            var coach by remember { mutableStateOf(deposited) }
            var loadError by remember { mutableStateOf(false) }

            if (coach == null && coachId.isNotBlank() && !loadError) {
                LaunchedEffect(coachId) {
                    try {
                        val dbCoach = CoachService.fetchCoachDetail(coachId)
                        if (dbCoach != null) {
                            coach = LocalCoach.from(dbCoach)
                        } else {
                            loadError = true
                        }
                    } catch (_: Exception) {
                        loadError = true
                    }
                }
            }

            when {
                coach != null -> CoachDetailScreen(
                    coach = coach!!,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToChat = { chatCoach ->
                        NavigationStateHolder.pendingChatCoach = chatCoach
                        navController.navigate(Routes.coachChat(chatCoach.id))
                    }
                )
                loadError -> PlaceholderScreen(title = "Koç Detayı", navController = navController)
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        composable(
            route = Routes.COACH_CHAT,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { backStack ->
            val coachId = backStack.arguments?.getString("id") ?: ""
            val deposited = remember {
                val c = NavigationStateHolder.pendingChatCoach
                NavigationStateHolder.pendingChatCoach = null
                c
            }
            var coach by remember { mutableStateOf(deposited) }
            var loadError by remember { mutableStateOf(false) }

            if (coach == null && coachId.isNotBlank() && !loadError) {
                LaunchedEffect(coachId) {
                    try {
                        val dbCoach = CoachService.fetchCoachDetail(coachId)
                        if (dbCoach != null) {
                            coach = LocalCoach.from(dbCoach)
                        } else {
                            loadError = true
                        }
                    } catch (_: Exception) {
                        loadError = true
                    }
                }
            }

            when {
                coach != null -> CoachChatScreen(
                    coach = coach!!,
                    onNavigateBack = { navController.popBackStack() }
                )
                loadError -> PlaceholderScreen(title = "Koç Sohbeti", navController = navController)
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        // ─── Results & Personal Records ───────────────────────────────────────
        composable(Routes.RESULTS) {
            ResultsScreen(
                navController = navController
            )
        }

        composable(
            route = Routes.RESULT_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { backStack ->
            val id = backStack.arguments?.getString("id") ?: ""
            ResultDetailScreen(
                resultId = id,
                navController = navController
            )
        }

        composable(Routes.PERSONAL_RECORDS) {
            PersonalRecordsScreen(
                navController = navController
            )
        }

        composable(Routes.STREAK) {
            StreakScreen(navController = navController)
        }

        // ─── Feature screens (single onNavigateBack callback) ─────────────────
        composable(Routes.APPOINTMENTS) {
            AppointmentsScreen(navController = navController)
        }

        composable(Routes.GROUP_CLASSES) {
            val currentProfile by UserService.currentProfile.collectAsState()
            val authUser by AuthService.currentUser.collectAsState(initial = null)
            val isIndividual = remember(currentProfile, authUser) {
                when {
                    currentProfile != null -> UserService.isIndividualUser(currentProfile)
                    AuthService.isIndividualAuthUser(authUser) -> true
                    else -> false
                }
            }
            if (isIndividual) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                GroupClassesScreen(navController = navController)
            }
        }

        composable(Routes.NUTRITION) {
            NutritionScreen(navController = navController)
        }

        composable(Routes.PROGRESS) {
            ProgressTrackingScreen(navController = navController)
        }

        composable(Routes.GOALS) {
            GoalsScreen(navController = navController)
        }

        composable(Routes.MEMBERSHIP) {
            MembershipScreen(navController = navController)
        }

        composable(Routes.PAYMENTS) {
            PaymentsScreen(navController = navController)
        }

        composable(Routes.SURVEYS) {
            SurveysScreen(navController = navController)
        }

        composable(Routes.RESERVATIONS) {
            ReservationsScreen(navController = navController)
        }

        composable(Routes.ALL_ENTRY_HISTORY) {
            AllEntryHistoryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ─── Active workout ───────────────────────────────────────────────────
        composable(
            route = Routes.ACTIVE_WORKOUT,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("programId") { type = NavType.StringType }
            )
        ) { backStack ->
            val sessionId = backStack.arguments?.getString("sessionId") ?: ""
            val programId = backStack.arguments?.getString("programId") ?: ""

            // Try the in-memory deposit first; fall back to fetching from DB.
            val deposited = remember {
                val t = NavigationStateHolder.pendingTraining
                NavigationStateHolder.pendingTraining = null
                t
            }

            var training by remember { mutableStateOf(deposited) }
            var loadError by remember { mutableStateOf(false) }

            if (training == null && !loadError) {
                LaunchedEffect(programId) {
                    try {
                        training = when {
                            programId.startsWith("builtin_") -> {
                                val categoryValue = programId.removePrefix("builtin_")
                                Training.builtin(TrainingCategory.fromDbValue(categoryValue))
                            }
                            else -> {
                                val program = TrainingService.fetchProgram(programId)
                                if (program != null) {
                                    TrainingService.loadProgramTraining(
                                        program,
                                        com.app.coachup.app.models.TrainingSource.GYM
                                    )
                                } else null
                            }
                        }
                        if (training == null) loadError = true
                    } catch (_: Exception) {
                        loadError = true
                    }
                }
            }

            when {
                training != null -> ActiveWorkoutScreen(
                    training = training!!,
                    sessionId = sessionId,
                    onNavigateBack = { navController.popBackStack() }
                )
                loadError -> PlaceholderScreen(title = "Aktif Antrenman", navController = navController)
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        // ─── Record Attempt ───────────────────────────────────────────────────
        composable(Routes.RECORD_ATTEMPT_SETUP) {
            var userId by remember { mutableStateOf("") }
            LaunchedEffect(Unit) {
                userId = AuthService.getCurrentUserId() ?: ""
            }
            RecordAttemptSetupScreen(
                userId = userId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSession = { attempt, sets, exercise, measureType, catalogId, categoryId ->
                    NavigationStateHolder.pendingRecordAttempt = attempt
                    NavigationStateHolder.pendingRecordAttemptSets = sets
                    NavigationStateHolder.pendingRecordAttemptExercise = exercise
                    NavigationStateHolder.pendingRecordMeasureType = measureType
                    NavigationStateHolder.pendingRecordCatalogId = catalogId
                    NavigationStateHolder.pendingRecordCategoryId = categoryId
                    navController.navigate(Routes.RECORD_ATTEMPT_SESSION)
                }
            )
        }

        composable(Routes.RECORD_ATTEMPT_SESSION) {
            val attempt = remember {
                val a = NavigationStateHolder.pendingRecordAttempt
                NavigationStateHolder.pendingRecordAttempt = null
                a
            }
            val sets = remember {
                val s = NavigationStateHolder.pendingRecordAttemptSets
                NavigationStateHolder.pendingRecordAttemptSets = emptyList()
                s
            }
            val exercise = remember {
                val e = NavigationStateHolder.pendingRecordAttemptExercise
                NavigationStateHolder.pendingRecordAttemptExercise = null
                e
            }
            val measureType = remember {
                val m = NavigationStateHolder.pendingRecordMeasureType
                NavigationStateHolder.pendingRecordMeasureType =
                    com.app.coachup.app.models.RecordMeasureType.WEIGHT
                m
            }
            val catalogId = remember {
                val id = NavigationStateHolder.pendingRecordCatalogId
                NavigationStateHolder.pendingRecordCatalogId = null
                id
            }
            val categoryId = remember {
                val id = NavigationStateHolder.pendingRecordCategoryId
                NavigationStateHolder.pendingRecordCategoryId = null
                id
            }
            if (attempt != null && exercise != null) {
                RecordAttemptSessionScreen(
                    attempt = attempt,
                    initialSets = sets,
                    exercise = exercise,
                    measureType = measureType,
                    catalogId = catalogId,
                    categoryId = categoryId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToSummary = { summaryResult, completedSets ->
                        NavigationStateHolder.pendingRecordAttempt = attempt
                        NavigationStateHolder.pendingRecordAttemptSets = completedSets
                        NavigationStateHolder.pendingRecordAttemptExercise = exercise
                        NavigationStateHolder.pendingSummaryResult = summaryResult
                        navController.navigate(Routes.RECORD_ATTEMPT_SUMMARY)
                    }
                )
            } else {
                PlaceholderScreen(title = "Rekor Denemesi", navController = navController)
            }
        }

        composable(Routes.RECORD_ATTEMPT_SUMMARY) {
            val attempt = remember {
                val a = NavigationStateHolder.pendingRecordAttempt
                NavigationStateHolder.pendingRecordAttempt = null
                a
            }
            val sets = remember {
                val s = NavigationStateHolder.pendingRecordAttemptSets
                NavigationStateHolder.pendingRecordAttemptSets = emptyList()
                s
            }
            val exercise = remember {
                val e = NavigationStateHolder.pendingRecordAttemptExercise
                NavigationStateHolder.pendingRecordAttemptExercise = null
                e
            }
            val summaryResult = remember {
                val r = NavigationStateHolder.pendingSummaryResult
                NavigationStateHolder.pendingSummaryResult = null
                r
            }
            if (attempt != null && exercise != null && summaryResult != null) {
                RecordAttemptSummaryScreen(
                    attempt = attempt,
                    exercise = exercise,
                    sets = sets,
                    result = summaryResult,
                    onDismiss = {
                        navController.popBackStack(Routes.HOME, inclusive = false)
                    }
                )
            } else {
                PlaceholderScreen(title = "Sonuç", navController = navController)
            }
        }

        // ─── Settings ─────────────────────────────────────────────────────────
        composable(Routes.SETTINGS) {
            SettingsScreen(
                navController = navController
            )
        }

        composable(Routes.SETTINGS_DEFAULT_SCREEN) {
            DefaultScreenSettingsScreen(
                navController = navController
            )
        }

        composable(Routes.SETTINGS_APPEARANCE) {
            AppearanceSettingsScreen(
                navController = navController
            )
        }

        composable(Routes.SETTINGS_LANGUAGE) {
            LanguageSettingsScreen(
                navController = navController
            )
        }

        composable(Routes.SETTINGS_PASSWORD) {
            PasswordSettingsScreen(
                navController = navController
            )
        }

        composable(Routes.SETTINGS_ADDRESS) {
            AddressScreen(
                navController = navController
            )
        }

        composable(Routes.SETTINGS_EMERGENCY_CONTACT) {
            EmergencyContactScreen(
                navController = navController
            )
        }

        composable(Routes.SETTINGS_MEMBERSHIP) {
            MembershipScreen(
                navController = navController
            )
        }

        composable(Routes.SETTINGS_REQUEST_MEMBERSHIP) {
            RequestMembershipScreen(
                navController = navController
            )
        }

        // ─── Admin Panel ───
        composable(Routes.ADMIN_DASHBOARD) {
            AdminDashboardScreen(navController = navController, initialTab = AdminTab.DASHBOARD)
        }
        composable(Routes.ADMIN_USERS) {
            AdminDashboardScreen(navController = navController, initialTab = AdminTab.USERS)
        }
        composable(Routes.ADMIN_COACHES) {
            AdminDashboardScreen(navController = navController, initialTab = AdminTab.COACHES)
        }
        composable(Routes.ADMIN_GYMS) {
            AdminDashboardScreen(navController = navController, initialTab = AdminTab.GYMS)
        }
        composable(Routes.ADMIN_MEMBERSHIP_PLANS) {
            AdminDashboardScreen(navController = navController, initialTab = AdminTab.MEMBERSHIPS)
        }
        composable(Routes.ADMIN_PROGRAMS) {
            AdminDashboardScreen(navController = navController, initialTab = AdminTab.PROGRAMS)
        }
        composable(Routes.ADMIN_AUDIT_LOGS) {
            AdminDashboardScreen(navController = navController, initialTab = AdminTab.AUDIT_LOGS)
        }
        composable(Routes.ADMIN_QR_ENTRIES) {
            AdminDashboardScreen(navController = navController, initialTab = AdminTab.QR_ENTRIES)
        }
        composable(Routes.ADMIN_REPORTS) {
            AdminDashboardScreen(navController = navController, initialTab = AdminTab.REPORTS)
        }
    }
}
