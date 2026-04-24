package com.banking.statement.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.banking.statement.LocalStrings
import com.banking.statement.ui.theme.AppColors
import com.banking.statement.categorization.TransactionCategory
import com.banking.statement.export.ExportFormat
import com.banking.statement.export.SpendingExportData
import com.banking.statement.ui.charts.CategorySpendingDonutChart
import com.banking.statement.ui.charts.CategoryStackedAreaChart
import com.banking.statement.ui.charts.IncomeVsExpensesBarChart
import com.banking.statement.ui.charts.MonthCategoryBreakdown
import com.banking.statement.ui.charts.MonthlySpendingLineChart
import kotlin.math.absoluteValue
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.todayIn

// Data classes (CategorySpending, MonthlySummary, TimePeriod, SpendingTrend,
// CategoryMonthlySpending) live in SpendingModels.kt.
// List item composables (SummaryCard, CategorySpendingItem, MonthlyItem) live
// in SpendingListItems.kt.
// Merchant-tab composables and aggregators live in MerchantSpendingItems.kt.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendingOverviewScreen(
    totalIncome: Double,
    totalExpenses: Double,
    categorySpending: List<CategorySpending>,
    monthlySummary: List<MonthlySummary>,
    transactions: List<TransactionDisplay> = emptyList(),
    accounts: List<AccountFilterOption> = emptyList(),
    selectedAccountId: Long? = null,  // Controlled from outside (App level)
    onBackClick: (() -> Unit)? = null,
    onShare: ((ExportFormat, SpendingExportData) -> Unit)? = null,
    showChartView: Boolean = false,  // Controlled by App level
    selectedTimePeriod: String = "all",  // Controlled by App level
    spendingEpochStart: Long? = null,  // Custom range start (epoch seconds)
    spendingEpochEnd: Long? = null     // Custom range end (epoch seconds)
) {
    val strings = LocalStrings.current
    // Convert string to TimePeriod enum
    val selectedPeriod = when (selectedTimePeriod) {
        "week" -> TimePeriod.WEEK
        "month" -> TimePeriod.MONTH
        "year" -> TimePeriod.YEAR
        "custom" -> TimePeriod.CUSTOM
        else -> TimePeriod.ALL
    }

    // Category drill-down state
    var selectedCategoryForDetails by remember { mutableStateOf<TransactionCategory?>(null) }

    // Monthly summary pagination state
    val monthlyPageSize = 6
    var monthlyItemsShown by remember { mutableStateOf(monthlyPageSize) }

    // Filter transactions based on selected account and time period
    val filteredData = remember(transactions, selectedAccountId, selectedPeriod, spendingEpochStart, spendingEpochEnd) {
        var filtered = if (selectedAccountId == null) {
            transactions
        } else {
            transactions.filter { it.accountId == selectedAccountId }
        }

        // Apply time period filter
        filtered = filterByTimePeriod(filtered, selectedPeriod, spendingEpochStart, spendingEpochEnd)

        val income = filtered.filter { it.amount > 0 }.sumOf { it.amount }
        val expenses = filtered.filter { it.amount < 0 }.sumOf { it.amount }

        // Calculate category spending
        val spendingByCategory = filtered
            .filter { it.amount < 0 }
            .groupBy { it.category }
            .mapValues { (_, txs) -> txs.sumOf { it.amount } }

        val totalExpensesAmount = spendingByCategory.values.sum()

        val categoryList = spendingByCategory.map { (category, total) ->
            CategorySpending(
                category = category,
                totalAmount = total,
                transactionCount = filtered.count { it.category == category && it.amount < 0 },
                percentage = if (totalExpensesAmount != 0.0) {
                    ((total / totalExpensesAmount) * 100).toFloat()
                } else 0f
            )
        }.sortedBy { it.totalAmount }

        // Calculate monthly summary
        val monthlyData = filtered.groupBy { tx ->
            // Extract month from date string (format: DD.MM.YYYY)
            val parts = tx.date.split(".")
            if (parts.size == 3) "${parts[2]}-${parts[1]}" else tx.date
        }

        val monthlyList = monthlyData.map { (month, txs) ->
            val monthIncome = txs.filter { it.amount > 0 }.sumOf { it.amount }
            val monthExpenses = txs.filter { it.amount < 0 }.sumOf { it.amount }
            MonthlySummary(
                month = formatMonthDisplay(month),
                income = monthIncome,
                expenses = monthExpenses
            )
        }.sortedByDescending { it.month }

        FilteredSpendingData(income, expenses, categoryList, monthlyList)
    }

    // Compute monthly category breakdown for stacked area chart
    val monthlyCategoryBreakdown = remember(transactions, selectedAccountId, selectedPeriod, spendingEpochStart, spendingEpochEnd) {
        var filtered = if (selectedAccountId == null) {
            transactions
        } else {
            transactions.filter { it.accountId == selectedAccountId }
        }
        filtered = filterByTimePeriod(filtered, selectedPeriod, spendingEpochStart, spendingEpochEnd)

        filtered
            .filter { it.amount < 0 }
            .groupBy { tx ->
                val parts = tx.date.split(".")
                if (parts.size == 3) "${parts[2]}-${parts[1]}" else tx.date
            }
            .map { (month, txs) ->
                val categoryAmounts = txs
                    .groupBy { it.category }
                    .map { (cat, catTxs) -> cat to catTxs.sumOf { it.amount.absoluteValue } }
                MonthCategoryBreakdown(
                    month = formatMonthDisplay(month),
                    categoryAmounts = categoryAmounts
                )
            }
            .sortedBy { it.month }
    }

    // For "all" period with no account filter, use DB-level aggregates (accurate full-corpus totals).
    // Otherwise fall back to in-memory computation from loaded transactions.
    val useDbLevel = selectedPeriod == TimePeriod.ALL && selectedAccountId == null
    val displayIncome = if (useDbLevel) totalIncome else filteredData.income
    val displayExpenses = if (useDbLevel) totalExpenses else filteredData.expenses
    val displayCategorySpending = if (useDbLevel) categorySpending else filteredData.categorySpending
    val displayMonthlySummary = if (useDbLevel) monthlySummary else filteredData.monthlySummary

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
            // Summary Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SummaryCard(
                        title = strings.income,
                        amount = displayIncome,
                        color = AppColors.Income,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryCard(
                        title = strings.expenses,
                        amount = displayExpenses,
                        color = AppColors.Expenses,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Net balance
            item {
                val netAmount = displayIncome + displayExpenses
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (netAmount >= 0) AppColors.IncomeTint
                            else AppColors.ExpenseTint
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.netBalance,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextPrimary
                    )
                    Text(
                        text = formatCurrency(netAmount),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (netAmount >= 0) AppColors.Income else AppColors.Expenses
                    )
                }
            }

            // Chart view or list view
            if (showChartView && displayCategorySpending.isNotEmpty()) {
                // Category Spending Donut Chart
                item {
                    CategorySpendingDonutChart(
                        categorySpending = displayCategorySpending.filter { it.totalAmount < 0 },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Monthly Spending Line Chart
                if (displayMonthlySummary.isNotEmpty()) {
                    item {
                        MonthlySpendingLineChart(
                            monthlySummary = displayMonthlySummary,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Income vs Expenses Bar Chart
                if (displayMonthlySummary.isNotEmpty()) {
                    item {
                        IncomeVsExpensesBarChart(
                            monthlySummary = displayMonthlySummary,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Category Stacked Area Chart
                if (monthlyCategoryBreakdown.size >= 2) {
                    item {
                        CategoryStackedAreaChart(
                            monthlyBreakdown = monthlyCategoryBreakdown,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                // Category breakdown title
                item {
                    Text(
                        text = strings.spendingByCategory,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // Category items
                if (displayCategorySpending.isEmpty()) {
                    item {
                        Text(
                            text = strings.noSpendingData,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(displayCategorySpending.filter { it.totalAmount < 0 }.sortedBy { it.totalAmount }) { spending ->
                        CategorySpendingItem(
                            spending = spending,
                            onClick = { selectedCategoryForDetails = spending.category }
                        )
                    }
                }

                // Monthly summary title with pagination
                if (displayMonthlySummary.isNotEmpty()) {
                    item {
                        Text(
                            text = strings.monthlySummary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }

                    val visibleMonthly = displayMonthlySummary.take(monthlyItemsShown)
                    items(visibleMonthly) { summary ->
                        MonthlyItem(summary)
                    }

                    // "Show more" button if there are more months
                    if (monthlyItemsShown < displayMonthlySummary.size) {
                        item {
                            TextButton(
                                onClick = { monthlyItemsShown += monthlyPageSize },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "${strings.showMore} (${displayMonthlySummary.size - monthlyItemsShown} ${strings.more})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

    // Category Details Dialog
    selectedCategoryForDetails?.let { category ->
        val categoryTransactions = remember(transactions, category, selectedPeriod, spendingEpochStart, spendingEpochEnd) {
            filterByTimePeriod(transactions, selectedPeriod, spendingEpochStart, spendingEpochEnd)
                .filter { it.category == category && it.amount < 0 }
                .sortedBy { it.amount }
                .take(10)
        }

        CategoryDetailsDialog(
            category = category,
            transactions = categoryTransactions,
            onDismiss = { selectedCategoryForDetails = null }
        )
    }
}

/**
 * Category Details Dialog showing top transactions
 */
@Composable
private fun CategoryDetailsDialog(
    category: TransactionCategory,
    transactions: List<TransactionDisplay>,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = getCategoryEmoji(category),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = category.getLocalizedName(strings),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = strings.topTransactions,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (transactions.isEmpty()) {
                    Text(
                        text = strings.noSpendingData,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // Scrollable transaction list with max height
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(transactions.size) { index ->
                            val tx = transactions[index]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${index + 1}. ${tx.counterparty ?: tx.description}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = tx.date,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = formatCurrency(tx.amount),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = AppColors.Expenses
                                )
                            }
                        }
                    }

                    // Total
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Total (${transactions.size} shown)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = formatCurrency(transactions.sumOf { it.amount }),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.Expenses
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Close button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(strings.close)
                }
            }
        }
    }
}

/**
 * Pie chart showing spending by category
 */
@Composable
private fun SpendingPieChart(
    categorySpending: List<CategorySpending>,
    totalExpenses: Double
) {
    val strings = LocalStrings.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = strings.spendingByCategory,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Donut chart
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 40.dp.toPx()
                    var startAngle = -90f

                    categorySpending.forEach { spending ->
                        val sweepAngle = (spending.percentage / 100f) * 360f
                        val color = parseHexColor(spending.category.color)

                        drawArc(
                            color = color,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                            topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                            size = Size(
                                size.width - strokeWidth,
                                size.height - strokeWidth
                            )
                        )
                        startAngle += sweepAngle
                    }
                }

                // Center text
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = strings.expenses,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatCurrency(totalExpenses),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Expenses
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Legend
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categorySpending.sortedByDescending { it.percentage }.forEach { spending ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(parseHexColor(spending.category.color))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = getCategoryEmoji(spending.category),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = spending.category.getLocalizedName(strings),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${spending.percentage.toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatCurrency(spending.totalAmount),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// Shared helpers — internal so sibling files in the ui package
// (SpendingListItems.kt, MerchantSpendingItems.kt) can reuse them.
// ============================================================

/**
 * Filter transactions by time period. CUSTOM uses epoch-second bounds.
 */
internal fun filterByTimePeriod(
    transactions: List<TransactionDisplay>,
    period: TimePeriod,
    customStartEpoch: Long? = null,
    customEndEpoch: Long? = null
): List<TransactionDisplay> {
    if (period == TimePeriod.ALL) return transactions

    val tz = TimeZone.currentSystemDefault()
    val today = Clock.System.todayIn(tz)
    val currentYear = today.year
    val currentMonth = today.monthNumber
    val currentDay = today.dayOfMonth

    if (period == TimePeriod.CUSTOM) {
        if (customStartEpoch == null || customEndEpoch == null) return transactions
        return transactions.filter { tx ->
            try {
                val parts = tx.date.split(".")
                if (parts.size != 3) return@filter true
                val day = parts[0].toIntOrNull() ?: return@filter true
                val month = parts[1].toIntOrNull() ?: return@filter true
                val year = parts[2].toIntOrNull() ?: return@filter true
                val txEpoch = LocalDate(year, month, day).atStartOfDayIn(tz).epochSeconds
                txEpoch in customStartEpoch..customEndEpoch
            } catch (e: Exception) { true }
        }
    }

    return transactions.filter { tx ->
        try {
            val parts = tx.date.split(".")
            if (parts.size != 3) return@filter true
            val day = parts[0].toIntOrNull() ?: return@filter true
            val month = parts[1].toIntOrNull() ?: return@filter true
            val year = parts[2].toIntOrNull() ?: return@filter true

            val txDays = year * 365 + month * 30 + day
            val currentDays = currentYear * 365 + currentMonth * 30 + currentDay
            val daysAgo = currentDays - txDays

            when (period) {
                TimePeriod.WEEK -> daysAgo in 0..7
                TimePeriod.MONTH -> daysAgo in 0..30
                TimePeriod.YEAR -> daysAgo in 0..365
                else -> true
            }
        } catch (e: Exception) { true }
    }
}

/**
 * Helper data class for filtered spending calculations.
 */
internal data class FilteredSpendingData(
    val income: Double,
    val expenses: Double,
    val categorySpending: List<CategorySpending>,
    val monthlySummary: List<MonthlySummary>
)

/**
 * Format month string for display.
 */
internal fun formatMonthDisplay(yearMonth: String): String {
    val parts = yearMonth.split("-")
    if (parts.size != 2) return yearMonth
    val monthNames = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )
    val monthIndex = parts[1].toIntOrNull()?.minus(1) ?: return yearMonth
    return if (monthIndex in 0..11) {
        "${monthNames[monthIndex]} ${parts[0]}"
    } else yearMonth
}

internal fun formatCurrency(amount: Double): String {
    val absAmount = kotlin.math.abs(amount)
    val rounded = kotlin.math.round(absAmount * 100) / 100
    val formatted = rounded.toString().replace(".", ",")
    return if (amount >= 0) "+$formatted €" else "-$formatted €"
}

internal fun parseHexColor(hexColor: String): Color {
    return try {
        val hex = hexColor.removePrefix("#")
        val r = hex.substring(0, 2).toInt(16)
        val g = hex.substring(2, 4).toInt(16)
        val b = hex.substring(4, 6).toInt(16)
        Color(r, g, b)
    } catch (e: Exception) {
        Color.Gray
    }
}

internal fun getCategoryEmoji(category: TransactionCategory): String {
    return when (category) {
        TransactionCategory.RENT -> "🏠"
        TransactionCategory.TRANSPORT -> "🚌"
        TransactionCategory.SUPERMARKET -> "🛒"
        TransactionCategory.RESTAURANT -> "🍽️"
        TransactionCategory.SHOPPING -> "🛍️"
        TransactionCategory.HEALTH -> "💊"
        TransactionCategory.INSURANCE -> "🛡️"
        TransactionCategory.ENTERTAINMENT -> "🎬"
        TransactionCategory.SUBSCRIPTIONS -> "📱"
        TransactionCategory.INVESTMENT -> "📈"
        TransactionCategory.TRAVEL -> "✈️"
        TransactionCategory.SALARY -> "💰"
        TransactionCategory.REFUND -> "↩️"
        TransactionCategory.TRANSFER -> "↔️"
        TransactionCategory.EDUCATION -> "🎓"
        TransactionCategory.TAXES -> "📋"
        TransactionCategory.OTHER -> "❓"
    }
}
