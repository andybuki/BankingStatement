package com.banking.statement

/**
 * App preferences storage - platform-specific implementation
 * Used to store user preferences like tutorial dismissed state
 */
expect class AppPreferences {
    fun isTutorialDismissed(): Boolean
    fun setTutorialDismissed(dismissed: Boolean)
    fun isOnboardingCompleted(): Boolean
    fun setOnboardingCompleted(completed: Boolean)
    fun areRemindersEnabled(): Boolean
    fun setRemindersEnabled(enabled: Boolean)
    fun getLastAppOpenTime(): Long
    fun setLastAppOpenTime(timeMillis: Long)
    fun getSuccessfulImportCount(): Int
    fun incrementSuccessfulImportCount()
    fun isRatingPromptDismissed(): Boolean
    fun setRatingPromptDismissed(dismissed: Boolean)
}
