package com.app.coachup.app.config

/**
 * Per-gym application configuration.
 * When cloning this app for a different gym, only the values in this file
 * need to be changed — mirroring the iOS GymConfig.swift pattern.
 */
object GymConfig {

    /** The gym's UUID in Supabase. */
    const val GYM_ID = "df334ba1-3f93-4f78-bf54-06be09ff989d"

    /** Gym display name shown in the UI. */
    const val GYM_NAME = "Military Wod - Alanya"

    /** Default entry location used for QR check-ins. */
    const val DEFAULT_LOCATION = "Ana Salon"

    /** Splash screen background color (default solid black: 0xFF000000) */
    const val SPLASH_BG_COLOR = 0xFF000000

    /** Splash screen center logo/icon resource ID */
    val SPLASH_LOGO_RES = com.app.coachup.app.R.drawable.coach_logo

    /** Login & Register screens logo resource ID */
    val LOGIN_LOGO_RES = com.app.coachup.app.R.drawable.coach_logo

    /** Login & Register screens top hero image background resource ID */
    val LOGIN_HERO_RES = com.app.coachup.app.R.drawable.man_image
}
