package com.app.coachup.app.utils

/** HH:mm — saniye ve milisaniye gösterilmez. */
fun formatTimeHm(time: String?): String {
    if (time.isNullOrBlank()) return ""
    val trimmed = time.trim()
    val timePart = trimmed.substringAfter('T', trimmed).substringBefore('+').substringBefore('Z')
    val match = Regex("""(\d{1,2}):(\d{2})""").find(timePart)
    if (match != null) {
        val (hourText, minuteText) = match.destructured
        val hour = hourText.toIntOrNull() ?: return timePart.take(5)
        val minute = minuteText.toIntOrNull() ?: 0
        return "%02d:%02d".format(hour, minute)
    }
    return timePart.take(5)
}

/** Saat:dakika seçenekleri (30 dk aralık). */
fun halfHourTimeSlots(fromHour: Int = 6, toHour: Int = 22): List<String> =
    (fromHour..toHour).flatMap { hour ->
        listOf("%02d:00".format(hour), "%02d:30".format(hour))
    }
