package com.banking.statement.di

import com.banking.statement.AppPreferences
import com.banking.statement.categorization.CategoryOverrideManager
import com.banking.statement.categorization.CategorizationConfig
import com.banking.statement.categorization.KeywordDatabaseOptimized
import com.banking.statement.categorization.MerchantDatabase
import com.banking.statement.categorization.TransactionCategorizer
import com.banking.statement.categorization.recognitionModeFromName
import com.banking.statement.db.TransactionRepository
import org.koin.dsl.module

val commonModule = module {
    // Database & Repository (single instance, no categorizer yet)
    single { TransactionRepository(get()) }

    // Keyword database (optimized with pre-normalized keywords and caching)
    single { KeywordDatabaseOptimized() }

    // Merchant database (needs BankingDatabase from repository)
    single { MerchantDatabase(get<TransactionRepository>().database) }

    // Category override manager (needs BankingDatabase from repository)
    single {
        CategoryOverrideManager(get<TransactionRepository>().database).apply {
            loadCache()
        }
    }

    // Transaction categorizer. Recognition mode is read lazily from preferences
    // so changing the setting can affect future categorization without
    // recreating the singleton.
    single {
        TransactionCategorizer(
            merchantDatabase = get(),
            categoryOverrideManager = get(),
            configProvider = {
                val prefs = get<AppPreferences>()
                CategorizationConfig(recognitionModeFromName(prefs.getRecognitionModeName()))
            }
        )
    }
}
