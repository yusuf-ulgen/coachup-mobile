package com.app.coachup.app.config

/** Varsayılan Supabase sorgu limitleri — aşırı payload ve bellek kullanımını önler. */
object ApiLimits {
    const val TRAINING_PROGRAMS = 100
    const val COACHES = 50
    const val COACH_MESSAGES = 100
    const val CLASS_BOOKINGS = 50
    const val PROGRESS_PHOTOS = 20
}
