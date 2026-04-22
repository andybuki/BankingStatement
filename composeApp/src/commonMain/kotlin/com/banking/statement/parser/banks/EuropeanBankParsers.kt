package com.banking.statement.parser.banks

import com.banking.statement.parser.ParseResult

/**
 * ING Poland
 */
class IngPolandParser : InternationalBankParser() {
    override val bankName = "ING Poland"
    override val currency = "PLN"
    override val dateFormat = DateFormat.DD_MM_YYYY
    override val amountFormat = AmountFormat.COMMA_DECIMAL

    private val identifiers = listOf("ing bank śląski", "ing bank slaski", "ingbplpw", "ing.pl")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * ABN AMRO (Netherlands)
 */
class AbnAmroParser : InternationalBankParser() {
    override val bankName = "ABN AMRO"
    override val currency = "EUR"
    override val dateFormat = DateFormat.DD_MM_YYYY
    override val amountFormat = AmountFormat.COMMA_DECIMAL

    private val identifiers = listOf("abn amro", "abnamro", "abnanl2a")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * Fineco Bank (Italy)
 */
class FinecoParser : InternationalBankParser() {
    override val bankName = "Fineco Bank"
    override val currency = "EUR"
    override val dateFormat = DateFormat.DD_MM_YYYY
    override val amountFormat = AmountFormat.COMMA_DECIMAL

    private val identifiers = listOf("fineco", "finecobank", "fineco bank")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * Banco BPM (Italy)
 */
class BancoBpmParser : InternationalBankParser() {
    override val bankName = "Banco BPM"
    override val currency = "EUR"
    override val dateFormat = DateFormat.DD_MM_YYYY
    override val amountFormat = AmountFormat.COMMA_DECIMAL

    private val identifiers = listOf("banco bpm", "bpm", "banco popolare", "banca popolare")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * illimity Bank (Italy)
 */
class IllimityParser : InternationalBankParser() {
    override val bankName = "illimity Bank"
    override val currency = "EUR"
    override val dateFormat = DateFormat.DD_MM_YYYY
    override val amountFormat = AmountFormat.COMMA_DECIMAL

    private val identifiers = listOf("illimity", "illimitybank")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * UniCredit (Italy/Europe)
 */
class UniCreditParser : InternationalBankParser() {
    override val bankName = "UniCredit"
    override val currency = "EUR"
    override val dateFormat = DateFormat.DD_MM_YYYY
    override val amountFormat = AmountFormat.COMMA_DECIMAL

    private val identifiers = listOf("unicredit", "uni credit", "hypovereinsbank", "hvb")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * Société Générale (France)
 */
class SocieteGeneraleParser : InternationalBankParser() {
    override val bankName = "Société Générale"
    override val currency = "EUR"
    override val dateFormat = DateFormat.DD_MM_YYYY
    override val amountFormat = AmountFormat.COMMA_DECIMAL

    private val identifiers = listOf("société générale", "societe generale", "sg ", "sogefrpp")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * Nickel (France - mobile bank)
 */
class NickelParser : InternationalBankParser() {
    override val bankName = "Nickel"
    override val currency = "EUR"
    override val dateFormat = DateFormat.DD_MM_YYYY
    override val amountFormat = AmountFormat.COMMA_DECIMAL

    private val identifiers = listOf("nickel", "compte nickel", "nickel.eu")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}
