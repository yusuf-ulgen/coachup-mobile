package com.app.coachup.app.services

import com.app.coachup.app.config.SupabaseConfig
import com.app.coachup.app.models.Exercise
import com.app.coachup.app.models.PersonalRecord
import com.app.coachup.app.models.PersonalRecordInsert
import com.app.coachup.app.models.RecordExercise
import com.app.coachup.app.models.RecordMeasureType
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.Locale
import java.util.UUID

// ---------------------------------------------------------------------------
// Models — mirror iOS RecordAttemptService.swift models
// ---------------------------------------------------------------------------

@Serializable
data class RecordAttempt(
    @SerialName("id")            val id: String,
    @SerialName("user_id")       val userId: String,
    @SerialName("exercise_id")   val exerciseId: String,
    @SerialName("target_weight") val targetWeight: Double,
    @SerialName("target_reps")   val targetReps: Int,
    @SerialName("estimated_1rm") val estimated1RM: Double? = null,
    @SerialName("status")        val status: String = "in_progress",
    @SerialName("success")       val success: Boolean = false,
    @SerialName("notes")         val notes: String? = null,
    @SerialName("source_device") val sourceDevice: String? = null,
    @SerialName("started_at")    val startedAt: String? = null,
    @SerialName("completed_at")  val completedAt: String? = null,
    @SerialName("created_at")    val createdAt: String = ""
)

@Serializable
data class RecordAttemptSet(
    @SerialName("id")                val id: String,
    @SerialName("attempt_id")        val attemptId: String,
    @SerialName("user_id")           val userId: String,
    @SerialName("set_index")         val setIndex: Int,
    @SerialName("set_type")          val setTypeRaw: String = "warmup",
    @SerialName("prescribed_weight") var prescribedWeight: Double? = null,
    @SerialName("prescribed_reps")   val prescribedReps: Int? = null,
    @SerialName("actual_weight")     var actualWeight: Double? = null,
    @SerialName("actual_reps")       var actualReps: Int? = null,
    @SerialName("rpe")               var rpe: Int? = null,
    @SerialName("rest_seconds")      var restSeconds: Int? = null,
    @SerialName("is_completed")      var isCompleted: Boolean = false,
    @SerialName("completed_at")      var completedAt: String? = null,
    @SerialName("notes")             var notes: String? = null,
    @SerialName("created_at")        val createdAt: String = ""
) {
    val setType: AttemptSetType get() = AttemptSetType.fromRaw(setTypeRaw)
}

enum class AttemptSetType(val raw: String) {
    WARMUP("warmup"),
    MAIN("main");

    companion object {
        fun fromRaw(v: String): AttemptSetType =
            entries.firstOrNull { it.raw == v } ?: WARMUP
    }
}

data class SummaryResult(
    val success: Boolean,
    val newPersonalRecord: Boolean,
    val overrideMeasureType: com.app.coachup.app.models.RecordMeasureType? = null,
    val overrideWeight: Double? = null,
    val overrideReps: Int? = null,
    val overrideDurationSeconds: Int? = null
)

// ---------------------------------------------------------------------------
// Insert / Update DTOs
// ---------------------------------------------------------------------------

@Serializable
data class RecordAttemptInsert(
    @SerialName("id")            val id: String,
    @SerialName("user_id")       val userId: String,
    @SerialName("exercise_id")   val exerciseId: String,
    @SerialName("target_weight") val targetWeight: Double,
    @SerialName("target_reps")   val targetReps: Int,
    @SerialName("estimated_1rm") val estimated1RM: Double,
    @SerialName("status")        val status: String,
    @SerialName("success")       val success: Boolean,
    @SerialName("notes")         val notes: String? = null,
    @SerialName("source_device") val sourceDevice: String? = null,
    @SerialName("started_at")    val startedAt: String
)

@Serializable
data class RecordAttemptSetInsert(
    @SerialName("id")                val id: String,
    @SerialName("attempt_id")        val attemptId: String,
    @SerialName("user_id")           val userId: String,
    @SerialName("set_index")         val setIndex: Int,
    @SerialName("set_type")          val setType: String,
    @SerialName("prescribed_weight") val prescribedWeight: Double,
    @SerialName("prescribed_reps")   val prescribedReps: Int,
    @SerialName("is_completed")      val isCompleted: Boolean
)

@Serializable
data class RecordAttemptSetUpdate(
    @SerialName("actual_weight") val actualWeight: Double,
    @SerialName("actual_reps")   val actualReps: Int,
    @SerialName("rpe")           val rpe: Int? = null,
    @SerialName("rest_seconds")  val restSeconds: Int,
    @SerialName("is_completed")  val isCompleted: Boolean,
    @SerialName("completed_at")  val completedAt: String,
    @SerialName("notes")         val notes: String? = null
)

@Serializable
data class RecordAttemptCompleteUpdate(
    @SerialName("status")       val status: String,
    @SerialName("success")      val success: Boolean,
    @SerialName("notes")        val notes: String? = null,
    @SerialName("completed_at") val completedAt: String
)

@Serializable
private data class ExerciseInsert(
    @SerialName("id")          val id: String,
    @SerialName("name")        val name: String,
    @SerialName("category")    val category: String,
    @SerialName("equipment")   val equipment: String? = null
)

// ---------------------------------------------------------------------------
// Service
// ---------------------------------------------------------------------------

object RecordAttemptService {

    private val supabase get() = SupabaseConfig.client

    private fun nowIso(): String = Instant.now().toString()

    // -----------------------------------------------------------------------
    // Exercises
    // -----------------------------------------------------------------------

    suspend fun fetchExercises(): List<Exercise> =
        supabase.from("exercises")
            .select { order("name", Order.ASCENDING) }
            .decodeList<Exercise>()

    /**
     * Katalog hareketini veritabanındaki UUID ile eşleştirir; yoksa exercises tablosuna ekler.
     * Slug (ör. push_press) asla exercise_id olarak kullanılmaz.
     */
    suspend fun resolveOrCreateExercise(
        catalog: RecordExercise,
        categoryId: String?,
        knownExercises: List<Exercise>
    ): Exercise {
        val exercises = knownExercises.ifEmpty { fetchExercises() }
        findMatchingExercise(catalog, exercises)?.let { return it }

        val exerciseId = UUID.randomUUID().toString()
        val category = categoryId ?: "record_attempt"
        supabase.from("exercises").insert(
            ExerciseInsert(
                id = exerciseId,
                name = catalog.name,
                category = category,
                equipment = catalog.equipment
            )
        )
        return Exercise(
            id = exerciseId,
            name = catalog.name,
            category = category,
            equipment = catalog.equipment
        )
    }

    private fun normalizeExerciseKey(value: String): String =
        value.lowercase(Locale.ROOT)
            .replace("&", "and")
            .replace(Regex("[^a-z0-9]"), "")

    private fun findMatchingExercise(
        catalog: RecordExercise,
        exercises: List<Exercise>
    ): Exercise? {
        val catalogNameKey = normalizeExerciseKey(catalog.name)
        val slugKey = normalizeExerciseKey(catalog.id.replace('_', ' '))

        return exercises.firstOrNull { exercise ->
            val nameKey = normalizeExerciseKey(exercise.name)
            nameKey == catalogNameKey ||
                nameKey == slugKey ||
                (catalogNameKey.length >= 4 && nameKey.contains(catalogNameKey)) ||
                (nameKey.length >= 4 && catalogNameKey.contains(nameKey))
        }
    }

    suspend fun fetchCompletedAttempts(userId: String, limit: Int = 100): List<RecordAttempt> =
        supabase.from("record_attempts")
            .select {
                filter {
                    eq("user_id", userId)
                    eq("status", "completed")
                }
                order("completed_at", Order.DESCENDING)
                limit(limit.toLong())
            }
            .decodeList<RecordAttempt>()

    /** Past attempts for one exercise (history list on setup/detail). */
    suspend fun fetchAttemptsForExercise(
        userId: String,
        exerciseId: String,
        limit: Int = 20
    ): List<RecordAttempt> = withContext(Dispatchers.IO) {
        try {
            supabase.from("record_attempts")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("exercise_id", exerciseId)
                        neq("status", "in_progress")
                    }
                    order("completed_at", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<RecordAttempt>()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchPersonalRecordsForExercise(
        userId: String,
        exerciseId: String,
        limit: Int = 10
    ): List<PersonalRecord> = withContext(Dispatchers.IO) {
        try {
            supabase.from("personal_records")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("exercise_id", exerciseId)
                    }
                    order("record_date", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchMainSetsForAttempts(attemptIds: List<String>): Map<String, RecordAttemptSet> {
        if (attemptIds.isEmpty()) return emptyMap()
        return try {
            supabase.from("record_attempt_sets")
                .select {
                    filter {
                        isIn("attempt_id", attemptIds)
                        eq("set_type", "main")
                        eq("is_completed", true)
                    }
                }
                .decodeList<RecordAttemptSet>()
                .associateBy { it.attemptId }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    // -----------------------------------------------------------------------
    // Attempt lifecycle
    // -----------------------------------------------------------------------

    suspend fun startAttempt(
        userId: String,
        exerciseId: String,
        targetWeight: Double,
        targetReps: Int
    ): RecordAttempt {
        val attemptId = UUID.randomUUID().toString()
        val estimated = PlateCalculator.epley1RM(targetWeight.coerceAtLeast(0.0), targetReps.coerceAtLeast(1))
        val started = nowIso()
        val insert = RecordAttemptInsert(
            id = attemptId,
            userId = userId,
            exerciseId = exerciseId,
            targetWeight = targetWeight,
            targetReps = targetReps,
            estimated1RM = estimated,
            status = "in_progress",
            success = false,
            notes = null,
            sourceDevice = null,
            startedAt = started
        )
        // Skip re-select round-trip — return local model immediately
        supabase.from("record_attempts").insert(insert)
        return RecordAttempt(
            id = attemptId,
            userId = userId,
            exerciseId = exerciseId,
            targetWeight = targetWeight,
            targetReps = targetReps,
            estimated1RM = estimated,
            status = "in_progress",
            success = false,
            startedAt = started,
            createdAt = started
        )
    }

    suspend fun insertPlannedSets(
        attemptId: String,
        userId: String,
        plan: List<PlannedSet>
    ): List<RecordAttemptSet> {
        val inserts = plan.mapIndexed { index, p ->
            RecordAttemptSetInsert(
                id = UUID.randomUUID().toString(),
                attemptId = attemptId,
                userId = userId,
                setIndex = index,
                setType = if (p.isMain) "main" else "warmup",
                prescribedWeight = p.plannedWeight,
                prescribedReps = p.plannedReps,
                isCompleted = false
            )
        }
        supabase.from("record_attempt_sets").insert(inserts)
        // Build local list without second fetch
        return inserts.map { ins ->
            RecordAttemptSet(
                id = ins.id,
                attemptId = ins.attemptId,
                userId = ins.userId,
                setIndex = ins.setIndex,
                setTypeRaw = ins.setType,
                prescribedWeight = ins.prescribedWeight,
                prescribedReps = ins.prescribedReps,
                isCompleted = false
            )
        }
    }

    suspend fun saveSet(
        setId: String,
        actualWeight: Double,
        actualReps: Int,
        rpe: Int?,
        restSeconds: Int,
        notes: String?
    ) {
        supabase.from("record_attempt_sets")
            .update(
                RecordAttemptSetUpdate(
                    actualWeight = actualWeight,
                    actualReps = actualReps,
                    rpe = rpe,
                    restSeconds = restSeconds,
                    isCompleted = true,
                    completedAt = nowIso(),
                    notes = notes
                )
            ) { filter { eq("id", setId) } }
    }

    /**
     * Mark attempt done. [userId] optional — when provided, skips extra SELECT.
     * Streak is best-effort and non-blocking for callers who fire-and-forget.
     */
    suspend fun completeAttempt(
        attemptId: String,
        success: Boolean,
        notes: String?,
        userId: String? = null
    ) {
        supabase.from("record_attempts")
            .update(
                RecordAttemptCompleteUpdate(
                    status = if (success) "completed" else "failed",
                    success = success,
                    notes = notes,
                    completedAt = nowIso()
                )
            ) { filter { eq("id", attemptId) } }

        if (success && userId != null) {
            try {
                StreakService.recordActivityAndSync(userId, activityType = "record_attempt")
            } catch (_: Exception) {
            }
        }
    }

    suspend fun abandonAttempt(attemptId: String) {
        supabase.from("record_attempts")
            .update(
                RecordAttemptCompleteUpdate(
                    status = "abandoned",
                    success = false,
                    notes = null,
                    completedAt = nowIso()
                )
            ) { filter { eq("id", attemptId) } }
    }

    /**
     * Returns true if a new personal record row was inserted.
     * [measureType] controls comparison (weight 1RM / max reps / min time).
     *
     * Storage convention:
     * - WEIGHT: weight=kg, reps=reps
     * - REPS: weight=0, reps=rep count
     * - TIME / running: weight=seconds, reps=1
     * - CALORIES: weight=calories, reps=seconds taken (or 1)
     */
    suspend fun updatePersonalRecordIfNeeded(
        userId: String,
        exerciseId: String,
        weight: Double,
        reps: Int,
        notes: String?,
        measureType: RecordMeasureType = RecordMeasureType.WEIGHT
    ): Boolean {
        val existing = supabase.from("personal_records")
            .select {
                filter {
                    eq("user_id", userId)
                    eq("exercise_id", exerciseId)
                }
                order("record_date", Order.DESCENDING)
                limit(1)
            }
            .decodeList<PersonalRecord>()

        val isBetter = if (existing.isEmpty()) {
            true
        } else {
            val prev = existing.first()
            isBetterPersonalRecord(
                measureType = measureType,
                newWeight = weight,
                newReps = reps,
                prevWeight = prev.weight,
                prevReps = prev.reps
            )
        }
        if (!isBetter) return false

        supabase.from("personal_records").insert(
            PersonalRecordInsert(
                userId = userId,
                exerciseId = exerciseId,
                weight = weight,
                reps = reps,
                recordDate = nowIso(),
                notes = notes
            )
        )
        return true
    }

    /** Running target distance in km from catalog exercise id. */
    fun runningTargetKm(catalogId: String): Double = when {
        catalogId.contains("400") -> 0.4
        catalogId.contains("800") -> 0.8
        catalogId.contains("1k") || catalogId.endsWith("_1k") -> 1.0
        catalogId.contains("3k") -> 3.0
        catalogId.contains("5k") -> 5.0
        catalogId.contains("10k") -> 10.0
        catalogId.contains("half") || catalogId.contains("21") -> 21.0975
        catalogId.contains("full") || catalogId.contains("42") -> 42.195
        else -> 1.0
    }

    /**
     * Pure PR comparison — unit-tested.
     *
     * Storage convention:
     * - WEIGHT: weight=kg, reps=reps (compare estimated 1RM)
     * - REPS / AMRAP rounds: higher reps wins
     * - TIME / DISTANCE: lower weight (seconds) wins
     * - CALORIES: higher cal wins; same cal → lower time (reps) wins
     */
    fun isBetterPersonalRecord(
        measureType: RecordMeasureType,
        newWeight: Double,
        newReps: Int,
        prevWeight: Double,
        prevReps: Int
    ): Boolean = when (measureType) {
        RecordMeasureType.WEIGHT -> {
            val new1RM = PlateCalculator.epley1RM(newWeight, newReps.coerceAtLeast(1))
            val old1RM = PlateCalculator.epley1RM(prevWeight, prevReps.coerceAtLeast(1))
            new1RM > old1RM
        }
        RecordMeasureType.REPS -> newReps > prevReps
        RecordMeasureType.TIME, RecordMeasureType.DISTANCE ->
            newWeight > 0 && (prevWeight <= 0 || newWeight < prevWeight)
        RecordMeasureType.CALORIES -> when {
            newWeight > prevWeight -> true
            newWeight == prevWeight ->
                newReps > 0 && (prevReps <= 0 || newReps < prevReps)
            else -> false
        }
    }

    /**
     * Join capacity decision for waitlist UX (mirrors class/event join).
     * @return WAITING if full, CONFIRMED if seat available, ALREADY_JOINED if already in.
     */
    fun resolveJoinResult(
        alreadyJoined: Boolean,
        currentSeats: Int,
        capacity: Int?
    ): GroupClassService.JoinResult = when {
        alreadyJoined -> GroupClassService.JoinResult.ALREADY_JOINED
        capacity != null && currentSeats >= capacity -> GroupClassService.JoinResult.WAITING
        else -> GroupClassService.JoinResult.CONFIRMED
    }
}
