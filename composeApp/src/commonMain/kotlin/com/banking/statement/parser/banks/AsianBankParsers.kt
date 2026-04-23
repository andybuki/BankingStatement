package com.banking.statement.parser.banks

import com.banking.statement.parser.ParseResult

/**
 * POSB Bank (Singapore - DBS subsidiary)
 */
class PosbParser : InternationalBankParser() {
    override val bankName = "POSB"
    override val currency = "SGD"
    override val dateFormat = DateFormat.DD_MM_YYYY
    override val amountFormat = AmountFormat.DOT_DECIMAL

    private val identifiers = listOf("posb", "posb bank", "posb savings", "dbs posb")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * HDFC Bank (India)
 */
class HdfcParser : InternationalBankParser() {
    override val bankName = "HDFC Bank"
    override val currency = "INR"
    override val dateFormat = DateFormat.DD_MM_YYYY
    override val amountFormat = AmountFormat.DOT_DECIMAL

    private val identifiers = listOf("hdfc bank", "hdfc", "hdfcbank")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * State Bank of India (India)
 */
class SbiParser : InternationalBankParser() {
    override val bankName = "State Bank of India"
    override val currency = "INR"
    override val dateFormat = DateFormat.DD_MM_YYYY
    override val amountFormat = AmountFormat.DOT_DECIMAL

    private val identifiers = listOf("state bank of india", "sbi ", "sbin", "onlinesbi")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * Canara Bank (India)
 */
class CanaraParser : InternationalBankParser() {
    override val bankName = "Canara Bank"
    override val currency = "INR"
    override val dateFormat = DateFormat.DD_MM_YYYY
    override val amountFormat = AmountFormat.DOT_DECIMAL

    private val identifiers = listOf("canara bank", "canarabank", "syndicatebank")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * Alfa Bank (Russia)
 */
class AlfaBankParser : InternationalBankParser() {
    override val bankName = "Alfa Bank"
    override val currency = "RUB"
    override val dateFormat = DateFormat.DD_MM_YYYY
    override val amountFormat = AmountFormat.COMMA_DECIMAL

    private val identifiers = listOf("alfa bank", "alfabank", "альфа-банк", "alfa-bank")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * MayBank (Malaysia)
 */
class MaybankParser : InternationalBankParser() {
    override val bankName = "Maybank"
    override val currency = "MYR"
    override val dateFormat = DateFormat.DD_MM_YYYY
    override val amountFormat = AmountFormat.DOT_DECIMAL

    private val identifiers = listOf("maybank", "malayan banking", "maybank2u")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * CIMB Bank (Malaysia/Southeast Asia)
 */
class CimbParser : InternationalBankParser() {
    override val bankName = "CIMB Bank"
    override val currency = "MYR"
    override val dateFormat = DateFormat.DD_MM_YYYY
    override val amountFormat = AmountFormat.DOT_DECIMAL

    private val identifiers = listOf("cimb", "cimb bank", "cimbclicks")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * NayaPay (Pakistan - fintech)
 */
class NayaPayParser : InternationalBankParser() {
    override val bankName = "NayaPay"
    override val currency = "PKR"
    override val dateFormat = DateFormat.DD_MM_YYYY
    override val amountFormat = AmountFormat.DOT_DECIMAL

    private val identifiers = listOf("nayapay", "naya pay")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * MCB - Muslim Commercial Bank (Pakistan)
 */
class McbParser : InternationalBankParser() {
    override val bankName = "MCB Bank"
    override val currency = "PKR"
    override val dateFormat = DateFormat.DD_MM_YYYY
    override val amountFormat = AmountFormat.DOT_DECIMAL

    private val identifiers = listOf("mcb bank", "muslim commercial bank", "mcb ", "mcbbank")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * AB Bank (Bangladesh)
 */
class AbBankParser : InternationalBankParser() {
    override val bankName = "AB Bank"
    override val currency = "BDT"
    override val dateFormat = DateFormat.DD_MM_YYYY
    override val amountFormat = AmountFormat.DOT_DECIMAL

    private val identifiers = listOf("ab bank", "arab bangladesh bank", "abbank")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * United Commercial Bank Limited (UCB - Bangladesh)
 */
class UcbParser : InternationalBankParser() {
    override val bankName = "UCB Bank"
    override val currency = "BDT"
    override val dateFormat = DateFormat.DD_MM_YYYY
    override val amountFormat = AmountFormat.DOT_DECIMAL

    private val identifiers = listOf("united commercial bank", "ucb bank", "ucbl", "ucb limited")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}
