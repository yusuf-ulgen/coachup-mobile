package com.app.coachup.app.models

// ---------------------------------------------------------------------------
// Calendar UI-only models
// These are presentation layer models, not DB models.
// The database equivalent is ScheduledProgramDB in DatabaseModels.kt.
// ---------------------------------------------------------------------------

data class CalendarDay(
    val day: Int,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val hasEvent: Boolean
)

enum class ProgramType(val label: String) {
    CARDIO("Kardiyo"),
    STRENGTH("Güç"),
    FLEXIBILITY("Esneklik");

    companion object {
        fun fromCategory(cat: String?): ProgramType = when (cat) {
            "cardio"      -> CARDIO
            "flexibility" -> FLEXIBILITY
            else          -> STRENGTH
        }
    }
}

/**
 * Lightweight UI model derived from [ScheduledProgramDB].
 * Used to render calendar event chips without carrying the full joined payload.
 */
data class ScheduledProgramUi(
    val id: String,
    val title: String,
    val coach: String,
    val startTime: String,
    val endTime: String,
    val iconName: String,
    val type: ProgramType
)
