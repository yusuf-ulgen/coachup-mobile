package com.app.coachup.app.services

import com.app.coachup.app.config.SupabaseConfig.client
import com.app.coachup.app.models.FinancialTransaction
import com.app.coachup.app.models.PaymentInstallment
import com.app.coachup.app.models.PaymentPlan
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException

/**
 * Android equivalent of iOS PaymentService.
 *
 * Mirrors tables: financial_transactions, payment_plans, payment_installments.
 */
object PaymentService {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // -------------------------------------------------------------------------
    // Transactions
    // -------------------------------------------------------------------------

    suspend fun fetchTransactions(userId: String, limit: Int = 30): List<FinancialTransaction> {
        _isLoading.value = true
        return try {
            client.postgrest["financial_transactions"]
                .select {
                    filter { eq("user_id", userId) }
                    order("created_at", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<FinancialTransaction>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e
        } finally {
            _isLoading.value = false
        }
    }

    // -------------------------------------------------------------------------
    // Payment Plans
    // -------------------------------------------------------------------------

    suspend fun fetchActivePaymentPlan(userId: String): PaymentPlan? {
        return try {
            client.postgrest["payment_plans"]
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("status", "active")
                    }
                    order("created_at", Order.DESCENDING)
                    limit(1)
                }
                .decodeList<PaymentPlan>()
                .firstOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    // -------------------------------------------------------------------------
    // Installments
    // -------------------------------------------------------------------------

    suspend fun fetchInstallments(planId: String): List<PaymentInstallment> {
        return try {
            client.postgrest["payment_installments"]
                .select {
                    filter { eq("plan_id", planId) }
                    order("installment_number", Order.ASCENDING)
                }
                .decodeList<PaymentInstallment>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e
        }
    }
}
