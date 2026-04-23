package com.banking.statement.parser.banks

import com.banking.statement.parser.ParseResult

/**
 * Afriland First Bank (Cameroon)
 */
class AfrilandParser : InternationalBankParser() {
    override val bankName = "Afriland First Bank"
    override val currency = "XAF"
    override val dateFormat = DateFormat.DD_MM_YYYY
    override val amountFormat = AmountFormat.COMMA_DECIMAL

    private val identifiers = listOf("afriland", "afriland first bank", "first bank cameroon")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * UBA Bank (Pan-African)
 */
class UbaParser : InternationalBankParser() {
    override val bankName = "UBA Bank"
    override val currency = "NGN"
    override val dateFormat = DateFormat.DD_MM_YYYY
    override val amountFormat = AmountFormat.DOT_DECIMAL

    private val identifiers = listOf("united bank for africa", "uba ", "ubagroup")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * Jaha Bank (Generic African)
 */
class JahaParser : InternationalBankParser() {
    override val bankName = "Jaha"
    override val currency = "USD"
    override val dateFormat = DateFormat.DD_MM_YYYY
    override val amountFormat = AmountFormat.DOT_DECIMAL

    private val identifiers = listOf("jaha", "jaha bank", "jahabank")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * Nairobi Bank (Kenya)
 */
class NairobiBankParser : InternationalBankParser() {
    override val bankName = "Nairobi Bank"
    override val currency = "KES"
    override val dateFormat = DateFormat.DD_MM_YYYY
    override val amountFormat = AmountFormat.DOT_DECIMAL

    private val identifiers = listOf("nairobi bank", "nairobi")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) } && (lower.contains("kenya") || lower.contains("kes"))
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * KCB Bank (Kenya)
 */
class KcbParser : InternationalBankParser() {
    override val bankName = "KCB Bank"
    override val currency = "KES"
    override val dateFormat = DateFormat.DD_MM_YYYY
    override val amountFormat = AmountFormat.DOT_DECIMAL

    private val identifiers = listOf("kcb bank", "kenya commercial bank", "kcb ")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}
