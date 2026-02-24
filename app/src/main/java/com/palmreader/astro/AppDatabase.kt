package com.palmreader.astro

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [UserEntity::class, HistoryEntity::class, CreditTransactionEntity::class, PersonaEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun historyDao(): HistoryDao
    abstract fun creditTransactionDao(): CreditTransactionDao
    abstract fun personaDao(): PersonaDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN dob TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE users ADD COLUMN birthPlace TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE users ADD COLUMN mobile TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE users ADD COLUMN profilePhotoUri TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `persona` (
                        `userId` INTEGER NOT NULL,
                        `dob` TEXT NOT NULL,
                        `relationshipStatus` TEXT NOT NULL,
                        `occupation` TEXT NOT NULL,
                        `lifeGoal` TEXT NOT NULL,
                        `biggestConcern` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`userId`)
                    )"""
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val hasPersonaTable = db.query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='persona'"
                ).use { it.moveToFirst() }

                if (hasPersonaTable) {
                    db.execSQL("ALTER TABLE persona ADD COLUMN aiSummary TEXT NOT NULL DEFAULT ''")
                } else {
                    db.execSQL(
                        """CREATE TABLE IF NOT EXISTS `persona` (
                            `userId` INTEGER NOT NULL,
                            `dob` TEXT NOT NULL,
                            `relationshipStatus` TEXT NOT NULL,
                            `occupation` TEXT NOT NULL,
                            `lifeGoal` TEXT NOT NULL,
                            `biggestConcern` TEXT NOT NULL,
                            `aiSummary` TEXT NOT NULL DEFAULT '',
                            `updatedAt` INTEGER NOT NULL,
                            PRIMARY KEY(`userId`)
                        )"""
                    )
                }
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "astro_db")
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration() // dev-only; use Migration objects in production
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
