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
 * Supports TWO different formats:
 *
 * FORMAT 1 (older statements):
 * - Header: "Kontowährung Euro" with columns "zu Ihren Lasten" | "zu Ihren Gunsten"
 * - Date headers: "Buchungsdatum: DD.MM.YYYY"
 * - Transaction: "DESCRIPTION    DD.MM    AMOUNT[-]"
 *   - Trailing minus = debit, no minus = credit
 *
 * FORMAT 2 (newer/alternative statements):
 * - Table with columns: Buchungstag | Umsatzart | Buchungstext | Betrag
 * - Each row: "DD.MM.YYYY | Gutschrift/Lastschrift/etc | Description | +/- AMOUNT EUR"
 * - Prefix sign: + = credit, - = debit
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
        "zu ihren lasten",  // Debit column header (Format 1)
        "zu ihren gunsten", // Credit column header (Format 1)
        "buchungsdatum:",   // Date header (Format 1)
        "alter kontostand vom", // Opening balance header
        "buchungstag",      // Column header (Format 2)
        "umsatzart"         // Column header (Format 2)
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return certainIdentifiers.any { lower.contains(it) } ||
               highIdentifiers.any { lower.contains(it) } ||
               (lower.contains("buchungsdatum:") &&
                (lower.contains("zu ihren lasten") || lower.contains("zu ihren gunsten"))) ||
               (lower.contains("buchungstag") && lower.contains("umsatzart") && lower.contains("buchungstext"))
    }

    override fun getConfidence(pdfText: String): Pair<DetectionConfidence, List<String>> {
        return calculateConfidence(pdfText, certainIdentifiers, highIdentifiers, mediumIdentifiers)
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        try {
            val lines = pdfText.lines()
            val accountIban = extractIban(pdfText)
            val statementPeriod = extractStatementPeriod(pdfText)
            val lower = pdfText.lowercase()

            // Determine which format to use
            val transactions = when {
                // Format 2: Table format with Buchungstag/Umsatzart/Buchungstext/Betrag columns
                lower.contains("buchungstag") && lower.contains("umsatzart") && lower.contains("betrag") -> {
                    parseTableFormat2(lines)
                }
                // Format 1: Buchungsdatum headers with trailing minus amounts
                lower.contains("buchungsdatum:") || lower.contains("zu ihren lasten") -> {
                    parseFormat1(lines)
                }
                // Try both and pick best result
                else -> {
                    val format1 = parseFormat1(lines)
                    val format2 = parseTableFormat2(lines)
                    if (format2.size > format1.size) format2 else format1
                }
            }

            // Fallback to generic parser if specific formats didn't work
            val finalTransactions = if (transactions.size < 2) {
                val genericTransactions = parseComprehensiveFormat(lines)
                if (genericTransactions.size > transactions.size) genericTransactions else transactions
            } else {
                transactions
            }

            return if (finalTransactions.isNotEmpty()) {
                ParseResult(
                    success = true,
                    bankName = bankName,
                    accountIban = accountIban,
                    statementPeriod = statementPeriod,
                    transactions = finalTransactions
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
     * FORMAT 1: Older Commerzbank format with "Buchungsdatum:" headers
     *
     * Buchungsdatum: 13.07.2022
     * AMAZON EU S.A R.L., NIEDERLASSUNG D    12.07    7,99-
     * EUTSCHLAND
     * D01-0693031-6475854 AMZNPrime DE 39
     */
    private fun parseFormat1(lines: List<String>): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()

        val bookingDateHeaderPattern = Regex("""Buchungsdatum:\s*(\d{2}\.\d{2}\.\d{4})""", RegexOption.IGNORE_CASE)

        // Pattern: TEXT + DD.MM + AMOUNT with optional trailing minus
        val transactionLinePattern = Regex(
            """^(.+?)\s+(\d{2}\.\d{2})\s+(\d{1,3}(?:[\s.]\d{3})*,\d{2})\s*(-)?$"""
        )

        var currentBookingDate: LocalDate? = null
        var currentYear = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            if (line.isEmpty()) {
                i++
                continue
            }

            val dateHeaderMatch = bookingDateHeaderPattern.find(line)
            if (dateHeaderMatch != null) {
                val dateStr = dateHeaderMatch.groupValues[1]
                currentBookingDate = parseGermanDate(dateStr)
                currentBookingDate?.let { currentYear = it.year }
                i++
                continue
            }

            if (isBalanceOrHeaderLine(line)) {
                i++
                continue
            }

            val txMatch = transactionLinePattern.find(line)
            if (txMatch != null) {
                val descriptionStart = txMatch.groupValues[1].trim()
                val valutaDateStr = txMatch.groupValues[2]
                val amountStr = txMatch.groupValues[3].replace(" ", "")
                val isDebit = txMatch.groupValues[4] == "-"

                val amount = parseGermanAmount(amountStr)
                val valutaDate = parseShortDate(valutaDateStr, currentYear)

                if (amount != null) {
                    val finalAmount = if (isDebit) -kotlin.math.abs(amount) else kotlin.math.abs(amount)
                    val bookingDate = currentBookingDate ?: valutaDate

                    val descriptionParts = mutableListOf(descriptionStart)
                    var j = i + 1

                    while (j < lines.size && j < i + 20) {
                        val nextLine = lines[j].trim()

                        if (nextLine.isEmpty()) {
                            j++
                            continue
                        }

                        if (bookingDateHeaderPattern.containsMatchIn(nextLine) ||
                            transactionLinePattern.containsMatchIn(nextLine) ||
                            isBalanceOrHeaderLine(nextLine)) {
                            break
                        }

                        descriptionParts.add(nextLine)
                        j++
                    }

                    val fullDescription = descriptionParts.joinToString(" ").replace(Regex("""\s+"""), " ").trim()

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
     * FORMAT 2: Table format with columns Buchungstag | Umsatzart | Buchungstext | Betrag
     *
     * 31.01.2020    Gutschrift    Bundesagentur für Arbeit-Service-Ha    + 841,50 EUR
     *                             us
     * 29.01.2020    Dauerauftrag  ERNST NILL                             - 409,50 EUR
     *                             NORSDE71XXX
     */
    private fun parseTableFormat2(lines: List<String>): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()

        // Pattern for line starting with date and ending with amount
        // DD.MM.YYYY ... +/- AMOUNT EUR
        val transactionStartPattern = Regex(
            """^(\d{2}\.\d{2}\.\d{4})\s+(\S+)\s+(.+?)\s+([+-])\s*(\d{1,3}(?:[.\s]\d{3})*,\d{2})\s*EUR\s*$"""
        )

        // Alternative pattern with more flexible spacing
        val transactionStartPatternAlt = Regex(
            """^(\d{2}\.\d{2}\.\d{4})\s+(.+?)\s+([+-])\s*(\d{1,3}(?:[.\s]\d{3})*,\d{2})\s*EUR"""
        )

        // Pattern just for amount at end of line: "+/- AMOUNT EUR"
        val amountEndPattern = Regex("""([+-])\s*(\d{1,3}(?:[.\s]\d{3})*,\d{2})\s*EUR\s*$""")

        // Pattern for date at start
        val dateStartPattern = Regex("""^(\d{2}\.\d{2}\.\d{4})""")

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            if (line.isEmpty() || isBalanceOrHeaderLine(line)) {
                i++
                continue
            }

            // Check for transaction line starting with date
            val dateMatch = dateStartPattern.find(line)
            val amountMatch = amountEndPattern.find(line)

            if (dateMatch != null && amountMatch != null) {
                // This line has both date and amount
                val dateStr = dateMatch.groupValues[1]
                val bookingDate = parseGermanDate(dateStr)
                val sign = amountMatch.groupValues[1]
                val amountStr = amountMatch.groupValues[2].replace(" ", "").replace(".", "")
                val amount = parseGermanAmount(amountStr)

                if (bookingDate != null && amount != null) {
                    val finalAmount = if (sign == "-") -kotlin.math.abs(amount) else kotlin.math.abs(amount)

                    // Extract description between date and amount
                    val descStart = dateMatch.range.last + 1
                    val descEnd = amountMatch.range.first
                    var description = if (descEnd > descStart) {
                        line.substring(descStart, descEnd).trim()
                    } else ""

                    // Try to extract Umsatzart from description
                    var transactionType = "Buchung"
                    val umsatzTypes = listOf("Gutschrift", "Lastschrift", "Dauerauftrag", "Überweisung",
                                             "Kartenzahlung", "Gehalt", "Lohn", "Abschluss")
                    for (uType in umsatzTypes) {
                        if (description.startsWith(uType, ignoreCase = true)) {
                            transactionType = uType
                            description = description.substring(uType.length).trim()
                            break
                        }
                    }

                    // Collect continuation lines (description may span multiple lines)
                    val descriptionParts = mutableListOf(description)
                    var j = i + 1

                    while (j < lines.size && j < i + 10) {
                        val nextLine = lines[j].trim()

                        if (nextLine.isEmpty()) {
                            j++
                            continue
                        }

                        // Stop at next transaction (starts with date) or has amount at end
                        if (dateStartPattern.containsMatchIn(nextLine) && amountEndPattern.containsMatchIn(nextLine)) {
                            break
                        }

                        // Stop at header/balance lines
                        if (isBalanceOrHeaderLine(nextLine)) {
                            break
                        }

                        // Check if this is a continuation line (no date at start, no amount at end)
                        if (!dateStartPattern.containsMatchIn(nextLine) && !amountEndPattern.containsMatchIn(nextLine)) {
                            descriptionParts.add(nextLine)
                            j++
                        } else {
                            break
                        }
                    }

                    val fullDescription = descriptionParts.joinToString(" ").replace(Regex("""\s+"""), " ").trim()

                    if (fullDescription.isNotBlank() && !isBalanceEntry(fullDescription, fullDescription)) {
                        transactions.add(
                            ParsedTransaction(
                                bookingDate = bookingDate,
                                valueDate = bookingDate,
                                amount = finalAmount,
                                currency = "EUR",
                                description = fullDescription,
                                counterpartyName = extractCommerzbankCounterparty(fullDescription),
                                transactionType = transactionType,
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
               (lower.contains("buchungstag") && lower.contains("umsatzart")) ||
               (lower.contains("valuta") && lower.length < 20) ||
               lower.startsWith("betrag") ||
               lower.startsWith("buchungstext") ||
               isHeaderOrFooter(line)
    }

    /**
     * Extract counterparty from Commerzbank transaction description
     */
    private fun extractCommerzbankCounterparty(description: String): String? {
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
        val techMarkers = listOf("D01-", "DE ", "IBAN", "BIC", "End-to-End", "Mandatsref", "Gläubiger", "NORSDE", "//")
        var counterparty = description

        for (marker in techMarkers) {
            val idx = counterparty.indexOf(marker, ignoreCase = true)
            if (idx > 3) {
                counterparty = counterparty.substring(0, idx).trim()
                break
            }
        }

        counterparty = counterparty.trimEnd(',', '.', ' ')

        if (counterparty.length > 60) {
            val spaceIdx = counterparty.indexOf(' ', 40)
            if (spaceIdx > 0) {
                counterparty = counterparty.substring(0, spaceIdx)
            }
        }

        return counterparty.takeIf { it.length > 2 }
    }
}
