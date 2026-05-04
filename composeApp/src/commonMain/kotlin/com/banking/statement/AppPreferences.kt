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

    /**
     * Recognition mode used by the categorizer.
     * Stored as enum name: SAFE, BALANCED, EXPERIMENTAL.
     */
    fun getRecognitionModeName(): String
    fun setRecognitionModeName(modeName: String)

    /**
     * Whether the app is allowed to persist imported PDFs on disk so they
     * can be re-opened from the transaction detail ("receipt view"). When
     * false, new imports won't retain the PDF and the "View source PDF"
     * action is hidden for future imports.
     */
    fun isPdfAccessEnabled(): Boolean
    fun setPdfAccessEnabled(enabled: Boolean)
}
