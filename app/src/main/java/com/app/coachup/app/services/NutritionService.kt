package com.app.coachup.app.services

import com.app.coachup.app.config.SupabaseConfig.client
import com.app.coachup.app.models.NutritionFood
import com.app.coachup.app.models.NutritionLog
import com.app.coachup.app.models.NutritionLogInsert
import com.app.coachup.app.models.NutritionMeal
import com.app.coachup.app.models.NutritionPlan
import com.app.coachup.app.models.UserNutritionPlan
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Android equivalent of iOS NutritionService.
 *
 * Mirrors tables: user_nutrition_plans, nutrition_plans, nutrition_meals,
 * nutrition_foods, nutrition_logs.
 */
object NutritionService {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Meal with its food items for rich plan UI. */
    data class MealWithFoods(
        val meal: NutritionMeal,
        val foods: List<NutritionFood> = emptyList()
    )

    // -------------------------------------------------------------------------
    // Plans
    // -------------------------------------------------------------------------

    suspend fun fetchActivePlan(userId: String): UserNutritionPlan? {
        return try {
            client.postgrest["user_nutrition_plans"]
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("status", "active")
                    }
                    limit(1)
                }
                .decodeList<UserNutritionPlan>()
                .firstOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchPlanDetails(planId: String): NutritionPlan? {
        return try {
            client.postgrest["nutrition_plans"]
                .select {
                    filter { eq("id", planId) }
                    limit(1)
                }
                .decodeSingle<NutritionPlan>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchMeals(planId: String): List<NutritionMeal> {
        return try {
            client.postgrest["nutrition_meals"]
                .select {
                    filter { eq("plan_id", planId) }
                    order("order_index", Order.ASCENDING)
                }
                .decodeList<NutritionMeal>()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Fallback when order_index column is missing
            try {
                client.postgrest["nutrition_meals"]
                    .select {
                        filter { eq("plan_id", planId) }
                    }
                    .decodeList<NutritionMeal>()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw e
            }
        }
    }

    suspend fun fetchFoodsForMeals(mealIds: List<String>): Map<String, List<NutritionFood>> {
        if (mealIds.isEmpty()) return emptyMap()
        return try {
            client.postgrest["nutrition_foods"]
                .select {
                    filter { isIn("meal_id", mealIds) }
                }
                .decodeList<NutritionFood>()
                .groupBy { it.mealId.orEmpty() }
                .filterKeys { it.isNotEmpty() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /** Meals + foods for modern plan cards. Foods are optional (empty if table empty/missing). */
    suspend fun fetchMealsWithFoods(planId: String): List<MealWithFoods> {
        val meals = fetchMeals(planId)
        val foodsByMeal = fetchFoodsForMeals(meals.map { it.id })
        return meals.map { meal ->
            MealWithFoods(
                meal = meal,
                foods = foodsByMeal[meal.id].orEmpty()
            )
        }
    }

    // -------------------------------------------------------------------------
    // Logging
    // -------------------------------------------------------------------------

    suspend fun logNutrition(
        userId: String,
        planId: String? = null,
        mealType: String,
        calories: Int,
        protein: Double,
        carbs: Double,
        fat: Double,
        notes: String? = null
    ) {
        _isLoading.value = true
        try {
            val loggedAt = LocalDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_DATE_TIME)

            val insert = NutritionLogInsert(
                userId = userId,
                planId = planId,
                mealType = mealType,
                calories = calories,
                protein = protein,
                carbs = carbs,
                fat = fat,
                notes = notes,
                loggedAt = loggedAt
            )
            client.postgrest["nutrition_logs"].insert(insert)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun fetchTodayLogs(userId: String): List<NutritionLog> {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        return try {
            client.postgrest["nutrition_logs"]
                .select {
                    filter {
                        eq("user_id", userId)
                        gte("logged_at", "${today}T00:00:00")
                        lte("logged_at", "${today}T23:59:59")
                    }
                    order("logged_at", Order.ASCENDING)
                }
                .decodeList<NutritionLog>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun fetchLogs(userId: String, limit: Int = 50): List<NutritionLog> {
        return try {
            client.postgrest["nutrition_logs"]
                .select {
                    filter { eq("user_id", userId) }
                    order("logged_at", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<NutritionLog>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e
        }
    }
}
