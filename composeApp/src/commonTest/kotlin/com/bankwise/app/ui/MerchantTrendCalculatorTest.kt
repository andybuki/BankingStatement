package com.bankwise.app.ui

import com.bankwise.app.categorization.TransactionCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MerchantTrendCalculatorTest {

    @Test
    fun testMerchantTrendCalculation_SpendingIncrease() {
        // October: Lidl €100, November: Lidl €150 = +50%
        val data = listOf(
            MerchantMonthlyData("2024-10", "Lidl", TransactionCategory.SUPERMARKET, -100.0, 5),
            MerchantMonthlyData("2024-11", "Lidl", TransactionCategory.SUPERMARKET, -150.0, 7)
        )

        val trends = MerchantTrendCalculator.calculateMerchantTrends(data, topN = 10)

        assertEquals(1, trends.size, "Should have one merchant trend")
        val lidlTrend = trends[0]
        assertEquals("Lidl", lidlTrend.merchantName)
        assertEquals(-150.0, lidlTrend.currentMonthAmount)
        assertEquals(-100.0, lidlTrend.previousMonthAmount)
        assertEquals(-50.0, lidlTrend.changeAmount)
        assertEquals(MerchantTrend.TrendDirection.UP, lidlTrend.direction)
        assertTrue(lidlTrend.changePercentage > 45f && lidlTrend.changePercentage < 55f, "Should be ~50%")
    }

    @Test
    fun testMerchantTrendCalculation_SpendingDecrease() {
        // October: Edeka €150, November: Edeka €100 = -33%
        val data = listOf(
            MerchantMonthlyData("2024-10", "Edeka", TransactionCategory.SUPERMARKET, -150.0, 8),
            MerchantMonthlyData("2024-11", "Edeka", TransactionCategory.SUPERMARKET, -100.0, 5)
        )

        val trends = MerchantTrendCalculator.calculateMerchantTrends(data, topN = 10)

        assertEquals(1, trends.size)
        val edekaTrend = trends[0]
        assertEquals("Edeka", edekaTrend.merchantName)
        assertEquals(MerchantTrend.TrendDirection.DOWN, edekaTrend.direction)
        assertTrue(edekaTrend.changePercentage > 30f && edekaTrend.changePercentage < 35f, "Should be ~33%")
    }

    @Test
    fun testMerchantTrendCalculation_StableSpending() {
        // October: REWE €100, November: REWE €103 = +3% (stable)
        val data = listOf(
            MerchantMonthlyData("2024-10", "REWE", TransactionCategory.SUPERMARKET, -100.0, 5),
            MerchantMonthlyData("2024-11", "REWE", TransactionCategory.SUPERMARKET, -103.0, 5)
        )

        val trends = MerchantTrendCalculator.calculateMerchantTrends(data, topN = 10)

        assertEquals(1, trends.size)
        val reweTrend = trends[0]
        assertEquals("REWE", reweTrend.merchantName)
        assertEquals(MerchantTrend.TrendDirection.STABLE, reweTrend.direction)
    }

    @Test
    fun testMerchantTrendCalculation_NewMerchant() {
        // Only November data = new merchant
        val data = listOf(
            MerchantMonthlyData("2024-11", "Aldi", TransactionCategory.SUPERMARKET, -80.0, 4)
        )

        val trends = MerchantTrendCalculator.calculateMerchantTrends(data, topN = 10)

        assertEquals(1, trends.size)
        val aldiTrend = trends[0]
        assertEquals("Aldi", aldiTrend.merchantName)
        assertEquals(MerchantTrend.TrendDirection.NEW, aldiTrend.direction)
        assertEquals(0.0, aldiTrend.previousMonthAmount)
    }

    @Test
    fun testMerchantTrendCalculation_MultipleMerchants() {
        // October vs November for multiple supermarkets
        val data = listOf(
            // Lidl: €100 → €150 (+50%)
            MerchantMonthlyData("2024-10", "Lidl", TransactionCategory.SUPERMARKET, -100.0, 5),
            MerchantMonthlyData("2024-11", "Lidl", TransactionCategory.SUPERMARKET, -150.0, 7),
            // Edeka: €200 → €180 (-10%)
            MerchantMonthlyData("2024-10", "Edeka", TransactionCategory.SUPERMARKET, -200.0, 10),
            MerchantMonthlyData("2024-11", "Edeka", TransactionCategory.SUPERMARKET, -180.0, 9),
            // REWE: only November (new)
            MerchantMonthlyData("2024-11", "REWE", TransactionCategory.SUPERMARKET, -120.0, 6)
        )

        val trends = MerchantTrendCalculator.calculateMerchantTrends(data, topN = 10)

        assertEquals(3, trends.size, "Should have three merchant trends")

        // Verify each merchant is present
        val merchantNames = trends.map { it.merchantName }.toSet()
        assertTrue(merchantNames.contains("Lidl"))
        assertTrue(merchantNames.contains("Edeka"))
        assertTrue(merchantNames.contains("REWE"))
    }

    @Test
    fun testMerchantTrendCalculation_TopNLimit() {
        // 5 merchants, request top 3
        val data = listOf(
            MerchantMonthlyData("2024-10", "Lidl", TransactionCategory.SUPERMARKET, -100.0, 5),
            MerchantMonthlyData("2024-11", "Lidl", TransactionCategory.SUPERMARKET, -150.0, 7),
            MerchantMonthlyData("2024-10", "Edeka", TransactionCategory.SUPERMARKET, -80.0, 4),
            MerchantMonthlyData("2024-11", "Edeka", TransactionCategory.SUPERMARKET, -90.0, 5),
            MerchantMonthlyData("2024-10", "REWE", TransactionCategory.SUPERMARKET, -120.0, 6),
            MerchantMonthlyData("2024-11", "REWE", TransactionCategory.SUPERMARKET, -130.0, 7),
            MerchantMonthlyData("2024-10", "Aldi", TransactionCategory.SUPERMARKET, -60.0, 3),
            MerchantMonthlyData("2024-11", "Aldi", TransactionCategory.SUPERMARKET, -65.0, 4),
            MerchantMonthlyData("2024-10", "Kaufland", TransactionCategory.SUPERMARKET, -40.0, 2),
            MerchantMonthlyData("2024-11", "Kaufland", TransactionCategory.SUPERMARKET, -45.0, 3)
        )

        val trends = MerchantTrendCalculator.calculateMerchantTrends(data, topN = 3)

        assertEquals(3, trends.size, "Should limit to top 3 merchants")
    }

    @Test
    fun testTrendIndicators() {
        val upTrend = MerchantTrend(
            "Lidl", TransactionCategory.SUPERMARKET, -150.0, -100.0, -50.0, 50f,
            MerchantTrend.TrendDirection.UP, 7, 5
        )
        assertEquals("↑", MerchantTrendCalculator.getTrendIndicator(upTrend))

        val downTrend = MerchantTrend(
            "Edeka", TransactionCategory.SUPERMARKET, -100.0, -150.0, 50.0, -33f,
            MerchantTrend.TrendDirection.DOWN, 5, 7
        )
        assertEquals("↓", MerchantTrendCalculator.getTrendIndicator(downTrend))

        val stableTrend = MerchantTrend(
            "REWE", TransactionCategory.SUPERMARKET, -103.0, -100.0, -3.0, 3f,
            MerchantTrend.TrendDirection.STABLE, 5, 5
        )
        assertEquals("→", MerchantTrendCalculator.getTrendIndicator(stableTrend))

        val newTrend = MerchantTrend(
            "Aldi", TransactionCategory.SUPERMARKET, -80.0, 0.0, -80.0, 100f,
            MerchantTrend.TrendDirection.NEW, 4, 0
        )
        assertEquals("✨", MerchantTrendCalculator.getTrendIndicator(newTrend))
    }

    @Test
    fun testFormatTrendPercentage() {
        val trend50 = MerchantTrend(
            "Lidl", TransactionCategory.SUPERMARKET, -150.0, -100.0, -50.0, 50f,
            MerchantTrend.TrendDirection.UP, 7, 5
        )
        assertEquals("50%", MerchantTrendCalculator.formatTrendPercentage(trend50))

        val trendNew = MerchantTrend(
            "Aldi", TransactionCategory.SUPERMARKET, -80.0, 0.0, -80.0, 100f,
            MerchantTrend.TrendDirection.NEW, 4, 0
        )
        assertEquals("New", MerchantTrendCalculator.formatTrendPercentage(trendNew))

        val trendSmall = MerchantTrend(
            "REWE", TransactionCategory.SUPERMARKET, -100.5, -100.0, -0.5, 0.5f,
            MerchantTrend.TrendDirection.STABLE, 5, 5
        )
        assertEquals("<1%", MerchantTrendCalculator.formatTrendPercentage(trendSmall))
    }
}
