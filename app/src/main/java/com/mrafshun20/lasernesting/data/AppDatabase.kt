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
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun partDao(): PartDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v2 -> v3: pricing fields on the single settings row. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE settings ADD COLUMN pricePerKg REAL NOT NULL DEFAULT 0.0"
                )
                db.execSQL(
                    "ALTER TABLE settings ADD COLUMN machineRatePerMinute REAL NOT NULL DEFAULT 0.0"
                )
                db.execSQL(
                    "ALTER TABLE settings ADD COLUMN pierceCost REAL NOT NULL DEFAULT 0.0"
                )
            }
        }

        /** v3 -> v4: cutting priced per metre of cut (cutPricePerMeter). */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE settings ADD COLUMN cutPricePerMeter REAL NOT NULL DEFAULT 0.0"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "laser_nesting.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    // The destructive fallback stays only as a safety net
                    // for unknown jumps; the steps above are the real
                    // migrations and keep the user's parts and settings.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
