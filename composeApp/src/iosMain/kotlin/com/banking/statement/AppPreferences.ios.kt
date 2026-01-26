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

    companion object {
        private const val KEY_TUTORIAL_DISMISSED = "tutorial_dismissed"
    }
}
