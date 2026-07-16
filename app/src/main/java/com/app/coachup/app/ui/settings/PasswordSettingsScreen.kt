package com.app.coachup.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.app.coachup.app.services.UserService
import com.app.coachup.app.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun PasswordSettingsScreen(
    navController: NavController
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showCurrentPassword by remember { mutableStateOf(false) }
    var showNewPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showSuccess by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (showSuccess) {
        AlertDialog(
            onDismissRequest = { showSuccess = false; navController.popBackStack() },
            title = { Text("Başarılı") },
            text = { Text("Şifreniz başarıyla güncellendi.") },
            confirmButton = {
                TextButton(onClick = { showSuccess = false; navController.popBackStack() }) {
                    Text("Tamam", color = Primary)
                }
            }
        )
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
                text = "Şifre",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            PasswordInputField(
                label = "Mevcut Şifre",
                placeholder = "Mevcut şifrenizi girin",
                value = currentPassword,
                onValueChange = { currentPassword = it },
                isVisible = showCurrentPassword,
                onToggleVisibility = { showCurrentPassword = !showCurrentPassword }
            )
            PasswordInputField(
                label = "Yeni Şifre",
                placeholder = "Yeni şifrenizi girin",
                value = newPassword,
                onValueChange = { newPassword = it },
                isVisible = showNewPassword,
                onToggleVisibility = { showNewPassword = !showNewPassword }
            )
            PasswordInputField(
                label = "Yeni Şifre Tekrar",
                placeholder = "Yeni şifrenizi tekrar girin",
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                isVisible = showConfirmPassword,
                onToggleVisibility = { showConfirmPassword = !showConfirmPassword }
            )
        }

        // Save Button
        Button(
            onClick = {
                when {
                    newPassword.isEmpty() -> errorMessage = "Yeni şifre boş olamaz"
                    newPassword.length < 6 -> errorMessage = "Şifre en az 6 karakter olmalıdır"
                    newPassword != confirmPassword -> errorMessage = "Şifreler eşleşmiyor"
                    else -> {
                        isLoading = true
                        scope.launch {
                            try {
                                UserService.changePassword(newPassword)
                                isLoading = false
                                showSuccess = true
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                isLoading = false
                                errorMessage = e.message ?: "Şifre güncellenemedi"
                            }
                        }
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

@Composable
fun PasswordInputField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    isVisible: Boolean,
    onToggleVisibility: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onToggleVisibility) {
                    Icon(
                        imageVector = if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (isVisible) "Şifreyi gizle" else "Şifreyi göster",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                cursorColor = Primary
            ),
            shape = RoundedCornerShape(Radius.item),
            singleLine = true
        )
    }
}
