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
 * Participant status: registered | waiting | cancelled
 */
object GymEventService {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    enum class JoinResult { CONFIRMED, WAITING, ALREADY_JOINED }

    private val ACTIVE_SEAT_STATUSES = setOf("registered")
    private val JOINED_STATUSES = setOf("registered", "waiting")

    fun isJoinedStatus(status: String): Boolean = status.lowercase() in JOINED_STATUSES
    fun isWaitingStatus(status: String): Boolean = status.equals("waiting", ignoreCase = true)
    fun isActiveSeat(status: String): Boolean = status.lowercase() in ACTIVE_SEAT_STATUSES

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
                .filter { isJoinedStatus(it.status) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun countRegistered(eventId: String): Int {
        return try {
            client.postgrest["event_participants"]
                .select {
                    filter {
                        eq("event_id", eventId)
                        eq("status", "registered")
                    }
                }
                .decodeList<EventParticipant>()
                .size
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            0
        }
    }

    suspend fun countRegisteredForEvents(eventIds: List<String>): Map<String, Int> {
        if (eventIds.isEmpty()) return emptyMap()
        return try {
            client.postgrest["event_participants"]
                .select {
                    filter {
                        isIn("event_id", eventIds)
                        eq("status", "registered")
                    }
                }
                .decodeList<EventParticipant>()
                .groupingBy { it.eventId }
                .eachCount()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyMap()
        }
    }

    suspend fun joinEvent(userId: String, eventId: String): JoinResult {
        _isLoading.value = true
        try {
            val existing = client.postgrest["event_participants"]
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("event_id", eventId)
                        neq("status", "cancelled")
                    }
                    limit(1)
                }
                .decodeList<EventParticipant>()
                .firstOrNull { isJoinedStatus(it.status) }
            if (existing != null) return JoinResult.ALREADY_JOINED

            val event = client.postgrest["gym_events"]
                .select {
                    filter { eq("id", eventId) }
                    limit(1)
                }
                .decodeList<GymEvent>()
                .firstOrNull()

            val capacity = event?.capacity
            val current = countRegistered(eventId)
            val status = if (capacity != null && current >= capacity) "waiting" else "registered"

            client.postgrest["event_participants"].insert(
                EventParticipantInsert(userId = userId, eventId = eventId, status = status)
            )
            return if (status == "waiting") JoinResult.WAITING else JoinResult.CONFIRMED
        } catch (e: CancellationException) {
            throw e
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun leaveEvent(participantId: String, userId: String) {
        _isLoading.value = true
        try {
            val participant = client.postgrest["event_participants"]
                .select {
                    filter {
                        eq("id", participantId)
                        eq("user_id", userId)
                    }
                    limit(1)
                }
                .decodeList<EventParticipant>()
                .firstOrNull()

            client.postgrest["event_participants"]
                .delete {
                    filter {
                        eq("id", participantId)
                        eq("user_id", userId)
                    }
                }

            if (participant != null && isActiveSeat(participant.status)) {
                promoteNextWaiting(participant.eventId)
            }
        } catch (e: CancellationException) {
            throw e
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun promoteNextWaiting(eventId: String) {
        try {
            val waiting = client.postgrest["event_participants"]
                .select {
                    filter {
                        eq("event_id", eventId)
                        eq("status", "waiting")
                    }
                    order("id", Order.ASCENDING)
                    limit(1)
                }
                .decodeList<EventParticipant>()
                .firstOrNull() ?: return

            client.postgrest["event_participants"]
                .update(mapOf("status" to "registered")) {
                    filter { eq("id", waiting.id) }
                }
        } catch (_: Exception) {
        }
    }
}
