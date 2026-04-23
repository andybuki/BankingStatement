package com.banking.statement.ui

import com.banking.statement.categorization.TransactionCategory

/**
 * Time period for filtering spending.
 */
enum class TimePeriod {
    WEEK, MONTH, YEAR, ALL, CUSTOM
}

/**
 * Category spending data for display.
 */
data class CategorySpending(
    val category: TransactionCategory,
    val totalAmount: Double,
    val transactionCount: Int,
    val percentage: Float,
    val trend: SpendingTrend? = null
)

/**
 * Spending trend comparison data.
 */
data class SpendingTrend(
    val currentAmount: Double,
    val previousAmount: Double,
    val changeAmount: Double,
    val changePercentage: Float,
    val direction: TrendDirection
) {
    enum class TrendDirection {
        UP,      // Spending increased
        DOWN,    // Spending decreased
        STABLE   // No significant change (<5%)
    }
}

/**
 * Monthly category spending for trend analysis.
 */
data class CategoryMonthlySpending(
    val month: String,
    val category: TransactionCategory,
    val amount: Double
)

/**
 * Monthly summary data.
 */
data class MonthlySummary(
    val month: String,
    val income: Double,
    val expenses: Double
) {
    val netAmount: Double get() = income + expenses // expenses are negative
}
