package com.banking.statement.categorization

import com.banking.statement.parser.ParsedTransaction

/**
 * Optional ML classifier used only as a fallback after deterministic rules.
 *
 * Implementations must run fully on-device and must not send transactions to
 * a network service.
 */
interface TransactionMlClassifier {
    fun classify(transaction: ParsedTransaction): MlCategoryPrediction?

    /**
     * Free-form version string identifying the loaded model, or null if no
     * on-device model is bundled (NoOp). Surfaced in the settings UI so the
     * user can confirm whether predictions are actually wired up.
     */
    val modelVersion: String? get() = null
}

data class MlCategoryPrediction(
    val category: TransactionCategory,
    val confidence: Double,
    val modelVersion: String? = null
)

/**
 * Default no-op classifier for builds where no on-device ML model is bundled.
 */
object NoOpTransactionMlClassifier : TransactionMlClassifier {
    override fun classify(transaction: ParsedTransaction): MlCategoryPrediction? = null
}
