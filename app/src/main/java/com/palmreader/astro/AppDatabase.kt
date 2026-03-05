package com.palmreader.astro

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [UserEntity::class, HistoryEntity::class, CreditTransactionEntity::class, PersonaEntity::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun historyDao(): HistoryDao
    abstract fun creditTransactionDao(): CreditTransactionDao
    abstract fun personaDao(): PersonaDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private fun tableExists(db: SupportSQLiteDatabase, tableName: String): Boolean {
            return db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='$tableName'"
            ).use { it.moveToFirst() }
        }

        private fun columnExists(db: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
            return db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0 && cursor.getString(nameIndex) == columnName) return@use true
                }
                false
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!tableExists(db, "credit_transactions")) {
                    db.execSQL(
                        """CREATE TABLE IF NOT EXISTS `credit_transactions` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `userId` INTEGER NOT NULL,
                            `type` TEXT NOT NULL,
                            `amount` INTEGER NOT NULL,
                            `description` TEXT NOT NULL,
                            `timestamp` INTEGER NOT NULL
                        )"""
                    )
                }
                if (!columnExists(db, "users", "planType")) {
                    db.execSQL("ALTER TABLE users ADD COLUMN planType TEXT NOT NULL DEFAULT 'FREE'")
                }
                if (!columnExists(db, "users", "planExpiry")) {
                    db.execSQL("ALTER TABLE users ADD COLUMN planExpiry INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!columnExists(db, "users", "dob")) {
                    db.execSQL("ALTER TABLE users ADD COLUMN dob TEXT NOT NULL DEFAULT ''")
                }
                if (!columnExists(db, "users", "birthPlace")) {
                    db.execSQL("ALTER TABLE users ADD COLUMN birthPlace TEXT NOT NULL DEFAULT ''")
                }
                if (!columnExists(db, "users", "mobile")) {
                    db.execSQL("ALTER TABLE users ADD COLUMN mobile TEXT NOT NULL DEFAULT ''")
                }
                if (!columnExists(db, "users", "profilePhotoUri")) {
                    db.execSQL("ALTER TABLE users ADD COLUMN profilePhotoUri TEXT NOT NULL DEFAULT ''")
                }
                if (!tableExists(db, "persona")) {
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
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val hasPersonaTable = db.query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='persona'"
                ).use { it.moveToFirst() }

                if (hasPersonaTable) {
                    if (!columnExists(db, "persona", "aiSummary")) {
                        db.execSQL("ALTER TABLE persona ADD COLUMN aiSummary TEXT NOT NULL DEFAULT ''")
                    }
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

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!columnExists(db, "users", "lastFreeTopupAt")) {
                    db.execSQL("ALTER TABLE users ADD COLUMN lastFreeTopupAt INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "astro_db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
