package com.banking.statement.di

import com.banking.statement.AppPreferences
import com.banking.statement.BiometricLockManager
import com.banking.statement.FileImportProcessor
import com.banking.statement.MainViewModel
import com.banking.statement.categorization.TransactionMlClassifier
import com.banking.statement.categorization.ml.MlClassifierFactory
import com.banking.statement.db.DatabaseDriverFactory
import com.banking.statement.export.FileExporter
import com.banking.statement.export.PdfGenerator
import com.banking.statement.ui.theme.ThemePreferences
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val androidModule = module {
    // Platform-specific: database driver needs Android Context
    single { DatabaseDriverFactory(androidContext()) }

    // File processing (Android-specific, needs Context)
    single { FileImportProcessor(androidContext()) }

    // Export services (Android-specific, need Context)
    single { FileExporter(androidContext()) }
    single { PdfGenerator(androidContext()) }

    // Preferences (Android-specific, need Context)
    single { ThemePreferences(androidContext()) }
    single { AppPreferences(androidContext()) }

    // Security (Android-specific, needs Context)
    single { BiometricLockManager(androidContext()) }

    // ML classifier: load the optional model from assets/files/category_model.json.
    // Missing or corrupt file falls back to NoOp without throwing.
    single<TransactionMlClassifier> {
        val payload = try {
            androidContext().assets.open("files/category_model.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
        } catch (_: Throwable) {
            null
        }
        MlClassifierFactory.fromJsonOrNoOp(payload)
    }

    // ViewModel with all dependencies injected
    viewModel {
        MainViewModel(
            context = androidContext(),
            repository = get(),
            keywordDatabase = get(),
            merchantDatabase = get(),
            categoryOverrideManager = get(),
            transactionCategorizer = get(),
            fileImportProcessor = get(),
            fileExporter = get(),
            pdfGenerator = get(),
            themePreferences = get(),
            appPreferences = get(),
            biometricLockManager = get()
        )
    }
}
