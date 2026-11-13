package com.aetheria.aura.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Conversation::class, Message::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AuraDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile private var INSTANCE: AuraDatabase? = null

        // Migration from version 1 to 2 (if needed for future schema changes)
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // No schema changes yet, but migration path exists
            }
        }

        fun getInstance(context: Context): AuraDatabase {
            return INSTANCE ?: synchronized(this) {
                Log.d("AuraDatabase", "Creating database instance")
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AuraDatabase::class.java,
                    "aura_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            Log.d("AuraDatabase", "Database created")
                        }
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            Log.d("AuraDatabase", "Database opened")
                        }
                    })
                    .build()
                Log.d("AuraDatabase", "Database instance created successfully")
                INSTANCE = instance
                instance
            }
        }
    }
}