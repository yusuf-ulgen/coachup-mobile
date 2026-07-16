package com.app.coachup.app.services

import com.app.coachup.app.config.GymConfig
import com.app.coachup.app.config.SupabaseConfig.client
import com.app.coachup.app.models.AutoRenewUpdate
import com.app.coachup.app.models.MembershipCancelUpdate
import com.app.coachup.app.models.MembershipPlan
import com.app.coachup.app.models.MembershipStatusUpdate
import com.app.coachup.app.models.UserMembership
import com.app.coachup.app.models.UserMembershipInsert
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Android equivalent of iOS MembershipService.
 *
 * All functions are suspend functions that must be called from a coroutine scope.
 * State is exposed via StateFlow for Compose observation.
 * Mirrors table names: membership_plans, user_memberships.
 */
object MembershipService {

    private val _plans = MutableStateFlow<List<MembershipPlan>>(emptyList())
    val plans: StateFlow<List<MembershipPlan>> = _plans.asStateFlow()

    private val _currentMembership = MutableStateFlow<UserMembership?>(null)
    val currentMembership: StateFlow<UserMembership?> = _currentMembership.asStateFlow()

    private val _membershipHistory = MutableStateFlow<List<UserMembership>>(emptyList())
    val membershipHistory: StateFlow<List<UserMembership>> = _membershipHistory.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // -------------------------------------------------------------------------
    // Fetch Plans
    // -------------------------------------------------------------------------

    suspend fun fetchPlans(gymId: String? = null): List<MembershipPlan> {
        _isLoading.value = true
        val effectiveGymId = gymId?.takeIf { it.isNotBlank() } ?: GymConfig.GYM_ID
        return try {
            val response = client.postgrest["membership_plans"]
                .select {
                    filter {
                        eq("is_active", true)
                        eq("gym_id", effectiveGymId)
                    }
                    order("price", Order.ASCENDING)
                }
                .decodeList<MembershipPlan>()
            _plans.value = response
            response
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _error.value = e.message
            throw e
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun fetchPlan(id: String): MembershipPlan? {
        return try {
            client.postgrest["membership_plans"]
                .select {
                    filter { eq("id", id) }
                    limit(1)
                }
                .decodeSingle<MembershipPlan>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    // -------------------------------------------------------------------------
    // User Membership
    // -------------------------------------------------------------------------

    suspend fun fetchCurrentMembership(userId: String, gymId: String? = null): UserMembership? {
        _isLoading.value = true
        return try {
            val response = client.postgrest["user_memberships"]
                .select(Columns.raw("*, plan:membership_plans(*)")) {
                    filter {
                        eq("user_id", userId)
                        eq("is_active", true)
                    }
                    order("end_date", Order.DESCENDING)
                }
                .decodeList<UserMembership>()
            val membership = response.firstOrNull { membership ->
                gymId.isNullOrBlank() || membership.plan?.gymId == gymId
            } ?: response.firstOrNull()
            _currentMembership.value = membership
            membership
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _error.value = e.message
            null
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun fetchMembershipHistory(userId: String): List<UserMembership> {
        return try {
            val response = client.postgrest["user_memberships"]
                .select(Columns.raw("*, plan:membership_plans(*)")) {
                    filter { eq("user_id", userId) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<UserMembership>()
            _membershipHistory.value = response
            response
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _error.value = e.message
            throw e
        }
    }

    // -------------------------------------------------------------------------
    // Purchase Membership
    // -------------------------------------------------------------------------

    suspend fun purchaseMembership(userId: String, planId: String) {
        _isLoading.value = true
        try {
            val plan = fetchPlan(planId)
                ?: throw IllegalStateException("Plan bulunamadı")

            // Deactivate existing active memberships
            client.postgrest["user_memberships"]
                .update(MembershipStatusUpdate(isActive = false)) {
                    filter {
                        eq("user_id", userId)
                        eq("is_active", true)
                    }
                }

            // Calculate dates
            val startDate = LocalDate.now()
            val endDate = startDate.plusMonths(plan.durationMonths.toLong())
            val formatter = DateTimeFormatter.ISO_DATE

            val insert = UserMembershipInsert(
                userId = userId,
                planId = planId,
                gymId = plan.gymId ?: GymConfig.GYM_ID,
                totalPrice = plan.price,
                startDate = startDate.format(formatter),
                endDate = endDate.format(formatter),
                isActive = true,
                autoRenew = false
            )

            client.postgrest["user_memberships"].insert(insert)

            // Refresh state
            fetchCurrentMembership(userId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _error.value = e.message
            throw e
        } finally {
            _isLoading.value = false
        }
    }

    // -------------------------------------------------------------------------
    // Manage Membership
    // -------------------------------------------------------------------------

    suspend fun toggleAutoRenew(membershipId: String, autoRenew: Boolean) {
        try {
            client.postgrest["user_memberships"]
                .update(AutoRenewUpdate(autoRenew = autoRenew)) {
                    filter { eq("id", membershipId) }
                }
            _currentMembership.value = _currentMembership.value?.copy(autoRenew = autoRenew)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _error.value = e.message
            throw e
        }
    }

    suspend fun cancelMembership(membershipId: String) {
        try {
            client.postgrest["user_memberships"]
                .update(MembershipCancelUpdate(isActive = false, autoRenew = false)) {
                    filter { eq("id", membershipId) }
                }
            _currentMembership.value = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _error.value = e.message
            throw e
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    suspend fun isMembershipActive(userId: String): Boolean {
        val membership = fetchCurrentMembership(userId) ?: return false
        val endDate = runCatching {
            LocalDate.parse(membership.endDate ?: return false, DateTimeFormatter.ISO_DATE)
        }.getOrNull() ?: return false
        return membership.isActive && endDate.isAfter(LocalDate.now())
    }

    fun getRemainingDays(membership: UserMembership): Int {
        val endDate = runCatching {
            LocalDate.parse(membership.endDate ?: return 0, DateTimeFormatter.ISO_DATE)
        }.getOrNull() ?: return 0
        val days = ChronoUnit.DAYS.between(LocalDate.now(), endDate).toInt()
        return maxOf(0, days)
    }
}
