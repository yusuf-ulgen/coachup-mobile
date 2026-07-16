package com.app.coachup.app.services

import com.app.coachup.app.config.SupabaseConfig.client
import com.app.coachup.app.models.Survey
import com.app.coachup.app.models.SurveyResponse
import com.app.coachup.app.models.SurveyResponseInsert
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Android equivalent of iOS SurveyService.
 *
 * Mirrors tables: surveys, survey_responses.
 *
 * Audience rules:
 * - Bireysel kullanıcılar: yalnızca platform anketleri (gym_id = null, süper admin)
 * - Salon üyeleri: yalnızca kendi salonlarının anketleri
 */
object SurveyService {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // -------------------------------------------------------------------------
    // Fetch Active Surveys
    // -------------------------------------------------------------------------

    suspend fun fetchActiveSurveys(): List<Survey> {
        val profile = UserService.currentProfile.value ?: return emptyList()
        val today = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE)
        val isIndividual = UserService.isIndividualUser(profile)

        return try {
            if (isIndividual) {
                client.postgrest["surveys"]
                    .select {
                        filter {
                            eq("status", "active")
                            gte("end_date", today)
                            exact("gym_id", null)
                        }
                        order("created_at", Order.DESCENDING)
                    }
                    .decodeList<Survey>()
            } else {
                val gymId = UserService.resolveActiveGymIdForContent(profile) ?: return emptyList()
                client.postgrest["surveys"]
                    .select {
                        filter {
                            eq("status", "active")
                            gte("end_date", today)
                            eq("gym_id", gymId)
                        }
                        order("created_at", Order.DESCENDING)
                    }
                    .decodeList<Survey>()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e
        }
    }

    // -------------------------------------------------------------------------
    // Responses
    // -------------------------------------------------------------------------

    suspend fun submitResponse(
        surveyId: String,
        userId: String,
        answers: JsonElement
    ) {
        _isLoading.value = true
        try {
            val insert = SurveyResponseInsert(
                surveyId = surveyId,
                userId = userId,
                gymId = UserService.resolveActiveGymIdForContent(),
                answers = answers.jsonObject
            )
            client.postgrest["survey_responses"].insert(insert)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun hasResponded(surveyId: String, userId: String): Boolean {
        return try {
            val response = client.postgrest["survey_responses"]
                .select {
                    filter {
                        eq("survey_id", surveyId)
                        eq("user_id", userId)
                    }
                    limit(1)
                }
                .decodeList<SurveyResponse>()
            response.isNotEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }
}
