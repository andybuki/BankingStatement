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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.banking.statement.LocalStrings
import com.banking.statement.ui.CategorySpending
import com.banking.statement.ui.theme.AppColors
import kotlin.math.abs
import kotlin.math.absoluteValue

/**
 * Donut chart showing category spending breakdown.
 */
@Composable
fun CategorySpendingDonutChart(
    categorySpending: List<CategorySpending>,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    // Cache computed values to avoid recalculation on every recomposition
    val totalExpenses = remember(categorySpending) {
        categorySpending.sumOf { it.totalAmount.absoluteValue }
    }
    val sortedSpending = remember(categorySpending) {
        categorySpending.sortedByDescending { abs(it.totalAmount) }
    }
    val legendItems = remember(sortedSpending) {
        sortedSpending.take(8)
    }
    // Pre-parse colors for the donut chart arcs
    val arcData = remember(sortedSpending) {
        sortedSpending.map { spending ->
            Triple(
                parseColor(spending.category.color),
                (spending.percentage / 100f) * 360f,
                spending
            )
        }
    }

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

                    arcData.forEach { (color, sweepAngle, _) ->
                        drawArc(
                            color = color,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle.coerceAtLeast(1f),
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
                        color = AppColors.Expenses
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Legend with percentages
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                legendItems.forEach { spending ->
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

/**
 * Horizontal bars showing category breakdown (alternative to pie chart).
 */
@Composable
fun CategoryBarsChart(
    categorySpending: List<CategorySpending>,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val topSpending = remember(categorySpending) {
        categorySpending.sortedByDescending { abs(it.totalAmount) }.take(8)
    }
    Column(modifier = modifier) {
        topSpending.forEach { spending ->
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
                    Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        drawRoundRect(
                            color = parseColor(spending.category.color),
                            size = size.copy(width = size.width * (spending.percentage / 100f)),
                            cornerRadius = CornerRadius(4.dp.toPx())
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
 * Category legend showing color and name.
 */
@Composable
fun CategoryLegend(
    categorySpending: List<CategorySpending>,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val legendRows = remember(categorySpending) {
        categorySpending.sortedByDescending { abs(it.totalAmount) }.take(8).chunked(2)
    }
    Column(modifier = modifier) {
        legendRows.forEach { row ->
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
 * Single legend item with colored box and label.
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
            Canvas(modifier = Modifier.fillMaxSize()) {
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
 * Stacked area chart showing how category spending proportions change over time.
 */
@Composable
fun CategoryStackedAreaChart(
    monthlyBreakdown: List<MonthCategoryBreakdown>,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    if (monthlyBreakdown.size < 2) return

    // Get all categories across all months, sorted by total spending
    val allCategories = remember(monthlyBreakdown) {
        monthlyBreakdown
            .flatMap { it.categoryAmounts }
            .groupBy { it.first }
            .mapValues { (_, pairs) -> pairs.sumOf { it.second } }
            .entries
            .sortedByDescending { it.value }
            .take(6)
            .map { it.key }
    }

    // Pre-parse category colors
    val categoryColors = remember(allCategories) {
        allCategories.map { it to parseColor(it.color) }
    }

    // Build stacked values per month: for each month, cumulative amounts per category
    val stackedData = remember(monthlyBreakdown, allCategories) {
        monthlyBreakdown.map { month ->
            val amountMap = month.categoryAmounts.toMap()
            val values = allCategories.map { cat -> amountMap[cat] ?: 0.0 }
            // cumulative sums for stacking
            val cumulative = mutableListOf<Double>()
            var sum = 0.0
            values.forEach { v ->
                sum += v
                cumulative.add(sum)
            }
            cumulative
        }
    }

    val maxValue = remember(stackedData) {
        stackedData.maxOfOrNull { it.lastOrNull() ?: 0.0 } ?: 0.0
    }

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
                text = strings.categoryTrends,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (maxValue > 0) {
                val gridColor = MaterialTheme.colorScheme.outlineVariant

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                ) {
                    val padding = 40.dp.toPx()
                    val chartWidth = size.width - padding * 2
                    val chartHeight = size.height - padding * 2
                    val monthCount = stackedData.size

                    // Draw grid lines
                    for (i in 0..4) {
                        val y = padding + (chartHeight * i / 4)
                        drawLine(
                            color = gridColor,
                            start = Offset(padding, y),
                            end = Offset(size.width - padding, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    val stepX = chartWidth / (monthCount - 1).coerceAtLeast(1)

                    // Draw stacked areas from top category to bottom (reverse to draw back layers first)
                    for (catIndex in allCategories.indices.reversed()) {
                        val color = categoryColors[catIndex].second.copy(alpha = 0.6f)
                        val borderColor = categoryColors[catIndex].second

                        val path = Path()
                        val borderPath = Path()

                        // Start from bottom-left
                        val baselineY = { monthIdx: Int ->
                            if (catIndex == 0) {
                                padding + chartHeight
                            } else {
                                val prevCum = stackedData[monthIdx][catIndex - 1]
                                padding + chartHeight - (prevCum / maxValue * chartHeight).toFloat()
                            }
                        }

                        val topY = { monthIdx: Int ->
                            val cum = stackedData[monthIdx][catIndex]
                            padding + chartHeight - (cum / maxValue * chartHeight).toFloat()
                        }

                        // Build filled area path
                        // Top edge (left to right)
                        path.moveTo(padding, topY(0))
                        borderPath.moveTo(padding, topY(0))
                        for (i in 1 until monthCount) {
                            val x = padding + i * stepX
                            path.lineTo(x, topY(i))
                            borderPath.lineTo(x, topY(i))
                        }

                        // Bottom edge (right to left)
                        for (i in (monthCount - 1) downTo 0) {
                            val x = padding + i * stepX
                            path.lineTo(x, baselineY(i))
                        }
                        path.close()

                        drawPath(path = path, color = color)
                        drawPath(
                            path = borderPath,
                            color = borderColor,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Month labels
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    monthlyBreakdown.forEach { month ->
                        Text(
                            text = month.month.takeLast(3),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Legend
                val legendRows = categoryColors.chunked(3)
                legendRows.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { (category, color) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(color)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = category.getLocalizedName(strings),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        // Fill remaining space if not full row
                        repeat(3 - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
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
