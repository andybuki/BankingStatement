package com.banking.statement.parser.banks

import com.banking.statement.parser.ParseResult
import kotlinx.datetime.LocalDate

// Month name mappings for date parsing (English and German)
internal val revolutMonths = mapOf(
    // English
    "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
    "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    "january" to 1, "february" to 2, "march" to 3, "april" to 4, "june" to 6,
    "july" to 7, "august" to 8, "september" to 9, "october" to 10, "november" to 11, "december" to 12,
    // German
    "januar" to 1, "februar" to 2, "märz" to 3, "mär" to 3, "mai" to 5, "juni" to 6,
    "juli" to 7, "oktober" to 10, "dezember" to 12
)

/**
 * Parser for Revolut PDF statements.
 * Handles multiple currencies and Revolut-specific date/amount formats.
 *
 * Parsing strategies (see [RevolutFormatParsers.kt] and [RevolutParsingHelpers.kt]):
 * - English column format (Money out / Money in)
 * - German column format (Geldausgang / Geldeingang)
 * - Generic table, line, and block fallbacks
 */
class RevolutPdfParser : BankPdfParser {
    override val bankName = "Revolut"

    private val identifiers = listOf(
        "revolut",
        "revolt21",
        "revogb21",
        "revolut.com",
        "revolut ltd",
        "revolut payments"
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        try {
            val lines = pdfText.lines()
            val currency = revolutDetectCurrency(pdfText)
            val accountNumber = revolutExtractAccountNumber(pdfText)
            val statementPeriod = revolutExtractStatementPeriod(pdfText)

            var transactions = parseRevolutEnglishFormat(lines, currency)

            if (transactions.size < 3) {
                val germanTransactions = parseRevolutGermanFormat(lines, currency)
                if (germanTransactions.size > transactions.size) {
                    transactions = germanTransactions
                }
            }

            if (transactions.size < 3) {
                val tableTransactions = parseRevolutTableFormat(lines, currency)
                if (tableTransactions.size > transactions.size) {
                    transactions = tableTransactions
                }
            }

            if (transactions.size < 3) {
                val lineTransactions = parseRevolutLineFormat(lines, currency)
                if (lineTransactions.size > transactions.size) {
                    transactions = lineTransactions
                }
            }

            if (transactions.size < 3) {
                val blockTransactions = parseRevolutBlockFormat(lines, currency)
                if (blockTransactions.size > transactions.size) {
                    transactions = blockTransactions
                }
            }

            return if (transactions.isNotEmpty()) {
                ParseResult(
                    success = true,
                    bankName = bankName,
                    accountIban = accountNumber,
                    statementPeriod = statementPeriod,
                    transactions = transactions
                )
            } else {
                ParseResult(
                    success = false,
                    bankName = bankName,
                    errorMessage = "Could not extract transactions from Revolut PDF. The PDF format may not be supported. Try exporting as CSV from the Revolut app."
                )
            }
        } catch (e: Exception) {
            return ParseResult(
                success = false,
                bankName = bankName,
                errorMessage = "Error parsing Revolut PDF: ${e.message}"
            )
        }
    }
}

// DkbParser and SparkasseParser live in their own files.
