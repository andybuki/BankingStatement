package com.banking.statement.db

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.security.SecureRandom

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        migrateUnencryptedDatabaseIfNeeded()

        val passphrase = getOrCreatePassphrase()
        val factory = SupportOpenHelperFactory(passphrase.toByteArray())

        return AndroidSqliteDriver(
            schema = BankingDatabase.Schema,
            context = context,
            name = "banking.db",
            factory = factory,
            callback = AndroidSqliteDriver.Callback(
                schema = BankingDatabase.Schema,
                *DatabaseMigration.getMigrationCallbacks()
            )
        )
    }

    /**
     * Migrates an existing unencrypted database to encrypted format.
     * This handles the upgrade path from app versions before SQLCipher was added.
     *
     * Detection: if the database file exists but no passphrase is stored yet,
     * the database was created by an older unencrypted version.
     */
    private fun migrateUnencryptedDatabaseIfNeeded() {
        val dbFile = context.getDatabasePath("banking.db")
        if (!dbFile.exists()) return

        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val prefs = EncryptedSharedPreferences.create(
            context,
            "banking_db_key_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        // If passphrase already exists, the DB is already encrypted — nothing to do
        if (prefs.getString(KEY_DB_PASSPHRASE, null) != null) return

        Log.i(TAG, "Detected unencrypted database from previous app version. Migrating to encrypted format...")

        val newPassphrase = generatePassphrase()
        val encryptedDbFile = File(dbFile.parent, "banking_encrypted.db")

        try {
            // Open the existing unencrypted database directly via SQLCipher with an empty key
            val unencryptedDb = net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                "",  // empty passphrase = unencrypted
                null,  // cursorFactory
                net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READWRITE,
                null,  // databaseHook
                null   // errorHandler
            )

            // Attach a new encrypted database and export all data into it
            unencryptedDb.execSQL(
                "ATTACH DATABASE '${encryptedDbFile.absolutePath}' AS encrypted KEY '$newPassphrase'"
            )
            unencryptedDb.execSQL("SELECT sqlcipher_export('encrypted')")
            unencryptedDb.execSQL("DETACH DATABASE encrypted")
            unencryptedDb.close()

            // Replace old unencrypted DB with the new encrypted one
            dbFile.delete()
            encryptedDbFile.renameTo(dbFile)

            // Store the passphrase so future launches use it
            prefs.edit().putString(KEY_DB_PASSPHRASE, newPassphrase).apply()

            Log.i(TAG, "Database migration to encrypted format completed successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to migrate unencrypted database to encrypted format", e)
            // Clean up partial encrypted file if it exists
            encryptedDbFile.delete()
            // Don't store the passphrase — let the next attempt retry the migration
        }
    }

    /**
     * Retrieves or generates the database encryption passphrase.
     * The passphrase is stored in EncryptedSharedPreferences backed by Android Keystore.
     */
    private fun getOrCreatePassphrase(): String {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val prefs = EncryptedSharedPreferences.create(
            context,
            "banking_db_key_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        val existing = prefs.getString(KEY_DB_PASSPHRASE, null)
        if (existing != null) return existing

        val passphrase = generatePassphrase()
        prefs.edit().putString(KEY_DB_PASSPHRASE, passphrase).apply()
        return passphrase
    }

    private fun generatePassphrase(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "DatabaseDriverFactory"
        private const val KEY_DB_PASSPHRASE = "db_encryption_passphrase"
    }
}
