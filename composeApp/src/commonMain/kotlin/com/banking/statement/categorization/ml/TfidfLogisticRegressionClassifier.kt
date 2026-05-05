package com.banking.statement.categorization.ml

import com.banking.statement.categorization.MlCategoryPrediction
import com.banking.statement.categorization.TransactionCategory
import com.banking.statement.categorization.TransactionMlClassifier
import com.banking.statement.parser.ParsedTransaction
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * On-device classifier matching the sklearn pipeline:
 * `Pipeline([("tfidf", TfidfVectorizer(...)), ("clf", LogisticRegression(...))])`.
 *
 * The math sklearn applies (and that this class reproduces):
 *   * tokenize features text with the default \b\w\w+\b Unicode pattern
 *   * generate n-grams in the configured range, joining tokens with a single space
 *   * count term frequencies, optionally apply 1 + ln(tf) (sublinear_tf)
 *   * multiply by precomputed idf weights
 *   * L2-normalize the resulting vector
 *   * compute logits = coefs @ vector + intercepts
 *   * softmax over logits → probabilities
 *
 * Predictions for the literal `OTHER` class or labels that don't map back to a
 * known [TransactionCategory] are dropped — the rules-based path already covers
 * the no-signal case, so we don't want the model to claim OTHER with high
 * confidence and crowd out a downstream suggestion.
 */
internal class TfidfLogisticRegressionClassifier(
    private val model: MlModelData
) : TransactionMlClassifier {

    private val categoryByLabelIndex: Array<TransactionCategory?> =
        Array(model.labels.size) { idx -> findCategory(model.labels[idx]) }

    override fun classify(transaction: ParsedTransaction): MlCategoryPrediction? {
        val featureText = MlFeatureExtractor.buildFeatureText(transaction)
        if (featureText.isBlank()) return null

        val tfidf = computeTfidfVector(featureText)
        if (tfidf.isEmpty()) return null

        val probabilities = predictProba(tfidf)

        var topIndex = 0
        var topProb = probabilities[0]
        for (i in 1 until probabilities.size) {
            if (probabilities[i] > topProb) {
                topProb = probabilities[i]
                topIndex = i
            }
        }

        val category = categoryByLabelIndex[topIndex] ?: return null
        if (category == TransactionCategory.OTHER) return null

        return MlCategoryPrediction(
            category = category,
            confidence = topProb,
            modelVersion = model.model_version
        )
    }

    // region TF-IDF

    /**
     * Builds the sparse TF-IDF vector keyed by feature index, identical to
     * sklearn's default TfidfVectorizer with norm='l2' and lowercase=True.
     * The exporter rejects models that diverge from those defaults.
     */
    private fun computeTfidfVector(text: String): Map<Int, Double> {
        val tokens = TOKEN_REGEX.findAll(text).map { it.value }.toList()
        if (tokens.isEmpty()) return emptyMap()

        val rawCounts = HashMap<Int, Int>()
        for (n in model.ngram_min..model.ngram_max) {
            if (n <= 0 || n > tokens.size) continue
            for (start in 0..tokens.size - n) {
                val ngram = if (n == 1) tokens[start] else tokens.subList(start, start + n).joinToString(" ")
                val featureIndex = model.vocabulary[ngram] ?: continue
                rawCounts[featureIndex] = (rawCounts[featureIndex] ?: 0) + 1
            }
        }

        if (rawCounts.isEmpty()) return emptyMap()

        val weighted = HashMap<Int, Double>(rawCounts.size)
        var l2Sum = 0.0
        for ((index, count) in rawCounts) {
            val tf = if (model.sublinear_tf) 1.0 + ln(count.toDouble()) else count.toDouble()
            val tfidf = tf * model.idf[index]
            weighted[index] = tfidf
            l2Sum += tfidf * tfidf
        }

        val norm = sqrt(l2Sum)
        if (norm == 0.0) return weighted

        for (key in weighted.keys.toList()) {
            weighted[key] = weighted.getValue(key) / norm
        }
        return weighted
    }

    // endregion

    // region Logistic regression

    private fun predictProba(tfidf: Map<Int, Double>): DoubleArray {
        val labelCount = model.labels.size
        val logits = DoubleArray(labelCount)
        for (cls in 0 until labelCount) {
            val coefRow = model.coefs[cls]
            var score = model.intercepts[cls]
            for ((index, value) in tfidf) {
                score += coefRow[index] * value
            }
            logits[cls] = score
        }

        var maxLogit = logits[0]
        for (i in 1 until logits.size) if (logits[i] > maxLogit) maxLogit = logits[i]

        var sumExp = 0.0
        val exps = DoubleArray(labelCount)
        for (i in 0 until labelCount) {
            val e = exp(logits[i] - maxLogit)
            exps[i] = e
            sumExp += e
        }

        if (sumExp == 0.0) return DoubleArray(labelCount) { 1.0 / labelCount }

        for (i in 0 until labelCount) exps[i] = exps[i] / sumExp
        return exps
    }

    // endregion

    private fun findCategory(label: String): TransactionCategory? {
        return TransactionCategory.entries.firstOrNull { it.name == label }
    }

    companion object {
        /**
         * sklearn TfidfVectorizer(token_pattern) defaults to `(?u)\b\w\w+\b`
         * with `\w` interpreted Unicode-aware. Kotlin's [Regex] honours that
         * the same way so this expression matches the same token spans.
         */
        private val TOKEN_REGEX = Regex("\\b\\w\\w+\\b")
    }
}
