package com.app.coachup.app.utils

import java.net.MalformedURLException
import java.net.URL
import java.util.Date

/**
 * Input validation utilities – mirrors iOS Validators exactly.
 *
 * Every function returns a [ValidationResult] so callers can branch on
 * [ValidationResult.isValid] or extract [ValidationResult.errorMessage]
 * for inline error display.
 */
object Validators {

    // -----------------------------------------------------------------------
    // Validation Result
    // -----------------------------------------------------------------------

    sealed class ValidationResult {
        /** The input passed all validation rules. */
        data object Valid : ValidationResult()

        /** The input failed; [message] contains a human-readable Turkish error. */
        data class Invalid(val message: String) : ValidationResult()

        val isValid: Boolean get() = this is Valid

        val errorMessage: String?
            get() = (this as? Invalid)?.message
    }

    // -----------------------------------------------------------------------
    // QR Code Validation
    // -----------------------------------------------------------------------

    /**
     * Validates a scanned or manually-entered QR code.
     *
     * Rules (matching iOS):
     *  - Non-empty after trimming.
     *  - Length between 6 and 100 characters.
     *  - Only alphanumeric chars, hyphens, and underscores allowed.
     */
    fun validateQRCode(code: String): ValidationResult {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return ValidationResult.Invalid("Kod boş olamaz")
        if (trimmed.length < 6) return ValidationResult.Invalid("Kod en az 6 karakter olmalıdır")
        if (trimmed.length > 100) return ValidationResult.Invalid("Kod çok uzun")
        if (!trimmed.matches(Regex("[A-Za-z0-9_-]+"))) {
            return ValidationResult.Invalid("Kod geçersiz karakterler içeriyor")
        }
        return ValidationResult.Valid
    }

    // -----------------------------------------------------------------------
    // Email Validation
    // -----------------------------------------------------------------------

    /**
     * Validates an e-mail address.
     *
     * Regex mirrors the iOS implementation: RFC-5321 compliant local part +
     * domain with a 2–64 character TLD.
     */
    fun validateEmail(email: String): ValidationResult {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) return ValidationResult.Invalid("E-posta boş olamaz")

        val regex = Regex("[A-Z0-9a-z._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,64}")
        if (!regex.matches(trimmed)) {
            return ValidationResult.Invalid("Geçerli bir e-posta adresi giriniz")
        }
        return ValidationResult.Valid
    }

    // -----------------------------------------------------------------------
    // Phone Validation  (Turkey)
    // -----------------------------------------------------------------------

    /**
     * Validates a Turkish phone number.
     *
     * Accepted formats (digits only after stripping non-digit chars):
     *  - 10 digits starting with `05`  (e.g. 0532 123 45 67)
     *  - 12 digits starting with `905` (e.g. +90 532 123 45 67)
     */
    fun validatePhone(phone: String): ValidationResult {
        val trimmed = phone.trim()
        if (trimmed.isEmpty()) return ValidationResult.Invalid("Telefon numarası boş olamaz")

        val digitsOnly = trimmed.filter { it.isDigit() }
        val isValid = (digitsOnly.length == 10 && digitsOnly.startsWith("05")) ||
                (digitsOnly.length == 12 && digitsOnly.startsWith("905"))

        return if (isValid) ValidationResult.Valid
        else ValidationResult.Invalid("Geçerli bir telefon numarası giriniz (05XX XXX XX XX)")
    }

    // -----------------------------------------------------------------------
    // Password Validation
    // -----------------------------------------------------------------------

    /**
     * Validates a password against security requirements.
     *
     * Rules:
     *  - Non-empty.
     *  - Minimum 8 characters.
     *  - At least one uppercase letter.
     *  - At least one lowercase letter.
     *  - At least one digit.
     */
    fun validatePassword(password: String): ValidationResult {
        if (password.isEmpty()) return ValidationResult.Invalid("Şifre boş olamaz")
        if (password.length < 8) return ValidationResult.Invalid("Şifre en az 8 karakter olmalıdır")
        if (!password.any { it.isUpperCase() }) {
            return ValidationResult.Invalid("Şifre en az bir büyük harf içermelidir")
        }
        if (!password.any { it.isLowerCase() }) {
            return ValidationResult.Invalid("Şifre en az bir küçük harf içermelidir")
        }
        if (!password.any { it.isDigit() }) {
            return ValidationResult.Invalid("Şifre en az bir rakam içermelidir")
        }
        return ValidationResult.Valid
    }

    // -----------------------------------------------------------------------
    // Price Validation
    // -----------------------------------------------------------------------

    /**
     * Validates that [price] falls within [[min], [max]].
     *
     * @param price  The value to check.
     * @param min    Lower bound (default 0).
     * @param max    Upper bound (default 1 000 000).
     */
    fun validatePrice(
        price: Double,
        min: Double = 0.0,
        max: Double = 1_000_000.0
    ): ValidationResult {
        if (price < min) return ValidationResult.Invalid("Fiyat ${FormatHelpers.formatCurrency(min)} değerinden az olamaz")
        if (price > max) return ValidationResult.Invalid("Fiyat ${FormatHelpers.formatCurrency(max)} değerinden fazla olamaz")
        return ValidationResult.Valid
    }

    // -----------------------------------------------------------------------
    // Positive Number Validation
    // -----------------------------------------------------------------------

    /**
     * Validates that [number] is strictly positive (> 0).
     *
     * @param number     The integer to check.
     * @param fieldName  Human-readable field name used in the error message.
     */
    fun validatePositiveNumber(number: Int, fieldName: String = "Değer"): ValidationResult {
        if (number <= 0) return ValidationResult.Invalid("$fieldName pozitif bir sayı olmalıdır")
        return ValidationResult.Valid
    }

    // -----------------------------------------------------------------------
    // Percentage Validation
    // -----------------------------------------------------------------------

    /**
     * Validates that [percentage] is within 0–100 (inclusive).
     */
    fun validatePercentage(percentage: Double): ValidationResult {
        if (percentage < 0.0 || percentage > 100.0) {
            return ValidationResult.Invalid("Yüzde değeri 0-100 arasında olmalıdır")
        }
        return ValidationResult.Valid
    }

    // -----------------------------------------------------------------------
    // Date Validation
    // -----------------------------------------------------------------------

    /**
     * Validates that [date] is strictly in the future.
     *
     * @param date       The date to check.
     * @param fieldName  Human-readable field name used in the error message.
     */
    fun validateFutureDate(date: Date, fieldName: String = "Tarih"): ValidationResult {
        if (!date.after(Date())) return ValidationResult.Invalid("$fieldName gelecek bir tarih olmalıdır")
        return ValidationResult.Valid
    }

    /**
     * Validates that [date] is strictly in the past.
     *
     * @param date       The date to check.
     * @param fieldName  Human-readable field name used in the error message.
     */
    fun validatePastDate(date: Date, fieldName: String = "Tarih"): ValidationResult {
        if (!date.before(Date())) return ValidationResult.Invalid("$fieldName geçmiş bir tarih olmalıdır")
        return ValidationResult.Valid
    }

    // -----------------------------------------------------------------------
    // Text Length Validation
    // -----------------------------------------------------------------------

    /**
     * Validates that [text] (after trimming) satisfies optional [min] and [max]
     * character-count constraints.
     *
     * @param text       Input string.
     * @param min        Optional minimum length (inclusive).
     * @param max        Optional maximum length (inclusive).
     * @param fieldName  Human-readable field name used in error messages.
     */
    fun validateTextLength(
        text: String,
        min: Int? = null,
        max: Int? = null,
        fieldName: String = "Metin"
    ): ValidationResult {
        val trimmed = text.trim()
        if (min != null && trimmed.length < min) {
            return ValidationResult.Invalid("$fieldName en az $min karakter olmalıdır")
        }
        if (max != null && trimmed.length > max) {
            return ValidationResult.Invalid("$fieldName en fazla $max karakter olabilir")
        }
        return ValidationResult.Valid
    }

    // -----------------------------------------------------------------------
    // URL Validation
    // -----------------------------------------------------------------------

    /**
     * Validates that [urlString] is a well-formed URL with a scheme and host.
     */
    fun validateURL(urlString: String): ValidationResult {
        val trimmed = urlString.trim()
        if (trimmed.isEmpty()) return ValidationResult.Invalid("URL boş olamaz")
        return try {
            val url = URL(trimmed)
            if (url.protocol.isNullOrBlank() || url.host.isNullOrBlank()) {
                ValidationResult.Invalid("Geçerli bir URL giriniz")
            } else {
                ValidationResult.Valid
            }
        } catch (e: MalformedURLException) {
            ValidationResult.Invalid("Geçerli bir URL giriniz")
        }
    }
}
