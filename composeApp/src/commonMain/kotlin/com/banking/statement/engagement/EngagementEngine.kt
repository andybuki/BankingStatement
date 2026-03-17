package com.banking.statement.engagement

import com.banking.statement.categorization.TransactionCategory
import com.banking.statement.ui.CategorySpending
import com.banking.statement.ui.MonthlySummary
import com.banking.statement.ui.TransactionDisplay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Data class representing a smart insight detected from spending patterns
 */
data class SmartInsight(
    val type: InsightType,
    val title: String,
    val description: String,
    val severity: InsightSeverity
)

enum class InsightType {
    SPENDING_SPIKE,
    SPENDING_DROP,
    NEW_RECURRING,
    UNUSUAL_TRANSACTION,
    SAVINGS_OPPORTUNITY,
    INCOME_CHANGE
}

enum class InsightSeverity {
    INFO,
    WARNING,
    POSITIVE
}

/**
 * Weekly spending digest summary
 */
data class WeeklyDigest(
    val totalSpent: Double,
    val totalIncome: Double,
    val topCategory: String,
    val topCategoryAmount: Double,
    val transactionCount: Int,
    val comparedToLastWeek: Double // percentage change
)

/**
 * Monthly financial health score (0-100)
 */
data class FinancialHealthScore(
    val overall: Int,
    val savingsRate: Int,
    val spendingStability: Int,
    val diversification: Int,
    val summary: String
)

/**
 * Year-in-review summary data
 */
data class YearInReview(
    val year: Int,
    val totalIncome: Double,
    val totalExpenses: Double,
    val totalSaved: Double,
    val topCategories: List<Pair<String, Double>>,
    val biggestMonth: Pair<String, Double>,
    val leanestMonth: Pair<String, Double>,
    val totalTransactions: Int,
    val averageMonthlySpending: Double,
    val savingsRate: Double
)

/**
 * Core engine that generates engagement features from transaction data.
 * All logic is pure computation — no platform dependencies.
 */
object EngagementEngine {

    // ==================== Smart Insights ====================

    /**
     * Detect anomalies and patterns in spending data
     */
    fun generateInsights(
        categorySpending: List<CategorySpending>,
        monthlySummary: List<MonthlySummary>,
        transactions: List<TransactionDisplay>,
        strings: InsightStrings
    ): List<SmartInsight> {
        val insights = mutableListOf<SmartInsight>()

        insights.addAll(detectSpendingSpikes(categorySpending, monthlySummary, strings))
        insights.addAll(detectIncomeChanges(monthlySummary, strings))
        insights.addAll(detectSavingsOpportunity(monthlySummary, strings))
        insights.addAll(detectUnusualTransactions(transactions, strings))

        return insights
    }

    private fun detectSpendingSpikes(
        categorySpending: List<CategorySpending>,
        monthlySummary: List<MonthlySummary>,
        strings: InsightStrings
    ): List<SmartInsight> {
        val insights = mutableListOf<SmartInsight>()

        // Check categories with significant trend changes
        categorySpending.forEach { spending ->
            val trend = spending.trend ?: return@forEach
            val pctChange = trend.changePercentage

            if (pctChange > 50f && abs(trend.changeAmount) > 20.0) {
                insights.add(
                    SmartInsight(
                        type = InsightType.SPENDING_SPIKE,
                        title = strings.spendingSpikeTitle(spending.category.name),
                        description = strings.spendingSpikeDesc(
                            spending.category.name,
                            abs(pctChange).roundToInt()
                        ),
                        severity = InsightSeverity.WARNING
                    )
                )
            } else if (pctChange < -30f && abs(trend.changeAmount) > 20.0) {
                insights.add(
                    SmartInsight(
                        type = InsightType.SPENDING_DROP,
                        title = strings.spendingDropTitle(spending.category.name),
                        description = strings.spendingDropDesc(
                            spending.category.name,
                            abs(pctChange).roundToInt()
                        ),
                        severity = InsightSeverity.POSITIVE
                    )
                )
            }
        }

        return insights
    }

    private fun detectIncomeChanges(
        monthlySummary: List<MonthlySummary>,
        strings: InsightStrings
    ): List<SmartInsight> {
        if (monthlySummary.size < 2) return emptyList()

        val sorted = monthlySummary.sortedBy { it.month }
        val current = sorted.last()
        val previous = sorted[sorted.size - 2]

        if (previous.income > 0 && current.income > 0) {
            val change = ((current.income - previous.income) / previous.income * 100)
            if (change > 20) {
                return listOf(
                    SmartInsight(
                        type = InsightType.INCOME_CHANGE,
                        title = strings.incomeUpTitle,
                        description = strings.incomeUpDesc(change.roundToInt()),
                        severity = InsightSeverity.POSITIVE
                    )
                )
            } else if (change < -20) {
                return listOf(
                    SmartInsight(
                        type = InsightType.INCOME_CHANGE,
                        title = strings.incomeDownTitle,
                        description = strings.incomeDownDesc(abs(change).roundToInt()),
                        severity = InsightSeverity.WARNING
                    )
                )
            }
        }
        return emptyList()
    }

    private fun detectSavingsOpportunity(
        monthlySummary: List<MonthlySummary>,
        strings: InsightStrings
    ): List<SmartInsight> {
        if (monthlySummary.isEmpty()) return emptyList()

        val sorted = monthlySummary.sortedBy { it.month }
        val latest = sorted.last()

        // If expenses exceed 90% of income
        if (latest.income > 0) {
            val expenseRatio = abs(latest.expenses) / latest.income
            if (expenseRatio > 0.9) {
                return listOf(
                    SmartInsight(
                        type = InsightType.SAVINGS_OPPORTUNITY,
                        title = strings.savingsOpportunityTitle,
                        description = strings.savingsOpportunityDesc(
                            (expenseRatio * 100).roundToInt()
                        ),
                        severity = InsightSeverity.WARNING
                    )
                )
            }
        }
        return emptyList()
    }

    private fun detectUnusualTransactions(
        transactions: List<TransactionDisplay>,
        strings: InsightStrings
    ): List<SmartInsight> {
        if (transactions.size < 10) return emptyList()

        val expenses = transactions.filter { it.amount < 0 }
        if (expenses.size < 5) return emptyList()

        val avgExpense = expenses.map { abs(it.amount) }.average()
        val largeTransactions = expenses.filter { abs(it.amount) > avgExpense * 3 }

        if (largeTransactions.isNotEmpty()) {
            val largest = largeTransactions.maxByOrNull { abs(it.amount) }!!
            return listOf(
                SmartInsight(
                    type = InsightType.UNUSUAL_TRANSACTION,
                    title = strings.unusualTransactionTitle,
                    description = strings.unusualTransactionDesc(
                        largest.counterparty ?: largest.description,
                        abs(largest.amount)
                    ),
                    severity = InsightSeverity.INFO
                )
            )
        }
        return emptyList()
    }

    // ==================== Weekly Digest ====================

    fun generateWeeklyDigest(
        transactions: List<TransactionDisplay>,
        categorySpending: List<CategorySpending>,
        previousWeekExpenses: Double
    ): WeeklyDigest {
        val income = transactions.filter { it.amount > 0 }.sumOf { it.amount }
        val spent = transactions.filter { it.amount < 0 }.sumOf { abs(it.amount) }

        val topCategory = categorySpending
            .filter { it.totalAmount < 0 }
            .maxByOrNull { abs(it.totalAmount) }

        val comparedToLastWeek = if (previousWeekExpenses > 0) {
            ((spent - previousWeekExpenses) / previousWeekExpenses * 100)
        } else 0.0

        return WeeklyDigest(
            totalSpent = spent,
            totalIncome = income,
            topCategory = topCategory?.category?.name ?: "",
            topCategoryAmount = abs(topCategory?.totalAmount ?: 0.0),
            transactionCount = transactions.size,
            comparedToLastWeek = comparedToLastWeek
        )
    }

    // ==================== Financial Health Score ====================

    fun calculateHealthScore(
        monthlySummary: List<MonthlySummary>,
        categorySpending: List<CategorySpending>
    ): FinancialHealthScore {
        if (monthlySummary.isEmpty()) {
            return FinancialHealthScore(
                overall = 0,
                savingsRate = 0,
                spendingStability = 0,
                diversification = 0,
                summary = "Import transactions to see your score"
            )
        }

        // 1. Savings rate score (0-40 points)
        val latestMonths = monthlySummary.sortedBy { it.month }.takeLast(3)
        val avgIncome = latestMonths.map { it.income }.average()
        val avgExpenses = latestMonths.map { abs(it.expenses) }.average()
        val savingsRateValue = if (avgIncome > 0) {
            ((avgIncome - avgExpenses) / avgIncome * 100).coerceIn(0.0, 100.0)
        } else 0.0
        val savingsScore = when {
            savingsRateValue >= 30 -> 40
            savingsRateValue >= 20 -> 35
            savingsRateValue >= 10 -> 25
            savingsRateValue >= 5 -> 15
            savingsRateValue > 0 -> 10
            else -> 0
        }

        // 2. Spending stability score (0-30 points) — low variance is good
        val stabilityScore = if (latestMonths.size >= 2) {
            val expenseValues = latestMonths.map { abs(it.expenses) }
            val mean = expenseValues.average()
            val variance = expenseValues.map { (it - mean) * (it - mean) }.average()
            val cv = if (mean > 0) kotlin.math.sqrt(variance) / mean else 0.0
            when {
                cv < 0.1 -> 30
                cv < 0.2 -> 25
                cv < 0.3 -> 20
                cv < 0.5 -> 15
                else -> 5
            }
        } else 15

        // 3. Diversification score (0-30 points) — not overspending in one category
        val diversificationScore = if (categorySpending.isNotEmpty()) {
            val topPct = categorySpending
                .filter { it.totalAmount < 0 }
                .maxByOrNull { abs(it.totalAmount) }
                ?.percentage ?: 0f
            when {
                topPct < 30 -> 30
                topPct < 40 -> 25
                topPct < 50 -> 20
                topPct < 70 -> 10
                else -> 5
            }
        } else 15

        val overall = (savingsScore + stabilityScore + diversificationScore).coerceIn(0, 100)

        val summary = when {
            overall >= 80 -> "Excellent"
            overall >= 60 -> "Good"
            overall >= 40 -> "Fair"
            overall >= 20 -> "Needs attention"
            else -> "Getting started"
        }

        return FinancialHealthScore(
            overall = overall,
            savingsRate = savingsScore,
            spendingStability = stabilityScore,
            diversification = diversificationScore,
            summary = summary
        )
    }

    // ==================== Year-in-Review ====================

    fun generateYearInReview(
        year: Int,
        monthlySummary: List<MonthlySummary>,
        categorySpending: List<CategorySpending>,
        transactions: List<TransactionDisplay>
    ): YearInReview? {
        val yearStr = year.toString()
        val yearMonths = monthlySummary.filter { it.month.startsWith(yearStr) }
        if (yearMonths.isEmpty()) return null

        val totalIncome = yearMonths.sumOf { it.income }
        val totalExpenses = yearMonths.sumOf { it.expenses }
        val totalSaved = totalIncome + totalExpenses // expenses are negative

        val topCategories = categorySpending
            .filter { it.totalAmount < 0 }
            .sortedBy { it.totalAmount }
            .take(5)
            .map { it.category.name to abs(it.totalAmount) }

        val biggestMonth = yearMonths
            .maxByOrNull { abs(it.expenses) }
            ?.let { it.month to abs(it.expenses) }
            ?: ("" to 0.0)

        val leanestMonth = yearMonths
            .minByOrNull { abs(it.expenses) }
            ?.let { it.month to abs(it.expenses) }
            ?: ("" to 0.0)

        val savingsRate = if (totalIncome > 0) totalSaved / totalIncome * 100 else 0.0

        return YearInReview(
            year = year,
            totalIncome = totalIncome,
            totalExpenses = abs(totalExpenses),
            totalSaved = totalSaved,
            topCategories = topCategories,
            biggestMonth = biggestMonth,
            leanestMonth = leanestMonth,
            totalTransactions = transactions.size,
            averageMonthlySpending = if (yearMonths.isNotEmpty()) abs(totalExpenses) / yearMonths.size else 0.0,
            savingsRate = savingsRate
        )
    }
}

/**
 * Interface for localized insight strings — avoids hardcoding English in engine
 */
data class InsightStrings(
    val spendingSpikeTitle: (String) -> String,
    val spendingSpikeDesc: (String, Int) -> String,
    val spendingDropTitle: (String) -> String,
    val spendingDropDesc: (String, Int) -> String,
    val incomeUpTitle: String,
    val incomeUpDesc: (Int) -> String,
    val incomeDownTitle: String,
    val incomeDownDesc: (Int) -> String,
    val savingsOpportunityTitle: String,
    val savingsOpportunityDesc: (Int) -> String,
    val unusualTransactionTitle: String,
    val unusualTransactionDesc: (String, Double) -> String
)
