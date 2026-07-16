package com.app.coachup.app

import android.app.Application
import com.app.coachup.app.data.local.AppDatabase
import com.app.coachup.app.services.CoachUpNotificationManager
import com.app.coachup.app.services.HealthConnectService
import com.app.coachup.app.services.OfflineService
import com.app.coachup.app.services.PusherService
import com.app.coachup.app.services.SyncService

/**
 * Application class for CoachUp.
 *
 * Mirrors the role of iOS CoachUpApp (the @main App struct) as the
 * application-level entry point.
 */
class CoachUpApp : Application() {

    lateinit var offlineService: OfflineService
        private set

    override fun onCreate() {
        super.onCreate()

        runCatching {
            @Suppress("UNUSED_EXPRESSION")
            com.app.coachup.app.config.SupabaseConfig.client
        }.onFailure {
            android.util.Log.e("CoachUpApp", "Supabase init failed", it)
        }

        runCatching { CoachUpNotificationManager.initializeChannelsOnly(this) }
            .onFailure { android.util.Log.e("CoachUpApp", "Notification channels failed", it) }

        runCatching { PusherService.initialize() }
            .onFailure { android.util.Log.e("CoachUpApp", "Pusher init failed", it) }

        runCatching { HealthConnectService.init(this) }
            .onFailure { android.util.Log.e("CoachUpApp", "HealthConnect init failed", it) }

        val db = AppDatabase.getInstance(this)
        offlineService = OfflineService(db)
        runCatching { SyncService.startMonitoring(this, offlineService) }
            .onFailure { android.util.Log.e("CoachUpApp", "Sync init failed", it) }
    }
}
