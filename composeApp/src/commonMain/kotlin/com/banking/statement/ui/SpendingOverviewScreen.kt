package com.banking.statement.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import bankingstatement.composeapp.generated.resources.Res
import bankingstatement.composeapp.generated.resources.back
import bankingstatement.composeapp.generated.resources.share
import com.banking.statement.LocalStrings
import com.banking.statement.ui.theme.AppColors
import com.banking.statement.categorization.TransactionCategory
import com.banking.statement.export.ExportFormat
import com.banking.statement.export.SpendingExportData
import com.banking.statement.ui.charts.CategorySpendingDonutChart
import com.banking.statement.ui.charts.IncomeVsExpensesBarChart
import com.banking.statement.ui.charts.MonthlySpendingLineChart
import org.jetbrains.compose.resources.painterResource
import kotlin.math.absoluteValue
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Time period for filtering spending
 */
enum class TimePeriod {
    WEEK, MONTH, YEAR, ALL, CUSTOM
}

/**
 * Custom date range for filtering
 */
data class CustomDateRange(
    val startDate: String? = null, // DD.MM.YYYY format
    val endDate: String? = null    // DD.MM.YYYY format
)

/**
 * Category spending data for display
 */
data class CategorySpending(
    val category: TransactionCategory,
    val totalAmount: Double,
    val transactionCount: Int,
    val percentage: Float,
    val trend: SpendingTrend? = null
)

/**
 * Spending trend comparison data
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
 * Monthly category spending for trend analysis
 */
data class CategoryMonthlySpending(
    val month: String,
    val category: TransactionCategory,
    val amount: Double
)

/**
 * Monthly summary data
 */
data class MonthlySummary(
    val month: String,
    val income: Double,
    val expenses: Double
) {
    val netAmount: Double get() = income + expenses // expenses are negative
}

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
    onTimePeriodChange: ((String) -> Unit)? = null  // Callback for custom date
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

    // Custom date range state
    var customDateRange by remember { mutableStateOf(CustomDateRange()) }
    var showDateRangeDialog by remember { mutableStateOf(false) }

    // Category drill-down state
    var selectedCategoryForDetails by remember { mutableStateOf<TransactionCategory?>(null) }

    // Auto-open date picker when custom is selected
    LaunchedEffect(selectedPeriod) {
        if (selectedPeriod == TimePeriod.CUSTOM && customDateRange.startDate == null) {
            showDateRangeDialog = true
        }
    }

    // Filter transactions based on selected account and time period
    val filteredData = remember(transactions, selectedAccountId, selectedPeriod, customDateRange) {
        var filtered = if (selectedAccountId == null) {
            transactions
        } else {
            transactions.filter { it.accountId == selectedAccountId }
        }

        // Apply time period filter
        filtered = filterByTimePeriod(filtered, selectedPeriod, customDateRange)

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

    // Use filtered data
    val displayIncome = filteredData.income
    val displayExpenses = filteredData.expenses
    val displayCategorySpending = filteredData.categorySpending
    val displayMonthlySummary = filteredData.monthlySummary

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
            // Custom date range display and dialog trigger (time period selector is in App header)
            item {
                // Show selected custom date range when Custom is selected
                if (selectedPeriod == TimePeriod.CUSTOM) {
                    if (customDateRange.startDate != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                                .clickable { showDateRangeDialog = true }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📅 ${customDateRange.startDate ?: ""} - ${customDateRange.endDate ?: ""}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        // Show prompt to select date range
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                                .clickable { showDateRangeDialog = true }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📅 ${strings.selectDateRange}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (netAmount >= 0) {
                            AppColors.Income.copy(alpha = 0.1f)
                        } else {
                            AppColors.Expenses.copy(alpha = 0.1f)
                        }
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.netBalance,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = formatCurrency(netAmount),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (netAmount >= 0) AppColors.Income else AppColors.Expenses
                        )
                    }
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

                // Monthly summary title
                if (displayMonthlySummary.isNotEmpty()) {
                    item {
                        Text(
                            text = strings.monthlySummary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }

                    items(displayMonthlySummary) { summary ->
                        MonthlyItem(summary)
                    }
                }
            }

            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

    // Custom Date Range Dialog
    if (showDateRangeDialog) {
        DateRangePickerDialog(
            currentRange = customDateRange,
            onDismiss = { showDateRangeDialog = false },
            onApply = { range ->
                customDateRange = range
                showDateRangeDialog = false
            }
        )
    }

    // Category Details Dialog
    selectedCategoryForDetails?.let { category ->
        val categoryTransactions = remember(transactions, category, selectedPeriod, customDateRange) {
            filterByTimePeriod(transactions, selectedPeriod, customDateRange)
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
 * Date Range Picker Dialog
 */
@Composable
private fun DateRangePickerDialog(
    currentRange: CustomDateRange,
    onDismiss: () -> Unit,
    onApply: (CustomDateRange) -> Unit
) {
    val strings = LocalStrings.current
    var startDate by remember { mutableStateOf(currentRange.startDate ?: "") }
    var endDate by remember { mutableStateOf(currentRange.endDate ?: "") }
    var startDateError by remember { mutableStateOf(false) }
    var endDateError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = strings.selectDateRange,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Start Date
                OutlinedTextField(
                    value = startDate,
                    onValueChange = {
                        startDate = it
                        startDateError = !isValidDate(it) && it.isNotEmpty()
                    },
                    label = { Text(strings.startDate) },
                    placeholder = { Text("DD.MM.YYYY") },
                    isError = startDateError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // End Date
                OutlinedTextField(
                    value = endDate,
                    onValueChange = {
                        endDate = it
                        endDateError = !isValidDate(it) && it.isNotEmpty()
                    },
                    label = { Text(strings.endDate) },
                    placeholder = { Text("DD.MM.YYYY") },
                    isError = endDateError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (startDateError || endDateError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Format: DD.MM.YYYY (e.g., 01.01.2024)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(strings.cancel)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (isValidDate(startDate) && isValidDate(endDate)) {
                                onApply(CustomDateRange(startDate, endDate))
                            }
                        },
                        enabled = isValidDate(startDate) && isValidDate(endDate)
                    ) {
                        Text(strings.apply)
                    }
                }
            }
        }
    }
}

/**
 * Validate date string format DD.MM.YYYY
 */
private fun isValidDate(date: String): Boolean {
    if (date.isBlank()) return false
    val parts = date.split(".")
    if (parts.size != 3) return false
    val day = parts[0].toIntOrNull() ?: return false
    val month = parts[1].toIntOrNull() ?: return false
    val year = parts[2].toIntOrNull() ?: return false
    return day in 1..31 && month in 1..12 && year in 1900..2100
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

/**
 * Filter transactions by time period
 */
private fun filterByTimePeriod(
    transactions: List<TransactionDisplay>,
    period: TimePeriod,
    customRange: CustomDateRange = CustomDateRange()
): List<TransactionDisplay> {
    if (period == TimePeriod.ALL) return transactions

    // Get actual current date from system
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val currentYear = today.year
    val currentMonth = today.monthNumber
    val currentDay = today.dayOfMonth

    // Since we're working with date strings in DD.MM.YYYY format
    // we need to parse and compare them
    return transactions.filter { tx ->
        try {
            val parts = tx.date.split(".")
            if (parts.size != 3) return@filter true

            val day = parts[0].toIntOrNull() ?: return@filter true
            val month = parts[1].toIntOrNull() ?: return@filter true
            val year = parts[2].toIntOrNull() ?: return@filter true

            when (period) {
                TimePeriod.CUSTOM -> {
                    // Filter by custom date range
                    if (customRange.startDate == null || customRange.endDate == null) {
                        return@filter true
                    }

                    val txDayValue = year * 10000 + month * 100 + day

                    val startParts = customRange.startDate.split(".")
                    val startDay = startParts[0].toIntOrNull() ?: return@filter true
                    val startMonth = startParts[1].toIntOrNull() ?: return@filter true
                    val startYear = startParts[2].toIntOrNull() ?: return@filter true
                    val startDayValue = startYear * 10000 + startMonth * 100 + startDay

                    val endParts = customRange.endDate.split(".")
                    val endDay = endParts[0].toIntOrNull() ?: return@filter true
                    val endMonth = endParts[1].toIntOrNull() ?: return@filter true
                    val endYear = endParts[2].toIntOrNull() ?: return@filter true
                    val endDayValue = endYear * 10000 + endMonth * 100 + endDay

                    txDayValue in startDayValue..endDayValue
                }
                else -> {
                    // Calculate days ago (simplified - assumes 30 days per month)
                    val txDays = year * 365 + month * 30 + day
                    val currentDays = currentYear * 365 + currentMonth * 30 + currentDay
                    val daysAgo = currentDays - txDays

                    when (period) {
                        TimePeriod.WEEK -> daysAgo in 0..7
                        TimePeriod.MONTH -> daysAgo in 0..30
                        TimePeriod.YEAR -> daysAgo in 0..365
                        TimePeriod.ALL -> true
                        TimePeriod.CUSTOM -> true // Already handled above
                    }
                }
            }
        } catch (e: Exception) {
            true // Include transaction if date parsing fails
        }
    }
}

/**
 * Helper data class for filtered spending calculations
 */
private data class FilteredSpendingData(
    val income: Double,
    val expenses: Double,
    val categorySpending: List<CategorySpending>,
    val monthlySummary: List<MonthlySummary>
)

/**
 * Format month string for display
 */
private fun formatMonthDisplay(yearMonth: String): String {
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

@Composable
fun SummaryCard(
    title: String,
    amount: Double,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatCurrency(amount),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun CategorySpendingItem(
    spending: CategorySpending,
    onClick: (() -> Unit)? = null
) {
    val strings = LocalStrings.current
    val categoryColor = parseHexColor(spending.category.color)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = getCategoryEmoji(spending.category),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = spending.category.getLocalizedName(strings),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${spending.transactionCount} ${strings.transactions}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatCurrency(spending.totalAmount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${spending.percentage.toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Show trend indicator
                        spending.trend?.let { trend ->
                            Text(
                                text = "${TrendCalculator.getTrendIndicator(trend)} ${TrendCalculator.formatTrendPercentage(trend)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = TrendCalculator.getTrendColor(trend, MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(spending.percentage / 100f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(categoryColor)
                )
            }

            // Trend details
            spending.trend?.let { trend ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "vs last month:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "${formatCurrency(trend.previousAmount)} → ${formatCurrency(trend.currentAmount)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Clickable hint
            if (onClick != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = strings.tapToViewTopTransactions,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun MonthlyItem(summary: MonthlySummary) {
    val strings = LocalStrings.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = summary.month,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = strings.income,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatCurrency(summary.income),
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppColors.Income
                    )
                }
                Column {
                    Text(
                        text = strings.expenses,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatCurrency(summary.expenses),
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppColors.Expenses
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = strings.net,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatCurrency(summary.netAmount),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (summary.netAmount >= 0) AppColors.Income else AppColors.Expenses
                    )
                }
            }
        }
    }
}

/*internal fun formatCurrency(amount: Double): String {
    val absAmount = amount.absoluteValue
    val formatted = "%.2f".format(absAmount).replace(".", ",")
    return if (amount >= 0) "+$formatted €" else "-$formatted €"
}*/

internal fun formatCurrency(amount: Double): String {
    val absAmount = kotlin.math.abs(amount)
    val rounded = kotlin.math.round(absAmount * 100) / 100
    val formatted = rounded.toString().replace(".", ",")
    return if (amount >= 0) "+$formatted €" else "-$formatted €"
}

private fun parseHexColor(hexColor: String): Color {
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

private fun getCategoryEmoji(category: TransactionCategory): String {
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
// Add at end of SpendingOverviewScreen.kt

@Composable
fun MerchantTrendItem(trend: MerchantTrend) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Merchant name
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = trend.merchantName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${trend.currentMonthTransactions} transactions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Trend indicator and amount
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatCurrency(trend.currentMonthAmount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${MerchantTrendCalculator.getTrendIndicator(trend)} ${MerchantTrendCalculator.formatTrendPercentage(trend)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MerchantTrendCalculator.getTrendColor(trend, MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }

            // Comparison details
            if (trend.direction != MerchantTrend.TrendDirection.NEW) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "vs last month: ${formatCurrency(trend.previousMonthAmount)} → ${formatCurrency(trend.currentMonthAmount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Calculate merchant trends from transactions
 */
fun calculateMerchantTrendsFromTransactions(transactions: List<TransactionDisplay>): List<MerchantTrend> {
    // Group transactions by month and merchant
    val monthlyData = transactions
        .filter { it.amount < 0 && !it.counterparty.isNullOrBlank() }
        .groupBy { tx ->
            val dateParts = tx.date.split(".")
            if (dateParts.size >= 3) {
                "${dateParts[2]}-${dateParts[1]}" // YYYY-MM format
            } else {
                null
            }
        }
        .filterKeys { it != null }
        .flatMap { (month, txs) ->
            txs.groupBy { it.counterparty!! }
                .map { (merchant, merchantTxs) ->
                    MerchantMonthlyData(
                        month = month!!,
                        merchantName = merchant,
                        category = merchantTxs.first().category,
                        totalAmount = merchantTxs.sumOf { it.amount },
                        transactionCount = merchantTxs.size
                    )
                }
        }

    return MerchantTrendCalculator.calculateMerchantTrends(monthlyData, topN = 10)
}

/**
 * Calculate merchant spending history from transactions
 */
fun calculateMerchantHistoryFromTransactions(transactions: List<TransactionDisplay>): List<MerchantHistory> {
    // Group transactions by month and merchant
    val monthlyData = transactions
        .filter { it.amount < 0 && !it.counterparty.isNullOrBlank() }
        .groupBy { tx ->
            val dateParts = tx.date.split(".")
            if (dateParts.size >= 3) {
                "${dateParts[2]}-${dateParts[1]}" // YYYY-MM format
            } else {
                null
            }
        }
        .filterKeys { it != null }
        .flatMap { (month, txs) ->
            txs.groupBy { it.counterparty!! }
                .map { (merchant, merchantTxs) ->
                    MerchantMonthlyData(
                        month = month!!,
                        merchantName = merchant,
                        category = merchantTxs.first().category,
                        totalAmount = merchantTxs.sumOf { it.amount },
                        transactionCount = merchantTxs.size
                    )
                }
        }

    return calculateMerchantHistory(monthlyData, topN = 10)
}

/**
 * Merchant history item showing spending across all months
 */
@Composable
fun MerchantHistoryItem(history: MerchantHistory) {
    val strings = LocalStrings.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with merchant name and total
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = history.merchantName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${getCategoryEmoji(history.category)} ${history.category.getLocalizedName(strings)} • ${history.totalTransactions} ${strings.transactions}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatCurrency(history.totalSpending),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "${formatCurrency(history.averageMonthlySpending)}${strings.perMonthAverage}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Monthly breakdown
            if (history.monthlySpending.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = strings.monthlyBreakdown,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    history.monthlySpending.forEach { monthData ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatMonthYear(monthData.month),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${monthData.transactionCount}x",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatCurrency(monthData.amount),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Format month string (YYYY-MM) to readable format
 */
private fun formatMonthYear(month: String): String {
    val parts = month.split("-")
    if (parts.size != 2) return month

    val year = parts[0]
    val monthNum = parts[1].toIntOrNull() ?: return month

    val monthNames = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    val monthName = monthNames.getOrNull(monthNum - 1) ?: return month
    return "$monthName $year"
}
