package com.app.coachup.app.services

import com.app.coachup.app.config.SupabaseConfig.client
import com.app.coachup.app.models.AppNotification
import com.app.coachup.app.models.IsReadUpdate
import com.app.coachup.app.models.NotificationInsert
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException

/**
 * Android equivalent of iOS NotificationService.
 *
 * Mirrors table: notifications.
 * Manages in-memory list state via StateFlow for Compose observation.
 */
object NotificationService {

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // -------------------------------------------------------------------------
    // Fetch
    // -------------------------------------------------------------------------

    suspend fun fetchNotifications(userId: String): List<AppNotification> {
        _isLoading.value = true
        return try {
            val response = client.postgrest["notifications"]
                .select {
                    filter { eq("user_id", userId) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<AppNotification>()

            _notifications.value = response
            _unreadCount.value = response.count { !it.isRead }
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

    suspend fun fetchUnreadNotifications(userId: String): List<AppNotification> {
        return try {
            val response = client.postgrest["notifications"]
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("is_read", false)
                    }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<AppNotification>()

            _unreadCount.value = response.size
            response
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _error.value = e.message
            throw e
        }
    }

    // -------------------------------------------------------------------------
    // Mark as Read
    // -------------------------------------------------------------------------

    suspend fun markAsRead(notificationId: String) {
        try {
            client.postgrest["notifications"]
                .update(IsReadUpdate(isRead = true)) {
                    filter { eq("id", notificationId) }
                }

            val updated = _notifications.value.map { n ->
                if (n.id == notificationId) n.copy(isRead = true) else n
            }
            _notifications.value = updated
            _unreadCount.value = maxOf(0, _unreadCount.value - 1)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _error.value = e.message
            throw e
        }
    }

    suspend fun markAllAsRead(userId: String) {
        try {
            client.postgrest["notifications"]
                .update(IsReadUpdate(isRead = true)) {
                    filter {
                        eq("user_id", userId)
                        eq("is_read", false)
                    }
                }

            _notifications.value = _notifications.value.map { it.copy(isRead = true) }
            _unreadCount.value = 0
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _error.value = e.message
            throw e
        }
    }

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    suspend fun deleteNotification(notificationId: String) {
        try {
            val notification = _notifications.value.find { it.id == notificationId }

            client.postgrest["notifications"]
                .delete {
                    filter { eq("id", notificationId) }
                }

            val updatedList = _notifications.value.filter { it.id != notificationId }
            _notifications.value = updatedList

            if (notification?.isRead == false) {
                _unreadCount.value = maxOf(0, _unreadCount.value - 1)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _error.value = e.message
            throw e
        }
    }

    suspend fun clearAllNotifications(userId: String) {
        try {
            client.postgrest["notifications"]
                .delete {
                    filter { eq("user_id", userId) }
                }
            _notifications.value = emptyList()
            _unreadCount.value = 0
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _error.value = e.message
            throw e
        }
    }

    // -------------------------------------------------------------------------
    // Create (system use)
    // -------------------------------------------------------------------------

    suspend fun createNotification(
        userId: String,
        title: String,
        message: String,
        type: String,
        actionUrl: String? = null
    ) {
        try {
            val data = NotificationInsert(
                userId = userId,
                title = title,
                message = message,
                type = type,
                isRead = false,
                actionUrl = actionUrl
            )
            client.postgrest["notifications"].insert(data)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _error.value = e.message
            throw e
        }
    }

    // -------------------------------------------------------------------------
    // Notification Types
    // -------------------------------------------------------------------------

    enum class NotificationType(val value: String) {
        WORKOUT("workout"),
        MEMBERSHIP("membership"),
        SYSTEM("system"),
        COACH("coach"),
        REMINDER("reminder")
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    fun timeAgo(createdAt: String): String {
        return try {
            val instant = java.time.Instant.parse(createdAt)
            val now = java.time.Instant.now()
            val seconds = java.time.Duration.between(instant, now).seconds

            when {
                seconds < 60 -> "Az önce"
                seconds < 3600 -> "${seconds / 60} dakika önce"
                seconds < 86400 -> "${seconds / 3600} saat önce"
                else -> "${seconds / 86400} gün önce"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            createdAt
        }
    }
}
