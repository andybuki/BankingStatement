package com.bankwise.app.categorization

import com.bankwise.app.parser.ParsedTransaction

/**
 * Service for automatically categorizing transactions.
 * Priority order:
 * 1) User overrides (manual corrections)
 * 2) Keyword matching from TransactionCategory (rent, salary, utilities, etc.)
 * 3) Amount-based rules for income (positive amounts)
 * 4) Merchant database (only for unknowns - shops/restaurants not in keywords)
 */
class TransactionCategorizer(
    private val merchantDatabase: MerchantDatabase? = null,
    private val categoryOverrideManager: CategoryOverrideManager? = null
) {

    companion object {
        /** Threshold for categorizing income as SALARY vs REFUND */
        const val SALARY_THRESHOLD = 300.0
    }

    /**
     * Categorize a single transaction.
     * Priority: 1) User overrides, 2) Keywords, 3) Income amount rules, 4) Merchant DB
     */
    fun categorize(transaction: ParsedTransaction): TransactionCategory {
        // 1) Check user overrides first (highest priority)
        categoryOverrideManager?.let { manager ->
            val override = manager.findOverride(
                description = transaction.description,
                counterparty = transaction.counterpartyName
            )
            if (override != null) {
                return override
            }
        }

        // 2) Try keyword matching from TransactionCategory
        val keywordCategory = TransactionCategory.categorize(
            description = transaction.description,
            counterparty = transaction.counterpartyName
        )

        // If keywords found a category, use it
        if (keywordCategory != TransactionCategory.OTHER) {
            return keywordCategory
        }

        // 3) For positive amounts (income), apply amount-based rules
        if (transaction.amount > 0) {
            return if (transaction.amount > SALARY_THRESHOLD) {
                TransactionCategory.SALARY
            } else {
                TransactionCategory.REFUND
            }
        }

        // 4) For expenses, try merchant database
        if (transaction.amount < 0) {
            merchantDatabase?.let { db ->
                val merchantCategory = db.findCategory(
                    description = transaction.description,
                    counterparty = transaction.counterpartyName
                )
                if (merchantCategory != null) {
                    return merchantCategory
                }
            }
        }

        return TransactionCategory.OTHER
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
