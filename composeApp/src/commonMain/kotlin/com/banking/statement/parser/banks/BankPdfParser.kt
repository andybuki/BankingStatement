package com.banking.statement.parser.banks

import com.banking.statement.parser.ParseResult

/**
 * Interface for bank-specific PDF parsers
 */
interface BankPdfParser {
    /**
     * The name of the bank this parser handles
     */
    val bankName: String

    /**
     * Check if this parser can handle the given PDF text
     */
    fun canParse(pdfText: String): Boolean

    /**
     * Parse the PDF text and extract transactions
     */
    fun parse(pdfText: String, fileName: String): ParseResult
}

/**
 * Registry of all available bank parsers
 */
object BankParserRegistry {
    private val parsers = mutableListOf<BankPdfParser>()

    init {
        // Register all bank parsers - German banks
        register(IngDiBaParser())
        register(DeutscheBankParser())
        register(PostbankParser())
        register(CommerzbankParser())
        register(SparkasseParser())
        register(VolksbankParser())  // Also handles VR-Bank and Raiffeisenbank
        register(DkbParser())
        register(N26Parser())
        register(C24Parser())
        register(ConsorsbankParser())
        register(RevolutPdfParser())
    }

    fun register(parser: BankPdfParser) {
        parsers.add(parser)
    }

    /**
     * Find a parser that can handle the given PDF text
     */
    fun findParser(pdfText: String): BankPdfParser? {
        return parsers.find { it.canParse(pdfText) }
    }

    /**
     * Get all registered bank names
     */
    fun supportedBanks(): List<String> = parsers.map { it.bankName }
}
