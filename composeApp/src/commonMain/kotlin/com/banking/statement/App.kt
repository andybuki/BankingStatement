package com.banking.statement

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.banking.statement.categorization.TransactionCategory
import com.banking.statement.export.ExportFormat
import com.banking.statement.export.SpendingExportData
import com.banking.statement.parser.ImportFileType
import com.banking.statement.parser.ParseResult
import com.banking.statement.ui.*
import com.banking.statement.ui.theme.BankingStatementTheme
import com.banking.statement.ui.theme.ThemeMode
import org.jetbrains.compose.ui.tooling.preview.Preview

// Composition local for strings
val LocalStrings = compositionLocalOf { defaultEnglishStrings() }

data class ImportState(
    val isProcessing: Boolean = false,
    val parseResult: ParseResult? = null,
    val savedToDatabase: Boolean = false,
    val transactionCount: Int = 0,
    val errorMessage: String? = null
)

data class DatabaseStats(
    val totalStatements: Int = 0,
    val totalTransactions: Int = 0,
    val totalAccounts: Int = 0
)

enum class Screen {
    HOME,
    TRANSACTIONS,
    SPENDING,
    ACCOUNTS
}

/**
 * Dialog state for imports - defined here for common access
 * Platform-specific implementation in MainActivity
 */
data class AppDialogState(
    val showAccountDialog: Boolean = false,
    val showSuccessDialog: Boolean = false,
    val bankName: String = "",
    val iban: String? = null,
    val statementPeriod: String? = null,
    val transactionCount: Int = 0,
    val existingAccounts: List<AccountOption> = emptyList(),
    val suggestedAccountName: String = "",
    val importedCount: Int = 0,
    val duplicatesSkipped: Int = 0,
    val isNewAccount: Boolean = false,
    val accountName: String = ""
)

@Composable
@Preview
fun App(
    onPickFile: ((List<String>) -> Unit)? = null,
    importState: ImportState = ImportState(),
    stats: DatabaseStats = DatabaseStats(),
    transactions: List<TransactionDisplay> = emptyList(),
    categorySpending: List<CategorySpending> = emptyList(),
    monthlySummary: List<MonthlySummary> = emptyList(),
    totalIncome: Double = 0.0,
    totalExpenses: Double = 0.0,
    // Dialog state - platform-specific handling passes these
    dialogState: Any? = null,
    onImportChoice: ((ImportChoice) -> Unit)? = null,
    onDismissSuccessDialog: (() -> Unit)? = null,
    // Account management
    accountsForManagement: List<AccountManagementItem> = emptyList(),
    onDeleteAccount: ((Long) -> Unit)? = null,
    onEditAccount: ((Long, String) -> Unit)? = null,
    onClearAllData: (() -> Unit)? = null,
    // Share callbacks
    onShareTransactions: ((ExportFormat, List<TransactionDisplay>, String?) -> Unit)? = null,
    onShareSpending: ((ExportFormat, SpendingExportData) -> Unit)? = null,
    // Theme
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: ((ThemeMode) -> Unit)? = null,
    // Category override
    onCategoryChange: ((TransactionDisplay, TransactionCategory) -> Unit)? = null
) {
    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    val strings = provideStrings()

    CompositionLocalProvider(LocalStrings provides strings) {
        BankingStatementTheme(themeMode = themeMode) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                when (currentScreen) {
                    Screen.HOME -> HomeScreen(
                        onPickFile = onPickFile,
                        importState = importState,
                        stats = stats,
                        onViewTransactions = { currentScreen = Screen.TRANSACTIONS },
                        onViewSpending = { currentScreen = Screen.SPENDING },
                        onManageAccounts = { currentScreen = Screen.ACCOUNTS }
                    )
                    Screen.TRANSACTIONS -> TransactionListScreen(
                        transactions = transactions,
                        accounts = accountsForManagement.map { AccountFilterOption(it.id, it.name) },
                        onBackClick = { currentScreen = Screen.HOME },
                        onShare = onShareTransactions,
                        onCategoryChange = onCategoryChange
                    )
                    Screen.SPENDING -> SpendingOverviewScreen(
                        totalIncome = totalIncome,
                        totalExpenses = totalExpenses,
                        categorySpending = categorySpending,
                        monthlySummary = monthlySummary,
                        transactions = transactions,
                        accounts = accountsForManagement.map { AccountFilterOption(it.id, it.name) },
                        onBackClick = { currentScreen = Screen.HOME },
                        onShare = onShareSpending
                    )
                    Screen.ACCOUNTS -> AccountManagementScreen(
                        accounts = accountsForManagement,
                        onBackClick = { currentScreen = Screen.HOME },
                        onDeleteAccount = { id -> onDeleteAccount?.invoke(id) },
                        onEditAccount = { id, name -> onEditAccount?.invoke(id, name) },
                        onClearAllData = { onClearAllData?.invoke() },
                        currentThemeMode = themeMode,
                        onThemeModeChange = { mode -> onThemeModeChange?.invoke(mode) }
                    )
                }

                // Import dialogs - handled through platform-specific dialog state
                // The dialogState is cast and handled in the platform-specific composable wrapper
                HandleImportDialogs(
                    dialogState = dialogState,
                    onImportChoice = onImportChoice,
                    onDismissSuccessDialog = onDismissSuccessDialog
                )
            }
        }
    }
}

@Composable
expect fun HandleImportDialogs(
    dialogState: Any?,
    onImportChoice: ((ImportChoice) -> Unit)?,
    onDismissSuccessDialog: (() -> Unit)?
)

@Composable
fun HomeScreen(
    onPickFile: ((List<String>) -> Unit)?,
    importState: ImportState,
    stats: DatabaseStats,
    onViewTransactions: () -> Unit,
    onViewSpending: () -> Unit,
    onManageAccounts: () -> Unit
) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Title
        Text(
            text = strings.homeTitle,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = strings.homeSubtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Stats Card with navigation
        if (stats.totalStatements > 0 || stats.totalTransactions > 0 || stats.totalAccounts > 0) {
            StatsCard(
                stats = stats,
                onViewTransactions = onViewTransactions,
                onViewSpending = onViewSpending,
                onManageAccounts = onManageAccounts
            )
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
            if (importState.isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(strings.processing)
            } else {
                Text(
                    text = strings.importButton,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = strings.supportedFormats,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Error Message
        AnimatedVisibility(visible = importState.errorMessage != null) {
            ErrorCard(message = importState.errorMessage ?: "")
        }

        // Success Result (without dialog - for backwards compatibility)
        AnimatedVisibility(visible = importState.savedToDatabase && importState.parseResult != null) {
            importState.parseResult?.let { result ->
                SuccessCard(
                    result = result,
                    transactionCount = importState.transactionCount
                )
            }
        }

        // Parse Failed Result
        AnimatedVisibility(
            visible = importState.parseResult != null &&
                      !importState.parseResult!!.success &&
                      importState.errorMessage == null &&
                      !importState.isProcessing
        ) {
            importState.parseResult?.let { result ->
                ValidationFailedCard(result = result)
            }
        }
    }
}

@Composable
fun StatsCard(
    stats: DatabaseStats,
    onViewTransactions: () -> Unit,
    onViewSpending: () -> Unit,
    onManageAccounts: () -> Unit
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
            // Stats row with better styling
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

            Spacer(modifier = Modifier.height(20.dp))

            // Navigation buttons - styled as proper buttons with clear background
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onViewTransactions,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = strings.viewTransactions,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Button(
                    onClick = onViewSpending,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = strings.viewSpending,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onManageAccounts,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text(
                    text = strings.manageAccounts,
                    style = MaterialTheme.typography.labelLarge
                )
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
fun ErrorCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
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
fun ValidationFailedCard(result: ParseResult) {
    val strings = LocalStrings.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "X",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = strings.importFailed,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = result.errorMessage ?: strings.errorReadingFile,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = strings.importTip,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
            )
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
