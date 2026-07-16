package com.app.coachup.app.navigation

import com.app.coachup.app.models.Exercise
import com.app.coachup.app.models.LocalCoach
import com.app.coachup.app.models.Training
import com.app.coachup.app.services.RecordAttempt
import com.app.coachup.app.services.RecordAttemptSet
import com.app.coachup.app.services.SummaryResult

/**
 * In-memory transit store for non-serialisable objects that must be passed
 * between navigation destinations.
 *
 * The typical usage pattern is:
 *  1. Caller deposits the object just before calling [navController.navigate].
 *  2. Destination reads and immediately clears the relevant entry on first composition.
 *
 * This avoids adding Parcelable / @Serializable boilerplate to domain/UI models and
 * keeps routing decoupled from serialisation concerns.
 *
 * Thread-safety: writes and reads happen exclusively on the main thread (Compose / NavHost),
 * so no synchronisation primitives are required.
 */
object NavigationStateHolder {

    // ─── Active workout ───────────────────────────────────────────────────────
    /**
     * The [Training] that the user selected before navigating to the active-workout
     * screen. Set by [TrainingScreen], consumed (and cleared) by [ActiveWorkoutScreen].
     */
    var pendingTraining: Training? = null

    // ─── Coach detail ─────────────────────────────────────────────────────────
    /**
     * The [LocalCoach] selected from the coaches list.
     * Set by [CoachesScreen], consumed (and cleared) by [CoachDetailScreen].
     */
    var pendingCoach: LocalCoach? = null

    // ─── Coach chat ───────────────────────────────────────────────────────────
    /**
     * The [LocalCoach] whose chat thread the user wants to open.
     * Set by [CoachDetailScreen], consumed (and cleared) by [CoachChatScreen].
     */
    var pendingChatCoach: LocalCoach? = null

    // ─── Record Attempt ───────────────────────────────────────────────────────
    var pendingRecordAttempt: RecordAttempt? = null
    var pendingRecordAttemptSets: List<RecordAttemptSet> = emptyList()
    var pendingRecordAttemptExercise: Exercise? = null
    var pendingRecordMeasureType: com.app.coachup.app.models.RecordMeasureType =
        com.app.coachup.app.models.RecordMeasureType.WEIGHT
    var pendingSummaryResult: SummaryResult? = null

    // ─── Result Detail ────────────────────────────────────────────────────────
    /** Exercise name pre-filled before navigating to ResultDetailScreen. */
    var pendingExerciseName: String? = null

    /** Selected membership plan id before navigating to RequestMembershipScreen. */
    var pendingMembershipPlanId: String? = null
}
