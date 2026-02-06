@file:OptIn(kotlin.time.ExperimentalTime::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package com.banking.statement

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.ComposeUIViewController
import com.banking.statement.categorization.CategoryOverrideManager
import com.banking.statement.categorization.KeywordDatabase
import com.banking.statement.categorization.MerchantDatabase
import com.banking.statement.categorization.TransactionCategory
import com.banking.statement.db.AccountMatchResult
import com.banking.statement.db.DatabaseDriverFactory
import com.banking.statement.db.ImportResult
import com.banking.statement.db.TransactionRepository
import com.banking.statement.parser.CsvParser
import com.banking.statement.parser.ImportFileType
import com.banking.statement.parser.ParseResult
import com.banking.statement.pdf.PdfProcessor
import com.banking.statement.parser.banks.BankParserRegistry
import com.banking.statement.parser.banks.DetectionConfidence
import com.banking.statement.ui.AccountManagementItem
import com.banking.statement.ui.AccountOption
import com.banking.statement.ui.CategorySpending
import com.banking.statement.ui.ImportChoice
import com.banking.statement.ui.MonthlySummary
import com.banking.statement.ui.TransactionDisplay
import com.banking.statement.ui.theme.ThemeMode
import com.banking.statement.ui.theme.ThemePreferences
import com.banking.statement.export.ExportFormat
import com.banking.statement.export.SpendingExportData
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIViewController
import platform.posix.memcpy

/**
 * Pending import waiting for user decision (iOS version)
 */
data class IosPendingImport(
    val parseResult: ParseResult,
    val fileName: String,
    val fileType: ImportFileType,
    val matchResult: AccountMatchResult
)

/**
 * State for showing import dialogs (iOS version)
 */
data class IosImportDialogState(
    val showAccountDialog: Boolean = false,
    val showSuccessDialog: Boolean = false,
    val pendingImport: IosPendingImport? = null,
    val existingAccounts: List<AccountOption> = emptyList(),
    val importResult: ImportResult? = null
)

/**
 * Convert NSData to ByteArray
 */
@kotlinx.cinterop.ExperimentalForeignApi
fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    if (length == 0) return ByteArray(0)

    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), this.bytes, this.length)
    }
    return bytes
}

fun MainViewController(): UIViewController = ComposeUIViewController {
    // Initialize state
    var importState by remember { mutableStateOf(ImportState()) }
    var stats by remember { mutableStateOf(DatabaseStats()) }
    var transactions by remember { mutableStateOf<List<TransactionDisplay>>(emptyList()) }
    var categorySpending by remember { mutableStateOf<List<CategorySpending>>(emptyList()) }
    var monthlySummary by remember { mutableStateOf<List<MonthlySummary>>(emptyList()) }
    var totalIncome by remember { mutableStateOf(0.0) }
    var totalExpenses by remember { mutableStateOf(0.0) }
    var accountsForManagement by remember { mutableStateOf<List<AccountManagementItem>>(emptyList()) }
    var dialogState by remember { mutableStateOf(IosImportDialogState()) }

    // Theme management
    val themePreferences = remember { ThemePreferences() }
    var currentThemeMode by remember { mutableStateOf(themePreferences.getThemeMode()) }

    // Initialize database driver
    val driverFactory = remember { DatabaseDriverFactory() }

    // Initialize keyword database for category matching
    val keywordDatabase = remember { KeywordDatabase() }

    // Initialize temporary repository to access database
    val tempRepository = remember { TransactionRepository(driverFactory) }

    // Initialize merchant database for improved categorization
    val merchantDatabase = remember { MerchantDatabase(tempRepository.database) }

    // Initialize category override manager for user corrections
    val categoryOverrideManager = remember {
        CategoryOverrideManager(tempRepository.database).apply {
            loadCache()
        }
    }

    // Initialize transaction categorizer
    val transactionCategorizer = remember {
        com.banking.statement.categorization.TransactionCategorizer(merchantDatabase, categoryOverrideManager)
    }

    // Initialize repository with categorizer for auto-categorization on import
    val repository = remember {
        TransactionRepository(driverFactory, transactionCategorizer)
    }

    // Initialize PDF processor
    val pdfProcessor = remember { PdfProcessor() }

    val coroutineScope = rememberCoroutineScope()

    // Helper function to update stats
    fun refreshStats() {
        coroutineScope.launch {
            updateStats(repository) { newStats ->
                stats = newStats
            }
        }
    }

    // Helper function to refresh all data
    fun refreshAllData() {
        coroutineScope.launch {
            updateStats(repository) { newStats -> stats = newStats }
            loadAccountsData(repository) { accounts -> accountsForManagement = accounts }
            loadTransactionData(
                repository = repository,
                categoryOverrideManager = categoryOverrideManager,
                merchantDatabase = merchantDatabase,
                onTransactionsLoaded = { txList, catSpending, monthSummary, income, expenses ->
                    transactions = txList
                    categorySpending = catSpending
                    monthlySummary = monthSummary
                    totalIncome = income
                    totalExpenses = expenses
                }
            )
        }
    }

    // Handle successful parse
    fun handleSuccessfulParse(
        parseResult: ParseResult,
        fileName: String,
        fileType: ImportFileType
    ) {
        coroutineScope.launch {
            // Check for matching accounts
            val matchResult = withContext(Dispatchers.Default) {
                repository.findMatchingAccount(
                    bankName = parseResult.bankName,
                    iban = parseResult.accountIban
                )
            }

            when (matchResult) {
                is AccountMatchResult.IbanMatch -> {
                    // Auto-add to matching account
                    val result = withContext(Dispatchers.Default) {
                        repository.saveImportToAccount(
                            accountId = matchResult.account.id,
                            parseResult = parseResult,
                            fileName = fileName,
                            filePath = null,
                            sourceType = fileType.name
                        )
                    }

                    importState = ImportState(
                        isProcessing = false,
                        parseResult = parseResult,
                        savedToDatabase = true,
                        transactionCount = result.transactionsImported
                    )

                    // Show success dialog
                    dialogState = dialogState.copy(
                        showSuccessDialog = true,
                        importResult = result.copy(isNewAccount = false)
                    )

                    refreshAllData()
                }

                is AccountMatchResult.BankMatch, AccountMatchResult.NoMatch -> {
                    // Need user decision - show dialog
                    val existingAccounts = withContext(Dispatchers.Default) {
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

                    dialogState = IosImportDialogState(
                        showAccountDialog = true,
                        pendingImport = IosPendingImport(
                            parseResult = parseResult,
                            fileName = fileName,
                            fileType = fileType,
                            matchResult = matchResult
                        ),
                        existingAccounts = existingAccounts
                    )
                }
            }
        }
    }

    // Process file data
    fun processFileData(data: ByteArray, fileName: String) {
        coroutineScope.launch {
            importState = ImportState(
                isProcessing = true,
                progress = 0,
                progressMessage = "Reading file..."
            )

            try {
                importState = importState.copy(progress = 10, progressMessage = "Reading file...")

                importState = importState.copy(progress = 20, progressMessage = "Detecting format...")

                // Detect file type
                val fileType = ImportFileType.fromFileName(fileName)
                    ?: detectFileType(data, pdfProcessor)
                    ?: throw Exception("Unsupported file format. Please use CSV or PDF files.")

                importState = importState.copy(progress = 30, progressMessage = "Parsing...")

                // Parse file
                val parseResult = withContext(Dispatchers.Default) {
                    when (fileType) {
                        ImportFileType.CSV -> {
                            withContext(Dispatchers.Main) {
                                importState = importState.copy(progress = 40, progressMessage = "Parsing CSV...")
                            }
                            parseCsv(data, fileName)
                        }
                        ImportFileType.PDF -> {
                            withContext(Dispatchers.Main) {
                                importState = importState.copy(progress = 40, progressMessage = "Extracting PDF text...")
                            }
                            parsePdf(data, fileName, pdfProcessor)
                        }
                        ImportFileType.EXCEL -> {
                            // Excel not yet supported on iOS
                            ParseResult(
                                success = false,
                                transactions = emptyList(),
                                bankName = "",
                                errorMessage = "Excel files are not yet supported on iOS. Please export as CSV."
                            )
                        }
                    }
                }

                importState = importState.copy(progress = 70, progressMessage = "Processing...")

                if (parseResult.success && parseResult.transactions.isNotEmpty()) {
                    importState = importState.copy(progress = 80, progressMessage = "Saving...")
                    handleSuccessfulParse(parseResult, fileName, fileType)
                } else {
                    importState = ImportState(
                        isProcessing = false,
                        parseResult = parseResult,
                        savedToDatabase = false,
                        errorMessage = parseResult.errorMessage ?: "No transactions found in file"
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

    // Handle import choice from dialog
    fun handleImportChoice(choice: ImportChoice) {
        val pending = dialogState.pendingImport ?: return

        coroutineScope.launch {
            when (choice) {
                is ImportChoice.CreateNew -> {
                    dialogState = dialogState.copy(showAccountDialog = false)
                    importState = ImportState(isProcessing = true, progress = 80, progressMessage = "Saving...")

                    val result = withContext(Dispatchers.Default) {
                        repository.saveImportWithNewAccount(
                            accountName = choice.accountName,
                            parseResult = pending.parseResult,
                            fileName = pending.fileName,
                            filePath = null,
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

                    dialogState = IosImportDialogState(
                        showSuccessDialog = true,
                        importResult = result
                    )

                    refreshAllData()
                }

                is ImportChoice.AddToExisting -> {
                    dialogState = dialogState.copy(showAccountDialog = false)
                    importState = ImportState(isProcessing = true, progress = 80, progressMessage = "Saving...")

                    val result = withContext(Dispatchers.Default) {
                        repository.saveImportToAccount(
                            accountId = choice.accountId,
                            parseResult = pending.parseResult,
                            fileName = pending.fileName,
                            filePath = null,
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

                    dialogState = IosImportDialogState(
                        showSuccessDialog = true,
                        importResult = result
                    )

                    refreshAllData()
                }

                ImportChoice.Cancel -> {
                    dialogState = IosImportDialogState()
                    importState = ImportState(
                        isProcessing = false,
                        errorMessage = "Import cancelled"
                    )
                }
            }
        }
    }

    // Load data on first composition
    remember {
        coroutineScope.launch {
            // Backfill categories for existing transactions (one-time migration)
            withContext(Dispatchers.Default) {
                repository.backfillAutoCategories()
                // Fix miscategorized supermarket transactions (one-time migration)
                repository.fixMiscategorizedSupermarkets()
            }

            // Load stats
            updateStats(repository) { newStats ->
                stats = newStats
            }

            // Load transaction data
            loadTransactionData(
                repository = repository,
                categoryOverrideManager = categoryOverrideManager,
                merchantDatabase = merchantDatabase,
                onTransactionsLoaded = { txList, catSpending, monthSummary, income, expenses ->
                    transactions = txList
                    categorySpending = catSpending
                    monthlySummary = monthSummary
                    totalIncome = income
                    totalExpenses = expenses
                }
            )

            // Load accounts data
            loadAccountsData(repository) { accounts ->
                accountsForManagement = accounts
            }
        }
    }

    App(
        // File picker callback for iOS
        onPickFile = { _ ->
            triggerFilePicker { data, fileName ->
                if (data != null && fileName != null) {
                    processFileData(data, fileName)
                }
            }
        },
        importState = importState,
        stats = stats,
        transactions = transactions,
        categorySpending = categorySpending,
        monthlySummary = monthlySummary,
        totalIncome = totalIncome,
        totalExpenses = totalExpenses,
        dialogState = dialogState,
        onImportChoice = { choice -> handleImportChoice(choice) },
        onDismissSuccessDialog = {
            dialogState = dialogState.copy(showSuccessDialog = false, importResult = null)
        },
        accountsForManagement = accountsForManagement,
        onDeleteAccount = { accountId ->
            coroutineScope.launch {
                withContext(Dispatchers.Default) {
                    repository.deleteAccount(accountId)
                }
                refreshAllData()
            }
        },
        onEditAccount = { accountId, newName ->
            coroutineScope.launch {
                withContext(Dispatchers.Default) {
                    repository.updateAccountName(accountId, newName)
                }
                loadAccountsData(repository) { accounts -> accountsForManagement = accounts }
            }
        },
        onClearAllData = {
            coroutineScope.launch {
                withContext(Dispatchers.Default) {
                    repository.clearAllData()
                }
                refreshAllData()
            }
        },
        // Share callbacks for iOS
        onShareTransactions = { format, txList, accountName ->
            IosShareHelper.shareTransactions(format, txList, accountName)
        },
        onShareSpending = { format, data ->
            IosShareHelper.shareSpending(format, data)
        },
        themeMode = currentThemeMode,
        onThemeModeChange = { mode ->
            currentThemeMode = mode
            themePreferences.setThemeMode(mode)
        },
        onCategoryChange = { transaction, newCategory ->
            coroutineScope.launch {
                // Update UI immediately - change all transactions with same match key
                val matchKey = getTransactionMatchKey(transaction)
                transactions = transactions.map { tx ->
                    val txMatchKey = getTransactionMatchKey(tx)
                    if (txMatchKey == matchKey) {
                        tx.copy(category = newCategory)
                    } else {
                        tx
                    }
                }

                // Update category spending
                updateCategorySpending(transactions) { catSpending, income, expenses ->
                    categorySpending = catSpending
                    totalIncome = income
                    totalExpenses = expenses
                }

                // Save to database in background
                val counterpartyLower = transaction.counterparty?.lowercase() ?: ""
                val descriptionLower = transaction.description.lowercase()
                val effectiveCounterparty = if (counterpartyLower.contains("paypal") || descriptionLower.contains("paypal")) {
                    TransactionDisplay.extractDisplayName(transaction.counterparty, transaction.description)
                } else {
                    transaction.counterparty
                }

                withContext(Dispatchers.Default) {
                    categoryOverrideManager.saveOverride(
                        description = transaction.description,
                        counterparty = effectiveCounterparty,
                        category = newCategory
                    )
                }
            }
        }
    )
}

/**
 * iOS Share helper - bridges to Swift implementation
 */
object IosShareHelper {
    fun shareTransactions(format: ExportFormat, transactions: List<TransactionDisplay>, accountName: String?) {
        val content = when (format) {
            ExportFormat.CSV -> generateCsvContent(transactions)
            ExportFormat.PDF -> generateTextContent(transactions) // Simplified for now
            ExportFormat.EXCEL -> generateCsvContent(transactions) // Fall back to CSV
        }

        val fileName = "transactions_${accountName ?: "all"}.${format.name.lowercase()}"
        shareContent(content, fileName)
    }

    fun shareSpending(format: ExportFormat, data: SpendingExportData) {
        val content = generateSpendingContent(data)
        val fileName = "spending_report.${format.name.lowercase()}"
        shareContent(content, fileName)
    }

    private fun generateCsvContent(transactions: List<TransactionDisplay>): String {
        val sb = StringBuilder()
        sb.appendLine("Date,Description,Amount,Currency,Category,Counterparty")
        transactions.forEach { tx ->
            sb.appendLine("${tx.date},\"${tx.description.replace("\"", "\"\"")}\",${tx.amount},${tx.currency},${tx.category.name},\"${tx.counterparty?.replace("\"", "\"\"") ?: ""}\"")
        }
        return sb.toString()
    }

    private fun generateTextContent(transactions: List<TransactionDisplay>): String {
        val sb = StringBuilder()
        sb.appendLine("Transaction Report")
        sb.appendLine("==================")
        sb.appendLine()
        transactions.forEach { tx ->
            sb.appendLine("${tx.date}: ${tx.description}")
            sb.appendLine("  Amount: ${tx.amount} ${tx.currency}")
            sb.appendLine("  Category: ${tx.category.name}")
            sb.appendLine()
        }
        return sb.toString()
    }

    private fun generateSpendingContent(data: SpendingExportData): String {
        val sb = StringBuilder()
        sb.appendLine("Spending Report")
        sb.appendLine("===============")
        sb.appendLine()
        sb.appendLine("Total Income: ${data.totalIncome}")
        sb.appendLine("Total Expenses: ${data.totalExpenses}")
        sb.appendLine()
        sb.appendLine("By Category:")
        data.categorySpending.forEach { cat ->
            sb.appendLine("  ${cat.category.name}: ${cat.totalAmount}")
        }
        return sb.toString()
    }

    private fun shareContent(content: String, fileName: String) {
        // Post notification to trigger Swift share sheet
        platform.Foundation.NSNotificationCenter.defaultCenter.postNotificationName(
            "ShareContent",
            `object` = mapOf("content" to content, "fileName" to fileName)
        )
    }
}

// Helper functions

private fun detectFileType(bytes: ByteArray, pdfProcessor: PdfProcessor): ImportFileType? {
    return when {
        pdfProcessor.isPdfFile(bytes) -> ImportFileType.PDF
        isLikelyCsv(bytes) -> ImportFileType.CSV
        else -> null
    }
}

private fun isLikelyCsv(bytes: ByteArray): Boolean {
    val text = bytes.decodeToString()
    val lines = text.split("\n").take(5)
    return lines.any { it.contains(",") || it.contains(";") || it.contains("\t") }
}

private fun parseCsv(bytes: ByteArray, fileName: String): ParseResult {
    val content = bytes.decodeToString()
    return CsvParser.parse(content, fileName)
}

private fun parsePdf(bytes: ByteArray, fileName: String, pdfProcessor: PdfProcessor): ParseResult {
    val text = pdfProcessor.extractText(bytes)
    if (text.isNullOrBlank()) {
        return ParseResult(
            success = false,
            transactions = emptyList(),
            bankName = "",
            errorMessage = "Could not extract text from PDF. The file may be image-based or protected."
        )
    }

    // Try to detect and parse with bank-specific parsers
    val detectedBanks = BankParserRegistry.detectBanks(text)

    if (detectedBanks.isEmpty()) {
        return ParseResult(
            success = false,
            transactions = emptyList(),
            bankName = "",
            errorMessage = "Could not detect bank format. This bank statement format may not be supported yet."
        )
    }

    // Use the highest confidence match
    val bestMatch = detectedBanks.maxByOrNull {
        when (it.confidence) {
            DetectionConfidence.HIGH -> 3
            DetectionConfidence.MEDIUM -> 2
            DetectionConfidence.LOW -> 1
        }
    } ?: return ParseResult(
        success = false,
        transactions = emptyList(),
        bankName = "",
        errorMessage = "Could not determine bank format."
    )

    val parser = BankParserRegistry.getParser(bestMatch.bankName)
    return parser?.parse(text) ?: ParseResult(
        success = false,
        transactions = emptyList(),
        bankName = bestMatch.bankName,
        errorMessage = "Parser not found for ${bestMatch.bankName}"
    )
}

private suspend fun updateStats(
    repository: TransactionRepository,
    onUpdate: (DatabaseStats) -> Unit
) {
    val statementsCount = withContext(Dispatchers.Default) {
        repository.getStatementCount().toInt()
    }
    val transactionsCount = withContext(Dispatchers.Default) {
        repository.getTransactionCount().toInt()
    }
    val accountsCount = withContext(Dispatchers.Default) {
        repository.getAccountCount().toInt()
    }
    onUpdate(DatabaseStats(
        totalStatements = statementsCount,
        totalTransactions = transactionsCount,
        totalAccounts = accountsCount
    ))
}

private suspend fun loadTransactionData(
    repository: TransactionRepository,
    categoryOverrideManager: CategoryOverrideManager,
    merchantDatabase: MerchantDatabase,
    onTransactionsLoaded: (
        List<TransactionDisplay>,
        List<CategorySpending>,
        List<MonthlySummary>,
        Double,
        Double
    ) -> Unit
) {
    withContext(Dispatchers.Default) {
        val allTransactions = repository.getAllTransactions()

        // Get account names map for display
        val accountNames = repository.getAccountSummary().associate { it.id to it.name }

        // Convert DB transactions to display format with categorization
        val txList = allTransactions.map { tx ->
            // Priority: 1) User overrides, 2) Saved category, 3) Recalculate
            val category = categoryOverrideManager.findOverride(tx.description, tx.counterparty_name)
                ?: run {
                    // Use saved category if available
                    if (!tx.auto_category.isNullOrBlank()) {
                        TransactionCategory.entries.find { it.name == tx.auto_category }
                            ?: TransactionCategory.OTHER
                    } else {
                        // Fall back to recalculation for old data without saved categories
                        TransactionCategory.categorize(tx.description, tx.counterparty_name).let { keywordCategory ->
                            if (keywordCategory != TransactionCategory.OTHER) {
                                keywordCategory
                            } else if (tx.amount < 0) {
                                // Only use merchant DB for expenses
                                merchantDatabase.findCategory(tx.description, tx.counterparty_name)
                                    ?: TransactionCategory.OTHER
                            } else {
                                TransactionCategory.OTHER
                            }
                        }
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
                accountName = tx.account_id?.let { accountNames[it] } ?: ""
            )
        }

        // Calculate category spending with trends
        val spendingByCategory = txList
            .filter { it.amount < 0 } // Only expenses
            .groupBy { it.category }
            .mapValues { (_, txs) ->
                txs.sumOf { it.amount }
            }

        val totalExpensesAmount = spendingByCategory.values.sum()

        // Get monthly category spending for trend analysis
        val monthlyCategoryData = repository.getCategorySpendingByMonth().map { row ->
            Triple(row.month ?: "", row.auto_category ?: "", row.total ?: 0.0)
        }
        val trends = com.banking.statement.ui.TrendCalculator.calculateTrends(monthlyCategoryData)

        val catSpending = spendingByCategory.map { (category, total) ->
            CategorySpending(
                category = category,
                totalAmount = total,
                transactionCount = txList.count { it.category == category && it.amount < 0 },
                percentage = if (totalExpensesAmount != 0.0) {
                    ((total / totalExpensesAmount) * 100).toFloat()
                } else 0f,
                trend = trends[category]
            )
        }.sortedBy { it.totalAmount }

        // Calculate totals
        val expenses = allTransactions.filter { it.amount < 0 }.sumOf { it.amount }
        val income = allTransactions.filter { it.amount > 0 }.sumOf { it.amount }

        // Calculate monthly summary
        val monthlyData = allTransactions.groupBy { tx ->
            val date = Instant.fromEpochSeconds(tx.booking_date)
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date
            "${date.year}-${date.monthNumber.toString().padStart(2, '0')}"
        }

        val monthSummary = monthlyData.map { (month, txs) ->
            val monthIncome = txs.filter { it.amount > 0 }.sumOf { it.amount }
            val monthExpenses = txs.filter { it.amount < 0 }.sumOf { it.amount }
            MonthlySummary(
                month = formatMonth(month),
                income = monthIncome,
                expenses = monthExpenses
            )
        }.sortedByDescending { it.month }

        onTransactionsLoaded(txList, catSpending, monthSummary, income, expenses)
    }
}

private suspend fun loadAccountsData(
    repository: TransactionRepository,
    onAccountsLoaded: (List<AccountManagementItem>) -> Unit
) {
    withContext(Dispatchers.Default) {
        val accountSummaries = repository.getAccountSummary()
        val accounts = accountSummaries.map { summary ->
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
        onAccountsLoaded(accounts)
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

/**
 * Get a matching key for grouping identical transactions.
 * Uses smart extraction for PayPal to differentiate by merchant.
 */
private fun getTransactionMatchKey(transaction: TransactionDisplay): String {
    // Normalize function - remove special chars, lowercase, trim
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

    // PayPal special handling - use the merchant name as the key
    if (counterpartyLower.contains("paypal") || descriptionLower.contains("paypal")) {
        // Extract merchant from the display name (which already contains extracted merchant)
        val displayName = TransactionDisplay.extractDisplayName(counterparty, description)
        return normalize(displayName)
    }

    // Use counterparty if available, otherwise description
    return if (!counterparty.isNullOrBlank()) {
        normalize(counterparty)
    } else {
        normalize(description)
    }
}

private fun updateCategorySpending(
    transactions: List<TransactionDisplay>,
    onUpdate: (List<CategorySpending>, Double, Double) -> Unit
) {
    // Recalculate category spending from current transactions
    val spendingByCategory = transactions
        .filter { it.amount < 0 }
        .groupBy { it.category }
        .mapValues { (_, txs) -> txs.sumOf { it.amount } }

    val totalExpensesAmount = spendingByCategory.values.sum()

    val catSpending = spendingByCategory.map { (category, total) ->
        CategorySpending(
            category = category,
            totalAmount = total,
            transactionCount = transactions.count { it.category == category && it.amount < 0 },
            percentage = if (totalExpensesAmount != 0.0) {
                (total / totalExpensesAmount * 100).toFloat()
            } else 0f
        )
    }.sortedBy { it.totalAmount }

    // Update totals
    val expenses = transactions.filter { it.amount < 0 }.sumOf { it.amount }
    val income = transactions.filter { it.amount > 0 }.sumOf { it.amount }

    onUpdate(catSpending, income, expenses)
}
