package com.app.coachup.app.ui.progress

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.app.coachup.app.models.ProgressPhoto
import com.app.coachup.app.models.UserProgressLog
import com.app.coachup.app.services.AuthService
import com.app.coachup.app.services.ProgressService
import com.app.coachup.app.theme.*
import com.app.coachup.app.utils.formatTimeHm
import com.app.coachup.app.utils.halfHourTimeSlots
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ProgressTrackingScreen(navController: NavController) {
    var selectedTab by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var progressLogs by remember { mutableStateOf<List<UserProgressLog>>(emptyList()) }
    var photos by remember { mutableStateOf<List<ProgressPhoto>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showPhotoTypeSheet by remember { mutableStateOf(false) }
    var showReplaceDialog by remember { mutableStateOf(false) }
    var pendingPhotoType by remember { mutableStateOf<String?>(null) }
    var isUploadingPhoto by remember { mutableStateOf(false) }
    var photoUploadError by remember { mutableStateOf<String?>(null) }
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingUploadRequest by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val tabs = listOf("Ölçümler", "Fotoğraflar")

    suspend fun loadData() {
        val userId = AuthService.getCurrentUserId() ?: return
        isLoading = true
        try {
            progressLogs = ProgressService.fetchProgressHistory(userId)
            photos = ProgressService.fetchPhotos(userId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        } finally {
            isLoading = false
        }
    }

    fun queuePhotoUpload(photoType: String, replaceExisting: Boolean) {
        pendingUploadRequest = photoType to replaceExisting
    }

    fun uploadPhoto(photoType: String, replaceExisting: Boolean) {
        val uri = pendingPhotoUri ?: return
        showPhotoTypeSheet = false
        showReplaceDialog = false
        isUploadingPhoto = true
        photoUploadError = null
        coroutineScope.launch {
            try {
                val userId = AuthService.getCurrentUserId()
                    ?: throw IllegalStateException("Oturum bulunamadı. Lütfen tekrar giriş yapın.")
                ProgressService.uploadPhotoFromUri(
                    context = context,
                    userId = userId,
                    uri = uri,
                    photoType = photoType,
                    replaceExisting = replaceExisting
                )
                loadData()
            } catch (e: CancellationException) {
                return@launch
            } catch (e: Throwable) {
                photoUploadError = e.message ?: "Fotoğraf yüklenemedi"
            } finally {
                isUploadingPhoto = false
                pendingPhotoUri = null
                pendingPhotoType = null
            }
        }
    }

    // Gallery picker — opens type sheet after image is selected (unless slot was pre-selected)
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            pendingPhotoUri = uri
            val presetType = pendingPhotoType
            if (presetType != null) {
                pendingPhotoType = null
                val hasExisting = photos.any { it.photoType == presetType }
                if (hasExisting) {
                    pendingPhotoType = presetType
                    showReplaceDialog = true
                } else {
                    queuePhotoUpload(presetType, replaceExisting = false)
                }
            } else {
                showPhotoTypeSheet = true
            }
        } else {
            pendingPhotoType = null
        }
    }

    LaunchedEffect(pendingUploadRequest) {
        val request = pendingUploadRequest ?: return@LaunchedEffect
        pendingUploadRequest = null
        delay(150)
        uploadPhoto(request.first, request.second)
    }

    fun onPhotoTypeSelected(photoType: String) {
        val hasExisting = photos.any { it.photoType == photoType }
        showPhotoTypeSheet = false
        if (hasExisting) {
            pendingPhotoType = photoType
            showReplaceDialog = true
        } else {
            queuePhotoUpload(photoType, replaceExisting = false)
        }
    }

    LaunchedEffect(Unit) { loadData() }

    if (showAddDialog) {
        AddMeasurementDialog(
            onDismiss = { showAddDialog = false },
            onSaved = {
                showAddDialog = false
                coroutineScope.launch { loadData() }
            }
        )
    }

    // Photo type selection sheet
    if (showPhotoTypeSheet) {
        PhotoTypeSheet(
            onSelect = { onPhotoTypeSelected(it) },
            onDismiss = { showPhotoTypeSheet = false; pendingPhotoUri = null }
        )
    }

    if (showReplaceDialog && pendingPhotoType != null) {
        AlertDialog(
            onDismissRequest = { showReplaceDialog = false; pendingPhotoUri = null; pendingPhotoType = null },
            title = { Text("Fotoğraf Değiştir") },
            text = {
                Text(
                    "Bu kategoride zaten bir fotoğraf var. Yeni fotoğraf yüklendiğinde eskisi silinecektir. Devam etmek istiyor musunuz?"
                )
            },
            confirmButton = {
                TextButton(onClick = { queuePhotoUpload(pendingPhotoType!!, replaceExisting = true) }) {
                    Text("Değiştir", color = Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showReplaceDialog = false
                    pendingPhotoUri = null
                    pendingPhotoType = null
                }) { Text("İptal") }
            }
        )
    }

    photoUploadError?.let { message ->
        AlertDialog(
            onDismissRequest = { photoUploadError = null },
            title = { Text("Yükleme Hatası") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { photoUploadError = null }) {
                    Text("Tamam", color = Primary)
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Spacing.xl).padding(top = Spacing.sm, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(text = "İlerleme", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            if (selectedTab == 0) {
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Ölçüm Ekle", tint = Primary)
                }
            } else {
                if (isUploadingPhoto) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Primary, strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = "Fotoğraf Ekle", tint = Primary)
                    }
                }
            }
        }

        // Segment control
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xl).padding(bottom = 16.dp).clip(RoundedCornerShape(Radius.pill)).background(MaterialTheme.colorScheme.surface).padding(4.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(Radius.pill))
                        .background(if (selectedTab == index) Primary else Color.Transparent)
                        .clickable { selectedTab = index }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selectedTab == index) Color.White else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else {
            if (selectedTab == 0) {
                MeasurementsContent(progressLogs = progressLogs)
            } else {
                PhotosContent(
                    photos = photos,
                    onAddPhoto = { photoType ->
                        pendingPhotoType = photoType
                        galleryLauncher.launch("image/*")
                    }
                )
            }
        }
    }
}

@Composable
private fun MeasurementsContent(progressLogs: List<UserProgressLog>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (progressLogs.isNotEmpty()) {
            item { LatestStatsCard(log = progressLogs.first()) }
            if (progressLogs.size > 1) {
                item { WeightChartSection(logs = progressLogs) }
            }
        }
        item { MeasurementHistorySection(logs = progressLogs) }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun LatestStatsCard(log: UserProgressLog) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(Color(0xFF6C5DD3)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Son Ölçüm", fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            log.weight?.let { StatItem(value = String.format("%.1f", it), label = "Kilo (kg)") }
            log.bodyFatPercentage?.let {
                Box(modifier = Modifier.width(1.dp).height(40.dp).background(Color.White.copy(alpha = 0.2f)))
                StatItem(value = String.format("%.1f%%", it), label = "Yağ Oranı")
            }
            log.bmi?.let {
                Box(modifier = Modifier.width(1.dp).height(40.dp).background(Color.White.copy(alpha = 0.2f)))
                StatItem(value = String.format("%.1f", it), label = "BMI")
            }
        }
        Text(text = log.displayDate, fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(text = label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun WeightChartSection(logs: List<UserProgressLog>) {
    val weights = logs.take(10).reversed().mapNotNull { it.weight }
    if (weights.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = "Kilo Grafiği", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        val maxW = weights.max()
        val minW = weights.min()
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(MaterialTheme.colorScheme.surface).padding(16.dp).height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            weights.forEach { w ->
                val barH = if (maxW > minW) ((w - minW) / (maxW - minW) * 80 + 20).dp else 60.dp
                Box(
                    modifier = Modifier.weight(1f).height(barH).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(Primary)
                )
            }
        }
    }
}

@Composable
private fun MeasurementHistorySection(logs: List<UserProgressLog>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = "Ölçüm Geçmişi", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(MaterialTheme.colorScheme.surface).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.ShowChart, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f), modifier = Modifier.size(48.dp))
                    Text(text = "Henüz ölçüm yok", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                }
            }
        } else {
            logs.forEach { log ->
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(MaterialTheme.colorScheme.surface).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(text = log.displayDate, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                            if (log.sourceLabel.isNotBlank()) {
                                Text(text = log.sourceLabel, fontSize = 11.sp, color = Primary.copy(alpha = 0.85f))
                            }
                        }
                        log.weight?.let { Text(text = String.format("%.1f kg", it), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Primary) }
                    }
                    val hasMeasurements = log.chest != null || log.waist != null || log.hips != null
                        || log.displayArms != null || log.displayLegs != null
                        || log.bicepsLeft != null || log.bicepsRight != null
                        || log.thighLeft != null || log.thighRight != null
                    if (hasMeasurements) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            log.chest?.let { MeasurementBadge(label = "Göğüs", value = "${it.toInt()}cm") }
                            log.waist?.let { MeasurementBadge(label = "Bel", value = "${it.toInt()}cm") }
                            log.hips?.let { MeasurementBadge(label = "Kalça", value = "${it.toInt()}cm") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            log.displayArms?.let { MeasurementBadge(label = "Kol", value = "${it.toInt()}cm") }
                            log.displayLegs?.let { MeasurementBadge(label = "Bacak", value = "${it.toInt()}cm") }
                        }
                    }
                    if (log.bodyFatPercentage != null || log.bmi != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            log.bodyFatPercentage?.let { MeasurementBadge(label = "Yağ", value = String.format("%.1f%%", it)) }
                            log.bmi?.let { MeasurementBadge(label = "BMI", value = String.format("%.1f", it)) }
                        }
                    }
                    log.notes?.takeIf { it.isNotBlank() }?.let { note ->
                        Text(
                            text = note,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MeasurementBadge(label: String, value: String) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.background).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun PhotosContent(
    photos: List<ProgressPhoto>,
    onAddPhoto: (String) -> Unit
) {
    val beforePhoto = photos.firstOrNull { it.photoType == "before" }
    val afterPhoto = photos.firstOrNull { it.photoType == "after" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PhotoSlotCard(
            label = "Öncesi",
            photo = beforePhoto,
            onAddClick = { onAddPhoto("before") }
        )
        PhotoSlotCard(
            label = "Sonrası",
            photo = afterPhoto,
            onAddClick = { onAddPhoto("after") }
        )
        Text(
            text = "Her kategoride yalnızca bir fotoğraf saklanır. Yeni yükleme eskisinin yerini alır.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun PhotoSlotCard(
    label: String,
    photo: ProgressPhoto?,
    onAddClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        if (photo != null) {
            AsyncImage(
                model = photo.photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(Radius.item))
                    .clickable(onClick = onAddClick)
            )
            Text(text = photo.takenAt.take(10), fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(Radius.item))
                    .background(MaterialTheme.colorScheme.background)
                    .clickable(onClick = onAddClick),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = Primary, modifier = Modifier.size(28.dp))
                    Text(text = "Fotoğraf ekle", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                }
            }
        }
    }
}

private fun photoTypeLabel(type: String): String = when (type) {
    "before" -> "Öncesi"
    "after" -> "Sonrası"
    "front" -> "Ön"
    "back" -> "Arka"
    "side_left" -> "Sol Yan"
    "side_right" -> "Sağ Yan"
    else -> type.replaceFirstChar { it.uppercase() }
}

// ---------------------------------------------------------------------------
// Photo type selection bottom sheet
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoTypeSheet(
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        "before" to "Öncesi",
        "after" to "Sonrası"
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Fotoğraf Tipi",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            options.forEach { (type, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onSelect(type) }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = label,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMeasurementDialog(onDismiss: () -> Unit, onSaved: () -> Unit) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var weight by remember { mutableStateOf("") }
    var bodyFat by remember { mutableStateOf("") }
    var chest by remember { mutableStateOf("") }
    var waist by remember { mutableStateOf("") }
    var hips by remember { mutableStateOf("") }
    var arms by remember { mutableStateOf("") }
    var legs by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("Tamam", color = Primary) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("İptal") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    saveError?.let { message ->
        AlertDialog(
            onDismissRequest = { saveError = null },
            title = { Text("Kayıt Hatası") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { saveError = null }) { Text("Tamam", color = Primary) }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ölçüm Ekle", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = selectedDate.toString(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Tarih") },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Tarih seç")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = { Text("Kilo (kg) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = bodyFat,
                        onValueChange = { bodyFat = it },
                        label = { Text("Yağ %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = chest,
                        onValueChange = { chest = it },
                        label = { Text("Göğüs (cm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = waist,
                        onValueChange = { waist = it },
                        label = { Text("Bel (cm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hips,
                        onValueChange = { hips = it },
                        label = { Text("Kalça (cm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = arms,
                        onValueChange = { arms = it },
                        label = { Text("Kol (cm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = legs,
                    onValueChange = { legs = it },
                    label = { Text("Bacak (cm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Not (isteğe bağlı)") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Panelde görünecek: Kendi ekledi",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (weight.isBlank()) {
                        saveError = "Kilo alanı zorunludur."
                        return@TextButton
                    }
                    coroutineScope.launch {
                        isSaving = true
                        try {
                            val userId = AuthService.getCurrentUserId()
                                ?: throw IllegalStateException("Oturum bulunamadı.")
                            ProgressService.logMeasurement(
                                userId = userId,
                                logDate = selectedDate,
                                weight = weight.toDoubleOrNull(),
                                bodyFat = bodyFat.toDoubleOrNull(),
                                chest = chest.toDoubleOrNull(),
                                waist = waist.toDoubleOrNull(),
                                hips = hips.toDoubleOrNull(),
                                arms = arms.toDoubleOrNull(),
                                legs = legs.toDoubleOrNull(),
                                notes = notes.takeIf { it.isNotBlank() }
                            )
                            onSaved()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            saveError = e.message ?: "Ölçüm kaydedilemedi."
                        } finally {
                            isSaving = false
                        }
                    }
                },
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Primary, strokeWidth = 2.dp)
                } else {
                    Text("Kaydet", color = Primary)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}
