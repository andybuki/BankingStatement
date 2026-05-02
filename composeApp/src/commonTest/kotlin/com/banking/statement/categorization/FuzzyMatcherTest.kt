package com.banking.statement.categorization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FuzzyMatcherTest {

    @Test
    fun levenshteinIdentical() {
        assertEquals(0, FuzzyMatcher.levenshteinDistance("rewe", "rewe"))
        assertEquals(1.0, FuzzyMatcher.levenshteinSimilarity("rewe", "rewe"))
    }

    @Test
    fun levenshteinSingleSubstitution() {
        // OCR error: w → vv
        assertEquals(1, FuzzyMatcher.levenshteinDistance("rewe", "reve"))
        // 1 - 1/4 = 0.75
        assertEquals(0.75, FuzzyMatcher.levenshteinSimilarity("rewe", "reve"))
    }

    @Test
    fun levenshteinInsertionDeletion() {
        assertEquals(1, FuzzyMatcher.levenshteinDistance("rewe", "rewes"))
        assertEquals(1, FuzzyMatcher.levenshteinDistance("rewes", "rewe"))
    }

    @Test
    fun levenshteinEarlyExit() {
        // Distance is 4, but we limit to 2 — should return >maxDistance
        val d = FuzzyMatcher.levenshteinDistance("aaaa", "bbbb", maxDistance = 2)
        assertTrue(d > 2, "Expected early exit value > 2, got $d")
    }

    @Test
    fun trigramSimilarityIdentical() {
        assertEquals(1.0, FuzzyMatcher.trigramSimilarity("starbucks", "starbucks"))
    }

    @Test
    fun trigramSimilarityHandlesShortTokens() {
        // Should not throw on very short strings
        val s = FuzzyMatcher.trigramSimilarity("ab", "ac")
        assertTrue(s in 0.0..1.0, "Score out of range: $s")
    }

    @Test
    fun similarityHandlesCommonOcrErrors() {
        // OCR confuses l → 1. Trigram similarity alone is low for short
        // strings with a substitution, but combined with Levenshtein the
        // overall similarity stays high enough to match.
        val combined = FuzzyMatcher.similarity("lidl", "lid1")
        assertTrue(combined >= 0.7, "Expected combined similarity >= 0.7, got $combined")
    }

    @Test
    fun trigramSimilarityWordOrderInvariant() {
        val score = FuzzyMatcher.trigramSimilarity("lidl markt", "markt lidl")
        assertTrue(score >= 0.5, "Trigram should tolerate word reordering, got $score")
    }

    @Test
    fun similarityCombinesBothMetrics() {
        val s = FuzzyMatcher.similarity("rewe", "reve")
        // Levenshtein gives 0.75 — combined should be >= 0.75
        assertTrue(s >= 0.75, "Expected combined similarity >= 0.75, got $s")
    }

    @Test
    fun bestMatchReturnsClosestCandidate() {
        val candidates = listOf("aldi", "rewe", "lidl", "edeka", "kaufland")
        val match = FuzzyMatcher.bestMatch(
            query = "rewe",
            candidates = candidates,
            keyOf = { it }
        )
        assertNotNull(match)
        assertEquals("rewe", match.candidate)
        assertEquals(1.0, match.score)
    }

    @Test
    fun bestMatchToleratesOcrError() {
        val candidates = listOf("aldi", "rewe", "lidl", "edeka", "starbucks")
        // "starbjcks" — single-character OCR substitution (u → j) on a long
        // brand. Levenshtein similarity is 8/9 ≈ 0.89, well above threshold.
        val match = FuzzyMatcher.bestMatch(
            query = "starbjcks",
            candidates = candidates,
            keyOf = { it }
        )
        assertNotNull(match, "Expected fuzzy match for OCR'd 'starbjcks'")
        assertEquals("starbucks", match.candidate)
    }

    @Test
    fun bestMatchRejectsBelowThreshold() {
        val candidates = listOf("rewe", "lidl", "edeka")
        val match = FuzzyMatcher.bestMatch(
            query = "completely unrelated",
            candidates = candidates,
            keyOf = { it }
        )
        assertNull(match)
    }

    @Test
    fun bestMatchRejectsTooShortQuery() {
        val candidates = listOf("rewe", "lidl")
        // Query under 4 chars should never fuzzy match (too ambiguous)
        val match = FuzzyMatcher.bestMatch(
            query = "re",
            candidates = candidates,
            keyOf = { it }
        )
        assertNull(match)
    }

    @Test
    fun bestMatchAppliesLengthFilter() {
        val candidates = listOf("a", "rewe-extremely-long-merchant-name-no-match")
        // Length differs by ~40 chars — should be filtered out before scoring
        val match = FuzzyMatcher.bestMatch(
            query = "rewe",
            candidates = candidates,
            keyOf = { it }
        )
        assertNull(match)
    }
}
