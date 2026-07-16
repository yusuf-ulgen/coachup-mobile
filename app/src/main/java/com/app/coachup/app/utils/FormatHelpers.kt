package com.app.coachup.app.utils

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Formatting utilities – mirrors iOS FormatHelpers exactly.
 *
 * All number formatting uses the `tr_TR` locale so that:
 *  - Decimal separator is a comma   (`,`)
 *  - Grouping separator is a period (`.`)
 *  …matching the iOS output e.g. "1.234,56 ₺".
 */
object FormatHelpers {

    private val trLocale = Locale("tr", "TR")

    // -----------------------------------------------------------------------
    // Currency Formatting
    // -----------------------------------------------------------------------

    /**
     * Formats [amount] as Turkish Lira with trailing symbol.
     *
     * Examples:
     *  - `formatCurrency(149.0)` → `"149,00 ₺"`
     *  - `formatCurrency(149.0, showSymbol = false)` → `"149,00"`
     *
     * @param amount                 Value to format.
     * @param showSymbol             Append ₺ symbol (default true).
     * @param minimumFractionDigits  Minimum decimal digits (default 2).
     * @param maximumFractionDigits  Maximum decimal digits (default 2).
     */
    fun formatCurrency(
        amount: Double,
        showSymbol: Boolean = true,
        minimumFractionDigits: Int = 2,
        maximumFractionDigits: Int = 2
    ): String {
        val formatter = buildDecimalFormatter(
            minFraction = minimumFractionDigits,
            maxFraction = maximumFractionDigits
        )
        val formatted = formatter.format(amount)
        return if (showSymbol) "$formatted ₺" else formatted
    }

    /**
     * Formats [amount] as Turkish Lira with a leading symbol.
     *
     * Example: `formatCurrencyWithPrefix(149.0)` → `"₺149,00"`
     */
    fun formatCurrencyWithPrefix(amount: Double): String {
        val formatter = buildDecimalFormatter(minFraction = 2, maxFraction = 2)
        return "₺${formatter.format(amount)}"
    }

    // -----------------------------------------------------------------------
    // Number Formatting
    // -----------------------------------------------------------------------

    /**
     * Formats [number] with Turkish thousand separators.
     *
     * Example: `formatNumber(1_234_567)` → `"1.234.567"`
     */
    fun formatNumber(number: Int): String {
        val nf = NumberFormat.getNumberInstance(trLocale)
        return nf.format(number)
    }

    /**
     * Formats [number] as a decimal with the given [decimalPlaces].
     *
     * Example: `formatDecimal(123.4567)` → `"123,46"`
     */
    fun formatDecimal(number: Double, decimalPlaces: Int = 2): String {
        val formatter = buildDecimalFormatter(minFraction = decimalPlaces, maxFraction = decimalPlaces)
        return formatter.format(number)
    }

    // -----------------------------------------------------------------------
    // Percentage Formatting
    // -----------------------------------------------------------------------

    /**
     * Formats a value as a percentage string.
     *
     * @param value                Value to format.
     * @param isAlreadyPercentage  If false (default), multiplies by 100 first.
     * @param decimalPlaces        Decimal precision (default 0).
     *
     * Examples:
     *  - `formatPercentage(0.75)` → `"75%"`
     *  - `formatPercentage(75.0, isAlreadyPercentage = true)` → `"75%"`
     */
    fun formatPercentage(
        value: Double,
        isAlreadyPercentage: Boolean = false,
        decimalPlaces: Int = 0
    ): String {
        val percentValue = if (isAlreadyPercentage) value else value * 100
        val formatter = buildDecimalFormatter(minFraction = decimalPlaces, maxFraction = decimalPlaces)
        return "${formatter.format(percentValue)}%"
    }

    // -----------------------------------------------------------------------
    // Date Formatting
    // -----------------------------------------------------------------------

    /**
     * Formats [date] using the Turkish locale with the given [style].
     *
     * [style] maps to [java.text.DateFormat] style constants:
     *  - [DateStyle.SHORT]  → "19.03.2026"
     *  - [DateStyle.MEDIUM] → "19 Mar 2026"  (default)
     *  - [DateStyle.LONG]   → "19 Mart 2026"
     */
    fun formatDate(date: Date, style: DateStyle = DateStyle.MEDIUM): String {
        val javaStyle = style.toJavaStyle()
        val formatter = java.text.DateFormat.getDateInstance(javaStyle, trLocale)
        return formatter.format(date)
    }

    /**
     * Formats [date] with both date and time components.
     *
     * @param dateStyle  Date portion style (default [DateStyle.MEDIUM]).
     * @param timeStyle  Time portion style (default [DateStyle.SHORT]).
     */
    fun formatDateTime(
        date: Date,
        dateStyle: DateStyle = DateStyle.MEDIUM,
        timeStyle: DateStyle = DateStyle.SHORT
    ): String {
        val formatter = java.text.DateFormat.getDateTimeInstance(
            dateStyle.toJavaStyle(),
            timeStyle.toJavaStyle(),
            trLocale
        )
        return formatter.format(date)
    }

    /**
     * Formats [date] using a custom [format] pattern string.
     *
     * Example: `formatDateCustom(date, "dd MMMM yyyy")` → `"19 Mart 2026"`
     */
    fun formatDateCustom(date: Date, format: String): String {
        val formatter = SimpleDateFormat(format, trLocale)
        return formatter.format(date)
    }

    /**
     * Formats [date] as `"HH:mm"` – used for message timestamps.
     *
     * Example: `formatTime(date)` → `"14:30"`
     */
    fun formatTime(date: Date): String = formatDateCustom(date, "HH:mm")

    // -----------------------------------------------------------------------
    // Duration Formatting
    // -----------------------------------------------------------------------

    /**
     * Formats a duration in [seconds] as `"H:MM:SS"` or `"MM:SS"`.
     *
     * Examples:
     *  - `formatDuration(83)` → `"01:23"`
     *  - `formatDuration(3723)` → `"1:02:03"`
     */
    fun formatDuration(seconds: Int): String {
        val absSeconds = abs(seconds)
        val hours = absSeconds / 3600
        val minutes = (absSeconds % 3600) / 60
        val secs = absSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, secs)
        } else {
            "%02d:%02d".format(minutes, secs)
        }
    }

    // -----------------------------------------------------------------------
    // File Size Formatting
    // -----------------------------------------------------------------------

    /**
     * Formats [bytes] as a human-readable file size string.
     *
     * Examples:
     *  - `formatFileSize(1_536)` → `"1,5 KB"`
     *  - `formatFileSize(1_572_864)` → `"1,5 MB"`
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
            bytes >= 1_024L -> "%.1f KB".format(bytes / 1_024.0)
            else -> "$bytes B"
        }.replace(".", ",") // align decimal separator to tr_TR convention
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private fun buildDecimalFormatter(minFraction: Int, maxFraction: Int): DecimalFormat {
        val symbols = DecimalFormatSymbols(trLocale).apply {
            decimalSeparator = ','
            groupingSeparator = '.'
        }
        val pattern = buildString {
            append("#,##0")
            if (maxFraction > 0) {
                append(".")
                repeat(minFraction) { append("0") }
                repeat(maxFraction - minFraction) { append("#") }
            }
        }
        return DecimalFormat(pattern, symbols).also {
            it.minimumFractionDigits = minFraction
            it.maximumFractionDigits = maxFraction
        }
    }

    // -----------------------------------------------------------------------
    // DateStyle enum – platform-agnostic mirror of iOS DateFormatter.Style
    // -----------------------------------------------------------------------

    enum class DateStyle {
        SHORT, MEDIUM, LONG, FULL;

        fun toJavaStyle(): Int = when (this) {
            SHORT -> java.text.DateFormat.SHORT
            MEDIUM -> java.text.DateFormat.MEDIUM
            LONG -> java.text.DateFormat.LONG
            FULL -> java.text.DateFormat.FULL
        }
    }
}
