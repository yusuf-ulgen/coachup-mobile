package com.app.coachup.app.services

import com.app.coachup.app.config.SupabaseConfig
import com.app.coachup.app.models.ProgramExercise
import com.app.coachup.app.models.WorkoutSet
import com.app.coachup.app.models.WorkoutSetInsert
import com.app.coachup.app.models.WorkoutSetUpdate
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

/**
 * WorkoutService — Android production equivalent of iOS WorkoutService.swift.
 *
 * Handles:
 *  - Fetching program exercises with exercise details (join "exercises(*)")
 *  - Creating initial workout sets for a session from program exercises
 *  - Completing individual sets
 *  - Fetching workout sets for a session
 *  - Delegating session completion to TrainingService
 *
 * Table names match iOS exactly:
 *  "program_exercises", "workout_sets", "training_sessions"
 */
object WorkoutService {

    private val supabase get() = SupabaseConfig.client

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // -----------------------------------------------------------------------
    // Program Exercises — "program_exercises" table
    // Mirrors iOS fetchProgramExercises(programId:): select("*, exercises(*)")
    // -----------------------------------------------------------------------

    /**
     * Returns all exercises for a program with joined exercise details.
     * Ordered by order_index ascending.
     */
    suspend fun fetchExercises(programId: String): List<ProgramExercise> {
        return supabase
            .from("program_exercises")
            .select(columns = Columns.raw("*, exercises(*)")) {
                filter { eq("program_id", programId) }
                order("order_index", Order.ASCENDING)
            }
            .decodeList<ProgramExercise>()
    }

    // -----------------------------------------------------------------------
    // Workout Sets — "workout_sets" table
    // Mirrors iOS fetchWorkoutSets(sessionId:)
    // -----------------------------------------------------------------------

    /**
     * Returns all workout sets for a session, ordered by set_number.
     */
    suspend fun fetchWorkoutSets(sessionId: String): List<WorkoutSet> {
        return supabase
            .from("workout_sets")
            .select {
                filter { eq("session_id", sessionId) }
                order("set_number", Order.ASCENDING)
            }
            .decodeList<WorkoutSet>()
    }

    // -----------------------------------------------------------------------
    // Create Workout Sets
    // Mirrors iOS createWorkoutSets(sessionId:userId:programExercises:)
    // Inserts one row per set per exercise (1..pe.sets) as the iOS code does.
    // -----------------------------------------------------------------------

    /**
     * Inserts one [WorkoutSet] row per set number for every program exercise.
     * Mirrors iOS for-loop: for pe in programExercises { for setNum in 1...pe.sets { ... } }.
     */
    suspend fun createWorkoutSet(
        sessionId: String,
        userId: String,
        programExercises: List<ProgramExercise>
    ) {
        for (pe in programExercises) {
            val setCount = maxOf(1, pe.sets)
            for (setNum in 1..setCount) {
                val insert = WorkoutSetInsert(
                    sessionId = sessionId,
                    exerciseId = pe.exerciseId,
                    userId = userId,
                    setNumber = setNum,
                    reps = pe.reps,
                    weight = pe.weightSuggestion,
                    isCompleted = false,
                    completedAt = null
                )
                supabase.from("workout_sets").insert(insert)
            }
        }
    }

    // -----------------------------------------------------------------------
    // Complete a Set
    // Mirrors iOS completeSet(setId:reps:weight:)
    // -----------------------------------------------------------------------

    /**
     * Marks a single workout set as completed with the performed reps/weight.
     */
    suspend fun completeWorkoutSet(setId: String, reps: Int, weight: Double?) {
        supabase.from("workout_sets")
            .update(
                WorkoutSetUpdate(
                    reps = reps,
                    weight = weight,
                    isCompleted = true,
                    completedAt = Instant.now().toString()
                )
            ) { filter { eq("id", setId) } }
    }

    // -----------------------------------------------------------------------
    // Complete Session
    // Mirrors iOS WorkoutService.completeSession(sessionId:) which delegates to
    // TrainingService.shared.completeSession(sessionId:notes:nil)
    // -----------------------------------------------------------------------

    /**
     * Delegates to [TrainingService.completeSession] — identical to iOS pattern.
     */
    suspend fun completeSession(sessionId: String, notes: String? = null) {
        TrainingService.completeSession(sessionId = sessionId, notes = notes)
    }
}
