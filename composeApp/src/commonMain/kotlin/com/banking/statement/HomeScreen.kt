package com.banking.statement

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banking.statement.parser.ImportFileType
import com.banking.statement.parser.ParseResult
import com.banking.statement.ui.AccountManagementItem
import com.banking.statement.ui.CategorySpending
import com.banking.statement.ui.TransactionDisplay
import com.banking.statement.ui.components.CategoryAvatar
import com.banking.statement.ui.components.EyebrowLabel
import com.banking.statement.ui.components.MLCard
import com.banking.statement.ui.components.MoneyAmount
import com.banking.statement.ui.components.QuickActionCard
import com.banking.statement.ui.components.SectionTitle
import com.banking.statement.ui.components.composeColor
import com.banking.statement.ui.theme.AppColors
import com.banking.statement.ui.theme.AppElevations
import com.banking.statement.ui.theme.AppRadii
import com.banking.statement.ui.theme.AppSpacing
import kotlin.math.abs
import kotlin.math.roundToInt

// ============================================================
// Amount formatters shared by header and cards.
// Pure helpers — no Compose state.
// ============================================================

private fun formatWithOneDecimal(value: Double): String {
    val rounded = kotlin.math.round(value * 10) / 10
    return rounded.toString().replace(".", ",")
}

private fun formatWithTwoDecimals(value: Double): String {
    val rounded = kotlin.math.round(value * 100) / 100
    return rounded.toString().replace(".", ",")
}

internal fun formatHeaderAmount(amount: Double): String {
    val absAmount = kotlin.math.abs(amount)

    return when {
        absAmount >= 1_000_000 -> {
            formatWithOneDecimal(absAmount / 1_000_000) + "M €"
        }
        absAmount >= 1_000 -> {
            formatWithOneDecimal(absAmount / 1_000) + "K €"
        }
        else -> {
            formatWithTwoDecimals(absAmount) + " €"
        }
    }
}

private fun formatMoney2dp(value: Double): String {
    val cents = (abs(value) * 100.0).roundToInt()
    val whole = cents / 100
    val frac = cents % 100
    val wholeStr = whole.toString().reversed().chunked(3).joinToString(",").reversed()
    return "$wholeStr.${frac.toString().padStart(2, '0')}"
}

// ============================================================
// Home tab
// ============================================================

@Composable
fun HomeScreen(
    onPickFile: ((List<String>) -> Unit)?,
    importState: ImportState,
    stats: DatabaseStats,
    accounts: List<AccountManagementItem> = emptyList(),
    totalIncome: Double = 0.0,
    totalExpenses: Double = 0.0,
    transactions: List<TransactionDisplay> = emptyList(),
    categorySpending: List<CategorySpending> = emptyList(),
    showSuccessCard: Boolean = false,
    showTutorial: Boolean = false,
    onDismissTutorial: () -> Unit = {},
    onEmailClick: (String) -> Unit = {},
    onNavigateToTransactions: () -> Unit = {},
    onNavigateToSpending: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val strings = LocalStrings.current
    val hasAnyData = stats.totalTransactions > 0
    val totalExpensesAbs = kotlin.math.abs(totalExpenses)

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            title = strings.appName,
            totalIncome = totalIncome,
            totalExpenses = totalExpenses
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.SurfaceTint)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.s4)
                .padding(top = AppSpacing.s4, bottom = AppSpacing.s6),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.s3 + 2.dp)
        ) {
            AnimatedVisibility(visible = showTutorial, enter = fadeIn(), exit = fadeOut()) {
                WelcomeTutorialCard(onDismiss = onDismissTutorial)
            }

            if (importState.isProcessing) {
                ProcessingCard(
                    progress = importState.progress,
                    message = importState.progressMessage.ifEmpty { strings.processing }
                )
            }

            AnimatedVisibility(
                visible = showSuccessCard && importState.savedToDatabase && importState.parseResult != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                importState.parseResult?.let { result ->
                    SuccessCard(result = result, transactionCount = importState.transactionCount)
                }
            }

            if (hasAnyData) {
                NetBalanceHeroCard(
                    totalIncome = totalIncome,
                    totalExpensesAbs = totalExpensesAbs,
                    accountsCount = stats.totalAccounts,
                    transactionsCount = stats.totalTransactions
                )
            } else {
                EmptyImportPromptCard()
            }

            QuickActionsRow(
                hasAccounts = stats.totalAccounts > 0,
                accountsCount = stats.totalAccounts,
                statementsCount = stats.totalStatements,
                enabled = !importState.isProcessing && onPickFile != null,
                onImport = { onPickFile?.invoke(ImportFileType.allMimeTypes()) },
                onAccounts = onNavigateToSettings
            )

            if (transactions.isNotEmpty()) {
                Column {
                    SectionTitle(
                        text = strings.tabTransactions,
                        actionText = strings.viewTransactions,
                        onAction = onNavigateToTransactions
                    )
                    Spacer(Modifier.height(AppSpacing.s2))
                    MLCard(padding = 0.dp) {
                        transactions.take(4).forEachIndexed { index, tx ->
                            RecentTxRow(
                                tx = tx,
                                first = index == 0
                            )
                        }
                    }
                }
            }

            if (categorySpending.isNotEmpty()) {
                Column {
                    SectionTitle(
                        text = strings.spendingByCategory,
                        actionText = strings.viewDetails,
                        onAction = onNavigateToSpending
                    )
                    Spacer(Modifier.height(AppSpacing.s2))
                    MLCard {
                        val top = categorySpending
                            .filter { it.totalAmount != 0.0 }
                            .sortedByDescending { abs(it.totalAmount) }
                            .take(4)
                        val maxAmount = top.maxOfOrNull { abs(it.totalAmount) } ?: 1.0
                        top.forEachIndexed { i, cs ->
                            TopCategoryBar(
                                cs = cs,
                                widthFraction = (abs(cs.totalAmount) / maxAmount).coerceIn(0.05, 1.0).toFloat(),
                                last = i == top.lastIndex
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(AppSpacing.s2))
        }
    }
}

// ============================================================
// Home — building blocks
// ============================================================

@Composable
private fun NetBalanceHeroCard(
    totalIncome: Double,
    totalExpensesAbs: Double,
    accountsCount: Int,
    transactionsCount: Int
) {
    val net = totalIncome - totalExpensesAbs
    val strings = LocalStrings.current
    val sumForBar = (totalIncome + totalExpensesAbs).coerceAtLeast(0.01)
    val incomeFraction = (totalIncome / sumForBar).toFloat().coerceIn(0f, 1f)
    val expenseFraction = (totalExpensesAbs / sumForBar).toFloat().coerceIn(0f, 1f)

    MLCard(floating = true, padding = AppSpacing.s4) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                EyebrowLabel(text = strings.netBalance)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(if (net >= 0) "+" else "−")
                        append("€")
                        append(formatMoney2dp(net))
                    },
                    fontSize = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = AppColors.TextPrimary,
                    lineHeight = 36.sp
                )
                Spacer(Modifier.height(4.dp))
                val meta = buildString {
                    append(accountsCount)
                    append(if (accountsCount == 1) " account" else " accounts")
                    append(" · ")
                    append(transactionsCount)
                    append(" ")
                    append(strings.transactions.lowercase())
                }
                Text(
                    text = meta,
                    fontSize = 12.sp,
                    color = AppColors.TextSecondary
                )
            }
        }

        Spacer(Modifier.height(AppSpacing.s3 + 2.dp))

        // Income / expense split bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(AppRadii.pill))
                .background(AppColors.SurfaceTint)
        ) {
            if (incomeFraction > 0f) {
                Box(
                    modifier = Modifier
                        .weight(incomeFraction.coerceAtLeast(0.001f))
                        .fillMaxHeight()
                        .background(AppColors.Income)
                )
            }
            if (expenseFraction > 0f) {
                Box(
                    modifier = Modifier
                        .weight(expenseFraction.coerceAtLeast(0.001f))
                        .fillMaxHeight()
                        .background(AppColors.Expenses)
                )
            }
        }

        Spacer(Modifier.height(AppSpacing.s1 + 2.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${strings.income} €${formatMoney2dp(totalIncome)}",
                fontSize = 11.sp,
                color = AppColors.TextSecondary
            )
            Text(
                text = "${strings.expenses} €${formatMoney2dp(totalExpensesAbs)}",
                fontSize = 11.sp,
                color = AppColors.TextSecondary
            )
        }
    }
}

@Composable
private fun EmptyImportPromptCard() {
    val strings = LocalStrings.current
    MLCard(floating = true, padding = AppSpacing.s5) {
        Text(
            text = strings.noTransactions,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.TextPrimary
        )
        Spacer(Modifier.height(AppSpacing.s1 + 2.dp))
        Text(
            text = strings.importFirst,
            fontSize = 14.sp,
            color = AppColors.TextSecondary,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun QuickActionsRow(
    hasAccounts: Boolean,
    accountsCount: Int,
    statementsCount: Int,
    enabled: Boolean,
    onImport: () -> Unit,
    onAccounts: () -> Unit
) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.s2 + 2.dp)
    ) {
        QuickActionCard(
            label = strings.importButton,
            subtitle = strings.supportedFormats,
            icon = Icons.Filled.FileUpload,
            tint = AppColors.Primary,
            onClick = { if (enabled) onImport() },
            modifier = Modifier.weight(1f)
        )
        if (hasAccounts) {
            QuickActionCard(
                label = strings.manageAccounts,
                subtitle = "$accountsCount · $statementsCount ${strings.statements.lowercase()}",
                icon = Icons.Filled.AccountBalanceWallet,
                tint = AppColors.Income,
                onClick = onAccounts,
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun RecentTxRow(tx: TransactionDisplay, first: Boolean) {
    val strings = LocalStrings.current
    val categoryName = tx.customCategoryName ?: tx.category.getLocalizedName(strings)
    val display = TransactionDisplay.extractDisplayName(tx.counterparty, tx.description)

    Column {
        if (!first) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(AppColors.SurfaceTint)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.s4, vertical = AppSpacing.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.s3)
        ) {
            CategoryAvatar(category = tx.category, size = 36.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = display,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = "$categoryName · ${tx.date}",
                    fontSize = 12.sp,
                    color = AppColors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            MoneyAmount(value = tx.amount, size = 14, weight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TopCategoryBar(
    cs: CategorySpending,
    widthFraction: Float,
    last: Boolean
) {
    val strings = LocalStrings.current
    val categoryColor = cs.category.composeColor()
    val name = cs.category.getLocalizedName(strings)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.s2 - 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.s3 - 2.dp)
    ) {
        CategoryAvatar(category = cs.category, size = 28.dp)
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "€${formatMoney2dp(abs(cs.totalAmount))}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    color = AppColors.TextPrimary
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(AppRadii.pill))
                    .background(AppColors.SurfaceTint)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(widthFraction)
                        .fillMaxHeight()
                        .background(categoryColor, RoundedCornerShape(AppRadii.pill))
                )
            }
        }
    }
    if (!last) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(AppColors.SurfaceTint)
        )
    }
}

// ============================================================
// AppHeader — shared navy header (still used by other tabs).
// Rebuilt to match the MoneyLupe spec: title + optional
// INCOME / EXPENSES pair with eyebrow labels and mono values.
// ============================================================

@Composable
fun AppHeader(
    title: String,
    totalIncome: Double = 0.0,
    totalExpenses: Double = 0.0,
    showSummary: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val strings = LocalStrings.current
    val totalExpensesAbs = kotlin.math.abs(totalExpenses)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = AppElevations.md)
            .background(AppColors.HeaderBackground)
            .padding(horizontal = AppSpacing.s4)
            .padding(top = AppSpacing.s3, bottom = if (showSummary) AppSpacing.s4 else AppSpacing.s3)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.HeaderText
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    actions()
                }
            }

            if (showSummary) {
                Spacer(modifier = Modifier.height(AppSpacing.s2 + 2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.s6)
                ) {
                    HeaderAmountPair(
                        label = strings.income,
                        amount = totalIncome,
                        color = Color(0xFF34D399),
                        sign = "+"
                    )
                    HeaderAmountPair(
                        label = strings.expenses,
                        amount = totalExpensesAbs,
                        color = Color(0xFFFCA5A5),
                        sign = "−"
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderAmountPair(label: String, amount: Double, color: Color, sign: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            letterSpacing = 0.6.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.HeaderSecondaryText
        )
        Text(
            text = "$sign€${formatMoney2dp(amount)}",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = color
        )
    }
}

// ============================================================
// Processing / Success / Tutorial cards (retained).
// ============================================================

@Composable
fun ProcessingCard(progress: Int, message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(AppRadii.lg)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(AppSpacing.s6),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$progress%",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(AppSpacing.s4))
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
            )
            Spacer(modifier = Modifier.height(AppSpacing.s4))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun SuccessCard(result: ParseResult, transactionCount: Int) {
    val strings = LocalStrings.current
    MLCard(floating = true, padding = AppSpacing.s5) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.s3)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(AppRadii.md))
                    .background(AppColors.IncomeTint),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AppColors.Income)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.importSuccessful,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary
                )
                Text(
                    text = "$transactionCount ${strings.transactions.lowercase()}",
                    fontSize = 12.sp,
                    color = AppColors.TextSecondary
                )
            }
        }
        Spacer(modifier = Modifier.height(AppSpacing.s3))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(AppColors.Divider))
        Spacer(modifier = Modifier.height(AppSpacing.s3))
        DetailRow(strings.bankLabel, result.bankName)
        result.statementPeriod?.let { DetailRow(strings.periodLabel, it) }
        result.accountIban?.let { DetailRow(strings.accountLabel, it.take(12) + "...") }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 13.sp, color = AppColors.TextTertiary)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
    }
}

@Composable
fun WelcomeTutorialCard(onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    MLCard(floating = true, padding = AppSpacing.s5) {
        Text(
            text = strings.welcomeTitle,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.TextPrimary
        )
        Spacer(modifier = Modifier.height(AppSpacing.s3))
        BulletPoint(text = strings.welcomeBullet1)
        BulletPoint(text = strings.welcomeBullet2)
        BulletPoint(text = strings.welcomeBullet3)
        Spacer(modifier = Modifier.height(AppSpacing.s4))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AppRadii.md),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
        ) {
            Text(text = strings.welcomeButton, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BulletPoint(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "•",
            fontSize = 16.sp,
            color = AppColors.Primary,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = text,
            fontSize = 14.sp,
            color = AppColors.TextSecondary
        )
    }
}

@Composable
fun ContactInfoCard(onEmailClick: (String) -> Unit) {
    val strings = LocalStrings.current
    MLCard {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppRadii.sm))
                .background(AppColors.SurfaceTint)
                .clickable { onEmailClick(strings.contactEmail) }
                .padding(horizontal = AppSpacing.s4, vertical = AppSpacing.s3),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = strings.contactEmail,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = AppColors.Primary
            )
        }
        Spacer(modifier = Modifier.height(AppSpacing.s2))
        Text(
            text = strings.contactHint,
            fontSize = 12.sp,
            color = AppColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
