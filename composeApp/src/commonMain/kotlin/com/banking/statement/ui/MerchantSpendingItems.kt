package com.banking.statement.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.banking.statement.LocalStrings

// ============================================================
// Merchants tab items and their data aggregators.
// Uses helpers (formatCurrency, getCategoryEmoji) from
// SpendingOverviewScreen.kt (internal, same package).
// ============================================================

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
 * Calculate merchant trends from transactions.
 */
fun calculateMerchantTrendsFromTransactions(transactions: List<TransactionDisplay>): List<MerchantTrend> {
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
 * Calculate merchant spending history from transactions.
 */
fun calculateMerchantHistoryFromTransactions(transactions: List<TransactionDisplay>): List<MerchantHistory> {
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
 * Merchant history item showing spending across all months.
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
 * Format month string (YYYY-MM) to readable format.
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
