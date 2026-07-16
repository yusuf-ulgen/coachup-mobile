package com.app.coachup.app.ui.admin

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.app.coachup.app.models.*
import com.app.coachup.app.services.AdminService
import com.app.coachup.app.services.AuthService
import com.app.coachup.app.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Admin Tab Enum
// ---------------------------------------------------------------------------

enum class AdminTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard),
    USERS("Kullanıcılar", Icons.Default.People),
    GYMS("Salonlar", Icons.Default.Business),
    COACHES("Koçlar", Icons.Default.SportsHandball),
    PROGRAMS("Programlar", Icons.Default.FitnessCenter),
    MEMBERSHIPS("Üyelikler", Icons.Default.CardMembership),
    COMMUNITY("Topluluk", Icons.Default.Groups),
    QR_ENTRIES("QR Girişler", Icons.Default.QrCode),
    AUDIT_LOGS("Loglar", Icons.Default.History),
    REPORTS("Raporlar", Icons.Default.BarChart)
}

// ---------------------------------------------------------------------------
// Admin Dashboard Screen — single-screen admin panel with tab switching
// ---------------------------------------------------------------------------

@Composable
fun AdminDashboardScreen(
    navController: NavController,
    initialTab: AdminTab = AdminTab.DASHBOARD
) {
    var selectedTab by remember { mutableStateOf(initialTab) }
    var hasAccess by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        val userId = AuthService.getCurrentUserId()
        hasAccess = if (userId == null) {
            false
        } else {
            try {
                AdminService.checkAdminAccess(userId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
        }
    }

    when (hasAccess) {
        null -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Primary)
        }
        false -> Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Bu sayfaya erişim yetkiniz yok",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { navController.popBackStack() }) {
                Text("Geri Dön")
            }
        }
        true -> AdminDashboardContent(
            navController = navController,
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it }
        )
    }
}

@Composable
private fun AdminDashboardContent(
    navController: NavController,
    selectedTab: AdminTab,
    onTabSelected: (AdminTab) -> Unit
) {

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Spacing.xl).padding(top = Spacing.sm, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(text = "Admin Panel", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        }

        // Horizontal tab row
        ScrollableTabRow(
            selectedTabIndex = AdminTab.entries.indexOf(selectedTab),
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = Primary,
            edgePadding = Spacing.xl,
            indicator = { tabPositions ->
                val idx = AdminTab.entries.indexOf(selectedTab).coerceIn(tabPositions.indices)
                TabRowDefaults.SecondaryIndicator(color = Primary, modifier = Modifier.tabIndicatorOffset(tabPositions[idx]))
            }
        ) {
            AdminTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { onTabSelected(tab) },
                    text = { Text(tab.label, fontSize = 12.sp, fontWeight = if (selectedTab == tab) FontWeight.SemiBold else FontWeight.Normal) },
                    icon = { Icon(tab.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    selectedContentColor = Primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))

        // Tab content
        when (selectedTab) {
            AdminTab.DASHBOARD -> AdminStatsContent()
            AdminTab.USERS -> AdminUsersContent()
            AdminTab.GYMS -> AdminGymsContent()
            AdminTab.COACHES -> AdminCoachesContent()
            AdminTab.PROGRAMS -> AdminProgramsContent()
            AdminTab.MEMBERSHIPS -> AdminMembershipsContent()
            AdminTab.COMMUNITY -> AdminCommunityContent()
            AdminTab.QR_ENTRIES -> AdminQREntriesContent()
            AdminTab.AUDIT_LOGS -> AdminAuditLogsContent()
            AdminTab.REPORTS -> AdminReportsContent()
        }
    }
}

// ---------------------------------------------------------------------------
// Dashboard Stats
// ---------------------------------------------------------------------------

@Composable
private fun AdminStatsContent() {
    var stats by remember { mutableStateOf<DashboardStats?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try { stats = AdminService.fetchDashboardStats() } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}
        isLoading = false
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary) }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(text = "Genel Bakış", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            }
            item {
                if (stats != null) {
                    val s = stats!!
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("Toplam Kullanıcı", "${s.totalUsers}", Icons.Default.People, Color(0xFF2196F3), modifier = Modifier.weight(1f))
                            StatCard("Aktif Kullanıcı", "${s.activeUsers}", Icons.Default.PersonOutline, Color(0xFF4CAF50), modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("Salonlar", "${s.totalGyms}", Icons.Default.Business, Color(0xFFFF9800), modifier = Modifier.weight(1f))
                            StatCard("Koçlar", "${s.totalCoaches}", Icons.Default.SportsHandball, Color(0xFF9C27B0), modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("Programlar", "${s.totalPrograms}", Icons.Default.FitnessCenter, Color(0xFFF44336), modifier = Modifier.weight(1f))
                            StatCard("Bugün Giriş", "${s.todayEntries}", Icons.Default.QrCode, Color(0xFF00BCD4), modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("Aktif Üyelik", "${s.activeMemberships}", Icons.Default.CardMembership, Color(0xFF3F51B5), modifier = Modifier.weight(1f))
                            StatCard("Aylık Gelir", String.format("%.0f₺", s.monthlyRevenue), Icons.Default.AttachMoney, Color(0xFF4CAF50), modifier = Modifier.weight(1f))
                        }
                    }
                } else {
                    Text(text = "İstatistikler yüklenemedi", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(Radius.card)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Text(text = title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        }
        Text(text = value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
    }
}

// ---------------------------------------------------------------------------
// Users Tab
// ---------------------------------------------------------------------------

@Composable
private fun AdminUsersContent() {
    var users by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchText by remember { mutableStateOf("") }
    var selectedUser by remember { mutableStateOf<UserProfile?>(null) }
    val coroutineScope = rememberCoroutineScope()

    suspend fun loadUsers() {
        isLoading = true
        try { users = AdminService.fetchAllUsers(searchText = searchText.takeIf { it.isNotBlank() }) } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}
        isLoading = false
    }

    LaunchedEffect(Unit) { loadUsers() }
    LaunchedEffect(searchText) { loadUsers() }

    selectedUser?.let { user ->
        AdminUserDetailDialog(user = user, onDismiss = { selectedUser = null; coroutineScope.launch { loadUsers() } })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchText, onValueChange = { searchText = it },
            placeholder = { Text("Kullanıcı ara...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = { if (searchText.isNotEmpty()) IconButton(onClick = { searchText = "" }) { Icon(Icons.Default.Clear, contentDescription = null) } },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = 8.dp),
            singleLine = true
        )
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary) }
        } else if (users.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Kullanıcı bulunamadı", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(users) { user ->
                    UserRow(user = user, onClick = { selectedUser = user })
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun UserRow(user: UserProfile, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(Primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Text(text = user.name?.take(1)?.uppercase() ?: "?", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Primary)
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "${user.name ?: ""} ${user.surname ?: ""}".trim().ifEmpty { "İsimsiz" }, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                if (user.isBanned == true) Icon(Icons.Default.Block, contentDescription = null, tint = Color.Red, modifier = Modifier.size(14.dp))
            }
            Text(text = user.email ?: "Email yok", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            user.gymName?.let { Text(text = it, fontSize = 11.sp, color = Primary) }
        }
        user.role?.let { role ->
            Box(modifier = Modifier.clip(RoundedCornerShape(Radius.pill)).background(roleColor(role).copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text(text = roleLabel(role), fontSize = 10.sp, color = roleColor(role), fontWeight = FontWeight.Medium)
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f), modifier = Modifier.size(16.dp))
    }
}

private fun roleLabel(role: String) = when (role.lowercase()) {
    "admin" -> "Admin"
    "gym_manager" -> "Yönetici"
    "coach" -> "Koç"
    else -> "Kullanıcı"
}

private fun roleColor(role: String) = when (role.lowercase()) {
    "admin" -> Color(0xFFF44336)
    "gym_manager" -> Color(0xFFFF9800)
    "coach" -> Color(0xFF9C27B0)
    else -> Color(0xFF2196F3)
}

@Composable
private fun AdminUserDetailDialog(user: UserProfile, onDismiss: () -> Unit) {
    var banReason by remember { mutableStateOf("") }
    var isBanning by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val isBanned = user.isBanned == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${user.name ?: ""} ${user.surname ?: ""}".trim(), fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = user.email ?: "", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                user.gymName?.let { Text(text = "Salon: $it", fontSize = 13.sp) }
                user.role?.let { Text(text = "Rol: ${roleLabel(it)}", fontSize = 13.sp) }
                if (isBanned) {
                    Text(text = "BAN: ${user.banReason ?: "Sebep belirtilmedi"}", fontSize = 12.sp, color = Color.Red)
                } else {
                    OutlinedTextField(
                        value = banReason, onValueChange = { banReason = it },
                        label = { Text("Ban Sebebi") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                        modifier = Modifier.fillMaxWidth(), maxLines = 2
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    coroutineScope.launch {
                        isBanning = true
                        try {
                            if (isBanned) AdminService.unbanUser(user.id)
                            else AdminService.banUser(user.id, banReason.ifBlank { "Admin kararı" })
                            onDismiss()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                        } finally { isBanning = false }
                    }
                }
            ) {
                if (isBanning) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Primary, strokeWidth = 2.dp)
                else Text(if (isBanned) "Banı Kaldır" else "Banla", color = if (isBanned) Color(0xFF4CAF50) else Color.Red)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Kapat") } }
    )
}

// ---------------------------------------------------------------------------
// Gyms Tab
// ---------------------------------------------------------------------------

@Composable
private fun AdminGymsContent() {
    var gyms by remember { mutableStateOf<List<Gym>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try { gyms = AdminService.fetchAllGyms() } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}
        isLoading = false
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary) }
    } else if (gyms.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Salon bulunamadı", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)) }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(gyms) { gym ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(MaterialTheme.colorScheme.surface).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Business, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = gym.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                        Text(text = "${gym.city} · ${gym.address}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    }
                    Box(modifier = Modifier.clip(RoundedCornerShape(Radius.pill)).background(if (gym.isActive) Color(0xFF4CAF50).copy(alpha = 0.15f) else Color.Red.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(text = if (gym.isActive) "Aktif" else "Pasif", fontSize = 11.sp, color = if (gym.isActive) Color(0xFF4CAF50) else Color.Red, fontWeight = FontWeight.Medium)
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

// ---------------------------------------------------------------------------
// Coaches Tab
// ---------------------------------------------------------------------------

@Composable
private fun AdminCoachesContent() {
    var coaches by remember { mutableStateOf<List<Coach>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchText by remember { mutableStateOf("") }

    LaunchedEffect(searchText) {
        isLoading = true
        try { coaches = AdminService.fetchAllCoaches(searchText.takeIf { it.isNotBlank() }) } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchText, onValueChange = { searchText = it },
            placeholder = { Text("Koç ara...") }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = 8.dp), singleLine = true
        )
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary) }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(coaches) { coach ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(MaterialTheme.colorScheme.surface).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF9C27B0).copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            Text(text = coach.name.take(1).uppercase(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9C27B0))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = coach.fullName, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                            Text(text = coach.specialty ?: "Uzmanlık belirtilmedi", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                        }
                        Text(text = "★ ${String.format("%.1f", coach.rating)}", fontSize = 13.sp, color = Color(0xFFFF9800), fontWeight = FontWeight.Medium)
                    }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Programs Tab
// ---------------------------------------------------------------------------

@Composable
private fun AdminProgramsContent() {
    var programs by remember { mutableStateOf<List<TrainingProgram>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try { programs = AdminService.fetchAllPrograms() } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}
        isLoading = false
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary) }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(programs) { program ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(MaterialTheme.colorScheme.surface).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = program.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                        Text(text = "${program.category ?: "Genel"} · ${program.duration ?: 0} dk · ${program.difficulty ?: "?"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    }
                    IconButton(onClick = {
                        coroutineScope.launch {
                            try { AdminService.toggleProgramStatus(program.id, !(program.isActive)) } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {}
                        }
                    }, modifier = Modifier.size(32.dp)) {
                        Icon(if (program.isActive) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null, tint = if (program.isActive) Primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

// ---------------------------------------------------------------------------
// Memberships Tab
// ---------------------------------------------------------------------------

@Composable
private fun AdminMembershipsContent() {
    var plans by remember { mutableStateOf<List<MembershipPlan>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try { plans = AdminService.fetchAllMembershipPlans() } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}
        isLoading = false
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary) }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(plans) { plan ->
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(text = plan.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                                if (plan.isPopular) Box(modifier = Modifier.clip(RoundedCornerShape(Radius.pill)).background(Color(0xFFFF9800).copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 2.dp)) { Text(text = "Popüler", fontSize = 10.sp, color = Color(0xFFFF9800)) }
                            }
                            Text(text = "${plan.durationMonths} ay", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                        }
                        Text(text = String.format("%.0f₺", plan.price), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Primary)
                    }
                    if (plan.features.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            plan.features.take(3).forEach { feature ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                                    Text(text = feature, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

// ---------------------------------------------------------------------------
// QR Entries Tab
// ---------------------------------------------------------------------------

@Composable
private fun AdminQREntriesContent() {
    var entries by remember { mutableStateOf<List<QREntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try { entries = AdminService.fetchAllQREntries() } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}
        isLoading = false
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary) }
    } else if (entries.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Giriş kaydı bulunamadı", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)) }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries) { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(MaterialTheme.colorScheme.surface).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(if (entry.entryType == "entry") Color(0xFF4CAF50).copy(alpha = 0.15f) else Color(0xFFFF9800).copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Icon(if (entry.entryType == "entry") Icons.Default.Login else Icons.Default.Logout, contentDescription = null, tint = if (entry.entryType == "entry") Color(0xFF4CAF50) else Color(0xFFFF9800), modifier = Modifier.size(18.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = entry.userId.take(8) + "...", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                        Text(text = "${entry.entryDate} ${entry.entryTime}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    }
                    Text(text = if (entry.entryType == "entry") "Giriş" else "Çıkış", fontSize = 12.sp, color = if (entry.entryType == "entry") Color(0xFF4CAF50) else Color(0xFFFF9800), fontWeight = FontWeight.Medium)
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

// ---------------------------------------------------------------------------
// Audit Logs Tab
// ---------------------------------------------------------------------------

@Composable
private fun AdminAuditLogsContent() {
    var logs by remember { mutableStateOf<List<AuditLog>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try { logs = AdminService.fetchAuditLogs() } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}
        isLoading = false
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary) }
    } else if (logs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Log bulunamadı", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)) }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(logs) { log ->
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(MaterialTheme.colorScheme.surface).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = log.action, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                        Text(text = log.createdAt.take(16).replace("T", " "), fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                    }
                    Text(text = "${log.resourceType} · ${log.userEmail ?: log.userId?.take(8) ?: "?"}...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    log.description?.let { Text(text = it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f), maxLines = 2) }
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

// ---------------------------------------------------------------------------
// Reports Tab
// ---------------------------------------------------------------------------

@Composable
private fun AdminReportsContent() {
    var userGrowth by remember { mutableStateOf<List<ChartDataPoint>>(emptyList()) }
    var revenue by remember { mutableStateOf<List<ChartDataPoint>>(emptyList()) }
    var popularPrograms by remember { mutableStateOf<List<ProgramStatistic>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            userGrowth = AdminService.fetchUserGrowthReport(days = 30)
            revenue = AdminService.fetchRevenueReport(months = 6)
            popularPrograms = AdminService.fetchPopularProgramsReport(limit = 5)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}
        isLoading = false
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary) }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(Spacing.xl), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                ReportSection(title = "Kullanıcı Büyümesi (30 gün)", data = userGrowth.map { it.label to it.value.toFloat() }, color = Color(0xFF2196F3))
            }
            item {
                ReportSection(title = "Aylık Gelir (6 ay)", data = revenue.map { it.label to it.value.toFloat() }, color = Color(0xFF4CAF50), valueFormat = "%.0f₺")
            }
            if (popularPrograms.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text = "Popüler Programlar", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        popularPrograms.forEach { prog ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(MaterialTheme.colorScheme.surface).padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = prog.programName, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
                                Text(text = "${prog.sessionCount} seans", fontSize = 13.sp, color = Primary, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun ReportSection(title: String, data: List<Pair<String, Float>>, color: Color, valueFormat: String = "%.0f") {
    if (data.isEmpty()) return
    val maxVal = data.maxOf { it.second }.takeIf { it > 0 } ?: 1f

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(MaterialTheme.colorScheme.surface).padding(16.dp).height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            data.forEach { (label, value) ->
                val barH = (value / maxVal * 80 + 20).dp
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(color))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = label.take(3), fontSize = 9.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Community settings + subgroups
// ---------------------------------------------------------------------------

@Composable
private fun AdminCommunityContent() {
    val scope = rememberCoroutineScope()
    var generalEnabled by remember { mutableStateOf(true) }
    var gymFeedEnabled by remember { mutableStateOf(true) }
    var generalGroups by remember { mutableStateOf<List<CommunityGroup>>(emptyList()) }
    var gymGroups by remember { mutableStateOf<List<CommunityGroup>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var newGroupName by remember { mutableStateOf("") }
    var newGroupScope by remember { mutableStateOf("general") }
    var memberUserId by remember { mutableStateOf("") }
    var selectedGroupForMember by remember { mutableStateOf<CommunityGroup?>(null) }

    suspend fun reload() {
        isLoading = true
        try {
            val settings = com.app.coachup.app.services.CommunityService.fetchGlobalSettings()
            generalEnabled = settings.generalEnabled
            gymFeedEnabled = settings.gymFeedEnabled
            generalGroups = com.app.coachup.app.services.CommunityService.fetchGroups("general")
            val gymId = com.app.coachup.app.services.UserService.resolveActiveGymIdForContent()
            gymGroups = if (gymId != null) {
                com.app.coachup.app.services.CommunityService.fetchGroups("gym", gymId)
            } else emptyList()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message = e.message ?: "Topluluk ayarları yüklenemedi"
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Primary)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Topluluk Ayarları", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "Genel topluluğu kapatabilir, salon feed’ini devre dışı bırakabilir ve alt grup açabilirsiniz.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.card))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Genel Topluluk", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text("Tüm uygulama kullanıcıları", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    }
                    Switch(
                        checked = generalEnabled,
                        onCheckedChange = { checked ->
                            generalEnabled = checked
                            scope.launch {
                                try {
                                    com.app.coachup.app.services.CommunityService.upsertGlobalSettings(checked, gymFeedEnabled)
                                    message = "Genel topluluk güncellendi"
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    generalEnabled = !checked
                                    message = e.message
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = Primary)
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Salon Toplulukları", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text("Salon üye feed’leri (global anahtar)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    }
                    Switch(
                        checked = gymFeedEnabled,
                        onCheckedChange = { checked ->
                            gymFeedEnabled = checked
                            scope.launch {
                                try {
                                    com.app.coachup.app.services.CommunityService.upsertGlobalSettings(generalEnabled, checked)
                                    message = "Salon toplulukları güncellendi"
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    gymFeedEnabled = !checked
                                    message = e.message
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = Primary)
                    )
                }
            }
        }

        item {
            Text("Alt Grup Oluştur", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.card))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = newGroupScope == "general",
                        onClick = { newGroupScope = "general" },
                        label = { Text("Genel") }
                    )
                    FilterChip(
                        selected = newGroupScope == "gym",
                        onClick = { newGroupScope = "gym" },
                        label = { Text("Salon") }
                    )
                }
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    label = { Text("Grup adı") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary)
                )
                Button(
                    onClick = {
                        if (newGroupName.isBlank()) return@Button
                        scope.launch {
                            try {
                                val userId = AuthService.getCurrentUserId() ?: return@launch
                                val gymId = com.app.coachup.app.services.UserService.resolveActiveGymIdForContent()
                                if (newGroupScope == "gym" && gymId.isNullOrBlank()) {
                                    message = "Salon grubu için aktif salon gerekli"
                                    return@launch
                                }
                                com.app.coachup.app.services.CommunityService.createGroup(
                                    scope = newGroupScope,
                                    name = newGroupName,
                                    description = null,
                                    gymId = gymId,
                                    createdBy = userId
                                )
                                newGroupName = ""
                                reload()
                                message = "Grup oluşturuldu"
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                message = e.message
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    enabled = newGroupName.isNotBlank()
                ) {
                    Text("Grup Ekle")
                }
            }
        }

        item {
            Text("Genel Alt Gruplar", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        }
        if (generalGroups.isEmpty()) {
            item {
                Text("Henüz genel alt grup yok", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            }
        } else {
            items(generalGroups, key = { it.id }) { group ->
                AdminGroupRow(
                    group = group,
                    onAddMember = { selectedGroupForMember = group },
                    onDeactivate = {
                        scope.launch {
                            try {
                                com.app.coachup.app.services.CommunityService.deactivateGroup(group.id)
                                reload()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                message = e.message
                            }
                        }
                    }
                )
            }
        }

        item {
            Text("Salon Alt Grupları", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        }
        if (gymGroups.isEmpty()) {
            item {
                Text("Henüz salon alt grubu yok", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            }
        } else {
            items(gymGroups, key = { it.id }) { group ->
                AdminGroupRow(
                    group = group,
                    onAddMember = { selectedGroupForMember = group },
                    onDeactivate = {
                        scope.launch {
                            try {
                                com.app.coachup.app.services.CommunityService.deactivateGroup(group.id)
                                reload()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                message = e.message
                            }
                        }
                    }
                )
            }
        }

        message?.let { msg ->
            item {
                Text(msg, fontSize = 13.sp, color = Primary)
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }

    selectedGroupForMember?.let { group ->
        AlertDialog(
            onDismissRequest = { selectedGroupForMember = null; memberUserId = "" },
            title = { Text("Üye Ekle — ${group.name}") },
            text = {
                OutlinedTextField(
                    value = memberUserId,
                    onValueChange = { memberUserId = it },
                    label = { Text("Kullanıcı ID (UUID)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (memberUserId.isBlank()) return@TextButton
                    scope.launch {
                        try {
                            com.app.coachup.app.services.CommunityService.addGroupMember(group.id, memberUserId.trim())
                            message = "Üye eklendi"
                            selectedGroupForMember = null
                            memberUserId = ""
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            message = e.message
                        }
                    }
                }) { Text("Ekle", color = Primary) }
            },
            dismissButton = {
                TextButton(onClick = { selectedGroupForMember = null; memberUserId = "" }) { Text("İptal") }
            }
        )
    }
}

@Composable
private fun AdminGroupRow(
    group: CommunityGroup,
    onAddMember: () -> Unit,
    onDeactivate: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(group.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(group.scope, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
        }
        TextButton(onClick = onAddMember) { Text("Üye", color = Primary) }
        TextButton(onClick = onDeactivate) { Text("Kapat", color = Color(0xFFE53935)) }
    }
}
