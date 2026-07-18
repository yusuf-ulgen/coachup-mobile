package com.app.coachup.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.app.coachup.app.navigation.Routes
import com.app.coachup.app.services.AuthService
import com.app.coachup.app.services.UserService
import com.app.coachup.app.theme.*
import com.app.coachup.app.utils.AppLocaleManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    navController: NavController
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var appLanguage by remember { mutableStateOf(AppLocaleManager.getLanguage(context)) }
    var userId by remember { mutableStateOf<String?>(null) }
    var notificationsEnabled by remember { mutableStateOf(true) }
    var biometricEnabled by remember { mutableStateOf(false) }
    var defaultScreen by remember { mutableStateOf("home") }
    var weightUnit by remember { mutableStateOf("kg") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val uid = AuthService.getCurrentUserId() ?: run { isLoading = false; return@LaunchedEffect }
        userId = uid
        try {
            val profile = UserService.fetchProfile(uid) ?: return@LaunchedEffect
            notificationsEnabled = profile.notificationsEnabled
            biometricEnabled = profile.biometricEnabled
            defaultScreen = profile.defaultScreen
            weightUnit = profile.weightUnit
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        } finally {
            isLoading = false
        }
    }

    fun persistSetting(update: suspend (String) -> Unit, onError: () -> Unit = {}) {
        val uid = userId ?: return
        scope.launch {
            try {
                update(uid)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                onError()
            }
        }
    }

    val defaultScreenDisplayName = when (defaultScreen) {
        "home" -> "Anasayfa"
        "calendar" -> "Takvim"
        "training" -> "Antrenman"
        "qr" -> "QR Tarama"
        else -> "Anasayfa"
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
                text = "Ayarlar",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Primary)
                }
            } else {
            // Tercihler Section
            SettingsSection(title = "Tercihler") {
                SettingsNavigationRow(
                    title = "Varsayılan giriş ekranı",
                    value = defaultScreenDisplayName,
                    onClick = { navController.navigate(Routes.SETTINGS_DEFAULT_SCREEN) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                SettingsToggleRow(
                    title = "Bildirimler",
                    isOn = notificationsEnabled,
                    onToggle = { enabled ->
                        notificationsEnabled = enabled
                        persistSetting({ uid -> UserService.updateNotificationSetting(uid, enabled) }) {
                            notificationsEnabled = !enabled
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                SettingsToggleRow(
                    title = "Biyometrik doğrulama",
                    isOn = biometricEnabled,
                    onToggle = { enabled ->
                        biometricEnabled = enabled
                        persistSetting({ uid -> UserService.updateBiometricSetting(uid, enabled) }) {
                            biometricEnabled = !enabled
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                SettingsNavigationRow(
                    title = "Görünüm",
                    onClick = { navController.navigate(Routes.SETTINGS_APPEARANCE) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                SettingsNavigationRow(
                    title = "Dil",
                    value = AppLocaleManager.displayName(appLanguage),
                    onClick = { navController.navigate(Routes.SETTINGS_LANGUAGE) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                // Weight Unit Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newUnit = if (weightUnit == "kg") "lbs" else "kg"
                            weightUnit = newUnit
                            persistSetting({ uid -> UserService.updateWeightUnit(uid, newUnit) }) {
                                weightUnit = if (newUnit == "kg") "lbs" else "kg"
                            }
                        }
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Ağırlık ölçü birimi", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (weightUnit == "kg") "Kilogram" else "Pound",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Primary
                        )
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Hesap Section
            SettingsSection(title = "Hesap") {
                SettingsNavigationRow(
                    title = "Profil",
                    onClick = { navController.navigate(Routes.PROFILE) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                SettingsNavigationRow(
                    title = "Üyeliğim",
                    onClick = { navController.navigate(Routes.SETTINGS_MEMBERSHIP) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                SettingsNavigationRow(
                    title = "Adres",
                    onClick = { navController.navigate(Routes.SETTINGS_ADDRESS) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                SettingsNavigationRow(
                    title = "Şifre",
                    onClick = { navController.navigate(Routes.SETTINGS_PASSWORD) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                SettingsNavigationRow(
                    title = "Acil durum iletişim bilgisi",
                    onClick = { navController.navigate(Routes.SETTINGS_EMERGENCY_CONTACT) }
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Column(content = content)
    }
}

@Composable
fun SettingsNavigationRow(
    title: String,
    value: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (value != null) {
                Text(text = value, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun SettingsToggleRow(
    title: String,
    isOn: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
        Switch(
            checked = isOn,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Primary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant
            )
        )
    }
}
