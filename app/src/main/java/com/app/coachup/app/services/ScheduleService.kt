package com.app.coachup.app.services

import com.app.coachup.app.config.SupabaseConfig
import com.app.coachup.app.models.QREntry
import com.app.coachup.app.models.QREntryInsert
import com.app.coachup.app.models.ScheduledProgramDB
import com.app.coachup.app.models.ScheduledProgramInsert
import com.app.coachup.app.models.StatusUpdate
import com.app.coachup.app.models.TrainingProgramIdRow
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * ScheduleService — Android production equivalent of iOS ScheduleService.swift.
 *
 * Manages:
 *  - Scheduled programs calendar ("scheduled_programs" table)
 *  - QR gym entry/exit recording ("qr_entries" table)
 *  - Active entry detection (more entries than exits today)
 *
 * All date arithmetic mirrors the iOS Calendar-based logic.
 */
object ScheduleService {

    private val supabase get() = SupabaseConfig.client

    // -----------------------------------------------------------------------
    // Reactive state
    // -----------------------------------------------------------------------

    private val _scheduledPrograms = MutableStateFlow<List<ScheduledProgramDB>>(emptyList())
    val scheduledPrograms: StateFlow<List<ScheduledProgramDB>> = _scheduledPrograms.asStateFlow()

    private val _qrEntries = MutableStateFlow<List<QREntry>>(emptyList())
    val qrEntries: StateFlow<List<QREntry>> = _qrEntries.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // -----------------------------------------------------------------------
    // Scheduled Programs — "scheduled_programs" table
    // -----------------------------------------------------------------------

    /**
     * Returns all scheduled programs for [userId] in the given [month]/[year].
     * Mirrors iOS fetchScheduledPrograms(userId:month:year:) with gte/lt on scheduled_date.
     */
    suspend fun fetchScheduledPrograms(
        userId: String,
        month: Int,
        year: Int
    ): List<ScheduledProgramDB> {
        _isLoading.value = true
        return try {
            val startOfMonth = LocalDate.of(year, month, 1)
            val endOfMonth = startOfMonth.plusMonths(1)

            val result = supabase
                .from("scheduled_programs")
                .select(columns = Columns.raw("*, program:training_programs(*), coach:coaches(*)")) {
                    filter {
                        eq("user_id", userId)
                        gte("scheduled_date", startOfMonth.toString())
                        lt("scheduled_date", endOfMonth.toString())
                    }
                    order("scheduled_date", Order.ASCENDING)
                    order("start_time", Order.ASCENDING)
                }
                .decodeList<ScheduledProgramDB>()

            _scheduledPrograms.value = result
            result
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Returns scheduled programs for a single calendar date.
     * Mirrors iOS fetchProgramsForDate(userId:date:).
     */
    suspend fun fetchScheduledPrograms(userId: String, date: LocalDate): List<ScheduledProgramDB> {
        val startOfDay = date.toString()          // YYYY-MM-DD
        val endOfDay = date.plusDays(1).toString()

        return supabase
            .from("scheduled_programs")
            .select(columns = Columns.raw("*, program:training_programs(*), coach:coaches(*)")) {
                filter {
                    eq("user_id", userId)
                    gte("scheduled_date", startOfDay)
                    lt("scheduled_date", endOfDay)
                }
                order("start_time", Order.ASCENDING)
            }
            .decodeList<ScheduledProgramDB>()
    }

    suspend fun fetchGymProgramsForDate(date: LocalDate, userId: String? = null): List<ScheduledProgramDB> {
        val gymId = UserService.resolveActiveGymIdForContent() ?: return emptyList()
        val startOfDay = date.toString()
        val endOfDay = date.plusDays(1).toString()
        val currentUserId = userId
            ?: UserService.currentProfile.value?.id
            ?: AuthService.getCurrentUserId()

        val programRows = supabase
            .from("training_programs")
            .select(columns = Columns.list("id")) {
                filter {
                    eq("gym_id", gymId)
                    if (!currentUserId.isNullOrBlank()) {
                        or {
                            eq("privacy", "public")
                            filter("visible_member_ids", FilterOperator.CS, "{\"$currentUserId\"}")
                        }
                    }
                }
            }
            .decodeList<TrainingProgramIdRow>()

        val programIds = programRows.map { it.id }
        if (programIds.isEmpty()) return emptyList()

        return supabase
            .from("scheduled_programs")
            .select(columns = Columns.raw("*, program:training_programs(*), coach:coaches(*)")) {
                filter {
                    gte("scheduled_date", startOfDay)
                    lt("scheduled_date", endOfDay)
                    isIn("program_id", programIds)
                    neq("status", "cancelled")
                }
                order("start_time", Order.ASCENDING)
            }
            .decodeList<ScheduledProgramDB>()
    }

    suspend fun fetchCalendarProgramsForDate(userId: String, date: LocalDate): List<ScheduledProgramDB> {
        val mine = fetchScheduledPrograms(userId, date)
        val gym = fetchGymProgramsForDate(date, userId = userId)
        val seen = mutableSetOf<String>()
        return (mine + gym)
            .filter { it.status != "cancelled" && seen.add(it.id) }
            .sortedBy { it.startTime }
    }

    suspend fun fetchGymPrograms(month: Int, year: Int, userId: String? = null): List<ScheduledProgramDB> {
        val gymId = UserService.resolveActiveGymIdForContent() ?: return emptyList()
        val startOfMonth = LocalDate.of(year, month, 1)
        val endOfMonth = startOfMonth.plusMonths(1)
        val currentUserId = userId
            ?: UserService.currentProfile.value?.id
            ?: AuthService.getCurrentUserId()

        val programRows = supabase
            .from("training_programs")
            .select(columns = Columns.list("id")) {
                filter {
                    eq("gym_id", gymId)
                    if (!currentUserId.isNullOrBlank()) {
                        or {
                            eq("privacy", "public")
                            filter("visible_member_ids", FilterOperator.CS, "{\"$currentUserId\"}")
                        }
                    }
                }
            }
            .decodeList<TrainingProgramIdRow>()

        val programIds = programRows.map { it.id }
        if (programIds.isEmpty()) return emptyList()

        return supabase
            .from("scheduled_programs")
            .select(columns = Columns.raw("*, program:training_programs(*), coach:coaches(*)")) {
                filter {
                    gte("scheduled_date", startOfMonth.toString())
                    lt("scheduled_date", endOfMonth.toString())
                    isIn("program_id", programIds)
                    neq("status", "cancelled")
                }
                order("scheduled_date", Order.ASCENDING)
                order("start_time", Order.ASCENDING)
            }
            .decodeList<ScheduledProgramDB>()
    }

    /**
     * Returns today's scheduled programs.
     * Mirrors iOS fetchTodayPrograms(userId:).
     */
    suspend fun fetchTodayPrograms(userId: String): List<ScheduledProgramDB> =
        fetchScheduledPrograms(userId, LocalDate.now(ZoneOffset.UTC))

    /**
     * Returns the set of day-of-month integers that have at least one scheduled program.
     * Mirrors iOS getDaysWithEvents(userId:month:year:).
     */
    suspend fun getDaysWithEvents(userId: String, month: Int, year: Int): Set<Int> {
        val mine = fetchScheduledPrograms(userId, month, year)
        val gym = fetchGymPrograms(month, year, userId = userId)
        return (mine + gym)
            .filter { it.status != "cancelled" }
            .mapNotNull { p ->
                try { LocalDate.parse(p.scheduledDate).dayOfMonth } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            }
            .toSet()
    }

    /**
     * Inserts a new scheduled program row.
     * Mirrors iOS scheduleProgram(userId:programId:coachId:date:startTime:endTime:).
     */
    suspend fun scheduleProgram(
        userId: String,
        programId: String,
        coachId: String? = null,
        date: LocalDate,
        startTime: String,  // HH:mm
        endTime: String
    ) {
        _isLoading.value = true
        try {
            supabase.from("scheduled_programs").insert(
                ScheduledProgramInsert(
                    userId = userId,
                    programId = programId,
                    coachId = coachId,
                    scheduledDate = date.toString(),
                    startTime = startTime,
                    endTime = endTime,
                    status = "scheduled"
                )
            )
        } finally {
            _isLoading.value = false
        }
    }

    /** Sets scheduled program status to "cancelled". */
    suspend fun cancelScheduledProgram(programId: String) {
        supabase.from("scheduled_programs")
            .update(StatusUpdate("cancelled")) {
                filter { eq("id", programId) }
            }
    }

    /** Sets scheduled program status to "completed". */
    suspend fun completeScheduledProgram(programId: String) {
        supabase.from("scheduled_programs")
            .update(StatusUpdate("completed")) {
                filter { eq("id", programId) }
            }
    }

    // -----------------------------------------------------------------------
    // QR Entry / Exit — "qr_entries" table
    // Mirrors iOS recordQREntry(userId:code:method:) / recordQRExit(userId:code:)
    // -----------------------------------------------------------------------

    /**
     * Records a gym ENTRY for the user.
     * Fetches the user's gym_id from their profile, then inserts a row with
     * entry_type = "entry". Mirrors iOS ScheduleService.recordQREntry exactly.
     *
     * @throws IllegalStateException if the user has no gym_id
     */
    suspend fun recordQREntry(
        userId: String,
        code: String? = null
    ): QREntry {
        _isLoading.value = true
        return try {
            val gymId = resolveGymId(userId)
            insertQRRecord(userId, gymId, "entry", code)
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Records a gym EXIT for the user.
     * Mirrors iOS ScheduleService.recordQRExit(userId:code:).
     *
     * @throws IllegalStateException if the user has no gym_id
     */
    suspend fun recordQRExit(
        userId: String,
        code: String? = null
    ): QREntry {
        _isLoading.value = true
        return try {
            val gymId = resolveGymId(userId)
            insertQRRecord(userId, gymId, "exit", code)
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Returns the last [limit] QR entries/exits ordered newest first.
     * Mirrors iOS fetchQREntries(userId:limit:).
     */
    suspend fun fetchQREntries(userId: String, limit: Int = 10): List<QREntry> {
        val result = supabase
            .from("qr_entries")
            .select {
                filter { eq("user_id", userId) }
                order("entry_date", Order.DESCENDING)
                order("entry_time", Order.DESCENDING)
                limit(limit.toLong())
            }
            .decodeList<QREntry>()
        _qrEntries.value = result
        return result
    }

    /**
     * Returns today's entries/exits for the user.
     * Mirrors iOS fetchTodayEntries(userId:).
     */
    suspend fun fetchTodayEntries(userId: String): List<QREntry> {
        val today = LocalDate.now(ZoneOffset.UTC).toString() // YYYY-MM-DD
        return supabase
            .from("qr_entries")
            .select {
                filter {
                    eq("user_id", userId)
                    eq("entry_date", today)
                }
                order("entry_time", Order.DESCENDING)
            }
            .decodeList<QREntry>()
    }

    /**
     * Returns true if there are more entry records than exit records today —
     * meaning the user is currently inside the gym.
     * Mirrors iOS hasActiveEntry(userId:).
     */
    suspend fun checkActiveEntry(userId: String): Boolean {
        val today = LocalDate.now(ZoneOffset.UTC).toString()
        val entries = supabase
            .from("qr_entries")
            .select {
                filter {
                    eq("user_id", userId)
                    eq("entry_date", today)
                }
                order("entry_time", Order.ASCENDING)
            }
            .decodeList<QREntry>()

        val entryCount = entries.count { it.entryType == "entry" }
        val exitCount = entries.count { it.entryType == "exit" }
        return entryCount > exitCount
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Resolves the gym_id for a user. Fetches the user profile if needed.
     * Throws if no gym is associated with the account.
     */
    private suspend fun resolveGymId(userId: String): String {
        val profile = UserService.fetchProfile(userId)
        return profile?.gymId
            ?: throw IllegalStateException("Kullanıcı bir salona kayıtlı değil")
    }

    private suspend fun insertQRRecord(
        userId: String,
        gymId: String,
        entryType: String,
        code: String?
    ): QREntry {
        val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")
        val now = java.time.LocalDateTime.now(ZoneOffset.UTC)

        val entryId = UUID.randomUUID().toString()
        val entryData = QREntryInsert(
            id = entryId,
            userId = userId,
            gymId = gymId,
            entryType = entryType,
            entryDate = dateFmt.format(now),
            entryTime = timeFmt.format(now),
            qrCode = code,
            location = null
        )
        supabase.from("qr_entries").insert(entryData)

        return supabase
            .from("qr_entries")
            .select { filter { eq("id", entryId) } }
            .decodeSingle<QREntry>()
    }
}
