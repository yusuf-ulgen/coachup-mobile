package com.app.coachup.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.app.coachup.app.data.local.dao.CoachDao
import com.app.coachup.app.data.local.dao.TrainingProgramDao
import com.app.coachup.app.data.local.dao.UserProfileDao
import com.app.coachup.app.data.local.entity.CachedCoach
import com.app.coachup.app.data.local.entity.CachedTrainingProgram
import com.app.coachup.app.data.local.entity.CachedUserProfile

/**
 * Room database for local caching.
 *
 * Schema version policy:
 * - Increment [version] on every structural change to an entity.
 * - Add a proper [androidx.room.migration.Migration] when going to production.
 *   [fallbackToDestructiveMigration] is acceptable during development only.
 */
@Database(
    entities = [
        CachedUserProfile::class,
        CachedCoach::class,
        CachedTrainingProgram::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userProfileDao(): UserProfileDao
    abstract fun coachDao(): CoachDao
    abstract fun trainingProgramDao(): TrainingProgramDao

    companion object {
        private const val DB_NAME = "coachup_cache.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
