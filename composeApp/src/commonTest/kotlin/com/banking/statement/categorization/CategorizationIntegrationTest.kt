package com.banking.statement.categorization

import com.banking.statement.parser.ParsedTransaction
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Integration tests for the categorization pipeline.
 * Tests the full priority chain: keywords -> amount rules -> fallback.
 * Also tests MerchantDatabase.categoryFromCode and TransactionCategory properties.
 */
class CategorizationIntegrationTest {

    private fun createTransaction(
        description: String,
        amount: Double,
        counterpartyName: String? = null
    ) = ParsedTransaction(
        bookingDate = LocalDate(2024, 1, 15),
        amount = amount,
        description = description,
        counterpartyName = counterpartyName
    )

    // ===== Priority: Keywords over amount rules =====

    @Test
    fun testPriority_KeywordMatchOverridesAmountRule() {
        // Set up keyword database
        val keywordDb = KeywordDatabase()
        keywordDb.loadFromCsv("category,keyword\nREFUND,erstattung", "de")
        TransactionCategory.setKeywordDatabase(keywordDb)

        try {
            val categorizer = TransactionCategorizer()

            // Large income with keyword "erstattung" -> should be REFUND (keyword), not SALARY (amount rule)
            val transaction = createTransaction("Erstattung Versicherung", 500.0)
            val result = categorizer.categorize(transaction)

            assertEquals(TransactionCategory.REFUND, result)
        } finally {
            TransactionCategory.setKeywordDatabase(null)
        }
    }

    @Test
    fun testPriority_KeywordMatchOverridesDefaultOther() {
        val keywordDb = KeywordDatabase()
        keywordDb.loadFromCsv("category,keyword\nSUPERMARKET,lidl", "de")
        TransactionCategory.setKeywordDatabase(keywordDb)

        try {
            val categorizer = TransactionCategorizer()

            val transaction = createTransaction("Lidl Sagt Danke", -25.50)
            val result = categorizer.categorize(transaction)

            assertEquals(TransactionCategory.SUPERMARKET, result)
        } finally {
            TransactionCategory.setKeywordDatabase(null)
        }
    }

    // ===== Amount-based rules =====

    @Test
    fun testAmountRule_LargeIncome_Salary() {
        val categorizer = TransactionCategorizer()
        val transaction = createTransaction("Monthly Payment", 3500.0)

        assertEquals(TransactionCategory.SALARY, categorizer.categorize(transaction))
    }

    @Test
    fun testAmountRule_SmallIncome_Refund() {
        val categorizer = TransactionCategorizer()
        val transaction = createTransaction("Payment Received", 50.0)

        assertEquals(TransactionCategory.REFUND, categorizer.categorize(transaction))
    }

    @Test
    fun testAmountRule_ExactThreshold_IsRefund() {
        val categorizer = TransactionCategorizer()

        assertEquals(
            TransactionCategory.REFUND,
            categorizer.categorize(createTransaction("Payment", 300.0))
        )
    }

    @Test
    fun testAmountRule_JustAboveThreshold_IsSalary() {
        val categorizer = TransactionCategorizer()

        assertEquals(
            TransactionCategory.SALARY,
            categorizer.categorize(createTransaction("Payment", 300.01))
        )
    }

    @Test
    fun testAmountRule_ZeroAmount_IsOther() {
        val categorizer = TransactionCategorizer()

        assertEquals(
            TransactionCategory.OTHER,
            categorizer.categorize(createTransaction("Zero", 0.0))
        )
    }

    @Test
    fun testAmountRule_NegativeAmount_NoKeywords_IsOther() {
        val categorizer = TransactionCategorizer()

        assertEquals(
            TransactionCategory.OTHER,
            categorizer.categorize(createTransaction("Random Expense", -50.0))
        )
    }

    // ===== CategorizeAll with real keywords =====

    @Test
    fun testCategorizeAll_WithKeywords_MixedTransactions() {
        val keywordDb = KeywordDatabase()
        keywordDb.loadFromCsv("""
            category,keyword
            SUPERMARKET,lidl
            SUPERMARKET,rewe
            RENT,miete
            TRANSPORT,deutsche bahn
        """.trimIndent(), "de")
        TransactionCategory.setKeywordDatabase(keywordDb)

        try {
            val categorizer = TransactionCategorizer()
            val transactions = listOf(
                createTransaction("Lidl Sagt Danke", -25.50),
                createTransaction("Miete Oktober", -800.0),
                createTransaction("Gehalt", 3500.0),          // No keyword, uses amount rule -> SALARY
                createTransaction("Random Expense", -15.0),   // No keyword, negative -> OTHER
                createTransaction("Deutsche Bahn ICE", -89.90),
                createTransaction("Small return", 10.0)       // No keyword, small income -> REFUND
            )

            val results = categorizer.categorizeAll(transactions)

            assertEquals(6, results.size)
            assertEquals(TransactionCategory.SUPERMARKET, results[0].second)
            assertEquals(TransactionCategory.RENT, results[1].second)
            assertEquals(TransactionCategory.SALARY, results[2].second)
            assertEquals(TransactionCategory.OTHER, results[3].second)
            assertEquals(TransactionCategory.TRANSPORT, results[4].second)
            assertEquals(TransactionCategory.REFUND, results[5].second)
        } finally {
            TransactionCategory.setKeywordDatabase(null)
        }
    }

    // ===== Category stats with real keywords =====

    @Test
    fun testCategoryStats_WithKeywords() {
        val keywordDb = KeywordDatabase()
        keywordDb.loadFromCsv("""
            category,keyword
            SUPERMARKET,lidl
            SUPERMARKET,rewe
        """.trimIndent(), "de")
        TransactionCategory.setKeywordDatabase(keywordDb)

        try {
            val categorizer = TransactionCategorizer()
            val transactions = listOf(
                createTransaction("Lidl Sagt Danke", -25.50),
                createTransaction("REWE Markt", -45.00),
                createTransaction("Lidl Filiale", -12.30)
            )

            val stats = categorizer.getCategoryStats(transactions)
            val supermarketStats = stats[TransactionCategory.SUPERMARKET]

            assertNotEquals(null, supermarketStats)
            assertEquals(3, supermarketStats!!.count)
            assertEquals(-82.80, supermarketStats.totalAmount, 0.01)
            assertEquals(-82.80, supermarketStats.expenses, 0.01)
            assertEquals(0.0, supermarketStats.income)
        } finally {
            TransactionCategory.setKeywordDatabase(null)
        }
    }

    // ===== MerchantDatabase.categoryFromCode =====

    @Test
    fun testCategoryFromCode_AllMappings() {
        assertEquals(TransactionCategory.RESTAURANT, MerchantDatabase.categoryFromCode("fd"))
        assertEquals(TransactionCategory.ENTERTAINMENT, MerchantDatabase.categoryFromCode("en"))
        assertEquals(TransactionCategory.SUBSCRIPTIONS, MerchantDatabase.categoryFromCode("ft"))
        assertEquals(TransactionCategory.HEALTH, MerchantDatabase.categoryFromCode("md"))
        assertEquals(TransactionCategory.SUPERMARKET, MerchantDatabase.categoryFromCode("sm"))
        assertEquals(TransactionCategory.SHOPPING, MerchantDatabase.categoryFromCode("el"))
        assertEquals(TransactionCategory.SHOPPING, MerchantDatabase.categoryFromCode("be"))
        assertEquals(TransactionCategory.SHOPPING, MerchantDatabase.categoryFromCode("cl"))
        assertEquals(TransactionCategory.RESTAURANT, MerchantDatabase.categoryFromCode("bk"))
        assertEquals(TransactionCategory.TRAVEL, MerchantDatabase.categoryFromCode("tr"))
    }

    @Test
    fun testCategoryFromCode_UnknownCode_ReturnsNull() {
        assertEquals(null, MerchantDatabase.categoryFromCode("xx"))
        assertEquals(null, MerchantDatabase.categoryFromCode(""))
        assertEquals(null, MerchantDatabase.categoryFromCode("unknown"))
    }

    // ===== TransactionCategory enum properties =====

    @Test
    fun testTransactionCategory_AllHaveDisplayNames() {
        for (category in TransactionCategory.entries) {
            assertTrue(category.displayName.isNotBlank(), "$category has empty displayName")
            assertTrue(category.displayNameDe.isNotBlank(), "$category has empty displayNameDe")
        }
    }

    @Test
    fun testTransactionCategory_AllHaveIcons() {
        for (category in TransactionCategory.entries) {
            assertTrue(category.icon.isNotBlank(), "$category has empty icon")
        }
    }

    @Test
    fun testTransactionCategory_AllHaveColors() {
        for (category in TransactionCategory.entries) {
            assertTrue(category.color.startsWith("#"), "$category color doesn't start with #")
            assertEquals(7, category.color.length, "$category color is not a valid hex color")
        }
    }

    @Test
    fun testTransactionCategory_Count() {
        assertEquals(17, TransactionCategory.entries.size)
    }

    @Test
    fun testTransactionCategory_GetLocalizedName() {
        assertEquals("Miete & Nebenkosten", TransactionCategory.RENT.getLocalizedName(useGerman = true))
        assertEquals("Rent & Utilities", TransactionCategory.RENT.getLocalizedName(useGerman = false))
    }

    // ===== TransactionCategory.categorize without DB =====

    @Test
    fun testCategorize_NoKeywordDatabase_ReturnsOther() {
        // Ensure no database is set
        TransactionCategory.setKeywordDatabase(null)

        assertEquals(TransactionCategory.OTHER, TransactionCategory.categorize("Lidl Sagt Danke"))
    }

    @Test
    fun testCategorize_WithKeywordDatabase_FindsMatch() {
        val keywordDb = KeywordDatabase()
        keywordDb.loadFromCsv("category,keyword\nSUPERMARKET,lidl", "de")
        TransactionCategory.setKeywordDatabase(keywordDb)

        try {
            assertEquals(TransactionCategory.SUPERMARKET, TransactionCategory.categorize("Lidl Sagt Danke"))
        } finally {
            TransactionCategory.setKeywordDatabase(null)
        }
    }
}
