package com.app.coachup.app.ui.guardian

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.coachup.app.services.AuthService
import com.app.coachup.app.services.GuardianService
import com.app.coachup.app.theme.Primary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// GuardianProfileScreen — mirrors iOS GuardianProfileView.swift
// ---------------------------------------------------------------------------

@Composable
fun GuardianProfileScreen(onLogout: () -> Unit) {
    val guardian by GuardianService.guardian.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Çıkış Yap") },
            text = { Text("Hesabınızdan çıkmak istiyor musunuz?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        coroutineScope.launch {
                            try {
                                AuthService.signOut()
                                GuardianService.reset()
                                onLogout()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {}
                        }
                    }
                ) {
                    Text("Çıkış", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F8F8)),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        item {
            Text(
                text = "Profil",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A),
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            )
        }

        // Profile card
        guardian?.let { g ->
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .shadow(4.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = g.name.firstOrNull()?.toString() ?: "?",
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                color = Primary
                            )
                        }

                        Text(
                            text = "${g.name} ${g.surname}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A1A1A)
                        )

                        g.email?.let { email ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.Email, null, tint = Color(0xFF9E9E9E), modifier = Modifier.size(14.dp))
                                Text(email, fontSize = 13.sp, color = Color(0xFF9E9E9E))
                            }
                        }

                        g.phone?.let { phone ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.Phone, null, tint = Color(0xFF9E9E9E), modifier = Modifier.size(14.dp))
                                Text(phone, fontSize = 13.sp, color = Color(0xFF9E9E9E))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        // Menu items
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .shadow(3.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column {
                    GuardianProfileMenuItem(
                        icon = Icons.Filled.Notifications,
                        title = "Bildirim Ayarları",
                        color = Color(0xFFFF9800),
                        onClick = {}
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFF0F0F0))
                    GuardianProfileMenuItem(
                        icon = Icons.Filled.Lock,
                        title = "Gizlilik",
                        color = Color(0xFF2196F3),
                        onClick = {}
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFF0F0F0))
                    GuardianProfileMenuItem(
                        icon = Icons.Filled.Help,
                        title = "Yardım",
                        color = Color(0xFF4CAF50),
                        onClick = {}
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Logout button
        item {
            Button(
                onClick = { showLogoutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red.copy(alpha = 0.08f),
                    contentColor = Color.Red
                ),
                shape = RoundedCornerShape(14.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Icon(Icons.Filled.ExitToApp, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Çıkış Yap", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun GuardianProfileMenuItem(
    icon: ImageVector,
    title: String,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 14.sp, color = Color(0xFF1A1A1A), modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, null, tint = Color(0xFFBDBDBD), modifier = Modifier.size(14.dp))
    }
}
