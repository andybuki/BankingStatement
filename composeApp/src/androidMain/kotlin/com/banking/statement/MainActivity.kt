package com.banking.statement

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
import com.banking.statement.db.DatabaseDriverFactory
import com.banking.statement.db.TransactionRepository
import com.banking.statement.parser.CsvParser
import com.banking.statement.parser.ExcelParser
import com.banking.statement.parser.ImportFileType
import com.banking.statement.parser.ParseResult
import com.banking.statement.categorization.TransactionCategory
import com.banking.statement.categorization.TransactionCategorizer
import com.banking.statement.parser.banks.BankParserRegistry
import com.banking.statement.pdf.PdfProcessor
import com.banking.statement.ui.CategorySpending
import com.banking.statement.ui.MonthlySummary
import com.banking.statement.ui.TransactionDisplay
import com.banking.statement.validation.BankStatementValidator
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.io.File

class MainActivity : ComponentActivity() {

    private var importState by mutableStateOf(ImportState())
    private var stats by mutableStateOf(DatabaseStats())
    private var transactions by mutableStateOf<List<TransactionDisplay>>(emptyList())
    private var categorySpending by mutableStateOf<List<CategorySpending>>(emptyList())
    private var monthlySummary by mutableStateOf<List<MonthlySummary>>(emptyList())
    private var totalIncome by mutableStateOf(0.0)
    private var totalExpenses by mutableStateOf(0.0)

    private lateinit var repository: TransactionRepository
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

        // Load stats and data
        updateStats()
        loadTransactionData()

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
                totalExpenses = totalExpenses
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
                    // Save to database
                    withContext(Dispatchers.IO) {
                        val filePath = if (fileType == ImportFileType.PDF) {
                            savePdfToStorage(uri, fileName)
                        } else null

                        repository.saveImport(
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
                        transactionCount = parseResult.transactions.size
                    )

                    updateStats()
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
            stats = DatabaseStats(
                totalStatements = statementsCount,
                totalTransactions = transactionsCount
            )
            // Also reload transaction data
            loadTransactionData()
        }
    }

    private fun loadTransactionData() {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                val allTransactions = repository.getAllTransactions()
                val categorizer = TransactionCategorizer()

                // Convert DB transactions to display format with categorization
                transactions = allTransactions.map { tx ->
                    val category = TransactionCategory.categorize(
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
                        counterparty = tx.counterparty_name
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
}
