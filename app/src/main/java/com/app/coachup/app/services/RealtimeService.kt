package com.app.coachup.app.services

import android.util.Log
import com.app.coachup.app.config.SupabaseConfig.client
import com.app.coachup.app.models.AppNotification
import com.app.coachup.app.models.CoachMessage
import com.app.coachup.app.models.UserMembership
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Android equivalent of iOS SupabaseRealtimeService.
 *
 * Uses Supabase Kotlin Realtime v3 API with [postgresChangeFlow].
 * Each subscription is keyed by a channel ID so it can be individually removed.
 */
object RealtimeService {

    private const val TAG = "RealtimeService"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val channels = mutableMapOf<String, RealtimeChannel>()
    private val channelJobs = mutableMapOf<String, Job>()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    // -------------------------------------------------------------------------
    // Notifications Listener
    // -------------------------------------------------------------------------

    fun subscribeToNotifications(
        userId: String,
        onInsert: (AppNotification) -> Unit
    ) {
        val channelId = "notifications:$userId"
        removeChannel(channelId)

        scope.launch {
            runCatching { client.realtime.connect() }
                .onFailure { Log.e(TAG, "Realtime connect failed", it) }

            val channel = client.realtime.channel(channelId)

            val job = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "notifications"
                filter("user_id", FilterOperator.EQ, userId)
            }.onEach { action ->
                runCatching {
                    val notification = json.decodeFromJsonElement(
                        AppNotification.serializer(),
                        action.record
                    )
                    onInsert(notification)
                }.onFailure { Log.e(TAG, "Failed to decode notification insert", it) }
            }.launchIn(scope)

            channelJobs[channelId] = job

            runCatching { channel.subscribe() }
                .onFailure { Log.e(TAG, "Failed to subscribe to notifications channel", it) }
                .onSuccess {
                    channels[channelId] = channel
                    _isConnected.value = true
                    Log.d(TAG, "Subscribed to notifications for user=$userId")
                }
        }
    }

    // -------------------------------------------------------------------------
    // Coach Messages Listener
    // -------------------------------------------------------------------------

    fun subscribeToCoachMessages(
        userId: String,
        coachId: String,
        onInsert: (CoachMessage) -> Unit
    ) {
        val channelId = "coach-messages:$userId:$coachId"
        removeChannel(channelId)

        scope.launch {
            runCatching { client.realtime.connect() }
                .onFailure { Log.e(TAG, "Realtime connect failed", it) }

            val channel = client.realtime.channel(channelId)

            val job = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "coach_messages"
                filter("user_id", FilterOperator.EQ, userId)
            }.onEach { action ->
                runCatching {
                    val message = json.decodeFromJsonElement(
                        CoachMessage.serializer(),
                        action.record
                    )
                    // Additional client-side filter for coach_id since Supabase
                    // realtime filter only supports a single column filter.
                    if (message.coachId == coachId) {
                        onInsert(message)
                    }
                }
            }.launchIn(scope)

            channelJobs[channelId] = job

            runCatching { channel.subscribe() }
                .onFailure { Log.e(TAG, "Failed to subscribe to coach messages channel", it) }
                .onSuccess { channels[channelId] = channel }
        }
    }

    // -------------------------------------------------------------------------
    // Membership Updates Listener
    // -------------------------------------------------------------------------

    fun subscribeToMembershipUpdates(
        userId: String,
        onUpdate: (UserMembership) -> Unit
    ) {
        val channelId = "user-memberships:$userId"
        removeChannel(channelId)

        scope.launch {
            runCatching { client.realtime.connect() }
                .onFailure { Log.e(TAG, "Realtime connect failed", it) }

            val channel = client.realtime.channel(channelId)

            val job = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                table = "user_memberships"
                filter("user_id", FilterOperator.EQ, userId)
            }.onEach { action ->
                runCatching {
                    val membership = json.decodeFromJsonElement(
                        UserMembership.serializer(),
                        action.record
                    )
                    onUpdate(membership)
                }
            }.launchIn(scope)

            channelJobs[channelId] = job

            runCatching { channel.subscribe() }
                .onFailure { Log.e(TAG, "Failed to subscribe to membership channel", it) }
                .onSuccess { channels[channelId] = channel }
        }
    }

    // -------------------------------------------------------------------------
    // Channel Management
    // -------------------------------------------------------------------------

    fun unsubscribe(channelId: String) {
        removeChannel(channelId)
        if (channels.isEmpty()) _isConnected.value = false
    }

    fun unsubscribeAll() {
        scope.launch {
            channels.values.forEach { channel ->
                runCatching { client.realtime.removeChannel(channel) }
            }
            channels.clear()
            channelJobs.values.forEach { it.cancel() }
            channelJobs.clear()
            _isConnected.value = false
        }
    }

    private fun removeChannel(channelId: String) {
        channels[channelId]?.let { channel ->
            scope.launch {
                runCatching { client.realtime.removeChannel(channel) }
            }
            channels.remove(channelId)
        }
        channelJobs[channelId]?.cancel()
        channelJobs.remove(channelId)
    }
}
