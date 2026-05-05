package com.banking.statement.categorization

import com.banking.statement.parser.ParsedTransaction
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests covering RecognitionMode behavior and the ML fallback path of
 * TransactionCategorizer. The ML classifier is replaced with a fake so we
 * can assert against the fallback contract without depending on a model.
 */
class RecognitionModeFallbackTest {

    @AfterTest
    fun tearDown() {
        TransactionCategory.setKeywordDatabase(null)
    }

    private class FakeMlClassifier(
        private val prediction: MlCategoryPrediction?
    ) : TransactionMlClassifier {
        var callCount: Int = 0
            private set

        override fun classify(transaction: ParsedTransaction): MlCategoryPrediction? {
            callCount++
            return prediction
        }
    }

    /** Negative amount with no rule hits → rules fall through to OTHER, enabling ML fallback. */
    private fun unknownTransaction() = ParsedTransaction(
        bookingDate = LocalDate(2024, 1, 15),
        amount = -50.0,
        description = "Random Payment XYZ",
        counterpartyName = null,
        remittanceInfo = null,
        rawText = null
    )

    @Test
    fun safeMode_skipsClassifier() {
        val classifier = FakeMlClassifier(
            MlCategoryPrediction(TransactionCategory.SHOPPING, 0.99)
        )
        val categorizer = TransactionCategorizer(
            mlClassifier = classifier,
            configProvider = { CategorizationConfig(RecognitionMode.SAFE) }
        )

        val result = categorizer.categorizeWithDetails(unknownTransaction())

        assertEquals(0, classifier.callCount, "SAFE must not invoke the classifier")
        assertEquals(TransactionCategory.OTHER, result.category)
        assertEquals(CategorizationSource.UNKNOWN, result.source)
    }

    @Test
    fun balancedMode_aboveThreshold_returnsMlCategory() {
        val classifier = FakeMlClassifier(
            MlCategoryPrediction(
                category = TransactionCategory.SHOPPING,
                confidence = 0.55,
                modelVersion = "v1.2"
            )
        )
        val categorizer = TransactionCategorizer(
            mlClassifier = classifier,
            configProvider = { CategorizationConfig(RecognitionMode.BALANCED) }
        )

        val result = categorizer.categorizeWithDetails(unknownTransaction())

        assertEquals(TransactionCategory.SHOPPING, result.category)
        assertEquals(0.55, result.confidence)
        assertEquals(CategorizationSource.ML, result.source)
        assertEquals("v1.2", result.modelVersion)
        assertNull(result.matchedValue, "matchedValue must not carry the model version")
    }

    @Test
    fun balancedMode_belowThreshold_returnsOtherWithSuggestion() {
        val classifier = FakeMlClassifier(
            MlCategoryPrediction(
                category = TransactionCategory.SHOPPING,
                confidence = 0.25,
                modelVersion = "v1.2"
            )
        )
        val categorizer = TransactionCategorizer(
            mlClassifier = classifier,
            configProvider = { CategorizationConfig(RecognitionMode.BALANCED) }
        )

        val result = categorizer.categorizeWithDetails(unknownTransaction())

        assertEquals(TransactionCategory.OTHER, result.category)
        assertEquals(CategorizationSource.UNKNOWN, result.source)
        assertEquals(TransactionCategory.SHOPPING, result.suggestedCategory)
        assertEquals(0.25, result.suggestedConfidence)
        assertEquals("v1.2", result.modelVersion)
    }

    @Test
    fun experimentalMode_acceptsLowerConfidenceThanBalanced() {
        val prediction = MlCategoryPrediction(TransactionCategory.SHOPPING, 0.25)
        val mode: () -> RecognitionMode = sequenceCallProvider(
            RecognitionMode.BALANCED,
            RecognitionMode.EXPERIMENTAL
        )
        val categorizer = TransactionCategorizer(
            mlClassifier = FakeMlClassifier(prediction),
            configProvider = { CategorizationConfig(mode()) }
        )

        val balanced = categorizer.categorizeWithDetails(unknownTransaction())
        val experimental = categorizer.categorizeWithDetails(unknownTransaction())

        assertEquals(TransactionCategory.OTHER, balanced.category)
        assertEquals(TransactionCategory.SHOPPING, experimental.category)
        assertEquals(CategorizationSource.ML, experimental.source)
    }

    @Test
    fun mlPredictingOther_isIgnored() {
        val classifier = FakeMlClassifier(
            MlCategoryPrediction(TransactionCategory.OTHER, 0.99)
        )
        val categorizer = TransactionCategorizer(
            mlClassifier = classifier,
            configProvider = { CategorizationConfig(RecognitionMode.BALANCED) }
        )

        val result = categorizer.categorizeWithDetails(unknownTransaction())

        assertEquals(TransactionCategory.OTHER, result.category)
        assertEquals(CategorizationSource.UNKNOWN, result.source)
        assertNull(result.suggestedCategory)
        assertNull(result.modelVersion)
    }

    @Test
    fun mlReturningNull_keepsRulesResult() {
        val classifier = FakeMlClassifier(prediction = null)
        val categorizer = TransactionCategorizer(
            mlClassifier = classifier,
            configProvider = { CategorizationConfig(RecognitionMode.BALANCED) }
        )

        val result = categorizer.categorizeWithDetails(unknownTransaction())

        assertEquals(1, classifier.callCount)
        assertEquals(TransactionCategory.OTHER, result.category)
        assertEquals(CategorizationSource.UNKNOWN, result.source)
        assertNull(result.suggestedCategory)
    }

    @Test
    fun rulesHit_skipsClassifierEvenInBalancedMode() {
        val classifier = FakeMlClassifier(
            MlCategoryPrediction(TransactionCategory.RESTAURANT, 0.99)
        )
        val categorizer = TransactionCategorizer(
            mlClassifier = classifier,
            configProvider = { CategorizationConfig(RecognitionMode.BALANCED) }
        )
        // "netflix" is a strong text rule for SUBSCRIPTIONS at 0.88 confidence,
        // which clears the auto-categorization threshold.
        val transaction = ParsedTransaction(
            bookingDate = LocalDate(2024, 1, 15),
            amount = -12.99,
            description = "Netflix subscription",
            counterpartyName = null,
            remittanceInfo = null,
            rawText = null
        )

        val result = categorizer.categorizeWithDetails(transaction)

        assertEquals(0, classifier.callCount, "Rules already produced a non-OTHER result")
        assertEquals(TransactionCategory.SUBSCRIPTIONS, result.category)
    }

    @Test
    fun configProviderIsReadEachCategorization() {
        var currentMode = RecognitionMode.SAFE
        val classifier = FakeMlClassifier(
            MlCategoryPrediction(TransactionCategory.SHOPPING, 0.99)
        )
        val categorizer = TransactionCategorizer(
            mlClassifier = classifier,
            configProvider = { CategorizationConfig(currentMode) }
        )

        val firstResult = categorizer.categorizeWithDetails(unknownTransaction())
        currentMode = RecognitionMode.BALANCED
        val secondResult = categorizer.categorizeWithDetails(unknownTransaction())

        assertEquals(TransactionCategory.OTHER, firstResult.category)
        assertEquals(TransactionCategory.SHOPPING, secondResult.category)
        assertEquals(CategorizationSource.ML, secondResult.source)
    }

    @Test
    fun safeMode_hasNoThreshold() {
        assertNull(RecognitionMode.SAFE.mlConfidenceThreshold)
        assertTrue(!RecognitionMode.SAFE.mlEnabled)
    }

    @Test
    fun balancedAndExperimental_haveThresholds() {
        assertNotNull(RecognitionMode.BALANCED.mlConfidenceThreshold)
        assertNotNull(RecognitionMode.EXPERIMENTAL.mlConfidenceThreshold)
        assertTrue(RecognitionMode.BALANCED.mlEnabled)
        assertTrue(RecognitionMode.EXPERIMENTAL.mlEnabled)
        assertTrue(
            RecognitionMode.EXPERIMENTAL.mlConfidenceThreshold!! <
                RecognitionMode.BALANCED.mlConfidenceThreshold!!,
            "Experimental should be more permissive than balanced"
        )
    }

    @Test
    fun recognitionModeFromName_handlesValidUnknownAndNull() {
        assertEquals(RecognitionMode.BALANCED, recognitionModeFromName("BALANCED"))
        assertEquals(RecognitionMode.EXPERIMENTAL, recognitionModeFromName("EXPERIMENTAL"))
        assertEquals(RecognitionMode.SAFE, recognitionModeFromName("SAFE"))
        assertEquals(RecognitionMode.SAFE, recognitionModeFromName(null))
        assertEquals(RecognitionMode.SAFE, recognitionModeFromName(""))
        assertEquals(RecognitionMode.SAFE, recognitionModeFromName("balanced"))
        assertEquals(RecognitionMode.SAFE, recognitionModeFromName("MISSING"))
    }

    private fun sequenceCallProvider(vararg modes: RecognitionMode): () -> RecognitionMode {
        var index = 0
        return {
            val value = modes[index]
            if (index < modes.size - 1) index++
            value
        }
    }
}
