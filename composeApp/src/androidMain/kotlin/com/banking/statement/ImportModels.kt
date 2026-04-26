package com.banking.statement

import com.banking.statement.db.AccountMatchResult
import com.banking.statement.parser.ImportFileType
import com.banking.statement.parser.ParseResult
import com.banking.statement.parser.banks.DetectionConfidence
import com.banking.statement.ui.AccountOption
import com.banking.statement.ui.ImportErrorDetails
import com.banking.statement.db.ImportResult

/**
 * Helper class for destructuring category info
 */
data class Tuple5<A, B, C, D, E>(
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
    val matchResult: AccountMatchResult,
    /** Original PDF bytes, kept in-memory so we can backfill source_page after the user picks an account. */
    val pdfBytes: ByteArray? = null
)

/**
 * State for showing import dialogs
 */
data class ImportDialogState(
    val showAccountDialog: Boolean = false,
    val showSuccessDialog: Boolean = false,
    val showBankSelectionDialog: Boolean = false,
    val showBankNotFoundDialog: Boolean = false,
    val showErrorDialog: Boolean = false,
    val showRatingDialog: Boolean = false,
    val pendingImport: PendingImport? = null,
    val existingAccounts: List<AccountOption> = emptyList(),
    val importResult: ImportResult? = null,
    val detectedBanks: List<DetectedBankOption> = emptyList(),
    val pendingPdfData: PendingPdfData? = null,
    val errorDetails: ImportErrorDetails? = null
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

/**
 * Data class to hold PDF pre-processing result
 */
data class PdfPreProcessResult(
    val needsUserSelection: Boolean,
    val text: String?,
    val errorResult: ParseResult?,
    val detectedBanks: List<DetectedBankOption> = emptyList()
)
