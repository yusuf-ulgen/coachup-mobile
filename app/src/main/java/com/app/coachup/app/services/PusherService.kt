package com.app.coachup.app.services

import android.util.Log
import com.pusher.client.Pusher
import com.pusher.client.PusherOptions
import com.pusher.client.channel.Channel
import com.pusher.client.channel.PusherEvent
import com.pusher.client.channel.SubscriptionEventListener
import com.pusher.client.connection.ConnectionEventListener
import com.pusher.client.connection.ConnectionState
import com.pusher.client.connection.ConnectionStateChange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Pusher Channels service for real-time event delivery.
 *
 * Channel structure:
 *  - "gym-{gymId}"   → salon geneli bildirimler (yalnızca o salona üye kullanıcılar)
 *  - "user-{userId}" → kişiye özel bildirimler
 */
object PusherService {

    private const val TAG = "PusherService"
    private const val APP_KEY = "7182d794928ce4c50d57"
    private const val CLUSTER = "eu"
    private const val EVENT_NOTIFICATION = "notification"

    private var pusher: Pusher? = null
    private val channels = mutableMapOf<String, Channel>()
    private val boundEvents = mutableSetOf<Pair<String, String>>()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    private var currentUserId: String? = null
    private var subscribedGymId: String? = null

    @Serializable
    private data class PusherNotification(
        val title: String,
        val message: String,
        @SerialName("target_type") val targetType: String? = null,
        @SerialName("target_id") val targetId: String? = null
    )

    /**
     * Connect to Pusher. Call once from Application.onCreate().
     * Gym/user channels are subscribed after login.
     */
    fun initialize() {
        runCatching {
            val options = PusherOptions().apply {
                setCluster(CLUSTER)
            }
            val instance = Pusher(APP_KEY, options)
            instance.connect(object : ConnectionEventListener {
                override fun onConnectionStateChange(change: ConnectionStateChange) {
                    _isConnected.value = change.currentState == ConnectionState.CONNECTED
                }

                override fun onError(message: String, code: String?, e: Exception?) {
                    _isConnected.value = false
                    Log.e(TAG, "Pusher error: $message code=$code", e)
                }
            }, ConnectionState.ALL)
            pusher = instance
        }.onFailure { Log.e(TAG, "Pusher initialize failed", it) }
    }

    /**
     * Subscribe to user-specific channel after login.
     */
    fun subscribeToUser(userId: String) {
        currentUserId = userId
        subscribe("user-$userId", EVENT_NOTIFICATION) { data ->
            handleNotificationEvent(data, channelScope = "user")
        }
    }

    /**
     * Subscribe to the user's gym channel (salon geneli duyurular).
     * Call after profile is loaded with the user's gymId.
     */
    fun subscribeToGym(gymId: String) {
        if (subscribedGymId == gymId) return
        subscribedGymId?.let { unsubscribe("gym-$it") }
        subscribedGymId = gymId
        subscribe("gym-$gymId", EVENT_NOTIFICATION) { data ->
            handleNotificationEvent(data, channelScope = "gym")
        }
        Log.d(TAG, "Subscribed to gym channel gym-$gymId")
    }

    /**
     * Unsubscribe from user-specific channel on logout.
     */
    fun unsubscribeFromUser(userId: String) {
        if (currentUserId == userId) currentUserId = null
        unsubscribe("user-$userId")
    }

    fun unsubscribeFromGym() {
        subscribedGymId?.let { unsubscribe("gym-$it") }
        subscribedGymId = null
    }

    fun setCurrentUser(userId: String?) {
        currentUserId = userId
    }

    private fun handleNotificationEvent(data: String, channelScope: String) {
        runCatching {
            val notification = json.decodeFromString<PusherNotification>(data)
            val userId = currentUserId
            val gymId = subscribedGymId

            when (notification.targetType) {
                "user" -> {
                    if (notification.targetId != null && notification.targetId != userId) return
                }
                "gym" -> {
                    if (notification.targetId != null && notification.targetId != gymId) return
                }
                null -> {
                    // Gym channel without explicit target — only for gym subscribers
                    if (channelScope == "user" && notification.targetId != null &&
                        notification.targetId != userId
                    ) return
                }
            }

            if (!CoachUpNotificationManager.canShowNotifications()) return

            CoachUpNotificationManager.showLocalNotification(
                title = notification.title,
                body = notification.message,
                type = "system"
            )
        }.onFailure { Log.e(TAG, "Failed to handle Pusher notification", it) }
    }

    fun subscribe(
        channelName: String,
        eventName: String,
        onEvent: (data: String) -> Unit
    ) {
        val bindingKey = channelName to eventName
        if (bindingKey in boundEvents) return

        val channel = channels[channelName]
            ?: pusher?.subscribe(channelName)?.also { channels[channelName] = it } ?: return

        channel.bind(eventName, object : SubscriptionEventListener {
            override fun onEvent(event: PusherEvent) {
                event.data?.let { onEvent(it) }
            }
        })
        boundEvents.add(bindingKey)
    }

    fun unsubscribe(channelName: String) {
        pusher?.unsubscribe(channelName)
        channels.remove(channelName)
        boundEvents.removeAll { it.first == channelName }
    }

    fun onLogout() {
        currentUserId?.let { unsubscribeFromUser(it) }
        unsubscribeFromGym()
        currentUserId = null
    }

    fun disconnect() {
        pusher?.disconnect()
        channels.clear()
        boundEvents.clear()
        subscribedGymId = null
        currentUserId = null
        _isConnected.value = false
    }
}
