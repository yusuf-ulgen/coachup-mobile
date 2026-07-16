package com.app.coachup.app.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URL
import javax.net.ssl.HttpsURLConnection

// ---------------------------------------------------------------------------
// FatSecretService — mirrors iOS FatSecretService.swift
//
// Searches FatSecret food database via proxy endpoint.
// ---------------------------------------------------------------------------

data class FatSecretFood(
    val id: String,
    val name: String,
    val description: String,
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val servingDescription: String
)

object FatSecretService {

    private const val PROXY_URL = "https://app.getcoachup.com/fatsecret_proxy.php"

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // -----------------------------------------------------------------------
    // Search foods — mirrors iOS searchFoods(query:maxResults:)
    // -----------------------------------------------------------------------

    suspend fun searchFoods(query: String, maxResults: Int = 20): List<FatSecretFood> =
        withContext(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val urlStr = "$PROXY_URL?q=${encode(query)}&max=$maxResults"
                val connection = URL(urlStr).openConnection() as HttpsURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000

                val responseCode = connection.responseCode
                if (responseCode != 200) {
                    android.util.Log.e("FatSecret", "HTTP $responseCode for: $query")
                    return@withContext emptyList()
                }

                val body = connection.inputStream.bufferedReader().readText()
                android.util.Log.d("FatSecret", "Response (500 chars): ${body.take(500)}")

                val decoded = json.decodeFromString<FoodSearchResponse>(body)
                val foods = decoded.foods?.food ?: emptyList()
                android.util.Log.d("FatSecret", "Got ${foods.size} results")

                foods.mapNotNull { item ->
                    val parsed = parseDescription(item.foodDescription) ?: return@mapNotNull null
                    FatSecretFood(
                        id = item.foodId,
                        name = item.foodName,
                        description = item.foodDescription,
                        calories = parsed.calories,
                        protein = parsed.protein,
                        carbs = parsed.carbs,
                        fat = parsed.fat,
                        servingDescription = parsed.serving
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("FatSecret", "searchFoods error: ${e.message}")
                emptyList()
            } finally {
                _isLoading.value = false
            }
        }

    // -----------------------------------------------------------------------
    // Parse description string
    // Format: "Per 100g - Calories: 52kcal | Fat: 0.17g | Carbs: 13.81g | Protein: 0.26g"
    // -----------------------------------------------------------------------

    private data class ParsedNutrition(
        val calories: Int,
        val protein: Double,
        val carbs: Double,
        val fat: Double,
        val serving: String
    )

    private fun parseDescription(desc: String): ParsedNutrition? {
        val serving = desc.split(" - ").firstOrNull() ?: ""

        fun extract(key: String): Double? {
            val marker = "$key: "
            val idx = desc.indexOf(marker)
            if (idx < 0) return null
            val sub = desc.substring(idx + marker.length)
            val numStr = sub.takeWhile { it.isDigit() || it == '.' }
            return numStr.toDoubleOrNull()
        }

        val cal = extract("Calories") ?: return null
        val fat = extract("Fat") ?: return null
        val carbs = extract("Carbs") ?: return null
        val protein = extract("Protein") ?: return null

        return ParsedNutrition(
            calories = cal.toInt(),
            protein = protein,
            carbs = carbs,
            fat = fat,
            serving = serving
        )
    }

    private fun encode(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}

// ---------------------------------------------------------------------------
// Serializable response models
// ---------------------------------------------------------------------------

@Serializable
private data class FoodSearchResponse(
    val foods: FoodsWrapper? = null
)

@Serializable
private data class FoodsWrapper(
    val food: List<FoodItem>? = null
)

@Serializable
private data class FoodItem(
    @SerialName("food_id")          val foodId: String,
    @SerialName("food_name")        val foodName: String,
    @SerialName("food_description") val foodDescription: String
)
