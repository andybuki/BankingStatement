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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banking.statement.LocalStrings
import com.banking.statement.ui.MonthlySummary
import com.banking.statement.ui.theme.AppColors
import kotlin.math.abs

/**
 * Line chart showing monthly spending trends with income and expenses.
 * Custom Canvas implementation for cross-platform support.
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

            if (monthlySummary.isNotEmpty()) {
                val reversed = remember(monthlySummary) { monthlySummary.reversed() }
                val incomeData = remember(reversed) { reversed.map { it.income } }
                val expensesData = remember(reversed) { reversed.map { abs(it.expenses) } }
                val maxValue = remember(incomeData, expensesData) {
                    maxOf(incomeData.maxOrNull() ?: 0.0, expensesData.maxOrNull() ?: 0.0)
                }

                val incomeColor = AppColors.Income
                val expensesColor = AppColors.Expenses
                val gridColor = MaterialTheme.colorScheme.outlineVariant

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    val padding = 40.dp.toPx()
                    val chartWidth = size.width - padding * 2
                    val chartHeight = size.height - padding * 2

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

                    if (incomeData.size > 1 && maxValue > 0) {
                        val stepX = chartWidth / (incomeData.size - 1)

                        // Draw income line
                        val incomePath = Path()
                        incomeData.forEachIndexed { index, value ->
                            val x = padding + index * stepX
                            val y = padding + chartHeight - (value / maxValue * chartHeight).toFloat()
                            if (index == 0) {
                                incomePath.moveTo(x, y)
                            } else {
                                incomePath.lineTo(x, y)
                            }
                        }
                        drawPath(
                            path = incomePath,
                            color = incomeColor,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Draw expenses line
                        val expensesPath = Path()
                        expensesData.forEachIndexed { index, value ->
                            val x = padding + index * stepX
                            val y = padding + chartHeight - (value / maxValue * chartHeight).toFloat()
                            if (index == 0) {
                                expensesPath.moveTo(x, y)
                            } else {
                                expensesPath.lineTo(x, y)
                            }
                        }
                        drawPath(
                            path = expensesPath,
                            color = expensesColor,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Draw data points
                        incomeData.forEachIndexed { index, value ->
                            val x = padding + index * stepX
                            val y = padding + chartHeight - (value / maxValue * chartHeight).toFloat()
                            drawCircle(color = incomeColor, radius = 5.dp.toPx(), center = Offset(x, y))
                        }
                        expensesData.forEachIndexed { index, value ->
                            val x = padding + index * stepX
                            val y = padding + chartHeight - (value / maxValue * chartHeight).toFloat()
                            drawCircle(color = expensesColor, radius = 5.dp.toPx(), center = Offset(x, y))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    LegendItem(
                        color = AppColors.Income,
                        label = strings.income
                    )
                    Spacer(modifier = Modifier.width(24.dp))
                    LegendItem(
                        color = AppColors.Expenses,
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
                                color = AppColors.Income
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = formatAmountShort(month.expenses),
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.Expenses
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

/**
 * Bar chart comparing monthly income vs expenses.
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
                val reversed = remember(monthlySummary) { monthlySummary.reversed() }
                val incomeData = remember(reversed) { reversed.map { it.income } }
                val expensesData = remember(reversed) { reversed.map { abs(it.expenses) } }
                val maxValue = remember(incomeData, expensesData) {
                    maxOf(incomeData.maxOrNull() ?: 0.0, expensesData.maxOrNull() ?: 0.0)
                }

                val incomeColor = AppColors.Income
                val expensesColor = AppColors.Expenses
                val gridColor = MaterialTheme.colorScheme.outlineVariant

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    val padding = 40.dp.toPx()
                    val chartWidth = size.width - padding * 2
                    val chartHeight = size.height - padding * 2

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

                    if (incomeData.isNotEmpty() && maxValue > 0) {
                        val groupWidth = chartWidth / incomeData.size
                        val barWidth = groupWidth * 0.35f
                        val gap = groupWidth * 0.05f

                        incomeData.forEachIndexed { index, income ->
                            val expense = expensesData[index]
                            val groupStart = padding + index * groupWidth

                            // Income bar
                            val incomeHeight = (income / maxValue * chartHeight).toFloat()
                            drawRoundRect(
                                color = incomeColor,
                                topLeft = Offset(
                                    groupStart + gap,
                                    padding + chartHeight - incomeHeight
                                ),
                                size = Size(barWidth, incomeHeight),
                                cornerRadius = CornerRadius(4.dp.toPx())
                            )

                            // Expense bar
                            val expenseHeight = (expense / maxValue * chartHeight).toFloat()
                            drawRoundRect(
                                color = expensesColor,
                                topLeft = Offset(
                                    groupStart + barWidth + gap * 2,
                                    padding + chartHeight - expenseHeight
                                ),
                                size = Size(barWidth, expenseHeight),
                                cornerRadius = CornerRadius(4.dp.toPx())
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    LegendItem(
                        color = AppColors.Income,
                        label = strings.income
                    )
                    Spacer(modifier = Modifier.width(24.dp))
                    LegendItem(
                        color = AppColors.Expenses,
                        label = strings.expenses
                    )
                }

                // Monthly comparison details
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Pre-compute net amounts to avoid recalculation during recomposition
                    val monthDetails = remember(reversed) {
                        reversed.map { it to (it.income + it.expenses) }
                    }
                    monthDetails.forEach { (month, netAmount) ->
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
                                    color = AppColors.Income
                                )
                                Text(
                                    text = " / ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatAmountShort(month.expenses),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.Expenses
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
                                    color = if (netAmount >= 0) AppColors.Income else AppColors.Expenses
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
 * Horizontal bar chart showing top merchants by spending.
 */
@Composable
fun TopMerchantsBarChart(
    merchants: List<MerchantSpendingData>,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    if (merchants.isEmpty()) return

    val topMerchants = remember(merchants) {
        merchants.sortedByDescending { it.amount }.take(8)
    }
    val maxAmount = remember(topMerchants) {
        topMerchants.maxOfOrNull { it.amount } ?: 0.0
    }

    // Generate colors for merchants based on their position
    val barColors = remember {
        listOf(
            Color(0xFF2563EB), // Blue
            Color(0xFF7C3AED), // Purple
            Color(0xFF0891B2), // Cyan
            Color(0xFF059669), // Emerald
            Color(0xFFD97706), // Amber
            Color(0xFFDC2626), // Red
            Color(0xFF4F46E5), // Indigo
            Color(0xFFDB2777)  // Pink
        )
    }

    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .shadow(1.dp, shape, clip = false)
            .clip(shape)
            .background(AppColors.CardBackground)
            .padding(16.dp)
    ) {
        Text(
            text = strings.topMerchantsBySpending,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.TextPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (maxAmount > 0) {
            topMerchants.forEachIndexed { index, merchant ->
                val barColor = barColors[index % barColors.size]
                val fraction = (merchant.amount / maxAmount).toFloat()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = merchant.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppColors.TextPrimary,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatCurrencyChart(merchant.amount),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = AppColors.TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(AppColors.SurfaceTint)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(999.dp))
                                .background(barColor)
                        )
                    }

                    // Transaction count
                    Text(
                        text = "${merchant.transactionCount} ${strings.transactions.lowercase()}",
                        fontSize = 11.sp,
                        color = AppColors.TextTertiary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
