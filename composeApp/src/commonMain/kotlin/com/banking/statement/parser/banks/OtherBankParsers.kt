package com.banking.statement.parser.banks

import com.banking.statement.parser.ParseResult

/**
 * Parser for Revolut PDF statements
 */
class RevolutPdfParser : GermanBankParser() {
    override val bankName = "Revolut"

    private val identifiers = listOf(
        "revolut",
        "revolt21",
        "revogb21",
        "revolut.com"
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        // Try generic German parsing first
        val result = parseGermanStatement(pdfText, fileName, "Revolut")

        // If no transactions found, suggest CSV/Excel
        return if (result.success && result.transactions.isNotEmpty()) {
            result
        } else {
            ParseResult(
                success = false,
                bankName = bankName,
                errorMessage = "Could not parse Revolut PDF. Please use CSV or Excel export from Revolut app for better results."
            )
        }
    }
}

/**
 * Parser for DKB (Deutsche Kreditbank) statements
 */
class DkbParser : GermanBankParser() {
    override val bankName = "DKB"

    private val identifiers = listOf(
        "deutsche kreditbank",
        "dkb",
        "byladem1",
        "dkb.de"
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) } &&
               (lower.contains("kontoauszug") || lower.contains("kreditkarte") || lower.contains("girokonto"))
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "DKB")
    }
}

/**
 * Parser for Sparkasse statements
 */
class SparkasseParser : GermanBankParser() {
    override val bankName = "Sparkasse"

    // Sparkassen have many local BICs, but share common patterns
    private val identifiers = listOf(
        "sparkasse",
        "spk ",
        "landesbank",
        "lbbw",
        "helaba",
        "naspa"
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) } &&
               (lower.contains("kontoauszug") || lower.contains("girokonto"))
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "Sparkasse")
    }
}
