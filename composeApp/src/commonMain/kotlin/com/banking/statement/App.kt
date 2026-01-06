package com.banking.statement

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.banking.statement.categorization.CustomCategory
import com.banking.statement.categorization.TransactionCategory
import com.banking.statement.export.ExportFormat
import com.banking.statement.export.SpendingExportData
import com.banking.statement.parser.ImportFileType
import com.banking.statement.parser.ParseResult
import com.banking.statement.ui.*
import com.banking.statement.ui.theme.BankingStatementTheme
import com.banking.statement.ui.theme.ThemeMode
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview

// Composition local for strings
val LocalStrings = compositionLocalOf { defaultEnglishStrings() }

data class ImportState(
    val isProcessing: Boolean = false,
    val parseResult: ParseResult? = null,
    val savedToDatabase: Boolean = false,
    val transactionCount: Int = 0,
    val errorMessage: String? = null,
    val progress: Int = 0,  // 0-100 percentage
    val progressMessage: String = ""  // Current step description
)

data class DatabaseStats(
    val totalStatements: Int = 0,
    val totalTransactions: Int = 0,
    val totalAccounts: Int = 0
)

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

@OptIn(ExperimentalMaterial3Api::class)
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
    onCategoryChange: ((TransactionDisplay, TransactionCategory) -> Unit)? = null,
    // Custom categories
    customCategories: List<CustomCategory> = emptyList(),
    onCustomCategoryChange: ((TransactionDisplay, Long) -> Unit)? = null,
    onAddCustomCategory: ((name: String, icon: String, color: String) -> Unit)? = null,
    onEditCustomCategory: ((id: Long, name: String, icon: String, color: String) -> Unit)? = null,
    onDeleteCustomCategory: ((Long) -> Unit)? = null
) {
    var currentTab by remember { mutableStateOf(NavigationTab.HOME) }
    var showCategoryManagement by remember { mutableStateOf(false) }
    val strings = provideStrings()
    val accounts = accountsForManagement.map { AccountFilterOption(it.id, it.name) }

    // Track success card visibility at App level to persist across tab switches
    var showSuccessCard by remember { mutableStateOf(false) }
    // Track which import we've shown the success card for (using a unique key)
    var lastShownImportKey by remember { mutableStateOf<String?>(null) }

    // Generate a unique key for the current import
    val currentImportKey = if (importState.savedToDatabase && importState.parseResult != null) {
        "${importState.parseResult.bankName}_${importState.transactionCount}_${importState.parseResult.statementPeriod}"
    } else null

    // Auto-dismiss success card after 5 seconds, only show once per import
    LaunchedEffect(currentImportKey) {
        if (currentImportKey != null && currentImportKey != lastShownImportKey) {
            lastShownImportKey = currentImportKey
            showSuccessCard = true
            delay(5000) // 5 seconds
            showSuccessCard = false
        }
    }

    CompositionLocalProvider(LocalStrings provides strings) {
        BankingStatementTheme(themeMode = themeMode) {
            Scaffold(
                bottomBar = {
                    AppBottomNavigation(
                        currentTab = currentTab,
                        onTabSelected = { currentTab = it }
                    )
                }
            ) { paddingValues ->
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (currentTab) {
                        NavigationTab.HOME -> HomeScreen(
                            onPickFile = onPickFile,
                            importState = importState,
                            stats = stats,
                            totalIncome = totalIncome,
                            totalExpenses = totalExpenses,
                            showSuccessCard = showSuccessCard
                        )
                        NavigationTab.TRANSACTIONS -> Column(modifier = Modifier.fillMaxSize()) {
                            AppHeader(
                                title = strings.tabTransactions,
                                totalIncome = totalIncome,
                                totalExpenses = totalExpenses
                            )
                            TransactionListScreen(
                                transactions = transactions,
                                accounts = accounts,
                                customCategories = customCategories,
                                onBackClick = null,
                                onShare = onShareTransactions,
                                onCategoryChange = onCategoryChange,
                                onCustomCategoryChange = onCustomCategoryChange,
                                onManageCategories = { showCategoryManagement = true }
                            )
                        }
                        NavigationTab.SPENDING -> Column(modifier = Modifier.fillMaxSize()) {
                            AppHeader(
                                title = strings.spendingTitle,
                                totalIncome = totalIncome,
                                totalExpenses = totalExpenses
                            )
                            SpendingOverviewScreen(
                                totalIncome = totalIncome,
                                totalExpenses = totalExpenses,
                                categorySpending = categorySpending,
                                monthlySummary = monthlySummary,
                                transactions = transactions,
                                accounts = accounts,
                                onBackClick = null,
                                onShare = onShareSpending
                            )
                        }
                        NavigationTab.MERCHANTS -> Column(modifier = Modifier.fillMaxSize()) {
                            AppHeader(
                                title = strings.merchantsTitle,
                                totalIncome = totalIncome,
                                totalExpenses = totalExpenses
                            )
                            MerchantsScreen(
                                transactions = transactions,
                                accounts = accounts
                            )
                        }
                        NavigationTab.SETTINGS -> Column(modifier = Modifier.fillMaxSize()) {
                            AppHeader(
                                title = strings.manageAccounts,
                                showSummary = false  // Settings doesn't need income/expense summary
                            )
                            AccountManagementScreen(
                                accounts = accountsForManagement,
                                onBackClick = null,
                                onDeleteAccount = { id -> onDeleteAccount?.invoke(id) },
                                onEditAccount = { id, name -> onEditAccount?.invoke(id, name) },
                                onClearAllData = { onClearAllData?.invoke() },
                                currentThemeMode = themeMode,
                                onThemeModeChange = { mode -> onThemeModeChange?.invoke(mode) }
                            )
                        }
                    }

                    // Import dialogs - handled through platform-specific dialog state
                    // The dialogState is cast and handled in the platform-specific composable wrapper
                    HandleImportDialogs(
                        dialogState = dialogState,
                        onImportChoice = onImportChoice,
                        onDismissSuccessDialog = onDismissSuccessDialog
                    )

                    // Category management screen overlay
                    if (showCategoryManagement) {
                        CategoryManagementScreen(
                            customCategories = customCategories,
                            onBackClick = { showCategoryManagement = false },
                            onAddCategory = { name, icon, color ->
                                onAddCustomCategory?.invoke(name, icon, color)
                            },
                            onEditCategory = { id, name, icon, color ->
                                onEditCustomCategory?.invoke(id, name, icon, color)
                            },
                            onDeleteCategory = { id ->
                                onDeleteCustomCategory?.invoke(id)
                            }
                        )
                    }
                }
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
    totalIncome: Double = 0.0,
    totalExpenses: Double = 0.0,
    showSuccessCard: Boolean = false  // Controlled by App level state
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

            // Stats Card (simplified - no navigation buttons)
            if (stats.totalStatements > 0 || stats.totalTransactions > 0 || stats.totalAccounts > 0) {
                StatsCard(stats = stats)
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

            // Error Message
            AnimatedVisibility(visible = importState.errorMessage != null) {
                ErrorCard(message = importState.errorMessage ?: "")
            }

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
}

// Helper function to format amounts for header
/*private fun formatHeaderAmount(amount: Double): String {
    val absAmount = kotlin.math.abs(amount)
    return if (absAmount >= 1000000) {
        "%.1fM €".format(absAmount / 1000000)
    } else if (absAmount >= 1000) {
        "%.1fK €".format(absAmount / 1000)
    } else {
        "%.2f €".format(absAmount).replace(".", ",")
    }
}*/
private fun formatWithOneDecimal(value: Double): String {
    val rounded = kotlin.math.round(value * 10) / 10
    return rounded.toString().replace(".", ",")
}

private fun formatWithTwoDecimals(value: Double): String {
    val rounded = kotlin.math.round(value * 100) / 100
    return rounded.toString().replace(".", ",")
}
private fun formatHeaderAmount(amount: Double): String {
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

/**
 * Reusable header component for consistent styling across all tabs
 */
@Composable
fun AppHeader(
    title: String,
    totalIncome: Double = 0.0,
    totalExpenses: Double = 0.0,
    showSummary: Boolean = true
) {
    val strings = LocalStrings.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp)
            .background(Color(0xFF000000))
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column {
            // Title
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            if (showSummary) {
                Spacer(modifier = Modifier.height(12.dp))

                // Income and Expenses summary row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Income
                    Column {
                        Text(
                            text = strings.income,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF888888)
                        )
                        Text(
                            text = formatHeaderAmount(totalIncome),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                    }

                    // Expenses
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = strings.expenses,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF888888)
                        )
                        Text(
                            text = formatHeaderAmount(totalExpenses),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE57373)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatsCard(
    stats: DatabaseStats
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
