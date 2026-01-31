package com.banking.statement.parser.banks

import com.banking.statement.parser.ParseResult
import com.banking.statement.parser.ParsedTransaction
import kotlinx.datetime.LocalDate

/**
 * Parser for Tomorrow Bank statements (Rechnungsabschluss)
 *
 * Format:
 * Buchung / Valuta    Beschreibung                                    Betrag
 * 06.03.2024 /        AMAZON PAYMENTS EUROPE S.C.A.                   -554,84 €
 * 06.03.2024          IBAN: DE28110101010000310001 – BIC: SOBKDEB2XXX
 *
 * Pattern: DD.MM.YYYY / DD.MM.YYYY  COUNTERPARTY  +/-AMOUNT €
 */
class TomorrowParser : GermanBankParser() {
    override val bankName = "Tomorrow"

    // CERTAIN: unique identifiers
    private val certainIdentifiers = listOf(
        "sobkdeb2",      // BIC
        "sobkdeb2xxx",   // BIC full
        "sobkdebbxxx",   // Another BIC variant
        "tomorrow gmbh",
        "tomorrow bank"
    )

    // HIGH: strong indicators
    private val highIdentifiers = listOf(
        "tomorrow"
    )

    // MEDIUM: patterns in Tomorrow statements
    private val mediumIdentifiers = listOf(
        "rechnungsabschluss",
        "buchung / valuta",
        "hauptkonto"
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return certainIdentifiers.any { lower.contains(it) } ||
               (lower.contains("tomorrow") && lower.contains("rechnungsabschluss"))
    }

    override fun getConfidence(pdfText: String): Pair<DetectionConfidence, List<String>> {
        return calculateConfidence(pdfText, certainIdentifiers, highIdentifiers, mediumIdentifiers)
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        try {
            val lines = pdfText.lines()
            val accountIban = extractIban(pdfText)
            val statementPeriod = extractTomorrowPeriod(pdfText)

            // Try Tomorrow specific format
            var transactions = parseTomorrowFormat(lines)

            // Fallback to generic parser
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
                    errorMessage = "Could not extract transactions from Tomorrow PDF"
                )
            }
        } catch (e: Exception) {
            return ParseResult(
                success = false,
                bankName = bankName,
                errorMessage = "Error parsing Tomorrow PDF: ${e.message}"
            )
        }
    }

    /**
     * Extract statement period from Tomorrow header
     * Format: "01.03.2024 – 31.03.2024"
     */
    private fun extractTomorrowPeriod(text: String): String? {
        val periodPattern = Regex("""(\d{2}\.\d{2}\.\d{4})\s*[–-]\s*(\d{2}\.\d{2}\.\d{4})""")
        val match = periodPattern.find(text)
        return match?.let { "${it.groupValues[1]} - ${it.groupValues[2]}" }
    }

    /**
     * Parse Tomorrow format:
     * DD.MM.YYYY /    COUNTERPARTY                    +/-AMOUNT €
     * DD.MM.YYYY      IBAN: ... – BIC: ...
     */
    private fun parseTomorrowFormat(lines: List<String>): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()

        // Pattern for transaction line:
        // DD.MM.YYYY / (optional second date on same or next line) COUNTERPARTY AMOUNT €
        // Amount format: -554,84 € or +1.000,00 €
        val transactionPattern = Regex(
            """^(\d{2}\.\d{2}\.\d{4})\s*/?\s*(.+?)\s+([+-]?\d{1,3}(?:\.\d{3})*,\d{2})\s*€\s*$"""
        )

        // Alternative: date at start, counterparty, then amount with € at end
        val altTransactionPattern = Regex(
            """^(\d{2}\.\d{2}\.\d{4})\s*/\s*(.+?)\s+([+-]?\d{1,3}(?:\.\d{3})*,\d{2})\s*€\s*$"""
        )

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            if (line.isEmpty() || isTomorrowHeaderLine(line)) {
                i++
                continue
            }

            // Try to match transaction line
            val match = transactionPattern.find(line) ?: altTransactionPattern.find(line)

            if (match != null) {
                val bookingDateStr = match.groupValues[1]
                var middlePart = match.groupValues[2].trim()
                val amountStr = match.groupValues[3]

                // Parse booking date
                val bookingDate = parseFullDate(bookingDateStr)

                // Parse amount (already has +/- sign)
                val amount = parseGermanAmount(amountStr.replace("+", "").replace("-", ""))
                val isDebit = amountStr.startsWith("-") || !amountStr.startsWith("+")

                if (bookingDate != null && amount != null) {
                    val finalAmount = if (isDebit && !amountStr.startsWith("-")) {
                        // No sign means expense in Tomorrow format? Check context
                        -kotlin.math.abs(amount)
                    } else if (amountStr.startsWith("-")) {
                        -kotlin.math.abs(amount)
                    } else {
                        kotlin.math.abs(amount)
                    }

                    // Extract value date if present in middle part
                    var valueDate = bookingDate
                    var counterpartyName = middlePart

                    // Check if middle part starts with a date (value date)
                    val valueDateMatch = Regex("""^(\d{2}\.\d{2}\.\d{4})\s+(.+)$""").find(middlePart)
                    if (valueDateMatch != null) {
                        valueDate = parseFullDate(valueDateMatch.groupValues[1]) ?: bookingDate
                        counterpartyName = valueDateMatch.groupValues[2].trim()
                    }

                    // Collect description lines
                    val descriptionParts = mutableListOf<String>()
                    var j = i + 1

                    while (j < lines.size && j < i + 10) {
                        val nextLine = lines[j].trim()

                        if (nextLine.isEmpty()) {
                            j++
                            continue
                        }

                        // Stop at next transaction (starts with date)
                        if (Regex("""^\d{2}\.\d{2}\.\d{4}\s*/""").containsMatchIn(nextLine)) {
                            break
                        }

                        // Stop at header
                        if (isTomorrowHeaderLine(nextLine)) {
                            break
                        }

                        // Check if this is a value date line (just date, no amount)
                        val justDateMatch = Regex("""^(\d{2}\.\d{2}\.\d{4})\s*$""").find(nextLine)
                        if (justDateMatch != null) {
                            valueDate = parseFullDate(justDateMatch.groupValues[1]) ?: valueDate
                            j++
                            continue
                        }

                        // Skip IBAN/BIC lines but continue collecting
                        if (!nextLine.startsWith("IBAN:") && !nextLine.contains("– BIC:")) {
                            descriptionParts.add(nextLine)
                        }
                        j++
                    }

                    // Clean counterparty name
                    counterpartyName = cleanTomorrowCounterparty(counterpartyName)

                    transactions.add(
                        ParsedTransaction(
                            bookingDate = bookingDate,
                            valueDate = valueDate,
                            amount = finalAmount,
                            currency = "EUR",
                            description = counterpartyName,
                            counterpartyName = counterpartyName,
                            transactionType = if (finalAmount >= 0) "Gutschrift" else "Lastschrift",
                            rawText = "$line\n${descriptionParts.joinToString("\n")}"
                        )
                    )

                    i = j
                    continue
                }
            }

            i++
        }

        return transactions.distinctBy { "${it.bookingDate}_${it.amount}_${it.counterpartyName?.take(20)}" }
    }

    /**
     * Parse full date (DD.MM.YYYY)
     */
    private fun parseFullDate(dateStr: String): LocalDate? {
        return try {
            val parts = dateStr.split(".")
            if (parts.size == 3) {
                LocalDate(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Clean Tomorrow counterparty name
     */
    private fun cleanTomorrowCounterparty(name: String): String {
        var cleaned = name.trim()

        // Remove trailing reference numbers
        cleaned = cleaned.replace(Regex("""\s+mr-[a-f0-9]+$"""), "")
        cleaned = cleaned.replace(Regex("""\s+[A-Z0-9]{20,}$"""), "")

        return cleaned.take(60)
    }

    /**
     * Check if line is Tomorrow header/footer
     */
    private fun isTomorrowHeaderLine(line: String): Boolean {
        val lower = line.lowercase()

        return lower.contains("tomorrow") && lower.length < 20 ||
               lower.contains("rechnungsabschluss") ||
               lower.contains("buchung / valuta") ||
               lower.contains("beschreibung") && lower.contains("betrag") ||
               lower.contains("hauptkonto") ||
               lower.startsWith("mr.") || lower.startsWith("frau") || lower.startsWith("herr") ||
               lower.contains("deutschland") && Regex("""\d{5}""").containsMatchIn(line) ||
               lower.startsWith("iban:") && lower.contains("bic:") ||
               lower.contains("seite") && Regex("""\d+\s*/\s*\d+""").containsMatchIn(lower) ||
               // Period line
               Regex("""^\d{2}\.\d{2}\.\d{4}\s*[–-]\s*\d{2}\.\d{2}\.\d{4}$""").matches(line.trim()) ||
               isHeaderOrFooter(line)
    }
}
