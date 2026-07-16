package com.app.coachup.app.services

import com.app.coachup.app.config.SupabaseConfig.client
import com.app.coachup.app.models.MembershipRequest
import com.app.coachup.app.models.MembershipRequestInsert
import com.app.coachup.app.models.PaymentMethod
import com.app.coachup.app.models.RequestStatusUpdate
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Salon üyelik talepleri — ödeme/dekont uygulama tarafında yapılmaz;
 * talep doğrudan salona iletilir ([PaymentMethod.LOCAL]).
 */
object MembershipRequestService {

    private val _currentRequest = MutableStateFlow<MembershipRequest?>(null)
    val currentRequest: StateFlow<MembershipRequest?> = _currentRequest.asStateFlow()

    private val _requestHistory = MutableStateFlow<List<MembershipRequest>>(emptyList())
    val requestHistory: StateFlow<List<MembershipRequest>> = _requestHistory.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    suspend fun createRequest(
        userId: String,
        planId: String,
        notes: String? = null
    ) {
        _isLoading.value = true
        try {
            if (hasPendingRequest(userId)) {
                throw IllegalStateException("Zaten bekleyen bir talebiniz var.")
            }
            // Fetch plan to get gymId
            val plan = MembershipService.fetchPlan(planId)
                ?: throw IllegalStateException("Seçilen üyelik planı bulunamadı.")

            val requestData = MembershipRequestInsert(
                userId = userId,
                planId = planId,
                gymId = plan.gymId ?: com.app.coachup.app.config.GymConfig.GYM_ID,
                paymentMethod = PaymentMethod.LOCAL.value,
                paymentProofUrl = null,
                adminNotes = notes?.takeIf { it.isNotBlank() }
            )
            client.postgrest["membership_requests"].insert(requestData)
            fetchUserRequests(userId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = toUserMessage(e)
            _error.value = message
            throw MembershipRequestException(message)
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun fetchUserRequests(userId: String): List<MembershipRequest> {
        _isLoading.value = true
        return try {
            val response = client.postgrest["membership_requests"]
                .select(Columns.raw("*, plan:membership_plans(*)")) {
                    filter { eq("user_id", userId) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<MembershipRequest>()

            _requestHistory.value = response
            _currentRequest.value = response.firstOrNull { it.status == "pending" }
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

    suspend fun fetchPendingRequest(userId: String): MembershipRequest? {
        _isLoading.value = true
        return try {
            val response = client.postgrest["membership_requests"]
                .select(Columns.raw("*, plan:membership_plans(*)")) {
                    filter {
                        eq("user_id", userId)
                        eq("status", "pending")
                    }
                    order("created_at", Order.DESCENDING)
                    limit(1)
                }
                .decodeList<MembershipRequest>()

            val request = response.firstOrNull()
            _currentRequest.value = request
            request
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _error.value = e.message
            null
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun cancelRequest(requestId: String, userId: String) {
        _isLoading.value = true
        try {
            client.postgrest["membership_requests"]
                .update(RequestStatusUpdate(status = "cancelled")) {
                    filter { eq("id", requestId) }
                }
            _currentRequest.value = null
            fetchUserRequests(userId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _error.value = e.message
            throw e
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun hasPendingRequest(userId: String): Boolean {
        return try {
            val response = client.postgrest["membership_requests"]
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("status", "pending")
                    }
                    limit(1)
                }
                .decodeList<MembershipRequest>()
            response.isNotEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private fun toUserMessage(e: Exception): String {
        val raw = buildString {
            append(e.message.orEmpty())
            e.cause?.message?.let { append(' ').append(it) }
        }
        return when {
            raw.contains("membership_requests_payment_method_check", ignoreCase = true) ->
                "Talep gönderilemedi. Lütfen uygulamayı güncelleyip tekrar deneyin."
            raw.contains("Zaten bekleyen", ignoreCase = true) -> raw
            raw.contains("duplicate", ignoreCase = true) ->
                "Bu plan için zaten bekleyen bir talebiniz var."
            raw.length > 160 || raw.contains("URL:") ->
                "Talep gönderilemedi. Lütfen tekrar deneyin."
            else -> raw.ifBlank { "Talep gönderilemedi." }
        }
    }
}

class MembershipRequestException(message: String) : Exception(message)
