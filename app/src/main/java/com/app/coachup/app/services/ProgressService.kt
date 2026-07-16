package com.app.coachup.app.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.app.coachup.app.config.ApiLimits
import com.app.coachup.app.config.GymConfig
import com.app.coachup.app.config.SupabaseConfig.client
import com.app.coachup.app.models.ProgressLogInsert
import com.app.coachup.app.models.ProgressPhoto
import com.app.coachup.app.models.ProgressPhotoInsert
import com.app.coachup.app.models.UserProgressLog
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.max

/**
 * Android equivalent of iOS ProgressService.
 *
 * Mirrors tables: user_progress_logs, progress_photos.
 * Mirrors Supabase Storage bucket: progress-photos.
 */
object ProgressService {

    private const val STORAGE_BUCKET = "progress-photos"

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // -------------------------------------------------------------------------
    // Measurements
    // -------------------------------------------------------------------------

    suspend fun fetchProgressHistory(userId: String, limit: Int = 50): List<UserProgressLog> {
        return try {
            client.postgrest["user_progress_logs"]
                .select {
                    filter { eq("user_id", userId) }
                    order("created_at", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<UserProgressLog>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun getLatestMeasurement(userId: String): UserProgressLog? {
        return try {
            client.postgrest["user_progress_logs"]
                .select {
                    filter { eq("user_id", userId) }
                    order("measured_at", Order.DESCENDING)
                    limit(1)
                }
                .decodeList<UserProgressLog>()
                .firstOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    suspend fun logMeasurement(
        userId: String,
        logDate: java.time.LocalDate = java.time.LocalDate.now(),
        weight: Double? = null,
        bodyFat: Double? = null,
        chest: Double? = null,
        waist: Double? = null,
        hips: Double? = null,
        arms: Double? = null,
        legs: Double? = null,
        bicepsLeft: Double? = null,
        bicepsRight: Double? = null,
        thighLeft: Double? = null,
        thighRight: Double? = null,
        notes: String? = null
    ) {
        _isLoading.value = true
        try {
            val measuredAt = LocalDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_DATE_TIME)

            val profile = UserService.currentProfile.value
            val memberName = listOfNotNull(profile?.name, profile?.surname)
                .joinToString(" ")
                .ifBlank { "Üye" }

            val resolvedArms = arms ?: listOfNotNull(bicepsLeft, bicepsRight).takeIf { it.isNotEmpty() }?.average()
            val resolvedLegs = legs ?: listOfNotNull(thighLeft, thighRight).takeIf { it.isNotEmpty() }?.average()
            val bmi = calculateBmi(weight, profile?.height)

            val insert = ProgressLogInsert(
                userId = userId,
                gymId = resolveGymId(),
                weight = weight,
                bodyFatPercentage = bodyFat,
                chest = chest,
                waist = waist,
                hips = hips,
                bicepsLeft = bicepsLeft ?: resolvedArms,
                bicepsRight = bicepsRight,
                thighLeft = thighLeft ?: resolvedLegs,
                thighRight = thighRight,
                bmi = bmi,
                notes = notes,
                logDate = logDate.toString(),
                arms = resolvedArms,
                legs = resolvedLegs,
                measuredAt = measuredAt,
                source = "self",
                recordedByName = memberName
            )
            client.postgrest["user_progress_logs"].insert(insert)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e
        } finally {
            _isLoading.value = false
        }
    }

    private fun calculateBmi(weight: Double?, heightCm: Double?): Double? {
        if (weight == null || heightCm == null || heightCm <= 0) return null
        val heightM = heightCm / 100.0
        return weight / (heightM * heightM)
    }

    // -------------------------------------------------------------------------
    // Photos
    // -------------------------------------------------------------------------

    suspend fun fetchPhotos(
        userId: String,
        limit: Int = ApiLimits.PROGRESS_PHOTOS
    ): List<ProgressPhoto> {
        return try {
            client.postgrest["progress_photos"]
                .select {
                    filter { eq("user_id", userId) }
                    order("taken_at", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<ProgressPhoto>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Uploads a JPEG photo to the "progress-photos" Storage bucket and records
     * a row in progress_photos. The path mirrors the iOS convention:
     * {userId}/progress/{timestamp}.jpg
     */
    suspend fun uploadPhoto(
        userId: String,
        imageBytes: ByteArray,
        photoType: String,
        notes: String? = null,
        @Suppress("UNUSED_PARAMETER") replaceExisting: Boolean = false
    ) {
        val jpegBytes = withContext(Dispatchers.IO) {
            compressToJpeg(imageBytes)
        }
        uploadCompressedPhoto(userId, jpegBytes, photoType, notes)
    }

    suspend fun uploadPhotoFromUri(
        context: Context,
        userId: String,
        uri: Uri,
        photoType: String,
        notes: String? = null,
        @Suppress("UNUSED_PARAMETER") replaceExisting: Boolean = false
    ) {
        val jpegBytes = withContext(Dispatchers.IO) {
            compressUriToJpeg(context, uri)
        }
        uploadCompressedPhoto(userId, jpegBytes, photoType, notes)
    }

    private suspend fun uploadCompressedPhoto(
        userId: String,
        jpegBytes: ByteArray,
        photoType: String,
        notes: String?
    ) {
        _isLoading.value = true
        try {
            withContext(Dispatchers.IO) {
                deletePhotosOfType(userId, photoType)

                val fileName = "$userId/progress/${System.currentTimeMillis()}.jpg"

                try {
                    client.storage[STORAGE_BUCKET].upload(
                        path = fileName,
                        data = jpegBytes
                    ) {
                        upsert = true
                    }
                } catch (e: Exception) {
                    val message = e.message.orEmpty()
                    if (message.contains("Bucket not found", ignoreCase = true)) {
                        throw IllegalStateException(
                            "Fotoğraf depolama alanı (progress-photos) Supabase'de tanımlı değil. " +
                                "Lütfen supabase/migrations/20250625100000_progress_photos_storage.sql dosyasını çalıştırın."
                        )
                    }
                    throw e
                }

                val publicUrl = client.storage[STORAGE_BUCKET].publicUrl(fileName)
                val takenAt = LocalDateTime.now(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_DATE_TIME)

                val insert = ProgressPhotoInsert(
                    userId = userId,
                    gymId = resolveGymId(),
                    photoUrl = publicUrl,
                    photoType = photoType,
                    notes = notes,
                    takenAt = takenAt
                )

                client.postgrest["progress_photos"].insert(insert)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw mapUploadError(e)
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun deletePhoto(photoId: String, userId: String, photoUrl: String? = null) {
        photoUrl?.let { url ->
            try {
                deleteStorageFile(url)
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                if (!msg.contains("not found", ignoreCase = true) &&
                    !msg.contains("404", ignoreCase = true)
                ) {
                    throw e
                }
            }
        }
        client.postgrest["progress_photos"]
            .delete {
                filter {
                    eq("id", photoId)
                    eq("user_id", userId)
                }
            }
    }

    private suspend fun deletePhotosOfType(userId: String, photoType: String) {
        val existing = fetchPhotos(userId).filter { it.photoType == photoType }
        existing.forEach { photo ->
            runCatching {
                deletePhoto(photo.id, userId, photo.photoUrl)
            }
        }
    }

    private suspend fun deleteStorageFile(photoUrl: String) {
        val path = storagePathFromPublicUrl(photoUrl)
            ?: throw IllegalStateException("Eski fotoğraf yolu çözümlenemedi: $photoUrl")
        client.storage[STORAGE_BUCKET].delete(listOf(path))
    }

    private fun storagePathFromPublicUrl(photoUrl: String): String? {
        val decoded = URLDecoder.decode(photoUrl, StandardCharsets.UTF_8.name())
        val markers = listOf(
            "/object/public/$STORAGE_BUCKET/",
            "/object/authenticated/$STORAGE_BUCKET/",
            "/object/sign/$STORAGE_BUCKET/",
            "$STORAGE_BUCKET/"
        )
        for (marker in markers) {
            val index = decoded.indexOf(marker)
            if (index >= 0) {
                return decoded
                    .substring(index + marker.length)
                    .substringBefore('?')
                    .substringBefore('#')
                    .trim('/')
                    .takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun resolveGymId(): String? {
        if (UserService.isIndividualUser()) return null
        return UserService.currentProfile.value?.gymId ?: GymConfig.GYM_ID
    }

    private fun compressUriToJpeg(context: Context, uri: Uri, maxEdge: Int = 1600, quality: Int = 85): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decodeUriBounds(context, uri, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalArgumentException("Seçilen görsel okunamadı. Lütfen farklı bir fotoğraf deneyin.")
        }

        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = decodeUriBitmap(context, uri, decodeOpts)
            ?: throw IllegalArgumentException("Seçilen görsel okunamadı. Lütfen farklı bir fotoğraf deneyin.")

        return scaleAndCompressBitmap(decoded, maxEdge, quality)
    }

    private fun decodeUriBounds(context: Context, uri: Uri, options: BitmapFactory.Options) {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, options)
        }
    }

    private fun decodeUriBitmap(
        context: Context,
        uri: Uri,
        options: BitmapFactory.Options
    ): Bitmap? {
        return context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, options)
        }
    }

    private fun scaleAndCompressBitmap(bitmap: Bitmap, maxEdge: Int, quality: Int): ByteArray {
        val longest = max(bitmap.width, bitmap.height)
        val scaled = if (longest > maxEdge) {
            val scale = maxEdge.toFloat() / longest
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            ).also { if (it !== bitmap) bitmap.recycle() }
        } else {
            bitmap
        }

        return try {
            ByteArrayOutputStream().use { out ->
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                    throw IllegalStateException("Fotoğraf sıkıştırılamadı.")
                }
                out.toByteArray()
            }
        } finally {
            if (!scaled.isRecycled) scaled.recycle()
        }
    }

    private fun compressToJpeg(imageBytes: ByteArray, maxEdge: Int = 1600, quality: Int = 85): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalArgumentException("Seçilen görsel okunamadı. Lütfen farklı bir fotoğraf deneyin.")
        }

        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, decodeOpts)
            ?: throw IllegalArgumentException("Seçilen görsel okunamadı. Lütfen farklı bir fotoğraf deneyin.")

        return scaleAndCompressBitmap(decoded, maxEdge, quality)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        val longest = max(width, height)
        while (longest / sample > maxEdge * 2) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    private fun mapUploadError(e: Exception): Exception {
        val message = e.message.orEmpty()
        return when {
            message.contains("row-level security", ignoreCase = true) ->
                IllegalStateException("Fotoğraf kaydedilemedi: yetki hatası. Oturumu kapatıp tekrar giriş yapın.")
            message.contains("Bucket not found", ignoreCase = true) ->
                IllegalStateException("Fotoğraf depolama alanı bulunamadı.")
            message.contains("photo_type", ignoreCase = true) ||
                message.contains("progress_photos_photo_type_check", ignoreCase = true) ->
                IllegalStateException("Geçersiz fotoğraf tipi. Uygulamayı güncelleyin.")
            else -> e
        }
    }
}
