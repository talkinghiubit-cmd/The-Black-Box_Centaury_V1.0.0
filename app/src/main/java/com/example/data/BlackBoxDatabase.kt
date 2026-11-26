package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        VaultRecordEntity::class,
        VaultMetadataEntity::class,
        PasswordEntity::class,
        SecureNoteEntity::class,
        VaultAttachmentEntity::class
    ],
    version = 6,
    exportSchema = true
)
@TypeConverters(EncryptedFieldConverter::class, EncryptedBlobConverter::class)
abstract class BlackBoxDatabase : RoomDatabase() {

    abstract fun vaultDao(): VaultDao
    abstract fun passwordDao(): PasswordDao
    abstract fun secureNoteDao(): SecureNoteDao
    abstract fun metadataDao(): VaultMetadataDao

    companion object {
        @Volatile
        private var INSTANCE: BlackBoxDatabase? = null

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vault_metadata ADD COLUMN is_voice_biometric_enabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE vault_metadata ADD COLUMN encrypted_voice_profile TEXT")
                db.execSQL("ALTER TABLE vault_metadata ADD COLUMN voice_wrapped_password TEXT")
            }
        }

        fun getDatabase(context: Context): BlackBoxDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BlackBoxDatabase::class.java,
                    "blackbox_vault.db"
                )
                    .addMigrations(MIGRATION_5_6)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
