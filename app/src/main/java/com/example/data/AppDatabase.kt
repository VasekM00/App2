package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SettingsEntity::class, LedgerEntryEntity::class, ActionStateEntity::class],
    version = 15,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun actionStateDao(): ActionStateDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN monteCarloVolatilityPct REAL NOT NULL DEFAULT 15.0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN monteCarloSeed INTEGER NOT NULL DEFAULT 42")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN primaryName TEXT NOT NULL DEFAULT 'Václav'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN spouseName TEXT NOT NULL DEFAULT 'Eleonora'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN isSingleHousehold INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN dcaAnnualGrowthPct REAL NOT NULL DEFAULT 0.0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "martinu_financials_db"
                )
                    .addMigrations(MIGRATION_13_14, MIGRATION_14_15)
                    .fallbackToDestructiveMigration(true)
                    .fallbackToDestructiveMigrationOnDowngrade(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
