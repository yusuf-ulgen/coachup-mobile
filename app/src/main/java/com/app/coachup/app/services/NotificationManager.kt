package com.app.coachup.app.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.app.coachup.app.MainActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.app.coachup.app.R
import com.app.coachup.app.models.AppNotification
import com.app.coachup.app.models.UserMembership
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Android equivalent of iOS NotificationManager + PushNotificationService combined.
 *
 * Responsibilities:
 *  - Register Android notification channels (mirrors iOS UNNotificationCategory setup).
 *  - Post local notifications via [NotificationManagerCompat].
 *  - Coordinate realtime events with [RealtimeService] and [NotificationService].
 *  - Expose unread count via StateFlow.
 *
 * Must be initialised once via [initialize] with a [Context] before any other call.
 */
object CoachUpNotificationManager {

    private const val TAG = "CoachUpNotificationMgr"
    private const val PREFS_NAME = "coachup_notifications"
    private const val KEY_SHOWN_IDS = "shown_notification_ids"
    private const val MAX_TRACKED_IDS = 200

    const val CHANNEL_WORKOUT    = "WORKOUT_CHANNEL"
    const val CHANNEL_MEMBERSHIP = "MEMBERSHIP_CHANNEL"
    const val CHANNEL_COACH      = "COACH_MESSAGE_CHANNEL"
    const val CHANNEL_SYSTEM     = "SYSTEM_CHANNEL"
    const val CHANNEL_REMINDER   = "REMINDER_CHANNEL"

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _currentNotification = MutableStateFlow<AppNotification?>(null)
    val currentNotification: StateFlow<AppNotification?> = _currentNotification.asStateFlow()

    private val notificationIdCounter = AtomicInteger(1000)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var appContext: Context? = null
    private var activeUserId: String? = null

    // -------------------------------------------------------------------------
    // Channel IDs — mirrors iOS category identifiers
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // Initialization
    // -------------------------------------------------------------------------

    /**
     * Call from Application.onCreate() to set up context and notification channels
     * before any event source (Pusher, Realtime) is connected.
     * Does NOT require a userId.
     */
    fun initializeChannelsOnly(context: Context) {
        appContext = context.applicationContext
        createNotificationChannels(context)
    }

    /**
     * Call once after login with a userId.
     * Creates notification channels and starts realtime subscriptions.
     */
    fun initialize(context: Context, userId: String) {
        appContext = context.applicationContext
        activeUserId = userId
        createNotificationChannels(context)

        setupRealtimeListeners(userId)

        scope.launch {
            fetchUnreadCount(userId)
            syncPendingNotifications(userId)
        }
    }

    fun onAppForegroundIfLoggedIn(context: Context) {
        val userId = activeUserId ?: return
        onAppForeground(context, userId)
    }

    /**
     * Re-subscribe Realtime and show any unread notifications not yet displayed in the tray.
     * Call when the app returns to foreground.
     */
    fun onAppForeground(context: Context, userId: String) {
        appContext = context.applicationContext
        activeUserId = userId
        setupRealtimeListeners(userId)
        scope.launch {
            syncPendingNotifications(userId)
        }
    }

    fun canShowNotifications(): Boolean {
        val ctx = appContext ?: return false
        if (!hasNotificationPermission(ctx)) return false
        val profile = UserService.currentProfile.value
        if (profile != null && !profile.notificationsEnabled) return false
        return true
    }

    // -------------------------------------------------------------------------
    // Notification Channels
    // -------------------------------------------------------------------------

    private fun createNotificationChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager

        val channels = listOf(
            NotificationChannel(
                CHANNEL_WORKOUT,
                "Antrenman Bildirimleri",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Antrenman ile ilgili bildirimler" },

            NotificationChannel(
                CHANNEL_MEMBERSHIP,
                "Üyelik Bildirimleri",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Üyelik durum güncellemeleri" },

            NotificationChannel(
                CHANNEL_COACH,
                "Koç Mesajları",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Koçunuzdan gelen mesajlar" },

            NotificationChannel(
                CHANNEL_SYSTEM,
                "Sistem Bildirimleri",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Sistem ve uygulama bildirimleri" },

            NotificationChannel(
                CHANNEL_REMINDER,
                "Hatırlatıcılar",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Programlanmış hatırlatıcılar" }
        )

        channels.forEach { manager.createNotificationChannel(it) }
    }

    // -------------------------------------------------------------------------
    // Post Local Notification
    // -------------------------------------------------------------------------

    /**
     * Posts a local notification. Mirrors iOS scheduleLocalNotification.
     *
     * [launchIntent] is the Intent opened when the user taps the notification.
     * Pass null to use a default launcher intent.
     */
    fun showLocalNotification(
        title: String,
        body: String,
        type: String,
        launchIntent: Intent? = null,
        extras: Map<String, String> = emptyMap(),
        notificationKey: String? = null
    ) {
        val ctx = appContext ?: return
        if (!canShowNotifications()) {
            Log.d(TAG, "Skipping notification — permission disabled or user opted out")
            return
        }

        val channelId = channelForType(type)
        val notifId = notificationIdCounter.getAndIncrement()

        val defaultLaunchIntent = launchIntent ?: Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            ctx,
            notifId,
            defaultLaunchIntent.apply { extras.forEach { (k, v) -> putExtra(k, v) } },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(ctx).notify(notifId, builder.build())
            notificationKey?.let { markNotificationShown(it) }
        } catch (e: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted", e)
        }
    }

    // -------------------------------------------------------------------------
    // Realtime Listeners
    // -------------------------------------------------------------------------

    private fun setupRealtimeListeners(userId: String) {
        RealtimeService.subscribeToNotifications(userId) { notification ->
            scope.launch {
                handleNewNotification(notification)
            }
        }

        RealtimeService.subscribeToMembershipUpdates(userId) { membership ->
            scope.launch {
                handleMembershipUpdate(membership)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Event Handlers
    // -------------------------------------------------------------------------

    private fun handleNewNotification(notification: AppNotification) {
        if (wasNotificationShown(notification.id)) return

        _currentNotification.value = notification
        _unreadCount.value += 1

        showLocalNotification(
            title = notification.title,
            body = notification.message,
            type = notification.type,
            extras = mapOf(
                "notification_id" to notification.id,
                "action_url" to (notification.actionUrl ?: "")
            ),
            notificationKey = notification.id
        )
    }

    private fun handleMembershipUpdate(membership: UserMembership) {
        if (membership.isActive) {
            showLocalNotification(
                title = "Üyelik Aktif",
                body = "Üyeliğiniz aktif edildi. Tüm özelliklere erişebilirsiniz!",
                type = "membership",
                extras = mapOf("membership_id" to membership.id)
            )
        }
    }

    // -------------------------------------------------------------------------
    // Unread Count Management
    // -------------------------------------------------------------------------

    suspend fun syncPendingNotifications(userId: String) {
        if (!canShowNotifications()) return
        try {
            val unread = NotificationService.fetchUnreadNotifications(userId)
            unread.filter { !wasNotificationShown(it.id) }.forEach { notification ->
                showLocalNotification(
                    title = notification.title,
                    body = notification.message,
                    type = notification.type,
                    extras = mapOf(
                        "notification_id" to notification.id,
                        "action_url" to (notification.actionUrl ?: "")
                    ),
                    notificationKey = notification.id
                )
            }
            _unreadCount.value = unread.size
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "syncPendingNotifications failed", e)
        }
    }

    suspend fun fetchUnreadCount(userId: String) {
        try {
            val notifications = NotificationService.fetchUnreadNotifications(userId)
            _unreadCount.value = notifications.size
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Non-fatal
        }
    }

    suspend fun markAsRead(notificationId: String, userId: String) {
        try {
            NotificationService.markAsRead(notificationId)
            _unreadCount.value = maxOf(0, _unreadCount.value - 1)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Non-fatal
        }
    }

    suspend fun markAllAsRead(userId: String) {
        try {
            NotificationService.markAllAsRead(userId)
            _unreadCount.value = 0
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Non-fatal
        }
    }

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    fun cleanup() {
        RealtimeService.unsubscribeAll()
        _unreadCount.value = 0
        activeUserId = null
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun wasNotificationShown(id: String): Boolean {
        val ctx = appContext ?: return false
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_SHOWN_IDS, emptySet())?.contains(id) == true
    }

    private fun markNotificationShown(id: String) {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_SHOWN_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(id)
        if (current.size > MAX_TRACKED_IDS) {
            val trimmed = current.toList().takeLast(MAX_TRACKED_IDS).toSet()
            prefs.edit().putStringSet(KEY_SHOWN_IDS, trimmed).apply()
        } else {
            prefs.edit().putStringSet(KEY_SHOWN_IDS, current).apply()
        }
    }

    private fun channelForType(type: String): String = when (type) {
        "workout"    -> CHANNEL_WORKOUT
        "membership" -> CHANNEL_MEMBERSHIP
        "coach"      -> CHANNEL_COACH
        "reminder"   -> CHANNEL_REMINDER
        else         -> CHANNEL_SYSTEM
    }
}
