package com.app.coachup.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for offline-cached user profiles.
 * Stores a denormalised snapshot of UserProfile fields most needed
 * across the app without re-fetching on every navigation.
 *
 * [lastSyncedAt] is an epoch-millisecond timestamp used for cache
 * invalidation — rows older than a threshold are treated as stale.
 */
@Entity(tableName = "cached_user_profiles")
data class CachedUserProfile(
    @PrimaryKey val id: String,
    val email: String? = null,
    val name: String? = null,
    val surname: String? = null,
    val phone: String? = null,
    val birthDate: String? = null,
    val gender: String? = null,
    val height: Double? = null,
    val weight: Double? = null,
    val profileImageUrl: String? = null,
    val role: String? = null,
    val notificationsEnabled: Boolean = true,
    val biometricEnabled: Boolean = false,
    val defaultScreen: String = "home",
    val weightUnit: String = "kg",
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val gymId: String? = null,
    val gymName: String? = null,
    val defaultLocation: String? = null,
    val isBanned: Boolean? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    val lastSyncedAt: Long = System.currentTimeMillis()
)

/**
 * Room entity for offline-cached coaches.
 * [specialty] is stored as a plain String (may be null or a free-text value).
 * [lastSyncedAt] is an epoch-millisecond timestamp for cache invalidation.
 */
@Entity(tableName = "cached_coaches")
data class CachedCoach(
    @PrimaryKey val id: String,
    val gymId: String? = null,
    val name: String,
    val surname: String,
    val email: String? = null,
    val phone: String? = null,
    val gender: String,
    val specialty: String? = null,
    val bio: String? = null,
    val profileImageUrl: String? = null,
    val rating: Double = 0.0,
    val experienceYears: Int = 0,
    val isActive: Boolean = true,
    val createdAt: String = "",
    val updatedAt: String = "",
    val lastSyncedAt: Long = System.currentTimeMillis()
)

/**
 * Room entity for offline-cached training programs.
 * [featuresJson] stores the features list as a JSON array String because
 * Room does not support List columns natively without a TypeConverter.
 */
@Entity(tableName = "cached_training_programs")
data class CachedTrainingProgram(
    @PrimaryKey val id: String,
    val name: String,
    val description: String? = null,
    val category: String,
    val duration: Int,
    val difficulty: String,
    val caloriesBurn: Int = 0,
    val exerciseCount: Int = 0,
    val iconName: String? = null,
    val isActive: Boolean = true,
    val createdAt: String = "",
    val lastSyncedAt: Long = System.currentTimeMillis()
)
