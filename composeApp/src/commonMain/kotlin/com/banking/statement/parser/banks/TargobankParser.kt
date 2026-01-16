package com.banking.statement.parser.banks

import com.banking.statement.parser.ParseResult
import com.banking.statement.parser.ParsedTransaction
import kotlinx.datetime.LocalDate

/**
 * Parser for TARGOBANK statements (Monatsübersicht)
 *
 * Format:
 * Datum Tag   Buchungstext                                    Ausgaben    Einnahmen   Guthaben/Kredit
 * 30.09  DI   ANFANGSSALDO                                                            1.589,23
 * 01.10  MI   AUSFÜHRUNG DAUERAUFTRAG    AUFTRAGSNUMMER       720,90                  868,33
 *             0300004
 *             WOLFGANG OVERATH
 *             BIC:GENODED1RST IBAN:DE58370695204107414067
 *             MIETE BOUJIBAR
 *
 * Pattern: DD.MM WEEKDAY DESCRIPTION    AUSGABEN    EINNAHMEN    BALANCE
 */
class TargobankParser : GermanBankParser() {
    override val bankName = "TARGOBANK"

    // CERTAIN: unique identifiers
    private val certainIdentifiers = listOf(
        "cmcidedd",      // BIC
        "cmcideddxxx",   // BIC full
        "targobank"
    )

    // HIGH: strong indicators
    private val highIdentifiers = listOf(
        "targo bank",
        "targo-bank",
        "citibank"       // Former name
    )

    // MEDIUM: patterns in TARGOBANK statements
    private val mediumIdentifiers = listOf(
        "monatsübersicht",
        "finanzstatus",
        "plus-konto",
        "guthaben/kredit",
        "ausgaben",
        "einnahmen"
    )

    // Weekday abbreviations used in TARGOBANK statements
    private val weekdays = listOf("MO", "DI", "MI", "DO", "FR", "SA", "SO")

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return certainIdentifiers.any { lower.contains(it) } ||
               highIdentifiers.any { lower.contains(it) } ||
               (lower.contains("ausgaben") && lower.contains("einnahmen") && lower.contains("guthaben/kredit"))
    }

    override fun getConfidence(pdfText: String): Pair<DetectionConfidence, List<String>> {
        return calculateConfidence(pdfText, certainIdentifiers, highIdentifiers, mediumIdentifiers)
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        try {
            val lines = pdfText.lines()
            val accountIban = extractIban(pdfText)
            val statementPeriod = extractTargobankPeriod(pdfText)

            // Extract year from statement period or FINANZSTATUS header
            val year = extractYearFromText(pdfText)

            // Try TARGOBANK specific format
            var transactions = parseTargobankFormat(lines, year)

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
                    errorMessage = "Could not extract transactions from TARGOBANK PDF"
                )
            }
        } catch (e: Exception) {
            return ParseResult(
                success = false,
                bankName = bankName,
                errorMessage = "Error parsing TARGOBANK PDF: ${e.message}"
            )
        }
    }

    /**
     * Extract statement period from TARGOBANK header
     * Format: "F I N A N Z S T A T U S vom 01.10.2025 - 31.10.2025"
     */
    private fun extractTargobankPeriod(text: String): String? {
        val periodPattern = Regex("""vom\s+(\d{2}\.\d{2}\.\d{4})\s*-\s*(\d{2}\.\d{2}\.\d{4})""")
        val match = periodPattern.find(text)
        return match?.let { "${it.groupValues[1]} - ${it.groupValues[2]}" }
    }

    /**
     * Extract year from statement period or any full date in the text
     */
    private fun extractYearFromText(text: String): Int {
        // Try FINANZSTATUS header first
        val finanzPattern = Regex("""vom\s+\d{2}\.\d{2}\.(\d{4})""")
        val finanzMatch = finanzPattern.find(text)
        if (finanzMatch != null) {
            return finanzMatch.groupValues[1].toIntOrNull() ?: 2024
        }

        // Try any full date
        val fullDatePattern = Regex("""\d{2}\.\d{2}\.(\d{4})""")
        val dateMatch = fullDatePattern.find(text)
        return dateMatch?.groupValues?.get(1)?.toIntOrNull() ?: 2024
    }

    /**
     * Parse TARGOBANK format:
     * DD.MM WEEKDAY DESCRIPTION    AUSGABEN    EINNAHMEN    BALANCE
     */
    private fun parseTargobankFormat(lines: List<String>, year: Int): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()

        // Pattern: DD.MM followed by weekday (MO, DI, MI, DO, FR, SA, SO)
        val transactionStartPattern = Regex("""^(\d{2})\.(\d{2})\s+(MO|DI|MI|DO|FR|SA|SO)\s+(.+)$""", RegexOption.IGNORE_CASE)

        // Amount pattern: German format (1.234,56 or 123,45)
        val amountPattern = Regex("""(\d{1,3}(?:\.\d{3})*,\d{2})""")

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            if (line.isEmpty() || isTargobankHeaderLine(line)) {
                i++
                continue
            }

            // Try to match transaction start line
            val match = transactionStartPattern.find(line)

            if (match != null) {
                val day = match.groupValues[1].toIntOrNull() ?: 1
                val month = match.groupValues[2].toIntOrNull() ?: 1
                val restOfLine = match.groupValues[4]

                // Skip ANFANGSSALDO (opening balance)
                if (restOfLine.contains("ANFANGSSALDO", ignoreCase = true)) {
                    i++
                    continue
                }

                // Find amounts in the line
                val amounts = amountPattern.findAll(restOfLine).toList()

                // Determine transaction type and amount
                // TARGOBANK format: description | Ausgaben | Einnahmen | Balance
                // We need to identify if it's expense (Ausgaben) or income (Einnahmen)
                val (amount, isDebit) = extractAmountFromTargobankLine(restOfLine, amounts)

                if (amount != null) {
                    val finalAmount = if (isDebit) -kotlin.math.abs(amount) else kotlin.math.abs(amount)

                    // Extract transaction type from beginning of description
                    val transactionType = extractTargobankTransactionType(restOfLine)

                    // Handle year transition (e.g., December transactions in January statement)
                    val adjustedYear = if (month > 10 && extractCurrentMonth(lines) < 3) year - 1 else year

                    val bookingDate = try {
                        LocalDate(adjustedYear, month, day)
                    } catch (e: Exception) {
                        null
                    }

                    if (bookingDate != null) {
                        // Collect description lines
                        val descriptionParts = mutableListOf<String>()
                        var counterpartyName: String? = null
                        var j = i + 1

                        while (j < lines.size && j < i + 15) {
                            val nextLine = lines[j].trim()

                            if (nextLine.isEmpty()) {
                                j++
                                continue
                            }

                            // Stop at next transaction (starts with date + weekday)
                            if (transactionStartPattern.containsMatchIn(nextLine)) {
                                break
                            }

                            // Stop at header/footer
                            if (isTargobankHeaderLine(nextLine)) {
                                break
                            }

                            // Extract counterparty name (first meaningful description line)
                            if (counterpartyName == null && isCounterpartyLine(nextLine)) {
                                counterpartyName = cleanCounterpartyName(nextLine)
                            }

                            descriptionParts.add(nextLine)
                            j++
                        }

                        // Use counterparty or transaction type as description
                        val mainDescription = counterpartyName ?: transactionType

                        transactions.add(
                            ParsedTransaction(
                                bookingDate = bookingDate,
                                valueDate = bookingDate,
                                amount = finalAmount,
                                currency = "EUR",
                                description = mainDescription,
                                counterpartyName = counterpartyName,
                                transactionType = transactionType,
                                rawText = "$line\n${descriptionParts.joinToString("\n")}"
                            )
                        )

                        i = j
                        continue
                    }
                }
            }

            i++
        }

        return transactions.distinctBy { "${it.bookingDate}_${it.amount}_${it.description.take(30)}" }
    }

    /**
     * Extract amount and determine if it's a debit or credit
     * TARGOBANK shows expenses in Ausgaben column, income in Einnahmen column
     */
    private fun extractAmountFromTargobankLine(line: String, amounts: List<MatchResult>): Pair<Double?, Boolean> {
        if (amounts.isEmpty()) return Pair(null, false)

        // If only one amount (besides balance), it's straightforward
        // The last amount is usually the balance, so we look at others

        // Try to determine column position based on spacing
        // Ausgaben is before Einnahmen, which is before Guthaben/Kredit

        val lineLength = line.length
        val amountPositions = amounts.map { it.range.first to it.value }

        // If we have amounts, analyze their positions
        // Usually: Description | Ausgaben | Einnahmen | Balance
        // Amounts at position ~60-70% of line = Ausgaben (debit)
        // Amounts at position ~75-85% of line = Einnahmen (credit)
        // Amounts at position >85% of line = Balance (ignore)

        for ((position, amountStr) in amountPositions) {
            val relativePos = position.toDouble() / lineLength

            // Skip balance column (usually at the end)
            if (relativePos > 0.85) continue

            val amount = parseGermanAmount(amountStr)
            if (amount != null) {
                // Ausgaben (expense) column is roughly 60-75% of line
                // Einnahmen (income) column is roughly 75-85% of line
                val isDebit = relativePos < 0.75
                return Pair(amount, isDebit)
            }
        }

        // Fallback: use first non-balance amount
        if (amounts.size >= 2) {
            val amount = parseGermanAmount(amounts[amounts.size - 2].value)
            // Check if it's in expense or income position
            val pos = amounts[amounts.size - 2].range.first.toDouble() / lineLength
            return Pair(amount, pos < 0.75)
        }

        return Pair(null, false)
    }

    /**
     * Extract transaction type from TARGOBANK description
     */
    private fun extractTargobankTransactionType(line: String): String {
        val types = listOf(
            "AUSFÜHRUNG DAUERAUFTRAG", "DAUERAUFTRAG", "GUTSCHRIFT",
            "LASTSCHRIFT", "ÜBERWEISUNG", "KREDITRATE", "KARTENZAHLUNG",
            "BARGELDAUSZAHLUNG", "GEHALT", "LOHN", "MIETE", "ANFANGSSALDO"
        )

        for (type in types) {
            if (line.uppercase().contains(type)) {
                return type
            }
        }

        return "BUCHUNG"
    }

    /**
     * Get current month from transactions to handle year transitions
     */
    private fun extractCurrentMonth(lines: List<String>): Int {
        val datePattern = Regex("""^(\d{2})\.(\d{2})\s+""")
        for (line in lines) {
            val match = datePattern.find(line.trim())
            if (match != null) {
                return match.groupValues[2].toIntOrNull() ?: 1
            }
        }
        return 1
    }

    /**
     * Check if line is likely a counterparty name
     */
    private fun isCounterpartyLine(line: String): Boolean {
        // Skip lines that are clearly technical
        if (line.startsWith("BIC:") || line.startsWith("IBAN:")) return false
        if (line.contains("BIC:") && line.contains("IBAN:")) return false
        if (Regex("""^\d{5,}$""").matches(line)) return false // Just numbers
        if (Regex("""^[A-Z0-9]{8,}$""").matches(line)) return false // Reference numbers
        if (line.startsWith("KREDIT ") && line.contains("PER")) return false
        if (line.startsWith("AUFTRAGSNUMMER")) return false
        if (line.startsWith("KundenRef:")) return false

        // Should have letters and be reasonable length
        return line.any { it.isLetter() } && line.length >= 3
    }

    /**
     * Clean counterparty name
     */
    private fun cleanCounterpartyName(line: String): String {
        var cleaned = line.trim()

        // Remove reference numbers at the end
        cleaned = cleaned.replace(Regex("""\s+\d{10,}$"""), "")

        // Remove EMPF. and following text
        val empfIdx = cleaned.indexOf("EMPF.")
        if (empfIdx > 10) {
            cleaned = cleaned.substring(0, empfIdx).trim()
        }

        return cleaned.take(60)
    }

    /**
     * Check if line is TARGOBANK header/footer
     */
    private fun isTargobankHeaderLine(line: String): Boolean {
        val lower = line.lowercase()
        val trimmed = line.trim()

        return lower.contains("monatsübersicht") ||
               lower.contains("finanzstatus") ||
               lower.contains("alle transaktionen ihres") ||
               lower.contains("plus-konto") && Regex("""\d{10}""").containsMatchIn(line) ||
               lower.contains("guthaben/kredit") && lower.contains("ausgaben") ||
               lower.contains("datum") && lower.contains("tag") && lower.contains("buchungstext") ||
               lower.contains("für girokonten erfolgt") ||
               lower.contains("textförmlichen einwendungen") ||
               lower.contains("belastungsbuchung") ||
               lower.startsWith("iban:") && lower.contains("bic") && trimmed.length < 60 ||
               lower.startsWith("herr ") || lower.startsWith("frau ") ||
               lower.contains("transaktionen") && lower.length < 30 ||
               // Page numbers
               Regex("""seite\s+\d+""", RegexOption.IGNORE_CASE).containsMatchIn(lower) ||
               // Summary sections
               lower.contains("girokonten") && lower.contains("kontotyp") ||
               lower.contains("kreditkarten") ||
               lower.contains("summe") && lower.length < 30 ||
               lower.contains("gesamtguthaben") ||
               isHeaderOrFooter(line)
    }
}
