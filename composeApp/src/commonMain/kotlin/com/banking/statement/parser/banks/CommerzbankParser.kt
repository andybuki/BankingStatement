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
 * Format from PDF:
 * - Header: "Kontowährung Euro" with columns "zu Ihren Lasten" | "zu Ihren Gunsten"
 * - Column header: "Angaben zu den Umsätzen | Valuta | zu Ihren Lasten | zu Ihren Gunsten"
 * - Date headers: "Buchungsdatum: DD.MM.YYYY"
 * - Transaction FIRST LINE: "DESCRIPTION    DD.MM    AMOUNT[-]"
 *   - Valuta date (DD.MM) and amount are on the SAME line as description start
 *   - Amount with trailing minus (7,99-) = debit (zu Ihren Lasten)
 *   - Amount without minus (7,99) = credit (zu Ihren Gunsten)
 * - Following lines: Additional description details (IBAN, BIC, End-to-End-Ref, etc.)
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
        "alter kontostand vom", // Opening balance header
        "angaben zu den umsätzen" // Transaction column header
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
            if (transactions.size < 2) {
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
     * Parse Commerzbank specific format where valuta and amount are on FIRST line:
     *
     * Buchungsdatum: 13.07.2022
     * AMAZON EU S.A R.L., NIEDERLASSUNG D    12.07    7,99-
     * EUTSCHLAND
     * D01-0693031-6475854 AMZNPrime DE 39
     * ZVXS82AE8XQOOJ
     * End-to-End-Ref.: 39ZVXS82AE8XQOOJ
     * ...
     *
     * BENACHRICHTIGUNGSENTGELT FUER    13.07    1,90-
     * DIE RUECKGABE VON LASTSCHRIFTEN
     * STUECK    1
     */
    private fun parseCommerzbankFormat(lines: List<String>): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()

        // Pattern for "Buchungsdatum: DD.MM.YYYY"
        val bookingDateHeaderPattern = Regex("""Buchungsdatum:\s*(\d{2}\.\d{2}\.\d{4})""", RegexOption.IGNORE_CASE)

        // Pattern for transaction first line: TEXT + DD.MM + AMOUNT with optional trailing minus
        // Matches: "AMAZON EU S.A R.L., NIEDERLASSUNG D    12.07    7,99-"
        // The amount can have spaces: "7,99-" or "7,99 -" or "1 234,56-"
        val transactionLinePattern = Regex(
            """^(.+?)\s+(\d{2}\.\d{2})\s+(\d{1,3}(?:[\s.]\d{3})*,\d{2})\s*(-)?$"""
        )

        // Alternative pattern for amounts that might have space before minus
        val transactionLinePatternAlt = Regex(
            """^(.+?)\s+(\d{2}\.\d{2})\s+(\d{1,3}(?:[\s.]\d{3})*,\d{2})\s*(-)?"""
        )

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
                currentBookingDate?.let { currentYear = it.year }
                i++
                continue
            }

            // Skip balance and header lines
            if (isBalanceOrHeaderLine(line)) {
                i++
                continue
            }

            // Check if this is a transaction first line (description + valuta + amount)
            val txMatch = transactionLinePattern.find(line) ?: transactionLinePatternAlt.find(line)
            if (txMatch != null) {
                val descriptionStart = txMatch.groupValues[1].trim()
                val valutaDateStr = txMatch.groupValues[2]
                val amountStr = txMatch.groupValues[3].replace(" ", "") // Remove spaces in amount
                val isDebit = txMatch.groupValues[4] == "-"

                val amount = parseGermanAmount(amountStr)
                val valutaDate = parseShortDate(valutaDateStr, currentYear)

                if (amount != null) {
                    val finalAmount = if (isDebit) -kotlin.math.abs(amount) else kotlin.math.abs(amount)
                    val bookingDate = currentBookingDate ?: valutaDate

                    // Collect additional description lines
                    val descriptionParts = mutableListOf(descriptionStart)
                    var j = i + 1

                    while (j < lines.size && j < i + 20) {
                        val nextLine = lines[j].trim()

                        // Skip empty lines
                        if (nextLine.isEmpty()) {
                            j++
                            continue
                        }

                        // Stop at new booking date header
                        if (bookingDateHeaderPattern.containsMatchIn(nextLine)) {
                            break
                        }

                        // Stop at next transaction (line with valuta + amount pattern)
                        if (transactionLinePattern.containsMatchIn(nextLine) ||
                            transactionLinePatternAlt.containsMatchIn(nextLine)) {
                            break
                        }

                        // Stop at balance/header lines
                        if (isBalanceOrHeaderLine(nextLine)) {
                            break
                        }

                        // Add to description
                        descriptionParts.add(nextLine)
                        j++
                    }

                    val fullDescription = descriptionParts.joinToString(" ")
                        .replace(Regex("""\s+"""), " ")
                        .trim()

                    if (fullDescription.isNotBlank() && !isBalanceEntry(fullDescription, fullDescription) && bookingDate != null) {
                        transactions.add(
                            ParsedTransaction(
                                bookingDate = bookingDate,
                                valueDate = valutaDate ?: bookingDate,
                                amount = finalAmount,
                                currency = "EUR",
                                description = fullDescription,
                                counterpartyName = extractCommerzbankCounterparty(fullDescription),
                                transactionType = detectTransactionType(fullDescription),
                                rawText = descriptionParts.joinToString("\n")
                            )
                        )
                    }

                    i = j
                    continue
                }
            }

            i++
        }

        return transactions.distinctBy { "${it.bookingDate}_${it.amount}_${it.description.take(30)}" }
    }

    /**
     * Parse short date format (DD.MM) with given year
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
               lower.contains("angaben zu den umsätzen") ||
               lower.contains("valuta") && lower.length < 20 ||
               isHeaderOrFooter(line)
    }

    /**
     * Extract counterparty from Commerzbank transaction description
     * First part of description is usually the counterparty name
     */
    private fun extractCommerzbankCounterparty(description: String): String? {
        // The counterparty is typically the first part before technical details
        // e.g., "AMAZON EU S.A R.L., NIEDERLASSUNG DEUTSCHLAND D01-0693031..."

        // Try to find explicit sender/recipient markers
        val patterns = listOf(
            Regex("""(?:Absender|Empfänger|Auftraggeber|Zahlungsempfänger)[:\s]+([A-Za-zäöüÄÖÜß\s.\-,]+?)(?:\s+IBAN|\s+BIC|\s+End-to-End|\s+DE\d|$)""", RegexOption.IGNORE_CASE),
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

        // For Commerzbank, take first meaningful segment as counterparty
        // Stop at technical markers like D01-, DE, IBAN, etc.
        val techMarkers = listOf("D01-", "DE ", "IBAN", "BIC", "End-to-End", "Mandatsref", "Gläubiger")
        var counterparty = description

        for (marker in techMarkers) {
            val idx = counterparty.indexOf(marker, ignoreCase = true)
            if (idx > 5) {
                counterparty = counterparty.substring(0, idx).trim()
                break
            }
        }

        // Clean up trailing punctuation
        counterparty = counterparty.trimEnd(',', '.', ' ')

        // If still too long, take first line worth
        if (counterparty.length > 60) {
            val spaceIdx = counterparty.indexOf(' ', 40)
            if (spaceIdx > 0) {
                counterparty = counterparty.substring(0, spaceIdx)
            }
        }

        return counterparty.takeIf { it.length > 2 }
    }
}
