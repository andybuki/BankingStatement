package com.banking.statement.di

import com.banking.statement.categorization.CategoryOverrideManager
import com.banking.statement.categorization.KeywordDatabaseOptimized
import com.banking.statement.categorization.MerchantDatabase
import com.banking.statement.categorization.TransactionCategorizer
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

    // Transaction categorizer
    single { TransactionCategorizer(get(), get()) }
}
