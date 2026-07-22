package com.app.coachup.app.models

// ---------------------------------------------------------------------------
// Coach UI enums and presentation models
// DB models (Coach, CoachSchedule, CoachMessage) are in DatabaseModels.kt.
// ---------------------------------------------------------------------------

enum class CoachGender(val dbValue: String, val label: String) {
    MALE("Erkek", "Erkek"),
    FEMALE("Kadın", "Kadın");

    companion object {
        fun fromDbValue(v: String?): CoachGender =
            if (v?.lowercase() == "kadın" || v?.lowercase() == "female") FEMALE else MALE
    }
}

enum class GenderFilter(val label: String) {
    ALL("Hepsi"),
    MALE("Erkek"),
    FEMALE("Kadın")
}

/**
 * UI presentation model derived from [Coach].
 * Carries only the fields the coaches screen needs to render,
 * with typed [CoachGender] instead of a raw String.
 */
data class LocalCoach(
    val id: String,
    val name: String,
    val surname: String,
    val gender: CoachGender,
    val specialty: String?,
    val specializations: List<String>,
    val certifications: List<String>,
    val bio: String?,
    val rating: Double,
    val experienceYears: Int,
    val profileImageUrl: String?
) {
    val fullName get() = "$name $surname"

    companion object {
        fun from(c: Coach) = LocalCoach(
            id = c.id,
            name = c.name,
            surname = c.surname,
            gender = CoachGender.fromDbValue(c.gender),
            specialty = c.specialty,
            specializations = c.specializations ?: c.specialty?.let { listOf(it) } ?: emptyList(),
            certifications = c.certifications ?: emptyList(),
            bio = c.bio,
            rating = c.rating,
            experienceYears = c.experienceYears,
            profileImageUrl = c.profileImageUrl
        )
    }
}

/**
 * UI slot model derived from [CoachSchedule].
 */
data class LessonSlot(
    val id: String,
    val scheduleId: String?,
    val startTime: String,
    val endTime: String,
    val lessonName: String?,
    val maxCapacity: Int,
    val currentBookings: Int
) {
    val hasAvailability get() = currentBookings < maxCapacity

    companion object {
        fun from(s: CoachSchedule) = LessonSlot(
            id = "${s.id}_ui",
            scheduleId = s.id,
            startTime = s.startTime.take(5),
            endTime = s.endTime.take(5),
            lessonName = if (s.isAvailable) null else "Dolu",
            maxCapacity = s.maxCapacity ?: 1,
            currentBookings = s.currentBookings ?: 0
        )
    }
}
