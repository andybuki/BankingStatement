package com.banking.statement.categorization

import com.banking.statement.parser.ParsedTransaction

/**
 * Explains where an automatic categorization came from.
 */
enum class CategorizationSource {
    USER_OVERRIDE,
    SIGNAL_RULE,
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
 * 2) Strong explicit overrides for known non-restaurant merchants/patterns
 * 3) Strong transaction signals (salary, transfers, cash withdrawal)
 * 4) Merchant database / keywords against extracted effective merchant
 * 5) Bank-provided category hint, if available
 * 6) Keyword matching against normalized transaction text
 * 7) OTHER fallback
 */
class TransactionCategorizer(
    private val merchantDatabase: MerchantDatabase? = null,
    private val categoryOverrideManager: CategoryOverrideManager? = null
) {

    companion object {
        /** Threshold for categorizing income as SALARY vs REFUND fallback */
        const val SALARY_THRESHOLD = 300.0

        private val STRONG_TEXT_RULES: List<Pair<TransactionCategory, List<String>>> = listOf(
            TransactionCategory.SHOPPING to listOf(
                "bauhaus", "obi", "hornbach", "toom", "hobbyshop", "hobby shop",
                "dm drogerie", "dm-drogerie", "dm drogerie markt", "drogerie markt",
                "google youtube", "youtube", "google play", "google payment", "google one",
                "paypal payment", "paypal *", "paypalzahlung"
            ),
            TransactionCategory.TRANSFER to listOf(
                "money transfer", "paypal payment", "paypal transfer", "db verti eb",
                "ueberweisung", "überweisung", "umbuchung", "transfer"
            ),
            TransactionCategory.SUBSCRIPTIONS to listOf(
                "youtube premium", "google youtube", "google one", "netflix", "spotify"
            ),
            TransactionCategory.HEALTH to listOf(
                "apotheke", "krankenversicherung", "krankenkasse", "bkk", "aok", "tk ", "barmer"
            ),
            TransactionCategory.INSURANCE to listOf(
                "versicherung", "verti", "allianz", "debeka", "generali", "huk", "axa"
            )
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
        // 1) User overrides first (highest priority)
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

        val signals = TransactionSignalExtractor.extract(transaction)

        // 2) Strong explicit category rules for repeatedly observed false
        // positives. These run before generic merchant/keyword matching to
        // prevent weak restaurant matches from winning for merchants like
        // Bauhaus, DM Drogerie, YouTube/Google or generic PayPal transfers.
        strongTextRule(transaction, signals)?.let { return it }

        // 3) Strong signal rules. These are based on transaction type, not on
        // generic amount-only assumptions.
        strongSignalRule(transaction, signals)?.let { return it }

        // 4) Merchant/keyword lookup against the extracted effective merchant.
        // This is the critical improvement for PayPal, VISA, girocard and
        // bank-app rows: classify "Wolt", "Booking.com", "Spotify", "LIDL" or
        // "NETTO" instead of the whole noisy bank statement line.
        val merchantText = signals.effectiveMerchant
        if (!merchantText.isNullOrBlank()) {
            merchantDatabase?.let { db ->
                val merchantCategory = db.findCategory(
                    description = merchantText,
                    counterparty = null
                )
                if (merchantCategory != null) {
                    return CategorizationResult(
                        category = merchantCategory,
                        confidence = 0.90,
                        source = CategorizationSource.MERCHANT,
                        matchedValue = merchantText
                    )
                }
            }

            val merchantKeywordCategory = TransactionCategory.categorize(
                description = merchantText,
                counterparty = null
            )
            if (merchantKeywordCategory != TransactionCategory.OTHER) {
                return CategorizationResult(
                    category = merchantKeywordCategory,
                    confidence = 0.82,
                    source = CategorizationSource.KEYWORD,
                    matchedValue = merchantText
                )
            }
        }

        // 5) Some banks already provide coarse category hints, e.g. N26
        // "Mastercard • Bars & Restaurants". Use that after merchant-specific
        // lookup so a known merchant can still be more precise.
        signals.categoryHint?.let { hint ->
            return CategorizationResult(
                category = hint,
                confidence = 0.78,
                source = CategorizationSource.SIGNAL_RULE,
                matchedValue = signals.normalizedSearchText
            )
        }

        // 6) Keyword fallback for categories not covered by extracted merchant.
        val keywordCategory = TransactionCategory.categorize(
            description = signals.normalizedSearchText,
            counterparty = null
        )

        if (keywordCategory != TransactionCategory.OTHER) {
            return CategorizationResult(
                category = keywordCategory,
                confidence = 0.75,
                source = CategorizationSource.KEYWORD,
                matchedValue = signals.normalizedSearchText
            )
        }

        // 7) Final amount fallback for incoming money only. This is deliberately
        // last so salary/rent/keyword signals can win over a naive threshold.
        if (transaction.amount > 0) {
            return if (transaction.amount > SALARY_THRESHOLD) {
                CategorizationResult(
                    category = TransactionCategory.SALARY,
                    confidence = 0.55,
                    source = CategorizationSource.SIGNAL_RULE,
                    matchedValue = "> $SALARY_THRESHOLD fallback"
                )
            } else {
                CategorizationResult(
                    category = TransactionCategory.REFUND,
                    confidence = 0.65,
                    source = CategorizationSource.SIGNAL_RULE,
                    matchedValue = "positive amount fallback"
                )
            }
        }

        return CategorizationResult(
            category = TransactionCategory.OTHER,
            confidence = 0.0,
            source = CategorizationSource.UNKNOWN
        )
    }

    private fun strongTextRule(
        transaction: ParsedTransaction,
        signals: TransactionSignals
    ): CategorizationResult? {
        val text = listOfNotNull(
            signals.effectiveMerchant,
            signals.normalizedSearchText,
            transaction.description,
            transaction.counterpartyName,
            transaction.remittanceInfo,
            transaction.rawText
        ).joinToString(" ").lowercase()

        for ((category, patterns) in STRONG_TEXT_RULES) {
            val pattern = patterns.firstOrNull { text.contains(it) } ?: continue
            return CategorizationResult(
                category = category,
                confidence = 0.88,
                source = CategorizationSource.SIGNAL_RULE,
                matchedValue = pattern
            )
        }

        return null
    }

    private fun strongSignalRule(
        transaction: ParsedTransaction,
        signals: TransactionSignals
    ): CategorizationResult? {
        if (signals.isSalaryLike) {
            return CategorizationResult(
                category = TransactionCategory.SALARY,
                confidence = 0.95,
                source = CategorizationSource.SIGNAL_RULE,
                matchedValue = signals.normalizedSearchText
            )
        }

        if (signals.isInvestmentLike) {
            return CategorizationResult(
                category = TransactionCategory.INVESTMENT,
                confidence = 0.90,
                source = CategorizationSource.SIGNAL_RULE,
                matchedValue = signals.normalizedSearchText
            )
        }

        if (signals.isCashWithdrawal) {
            return CategorizationResult(
                category = TransactionCategory.TRANSFER,
                confidence = 0.85,
                source = CategorizationSource.SIGNAL_RULE,
                matchedValue = "cash withdrawal"
            )
        }

        if (signals.isCashDeposit) {
            return CategorizationResult(
                category = TransactionCategory.TRANSFER,
                confidence = 0.85,
                source = CategorizationSource.SIGNAL_RULE,
                matchedValue = "cash deposit"
            )
        }

        if (signals.isBankFeeLike) {
            return CategorizationResult(
                category = TransactionCategory.OTHER,
                confidence = 0.70,
                source = CategorizationSource.SIGNAL_RULE,
                matchedValue = signals.normalizedSearchText
            )
        }

        if (transaction.amount < 0 && signals.isTransferLike) {
            return CategorizationResult(
                category = TransactionCategory.TRANSFER,
                confidence = 0.85,
                source = CategorizationSource.SIGNAL_RULE,
                matchedValue = signals.normalizedSearchText
            )
        }

        if (transaction.amount < 0 && signals.isRentLike) {
            return CategorizationResult(
                category = TransactionCategory.RENT,
                confidence = 0.88,
                source = CategorizationSource.SIGNAL_RULE,
                matchedValue = signals.normalizedSearchText
            )
        }

        if (transaction.amount > 0 && signals.isRentLike) {
            return CategorizationResult(
                category = TransactionCategory.REFUND,
                confidence = 0.80,
                source = CategorizationSource.SIGNAL_RULE,
                matchedValue = signals.normalizedSearchText
            )
        }

        return null
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
