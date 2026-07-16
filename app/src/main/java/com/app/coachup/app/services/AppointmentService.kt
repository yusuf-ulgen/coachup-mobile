package com.app.coachup.app.services

import com.app.coachup.app.config.GymConfig
import com.app.coachup.app.config.SupabaseConfig.client
import com.app.coachup.app.models.Appointment
import com.app.coachup.app.models.AppointmentCancelUpdate
import com.app.coachup.app.models.AppointmentInsert
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Android equivalent of iOS AppointmentService.
 *
 * Mirrors table: appointments.
 */
object AppointmentService {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // -------------------------------------------------------------------------
    // Fetch
    // -------------------------------------------------------------------------

    suspend fun fetchAppointments(userId: String): List<Appointment> {
        return try {
            client.postgrest["appointments"]
                .select {
                    filter { eq("user_id", userId) }
                    order("date", Order.ASCENDING)
                }
                .decodeList<Appointment>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun fetchUpcomingAppointments(userId: String): List<Appointment> {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        return try {
            client.postgrest["appointments"]
                .select {
                    filter {
                        eq("user_id", userId)
                        gte("date", today)
                        neq("status", "cancelled")
                    }
                    order("date", Order.ASCENDING)
                }
                .decodeList<Appointment>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e
        }
    }

    // -------------------------------------------------------------------------
    // Book
    // -------------------------------------------------------------------------

    suspend fun bookAppointment(
        userId: String,
        coachId: String?,
        date: String,
        startTime: String,
        endTime: String,
        type: String,
        notes: String? = null
    ) {
        _isLoading.value = true
        try {
            val insert = AppointmentInsert(
                userId = userId,
                coachId = coachId,
                gymId = GymConfig.GYM_ID,
                date = date,
                startTime = startTime,
                endTime = endTime,
                type = type,
                notes = notes,
                status = "pending"
            )
            client.postgrest["appointments"].insert(insert)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e
        } finally {
            _isLoading.value = false
        }
    }

    // -------------------------------------------------------------------------
    // Cancel
    // -------------------------------------------------------------------------

    suspend fun cancelAppointment(
        appointmentId: String,
        userId: String,
        reason: String? = null
    ) {
        _isLoading.value = true
        try {
            client.postgrest["appointments"]
                .update(AppointmentCancelUpdate(status = "cancelled", cancelReason = reason)) {
                    filter {
                        eq("id", appointmentId)
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
}
