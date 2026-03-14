package com.banking.statement

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.banking.statement.categorization.CategoryOverrideManager
import com.banking.statement.categorization.CategoryOverrideResult
import com.banking.statement.categorization.CustomCategory
import com.banking.statement.categorization.KeywordDatabase
import com.banking.statement.categorization.MerchantDatabase
import com.banking.statement.categorization.TransactionCategory
import com.banking.statement.categorization.TransactionCategorizer
import com.banking.statement.db.AccountMatchResult
import com.banking.statement.db.DatabaseDriverFactory
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Main ViewModel holding all app state and business logic.
 * Extracted from the monolithic MainActivity.
 */
class MainViewModel(private val context: Context) : ViewModel() {

    // --- Observable State ---
    var importState by mutableStateOf(ImportState())
        private set
    var stats by mutableStateOf(DatabaseStats())
        private set
    var transactions by mutableStateOf<List<TransactionDisplay>>(emptyList())
        private set
    var categorySpending by mutableStateOf<List<CategorySpending>>(emptyList())
        private set
    var monthlySummary by mutableStateOf<List<MonthlySummary>>(emptyList())
        private set
    var totalIncome by mutableStateOf(0.0)
        private set
    var totalExpenses by mutableStateOf(0.0)
        private set
    var dialogState by mutableStateOf(ImportDialogState())
        private set
    var accountsForManagement by mutableStateOf<List<AccountManagementItem>>(emptyList())
        private set
    var currentThemeMode by mutableStateOf(ThemeMode.SYSTEM)
        private set
    var customCategories by mutableStateOf<List<CustomCategory>>(emptyList())
        private set
    var showTutorial by mutableStateOf(false)
        private set

    // --- Dependencies ---
    val repository: TransactionRepository
    private val appPreferences: AppPreferences
    private val fileExporter: FileExporter
    private val pdfGenerator: PdfGenerator
    private val themePreferences: ThemePreferences
    private val merchantDatabase: MerchantDatabase
    private val categoryOverrideManager: CategoryOverrideManager
    private val transactionCategorizer: TransactionCategorizer
    private val keywordDatabase: KeywordDatabase
    val fileImportProcessor: FileImportProcessor
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    init {
        // Initialize database
        val driverFactory = DatabaseDriverFactory(context)

        // Initialize keyword database first
        keywordDatabase = KeywordDatabase()
        loadKeywordDatabase()

        // Initialize temporary repository to get database instance
        val tempRepository = TransactionRepository(driverFactory)

        // Initialize merchant database for improved categorization
        merchantDatabase = MerchantDatabase(tempRepository.database)

        // Initialize category override manager for user corrections
        categoryOverrideManager = CategoryOverrideManager(tempRepository.database)
        categoryOverrideManager.loadCache()

        // Initialize transaction categorizer with proper priority
        transactionCategorizer = TransactionCategorizer(merchantDatabase, categoryOverrideManager)

        // Initialize repository with categorizer for auto-categorization on import
        repository = TransactionRepository(driverFactory, transactionCategorizer)

        // Initialize file import processor
        fileImportProcessor = FileImportProcessor(context)

        // Load merchant data from CSV if not already loaded
        loadMerchantDatabase()

        // Backfill categories for existing transactions (one-time migration)
        coroutineScope.launch(Dispatchers.IO) {
            val backfilledCount = repository.backfillAutoCategories()
            android.util.Log.d("Migration", "Backfilled $backfilledCount transactions")

            val fixedCount = repository.fixMiscategorizedSupermarkets()
            android.util.Log.d("Migration", "Fixed $fixedCount miscategorized transactions")
        }

        // Initialize exporters
        fileExporter = FileExporter(context)
        pdfGenerator = PdfGenerator(context)

        // Initialize theme preferences
        themePreferences = ThemePreferences(context)
        currentThemeMode = themePreferences.getThemeMode()

        // Initialize app preferences and tutorial state
        appPreferences = AppPreferences(context)
        showTutorial = !appPreferences.isTutorialDismissed()

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
        coroutineScope.launch {
            importState = ImportState(
                isProcessing = true,
                progress = 0,
                progressMessage = context.getString(R.string.processing_reading)
            )

            try {
                val fileName = fileImportProcessor.getFileName(uri) ?: "document"
                importState = importState.copy(progress = 10, progressMessage = context.getString(R.string.processing_reading))

                val bytes = fileImportProcessor.readFileBytes(uri) ?: throw Exception("Could not read file")
                importState = importState.copy(progress = 20, progressMessage = context.getString(R.string.processing_detecting))

                val fileType = ImportFileType.fromFileName(fileName)
                    ?: fileImportProcessor.detectFileType(bytes)
                    ?: throw Exception("Unsupported file format")
                importState = importState.copy(progress = 30, progressMessage = context.getString(R.string.processing_parsing))

                val parseResult = withContext(Dispatchers.IO) {
                    when (fileType) {
                        ImportFileType.CSV -> {
                            withContext(Dispatchers.Main) {
                                importState = importState.copy(progress = 40, progressMessage = context.getString(R.string.processing_csv))
                            }
                            fileImportProcessor.parseCsv(bytes, fileName)
                        }
                        ImportFileType.EXCEL -> {
                            withContext(Dispatchers.Main) {
                                importState = importState.copy(progress = 40, progressMessage = context.getString(R.string.processing_excel))
                            }
                            fileImportProcessor.parseExcel(bytes, fileName)
                        }
                        ImportFileType.PDF -> {
                            withContext(Dispatchers.Main) {
                                importState = importState.copy(progress = 40, progressMessage = context.getString(R.string.processing_pdf))
                            }

                            val preProcessResult = fileImportProcessor.preProcessPdf(bytes)

                            if (preProcessResult.needsUserSelection && preProcessResult.detectedBanks.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    dialogState = dialogState.copy(
                                        showBankSelectionDialog = true,
                                        detectedBanks = preProcessResult.detectedBanks,
                                        pendingPdfData = PendingPdfData(bytes, preProcessResult.text ?: "", fileName, uri)
                                    )
                                    importState = ImportState(isProcessing = false)
                                }
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

                importState = importState.copy(progress = 70, progressMessage = context.getString(R.string.processing_categorizing))

                if (parseResult.success && parseResult.transactions.isNotEmpty()) {
                    importState = importState.copy(progress = 80, progressMessage = context.getString(R.string.processing_saving))

                    val filePath = if (fileType == ImportFileType.PDF) {
                        fileImportProcessor.savePdfToStorage(uri, fileName)
                    } else null

                    importState = importState.copy(progress = 90, progressMessage = context.getString(R.string.processing_finalizing))
                    handleSuccessfulParse(parseResult, fileName, filePath, fileType)
                } else {
                    importState = ImportState(
                        isProcessing = false,
                        parseResult = parseResult,
                        savedToDatabase = false,
                        errorMessage = parseResult.errorMessage
                    )
                }

            } catch (e: Exception) {
                importState = ImportState(
                    isProcessing = false,
                    errorMessage = "Error: ${e.message}"
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

                importState = ImportState(
                    isProcessing = false,
                    parseResult = parseResult,
                    savedToDatabase = true,
                    transactionCount = result.transactionsImported
                )

                dialogState = dialogState.copy(
                    showSuccessDialog = true,
                    importResult = result.copy(isNewAccount = false)
                )

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

                importState = ImportState(isProcessing = false)

                dialogState = ImportDialogState(
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
        val pending = dialogState.pendingImport ?: return

        coroutineScope.launch {
            when (choice) {
                is ImportChoice.CreateNew -> {
                    dialogState = dialogState.copy(showAccountDialog = false)
                    importState = ImportState(isProcessing = true, progress = 80, progressMessage = context.getString(R.string.processing_saving))

                    val result = withContext(Dispatchers.IO) {
                        repository.saveImportWithNewAccount(
                            accountName = choice.accountName,
                            parseResult = pending.parseResult,
                            fileName = pending.fileName,
                            filePath = pending.filePath,
                            sourceType = pending.fileType.name
                        )
                    }

                    importState = ImportState(
                        isProcessing = false,
                        parseResult = pending.parseResult,
                        savedToDatabase = true,
                        transactionCount = result.transactionsImported,
                        progress = 100
                    )

                    dialogState = ImportDialogState(
                        showSuccessDialog = true,
                        importResult = result
                    )

                    updateStats()
                }

                is ImportChoice.AddToExisting -> {
                    dialogState = dialogState.copy(showAccountDialog = false)
                    importState = ImportState(isProcessing = true, progress = 80, progressMessage = context.getString(R.string.processing_saving))

                    val result = withContext(Dispatchers.IO) {
                        repository.saveImportToAccount(
                            accountId = choice.accountId,
                            parseResult = pending.parseResult,
                            fileName = pending.fileName,
                            filePath = pending.filePath,
                            sourceType = pending.fileType.name
                        )
                    }

                    importState = ImportState(
                        isProcessing = false,
                        parseResult = pending.parseResult,
                        savedToDatabase = true,
                        transactionCount = result.transactionsImported,
                        progress = 100
                    )

                    dialogState = ImportDialogState(
                        showSuccessDialog = true,
                        importResult = result
                    )

                    updateStats()
                }

                ImportChoice.Cancel -> {
                    dialogState = ImportDialogState()
                    importState = ImportState(
                        isProcessing = false,
                        errorMessage = context.getString(R.string.import_cancelled)
                    )
                }
            }
        }
    }

    fun handleBankSelection(bankName: String) {
        val pendingData = dialogState.pendingPdfData ?: return

        dialogState = dialogState.copy(
            showBankSelectionDialog = false,
            detectedBanks = emptyList(),
            pendingPdfData = null
        )
        importState = ImportState(isProcessing = true, progress = 50, progressMessage = context.getString(R.string.processing_parsing))

        coroutineScope.launch {
            val parseResult = withContext(Dispatchers.IO) {
                fileImportProcessor.parsePdfWithParser(pendingData.text, pendingData.fileName, bankName)
            }

            importState = importState.copy(progress = 70, progressMessage = context.getString(R.string.processing_categorizing))

            if (parseResult.success && parseResult.transactions.isNotEmpty()) {
                importState = importState.copy(progress = 80, progressMessage = context.getString(R.string.processing_saving))

                val filePath = fileImportProcessor.savePdfToStorage(pendingData.uri, pendingData.fileName)
                importState = importState.copy(progress = 90, progressMessage = context.getString(R.string.processing_finalizing))
                handleSuccessfulParse(parseResult, pendingData.fileName, filePath, ImportFileType.PDF)
            } else {
                importState = ImportState(
                    isProcessing = false,
                    parseResult = parseResult,
                    savedToDatabase = false,
                    errorMessage = parseResult.errorMessage
                )
            }
        }
    }

    fun cancelBankSelection() {
        dialogState = dialogState.copy(
            showBankSelectionDialog = false,
            detectedBanks = emptyList(),
            pendingPdfData = null
        )
        importState = ImportState(
            isProcessing = false,
            errorMessage = context.getString(R.string.bank_selection_cancelled)
        )
    }

    fun dismissSuccessDialog() {
        dialogState = dialogState.copy(showSuccessDialog = false, importResult = null)
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
            stats = DatabaseStats(
                totalStatements = statementsCount,
                totalTransactions = transactionsCount,
                totalAccounts = accountsCount
            )
            loadTransactionData()
            loadAccountsData()
        }
    }

    private fun loadTransactionData() {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                val allTransactions = repository.getAllTransactions()

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

                transactions = allTransactions.map { tx ->
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

                // Calculate category spending with trends
                val spendingByCategory = transactions
                    .filter { it.amount < 0 }
                    .groupBy { it.category }
                    .mapValues { (_, txs) -> txs.sumOf { it.amount } }

                val totalExpensesAmount = spendingByCategory.values.sum()

                val monthlyCategoryData = repository.getCategorySpendingByMonth().map { row ->
                    Triple(row.month ?: "", row.auto_category ?: "", row.total ?: 0.0)
                }
                val trends = com.banking.statement.ui.TrendCalculator.calculateTrends(monthlyCategoryData)

                categorySpending = spendingByCategory.map { (cat, total) ->
                    CategorySpending(
                        category = cat,
                        totalAmount = total,
                        transactionCount = transactions.count { it.category == cat && it.amount < 0 },
                        percentage = if (totalExpensesAmount != 0.0) {
                            ((total / totalExpensesAmount) * 100).toFloat()
                        } else 0f,
                        trend = trends[cat]
                    )
                }.sortedBy { it.totalAmount }

                totalExpenses = allTransactions.filter { it.amount < 0 }.sumOf { it.amount }
                totalIncome = allTransactions.filter { it.amount > 0 }.sumOf { it.amount }

                val monthlyData = allTransactions.groupBy { tx ->
                    val d = Instant.fromEpochSeconds(tx.booking_date)
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                        .date
                    "${d.year}-${d.monthNumber.toString().padStart(2, '0')}"
                }

                monthlySummary = monthlyData.map { (month, txs) ->
                    val income = txs.filter { it.amount > 0 }.sumOf { it.amount }
                    val expenses = txs.filter { it.amount < 0 }.sumOf { it.amount }
                    MonthlySummary(
                        month = formatMonth(month),
                        income = income,
                        expenses = expenses
                    )
                }.sortedByDescending { it.month }
            }
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
                accountsForManagement = accountSummaries.map { summary ->
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
        transactions = transactions.map { tx ->
            val txMatchKey = getTransactionMatchKey(tx)
            if (txMatchKey == matchKey) {
                updatedCount++
                tx.copy(category = newCategory)
            } else {
                tx
            }
        }

        updateCategorySpending()

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
        val customCategory = customCategories.find { it.id == customCategoryId } ?: return

        val matchKey = getTransactionMatchKey(transaction)

        var updatedCount = 0
        transactions = transactions.map { tx ->
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

        updateCategorySpending()

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

    private fun updateCategorySpending() {
        val spendingByCategory = transactions
            .filter { it.amount < 0 }
            .groupBy { it.category }
            .mapValues { (_, txs) -> txs.sumOf { it.amount } }

        val totalExpensesAmount = spendingByCategory.values.sum()

        categorySpending = spendingByCategory.map { (cat, total) ->
            CategorySpending(
                category = cat,
                totalAmount = total,
                transactionCount = transactions.count { it.category == cat && it.amount < 0 },
                percentage = if (totalExpensesAmount != 0.0) {
                    (total / totalExpensesAmount * 100).toFloat()
                } else 0f
            )
        }.sortedBy { it.totalAmount }

        totalExpenses = transactions.filter { it.amount < 0 }.sumOf { it.amount }
        totalIncome = transactions.filter { it.amount > 0 }.sumOf { it.amount }
    }

    // =====================================================================
    // CUSTOM CATEGORIES
    // =====================================================================

    private fun loadCustomCategories() {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                val categories = repository.getAllCategories()
                customCategories = categories.map { category ->
                    CustomCategory(
                        id = category.id,
                        name = category.name,
                        icon = category.icon ?: "\uD83C\uDFF7\uFE0F",
                        color = category.color ?: "#808080",
                        parentId = category.parent_id
                    )
                }
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
        currentThemeMode = mode
        themePreferences.setThemeMode(mode)
    }

    fun dismissTutorial() {
        showTutorial = false
        appPreferences.setTutorialDismissed(true)
    }

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

    // =====================================================================
    // FACTORY
    // =====================================================================

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(context.applicationContext) as T
        }
    }
}
