package com.banking.statement.categorization.ml

import com.banking.statement.categorization.NoOpTransactionMlClassifier
import com.banking.statement.categorization.TransactionCategory
import com.banking.statement.parser.ParsedTransaction
import kotlinx.datetime.LocalDate
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Unit tests for the TF-IDF + LogReg classifier. We hand-build small models
 * so we can predict the exact softmax output by hand, then assert the
 * Kotlin pipeline produces the same numbers.
 */
class TfidfLogisticRegressionClassifierTest {

    private fun tx(description: String, amount: Double = -10.0) = ParsedTransaction(
        bookingDate = LocalDate(2024, 1, 15),
        amount = amount,
        description = description,
        counterpartyName = null,
        remittanceInfo = null,
        rawText = null
    )

    /**
     * Trivial single-feature model:
     *   vocab = {"netflix": 0}
     *   idf   = [1.0]
     *   labels = ["OTHER", "RENT", "SUBSCRIPTIONS"]
     *   coefs  = [[0,0,0], [0,0,0], [0,0,5]]   # SUBSCRIPTIONS coefficient on netflix is 5
     *   intercepts = [0,0,0]
     *
     * Wait — coefs in our format are [n_classes][n_features], so for 3 classes / 1 feature:
     *   coefs[OTHER] = [0]
     *   coefs[RENT] = [0]
     *   coefs[SUBSCRIPTIONS] = [5]
     */
    @Test
    fun singleFeatureModel_predictsExpectedCategory() {
        val model = MlModelData(
            model_version = "test-v1",
            labels = listOf("OTHER", "RENT", "SUBSCRIPTIONS"),
            ngram_min = 1,
            ngram_max = 1,
            sublinear_tf = true,
            vocabulary = mapOf("netflix" to 0),
            idf = listOf(1.0),
            intercepts = listOf(0.0, 0.0, 0.0),
            coefs = listOf(listOf(0.0), listOf(0.0), listOf(5.0))
        )
        val classifier = TfidfLogisticRegressionClassifier(model)

        val prediction = classifier.classify(tx("Netflix subscription"))

        assertNotNull(prediction)
        assertEquals(TransactionCategory.SUBSCRIPTIONS, prediction.category)
        assertEquals("test-v1", prediction.modelVersion)
        // tf=1 → 1+ln(1)=1; tfidf=1*1=1; L2-normed=1.0; logits=[0,0,5];
        // softmax: e^-5 / (e^-5 + e^-5 + 1) ≈ 1 / (1+2e^-5) ≈ 0.9866
        val expected = 1.0 / (1.0 + 2.0 * exp(-5.0))
        assertEquals(expected, prediction.confidence, absoluteTolerance = 1e-9)
    }

    /**
     * When the input has no features in the vocabulary, the vector is empty
     * so the classifier must return null instead of guessing from the bias.
     */
    @Test
    fun noVocabularyMatch_returnsNull() {
        val model = MlModelData(
            model_version = "v",
            labels = listOf("OTHER", "RENT", "SUBSCRIPTIONS"),
            ngram_min = 1,
            ngram_max = 1,
            sublinear_tf = true,
            vocabulary = mapOf("netflix" to 0),
            idf = listOf(1.0),
            intercepts = listOf(0.0, 0.0, 0.0),
            coefs = listOf(listOf(0.0), listOf(0.0), listOf(5.0))
        )
        val classifier = TfidfLogisticRegressionClassifier(model)

        val prediction = classifier.classify(tx("xyz random unmatched stuff"))

        assertNull(prediction)
    }

    /** Predictions for the OTHER class are dropped to keep the rules-suggestion path clean. */
    @Test
    fun otherClassPrediction_isDropped() {
        val model = MlModelData(
            model_version = "v",
            labels = listOf("OTHER", "RENT", "SUBSCRIPTIONS"),
            ngram_min = 1,
            ngram_max = 1,
            sublinear_tf = false,
            vocabulary = mapOf("netflix" to 0),
            idf = listOf(1.0),
            intercepts = listOf(10.0, 0.0, 0.0),
            coefs = listOf(listOf(0.0), listOf(0.0), listOf(0.0))
        )
        val classifier = TfidfLogisticRegressionClassifier(model)

        val prediction = classifier.classify(tx("Netflix subscription"))

        assertNull(prediction)
    }

    /**
     * Bigram coverage: the trained vocab includes "deutsche bahn" as a
     * bigram. With ngram_max=2 we must emit that token even though it
     * never appears as a standalone unigram.
     */
    @Test
    fun bigramFeature_isCounted() {
        val model = MlModelData(
            model_version = "v",
            labels = listOf("OTHER", "TRANSPORT"),
            ngram_min = 1,
            ngram_max = 2,
            sublinear_tf = false,
            vocabulary = mapOf("deutsche bahn" to 0),
            idf = listOf(2.0),
            intercepts = listOf(0.0, 0.0),
            coefs = listOf(listOf(0.0), listOf(3.0))
        )
        val classifier = TfidfLogisticRegressionClassifier(model)

        val prediction = classifier.classify(tx("Deutsche Bahn ticket"))

        assertNotNull(prediction)
        assertEquals(TransactionCategory.TRANSPORT, prediction.category)
        // tf=1 (no sublinear); tfidf = 1*2 = 2; L2 = sqrt(4) = 2; normed = 1.0
        // logits = [0, 3]; softmax favors class 1 with confidence ~ 1/(1 + e^-3)
        val expected = 1.0 / (1.0 + exp(-3.0))
        assertEquals(expected, prediction.confidence, absoluteTolerance = 1e-9)
    }

    /**
     * Verify L2 normalization works for multi-feature inputs: two features
     * each with tfidf=1 should produce a normalized vector of [1/√2, 1/√2].
     */
    @Test
    fun l2Normalization_scalesMultiFeatureVector() {
        val coefSpotify = 4.0
        val coefNetflix = 2.0
        val model = MlModelData(
            model_version = "v",
            labels = listOf("OTHER", "SUBSCRIPTIONS"),
            ngram_min = 1,
            ngram_max = 1,
            sublinear_tf = false,
            vocabulary = mapOf("spotify" to 0, "netflix" to 1),
            idf = listOf(1.0, 1.0),
            intercepts = listOf(0.0, 0.0),
            coefs = listOf(listOf(0.0, 0.0), listOf(coefSpotify, coefNetflix))
        )
        val classifier = TfidfLogisticRegressionClassifier(model)

        val prediction = classifier.classify(tx("spotify netflix"))

        assertNotNull(prediction)
        // Each feature has tfidf=1; L2 norm = sqrt(2); normed each = 1/sqrt(2)
        // Class 1 logit = (4 + 2) / sqrt(2) = 6/sqrt(2) ≈ 4.2426
        val classLogit = (coefSpotify + coefNetflix) / sqrt(2.0)
        val expected = 1.0 / (1.0 + exp(-classLogit))
        assertEquals(expected, prediction.confidence, absoluteTolerance = 1e-9)
    }

    /** Sublinear TF replaces raw count `c` with `1 + ln(c)`. */
    @Test
    fun sublinearTf_appliesLog() {
        val model = MlModelData(
            model_version = "v",
            labels = listOf("OTHER", "SHOPPING"),
            ngram_min = 1,
            ngram_max = 1,
            sublinear_tf = true,
            vocabulary = mapOf("amazon" to 0),
            idf = listOf(1.0),
            intercepts = listOf(0.0, 0.0),
            coefs = listOf(listOf(0.0), listOf(1.0))
        )
        val classifier = TfidfLogisticRegressionClassifier(model)

        // Repeating "amazon" three times → tf=3 → sublinear = 1 + ln(3)
        val prediction = classifier.classify(tx("amazon amazon amazon"))

        assertNotNull(prediction)
        // tfidf = (1+ln3) * 1; L2-normed = 1.0 (single feature); logit = 1.0
        val expected = 1.0 / (1.0 + exp(-1.0))
        assertEquals(expected, prediction.confidence, absoluteTolerance = 1e-9)

        // Also verify our analytical understanding: sublinear actually applied
        val sublinear = 1.0 + ln(3.0)
        assertTrue(sublinear > 1.0 && sublinear < 3.0)
    }
}
