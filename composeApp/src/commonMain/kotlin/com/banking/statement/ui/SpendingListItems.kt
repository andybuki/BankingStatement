package com.banking.statement.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banking.statement.LocalStrings
import com.banking.statement.ui.components.CategoryAvatar
import com.banking.statement.ui.components.EyebrowLabel
import com.banking.statement.ui.components.composeColor
import com.banking.statement.ui.theme.AppColors
import com.banking.statement.ui.theme.AppElevations
import com.banking.statement.ui.theme.AppRadii
import com.banking.statement.ui.theme.AppSpacing
import kotlin.math.abs
import kotlin.math.roundToInt

// ============================================================
// Summary, category, and monthly cards shown on the Spending tab.
// Redesigned to match the MoneyLupe hi-fi kit (ScreensB.MLSpendingScreen).
// ============================================================

@Composable
fun SummaryCard(
    title: String,
    amount: Double,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .shadow(AppElevations.xs, RoundedCornerShape(AppRadii.lg), clip = false)
            .clip(RoundedCornerShape(AppRadii.lg))
            .background(AppColors.CardBackground)
            .padding(AppSpacing.s4)
    ) {
        EyebrowLabel(text = title)
        Spacer(modifier = Modifier.height(4.dp))
        // Sign is implied by the label + color; dropping it keeps the amount
        // on one line even at 5-6-digit magnitudes (e.g. €109,618.74).
        Text(
            text = formatCompactAmount(amount),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CategorySpendingItem(
    spending: CategorySpending,
    onClick: (() -> Unit)? = null
) {
    val strings = LocalStrings.current
    val categoryColor = spending.category.composeColor()
    val amount = abs(spending.totalAmount)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(AppElevations.xs, RoundedCornerShape(AppRadii.lg), clip = false)
            .clip(RoundedCornerShape(AppRadii.lg))
            .background(AppColors.CardBackground)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(AppSpacing.s4)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.s3)
        ) {
            CategoryAvatar(category = spending.category, size = 36.dp)
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = spending.category.getLocalizedName(strings),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "€${formatAbs2dp(amount)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        color = AppColors.TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${spending.percentage.roundToInt()}% · ${spending.transactionCount} ${strings.transactions.lowercase()}",
                        fontSize = 11.sp,
                        color = AppColors.TextTertiary
                    )
                    spending.trend?.let { trend ->
                        Text(
                            text = "${TrendCalculator.getTrendIndicator(trend)} ${TrendCalculator.formatTrendPercentage(trend)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            color = TrendCalculator.getTrendColor(trend, AppColors.Primary)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.s2 + 2.dp))

        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(AppRadii.pill))
                .background(AppColors.SurfaceTint)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((spending.percentage / 100f).coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(AppRadii.pill))
                    .background(categoryColor)
            )
        }

        if (onClick != null) {
            Spacer(modifier = Modifier.height(AppSpacing.s2))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.tapToViewTopTransactions,
                    fontSize = 11.sp,
                    color = AppColors.Primary.copy(alpha = 0.85f)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = AppColors.TextTertiary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun MonthlyItem(summary: MonthlySummary) {
    val strings = LocalStrings.current
    val expensesAbs = abs(summary.expenses)
    val net = summary.netAmount

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(AppElevations.xs, RoundedCornerShape(AppRadii.lg), clip = false)
            .clip(RoundedCornerShape(AppRadii.lg))
            .background(AppColors.CardBackground)
            .padding(AppSpacing.s4)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = summary.month,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary
            )
            Text(
                text = "${if (net >= 0) "+" else "−"}€${formatAbs2dp(net)}",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = if (net >= 0) AppColors.Income else AppColors.Expenses
            )
        }

        Spacer(modifier = Modifier.height(AppSpacing.s3))

        val total = (summary.income + expensesAbs).coerceAtLeast(0.01)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(AppRadii.pill))
                .background(AppColors.SurfaceTint)
        ) {
            if (summary.income > 0) {
                Box(
                    modifier = Modifier
                        .weight((summary.income / total).toFloat().coerceAtLeast(0.001f))
                        .fillMaxHeight()
                        .background(AppColors.Income)
                )
            }
            if (expensesAbs > 0) {
                Box(
                    modifier = Modifier
                        .weight((expensesAbs / total).toFloat().coerceAtLeast(0.001f))
                        .fillMaxHeight()
                        .background(AppColors.Expenses)
                )
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.s2 + 2.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MonthlyStat(label = strings.income, amount = summary.income, color = AppColors.Income)
            MonthlyStat(label = strings.expenses, amount = expensesAbs, color = AppColors.Expenses)
        }
    }
}

@Composable
private fun MonthlyStat(label: String, amount: Double, color: Color) {
    Column {
        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            letterSpacing = 0.6.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.TextTertiary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "€${formatAbs2dp(amount)}",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = color
        )
    }
}

private fun formatAbs2dp(value: Double): String {
    val cents = (abs(value) * 100.0).roundToInt()
    val whole = cents / 100
    val frac = cents % 100
    val wholeStr = whole.toString().reversed().chunked(3).joinToString(",").reversed()
    return "$wholeStr.${frac.toString().padStart(2, '0')}"
}

/**
 * Compact, single-line "€…" for the half-width summary cards on the
 * Spending tab. Uses K/M suffixes above 10,000 so yearly totals fit.
 */
private fun formatCompactAmount(amount: Double): String {
    val abs = abs(amount)
    return when {
        abs >= 1_000_000 -> "€${formatOneDecimal(abs / 1_000_000)}M"
        abs >= 10_000 -> "€${formatOneDecimal(abs / 1_000)}K"
        else -> "€${formatAbs2dp(abs)}"
    }
}

private fun formatOneDecimal(value: Double): String {
    val tenths = (value * 10.0).roundToInt()
    val whole = tenths / 10
    val frac = tenths % 10
    val wholeStr = whole.toString().reversed().chunked(3).joinToString(",").reversed()
    return if (frac == 0) wholeStr else "$wholeStr.$frac"
}
