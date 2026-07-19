package com.app.coachup.app.services

import com.app.coachup.app.config.ApiLimits
import com.app.coachup.app.config.SupabaseConfig
import com.app.coachup.app.models.SessionCompleteUpdate
import com.app.coachup.app.models.SessionStats
import com.app.coachup.app.models.StatusUpdate
import com.app.coachup.app.models.Training
import com.app.coachup.app.models.TrainingCategory
import com.app.coachup.app.models.BuiltInActivities
import com.app.coachup.app.models.TrainingProgram
import com.app.coachup.app.models.TrainingSession
import com.app.coachup.app.models.TrainingSessionInsert
import com.app.coachup.app.models.TrainingSource
import com.app.coachup.app.models.UserAssignedProgram
import com.app.coachup.app.services.AuthService
import com.app.coachup.app.services.UserService
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/**
 * TrainingService — Android production equivalent of iOS TrainingService.swift.
 *
 * Manages training programs and user sessions. All date strings are ISO-8601 UTC
 * to match the iOS behaviour.
 */
object TrainingService {

    private val supabase get() = SupabaseConfig.client

    // -----------------------------------------------------------------------
    // Reactive state — mirrors iOS @Published vars
    // -----------------------------------------------------------------------

    private val _programs = MutableStateFlow<List<TrainingProgram>>(emptyList())
    val programs: StateFlow<List<TrainingProgram>> = _programs.asStateFlow()

    private val _userSessions = MutableStateFlow<List<TrainingSession>>(emptyList())
    val userSessions: StateFlow<List<TrainingSession>> = _userSessions.asStateFlow()

    private val _currentSession = MutableStateFlow<TrainingSession?>(null)
    val currentSession: StateFlow<TrainingSession?> = _currentSession.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // -----------------------------------------------------------------------
    // Fetch Programs — "training_programs" table
    // -----------------------------------------------------------------------

    /**
     * Returns active training programs, optionally filtered (capped at [limit]).
     */
    suspend fun fetchPrograms(
        category: String? = null,
        searchText: String? = null,
        limit: Int = ApiLimits.TRAINING_PROGRAMS
    ): List<TrainingProgram> {
        _isLoading.value = true
        return try {
            val result = supabase
                .from("training_programs")
                .select {
                    filter {
                        eq("is_active", true)
                        if (!category.isNullOrEmpty() && category.lowercase() != "all") {
                            eq("category", category)
                        }
                        if (!searchText.isNullOrEmpty()) {
                            ilike("name", "%$searchText%")
                        }
                    }
                    order("name", Order.ASCENDING)
                    limit(limit.toLong())
                }
                .decodeList<TrainingProgram>()
            _programs.value = result
            result
        } finally {
            _isLoading.value = false
        }
    }

    /** Fetches a single program by primary key. */
    suspend fun fetchProgram(programId: String): TrainingProgram? {
        return supabase
            .from("training_programs")
            .select { filter { eq("id", programId) } }
            .decodeSingle<TrainingProgram>()
    }

    /** Salon tarafından tanımlanan programlar. Salonun tüm aktif antrenmanlarını getirir. */
    suspend fun fetchGymPrograms(
        gymId: String?,
        userId: String? = null,
        searchText: String? = null,
        limit: Int = ApiLimits.TRAINING_PROGRAMS
    ): List<TrainingProgram> {
        if (gymId.isNullOrBlank()) return emptyList()
        val currentUserId = userId
            ?: UserService.currentProfile.value?.id
            ?: AuthService.getCurrentUserId()

        return try {
            supabase
                .from("training_programs")
                .select {
                    filter {
                        eq("gym_id", gymId)
                        eq("is_active", true)
                        neq("category", TrainingCategory.AI_PROGRAM.dbValue)
                        if (!currentUserId.isNullOrBlank()) {
                            or {
                                eq("privacy", "public")
                                filter("visible_member_ids", FilterOperator.CS, "{\"$currentUserId\"}")
                            }
                        } else {
                            eq("privacy", "public")
                        }
                        if (!searchText.isNullOrEmpty()) {
                            ilike("name", "%$searchText%")
                        }
                    }
                    order("name", Order.ASCENDING)
                    limit(limit.toLong())
                }
                .decodeList<TrainingProgram>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("TrainingService", "fetchGymPrograms failed: ${e.message}", e)
            emptyList()
        }
    }


    /** Kişisel AI programları. */
    suspend fun fetchAiPrograms(
        searchText: String? = null,
        limit: Int = ApiLimits.TRAINING_PROGRAMS
    ): List<TrainingProgram> {
        val currentUserId = UserService.currentProfile.value?.id
            ?: AuthService.getCurrentUserId()
        val gymId = UserService.currentProfile.value?.gymId
            ?: UserService.resolveActiveGymIdForContent()
        return supabase
            .from("training_programs")
            .select {
                filter {
                    eq("is_active", true)
                    eq("category", TrainingCategory.AI_PROGRAM.dbValue)
                    // Filter AI programs to only those belonging to user's gym or assigned to user
                    if (!gymId.isNullOrBlank()) {
                        eq("gym_id", gymId)
                    }
                    if (!currentUserId.isNullOrBlank()) {
                        or {
                            eq("privacy", "public")
                            filter("visible_member_ids", FilterOperator.CS, "{\"$currentUserId\"}")
                        }
                    } else {
                        eq("privacy", "public")
                    }
                    if (!searchText.isNullOrEmpty()) {
                        ilike("name", "%$searchText%")
                    }
                }
                order("name", Order.ASCENDING)
                limit(limit.toLong())
            }
            .decodeList<TrainingProgram>()
    }

    suspend fun fetchProgramExerciseNames(programId: String): List<String> =
        WorkoutService.fetchExercises(programId).mapNotNull { pe ->
            pe.exercise?.name?.takeIf { it.isNotBlank() }
        }

    suspend fun loadProgramTraining(
        program: TrainingProgram,
        source: TrainingSource
    ): Training {
        val names = runCatching { fetchProgramExerciseNames(program.id) }.getOrDefault(emptyList())
        return Training.fromProgram(program, source, names)
    }

    /**
     * Yerleşik aktivite veya salon/AI programı için oturum başlatır.
     * Yerleşik aktivitelerde program_id opsiyoneldir.
     */
    suspend fun startActivity(userId: String, training: Training): TrainingSession {
        if (!training.isBuiltIn) {
            return startSession(userId = userId, programId = training.id)
        }
        return startBuiltinSession(userId, training)
    }

    private suspend fun startBuiltinSession(userId: String, training: Training): TrainingSession {
        _isLoading.value = true
        return try {
            val resolvedUserId = AuthService.getCurrentUserId() ?: userId
            val sessionId = UUID.randomUUID().toString()
            val now = Instant.now().toString()
            val notes = "builtin:${training.category.dbValue}"
            val baseInsert = newSessionInsert(
                sessionId = sessionId,
                userId = resolvedUserId,
                scheduledAt = now,
                startedAt = now,
                status = "in_progress",
                notes = notes
            )

            insertBuiltinSessionWithFallback(sessionId, baseInsert, training)

            val session = fetchSessionById(sessionId)
            _currentSession.value = session
            session
        } finally {
            _isLoading.value = false
        }
    }

    private fun newSessionInsert(
        sessionId: String,
        userId: String,
        programId: String? = null,
        coachId: String? = null,
        scheduledAt: String,
        startedAt: String? = null,
        status: String,
        notes: String? = null
    ) = TrainingSessionInsert(
        id = sessionId,
        userId = userId,
        gymId = UserService.resolveActiveGymIdForContent(),
        programId = programId,
        coachId = coachId,
        scheduledAt = scheduledAt,
        startedAt = startedAt,
        status = status,
        notes = notes
    )

    /**
     * Yerleşik aktivite oturumu — önce program_id olmadan dener (migration gerekir),
     * ardından mevcut program ID'leri ile FK uyumlu ekler.
     */
    private suspend fun insertBuiltinSessionWithFallback(
        sessionId: String,
        baseInsert: TrainingSessionInsert,
        training: Training
    ) {
        val programCandidates = buildList {
            add(null)
            addAll(BuiltInActivities.programIdCandidates(training.category))
        }.distinctBy { it ?: "null" }

        var lastError: Exception? = null
        for (programId in programCandidates) {
            try {
                supabase.from("training_sessions").insert(
                    baseInsert.copy(programId = programId)
                )
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                android.util.Log.w(
                    "TrainingService",
                    "builtin session insert failed programId=$programId category=${training.category.dbValue}: ${e.message}"
                )
            }
        }
        throw lastError ?: IllegalStateException("Yerleşik aktivite oturumu oluşturulamadı")
    }

    private suspend fun fetchSessionById(sessionId: String): TrainingSession {
        return runCatching {
            supabase
                .from("training_sessions")
                .select(columns = Columns.raw("*, program:training_programs(*), coach:coaches(*)")) {
                    filter { eq("id", sessionId) }
                }
                .decodeSingle<TrainingSession>()
        }.getOrElse {
            supabase
                .from("training_sessions")
                .select { filter { eq("id", sessionId) } }
                .decodeSingle<TrainingSession>()
        }
    }

    // -----------------------------------------------------------------------
    // User Sessions — "training_sessions" table
    // -----------------------------------------------------------------------

    /**
     * Returns all sessions for a user with joined program + coach data.
     * Mirrors iOS fetchUserSessions(userId:).
     */
    suspend fun fetchUserSessions(userId: String): List<TrainingSession> {
        _isLoading.value = true
        return try {
            val result = supabase
                .from("training_sessions")
                .select(columns = Columns.raw("*, program:training_programs(*), coach:coaches(*)")) {
                    filter { eq("user_id", userId) }
                    order("scheduled_at", Order.DESCENDING)
                }
                .decodeList<TrainingSession>()
            _userSessions.value = result
            result
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Returns only future scheduled sessions.
     * Mirrors iOS fetchUpcomingSessions(userId:).
     */
    suspend fun fetchUpcomingSessions(userId: String): List<TrainingSession> {
        val now = Instant.now().toString()
        return supabase
            .from("training_sessions")
            .select(columns = Columns.raw("*, program:training_programs(*), coach:coaches(*)")) {
                filter {
                    eq("user_id", userId)
                    eq("status", "scheduled")
                    gte("scheduled_at", now)
                }
                order("scheduled_at", Order.ASCENDING)
            }
            .decodeList<TrainingSession>()
    }

    // -----------------------------------------------------------------------
    // Start / Complete / Cancel Session
    // -----------------------------------------------------------------------

    /**
     * Creates a new in-progress session and returns it with joined data.
     * Mirrors iOS startSession(userId:programId:coachId:).
     */
    suspend fun startSession(
        userId: String,
        programId: String,
        coachId: String? = null
    ): TrainingSession {
        _isLoading.value = true
        return try {
            val resolvedUserId = AuthService.getCurrentUserId() ?: userId
            val sessionId = UUID.randomUUID().toString()
            val now = Instant.now().toString()

            supabase.from("training_sessions").insert(
                newSessionInsert(
                    sessionId = sessionId,
                    userId = resolvedUserId,
                    programId = programId,
                    coachId = coachId,
                    scheduledAt = now,
                    startedAt = now,
                    status = "in_progress"
                )
            )

            val session = supabase
                .from("training_sessions")
                .select(columns = Columns.raw("*, program:training_programs(*), coach:coaches(*)")) {
                    filter { eq("id", sessionId) }
                }
                .decodeSingle<TrainingSession>()

            _currentSession.value = session
            session
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Marks a session as completed with all workout metrics.
     *
     * @param notes          Legacy builtin:… encoded notes (backward compat)
     * @param durationSeconds Workout duration in seconds (always set)
     * @param distanceKm     GPS distance — outdoor activities only
     * @param avgHeartRate   From Health Connect — null if no wearable
     * @param maxHeartRate   From Health Connect — null if no wearable
     * @param calories       From Health Connect — null if no wearable (NO fake estimates)
     * @param avgPace        min/km — running/walking only
     * @param avgSpeed       km/h — cycling only
     * @param altitudeGain   metres — GPS activities with altitude data
     * @param perceivedEffort "great"|"good"|"normal"|"hard"|"very_hard"
     */
    suspend fun completeSession(
        sessionId: String,
        notes: String? = null,
        durationSeconds: Int? = null,
        distanceKm: Double? = null,
        avgHeartRate: Int? = null,
        maxHeartRate: Int? = null,
        calories: Int? = null,
        avgPace: Double? = null,
        avgSpeed: Double? = null,
        altitudeGain: Double? = null,
        perceivedEffort: String? = null
    ) {
        _isLoading.value = true
        try {
            val session = supabase
                .from("training_sessions")
                .select { filter { eq("id", sessionId) } }
                .decodeSingle<TrainingSession>()

            supabase.from("training_sessions")
                .update(
                    SessionCompleteUpdate(
                        completedAt = Instant.now().toString(),
                        status = "completed",
                        notes = notes ?: session.notes,
                        durationSeconds = durationSeconds,
                        distanceKm = distanceKm?.takeIf { it > 0.001 },
                        avgHeartRate = avgHeartRate?.takeIf { it > 0 },
                        maxHeartRate = maxHeartRate?.takeIf { it > 0 },
                        calories = calories?.takeIf { it > 0 },
                        avgPace = avgPace?.takeIf { it > 0.0 },
                        avgSpeed = avgSpeed?.takeIf { it > 0.0 },
                        altitudeGain = altitudeGain?.takeIf { it > 0.0 },
                        perceivedEffort = perceivedEffort
                    )
                ) { filter { eq("id", sessionId) } }

            val label = StreakService.activityLabelFromSession(session)
            StreakService.recordActivityAndSync(
                userId = session.userId,
                activityType = label
            )
            _currentSession.value = null
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Sets session status to "cancelled".
     * Mirrors iOS cancelSession(sessionId:).
     */
    suspend fun cancelSession(sessionId: String) {
        supabase.from("training_sessions")
            .update(StatusUpdate("cancelled")) {
                filter { eq("id", sessionId) }
            }
    }

    // -----------------------------------------------------------------------
    // Schedule Session
    // -----------------------------------------------------------------------

    /**
     * Creates a session in "scheduled" status for a future date.
     * Mirrors iOS scheduleSession(userId:programId:coachId:scheduledAt:).
     */
    suspend fun scheduleSession(
        userId: String,
        programId: String,
        coachId: String? = null,
        scheduledAt: String  // ISO-8601
    ) {
        _isLoading.value = true
        try {
            supabase.from("training_sessions").insert(
                TrainingSessionInsert(
                    id = null,
                    userId = userId,
                    programId = programId,
                    coachId = coachId,
                    scheduledAt = scheduledAt,
                    startedAt = null,
                    status = "scheduled"
                )
            )
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun fetchAllCompletedSessions(
        userId: String,
        limit: Int = 100
    ): List<TrainingSession> {
        return try {
            val sessions = supabase
                .from("training_sessions")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("status", "completed")
                    }
                    order("completed_at", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<TrainingSession>()

            val programIds = sessions.mapNotNull { it.programId }.distinct()
            val programs = if (programIds.isNotEmpty()) {
                supabase.from("training_programs")
                    .select { filter { isIn("id", programIds) } }
                    .decodeList<TrainingProgram>()
                    .associateBy { it.id }
            } else emptyMap()

            sessions.map { s -> s.copy(program = s.programId?.let { programs[it] }) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    // -----------------------------------------------------------------------
    // Completed Sessions for Date — mirrors iOS fetchCompletedSessionsForDate
    // -----------------------------------------------------------------------

    /**
     * Returns training sessions with status="completed" whose completed_at falls
     * within the given [date] (UTC). Fetches programs separately to avoid FK
     * schema-cache issues.
     * Mirrors iOS TrainingService.fetchCompletedSessionsForDate(userId:date:).
     */
    suspend fun fetchCompletedSessionsForDate(
        userId: String,
        date: LocalDate
    ): List<TrainingSession> {
        val startOfDay = date.atStartOfDay().toInstant(ZoneOffset.UTC).toString()
        val startOfNextDay = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toString()

        return try {
            val sessions = supabase
                .from("training_sessions")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("status", "completed")
                        gte("completed_at", startOfDay)
                        lt("completed_at", startOfNextDay)
                    }
                    order("completed_at", Order.DESCENDING)
                }
                .decodeList<TrainingSession>()

            // Attach program data manually (avoids FK schema-cache errors)
            val programIds = sessions.mapNotNull { it.programId }.distinct()
            val programs = if (programIds.isNotEmpty()) {
                supabase.from("training_programs")
                    .select { filter { isIn("id", programIds) } }
                    .decodeList<TrainingProgram>()
                    .associateBy { it.id }
            } else emptyMap()

            sessions.map { s -> s.copy(program = s.programId?.let { programs[it] }) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    // -----------------------------------------------------------------------
    // Statistics — mirrors iOS fetchSessionStats(userId:)
    // -----------------------------------------------------------------------

    /**
     * Returns aggregate session counts.
     * [thisWeek] counts completed sessions since Monday of the current ISO week.
     */
    suspend fun getSessionStats(userId: String): SessionStats {
        val all = supabase
            .from("training_sessions")
            .select { filter { eq("user_id", userId) } }
            .decodeList<TrainingSession>()

        val completedCount = all.count { it.status == "completed" }

        val startOfWeek = LocalDate.now(ZoneOffset.UTC)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC)

        val thisWeek = all.count { session ->
            if (session.status != "completed") return@count false
            val completedAt = session.completedAt ?: return@count false
            try { Instant.parse(completedAt) >= startOfWeek } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
        }

        return SessionStats(total = all.size, completed = completedCount, thisWeek = thisWeek)
    }
}
