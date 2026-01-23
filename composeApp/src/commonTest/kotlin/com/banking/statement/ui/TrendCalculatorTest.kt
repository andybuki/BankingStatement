package com.banking.statement.ui

import com.banking.statement.categorization.TransactionCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for TrendCalculator.
 * Validates spending trend calculations between months.
 */
class TrendCalculatorTest {

    // ===== Empty data tests =====

    @Test
    fun testCalculateTrends_EmptyData_ReturnsEmptyMap() {
        val result = TrendCalculator.calculateTrends(emptyList())

        assertTrue(result.isEmpty())
    }

    @Test
    fun testCalculateTrends_SingleMonth_ReturnsEmptyMap() {
        // Need at least 2 months for trend calculation
        val data = listOf(
            Triple("2024-01", "SUPERMARKET", -500.0)
        )

        val result = TrendCalculator.calculateTrends(data)

        assertTrue(result.isEmpty())
    }

    // ===== Trend direction tests =====

    @Test
    fun testCalculateTrends_SpendingIncrease_DirectionUP() {
        // More negative = more spending = UP trend
        val data = listOf(
            Triple("2024-01", "SUPERMARKET", -100.0),  // Previous: €100 spent
            Triple("2024-02", "SUPERMARKET", -150.0)   // Current: €150 spent (+50%)
        )

        val result = TrendCalculator.calculateTrends(data)
        val trend = result[TransactionCategory.SUPERMARKET]!!

        assertEquals(SpendingTrend.TrendDirection.UP, trend.direction)
        assertEquals(-150.0, trend.currentAmount)
        assertEquals(-100.0, trend.previousAmount)
    }

    @Test
    fun testCalculateTrends_SpendingDecrease_DirectionDOWN() {
        // Less negative = less spending = DOWN trend
        val data = listOf(
            Triple("2024-01", "SUPERMARKET", -200.0),  // Previous: €200 spent
            Triple("2024-02", "SUPERMARKET", -100.0)   // Current: €100 spent (-50%)
        )

        val result = TrendCalculator.calculateTrends(data)
        val trend = result[TransactionCategory.SUPERMARKET]!!

        assertEquals(SpendingTrend.TrendDirection.DOWN, trend.direction)
    }

    @Test
    fun testCalculateTrends_StableSpending_DirectionSTABLE() {
        // Less than 5% change = STABLE
        val data = listOf(
            Triple("2024-01", "SUPERMARKET", -100.0),  // Previous
            Triple("2024-02", "SUPERMARKET", -103.0)   // Current: +3% change
        )

        val result = TrendCalculator.calculateTrends(data)
        val trend = result[TransactionCategory.SUPERMARKET]!!

        assertEquals(SpendingTrend.TrendDirection.STABLE, trend.direction)
    }

    @Test
    fun testCalculateTrends_ExactlyAt5Percent_IsStable() {
        // 5% change boundary should be STABLE (< 5f check)
        val data = listOf(
            Triple("2024-01", "SUPERMARKET", -100.0),
            Triple("2024-02", "SUPERMARKET", -104.99)  // ~5% change
        )

        val result = TrendCalculator.calculateTrends(data)
        val trend = result[TransactionCategory.SUPERMARKET]!!

        assertEquals(SpendingTrend.TrendDirection.STABLE, trend.direction)
    }

    // ===== Change amount and percentage tests =====

    @Test
    fun testCalculateTrends_CalculatesChangeAmount() {
        val data = listOf(
            Triple("2024-01", "SUPERMARKET", -100.0),
            Triple("2024-02", "SUPERMARKET", -150.0)
        )

        val result = TrendCalculator.calculateTrends(data)
        val trend = result[TransactionCategory.SUPERMARKET]!!

        assertEquals(-50.0, trend.changeAmount)  // -150 - (-100) = -50
    }

    @Test
    fun testCalculateTrends_CalculatesChangePercentage() {
        val data = listOf(
            Triple("2024-01", "SUPERMARKET", -100.0),
            Triple("2024-02", "SUPERMARKET", -150.0)
        )

        val result = TrendCalculator.calculateTrends(data)
        val trend = result[TransactionCategory.SUPERMARKET]!!

        // (-150 - (-100)) / 100 * 100 = -50%
        assertTrue(trend.changePercentage < 0)
        assertTrue(trend.changePercentage > -55f && trend.changePercentage < -45f)
    }

    @Test
    fun testCalculateTrends_NewCategory_100PercentChange() {
        // When previous amount is 0, new category = 100% change
        val data = listOf(
            Triple("2024-01", "RESTAURANT", 0.0),
            Triple("2024-02", "SUPERMARKET", -100.0)
        )

        val result = TrendCalculator.calculateTrends(data)
        val trend = result[TransactionCategory.SUPERMARKET]!!

        assertEquals(100f, trend.changePercentage)
    }

    // ===== Multiple categories tests =====

    @Test
    fun testCalculateTrends_MultipleCategories() {
        val data = listOf(
            // Supermarket: increase
            Triple("2024-01", "SUPERMARKET", -100.0),
            Triple("2024-02", "SUPERMARKET", -150.0),
            // Restaurant: decrease
            Triple("2024-01", "RESTAURANT", -200.0),
            Triple("2024-02", "RESTAURANT", -100.0),
            // Transport: stable
            Triple("2024-01", "TRANSPORT", -50.0),
            Triple("2024-02", "TRANSPORT", -51.0)
        )

        val result = TrendCalculator.calculateTrends(data)

        assertEquals(3, result.size)
        assertEquals(SpendingTrend.TrendDirection.UP, result[TransactionCategory.SUPERMARKET]?.direction)
        assertEquals(SpendingTrend.TrendDirection.DOWN, result[TransactionCategory.RESTAURANT]?.direction)
        assertEquals(SpendingTrend.TrendDirection.STABLE, result[TransactionCategory.TRANSPORT]?.direction)
    }

    @Test
    fun testCalculateTrends_AggregatesMultipleTransactionsSameCategory() {
        val data = listOf(
            // Multiple supermarket entries in January
            Triple("2024-01", "SUPERMARKET", -50.0),
            Triple("2024-01", "SUPERMARKET", -30.0),
            Triple("2024-01", "SUPERMARKET", -20.0),  // Total: -100
            // Single entry in February
            Triple("2024-02", "SUPERMARKET", -150.0)
        )

        val result = TrendCalculator.calculateTrends(data)
        val trend = result[TransactionCategory.SUPERMARKET]!!

        assertEquals(-150.0, trend.currentAmount)
        assertEquals(-100.0, trend.previousAmount)
    }

    // ===== Month ordering tests =====

    @Test
    fun testCalculateTrends_UsesLastTwoMonths() {
        // Should only compare the two most recent months
        val data = listOf(
            Triple("2024-01", "SUPERMARKET", -100.0),  // Oldest - ignored
            Triple("2024-02", "SUPERMARKET", -200.0),  // Previous month
            Triple("2024-03", "SUPERMARKET", -150.0)   // Current month
        )

        val result = TrendCalculator.calculateTrends(data)
        val trend = result[TransactionCategory.SUPERMARKET]!!

        assertEquals(-150.0, trend.currentAmount)
        assertEquals(-200.0, trend.previousAmount)
    }

    @Test
    fun testCalculateTrends_HandlesUnorderedData() {
        // Data not in chronological order
        val data = listOf(
            Triple("2024-03", "SUPERMARKET", -150.0),
            Triple("2024-01", "SUPERMARKET", -100.0),
            Triple("2024-02", "SUPERMARKET", -200.0)
        )

        val result = TrendCalculator.calculateTrends(data)
        val trend = result[TransactionCategory.SUPERMARKET]!!

        // Should still use 2024-03 as current and 2024-02 as previous
        assertEquals(-150.0, trend.currentAmount)
        assertEquals(-200.0, trend.previousAmount)
    }

    // ===== Trend indicator tests =====

    @Test
    fun testGetTrendIndicator_UP() {
        val trend = SpendingTrend(
            currentAmount = -150.0,
            previousAmount = -100.0,
            changeAmount = -50.0,
            changePercentage = -50f,
            direction = SpendingTrend.TrendDirection.UP
        )

        assertEquals("↑", TrendCalculator.getTrendIndicator(trend))
    }

    @Test
    fun testGetTrendIndicator_DOWN() {
        val trend = SpendingTrend(
            currentAmount = -100.0,
            previousAmount = -150.0,
            changeAmount = 50.0,
            changePercentage = 33f,
            direction = SpendingTrend.TrendDirection.DOWN
        )

        assertEquals("↓", TrendCalculator.getTrendIndicator(trend))
    }

    @Test
    fun testGetTrendIndicator_STABLE() {
        val trend = SpendingTrend(
            currentAmount = -102.0,
            previousAmount = -100.0,
            changeAmount = -2.0,
            changePercentage = -2f,
            direction = SpendingTrend.TrendDirection.STABLE
        )

        assertEquals("→", TrendCalculator.getTrendIndicator(trend))
    }

    // ===== Format percentage tests =====

    @Test
    fun testFormatTrendPercentage_Normal() {
        val trend = SpendingTrend(
            currentAmount = -150.0,
            previousAmount = -100.0,
            changeAmount = -50.0,
            changePercentage = -50f,
            direction = SpendingTrend.TrendDirection.UP
        )

        assertEquals("50%", TrendCalculator.formatTrendPercentage(trend))
    }

    @Test
    fun testFormatTrendPercentage_LessThanOne() {
        val trend = SpendingTrend(
            currentAmount = -100.5,
            previousAmount = -100.0,
            changeAmount = -0.5,
            changePercentage = -0.5f,
            direction = SpendingTrend.TrendDirection.STABLE
        )

        assertEquals("<1%", TrendCalculator.formatTrendPercentage(trend))
    }

    @Test
    fun testFormatTrendPercentage_VeryLarge() {
        val trend = SpendingTrend(
            currentAmount = -10000.0,
            previousAmount = -10.0,
            changeAmount = -9990.0,
            changePercentage = -99900f,
            direction = SpendingTrend.TrendDirection.UP
        )

        assertEquals(">999%", TrendCalculator.formatTrendPercentage(trend))
    }

    @Test
    fun testFormatTrendPercentage_UsesAbsoluteValue() {
        val trend = SpendingTrend(
            currentAmount = -150.0,
            previousAmount = -100.0,
            changeAmount = -50.0,
            changePercentage = -50f,  // Negative percentage
            direction = SpendingTrend.TrendDirection.UP
        )

        // Should display as positive percentage
        assertEquals("50%", TrendCalculator.formatTrendPercentage(trend))
    }

    // ===== Edge cases =====

    @Test
    fun testCalculateTrends_BothAmountsZero_ReturnsZeroPercentage() {
        val data = listOf(
            Triple("2024-01", "OTHER", 0.0),
            Triple("2024-02", "OTHER", 0.0)
        )

        val result = TrendCalculator.calculateTrends(data)

        // Should not include categories with no activity
        assertTrue(result.isEmpty() || result[TransactionCategory.OTHER]?.changePercentage == 0f)
    }

    @Test
    fun testCalculateTrends_InvalidCategoryName_Skipped() {
        val data = listOf(
            Triple("2024-01", "INVALID_CATEGORY", -100.0),
            Triple("2024-02", "INVALID_CATEGORY", -150.0)
        )

        val result = TrendCalculator.calculateTrends(data)

        // Invalid category names should be skipped
        assertTrue(result.isEmpty())
    }

    // ===== SpendingTrend data class tests =====

    @Test
    fun testSpendingTrend_TrendDirectionEnum() {
        assertEquals(3, SpendingTrend.TrendDirection.entries.size)
        assertTrue(SpendingTrend.TrendDirection.entries.contains(SpendingTrend.TrendDirection.UP))
        assertTrue(SpendingTrend.TrendDirection.entries.contains(SpendingTrend.TrendDirection.DOWN))
        assertTrue(SpendingTrend.TrendDirection.entries.contains(SpendingTrend.TrendDirection.STABLE))
    }
}
