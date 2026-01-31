package com.banking.statement

import android.content.Context
import android.content.SharedPreferences

/**
 * Android implementation of app preferences using SharedPreferences
 */
actual class AppPreferences(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    actual fun isTutorialDismissed(): Boolean {
        return prefs.getBoolean(KEY_TUTORIAL_DISMISSED, false)
    }

    actual fun setTutorialDismissed(dismissed: Boolean) {
        prefs.edit().putBoolean(KEY_TUTORIAL_DISMISSED, dismissed).apply()
    }

    companion object {
        private const val PREFS_NAME = "bankwise_app_prefs"
        private const val KEY_TUTORIAL_DISMISSED = "tutorial_dismissed"
    }
}
