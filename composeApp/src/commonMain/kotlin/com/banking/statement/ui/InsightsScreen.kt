package com.banking.statement.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.banking.statement.LocalStrings
import com.banking.statement.engagement.*
import com.banking.statement.ui.theme.AppColors
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun InsightsScreen(
    insights: List<SmartInsight>,
    healthScore: FinancialHealthScore,
    weeklyDigest: WeeklyDigest?,
    yearInReview: YearInReview?,
    showYearInReview: Boolean = false
) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Financial Health Score Card
        HealthScoreCard(healthScore)

        // Weekly Digest Card
        if (weeklyDigest != null && weeklyDigest.transactionCount > 0) {
            WeeklyDigestCard(weeklyDigest)
        }

        // Smart Insights
        if (insights.isNotEmpty()) {
            Text(
                text = strings.smartInsights,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            insights.forEach { insight ->
                InsightCard(insight)
            }
        } else {
            EmptyInsightsCard()
        }

        // Year in Review
        if (showYearInReview && yearInReview != null) {
            YearInReviewCard(yearInReview)
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun HealthScoreCard(score: FinancialHealthScore) {
    val strings = LocalStrings.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = strings.financialHealthScore,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Circular score gauge
            ScoreGauge(
                score = score.overall,
                summary = score.summary
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Breakdown bars
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ScoreBreakdownItem(
                    label = strings.healthScoreSavings,
                    score = score.savingsRate,
                    maxScore = 40,
                    color = AppColors.Income
                )
                ScoreBreakdownItem(
                    label = strings.healthScoreStability,
                    score = score.spendingStability,
                    maxScore = 30,
                    color = AppColors.Primary
                )
                ScoreBreakdownItem(
                    label = strings.healthScoreDiversity,
                    score = score.diversification,
                    maxScore = 30,
                    color = Color(0xFF7C3AED)
                )
            }
        }
    }
}

@Composable
private fun ScoreGauge(score: Int, summary: String) {
    val animatedScore by animateFloatAsState(
        targetValue = score.toFloat(),
        animationSpec = tween(durationMillis = 1000)
    )

    val scoreColor = when {
        score >= 80 -> AppColors.Income
        score >= 60 -> Color(0xFF2563EB)
        score >= 40 -> Color(0xFFF59E0B)
        else -> AppColors.Expenses
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(140.dp)
    ) {
        Canvas(modifier = Modifier.size(140.dp)) {
            val strokeWidth = 12.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2
            val topLeft = Offset(
                (size.width - radius * 2) / 2,
                (size.height - radius * 2) / 2
            )
            val arcSize = Size(radius * 2, radius * 2)

            // Background arc
            drawArc(
                color = Color.Gray.copy(alpha = 0.2f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Score arc
            drawArc(
                color = scoreColor,
                startAngle = 135f,
                sweepAngle = 270f * (animatedScore / 100f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = score.toString(),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = scoreColor
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScoreBreakdownItem(
    label: String,
    score: Int,
    maxScore: Int,
    color: Color
) {
    val percentage = if (maxScore > 0) score.toFloat() / maxScore else 0f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp)
    ) {
        Text(
            text = "$score/$maxScore",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { percentage },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.15f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun WeeklyDigestCard(digest: WeeklyDigest) {
    val strings = LocalStrings.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = strings.weeklyDigest,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Spent and Income row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DigestStat(
                    label = strings.weeklyDigestSpent,
                    value = formatAmount(digest.totalSpent),
                    color = AppColors.Expenses
                )
                DigestStat(
                    label = strings.weeklyDigestIncome,
                    value = formatAmount(digest.totalIncome),
                    color = AppColors.Income
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Top category and transactions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DigestStat(
                    label = strings.weeklyDigestTopCategory,
                    value = digest.topCategory.ifEmpty { "-" },
                    color = MaterialTheme.colorScheme.onSurface
                )
                DigestStat(
                    label = strings.weeklyDigestTransactions,
                    value = digest.transactionCount.toString(),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Week-over-week comparison
            if (digest.comparedToLastWeek != 0.0) {
                Spacer(modifier = Modifier.height(12.dp))
                val isUp = digest.comparedToLastWeek > 0
                val arrow = if (isUp) "↑" else "↓"
                val color = if (isUp) AppColors.Expenses else AppColors.Income
                Text(
                    text = "$arrow ${abs(digest.comparedToLastWeek).roundToInt()}% ${strings.weeklyDigestCompared}",
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun DigestStat(label: String, value: String, color: Color) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun InsightCard(insight: SmartInsight) {
    val backgroundColor = when (insight.severity) {
        InsightSeverity.WARNING -> Color(0xFFFEF3C7)
        InsightSeverity.POSITIVE -> Color(0xFFD1FAE5)
        InsightSeverity.INFO -> Color(0xFFDBEAFE)
    }

    val accentColor = when (insight.severity) {
        InsightSeverity.WARNING -> Color(0xFFF59E0B)
        InsightSeverity.POSITIVE -> AppColors.Income
        InsightSeverity.INFO -> AppColors.Primary
    }

    val icon = when (insight.severity) {
        InsightSeverity.WARNING -> "!"
        InsightSeverity.POSITIVE -> "+"
        InsightSeverity.INFO -> "i"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Severity indicator
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = icon,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = insight.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1F2937)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = insight.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B7280)
                )
            }
        }
    }
}

@Composable
private fun EmptyInsightsCard() {
    val strings = LocalStrings.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = strings.noInsightsYet,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = strings.noInsightsHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun YearInReviewCard(review: YearInReview) {
    val strings = LocalStrings.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E293B)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "${strings.yearInReview} ${review.year}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Income / Expenses / Saved
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                YearStat(
                    label = strings.yearInReviewTotalIncome,
                    value = formatAmount(review.totalIncome),
                    color = AppColors.Income
                )
                YearStat(
                    label = strings.yearInReviewTotalExpenses,
                    value = formatAmount(review.totalExpenses),
                    color = AppColors.Expenses
                )
                YearStat(
                    label = strings.yearInReviewTotalSaved,
                    value = formatAmount(review.totalSaved),
                    color = if (review.totalSaved >= 0) AppColors.Income else AppColors.Expenses
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.dp))

            // Top Categories
            Text(
                text = strings.yearInReviewTopCategories,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            review.topCategories.forEach { (name, amount) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Text(
                        text = formatAmount(amount),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.dp))

            // Stats grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                YearStat(
                    label = strings.yearInReviewTransactions,
                    value = review.totalTransactions.toString(),
                    color = Color.White
                )
                YearStat(
                    label = strings.yearInReviewAvgMonthly,
                    value = formatAmount(review.averageMonthlySpending),
                    color = Color.White
                )
                YearStat(
                    label = strings.yearInReviewSavingsRate,
                    value = "${review.savingsRate.roundToInt()}%",
                    color = if (review.savingsRate >= 0) AppColors.Income else AppColors.Expenses
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Biggest and leanest month
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                YearStat(
                    label = strings.yearInReviewBiggestMonth,
                    value = "${review.biggestMonth.first}\n${formatAmount(review.biggestMonth.second)}",
                    color = AppColors.Expenses
                )
                YearStat(
                    label = strings.yearInReviewLeanestMonth,
                    value = "${review.leanestMonth.first}\n${formatAmount(review.leanestMonth.second)}",
                    color = AppColors.Income
                )
            }
        }
    }
}

@Composable
private fun YearStat(label: String, value: String, color: Color) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

private fun formatAmount(amount: Double): String {
    val absAmount = abs(amount)
    val rounded = kotlin.math.round(absAmount * 100) / 100
    return "${rounded.toString().replace(".", ",")} €"
}
