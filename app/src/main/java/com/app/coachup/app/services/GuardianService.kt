package com.app.coachup.app.services

import com.app.coachup.app.config.SupabaseConfig
import com.app.coachup.app.models.QREntry
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.format.DateTimeParseException

// ---------------------------------------------------------------------------
// Models — mirrors iOS GuardianService.swift structs
// ---------------------------------------------------------------------------

@Serializable
data class Guardian(
    @SerialName("id")           val id: String,
    @SerialName("auth_user_id") val authUserId: String? = null,
    @SerialName("name")         val name: String,
    @SerialName("surname")      val surname: String,
    @SerialName("email")        val email: String? = null,
    @SerialName("phone")        val phone: String? = null,
    @SerialName("tc_no")        val tcNo: String? = null,
    @SerialName("created_at")   val createdAt: String = ""
)

@Serializable
data class GuardianChildInfo(
    @SerialName("id")                val id: String,
    @SerialName("name")              val name: String? = null,
    @SerialName("surname")           val surname: String? = null,
    @SerialName("profile_image_url") val profileImageUrl: String? = null,
    @SerialName("gym_id")            val gymId: String? = null
) {
    val fullName: String get() = listOfNotNull(name, surname).joinToString(" ")
    val initials: String get() = name?.firstOrNull()?.toString() ?: "?"
}

@Serializable
data class MemberGuardian(
    @SerialName("id")                    val id: String,
    @SerialName("member_id")             val memberId: String,
    @SerialName("guardian_id")           val guardianId: String,
    @SerialName("can_view_health")       val canViewHealth: Boolean = false,
    @SerialName("can_view_attendance")   val canViewAttendance: Boolean = false,
    @SerialName("can_view_progress")     val canViewProgress: Boolean = false,
    @SerialName("receive_notifications") val receiveNotifications: Boolean = false,
    var member: GuardianChildInfo? = null
)

data class ChildHealthMetrics(
    val heartRate: Int = 0,
    val spo2: Int = 0,
    val steps: Int = 0,
    val calories: Int = 0,
    val stressLevel: Int = 0,
    val lastUpdated: String? = null,
    val isLive: Boolean = false
)

// ---------------------------------------------------------------------------
// Service — mirrors iOS GuardianService.swift
// ---------------------------------------------------------------------------

object GuardianService {

    private val client get() = SupabaseConfig.client

    private val _guardian = MutableStateFlow<Guardian?>(null)
    val guardian: StateFlow<Guardian?> = _guardian.asStateFlow()

    private val _children = MutableStateFlow<List<MemberGuardian>>(emptyList())
    val children: StateFlow<List<MemberGuardian>> = _children.asStateFlow()

    private val _childHealthData = MutableStateFlow<Map<String, ChildHealthMetrics>>(emptyMap())
    val childHealthData: StateFlow<Map<String, ChildHealthMetrics>> = _childHealthData.asStateFlow()

    private val _childEntries = MutableStateFlow<Map<String, List<QREntry>>>(emptyMap())
    val childEntries: StateFlow<Map<String, List<QREntry>>> = _childEntries.asStateFlow()

    private val _recentEntries = MutableStateFlow<List<QREntry>>(emptyList())
    val recentEntries: StateFlow<List<QREntry>> = _recentEntries.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // -----------------------------------------------------------------------
    // Fetch guardian profile from "guardians" table
    // -----------------------------------------------------------------------

    suspend fun fetchGuardian(authUserId: String): Guardian? {
        return try {
            val result = client.from("guardians")
                .select {
                    filter { eq("auth_user_id", authUserId) }
                }
                .decodeList<Guardian>()
            _guardian.value = result.firstOrNull()
            result.firstOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("GuardianService", "fetchGuardian error: ${e.message}")
            null
        }
    }

    // -----------------------------------------------------------------------
    // Fetch all children (member_guardians + user info join)
    // -----------------------------------------------------------------------

    suspend fun fetchChildren(guardianId: String) {
        _isLoading.value = true
        try {
            val relations = client.from("member_guardians")
                .select {
                    filter { eq("guardian_id", guardianId) }
                }
                .decodeList<MemberGuardian>()

            val result = relations.map { relation ->
                var r = relation
                try {
                    val members = client.from("users")
                        .select(columns = io.github.jan.supabase.postgrest.query.Columns.raw("id, name, surname, profile_image_url, gym_id")) {
                            filter { eq("id", relation.memberId) }
                        }
                        .decodeList<GuardianChildInfo>()
                    r = r.copy(member = members.firstOrNull())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("GuardianService", "Error loading member ${relation.memberId}: ${e.message}")
                }
                r
            }

            _children.value = result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("GuardianService", "fetchChildren error: ${e.message}")
        } finally {
            _isLoading.value = false
        }
    }

    // -----------------------------------------------------------------------
    // Fetch child QR entries
    // -----------------------------------------------------------------------

    suspend fun fetchChildEntries(childId: String) {
        try {
            val entries = client.from("qr_entries")
                .select {
                    filter { eq("user_id", childId) }
                    order("created_at", Order.DESCENDING)
                    limit(20)
                }
                .decodeList<QREntry>()

            val current = _childEntries.value.toMutableMap()
            current[childId] = entries
            _childEntries.value = current

            // Today's entries → recentEntries
            val today = java.time.LocalDate.now().toString()
            val todayEntries = entries.filter { it.entryDate == today }
            val combined = (_recentEntries.value + todayEntries)
                .distinctBy { it.id }
                .sortedByDescending { it.entryTime }
            _recentEntries.value = combined
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("GuardianService", "fetchChildEntries error: ${e.message}")
        }
    }

    // -----------------------------------------------------------------------
    // Check if user is a guardian
    // -----------------------------------------------------------------------

    suspend fun isGuardian(authUserId: String): Boolean {
        return fetchGuardian(authUserId) != null
    }

    // -----------------------------------------------------------------------
    // Reset state (on logout)
    // -----------------------------------------------------------------------

    fun reset() {
        _guardian.value = null
        _children.value = emptyList()
        _childHealthData.value = emptyMap()
        _childEntries.value = emptyMap()
        _recentEntries.value = emptyList()
    }
}
