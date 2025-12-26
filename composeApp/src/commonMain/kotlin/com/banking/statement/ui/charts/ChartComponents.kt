package com.banking.statement.ui.charts

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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

/**
 * Donut chart showing category spending breakdown
 */
@Composable
fun CategorySpendingDonutChart(
    categorySpending: List<CategorySpending>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Spending by Category",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // For now, display as a simple bar chart since Vico's pie chart
            // implementation requires more complex setup
            // We'll use a horizontal bar chart as a visual alternative
            CategoryBarsChart(categorySpending, Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(16.dp))

            // Legend
            CategoryLegend(categorySpending)
        }
    }
}

/**
 * Horizontal bars showing category breakdown (alternative to pie chart)
 */
@Composable
fun CategoryBarsChart(
    categorySpending: List<CategorySpending>,
    modifier: Modifier = Modifier
) {
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
                    text = spending.category.displayName,
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
 * Line chart showing monthly spending trends
 */
@Composable
fun MonthlySpendingLineChart(
    monthlySummary: List<MonthlySummary>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Monthly Spending Trend",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (monthlySummary.isNotEmpty()) {
                val modelProducer = remember { CartesianChartModelProducer() }

                remember(monthlySummary) {
                    val expensesData = monthlySummary.reversed().map { abs(it.expenses) }
                    modelProducer.runTransaction {
                        lineSeries {
                            series(expensesData)
                        }
                    }
                }

                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberLineCartesianLayer(),
                        startAxis = rememberStartAxis(),
                        bottomAxis = rememberBottomAxis(
                            valueFormatter = { value, _, _ ->
                                monthlySummary.reversed().getOrNull(value.toInt())?.month?.take(3) ?: ""
                            }
                        )
                    ),
                    modelProducer = modelProducer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
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
 * Bar chart comparing income vs expenses
 */
@Composable
fun IncomeVsExpensesBarChart(
    monthlySummary: List<MonthlySummary>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Income vs Expenses",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (monthlySummary.isNotEmpty()) {
                val modelProducer = remember { CartesianChartModelProducer() }

                remember(monthlySummary) {
                    val reversed = monthlySummary.reversed()
                    val incomeData = reversed.map { it.income }
                    val expensesData = reversed.map { abs(it.expenses) }

                    modelProducer.runTransaction {
                        columnSeries {
                            series(incomeData, expensesData)
                        }
                    }
                }

                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberColumnCartesianLayer(),
                        startAxis = rememberStartAxis(),
                        bottomAxis = rememberBottomAxis(
                            valueFormatter = { value, _, _ ->
                                monthlySummary.reversed().getOrNull(value.toInt())?.month?.take(3) ?: ""
                            }
                        )
                    ),
                    modelProducer = modelProducer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    LegendItem(
                        color = Color(0xFF66BB6A),
                        label = "Income"
                    )
                    Spacer(modifier = Modifier.width(24.dp))
                    LegendItem(
                        color = Color(0xFFE57373),
                        label = "Expenses"
                    )
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
                        label = spending.category.displayName,
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
