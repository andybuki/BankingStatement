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
import com.banking.statement.pdf.PdfProcessor
import com.banking.statement.validation.BankStatementValidator
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    private var importState by mutableStateOf(ImportState())
    private var stats by mutableStateOf(DatabaseStats())

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

        // Load stats
        updateStats()

        setContent {
            App(
                onPickFile = { mimeTypes ->
                    // Accept all supported file types
                    filePickerLauncher.launch("*/*")
                },
                importState = importState,
                stats = stats
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

        // Validate as bank statement
        val validator = BankStatementValidator()
        val validationResult = validator.validate(text)

        if (!validationResult.isValid) {
            return ParseResult(
                success = false,
                bankName = "Unknown",
                errorMessage = "This does not appear to be a bank statement (Score: ${validationResult.score}/50 required)"
            )
        }

        // For now, PDF parsing just validates - transaction extraction will be added later
        // TODO: Add PDF transaction extraction
        return ParseResult(
            success = true,
            bankName = detectBankFromText(text),
            transactions = emptyList(), // PDF transaction extraction not implemented yet
            statementPeriod = null,
            errorMessage = "PDF validated but transaction extraction not yet implemented. Use CSV/Excel for now."
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
        }
    }
}
