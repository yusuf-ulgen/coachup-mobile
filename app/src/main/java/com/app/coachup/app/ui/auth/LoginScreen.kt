package com.app.coachup.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.app.coachup.app.services.AuthException
import com.app.coachup.app.services.AuthService
import com.app.coachup.app.theme.AllWhite
import com.app.coachup.app.theme.Black100
import com.app.coachup.app.theme.Primary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Login screen – Android equivalent of iOS LoginView.
 *
 * Visual structure mirrors the iOS layout exactly:
 *  - Hero image fills the top ~50 % of the screen.
 *  - App logo is centred on top of the hero image.
 *  - A white card slides up from the bottom; when the keyboard is visible
 *    it animates further up so the fields remain accessible.
 *  - Pill-shaped OutlinedTextFields for email and password.
 *  - Primary-coloured pill button with a white arrow-circle on the right.
 *  - "Hesabınız yok mu? Hesap oluşturun." link centred below the button.
 */
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showError by remember { mutableStateOf(false) }
    var showEmailNotConfirmed by remember { mutableStateOf(false) }
    var isResendingConfirmation by remember { mutableStateOf(false) }
    var resendMessage by remember { mutableStateOf<String?>(null) }
    val passwordFocusRequester = remember { FocusRequester() }

    fun login() {
        focusManager.clearFocus()
        if (email.isBlank() || password.isBlank()) {
            errorMessage = "Lütfen tüm alanları doldurun"
            showError = true
            return
        }
        scope.launch {
            isLoading = true
            try {
                AuthService.signIn(email.trim(), password)
                AuthService.ensureProfileFromAuthIfMissing()
                onLoginSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (_: AuthException.EmailNotConfirmed) {
                errorMessage = "E-posta adresinizi doğrulamadınız. Gelen kutunuzu kontrol edin."
                showEmailNotConfirmed = true
            } catch (e: Exception) {
                errorMessage = when {
                    e.message?.contains("invalid_credentials", ignoreCase = true) == true ||
                    e.message?.contains("Invalid login credentials", ignoreCase = true) == true ->
                        "E-posta veya şifre hatalı."
                    e.message?.contains("email_not_confirmed", ignoreCase = true) == true ->
                        "E-posta adresinizi doğrulamadınız. Gelen kutunuzu kontrol edin."
                    e.message?.contains("network", ignoreCase = true) == true ||
                    e.message?.contains("Unable to resolve host", ignoreCase = true) == true ->
                        "İnternet bağlantısı yok."
                    else -> "Giriş yapılamadı. Lütfen tekrar deneyin."
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
        // ── Hero image – top 50 % of the screen ──────────────────────────────
        AsyncImage(
            model = GymConfig.LOGIN_HERO_RES,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize(0.50f),
            error = painterResource(id = R.drawable.ic_launcher_background),
            placeholder = painterResource(id = R.drawable.ic_launcher_background)
        )

        // ── Logo centred on the hero image ────────────────────────────────────
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

        // ── White card with form fields ───────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            Spacer(modifier = Modifier.weight(0.42f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.58f)
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 28.dp, bottom = 40.dp)
            ) {
                // Title
                Text(
                    text = "Giriş Yap",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Subtitle
                Text(
                    text = "Kaldığın yerden devam etmek için\ngiriş yap.",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Email field
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
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = Primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Password field
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
                    keyboardActions = KeyboardActions(onDone = { login() }),
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
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = Primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(passwordFocusRequester)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Primary pill button with trailing arrow circle
                Button(
                    onClick = { login() },
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
                            color = AllWhite,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        Text(
                            text = "Giriş Yap",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = AllWhite
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    // Right-side arrow circle – mirrors iOS Circle + arrow.right
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
                            color = Black100
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = buildAnnotatedString {
                        append("Hesabınız yok mu? ")
                        withStyle(
                            style = SpanStyle(
                                color = Primary,
                                fontWeight = FontWeight.Medium,
                                textDecoration = TextDecoration.Underline
                            )
                        ) { append("Hesap oluşturun.") }
                    },
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onNavigateToRegister() }
                )

            }
        }
    }

    if (showEmailNotConfirmed) {
        AlertDialog(
            onDismissRequest = { showEmailNotConfirmed = false },
            title = { Text("E-posta Doğrulama Gerekli") },
            text = {
                Column {
                    Text(
                        errorMessage
                            ?: "Giriş yapmadan önce e-posta adresinize gelen doğrulama linkine tıklayın."
                    )
                    resendMessage?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = Primary, fontSize = 14.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            isResendingConfirmation = true
                            resendMessage = null
                            try {
                                AuthService.resendConfirmationEmail(email.trim())
                                resendMessage = "Doğrulama e-postası tekrar gönderildi."
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                resendMessage = e.message ?: "E-posta gönderilemedi."
                            } finally {
                                isResendingConfirmation = false
                            }
                        }
                    },
                    enabled = !isResendingConfirmation && email.isNotBlank()
                ) {
                    if (isResendingConfirmation) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Primary
                        )
                    } else {
                        Text("Tekrar Gönder", color = Primary)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmailNotConfirmed = false }) {
                    Text("Tamam")
                }
            }
        )
    }

    // Error dialog – mirrors iOS .alert("Hata", ...)
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
}
