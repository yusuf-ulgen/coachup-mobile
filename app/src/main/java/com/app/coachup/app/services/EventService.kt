package com.app.coachup.app.services

import com.app.coachup.app.config.SupabaseConfig
import com.app.coachup.app.models.UserEvent
import com.app.coachup.app.models.UserEventInsert
import com.app.coachup.app.models.UserEventUpdate
import com.app.coachup.app.utils.formatTimeHm
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order

object EventService {

    private val client get() = SupabaseConfig.client

    suspend fun fetchEventsForDate(userId: String, dateStr: String): List<UserEvent> {
        return client.from("user_events")
            .select {
                filter {
                    eq("user_id", userId)
                    eq("event_date", dateStr)
                }
                order("start_time", Order.ASCENDING)
            }
            .decodeList<UserEvent>()
            .map { event ->
                event.copy(
                    startTime = formatTimeHm(event.startTime).ifBlank { null },
                    endTime = formatTimeHm(event.endTime).ifBlank { null }
                )
            }
    }

    suspend fun fetchEventDays(userId: String, year: Int, month: Int): Set<Int> {
        val startDate = "%04d-%02d-01".format(year, month)
        val nextMonth = if (month == 12) 1 else month + 1
        val nextYear  = if (month == 12) year + 1 else year
        val endDate   = "%04d-%02d-01".format(nextYear, nextMonth)

        val events: List<UserEvent> = client.from("user_events")
            .select {
                filter {
                    eq("user_id", userId)
                    gte("event_date", startDate)
                    lt("event_date", endDate)
                }
            }
            .decodeList()

        return events.mapNotNull { event ->
            val parts = event.eventDate.split("-")
            if (parts.size == 3) parts[2].toIntOrNull() else null
        }.toSet()
    }

    suspend fun createEvent(
        userId: String,
        title: String,
        note: String?,
        eventDate: String,
        startTime: String?,
        endTime: String?,
        icon: String,
        color: String
    ) {
        val payload = UserEventInsert(
            userId = userId,
            title = title,
            note = note?.takeIf { it.isNotBlank() },
            eventDate = eventDate,
            startTime = formatTimeHm(startTime).ifBlank { null },
            endTime = formatTimeHm(endTime).ifBlank { null },
            icon = icon,
            color = color
        )
        client.from("user_events").insert(payload)
    }

    suspend fun updateEvent(event: UserEvent) {
        val payload = UserEventUpdate(
            title = event.title,
            note = event.note,
            eventDate = event.eventDate,
            startTime = formatTimeHm(event.startTime).ifBlank { null },
            endTime = formatTimeHm(event.endTime).ifBlank { null },
            icon = event.icon,
            color = event.color
        )
        client.from("user_events")
            .update(payload) {
                filter { eq("id", event.id) }
            }
    }

    suspend fun deleteEvent(id: String) {
        client.from("user_events")
            .delete {
                filter { eq("id", id) }
            }
    }
}
