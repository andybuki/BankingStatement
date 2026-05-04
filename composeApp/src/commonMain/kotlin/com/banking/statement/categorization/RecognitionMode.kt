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
    val mlEnabled: Boolean,
    val mlConfidenceThreshold: Double
) {
    SAFE(
        mlEnabled = false,
        mlConfidenceThreshold = 1.0
    ),
    BALANCED(
        mlEnabled = true,
        mlConfidenceThreshold = 0.30
    ),
    EXPERIMENTAL(
        mlEnabled = true,
        mlConfidenceThreshold = 0.20
    )
}

/**
 * Categorization runtime configuration.
 */
data class CategorizationConfig(
    val recognitionMode: RecognitionMode = RecognitionMode.SAFE
) {
    val mlEnabled: Boolean get() = recognitionMode.mlEnabled
    val mlConfidenceThreshold: Double get() = recognitionMode.mlConfidenceThreshold
}
