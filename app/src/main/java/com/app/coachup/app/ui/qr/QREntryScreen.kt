package com.app.coachup.app.ui.qr

import android.Manifest
import android.content.Context
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.app.coachup.app.models.EntryHistory
import com.app.coachup.app.services.AuthService
import com.app.coachup.app.services.QRScannerService
import com.app.coachup.app.services.UserService
import com.app.coachup.app.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class QREntryViewModel : ViewModel() {

    private val _showSuccess = MutableStateFlow(false)
    val showSuccess: StateFlow<Boolean> = _showSuccess.asStateFlow()

    private val _successType = MutableStateFlow("entry")
    val successType: StateFlow<String> = _successType.asStateFlow()

    private val _showManualEntry = MutableStateFlow(false)
    val showManualEntry: StateFlow<Boolean> = _showManualEntry.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _isScanning = MutableStateFlow(true)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _validationError = MutableStateFlow<String?>(null)
    val validationError: StateFlow<String?> = _validationError.asStateFlow()

    val entries = QRScannerService.entries
    val isLoadingHistory = QRScannerService.isLoading
    val historyError = QRScannerService.error

    fun loadHistory(userId: String) {
        viewModelScope.launch {
            QRScannerService.fetchEntries(userId)
        }
    }

    private fun resolveCodeType(code: String): String {
        return if (code.startsWith("COACHUP_EXIT:")) {
            "exit"
        } else if (code.startsWith("COACHUP_ENTRY:")) {
            "entry"
        } else if (code.matches(Regex("^\\d{6}$"))) {
            val userGymId = UserService.currentProfile.value?.gymId ?: com.app.coachup.app.config.GymConfig.GYM_ID
            val currentSlot = System.currentTimeMillis() / 30000
            var guessedType = "entry"
            for (s in (currentSlot - 1)..(currentSlot + 1)) {
                if (QRScannerService.buildManualCode(userGymId, s, "exit") == code) {
                    guessedType = "exit"
                    break
                }
            }
            guessedType
        } else {
            "entry"
        }
    }

    fun handleScannedCode(userId: String, code: String) {
        val scanError = QRScannerService.validateCode(code)
        if (scanError != null) {
            _errorMessage.value = scanError
            _isScanning.value = true
            return
        }

        _isScanning.value = false
        _isProcessing.value = true

        viewModelScope.launch {
            try {
                _successType.value = resolveCodeType(code)
                QRScannerService.recordEntry(userId, code, method = "qr")
                _showSuccess.value = true
                loadHistory(userId)
                // Auto-hide after 2s
                kotlinx.coroutines.delay(2000)
                _showSuccess.value = false
                _isScanning.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "İşlem gerçekleştirilemedi."
                _isScanning.value = true
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun handleManualEntry(userId: String, code: String) {
        val validError = QRScannerService.validateCode(code)
        if (validError != null) {
            _validationError.value = validError
            return
        }

        _isProcessing.value = true

        viewModelScope.launch {
            try {
                _successType.value = resolveCodeType(code)
                QRScannerService.recordEntry(userId, code, method = "manual")
                _showManualEntry.value = false
                _showSuccess.value = true
                loadHistory(userId)
                kotlinx.coroutines.delay(2000)
                _showSuccess.value = false
                _isScanning.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "İşlem gerçekleştirilemedi."
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun setShowManualEntry(show: Boolean) {
        _showManualEntry.value = show
        if (show) _isScanning.value = false else _isScanning.value = true
    }

    fun validateManualCode(code: String) {
        _validationError.value = QRScannerService.validateCode(code)
    }

    fun clearError() {
        _errorMessage.value = null
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun QREntryScreen(
    onNavigateToAllHistory: () -> Unit,
    vm: QREntryViewModel = viewModel()
) {
    val userId by AuthService.currentUser.collectAsState(initial = null)
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    val showSuccess by vm.showSuccess.collectAsState()
    val successType by vm.successType.collectAsState()
    val showManualEntry by vm.showManualEntry.collectAsState()
    val isProcessing by vm.isProcessing.collectAsState()
    val isScanning by vm.isScanning.collectAsState()
    val errorMessage by vm.errorMessage.collectAsState()
    val validationError by vm.validationError.collectAsState()
    val entries by vm.entries.collectAsState()
    val isLoadingHistory by vm.isLoadingHistory.collectAsState()
    val historyError by vm.historyError.collectAsState()

    var manualCode by remember { mutableStateOf("") }

    // Request camera + load history
    LaunchedEffect(Unit) {
        cameraPermission.launchPermissionRequest()
    }
    LaunchedEffect(userId) {
        userId?.id?.let { vm.loadHistory(it) }
    }

    // Error dialog
    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { vm.clearError() },
            title = { Text("Hata") },
            text = { Text(errorMessage ?: "") },
            confirmButton = {
                TextButton(onClick = { vm.clearError() }) {
                    Text("Tamam", color = Primary)
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Header
        item {
            Column(
                modifier = Modifier
                    .padding(horizontal = Spacing.xl)
                    .padding(top = Spacing.sm, bottom = Spacing.lg)
            ) {
                Text(
                    text = "Giriş / Çıkış",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Giriş veya çıkış yapmak için QR kodu okutun ya da şifre girin",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Main content area
        item {
            when {
                showSuccess -> SuccessView(type = successType, modifier = Modifier.height(280.dp))

                showManualEntry -> ManualEntrySection(
                    code = manualCode,
                    onCodeChange = {
                        manualCode = it
                        vm.validateManualCode(it)
                    },
                    validationError = validationError,
                    isProcessing = isProcessing,
                    onCancel = {
                        vm.setShowManualEntry(false)
                        manualCode = ""
                    },
                    onSubmit = {
                        userId?.id?.let { vm.handleManualEntry(it, manualCode) }
                    }
                )

                !cameraPermission.status.isGranted -> CameraPermissionPlaceholder(
                    onRequest = { cameraPermission.launchPermissionRequest() },
                    modifier = Modifier.height(280.dp)
                )

                else -> QRScannerSection(
                    isScanning = isScanning,
                    isProcessing = isProcessing,
                    onCodeScanned = { code ->
                        userId?.id?.let { vm.handleScannedCode(it, code) }
                    },
                    onManualEntry = { vm.setShowManualEntry(true) }
                )
            }
        }

        // Entry history section header
        item {
            Row(
                modifier = Modifier
                    .padding(horizontal = Spacing.xl)
                    .padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                ) {
                Text(
                    text = "Giriş Geçmişi",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onNavigateToAllHistory) {
                    Text("Tümünü Gör", color = Primary, fontSize = 14.sp)
                }
            }
        }

        // History content
        when {
            isLoadingHistory -> item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = Primary, strokeWidth = 2.dp) }
            }

            historyError != null -> item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(32.dp))
                    Text(historyError ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { userId?.id?.let { vm.loadHistory(it) } }) {
                        Text("Tekrar Dene", color = Primary)
                    }
                }
            }

            entries.isEmpty() -> item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                    Text("Henüz giriş kaydı yok", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            else -> {
                items(entries.take(3)) { entry ->
                    EntryHistoryRow(entry = entry)
                    if (entry != entries.take(3).last()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = (Spacing.xl.value + 44 + 12).dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
                if (entries.size > 3) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.xl, vertical = 8.dp)
                                .background(Primary.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                .clickable { onNavigateToAllHistory() }
                                .padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tümünü Gör (${entries.size})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Primary
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = Primary, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// QR Scanner section (CameraX + ML Kit)
// ---------------------------------------------------------------------------

@Composable
private fun QRScannerSection(
    isScanning: Boolean,
    isProcessing: Boolean,
    onCodeScanned: (String) -> Unit,
    onManualEntry: () -> Unit
) {
    Column(
        modifier = Modifier.padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Camera preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl)
                .height(280.dp)
                .clip(RoundedCornerShape(24.dp))
        ) {
            CameraPreview(
                isScanning = isScanning,
                onCodeScanned = onCodeScanned,
                modifier = Modifier.fillMaxSize()
            )

            // QR frame overlay
            QRFrameOverlay(modifier = Modifier.align(Alignment.Center))

            // Processing overlay
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
                        Text("İşleniyor...", fontSize = 14.sp, color = Color.White)
                    }
                }
            }
        }

        // Manual entry button
        Row(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .border(1.dp, Primary, RoundedCornerShape(Radius.pill))
                .clickable(enabled = !isProcessing) { onManualEntry() }
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = Icons.Default.Keyboard, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
            Text("Manuel Kod Gir", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Primary)
        }
    }
}

// ---------------------------------------------------------------------------
// CameraX preview composable
// ---------------------------------------------------------------------------

@Composable
private fun CameraPreview(
    isScanning: Boolean,
    onCodeScanned: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasScanned by remember { mutableStateOf(false) }
    val executor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val cameraProviderRef = remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(isScanning) {
        if (isScanning) hasScanned = false
    }

    // Unbind camera BEFORE shutting down executor so no frames arrive on a dead executor.
    DisposableEffect(Unit) {
        onDispose {
            cameraProviderRef.value?.unbindAll()
            executor.shutdownNow()
        }
    }

    val coroutineScope = rememberCoroutineScope()

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            previewView.scaleType = PreviewView.ScaleType.FILL_CENTER

            coroutineScope.launch {
                try {
                    val cameraProvider = suspendCancellableCoroutine<ProcessCameraProvider> { cont ->
                        val future = ProcessCameraProvider.getInstance(ctx)
                        future.addListener({
                            try { cont.resume(future.get()) }
                            catch (e: Exception) { cont.resumeWithException(e) }
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                    cameraProviderRef.value = cameraProvider

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(executor) { imageProxy ->
                        if (!hasScanned && isScanning) {
                            processImageProxy(imageProxy) { code ->
                                if (!hasScanned) {
                                    hasScanned = true
                                    onCodeScanned(code)
                                }
                            }
                        } else {
                            imageProxy.close()
                        }
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (_: Exception) { }
                } catch (_: Exception) { }
            }

            previewView
        }
    )
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun processImageProxy(imageProxy: ImageProxy, onResult: (String) -> Unit) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val scanner = BarcodeScanning.getClient()
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                barcodes.firstOrNull()?.rawValue?.let { onResult(it) }
            }
            .addOnCompleteListener { imageProxy.close() }
    } else {
        imageProxy.close()
    }
}

// ---------------------------------------------------------------------------
// QR frame overlay with corner markers + scan line
// ---------------------------------------------------------------------------

@Composable
private fun QRFrameOverlay(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val scanLineY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanLine"
    )

    Box(
        modifier = modifier
            .size(width = 220.dp, height = 200.dp),
        contentAlignment = Alignment.TopStart
    ) {
        // Corner strokes
        QRCorner(modifier = Modifier.align(Alignment.TopStart))
        QRCorner(modifier = Modifier.align(Alignment.TopEnd).then(Modifier.padding(start = 0.dp).wrapContentSize().then(Modifier.size(20.dp))), rotation = 90f)
        QRCorner(modifier = Modifier.align(Alignment.BottomStart), rotation = -90f)
        QRCorner(modifier = Modifier.align(Alignment.BottomEnd), rotation = 180f)

        // Scan line
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(2.dp)
                .align(Alignment.CenterStart)
                .offset(x = 20.dp, y = (scanLineY * 160 - 80).dp)
                .background(Primary)
        )
    }
}

@Composable
private fun QRCorner(modifier: Modifier = Modifier, rotation: Float = 0f) {
    androidx.compose.foundation.Canvas(
        modifier = modifier
            .size(20.dp)
            .then(
                if (rotation != 0f)
                    Modifier.then(Modifier)
                else Modifier
            )
    ) {
        val sw = 3.dp.toPx()
        drawLine(Color.White, start = androidx.compose.ui.geometry.Offset(0f, 20.dp.toPx()), end = androidx.compose.ui.geometry.Offset(0f, 0f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(Color.White, start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(20.dp.toPx(), 0f), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

// ---------------------------------------------------------------------------
// Manual entry section
// ---------------------------------------------------------------------------

@Composable
private fun ManualEntrySection(
    code: String,
    onCodeChange: (String) -> Unit,
    validationError: String?,
    isProcessing: Boolean,
    onCancel: () -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = Spacing.xl)
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Code input
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Giriş Kodu",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            BasicTextField(
                value = code,
                onValueChange = onCodeChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (validationError != null) 2.dp else 1.dp,
                        color = if (validationError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                    .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
                decorationBox = { inner ->
                    if (code.isEmpty()) {
                        Text(
                            "Kodu girin...",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    inner()
                }
            )
            if (validationError != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(12.dp))
                    Text(validationError, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // Buttons row
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Cancel
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Radius.pill))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.pill))
                    .clickable { onCancel() }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("İptal", style = AppNormalMediumStyle, color = MaterialTheme.colorScheme.onSurface)
            }

            // Submit
            val canSubmit = code.isNotEmpty() && !isProcessing && validationError == null
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        Primary.copy(alpha = if (canSubmit) 1f else 0.6f),
                        RoundedCornerShape(Radius.pill)
                    )
                    .clickable(enabled = canSubmit) { onSubmit() }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Giriş Yap", style = AppNormalMediumStyle, color = Color.White)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Success view
// ---------------------------------------------------------------------------

@Composable
private fun SuccessView(type: String, modifier: Modifier = Modifier) {
    val isExit = type == "exit"
    val titleText = if (isExit) "Çıkış Başarılı!" else "Giriş Başarılı!"
    val subtitleText = if (isExit) "Tekrar görüşmek üzere!" else "Hoş geldiniz, iyi antrenmanlar!"

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(Color(0xFF4CAF50).copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(60.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(titleText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(subtitleText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------------------
// Camera permission placeholder
// ---------------------------------------------------------------------------

@Composable
private fun CameraPermissionPlaceholder(
    onRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("Kamera izni gerekli", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(
            "QR kod taramak için kamera erişimine izin verin",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .background(Primary, RoundedCornerShape(Radius.pill))
                .clickable { onRequest() }
                .padding(horizontal = 32.dp, vertical = 14.dp)
        ) {
            Text("İzin Ver", style = AppNormalMediumStyle, color = Color.White)
        }
    }
}

// ---------------------------------------------------------------------------
// Entry history row
// ---------------------------------------------------------------------------

@Composable
fun EntryHistoryRow(
    entry: EntryHistory,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Primary.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (entry.type.name == "QR") Icons.Default.QrCode else Icons.Default.Keyboard,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(entry.location, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Text(formatEntryDate(entry.date), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Text(entry.time, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun formatEntryDate(date: Date): String {
    val calendar = Calendar.getInstance()
    val today = Calendar.getInstance()
    calendar.time = date
    return when {
        calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                calendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Bugün"
        calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                calendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) - 1 -> "Dün"
        else -> {
            val fmt = SimpleDateFormat("d MMMM", Locale("tr"))
            fmt.format(date)
        }
    }
}

