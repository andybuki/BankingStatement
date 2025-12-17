package com.banking.statement.categorization

import com.banking.statement.parser.ParsedTransaction

/**
 * Service for automatically categorizing transactions
 */
class TransactionCategorizer {

    /**
     * Categorize a single transaction
     */
    fun categorize(transaction: ParsedTransaction): TransactionCategory {
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
