package com.app.coachup.app.services

import com.app.coachup.app.config.ApiLimits
import com.app.coachup.app.config.SupabaseConfig
import com.app.coachup.app.models.BookingCountUpdate
import com.app.coachup.app.models.Coach
import com.app.coachup.app.models.CoachMessage
import com.app.coachup.app.models.CoachMessageInsert
import com.app.coachup.app.models.CoachSchedule
import com.app.coachup.app.models.IsReadUpdate
import com.app.coachup.app.models.TrainingSessionInsert
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.util.UUID

/**
 * CoachService — Android production equivalent of iOS CoachService.swift.
 *
 * Handles:
 *  - Fetching coaches with optional gender/search filters
 *  - Coach schedule lookup
 *  - Session booking
 *  - Coach ↔ user messaging with read-receipt tracking
 */
object CoachService {

    private val supabase get() = SupabaseConfig.client

    // -----------------------------------------------------------------------
    // Reactive state
    // -----------------------------------------------------------------------

    private val _coaches = MutableStateFlow<List<Coach>>(emptyList())
    val coaches: StateFlow<List<Coach>> = _coaches.asStateFlow()

    private val _selectedCoach = MutableStateFlow<Coach?>(null)
    val selectedCoach: StateFlow<Coach?> = _selectedCoach.asStateFlow()

    private val _coachSchedule = MutableStateFlow<List<CoachSchedule>>(emptyList())
    val coachSchedule: StateFlow<List<CoachSchedule>> = _coachSchedule.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // -----------------------------------------------------------------------
    // Fetch Coaches — "coaches" table
    // Mirrors iOS fetchCoaches() and fetchCoaches(gender:searchText:)
    // -----------------------------------------------------------------------

    /**
     * Returns active coaches for the current user context.
     * Bireysel: admin ataması veya platform koçları (gym_id yok).
     * Salon üyesi: yalnızca kendi salonunun koçları.
     */
    suspend fun fetchCoaches(
        gymId: String? = null,
        gender: String? = null,
        searchText: String? = null,
        limit: Int = ApiLimits.COACHES
    ): List<Coach> {
        val profile = UserService.currentProfile.value ?: return emptyList()
        if (UserService.isIndividualUser(profile)) {
            return fetchIndividualCoaches(gender, searchText, limit)
        }
        val resolvedGymId = gymId ?: UserService.resolveActiveGymIdForContent(profile)
            ?: return emptyList()
        return fetchGymCoaches(resolvedGymId, gender, searchText, limit)
    }

    private suspend fun fetchGymCoaches(
        gymId: String,
        gender: String? = null,
        searchText: String? = null,
        limit: Int = ApiLimits.COACHES
    ): List<Coach> {
        _isLoading.value = true
        return try {
            val result = supabase
                .from("coaches")
                .select {
                    filter {
                        eq("is_active", true)
                        eq("gym_id", gymId)
                        if (!gender.isNullOrEmpty() && gender != "all") {
                            eq("gender", gender)
                        }
                        if (!searchText.isNullOrEmpty()) {
                            or {
                                ilike("name", "%$searchText%")
                                ilike("surname", "%$searchText%")
                            }
                        }
                    }
                    limit(limit.toLong())
                }
                .decodeList<Coach>()
            _coaches.value = result
            result
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun fetchIndividualCoaches(
        gender: String? = null,
        searchText: String? = null,
        limit: Int = ApiLimits.COACHES
    ): List<Coach> {
        _isLoading.value = true
        return try {
            val userId = AuthService.getCurrentUserId() ?: return emptyList()
            val assignedIds = fetchAssignedCoachIds(userId)
            if (assignedIds.isEmpty()) {
                _coaches.value = emptyList()
                return emptyList()
            }

            val coaches = supabase.from("coaches").select {
                filter {
                    eq("is_active", true)
                    isIn("id", assignedIds.toList())
                }
            }.decodeList<Coach>()

            val filtered = coaches.filter { coach ->
                val genderOk = gender.isNullOrEmpty() || gender == "all" || coach.gender == gender
                val searchOk = searchText.isNullOrEmpty() ||
                    coach.name.contains(searchText, ignoreCase = true) ||
                    coach.surname.contains(searchText, ignoreCase = true)
                genderOk && searchOk
            }.take(limit)

            _coaches.value = filtered
            filtered
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun fetchAssignedCoachIds(userId: String): Set<String> {
        return runCatching {
            supabase.from("user_coach_assignments")
                .select(columns = io.github.jan.supabase.postgrest.query.Columns.list("coach_id")) {
                    filter { eq("user_id", userId) }
                }
                .decodeList<CoachIdRow>()
                .mapNotNull { it.coachId }
                .toSet()
        }.getOrDefault(emptySet())
    }

    @kotlinx.serialization.Serializable
    private data class CoachIdRow(
        @kotlinx.serialization.SerialName("coach_id") val coachId: String? = null
    )

    // Legacy entry point kept for admin / explicit gym queries
    suspend fun fetchCoachesForGym(
        gymId: String = SupabaseConfig.GYM_ID,
        gender: String? = null,
        searchText: String? = null,
        limit: Int = ApiLimits.COACHES
    ): List<Coach> = fetchGymCoaches(gymId, gender, searchText, limit)

    // -----------------------------------------------------------------------
    // Fetch Single Coach
    // Mirrors iOS fetchCoach(id:)
    // -----------------------------------------------------------------------

    /**
     * Returns a single coach by primary key.
     */
    suspend fun fetchCoachDetail(coachId: String): Coach? {
        _isLoading.value = true
        return try {
            val coach = supabase
                .from("coaches")
                .select {
                    filter { eq("id", coachId) }
                }
                .decodeSingle<Coach>()
            _selectedCoach.value = coach
            coach
        } finally {
            _isLoading.value = false
        }
    }

    // -----------------------------------------------------------------------
    // Coach Schedule — "coach_schedules" table
    // Mirrors iOS fetchCoachSchedule(coachId:) and fetchCoachScheduleForDay(coachId:dayOfWeek:)
    // -----------------------------------------------------------------------

    /**
     * Returns all schedule slots for a coach ordered by day then start time.
     */
    suspend fun fetchCoachSchedules(coachId: String): List<CoachSchedule> {
        _isLoading.value = true
        return try {
            val result = supabase
                .from("coach_schedules")
                .select {
                    filter { eq("coach_id", coachId) }
                    order("day_of_week", Order.ASCENDING)
                    order("start_time", Order.ASCENDING)
                }
                .decodeList<CoachSchedule>()
            _coachSchedule.value = result
            result
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Returns schedule slots for a specific day of the week.
     * [dayOfWeek] uses 0 = Monday … 6 = Sunday (matches iOS CoachSchedule).
     */
    suspend fun fetchCoachScheduleForDay(coachId: String, dayOfWeek: Int): List<CoachSchedule> {
        return supabase
            .from("coach_schedules")
            .select {
                filter {
                    eq("coach_id", coachId)
                    eq("day_of_week", dayOfWeek)
                }
                order("start_time", Order.ASCENDING)
            }
            .decodeList<CoachSchedule>()
    }

    // -----------------------------------------------------------------------
    // Book Session
    // Mirrors iOS CoachService.bookSession(userId:coachId:scheduleId:date:)
    // -----------------------------------------------------------------------

    /**
     * Books a training session with a coach at a given schedule slot.
     * Checks capacity before inserting and increments booking count on success.
     *
     * @throws IllegalStateException when the slot is full (mirrors iOS NSError code 1)
     */
    suspend fun bookSession(
        userId: String,
        coachId: String,
        scheduleId: String,
        scheduledAt: String   // ISO-8601 date-time string
    ) {
        // 1. Load the schedule slot
        val schedule = supabase
            .from("coach_schedules")
            .select {
                filter { eq("id", scheduleId) }
            }
            .decodeSingle<CoachSchedule>()

        val currentBookings = schedule.currentBookings ?: 0
        val maxCapacity = schedule.maxCapacity ?: 1

        if (currentBookings >= maxCapacity) {
            throw IllegalStateException("Bu seans dolu")
        }

        // 2. Insert training session
        val session = TrainingSessionInsert(
            id = UUID.randomUUID().toString(),
            userId = userId,
            programId = null,
            coachId = coachId,
            scheduledAt = scheduledAt,
            startedAt = null,
            status = "scheduled"
        )
        supabase.from("training_sessions").insert(session)

        // 3. Increment booking count
        supabase.from("coach_schedules")
            .update(BookingCountUpdate(currentBookings + 1)) {
                filter { eq("id", scheduleId) }
            }
    }

    // -----------------------------------------------------------------------
    // Messaging — "coach_messages" table
    // Mirrors iOS sendMessageToCoach / fetchMessages / markMessagesAsRead / fetchUnreadMessageCount
    // -----------------------------------------------------------------------

    /**
     * Inserts a new message from the user to a coach and returns the persisted row shape.
     */
    suspend fun sendMessage(userId: String, coachId: String, message: String): CoachMessage {
        val sentAt = Instant.now().toString()
        val msg = CoachMessageInsert(
            userId = userId,
            coachId = coachId,
            message = message,
            sentAt = sentAt
        )
        return supabase.from("coach_messages").insert(msg) {
            select()
        }.decodeSingle<CoachMessage>()
    }

    /**
     * Returns the most recent messages in a conversation, ordered chronologically.
     * Joins coach data via "coach:coaches(*)".
     */
    suspend fun fetchMessages(
        userId: String,
        coachId: String,
        limit: Int = ApiLimits.COACH_MESSAGES
    ): List<CoachMessage> {
        val recent = supabase
            .from("coach_messages")
            .select(columns = io.github.jan.supabase.postgrest.query.Columns.raw("*, coach:coaches(*)")) {
                filter {
                    eq("user_id", userId)
                    eq("coach_id", coachId)
                }
                order("sent_at", Order.DESCENDING)
                limit(limit.toLong())
            }
            .decodeList<CoachMessage>()
        return recent.asReversed()
    }

    /**
     * Marks all unread coach-originated messages in a conversation as read.
     * Mirrors iOS markMessagesAsRead(userId:coachId:).
     */
    suspend fun markAsRead(userId: String, coachId: String) {
        supabase.from("coach_messages")
            .update(IsReadUpdate(isRead = true)) {
                filter {
                    eq("user_id", userId)
                    eq("coach_id", coachId)
                    eq("is_from_coach", true)
                    eq("is_read", false)
                }
            }
    }

    /**
     * Returns the number of unread messages from a coach.
     * Mirrors iOS fetchUnreadMessageCount(userId:coachId:).
     */
    suspend fun getUnreadCount(userId: String, coachId: String): Int {
        return supabase
            .from("coach_messages")
            .select {
                filter {
                    eq("user_id", userId)
                    eq("coach_id", coachId)
                    eq("is_from_coach", true)
                    eq("is_read", false)
                }
            }
            .decodeList<CoachMessage>()
            .size
    }
}
