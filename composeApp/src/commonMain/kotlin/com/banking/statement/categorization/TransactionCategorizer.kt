package com.banking.statement.categorization

import com.banking.statement.parser.ParsedTransaction

/**
 * Service for automatically categorizing transactions.
 * Uses merchant database lookup first, then falls back to keyword matching.
 */
class TransactionCategorizer(
    private val merchantDatabase: MerchantDatabase? = null
) {

    /**
     * Categorize a single transaction.
     * Priority: 1) Merchant database lookup, 2) Keyword matching
     */
    fun categorize(transaction: ParsedTransaction): TransactionCategory {
        // First try merchant database if available
        merchantDatabase?.let { db ->
            val merchantCategory = db.findCategory(
                description = transaction.description,
                counterparty = transaction.counterpartyName
            )
            if (merchantCategory != null) {
                return merchantCategory
            }
        }

        // Fall back to keyword matching
        return TransactionCategory.categorize(
            description = transaction.description,
            counterparty = transaction.counterpartyName
        )
    }

    /**
     * Categorize multiple transactions
     */
    fun categorizeAll(transactions: List<ParsedTransaction>): List<Pair<ParsedTransaction, TransactionCategory>> {
        return transactions.map { it to categorize(it) }
    }

    /**
     * Get category statistics for a list of transactions
     */
    fun getCategoryStats(transactions: List<ParsedTransaction>): Map<TransactionCategory, CategoryStats> {
        val categorized = categorizeAll(transactions)

        return categorized.groupBy { it.second }
            .mapValues { (_, items) ->
                val transactionAmounts = items.map { it.first.amount }
                CategoryStats(
                    count = items.size,
                    totalAmount = transactionAmounts.sum(),
                    expenses = transactionAmounts.filter { it < 0 }.sum(),
                    income = transactionAmounts.filter { it > 0 }.sum()
                )
            }
    }
}

/**
 * Statistics for a category
 */
data class CategoryStats(
    val count: Int,
    val totalAmount: Double,
    val expenses: Double,
    val income: Double
) {
    val averageAmount: Double get() = if (count > 0) totalAmount / count else 0.0
}
