package com.banking.statement.categorization

/**
 * Controls how aggressively automatic transaction categorization should work.
 *
 * SAFE:
 * - Rules and user overrides only
 * - ML is disabled
 *
 * BALANCED:
 * - Rules first
 * - ML fallback only if confidence is high enough for the current model
 *
 * EXPERIMENTAL:
 * - Rules first
 * - ML fallback with lower threshold and higher coverage
 */
enum class RecognitionMode(
    val mlConfidenceThreshold: Double?
) {
    SAFE(mlConfidenceThreshold = null),
    BALANCED(mlConfidenceThreshold = 0.30),
    EXPERIMENTAL(mlConfidenceThreshold = 0.20);

    val mlEnabled: Boolean get() = mlConfidenceThreshold != null
}

/**
 * Categorization runtime configuration.
 */
data class CategorizationConfig(
    val recognitionMode: RecognitionMode = RecognitionMode.SAFE
) {
    val mlEnabled: Boolean get() = recognitionMode.mlEnabled
    val mlConfidenceThreshold: Double? get() = recognitionMode.mlConfidenceThreshold
}
