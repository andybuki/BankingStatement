package com.banking.statement.parser.banks

import com.banking.statement.parser.ParseResult

/**
 * Base class for German bank parsers.
 *
 * Common parsing logic lives in sibling files:
 * - [GermanBankingFields.kt]: EREF/KREF/MREF/CRED/SVWZ/IBAN/BIC extractors
 * - [GermanBankParsingUtils.kt]: date/amount parsing, header/balance detection,
 *   transaction-type and counterparty extraction, keyword lists
 * - [GermanBankFormatParsers.kt]: the five parse*Format strategies
 */
abstract class GermanBankParser : BankPdfParser {

    /**
     * Calculate confidence based on matched identifiers.
     * @param pdfText the PDF text to analyze
     * @param certainIdentifiers identifiers that give CERTAIN confidence (BIC codes, explicit bank name)
     * @param highIdentifiers identifiers that give HIGH confidence
     * @param mediumIdentifiers identifiers that give MEDIUM confidence (generic patterns)
     */
    protected fun calculateConfidence(
        pdfText: String,
        certainIdentifiers: List<String>,
        highIdentifiers: List<String> = emptyList(),
        mediumIdentifiers: List<String> = emptyList()
    ): Pair<DetectionConfidence, List<String>> {
        val lower = pdfText.lowercase()
        val matchedIdentifiers = mutableListOf<String>()

        for (id in certainIdentifiers) {
            if (lower.contains(id.lowercase())) {
                matchedIdentifiers.add(id)
            }
        }
        if (matchedIdentifiers.isNotEmpty()) {
            return Pair(DetectionConfidence.CERTAIN, matchedIdentifiers)
        }

        for (id in highIdentifiers) {
            if (lower.contains(id.lowercase())) {
                matchedIdentifiers.add(id)
            }
        }
        if (matchedIdentifiers.size >= 2) {
            return Pair(DetectionConfidence.HIGH, matchedIdentifiers)
        }
        if (matchedIdentifiers.size == 1) {
            val mediumMatches = mediumIdentifiers.filter { lower.contains(it.lowercase()) }
            if (mediumMatches.isNotEmpty()) {
                matchedIdentifiers.addAll(mediumMatches)
                return Pair(DetectionConfidence.HIGH, matchedIdentifiers)
            }
            return Pair(DetectionConfidence.MEDIUM, matchedIdentifiers)
        }

        for (id in mediumIdentifiers) {
            if (lower.contains(id.lowercase())) {
                matchedIdentifiers.add(id)
            }
        }
        if (matchedIdentifiers.size >= 2) {
            return Pair(DetectionConfidence.MEDIUM, matchedIdentifiers)
        }
        if (matchedIdentifiers.isNotEmpty()) {
            return Pair(DetectionConfidence.LOW, matchedIdentifiers)
        }

        return Pair(DetectionConfidence.NONE, emptyList())
    }

    /**
     * Generic German bank statement parser.
     * Tries multiple strategies and returns the one that yields the most transactions.
     */
    protected fun parseGermanStatement(
        pdfText: String,
        fileName: String,
        bankIdentifier: String
    ): ParseResult {
        try {
            val lines = pdfText.lines()
            val accountIban = extractIban(pdfText)
            val statementPeriod = extractStatementPeriod(pdfText)

            var transactions = parseComprehensiveFormat(lines)

            if (transactions.size < 3) {
                val multiLineTransactions = parseMultiLineFormat(lines)
                if (multiLineTransactions.size > transactions.size) {
                    transactions = multiLineTransactions
                }
            }

            if (transactions.size < 3) {
                val tableTransactions = parseTableFormat(lines)
                if (tableTransactions.size > transactions.size) {
                    transactions = tableTransactions
                }
            }

            if (transactions.size < 3) {
                val dateAmountTransactions = parseDateAmountLines(lines)
                if (dateAmountTransactions.size > transactions.size) {
                    transactions = dateAmountTransactions
                }
            }

            if (transactions.size < 3) {
                val blockTransactions = parseBlockFormat(lines)
                if (blockTransactions.size > transactions.size) {
                    transactions = blockTransactions
                }
            }

            return if (transactions.isNotEmpty()) {
                ParseResult(
                    success = true,
                    bankName = bankName,
                    accountIban = accountIban,
                    statementPeriod = statementPeriod,
                    transactions = transactions
                )
            } else {
                val sampleLines = lines.filter { it.isNotBlank() }.take(30).joinToString("\n")
                ParseResult(
                    success = false,
                    bankName = bankName,
                    errorMessage = "Could not extract transactions from $bankName PDF. Sample:\n$sampleLines"
                )
            }
        } catch (e: Exception) {
            return ParseResult(
                success = false,
                bankName = bankName,
                errorMessage = "Error parsing $bankName PDF: ${e.message}"
            )
        }
    }
}
