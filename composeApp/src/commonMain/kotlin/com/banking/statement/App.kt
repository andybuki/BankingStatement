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
import androidx.compose.ui.unit.sp
import bankingstatement.composeapp.generated.resources.Res
import bankingstatement.composeapp.generated.resources.ic_calendar
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
import androidx.compose.foundation.clickable
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.todayIn
import org.jetbrains.compose.ui.tooling.preview.Preview

// Composition local for strings
val LocalStrings = compositionLocalOf { defaultEnglishStrings() }

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
    onDeleteStatement: ((Long) -> Unit)? = null,
    statementExpandedMap: Map<Long, Boolean> = emptyMap(),
    statementSortMap: Map<Long, StatementSortOrder> = emptyMap(),
    onStatementExpandedChange: ((Long, Boolean) -> Unit)? = null,
    onStatementSortOrderChange: ((Long, StatementSortOrder) -> Unit)? = null,
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
    // Notification Reminders
    remindersEnabled: Boolean = true,
    onRemindersEnabledChange: (Boolean) -> Unit = {},
    // Share App
    onShareApp: () -> Unit = {},
    // Rating prompt
    onRateApp: (() -> Unit)? = null,
    onRateLater: (() -> Unit)? = null,
    onRateNever: (() -> Unit)? = null,
    // Transaction pagination
    hasMoreTransactions: Boolean = false,
    isLoadingMoreTransactions: Boolean = false,
    onLoadMoreTransactions: (() -> Unit)? = null,
    // Account selection (lifted to ViewModel for DB-level filtering)
    selectedAccountId: Long? = null,
    onAccountSelected: ((Long?) -> Unit)? = null,
    totalTransactionCount: Long = 0L,
    // Date range filter (DB-level, epoch seconds)
    selectedDateRange: Pair<Long, Long>? = null,
    onDateRangeChange: ((Long?, Long?) -> Unit)? = null,
    // Sort order (DB-level, applies to full filtered corpus)
    selectedSortOrder: TransactionSortOrder = TransactionSortOrder.DATE_DESC,
    onSortOrderChange: ((TransactionSortOrder) -> Unit)? = null,
    // PDF viewer / source linking
    pdfViewerState: PdfViewerScreenState = PdfViewerScreenState(),
    onOpenTransactionSourcePdf: ((TransactionDisplay) -> Unit)? = null,
    onOpenStatementPdf: ((Long) -> Unit)? = null,
    onClosePdfViewer: (() -> Unit)? = null,
    // PDF access setting
    pdfAccessEnabled: Boolean = true,
    onPdfAccessEnabledChange: ((Boolean) -> Unit)? = null
) {
    var currentTab by remember { mutableStateOf(NavigationTab.HOME) }
    var showCategoryManagement by remember { mutableStateOf(false) }
    val strings = provideStrings()
    val accounts = accountsForManagement.map { AccountFilterOption(it.id, it.name) }

    // Share menu states for header actions
    var transactionsShareMenuExpanded by remember { mutableStateOf(false) }
    var spendingShareMenuExpanded by remember { mutableStateOf(false) }
    var showChartView by remember { mutableStateOf(false) }

    // accountDropdownExpanded is local UI state only
    var accountDropdownExpanded by remember { mutableStateOf(false) }

    // Spending tab time period state — defaults to "all" so full corpus is shown
    var selectedSpendingTimePeriod by remember { mutableStateOf("all") }
    var showSpendingFilterDialog by remember { mutableStateOf(false) }
    var showSpendingStartDatePicker by remember { mutableStateOf(false) }
    var showSpendingEndDatePicker by remember { mutableStateOf(false) }
    var pendingSpendingStartMillis by remember { mutableStateOf<Long?>(null) }
    val spendingStartDateState = rememberDatePickerState()
    val spendingEndDateState = rememberDatePickerState()
    var spendingEpochStart by remember { mutableStateOf<Long?>(null) }
    var spendingEpochEnd by remember { mutableStateOf<Long?>(null) }

    // Merchants tab time period state (lifted so the navy header summary
    // reflects the same filter that's applied inside the screen).
    var merchantsTimePeriod by remember { mutableStateOf(TimePeriod.ALL) }
    var merchantsEpochStart by remember { mutableStateOf<Long?>(null) }
    var merchantsEpochEnd by remember { mutableStateOf<Long?>(null) }
    var showMerchantsStartDatePicker by remember { mutableStateOf(false) }
    var showMerchantsEndDatePicker by remember { mutableStateOf(false) }
    var pendingMerchantsStartMillis by remember { mutableStateOf<Long?>(null) }
    val merchantsStartDateState = rememberDatePickerState()
    val merchantsEndDateState = rememberDatePickerState()

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

    // Income/expenses for the Merchants tab also honour the time-period filter
    // so the navy header totals match the rows below.
    val merchantsScopedTx = remember(transactions, selectedAccountId, merchantsTimePeriod, merchantsEpochStart, merchantsEpochEnd) {
        val byAccount = if (selectedAccountId == null) transactions
        else transactions.filter { it.accountId == selectedAccountId }
        filterByTimePeriod(byAccount, merchantsTimePeriod, merchantsEpochStart, merchantsEpochEnd)
    }
    val merchantsFilteredIncome = remember(merchantsScopedTx) {
        merchantsScopedTx.filter { it.amount > 0 }.sumOf { it.amount }
    }
    val merchantsFilteredExpenses = remember(merchantsScopedTx) {
        merchantsScopedTx.filter { it.amount < 0 }.sumOf { it.amount }
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
                    color = MaterialTheme.colorScheme.background,
                    contentColor = AppColors.TextPrimary
                ) {
                    when (currentTab) {
                        NavigationTab.HOME -> HomeScreen(
                            onPickFile = onPickFile,
                            importState = importState,
                            stats = stats,
                            accounts = accountsForManagement,
                            totalIncome = totalIncome,
                            totalExpenses = totalExpenses,
                            transactions = transactions,
                            categorySpending = categorySpending,
                            showSuccessCard = showSuccessCard,
                            showTutorial = showTutorial,
                            onDismissTutorial = onDismissTutorial,
                            onEmailClick = onEmailClick,
                            onNavigateToTransactions = { currentTab = NavigationTab.TRANSACTIONS },
                            onNavigateToSpending = { currentTab = NavigationTab.SPENDING },
                            onNavigateToSettings = { currentTab = NavigationTab.SETTINGS }
                        )
                        NavigationTab.TRANSACTIONS -> Column(modifier = Modifier.fillMaxSize()) {
                            AppHeader(
                                title = strings.tabTransactions,
                                showSummary = false,
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
                                                        onAccountSelected?.invoke(null)
                                                        accountDropdownExpanded = false
                                                    }
                                                )
                                                accounts.forEach { account ->
                                                    DropdownMenuItem(
                                                        text = { Text(account.name) },
                                                        onClick = {
                                                            onAccountSelected?.invoke(account.id)
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
                                            IconButton(
                                                onClick = { transactionsShareMenuExpanded = true },
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
                                onAccountSelected = { onAccountSelected?.invoke(it) },
                                totalTransactionCount = totalTransactionCount,
                                selectedDateRange = selectedDateRange,
                                onDateRangeChange = onDateRangeChange,
                                selectedSortOrder = selectedSortOrder,
                                onSortOrderChange = onSortOrderChange,
                                customCategories = customCategories,
                                onBackClick = null,
                                onShare = null,  // Share moved to header
                                onCategoryChange = onCategoryChange,
                                onCustomCategoryChange = onCustomCategoryChange,
                                onManageCategories = { showCategoryManagement = true },
                                onViewSourcePdf = if (pdfAccessEnabled && onOpenTransactionSourcePdf != null) {
                                    onOpenTransactionSourcePdf
                                } else null,
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
                                    .padding(horizontal = 16.dp)
                                    .padding(top = 12.dp, bottom = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = strings.spendingTitle,
                                    fontSize = 22.sp,
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
                                                        onAccountSelected?.invoke(null)
                                                        accountDropdownExpanded = false
                                                    }
                                                )
                                                accounts.forEach { account ->
                                                    DropdownMenuItem(
                                                        text = { Text(account.name) },
                                                        onClick = {
                                                            onAccountSelected?.invoke(account.id)
                                                            accountDropdownExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Calendar icon for time period filter
                                    IconButton(
                                        onClick = { showSpendingFilterDialog = true },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Image(
                                            painter = painterResource(Res.drawable.ic_calendar),
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            colorFilter = ColorFilter.tint(
                                                if (selectedSpendingTimePeriod != "all") MaterialTheme.colorScheme.primary else Color.White
                                            )
                                        )
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

                            SpendingOverviewScreen(
                                totalIncome = totalIncome,
                                totalExpenses = totalExpenses,
                                categorySpending = categorySpending,
                                monthlySummary = monthlySummary,
                                transactions = transactions,
                                accounts = accounts,
                                selectedAccountId = selectedAccountId,
                                onBackClick = null,
                                onShare = null,
                                showChartView = showChartView,
                                selectedTimePeriod = selectedSpendingTimePeriod,
                                spendingEpochStart = spendingEpochStart,
                                spendingEpochEnd = spendingEpochEnd
                            )
                        }
                        NavigationTab.MERCHANTS -> Column(modifier = Modifier.fillMaxSize()) {
                            AppHeader(
                                title = strings.merchantsTitle,
                                totalIncome = merchantsFilteredIncome,
                                totalExpenses = merchantsFilteredExpenses,
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
                                                        onAccountSelected?.invoke(null)
                                                        accountDropdownExpanded = false
                                                    }
                                                )
                                                accounts.forEach { account ->
                                                    DropdownMenuItem(
                                                        text = { Text(account.name) },
                                                        onClick = {
                                                            onAccountSelected?.invoke(account.id)
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
                                selectedAccountId = selectedAccountId,
                                timePeriod = merchantsTimePeriod,
                                onTimePeriodChange = { period ->
                                    merchantsTimePeriod = period
                                    if (period != TimePeriod.CUSTOM) {
                                        merchantsEpochStart = null
                                        merchantsEpochEnd = null
                                    }
                                },
                                customStartEpoch = merchantsEpochStart,
                                customEndEpoch = merchantsEpochEnd,
                                onRequestCustomRange = {
                                    showMerchantsStartDatePicker = true
                                }
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
                                onDeleteStatement = { id -> onDeleteStatement?.invoke(id) },
                                statementExpandedMap = statementExpandedMap,
                                statementSortMap = statementSortMap,
                                onStatementExpandedChange = { id, exp -> onStatementExpandedChange?.invoke(id, exp) },
                                onStatementSortOrderChange = { id, order -> onStatementSortOrderChange?.invoke(id, order) },
                                currentThemeMode = themeMode,
                                onThemeModeChange = { mode -> onThemeModeChange?.invoke(mode) },
                                biometricLockEnabled = biometricLockEnabled,
                                biometricAvailable = biometricAvailable,
                                onBiometricLockChange = onBiometricLockChange,
                                onEmailClick = onEmailClick,
                                remindersEnabled = remindersEnabled,
                                onRemindersEnabledChange = onRemindersEnabledChange,
                                pdfAccessEnabled = pdfAccessEnabled,
                                onPdfAccessEnabledChange = { enabled -> onPdfAccessEnabledChange?.invoke(enabled) },
                                onShareApp = onShareApp
                            )
                        }
                    }

                    // Spending filter period dialog — brand-styled choice
                    // dialog. CUSTOM is a regular option that opens the
                    // start/end date pickers like the Merchants tab.
                    if (showSpendingFilterDialog) {
                        val tz = TimeZone.currentSystemDefault()
                        com.banking.statement.ui.components.MoneyLupeChoiceDialog(
                            eyebrow = "Time range",
                            title = "Filter by time",
                            options = listOf(
                                "all" to strings.periodAll,
                                "week" to strings.periodWeek,
                                "month" to strings.periodMonth,
                                "year" to strings.periodYear,
                                "custom" to strings.periodCustom
                            ),
                            selected = selectedSpendingTimePeriod,
                            onSelect = { period ->
                                showSpendingFilterDialog = false
                                if (period == "custom") {
                                    selectedSpendingTimePeriod = "custom"
                                    showSpendingStartDatePicker = true
                                } else {
                                    selectedSpendingTimePeriod = period
                                    val today = Clock.System.todayIn(tz)
                                    when (period) {
                                        "all" -> {
                                            spendingEpochStart = null
                                            spendingEpochEnd = null
                                        }
                                        "week" -> {
                                            spendingEpochStart = LocalDate.fromEpochDays(today.toEpochDays() - 6).atStartOfDayIn(tz).epochSeconds
                                            spendingEpochEnd = today.atStartOfDayIn(tz).epochSeconds + 86399L
                                        }
                                        "month" -> {
                                            val s = LocalDate(today.year, today.monthNumber, 1).atStartOfDayIn(tz).epochSeconds
                                            val nextMonth = if (today.monthNumber == 12) LocalDate(today.year + 1, 1, 1) else LocalDate(today.year, today.monthNumber + 1, 1)
                                            spendingEpochStart = s
                                            spendingEpochEnd = nextMonth.atStartOfDayIn(tz).epochSeconds - 1L
                                        }
                                        "year" -> {
                                            spendingEpochStart = LocalDate(today.year, 1, 1).atStartOfDayIn(tz).epochSeconds
                                            spendingEpochEnd = LocalDate(today.year + 1, 1, 1).atStartOfDayIn(tz).epochSeconds - 1L
                                        }
                                    }
                                }
                            },
                            onDismiss = { showSpendingFilterDialog = false },
                            secondaryAction = if (selectedSpendingTimePeriod != "all") {
                                strings.clear to {
                                    selectedSpendingTimePeriod = "all"
                                    spendingEpochStart = null
                                    spendingEpochEnd = null
                                    showSpendingFilterDialog = false
                                }
                            } else null
                        )
                    }

                    // Spending start date picker
                    if (showSpendingStartDatePicker) {
                        DatePickerDialog(
                            onDismissRequest = { showSpendingStartDatePicker = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    val millis = spendingStartDateState.selectedDateMillis
                                    if (millis != null) {
                                        pendingSpendingStartMillis = millis
                                        showSpendingStartDatePicker = false
                                        showSpendingEndDatePicker = true
                                    }
                                }) { Text("Next") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showSpendingStartDatePicker = false }) { Text(strings.cancel) }
                            }
                        ) {
                            DatePicker(
                                state = spendingStartDateState,
                                title = { Text("Select start date", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) }
                            )
                        }
                    }

                    // Spending end date picker
                    if (showSpendingEndDatePicker) {
                        DatePickerDialog(
                            onDismissRequest = { showSpendingEndDatePicker = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    val startMillis = pendingSpendingStartMillis
                                    val endMillis = spendingEndDateState.selectedDateMillis
                                    if (startMillis != null && endMillis != null) {
                                        spendingEpochStart = startMillis / 1000L
                                        spendingEpochEnd = endMillis / 1000L + 86399L
                                    }
                                    showSpendingEndDatePicker = false
                                }) { Text(strings.apply) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showSpendingEndDatePicker = false }) { Text(strings.cancel) }
                            }
                        ) {
                            DatePicker(
                                state = spendingEndDateState,
                                title = { Text("Select end date", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) }
                            )
                        }
                    }

                    // Merchants tab — start date picker (custom range step 1)
                    if (showMerchantsStartDatePicker) {
                        DatePickerDialog(
                            onDismissRequest = { showMerchantsStartDatePicker = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    val millis = merchantsStartDateState.selectedDateMillis
                                    if (millis != null) {
                                        pendingMerchantsStartMillis = millis
                                        showMerchantsStartDatePicker = false
                                        showMerchantsEndDatePicker = true
                                    }
                                }) { Text("Next") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showMerchantsStartDatePicker = false }) { Text(strings.cancel) }
                            }
                        ) {
                            DatePicker(
                                state = merchantsStartDateState,
                                title = { Text("Select start date", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) }
                            )
                        }
                    }

                    // Merchants tab — end date picker (custom range step 2)
                    if (showMerchantsEndDatePicker) {
                        DatePickerDialog(
                            onDismissRequest = { showMerchantsEndDatePicker = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    val startMillis = pendingMerchantsStartMillis
                                    val endMillis = merchantsEndDateState.selectedDateMillis
                                    if (startMillis != null && endMillis != null) {
                                        merchantsEpochStart = startMillis / 1000L
                                        merchantsEpochEnd = endMillis / 1000L + 86399L
                                        merchantsTimePeriod = TimePeriod.CUSTOM
                                    }
                                    showMerchantsEndDatePicker = false
                                }) { Text(strings.apply) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showMerchantsEndDatePicker = false }) { Text(strings.cancel) }
                            }
                        ) {
                            DatePicker(
                                state = merchantsEndDateState,
                                title = { Text("Select end date", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) }
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
                        onDismissErrorDialog = onDismissErrorDialog,
                        onRateApp = onRateApp,
                        onRateLater = onRateLater,
                        onRateNever = onRateNever
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

                    // In-app PDF viewer overlay (source linking / receipt view)
                    if (pdfViewerState.isOpen && pdfViewerState.filePath != null) {
                        PdfViewerScreen(
                            filePath = pdfViewerState.filePath,
                            fileName = pdfViewerState.fileName,
                            initialPage = pdfViewerState.initialPage,
                            highlightSnippet = pdfViewerState.highlightSnippet,
                            highlightTitle = pdfViewerState.highlightTitle,
                            highlightBbox = pdfViewerState.highlightBbox,
                            onClose = { onClosePdfViewer?.invoke() }
                        )
                    }
                }
            }
        }
    }
}
