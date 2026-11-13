package com.mrafshun20.lasernesting.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PartEntity::class, SettingsEntity::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun partDao(): PartDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        /** v3 adds the nesting-engine settings; existing parts and settings are kept. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN nestMode TEXT NOT NULL DEFAULT 'AUTO'")
                db.execSQL("ALTER TABLE settings ADD COLUMN precisionMm REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE settings ADD COLUMN rotationStepDeg INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE settings ADD COLUMN pierceTimeSec REAL NOT NULL DEFAULT 0.5")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "laser_nesting.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    // Older (v1) databases cannot be migrated; their part
                    // lists are small and re-enterable.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
