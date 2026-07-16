package com.app.coachup.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.app.coachup.app.data.local.entity.CachedCoach
import com.app.coachup.app.data.local.entity.CachedTrainingProgram
import com.app.coachup.app.data.local.entity.CachedUserProfile

// ---------------------------------------------------------------------------
// CachedUserProfile DAO
// ---------------------------------------------------------------------------

@Dao
interface UserProfileDao {

    @Query("SELECT * FROM cached_user_profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CachedUserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: CachedUserProfile)

    @Query("DELETE FROM cached_user_profiles WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM cached_user_profiles")
    suspend fun deleteAll()
}

// ---------------------------------------------------------------------------
// CachedCoach DAO
// ---------------------------------------------------------------------------

@Dao
interface CoachDao {

    @Query("SELECT * FROM cached_coaches ORDER BY name ASC")
    suspend fun getAll(): List<CachedCoach>

    @Query("SELECT * FROM cached_coaches WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CachedCoach?

    @Query("SELECT * FROM cached_coaches WHERE gymId = :gymId ORDER BY name ASC")
    suspend fun getByGym(gymId: String): List<CachedCoach>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(coaches: List<CachedCoach>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(coach: CachedCoach)

    @Query("DELETE FROM cached_coaches")
    suspend fun deleteAll()

    /**
     * Evict stale entries whose sync timestamp is older than [cutoffMillis].
     */
    @Query("DELETE FROM cached_coaches WHERE lastSyncedAt < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)
}

// ---------------------------------------------------------------------------
// CachedTrainingProgram DAO
// ---------------------------------------------------------------------------

@Dao
interface TrainingProgramDao {

    @Query("SELECT * FROM cached_training_programs ORDER BY name ASC")
    suspend fun getAll(): List<CachedTrainingProgram>

    @Query("SELECT * FROM cached_training_programs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CachedTrainingProgram?

    @Query("SELECT * FROM cached_training_programs WHERE isActive = 1 ORDER BY name ASC")
    suspend fun getAllActive(): List<CachedTrainingProgram>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(programs: List<CachedTrainingProgram>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(program: CachedTrainingProgram)

    @Query("DELETE FROM cached_training_programs")
    suspend fun deleteAll()

    /**
     * Evict stale entries whose sync timestamp is older than [cutoffMillis].
     */
    @Query("DELETE FROM cached_training_programs WHERE lastSyncedAt < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)
}
