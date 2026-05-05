package com.banking.statement.categorization.ml

import com.banking.statement.categorization.NoOpTransactionMlClassifier
import com.banking.statement.categorization.TransactionMlClassifier
import kotlinx.serialization.json.Json

/**
 * Parses the JSON exported by `ml/export_for_app.py` and returns the
 * on-device classifier. Falls back to [NoOpTransactionMlClassifier] if the
 * payload is null/blank or fails to parse, so a missing or corrupt model
 * file never crashes the app.
 */
object MlClassifierFactory {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun fromJsonOrNoOp(payload: String?): TransactionMlClassifier {
        if (payload.isNullOrBlank()) return NoOpTransactionMlClassifier
        return try {
            val model = json.decodeFromString(MlModelData.serializer(), payload)
            TfidfLogisticRegressionClassifier(model)
        } catch (_: Throwable) {
            NoOpTransactionMlClassifier
        }
    }
}
