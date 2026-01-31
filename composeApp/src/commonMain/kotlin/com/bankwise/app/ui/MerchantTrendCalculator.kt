package com.bankwise.app.ui

import com.bankwise.app.categorization.TransactionCategory

/**
 * Merchant spending trend for individual merchants (e.g., Lidl, Edeka, REWE)
 */
data class MerchantTrend(
    val merchantName: String,
    val category: TransactionCategory,
    val currentMonthAmount: Double,
    val previousMonthAmount: Double,
    val changeAmount: Double,
    val changePercentage: Float,
    val direction: TrendDirection,
    val currentMonthTransactions: Int,
    val previousMonthTransactions: Int
) {
    enum class TrendDirection {
        UP,      // Spending increased
        DOWN,    // Spending decreased
        STABLE,  // No significant change (<5%)
        NEW      // First time this month
    }
}

/**
 * Calculate merchant-level trends from monthly data
 */
object MerchantTrendCalculator {

    /**
     * Calculate trends for top merchants comparing last two months
     * Returns map of merchant name to their trend
     */
    fun calculateMerchantTrends(
        monthlyData: List<MerchantMonthlyData>,  // (month, merchant, category, amount, count)
        topN: Int = 10
    ): List<MerchantTrend> {
        if (monthlyData.isEmpty()) return emptyList()

        // Get last two months
        val months = monthlyData.map { it.month }.distinct().sorted().reversed()
        if (months.isEmpty()) return emptyList()

        val currentMonth = months[0]
        val previousMonth = if (months.size > 1) months[1] else null

        // Group by merchant
        val merchantData = monthlyData.groupBy { it.merchantName }

        // Calculate trends for each merchant
        val trends = merchantData.mapNotNull { (merchantName, data) ->
            val currentData = data.find { it.month == currentMonth }
            val previousData = previousMonth?.let { month ->
                data.find { it.month == month }
            }

            if (currentData == null) return@mapNotNull null

            val currentAmount = currentData.totalAmount
            val previousAmount = previousData?.totalAmount ?: 0.0
            val changeAmount = currentAmount - previousAmount

            val changePercentage = if (previousAmount != 0.0) {
                ((changeAmount / kotlin.math.abs(previousAmount)) * 100).toFloat()
            } else if (currentAmount != 0.0) {
                100f  // New merchant
            } else {
                0f
            }

            val direction = when {
                previousAmount == 0.0 -> MerchantTrend.TrendDirection.NEW
                kotlin.math.abs(changePercentage) < 5f -> MerchantTrend.TrendDirection.STABLE
                changeAmount < 0 -> MerchantTrend.TrendDirection.UP      // More negative = more spending
                else -> MerchantTrend.TrendDirection.DOWN                 // Less negative = less spending
            }

            MerchantTrend(
                merchantName = merchantName,
                category = currentData.category,
                currentMonthAmount = currentAmount,
                previousMonthAmount = previousAmount,
                changeAmount = changeAmount,
                changePercentage = changePercentage,
                direction = direction,
                currentMonthTransactions = currentData.transactionCount,
                previousMonthTransactions = previousData?.transactionCount ?: 0
            )
        }

        // Sort by total spending (most significant merchants first)
        return trends
            .sortedBy { it.currentMonthAmount }  // Most negative first (highest spending)
            .take(topN)
    }

    /**
     * Get trend indicator for merchant
     */
    fun getTrendIndicator(trend: MerchantTrend): String {
        return when (trend.direction) {
            MerchantTrend.TrendDirection.UP -> "↑"
            MerchantTrend.TrendDirection.DOWN -> "↓"
            MerchantTrend.TrendDirection.STABLE -> "→"
            MerchantTrend.TrendDirection.NEW -> "✨"
        }
    }

    /**
     * Get trend color
     */
    fun getTrendColor(trend: MerchantTrend, primaryColor: androidx.compose.ui.graphics.Color): androidx.compose.ui.graphics.Color {
        return when (trend.direction) {
            MerchantTrend.TrendDirection.UP -> androidx.compose.ui.graphics.Color(0xFFE53935)      // Red - spending increased
            MerchantTrend.TrendDirection.DOWN -> androidx.compose.ui.graphics.Color(0xFF43A047)    // Green - spending decreased
            MerchantTrend.TrendDirection.NEW -> androidx.compose.ui.graphics.Color(0xFF1E88E5)     // Blue - new merchant
            MerchantTrend.TrendDirection.STABLE -> primaryColor.copy(alpha = 0.6f)                 // Neutral
        }
    }

    /**
     * Format trend percentage
     */
    fun formatTrendPercentage(trend: MerchantTrend): String {
        if (trend.direction == MerchantTrend.TrendDirection.NEW) return "New"
        val percentage = kotlin.math.abs(trend.changePercentage)
        return when {
            percentage < 1f -> "<1%"
            percentage > 999f -> ">999%"
            else -> "${percentage.toInt()}%"
        }
    }
}

/**
 * Data class for merchant monthly spending
 */
data class MerchantMonthlyData(
    val month: String,
    val merchantName: String,
    val category: TransactionCategory,
    val totalAmount: Double,
    val transactionCount: Int
)

/**
 * Merchant spending history across multiple months
 */
data class MerchantHistory(
    val merchantName: String,
    val category: TransactionCategory,
    val monthlySpending: List<MonthSpending>,
    val totalSpending: Double,
    val totalTransactions: Int,
    val averageMonthlySpending: Double
) {
    data class MonthSpending(
        val month: String,
        val amount: Double,
        val transactionCount: Int
    )
}

/**
 * Calculate merchant spending history across all months
 */
fun calculateMerchantHistory(
    monthlyData: List<MerchantMonthlyData>,
    topN: Int = 10
): List<MerchantHistory> {
    if (monthlyData.isEmpty()) return emptyList()

    // Group by merchant
    val merchantGroups = monthlyData.groupBy { it.merchantName }

    // Create history for each merchant
    val histories = merchantGroups.map { (merchantName, data) ->
        val monthlySpending = data
            .sortedByDescending { it.month }
            .map { MerchantHistory.MonthSpending(it.month, it.totalAmount, it.transactionCount) }

        val totalSpending = data.sumOf { it.totalAmount }
        val totalTransactions = data.sumOf { it.transactionCount }
        val averageMonthlySpending = totalSpending / data.size

        MerchantHistory(
            merchantName = merchantName,
            category = data.first().category,
            monthlySpending = monthlySpending,
            totalSpending = totalSpending,
            totalTransactions = totalTransactions,
            averageMonthlySpending = averageMonthlySpending
        )
    }

    // Sort by total spending (most significant merchants first)
    return histories
        .sortedBy { it.totalSpending }  // Most negative first (highest spending)
        .take(topN)
}
