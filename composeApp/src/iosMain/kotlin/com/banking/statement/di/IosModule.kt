package com.banking.statement.di

import com.banking.statement.AppPreferences
import com.banking.statement.categorization.TransactionMlClassifier
import com.banking.statement.categorization.ml.MlClassifierFactory
import com.banking.statement.db.DatabaseDriverFactory
import com.banking.statement.ui.theme.ThemePreferences
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.dsl.module
import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile

@OptIn(ExperimentalForeignApi::class)
val iosModule = module {
    // Platform-specific: database driver (no Context needed on iOS)
    single { DatabaseDriverFactory() }

    // Preferences (iOS-specific, no Context needed)
    single { ThemePreferences() }
    single { AppPreferences() }

    // ML classifier: read the optional model from the app bundle.
    // Place category_model.json in the iOS Xcode project's resources to enable.
    single<TransactionMlClassifier> {
        val path = NSBundle.mainBundle.pathForResource("category_model", ofType = "json")
        val payload = path?.let {
            NSString.stringWithContentsOfFile(it, NSUTF8StringEncoding, null) as String?
        }
        MlClassifierFactory.fromJsonOrNoOp(payload)
    }
}
