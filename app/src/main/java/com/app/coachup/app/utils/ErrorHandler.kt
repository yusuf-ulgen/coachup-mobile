package com.app.coachup.app.utils

import java.util.UUID

/**
 * Centralised error handling utility – mirrors iOS ErrorHandler exactly.
 *
 * Usage pattern:
 * ```kotlin
 * try {
 *     // ... operation
 * } catch (e: Exception) {
 *     val appError = ErrorHandler.parse(e)
 *     showError(appError.message)
 * }
 * ```
 */
object ErrorHandler {

    // -----------------------------------------------------------------------
    // Error Codes
    // -----------------------------------------------------------------------

    enum class ErrorCode(val rawValue: String) {
        // Authentication
        AUTH_INVALID_CREDENTIALS("AUTH_INVALID_CREDENTIALS"),
        AUTH_USER_NOT_FOUND("AUTH_USER_NOT_FOUND"),
        AUTH_SESSION_EXPIRED("AUTH_SESSION_EXPIRED"),
        AUTH_UNAUTHORIZED("AUTH_UNAUTHORIZED"),

        // Database
        DB_CONNECTION_FAILED("DB_CONNECTION_FAILED"),
        DB_QUERY_FAILED("DB_QUERY_FAILED"),
        DB_UNIQUE_VIOLATION("DB_UNIQUE_VIOLATION"),
        DB_FOREIGN_KEY_VIOLATION("DB_FOREIGN_KEY_VIOLATION"),
        DB_CONSTRAINT_VIOLATION("DB_CONSTRAINT_VIOLATION"),
        DB_NOT_FOUND("DB_NOT_FOUND"),

        // Validation
        VALIDATION_INVALID_INPUT("VALIDATION_INVALID_INPUT"),
        VALIDATION_MISSING_FIELD("VALIDATION_MISSING_FIELD"),
        VALIDATION_INVALID_FORMAT("VALIDATION_INVALID_FORMAT"),

        // Permission
        PERMISSION_DENIED("PERMISSION_DENIED"),
        PERMISSION_INSUFFICIENT_ROLE("PERMISSION_INSUFFICIENT_ROLE"),

        // Network
        NETWORK_NO_CONNECTION("NETWORK_NO_CONNECTION"),
        NETWORK_TIMEOUT("NETWORK_TIMEOUT"),
        NETWORK_UNKNOWN("NETWORK_UNKNOWN"),

        // QR Scanner
        QR_INVALID_CODE("QR_INVALID_CODE"),
        QR_CAMERA_UNAVAILABLE("QR_CAMERA_UNAVAILABLE"),
        QR_SCAN_FAILED("QR_SCAN_FAILED"),

        // Generic
        UNKNOWN("UNKNOWN");
    }

    // -----------------------------------------------------------------------
    // AppError
    // -----------------------------------------------------------------------

    /**
     * Typed, user-displayable error – mirrors iOS AppError.
     *
     * @property id            Unique identifier for list rendering / Snackbar deduplication.
     * @property code          Structured error code for programmatic handling.
     * @property message       Localised Turkish message for display to the user.
     * @property originalError The underlying [Throwable] preserved for logging.
     */
    data class AppError(
        val id: String = UUID.randomUUID().toString(),
        val code: ErrorCode,
        override val message: String,
        val originalError: Throwable? = null
    ) : Exception(message, originalError) {

        companion object {
            operator fun invoke(
                code: ErrorCode,
                message: String? = null,
                originalError: Throwable? = null
            ): AppError = AppError(
                id = UUID.randomUUID().toString(),
                code = code,
                message = message ?: defaultMessage(code),
                originalError = originalError
            )

            fun defaultMessage(code: ErrorCode): String = when (code) {
                // Auth
                ErrorCode.AUTH_INVALID_CREDENTIALS -> "Kullanıcı adı veya şifre hatalı"
                ErrorCode.AUTH_USER_NOT_FOUND -> "Kullanıcı bulunamadı"
                ErrorCode.AUTH_SESSION_EXPIRED -> "Oturumunuz sona erdi. Lütfen tekrar giriş yapın"
                ErrorCode.AUTH_UNAUTHORIZED -> "Bu işlem için yetkiniz yok"

                // Database
                ErrorCode.DB_CONNECTION_FAILED -> "Veritabanı bağlantısı başarısız"
                ErrorCode.DB_QUERY_FAILED -> "Veri sorgulama başarısız"
                ErrorCode.DB_UNIQUE_VIOLATION -> "Bu kayıt zaten mevcut"
                ErrorCode.DB_FOREIGN_KEY_VIOLATION -> "İlişkili veri bulunamadı"
                ErrorCode.DB_CONSTRAINT_VIOLATION -> "Veri kısıtlaması ihlali"
                ErrorCode.DB_NOT_FOUND -> "Kayıt bulunamadı"

                // Validation
                ErrorCode.VALIDATION_INVALID_INPUT -> "Geçersiz girdi"
                ErrorCode.VALIDATION_MISSING_FIELD -> "Gerekli alan eksik"
                ErrorCode.VALIDATION_INVALID_FORMAT -> "Geçersiz format"

                // Permission
                ErrorCode.PERMISSION_DENIED -> "Erişim reddedildi"
                ErrorCode.PERMISSION_INSUFFICIENT_ROLE -> "Bu işlem için yetkiniz yok"

                // Network
                ErrorCode.NETWORK_NO_CONNECTION -> "İnternet bağlantısı yok"
                ErrorCode.NETWORK_TIMEOUT -> "Bağlantı zaman aşımına uğradı"
                ErrorCode.NETWORK_UNKNOWN -> "Ağ hatası"

                // QR Scanner
                ErrorCode.QR_INVALID_CODE -> "Geçersiz QR kod"
                ErrorCode.QR_CAMERA_UNAVAILABLE -> "Kamera kullanılamıyor"
                ErrorCode.QR_SCAN_FAILED -> "QR kod okuma başarısız"

                // Generic
                ErrorCode.UNKNOWN -> "Bilinmeyen bir hata oluştu"
            }
        }
    }

    // -----------------------------------------------------------------------
    // Error Parsing
    // -----------------------------------------------------------------------

    /**
     * Parses any [Throwable] into a typed [AppError].
     *
     * Strategy (matches iOS logic):
     *  1. If already an [AppError], return as-is.
     *  2. Inspect [Throwable.message] for known Supabase / Postgres keywords.
     *  3. Fall through to [ErrorCode.UNKNOWN].
     */
    fun parse(error: Throwable): AppError {
        if (error is AppError) return error

        val msg = (error.message ?: "").lowercase()

        // Auth
        if (msg.contains("invalid login credentials")) {
            return AppError(ErrorCode.AUTH_INVALID_CREDENTIALS, originalError = error)
        }
        if (msg.contains("user not found")) {
            return AppError(ErrorCode.AUTH_USER_NOT_FOUND, originalError = error)
        }
        if (msg.contains("jwt expired") || msg.contains("session expired")) {
            return AppError(ErrorCode.AUTH_SESSION_EXPIRED, originalError = error)
        }

        // Database
        if (msg.contains("unique constraint") || msg.contains("duplicate key")) {
            return AppError(
                code = ErrorCode.DB_UNIQUE_VIOLATION,
                message = "Bu kayıt zaten mevcut",
                originalError = error
            )
        }
        if (msg.contains("foreign key constraint")) {
            return AppError(ErrorCode.DB_FOREIGN_KEY_VIOLATION, originalError = error)
        }
        if (msg.contains("check constraint")) {
            return AppError(ErrorCode.DB_CONSTRAINT_VIOLATION, originalError = error)
        }
        if (msg.contains("not found") || msg.contains("no rows")) {
            return AppError(ErrorCode.DB_NOT_FOUND, originalError = error)
        }

        // Network
        if (msg.contains("network") || msg.contains("connection")) {
            return AppError(ErrorCode.NETWORK_NO_CONNECTION, originalError = error)
        }
        if (msg.contains("timeout")) {
            return AppError(ErrorCode.NETWORK_TIMEOUT, originalError = error)
        }

        return AppError(
            code = ErrorCode.UNKNOWN,
            message = error.message ?: AppError.defaultMessage(ErrorCode.UNKNOWN),
            originalError = error
        )
    }

    /**
     * Returns a user-friendly Turkish message for any [Throwable].
     *
     * Convenience wrapper around [parse] for call-sites that only need the
     * display string and do not require the structured [AppError].
     */
    fun userFriendlyMessage(error: Throwable): String = parse(error).message
}
