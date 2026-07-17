package com.app.coachup.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.app.coachup.app.R
import com.app.coachup.app.config.GymConfig
import com.app.coachup.app.models.Gender

import com.app.coachup.app.services.AuthException
import com.app.coachup.app.services.AuthService
import com.app.coachup.app.services.UserService
import com.app.coachup.app.theme.Primary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Register screen – Android equivalent of iOS RegisterView.
 *
 * Mirrors iOS layout:
 *  - Hero image (top ~40 %).
 *  - Logo centred on the hero.
 *  - Scrollable white card from bottom with:
 *      name → email → password (with eye toggle) → gender radio pair → register button → login link.
 *  - Form card uses the same 40/60 hero split as LoginScreen.
 */
@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onNavigateBack: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedGender by remember { mutableStateOf<Gender?>(null) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showError by remember { mutableStateOf(false) }
    var showEmailConfirm by remember { mutableStateOf(false) }
    var isResendingConfirmation by remember { mutableStateOf(false) }

    val emailFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }

    val authFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        cursorColor = Primary
    )

    val authFieldTextStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp)

    fun register() {
        focusManager.clearFocus()

        if (name.isBlank()) {
            errorMessage = "Lütfen isminizi girin"
            showError = true
            return
        }
        if (selectedGender == null) {
            errorMessage = "Lütfen cinsiyetinizi seçin"
            showError = true
            return
        }
        if (email.isBlank() || password.isBlank()) {
            errorMessage = "Lütfen tüm alanları doldurun"
            showError = true
            return
        }
        if (password.length < 6) {
            errorMessage = "Şifre en az 6 karakter olmalıdır"
            showError = true
            return
        }

        scope.launch {
            isLoading = true
            try {
                val emailConfirmed = AuthService.signUp(
                    email = email.trim(),
                    password = password,
                    name = name.trim(),
                    gender = selectedGender?.dbValue ?: "",
                    isIndividual = true
                )
                val userId = AuthService.getCurrentUserId()
                if (userId != null) {
                    UserService.ensureProfileExists(
                        userId = userId,
                        email = email.trim(),
                        name = name.trim(),
                        gender = selectedGender?.dbValue ?: "",
                        isIndividual = true
                    )
                }
                if (emailConfirmed && AuthService.isCurrentUserEmailConfirmed()) {
                    onRegisterSuccess()
                } else {
                    showEmailConfirm = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: AuthException.EmailNotConfirmed) {
                showEmailConfirm = true
            } catch (e: Exception) {
                android.util.Log.e("RegisterScreen", "Registration failed", e)
                errorMessage = when {
                    e.message?.contains("already registered", ignoreCase = true) == true ||
                    e.message?.contains("User already registered", ignoreCase = true) == true ->
                        "Bu e-posta adresi zaten kayıtlı."
                    else -> e.message ?: "Kayıt başarısız. Lütfen tekrar deneyin."
                }
                showError = true
            } finally {
                isLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { focusManager.clearFocus() }
    ) {
        // ── Hero image – top ~40 % ────────────────────────────────────────────
        AsyncImage(
            model = GymConfig.LOGIN_HERO_RES,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize(0.40f),
            error = painterResource(id = R.drawable.ic_launcher_background),
            placeholder = painterResource(id = R.drawable.ic_launcher_background)
        )

        // ── Logo centred on hero ──────────────────────────────────────────────
        AsyncImage(
            model = GymConfig.LOGIN_LOGO_RES,
            contentDescription = "CoachUp Logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .width(140.dp)
                .padding(top = 56.dp)
                .align(Alignment.TopCenter),
            error = painterResource(id = R.mipmap.ic_launcher_foreground),
            placeholder = painterResource(id = R.mipmap.ic_launcher_foreground)
        )

        // ── Form card — Login ile aynı oran ───────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            Spacer(modifier = Modifier.weight(0.40f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.60f)
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 32.dp, bottom = 40.dp)
            ) {
                Text(
                    text = "Kayıt Ol",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Bireysel hesabınızı oluşturun,\nsalon üyeliği gerekmez.",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = {
                        Text(
                            text = "İsim",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { emailFocusRequester.requestFocus() }
                    ),
                    shape = RoundedCornerShape(100.dp),
                    textStyle = authFieldTextStyle,
                    colors = authFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = {
                        Text(
                            text = "Email adresinizi girin",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { passwordFocusRequester.requestFocus() }
                    ),
                    shape = RoundedCornerShape(100.dp),
                    textStyle = authFieldTextStyle,
                    colors = authFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(emailFocusRequester)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = {
                        Text(
                            text = "Şifre",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp
                        )
                    },
                    singleLine = true,
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { register() }),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Filled.Visibility
                                else Icons.Filled.VisibilityOff,
                                contentDescription = if (isPasswordVisible) "Şifreyi gizle"
                                else "Şifreyi göster",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    shape = RoundedCornerShape(100.dp),
                    textStyle = authFieldTextStyle,
                    colors = authFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(passwordFocusRequester)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Gender.values().forEach { gender ->
                        val isSelected = selectedGender == gender
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(RoundedCornerShape(100.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) Primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(100.dp)
                                )
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { selectedGender = gender }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = gender.value,
                                fontSize = 16.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .border(
                                        width = if (isSelected) 6.dp else 1.dp,
                                        color = if (isSelected) Primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Register button
                Button(
                    onClick = { register() },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary,
                        disabledContainerColor = Primary.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        Text(
                            text = "Kayıt Ol",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .shadow(elevation = 1.dp, shape = CircleShape, clip = false)
                            .background(Color.White, CircleShape)
                    ) {
                        Text(
                            text = "→",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // "Hesabınız var mı? Giriş yapın." link
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "Hesabınız var mı? ",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    color = Primary,
                                    textDecoration = TextDecoration.Underline
                                )
                            ) { append("Giriş yapın.") }
                        },
                        fontSize = 14.sp,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onNavigateBack
                        )
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }

    // Error dialog
    if (showError) {
        AlertDialog(
            onDismissRequest = { showError = false },
            title = { Text("Hata") },
            text = { Text(errorMessage ?: "Bir hata oluştu") },
            confirmButton = {
                TextButton(onClick = { showError = false }) {
                    Text("Tamam", color = Primary)
                }
            }
        )
    }

    if (showEmailConfirm) {
        AlertDialog(
            onDismissRequest = {
                scope.launch {
                    try { AuthService.signOut() } catch (_: Exception) {}
                    showEmailConfirm = false
                    onNavigateBack()
                }
            },
            title = { Text("E-posta Doğrulama") },
            text = {
                Text("Kayıt oluşturuldu. Giriş yapmadan önce e-posta adresinize gelen doğrulama linkine tıklayın.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            isResendingConfirmation = true
                            try {
                                AuthService.resendConfirmationEmail(email.trim())
                            } catch (_: Exception) {
                            } finally {
                                isResendingConfirmation = false
                            }
                        }
                    },
                    enabled = !isResendingConfirmation
                ) {
                    Text(
                        if (isResendingConfirmation) "Gönderiliyor..." else "E-postayı Tekrar Gönder",
                        color = Primary
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try { AuthService.signOut() } catch (_: Exception) {}
                            showEmailConfirm = false
                            onNavigateBack()
                        }
                    }
                ) {
                    Text("Giriş Ekranına Dön", color = Primary)
                }
            }
        )
    }
}
