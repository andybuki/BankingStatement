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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.banking.statement.parser.ImportFileType
import com.banking.statement.parser.ParseResult
import com.banking.statement.ui.AccountManagementItem
import com.banking.statement.ui.theme.AppColors

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
    showSuccessCard: Boolean = false,  // Controlled by App level state
    showTutorial: Boolean = false,     // Show welcome tutorial on first launch
    onDismissTutorial: () -> Unit = {},
    onEmailClick: (String) -> Unit = {}
) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header section - same color as footer with shadow
        AppHeader(
            title = strings.homeTitle,
            totalIncome = totalIncome,
            totalExpenses = totalExpenses
        )

        // Main content area with different background color
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = strings.homeSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Welcome Tutorial Card - shown on first launch
            AnimatedVisibility(
                visible = showTutorial,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column {
                    WelcomeTutorialCard(onDismiss = onDismissTutorial)
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // Stats Card (simplified - no navigation buttons)
            if (stats.totalStatements > 0 || stats.totalTransactions > 0 || stats.totalAccounts > 0) {
                StatsCard(stats = stats, accounts = accounts)
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Import Button
            Button(
                onClick = { onPickFile?.invoke(ImportFileType.allMimeTypes()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !importState.isProcessing && onPickFile != null,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = strings.importButton,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // Processing Overlay
            if (importState.isProcessing) {
                Spacer(modifier = Modifier.height(16.dp))
                ProcessingCard(
                    progress = importState.progress,
                    message = importState.progressMessage.ifEmpty { strings.processing }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = strings.supportedFormats,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Success Result - auto-dismisses after 5 seconds
            AnimatedVisibility(
                visible = showSuccessCard && importState.savedToDatabase && importState.parseResult != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                importState.parseResult?.let { result ->
                    SuccessCard(
                        result = result,
                        transactionCount = importState.transactionCount
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Reusable header component for consistent styling across all tabs
 */
@Composable
fun AppHeader(
    title: String,
    totalIncome: Double = 0.0,
    totalExpenses: Double = 0.0,
    showSummary: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val strings = LocalStrings.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp)
            .background(AppColors.HeaderBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column {
            // Compact single-row layout with title, summary, and actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
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
                Spacer(modifier = Modifier.height(8.dp))

                // Compact income and expenses summary row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Income - compact inline layout
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${strings.income}: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.HeaderSecondaryText
                        )
                        Text(
                            text = formatHeaderAmount(totalIncome),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.Income
                        )
                    }

                    // Expenses - compact inline layout
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${strings.expenses}: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.HeaderSecondaryText
                        )
                        Text(
                            text = formatHeaderAmount(totalExpenses),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.Expenses
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatsCard(
    stats: DatabaseStats,
    accounts: List<AccountManagementItem> = emptyList()
) {
    val strings = LocalStrings.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Show per-bank breakdown if we have account data
            if (accounts.isNotEmpty()) {
                accounts.forEach { account ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = account.bankName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 12.dp)
                        )
                        Text(
                            text = "${account.statementCount} ${strings.statements.lowercase()}, ${account.transactionCount} ${strings.transactions.lowercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                if (accounts.size > 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    )
                    // Total row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.total,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 12.dp)
                        )
                        Text(
                            text = "${stats.totalStatements} ${strings.statements.lowercase()}, ${stats.totalTransactions} ${strings.transactions.lowercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            } else {
                // Fallback to simple stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(
                        label = strings.accounts,
                        value = stats.totalAccounts.toString()
                    )
                    StatItem(
                        label = strings.statements,
                        value = stats.totalStatements.toString()
                    )
                    StatItem(
                        label = strings.transactions,
                        value = stats.totalTransactions.toString()
                    )
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun SuccessCard(result: ParseResult, transactionCount: Int) {
    val strings = LocalStrings.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "OK",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = strings.importSuccessful,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "$transactionCount ${strings.transactions.lowercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))

            DetailRow(strings.bankLabel, result.bankName)
            result.statementPeriod?.let {
                DetailRow(strings.periodLabel, it)
            }
            result.accountIban?.let {
                DetailRow(strings.accountLabel, it.take(12) + "...")
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
fun ProcessingCard(
    progress: Int,
    message: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Progress percentage
            Text(
                text = "$progress%",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Status message
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Welcome tutorial card - shown on first launch, dismissible
 */
@Composable
fun WelcomeTutorialCard(
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = strings.welcomeTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Bullet points
            BulletPoint(text = strings.welcomeBullet1)
            BulletPoint(text = strings.welcomeBullet2)
            BulletPoint(text = strings.welcomeBullet3)

            Spacer(modifier = Modifier.height(20.dp))

            // Dismiss button
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = strings.welcomeButton,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun BulletPoint(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/**
 * Contact info card - always visible at the bottom of home screen
 */
@Composable
fun ContactInfoCard(
    onEmailClick: (String) -> Unit
) {
    val strings = LocalStrings.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Email row - clickable
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = strings.contactEmail,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = strings.contactHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
