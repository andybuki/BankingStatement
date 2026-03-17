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

    actual fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    actual fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }

    fun isBiometricLockEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_LOCK, false)
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_LOCK, enabled).apply()
    }

    actual fun isWeeklyDigestEnabled(): Boolean {
        return prefs.getBoolean(KEY_WEEKLY_DIGEST, true)
    }

    actual fun setWeeklyDigestEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WEEKLY_DIGEST, enabled).apply()
    }

    actual fun isSmartInsightsEnabled(): Boolean {
        return prefs.getBoolean(KEY_SMART_INSIGHTS, true)
    }

    actual fun setSmartInsightsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SMART_INSIGHTS, enabled).apply()
    }

    actual fun isMonthlyHealthEnabled(): Boolean {
        return prefs.getBoolean(KEY_MONTHLY_HEALTH, true)
    }

    actual fun setMonthlyHealthEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MONTHLY_HEALTH, enabled).apply()
    }

    actual fun isYearReviewEnabled(): Boolean {
        return prefs.getBoolean(KEY_YEAR_REVIEW, true)
    }

    actual fun setYearReviewEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_YEAR_REVIEW, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "bankwise_secure_prefs"
        private const val KEY_TUTORIAL_DISMISSED = "tutorial_dismissed"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_BIOMETRIC_LOCK = "biometric_lock_enabled"
        private const val KEY_WEEKLY_DIGEST = "notification_weekly_digest"
        private const val KEY_SMART_INSIGHTS = "notification_smart_insights"
        private const val KEY_MONTHLY_HEALTH = "notification_monthly_health"
        private const val KEY_YEAR_REVIEW = "notification_year_review"
    }
}
