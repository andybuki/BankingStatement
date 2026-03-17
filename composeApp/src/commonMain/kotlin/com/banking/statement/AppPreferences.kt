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

    // Notification preferences
    fun isWeeklyDigestEnabled(): Boolean
    fun setWeeklyDigestEnabled(enabled: Boolean)
    fun isSmartInsightsEnabled(): Boolean
    fun setSmartInsightsEnabled(enabled: Boolean)
    fun isMonthlyHealthEnabled(): Boolean
    fun setMonthlyHealthEnabled(enabled: Boolean)
    fun isYearReviewEnabled(): Boolean
    fun setYearReviewEnabled(enabled: Boolean)
}
