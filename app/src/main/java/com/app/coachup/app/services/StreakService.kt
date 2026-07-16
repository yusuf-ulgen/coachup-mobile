package com.app.coachup.app.services

import com.app.coachup.app.config.SupabaseConfig
import com.app.coachup.app.models.StreakUpdate
import com.app.coachup.app.models.TrainingSession
import com.app.coachup.app.models.UserActivity
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class StreakActivityItem(
    val date: LocalDate,
    val label: String
)

object StreakService {

    private val supabase get() = SupabaseConfig.client
    private val zone: ZoneId get() = ZoneId.systemDefault()

    val milestoneDays = listOf(3, 7, 14, 30, 50, 100)

    fun milestoneLevel(streak: Int): Int =
        milestoneDays.lastOrNull { streak >= it } ?: 0

    /**
     * Ardışık gün streak — bugün aktivite yoksa dünden geriye sayar (gün bitmeden seri korunur).
     */
    fun computeConsecutiveStreak(activeDays: Set<LocalDate>, today: LocalDate = LocalDate.now()): Int {
        var streak = 0
        var cursor = today
        if (cursor !in activeDays) {
            cursor = cursor.minusDays(1)
        }
        while (cursor in activeDays) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    suspend fun syncUserStreak(userId: String): Int {
        val activeDays = fetchActivityDays(userId)
        val streak = computeConsecutiveStreak(activeDays)
        val profile = UserService.currentProfile.value
        val longest = maxOf(streak, profile?.longestStreak ?: 0)
        val lastDate = activeDays.maxOrNull()?.toString()

        supabase.from("users")
            .update(
                StreakUpdate(
                    currentStreak = streak,
                    longestStreak = longest,
                    lastActivityDate = lastDate
                )
            ) {
                filter { eq("id", userId) }
            }

        UserService.fetchProfile(userId)
        return streak
    }

    suspend fun recordActivityAndSync(
        userId: String,
        activityType: String,
        durationMinutes: Int? = null,
        calories: Int? = null
    ) {
        val today = LocalDate.now()
        if (!hasActivityOnDate(userId, today)) {
            UserService.recordActivity(
                userId = userId,
                type = activityType,
                duration = durationMinutes,
                calories = calories
            )
        }
        syncUserStreak(userId)
    }

    suspend fun hasActivityOnDate(userId: String, date: LocalDate): Boolean =
        date in fetchActivityDays(userId, lookbackDays = 3)

    suspend fun fetchActivityDays(userId: String, lookbackDays: Int = 120): Set<LocalDate> {
        val since = LocalDate.now().minusDays(lookbackDays.toLong())
        val days = mutableSetOf<LocalDate>()

        runCatching {
            val sinceIso = since.atStartOfDay(zone).toInstant().toString()
            supabase.from("training_sessions")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("status", "completed")
                        gte("completed_at", sinceIso)
                    }
                }
                .decodeList<TrainingSession>()
        }.getOrDefault(emptyList()).forEach { session ->
            parseToLocalDate(session.completedAt)?.let { days.add(it) }
        }

        runCatching {
            supabase.from("record_attempts")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("status", "completed")
                    }
                    order("completed_at", Order.DESCENDING)
                    limit(100)
                }
                .decodeList<RecordAttempt>()
        }.getOrDefault(emptyList()).forEach { attempt ->
            parseToLocalDate(attempt.completedAt)?.let { days.add(it) }
        }

        runCatching {
            supabase.from("user_activities")
                .select {
                    filter {
                        eq("user_id", userId)
                        gte("activity_date", since.toString())
                    }
                }
                .decodeList<UserActivity>()
        }.getOrDefault(emptyList()).forEach { activity ->
            parseActivityDate(activity.activityDate)?.let { days.add(it) }
        }

        return days
    }

    suspend fun fetchRecentActivities(userId: String, limit: Int = 10): List<StreakActivityItem> {
        val items = mutableListOf<StreakActivityItem>()

        runCatching {
            supabase.from("training_sessions")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("status", "completed")
                    }
                    order("completed_at", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<TrainingSession>()
        }.getOrDefault(emptyList()).forEach { session ->
            val date = parseToLocalDate(session.completedAt) ?: return@forEach
            items.add(StreakActivityItem(date = date, label = session.activityDisplayName))
        }

        runCatching {
            supabase.from("record_attempts")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("status", "completed")
                    }
                    order("completed_at", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<RecordAttempt>()
        }.getOrDefault(emptyList()).forEach { attempt ->
            val date = parseToLocalDate(attempt.completedAt) ?: return@forEach
            items.add(StreakActivityItem(date = date, label = "Rekor Denemesi"))
        }

        return items
            .sortedByDescending { it.date }
            .distinctBy { "${it.date}_${it.label}" }
            .take(limit)
    }

    fun activityLabelFromSession(session: TrainingSession): String = session.activityDisplayName

    fun formatActivityDate(date: LocalDate, today: LocalDate = LocalDate.now()): String {
        val yesterday = today.minusDays(1)
        return when (date) {
            today -> "Bugün"
            yesterday -> "Dün"
            else -> date.format(DateTimeFormatter.ofPattern("d MMM", Locale("tr")))
        }
    }

    private fun parseToLocalDate(iso: String?): LocalDate? = runCatching {
        if (iso.isNullOrBlank()) return null
        if (iso.length == 10) LocalDate.parse(iso)
        else Instant.parse(iso).atZone(zone).toLocalDate()
    }.getOrNull()

    private fun parseActivityDate(raw: String): LocalDate? = runCatching {
        if (raw.length >= 10) LocalDate.parse(raw.take(10))
        else Instant.parse(raw).atZone(zone).toLocalDate()
    }.getOrNull()

    private const val REMINDER_PREFS = "coachup_streak_reminder"
    private const val KEY_LAST_REMINDER_DATE = "last_date"

    /** 18:00 sonrası bugün aktivite yoksa ve aktif streak varsa hatırlatma (günde bir). */
    suspend fun maybeSendEveningReminder(context: android.content.Context, userId: String) {
        if (LocalTime.now().hour < 18) return

        val prefs = context.getSharedPreferences(REMINDER_PREFS, android.content.Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        if (prefs.getString(KEY_LAST_REMINDER_DATE, null) == today) return

        syncUserStreak(userId)
        val streak = UserService.currentProfile.value?.currentStreak ?: 0
        if (streak < 1) return
        if (hasActivityOnDate(userId, LocalDate.now())) return

        CoachUpNotificationManager.showLocalNotification(
            title = "Streak",
            body = "🔥 $streak günlük streak'in devam ediyor! Bugünkü antrenmanını kaydetmeyi unutma.",
            type = "reminder",
            notificationKey = "streak_evening_$today"
        )
        prefs.edit().putString(KEY_LAST_REMINDER_DATE, today).apply()
    }
}
