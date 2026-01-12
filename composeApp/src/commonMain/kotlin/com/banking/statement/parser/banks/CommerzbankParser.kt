package com.banking.statement.parser.banks

import com.banking.statement.parser.ParseResult
import com.banking.statement.parser.ParsedTransaction
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Parser for Commerzbank statements
 *
 * Format:
 * - Header: "Kontowährung Euro" with columns "zu Ihren Lasten" | "zu Ihren Gunsten"
 * - Balance: "Alter Kontostand vom DD.MM.YYYY" with amount
 * - Date headers: "Buchungsdatum: DD.MM.YYYY"
 * - Transactions: Multi-line description, last line has Valuta (DD.MM) and amount
 * - Amount format: 56,87- (trailing minus for debits) or 127,50 (positive for credits)
 */
class CommerzbankParser : GermanBankParser() {
    override val bankName = "Commerzbank"

    // CERTAIN: unique identifiers (BIC codes, official name)
    private val certainIdentifiers = listOf(
        "cobadeff",      // Commerzbank BIC Frankfurt
        "cobadehd",      // Commerzbank BIC variant
        "cobadehdxxx",   // Full BIC
        "cobadeffxxx",   // Full BIC
        "commerzbank ag" // Official legal name
    )

    // HIGH: strong indicators
    private val highIdentifiers = listOf(
        "commerzbank",
        "dresdner bank",  // Former name (merged with Commerzbank)
        "commerzbank.de"
    )

    // MEDIUM: patterns that appear in Commerzbank statements
    private val mediumIdentifiers = listOf(
        "zu ihren lasten",  // Debit column header
        "zu ihren gunsten", // Credit column header
        "buchungsdatum:",   // Date header specific to Commerzbank
        "alter kontostand vom" // Opening balance header
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return certainIdentifiers.any { lower.contains(it) } ||
               highIdentifiers.any { lower.contains(it) } ||
               (lower.contains("buchungsdatum:") &&
                (lower.contains("zu ihren lasten") || lower.contains("zu ihren gunsten")))
    }

    override fun getConfidence(pdfText: String): Pair<DetectionConfidence, List<String>> {
        return calculateConfidence(pdfText, certainIdentifiers, highIdentifiers, mediumIdentifiers)
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        try {
            val lines = pdfText.lines()
            val accountIban = extractIban(pdfText)
            val statementPeriod = extractStatementPeriod(pdfText)

            // Try Commerzbank specific format first
            var transactions = parseCommerzbankFormat(lines)

            // Fallback to generic parser if specific format didn't work well
            if (transactions.size < 3) {
                val genericTransactions = parseComprehensiveFormat(lines)
                if (genericTransactions.size > transactions.size) {
                    transactions = genericTransactions
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
                ParseResult(
                    success = false,
                    bankName = bankName,
                    errorMessage = "Could not extract transactions from Commerzbank PDF"
                )
            }
        } catch (e: Exception) {
            return ParseResult(
                success = false,
                bankName = bankName,
                errorMessage = "Error parsing Commerzbank PDF: ${e.message}"
            )
        }
    }

    /**
     * Parse Commerzbank specific format:
     *
     * Buchungsdatum: 10.02.2021
     * SEPA-Gutschrift
     * IBAN: DE12345678901234567890
     * BIC: COBADEFFXXX
     * Absender: Max Mustermann
     * End-to-End-Ref: ...
     * 10.02.           127,50
     *
     * SEPA-Überweisung
     * IBAN: DE09876543210987654321
     * ...
     * 10.02.           56,87-
     */
    private fun parseCommerzbankFormat(lines: List<String>): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()

        // Pattern for "Buchungsdatum: DD.MM.YYYY"
        val bookingDateHeaderPattern = Regex("""Buchungsdatum:\s*(\d{2}\.\d{2}\.\d{4})""", RegexOption.IGNORE_CASE)

        // Pattern for valuta date + amount line: "DD.MM.     amount" or "DD.MM     amount-"
        // Amount can have trailing minus for debits: "56,87-" or be positive: "127,50"
        val valutaAmountPattern = Regex(
            """^(\d{2}\.\d{2}\.?)\s+(\d{1,3}(?:[.]\d{3})*,\d{2})(-)?$"""
        )

        // Alternative: amount with trailing minus anywhere on line
        val amountWithTrailingMinusPattern = Regex(
            """(\d{1,3}(?:[.]\d{3})*,\d{2})(-)?$"""
        )

        // Short date pattern: DD.MM. or DD.MM
        val shortDatePattern = Regex("""^(\d{2}\.\d{2}\.?)""")

        var currentBookingDate: LocalDate? = null
        var currentYear = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            // Skip empty lines
            if (line.isEmpty()) {
                i++
                continue
            }

            // Check for booking date header: "Buchungsdatum: DD.MM.YYYY"
            val dateHeaderMatch = bookingDateHeaderPattern.find(line)
            if (dateHeaderMatch != null) {
                val dateStr = dateHeaderMatch.groupValues[1]
                currentBookingDate = parseGermanDate(dateStr)
                // Extract year from the booking date for short dates
                currentBookingDate?.let { currentYear = it.year }
                i++
                continue
            }

            // Skip balance lines
            if (isBalanceOrHeaderLine(line)) {
                i++
                continue
            }

            // Check if this line ends with a valuta date + amount (transaction ending)
            val valutaMatch = valutaAmountPattern.find(line)
            if (valutaMatch != null) {
                // This is a single-line transaction ending or standalone amount line
                // Look back to find description
                val valutaDateStr = valutaMatch.groupValues[1]
                val amountStr = valutaMatch.groupValues[2]
                val isDebit = valutaMatch.groupValues[3] == "-"

                val amount = parseGermanAmount(amountStr)
                val valutaDate = parseShortDate(valutaDateStr, currentYear)

                if (amount != null && (currentBookingDate != null || valutaDate != null)) {
                    val finalAmount = if (isDebit) -kotlin.math.abs(amount) else kotlin.math.abs(amount)
                    val bookingDate = currentBookingDate ?: valutaDate!!

                    // This is a single amount line - look back for description
                    val descriptionParts = mutableListOf<String>()
                    var j = i - 1
                    while (j >= 0 && j > i - 15) {
                        val prevLine = lines[j].trim()
                        if (prevLine.isEmpty()) {
                            j--
                            continue
                        }
                        // Stop if we hit a previous amount line or booking date header
                        if (valutaAmountPattern.containsMatchIn(prevLine) ||
                            bookingDateHeaderPattern.containsMatchIn(prevLine) ||
                            isBalanceOrHeaderLine(prevLine)) {
                            break
                        }
                        descriptionParts.add(0, prevLine)
                        j--
                    }

                    val fullDescription = descriptionParts.joinToString(" ")
                        .replace(Regex("""\s+"""), " ")
                        .trim()

                    if (fullDescription.isNotBlank() && !isBalanceEntry(fullDescription, fullDescription)) {
                        transactions.add(
                            ParsedTransaction(
                                bookingDate = bookingDate,
                                valueDate = valutaDate ?: bookingDate,
                                amount = finalAmount,
                                currency = "EUR",
                                description = fullDescription,
                                counterpartyName = extractCommerzbankCounterparty(fullDescription),
                                transactionType = detectTransactionType(fullDescription),
                                rawText = descriptionParts.joinToString("\n") + "\n" + line
                            )
                        )
                    }
                }
                i++
                continue
            }

            // Check if this is a transaction start line (description start)
            // Look ahead to find the valuta + amount line
            if (!isHeaderOrFooter(line) && currentBookingDate != null) {
                val descriptionParts = mutableListOf(line)
                var foundAmount = false
                var j = i + 1

                while (j < lines.size && j < i + 20) {
                    val nextLine = lines[j].trim()

                    // Skip empty lines within transaction
                    if (nextLine.isEmpty()) {
                        j++
                        continue
                    }

                    // Stop at new booking date header
                    if (bookingDateHeaderPattern.containsMatchIn(nextLine)) {
                        break
                    }

                    // Check for valuta + amount line
                    val amountMatch = valutaAmountPattern.find(nextLine)
                    if (amountMatch != null) {
                        val valutaDateStr = amountMatch.groupValues[1]
                        val amountStr = amountMatch.groupValues[2]
                        val isDebit = amountMatch.groupValues[3] == "-"

                        val amount = parseGermanAmount(amountStr)
                        val valutaDate = parseShortDate(valutaDateStr, currentYear)

                        if (amount != null) {
                            val finalAmount = if (isDebit) -kotlin.math.abs(amount) else kotlin.math.abs(amount)

                            val fullDescription = descriptionParts.joinToString(" ")
                                .replace(Regex("""\s+"""), " ")
                                .trim()

                            if (fullDescription.isNotBlank() && !isBalanceEntry(fullDescription, fullDescription)) {
                                transactions.add(
                                    ParsedTransaction(
                                        bookingDate = currentBookingDate!!,
                                        valueDate = valutaDate ?: currentBookingDate!!,
                                        amount = finalAmount,
                                        currency = "EUR",
                                        description = fullDescription,
                                        counterpartyName = extractCommerzbankCounterparty(fullDescription),
                                        transactionType = detectTransactionType(fullDescription),
                                        rawText = descriptionParts.joinToString("\n") + "\n" + nextLine
                                    )
                                )
                            }
                            foundAmount = true
                            i = j + 1
                            break
                        }
                    }

                    // Check if line ends with amount (without short date prefix)
                    val amountEndMatch = amountWithTrailingMinusPattern.find(nextLine)
                    if (amountEndMatch != null && shortDatePattern.containsMatchIn(nextLine)) {
                        // This line has both date and amount at end
                        val shortDateMatch = shortDatePattern.find(nextLine)
                        val textBeforeDate = if (shortDateMatch != null) {
                            nextLine.substring(0, shortDateMatch.range.first).trim()
                        } else ""

                        if (textBeforeDate.isNotEmpty()) {
                            descriptionParts.add(textBeforeDate)
                        }

                        val valutaDateStr = shortDateMatch?.groupValues?.get(1) ?: ""
                        val amountStr = amountEndMatch.groupValues[1]
                        val isDebit = amountEndMatch.groupValues[2] == "-"

                        val amount = parseGermanAmount(amountStr)
                        val valutaDate = parseShortDate(valutaDateStr, currentYear)

                        if (amount != null) {
                            val finalAmount = if (isDebit) -kotlin.math.abs(amount) else kotlin.math.abs(amount)

                            val fullDescription = descriptionParts.joinToString(" ")
                                .replace(Regex("""\s+"""), " ")
                                .trim()

                            if (fullDescription.isNotBlank() && !isBalanceEntry(fullDescription, fullDescription)) {
                                transactions.add(
                                    ParsedTransaction(
                                        bookingDate = currentBookingDate!!,
                                        valueDate = valutaDate ?: currentBookingDate!!,
                                        amount = finalAmount,
                                        currency = "EUR",
                                        description = fullDescription,
                                        counterpartyName = extractCommerzbankCounterparty(fullDescription),
                                        transactionType = detectTransactionType(fullDescription),
                                        rawText = descriptionParts.joinToString("\n") + "\n" + nextLine
                                    )
                                )
                            }
                            foundAmount = true
                            i = j + 1
                            break
                        }
                    }

                    // Add to description
                    if (!isBalanceOrHeaderLine(nextLine)) {
                        descriptionParts.add(nextLine)
                    }
                    j++
                }

                if (foundAmount) continue
            }

            i++
        }

        return transactions.distinctBy { "${it.bookingDate}_${it.amount}_${it.description.take(30)}" }
    }

    /**
     * Parse short date format (DD.MM. or DD.MM) with given year
     */
    private fun parseShortDate(dateStr: String, year: Int): LocalDate? {
        return try {
            val cleanDate = dateStr.trimEnd('.')
            val parts = cleanDate.split(".")
            if (parts.size >= 2) {
                LocalDate(year, parts[1].toInt(), parts[0].toInt())
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if line is a balance, summary, or header line
     */
    private fun isBalanceOrHeaderLine(line: String): Boolean {
        val lower = line.lowercase()
        return lower.contains("alter kontostand") ||
               lower.contains("neuer kontostand") ||
               lower.contains("neuer saldo") ||
               lower.contains("alter saldo") ||
               lower.contains("kontowährung") ||
               lower.contains("zu ihren lasten") ||
               lower.contains("zu ihren gunsten") ||
               lower.contains("kontostand") ||
               lower.contains("saldo") ||
               lower.contains("summe") ||
               lower.contains("übertrag") ||
               lower.contains("seite ") ||
               isHeaderOrFooter(line)
    }

    /**
     * Extract counterparty from Commerzbank transaction description
     * Looks for patterns like "Absender:", "Empfänger:", or extracts from SEPA fields
     */
    private fun extractCommerzbankCounterparty(description: String): String? {
        // Try to find explicit sender/recipient
        val patterns = listOf(
            Regex("""(?:Absender|Empfänger|Auftraggeber|Zahlungsempfänger)[:\s]+([A-Za-zäöüÄÖÜß\s.\-]+?)(?:\s+IBAN|\s+BIC|\s+End-to-End|$)""", RegexOption.IGNORE_CASE),
            Regex("""(?:von|an)[:\s]+([A-Za-zäöüÄÖÜß\s.\-]{3,50})""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            val match = pattern.find(description)
            if (match != null) {
                val name = match.groupValues[1].trim()
                if (name.length > 2) {
                    return name
                }
            }
        }

        // Fallback to generic extraction
        return extractCounterparty(description)
    }
}
