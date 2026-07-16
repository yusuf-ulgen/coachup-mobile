package com.app.coachup.app.services

import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.app.coachup.app.config.GymConfig
import com.app.coachup.app.config.SupabaseConfig
import com.app.coachup.app.models.EntryHistory
import com.app.coachup.app.models.EntryType
import com.app.coachup.app.models.QREntry
import com.app.coachup.app.models.QREntryInsert
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Android equivalent of iOS QRScannerService.
 *
 * Uses CameraX for camera lifecycle management and ML Kit Barcode Scanning for
 * QR / barcode detection — replacing iOS AVFoundation + AVCaptureMetadataOutput.
 *
 * Also retains the legacy entry-history fetch (entry_logs) that was present in
 * the original Android stub, while adding full qr_entries support matching iOS.
 *
 * Architecture notes:
 *  - [bindCamera] wires a [PreviewView] to a [LifecycleOwner]. Must be called
 *    on the main thread from a Fragment or Activity.
 *  - [startScanning] / [stopScanning] control the image analysis pipeline.
 *  - [recordEntry] writes to the "qr_entries" table (matches iOS).
 *  - Camera permission check is provided by [hasCameraPermission].
 */
object QRScannerService {

    private val client get() = SupabaseConfig.client
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _lastScannedCode = MutableStateFlow<String?>(null)
    val lastScannedCode: StateFlow<String?> = _lastScannedCode.asStateFlow()

    /** Legacy entry-history list for UI display. */
    private val _entries = MutableStateFlow<List<EntryHistory>>(emptyList())
    val entries: StateFlow<List<EntryHistory>> = _entries.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // -------------------------------------------------------------------------
    // Camera internals
    // -------------------------------------------------------------------------

    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null

    /** Callback invoked on the main thread when a valid barcode is detected. */
    private var onCodeScanned: ((String) -> Unit)? = null

    // -------------------------------------------------------------------------
    // Camera Binding (CameraX + ML Kit)
    // -------------------------------------------------------------------------

    /**
     * Binds the CameraX pipeline to [lifecycleOwner].
     * Must be called on the main thread.
     *
     * @param context        Activity or Fragment context.
     * @param lifecycleOwner Lifecycle owner that controls camera release.
     * @param previewView    Surface used to render the camera preview.
     * @param onScanned      Callback invoked (main thread) when a valid QR/barcode
     *                       is detected. Debounce in the caller if needed to avoid
     *                       duplicate triggers.
     */
    @ExperimentalGetImage
    fun bindCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onScanned: (String) -> Unit
    ) {
        onCodeScanned = onScanned
        scope.launch {
            try {
                val provider = suspendCancellableCoroutine<ProcessCameraProvider> { cont ->
                    val future = ProcessCameraProvider.getInstance(context)
                    future.addListener({
                        try { cont.resume(future.get()) }
                        catch (e: Exception) { cont.resumeWithException(e) }
                    }, ContextCompat.getMainExecutor(context))
                }
                cameraProvider = provider

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            processImageProxy(imageProxy)
                        }
                    }

                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                    _isScanning.value = true
                }.onFailure {
                    _error.value = ScannerError.NoCameraAvailable.message
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _error.value = ScannerError.NoCameraAvailable.message
            }
        }
    }

    // -------------------------------------------------------------------------
    // Scanner Control
    // -------------------------------------------------------------------------

    fun startScanning() {
        imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
            processImageProxy(imageProxy)
        }
        _isScanning.value = true
    }

    fun stopScanning() {
        imageAnalysis?.clearAnalyzer()
        _isScanning.value = false
    }

    fun release() {
        stopScanning()
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    // -------------------------------------------------------------------------
    // Image Analysis via ML Kit
    // -------------------------------------------------------------------------

    @ExperimentalGetImage
    private fun processImageProxy(imageProxy: androidx.camera.core.ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )
        val scanner = BarcodeScanning.getClient()

        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    val rawValue = barcode.rawValue ?: continue
                    val isSupported = barcode.format in listOf(
                        Barcode.FORMAT_QR_CODE,
                        Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_PDF417
                    )
                    if (isSupported && validateQRCode(rawValue)) {
                        scope.launch {
                            _lastScannedCode.value = rawValue
                            onCodeScanned?.invoke(rawValue)
                        }
                        break
                    }
                }
            }
            .addOnFailureListener { e ->
                scope.launch { _error.value = e.message }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    // -------------------------------------------------------------------------
    // QR Code Validation — mirrors iOS validateQRCode
    // -------------------------------------------------------------------------

    /**
     * Returns true if [code] is non-empty and at least 6 characters long.
     * Matches iOS QRScannerService.validateQRCode(_:) exactly.
     */
    fun validateQRCode(code: String): Boolean = code.isNotEmpty() && code.length >= 6

    /**
     * Legacy alias used in entry-log flows. Returns an error string or null (valid).
     */
    fun validateCode(code: String): String? = when {
        code.isBlank()   -> "Kod boş olamaz"
        code.length < 6  -> "Kod en az 6 karakter olmalıdır"
        else             -> null
    }

    // -------------------------------------------------------------------------
    // Record Entry — mirrors iOS recordEntry(userId:code:)
    // -------------------------------------------------------------------------

    /**
     * Writes a row to "qr_entries" (matches iOS table name).
     * Also writes a row to "entry_logs" for backwards compat with the
     * existing Android implementation.
     */
    suspend fun recordEntry(
        userId: String,
        code: String,
        gymId: String = GymConfig.GYM_ID,
        method: String = "qr"
    ) {
        if (!validateQRCode(code)) throw ScannerError.InvalidCode

        val currentSlot = System.currentTimeMillis() / 30000

        var targetType = "entry"
        val parsedResult = parseQRCode(code)

        if (parsedResult != null) {
            targetType = parsedResult.type
            if (parsedResult.slot !in (currentSlot - 1)..(currentSlot + 1)) {
                throw ScannerError.Expired
            }
        } else {
            if (code.matches(Regex("^\\d{6}$"))) {
                val userGymId = UserService.currentProfile.value?.gymId ?: GymConfig.GYM_ID
                var matched = false
                for (s in (currentSlot - 1)..(currentSlot + 1)) {
                    if (buildManualCode(userGymId, s, "exit") == code) {
                        targetType = "exit"
                        matched = true
                        break
                    }
                }
                if (!matched) {
                    for (s in (currentSlot - 1)..(currentSlot + 1)) {
                        if (buildManualCode(userGymId, s, "entry") == code) {
                            targetType = "entry"
                            matched = true
                            break
                        }
                    }
                }
            }
        }

        val functionName = if (targetType == "exit") "qr-exit" else "qr-validate"
        
        try {
            val response = client.functions.invoke(functionName, body = buildJsonObject {
                put("qr", code)
                put("user_id", userId)
            })

            val jsonResponse = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val accepted = jsonResponse["accepted"]?.jsonPrimitive?.booleanOrNull ?: false
            if (!accepted) {
                val reason = jsonResponse["reason"]?.jsonPrimitive?.content ?: "unknown"
                val errorMessage = when (reason) {
                    "slot_mismatch" -> "QR Kodunun veya Şifrenin süresi dolmuş. Lütfen yeni kodu okutun."
                    "user_gym_not_found" -> "Kullanıcıya ait salon bulunamadı."
                    "no_open_entry" -> "Açık bir giriş kaydınız bulunmamaktadır. Çıkış yapamazsınız."
                    "invalid_format" -> "Geçersiz kod formatı."
                    "no_membership" -> "Aktif bir üyeliğiniz bulunmamaktadır."
                    else -> jsonResponse["error"]?.jsonPrimitive?.content ?: "Doğrulama başarısız oldu ($reason)"
                }
                throw ScannerError.ValidationError(errorMessage)
            }
            android.util.Log.d("QRScanner", "Edge function invoke OK - userId=$userId, type=$targetType")
        } catch (e: ScannerError) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("QRScanner", "Edge function invoke FAILED: ${e.message}", e)
            throw ScannerError.ValidationError(e.message ?: "İşlem gerçekleştirilemedi.")
        }

        _lastScannedCode.value = code
    }

    fun parseQRCode(code: String): QrParseResult? {
        val parts = code.split(":")
        if (parts.size < 3) return null
        if (parts[0] != "COACHUP_ENTRY" && parts[0] != "COACHUP_EXIT") return null
        val parsedGymId = parts[1]
        val slot = parts[2].toLongOrNull() ?: return null
        val type = if (parts[0] == "COACHUP_EXIT") "exit" else "entry"
        return QrParseResult(parsedGymId, slot, type)
    }

    fun buildManualCode(gymId: String, slot: Long, type: String = "entry"): String {
        if (gymId.isEmpty()) return "000000"
        val seed = "${if (type == "exit") "EXIT" else "ENTRY"}:$gymId:$slot"
        var h = 0x811C9DC5.toInt() // 2166136261
        val prime = 0x01000193 // 16777619
        for (char in seed) {
            h = h xor char.code
            h = h * prime
        }
        val unsignedH = h.toLong() and 0xFFFFFFFFL
        val codeNum = unsignedH % 1000000
        return codeNum.toString().padStart(6, '0')
    }

    data class QrParseResult(val gymId: String, val slot: Long, val type: String)

    // -------------------------------------------------------------------------
    // Entry History
    // -------------------------------------------------------------------------

    suspend fun fetchEntries(userId: String, limit: Int = 20) {
        _isLoading.value = true
        _error.value = null
        try {
            val records = client.from("qr_entries")
                .select {
                    filter { eq("user_id", userId) }
                    order("entry_date", Order.DESCENDING)
                    order("entry_time", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<QREntry>()

            _entries.value = records.map { it.toUiModel() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("QRScanner", "fetchEntries error: ${e.javaClass.simpleName}: ${e.message}", e)
            _error.value = "Giriş geçmişi yüklenemedi"
        } finally {
            _isLoading.value = false
        }
    }

    // -------------------------------------------------------------------------
    // Camera Permission
    // -------------------------------------------------------------------------

    fun hasCameraPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    // -------------------------------------------------------------------------
    // Error Types — mirrors iOS ScannerError enum
    // -------------------------------------------------------------------------

    sealed class ScannerError(message: String) : Exception(message) {
        object NoCameraAvailable : ScannerError("Kamera bulunamadı")
        object VideoInputFailed  : ScannerError("Kamera erişimi başarısız")
        object CannotAddInput    : ScannerError("Kamera input eklenemedi")
        object CannotAddOutput   : ScannerError("Metadata output eklenemedi")
        object InvalidCode       : ScannerError("Geçersiz QR kod")
        object NoGymAssociated   : ScannerError("Kullanıcı bir salona kayıtlı değil")
        class ValidationError(msg: String) : ScannerError(msg)
        object Expired           : ScannerError("QR Kodunun süresi dolmuş. Lütfen yeni kodu okutun.")
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private fun QREntry.toUiModel(): EntryHistory {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = try {
            dateFormat.parse(entryDate ?: "") ?: Date()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Date()
        }
        val displayTime = entryTime?.take(5) ?: "" // HH:MM
        return EntryHistory(
            id = id,
            date = date,
            time = displayTime,
            location = "Spor Salonu",
            type = EntryType.QR
        )
    }
}
