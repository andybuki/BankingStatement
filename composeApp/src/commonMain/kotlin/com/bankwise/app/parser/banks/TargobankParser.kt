package com.bankwise.app.parser.banks

import com.bankwise.app.parser.ParseResult
import com.bankwise.app.parser.ParsedTransaction
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

                // Skip RESERV. BETRAG (card reservations/holds) - they are temporary
                // and will be replaced by actual KARTENZAHLUNG transactions
                if (restOfLine.contains("RESERV.", ignoreCase = true) ||
                    restOfLine.contains("RESERVIERUNG", ignoreCase = true)) {
                    i++
                    continue
                }

                // Find amounts in the line
                val amounts = amountPattern.findAll(restOfLine).toList()

                // Skip entries that only have balance (no actual transaction)
                // If only 1 amount, it's just the balance column - skip
                if (amounts.size < 2) {
                    i++
                    continue
                }

                // Determine transaction type and amount
                // TARGOBANK format: description | Ausgaben | Einnahmen | Balance
                // If 2 amounts: first is transaction (Ausgaben OR Einnahmen), second is balance
                val (amount, isDebit) = extractAmountFromTargobankLine(restOfLine, amounts)

                // Skip if no transaction amount found (only balance)
                if (amount == null) {
                    i++
                    continue
                }

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

            i++
        }

        return transactions.distinctBy { "${it.bookingDate}_${it.amount}_${it.description.take(30)}" }
    }

    /**
     * Extract amount and determine if it's a debit or credit
     * TARGOBANK shows expenses in Ausgaben column, income in Einnahmen column
     *
     * Since PDF text extraction doesn't preserve column spacing,
     * we determine income vs expense based on TRANSACTION TYPE:
     * - GUTSCHRIFT, LOHN, GEHALT, RENTE, EINZAHLUNG, HABEN → income (+)
     * - LASTSCHRIFT, ÜBERWEISUNG, KREDITRATE, KARTENZAHLUNG, SOLL → expense (-)
     */
    private fun extractAmountFromTargobankLine(line: String, amounts: List<MatchResult>): Pair<Double?, Boolean> {
        // Need at least 2 amounts (transaction + balance)
        if (amounts.size < 2) return Pair(null, false)

        // First amount is the transaction, last is the balance
        val transactionAmount = amounts[0]

        // Get the transaction amount
        val amount = parseGermanAmount(transactionAmount.value) ?: return Pair(null, false)

        // Determine if it's expense based on transaction type keywords
        val upperLine = line.uppercase()
        val isDebit = isExpenseTransaction(upperLine)

        return Pair(amount, isDebit)
    }

    /**
     * Determine if transaction is an expense based on type keywords
     */
    private fun isExpenseTransaction(line: String): Boolean {
        // Income keywords - if present, it's NOT an expense
        val incomeKeywords = listOf(
            "GUTSCHRIFT", "LOHN", "GEHALT", "RENTE", "EINZAHLUNG",
            "UMBUCHUNG HABEN",  // HABEN = credit side
            "EINGANG"
        )

        // If line contains income keyword, it's NOT an expense
        for (keyword in incomeKeywords) {
            if (line.contains(keyword)) {
                return false  // It's income, not expense
            }
        }

        // Otherwise it's an expense (LASTSCHRIFT, ÜBERWEISUNG, KREDITRATE, etc.)
        return true
    }

    /**
     * Extract transaction type from TARGOBANK description
     */
    private fun extractTargobankTransactionType(line: String): String {
        val types = listOf(
            "ECHTZEITÜBERWEISUNG INLAND", "ECHTZEITÜBERWEISUNG",
            "AUSFÜHRUNG DAUERAUFTRAG", "LÖSCHUNG DAUERAUFTRAG", "ÄNDERUNG DAUERAUFTRAG",
            "INTERNE UMBUCHUNG HABEN", "INTERNE UMBUCHUNG SOLL", "INTERNE UMBUCHUNG",
            "SEPA LASTSCHRIFT", "SEPA ÜBERWEISUNG", "SEPA GUTSCHRIFT",
            "LOHN / GEHALT / RENTE", "LOHN/GEHALT/RENTE",
            "NFC KARTENZAHLUNG", "KARTENZAHLUNG", "RESERV. BETRAG POS",
            "DAUERAUFTRAG", "GUTSCHRIFT", "LASTSCHRIFT", "ÜBERWEISUNG",
            "KREDITRATE", "BARGELDAUSZAHLUNG", "BARGELDEINZAHLUNG",
            "GEHALT", "LOHN", "MIETE", "ANFANGSSALDO", "ABSCHLUSS"
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
        if (line.startsWith("BIC:") || line.startsWith("BIC :")) return false
        if (line.startsWith("IBAN:") || line.startsWith("IBAN ")) return false
        if (line.contains("BIC:") && line.contains("IBAN:")) return false
        if (line.contains("BIC ") && line.contains("IBAN")) return false
        if (Regex("""^\d{5,}$""").matches(line)) return false // Just numbers
        if (Regex("""^[A-Z0-9]{8,}$""").matches(line)) return false // Reference numbers
        if (line.startsWith("KREDIT ") && line.contains("PER")) return false
        if (line.startsWith("AUFTRAGSNUMMER")) return false
        if (line.startsWith("KundenRef:") || line.startsWith("Kundenreferenz:")) return false
        if (line.startsWith("Gläubiger-ID")) return false
        if (line.startsWith("Mandat:")) return false
        if (line.startsWith("Wertstellung")) return false

        // Skip timestamp lines like "AM 26.11.2024 UM 10.52" or "AM 01.11.2024 UM 21.32.21"
        if (Regex("""^AM\s+\d{2}\.\d{2}\.\d{4}\s+UM\s+\d{2}[.:]\d{2}""", RegexOption.IGNORE_CASE).containsMatchIn(line)) return false

        // Skip date range lines like "01.12.2024 BIS 31.12.9999"
        if (Regex("""^\d{2}\.\d{2}\.\d{4}\s+BIS\s+\d{2}\.\d{2}""", RegexOption.IGNORE_CASE).containsMatchIn(line)) return false

        // Skip lines with multiple amounts like "150,00/150,00/150,00 EUR"
        if (Regex("""\d+,\d{2}/\d+,\d{2}""").containsMatchIn(line)) return false

        // Skip lines that are just "SPAREN" or similar short keywords at end of descriptions
        if (line.uppercase() == "SPAREN") return false

        // Skip lines starting with "monatlich" (monthly schedule info)
        if (line.lowercase().startsWith("monatlich")) return false

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
