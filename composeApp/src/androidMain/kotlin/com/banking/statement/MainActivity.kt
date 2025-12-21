@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.banking.statement

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.banking.statement.db.AccountMatchResult
import com.banking.statement.db.DatabaseDriverFactory
import com.banking.statement.db.ImportResult
import com.banking.statement.db.TransactionRepository
import com.banking.statement.parser.CsvParser
import com.banking.statement.parser.ExcelParser
import com.banking.statement.parser.ImportFileType
import com.banking.statement.parser.ParseResult
import com.banking.statement.categorization.MerchantDatabase
import com.banking.statement.categorization.TransactionCategory
import com.banking.statement.parser.banks.BankParserRegistry
import com.banking.statement.pdf.PdfProcessor
import com.banking.statement.export.ExportFormat
import com.banking.statement.export.ExportManager
import com.banking.statement.export.FileExporter
import com.banking.statement.export.PdfGenerator
import com.banking.statement.export.SpendingExportData
import com.banking.statement.ui.AccountManagementItem
import com.banking.statement.ui.AccountOption
import com.banking.statement.ui.CategorySpending
import com.banking.statement.ui.ImportChoice
import com.banking.statement.ui.MonthlySummary
import com.banking.statement.ui.TransactionDisplay
import com.banking.statement.ui.theme.ThemeMode
import com.banking.statement.ui.theme.ThemePreferences
import com.banking.statement.validation.BankStatementValidator
import android.widget.Toast
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.io.File

/**
 * Pending import waiting for user decision
 */
data class PendingImport(
    val parseResult: ParseResult,
    val fileName: String,
    val filePath: String?,
    val fileType: ImportFileType,
    val matchResult: AccountMatchResult
)

/**
 * State for showing import dialogs
 */
data class ImportDialogState(
    val showAccountDialog: Boolean = false,
    val showSuccessDialog: Boolean = false,
    val pendingImport: PendingImport? = null,
    val existingAccounts: List<AccountOption> = emptyList(),
    val importResult: ImportResult? = null
)

class MainActivity : ComponentActivity() {

    private var importState by mutableStateOf(ImportState())
    private var stats by mutableStateOf(DatabaseStats())
    private var transactions by mutableStateOf<List<TransactionDisplay>>(emptyList())
    private var categorySpending by mutableStateOf<List<CategorySpending>>(emptyList())
    private var monthlySummary by mutableStateOf<List<MonthlySummary>>(emptyList())
    private var totalIncome by mutableStateOf(0.0)
    private var totalExpenses by mutableStateOf(0.0)
    private var dialogState by mutableStateOf(ImportDialogState())
    private var accountsForManagement by mutableStateOf<List<AccountManagementItem>>(emptyList())
    private var currentThemeMode by mutableStateOf(ThemeMode.SYSTEM)

    private lateinit var repository: TransactionRepository
    private lateinit var fileExporter: FileExporter
    private lateinit var pdfGenerator: PdfGenerator
    private lateinit var themePreferences: ThemePreferences
    private lateinit var merchantDatabase: MerchantDatabase
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { processFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Initialize PDFBox
        PDFBoxResourceLoader.init(applicationContext)

        // Initialize database
        val driverFactory = DatabaseDriverFactory(applicationContext)
        repository = TransactionRepository(driverFactory)

        // Initialize merchant database for improved categorization
        merchantDatabase = MerchantDatabase(repository.database)

        // Load merchant data from CSV if not already loaded
        loadMerchantDatabase()

        // Initialize exporters
        fileExporter = FileExporter(applicationContext)
        pdfGenerator = PdfGenerator(applicationContext)

        // Initialize theme preferences
        themePreferences = ThemePreferences(applicationContext)
        currentThemeMode = themePreferences.getThemeMode()

        // Clean up old export files
        fileExporter.cleanupOldExports()

        // Load stats and data
        updateStats()
        loadTransactionData()
        loadAccountsData()

        setContent {
            App(
                onPickFile = { mimeTypes ->
                    // Accept all supported file types
                    filePickerLauncher.launch("*/*")
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
                onDeleteAccount = { accountId -> deleteAccount(accountId) },
                onEditAccount = { accountId, newName -> editAccount(accountId, newName) },
                onClearAllData = { clearAllData() },
                onShareTransactions = { format, txList, accountName ->
                    shareTransactions(format, txList, accountName)
                },
                onShareSpending = { format, data ->
                    shareSpending(format, data)
                },
                themeMode = currentThemeMode,
                onThemeModeChange = { mode ->
                    currentThemeMode = mode
                    themePreferences.setThemeMode(mode)
                }
            )
        }
    }

    private fun processFile(uri: Uri) {
        coroutineScope.launch {
            importState = ImportState(isProcessing = true)

            try {
                val fileName = getFileName(uri) ?: "document"
                val bytes = readFileBytes(uri) ?: throw Exception("Could not read file")
                val fileType = ImportFileType.fromFileName(fileName)
                    ?: detectFileType(bytes)
                    ?: throw Exception("Unsupported file format")

                val parseResult = withContext(Dispatchers.IO) {
                    when (fileType) {
                        ImportFileType.CSV -> parseCsv(bytes, fileName)
                        ImportFileType.EXCEL -> parseExcel(bytes, fileName)
                        ImportFileType.PDF -> parsePdf(bytes, fileName, uri)
                    }
                }

                if (parseResult.success && parseResult.transactions.isNotEmpty()) {
                    // Check if we need to show a dialog
                    val filePath = if (fileType == ImportFileType.PDF) {
                        savePdfToStorage(uri, fileName)
                    } else null

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
        // Check for matching accounts
        val matchResult = withContext(Dispatchers.IO) {
            repository.findMatchingAccount(
                bankName = parseResult.bankName,
                iban = parseResult.accountIban
            )
        }

        when (matchResult) {
            is AccountMatchResult.IbanMatch -> {
                // Auto-add to matching account
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

                // Show success dialog
                dialogState = dialogState.copy(
                    showSuccessDialog = true,
                    importResult = result.copy(isNewAccount = false)
                )

                updateStats()
            }

            is AccountMatchResult.BankMatch, AccountMatchResult.NoMatch -> {
                // Need user decision - show dialog
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

    private fun handleImportChoice(choice: ImportChoice) {
        val pending = dialogState.pendingImport ?: return

        coroutineScope.launch {
            when (choice) {
                is ImportChoice.CreateNew -> {
                    dialogState = dialogState.copy(showAccountDialog = false)
                    importState = ImportState(isProcessing = true)

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
                        transactionCount = result.transactionsImported
                    )

                    dialogState = ImportDialogState(
                        showSuccessDialog = true,
                        importResult = result
                    )

                    updateStats()
                }

                is ImportChoice.AddToExisting -> {
                    dialogState = dialogState.copy(showAccountDialog = false)
                    importState = ImportState(isProcessing = true)

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
                        transactionCount = result.transactionsImported
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
                        errorMessage = "Import cancelled"
                    )
                }
            }
        }
    }

    private fun parseCsv(bytes: ByteArray, fileName: String): ParseResult {
        val csvContent = bytes.toString(Charsets.UTF_8)
        return CsvParser().parse(csvContent, fileName)
    }

    private fun parseExcel(bytes: ByteArray, fileName: String): ParseResult {
        return ExcelParser().parse(bytes, fileName)
    }

    private fun parsePdf(bytes: ByteArray, fileName: String, uri: Uri): ParseResult {
        val pdfProcessor = PdfProcessor()

        // Check if it's a PDF
        if (!pdfProcessor.isPdfFile(bytes)) {
            return ParseResult(
                success = false,
                bankName = "Unknown",
                errorMessage = "File is not a valid PDF"
            )
        }

        // Extract text
        val text = pdfProcessor.extractText(bytes)
        if (text.isNullOrBlank()) {
            return ParseResult(
                success = false,
                bankName = "Unknown",
                errorMessage = "Could not extract text from PDF. It may be a scanned document."
            )
        }

        // Validate as bank statement first
        val validator = BankStatementValidator()
        val validationResult = validator.validate(text)

        if (!validationResult.isValid) {
            return ParseResult(
                success = false,
                bankName = "Unknown",
                errorMessage = "This does not appear to be a bank statement (Score: ${validationResult.score}/50 required)"
            )
        }

        // Try to find a bank-specific parser
        val bankParser = BankParserRegistry.findParser(text)
        if (bankParser != null) {
            val result = bankParser.parse(text, fileName)
            if (result.success && result.transactions.isNotEmpty()) {
                return result
            }
            // If bank parser found but no transactions, fall through to error
            if (result.errorMessage != null) {
                return result
            }
        }

        // Fallback: PDF validated but no parser available or parsing failed
        val detectedBank = detectBankFromText(text)
        return ParseResult(
            success = false,
            bankName = detectedBank,
            errorMessage = buildString {
                append("Bank statement recognized ($detectedBank) but could not extract transactions. ")
                if (bankParser != null) {
                    append("Parser found but format may differ from expected. ")
                } else {
                    append("No parser available for this bank. ")
                    append("Supported banks: ${BankParserRegistry.supportedBanks().joinToString(", ")}. ")
                }
                append("Try using CSV/Excel export from your bank.")
            }
        )
    }

    private fun detectBankFromText(text: String): String {
        val lowerText = text.lowercase()
        return when {
            lowerText.contains("ing-diba") || lowerText.contains("ing diba") -> "ING DiBa"
            lowerText.contains("deutsche bank") -> "Deutsche Bank"
            lowerText.contains("sparkasse") -> "Sparkasse"
            lowerText.contains("commerzbank") -> "Commerzbank"
            lowerText.contains("dkb") -> "DKB"
            lowerText.contains("n26") -> "N26"
            lowerText.contains("revolut") -> "Revolut"
            else -> "Unknown Bank"
        }
    }

    private fun detectFileType(bytes: ByteArray): ImportFileType? {
        // Check PDF magic bytes
        if (bytes.size >= 5 &&
            bytes[0] == 0x25.toByte() && // %
            bytes[1] == 0x50.toByte() && // P
            bytes[2] == 0x44.toByte() && // D
            bytes[3] == 0x46.toByte()) { // F
            return ImportFileType.PDF
        }

        // Check Excel XLSX magic bytes (ZIP file header with xlsx content)
        if (bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() && // P
            bytes[1] == 0x4B.toByte()) { // K
            return ImportFileType.EXCEL
        }

        // Check Excel XLS magic bytes
        if (bytes.size >= 8 &&
            bytes[0] == 0xD0.toByte() &&
            bytes[1] == 0xCF.toByte()) {
            return ImportFileType.EXCEL
        }

        // Try to detect CSV by checking for text content with common delimiters
        val sample = bytes.take(1000).toByteArray().toString(Charsets.UTF_8)
        if (sample.contains(",") || sample.contains(";") || sample.contains("\t")) {
            return ImportFileType.CSV
        }

        return null
    }

    private fun savePdfToStorage(uri: Uri, fileName: String): String? {
        return try {
            val pdfDir = File(filesDir, "pdfs")
            if (!pdfDir.exists()) pdfDir.mkdirs()

            val targetFile = File(pdfDir, "${System.currentTimeMillis()}_$fileName")
            contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            targetFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun readFileBytes(uri: Uri): ByteArray? {
        return try {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    name = it.getString(nameIndex)
                }
            }
        }
        return name
    }

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
            // Also reload transaction and account data
            loadTransactionData()
            loadAccountsData()
        }
    }

    private fun loadTransactionData() {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                val allTransactions = repository.getAllTransactions()

                // Get account names map for display
                val accountNames = repository.getAccountSummary().associate { it.id to it.name }

                // Convert DB transactions to display format with categorization
                transactions = allTransactions.map { tx ->
                    // First try merchant database, then fall back to keyword matching
                    val category = merchantDatabase.findCategory(
                        tx.description,
                        tx.counterparty_name
                    ) ?: TransactionCategory.categorize(
                        tx.description,
                        tx.counterparty_name
                    )
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
                        accountId = tx.account_id ?: 0L,
                        accountName = tx.account_id?.let { accountNames[it] } ?: ""
                    )
                }

                // Calculate category spending
                val spendingByCategory = transactions
                    .filter { it.amount < 0 } // Only expenses
                    .groupBy { it.category }
                    .mapValues { (_, txs) ->
                        txs.sumOf { it.amount }
                    }

                val totalExpensesAmount = spendingByCategory.values.sum()

                categorySpending = spendingByCategory.map { (category, total) ->
                    CategorySpending(
                        category = category,
                        totalAmount = total,
                        transactionCount = transactions.count { it.category == category && it.amount < 0 },
                        percentage = if (totalExpensesAmount != 0.0) {
                            ((total / totalExpensesAmount) * 100).toFloat()
                        } else 0f
                    )
                }.sortedBy { it.totalAmount }

                // Calculate totals
                totalExpenses = allTransactions.filter { it.amount < 0 }.sumOf { it.amount }
                totalIncome = allTransactions.filter { it.amount > 0 }.sumOf { it.amount }

                // Calculate monthly summary
                val monthlyData = allTransactions.groupBy { tx ->
                    val date = Instant.fromEpochSeconds(tx.booking_date)
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                        .date
                    "${date.year}-${date.monthNumber.toString().padStart(2, '0')}"
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

    private fun deleteAccount(accountId: Long) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                repository.deleteAccount(accountId)
            }
            updateStats()
            loadAccountsData()
            loadTransactionData()
        }
    }

    private fun editAccount(accountId: Long, newName: String) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                repository.updateAccountName(accountId, newName)
            }
            loadAccountsData()
        }
    }

    private fun clearAllData() {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                repository.clearAllData()
            }
            updateStats()
            loadAccountsData()
            loadTransactionData()
        }
    }

    private fun shareTransactions(
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
                                title = getString(R.string.export_transactions)
                            )
                            val fileName = "transactions_${accountSuffix}_$timestamp.pdf"
                            pdfGenerator.generatePdf(pdfContent, fileName)
                        }
                    }
                }

                if (result.success) {
                    val shareIntent = fileExporter.createShareIntent(result)
                    if (shareIntent != null) {
                        startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
                    } else {
                        Toast.makeText(this@MainActivity, getString(R.string.export_error), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@MainActivity, result.errorMessage ?: getString(R.string.export_error), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@MainActivity, getString(R.string.export_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareSpending(
        format: ExportFormat,
        data: SpendingExportData
    ) {
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
                                title = getString(R.string.export_spending)
                            )
                            val fileName = "spending_overview_$timestamp.pdf"
                            pdfGenerator.generatePdf(pdfContent, fileName)
                        }
                    }
                }

                if (result.success) {
                    val shareIntent = fileExporter.createShareIntent(result)
                    if (shareIntent != null) {
                        startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
                    } else {
                        Toast.makeText(this@MainActivity, getString(R.string.export_error), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@MainActivity, result.errorMessage ?: getString(R.string.export_error), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@MainActivity, getString(R.string.export_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Load merchant database from CSV resource if not already loaded.
     * This enables improved transaction categorization based on known merchant names.
     */
    private fun loadMerchantDatabase() {
        coroutineScope.launch {
            try {
                // Check if already loaded
                val isLoaded = withContext(Dispatchers.IO) {
                    merchantDatabase.isLoaded()
                }

                if (isLoaded) {
                    val count = withContext(Dispatchers.IO) { merchantDatabase.getMerchantCount() }
                    android.util.Log.d("MerchantDB", "Merchant database already loaded with $count entries")
                    return@launch
                }

                // Try to load from resources
                val csvStream = try {
                    assets.open("files/merchants.csv")
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

                // Re-categorize transactions now that merchants are loaded
                loadTransactionData()

            } catch (e: Exception) {
                android.util.Log.e("MerchantDB", "Error loading merchant database: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}
