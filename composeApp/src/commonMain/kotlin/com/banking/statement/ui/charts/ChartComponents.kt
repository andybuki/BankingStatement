package com.banking.statement.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import com.banking.statement.LocalStrings
import com.banking.statement.categorization.TransactionCategory
import com.banking.statement.ui.CategorySpending
import com.banking.statement.ui.MonthlySummary
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import kotlin.math.abs
import kotlin.math.absoluteValue

/**
 * Donut chart showing category spending breakdown
 */
@Composable
fun CategorySpendingDonutChart(
    categorySpending: List<CategorySpending>,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val totalExpenses = categorySpending.sumOf { it.totalAmount.absoluteValue }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = strings.spendingByCategory,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Actual Donut Chart
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 40.dp.toPx()
                    var startAngle = -90f

                    // Sort by amount for better visual
                    val sortedSpending = categorySpending.sortedByDescending { abs(it.totalAmount) }

                    sortedSpending.forEach { spending ->
                        val sweepAngle = (spending.percentage / 100f) * 360f
                        val color = parseColor(spending.category.color)

                        drawArc(
                            color = color,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle.coerceAtLeast(1f), // Minimum 1 degree for visibility
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
                        text = formatCurrencyChart(totalExpenses),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE57373)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Legend with percentages
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categorySpending.sortedByDescending { abs(it.totalAmount) }.take(8).forEach { spending ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(parseColor(spending.category.color))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
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
                            text = formatCurrencyChart(spending.totalAmount.absoluteValue),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

private fun formatCurrencyChart(amount: Double): String {
    val formatted = "%.2f".format(amount).replace(".", ",")
    return "-$formatted €"
}

/**
 * Horizontal bars showing category breakdown (alternative to pie chart)
 */
@Composable
fun CategoryBarsChart(
    categorySpending: List<CategorySpending>,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    Column(modifier = modifier) {
        val sortedSpending = categorySpending.sortedByDescending { abs(it.totalAmount) }

        sortedSpending.take(8).forEach { spending ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = spending.category.getLocalizedName(strings),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(0.3f)
                )

                // Progress bar
                Box(
                    modifier = Modifier
                        .weight(0.5f)
                        .height(20.dp)
                ) {
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        drawRoundRect(
                            color = parseColor(spending.category.color),
                            size = size.copy(width = size.width * (spending.percentage / 100f)),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                        )
                    }
                }

                Text(
                    text = "${spending.percentage.toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(0.2f)
                )
            }
        }
    }
}

/**
 * Line chart showing monthly spending trends with income and expenses
 */
@Composable
fun MonthlySpendingLineChart(
    monthlySummary: List<MonthlySummary>,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Monthly Trend",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (monthlySummary.size >= 1) {
                val modelProducer = remember { CartesianChartModelProducer() }
                val reversed = remember(monthlySummary) { monthlySummary.reversed() }

                LaunchedEffect(monthlySummary) {
                    val incomeData = reversed.map { it.income }
                    val expensesData = reversed.map { abs(it.expenses) }
                    modelProducer.runTransaction {
                        lineSeries {
                            series(incomeData)   // Green line for income
                            series(expensesData) // Red line for expenses
                        }
                    }
                }

                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberLineCartesianLayer(),
                        startAxis = rememberStartAxis(),
                        bottomAxis = rememberBottomAxis(
                            valueFormatter = { value, _, _ ->
                                reversed.getOrNull(value.toInt())?.month ?: ""
                            }
                        )
                    ),
                    modelProducer = modelProducer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    LegendItem(
                        color = Color(0xFF4CAF50),
                        label = strings.income
                    )
                    Spacer(modifier = Modifier.width(24.dp))
                    LegendItem(
                        color = Color(0xFFE57373),
                        label = strings.expenses
                    )
                }

                // Monthly details summary
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    reversed.forEach { month ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = month.month,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "+${formatAmountShort(month.income)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF4CAF50)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = formatAmountShort(month.expenses),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFE57373)
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "No data available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatAmountShort(amount: Double): String {
    val absAmount = abs(amount)
    val formatted = "%.0f€".format(absAmount)
    return if (amount < 0) "-$formatted" else formatted
}

/**
 * Bar chart comparing income vs expenses - grouped bars for each month
 */
@Composable
fun IncomeVsExpensesBarChart(
    monthlySummary: List<MonthlySummary>,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Income vs Expenses",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (monthlySummary.isNotEmpty()) {
                val modelProducer = remember { CartesianChartModelProducer() }
                val reversed = remember(monthlySummary) { monthlySummary.reversed() }

                LaunchedEffect(monthlySummary) {
                    val incomeData = reversed.map { it.income }
                    val expensesData = reversed.map { abs(it.expenses) }

                    modelProducer.runTransaction {
                        columnSeries {
                            // Separate series for income and expenses = grouped bars
                            series(incomeData)
                            series(expensesData)
                        }
                    }
                }

                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberColumnCartesianLayer(),
                        startAxis = rememberStartAxis(),
                        bottomAxis = rememberBottomAxis(
                            valueFormatter = { value, _, _ ->
                                reversed.getOrNull(value.toInt())?.month ?: ""
                            }
                        )
                    ),
                    modelProducer = modelProducer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    LegendItem(
                        color = Color(0xFF4CAF50),
                        label = strings.income
                    )
                    Spacer(modifier = Modifier.width(24.dp))
                    LegendItem(
                        color = Color(0xFFE57373),
                        label = strings.expenses
                    )
                }

                // Monthly comparison details
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    reversed.forEach { month ->
                        val netAmount = month.income + month.expenses
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = month.month,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(0.3f)
                            )
                            Row(
                                modifier = Modifier.weight(0.7f),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = "+${formatAmountShort(month.income)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF4CAF50)
                                )
                                Text(
                                    text = " / ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatAmountShort(month.expenses),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFE57373)
                                )
                                Text(
                                    text = " = ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (netAmount >= 0) "+${formatAmountShort(netAmount)}" else formatAmountShort(netAmount),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (netAmount >= 0) Color(0xFF4CAF50) else Color(0xFFE57373)
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "No data available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Category legend showing color and name
 */
@Composable
fun CategoryLegend(
    categorySpending: List<CategorySpending>,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    Column(modifier = modifier) {
        val sortedSpending = categorySpending.sortedByDescending { abs(it.totalAmount) }

        sortedSpending.take(8).chunked(2).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                row.forEach { spending ->
                    LegendItem(
                        color = parseColor(spending.category.color),
                        label = spending.category.getLocalizedName(strings),
                        modifier = Modifier.weight(1f)
                    )
                }
                // Fill remaining space if odd number of items
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Single legend item with colored box and label
 */
@Composable
fun LegendItem(
    color: Color,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(end = 8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .padding(top = 4.dp)
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(color)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Parse hex color string to Color (cross-platform)
 */
fun parseColor(colorString: String): Color {
    return try {
        val cleanColor = colorString.removePrefix("#")
        val colorInt = cleanColor.toLong(16)

        when (cleanColor.length) {
            6 -> {
                // RGB format: #RRGGBB
                val r = ((colorInt shr 16) and 0xFF) / 255f
                val g = ((colorInt shr 8) and 0xFF) / 255f
                val b = (colorInt and 0xFF) / 255f
                Color(r, g, b)
            }
            8 -> {
                // ARGB format: #AARRGGBB
                val a = ((colorInt shr 24) and 0xFF) / 255f
                val r = ((colorInt shr 16) and 0xFF) / 255f
                val g = ((colorInt shr 8) and 0xFF) / 255f
                val b = (colorInt and 0xFF) / 255f
                Color(r, g, b, a)
            }
            else -> Color.Gray
        }
    } catch (e: Exception) {
        Color.Gray
    }
}
