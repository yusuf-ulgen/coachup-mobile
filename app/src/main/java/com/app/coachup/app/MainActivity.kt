package com.app.coachup.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.app.coachup.app.navigation.HomeTabState
import com.app.coachup.app.navigation.NavGraph
import com.app.coachup.app.navigation.Routes
import com.app.coachup.app.services.AuthService
import com.app.coachup.app.services.CoachUpNotificationManager
import com.app.coachup.app.services.GuardianService
import com.app.coachup.app.services.HealthConnectService
import com.app.coachup.app.services.PusherService
import com.app.coachup.app.services.StreakService
import com.app.coachup.app.services.UserService
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.app.coachup.app.theme.CoachUpTheme
import com.app.coachup.app.theme.ThemeManager
import com.app.coachup.app.utils.AppLocaleManager
import com.app.coachup.app.ui.components.SplashView
import com.app.coachup.app.ui.guardian.GuardianScreen
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.roundToInt
import com.app.coachup.app.services.ActiveWorkoutManager

/**
 * Single Activity that hosts the entire Compose UI tree.
 *
 * Mirrors the iOS CoachUpApp entry-point logic:
 *   1. While [AuthService.isLoading] is true  → show [SplashView].
 *   2. When authenticated                      → start at [Routes.HOME].
 *   3. When unauthenticated                    → start at [Routes.LOGIN].
 *
 * [CoachUpTheme] wraps the entire tree so every screen inherits the brand
 * colour scheme and typography automatically.
 */
class MainActivity : ComponentActivity() {

    private var healthPermissionRationaleIntent: Boolean = false
    /** Sistem splash turuncu ekranda kalır; Compose SplashView logo gösterene kadar. */
    private var keepSystemSplash = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied — local notifications still work when granted */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        healthPermissionRationaleIntent = isHealthPermissionUsageIntent(intent)
        AppLocaleManager.applyStored(this)
        requestNotificationPermissionIfNeeded()
        enableEdgeToEdge()
        runCatching { com.app.coachup.app.services.LocationTrackingService.init(applicationContext) }
        runCatching { com.app.coachup.app.services.WorkoutAudioCoach.init(applicationContext) }

        setContent {
            val themeManager = remember { ThemeManager.getInstance(applicationContext) }

            CoachUpTheme(themeManager = themeManager) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val context = LocalContext.current

                    // ── Auth state — single source of truth ──────────────────
                    // Both isLoading and isAuthenticated are derived from the SAME
                    // sessionStatus StateFlow so they are always consistent within
                    // a single composition pass → no race-condition white flash.
                    val sessionStatus by AuthService.sessionStatus.collectAsState()
                    val isLoading = sessionStatus is SessionStatus.Initializing

                    // Persist across process death so a background token refresh does not
                    // replay the splash or rebuild NavHost with a new start destination.
                    var hasBooted by rememberSaveable { mutableStateOf(false) }
                    LaunchedEffect(isLoading) {
                        if (!isLoading) {
                            keepSystemSplash = false
                            delay(1800) // Minimum display time of 1.8 seconds for SplashView
                            hasBooted = true
                        }
                    }

                    // Fixed once on first resolved session — never toggled on refresh.
                    var graphStartDestination by rememberSaveable { mutableStateOf<String?>(null) }
                    LaunchedEffect(hasBooted, sessionStatus) {
                        if (!hasBooted || graphStartDestination != null) return@LaunchedEffect
                        graphStartDestination = when (sessionStatus) {
                            is SessionStatus.Authenticated -> Routes.HOME
                            is SessionStatus.NotAuthenticated,
                            is SessionStatus.RefreshFailure -> Routes.LOGIN
                            else -> return@LaunchedEffect
                        }
                    }

                    // Guardian role — checked in the background; never blocks the UI.
                    var isGuardian by rememberSaveable { mutableStateOf(false) }

                    // Restore persisted Supabase session on first composition.
                    LaunchedEffect(Unit) {
                        AuthService.checkSession()
                    }

                    // Subscribe Pusher gym channel once profile loads.
                    val profile by UserService.currentProfile.collectAsState()
                    LaunchedEffect(profile?.gymId) {
                        val gymId = profile?.gymId
                        if (!gymId.isNullOrBlank()) {
                            PusherService.subscribeToGym(gymId)
                        } else {
                            PusherService.unsubscribeFromGym()
                        }
                    }

                    LaunchedEffect(profile?.id) {
                        val userId = profile?.id ?: return@LaunchedEffect
                        try {
                            StreakService.maybeSendEveningReminder(context, userId)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {}
                    }

                    // ── Navigation controller (stable across recompositions) ──
                    val navController = rememberNavController()

                    // React only to definitive auth transitions — ignore Initializing /
                    // RefreshFailure so background resume does not wipe user state or nav.
                    LaunchedEffect(Unit) {
                        snapshotFlow { sessionStatus }
                            .collect { status ->
                                when (status) {
                                    is SessionStatus.Authenticated -> {
                                        try {
                                            val userId = AuthService.getCurrentUserId()
                                            if (userId != null) {
                                                PusherService.subscribeToUser(userId)
                                                CoachUpNotificationManager.initialize(context, userId)
                                                isGuardian = GuardianService.isGuardian(userId)
                                                if (isGuardian) {
                                                    GuardianService.guardian.value?.id?.let { guardianId ->
                                                        GuardianService.fetchChildren(guardianId)
                                                    }
                                                }
                                                if (UserService.currentProfile.value == null) {
                                                    UserService.fetchProfile(userId)
                                                }
                                                AuthService.ensureProfileFromAuthIfMissing()
                                                UserService.currentProfile.value?.gymId?.let { gymId ->
                                                    if (gymId.isNotBlank()) {
                                                        PusherService.subscribeToGym(gymId)
                                                    }
                                                }
                                            }
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            android.util.Log.e("MainActivity", "Role check error: ${e.message}")
                                        }
                                    }
                                    is SessionStatus.NotAuthenticated -> {
                                        if (status.isSignOut) {
                                            isGuardian = false
                                            GuardianService.reset()
                                            HealthConnectService.reset()
                                            PusherService.onLogout()
                                            CoachUpNotificationManager.cleanup()
                                            UserService.reset()
                                            HomeTabState.reset()
                                            navController.navigate(Routes.LOGIN) {
                                                popUpTo(0) { inclusive = true }
                                            }
                                        }
                                    }
                                    else -> Unit
                                }
                            }
                    }

                    val showGuardianUi = isGuardian &&
                        sessionStatus !is SessionStatus.NotAuthenticated

                    var showHealthPermissionRationale by remember {
                        mutableStateOf(healthPermissionRationaleIntent)
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        when {
                            showGuardianUi -> {
                                GuardianScreen(
                                    onLogout = {
                                        navController.navigate(Routes.LOGIN) {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    }
                                )
                            }

                            graphStartDestination != null -> {
                                NavGraph(
                                    navController = navController,
                                    startDestination = graphStartDestination!!
                                )
                            }
                        }

                        // Overlay splash on first boot only — NavGraph stays mounted once ready.
                        if (!hasBooted) {
                            SplashView()
                        }

                        val activeSession by ActiveWorkoutManager.activeSession.collectAsState()
                        val showFloatingOverlay by ActiveWorkoutManager.showFloatingOverlay.collectAsState()

                        if (activeSession != null && showFloatingOverlay) {
                            val session = activeSession!!
                            val elapsedSeconds by session.elapsedSeconds.collectAsState()
                            val isPaused by session.isPaused.collectAsState()

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = 100.dp, end = 20.dp),
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                var offsetX by remember { mutableStateOf(0f) }
                                var offsetY by remember { mutableStateOf(0f) }

                                Surface(
                                    modifier = Modifier
                                        .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                                        .pointerInput(Unit) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                offsetX += dragAmount.x
                                                offsetY += dragAmount.y
                                            }
                                        }
                                        .width(260.dp)
                                        .height(72.dp)
                                        .clickable {
                                            ActiveWorkoutManager.hideOverlay()
                                            navController.navigate(Routes.activeWorkout(session.sessionId, session.programId)) {
                                                launchSingleTop = true
                                            }
                                        },
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    shadowElevation = 8.dp,
                                    border = androidx.compose.foundation.BorderStroke(1.2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = session.training.category.emoji,
                                            fontSize = 24.sp
                                        )

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = formatTime(elapsedSeconds),
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                            Text(
                                                text = session.training.title,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                ActiveWorkoutManager.hideOverlay()
                                                navController.navigate(Routes.activeWorkout(session.sessionId, session.programId)) {
                                                    launchSingleTop = true
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.OpenInFull,
                                                contentDescription = "Büyüt",
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { ActiveWorkoutManager.togglePause() },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                                contentDescription = if (isPaused) "Devam Et" else "Duraklat",
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { ActiveWorkoutManager.stopSession() },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Kapat",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (showHealthPermissionRationale) {
                        AlertDialog(
                            onDismissRequest = { showHealthPermissionRationale = false },
                            title = { Text("Nabız verisi") },
                            text = {
                                Text(
                                    "CoachUP, antrenman sırasında nabzınızı göstermek için Health Connect'ten " +
                                        "yalnızca okuma izni ister. Veriler cihazınızda kalır; üçüncü tarafla paylaşılmaz."
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = { showHealthPermissionRationale = false }) {
                                    Text("Tamam")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isHealthPermissionUsageIntent(intent)) {
            healthPermissionRationaleIntent = true
        }
    }

    private fun isHealthPermissionUsageIntent(intent: android.content.Intent?): Boolean =
        intent?.action == "android.intent.action.VIEW_PERMISSION_USAGE"

    override fun onResume() {
        super.onResume()
        CoachUpNotificationManager.onAppForegroundIfLoggedIn(applicationContext)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun formatTime(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format("%02d:%02d", m, s)
    }
}
