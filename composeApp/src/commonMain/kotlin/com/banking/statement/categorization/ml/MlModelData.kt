package com.banking.statement.categorization.ml

import kotlinx.serialization.Serializable

/**
 * Wire format produced by `ml/export_for_app.py` and consumed by
 * [TfidfLogisticRegressionClassifier]. Field names must stay in sync with
 * the Python writer.
 */
@Serializable
internal data class MlModelData(
    val model_version: String,
    val labels: List<String>,
    val ngram_min: Int,
    val ngram_max: Int,
    val sublinear_tf: Boolean,
    val vocabulary: Map<String, Int>,
    val idf: List<Double>,
    val intercepts: List<Double>,
    val coefs: List<List<Double>>
)
