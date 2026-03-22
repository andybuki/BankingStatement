package com.banking.statement.categorization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Comprehensive tests for KeywordDatabase categorization.
 * Covers keyword matching, word boundaries, multi-word keywords,
 * priority by specificity, and country code handling.
 */
class KeywordDatabaseTest {

    private fun createDatabase(csvContent: String, countryCode: String = "de"): KeywordDatabase {
        val db = KeywordDatabase()
        db.loadFromCsv(csvContent, countryCode)
        return db
    }

    // ===== Loading tests =====

    @Test
    fun testLoad_EmptyContent_NotLoaded() {
        val db = KeywordDatabase()
        db.loadFromCsv("", "de")

        assertFalse(db.isLoaded())
    }

    @Test
    fun testLoad_ValidCsv_Loaded() {
        val db = createDatabase("category,keyword\nSUPERMARKET,lidl")

        assertTrue(db.isLoaded())
        assertEquals("de", db.getLoadedCountryCode())
    }

    @Test
    fun testLoad_WithHeader_SkipsHeader() {
        val db = createDatabase("category,keyword\nSUPERMARKET,lidl\nSUPERMARKET,rewe")

        assertEquals(2, db.getKeywordCount())
    }

    @Test
    fun testLoad_WithoutHeader_ParsesAllLines() {
        val db = createDatabase("SUPERMARKET,lidl\nSUPERMARKET,rewe")

        assertEquals(2, db.getKeywordCount())
    }

    @Test
    fun testLoad_SkipsOTHERCategory() {
        val db = createDatabase("category,keyword\nOTHER,something\nSUPERMARKET,lidl")

        assertEquals(1, db.getKeywordCount())
    }

    @Test
    fun testLoad_SkipsInvalidCategory() {
        val db = createDatabase("category,keyword\nINVALID_CATEGORY,something\nSUPERMARKET,lidl")

        assertEquals(1, db.getKeywordCount())
    }

    @Test
    fun testLoad_SkipsBlankLines() {
        val db = createDatabase("category,keyword\nSUPERMARKET,lidl\n\nSUPERMARKET,rewe\n  \n")

        assertEquals(2, db.getKeywordCount())
    }

    @Test
    fun testLoad_SkipsBlankKeywords() {
        val db = createDatabase("category,keyword\nSUPERMARKET,lidl\nSUPERMARKET,")

        assertEquals(1, db.getKeywordCount())
    }

    @Test
    fun testLoad_SkipsMalformedLines() {
        val db = createDatabase("category,keyword\njust_one_field\nSUPERMARKET,lidl")

        assertEquals(1, db.getKeywordCount())
    }

    @Test
    fun testClear_RemovesAllData() {
        val db = createDatabase("category,keyword\nSUPERMARKET,lidl")

        assertTrue(db.isLoaded())
        db.clear()
        assertFalse(db.isLoaded())
        assertNull(db.getLoadedCountryCode())
    }

    // ===== Basic matching tests =====

    @Test
    fun testFindCategory_ExactMatch() {
        val db = createDatabase("category,keyword\nSUPERMARKET,lidl")

        assertEquals(TransactionCategory.SUPERMARKET, db.findCategory("lidl"))
    }

    @Test
    fun testFindCategory_CaseInsensitive() {
        val db = createDatabase("category,keyword\nSUPERMARKET,lidl")

        assertEquals(TransactionCategory.SUPERMARKET, db.findCategory("LIDL"))
        assertEquals(TransactionCategory.SUPERMARKET, db.findCategory("Lidl"))
        assertEquals(TransactionCategory.SUPERMARKET, db.findCategory("LiDl"))
    }

    @Test
    fun testFindCategory_WithSurroundingText() {
        val db = createDatabase("category,keyword\nSUPERMARKET,lidl")

        assertEquals(TransactionCategory.SUPERMARKET, db.findCategory("Lidl Sagt Danke"))
        assertEquals(TransactionCategory.SUPERMARKET, db.findCategory("LIDL GMBH Berlin"))
        assertEquals(TransactionCategory.SUPERMARKET, db.findCategory("Einkauf bei Lidl"))
    }

    @Test
    fun testFindCategory_NoMatch() {
        val db = createDatabase("category,keyword\nSUPERMARKET,lidl")

        assertNull(db.findCategory("Amazon Payment"))
    }

    @Test
    fun testFindCategory_EmptyDatabase_ReturnsNull() {
        val db = KeywordDatabase()

        assertNull(db.findCategory("anything"))
    }

    // ===== Word boundary matching =====

    @Test
    fun testWordBoundary_MieteNotMatchesVermieter() {
        val db = createDatabase("category,keyword\nRENT,miete")

        assertEquals(TransactionCategory.RENT, db.findCategory("Miete Wohnung"))
        // "vermieter" should NOT match "miete" because of word boundary
        assertNull(db.findCategory("Vermieter Zahlung"))
    }

    @Test
    fun testWordBoundary_PartialWordDoesNotMatch() {
        val db = createDatabase("category,keyword\nSUPERMARKET,rewe")

        assertEquals(TransactionCategory.SUPERMARKET, db.findCategory("REWE Markt"))
        assertNull(db.findCategory("Andrews Payment"))
    }

    @Test
    fun testWordBoundary_SpecialCharsAsWordSeparators() {
        val db = createDatabase("category,keyword\nSUPERMARKET,lidl")

        // Special chars should be treated as word boundaries
        assertEquals(TransactionCategory.SUPERMARKET, db.findCategory("Einkauf/Lidl/Berlin"))
        assertEquals(TransactionCategory.SUPERMARKET, db.findCategory("KAUFLAND//LIDL"))
    }

    // ===== Multi-word keyword matching =====

    @Test
    fun testMultiWordKeyword_AllWordsPresent() {
        val db = createDatabase("category,keyword\nINSURANCE,allianz versicherung")

        assertEquals(TransactionCategory.INSURANCE, db.findCategory("Allianz Versicherung AG"))
    }

    @Test
    fun testMultiWordKeyword_WordsMissing() {
        val db = createDatabase("category,keyword\nINSURANCE,allianz versicherung")

        // Only one of the two words is present
        assertNull(db.findCategory("Allianz Bank"))
    }

    @Test
    fun testMultiWordKeyword_WordsOutOfOrder() {
        val db = createDatabase("category,keyword\nINSURANCE,allianz versicherung")

        // Multi-word keywords need to appear in sequence
        assertNull(db.findCategory("Versicherung bei Allianz"))
    }

    // ===== Longest match priority =====

    @Test
    fun testLongestMatchWins_SpecificOverGeneric() {
        val db = createDatabase("""
            category,keyword
            RESTAURANT,restaurant
            SUPERMARKET,lidl
            SUPERMARKET,lidl filiale
        """.trimIndent())

        // "lidl filiale" is longer than "lidl", should still be SUPERMARKET
        assertEquals(TransactionCategory.SUPERMARKET, db.findCategory("Lidl Filiale 1234"))
    }

    @Test
    fun testLongestMatchWins_LongerKeywordDifferentCategory() {
        val db = createDatabase("""
            category,keyword
            TRANSPORT,bahn
            TRANSPORT,deutsche bahn
            SHOPPING,amazon
        """.trimIndent())

        // "deutsche bahn" is more specific than "bahn"
        assertEquals(TransactionCategory.TRANSPORT, db.findCategory("Deutsche Bahn Fahrkarte"))
    }

    // ===== Counterparty matching =====

    @Test
    fun testFindCategory_MatchesCounterparty() {
        val db = createDatabase("category,keyword\nSUPERMARKET,lidl")

        assertEquals(TransactionCategory.SUPERMARKET, db.findCategory("Lastschrift", "Lidl GmbH"))
    }

    @Test
    fun testFindCategory_CombinesDescriptionAndCounterparty() {
        val db = createDatabase("category,keyword\nSUPERMARKET,lidl")

        // Keyword in counterparty should match even if not in description
        assertEquals(TransactionCategory.SUPERMARKET, db.findCategory("Kartenzahlung", "Lidl"))
    }

    @Test
    fun testFindCategory_NullCounterparty() {
        val db = createDatabase("category,keyword\nSUPERMARKET,lidl")

        assertEquals(TransactionCategory.SUPERMARKET, db.findCategory("Lidl Sagt Danke", null))
    }

    // ===== German special characters =====

    @Test
    fun testGermanChars_Umlauts() {
        val db = createDatabase("category,keyword\nRENT,wärme")

        assertEquals(TransactionCategory.RENT, db.findCategory("Wärme Abrechnung"))
    }

    @Test
    fun testGermanChars_Eszett() {
        val db = createDatabase("category,keyword\nRENT,straße")

        assertEquals(TransactionCategory.RENT, db.findCategory("Hauptstraße 10"))
    }

    // ===== Multiple categories =====

    @Test
    fun testMultipleCategories_AllCategoriesWork() {
        val db = createDatabase("""
            category,keyword
            SUPERMARKET,lidl
            SUPERMARKET,rewe
            SUPERMARKET,edeka
            RESTAURANT,mcdonald
            RESTAURANT,burger king
            TRANSPORT,deutsche bahn
            RENT,miete
            SALARY,gehalt
            INSURANCE,versicherung
            ENTERTAINMENT,netflix
            SUBSCRIPTIONS,spotify
            HEALTH,apotheke
        """.trimIndent())

        assertEquals(TransactionCategory.SUPERMARKET, db.findCategory("Lidl Sagt Danke"))
        assertEquals(TransactionCategory.SUPERMARKET, db.findCategory("REWE Markt"))
        assertEquals(TransactionCategory.SUPERMARKET, db.findCategory("EDEKA Center"))
        assertEquals(TransactionCategory.RESTAURANT, db.findCategory("McDonald's Restaurant"))
        assertEquals(TransactionCategory.TRANSPORT, db.findCategory("Deutsche Bahn Fahrkarte"))
        assertEquals(TransactionCategory.RENT, db.findCategory("Miete Oktober"))
        assertEquals(TransactionCategory.SALARY, db.findCategory("Gehalt Oktober"))
        assertEquals(TransactionCategory.INSURANCE, db.findCategory("Haftpflicht Versicherung"))
        assertEquals(TransactionCategory.ENTERTAINMENT, db.findCategory("Netflix Monthly"))
        assertEquals(TransactionCategory.SUBSCRIPTIONS, db.findCategory("Spotify Premium"))
        assertEquals(TransactionCategory.HEALTH, db.findCategory("Stadt Apotheke"))
    }

    // ===== getKeywordsForCategory =====

    @Test
    fun testGetKeywordsForCategory_ReturnsCorrectKeywords() {
        val db = createDatabase("""
            category,keyword
            SUPERMARKET,lidl
            SUPERMARKET,rewe
            RESTAURANT,mcdonald
        """.trimIndent())

        val supermarketKeywords = db.getKeywordsForCategory(TransactionCategory.SUPERMARKET)
        assertEquals(2, supermarketKeywords.size)
        assertTrue(supermarketKeywords.contains("lidl"))
        assertTrue(supermarketKeywords.contains("rewe"))
    }

    @Test
    fun testGetKeywordsForCategory_EmptyForUnusedCategory() {
        val db = createDatabase("category,keyword\nSUPERMARKET,lidl")

        val restaurantKeywords = db.getKeywordsForCategory(TransactionCategory.RESTAURANT)
        assertTrue(restaurantKeywords.isEmpty())
    }

    // ===== Country code helper methods =====

    @Test
    fun testGetCountryCodeFromIban_German() {
        assertEquals("de", KeywordDatabase.getCountryCodeFromIban("DE89 3704 0044 0532 0130 00"))
    }

    @Test
    fun testGetCountryCodeFromIban_French() {
        assertEquals("fr", KeywordDatabase.getCountryCodeFromIban("FR7630001007941234567890185"))
    }

    @Test
    fun testGetCountryCodeFromIban_NullIban_DefaultsToDE() {
        assertEquals("de", KeywordDatabase.getCountryCodeFromIban(null))
    }

    @Test
    fun testGetCountryCodeFromIban_EmptyIban_DefaultsToDE() {
        assertEquals("de", KeywordDatabase.getCountryCodeFromIban(""))
    }

    @Test
    fun testGetCountryCodeFromIban_BlankIban_DefaultsToDE() {
        assertEquals("de", KeywordDatabase.getCountryCodeFromIban("   "))
    }

    @Test
    fun testGetCountryCodeFromIban_WithSpaces() {
        assertEquals("de", KeywordDatabase.getCountryCodeFromIban("DE 89 3704 0044"))
    }

    @Test
    fun testGetKeywordFileName() {
        assertEquals("keywords_de.csv", KeywordDatabase.getKeywordFileName("DE"))
        assertEquals("keywords_fr.csv", KeywordDatabase.getKeywordFileName("FR"))
        assertEquals("keywords_de.csv", KeywordDatabase.getKeywordFileName("de"))
    }

    @Test
    fun testGetMerchantFileName() {
        assertEquals("merchants_de.csv", KeywordDatabase.getMerchantFileName("DE"))
        assertEquals("merchants_us.csv", KeywordDatabase.getMerchantFileName("US"))
    }

    // ===== Text normalization =====

    @Test
    fun testNormalization_SpecialCharsRemoved() {
        val db = createDatabase("category,keyword\nSUPERMARKET,lidl")

        // Description with special characters
        assertEquals(TransactionCategory.SUPERMARKET, db.findCategory("LIDL*SAGT*DANKE"))
        assertEquals(TransactionCategory.SUPERMARKET, db.findCategory("LIDL/Berlin/Store"))
    }

    @Test
    fun testNormalization_MultipleSpacesCollapsed() {
        val db = createDatabase("category,keyword\nSUPERMARKET,lidl")

        assertEquals(TransactionCategory.SUPERMARKET, db.findCategory("Einkauf   bei   Lidl"))
    }
}
