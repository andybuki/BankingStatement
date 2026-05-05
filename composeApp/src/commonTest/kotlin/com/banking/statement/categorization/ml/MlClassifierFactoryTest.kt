package com.banking.statement.categorization.ml

import com.banking.statement.categorization.NoOpTransactionMlClassifier
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MlClassifierFactoryTest {

    @Test
    fun nullPayload_returnsNoOp() {
        assertSame(NoOpTransactionMlClassifier, MlClassifierFactory.fromJsonOrNoOp(null))
    }

    @Test
    fun blankPayload_returnsNoOp() {
        assertSame(NoOpTransactionMlClassifier, MlClassifierFactory.fromJsonOrNoOp("   \n  "))
    }

    @Test
    fun invalidJson_returnsNoOp() {
        assertSame(NoOpTransactionMlClassifier, MlClassifierFactory.fromJsonOrNoOp("not json"))
    }

    @Test
    fun missingRequiredFields_returnsNoOp() {
        // Missing labels, vocabulary, etc.
        val payload = """{"model_version":"v","ngram_min":1,"ngram_max":1}"""
        assertSame(NoOpTransactionMlClassifier, MlClassifierFactory.fromJsonOrNoOp(payload))
    }

    @Test
    fun validJson_returnsRealClassifier() {
        val payload = """
            {
                "model_version": "v1",
                "labels": ["OTHER", "SUBSCRIPTIONS"],
                "ngram_min": 1,
                "ngram_max": 1,
                "sublinear_tf": true,
                "vocabulary": {"netflix": 0},
                "idf": [1.0],
                "intercepts": [0.0, 0.0],
                "coefs": [[0.0], [3.0]]
            }
        """.trimIndent()

        val classifier = MlClassifierFactory.fromJsonOrNoOp(payload)

        assertTrue(classifier is TfidfLogisticRegressionClassifier)
    }
}
