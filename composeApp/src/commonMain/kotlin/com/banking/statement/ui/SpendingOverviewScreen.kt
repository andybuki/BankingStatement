package com.banking.statement.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import bankingstatement.composeapp.generated.resources.Res
import bankingstatement.composeapp.generated.resources.back
import bankingstatement.composeapp.generated.resources.share
import com.banking.statement.LocalStrings
import com.banking.statement.categorization.TransactionCategory
import com.banking.statement.export.ExportFormat
import com.banking.statement.export.SpendingExportData
import org.jetbrains.compose.resources.painterResource
import kotlin.math.absoluteValue

/**
 * Time period for filtering spending
 */
enum class TimePeriod {
    WEEK, MONTH, YEAR, ALL
}

/**
 * Category spending data for display
 */
data class CategorySpending(
    val category: TransactionCategory,
    val totalAmount: Double,
    val transactionCount: Int,
    val percentage: Float
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
    onBackClick: () -> Unit,
    onShare: ((ExportFormat, SpendingExportData) -> Unit)? = null
) {
    val strings = LocalStrings.current
    var selectedAccountId by remember { mutableStateOf<Long?>(null) }
    var selectedPeriod by remember { mutableStateOf(TimePeriod.ALL) }
    var showChartView by remember { mutableStateOf(false) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var shareMenuExpanded by remember { mutableStateOf(false) }

    // Filter transactions based on selected account and time period
    val filteredData = remember(transactions, selectedAccountId, selectedPeriod) {
        var filtered = if (selectedAccountId == null) {
            transactions
        } else {
            transactions.filter { it.accountId == selectedAccountId }
        }

        // Apply time period filter
        filtered = filterByTimePeriod(filtered, selectedPeriod)

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

    // Get selected account name for display
    val selectedAccountName = remember(selectedAccountId, accounts) {
        if (selectedAccountId == null) {
            strings.allAccounts
        } else {
            accounts.find { it.id == selectedAccountId }?.name ?: strings.allAccounts
        }
    }

    // Create export data for share menu
    val exportData = SpendingExportData(
        totalIncome = displayIncome,
        totalExpenses = displayExpenses,
        categorySpending = displayCategorySpending,
        monthlySummary = displayMonthlySummary
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = strings.spendingTitle,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Image(
                            painter = painterResource(Res.drawable.back),
                            contentDescription = strings.back,
                            modifier = Modifier.size(24.dp),
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                        )
                    }
                },
                actions = {
                    // Chart toggle button
                    if (displayCategorySpending.isNotEmpty()) {
                        IconButton(onClick = { showChartView = !showChartView }) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (showChartView) MaterialTheme.colorScheme.primaryContainer
                                        else Color.Transparent
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                // Simple pie chart icon using Canvas
                                Canvas(modifier = Modifier.size(18.dp)) {
                                    val strokeWidth = 3.dp.toPx()
                                    drawArc(
                                        color = Color(0xFF4CAF50),
                                        startAngle = -90f,
                                        sweepAngle = 120f,
                                        useCenter = false,
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                                        size = Size(size.width, size.height)
                                    )
                                    drawArc(
                                        color = Color(0xFFE57373),
                                        startAngle = 30f,
                                        sweepAngle = 100f,
                                        useCenter = false,
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                                        size = Size(size.width, size.height)
                                    )
                                    drawArc(
                                        color = Color(0xFF2196F3),
                                        startAngle = 130f,
                                        sweepAngle = 140f,
                                        useCenter = false,
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                                        size = Size(size.width, size.height)
                                    )
                                }
                            }
                        }
                    }

                    // Share button with dropdown
                    if (onShare != null && displayCategorySpending.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { shareMenuExpanded = true }) {
                                Image(
                                    painter = painterResource(Res.drawable.share),
                                    contentDescription = strings.share,
                                    modifier = Modifier.size(24.dp),
                                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                                )
                            }
                            DropdownMenu(
                                expanded = shareMenuExpanded,
                                onDismissRequest = { shareMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(strings.exportCsv) },
                                    onClick = {
                                        shareMenuExpanded = false
                                        onShare(ExportFormat.CSV, exportData)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.exportPdf) },
                                    onClick = {
                                        shareMenuExpanded = false
                                        onShare(ExportFormat.PDF, exportData)
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Account filter dropdown (only show if multiple accounts)
            if (accounts.size > 1) {
                item {
                    Box {
                        OutlinedButton(
                            onClick = { dropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = selectedAccountName,
                                modifier = Modifier.weight(1f)
                            )
                            Text(" ▼")
                        }

                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            // All Accounts option
                            DropdownMenuItem(
                                text = { Text(strings.allAccounts) },
                                onClick = {
                                    selectedAccountId = null
                                    dropdownExpanded = false
                                }
                            )
                            HorizontalDivider()
                            // Individual accounts
                            accounts.forEach { account ->
                                if (account.id != null) {
                                    DropdownMenuItem(
                                        text = { Text(account.name) },
                                        onClick = {
                                            selectedAccountId = account.id
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Time period toggle
            item {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TimePeriod.entries.forEachIndexed { index, period ->
                        SegmentedButton(
                            selected = selectedPeriod == period,
                            onClick = { selectedPeriod = period },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = TimePeriod.entries.size
                            )
                        ) {
                            Text(
                                text = when (period) {
                                    TimePeriod.WEEK -> strings.periodWeek
                                    TimePeriod.MONTH -> strings.periodMonth
                                    TimePeriod.YEAR -> strings.periodYear
                                    TimePeriod.ALL -> strings.periodAll
                                },
                                style = MaterialTheme.typography.labelMedium
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
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.weight(1f)
                    )
                    SummaryCard(
                        title = strings.expenses,
                        amount = displayExpenses,
                        color = Color(0xFFE57373),
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
                            Color(0xFF4CAF50).copy(alpha = 0.1f)
                        } else {
                            Color(0xFFE57373).copy(alpha = 0.1f)
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
                            color = if (netAmount >= 0) Color(0xFF4CAF50) else Color(0xFFE57373)
                        )
                    }
                }
            }

            // Chart view or list view
            if (showChartView && displayCategorySpending.isNotEmpty()) {
                // Pie Chart
                item {
                    SpendingPieChart(
                        categorySpending = displayCategorySpending.filter { it.totalAmount < 0 },
                        totalExpenses = displayExpenses
                    )
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
                        CategorySpendingItem(spending)
                    }
                }

                // Monthly summary title
                if (displayMonthlySummary.isNotEmpty()) {
                    item {
                        Text(
                            text = strings.monthlySummary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
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
                        color = Color(0xFFE57373)
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
                            text = spending.category.displayName,
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
    period: TimePeriod
): List<TransactionDisplay> {
    if (period == TimePeriod.ALL) return transactions

    // Get current date components
    // Since we're working with date strings in DD.MM.YYYY format
    // we need to parse and compare them
    return transactions.filter { tx ->
        try {
            val parts = tx.date.split(".")
            if (parts.size != 3) return@filter true

            val day = parts[0].toIntOrNull() ?: return@filter true
            val month = parts[1].toIntOrNull() ?: return@filter true
            val year = parts[2].toIntOrNull() ?: return@filter true

            // Calculate days ago (simplified - assumes 30 days per month)
            val currentYear = 2024 // This would ideally come from system
            val currentMonth = 12
            val currentDay = 19

            val txDays = year * 365 + month * 30 + day
            val currentDays = currentYear * 365 + currentMonth * 30 + currentDay
            val daysAgo = currentDays - txDays

            when (period) {
                TimePeriod.WEEK -> daysAgo <= 7
                TimePeriod.MONTH -> daysAgo <= 30
                TimePeriod.YEAR -> daysAgo <= 365
                TimePeriod.ALL -> true
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
fun CategorySpendingItem(spending: CategorySpending) {
    val categoryColor = parseHexColor(spending.category.color)

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
                            text = spending.category.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${spending.transactionCount} transactions",
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
                    Text(
                        text = "${spending.percentage.toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                        color = Color(0xFF4CAF50)
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
                        color = Color(0xFFE57373)
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
                        color = if (summary.netAmount >= 0) Color(0xFF4CAF50) else Color(0xFFE57373)
                    )
                }
            }
        }
    }
}

private fun formatCurrency(amount: Double): String {
    val absAmount = amount.absoluteValue
    val formatted = "%.2f".format(absAmount).replace(".", ",")
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
        TransactionCategory.UTILITIES -> "⚡"
        TransactionCategory.PUBLIC_TRANSPORT -> "🚇"
        TransactionCategory.CAR -> "🚗"
        TransactionCategory.SUPERMARKET -> "🛒"
        TransactionCategory.RESTAURANT -> "🍽️"
        TransactionCategory.SHOPPING -> "🛍️"
        TransactionCategory.HEALTH -> "💊"
        TransactionCategory.INSURANCE -> "🛡️"
        TransactionCategory.ENTERTAINMENT -> "🎬"
        TransactionCategory.SUBSCRIPTIONS -> "📱"
        TransactionCategory.PHONE_INTERNET -> "📞"
        TransactionCategory.BANK_FEES -> "🏦"
        TransactionCategory.INVESTMENT -> "📈"
        TransactionCategory.FITNESS -> "💪"
        TransactionCategory.SALARY -> "💰"
        TransactionCategory.REFUND -> "↩️"
        TransactionCategory.TRANSFER -> "↔️"
        TransactionCategory.CASH -> "💵"
        TransactionCategory.PAYMENT_SERVICE -> "💳"
        TransactionCategory.OTHER -> "❓"
    }
}
