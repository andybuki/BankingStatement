package com.banking.statement.parser.banks

import com.banking.statement.parser.ParseResult

/**
 * BCP (Banco de Crédito del Perú)
 */
class BcpParser : InternationalBankParser() {
    override val bankName = "BCP Peru"
    override val currency = "PEN"
    override val dateFormat = DateFormat.DD_MM_YYYY
    override val amountFormat = AmountFormat.COMMA_DECIMAL

    private val identifiers = listOf("banco de crédito del perú", "banco de credito del peru", "bcp", "viabcp")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) } && (lower.contains("perú") || lower.contains("peru") || lower.contains("sol"))
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * Banco de Occidente (Colombia)
 */
class BancoOccidenteParser : InternationalBankParser() {
    override val bankName = "Banco de Occidente"
    override val currency = "COP"
    override val dateFormat = DateFormat.DD_MM_YYYY
    override val amountFormat = AmountFormat.COMMA_DECIMAL

    private val identifiers = listOf("banco de occidente", "occidente")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) } && (lower.contains("colombia") || lower.contains("bogotá") || lower.contains("bogota"))
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * Grupo Inversores (Argentina)
 */
class GrupoInversoresParser : InternationalBankParser() {
    override val bankName = "Grupo Inversores"
    override val currency = "ARS"
    override val dateFormat = DateFormat.DD_MM_YYYY
    override val amountFormat = AmountFormat.COMMA_DECIMAL

    private val identifiers = listOf("grupo inversores", "inversores", "invertir")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) } && lower.contains("argentin")
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * BBVA Francés (Argentina)
 */
class BbvaFrancesParser : InternationalBankParser() {
    override val bankName = "BBVA Francés"
    override val currency = "ARS"
    override val dateFormat = DateFormat.DD_MM_YYYY
    override val amountFormat = AmountFormat.COMMA_DECIMAL

    private val identifiers = listOf("bbva francés", "bbva frances", "banco francés", "banco frances")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}

/**
 * Banco Provincia (Argentina)
 */
class BancoProvinciaParser : InternationalBankParser() {
    override val bankName = "Banco Provincia"
    override val currency = "ARS"
    override val dateFormat = DateFormat.DD_MM_YYYY
    override val amountFormat = AmountFormat.COMMA_DECIMAL

    private val identifiers = listOf("banco provincia", "banco de la provincia", "bapro")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseInternationalStatement(pdfText, fileName)
    }
}
