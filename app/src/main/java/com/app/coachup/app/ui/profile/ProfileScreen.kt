package com.app.coachup.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.app.coachup.app.models.Gender
import com.app.coachup.app.services.AuthService
import com.app.coachup.app.services.UserService
import com.app.coachup.app.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentProfile by UserService.currentProfile.collectAsState()

    var name by remember { mutableStateOf("") }
    var surname by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var selectedGender by remember { mutableStateOf("Erkek") }
    var isLoading by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var genderExpanded by remember { mutableStateOf(false) }

    // Photo pickers state
    var showPhotoSourceDialog by remember { mutableStateOf(false) }

    val genderOptions = listOf("Erkek", "Kadın")

    // Launchers
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            isLoading = true
            coroutineScope.launch {
                try {
                    val userId = AuthService.getCurrentUserId() ?: return@launch
                    UserService.uploadAvatar(context, userId, it)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    errorMessage = "Fotoğraf yüklenemedi: ${e.localizedMessage}"
                } finally {
                    isLoading = false
                }
            }
        }
    }

    var cameraImageUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val uri = cameraImageUri ?: return@rememberLauncherForActivityResult
            isLoading = true
            coroutineScope.launch {
                try {
                    val userId = AuthService.getCurrentUserId() ?: return@launch
                    UserService.uploadAvatar(context, userId, uri)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    errorMessage = "Fotoğraf yüklenemedi: ${e.localizedMessage}"
                } finally {
                    isLoading = false
                }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = java.io.File(context.cacheDir, "avatar_capture.jpg")
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            cameraImageUri = uri
            cameraLauncher.launch(uri)
        } else {
            errorMessage = "Kamera izni verilmedi."
        }
    }

    fun requestCameraAndLaunch() {
        val check = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        )
        if (check == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val file = java.io.File(context.cacheDir, "avatar_capture.jpg")
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            cameraImageUri = uri
            cameraLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(currentProfile) {
        val p = currentProfile ?: return@LaunchedEffect
        name = p.name ?: ""
        surname = p.surname ?: ""
        email = p.email ?: ""
        phone = p.phone ?: ""
        birthDate = p.birthDate ?: ""
        height = p.height?.toInt()?.toString() ?: ""
        weight = p.weight?.toInt()?.toString() ?: ""
        selectedGender = Gender.toDisplayValue(p.gender)
    }

    LaunchedEffect(Unit) {
        val userId = AuthService.getCurrentUserId() ?: return@LaunchedEffect
        if (UserService.currentProfile.value == null) {
            UserService.fetchProfile(userId)
        }
    }

    if (showPhotoSourceDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoSourceDialog = false },
            title = { Text("Profil Fotoğrafı") },
            text = { Text("Fotoğraf eklemek için bir kaynak seçin:") },
            confirmButton = {
                TextButton(onClick = {
                    showPhotoSourceDialog = false
                    requestCameraAndLaunch()
                }) {
                    Text("Kamera", color = Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPhotoSourceDialog = false
                    galleryLauncher.launch("image/*")
                }) {
                    Text("Galeri", color = Primary)
                }
            }
        )
    }

    if (showSuccess) {
        AlertDialog(
            onDismissRequest = { showSuccess = false; navController.popBackStack() },
            title = { Text("Başarılı") },
            text = { Text("Profil bilgileriniz güncellendi.") },
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
                text = "Profil",
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Avatar
            Box(
                contentAlignment = Alignment.BottomEnd,
                modifier = Modifier
                    .size(96.dp)
                    .clickable { showPhotoSourceDialog = true }
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(PrimaryLight),
                    contentAlignment = Alignment.Center
                ) {
                    val avatarUrl = currentProfile?.profileImageUrl
                    if (!avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = "Profil resmi",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Primary)
                        .clickable { showPhotoSourceDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Fotoğraf değiştir",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            ProfileTextField(label = "Ad", value = name, onValueChange = { name = it })
            Spacer(modifier = Modifier.height(16.dp))

            ProfileTextField(label = "Soyad", value = surname, onValueChange = { surname = it })
            Spacer(modifier = Modifier.height(16.dp))

            ProfileTextField(
                label = "E-posta",
                value = email,
                onValueChange = { email = it },
                keyboardType = KeyboardType.Email
            )
            Spacer(modifier = Modifier.height(16.dp))

            ProfileTextField(
                label = "Telefon",
                value = phone,
                onValueChange = { phone = it },
                keyboardType = KeyboardType.Phone
            )
            Spacer(modifier = Modifier.height(16.dp))

            ProfileTextField(
                label = "Doğum Tarihi",
                value = birthDate,
                onValueChange = { birthDate = it },
                placeholder = "GG.AA.YYYY"
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Gender Dropdown
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Cinsiyet", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = genderExpanded,
                    onExpandedChange = { genderExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedGender,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        shape = RoundedCornerShape(Radius.item)
                    )
                    ExposedDropdownMenu(
                        expanded = genderExpanded,
                        onDismissRequest = { genderExpanded = false }
                    ) {
                        genderOptions.forEach { gender ->
                            DropdownMenuItem(
                                text = { Text(gender) },
                                onClick = {
                                    selectedGender = gender
                                    genderExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Height & Weight
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    ProfileTextField(
                        label = "Boy (cm)",
                        value = height,
                        onValueChange = { height = it },
                        keyboardType = KeyboardType.Number
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    ProfileTextField(
                        label = "Kilo (kg)",
                        value = weight,
                        onValueChange = { weight = it },
                        keyboardType = KeyboardType.Number
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Save Button
        Button(
            onClick = {
                if (name.isBlank() || surname.isBlank()) {
                    errorMessage = "Ad ve soyad boş bırakılamaz"
                    return@Button
                }
                isLoading = true
                coroutineScope.launch {
                    try {
                        val userId = AuthService.getCurrentUserId() ?: return@launch
                        UserService.updateUserProfile(
                            userId = userId,
                            name = name,
                            surname = surname,
                            email = email,
                            gender = Gender.toDbValue(selectedGender),
                            birthDate = birthDate.takeIf { it.isNotBlank() },
                            height = height.toDoubleOrNull(),
                            weight = weight.toDoubleOrNull()
                        )
                        showSuccess = true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        errorMessage = "Profil güncellenemedi. Lütfen tekrar deneyin."
                    } finally {
                        isLoading = false
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
private fun ProfileTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = if (placeholder.isNotEmpty()) {
                { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else null,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
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
