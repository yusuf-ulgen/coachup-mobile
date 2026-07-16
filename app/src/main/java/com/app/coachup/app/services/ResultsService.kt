package com.app.coachup.app.services

import com.app.coachup.app.config.SupabaseConfig
import com.app.coachup.app.models.Exercise
import com.app.coachup.app.models.ExerciseHistory
import com.app.coachup.app.models.ExerciseHistoryInsert
import com.app.coachup.app.models.ExerciseResult
import com.app.coachup.app.models.ExerciseResultInsert
import com.app.coachup.app.models.ExerciseResultUpdate
import com.app.coachup.app.models.OneRmPercentage
import com.app.coachup.app.models.PersonalRecord
import com.app.coachup.app.models.PersonalRecordInsert
import com.app.coachup.app.models.ProgressDataPoint
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * ResultsService — Android production equivalent of iOS ResultsService.swift.
 *
 * Manages:
 *  - Exercise catalogue with optional search/category filter
 *  - Exercise results (best weight/reps per exercise per user)
 *  - Exercise history (individual session entries)
 *  - Personal records
 *  - Progress chart data
 *  - Brzycki 1-RM calculation and percentage table
 *
 * Table names and column names match iOS exactly.
 */
object ResultsService {

    private val supabase get() = SupabaseConfig.client

    // -----------------------------------------------------------------------
    // Reactive state — mirrors iOS @Published vars
    // -----------------------------------------------------------------------

    private val _exercises = MutableStateFlow<List<Exercise>>(emptyList())
    val exercises: StateFlow<List<Exercise>> = _exercises.asStateFlow()

    private val _exerciseResults = MutableStateFlow<List<ExerciseResult>>(emptyList())
    val exerciseResults: StateFlow<List<ExerciseResult>> = _exerciseResults.asStateFlow()

    private val _exerciseHistory = MutableStateFlow<List<ExerciseHistory>>(emptyList())
    val exerciseHistory: StateFlow<List<ExerciseHistory>> = _exerciseHistory.asStateFlow()

    private val _personalRecords = MutableStateFlow<List<PersonalRecord>>(emptyList())
    val personalRecords: StateFlow<List<PersonalRecord>> = _personalRecords.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // -----------------------------------------------------------------------
    // Exercises — "exercises" table
    // Mirrors iOS fetchExercises() / fetchExercises(searchText:category:)
    // -----------------------------------------------------------------------

    /**
     * Returns all exercises ordered by name.
     */
    suspend fun fetchExercises(): List<Exercise> {
        _isLoading.value = true
        return try {
            val result = supabase
                .from("exercises")
                .select { order("name", Order.ASCENDING) }
                .decodeList<Exercise>()
            _exercises.value = result
            result
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Returns exercises filtered by optional [searchText] (ilike on name) and [category].
     * Mirrors iOS fetchExercises(searchText:category:).
     */
    suspend fun fetchExercises(
        searchText: String? = null,
        category: String? = null
    ): List<Exercise> {
        val result = supabase
            .from("exercises")
            .select {
                filter {
                    if (!category.isNullOrEmpty()) {
                        eq("category", category)
                    }
                    if (!searchText.isNullOrEmpty()) {
                        ilike("name", "%$searchText%")
                    }
                }
                order("name", Order.ASCENDING)
            }
            .decodeList<Exercise>()
        _exercises.value = result
        return result
    }

    // -----------------------------------------------------------------------
    // Exercise Results — "exercise_results" table
    // Mirrors iOS fetchExerciseResults(userId:) / fetchExerciseResult(userId:exerciseId:)
    // -----------------------------------------------------------------------

    /**
     * Returns all exercise results for a user, ordered by last_updated descending.
     * Fetches exercises separately and merges in Kotlin (schema cache has no FK).
     */
    suspend fun fetchExerciseResults(userId: String): List<ExerciseResult> {
        _isLoading.value = true
        return try {
            val results = supabase
                .from("exercise_results")
                .select {
                    filter { eq("user_id", userId) }
                    order("last_updated", Order.DESCENDING)
                }
                .decodeList<ExerciseResult>()

            // Fetch referenced exercises in one batch and attach
            val exerciseIds = results.map { it.exerciseId }.distinct()
            val exercises = if (exerciseIds.isNotEmpty()) {
                supabase.from("exercises")
                    .select { filter { isIn("id", exerciseIds) } }
                    .decodeList<Exercise>()
                    .associateBy { it.id }
            } else emptyMap()

            val merged = results.map { it.copy(exercise = exercises[it.exerciseId]) }
            _exerciseResults.value = merged
            merged
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Returns the result record for a specific exercise, or null if none exists.
     */
    suspend fun fetchExerciseResult(userId: String, exerciseId: String): ExerciseResult? {
        val result = supabase
            .from("exercise_results")
            .select {
                filter {
                    eq("user_id", userId)
                    eq("exercise_id", exerciseId)
                }
            }
            .decodeList<ExerciseResult>()
            .firstOrNull() ?: return null

        val exercise = supabase.from("exercises")
            .select { filter { eq("id", exerciseId) } }
            .decodeList<Exercise>()
            .firstOrNull()

        return result.copy(exercise = exercise)
    }

    // -----------------------------------------------------------------------
    // Exercise History — "exercise_history" table
    // Mirrors iOS fetchExerciseHistory / addExerciseHistory
    // -----------------------------------------------------------------------

    /**
     * Returns the last [limit] history entries for an exercise, newest first.
     * Mirrors iOS fetchExerciseHistory(userId:exerciseId:limit:).
     */
    suspend fun fetchExerciseHistory(
        userId: String,
        exerciseId: String,
        limit: Int = 20
    ): List<ExerciseHistory> {
        _isLoading.value = true
        return try {
            val result = supabase
                .from("exercise_history")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("exercise_id", exerciseId)
                    }
                    order("recorded_at", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<ExerciseHistory>()
            _exerciseHistory.value = result
            result
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Inserts a new history entry and then updates the exercise_results record.
     * Mirrors iOS addExerciseHistory(userId:exerciseId:weight:reps:sets:notes:).
     */
    suspend fun addExerciseHistory(
        userId: String,
        exerciseId: String,
        weight: Double,
        reps: Int,
        sets: Int,
        notes: String? = null
    ) {
        _isLoading.value = true
        try {
            supabase.from("exercise_history").insert(
                ExerciseHistoryInsert(
                    userId = userId,
                    exerciseId = exerciseId,
                    weight = weight,
                    reps = reps,
                    sets = sets,
                    notes = notes,
                    recordedAt = Instant.now().toString()
                )
            )
            // Keep the exercise_results record up to date
            updateExerciseResult(userId, exerciseId, weight, reps)
        } finally {
            _isLoading.value = false
        }
    }

    // -----------------------------------------------------------------------
    // Personal Records — "personal_records" table
    // Mirrors iOS fetchPersonalRecords / addPersonalRecord
    // -----------------------------------------------------------------------

    /**
     * Returns all personal records for a user with joined exercise data.
     * Mirrors iOS fetchPersonalRecords(userId:).
     */
    suspend fun fetchPersonalRecords(userId: String): List<PersonalRecord> {
        _isLoading.value = true
        return try {
            val records = supabase
                .from("personal_records")
                .select {
                    filter { eq("user_id", userId) }
                    order("record_date", Order.DESCENDING)
                }
                .decodeList<PersonalRecord>()

            val exerciseIds = records.map { it.exerciseId }.distinct()
            val exercises = if (exerciseIds.isNotEmpty()) {
                supabase.from("exercises")
                    .select { filter { isIn("id", exerciseIds) } }
                    .decodeList<Exercise>()
                    .associateBy { it.id }
            } else emptyMap()

            val merged = records.map { it.copy(exercise = exercises[it.exerciseId]) }
            _personalRecords.value = merged
            merged
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Inserts a new personal record row.
     * Mirrors iOS addPersonalRecord(userId:exerciseId:weight:reps:notes:).
     */
    suspend fun addPersonalRecord(
        userId: String,
        exerciseId: String,
        weight: Double,
        reps: Int,
        notes: String? = null
    ) {
        supabase.from("personal_records").insert(
            PersonalRecordInsert(
                userId = userId,
                exerciseId = exerciseId,
                weight = weight,
                reps = reps,
                recordDate = Instant.now().toString(),
                notes = notes
            )
        )
    }

    // -----------------------------------------------------------------------
    // Progress Chart Data
    // Mirrors iOS fetchProgressData(userId:exerciseId:days:)
    // -----------------------------------------------------------------------

    /**
     * Returns (date, weight) data points for the last [days] days.
     * Mirrors iOS fetchProgressData returning [(date: Date, weight: Double)].
     */
    suspend fun getProgressData(
        userId: String,
        exerciseId: String,
        days: Int = 30
    ): List<ProgressDataPoint> {
        val startDate = LocalDate.now(ZoneOffset.UTC).minusDays(days.toLong())
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC)
            .toString()

        return supabase
            .from("exercise_history")
            .select {
                filter {
                    eq("user_id", userId)
                    eq("exercise_id", exerciseId)
                    gte("recorded_at", startDate)
                }
                order("recorded_at", Order.ASCENDING)
            }
            .decodeList<ExerciseHistory>()
            .map { ProgressDataPoint(date = it.recordedAt, weight = it.weight) }
    }

    // -----------------------------------------------------------------------
    // 1RM Calculation — Brzycki formula
    // Mirrors iOS calculate1RM and calculatePercentages
    // -----------------------------------------------------------------------

    /**
     * Calculates the estimated 1-rep maximum using the Brzycki formula:
     *   1RM = weight × (36 / (37 − reps))
     *
     * Mirrors iOS: let oneRepMax = weight * (36.0 / (37.0 - Double(reps)))
     */
    fun calculate1RM(weight: Double, reps: Int): Double {
        if (reps >= 37) return weight
        return weight * (36.0 / (37.0 - reps.toDouble()))
    }

    /**
     * Returns a table of 1RM percentages with the corresponding weight.
     * Mirrors iOS calculatePercentages(oneRepMax:).
     * Percentages: 100, 95, 90, 85, 80, 75, 70, 65, 60, 55, 50
     */
    fun calculatePercentages(oneRepMax: Double): List<OneRmPercentage> {
        val percentages = listOf(100, 95, 90, 85, 80, 75, 70, 65, 60, 55, 50)
        return percentages.map { pct ->
            OneRmPercentage(
                percentage = pct,
                weight = oneRepMax * pct / 100.0
            )
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Upserts the exercise_results row: updates if an existing record is found,
     * inserts otherwise. Only updates fields when new values are better.
     * Mirrors iOS ResultsService.updateExerciseResult exactly.
     */
    suspend fun upsertExerciseResult(
        userId: String,
        exerciseId: String,
        weight: Double,
        reps: Int
    ) = updateExerciseResult(userId, exerciseId, weight, reps)

    private suspend fun updateExerciseResult(
        userId: String,
        exerciseId: String,
        weight: Double,
        reps: Int
    ) {
        val oneRepMax = calculate1RM(weight, reps)
        val now = Instant.now().toString()

        val existing = supabase
            .from("exercise_results")
            .select {
                filter {
                    eq("user_id", userId)
                    eq("exercise_id", exerciseId)
                }
            }
            .decodeList<ExerciseResult>()

        val existingResult = existing.firstOrNull()
        if (existingResult != null) {
            // Only update fields that improved — mirrors iOS logic
            val newMaxWeight = if (weight > existingResult.maxWeight) weight else null
            val newMaxReps = if (weight > existingResult.maxWeight) reps else null
            val newOneRepMax = if (oneRepMax > existingResult.oneRepMax) oneRepMax else null

            supabase.from("exercise_results")
                .update(
                    ExerciseResultUpdate(
                        maxWeight = newMaxWeight,
                        maxReps = newMaxReps,
                        oneRepMax = newOneRepMax,
                        lastUpdated = now
                    )
                ) { filter { eq("id", existingResult.id) } }
        } else {
            supabase.from("exercise_results").insert(
                ExerciseResultInsert(
                    userId = userId,
                    exerciseId = exerciseId,
                    maxWeight = weight,
                    maxReps = reps,
                    oneRepMax = oneRepMax,
                    lastUpdated = now
                )
            )
        }
    }
}
