package com.banking.statement.parser.banks

import com.banking.statement.parser.ParseResult

/**
 * Bank TRN
 */
class BankTrnParser : InternationalBankParser() {
    override val bankName = "Bank TRN"
    override val currency = "USD"
    override val dateFormat = DateFormat.MM_DD_YYYY
    override val amountFormat = AmountFormat.DOT_DECIMAL

    private val identifiers = listOf("bank trn", "banktrn", "trn bank")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * Dave Bank (US - fintech)
 */
class DaveBankParser : InternationalBankParser() {
    override val bankName = "Dave"
    override val currency = "USD"
    override val dateFormat = DateFormat.MM_DD_YYYY
    override val amountFormat = AmountFormat.DOT_DECIMAL

    private val identifiers = listOf("dave ", "dave bank", "dave.com", "davebank")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * US Bank
 */
class UsBankParser : InternationalBankParser() {
    override val bankName = "US Bank"
    override val currency = "USD"
    override val dateFormat = DateFormat.MM_DD_YYYY
    override val amountFormat = AmountFormat.DOT_DECIMAL

    private val identifiers = listOf("u.s. bank", "us bank", "usbank", "usbancorp")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * TD Bank (USA/Canada)
 */
class TdBankParser : InternationalBankParser() {
    override val bankName = "TD Bank"
    override val currency = "USD"
    override val dateFormat = DateFormat.MM_DD_YYYY
    override val amountFormat = AmountFormat.DOT_DECIMAL

    private val identifiers = listOf("td bank", "td canada trust", "tdbank", "toronto-dominion")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * Walmart MoneyCard
 */
class WalmartParser : InternationalBankParser() {
    override val bankName = "Walmart MoneyCard"
    override val currency = "USD"
    override val dateFormat = DateFormat.MM_DD_YYYY
    override val amountFormat = AmountFormat.DOT_DECIMAL

    private val identifiers = listOf("walmart", "walmart moneycard", "walmartmoneycard", "green dot")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * ScotiaBank (Canada)
 */
class ScotiaBankParser : InternationalBankParser() {
    override val bankName = "Scotiabank"
    override val currency = "CAD"
    override val dateFormat = DateFormat.MM_DD_YYYY
    override val amountFormat = AmountFormat.DOT_DECIMAL

    private val identifiers = listOf("scotiabank", "bank of nova scotia", "scotia bank")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * Royal Bank of Canada (RBC)
 */
class RbcParser : InternationalBankParser() {
    override val bankName = "Royal Bank of Canada"
    override val currency = "CAD"
    override val dateFormat = DateFormat.MM_DD_YYYY
    override val amountFormat = AmountFormat.DOT_DECIMAL

    private val identifiers = listOf("royal bank of canada", "rbc ", "rbc bank", "rbcroyalbank")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}
