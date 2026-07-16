package com.app.coachup.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.navigation.NavController
import com.app.coachup.app.services.AuthService
import com.app.coachup.app.services.UserService
import com.app.coachup.app.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private data class TabOption(
    val key: String,
    val label: String,
    val icon: ImageVector
)

private val tabOptions = listOf(
    TabOption("home", "Anasayfa", Icons.Default.Home),
    TabOption("calendar", "Takvim", Icons.Default.CalendarMonth),
    TabOption("training", "Antrenman", Icons.Default.FitnessCenter),
    TabOption("qr", "QR Giriş", Icons.Default.QrCode)
)

@Composable
fun DefaultScreenSettingsScreen(
    navController: NavController
) {
    var selectedScreen by remember { mutableStateOf("home") }
    var isLoading by remember { mutableStateOf(false) }
    var userId by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val uid = AuthService.getCurrentUserId() ?: return@LaunchedEffect
        userId = uid
        try {
            UserService.fetchProfile(uid)?.let { profile ->
                selectedScreen = profile.defaultScreen
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Hata") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text("Tamam", color = Primary)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Spacing.xl)
                .padding(top = Spacing.sm, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { navController.popBackStack() },
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
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(
                text = "Varsayılan Giriş Ekranı",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.xl)
        ) {
            Text(text = "Varsayılan ekranı seçin", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(16.dp))

            // Tab Options Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                tabOptions.forEach { tab ->
                    val isSelected = selectedScreen == tab.key
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedScreen = tab.key }
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) Primary else Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = tab.label,
                            fontSize = 10.sp,
                            color = if (isSelected) Primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "*Seçtiğiniz ekran uygulama ilk açıldığında karşılaşacağınız ekran olacak.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Save Button
        Button(
            onClick = {
                val uid = userId
                if (uid == null) {
                    errorMessage = "Oturum bulunamadı"
                    return@Button
                }
                isLoading = true
                scope.launch {
                    try {
                        UserService.updateDefaultScreen(uid, selectedScreen)
                        isLoading = false
                        navController.popBackStack()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        isLoading = false
                        errorMessage = e.message ?: "Kayıt başarısız"
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl)
                .padding(bottom = 40.dp)
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            shape = RoundedCornerShape(Radius.pill),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            } else {
                Text(
                    text = "Değişiklikleri Kaydet",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }
    }
}
