package com.app.coachup.app.services

import android.util.Log
import com.app.coachup.app.config.GymConfig
import com.app.coachup.app.config.SupabaseConfig.client
import com.app.coachup.app.models.AreaReservation
import com.app.coachup.app.models.GymArea
import com.app.coachup.app.models.ReservationInsert
import com.app.coachup.app.models.StatusUpdate
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Salon alan rezervasyonları — [gym_areas] ve [area_reservations] tabloları.
 */
object ReservationService {

    private const val TAG = "ReservationService"

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private fun effectiveGymId(gymId: String?): String =
        gymId?.takeIf { it.isNotBlank() } ?: GymConfig.GYM_ID

    suspend fun fetchAreas(gymId: String? = null): List<GymArea> {
        val targetGymId = effectiveGymId(gymId)
        return try {
            val areas = client.postgrest["gym_areas"]
                .select {
                    filter {
                        eq("gym_id", targetGymId)
                        eq("is_active", true)
                    }
                    order("name", Order.ASCENDING)
                }
                .decodeList<GymArea>()
            Log.d(TAG, "fetchAreas gymId=$targetGymId count=${areas.size}")
            areas
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "fetchAreas failed gymId=$targetGymId", e)
            throw e
        }
    }

    suspend fun fetchMyReservations(userId: String, gymId: String? = null): List<AreaReservation> {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        return try {
            val reservations = client.postgrest["area_reservations"]
                .select(Columns.raw("*, gym_areas(*)")) {
                    filter {
                        eq("user_id", userId)
                        gte("reservation_date", today)
                    }
                    order("reservation_date", Order.ASCENDING)
                }
                .decodeList<AreaReservation>()
            Log.d(TAG, "fetchMyReservations userId=$userId count=${reservations.size}")
            reservations
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "fetchMyReservations failed userId=$userId", e)
            throw e
        }
    }

    suspend fun bookReservation(
        userId: String,
        areaId: String,
        gymId: String?,
        date: String,
        startTime: String,
        endTime: String,
        notes: String? = null
    ) {
        _isLoading.value = true
        try {
            val insert = ReservationInsert(
                areaId = areaId,
                userId = userId,
                gymId = effectiveGymId(gymId),
                reservationDate = date,
                startTime = startTime,
                endTime = endTime,
                status = "pending",
                notes = notes
            )
            client.postgrest["area_reservations"].insert(insert)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "bookReservation failed areaId=$areaId", e)
            throw e
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun cancelReservation(reservationId: String, userId: String) {
        _isLoading.value = true
        try {
            client.postgrest["area_reservations"]
                .update(StatusUpdate(status = "cancelled")) {
                    filter {
                        eq("id", reservationId)
                        eq("user_id", userId)
                    }
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "cancelReservation failed id=$reservationId", e)
            throw e
        } finally {
            _isLoading.value = false
        }
    }
}
