package com.banking.statement.categorization

import com.banking.statement.parser.ParsedTransaction

/**
 * Explains where an automatic categorization came from.
 */
enum class CategorizationSource {
    USER_OVERRIDE,
    AMOUNT_RULE,
    MERCHANT,
    KEYWORD,
    UNKNOWN
}

/**
 * Categorization result with confidence and optional diagnostics.
 */
data class CategorizationResult(
    val category: TransactionCategory,
    val confidence: Double,
    val source: CategorizationSource,
    val matchedValue: String? = null
)

/**
 * Service for automatically categorizing transactions.
 *
 * Priority order:
 * 1) User overrides (manual corrections)
 * 2) Amount-based rules for positive amounts (salary/refund)
 * 3) Merchant database for card-like payments or explicit counterparties
 * 4) Keyword matching from TransactionCategory
 * 5) OTHER fallback
 */
class TransactionCategorizer(
    private val merchantDatabase: MerchantDatabase? = null,
    private val categoryOverrideManager: CategoryOverrideManager? = null
) {

    companion object {
        /** Threshold for categorizing income as SALARY vs REFUND */
        const val SALARY_THRESHOLD = 300.0

        private val CARD_LIKE_MARKERS = listOf(
            "visa",
            "mastercard",
            "maestro",
            "girocard",
            "kartenzahlung",
            "kaufumsatz",
            "debitk",
            "credit card",
            "debit card"
        )
    }

    /**
     * Backward-compatible API for callers that only need the category.
     */
    fun categorize(transaction: ParsedTransaction): TransactionCategory {
        return categorizeWithDetails(transaction).category
    }

    /**
     * Categorize a single transaction with confidence and source metadata.
     */
    fun categorizeWithDetails(transaction: ParsedTransaction): CategorizationResult {
        // 1) Check user overrides first (highest priority)
        categoryOverrideManager?.let { manager ->
            val override = manager.findOverride(
                description = transaction.description,
                counterparty = transaction.counterpartyName
            )
            if (override != null) {
                return CategorizationResult(
                    category = override,
                    confidence = 1.0,
                    source = CategorizationSource.USER_OVERRIDE
                )
            }
        }

        // 2) For positive amounts (income), apply amount-based rules before
        // merchant/keyword matching. This prevents words like "miete" in an
        // incoming transfer from overriding refund/salary logic.
        if (transaction.amount > 0) {
            return if (transaction.amount > SALARY_THRESHOLD) {
                CategorizationResult(
                    category = TransactionCategory.SALARY,
                    confidence = 0.90,
                    source = CategorizationSource.AMOUNT_RULE,
                    matchedValue = "> $SALARY_THRESHOLD"
                )
            } else {
                CategorizationResult(
                    category = TransactionCategory.REFUND,
                    confidence = 0.80,
                    source = CategorizationSource.AMOUNT_RULE,
                    matchedValue = "<= $SALARY_THRESHOLD"
                )
            }
        }

        // 3) For expenses, prefer merchant database over generic keywords —
        // but only when the transaction looks like a card payment or the bank
        // parsed an explicit counterparty. Long SEPA/Lastschrift descriptions
        // often contain unrelated person names such as "Müller"; matching the
        // whole raw text against merchants would create false positives.
        if (transaction.amount < 0 && shouldUseMerchantDatabase(transaction)) {
            merchantDatabase?.let { db ->
                val merchantCategory = db.findCategory(
                    description = transaction.description,
                    counterparty = transaction.counterpartyName
                )
                if (merchantCategory != null) {
                    return CategorizationResult(
                        category = merchantCategory,
                        confidence = 0.90,
                        source = CategorizationSource.MERCHANT
                    )
                }
            }
        }

        // 4) Keyword fallback for categories not covered by merchant data.
        val keywordCategory = TransactionCategory.categorize(
            description = transaction.description,
            counterparty = transaction.counterpartyName
        )

        if (keywordCategory != TransactionCategory.OTHER) {
            return CategorizationResult(
                category = keywordCategory,
                confidence = 0.75,
                source = CategorizationSource.KEYWORD
            )
        }

        return CategorizationResult(
            category = TransactionCategory.OTHER,
            confidence = 0.0,
            source = CategorizationSource.UNKNOWN
        )
    }

    /**
     * Merchant database is strongest for card payments and explicit bank-
     * parsed counterparties. For generic SEPA/Lastschrift text with no
     * counterparty, prefer keywords/amount rules to avoid matching incidental
     * names inside long remittance/legal descriptions.
     */
    private fun shouldUseMerchantDatabase(transaction: ParsedTransaction): Boolean {
        if (!transaction.counterpartyName.isNullOrBlank()) return true

        val text = transaction.description.lowercase()
        return CARD_LIKE_MARKERS.any { marker -> text.contains(marker) }
    }

    /**
     * Categorize multiple transactions
     */
    fun categorizeAll(transactions: List<ParsedTransaction>): List<Pair<ParsedTransaction, TransactionCategory>> {
        return transactions.map { it to categorize(it) }
    }

    /**
     * Categorize multiple transactions with source/confidence metadata.
     */
    fun categorizeAllWithDetails(transactions: List<ParsedTransaction>): List<Pair<ParsedTransaction, CategorizationResult>> {
        return transactions.map { it to categorizeWithDetails(it) }
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
