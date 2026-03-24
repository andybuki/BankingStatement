package com.banking.statement.categorization

/**
 * Interface for keyword-based transaction categorization.
 * Allows TransactionCategory to work with any keyword database implementation.
 */
interface KeywordLookup {
    fun findCategory(description: String, counterparty: String? = null): TransactionCategory?
    fun isLoaded(): Boolean
}
