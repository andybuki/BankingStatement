package com.banking.statement

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.banking.statement.ui.ImportErrorDetails
import androidx.lifecycle.ViewModel
import com.banking.statement.categorization.CategoryOverrideManager
import com.banking.statement.categorization.CategoryOverrideResult
import com.banking.statement.categorization.CustomCategory
import com.banking.statement.categorization.KeywordDatabase
import com.banking.statement.categorization.MerchantDatabase
import com.banking.statement.categorization.TransactionCategory
import com.banking.statement.categorization.TransactionCategorizer
import com.banking.statement.db.AccountMatchResult
import com.banking.statement.db.TransactionRepository
import com.banking.statement.export.ExportFormat
import com.banking.statement.export.ExportManager
import com.banking.statement.export.FileExporter
import com.banking.statement.export.PdfGenerator
import com.banking.statement.export.SpendingExportData
import com.banking.statement.parser.ImportFileType
import com.banking.statement.parser.ParseResult
import com.banking.statement.ui.AccountManagementItem
import com.banking.statement.ui.AccountOption
import com.banking.statement.ui.CategorySpending
import com.banking.statement.ui.ImportChoice
import com.banking.statement.ui.MonthlySummary
import com.banking.statement.ui.TransactionDisplay
import com.banking.statement.ui.theme.ThemeMode
import com.banking.statement.ui.theme.ThemePreferences
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Consolidated UI state for the financial data displayed across screens.
 */
data class FinancialUiState(
    val transactions: List<TransactionDisplay> = emptyList(),
    val categorySpending: List<CategorySpending> = emptyList(),
    val monthlySummary: List<MonthlySummary> = emptyList(),
    val totalIncome: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val hasMoreTransactions: Boolean = false,
    val isLoadingMore: Boolean = false
)

/**
 * Consolidated UI state for app-level settings and preferences.
 */
data class AppSettingsState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val showTutorial: Boolean = false,
    val customCategories: List<CustomCategory> = emptyList(),
    val biometricLockEnabled: Boolean = false,
    val biometricAvailable: Boolean = false
)

/**
 * Main ViewModel holding all app state and business logic.
 * Uses StateFlow for reactive, lifecycle-aware state management.
 */
class MainViewModel(
    private val context: Context,
    val repository: TransactionRepository,
    private val keywordDatabase: KeywordDatabase,
    private val merchantDatabase: MerchantDatabase,
    private val categoryOverrideManager: CategoryOverrideManager,
    private val transactionCategorizer: TransactionCategorizer,
    val fileImportProcessor: FileImportProcessor,
    private val fileExporter: FileExporter,
    private val pdfGenerator: PdfGenerator,
    private val themePreferences: ThemePreferences,
    private val appPreferences: AppPreferences,
    private val biometricLockManager: BiometricLockManager
) : ViewModel() {

    // --- Observable State (StateFlow) ---
    private val _importState = MutableStateFlow(ImportState())
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private val _stats = MutableStateFlow(DatabaseStats())
    val stats: StateFlow<DatabaseStats> = _stats.asStateFlow()

    private val _financialState = MutableStateFlow(FinancialUiState())
    val financialState: StateFlow<FinancialUiState> = _financialState.asStateFlow()

    private val _dialogState = MutableStateFlow(ImportDialogState())
    val dialogState: StateFlow<ImportDialogState> = _dialogState.asStateFlow()

    // Track last import URI for retry support
    private var lastImportUri: Uri? = null

    // Pagination state for transaction loading
    private val PAGE_SIZE = 100
    private var currentTransactionPage = 0
    private var allTransactionsLoaded = false
    private var isLoadingMore = false

    private val _accountsForManagement = MutableStateFlow<List<AccountManagementItem>>(emptyList())
    val accountsForManagement: StateFlow<List<AccountManagementItem>> = _accountsForManagement.asStateFlow()

    private val _appSettings = MutableStateFlow(AppSettingsState())
    val appSettings: StateFlow<AppSettingsState> = _appSettings.asStateFlow()

    private val coroutineScope = viewModelScope

    init {
        // Wire the categorizer into the repository (breaks circular dependency)
        repository.transactionCategorizer = transactionCategorizer

        // Load keyword database from assets
        loadKeywordDatabase()

        // Load merchant data from CSV if not already loaded
        loadMerchantDatabase()

        // Backfill categories for existing transactions (one-time migration)
        coroutineScope.launch(Dispatchers.IO) {
            val backfilledCount = repository.backfillAutoCategories()
            android.util.Log.d("Migration", "Backfilled $backfilledCount transactions")

            val fixedCount = repository.fixMiscategorizedSupermarkets()
            android.util.Log.d("Migration", "Fixed $fixedCount miscategorized transactions")
        }

        // Initialize app settings state
        _appSettings.update {
            it.copy(
                themeMode = themePreferences.getThemeMode(),
                showTutorial = !appPreferences.isTutorialDismissed(),
                biometricLockEnabled = appPreferences.isBiometricLockEnabled(),
                biometricAvailable = biometricLockManager.canAuthenticate()
            )
        }

        // Clean up old export files
        fileExporter.cleanupOldExports()

        // Load stats and data
        updateStats()
        loadTransactionData()
        loadAccountsData()
        loadCustomCategories()
    }

    // =====================================================================
    // FILE IMPORT ORCHESTRATION
    // =====================================================================

    fun processFile(uri: Uri) {
        lastImportUri = uri

        // Launch on Default dispatcher to avoid blocking the main thread.
        // StateFlow updates are thread-safe and don't require Main dispatcher.
        coroutineScope.launch(Dispatchers.Default) {
            _importState.value = ImportState(
                isProcessing = true,
                progress = 0,
                progressMessage = context.getString(R.string.processing_reading)
            )

            try {
                val fileName = withContext(Dispatchers.IO) {
                    fileImportProcessor.getFileName(uri) ?: "document"
                }
                _importState.update { it.copy(progress = 10, progressMessage = context.getString(R.string.processing_reading)) }

                val bytes = withContext(Dispatchers.IO) {
                    fileImportProcessor.readFileBytes(uri)
                }
                if (bytes == null) {
                    _importState.value = ImportState(isProcessing = false)
                    showImportErrorDialog(
                        errorMessage = context.getString(R.string.import_error_file_could_not_be_read),
                        fileName = fileName
                    )
                    return@launch
                }
                _importState.update { it.copy(progress = 20, progressMessage = context.getString(R.string.processing_detecting)) }

                val fileType = ImportFileType.fromFileName(fileName)
                    ?: fileImportProcessor.detectFileType(bytes)
                if (fileType == null) {
                    _importState.value = ImportState(isProcessing = false)
                    showImportErrorDialog(
                        errorMessage = context.getString(R.string.import_error_unsupported_format_detail),
                        fileName = fileName
                    )
                    return@launch
                }
                _importState.update { it.copy(progress = 30, progressMessage = context.getString(R.string.processing_parsing)) }

                val parseResult = withContext(Dispatchers.IO) {
                    when (fileType) {
                        ImportFileType.CSV -> {
                            _importState.update { it.copy(progress = 40, progressMessage = context.getString(R.string.processing_csv)) }
                            fileImportProcessor.parseCsv(bytes, fileName)
                        }
                        ImportFileType.EXCEL -> {
                            _importState.update { it.copy(progress = 40, progressMessage = context.getString(R.string.processing_excel)) }
                            fileImportProcessor.parseExcel(bytes, fileName)
                        }
                        ImportFileType.PDF -> {
                            _importState.update { it.copy(progress = 40, progressMessage = context.getString(R.string.processing_pdf)) }

                            val preProcessResult = fileImportProcessor.preProcessPdf(bytes)

                            if (preProcessResult.needsUserSelection && preProcessResult.detectedBanks.isNotEmpty()) {
                                _dialogState.update { it.copy(
                                    showBankSelectionDialog = true,
                                    detectedBanks = preProcessResult.detectedBanks,
                                    pendingPdfData = PendingPdfData(bytes, preProcessResult.text ?: "", fileName, uri)
                                ) }
                                _importState.value = ImportState(isProcessing = false)
                                null
                            } else {
                                fileImportProcessor.parsePdf(bytes, fileName, preProcessResult)
                            }
                        }
                    }
                }

                if (parseResult == null) {
                    return@launch
                }

                _importState.update { it.copy(progress = 70, progressMessage = context.getString(R.string.processing_categorizing)) }

                if (parseResult.success && parseResult.transactions.isNotEmpty()) {
                    _importState.update { it.copy(progress = 80, progressMessage = context.getString(R.string.processing_saving)) }

                    val filePath = withContext(Dispatchers.IO) {
                        if (fileType == ImportFileType.PDF) {
                            fileImportProcessor.savePdfToStorage(uri, fileName)
                        } else null
                    }

                    _importState.update { it.copy(progress = 90, progressMessage = context.getString(R.string.processing_finalizing)) }
                    handleSuccessfulParse(parseResult, fileName, filePath, fileType)
                } else {
                    _importState.value = ImportState(isProcessing = false)
                    showImportErrorDialog(
                        errorMessage = parseResult.errorMessage ?: context.getString(R.string.import_error_not_bank_statement_detail),
                        fileName = fileName,
                        fileFormat = fileType.name,
                        technicalDetails = parseResult.bankName.takeIf { it != "Unknown" }
                    )
                }

            } catch (e: Exception) {
                _importState.value = ImportState(isProcessing = false)
                showImportErrorDialog(
                    errorMessage = e.message ?: context.getString(R.string.import_error_generic)
                )
            }
        }
    }

    private suspend fun handleSuccessfulParse(
        parseResult: ParseResult,
        fileName: String,
        filePath: String?,
        fileType: ImportFileType
    ) {
        val matchResult = withContext(Dispatchers.IO) {
            repository.findMatchingAccount(
                bankName = parseResult.bankName,
                iban = parseResult.accountIban
            )
        }

        when (matchResult) {
            is AccountMatchResult.IbanMatch -> {
                val result = withContext(Dispatchers.IO) {
                    repository.saveImportToAccount(
                        accountId = matchResult.account.id,
                        parseResult = parseResult,
                        fileName = fileName,
                        filePath = filePath,
                        sourceType = fileType.name
                    )
                }

                _importState.value = ImportState(
                    isProcessing = false,
                    parseResult = parseResult,
                    savedToDatabase = true,
                    transactionCount = result.transactionsImported
                )

                _dialogState.update { it.copy(
                    showSuccessDialog = true,
                    importResult = result.copy(isNewAccount = false)
                ) }

                updateStats()
            }

            is AccountMatchResult.BankMatch, AccountMatchResult.NoMatch -> {
                val existingAccounts = withContext(Dispatchers.IO) {
                    repository.getAccountSummary().map { summary ->
                        AccountOption(
                            id = summary.id,
                            name = summary.name,
                            bankName = summary.bank_name,
                            iban = summary.iban,
                            color = summary.color,
                            transactionCount = summary.transaction_count,
                            balance = summary.balance
                        )
                    }
                }

                _importState.value = ImportState(isProcessing = false)

                _dialogState.value = ImportDialogState(
                    showAccountDialog = true,
                    pendingImport = PendingImport(
                        parseResult = parseResult,
                        fileName = fileName,
                        filePath = filePath,
                        fileType = fileType,
                        matchResult = matchResult
                    ),
                    existingAccounts = existingAccounts
                )
            }
        }
    }

    fun handleImportChoice(choice: ImportChoice) {
        val pending = _dialogState.value.pendingImport ?: return

        coroutineScope.launch {
            when (choice) {
                is ImportChoice.CreateNew -> {
                    _dialogState.update { it.copy(showAccountDialog = false) }
                    _importState.value = ImportState(isProcessing = true, progress = 80, progressMessage = context.getString(R.string.processing_saving))

                    val result = withContext(Dispatchers.IO) {
                        repository.saveImportWithNewAccount(
                            accountName = choice.accountName,
                            parseResult = pending.parseResult,
                            fileName = pending.fileName,
                            filePath = pending.filePath,
                            sourceType = pending.fileType.name
                        )
                    }

                    _importState.value = ImportState(
                        isProcessing = false,
                        parseResult = pending.parseResult,
                        savedToDatabase = true,
                        transactionCount = result.transactionsImported,
                        progress = 100
                    )

                    _dialogState.value = ImportDialogState(
                        showSuccessDialog = true,
                        importResult = result
                    )

                    updateStats()
                }

                is ImportChoice.AddToExisting -> {
                    _dialogState.update { it.copy(showAccountDialog = false) }
                    _importState.value = ImportState(isProcessing = true, progress = 80, progressMessage = context.getString(R.string.processing_saving))

                    val result = withContext(Dispatchers.IO) {
                        repository.saveImportToAccount(
                            accountId = choice.accountId,
                            parseResult = pending.parseResult,
                            fileName = pending.fileName,
                            filePath = pending.filePath,
                            sourceType = pending.fileType.name
                        )
                    }

                    _importState.value = ImportState(
                        isProcessing = false,
                        parseResult = pending.parseResult,
                        savedToDatabase = true,
                        transactionCount = result.transactionsImported,
                        progress = 100
                    )

                    _dialogState.value = ImportDialogState(
                        showSuccessDialog = true,
                        importResult = result
                    )

                    updateStats()
                }

                ImportChoice.Cancel -> {
                    _dialogState.value = ImportDialogState()
                    _importState.value = ImportState(isProcessing = false)
                }
            }
        }
    }

    fun handleBankSelection(bankName: String) {
        val pendingData = _dialogState.value.pendingPdfData ?: return

        _dialogState.update { it.copy(
            showBankSelectionDialog = false,
            detectedBanks = emptyList(),
            pendingPdfData = null
        ) }
        _importState.value = ImportState(isProcessing = true, progress = 50, progressMessage = context.getString(R.string.processing_parsing))

        coroutineScope.launch {
            val parseResult = withContext(Dispatchers.IO) {
                fileImportProcessor.parsePdfWithParser(pendingData.text, pendingData.fileName, bankName)
            }

            _importState.update { it.copy(progress = 70, progressMessage = context.getString(R.string.processing_categorizing)) }

            if (parseResult.success && parseResult.transactions.isNotEmpty()) {
                _importState.update { it.copy(progress = 80, progressMessage = context.getString(R.string.processing_saving)) }

                val filePath = fileImportProcessor.savePdfToStorage(pendingData.uri, pendingData.fileName)
                _importState.update { it.copy(progress = 90, progressMessage = context.getString(R.string.processing_finalizing)) }
                handleSuccessfulParse(parseResult, pendingData.fileName, filePath, ImportFileType.PDF)
            } else {
                _importState.value = ImportState(isProcessing = false)
                showImportErrorDialog(
                    errorMessage = parseResult.errorMessage ?: context.getString(R.string.import_error_not_bank_statement_detail),
                    fileName = pendingData.fileName,
                    fileFormat = "PDF",
                    technicalDetails = parseResult.bankName.takeIf { it != "Unknown" }
                )
            }
        }
    }

    fun cancelBankSelection() {
        _dialogState.update { it.copy(
            showBankSelectionDialog = false,
            detectedBanks = emptyList(),
            pendingPdfData = null
        ) }
        _importState.value = ImportState(isProcessing = false)
    }

    fun dismissSuccessDialog() {
        _dialogState.update { it.copy(showSuccessDialog = false, importResult = null) }
    }

    private fun showImportErrorDialog(
        errorMessage: String,
        fileName: String? = null,
        fileFormat: String? = null,
        technicalDetails: String? = null
    ) {
        _dialogState.update { it.copy(
            showErrorDialog = true,
            errorDetails = ImportErrorDetails(
                errorMessage = errorMessage,
                fileName = fileName,
                fileFormat = fileFormat,
                technicalDetails = technicalDetails
            )
        ) }
    }

    fun dismissErrorDialog() {
        _dialogState.update { it.copy(showErrorDialog = false, errorDetails = null) }
    }

    fun retryImport() {
        _dialogState.update { it.copy(showErrorDialog = false, errorDetails = null) }
        lastImportUri?.let { processFile(it) }
    }

    // =====================================================================
    // DATA LOADING
    // =====================================================================

    private fun updateStats() {
        coroutineScope.launch {
            val statementsCount = withContext(Dispatchers.IO) {
                repository.getStatementCount().toInt()
            }
            val transactionsCount = withContext(Dispatchers.IO) {
                repository.getTransactionCount().toInt()
            }
            val accountsCount = withContext(Dispatchers.IO) {
                repository.getAccountCount().toInt()
            }
            _stats.value = DatabaseStats(
                totalStatements = statementsCount,
                totalTransactions = transactionsCount,
                totalAccounts = accountsCount
            )
            loadTransactionData()
            loadAccountsData()
        }
    }

    private fun loadTransactionData() {
        // Reset pagination state
        currentTransactionPage = 0
        allTransactionsLoaded = false

        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                val accountNames = repository.getAccountSummary().associate { it.id to it.name }

                val customCategoriesMap = repository.getAllCategories().associate { category ->
                    category.id to CustomCategory(
                        id = category.id,
                        name = category.name,
                        icon = category.icon ?: "\uD83C\uDFF7\uFE0F",
                        color = category.color ?: "#808080",
                        parentId = category.parent_id
                    )
                }

                // Load first page of transactions for the list
                val firstPage = repository.getTransactionsPaged(
                    PAGE_SIZE.toLong(), 0L
                )
                val totalCount = repository.getTransactionCount()
                allTransactionsLoaded = firstPage.size < PAGE_SIZE
                currentTransactionPage = 1

                val mappedFirstPage = mapTransactions(firstPage, accountNames, customCategoriesMap)

                // Load aggregates from DB directly (not from in-memory list)
                // This avoids loading all transactions into memory for summaries
                val monthlyCategoryData = repository.getCategorySpendingByMonth().map { row ->
                    Triple(row.month ?: "", row.auto_category ?: "", row.total ?: 0.0)
                }
                val trends = com.banking.statement.ui.TrendCalculator.calculateTrends(monthlyCategoryData)

                // Category spending: use ALL transactions for accurate stats
                // but compute via DB aggregates where possible
                val allTransactions = repository.getAllTransactions()
                val allMapped = mapTransactions(allTransactions, accountNames, customCategoriesMap)

                val spendingByCategory = allMapped
                    .filter { it.amount < 0 }
                    .groupBy { it.category }
                    .mapValues { (_, txs) -> txs.sumOf { it.amount } }

                val totalExpensesAmount = spendingByCategory.values.sum()

                val computedCategorySpending = spendingByCategory.map { (cat, total) ->
                    CategorySpending(
                        category = cat,
                        totalAmount = total,
                        transactionCount = allMapped.count { it.category == cat && it.amount < 0 },
                        percentage = if (totalExpensesAmount != 0.0) {
                            ((total / totalExpensesAmount) * 100).toFloat()
                        } else 0f,
                        trend = trends[cat]
                    )
                }.sortedBy { it.totalAmount }

                val computedExpenses = allTransactions.filter { it.amount < 0 }.sumOf { it.amount }
                val computedIncome = allTransactions.filter { it.amount > 0 }.sumOf { it.amount }

                val monthlyData = allTransactions.groupBy { tx ->
                    val d = Instant.fromEpochSeconds(tx.booking_date)
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                        .date
                    "${d.year}-${d.monthNumber.toString().padStart(2, '0')}"
                }

                val computedMonthlySummary = monthlyData.map { (month, txs) ->
                    val income = txs.filter { it.amount > 0 }.sumOf { it.amount }
                    val expenses = txs.filter { it.amount < 0 }.sumOf { it.amount }
                    MonthlySummary(
                        month = formatMonth(month),
                        income = income,
                        expenses = expenses
                    )
                }.sortedByDescending { it.month }

                // Atomic update of all financial state
                _financialState.value = FinancialUiState(
                    transactions = mappedFirstPage,
                    categorySpending = computedCategorySpending,
                    monthlySummary = computedMonthlySummary,
                    totalIncome = computedIncome,
                    totalExpenses = computedExpenses,
                    hasMoreTransactions = !allTransactionsLoaded
                )
            }
        }
    }

    /**
     * Load the next page of transactions for the list. Called when the user
     * scrolls near the bottom of the transaction list.
     */
    fun loadMoreTransactions() {
        if (allTransactionsLoaded || isLoadingMore) return
        isLoadingMore = true
        _financialState.update { it.copy(isLoadingMore = true) }

        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                val accountNames = repository.getAccountSummary().associate { it.id to it.name }
                val customCategoriesMap = repository.getAllCategories().associate { category ->
                    category.id to CustomCategory(
                        id = category.id,
                        name = category.name,
                        icon = category.icon ?: "\uD83C\uDFF7\uFE0F",
                        color = category.color ?: "#808080",
                        parentId = category.parent_id
                    )
                }

                val offset = currentTransactionPage * PAGE_SIZE
                val nextPage = repository.getTransactionsPaged(
                    PAGE_SIZE.toLong(), offset.toLong()
                )

                if (nextPage.size < PAGE_SIZE) {
                    allTransactionsLoaded = true
                }
                currentTransactionPage++

                val mappedPage = mapTransactions(nextPage, accountNames, customCategoriesMap)

                _financialState.update { state ->
                    state.copy(
                        transactions = state.transactions + mappedPage,
                        hasMoreTransactions = !allTransactionsLoaded,
                        isLoadingMore = false
                    )
                }
                isLoadingMore = false
            }
        }
    }

    private fun mapTransactions(
        transactions: List<com.banking.statement.db.Transactions>,
        accountNames: Map<Long, String>,
        customCategoriesMap: Map<Long, CustomCategory>
    ): List<TransactionDisplay> {
        return transactions.map { tx ->
            val overrideResult = categoryOverrideManager.findOverrideWithCustom(tx.description, tx.counterparty_name)

            val (category, customCategoryId, customCategoryName, customCategoryIcon, customCategoryColor) = when (overrideResult) {
                is CategoryOverrideResult.Custom -> {
                    val customCat = customCategoriesMap[overrideResult.categoryId]
                    if (customCat != null) {
                        Tuple5(TransactionCategory.OTHER, customCat.id, customCat.name, customCat.icon, customCat.color)
                    } else {
                        Tuple5(determineAutoCategory(tx), null, null, null, null)
                    }
                }
                is CategoryOverrideResult.Predefined -> {
                    Tuple5(overrideResult.category, null, null, null, null)
                }
                null -> {
                    Tuple5(determineAutoCategory(tx), null, null, null, null)
                }
            }

            val date = Instant.fromEpochSeconds(tx.booking_date)
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date

            TransactionDisplay(
                id = tx.id,
                date = "${date.dayOfMonth.toString().padStart(2, '0')}.${date.monthNumber.toString().padStart(2, '0')}.${date.year}",
                description = tx.description,
                amount = tx.amount,
                currency = tx.currency,
                category = category,
                counterparty = tx.counterparty_name,
                detailText = TransactionDisplay.extractDetailText(
                    description = tx.description,
                    remittanceInfo = tx.remittance_info,
                    counterparty = tx.counterparty_name
                ),
                accountId = tx.account_id ?: 0L,
                accountName = tx.account_id?.let { accountNames[it] } ?: "",
                customCategoryId = customCategoryId,
                customCategoryName = customCategoryName,
                customCategoryIcon = customCategoryIcon,
                customCategoryColor = customCategoryColor
            )
        }
    }

    private fun formatMonth(yearMonth: String): String {
        val parts = yearMonth.split("-")
        if (parts.size != 2) return yearMonth
        val monthNames = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        )
        val monthIndex = parts[1].toIntOrNull()?.minus(1) ?: return yearMonth
        return if (monthIndex in 0..11) {
            "${monthNames[monthIndex]} ${parts[0]}"
        } else yearMonth
    }

    private fun loadAccountsData() {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                val accountSummaries = repository.getAccountSummary()
                _accountsForManagement.value = accountSummaries.map { summary ->
                    val statementCount = repository.getStatementCountByAccount(summary.id)
                    AccountManagementItem(
                        id = summary.id,
                        name = summary.name,
                        bankName = summary.bank_name,
                        iban = summary.iban,
                        color = summary.color,
                        transactionCount = summary.transaction_count,
                        statementCount = statementCount,
                        balance = summary.balance
                    )
                }
            }
        }
    }

    // =====================================================================
    // ACCOUNT MANAGEMENT
    // =====================================================================

    fun deleteAccount(accountId: Long) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                repository.deleteAccount(accountId)
            }
            updateStats()
            loadAccountsData()
            loadTransactionData()
        }
    }

    fun editAccount(accountId: Long, newName: String) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                repository.updateAccountName(accountId, newName)
            }
            loadAccountsData()
        }
    }

    fun clearAllData() {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                repository.clearAllData()
            }
            updateStats()
            loadAccountsData()
            loadTransactionData()
        }
    }

    // =====================================================================
    // CATEGORY MANAGEMENT
    // =====================================================================

    fun handleCategoryChange(transaction: TransactionDisplay, newCategory: TransactionCategory) {
        val matchKey = getTransactionMatchKey(transaction)

        var updatedCount = 0
        _financialState.update { state ->
            val updatedTx = state.transactions.map { tx ->
                val txMatchKey = getTransactionMatchKey(tx)
                if (txMatchKey == matchKey) {
                    updatedCount++
                    tx.copy(category = newCategory)
                } else {
                    tx
                }
            }
            recomputeFinancialState(state.copy(transactions = updatedTx))
        }

        val counterpartyLower = transaction.counterparty?.lowercase() ?: ""
        val descriptionLower = transaction.description.lowercase()
        val effectiveCounterparty = if (counterpartyLower.contains("paypal") || descriptionLower.contains("paypal")) {
            TransactionDisplay.extractDisplayName(transaction.counterparty, transaction.description)
        } else {
            transaction.counterparty
        }

        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                categoryOverrideManager.saveOverride(
                    description = transaction.description,
                    counterparty = effectiveCounterparty,
                    category = newCategory
                )
            }
        }

        Toast.makeText(
            context,
            if (updatedCount > 1) "$updatedCount transactions updated" else "Category updated",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun handleCustomCategoryChange(transaction: TransactionDisplay, customCategoryId: Long) {
        val customCategory = _appSettings.value.customCategories.find { it.id == customCategoryId } ?: return

        val matchKey = getTransactionMatchKey(transaction)

        var updatedCount = 0
        _financialState.update { state ->
            val updatedTx = state.transactions.map { tx ->
                val txMatchKey = getTransactionMatchKey(tx)
                if (txMatchKey == matchKey) {
                    updatedCount++
                    tx.copy(
                        category = TransactionCategory.OTHER,
                        customCategoryId = customCategoryId,
                        customCategoryName = customCategory.name,
                        customCategoryIcon = customCategory.icon,
                        customCategoryColor = customCategory.color
                    )
                } else {
                    tx
                }
            }
            recomputeFinancialState(state.copy(transactions = updatedTx))
        }

        val counterpartyLower = transaction.counterparty?.lowercase() ?: ""
        val descriptionLower = transaction.description.lowercase()
        val effectiveCounterparty = if (counterpartyLower.contains("paypal") || descriptionLower.contains("paypal")) {
            TransactionDisplay.extractDisplayName(transaction.counterparty, transaction.description)
        } else {
            transaction.counterparty
        }

        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                categoryOverrideManager.saveCustomCategoryOverride(
                    description = transaction.description,
                    counterparty = effectiveCounterparty,
                    customCategoryId = customCategoryId
                )
            }
        }

        Toast.makeText(
            context,
            if (updatedCount > 1) "$updatedCount transactions updated to '${customCategory.name}'" else "Category updated to '${customCategory.name}'",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun getTransactionMatchKey(transaction: TransactionDisplay): String {
        fun normalize(text: String): String {
            return text.lowercase()
                .replace(Regex("[^a-z0-9äöüß ]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        val counterparty = transaction.counterparty
        val description = transaction.description
        val counterpartyLower = counterparty?.lowercase() ?: ""
        val descriptionLower = description.lowercase()

        if (counterpartyLower.contains("paypal") || descriptionLower.contains("paypal")) {
            val displayName = TransactionDisplay.extractDisplayName(counterparty, description)
            return normalize(displayName)
        }

        return if (!counterparty.isNullOrBlank()) {
            normalize(counterparty)
        } else {
            normalize(description)
        }
    }

    /**
     * Recomputes derived financial fields (spending, income, expenses) from transactions.
     * Returns the updated state for use in atomic [MutableStateFlow.update] calls.
     */
    private fun recomputeFinancialState(state: FinancialUiState): FinancialUiState {
        val spendingByCategory = state.transactions
            .filter { it.amount < 0 }
            .groupBy { it.category }
            .mapValues { (_, txs) -> txs.sumOf { it.amount } }

        val totalExpensesAmount = spendingByCategory.values.sum()

        val computedSpending = spendingByCategory.map { (cat, total) ->
            CategorySpending(
                category = cat,
                totalAmount = total,
                transactionCount = state.transactions.count { it.category == cat && it.amount < 0 },
                percentage = if (totalExpensesAmount != 0.0) {
                    (total / totalExpensesAmount * 100).toFloat()
                } else 0f
            )
        }.sortedBy { it.totalAmount }

        return state.copy(
            categorySpending = computedSpending,
            totalExpenses = state.transactions.filter { it.amount < 0 }.sumOf { it.amount },
            totalIncome = state.transactions.filter { it.amount > 0 }.sumOf { it.amount }
        )
    }

    // =====================================================================
    // CUSTOM CATEGORIES
    // =====================================================================

    private fun loadCustomCategories() {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                val categories = repository.getAllCategories()
                val mapped = categories.map { category ->
                    CustomCategory(
                        id = category.id,
                        name = category.name,
                        icon = category.icon ?: "\uD83C\uDFF7\uFE0F",
                        color = category.color ?: "#808080",
                        parentId = category.parent_id
                    )
                }
                _appSettings.update { it.copy(customCategories = mapped) }
            }
        }
    }

    fun addCustomCategory(name: String, icon: String, color: String) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                repository.insertCategory(name, icon, color)
            }
            loadCustomCategories()
        }
    }

    fun editCustomCategory(id: Long, name: String, icon: String, color: String) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                repository.updateCategory(id, name, icon, color)
            }
            loadCustomCategories()
        }
    }

    fun deleteCustomCategory(id: Long) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                repository.deleteCategory(id)
            }
            loadCustomCategories()
        }
    }

    // =====================================================================
    // EXPORT / SHARE
    // =====================================================================

    fun shareTransactions(
        format: ExportFormat,
        transactions: List<TransactionDisplay>,
        accountName: String?
    ) {
        coroutineScope.launch {
            try {
                val timestamp = System.currentTimeMillis()
                val accountSuffix = accountName?.replace(" ", "_")?.take(20) ?: "all"
                val result = withContext(Dispatchers.IO) {
                    when (format) {
                        ExportFormat.CSV -> {
                            val csvContent = ExportManager.generateTransactionsCsv(transactions, accountName)
                            val fileName = "transactions_${accountSuffix}_$timestamp.csv"
                            fileExporter.saveCsv(csvContent, fileName)
                        }
                        ExportFormat.PDF -> {
                            val pdfContent = ExportManager.generateTransactionsPdfContent(
                                transactions = transactions,
                                accountName = accountName,
                                title = context.getString(R.string.export_transactions)
                            )
                            val fileName = "transactions_${accountSuffix}_$timestamp.pdf"
                            pdfGenerator.generatePdf(pdfContent, fileName)
                        }
                    }
                }

                if (result.success) {
                    val shareIntent = fileExporter.createShareIntent(result)
                    if (shareIntent != null) {
                        val chooser = Intent.createChooser(shareIntent, context.getString(R.string.share))
                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(chooser)
                    } else {
                        Toast.makeText(context, context.getString(R.string.export_error), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, result.errorMessage ?: context.getString(R.string.export_error), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, context.getString(R.string.export_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun shareSpending(format: ExportFormat, data: SpendingExportData) {
        coroutineScope.launch {
            try {
                val timestamp = System.currentTimeMillis()
                val result = withContext(Dispatchers.IO) {
                    when (format) {
                        ExportFormat.CSV -> {
                            val csvContent = ExportManager.generateSpendingCsv(data)
                            val fileName = "spending_overview_$timestamp.csv"
                            fileExporter.saveCsv(csvContent, fileName)
                        }
                        ExportFormat.PDF -> {
                            val pdfContent = ExportManager.generateSpendingPdfContent(
                                data = data,
                                title = context.getString(R.string.export_spending)
                            )
                            val fileName = "spending_overview_$timestamp.pdf"
                            pdfGenerator.generatePdf(pdfContent, fileName)
                        }
                    }
                }

                if (result.success) {
                    val shareIntent = fileExporter.createShareIntent(result)
                    if (shareIntent != null) {
                        val chooser = Intent.createChooser(shareIntent, context.getString(R.string.share))
                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(chooser)
                    } else {
                        Toast.makeText(context, context.getString(R.string.export_error), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, result.errorMessage ?: context.getString(R.string.export_error), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, context.getString(R.string.export_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // =====================================================================
    // THEME & TUTORIAL
    // =====================================================================

    fun setThemeMode(mode: ThemeMode) {
        _appSettings.update { it.copy(themeMode = mode) }
        themePreferences.setThemeMode(mode)
    }

    fun dismissTutorial() {
        _appSettings.update { it.copy(showTutorial = false) }
        appPreferences.setTutorialDismissed(true)
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        _appSettings.update { it.copy(biometricLockEnabled = enabled) }
        appPreferences.setBiometricLockEnabled(enabled)
    }

    fun isBiometricLockEnabled(): Boolean {
        return appPreferences.isBiometricLockEnabled()
    }

    fun getBiometricLockManager(): BiometricLockManager = biometricLockManager

    fun openEmailClient(email: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$email")
                putExtra(Intent.EXTRA_SUBJECT, "Bankwise Feedback")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
            val clip = android.content.ClipData.newPlainText("email", email)
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(context, "Email copied to clipboard: $email", Toast.LENGTH_SHORT).show()
        }
    }

    // =====================================================================
    // INITIALIZATION HELPERS
    // =====================================================================

    private fun loadKeywordDatabase(countryCode: String = "de") {
        try {
            val fileName = "files/keywords/keywords_${countryCode.lowercase()}.csv"
            val csvStream = try {
                context.assets.open(fileName)
            } catch (e: Exception) {
                if (countryCode != "de") {
                    android.util.Log.d("KeywordDB", "No keywords file for $countryCode, falling back to German")
                    try {
                        context.assets.open("files/keywords/keywords_de.csv")
                    } catch (e2: Exception) {
                        android.util.Log.d("KeywordDB", "No keyword files found")
                        return
                    }
                } else {
                    android.util.Log.d("KeywordDB", "No keyword files found")
                    return
                }
            }

            val csvContent = csvStream.bufferedReader().use { it.readText() }
            keywordDatabase.loadFromCsv(csvContent, countryCode)

            TransactionCategory.setKeywordDatabase(keywordDatabase)

            android.util.Log.d("KeywordDB", "Loaded ${keywordDatabase.getKeywordCount()} keywords for $countryCode")

        } catch (e: Exception) {
            android.util.Log.e("KeywordDB", "Error loading keyword database: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun loadMerchantDatabase() {
        coroutineScope.launch {
            try {
                val isLoaded = withContext(Dispatchers.IO) {
                    merchantDatabase.isLoaded()
                }

                if (isLoaded) {
                    val count = withContext(Dispatchers.IO) { merchantDatabase.getMerchantCount() }
                    android.util.Log.d("MerchantDB", "Merchant database already loaded with $count entries")
                    return@launch
                }

                val csvStream = try {
                    context.assets.open("files/merchants.csv")
                } catch (e: Exception) {
                    android.util.Log.d("MerchantDB", "No merchants.csv found in assets, using keyword matching only")
                    return@launch
                }

                android.util.Log.d("MerchantDB", "Loading merchant database from CSV...")
                val startTime = System.currentTimeMillis()

                withContext(Dispatchers.IO) {
                    val csvContent = csvStream.bufferedReader().use { it.readText() }
                    merchantDatabase.loadFromCsv(csvContent) { loaded, total ->
                        android.util.Log.d("MerchantDB", "Loaded $loaded / $total merchants")
                    }
                }

                val duration = System.currentTimeMillis() - startTime
                val count = withContext(Dispatchers.IO) { merchantDatabase.getMerchantCount() }
                android.util.Log.d("MerchantDB", "Merchant database loaded: $count entries in ${duration}ms")

                loadTransactionData()

            } catch (e: Exception) {
                android.util.Log.e("MerchantDB", "Error loading merchant database: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun determineAutoCategory(tx: com.banking.statement.db.Transactions): TransactionCategory {
        if (!tx.auto_category.isNullOrBlank()) {
            return TransactionCategory.entries.find { it.name == tx.auto_category }
                ?: TransactionCategory.OTHER
        }
        return TransactionCategory.categorize(tx.description, tx.counterparty_name).let { keywordCategory ->
            if (keywordCategory != TransactionCategory.OTHER) {
                keywordCategory
            } else if (tx.amount < 0) {
                merchantDatabase.findCategory(tx.description, tx.counterparty_name)
                    ?: TransactionCategory.OTHER
            } else {
                TransactionCategory.OTHER
            }
        }
    }

}
