package com.banking.statement.ui

import com.banking.statement.categorization.TransactionCategory
import kotlin.math.absoluteValue

/**
 * Utility for calculating spending trends
 */
object TrendCalculator {

    /**
     * Calculate spending trends for each category by comparing the last two months
     */
    fun calculateTrends(
        monthlyData: List<Triple<String, String, Double>>  // (month, category, amount)
    ): Map<TransactionCategory, SpendingTrend> {
        if (monthlyData.isEmpty()) return emptyMap()

        // Group by category
        val categoryData = monthlyData.groupBy { it.second }

        // Get the last two months available
        val months = monthlyData.map { it.first }.distinct().sorted().reversed()
        if (months.size < 2) return emptyMap()

        val currentMonth = months[0]
        val previousMonth = months[1]

        val trends = mutableMapOf<TransactionCategory, SpendingTrend>()

        categoryData.forEach { (categoryName, data) ->
            val category = TransactionCategory.entries.find { it.name == categoryName } ?: return@forEach

            val currentAmount = data
                .filter { it.first == currentMonth }
                .sumOf { it.third }

            val previousAmount = data
                .filter { it.first == previousMonth }
                .sumOf { it.third }

            if (currentAmount != 0.0 || previousAmount != 0.0) {
                val trend = calculateTrend(currentAmount, previousAmount)
                trends[category] = trend
            }
        }

        return trends
    }

    /**
     * Calculate trend between two spending amounts
     */
    private fun calculateTrend(
        currentAmount: Double,
        previousAmount: Double
    ): SpendingTrend {
        // Note: amounts are negative for expenses
        val changeAmount = currentAmount - previousAmount

        val changePercentage = if (previousAmount != 0.0) {
            ((currentAmount - previousAmount) / previousAmount.absoluteValue * 100).toFloat()
        } else if (currentAmount != 0.0) {
            100f  // New spending category
        } else {
            0f
        }

        val direction = when {
            changePercentage.absoluteValue < 5f -> SpendingTrend.TrendDirection.STABLE
            changeAmount < 0 -> SpendingTrend.TrendDirection.UP      // More negative = more spending
            else -> SpendingTrend.TrendDirection.DOWN                 // Less negative = less spending
        }

        return SpendingTrend(
            currentAmount = currentAmount,
            previousAmount = previousAmount,
            changeAmount = changeAmount,
            changePercentage = changePercentage,
            direction = direction
        )
    }

    /**
     * Get trend indicator text for display
     */
    fun getTrendIndicator(trend: SpendingTrend): String {
        return when (trend.direction) {
            SpendingTrend.TrendDirection.UP -> "↑"
            SpendingTrend.TrendDirection.DOWN -> "↓"
            SpendingTrend.TrendDirection.STABLE -> "→"
        }
    }

    /**
     * Get trend color for display
     */
    fun getTrendColor(trend: SpendingTrend, primaryColor: androidx.compose.ui.graphics.Color): androidx.compose.ui.graphics.Color {
        return when (trend.direction) {
            SpendingTrend.TrendDirection.UP -> androidx.compose.ui.graphics.Color(0xFFE53935)      // Red - spending increased
            SpendingTrend.TrendDirection.DOWN -> androidx.compose.ui.graphics.Color(0xFF43A047)    // Green - spending decreased
            SpendingTrend.TrendDirection.STABLE -> primaryColor.copy(alpha = 0.6f)                 // Neutral
        }
    }

    /**
     * Format trend percentage for display
     */
    fun formatTrendPercentage(trend: SpendingTrend): String {
        val percentage = trend.changePercentage.absoluteValue
        return when {
            percentage < 1f -> "<1%"
            percentage > 999f -> ">999%"
            else -> "${percentage.toInt()}%"
        }
    }
}
