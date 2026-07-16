package com.app.coachup.app.ui.coaches

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.app.coachup.app.models.*
import com.app.coachup.app.services.CoachService
import com.app.coachup.app.services.UserService
import com.app.coachup.app.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class CoachesViewModel : ViewModel() {

    private val _coaches = MutableStateFlow<List<LocalCoach>>(emptyList())
    val coaches: StateFlow<List<LocalCoach>> = _coaches.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadCoaches() {
        if (UserService.currentProfile.value == null) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val dbCoaches: List<Coach> = CoachService.fetchCoaches()
                _coaches.value = dbCoaches.map { LocalCoach.from(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "Koçlar yüklenemedi"
            } finally {
                _isLoading.value = false
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@Composable
fun CoachesScreen(
    onNavigateToDetail: (LocalCoach) -> Unit,
    onNavigateToChat: (LocalCoach) -> Unit,
    onNavigateBack: () -> Unit,
    vm: CoachesViewModel = viewModel()
) {
    var searchText by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(GenderFilter.ALL) }
    var showFilterMenu by remember { mutableStateOf(false) }

    val coaches by vm.coaches.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()
    val currentProfile by UserService.currentProfile.collectAsState()

    LaunchedEffect(currentProfile?.id) {
        if (currentProfile != null) vm.loadCoaches()
    }

    val filtered = remember(coaches, searchText, selectedFilter) {
        coaches.filter { coach ->
            val matchesSearch = searchText.isBlank() ||
                    coach.fullName.contains(searchText, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                GenderFilter.ALL -> true
                GenderFilter.MALE -> coach.gender == CoachGender.MALE
                GenderFilter.FEMALE -> coach.gender == CoachGender.FEMALE
            }
            matchesSearch && matchesFilter
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Header
        CoachesHeader(
            onBack = onNavigateBack,
            modifier = Modifier
                .padding(horizontal = Spacing.xl)
                .padding(top = Spacing.sm, bottom = Spacing.lg)
        )

        // Search + filter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl)
                .padding(bottom = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search field
            Row(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Radius.pill))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.pill))
                    .padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BasicTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    singleLine = true,
                    textStyle = AppNormalStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (searchText.isEmpty()) {
                            Text("Koç ara...", style = AppNormalStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        inner()
                    }
                )
                if (searchText.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Temizle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp).clickable { searchText = "" }
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Filter dropdown
            Box {
                Row(
                    modifier = Modifier
                        .border(
                            1.dp,
                            if (selectedFilter == GenderFilter.ALL) MaterialTheme.colorScheme.outlineVariant else Primary,
                            RoundedCornerShape(Radius.pill)
                        )
                        .clickable { showFilterMenu = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedFilter.label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (selectedFilter == GenderFilter.ALL) MaterialTheme.colorScheme.onSurface else Primary
                    )
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = if (selectedFilter == GenderFilter.ALL) MaterialTheme.colorScheme.onSurface else Primary,
                        modifier = Modifier.size(14.dp)
                    )
                }

                DropdownMenu(
                    expanded = showFilterMenu,
                    onDismissRequest = { showFilterMenu = false }
                ) {
                    GenderFilter.values().forEach { filter ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(filter.label)
                                    if (selectedFilter == filter) {
                                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            onClick = {
                                selectedFilter = filter
                                showFilterMenu = false
                            }
                        )
                    }
                }
            }
        }

        // Error
        if (error != null) {
            Text(
                text = error ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm)
            )
        }

        // Coach list
        when {
            isLoading -> Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = Primary, strokeWidth = 2.dp) }

            filtered.isEmpty() -> Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(imageVector = Icons.Default.PersonOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                Text("Koç bulunamadı", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = 100.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { coach ->
                    CoachCard(
                        coach = coach,
                        onTap = { onNavigateToDetail(coach) },
                        onMessage = { onNavigateToChat(coach) },
                        modifier = Modifier.padding(horizontal = Spacing.xl, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

@Composable
private fun CoachesHeader(onBack: () -> Unit, modifier: Modifier = Modifier) {
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
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Geri",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = "Koçlar",
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// Coach row
// ---------------------------------------------------------------------------

@Composable
private fun CoachCard(
    coach: LocalCoach,
    onTap: () -> Unit,
    onMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        onClick = onTap
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        if (coach.gender == CoachGender.MALE) Purple100 else Primary,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (coach.profileImageUrl != null) {
                    AsyncImage(
                        model = coach.profileImageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Text(
                        text = "${coach.name.firstOrNull() ?: ""}${coach.surname.firstOrNull() ?: ""}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = coach.fullName,
                    style = AppNormalStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!coach.specialty.isNullOrEmpty()) {
                    Text(
                        text = coach.specialty,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(14.dp))
                    Text(String.format("%.1f", coach.rating), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("•", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${coach.experienceYears} yıl", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            IconButton(
                onClick = onMessage,
                modifier = Modifier
                    .size(40.dp)
                    .background(Primary.copy(alpha = 0.1f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Message,
                    contentDescription = "Mesaj",
                    tint = Primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
