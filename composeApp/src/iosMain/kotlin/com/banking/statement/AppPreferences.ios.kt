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

    actual fun getSuccessfulImportCount(): Int {
        return defaults.integerForKey(KEY_SUCCESSFUL_IMPORT_COUNT).toInt()
    }

    actual fun incrementSuccessfulImportCount() {
        val current = getSuccessfulImportCount()
        defaults.setInteger((current + 1).toLong(), KEY_SUCCESSFUL_IMPORT_COUNT)
    }

    actual fun isRatingPromptDismissed(): Boolean {
        return defaults.boolForKey(KEY_RATING_PROMPT_DISMISSED)
    }

    actual fun setRatingPromptDismissed(dismissed: Boolean) {
        defaults.setBool(dismissed, KEY_RATING_PROMPT_DISMISSED)
    }

    actual fun getRecognitionModeName(): String {
        return defaults.stringForKey(KEY_RECOGNITION_MODE) ?: DEFAULT_RECOGNITION_MODE
    }

    actual fun setRecognitionModeName(modeName: String) {
        defaults.setObject(modeName, KEY_RECOGNITION_MODE)
    }

    actual fun isPdfAccessEnabled(): Boolean {
        return if (defaults.objectForKey(KEY_PDF_ACCESS_ENABLED) != null) {
            defaults.boolForKey(KEY_PDF_ACCESS_ENABLED)
        } else true
    }

    actual fun setPdfAccessEnabled(enabled: Boolean) {
        defaults.setBool(enabled, KEY_PDF_ACCESS_ENABLED)
    }

    companion object {
        private const val KEY_TUTORIAL_DISMISSED = "tutorial_dismissed"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_REMINDERS_ENABLED = "reminders_enabled"
        private const val KEY_LAST_APP_OPEN = "last_app_open_time"
        private const val KEY_SUCCESSFUL_IMPORT_COUNT = "successful_import_count"
        private const val KEY_RATING_PROMPT_DISMISSED = "rating_prompt_dismissed"
        private const val KEY_RECOGNITION_MODE = "recognition_mode"
        private const val DEFAULT_RECOGNITION_MODE = "SAFE"
        private const val KEY_PDF_ACCESS_ENABLED = "pdf_access_enabled"
    }
}
