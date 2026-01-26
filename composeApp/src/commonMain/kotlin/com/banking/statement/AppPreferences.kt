package com.banking.statement

/**
 * App preferences storage - platform-specific implementation
 * Used to store user preferences like tutorial dismissed state
 */
expect class AppPreferences {
    fun isTutorialDismissed(): Boolean
    fun setTutorialDismissed(dismissed: Boolean)
}
