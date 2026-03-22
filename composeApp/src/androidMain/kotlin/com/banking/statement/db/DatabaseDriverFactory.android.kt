package com.banking.statement.db

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.SecureRandom

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
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
        private const val KEY_DB_PASSPHRASE = "db_encryption_passphrase"
    }
}
