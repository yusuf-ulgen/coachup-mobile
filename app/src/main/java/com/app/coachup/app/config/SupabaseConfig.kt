package com.app.coachup.app.config

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

/**
 * Singleton Supabase client.
 * Matches the iOS SupabaseClient.swift singleton in url, key, and installed plugins.
 *
 * Gym-specific constants (gym id, name, default location) live in [GymConfig]
 * so that cloning the app for a new gym requires touching only that one object.
 */
object SupabaseConfig {

    const val SUPABASE_URL = "https://auiebboyocmkkxbdahqf.supabase.co"
    const val SUPABASE_KEY = "sb_publishable_gldj0fxGYVdS5WmeTEQC0Q_L5sPqgEa"

    // ---------------------------------------------------------------------------
    // Gym-level convenience aliases — delegate to GymConfig (the canonical source).
    // Update GymConfig.kt when deploying for a different gym.
    // ---------------------------------------------------------------------------
    val GYM_ID: String get() = GymConfig.GYM_ID
    val GYM_NAME: String get() = GymConfig.GYM_NAME
    val DEFAULT_LOCATION: String get() = GymConfig.DEFAULT_LOCATION

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_KEY
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
            install(Storage)
            install(Functions)
        }
    }
}
