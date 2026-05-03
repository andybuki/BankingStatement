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
 *
 * If [category] is OTHER but [suggestedCategory] is set, the system found a
 * plausible category but did not trust it enough for automatic assignment.
 */
data class CategorizationResult(
    val category: TransactionCategory,
    val confidence: Double,
    val source: CategorizationSource,
    val matchedValue: String? = null,
    val suggestedCategory: TransactionCategory? = null,
    val suggestedConfidence: Double? = null
)

/**
 * Service for automatically categorizing transactions.
 *
 * Priority order:
 * 1) User overrides (manual corrections)
 * 2) Strong explicit overrides for known non-restaurant merchants/patterns
 * 3) Strong transaction signals (salary, refunds, transfers, cash withdrawal)
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

        /**
         * Minimum confidence required for automatic categorization.
         * Below this value, categorizeWithDetails() returns OTHER with a
         * suggestedCategory. This trades coverage for fewer wrong categories.
         */
        const val AUTO_CATEGORIZATION_THRESHOLD = 0.80

        private val STRONG_TEXT_RULES: List<Pair<TransactionCategory, List<String>>> = listOf(
            TransactionCategory.SUBSCRIPTIONS to listOf(
                "apple services", "apple service", "youtube premium", "google youtube",
                "google one", "netflix", "spotify", "drillisch", "winsim", "lagoa yoga"
            ),
            TransactionCategory.TRANSPORT to listOf(
                "bvg", "berliner verkehrsbetriebe", "s-bahn", "s bahn", "db vertrieb",
                "logpay financial services", "flix se", "gmsa guaguas"
            ),
            TransactionCategory.RENT to listOf(
                "commerzbank ag leistungen", "tilgung", "zinsen", "telekom deutschland",
                "enstroga", "strom", "gehag", "miete01"
            ),
            TransactionCategory.SUPERMARKET to listOf(
                "hiperdino", "ullrich", "c+c mercado", "flaschenpost", "bamut markt",
                "frischeparadies", "reformhaus", "go asia", "alnatura", "bio company"
            ),
            TransactionCategory.SHOPPING to listOf(
                "ikea", "bauhaus", "obi", "hornbach", "toom", "hobbyshop", "hobby shop",
                "dm drogerie", "dm-drogerie", "dm drogerie markt", "drogerie markt",
                "rossmann", "butlers", "amazon payments", "amzn mktp", "ebay", "alipay",
                "otrium", "ic group", "tantal", "breuninger", "schuhhandelsgesellschaft",
                "google play", "google payment", "el corte ingles", "wdfg", "cos berlin", "muji"
            ),
            TransactionCategory.ENTERTAINMENT to listOf(
                "fienta", "ticket messe"
            ),
            TransactionCategory.HEALTH to listOf(
                "apotheke", "krankenkasse", "bkk", "aok", "tk ", "barmer"
            ),
            TransactionCategory.INSURANCE to listOf(
                "krankenversicherung", "versicherung", "verti", "allianz", "debeka", "generali", "huk", "axa"
            ),
            TransactionCategory.RESTAURANT to listOf(
                "takeaway", "drei koche", "sticknsushi", "restaurant kuchi", "burger king",
                "sironi backerei", "19 grams", "la tapatica", "granier", "allende", "bar perdomo"
            ),
            TransactionCategory.TRANSFER to listOf(
                "money transfer", "paypal payment", "paypal transfer", "db verti eb",
                "extra-konto", "extra konto", "ueberweisung", "überweisung", "umbuchung", "transfer"
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
        // positives. These run before generic merchant/keyword matching.
        strongTextRule(transaction, signals)?.let { return finalizeAutoCategory(it) }

        // 3) Strong signal rules. These are based on transaction type, not on
        // generic amount-only assumptions.
        strongSignalRule(transaction, signals)?.let { return finalizeAutoCategory(it) }

        // 4) Merchant/keyword lookup against the extracted effective merchant.
        val merchantText = signals.effectiveMerchant
        if (!merchantText.isNullOrBlank()) {
            merchantDatabase?.let { db ->
                val merchantCategory = db.findCategory(
                    description = merchantText,
                    counterparty = null
                )
                if (merchantCategory != null) {
                    return finalizeAutoCategory(
                        CategorizationResult(
                            category = merchantCategory,
                            confidence = 0.90,
                            source = CategorizationSource.MERCHANT,
                            matchedValue = merchantText
                        )
                    )
                }
            }

            val merchantKeywordCategory = TransactionCategory.categorize(
                description = merchantText,
                counterparty = null
            )
            if (merchantKeywordCategory != TransactionCategory.OTHER) {
                return finalizeAutoCategory(
                    CategorizationResult(
                        category = merchantKeywordCategory,
                        confidence = 0.82,
                        source = CategorizationSource.KEYWORD,
                        matchedValue = merchantText
                    )
                )
            }
        }

        signals.categoryHint?.let { hint ->
            return finalizeAutoCategory(
                CategorizationResult(
                    category = hint,
                    confidence = 0.78,
                    source = CategorizationSource.SIGNAL_RULE,
                    matchedValue = signals.normalizedSearchText
                )
            )
        }

        val keywordCategory = TransactionCategory.categorize(
            description = signals.normalizedSearchText,
            counterparty = null
        )

        if (keywordCategory != TransactionCategory.OTHER) {
            return finalizeAutoCategory(
                CategorizationResult(
                    category = keywordCategory,
                    confidence = 0.75,
                    source = CategorizationSource.KEYWORD,
                    matchedValue = signals.normalizedSearchText
                )
            )
        }

        if (transaction.amount > 0) {
            return if (transaction.amount > SALARY_THRESHOLD) {
                finalizeAutoCategory(
                    CategorizationResult(
                        category = TransactionCategory.SALARY,
                        confidence = 0.55,
                        source = CategorizationSource.SIGNAL_RULE,
                        matchedValue = "> $SALARY_THRESHOLD fallback"
                    )
                )
            } else {
                finalizeAutoCategory(
                    CategorizationResult(
                        category = TransactionCategory.REFUND,
                        confidence = 0.65,
                        source = CategorizationSource.SIGNAL_RULE,
                        matchedValue = "positive amount fallback"
                    )
                )
            }
        }

        return CategorizationResult(
            category = TransactionCategory.OTHER,
            confidence = 0.0,
            source = CategorizationSource.UNKNOWN
        )
    }

    private fun finalizeAutoCategory(result: CategorizationResult): CategorizationResult {
        if (result.source == CategorizationSource.USER_OVERRIDE) return result
        if (result.category == TransactionCategory.OTHER) return result
        if (result.confidence >= AUTO_CATEGORIZATION_THRESHOLD) return result

        return CategorizationResult(
            category = TransactionCategory.OTHER,
            confidence = 0.0,
            source = CategorizationSource.UNKNOWN,
            matchedValue = result.matchedValue,
            suggestedCategory = result.category,
            suggestedConfidence = result.confidence
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

        if (signals.isRefundLike) {
            return CategorizationResult(
                category = TransactionCategory.REFUND,
                confidence = 0.88,
                source = CategorizationSource.SIGNAL_RULE,
                matchedValue = signals.effectiveMerchant ?: signals.normalizedSearchText
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

    fun categorizeAll(transactions: List<ParsedTransaction>): List<Pair<ParsedTransaction, TransactionCategory>> {
        return transactions.map { it to categorize(it) }
    }

    fun categorizeAllWithDetails(transactions: List<ParsedTransaction>): List<Pair<ParsedTransaction, CategorizationResult>> {
        return transactions.map { it to categorizeWithDetails(it) }
    }

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

data class CategoryStats(
    val count: Int,
    val totalAmount: Double,
    val expenses: Double,
    val income: Double
) {
    val averageAmount: Double get() = if (count > 0) totalAmount / count else 0.0
}
