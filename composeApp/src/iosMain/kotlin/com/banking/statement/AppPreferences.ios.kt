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

    actual fun isWeeklyDigestEnabled(): Boolean {
        return if (defaults.objectForKey(KEY_WEEKLY_DIGEST) != null) defaults.boolForKey(KEY_WEEKLY_DIGEST) else true
    }

    actual fun setWeeklyDigestEnabled(enabled: Boolean) {
        defaults.setBool(enabled, KEY_WEEKLY_DIGEST)
    }

    actual fun isSmartInsightsEnabled(): Boolean {
        return if (defaults.objectForKey(KEY_SMART_INSIGHTS) != null) defaults.boolForKey(KEY_SMART_INSIGHTS) else true
    }

    actual fun setSmartInsightsEnabled(enabled: Boolean) {
        defaults.setBool(enabled, KEY_SMART_INSIGHTS)
    }

    actual fun isMonthlyHealthEnabled(): Boolean {
        return if (defaults.objectForKey(KEY_MONTHLY_HEALTH) != null) defaults.boolForKey(KEY_MONTHLY_HEALTH) else true
    }

    actual fun setMonthlyHealthEnabled(enabled: Boolean) {
        defaults.setBool(enabled, KEY_MONTHLY_HEALTH)
    }

    actual fun isYearReviewEnabled(): Boolean {
        return if (defaults.objectForKey(KEY_YEAR_REVIEW) != null) defaults.boolForKey(KEY_YEAR_REVIEW) else true
    }

    actual fun setYearReviewEnabled(enabled: Boolean) {
        defaults.setBool(enabled, KEY_YEAR_REVIEW)
    }

    companion object {
        private const val KEY_TUTORIAL_DISMISSED = "tutorial_dismissed"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_WEEKLY_DIGEST = "notification_weekly_digest"
        private const val KEY_SMART_INSIGHTS = "notification_smart_insights"
        private const val KEY_MONTHLY_HEALTH = "notification_monthly_health"
        private const val KEY_YEAR_REVIEW = "notification_year_review"
    }
}
