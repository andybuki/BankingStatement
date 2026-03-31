package com.banking.statement

import platform.Foundation.NSUserDefaults

/**
 * iOS implementation of app preferences using NSUserDefaults
 */
actual class AppPreferences {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun isTutorialDismissed(): Boolean {
        return defaults.boolForKey(KEY_TUTORIAL_DISMISSED)
    }

    actual fun setTutorialDismissed(dismissed: Boolean) {
        defaults.setBool(dismissed, KEY_TUTORIAL_DISMISSED)
    }

    actual fun isOnboardingCompleted(): Boolean {
        return defaults.boolForKey(KEY_ONBOARDING_COMPLETED)
    }

    actual fun setOnboardingCompleted(completed: Boolean) {
        defaults.setBool(completed, KEY_ONBOARDING_COMPLETED)
    }

    actual fun areRemindersEnabled(): Boolean {
        // Default to true if key hasn't been set yet
        return if (defaults.objectForKey(KEY_REMINDERS_ENABLED) != null) {
            defaults.boolForKey(KEY_REMINDERS_ENABLED)
        } else true
    }

    actual fun setRemindersEnabled(enabled: Boolean) {
        defaults.setBool(enabled, KEY_REMINDERS_ENABLED)
    }

    actual fun getLastAppOpenTime(): Long {
        return defaults.integerForKey(KEY_LAST_APP_OPEN)
    }

    actual fun setLastAppOpenTime(timeMillis: Long) {
        defaults.setInteger(timeMillis, KEY_LAST_APP_OPEN)
    }

    companion object {
        private const val KEY_TUTORIAL_DISMISSED = "tutorial_dismissed"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_REMINDERS_ENABLED = "reminders_enabled"
        private const val KEY_LAST_APP_OPEN = "last_app_open_time"
    }
}
