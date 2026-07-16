package com.app.coachup.app.services

import com.app.coachup.app.config.GymConfig
import com.app.coachup.app.config.SupabaseConfig.client
import com.app.coachup.app.models.EventParticipant
import com.app.coachup.app.models.EventParticipantInsert
import com.app.coachup.app.models.GymEvent
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.YearMonth

/**
 * Admin panelinden eklenen salon etkinlikleri (gym_events tablosu).
 */
object GymEventService {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    suspend fun fetchEventsForDate(date: LocalDate): List<GymEvent> {
        val gymId = UserService.resolveActiveGymIdForContent() ?: return emptyList()
        val dateStr = date.toString()
        return try {
            client.postgrest["gym_events"]
                .select {
                    filter {
                        eq("gym_id", gymId)
                        eq("event_date", dateStr)
                        eq("status", "active")
                    }
                    order("start_time", Order.ASCENDING)
                }
                .decodeList<GymEvent>()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchEventDaysInMonth(yearMonth: YearMonth): Set<Int> {
        val gymId = UserService.resolveActiveGymIdForContent() ?: return emptySet()
        val startDate = yearMonth.atDay(1).toString()
        val endDate = yearMonth.plusMonths(1).atDay(1).toString()
        return try {
            client.postgrest["gym_events"]
                .select(columns = io.github.jan.supabase.postgrest.query.Columns.list("event_date")) {
                    filter {
                        eq("gym_id", gymId)
                        eq("status", "active")
                        gte("event_date", startDate)
                        lt("event_date", endDate)
                    }
                }
                .decodeList<GymEvent>()
                .mapNotNull { event ->
                    event.eventDate.split("-").getOrNull(2)?.toIntOrNull()
                }
                .toSet()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptySet()
        }
    }

    suspend fun fetchParticipationsForDate(userId: String, date: LocalDate): List<EventParticipant> {
        val events = fetchEventsForDate(date)
        val eventIds = events.map { it.id }
        if (eventIds.isEmpty()) return emptyList()
        return try {
            client.postgrest["event_participants"]
                .select {
                    filter {
                        eq("user_id", userId)
                        isIn("event_id", eventIds)
                        neq("status", "cancelled")
                    }
                }
                .decodeList<EventParticipant>()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun joinEvent(userId: String, eventId: String) {
        _isLoading.value = true
        try {
            val existing = client.postgrest["event_participants"]
                .select(columns = io.github.jan.supabase.postgrest.query.Columns.list("id")) {
                    filter {
                        eq("user_id", userId)
                        eq("event_id", eventId)
                    }
                    limit(1)
                }
                .decodeList<EventParticipant>()
            if (existing.isNotEmpty()) return

            client.postgrest["event_participants"].insert(
                EventParticipantInsert(userId = userId, eventId = eventId)
            )
        } catch (e: CancellationException) {
            throw e
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun leaveEvent(participantId: String, userId: String) {
        _isLoading.value = true
        try {
            client.postgrest["event_participants"]
                .delete {
                    filter {
                        eq("id", participantId)
                        eq("user_id", userId)
                    }
                }
        } catch (e: CancellationException) {
            throw e
        } finally {
            _isLoading.value = false
        }
    }
}
