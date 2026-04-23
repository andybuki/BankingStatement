package com.banking.statement

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.banking.statement.parser.CsvParser
import com.banking.statement.parser.ExcelParser
import com.banking.statement.parser.ImportFileType
import com.banking.statement.parser.ParseResult
import com.banking.statement.parser.banks.BankParserRegistry
import com.banking.statement.parser.banks.DetectionConfidence
import com.banking.statement.pdf.PdfProcessor
import com.banking.statement.validation.BankStatementValidator
import java.io.File

/**
 * Handles all file import operations: reading, type detection, parsing.
 * Extracted from MainActivity to keep file processing logic isolated.
 */
class FileImportProcessor(private val context: Context) {

    fun readFileBytes(uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getFileName(uri: Uri): String? {
        var name: String? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
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

    fun detectFileType(bytes: ByteArray): ImportFileType? {
        // Check PDF magic bytes
        if (bytes.size >= 5 &&
            bytes[0] == 0x25.toByte() && // %
            bytes[1] == 0x50.toByte() && // P
            bytes[2] == 0x44.toByte() && // D
            bytes[3] == 0x46.toByte()    // F
        ) {
            return ImportFileType.PDF
        }

        // Check Excel XLSX magic bytes (ZIP file header with xlsx content)
        if (bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() && // P
            bytes[1] == 0x4B.toByte()    // K
        ) {
            return ImportFileType.EXCEL
        }

        // Check Excel XLS magic bytes
        if (bytes.size >= 8 &&
            bytes[0] == 0xD0.toByte() &&
            bytes[1] == 0xCF.toByte()
        ) {
            return ImportFileType.EXCEL
        }

        // Try to detect CSV by checking for text content with common delimiters
        val sample = bytes.take(1000).toByteArray().toString(Charsets.UTF_8)
        if (sample.contains(",") || sample.contains(";") || sample.contains("\t")) {
            return ImportFileType.CSV
        }

        return null
    }

    fun parseCsv(bytes: ByteArray, fileName: String): ParseResult {
        val csvContent = bytes.toString(Charsets.UTF_8)
        return CsvParser().parse(csvContent, fileName)
    }

    fun parseExcel(bytes: ByteArray, fileName: String): ParseResult {
        return ExcelParser().parse(bytes, fileName)
    }

    /**
     * Pre-process PDF: validate and check if user selection is needed
     */
    fun preProcessPdf(bytes: ByteArray): PdfPreProcessResult {
        val pdfProcessor = PdfProcessor()

        // Check if it's a PDF
        if (!pdfProcessor.isPdfFile(bytes)) {
            return PdfPreProcessResult(
                needsUserSelection = false,
                text = null,
                errorResult = ParseResult(
                    success = false,
                    bankName = "Unknown",
                    errorMessage = context.getString(R.string.error_invalid_pdf)
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
                    errorMessage = context.getString(R.string.error_pdf_no_text)
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
                    errorMessage = context.getString(R.string.error_low_score, validationResult.score)
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

    fun parsePdf(
        bytes: ByteArray,
        fileName: String,
        preProcessResult: PdfPreProcessResult? = null
    ): ParseResult {
        val result = preProcessResult ?: preProcessPdf(bytes)

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

    fun parsePdfWithParser(text: String, fileName: String, bankName: String): ParseResult {
        val parser = BankParserRegistry.getParserByName(bankName)
        return if (parser != null) {
            parser.parse(text, fileName)
        } else {
            ParseResult(
                success = false,
                bankName = bankName,
                errorMessage = context.getString(R.string.error_parser_not_found, bankName)
            )
        }
    }

    fun savePdfToStorage(uri: Uri, fileName: String): String? {
        return try {
            val pdfDir = File(context.filesDir, "pdfs")
            if (!pdfDir.exists()) pdfDir.mkdirs()

            val targetFile = File(pdfDir, "${System.currentTimeMillis()}_$fileName")
            context.contentResolver.openInputStream(uri)?.use { input ->
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

    /**
     * Delete every PDF persisted by [savePdfToStorage]. Used when the user
     * turns off "PDF access" in settings to purge existing cached files.
     * Returns the number of files deleted.
     */
    fun purgeStoredPdfs(): Int {
        val pdfDir = File(context.filesDir, "pdfs")
        if (!pdfDir.exists()) return 0
        val files = pdfDir.listFiles() ?: return 0
        var deleted = 0
        files.forEach { if (it.delete()) deleted++ }
        return deleted
    }

    fun detectBankFromText(text: String): String {
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
}
