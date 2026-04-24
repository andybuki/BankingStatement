package com.banking.statement.ui

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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banking.statement.LocalStrings
import com.banking.statement.ui.charts.MerchantSpendingData
import com.banking.statement.ui.charts.TopMerchantsBarChart
import com.banking.statement.ui.components.CategoryAvatar
import com.banking.statement.ui.components.EyebrowLabel
import com.banking.statement.ui.theme.AppColors
import com.banking.statement.ui.theme.AppElevations
import com.banking.statement.ui.theme.AppRadii
import com.banking.statement.ui.theme.AppSpacing
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * Dedicated screen for displaying top merchants and spending trends,
 * styled to match MoneyLupe MLMerchantsScreen (ui_kits/mobile/ScreensB.jsx).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantsScreen(
    transactions: List<TransactionDisplay>,
    accounts: List<AccountFilterOption> = emptyList(),
    selectedAccountId: Long? = null,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    val filteredTransactions = remember(transactions, selectedAccountId) {
        if (selectedAccountId == null) transactions
        else transactions.filter { it.accountId == selectedAccountId }
    }

    val merchantHistory = remember(filteredTransactions) {
        calculateMerchantHistoryFromTransactions(filteredTransactions)
    }

    val merchantTrends = remember(filteredTransactions) {
        calculateMerchantTrendsFromTransactions(filteredTransactions)
    }

    val merchantSpending = remember(filteredTransactions) {
        filteredTransactions
            .filter { it.amount < 0 && !it.counterparty.isNullOrBlank() }
            .groupBy { it.counterparty!! }
            .map { (name, txs) ->
                MerchantSpendingData(
                    name = name,
                    amount = txs.sumOf { it.amount.absoluteValue },
                    transactionCount = txs.size
                )
            }
            .sortedByDescending { it.amount }
    }

    val totalMerchantSpending = remember(merchantHistory) {
        merchantHistory.sumOf { it.totalSpending }
    }
    val totalMerchantTransactions = remember(merchantHistory) {
        merchantHistory.sumOf { it.totalTransactions }
    }

    // Join history rows with trend info (by merchant name) so we can render
    // name / category / tx count / total / trend in one row per merchant.
    val ranked = remember(merchantHistory, merchantTrends) {
        val trendByName = merchantTrends.associateBy { it.merchantName }
        merchantHistory
            .sortedByDescending { it.totalSpending }
            .map { history ->
                val trend = trendByName[history.merchantName]
                RankedMerchant(history = history, trend = trend)
            }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.SurfaceTint)
    ) {
        if (merchantHistory.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(AppSpacing.s8),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = strings.noMerchantData,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(AppSpacing.s1))
                    Text(
                        text = strings.importFirst,
                        fontSize = 13.sp,
                        color = AppColors.TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = AppSpacing.s4,
                    end = AppSpacing.s4,
                    top = AppSpacing.s3,
                    bottom = AppSpacing.s6
                ),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.s3 + 2.dp)
            ) {
                item(key = "summary") {
                    MerchantSummaryCard(
                        merchantCount = merchantHistory.size,
                        totalSpending = totalMerchantSpending,
                        totalTransactions = totalMerchantTransactions
                    )
                }

                item(key = "title") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 4.dp, top = AppSpacing.s1),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EyebrowLabel(text = strings.topMerchants)
                        Text(
                            text = "By ${strings.totalSpent.lowercase()}",
                            fontSize = 11.sp,
                            color = AppColors.TextTertiary
                        )
                    }
                }

                item(key = "top-merchants-card") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(AppElevations.xs, RoundedCornerShape(AppRadii.lg), clip = false)
                            .clip(RoundedCornerShape(AppRadii.lg))
                            .background(AppColors.CardBackground)
                    ) {
                        ranked.take(10).forEachIndexed { idx, rm ->
                            if (idx > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 78.dp)
                                        .height(1.dp)
                                        .background(AppColors.SurfaceTint)
                                )
                            }
                            RankedMerchantRow(rank = idx + 1, merchant = rm)
                        }
                    }
                }

                if (merchantSpending.isNotEmpty()) {
                    item(key = "chart") {
                        Spacer(Modifier.height(AppSpacing.s2))
                        TopMerchantsBarChart(
                            merchants = merchantSpending,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

private data class RankedMerchant(
    val history: MerchantHistory,
    val trend: MerchantTrend?
)

@Composable
private fun RankedMerchantRow(rank: Int, merchant: RankedMerchant) {
    val strings = LocalStrings.current
    val history = merchant.history
    val trend = merchant.trend
    val trendPercent = trend?.let { MerchantTrendCalculator.formatTrendPercentage(it) }
    val trendDir = trend?.direction

    // For expense merchants, spending going UP is bad (red), DOWN is good (green).
    val trendColor = when (trendDir) {
        MerchantTrend.TrendDirection.UP -> AppColors.Expenses
        MerchantTrend.TrendDirection.DOWN -> AppColors.Income
        else -> AppColors.TextTertiary
    }
    val trendLabel = when (trendDir) {
        MerchantTrend.TrendDirection.UP -> "↑ $trendPercent"
        MerchantTrend.TrendDirection.DOWN -> "↓ $trendPercent"
        MerchantTrend.TrendDirection.NEW -> "NEW"
        MerchantTrend.TrendDirection.STABLE -> "—"
        else -> "—"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.s4, vertical = AppSpacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.s3)
    ) {
        Text(
            text = rank.toString(),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = AppColors.TextTertiary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(22.dp)
        )

        CategoryAvatar(category = history.category, size = 36.dp)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = history.merchantName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${history.category.getLocalizedName(strings)} · ${history.totalTransactions} tx",
                fontSize = 11.sp,
                color = AppColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "€${formatMoneyAbs2dp(history.totalSpending)}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                color = AppColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = trendLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = trendColor
            )
        }
    }
}

@Composable
private fun MerchantSummaryCard(
    merchantCount: Int,
    totalSpending: Double,
    totalTransactions: Int
) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(AppElevations.xs, RoundedCornerShape(AppRadii.lg), clip = false)
            .clip(RoundedCornerShape(AppRadii.lg))
            .background(AppColors.CardBackground)
            .padding(AppSpacing.s4),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        SummaryStat(label = strings.tabMerchants, value = merchantCount.toString())
        SummaryStat(label = strings.totalSpent, value = "€${formatMoneyAbs2dp(totalSpending)}", mono = true)
        SummaryStat(label = strings.transactions, value = totalTransactions.toString())
    }
}

@Composable
private fun SummaryStat(label: String, value: String, mono: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        EyebrowLabel(text = label)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            color = AppColors.TextPrimary
        )
    }
}

private fun formatMoneyAbs2dp(value: Double): String {
    val cents = (value.absoluteValue * 100.0).roundToInt()
    val whole = cents / 100
    val frac = cents % 100
    val wholeStr = whole.toString().reversed().chunked(3).joinToString(",").reversed()
    return "$wholeStr.${frac.toString().padStart(2, '0')}"
}
