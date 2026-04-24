package com.banking.statement.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banking.statement.LocalStrings
import com.banking.statement.categorization.TransactionCategory
import com.banking.statement.ui.components.composeColor
import com.banking.statement.ui.theme.AppColors

/**
 * 3-screen onboarding flow shown on first launch.
 * Screen 1: "Import your bank statement" with supported banks
 * Screen 2: "See where your money goes" with sample donut chart
 * Screen 3: "Track trends over time" with sample line chart
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val strings = LocalStrings.current
    var currentPage by remember { mutableIntStateOf(0) }
    val totalPages = 3

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.MainBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 48.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Page content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = currentPage,
                    transitionSpec = {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                    }
                ) { page ->
                    when (page) {
                        0 -> OnboardingPageImport()
                        1 -> OnboardingPageSpending()
                        2 -> OnboardingPageTrends()
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Page indicators
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                repeat(totalPages) { index ->
                    val isSelected = index == currentPage
                    val width by animateFloatAsState(
                        targetValue = if (isSelected) 24f else 8f,
                        animationSpec = tween(300)
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(8.dp)
                            .width(width.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) AppColors.Primary
                                else Color.White.copy(alpha = 0.3f)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Navigation buttons
            if (currentPage < totalPages - 1) {
                Button(
                    onClick = { currentPage++ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Primary
                    )
                ) {
                    Text(
                        text = strings.onboardingNext,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = onComplete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = strings.onboardingSkip,
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Button(
                    onClick = onComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Primary
                    )
                ) {
                    Text(
                        text = strings.onboardingGetStarted,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// --- Page 1: Import your bank statement ---

@Composable
private fun OnboardingPageImport() {
    val strings = LocalStrings.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Icon illustration
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(AppColors.Primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(88.dp)) {
                val w = size.width
                val h = size.height
                val sheetW = w * 0.54f
                val sheetH = h * 0.70f
                val backTopLeft = Offset(w * 0.18f, h * 0.14f)
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.75f),
                    topLeft = backTopLeft,
                    size = Size(sheetW, sheetH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                )
                val frontTopLeft = Offset(w * 0.08f, h * 0.20f)
                drawRoundRect(
                    color = Color.White,
                    topLeft = frontTopLeft,
                    size = Size(sheetW, sheetH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                )
                val lineLeft = frontTopLeft.x + w * 0.06f
                val lineStart = frontTopLeft.y + h * 0.12f
                for (i in 0 until 3) {
                    drawRoundRect(
                        color = AppColors.Primary.copy(alpha = 0.25f),
                        topLeft = Offset(lineLeft, lineStart + i * h * 0.11f),
                        size = Size(sheetW * 0.64f, h * 0.04f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f)
                    )
                }
                val lensCenter = Offset(w * 0.66f, h * 0.70f)
                val lensRadius = w * 0.17f
                drawCircle(
                    color = AppColors.Primary,
                    radius = lensRadius + 4f,
                    center = lensCenter,
                    style = Stroke(width = 5f, cap = StrokeCap.Round)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.18f),
                    radius = lensRadius,
                    center = lensCenter
                )
                drawLine(
                    color = AppColors.Primary,
                    start = Offset(lensCenter.x + lensRadius * 0.70f, lensCenter.y + lensRadius * 0.70f),
                    end = Offset(lensCenter.x + lensRadius * 1.55f, lensCenter.y + lensRadius * 1.55f),
                    strokeWidth = 6f,
                    cap = StrokeCap.Round
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = strings.onboardingImportTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = strings.onboardingImportSubtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Supported banks grid
        SupportedBanksGrid()
    }
}

@Composable
private fun SupportedBanksGrid() {
    val banks = listOf(
        "Sparkasse", "Deutsche Bank", "Commerzbank", "ING",
        "DKB", "N26", "Postbank", "Volksbank",
        "Comdirect", "Targobank", "Sparda", "Bunq"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        banks.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { bank ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = bank,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Text(
            text = "+ 10 more",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
    }
}

// --- Page 2: See where your money goes ---

@Composable
private fun OnboardingPageSpending() {
    val strings = LocalStrings.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Sample donut chart
        SampleDonutChart()

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = strings.onboardingSpendingTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = strings.onboardingSpendingSubtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SampleDonutChart() {
    // Use MoneyLupe category seed colors so the onboarding preview matches
    // what users will see once they import real transactions.
    val categories = listOf(
        Triple("Rent", TransactionCategory.RENT.composeColor(), 35f),
        Triple("Food", TransactionCategory.SUPERMARKET.composeColor(), 25f),
        Triple("Transport", TransactionCategory.TRANSPORT.composeColor(), 15f),
        Triple("Shopping", TransactionCategory.SHOPPING.composeColor(), 12f),
        Triple("Other", TransactionCategory.OTHER.composeColor(), 13f)
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(200.dp)
    ) {
        Canvas(modifier = Modifier.size(200.dp)) {
            val strokeWidth = 32.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2
            val center = Offset(size.width / 2, size.height / 2)
            val arcSize = Size(radius * 2, radius * 2)
            val topLeft = Offset(center.x - radius, center.y - radius)

            var startAngle = -90f
            categories.forEach { (_, color, percentage) ->
                val sweep = percentage / 100f * 360f
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweep - 2f, // gap between segments
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                startAngle += sweep
            }
        }

        // Center label
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "\u20AC2,450",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "total",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Legend
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        categories.forEach { (name, color, pct) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${pct.toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// --- Page 3: Track trends over time ---

@Composable
private fun OnboardingPageTrends() {
    val strings = LocalStrings.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Sample line chart
        SampleLineChart()

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = strings.onboardingTrendsTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = strings.onboardingTrendsSubtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SampleLineChart() {
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun")
    val expenseData = listOf(2100f, 1850f, 2400f, 1900f, 2200f, 1750f)
    val incomeData = listOf(3200f, 3200f, 3400f, 3200f, 3500f, 3200f)
    val maxVal = 4000f

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .padding(16.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val stepX = width / (months.size - 1)

                // Grid lines
                for (i in 0..3) {
                    val y = height * i / 3f
                    drawLine(
                        color = Color.White.copy(alpha = 0.1f),
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1f
                    )
                }

                // Income line (green)
                val incomePath = Path()
                incomeData.forEachIndexed { index, value ->
                    val x = index * stepX
                    val y = height - (value / maxVal * height)
                    if (index == 0) incomePath.moveTo(x, y) else incomePath.lineTo(x, y)
                }
                drawPath(
                    path = incomePath,
                    color = AppColors.Income,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // Expense line (red/orange)
                val expensePath = Path()
                expenseData.forEachIndexed { index, value ->
                    val x = index * stepX
                    val y = height - (value / maxVal * height)
                    if (index == 0) expensePath.moveTo(x, y) else expensePath.lineTo(x, y)
                }
                drawPath(
                    path = expensePath,
                    color = AppColors.Expenses,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // Data points
                incomeData.forEachIndexed { index, value ->
                    val x = index * stepX
                    val y = height - (value / maxVal * height)
                    drawCircle(color = AppColors.Income, radius = 4.dp.toPx(), center = Offset(x, y))
                }
                expenseData.forEachIndexed { index, value ->
                    val x = index * stepX
                    val y = height - (value / maxVal * height)
                    drawCircle(color = AppColors.Expenses, radius = 4.dp.toPx(), center = Offset(x, y))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Month labels
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            months.forEach { month ->
                Text(
                    text = month,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(AppColors.Income)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Income",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.width(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(AppColors.Expenses)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Expenses",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}
