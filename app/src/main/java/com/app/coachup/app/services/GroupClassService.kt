package com.app.coachup.app.services

import com.app.coachup.app.config.ApiLimits
import com.app.coachup.app.config.GymConfig
import com.app.coachup.app.config.SupabaseConfig.client
import com.app.coachup.app.models.ClassBooking
import com.app.coachup.app.models.ClassBookingInsert
import com.app.coachup.app.models.GroupClass
import com.app.coachup.app.utils.CalendarDayOfWeek
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException

/**
 * Android equivalent of iOS GroupClassService.
 *
 * Mirrors tables: group_classes, class_bookings.
 * Status: booked | confirmed | waiting | attended | cancelled | no_show
 */
object GroupClassService {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    enum class JoinResult { CONFIRMED, WAITING, ALREADY_JOINED }

    private val ACTIVE_SEAT_STATUSES = setOf("booked", "confirmed", "attended")
    private val JOINED_STATUSES = setOf("booked", "confirmed", "waiting", "attended")

    fun isActiveSeat(status: String): Boolean = status.lowercase() in ACTIVE_SEAT_STATUSES
    fun isJoinedStatus(status: String): Boolean = status.lowercase() in JOINED_STATUSES
    fun isWaitingStatus(status: String): Boolean = status.equals("waiting", ignoreCase = true)

    // -------------------------------------------------------------------------
    // Fetch Classes
    // -------------------------------------------------------------------------

    suspend fun fetchClasses(): List<GroupClass> = fetchClassesForGym()

    private suspend fun fetchClassesForGym(): List<GroupClass> {
        return try {
            client.postgrest["group_classes"]
                .select {
                    filter { eq("is_active", true) }
                    order("day_of_week", Order.ASCENDING)
                }
                .decodeList<GroupClass>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun fetchClassesByDay(dayOfWeek: Int): List<GroupClass> {
        return fetchClassesForGym().filter { matchesStoredDay(it.dayOfWeek, appDay = dayOfWeek) }
    }

    suspend fun fetchClassesForDate(date: java.time.LocalDate): List<GroupClass> {
        return fetchClassesForGym()
            .filter { CalendarDayOfWeek.classMatchesDate(it.dayOfWeek, date) }
            .sortedBy { it.startTime }
    }

    private fun matchesStoredDay(storedDay: Int, appDay: Int): Boolean {
        val jsDay = (appDay + 1) % 7
        return storedDay == appDay || storedDay == jsDay
    }

    // -------------------------------------------------------------------------
    // Bookings
    // -------------------------------------------------------------------------

    /**
     * Join or waitlist. If capacity full → status=waiting, else booked.
     */
    suspend fun joinClass(userId: String, classId: String, date: String): JoinResult {
        _isLoading.value = true
        try {
            val existing = fetchBookingsForDate(userId, java.time.LocalDate.parse(date))
                .firstOrNull { it.classId == classId && isJoinedStatus(it.status) }
            if (existing != null) return JoinResult.ALREADY_JOINED

            val groupClass = fetchClassesByIds(listOf(classId)).firstOrNull()
            val capacity = groupClass?.capacity ?: Int.MAX_VALUE
            val current = countActiveSeats(classId, date)
            val status = if (current >= capacity) "waiting" else "booked"

            val insert = ClassBookingInsert(
                userId = userId,
                classId = classId,
                gymId = UserService.resolveActiveGymIdForContent() ?: GymConfig.GYM_ID,
                bookingDate = date,
                status = status
            )
            client.postgrest["class_bookings"].insert(insert)
            return if (status == "waiting") JoinResult.WAITING else JoinResult.CONFIRMED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun leaveClass(bookingId: String, userId: String) {
        _isLoading.value = true
        try {
            val booking = client.postgrest["class_bookings"]
                .select {
                    filter {
                        eq("id", bookingId)
                        eq("user_id", userId)
                    }
                    limit(1)
                }
                .decodeList<ClassBooking>()
                .firstOrNull()

            client.postgrest["class_bookings"]
                .delete {
                    filter {
                        eq("id", bookingId)
                        eq("user_id", userId)
                    }
                }

            // Promote oldest waiting member when a seat frees up
            if (booking != null && isActiveSeat(booking.status)) {
                promoteNextWaiting(booking.classId, booking.bookingDate)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun promoteNextWaiting(classId: String, bookingDate: String) {
        try {
            val waiting = client.postgrest["class_bookings"]
                .select {
                    filter {
                        eq("class_id", classId)
                        eq("booking_date", bookingDate)
                        eq("status", "waiting")
                    }
                    order("created_at", Order.ASCENDING)
                    limit(1)
                }
                .decodeList<ClassBooking>()
                .firstOrNull() ?: return

            client.postgrest["class_bookings"]
                .update(mapOf("status" to "booked")) {
                    filter { eq("id", waiting.id) }
                }
        } catch (_: Exception) {
            // Best-effort promote; ignore if RLS/schema blocks
        }
    }

    /** Confirmed seats for class+date (excludes waiting). */
    suspend fun countActiveSeats(classId: String, date: String): Int {
        return try {
            client.postgrest["class_bookings"]
                .select {
                    filter {
                        eq("class_id", classId)
                        eq("booking_date", date)
                        isIn("status", ACTIVE_SEAT_STATUSES.toList())
                    }
                }
                .decodeList<ClassBooking>()
                .size
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            0
        }
    }

    /** Map classId → active seat count for a date. */
    suspend fun countActiveSeatsForClasses(classIds: List<String>, date: String): Map<String, Int> {
        if (classIds.isEmpty()) return emptyMap()
        return try {
            client.postgrest["class_bookings"]
                .select {
                    filter {
                        isIn("class_id", classIds)
                        eq("booking_date", date)
                        isIn("status", ACTIVE_SEAT_STATUSES.toList())
                    }
                }
                .decodeList<ClassBooking>()
                .groupingBy { it.classId }
                .eachCount()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyMap()
        }
    }

    suspend fun fetchMyBookings(
        userId: String,
        limit: Int = ApiLimits.CLASS_BOOKINGS
    ): List<ClassBooking> {
        return try {
            client.postgrest["class_bookings"]
                .select {
                    filter {
                        eq("user_id", userId)
                        neq("status", "cancelled")
                    }
                    order("booking_date", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<ClassBooking>()
                .filter { isJoinedStatus(it.status) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun fetchBookingsForDate(userId: String, date: java.time.LocalDate): List<ClassBooking> {
        val dateStr = date.toString()
        return try {
            client.postgrest["class_bookings"]
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("booking_date", dateStr)
                        neq("status", "cancelled")
                    }
                }
                .decodeList<ClassBooking>()
                .filter { isJoinedStatus(it.status) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchClassesByIds(classIds: List<String>): List<GroupClass> {
        if (classIds.isEmpty()) return emptyList()
        return try {
            client.postgrest["group_classes"]
                .select {
                    filter { isIn("id", classIds) }
                }
                .decodeList<GroupClass>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Days of month that have at least one active group class (by day-of-week). */
    suspend fun fetchClassDaysInMonth(yearMonth: java.time.YearMonth): Set<Int> {
        return try {
            val all = fetchClassesForGym()
            if (all.isEmpty()) return emptySet()
            (1..yearMonth.lengthOfMonth()).filter { day ->
                val date = java.time.LocalDate.of(yearMonth.year, yearMonth.monthValue, day)
                all.any { CalendarDayOfWeek.classMatchesDate(it.dayOfWeek, date) }
            }.toSet()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptySet()
        }
    }

    /** Days of month where the user has a non-cancelled class booking. */
    suspend fun fetchBookedDaysInMonth(userId: String, yearMonth: java.time.YearMonth): Set<Int> {
        val startDate = yearMonth.atDay(1).toString()
        val endDate = yearMonth.plusMonths(1).atDay(1).toString()
        return try {
            client.postgrest["class_bookings"]
                .select {
                    filter {
                        eq("user_id", userId)
                        gte("booking_date", startDate)
                        lt("booking_date", endDate)
                        neq("status", "cancelled")
                    }
                }
                .decodeList<ClassBooking>()
                .mapNotNull { booking ->
                    booking.bookingDate.split("-").getOrNull(2)?.toIntOrNull()
                }
                .toSet()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptySet()
        }
    }
}
