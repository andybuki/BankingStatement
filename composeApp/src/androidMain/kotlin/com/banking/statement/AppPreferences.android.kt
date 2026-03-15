package com.banking.statement

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Android implementation of app preferences using EncryptedSharedPreferences.
 * All preference data is encrypted at rest using Android Keystore-backed keys.
 */
actual class AppPreferences(private val context: Context) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    actual fun isTutorialDismissed(): Boolean {
        return prefs.getBoolean(KEY_TUTORIAL_DISMISSED, false)
    }

    actual fun setTutorialDismissed(dismissed: Boolean) {
        prefs.edit().putBoolean(KEY_TUTORIAL_DISMISSED, dismissed).apply()
    }

    fun isBiometricLockEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_LOCK, false)
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_LOCK, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "bankwise_secure_prefs"
        private const val KEY_TUTORIAL_DISMISSED = "tutorial_dismissed"
        private const val KEY_BIOMETRIC_LOCK = "biometric_lock_enabled"
    }
}
