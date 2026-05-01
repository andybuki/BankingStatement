package com.banking.statement.categorization

import kotlin.math.max
import kotlin.math.min

/**
 * Approximate string matching utilities used to recover merchant matches
 * when exact lookups fail — typically because of OCR noise on scanned PDFs
 * (e.g. "REVVE" vs "REWE", "lid1" vs "lidl") or harmless name variations
 * (e.g. "MCDONALD'S" vs "MCDONALDS").
 *
 * Two complementary metrics are exposed:
 *  - [levenshteinSimilarity]: tolerant to small character substitutions.
 *  - [trigramSimilarity]: tolerant to insertions, deletions and word order.
 *
 * For merchant matching, [bestMatch] uses trigrams to cheaply prune
 * candidates and falls back to Levenshtein for the final ranking.
 */
object FuzzyMatcher {

    /** Default similarity floor for accepting a fuzzy merchant match. */
    const val DEFAULT_SIMILARITY_THRESHOLD: Double = 0.82

    /** Minimum string length required to attempt fuzzy matching. */
    private const val MIN_FUZZY_LENGTH: Int = 4

    /**
     * Classic Levenshtein edit distance (insertions, deletions, substitutions).
     *
     * Uses two-row dynamic programming so memory is O(min(|a|,|b|)). An early
     * exit triggers as soon as every value in a row exceeds [maxDistance].
     */
    fun levenshteinDistance(a: String, b: String, maxDistance: Int = Int.MAX_VALUE): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        // Ensure b is the shorter string so the rows we keep are smaller.
        val (s1, s2) = if (a.length >= b.length) a to b else b to a
        val n = s1.length
        val m = s2.length

        if (n - m > maxDistance) return maxDistance + 1

        var prev = IntArray(m + 1) { it }
        var curr = IntArray(m + 1)

        for (i in 1..n) {
            curr[0] = i
            var rowMin = curr[0]
            val c1 = s1[i - 1]
            for (j in 1..m) {
                val cost = if (c1 == s2[j - 1]) 0 else 1
                curr[j] = min(
                    min(curr[j - 1] + 1, prev[j] + 1),
                    prev[j - 1] + cost
                )
                if (curr[j] < rowMin) rowMin = curr[j]
            }
            if (rowMin > maxDistance) return maxDistance + 1
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[m]
    }

    /**
     * Levenshtein similarity in [0.0, 1.0]: 1.0 == identical, 0.0 == disjoint.
     * Computed as `1 - distance / max(|a|,|b|)`.
     */
    fun levenshteinSimilarity(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        val maxLen = max(a.length, b.length)
        if (maxLen == 0) return 1.0
        val distance = levenshteinDistance(a, b)
        return 1.0 - distance.toDouble() / maxLen
    }

    /**
     * Generate the set of character trigrams for a string. Strings shorter
     * than 3 characters return a single padded token so they can still be
     * compared.
     */
    fun trigrams(s: String): Set<String> {
        if (s.isEmpty()) return emptySet()
        val padded = "  $s "
        if (padded.length < 3) return setOf(padded)
        val out = HashSet<String>(padded.length)
        for (i in 0..padded.length - 3) {
            out.add(padded.substring(i, i + 3))
        }
        return out
    }

    /**
     * Jaccard similarity over character trigrams in [0.0, 1.0].
     * More robust than Levenshtein for word-order changes and prefix/suffix
     * differences (e.g. "Lidl Markt" vs "Markt Lidl Berlin").
     */
    fun trigramSimilarity(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        val ta = trigrams(a)
        val tb = trigrams(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        var intersect = 0
        val (smaller, larger) = if (ta.size <= tb.size) ta to tb else tb to ta
        for (t in smaller) if (t in larger) intersect++
        val union = ta.size + tb.size - intersect
        return if (union == 0) 0.0 else intersect.toDouble() / union
    }

    /**
     * Combined similarity score: max(trigram, levenshtein).
     * Gives the best of both metrics — Levenshtein wins on short tokens with
     * a single typo, trigrams win on longer phrases with rearranged words.
     */
    fun similarity(a: String, b: String): Double {
        val tri = trigramSimilarity(a, b)
        val lev = levenshteinSimilarity(a, b)
        return max(tri, lev)
    }

    /**
     * Result of a fuzzy lookup against a candidate set.
     */
    data class Match<T>(val candidate: T, val score: Double)

    /**
     * Find the best matching candidate by similarity. Returns null if no
     * candidate scores above [threshold] or if [query] is too short to match
     * meaningfully.
     */
    fun <T> bestMatch(
        query: String,
        candidates: Iterable<T>,
        threshold: Double = DEFAULT_SIMILARITY_THRESHOLD,
        keyOf: (T) -> String
    ): Match<T>? {
        if (query.length < MIN_FUZZY_LENGTH) return null

        var best: Match<T>? = null
        for (candidate in candidates) {
            val key = keyOf(candidate)
            if (key.length < MIN_FUZZY_LENGTH) continue

            // Cheap length filter: similarity can't exceed 1 - |Δlen|/maxLen
            val maxLen = max(query.length, key.length)
            val lengthDelta = kotlin.math.abs(query.length - key.length)
            if (1.0 - lengthDelta.toDouble() / maxLen < threshold) continue

            val score = similarity(query, key)
            if (score >= threshold && (best == null || score > best.score)) {
                best = Match(candidate, score)
                if (score >= 0.999) return best
            }
        }
        return best
    }
}
