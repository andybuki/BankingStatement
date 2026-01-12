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
import com.banking.statement.categorization.CategoryOverrideManager
import com.banking.statement.categorization.CategoryOverrideResult
import com.banking.statement.categorization.CustomCategory
import com.banking.statement.categorization.KeywordDatabase
import com.banking.statement.categorization.MerchantDatabase
import com.banking.statement.categorization.TransactionCategory
import com.banking.statement.categorization.TransactionCategorizer
import com.banking.statement.parser.banks.BankParserRegistry
import com.banking.statement.parser.banks.DetectionConfidence
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
import com.banking.statement.ui.BankSelectionDialog
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
 * Helper class for destructuring category info
 */
private data class Tuple5<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)

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
    val showBankSelectionDialog: Boolean = false,
    val pendingImport: PendingImport? = null,
    val existingAccounts: List<AccountOption> = emptyList(),
    val importResult: ImportResult? = null,
    val detectedBanks: List<DetectedBankOption> = emptyList(),
    val pendingPdfData: PendingPdfData? = null
)

/**
 * Data for pending PDF that needs bank selection
 */
data class PendingPdfData(
    val bytes: ByteArray,
    val text: String,
    val fileName: String,
    val uri: android.net.Uri
)

/**
 * Bank option for user selection
 */
data class DetectedBankOption(
    val bankName: String,
    val confidence: String,
    val matchedIdentifiers: List<String>
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
    private var customCategories by mutableStateOf<List<CustomCategory>>(emptyList())

    private lateinit var repository: TransactionRepository
    private lateinit var fileExporter: FileExporter
    private lateinit var pdfGenerator: PdfGenerator
    private lateinit var themePreferences: ThemePreferences
    private lateinit var merchantDatabase: MerchantDatabase
    private lateinit var categoryOverrideManager: CategoryOverrideManager
    private lateinit var transactionCategorizer: TransactionCategorizer
    private lateinit var keywordDatabase: KeywordDatabase
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

        // Load merchant data from CSV if not already loaded
        loadMerchantDatabase()

        // Backfill categories for existing transactions (one-time migration)
        coroutineScope.launch(Dispatchers.IO) {
            val backfilledCount = repository.backfillAutoCategories()
            android.util.Log.d("Migration", "✅ Backfilled $backfilledCount transactions")

            // Fix miscategorized supermarket transactions (one-time migration)
            android.util.Log.d("Migration", "🔄 Fixing miscategorized supermarket transactions...")
            val fixedCount = repository.fixMiscategorizedSupermarkets()
            android.util.Log.d("Migration", "✅ Fixed $fixedCount miscategorized transactions")

            // Debug: Check if trends data is available
            val categoryData = repository.getCategorySpendingByMonth()
            android.util.Log.d("Migration", "📊 Category trend data: ${categoryData.size} entries")
            categoryData.take(5).forEach { row ->
                android.util.Log.d("Migration", "  - ${row.month}: ${row.auto_category} = ${row.total}")
            }

            val merchantData = repository.getMerchantSpendingByMonth()
            android.util.Log.d("Migration", "🏪 Merchant trend data: ${merchantData.size} entries")
            merchantData.take(5).forEach { row ->
                android.util.Log.d("Migration", "  - ${row.month}: ${row.counterparty_name} = ${row.total}")
            }
        }

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
        loadCustomCategories()

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
                },
                onCategoryChange = { transaction, newCategory ->
                    handleCategoryChange(transaction, newCategory)
                },
                customCategories = customCategories,
                onCustomCategoryChange = { transaction, categoryId ->
                    handleCustomCategoryChange(transaction, categoryId)
                },
                onAddCustomCategory = { name, icon, color ->
                    addCustomCategory(name, icon, color)
                },
                onEditCustomCategory = { id, name, icon, color ->
                    editCustomCategory(id, name, icon, color)
                },
                onDeleteCustomCategory = { id ->
                    deleteCustomCategory(id)
                }
            )

            // Bank selection dialog
            if (dialogState.showBankSelectionDialog) {
                BankSelectionDialog(
                    detectedBanks = dialogState.detectedBanks,
                    onBankSelected = { bankName -> handleBankSelection(bankName) },
                    onDismiss = { cancelBankSelection() }
                )
            }
        }
    }

    private fun processFile(uri: Uri) {
        coroutineScope.launch {
            importState = ImportState(
                isProcessing = true,
                progress = 0,
                progressMessage = getString(R.string.processing_reading)
            )

            try {
                // Step 1: Read file (10%)
                val fileName = getFileName(uri) ?: "document"
                importState = importState.copy(progress = 10, progressMessage = getString(R.string.processing_reading))

                val bytes = readFileBytes(uri) ?: throw Exception("Could not read file")
                importState = importState.copy(progress = 20, progressMessage = getString(R.string.processing_detecting))

                // Step 2: Detect file type (20%)
                val fileType = ImportFileType.fromFileName(fileName)
                    ?: detectFileType(bytes)
                    ?: throw Exception("Unsupported file format")
                importState = importState.copy(progress = 30, progressMessage = getString(R.string.processing_parsing))

                // Step 3: Parse file (30-70%)
                val parseResult = withContext(Dispatchers.IO) {
                    when (fileType) {
                        ImportFileType.CSV -> {
                            withContext(Dispatchers.Main) {
                                importState = importState.copy(progress = 40, progressMessage = getString(R.string.processing_csv))
                            }
                            parseCsv(bytes, fileName)
                        }
                        ImportFileType.EXCEL -> {
                            withContext(Dispatchers.Main) {
                                importState = importState.copy(progress = 40, progressMessage = getString(R.string.processing_excel))
                            }
                            parseExcel(bytes, fileName)
                        }
                        ImportFileType.PDF -> {
                            withContext(Dispatchers.Main) {
                                importState = importState.copy(progress = 40, progressMessage = getString(R.string.processing_pdf))
                            }

                            // Pre-process PDF to check if user selection is needed
                            val preProcessResult = preProcessPdf(bytes)

                            if (preProcessResult.needsUserSelection && preProcessResult.detectedBanks.isNotEmpty()) {
                                // Show bank selection dialog on main thread
                                withContext(Dispatchers.Main) {
                                    dialogState = dialogState.copy(
                                        showBankSelectionDialog = true,
                                        detectedBanks = preProcessResult.detectedBanks,
                                        pendingPdfData = PendingPdfData(bytes, preProcessResult.text ?: "", fileName, uri)
                                    )
                                    importState = ImportState(isProcessing = false)
                                }
                                // Return null to indicate waiting for user selection
                                null
                            } else {
                                parsePdf(bytes, fileName, uri, preProcessResult)
                            }
                        }
                    }
                }

                // If parseResult is null, we're waiting for user bank selection
                if (parseResult == null) {
                    return@launch
                }

                importState = importState.copy(progress = 70, progressMessage = getString(R.string.processing_categorizing))

                // Step 4: Process result (70-90%)
                if (parseResult.success && parseResult.transactions.isNotEmpty()) {
                    importState = importState.copy(progress = 80, progressMessage = getString(R.string.processing_saving))

                    // Check if we need to show a dialog
                    val filePath = if (fileType == ImportFileType.PDF) {
                        savePdfToStorage(uri, fileName)
                    } else null

                    importState = importState.copy(progress = 90, progressMessage = getString(R.string.processing_finalizing))
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
                    importState = ImportState(isProcessing = true, progress = 80, progressMessage = getString(R.string.processing_saving))

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
                    importState = ImportState(isProcessing = true, progress = 80, progressMessage = getString(R.string.processing_saving))

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

    /**
     * Data class to hold PDF pre-processing result
     */
    private data class PdfPreProcessResult(
        val needsUserSelection: Boolean,
        val text: String?,
        val errorResult: ParseResult?,
        val detectedBanks: List<DetectedBankOption> = emptyList()
    )

    /**
     * Pre-process PDF: validate and check if user selection is needed
     */
    private fun preProcessPdf(bytes: ByteArray): PdfPreProcessResult {
        val pdfProcessor = PdfProcessor()

        // Check if it's a PDF
        if (!pdfProcessor.isPdfFile(bytes)) {
            return PdfPreProcessResult(
                needsUserSelection = false,
                text = null,
                errorResult = ParseResult(
                    success = false,
                    bankName = "Unknown",
                    errorMessage = "File is not a valid PDF"
                )
            )
        }

        // Extract text
        val text = pdfProcessor.extractText(bytes)
        if (text.isNullOrBlank()) {
            return PdfPreProcessResult(
                needsUserSelection = false,
                text = null,
                errorResult = ParseResult(
                    success = false,
                    bankName = "Unknown",
                    errorMessage = "Could not extract text from PDF. It may be a scanned document."
                )
            )
        }

        // Validate as bank statement first
        val validator = BankStatementValidator()
        val validationResult = validator.validate(text)

        if (!validationResult.isValid) {
            return PdfPreProcessResult(
                needsUserSelection = false,
                text = text,
                errorResult = ParseResult(
                    success = false,
                    bankName = "Unknown",
                    errorMessage = "This does not appear to be a bank statement (Score: ${validationResult.score}/50 required)"
                )
            )
        }

        // Check if user selection is needed (multiple banks detected or low confidence)
        if (BankParserRegistry.needsUserSelection(text)) {
            val detectedBanks = BankParserRegistry.detectBanks(text)
            if (detectedBanks.isNotEmpty()) {
                val bankOptions = detectedBanks.map { result ->
                    DetectedBankOption(
                        bankName = result.parser.bankName,
                        confidence = when (result.confidence) {
                            DetectionConfidence.CERTAIN -> "Certain"
                            DetectionConfidence.HIGH -> "High"
                            DetectionConfidence.MEDIUM -> "Medium"
                            DetectionConfidence.LOW -> "Low"
                            DetectionConfidence.NONE -> "None"
                        },
                        matchedIdentifiers = result.matchedIdentifiers
                    )
                }
                return PdfPreProcessResult(
                    needsUserSelection = true,
                    text = text,
                    errorResult = null,
                    detectedBanks = bankOptions
                )
            }
        }

        // No user selection needed
        return PdfPreProcessResult(
            needsUserSelection = false,
            text = text,
            errorResult = null
        )
    }

    private fun parsePdf(bytes: ByteArray, fileName: String, uri: Uri, preProcessResult: PdfPreProcessResult? = null): ParseResult {
        // If we already have a pre-process result, use it
        val result = preProcessResult ?: preProcessPdf(bytes)

        // If there was an error during pre-processing, return it
        if (result.errorResult != null) {
            return result.errorResult
        }

        val text = result.text ?: return ParseResult(
            success = false,
            bankName = "Unknown",
            errorMessage = "Could not extract text from PDF"
        )

        // Try to find a bank-specific parser (high confidence)
        val bankParser = BankParserRegistry.findParser(text)
        if (bankParser != null) {
            val parseResult = bankParser.parse(text, fileName)
            if (parseResult.success && parseResult.transactions.isNotEmpty()) {
                return parseResult
            }
            // If bank parser found but no transactions, fall through to error
            if (parseResult.errorMessage != null) {
                return parseResult
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

    /**
     * Parse PDF with a specific bank parser (after user selection)
     */
    private fun parsePdfWithParser(text: String, fileName: String, bankName: String): ParseResult {
        val parser = BankParserRegistry.getParserByName(bankName)
        return if (parser != null) {
            parser.parse(text, fileName)
        } else {
            ParseResult(
                success = false,
                bankName = bankName,
                errorMessage = "Parser not found for $bankName"
            )
        }
    }

    /**
     * Handle bank selection from user
     */
    private fun handleBankSelection(bankName: String) {
        val pendingData = dialogState.pendingPdfData ?: return

        dialogState = dialogState.copy(
            showBankSelectionDialog = false,
            detectedBanks = emptyList(),
            pendingPdfData = null
        )
        importState = ImportState(isProcessing = true, progress = 50, progressMessage = getString(R.string.processing_parsing))

        coroutineScope.launch {
            val parseResult = withContext(Dispatchers.IO) {
                parsePdfWithParser(pendingData.text, pendingData.fileName, bankName)
            }

            importState = importState.copy(progress = 70, progressMessage = getString(R.string.processing_categorizing))

            if (parseResult.success && parseResult.transactions.isNotEmpty()) {
                importState = importState.copy(progress = 80, progressMessage = getString(R.string.processing_saving))

                val filePath = savePdfToStorage(pendingData.uri, pendingData.fileName)
                importState = importState.copy(progress = 90, progressMessage = getString(R.string.processing_finalizing))
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

    /**
     * Cancel bank selection
     */
    private fun cancelBankSelection() {
        dialogState = dialogState.copy(
            showBankSelectionDialog = false,
            detectedBanks = emptyList(),
            pendingPdfData = null
        )
        importState = ImportState(
            isProcessing = false,
            errorMessage = "Bank selection cancelled"
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

                // Load custom categories map for quick lookup
                val customCategoriesMap = repository.getAllCategories().associate { category ->
                    category.id to CustomCategory(
                        id = category.id,
                        name = category.name,
                        icon = category.icon ?: "🏷️",
                        color = category.color ?: "#808080",
                        parentId = category.parent_id
                    )
                }

                // Convert DB transactions to display format with categorization
                transactions = allTransactions.map { tx ->
                    // Priority: 1) User overrides (including custom), 2) Saved category, 3) Recalculate
                    val overrideResult = categoryOverrideManager.findOverrideWithCustom(tx.description, tx.counterparty_name)

                    // Determine category and custom category info
                    val (category, customCategoryId, customCategoryName, customCategoryIcon, customCategoryColor) = when (overrideResult) {
                        is CategoryOverrideResult.Custom -> {
                            val customCat = customCategoriesMap[overrideResult.categoryId]
                            if (customCat != null) {
                                // Custom category found
                                Tuple5(TransactionCategory.OTHER, customCat.id, customCat.name, customCat.icon, customCat.color)
                            } else {
                                // Custom category was deleted, fall back to auto-categorization
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

                categorySpending = spendingByCategory.map { (category, total) ->
                    CategorySpending(
                        category = category,
                        totalAmount = total,
                        transactionCount = transactions.count { it.category == category && it.amount < 0 },
                        percentage = if (totalExpensesAmount != 0.0) {
                            ((total / totalExpensesAmount) * 100).toFloat()
                        } else 0f,
                        trend = trends[category]
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

    private fun handleCategoryChange(transaction: TransactionDisplay, newCategory: TransactionCategory) {
        // Get matching key - use counterparty if available, otherwise first words of description
        val matchKey = getTransactionMatchKey(transaction)

        // Update UI immediately - change all transactions with same match key
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

        // Also update category spending for immediate visual feedback
        updateCategorySpending()

        // Save to database in background
        // For PayPal, use the extracted display name so each merchant gets its own category
        val counterpartyLower = transaction.counterparty?.lowercase() ?: ""
        val descriptionLower = transaction.description.lowercase()
        val effectiveCounterparty = if (counterpartyLower.contains("paypal") || descriptionLower.contains("paypal")) {
            // Use the smart display name (e.g., "PayPal · Wolt") for PayPal transactions
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
            applicationContext,
            if (updatedCount > 1) "$updatedCount transactions updated" else "Category updated",
            Toast.LENGTH_SHORT
        ).show()
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

    private fun updateCategorySpending() {
        // Recalculate category spending from current transactions
        val spendingByCategory = transactions
            .filter { it.amount < 0 }
            .groupBy { it.category }
            .mapValues { (_, txs) -> txs.sumOf { it.amount } }

        val totalExpensesAmount = spendingByCategory.values.sum()

        categorySpending = spendingByCategory.map { (category, total) ->
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
        totalExpenses = transactions.filter { it.amount < 0 }.sumOf { it.amount }
        totalIncome = transactions.filter { it.amount > 0 }.sumOf { it.amount }
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
     * Load keyword database from CSV resource.
     * Uses country code from IBAN to load appropriate language file.
     */
    private fun loadKeywordDatabase(countryCode: String = "de") {
        try {
            // Try to load country-specific keywords first
            val fileName = "files/keywords/keywords_${countryCode.lowercase()}.csv"
            val csvStream = try {
                assets.open(fileName)
            } catch (e: Exception) {
                // Fall back to German if country-specific file not found
                if (countryCode != "de") {
                    android.util.Log.d("KeywordDB", "No keywords file for $countryCode, falling back to German")
                    try {
                        assets.open("files/keywords/keywords_de.csv")
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

            // Set the keyword database in TransactionCategory
            TransactionCategory.setKeywordDatabase(keywordDatabase)

            android.util.Log.d("KeywordDB", "Loaded ${keywordDatabase.getKeywordCount()} keywords for $countryCode")

        } catch (e: Exception) {
            android.util.Log.e("KeywordDB", "Error loading keyword database: ${e.message}")
            e.printStackTrace()
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

    /**
     * Determine automatic category for a transaction based on saved category or merchant/keyword matching
     */
    private fun determineAutoCategory(tx: com.banking.statement.db.Transactions): TransactionCategory {
        // Use saved category if available
        if (!tx.auto_category.isNullOrBlank()) {
            return TransactionCategory.entries.find { it.name == tx.auto_category }
                ?: TransactionCategory.OTHER
        }
        // Fall back to recalculation for old data without saved categories
        return TransactionCategory.categorize(tx.description, tx.counterparty_name).let { keywordCategory ->
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

    /**
     * Load custom categories from database
     */
    private fun loadCustomCategories() {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                val categories = repository.getAllCategories()
                customCategories = categories.map { category ->
                    CustomCategory(
                        id = category.id,
                        name = category.name,
                        icon = category.icon ?: "🏷️",
                        color = category.color ?: "#808080",
                        parentId = category.parent_id
                    )
                }
            }
        }
    }

    /**
     * Add a new custom category
     */
    private fun addCustomCategory(name: String, icon: String, color: String) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                repository.insertCategory(name, icon, color)
            }
            loadCustomCategories()
        }
    }

    /**
     * Edit an existing custom category
     */
    private fun editCustomCategory(id: Long, name: String, icon: String, color: String) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                repository.updateCategory(id, name, icon, color)
            }
            loadCustomCategories()
        }
    }

    /**
     * Delete a custom category
     */
    private fun deleteCustomCategory(id: Long) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                repository.deleteCategory(id)
            }
            loadCustomCategories()
        }
    }

    /**
     * Handle changing a transaction to a custom category
     */
    private fun handleCustomCategoryChange(transaction: TransactionDisplay, customCategoryId: Long) {
        // Find the custom category
        val customCategory = customCategories.find { it.id == customCategoryId } ?: return

        // Get matching key - use counterparty if available, otherwise first words of description
        val matchKey = getTransactionMatchKey(transaction)

        // Update UI immediately - change all transactions with same match key
        var updatedCount = 0
        transactions = transactions.map { tx ->
            val txMatchKey = getTransactionMatchKey(tx)
            if (txMatchKey == matchKey) {
                updatedCount++
                tx.copy(
                    category = TransactionCategory.OTHER, // Use OTHER as placeholder
                    customCategoryId = customCategoryId,
                    customCategoryName = customCategory.name,
                    customCategoryIcon = customCategory.icon,
                    customCategoryColor = customCategory.color
                )
            } else {
                tx
            }
        }

        // Also update category spending for immediate visual feedback
        updateCategorySpending()

        // For PayPal, use the extracted display name so each merchant gets its own category
        val counterpartyLower = transaction.counterparty?.lowercase() ?: ""
        val descriptionLower = transaction.description.lowercase()
        val effectiveCounterparty = if (counterpartyLower.contains("paypal") || descriptionLower.contains("paypal")) {
            TransactionDisplay.extractDisplayName(transaction.counterparty, transaction.description)
        } else {
            transaction.counterparty
        }

        // Save custom category override in background
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
            applicationContext,
            if (updatedCount > 1) "$updatedCount transactions updated to '${customCategory.name}'" else "Category updated to '${customCategory.name}'",
            Toast.LENGTH_SHORT
        ).show()
    }
}
