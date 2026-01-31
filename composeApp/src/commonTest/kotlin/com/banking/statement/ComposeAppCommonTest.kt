package com.banking.statement

import com.banking.statement.categorization.TransactionCategory
import com.banking.statement.ui.MerchantHistory
import com.banking.statement.ui.MerchantMonthlyData
import com.banking.statement.ui.calculateMerchantHistory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * General tests for common banking statement functionality.
 */
class ComposeAppCommonTest {

    // ===== MerchantHistory calculation tests =====

    @Test
    fun testCalculateMerchantHistory_EmptyData_ReturnsEmptyList() {
        val result = calculateMerchantHistory(emptyList())

        assertTrue(result.isEmpty())
    }

    @Test
    fun testCalculateMerchantHistory_SingleMerchant() {
        val data = listOf(
            MerchantMonthlyData("2024-01", "Lidl", TransactionCategory.SUPERMARKET, -100.0, 5),
            MerchantMonthlyData("2024-02", "Lidl", TransactionCategory.SUPERMARKET, -150.0, 7)
        )

        val result = calculateMerchantHistory(data)

        assertEquals(1, result.size)
        val lidlHistory = result[0]
        assertEquals("Lidl", lidlHistory.merchantName)
        assertEquals(TransactionCategory.SUPERMARKET, lidlHistory.category)
        assertEquals(-250.0, lidlHistory.totalSpending)
        assertEquals(12, lidlHistory.totalTransactions)
        assertEquals(-125.0, lidlHistory.averageMonthlySpending)
    }

    @Test
    fun testCalculateMerchantHistory_MultipleMerchants() {
        val data = listOf(
            MerchantMonthlyData("2024-01", "Lidl", TransactionCategory.SUPERMARKET, -100.0, 5),
            MerchantMonthlyData("2024-01", "Edeka", TransactionCategory.SUPERMARKET, -200.0, 8),
            MerchantMonthlyData("2024-02", "Lidl", TransactionCategory.SUPERMARKET, -150.0, 7),
            MerchantMonthlyData("2024-02", "Edeka", TransactionCategory.SUPERMARKET, -180.0, 6)
        )

        val result = calculateMerchantHistory(data, topN = 10)

        assertEquals(2, result.size)
    }

    @Test
    fun testCalculateMerchantHistory_TopNLimit() {
        val data = listOf(
            MerchantMonthlyData("2024-01", "Lidl", TransactionCategory.SUPERMARKET, -100.0, 5),
            MerchantMonthlyData("2024-01", "Edeka", TransactionCategory.SUPERMARKET, -200.0, 8),
            MerchantMonthlyData("2024-01", "REWE", TransactionCategory.SUPERMARKET, -150.0, 6),
            MerchantMonthlyData("2024-01", "Aldi", TransactionCategory.SUPERMARKET, -80.0, 4)
        )

        val result = calculateMerchantHistory(data, topN = 2)

        assertEquals(2, result.size)
    }

    @Test
    fun testCalculateMerchantHistory_SortsByTotalSpending() {
        val data = listOf(
            MerchantMonthlyData("2024-01", "Small", TransactionCategory.SUPERMARKET, -50.0, 2),
            MerchantMonthlyData("2024-01", "Large", TransactionCategory.SUPERMARKET, -500.0, 15),
            MerchantMonthlyData("2024-01", "Medium", TransactionCategory.SUPERMARKET, -200.0, 8)
        )

        val result = calculateMerchantHistory(data)

        // Should be sorted by spending (most negative first)
        assertEquals("Large", result[0].merchantName)
        assertEquals("Medium", result[1].merchantName)
        assertEquals("Small", result[2].merchantName)
    }

    @Test
    fun testCalculateMerchantHistory_MonthlySpendingSortedByDate() {
        val data = listOf(
            MerchantMonthlyData("2024-01", "Lidl", TransactionCategory.SUPERMARKET, -100.0, 5),
            MerchantMonthlyData("2024-03", "Lidl", TransactionCategory.SUPERMARKET, -120.0, 6),
            MerchantMonthlyData("2024-02", "Lidl", TransactionCategory.SUPERMARKET, -110.0, 5)
        )

        val result = calculateMerchantHistory(data)
        val lidlHistory = result[0]

        // Monthly spending should be sorted by month (descending)
        assertEquals(3, lidlHistory.monthlySpending.size)
        assertEquals("2024-03", lidlHistory.monthlySpending[0].month)
        assertEquals("2024-02", lidlHistory.monthlySpending[1].month)
        assertEquals("2024-01", lidlHistory.monthlySpending[2].month)
    }

    // ===== TransactionCategory tests =====

    @Test
    fun testTransactionCategory_AllCategoriesHaveNames() {
        TransactionCategory.entries.forEach { category ->
            assertTrue(category.displayName.isNotBlank(), "Category ${category.name} should have displayName")
            assertTrue(category.displayNameDe.isNotBlank(), "Category ${category.name} should have displayNameDe")
        }
    }

    @Test
    fun testTransactionCategory_AllCategoriesHaveIcons() {
        TransactionCategory.entries.forEach { category ->
            assertTrue(category.icon.isNotBlank(), "Category ${category.name} should have icon")
        }
    }

    @Test
    fun testTransactionCategory_AllCategoriesHaveColors() {
        TransactionCategory.entries.forEach { category ->
            assertTrue(category.color.isNotBlank(), "Category ${category.name} should have color")
            assertTrue(category.color.startsWith("#"), "Category ${category.name} color should be hex format")
        }
    }

    @Test
    fun testTransactionCategory_Count() {
        // Should have exactly 17 categories as documented
        assertEquals(17, TransactionCategory.entries.size)
    }

    @Test
    fun testTransactionCategory_GetLocalizedName_German() {
        val rent = TransactionCategory.RENT
        assertEquals("Miete & Nebenkosten", rent.getLocalizedName(useGerman = true))
    }

    @Test
    fun testTransactionCategory_GetLocalizedName_English() {
        val rent = TransactionCategory.RENT
        assertEquals("Rent & Utilities", rent.getLocalizedName(useGerman = false))
    }

    @Test
    fun testTransactionCategory_Categorize_WithoutDatabase_ReturnsOther() {
        // Without keyword database, should return OTHER
        TransactionCategory.setKeywordDatabase(null)

        val result = TransactionCategory.categorize("Random description")

        assertEquals(TransactionCategory.OTHER, result)
    }
}