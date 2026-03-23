package com.banking.statement

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import bankingstatement.composeapp.generated.resources.Res
import bankingstatement.composeapp.generated.resources.share
import org.jetbrains.compose.resources.painterResource
import com.banking.statement.categorization.CustomCategory
import com.banking.statement.categorization.TransactionCategory
import com.banking.statement.export.ExportFormat
import com.banking.statement.export.SpendingExportData
import com.banking.statement.parser.ImportFileType
import com.banking.statement.parser.ParseResult
import com.banking.statement.ui.*
import com.banking.statement.ui.theme.AppColors
import com.banking.statement.ui.theme.BankingStatementTheme
import com.banking.statement.ui.theme.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
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
    onRetryImport: (() -> Unit)? = null,
    onDismissErrorDialog: (() -> Unit)? = null,
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
    onDeleteCustomCategory: ((Long) -> Unit)? = null,
    // Onboarding
    showOnboarding: Boolean = false,
    onCompleteOnboarding: () -> Unit = {},
    // Tutorial & Contact
    showTutorial: Boolean = false,
    onDismissTutorial: () -> Unit = {},
    onEmailClick: (String) -> Unit = {},
    // Security / Biometric
    biometricLockEnabled: Boolean = false,
    biometricAvailable: Boolean = false,
    onBiometricLockChange: (Boolean) -> Unit = {},
    // Transaction pagination
    hasMoreTransactions: Boolean = false,
    isLoadingMoreTransactions: Boolean = false,
    onLoadMoreTransactions: (() -> Unit)? = null
) {
    var currentTab by remember { mutableStateOf(NavigationTab.HOME) }
    var showCategoryManagement by remember { mutableStateOf(false) }
    val strings = provideStrings()
    val accounts = accountsForManagement.map { AccountFilterOption(it.id, it.name) }

    // Share menu states for header actions
    var transactionsShareMenuExpanded by remember { mutableStateOf(false) }
    var spendingShareMenuExpanded by remember { mutableStateOf(false) }
    var showChartView by remember { mutableStateOf(false) }

    // Shared account filter state for Transactions and Merchants tabs
    var selectedAccountId by remember { mutableStateOf<Long?>(null) }
    var accountDropdownExpanded by remember { mutableStateOf(false) }

    // Time period selector for Spending tab - smart default based on data availability
    var selectedTimePeriod by remember { mutableStateOf("month") }
    var hasAutoSelectedTimePeriod by remember { mutableStateOf(false) }

    // Auto-select the best time period when transactions change
    LaunchedEffect(transactions.size, hasAutoSelectedTimePeriod) {
        if (!hasAutoSelectedTimePeriod && transactions.isNotEmpty()) {
            hasAutoSelectedTimePeriod = true
            val now = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val currentYear = now.year
            val currentMonth = now.monthNumber
            val currentDay = now.dayOfMonth

            // Check if there's data for current month
            val hasMonthData = transactions.any { tx ->
                try {
                    val parts = tx.date.split(".")
                    if (parts.size == 3) {
                        val month = parts[1].toIntOrNull() ?: return@any false
                        val year = parts[2].toIntOrNull() ?: return@any false
                        year == currentYear && month == currentMonth
                    } else false
                } catch (_: Exception) { false }
            }

            if (!hasMonthData) {
                // Check if there's data for current year
                val hasYearData = transactions.any { tx ->
                    try {
                        val parts = tx.date.split(".")
                        if (parts.size == 3) {
                            val year = parts[2].toIntOrNull() ?: return@any false
                            year == currentYear
                        } else false
                    } catch (_: Exception) { false }
                }

                selectedTimePeriod = if (hasYearData) "year" else "all"
            }
        }
    }

    // Calculate filtered income/expenses based on selected account
    val filteredIncome = remember(transactions, selectedAccountId) {
        val filtered = if (selectedAccountId == null) {
            transactions
        } else {
            transactions.filter { it.accountId == selectedAccountId }
        }
        filtered.filter { it.amount > 0 }.sumOf { it.amount }
    }

    val filteredExpenses = remember(transactions, selectedAccountId) {
        val filtered = if (selectedAccountId == null) {
            transactions
        } else {
            transactions.filter { it.accountId == selectedAccountId }
        }
        filtered.filter { it.amount < 0 }.sumOf { it.amount }
    }

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
            if (showOnboarding) {
                OnboardingScreen(onComplete = onCompleteOnboarding)
                return@BankingStatementTheme
            }
            Scaffold(
                bottomBar = {
                    AppBottomNavigation(
                        currentTab = currentTab,
                        onTabSelected = {
                            currentTab = it
                            showCategoryManagement = false  // Close category management when switching tabs
                        }
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
                            accounts = accountsForManagement,
                            totalIncome = totalIncome,
                            totalExpenses = totalExpenses,
                            showSuccessCard = showSuccessCard,
                            showTutorial = showTutorial,
                            onDismissTutorial = onDismissTutorial,
                            onEmailClick = onEmailClick
                        )
                        NavigationTab.TRANSACTIONS -> Column(modifier = Modifier.fillMaxSize()) {
                            AppHeader(
                                title = strings.tabTransactions,
                                totalIncome = filteredIncome,
                                totalExpenses = filteredExpenses,
                                actions = {
                                    // Account filter dropdown
                                    if (accounts.size > 1) {
                                        Box {
                                            FilterChip(
                                                selected = selectedAccountId != null,
                                                onClick = { accountDropdownExpanded = true },
                                                label = {
                                                    Text(
                                                        text = selectedAccountId?.let { id ->
                                                            accounts.find { it.id == id }?.name ?: strings.allAccounts
                                                        } ?: strings.allAccounts,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = Color.White
                                                    )
                                                },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    containerColor = AppColors.HeaderSeparator,
                                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                                )
                                            )
                                            DropdownMenu(
                                                expanded = accountDropdownExpanded,
                                                onDismissRequest = { accountDropdownExpanded = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text(strings.allAccounts) },
                                                    onClick = {
                                                        selectedAccountId = null
                                                        accountDropdownExpanded = false
                                                    }
                                                )
                                                accounts.forEach { account ->
                                                    DropdownMenuItem(
                                                        text = { Text(account.name) },
                                                        onClick = {
                                                            selectedAccountId = account.id
                                                            accountDropdownExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    // Share button
                                    if (onShareTransactions != null && transactions.isNotEmpty()) {
                                        Box {
                                            IconButton(onClick = { transactionsShareMenuExpanded = true }) {
                                                Image(
                                                    painter = painterResource(Res.drawable.share),
                                                    contentDescription = strings.share,
                                                    modifier = Modifier.size(24.dp),
                                                    colorFilter = ColorFilter.tint(Color.White)
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = transactionsShareMenuExpanded,
                                                onDismissRequest = { transactionsShareMenuExpanded = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text(strings.exportCsv) },
                                                    onClick = {
                                                        transactionsShareMenuExpanded = false
                                                        onShareTransactions(ExportFormat.CSV, transactions, null)
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(strings.exportPdf) },
                                                    onClick = {
                                                        transactionsShareMenuExpanded = false
                                                        onShareTransactions(ExportFormat.PDF, transactions, null)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            )
                            TransactionListScreen(
                                transactions = transactions,
                                accounts = accounts,
                                selectedAccountId = selectedAccountId,
                                onAccountSelected = { selectedAccountId = it },
                                customCategories = customCategories,
                                onBackClick = null,
                                onShare = null,  // Share moved to header
                                onCategoryChange = onCategoryChange,
                                onCustomCategoryChange = onCustomCategoryChange,
                                onManageCategories = { showCategoryManagement = true },
                                hasMoreTransactions = hasMoreTransactions,
                                isLoadingMore = isLoadingMoreTransactions,
                                onLoadMore = onLoadMoreTransactions
                            )
                        }
                        NavigationTab.SPENDING -> Column(modifier = Modifier.fillMaxSize()) {
                            // Header row with title, account filter, chart toggle, and share
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(AppColors.HeaderBackground)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = strings.spendingTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AppColors.HeaderText
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Account filter (if multiple accounts)
                                    if (accounts.size > 1) {
                                        Box {
                                            FilterChip(
                                                selected = selectedAccountId != null,
                                                onClick = { accountDropdownExpanded = true },
                                                label = {
                                                    Text(
                                                        text = selectedAccountId?.let { id ->
                                                            accounts.find { it.id == id }?.name ?: strings.allAccounts
                                                        } ?: strings.allAccounts,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color.White
                                                    )
                                                },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    containerColor = AppColors.HeaderSeparator,
                                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                                )
                                            )
                                            DropdownMenu(
                                                expanded = accountDropdownExpanded,
                                                onDismissRequest = { accountDropdownExpanded = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text(strings.allAccounts) },
                                                    onClick = {
                                                        selectedAccountId = null
                                                        accountDropdownExpanded = false
                                                    }
                                                )
                                                accounts.forEach { account ->
                                                    DropdownMenuItem(
                                                        text = { Text(account.name) },
                                                        onClick = {
                                                            selectedAccountId = account.id
                                                            accountDropdownExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Chart/List toggle button - shows different icon based on current view
                                    IconButton(
                                        onClick = { showChartView = !showChartView },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Canvas(modifier = Modifier.size(20.dp)) {
                                            val iconColor = Color.White
                                            if (showChartView) {
                                                // Show list icon when chart view is active (clicking will switch to list)
                                                val lineHeight = size.height * 0.12f
                                                val lineSpacing = size.height * 0.28f
                                                // Three horizontal lines for list icon
                                                drawRect(
                                                    color = iconColor,
                                                    topLeft = Offset(0f, size.height * 0.15f),
                                                    size = Size(size.width, lineHeight)
                                                )
                                                drawRect(
                                                    color = iconColor,
                                                    topLeft = Offset(0f, size.height * 0.15f + lineSpacing),
                                                    size = Size(size.width, lineHeight)
                                                )
                                                drawRect(
                                                    color = iconColor,
                                                    topLeft = Offset(0f, size.height * 0.15f + lineSpacing * 2),
                                                    size = Size(size.width, lineHeight)
                                                )
                                            } else {
                                                // Show bar chart icon when list view is active (clicking will switch to chart)
                                                drawRect(
                                                    color = iconColor,
                                                    topLeft = Offset(0f, size.height * 0.5f),
                                                    size = Size(size.width * 0.25f, size.height * 0.5f)
                                                )
                                                drawRect(
                                                    color = iconColor,
                                                    topLeft = Offset(size.width * 0.35f, size.height * 0.2f),
                                                    size = Size(size.width * 0.25f, size.height * 0.8f)
                                                )
                                                drawRect(
                                                    color = iconColor,
                                                    topLeft = Offset(size.width * 0.7f, size.height * 0.35f),
                                                    size = Size(size.width * 0.25f, size.height * 0.65f)
                                                )
                                            }
                                        }
                                    }

                                    // Share button
                                    if (onShareSpending != null && categorySpending.isNotEmpty()) {
                                        Box {
                                            IconButton(
                                                onClick = { spendingShareMenuExpanded = true },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Image(
                                                    painter = painterResource(Res.drawable.share),
                                                    contentDescription = strings.share,
                                                    modifier = Modifier.size(20.dp),
                                                    colorFilter = ColorFilter.tint(Color.White)
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = spendingShareMenuExpanded,
                                                onDismissRequest = { spendingShareMenuExpanded = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text(strings.exportCsv) },
                                                    onClick = {
                                                        spendingShareMenuExpanded = false
                                                        val exportData = SpendingExportData(
                                                            totalIncome = filteredIncome,
                                                            totalExpenses = filteredExpenses,
                                                            categorySpending = categorySpending,
                                                            monthlySummary = monthlySummary
                                                        )
                                                        onShareSpending(ExportFormat.CSV, exportData)
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(strings.exportPdf) },
                                                    onClick = {
                                                        spendingShareMenuExpanded = false
                                                        val exportData = SpendingExportData(
                                                            totalIncome = filteredIncome,
                                                            totalExpenses = filteredExpenses,
                                                            categorySpending = categorySpending,
                                                            monthlySummary = monthlySummary
                                                        )
                                                        onShareSpending(ExportFormat.PDF, exportData)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Time period tab row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(AppColors.HeaderBackground)
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                listOf("week", "month", "year", "all", "custom").forEach { period ->
                                    FilterChip(
                                        selected = selectedTimePeriod == period,
                                        onClick = { selectedTimePeriod = period },
                                        label = {
                                            Text(
                                                text = when (period) {
                                                    "week" -> strings.periodWeek
                                                    "month" -> strings.periodMonth
                                                    "year" -> strings.periodYear
                                                    "all" -> strings.periodAll
                                                    "custom" -> strings.periodCustom
                                                    else -> period
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = AppColors.HeaderSeparator.copy(alpha = 0.5f),
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            labelColor = Color.White.copy(alpha = 0.7f),
                                            selectedLabelColor = Color.White
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            SpendingOverviewScreen(
                                totalIncome = filteredIncome,
                                totalExpenses = filteredExpenses,
                                categorySpending = categorySpending,
                                monthlySummary = monthlySummary,
                                transactions = transactions,
                                accounts = accounts,
                                selectedAccountId = selectedAccountId,
                                onBackClick = null,
                                onShare = null,
                                showChartView = showChartView,
                                selectedTimePeriod = selectedTimePeriod,
                                onTimePeriodChange = { selectedTimePeriod = it }
                            )
                        }
                        NavigationTab.MERCHANTS -> Column(modifier = Modifier.fillMaxSize()) {
                            AppHeader(
                                title = strings.merchantsTitle,
                                totalIncome = filteredIncome,
                                totalExpenses = filteredExpenses,
                                actions = {
                                    if (accounts.size > 1) {
                                        Box {
                                            FilterChip(
                                                selected = selectedAccountId != null,
                                                onClick = { accountDropdownExpanded = true },
                                                label = {
                                                    Text(
                                                        text = selectedAccountId?.let { id ->
                                                            accounts.find { it.id == id }?.name ?: strings.allAccounts
                                                        } ?: strings.allAccounts,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = Color.White
                                                    )
                                                },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    containerColor = AppColors.HeaderSeparator,
                                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                                )
                                            )
                                            DropdownMenu(
                                                expanded = accountDropdownExpanded,
                                                onDismissRequest = { accountDropdownExpanded = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text(strings.allAccounts) },
                                                    onClick = {
                                                        selectedAccountId = null
                                                        accountDropdownExpanded = false
                                                    }
                                                )
                                                accounts.forEach { account ->
                                                    DropdownMenuItem(
                                                        text = { Text(account.name) },
                                                        onClick = {
                                                            selectedAccountId = account.id
                                                            accountDropdownExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                            MerchantsScreen(
                                transactions = transactions,
                                accounts = accounts,
                                selectedAccountId = selectedAccountId
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
                                onThemeModeChange = { mode -> onThemeModeChange?.invoke(mode) },
                                biometricLockEnabled = biometricLockEnabled,
                                biometricAvailable = biometricAvailable,
                                onBiometricLockChange = onBiometricLockChange
                            )
                        }
                    }

                    // Import dialogs - handled through platform-specific dialog state
                    // The dialogState is cast and handled in the platform-specific composable wrapper
                    HandleImportDialogs(
                        dialogState = dialogState,
                        onImportChoice = onImportChoice,
                        onDismissSuccessDialog = onDismissSuccessDialog,
                        onRetryImport = onRetryImport,
                        onDismissErrorDialog = onDismissErrorDialog
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
    onDismissSuccessDialog: (() -> Unit)?,
    onRetryImport: (() -> Unit)? = null,
    onDismissErrorDialog: (() -> Unit)? = null
)

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

            // Contact Info Card - always visible at the bottom
            Spacer(modifier = Modifier.height(24.dp))
            ContactInfoCard(onEmailClick = onEmailClick)
            Spacer(modifier = Modifier.height(16.dp))
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
