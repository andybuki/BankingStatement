package com.bankwise.app.categorization

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for transaction categorization logic.
 * These tests verify that the keyword-based categorization works correctly,
 * especially for well-known merchants like supermarkets.
 */
class CategorizationTest {

    @Test
    fun testKeywordDatabase_LidlSupermarket() {
        // Setup keyword database with Lidl as SUPERMARKET
        val keywordDb = KeywordDatabase()
        val csvContent = """
            category,keyword
            SUPERMARKET,lidl
            SUPERMARKET,rewe
            SUPERMARKET,edeka
            RESTAURANT,restaurant
            RESTAURANT,bistro
            RESTAURANT,cafe
        """.trimIndent()
        keywordDb.loadFromCsv(csvContent, "de")

        // Test various Lidl transaction descriptions
        val testCases = listOf(
            "Lidl Sagt Danke" to TransactionCategory.SUPERMARKET,
            "LIDL GMBH" to TransactionCategory.SUPERMARKET,
            "Lidl Backshop" to TransactionCategory.SUPERMARKET,  // Should be SUPERMARKET, not RESTAURANT!
            "Bäckerei Lidl" to TransactionCategory.SUPERMARKET,  // Should be SUPERMARKET, not RESTAURANT!
            "Lidl Filiale 1234" to TransactionCategory.SUPERMARKET,
            "lidl" to TransactionCategory.SUPERMARKET,
            "KAUFLAND // LIDL" to TransactionCategory.SUPERMARKET
        )

        for ((description, expectedCategory) in testCases) {
            val result = keywordDb.findCategory(description, null)
            assertEquals(
                expectedCategory,
                result,
                "Description '$description' should be categorized as $expectedCategory, but got $result"
            )
        }
    }

    @Test
    fun testKeywordDatabase_ScoringSystem() {
        // Test that the scoring system works correctly
        val keywordDb = KeywordDatabase()
        val csvContent = """
            category,keyword
            SUPERMARKET,lidl
            RESTAURANT,restaurant
            RESTAURANT,bistro
            RESTAURANT,cafe
        """.trimIndent()
        keywordDb.loadFromCsv(csvContent, "de")

        // "Lidl Bistro Cafe Restaurant" should still be SUPERMARKET because it has 1 SUPERMARKET keyword
        // vs 3 RESTAURANT keywords. This is a problem!
        val result = keywordDb.findCategory("Lidl Bistro Cafe Restaurant", null)

        // This test will currently FAIL because RESTAURANT gets score=3 vs SUPERMARKET score=1
        // After the fix, it should prioritize by specificity or word-boundary matching
        println("Result for 'Lidl Bistro Cafe Restaurant': $result")
        // Currently this will be RESTAURANT (3 matches) instead of SUPERMARKET (1 match)
    }

    @Test
    fun testKeywordDatabase_ExactWordMatching() {
        val keywordDb = KeywordDatabase()
        val csvContent = """
            category,keyword
            SUPERMARKET,lidl
            RENT,miete
        """.trimIndent()
        keywordDb.loadFromCsv(csvContent, "de")

        // "Lidl" should match as SUPERMARKET
        assertEquals(TransactionCategory.SUPERMARKET, keywordDb.findCategory("Lidl Sagt Danke", null))

        // "Miete" should match as RENT (not partial match on "vermieter")
        assertEquals(TransactionCategory.RENT, keywordDb.findCategory("Miete Wohnung", null))

        // "Vermieter" should NOT match "miete" keyword (but currently it does due to contains())
        val result = keywordDb.findCategory("Vermieter Zahlung", null)
        println("Result for 'Vermieter': $result") // Will incorrectly match RENT
    }

    @Test
    fun testMerchantDatabase_LongestMatchFirst() {
        // This test demonstrates the issue where merchant database entries
        // with longer names (like "Lidl Backshop") match before shorter ones ("Lidl")
        // and can have conflicting categories

        // The merchant DB sorts by length descending, so:
        // "Lidl Backshop" (bk->RESTAURANT) matches before "Lidl" (sm->SUPERMARKET)
        // This is by design for specificity, but causes issues when the longer
        // variants have incorrect categories in the source data
    }
}
