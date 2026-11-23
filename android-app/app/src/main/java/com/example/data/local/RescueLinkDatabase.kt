package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserAccountEntity::class,
        EmergencyContactEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        SosAlertEntity::class,
        SeenPacketEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class RescueLinkDatabase : RoomDatabase() {
    abstract fun dao(): RescueLinkDao

    companion object {
        @Volatile
        private var INSTANCE: RescueLinkDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sos_alerts ADD COLUMN senderId TEXT NOT NULL DEFAULT 'RL-LOCAL'")
                db.execSQL("ALTER TABLE sos_alerts ADD COLUMN senderName TEXT NOT NULL DEFAULT 'Me'")
                db.execSQL("ALTER TABLE sos_alerts ADD COLUMN isFromMe INTEGER NOT NULL DEFAULT 1")
            }
        }

        fun getInstance(context: Context): RescueLinkDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RescueLinkDatabase::class.java,
                    "rescuelink_local.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
