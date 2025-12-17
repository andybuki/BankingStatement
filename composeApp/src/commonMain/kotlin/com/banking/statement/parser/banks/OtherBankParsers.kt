package com.banking.statement.parser.banks

import com.banking.statement.parser.ParseResult

/**
 * Parser for Revolut PDF statements
 * Note: Revolut CSV/Excel is preferred - this is a fallback
 */
class RevolutPdfParser : BankPdfParser {
    override val bankName = "Revolut"

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return lower.contains("revolut") &&
               (lower.contains("account statement") || lower.contains("transaction"))
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        // Revolut PDFs are complex - recommend CSV/Excel instead
        return ParseResult(
            success = false,
            bankName = bankName,
            errorMessage = "Revolut PDF parsing not fully implemented. Please use CSV or Excel export from Revolut app instead."
        )
    }
}

/**
 * Parser for DKB (Deutsche Kreditbank) statements
 */
class DkbParser : BankPdfParser {
    override val bankName = "DKB"

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return lower.contains("deutsche kreditbank") ||
               lower.contains("dkb") && lower.contains("kontoauszug")
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        // TODO: Implement DKB parsing
        return ParseResult(
            success = false,
            bankName = bankName,
            errorMessage = "DKB PDF parsing not yet implemented"
        )
    }
}

/**
 * Parser for Sparkasse statements
 */
class SparkasseParser : BankPdfParser {
    override val bankName = "Sparkasse"

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return lower.contains("sparkasse") && lower.contains("kontoauszug")
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        // TODO: Implement Sparkasse parsing
        return ParseResult(
            success = false,
            bankName = bankName,
            errorMessage = "Sparkasse PDF parsing not yet implemented"
        )
    }
}
