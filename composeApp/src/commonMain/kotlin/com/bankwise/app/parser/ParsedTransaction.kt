package com.bankwise.app.parser

import kotlinx.datetime.LocalDate

/**
 * Represents a parsed transaction from any source (CSV, Excel, PDF)
 */
data class ParsedTransaction(
    val transactionId: String? = null,
    val bookingDate: LocalDate,
    val valueDate: LocalDate? = null,
    val amount: Double,
    val currency: String = "EUR",
    val balance: Double? = null,
    val description: String,
    val counterpartyName: String? = null,
    val counterpartyIban: String? = null,
    val remittanceInfo: String? = null,
    val transactionType: String? = null,
    val bankTransactionCode: String? = null,
    val rawText: String? = null
)

/**
 * Result of parsing a statement file
 */
data class ParseResult(
    val success: Boolean,
    val bankName: String,
    val accountIban: String? = null,
    val statementPeriod: String? = null,
    val transactions: List<ParsedTransaction> = emptyList(),
    val errorMessage: String? = null
)

/**
 * Supported file types for import
 */
enum class ImportFileType(val mimeTypes: List<String>, val extensions: List<String>) {
    PDF(listOf("application/pdf"), listOf("pdf")),
    CSV(listOf("text/csv", "text/comma-separated-values", "application/csv"), listOf("csv")),
    EXCEL(listOf(
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    ), listOf("xls", "xlsx"));

    companion object {
        fun fromFileName(fileName: String): ImportFileType? {
            val extension = fileName.substringAfterLast('.', "").lowercase()
            return entries.find { it.extensions.contains(extension) }
        }

        fun fromMimeType(mimeType: String): ImportFileType? {
            return entries.find { it.mimeTypes.contains(mimeType.lowercase()) }
        }

        fun allMimeTypes(): List<String> = entries.flatMap { it.mimeTypes }
    }
}
