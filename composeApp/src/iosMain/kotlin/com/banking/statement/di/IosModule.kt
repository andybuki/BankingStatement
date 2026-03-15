package com.banking.statement.di

import com.banking.statement.AppPreferences
import com.banking.statement.db.DatabaseDriverFactory
import com.banking.statement.ui.theme.ThemePreferences
import org.koin.dsl.module

val iosModule = module {
    // Platform-specific: database driver (no Context needed on iOS)
    single { DatabaseDriverFactory() }

    // Preferences (iOS-specific, no Context needed)
    single { ThemePreferences() }
    single { AppPreferences() }
}
