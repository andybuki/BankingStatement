package com.banking.statement.categorization

import com.banking.statement.parser.ParsedTransaction
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Tests for TransactionCategorizer service.
 * Validates the priority-based categorization logic:
 * 1) User overrides
 * 2) Keyword matching
 * 3) Amount-based rules for income
 * 4) Merchant database
 */
class TransactionCategorizerTest {

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

    // ===== Basic categorization tests =====

    @Test
    fun testCategorize_WithNoKeywords_ReturnsOther() {
        val categorizer = TransactionCategorizer()
        val transaction = createTransaction("Random Payment XYZ", -50.0)

        val result = categorizer.categorize(transaction)

        assertEquals(TransactionCategory.OTHER, result)
    }

    @Test
    fun testCategorize_PositiveAmount_LargeIncome_ReturnsSalary() {
        val categorizer = TransactionCategorizer()
        val transaction = createTransaction("Monthly Payment", 3500.0)

        val result = categorizer.categorize(transaction)

        assertEquals(TransactionCategory.SALARY, result)
    }

    @Test
    fun testCategorize_PositiveAmount_SmallIncome_ReturnsRefund() {
        val categorizer = TransactionCategorizer()
        val transaction = createTransaction("Refund from Amazon", 25.0)

        val result = categorizer.categorize(transaction)

        assertEquals(TransactionCategory.REFUND, result)
    }

    @Test
    fun testCategorize_SalaryThreshold_ExactBoundary() {
        val categorizer = TransactionCategorizer()

        // Exactly at threshold (300.0) should be REFUND (not > threshold)
        val atThreshold = createTransaction("Payment", 300.0)
        assertEquals(TransactionCategory.REFUND, categorizer.categorize(atThreshold))

        // Just above threshold should be SALARY
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
            createTransaction("Some Income", 500.0)  // Will be categorized as SALARY
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
            createTransaction("Salary", 3000.0),      // SALARY (income > 300)
            createTransaction("Small Refund", 50.0),  // REFUND (income <= 300)
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
