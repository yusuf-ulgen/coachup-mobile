package com.app.coachup.app.services

import com.app.coachup.app.config.GymConfig
import com.app.coachup.app.config.SupabaseConfig.client
import com.app.coachup.app.models.GoalInsert
import com.app.coachup.app.models.GoalProgressUpdate
import com.app.coachup.app.models.StatusUpdate
import com.app.coachup.app.models.UserGoal
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android equivalent of iOS GoalService.
 *
 * Mirrors table: user_goals.
 */
object GoalService {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // -------------------------------------------------------------------------
    // Fetch
    // -------------------------------------------------------------------------

    suspend fun fetchGoals(userId: String): List<UserGoal> {
        return try {
            client.postgrest["user_goals"]
                .select {
                    filter { eq("user_id", userId) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<UserGoal>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun fetchActiveGoals(userId: String): List<UserGoal> {
        return try {
            client.postgrest["user_goals"]
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("status", "in_progress")
                    }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<UserGoal>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun fetchGoalsForDate(userId: String, date: java.time.LocalDate): List<UserGoal> {
        val dateStr = date.toString()
        return try {
            // Match by date prefix so both `date` and `timestamptz` columns work.
            fetchGoals(userId).filter { goal ->
                goal.targetDate?.take(10) == dateStr
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchGoalDays(userId: String, year: Int, month: Int): Set<Int> {
        val startOfMonth = java.time.LocalDate.of(year, month, 1)
        val endOfMonth = startOfMonth.plusMonths(1)
        return try {
            fetchGoals(userId)
                .mapNotNull { goal ->
                    goal.targetDate?.take(10)?.let { dateStr ->
                        try {
                            val date = java.time.LocalDate.parse(dateStr)
                            if (!date.isBefore(startOfMonth) && date.isBefore(endOfMonth)) {
                                date.dayOfMonth
                            } else null
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
                .toSet()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptySet()
        }
    }

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    suspend fun createGoal(
        userId: String,
        type: String,
        title: String,
        targetValue: Double? = null,
        currentValue: Double? = null,
        unit: String? = null,
        startDate: String,
        targetDate: String? = null
    ) {
        _isLoading.value = true
        try {
            val insert = GoalInsert(
                userId = userId,
                gymId = resolveGymId(),
                type = type,
                title = title,
                targetValue = targetValue,
                currentValue = currentValue,
                unit = unit,
                startDate = startDate,
                targetDate = targetDate,
                status = "in_progress"
            )
            client.postgrest["user_goals"].insert(insert)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e
        } finally {
            _isLoading.value = false
        }
    }

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    suspend fun updateGoalProgress(
        goalId: String,
        userId: String,
        currentValue: Double,
        progressPercentage: Int,
        notes: String? = null
    ) {
        _isLoading.value = true
        try {
            val update = GoalProgressUpdate(
                currentValue = currentValue,
                progressPercentage = progressPercentage,
                notes = notes
            )
            client.postgrest["user_goals"]
                .update(update) {
                    filter {
                        eq("id", goalId)
                        eq("user_id", userId)
                    }
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun completeGoal(goalId: String, userId: String) {
        try {
            client.postgrest["user_goals"]
                .update(StatusUpdate(status = "completed")) {
                    filter {
                        eq("id", goalId)
                        eq("user_id", userId)
                    }
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e
        }
    }

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    suspend fun deleteGoal(goalId: String, userId: String) {
        try {
            client.postgrest["user_goals"]
                .delete {
                    filter {
                        eq("id", goalId)
                        eq("user_id", userId)
                    }
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e
        }
    }

    private fun resolveGymId(): String? {
        if (UserService.isIndividualUser()) return null
        return UserService.currentProfile.value?.gymId ?: GymConfig.GYM_ID
    }
}
