package com.app.coachup.app.ui.training

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.coachup.app.models.*
import com.app.coachup.app.services.AuthService
import com.app.coachup.app.services.TrainingService
import com.app.coachup.app.services.UserService
import com.app.coachup.app.services.ActiveWorkoutManager
import com.app.coachup.app.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class TrainingViewModel : ViewModel() {

    private val _builtinActivities = MutableStateFlow(BuiltInActivities.all())
    val builtinActivities: StateFlow<List<Training>> = _builtinActivities.asStateFlow()

    private val _gymPrograms = MutableStateFlow<List<Training>>(emptyList())
    val gymPrograms: StateFlow<List<Training>> = _gymPrograms.asStateFlow()

    private val _aiPrograms = MutableStateFlow<List<Training>>(emptyList())
    val aiPrograms: StateFlow<List<Training>> = _aiPrograms.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _startingId = MutableStateFlow<String?>(null)
    val startingId: StateFlow<String?> = _startingId.asStateFlow()

    private val _startedSession = MutableStateFlow<Pair<Training, String>?>(null)
    val startedSession: StateFlow<Pair<Training, String>?> = _startedSession.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadAll(gymId: String?, userId: String?, searchText: String = "") {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _builtinActivities.value = BuiltInActivities.all()
                coroutineScope {
                    val gymDeferred = async {
                        TrainingService.fetchGymPrograms(gymId, searchText).map { program ->
                            TrainingService.loadProgramTraining(program, TrainingSource.GYM)
                        }
                    }
                    val aiDeferred = async {
                        TrainingService.fetchAiPrograms(searchText).map { program ->
                            TrainingService.loadProgramTraining(program, TrainingSource.AI)
                        }
                    }
                    _gymPrograms.value = gymDeferred.await()
                    _aiPrograms.value = aiDeferred.await()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("TrainingScreen", "loadAll FAILED: ${e.message}", e)
                _error.value = "Programlar yüklenemedi"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun startTraining(training: Training, userId: String) {
        viewModelScope.launch {
            _startingId.value = training.id
            try {
                val session = TrainingService.startActivity(userId = userId, training = training)
                _startedSession.value = Pair(training, session.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("TrainingViewModel", "startTraining failed", e)
                _error.value = when {
                    e.message?.contains("policy", ignoreCase = true) == true ->
                        "Antrenman başlatılamadı. Supabase'de üye RLS migration'ını çalıştırın."
                    !e.message.isNullOrBlank() -> "Antrenman başlatılamadı: ${e.message}"
                    else -> "Antrenman başlatılamadı"
                }
            } finally {
                _startingId.value = null
            }
        }
    }

    fun clearStartedSession() {
        _startedSession.value = null
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@Composable
fun TrainingScreen(
    onNavigateToActiveWorkout: (training: Training, sessionId: String) -> Unit,
    onNavigateToPersonalRecords: () -> Unit = {},
    onNavigateToRecordAttempt: () -> Unit = {},
    vm: TrainingViewModel = viewModel()
) {
    var searchText by remember { mutableStateOf("") }
    val activeSessionState by ActiveWorkoutManager.activeSession.collectAsState()
    var showActiveWorkoutWarning by remember { mutableStateOf(false) }
    var warningActiveSession by remember { mutableStateOf<ActiveWorkoutManager.ActiveSession?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentProfile by UserService.currentProfile.collectAsState(initial = null)
    val user by AuthService.currentUser.collectAsState(initial = null)

    val builtinActivities by vm.builtinActivities.collectAsState()
    val gymPrograms by vm.gymPrograms.collectAsState()
    val aiPrograms by vm.aiPrograms.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val startingId by vm.startingId.collectAsState()
    val startedSession by vm.startedSession.collectAsState()
    val error by vm.error.collectAsState()

    val userId = user?.id ?: currentProfile?.id

    LaunchedEffect(currentProfile?.gymId, searchText, userId) {
        vm.loadAll(currentProfile?.gymId, userId, searchText)
    }

    LaunchedEffect(startedSession) {
        startedSession?.let { (training, sessionId) ->
            vm.clearStartedSession()
            onNavigateToActiveWorkout(training, sessionId)
        }
    }

    val filteredGym = remember(gymPrograms, searchText) {
        if (searchText.isBlank()) gymPrograms
        else gymPrograms.filter { it.title.contains(searchText, ignoreCase = true) }
    }
    val filteredAi = remember(aiPrograms, searchText) {
        if (searchText.isBlank()) aiPrograms
        else aiPrograms.filter { it.title.contains(searchText, ignoreCase = true) }
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    val activityRows = remember(builtinActivities) { builtinActivities.chunked(2) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        item {
            TrainingHeader(onStatsClick = onNavigateToPersonalRecords)
        }

        item {
            TrainingSearchBar(
                text = searchText,
                onTextChange = { searchText = it },
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(16.dp))
        }

        item {
            RecordAttemptButton(onClick = onNavigateToRecordAttempt)
            Spacer(Modifier.height(20.dp))
        }

        item {
            SectionTitle(
                title = "Aktiviteler",
                subtitle = "Saatinden veya manuel olarak kaydet"
            )
            Spacer(Modifier.height(12.dp))
        }

        items(
            activityRows,
            key = { row -> row.joinToString { it.id } }
        ) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { training ->
                    ActivityCard(
                        training = training,
                        isStarting = startingId == training.id,
                        onClick = {
                            if (activeSessionState != null) {
                                warningActiveSession = activeSessionState
                                showActiveWorkoutWarning = true
                            } else {
                                coroutineScope.launch {
                                    val uid = AuthService.getCurrentUserId()
                                        ?: user?.id
                                        ?: currentProfile?.id
                                    if (uid == null) {
                                        snackbarHostState.showSnackbar("Oturum bulunamadı. Lütfen tekrar giriş yapın.")
                                        return@launch
                                    }
                                    vm.startTraining(training, uid)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        item { Spacer(Modifier.height(12.dp)) }

        if (isLoading && filteredGym.isEmpty() && filteredAi.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Primary, strokeWidth = 2.dp)
                }
            }
        }

        if (filteredGym.isNotEmpty()) {
            item {
                SectionTitle(
                    title = "Salon Programları",
                    subtitle = "Salonunuzun tanımladığı antrenmanlar",
                    accentColor = Primary
                )
                Spacer(Modifier.height(12.dp))
            }
            items(filteredGym.size, key = { filteredGym[it].id }) { index ->
                ProgramTrainingCard(
                    training = filteredGym[index],
                    isStarting = startingId == filteredGym[index].id,
                    onStartClick = {
                        if (activeSessionState != null) {
                            warningActiveSession = activeSessionState
                            showActiveWorkoutWarning = true
                        } else {
                            coroutineScope.launch {
                                val uid = AuthService.getCurrentUserId()
                                    ?: user?.id
                                    ?: currentProfile?.id
                                if (uid == null) {
                                    snackbarHostState.showSnackbar("Oturum bulunamadı. Lütfen tekrar giriş yapın.")
                                    return@launch
                                }
                                vm.startTraining(filteredGym[index], uid)
                            }
                        }
                    }
                )
                Spacer(Modifier.height(14.dp))
            }
            item { Spacer(Modifier.height(10.dp)) }
        }

        if (filteredAi.isNotEmpty()) {
            item {
                SectionTitle(
                    title = "AI Programları",
                    subtitle = "Kişisel yapay zeka programların",
                    accentColor = Color(0xFF7B1FA2)
                )
                Spacer(Modifier.height(12.dp))
            }
            items(filteredAi.size, key = { filteredAi[it].id }) { index ->
                ProgramTrainingCard(
                    training = filteredAi[index],
                    isStarting = startingId == filteredAi[index].id,
                    onStartClick = {
                        if (activeSessionState != null) {
                            warningActiveSession = activeSessionState
                            showActiveWorkoutWarning = true
                        } else {
                            coroutineScope.launch {
                                val uid = AuthService.getCurrentUserId()
                                    ?: user?.id
                                    ?: currentProfile?.id
                                if (uid == null) {
                                    snackbarHostState.showSnackbar("Oturum bulunamadı. Lütfen tekrar giriş yapın.")
                                    return@launch
                                }
                                vm.startTraining(filteredAi[index], uid)
                            }
                        }
                    }
                )
                Spacer(Modifier.height(14.dp))
            }
        }

        error?.let { msg ->
            item {
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
        }
    }

    if (showActiveWorkoutWarning && warningActiveSession != null) {
        val session = warningActiveSession!!
        AlertDialog(
            onDismissRequest = { showActiveWorkoutWarning = false },
            title = { Text("Aktif Antrenman Mevcut", fontWeight = FontWeight.Bold) },
            text = { Text("Zaten devam eden bir ${session.training.title} antrenmanınız var. Yeni bir antrenmana başlamak için lütfen mevcut antrenmanı bitirin veya iptal edin.") },
            confirmButton = {
                Button(
                    onClick = {
                        showActiveWorkoutWarning = false
                        onNavigateToActiveWorkout(session.training, session.sessionId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text("Antrenmana Dön")
                }
            },
            dismissButton = {
                TextButton(onClick = { showActiveWorkoutWarning = false }) {
                    Text("Vazgeç")
                }
            }
        )
    }
    }
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

@Composable
private fun TrainingHeader(onStatsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "Antrenman",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Aktivite kaydet veya programını başlat",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(shape = CircleShape, shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
            IconButton(onClick = onStatsClick) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = "Aktivite Geçmişi",
                    tint = Primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String,
    accentColor: Color = Primary
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
        Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------------------
// Rekor Denemesi
// ---------------------------------------------------------------------------

@Composable
private fun RecordAttemptButton(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .shadow(1.dp, RoundedCornerShape(14.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.LocalFireDepartment, null, tint = Primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "Rekor Denemesi",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun TrainingSearchBar(text: String, onTextChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (text.isEmpty()) {
                    Text("Program ara...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                inner()
            }
        )
        if (text.isNotEmpty()) {
            Icon(
                Icons.Default.Cancel,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp).clickable { onTextChange("") }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Activity card — varsayılan modüller
// ---------------------------------------------------------------------------

@Composable
private fun ActivityCard(
    training: Training,
    isStarting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hint = activityHint(training.category)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp))
            .clickable(enabled = !isStarting) { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(training.category.emoji, fontSize = 26.sp)
                if (isStarting) {
                    CircularProgressIndicator(color = Primary, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }
            Text(
                text = training.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = hint,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun activityHint(category: TrainingCategory): String = when (category) {
    TrainingCategory.RUNNING, TrainingCategory.WALKING, TrainingCategory.HYROX ->
        "Süre · Mesafe · Tempo"
    TrainingCategory.CYCLING -> "Süre · Mesafe · Hız"
    TrainingCategory.SWIMMING -> "Süre · Mesafe"
    TrainingCategory.YOGA, TrainingCategory.PILATES -> "Süre · Nabız"
    TrainingCategory.CUSTOM -> "Serbest kayıt"
    else -> "Süre · Nabız"
}

// ---------------------------------------------------------------------------
// Program card — salon / AI
// ---------------------------------------------------------------------------

@Composable
private fun ProgramTrainingCard(
    training: Training,
    isStarting: Boolean,
    onStartClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val (badgeLabel, badgeColor) = when (training.source) {
        TrainingSource.GYM -> "Salon" to Primary
        TrainingSource.AI -> "AI Program" to Color(0xFF7B1FA2)
        TrainingSource.BUILTIN -> "Aktivite" to Primary
    }

    val difficultyColor = when (training.difficulty) {
        Difficulty.BEGINNER -> Color(0xFF4CAF50)
        Difficulty.INTERMEDIATE -> Color(0xFFFF9800)
        Difficulty.ADVANCED -> Color(0xFFF44336)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .shadow(3.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                    .background(badgeColor)
            )

            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = training.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProgramBadge(label = badgeLabel, color = badgeColor)
                            Text(
                                text = training.difficulty.label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = difficultyColor
                            )
                        }
                    }
                }

                training.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    Text(
                        text = desc,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (training.exerciseNames.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .clickable { expanded = !expanded }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Program içeriği (${training.exerciseNames.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (expanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val visible = training.exerciseNames.take(12)
                            visible.forEachIndexed { index, name ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}.",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeColor
                                    )
                                    Text(
                                        text = name,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            if (training.exerciseNames.size > 12) {
                                Text(
                                    text = "+${training.exerciseNames.size - 12} hareket daha",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = { if (!isStarting) onStartClick() },
                    enabled = !isStarting,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = badgeColor,
                        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 11.dp)
                ) {
                    if (isStarting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Başlatılıyor...", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Programı Başlat", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramBadge(label: String, color: Color) {
    Text(
        text = label,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = color
    )
}
