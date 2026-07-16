package com.app.coachup.app.ui.guardian

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.app.coachup.app.services.GuardianService
import com.app.coachup.app.theme.Primary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Guardian Tab Enum — mirrors iOS GuardianTab
// ---------------------------------------------------------------------------

enum class GuardianTab(
    val label: String,
    val route: String,
    val icon: ImageVector
) {
    HOME("Ana Sayfa", "guardian_home", Icons.Filled.Home),
    CHILDREN("Çocuklarım", "guardian_children", Icons.Filled.People),
    PAYMENTS("Ödemeler", "guardian_payments", Icons.Filled.CreditCard),
    PROFILE("Profil", "guardian_profile", Icons.Filled.Person)
}

// ---------------------------------------------------------------------------
// Guardian Root Screen — mirrors iOS GuardianTabView.swift
// ---------------------------------------------------------------------------

@Composable
fun GuardianScreen(
    onLogout: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(GuardianTab.HOME) }
    val innerNav = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    val children by GuardianService.children.collectAsState()
    val guardian by GuardianService.guardian.collectAsState()
    val isLoading by GuardianService.isLoading.collectAsState()

    LaunchedEffect(guardian?.id) {
        val guardianId = guardian?.id ?: return@LaunchedEffect
        try {
            GuardianService.fetchChildren(guardianId)
            GuardianService.children.value.forEach { relation ->
                GuardianService.fetchChildEntries(relation.memberId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("GuardianScreen", "Failed to load guardian data: ${e.message}")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Inner NavHost for guardian tabs
        NavHost(
            navController = innerNav,
            startDestination = GuardianTab.HOME.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 64.dp)
        ) {
            composable(GuardianTab.HOME.route) {
                GuardianHomeScreen(innerNav)
            }
            composable(GuardianTab.CHILDREN.route) {
                GuardianChildrenScreen(innerNav)
            }
            composable(GuardianTab.PAYMENTS.route) {
                GuardianPaymentsScreen()
            }
            composable(GuardianTab.PROFILE.route) {
                GuardianProfileScreen(onLogout = onLogout)
            }
            composable(
                route = "guardian_child_detail/{childId}",
                arguments = listOf(navArgument("childId") { type = NavType.StringType })
            ) { backStack ->
                val childId = backStack.arguments?.getString("childId") ?: return@composable
                val relation = children.find { it.memberId == childId }
                when {
                    relation?.member != null -> {
                        GuardianChildDetailScreen(
                            relation = relation,
                            onBack = { innerNav.popBackStack() }
                        )
                    }
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Primary)
                        }
                    }
                    else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Çocuk bilgisi yüklenemedi",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { innerNav.popBackStack() }) {
                                Text("Geri Dön")
                            }
                        }
                    }
                }
            }
        }

        // Custom bottom tab bar
        GuardianTabBar(
            selectedTab = selectedTab,
            onTabSelected = { tab ->
                selectedTab = tab
                innerNav.navigate(tab.route) {
                    popUpTo(GuardianTab.HOME.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun GuardianTabBar(
    selectedTab: GuardianTab,
    onTabSelected: (GuardianTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 8.dp)
        ) {
            GuardianTab.values().forEach { tab ->
                val selected = tab == selectedTab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (selected) Primary else Color(0xFF9E9E9E),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = tab.label,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) Primary else Color(0xFF9E9E9E)
                    )
                }
            }
        }
    }
}
