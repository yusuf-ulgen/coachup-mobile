package com.app.coachup.app.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Admin Dashboard — non-serializable aggregated view-model types.
// These are never decoded from JSON; they are assembled in AdminService.
// ---------------------------------------------------------------------------

data class DashboardStats(
    val totalUsers: Int,
    val activeUsers: Int,
    val totalGyms: Int,
    val totalCoaches: Int,
    val totalPrograms: Int,
    val todayEntries: Int,
    val activeMemberships: Int,
    val monthlyRevenue: Double,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class ChartDataPoint(
    val label: String,
    val value: Double
)

data class ProgramStatistic(
    val programId: String,
    val programName: String,
    val sessionCount: Int
)

// ---------------------------------------------------------------------------
// Update payloads used by MembershipRequestService.
// Kept here because they are not in InsertDTOs.kt.
// ---------------------------------------------------------------------------

@Serializable
data class RequestStatusUpdate(
    @SerialName("status") val status: String
)
