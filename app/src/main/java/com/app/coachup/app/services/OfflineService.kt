package com.app.coachup.app.services

import com.app.coachup.app.data.local.AppDatabase
import com.app.coachup.app.data.local.entity.CachedCoach
import com.app.coachup.app.data.local.entity.CachedTrainingProgram
import com.app.coachup.app.data.local.entity.CachedUserProfile
import com.app.coachup.app.models.Coach
import com.app.coachup.app.models.TrainingProgram
import com.app.coachup.app.models.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android equivalent of iOS OfflineService.
 *
 * Uses Room (via [AppDatabase]) to cache:
 *  - User profile
 *  - Coaches list
 *  - Training programs list
 *
 * Create one instance per process (e.g. in Application) and inject or access it
 * from [SyncService].
 */
class OfflineService(private val db: AppDatabase) {

    private val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    // -------------------------------------------------------------------------
    // User Profile Cache
    // -------------------------------------------------------------------------

    suspend fun cacheUserProfile(profile: UserProfile) {
        val cached = CachedUserProfile(
            id = profile.id,
            email = profile.email,
            name = profile.name,
            surname = profile.surname,
            phone = profile.phone,
            gender = profile.gender,
            height = profile.height,
            weight = profile.weight,
            profileImageUrl = profile.profileImageUrl,
            role = profile.role,
            notificationsEnabled = profile.notificationsEnabled,
            biometricEnabled = profile.biometricEnabled,
            defaultScreen = profile.defaultScreen,
            weightUnit = profile.weightUnit,
            currentStreak = profile.currentStreak,
            longestStreak = profile.longestStreak,
            gymId = profile.gymId,
            gymName = profile.gymName,
            defaultLocation = profile.defaultLocation,
            isBanned = profile.isBanned,
            createdAt = profile.createdAt ?: "",
            updatedAt = profile.updatedAt ?: "",
            lastSyncedAt = System.currentTimeMillis()
        )
        db.userProfileDao().upsert(cached)
    }

    suspend fun getCachedUserProfile(id: String): UserProfile? {
        val cached = db.userProfileDao().getById(id) ?: return null
        return UserProfile(
            id = cached.id,
            email = cached.email,
            name = cached.name,
            surname = cached.surname,
            phone = cached.phone,
            gender = cached.gender,
            height = cached.height,
            weight = cached.weight,
            profileImageUrl = cached.profileImageUrl,
            role = cached.role,
            notificationsEnabled = cached.notificationsEnabled,
            biometricEnabled = cached.biometricEnabled,
            defaultScreen = cached.defaultScreen,
            weightUnit = cached.weightUnit,
            currentStreak = cached.currentStreak,
            longestStreak = cached.longestStreak,
            gymId = cached.gymId,
            gymName = cached.gymName,
            defaultLocation = cached.defaultLocation,
            isBanned = cached.isBanned,
            createdAt = cached.createdAt,
            updatedAt = cached.updatedAt
        )
    }

    // -------------------------------------------------------------------------
    // Coaches Cache
    // -------------------------------------------------------------------------

    suspend fun cacheCoaches(coaches: List<Coach>) {
        val cached = coaches.map { coach ->
            CachedCoach(
                id = coach.id,
                gymId = coach.gymId,
                name = coach.name,
                surname = coach.surname,
                email = coach.email,
                phone = coach.phone,
                gender = coach.gender,
                specialty = coach.specialty,
                bio = coach.bio,
                profileImageUrl = coach.profileImageUrl,
                rating = coach.rating,
                experienceYears = coach.experienceYears,
                isActive = coach.isActive,
                createdAt = coach.createdAt,
                lastSyncedAt = System.currentTimeMillis()
            )
        }
        db.coachDao().deleteAll()
        db.coachDao().upsertAll(cached)
    }

    suspend fun getCachedCoaches(): List<Coach> {
        return db.coachDao().getAll().map { cached ->
            Coach(
                id = cached.id,
                gymId = cached.gymId,
                name = cached.name,
                surname = cached.surname,
                email = cached.email,
                phone = cached.phone,
                gender = cached.gender,
                specialty = cached.specialty,
                bio = cached.bio,
                profileImageUrl = cached.profileImageUrl,
                rating = cached.rating,
                experienceYears = cached.experienceYears,
                isActive = cached.isActive,
                createdAt = cached.createdAt
            )
        }
    }

    // -------------------------------------------------------------------------
    // Training Programs Cache
    // -------------------------------------------------------------------------

    suspend fun cacheTrainingPrograms(programs: List<TrainingProgram>) {
        val cached = programs.map { program ->
            CachedTrainingProgram(
                id = program.id,
                name = program.name,
                description = program.description,
                category = program.category ?: "strength",
                difficulty = program.difficulty ?: "intermediate",
                duration = program.duration ?: 0,
                caloriesBurn = program.caloriesBurn ?: 0,
                exerciseCount = program.exerciseCount ?: 0,
                iconName = program.iconName,
                isActive = program.isActive,
                createdAt = program.createdAt,
                lastSyncedAt = System.currentTimeMillis()
            )
        }
        db.trainingProgramDao().deleteAll()
        db.trainingProgramDao().upsertAll(cached)
    }

    suspend fun getCachedTrainingPrograms(): List<TrainingProgram> {
        return db.trainingProgramDao().getAll().map { cached ->
            TrainingProgram(
                id = cached.id,
                name = cached.name,
                description = cached.description,
                category = cached.category,
                difficulty = cached.difficulty,
                duration = cached.duration,
                caloriesBurn = cached.caloriesBurn,
                exerciseCount = cached.exerciseCount,
                iconName = cached.iconName,
                isActive = cached.isActive,
                createdAt = cached.createdAt
            )
        }
    }

    // -------------------------------------------------------------------------
    // Clear Cache
    // -------------------------------------------------------------------------

    suspend fun clearAllCache() {
        db.userProfileDao().deleteAll()
        db.coachDao().deleteAll()
        db.trainingProgramDao().deleteAll()
    }

    /**
     * Clears coach and program cache entries older than [days] days.
     * Mirrors iOS clearOldCache(olderThan:).
     */
    suspend fun clearOldCache(days: Int) {
        val cutoffMs = System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000)
        db.coachDao().deleteOlderThan(cutoffMs)
        db.trainingProgramDao().deleteOlderThan(cutoffMs)
    }

    // -------------------------------------------------------------------------
    // Sync All — called by SyncService
    // -------------------------------------------------------------------------

    /**
     * Performs a full remote-to-cache sync.
     * Each sub-sync is wrapped in [runCatching] so one failure does not abort the rest.
     * Callers (UserService, CoachService, TrainingService) are wired in once those
     * primary services are available in the same package.
     */
    suspend fun syncAll() {
        _isOfflineMode.value = false

        runCatching {
            val userId = AuthService.getCurrentUserId()
            if (userId != null) {
                UserService.fetchProfile(userId)?.let { cacheUserProfile(it) }
            }
        }

        runCatching {
            cacheCoaches(CoachService.fetchCoaches())
        }

        runCatching {
            cacheTrainingPrograms(TrainingService.fetchPrograms())
        }
    }
}
