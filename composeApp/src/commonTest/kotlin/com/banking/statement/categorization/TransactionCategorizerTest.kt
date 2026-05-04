package com.banking.statement.categorization

import com.banking.statement.parser.ParsedTransaction
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for TransactionCategorizer service.
 * Validates the priority-based categorization logic:
 * 1) User overrides
 * 2) Strong transaction signals
 * 3) Extracted merchant lookup
 * 4) Keyword matching fallback
 */
class TransactionCategorizerTest {

    @AfterTest
    fun tearDown() {
        TransactionCategory.setKeywordDatabase(null)
    }

    private fun createTransaction(
        description: String,
        amount: Double,
        counterpartyName: String? = null,
        remittanceInfo: String? = null,
        rawText: String? = null
    ) = ParsedTransaction(
        bookingDate = LocalDate(2024, 1, 15),
        amount = amount,
        description = description,
        counterpartyName = counterpartyName,
        remittanceInfo = remittanceInfo,
        rawText = rawText
    )

    private fun installKeywords(csvContent: String) {
        val keywordDatabase = KeywordDatabaseOptimized().apply {
            loadFromCsv(csvContent, "de")
        }
        TransactionCategory.setKeywordDatabase(keywordDatabase)
    }

    // ===== Basic categorization tests =====

    @Test
    fun testCategorize_WithNoKeywords_ReturnsOther() {
        val categorizer = TransactionCategorizer()
        val transaction = createTransaction("Random Payment XYZ", -50.0)

        val result = categorizer.categorize(transaction)

        assertEquals(TransactionCategory.OTHER, result)
    }

    @Test
    fun testCategorizeWithDetails_WithNoKeywords_ReturnsUnknownSource() {
        val categorizer = TransactionCategorizer()
        val transaction = createTransaction("Random Payment XYZ", -50.0)

        val result = categorizer.categorizeWithDetails(transaction)

        assertEquals(TransactionCategory.OTHER, result.category)
        assertEquals(CategorizationSource.UNKNOWN, result.source)
        assertEquals(0.0, result.confidence)
    }

    @Test
    fun testCategorize_PositiveAmount_LargeIncomeFallback_ReturnsSalary() {
        val categorizer = TransactionCategorizer()
        val transaction = createTransaction("Monthly Payment", 3500.0)

        val result = categorizer.categorize(transaction)

        assertEquals(TransactionCategory.SALARY, result)
    }

    @Test
    fun testCategorizeWithDetails_PositiveAmount_LargeIncomeFallback_IsLowConfidenceSignalRule() {
        val categorizer = TransactionCategorizer()
        val transaction = createTransaction("Monthly Payment", 3500.0)

        val result = categorizer.categorizeWithDetails(transaction)

        assertEquals(TransactionCategory.SALARY, result.category)
        assertEquals(CategorizationSource.SIGNAL_RULE, result.source)
        assertEquals(0.55, result.confidence)
    }

    @Test
    fun testCategorize_PositiveAmount_SmallIncome_ReturnsRefund() {
        val categorizer = TransactionCategorizer()
        val transaction = createTransaction("Refund from Amazon", 25.0)

        val result = categorizer.categorize(transaction)

        assertEquals(TransactionCategory.REFUND, result)
    }

    @Test
    fun testCategorizeWithDetails_PositiveAmount_SmallIncome_ReturnsRefundFallback() {
        val categorizer = TransactionCategorizer()
        val transaction = createTransaction("Refund from Amazon", 25.0)

        val result = categorizer.categorizeWithDetails(transaction)

        assertEquals(TransactionCategory.REFUND, result.category)
        assertEquals(CategorizationSource.SIGNAL_RULE, result.source)
        assertEquals(0.65, result.confidence)
    }

    @Test
    fun testCategorize_SalaryThreshold_ExactBoundary() {
        val categorizer = TransactionCategorizer()

        // Exactly at threshold (300.0) should be REFUND (not > threshold)
        val atThreshold = createTransaction("Payment", 300.0)
        assertEquals(TransactionCategory.REFUND, categorizer.categorize(atThreshold))

        // Just above threshold should be SALARY fallback
        val aboveThreshold = createTransaction("Payment", 300.01)
        assertEquals(TransactionCategory.SALARY, categorizer.categorize(aboveThreshold))
    }

    @Test
    fun testCategorize_ZeroAmount_ReturnsOther() {
        val categorizer = TransactionCategorizer()
        val transaction = createTransaction("Zero Amount Transaction", 0.0)

        val result = categorizer.categorize(transaction)

        assertEquals(TransactionCategory.OTHER, result)
    }

    @Test
    fun testKeywordFallback_ExpenseWithoutMerchant_UsesKeywordSource() {
        installKeywords("category,keyword\nINSURANCE,versicherung")

        val categorizer = TransactionCategorizer()
        val transaction = createTransaction("Allianz Versicherung Beitrag", -42.0)

        val result = categorizer.categorizeWithDetails(transaction)

        assertEquals(TransactionCategory.INSURANCE, result.category)
        assertEquals(CategorizationSource.KEYWORD, result.source)
        assertEquals(0.75, result.confidence)
    }

    @Test
    fun testPositiveRentLikeIncome_IsRefundNotSalary() {
        installKeywords("category,keyword\nRENT,miete")

        val categorizer = TransactionCategorizer()
        val transaction = createTransaction("Gutschrift Stanislav Kuzmin Miete berliner allee 121c", 1660.0)

        val result = categorizer.categorizeWithDetails(transaction)

        assertEquals(TransactionCategory.REFUND, result.category)
        assertEquals(CategorizationSource.SIGNAL_RULE, result.source)
    }

    @Test
    fun testSmallSalaryLikeIncome_IsSalaryDespiteAmount() {
        val categorizer = TransactionCategorizer()
        val transaction = createTransaction("Gehalt/Rente Stiftung Oper in Berlin GEHALT 2/25", 16.0)

        val result = categorizer.categorizeWithDetails(transaction)

        assertEquals(TransactionCategory.SALARY, result.category)
        assertEquals(CategorizationSource.SIGNAL_RULE, result.source)
        assertEquals(0.95, result.confidence)
    }

    @Test
    fun testNegativeRentLikeExpense_IsRent() {
        val categorizer = TransactionCategorizer()
        val transaction = createTransaction("Lastschrift GEHAG GmbH 1194748002015 MIETE03/25", -1033.34)

        val result = categorizer.categorizeWithDetails(transaction)

        assertEquals(TransactionCategory.RENT, result.category)
        assertEquals(CategorizationSource.SIGNAL_RULE, result.source)
    }

    @Test
    fun testNegativeTransferLikeExpense_IsTransfer() {
        val categorizer = TransactionCategorizer()
        val transaction = createTransaction("Ueberweisung Andrey Buchmann Extra Konto", -3000.0)

        val result = categorizer.categorizeWithDetails(transaction)

        assertEquals(TransactionCategory.TRANSFER, result.category)
        assertEquals(CategorizationSource.SIGNAL_RULE, result.source)
    }

    @Test
    fun testCashWithdrawal_IsTransfer() {
        val categorizer = TransactionCategorizer()
        val transaction = createTransaction("Lastschrift Bargeldauszahlung VISA Card BERLINER SPARKASSE", -200.0)

        val result = categorizer.categorizeWithDetails(transaction)

        assertEquals(TransactionCategory.TRANSFER, result.category)
        assertEquals(CategorizationSource.SIGNAL_RULE, result.source)
    }

    // ===== PDF-derived PayPal / VISA effective merchant tests =====

    @Test
    fun testPaypalWolt_UsesExtractedMerchantForRestaurantKeyword() {
        installKeywords("category,keyword\nRESTAURANT,wolt")

        val categorizer = TransactionCategorizer()
        val transaction = createTransaction(
            description = "Lastschrift PayPal Europe S.a.r.l. et Cie S.C.A 1040547284177/. Wolt, Ihr Einkauf bei Wolt",
            amount = -18.57
        )

        val result = categorizer.categorizeWithDetails(transaction)

        assertEquals(TransactionCategory.RESTAURANT, result.category)
        assertEquals(CategorizationSource.KEYWORD, result.source)
        assertEquals("Wolt", result.matchedValue)
    }

    @Test
    fun testPaypalSpotify_UsesExtractedMerchantForSubscriptionKeyword() {
        installKeywords("category,keyword\nSUBSCRIPTIONS,spotify")

        val categorizer = TransactionCategorizer()
        val transaction = createTransaction(
            description = "Lastschrift PayPal Europe S.a.r.l. et Cie S.C.A 1040595805955/PP.7706.PP/. Spotify AB, Ihr Einkauf bei Spotify AB",
            amount = -10.99
        )

        val result = categorizer.categorizeWithDetails(transaction)

        assertEquals(TransactionCategory.SUBSCRIPTIONS, result.category)
        assertEquals(CategorizationSource.KEYWORD, result.source)
        assertEquals("Spotify", result.matchedValue)
    }

    @Test
    fun testPaypalBooking_UsesExtractedMerchantForTravelKeyword() {
        installKeywords("category,keyword\nTRAVEL,booking")

        val categorizer = TransactionCategorizer()
        val transaction = createTransaction(
            description = "Lastschrift PayPal Europe S.a.r.l. et Cie S.C.A 1040587445790/PP.7706.PP/. Booking.com BV, Ihr Einkauf bei Booking.com BV",
            amount = -360.0
        )

        val result = categorizer.categorizeWithDetails(transaction)

        assertEquals(TransactionCategory.TRAVEL, result.category)
        assertEquals(CategorizationSource.KEYWORD, result.source)
        assertEquals("Booking com", result.matchedValue)
    }

    @Test
    fun testPaypalPreply_UsesExtractedMerchantForEducationKeyword() {
        installKeywords("category,keyword\nEDUCATION,preply")

        val categorizer = TransactionCategorizer()
        val transaction = createTransaction(
            description = "Lastschrift PayPal Europe S.a.r.l. et Cie S.C.A 1040653081126/PP.7706.PP/. Preply, Inc., Ihr Einkauf bei Preply, Inc.",
            amount = -78.40
        )

        val result = categorizer.categorizeWithDetails(transaction)

        assertEquals(TransactionCategory.EDUCATION, result.category)
        assertEquals(CategorizationSource.KEYWORD, result.source)
        assertEquals("Preply", result.matchedValue)
    }

    @Test
    fun testVisaLidl_UsesExtractedMerchantForSupermarketKeyword() {
        installKeywords("category,keyword\nSUPERMARKET,lidl")

        val categorizer = TransactionCategorizer()
        val transaction = createTransaction(
            description = "Lastschrift VISA LIDL SAGT DANKE NR XXXX 0027 BERLIN DE KAUFUMSATZ 27.02 4.97 192846 ARN74830725058313356752734",
            amount = -4.97
        )

        val result = categorizer.categorizeWithDetails(transaction)

        assertEquals(TransactionCategory.SUPERMARKET, result.category)
        assertEquals(CategorizationSource.KEYWORD, result.source)
        assertEquals("LIDL", result.matchedValue)
    }

    @Test
    fun testVisaEasyJet_UsesExtractedMerchantForTravelKeyword() {
        installKeywords("category,keyword\nTRAVEL,easyjet")

        val categorizer = TransactionCategorizer()
        val transaction = createTransaction(
            description = "Lastschrift VISA EASYJET AIR K981QLN NR XXXX 0035 FRANKFURT DE KAUFUMSATZ 26.03 324.09 212514",
            amount = -324.09
        )

        val result = categorizer.categorizeWithDetails(transaction)

        assertEquals(TransactionCategory.TRAVEL, result.category)
        assertEquals(CategorizationSource.KEYWORD, result.source)
        assertEquals("EASYJET AIR K981QLN", result.matchedValue)
    }

    // ===== CategoryStats tests =====

    @Test
    fun testGetCategoryStats_EmptyList_ReturnsEmptyMap() {
        val categorizer = TransactionCategorizer()

        val stats = categorizer.getCategoryStats(emptyList())

        assertEquals(0, stats.size)
    }

    @Test
    fun testGetCategoryStats_SingleTransaction() {
        val categorizer = TransactionCategorizer()
        val transactions = listOf(
            createTransaction("Some Income", 500.0)  // Will be categorized as SALARY fallback
        )

        val stats = categorizer.getCategoryStats(transactions)

        val salaryStats = stats[TransactionCategory.SALARY]
        assertEquals(1, salaryStats?.count)
        assertEquals(500.0, salaryStats?.totalAmount)
        assertEquals(500.0, salaryStats?.income)
        assertEquals(0.0, salaryStats?.expenses)
    }

    @Test
    fun testGetCategoryStats_MixedTransactions() {
        val categorizer = TransactionCategorizer()
        val transactions = listOf(
            createTransaction("Salary", 3000.0),      // SALARY fallback (income > 300)
            createTransaction("Small Refund", 50.0),  // REFUND fallback (income <= 300)
            createTransaction("Some expense", -100.0) // OTHER (expense, no keywords)
        )

        val stats = categorizer.getCategoryStats(transactions)

        // Check SALARY stats
        val salaryStats = stats[TransactionCategory.SALARY]
        assertEquals(1, salaryStats?.count)
        assertEquals(3000.0, salaryStats?.income)

        // Check REFUND stats
        val refundStats = stats[TransactionCategory.REFUND]
        assertEquals(1, refundStats?.count)
        assertEquals(50.0, refundStats?.income)

        // Check OTHER stats (expense)
        val otherStats = stats[TransactionCategory.OTHER]
        assertEquals(1, otherStats?.count)
        assertEquals(-100.0, otherStats?.expenses)
    }

    @Test
    fun testGetCategoryStats_CalculatesAverageAmount() {
        val categorizer = TransactionCategorizer()
        val transactions = listOf(
            createTransaction("Salary 1", 1000.0),
            createTransaction("Salary 2", 2000.0),
            createTransaction("Salary 3", 3000.0)
        )

        val stats = categorizer.getCategoryStats(transactions)
        val salaryStats = stats[TransactionCategory.SALARY]!!

        assertEquals(3, salaryStats.count)
        assertEquals(6000.0, salaryStats.totalAmount)
        assertEquals(2000.0, salaryStats.averageAmount)
    }

    // ===== CategorizeAll tests =====

    @Test
    fun testCategorizeAll_ReturnsCorrectPairs() {
        val categorizer = TransactionCategorizer()
        val transactions = listOf(
            createTransaction("Big Income", 1000.0),
            createTransaction("Small Income", 50.0)
        )

        val results = categorizer.categorizeAll(transactions)

        assertEquals(2, results.size)
        assertEquals(TransactionCategory.SALARY, results[0].second)
        assertEquals(TransactionCategory.REFUND, results[1].second)
    }

    @Test
    fun testCategorizeAllWithDetails_ReturnsDetailedResults() {
        val categorizer = TransactionCategorizer()
        val transactions = listOf(
            createTransaction("Big Income", 1000.0),
            createTransaction("Unknown Expense", -50.0)
        )

        val results = categorizer.categorizeAllWithDetails(transactions)

        assertEquals(2, results.size)
        assertEquals(TransactionCategory.SALARY, results[0].second.category)
        assertEquals(CategorizationSource.SIGNAL_RULE, results[0].second.source)
        assertEquals(TransactionCategory.OTHER, results[1].second.category)
        assertEquals(CategorizationSource.UNKNOWN, results[1].second.source)
    }

    @Test
    fun testCategorizeAll_PreservesTransactionOrder() {
        val categorizer = TransactionCategorizer()
        val transactions = listOf(
            createTransaction("First", 100.0),
            createTransaction("Second", 200.0),
            createTransaction("Third", 500.0)
        )

        val results = categorizer.categorizeAll(transactions)

        assertEquals("First", results[0].first.description)
        assertEquals("Second", results[1].first.description)
        assertEquals("Third", results[2].first.description)
    }

    // ===== CategoryStats data class tests =====

    @Test
    fun testCategoryStats_AverageAmount_WithZeroCount() {
        val stats = CategoryStats(
            count = 0,
            totalAmount = 0.0,
            expenses = 0.0,
            income = 0.0
        )

        assertEquals(0.0, stats.averageAmount)
    }

    @Test
    fun testCategoryStats_AverageAmount_WithPositiveCount() {
        val stats = CategoryStats(
            count = 4,
            totalAmount = 100.0,
            expenses = -50.0,
            income = 150.0
        )

        assertEquals(25.0, stats.averageAmount)
    }

    // ===== Salary threshold constant test =====

    @Test
    fun testSalaryThreshold_IsCorrectValue() {
        assertEquals(300.0, TransactionCategorizer.SALARY_THRESHOLD)
    }
}
