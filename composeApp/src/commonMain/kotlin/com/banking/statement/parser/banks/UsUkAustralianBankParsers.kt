package com.banking.statement.parser.banks

import com.banking.statement.parser.ParseResult

/**
 * Bankwest (Australia)
 */
class BankwestParser : InternationalBankParser() {
    override val bankName = "Bankwest"
    override val currency = "AUD"
    override val dateFormat = DateFormat.DD_MM_YYYY
    override val amountFormat = AmountFormat.DOT_DECIMAL

    private val identifiers = listOf("bankwest", "bank of western australia", "bkwaau")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * SunTrust Bank (US - now Truist)
 */
class SunTrustParser : InternationalBankParser() {
    override val bankName = "SunTrust"
    override val currency = "USD"
    override val dateFormat = DateFormat.MM_DD_YYYY
    override val amountFormat = AmountFormat.DOT_DECIMAL

    private val identifiers = listOf("suntrust", "sun trust", "truist", "sntrus")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * Bank of America (US)
 */
class BankOfAmericaParser : InternationalBankParser() {
    override val bankName = "Bank of America"
    override val currency = "USD"
    override val dateFormat = DateFormat.MM_DD_YYYY
    override val amountFormat = AmountFormat.DOT_DECIMAL

    private val identifiers = listOf("bank of america", "bofa", "bankofamerica", "bofaus")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * Virgin Money (UK)
 */
class VirginMoneyParser : InternationalBankParser() {
    override val bankName = "Virgin Money"
    override val currency = "GBP"
    override val dateFormat = DateFormat.DD_MMM_YYYY
    override val amountFormat = AmountFormat.DOT_DECIMAL

    private val identifiers = listOf("virgin money", "virginmoney", "virgin.money", "clydesdale", "yorkshire bank")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * Lloyds Bank (UK)
 */
class LloydsParser : InternationalBankParser() {
    override val bankName = "Lloyds Bank"
    override val currency = "GBP"
    override val dateFormat = DateFormat.DD_MMM_YYYY
    override val amountFormat = AmountFormat.DOT_DECIMAL

    private val identifiers = listOf("lloyds bank", "lloyds banking", "lloydsgb", "lloyds tsb")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * Westpac (Australia)
 */
class WestpacParser : InternationalBankParser() {
    override val bankName = "Westpac"
    override val currency = "AUD"
    override val dateFormat = DateFormat.DD_MM_YYYY
    override val amountFormat = AmountFormat.DOT_DECIMAL

    private val identifiers = listOf("westpac", "st.george", "bank of melbourne", "wpacau")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}
