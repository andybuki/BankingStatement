package com.banking.statement.parser.banks

import com.banking.statement.parser.ParseResult

/**
 * Metro Bank (UK)
 */
class MetroBankParser : InternationalBankParser() {
    override val bankName = "Metro Bank"
    override val currency = "GBP"
    override val dateFormat = DateFormat.DD_MMM_YYYY
    override val amountFormat = AmountFormat.DOT_DECIMAL

    private val identifiers = listOf("metro bank", "metrobank")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * Wise (TransferWise - UK fintech)
 */
class WiseParser : InternationalBankParser() {
    override val bankName = "Wise"
    override val currency = "EUR"
    override val dateFormat = DateFormat.DD_MM_YYYY
    override val amountFormat = AmountFormat.DOT_DECIMAL

    private val identifiers = listOf("wise ", "wise.com", "transferwise", "wise account")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * Bank of Ireland
 */
class BankOfIrelandParser : InternationalBankParser() {
    override val bankName = "Bank of Ireland"
    override val currency = "EUR"
    override val dateFormat = DateFormat.DD_MM_YYYY
    override val amountFormat = AmountFormat.DOT_DECIMAL

    private val identifiers = listOf("bank of ireland", "boi ", "bankofireland", "bofi")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * BancaMarch (Spain)
 */
class BancaMarchParser : InternationalBankParser() {
    override val bankName = "Banca March"
    override val currency = "EUR"
    override val dateFormat = DateFormat.DD_MM_YYYY
    override val amountFormat = AmountFormat.COMMA_DECIMAL

    private val identifiers = listOf("banca march", "bancamarch", "march.es")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * Banco CTT (Portugal)
 */
class BancoCttParser : InternationalBankParser() {
    override val bankName = "Banco CTT"
    override val currency = "EUR"
    override val dateFormat = DateFormat.DD_MM_YYYY
    override val amountFormat = AmountFormat.COMMA_DECIMAL

    private val identifiers = listOf("banco ctt", "bancoctt", "ctt banco")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}
