package com.app.coachup.app.services

import com.app.coachup.app.config.SupabaseConfig.client
import com.app.coachup.app.models.ActiveStatusUpdate
import com.app.coachup.app.models.AuditLog
import com.app.coachup.app.models.ChartDataPoint
import com.app.coachup.app.models.Coach
import com.app.coachup.app.models.DashboardStats
import com.app.coachup.app.models.Gym
import com.app.coachup.app.models.MembershipPlan
import com.app.coachup.app.models.ProgramStatistic
import com.app.coachup.app.models.QREntry
import com.app.coachup.app.models.TrainingProgram
import com.app.coachup.app.models.TrainingSession
import com.app.coachup.app.models.UserBanUpdate
import com.app.coachup.app.models.UserGymUpdate
import com.app.coachup.app.models.UserProfile
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Android equivalent of iOS AdminService.
 *
 * Mirrors tables: users, gyms, coaches, training_programs, membership_plans,
 *                 user_memberships, qr_entries, audit_logs, training_sessions.
 *
 * All insert / update payloads are defined inline as private @Serializable data classes
 * to keep the model file clean (matching iOS convention of defining them inside the service).
 */
object AdminService {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // -------------------------------------------------------------------------
    // Authorization Check
    // -------------------------------------------------------------------------

    suspend fun checkAdminAccess(userId: String): Boolean {
        return try {
            val profiles = client.postgrest["users"]
                .select {
                    filter { eq("id", userId) }
                    limit(1)
                }
                .decodeList<UserProfile>()
            val role = profiles.firstOrNull()?.role ?: return false
            role == "admin" || role == "gym_manager"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    // -------------------------------------------------------------------------
    // Dashboard Stats
    // -------------------------------------------------------------------------

    suspend fun fetchDashboardStats(): DashboardStats = coroutineScope {
        _isLoading.value = true
        try {
            val totalUsers      = async { fetchUsersCount() }
            val activeUsers     = async { fetchActiveUsersCount() }
            val totalGyms       = async { fetchGymsCount() }
            val totalCoaches    = async { fetchCoachesCount() }
            val totalPrograms   = async { fetchProgramsCount() }
            val todayEntries    = async { fetchTodayEntriesCount() }
            val activeMemberships = async { fetchActiveMembershipsCount() }
            val monthlyRevenue  = async { fetchMonthlyRevenue() }

            DashboardStats(
                totalUsers        = totalUsers.await(),
                activeUsers       = activeUsers.await(),
                totalGyms         = totalGyms.await(),
                totalCoaches      = totalCoaches.await(),
                totalPrograms     = totalPrograms.await(),
                todayEntries      = todayEntries.await(),
                activeMemberships = activeMemberships.await(),
                monthlyRevenue    = monthlyRevenue.await()
            )
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun fetchUsersCount(): Int =
        client.postgrest["users"].select().decodeList<UserProfile>().size

    private suspend fun fetchActiveUsersCount(): Int {
        val thirtyDaysAgo = Instant.now()
            .minusSeconds(30L * 24 * 3600)
            .toString()
        return client.postgrest["users"]
            .select {
                filter { gte("last_activity_date", thirtyDaysAgo) }
            }
            .decodeList<UserProfile>()
            .size
    }

    private suspend fun fetchGymsCount(): Int =
        client.postgrest["gyms"].select().decodeList<Gym>().size

    private suspend fun fetchCoachesCount(): Int =
        client.postgrest["coaches"]
            .select { filter { eq("is_active", true) } }
            .decodeList<Coach>()
            .size

    private suspend fun fetchProgramsCount(): Int =
        client.postgrest["training_programs"]
            .select { filter { eq("is_active", true) } }
            .decodeList<TrainingProgram>()
            .size

    private suspend fun fetchTodayEntriesCount(): Int {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        return client.postgrest["qr_entries"]
            .select {
                filter {
                    eq("entry_date", today)
                    eq("entry_type", "entry")
                }
            }
            .decodeList<QREntry>()
            .size
    }

    private suspend fun fetchActiveMembershipsCount(): Int =
        client.postgrest["user_memberships"]
            .select { filter { eq("is_active", true) } }
            .decodeList<com.app.coachup.app.models.UserMembership>()
            .size

    private suspend fun fetchMonthlyRevenue(): Double {
        val startOfMonth = YearMonth.now()
            .atDay(1)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toString()

        return client.postgrest["user_memberships"]
            .select(Columns.raw("*, plan:membership_plans(*)")) {
                filter { gte("created_at", startOfMonth) }
            }
            .decodeList<com.app.coachup.app.models.UserMembership>()
            .sumOf { it.plan?.price ?: 0.0 }
    }

    // -------------------------------------------------------------------------
    // User Management
    // -------------------------------------------------------------------------

    suspend fun fetchAllUsers(
        searchText: String? = null,
        page: Int = 0,
        limit: Int = 50
    ): List<UserProfile> {
        _isLoading.value = true
        return try {
            client.postgrest["users"]
                .select {
                    if (!searchText.isNullOrBlank()) {
                        filter {
                            or {
                                ilike("name", "%$searchText%")
                                ilike("surname", "%$searchText%")
                                ilike("email", "%$searchText%")
                            }
                        }
                    }
                    order("created_at", Order.DESCENDING)
                    range(
                        from = (page * limit).toLong(),
                        to = ((page + 1) * limit - 1).toLong()
                    )
                }
                .decodeList<UserProfile>()
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun updateUserRole(userId: String, role: String) {
        client.postgrest["users"]
            .update(mapOf("role" to role)) {
                filter { eq("id", userId) }
            }
    }

    suspend fun updateUserGym(userId: String, gymId: String?) {
        client.postgrest["users"]
            .update(UserGymUpdate(gymId = gymId)) {
                filter { eq("id", userId) }
            }
    }

    suspend fun deleteUser(userId: String) {
        client.postgrest["users"]
            .delete { filter { eq("id", userId) } }
    }

    suspend fun banUser(userId: String, reason: String) {
        client.postgrest["users"]
            .update(
                UserBanUpdate(
                    isBanned = true,
                    banReason = reason,
                    bannedAt = Instant.now().toString()
                )
            ) { filter { eq("id", userId) } }
    }

    suspend fun unbanUser(userId: String) {
        client.postgrest["users"]
            .update(UserBanUpdate(isBanned = false)) {
                filter { eq("id", userId) }
            }
    }

    // -------------------------------------------------------------------------
    // Gym Management
    // -------------------------------------------------------------------------

    suspend fun fetchAllGyms(searchText: String? = null): List<Gym> {
        _isLoading.value = true
        return try {
            client.postgrest["gyms"]
                .select {
                    if (!searchText.isNullOrBlank()) {
                        filter { ilike("name", "%$searchText%") }
                    }
                    order("name", Order.ASCENDING)
                }
                .decodeList<Gym>()
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun createGym(
        name: String,
        address: String,
        city: String,
        phone: String? = null,
        email: String? = null,
        capacity: Int? = null
    ): Gym {
        val gymId = UUID.randomUUID().toString()
        val data = GymInsert(
            id = gymId,
            name = name,
            address = address,
            city = city,
            phone = phone,
            email = email,
            capacity = capacity,
            isActive = true
        )
        client.postgrest["gyms"].insert(data)
        return client.postgrest["gyms"]
            .select { filter { eq("id", gymId) } }
            .decodeSingle<Gym>()
    }

    suspend fun updateGym(gym: Gym) {
        client.postgrest["gyms"]
            .update(gym) { filter { eq("id", gym.id) } }
    }

    suspend fun deleteGym(gymId: String) {
        client.postgrest["gyms"]
            .delete { filter { eq("id", gymId) } }
    }

    suspend fun toggleGymStatus(gymId: String, isActive: Boolean) {
        client.postgrest["gyms"]
            .update(ActiveStatusUpdate(isActive = isActive)) {
                filter { eq("id", gymId) }
            }
    }

    // -------------------------------------------------------------------------
    // Coach Management
    // -------------------------------------------------------------------------

    suspend fun fetchAllCoaches(searchText: String? = null): List<Coach> {
        _isLoading.value = true
        return try {
            client.postgrest["coaches"]
                .select {
                    if (!searchText.isNullOrBlank()) {
                        filter {
                            or {
                                ilike("name", "%$searchText%")
                                ilike("surname", "%$searchText%")
                            }
                        }
                    }
                    order("name", Order.ASCENDING)
                }
                .decodeList<Coach>()
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun createCoach(
        name: String,
        surname: String,
        email: String? = null,
        phone: String? = null,
        gender: String,
        specialty: String? = null,
        bio: String? = null,
        profileImageUrl: String? = null,
        gymId: String? = null
    ): Coach {
        val coachId = UUID.randomUUID().toString()
        val data = CoachInsert(
            id = coachId,
            name = name,
            surname = surname,
            email = email,
            phone = phone,
            gender = gender,
            specialty = specialty,
            bio = bio,
            profileImageUrl = profileImageUrl,
            rating = 0.0,
            experienceYears = 0,
            isActive = true,
            gymId = gymId
        )
        client.postgrest["coaches"].insert(data)
        return client.postgrest["coaches"]
            .select { filter { eq("id", coachId) } }
            .decodeSingle<Coach>()
    }

    suspend fun updateCoach(coach: Coach) {
        client.postgrest["coaches"]
            .update(coach) { filter { eq("id", coach.id) } }
    }

    suspend fun deleteCoach(coachId: String) {
        client.postgrest["coaches"]
            .delete { filter { eq("id", coachId) } }
    }

    suspend fun toggleCoachStatus(coachId: String, isActive: Boolean) {
        client.postgrest["coaches"]
            .update(ActiveStatusUpdate(isActive = isActive)) {
                filter { eq("id", coachId) }
            }
    }

    // -------------------------------------------------------------------------
    // Training Program Management
    // -------------------------------------------------------------------------

    suspend fun fetchAllPrograms(searchText: String? = null): List<TrainingProgram> {
        _isLoading.value = true
        return try {
            client.postgrest["training_programs"]
                .select {
                    if (!searchText.isNullOrBlank()) {
                        filter { ilike("name", "%$searchText%") }
                    }
                    order("name", Order.ASCENDING)
                }
                .decodeList<TrainingProgram>()
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun createProgram(
        name: String,
        description: String,
        category: String,
        difficulty: String,
        duration: Int,
        caloriesBurn: Int? = null,
        iconName: String? = null
    ): TrainingProgram {
        val programId = UUID.randomUUID().toString()
        val data = ProgramInsert(
            id = programId,
            name = name,
            description = description,
            category = category,
            difficulty = difficulty,
            duration = duration,
            caloriesBurn = caloriesBurn ?: 0,
            iconName = iconName,
            isActive = true
        )
        client.postgrest["training_programs"].insert(data)
        return client.postgrest["training_programs"]
            .select { filter { eq("id", programId) } }
            .decodeSingle<TrainingProgram>()
    }

    suspend fun updateProgram(program: TrainingProgram) {
        client.postgrest["training_programs"]
            .update(program) { filter { eq("id", program.id) } }
    }

    suspend fun deleteProgram(programId: String) {
        client.postgrest["training_programs"]
            .delete { filter { eq("id", programId) } }
    }

    suspend fun toggleProgramStatus(programId: String, isActive: Boolean) {
        client.postgrest["training_programs"]
            .update(ActiveStatusUpdate(isActive = isActive)) {
                filter { eq("id", programId) }
            }
    }

    // -------------------------------------------------------------------------
    // Membership Plan Management
    // -------------------------------------------------------------------------

    suspend fun fetchAllMembershipPlans(): List<MembershipPlan> {
        _isLoading.value = true
        return try {
            client.postgrest["membership_plans"]
                .select { order("price", Order.ASCENDING) }
                .decodeList<MembershipPlan>()
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun createMembershipPlan(
        name: String,
        description: String,
        price: Double,
        durationMonths: Int,
        features: List<String>
    ): MembershipPlan {
        val planId = UUID.randomUUID().toString()
        val data = MembershipPlanInsert(
            id = planId,
            name = name,
            description = description,
            price = price,
            durationMonths = durationMonths,
            features = features,
            isActive = true
        )
        client.postgrest["membership_plans"].insert(data)
        return client.postgrest["membership_plans"]
            .select { filter { eq("id", planId) } }
            .decodeSingle<MembershipPlan>()
    }

    suspend fun updateMembershipPlan(plan: MembershipPlan) {
        client.postgrest["membership_plans"]
            .update(plan) { filter { eq("id", plan.id) } }
    }

    suspend fun deleteMembershipPlan(planId: String) {
        client.postgrest["membership_plans"]
            .delete { filter { eq("id", planId) } }
    }

    suspend fun toggleMembershipPlanStatus(planId: String, isActive: Boolean) {
        client.postgrest["membership_plans"]
            .update(ActiveStatusUpdate(isActive = isActive)) {
                filter { eq("id", planId) }
            }
    }

    // -------------------------------------------------------------------------
    // QR Entry Management
    // -------------------------------------------------------------------------

    suspend fun fetchAllQREntries(
        gymId: String? = null,
        startDate: String? = null,
        endDate: String? = null,
        page: Int = 0,
        limit: Int = 50
    ): List<QREntry> {
        _isLoading.value = true
        return try {
            client.postgrest["qr_entries"]
                .select {
                    filter {
                        gymId?.let { eq("gym_id", it) }
                        startDate?.let { gte("entry_date", it) }
                        endDate?.let { lte("entry_date", it) }
                    }
                    order("entry_date", Order.DESCENDING)
                    order("entry_time", Order.DESCENDING)
                    range(
                        from = (page * limit).toLong(),
                        to = ((page + 1) * limit - 1).toLong()
                    )
                }
                .decodeList<QREntry>()
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun exportQREntries(
        gymId: String? = null,
        startDate: String? = null,
        endDate: String? = null
    ): String {
        val entries = fetchAllQREntries(
            gymId = gymId,
            startDate = startDate,
            endDate = endDate,
            page = 0,
            limit = 10_000
        )
        val sb = StringBuilder("ID,User ID,Gym ID,Entry Type,Date,Time,QR Code\n")
        entries.forEach { e ->
            sb.append("${e.id},${e.userId},${e.gymId},${e.entryType},${e.entryDate},${e.entryTime},${e.qrCode ?: ""}\n")
        }
        return sb.toString()
    }

    // -------------------------------------------------------------------------
    // Audit Log Management
    // -------------------------------------------------------------------------

    suspend fun fetchAuditLogs(
        userId: String? = null,
        action: String? = null,
        resourceType: String? = null,
        page: Int = 0,
        limit: Int = 50
    ): List<AuditLog> {
        _isLoading.value = true
        return try {
            client.postgrest["audit_logs"]
                .select {
                    filter {
                        userId?.let { eq("user_id", it) }
                        action?.let { eq("action", it) }
                        resourceType?.let { eq("resource_type", it) }
                    }
                    order("created_at", Order.DESCENDING)
                    range(
                        from = (page * limit).toLong(),
                        to = ((page + 1) * limit - 1).toLong()
                    )
                }
                .decodeList<AuditLog>()
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun exportAuditLogs(
        userId: String? = null,
        startDate: String? = null,
        endDate: String? = null
    ): String {
        val logs = client.postgrest["audit_logs"]
            .select {
                filter {
                    userId?.let { eq("user_id", it) }
                    startDate?.let { gte("created_at", it) }
                    endDate?.let { lte("created_at", it) }
                }
                order("created_at", Order.DESCENDING)
                limit(10_000)
            }
            .decodeList<AuditLog>()

        val sb = StringBuilder("ID,User Email,Action,Resource Type,Resource ID,Description,Status,Created At\n")
        logs.forEach { l ->
            sb.append("${l.id},${l.userEmail ?: ""},${l.action},${l.resourceType},${l.resourceId ?: ""},${l.description ?: ""},${l.status},${l.createdAt ?: ""}\n")
        }
        return sb.toString()
    }

    // -------------------------------------------------------------------------
    // Reports & Analytics
    // -------------------------------------------------------------------------

    suspend fun fetchUserGrowthReport(days: Int = 30): List<ChartDataPoint> {
        val startDate = Instant.now().minusSeconds(days * 86_400L).toString()
        val users = client.postgrest["users"]
            .select {
                filter {
                    gte("created_at", startDate)
                }
            }
            .decodeList<UserProfile>()

        val grouped = mutableMapOf<String, Int>()
        users.forEach { user ->
            val day = user.createdAt.take(10).takeIf { it.isNotBlank() } ?: return@forEach
            grouped[day] = (grouped[day] ?: 0) + 1
        }
        return grouped.entries
            .sortedBy { it.key }
            .map { ChartDataPoint(label = it.key, value = it.value.toDouble()) }
    }

    suspend fun fetchRevenueReport(months: Int = 12): List<ChartDataPoint> {
        val startDate = Instant.now().minusSeconds(months * 30L * 86_400).toString()
        val memberships = client.postgrest["user_memberships"]
            .select(Columns.raw("*, plan:membership_plans(*)")) {
                filter { gte("created_at", startDate) }
            }
            .decodeList<com.app.coachup.app.models.UserMembership>()

        val grouped = mutableMapOf<String, Double>()
        memberships.forEach { m ->
            val month = m.createdAt.take(7).takeIf { it.isNotBlank() } ?: return@forEach
            grouped[month] = (grouped[month] ?: 0.0) + (m.plan?.price ?: 0.0)
        }
        return grouped.entries
            .sortedBy { it.key }
            .map { ChartDataPoint(label = it.key, value = it.value) }
    }

    suspend fun fetchAttendanceReport(gymId: String, days: Int = 30): List<ChartDataPoint> {
        val startDate = LocalDate.now().minusDays(days.toLong())
            .format(DateTimeFormatter.ISO_DATE)
        val endDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE)

        val entries = client.postgrest["qr_entries"]
            .select {
                filter {
                    eq("gym_id", gymId)
                    eq("entry_type", "entry")
                    gte("entry_date", startDate)
                    lte("entry_date", endDate)
                }
            }
            .decodeList<QREntry>()

        val grouped = mutableMapOf<String, Int>()
        entries.forEach { e ->
            val key = e.entryDate ?: return@forEach
            grouped[key] = (grouped[key] ?: 0) + 1
        }
        return grouped.entries
            .sortedBy { it.key }
            .map { ChartDataPoint(label = it.key, value = it.value.toDouble()) }
    }

    suspend fun fetchPopularProgramsReport(limit: Int = 10): List<ProgramStatistic> {
        val sessions = client.postgrest["training_sessions"]
            .select(Columns.raw("*, program:training_programs(*)"))
            .decodeList<TrainingSession>()

        val counts = mutableMapOf<String, Pair<String, Int>>()
        sessions.forEach { session ->
            val program = session.program ?: return@forEach
            val existing = counts[program.id]
            counts[program.id] = Pair(program.name, (existing?.second ?: 0) + 1)
        }

        return counts.entries
            .map { ProgramStatistic(programId = it.key, programName = it.value.first, sessionCount = it.value.second) }
            .sortedByDescending { it.sessionCount }
            .take(limit)
    }

    // -------------------------------------------------------------------------
    // Private Insert / Update DTOs
    // -------------------------------------------------------------------------

    @Serializable
    private data class GymInsert(
        val id: String,
        val name: String,
        val address: String,
        val city: String,
        val phone: String?,
        val email: String?,
        val capacity: Int?,
        @SerialName("is_active") val isActive: Boolean
    )

    @Serializable
    private data class CoachInsert(
        val id: String,
        val name: String,
        val surname: String,
        val email: String? = null,
        val phone: String? = null,
        val gender: String,
        val specialty: String? = null,
        val bio: String? = null,
        @SerialName("profile_image_url") val profileImageUrl: String? = null,
        val rating: Double = 0.0,
        @SerialName("experience_years") val experienceYears: Int = 0,
        @SerialName("is_active") val isActive: Boolean,
        @SerialName("gym_id") val gymId: String? = null
    )

    @Serializable
    private data class ProgramInsert(
        val id: String,
        val name: String,
        val description: String? = null,
        val category: String,
        val difficulty: String,
        val duration: Int,
        @SerialName("calories_burn") val caloriesBurn: Int = 0,
        @SerialName("icon_name") val iconName: String? = null,
        @SerialName("is_active") val isActive: Boolean
    )

    @Serializable
    private data class MembershipPlanInsert(
        val id: String,
        val name: String,
        val description: String,
        val price: Double,
        @SerialName("duration_months") val durationMonths: Int,
        val features: List<String>,
        @SerialName("is_active") val isActive: Boolean
    )
}
