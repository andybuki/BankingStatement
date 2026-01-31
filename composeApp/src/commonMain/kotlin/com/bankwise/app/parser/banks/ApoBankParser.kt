package com.bankwise.app.parser.banks

import com.bankwise.app.parser.ParseResult
import com.bankwise.app.parser.ParsedTransaction
import kotlinx.datetime.LocalDate

/**
 * Parser for apoBank (Deutsche Apotheker- und Ärztebank) statements
 *
 * Format:
 * Buchungsdatum    Beschreibung                                    Soll        Haben
 * 02.05.2023       Wertstellung: 28.04.2023                                    33,50
 *                  Kartenzahlung Debitkarte; DM FIL 2453...
 *
 * - Booking date at start of first line
 * - "Wertstellung: DD.MM.YYYY" contains value date
 * - Amount in Soll (debit) or Haben (credit) column
 * - Multi-line descriptions
 */
class ApoBankParser : GermanBankParser() {
    override val bankName = "apoBank"

    // CERTAIN: unique identifiers
    private val certainIdentifiers = listOf(
        "apobank",
        "apo bank",
        "apo-bank",
        "daaededd",         // BIC
        "daaededdxxx",      // BIC full
        "bank der gesundheit",
        "deutsche apotheker- und ärztebank"
    )

    // HIGH: strong indicators
    private val highIdentifiers = listOf(
        "apotheker",
        "ärztebank",
        "deutsche apotheker",
        "300 606 01",       // BLZ
        "30060601"          // BLZ without spaces
    )

    // MEDIUM: common patterns in apoBank statements
    private val mediumIdentifiers = listOf(
        "wertstellung:",
        "kartenzahlung debitkarte",
        "buchungsdatum",
        "beschreibung",
        "übertrag",
        "saldo in eur",
        "kunden-nr",
        "konto-nr",
        "referenz-nr"
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return certainIdentifiers.any { lower.contains(it) } ||
               (highIdentifiers.count { lower.contains(it) } >= 1 &&
                mediumIdentifiers.count { lower.contains(it) } >= 2)
    }

    override fun getConfidence(pdfText: String): Pair<DetectionConfidence, List<String>> {
        return calculateConfidence(pdfText, certainIdentifiers, highIdentifiers, mediumIdentifiers)
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        try {
            val lines = pdfText.lines()
            val accountIban = extractIban(pdfText)
            val statementPeriod = extractApoBankPeriod(pdfText)

            // Try apoBank specific format first
            var transactions = parseApoBankFormat(lines)

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
                    errorMessage = "Could not extract transactions from apoBank PDF"
                )
            }
        } catch (e: Exception) {
            return ParseResult(
                success = false,
                bankName = bankName,
                errorMessage = "Error parsing apoBank PDF: ${e.message}"
            )
        }
    }

    /**
     * Extract statement period from apoBank header
     */
    private fun extractApoBankPeriod(text: String): String? {
        // Look for "Datum DD.MM.YYYY" in header
        val datePattern = Regex("""Datum\s+(\d{2}\.\d{2}\.\d{4})""", RegexOption.IGNORE_CASE)
        val match = datePattern.find(text)
        return match?.groupValues?.get(1)
    }

    /**
     * Parse apoBank format:
     * DD.MM.YYYY    Wertstellung: DD.MM.YYYY    [Amount in Soll or Haben]
     *               Description continuation...
     */
    private fun parseApoBankFormat(lines: List<String>): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()

        // Pattern for line starting with booking date
        val bookingDatePattern = Regex("""^(\d{2}\.\d{2}\.\d{4})\s+(.*)$""")

        // Pattern for Wertstellung (value date)
        val wertstellungPattern = Regex("""Wertstellung:\s*(\d{2}\.\d{2}\.\d{4})""", RegexOption.IGNORE_CASE)

        // Amount pattern: number with optional thousand separator and comma decimal
        // Can be at end of line (Soll or Haben column)
        val amountPattern = Regex("""(\d{1,3}(?:\.\d{3})*,\d{2})\s*$""")

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            // Skip empty lines and headers
            if (line.isEmpty() || isApoBankHeaderLine(line)) {
                i++
                continue
            }

            // Try to match a line starting with booking date
            val bookingMatch = bookingDatePattern.find(line)
            if (bookingMatch != null) {
                val bookingDateStr = bookingMatch.groupValues[1]
                val restOfLine = bookingMatch.groupValues[2].trim()

                // Check for Wertstellung in this line
                val wertstellungMatch = wertstellungPattern.find(restOfLine)
                var valueDateStr = bookingDateStr
                if (wertstellungMatch != null) {
                    valueDateStr = wertstellungMatch.groupValues[1]
                }

                // Check for amount in this line
                val amountMatch = amountPattern.find(restOfLine)
                var amount: Double? = null
                var isDebit = false

                if (amountMatch != null) {
                    amount = parseApoBankAmount(amountMatch.groupValues[1])
                    // Determine if Soll (debit) or Haben (credit) based on position
                    // In apoBank format, amounts appear at specific column positions
                    // For now, we'll collect description and determine later
                }

                // Collect description lines
                val descriptionParts = mutableListOf<String>()

                // Add rest of first line (without date and amount)
                var firstLineDesc = restOfLine
                if (wertstellungMatch != null) {
                    firstLineDesc = firstLineDesc.replace(wertstellungMatch.value, "").trim()
                }
                if (amountMatch != null) {
                    firstLineDesc = firstLineDesc.replace(amountMatch.value, "").trim()
                }
                if (firstLineDesc.isNotEmpty()) {
                    descriptionParts.add(firstLineDesc)
                }

                var j = i + 1
                while (j < lines.size) {
                    val nextLine = lines[j].trim()

                    if (nextLine.isEmpty()) {
                        j++
                        continue
                    }

                    // Stop at next transaction (starts with date)
                    if (bookingDatePattern.containsMatchIn(nextLine)) {
                        break
                    }

                    // Stop at header lines
                    if (isApoBankHeaderLine(nextLine)) {
                        break
                    }

                    // Check if this line has an amount (might be on separate line)
                    val nextAmountMatch = amountPattern.find(nextLine)
                    if (nextAmountMatch != null && amount == null) {
                        amount = parseApoBankAmount(nextAmountMatch.groupValues[1])
                        val beforeAmount = nextLine.substringBefore(nextAmountMatch.value).trim()
                        if (beforeAmount.isNotEmpty()) {
                            descriptionParts.add(beforeAmount)
                        }
                    } else {
                        descriptionParts.add(nextLine)
                    }
                    j++
                }

                // Parse dates
                val bookingDate = parseGermanDate(bookingDateStr)
                val valueDate = parseGermanDate(valueDateStr)

                if (bookingDate != null && amount != null) {
                    val fullDescription = descriptionParts.joinToString(" ").trim()

                    // Determine if debit or credit from description keywords
                    val finalAmount = determineAmountSign(fullDescription, amount)

                    // Extract counterparty from description
                    val counterparty = extractApoBankCounterparty(fullDescription)

                    transactions.add(
                        ParsedTransaction(
                            bookingDate = bookingDate,
                            valueDate = valueDate ?: bookingDate,
                            amount = finalAmount,
                            currency = "EUR",
                            description = fullDescription,
                            counterpartyName = counterparty.ifEmpty { null },
                            transactionType = extractApoBankTransactionType(fullDescription),
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
     * Parse apoBank amount (German format)
     */
    private fun parseApoBankAmount(amountStr: String): Double? {
        return try {
            val normalized = amountStr
                .replace(".", "")      // Remove thousand separators
                .replace(",", ".")     // Convert decimal comma to dot
            normalized.toDoubleOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Determine amount sign based on transaction type
     * In apoBank, Soll (debit) is negative, Haben (credit) is positive
     */
    private fun determineAmountSign(description: String, amount: Double): Double {
        val lower = description.lowercase()

        // Income indicators (Haben/credit - positive)
        val incomeKeywords = listOf(
            "gutschrift", "eingang", "gehalt", "lohn", "rente",
            "überweisung von", "einzahlung", "erstattung"
        )

        // Expense indicators (Soll/debit - negative)
        val expenseKeywords = listOf(
            "kartenzahlung", "lastschrift", "abbuchung", "überweisung an",
            "auszahlung", "gebühr", "entgelt", "debitkarte", "girocard"
        )

        for (keyword in incomeKeywords) {
            if (lower.contains(keyword)) {
                return kotlin.math.abs(amount)
            }
        }

        for (keyword in expenseKeywords) {
            if (lower.contains(keyword)) {
                return -kotlin.math.abs(amount)
            }
        }

        // Default to negative (expense) if unknown
        return -kotlin.math.abs(amount)
    }

    /**
     * Check if line is an apoBank header line
     */
    private fun isApoBankHeaderLine(line: String): Boolean {
        val lower = line.lowercase()
        return lower.contains("buchungsdatum") && lower.contains("beschreibung") ||
               lower.contains("soll") && lower.contains("haben") ||
               lower.contains("saldo in eur") ||
               lower.contains("übertrag") ||
               lower.contains("kunden-nr") ||
               lower.contains("konto-nr") ||
               lower.contains("referenz-nr") ||
               lower.contains("apobank") && lower.contains("bank der gesundheit") ||
               lower.contains("deutsche apotheker")
    }

    /**
     * Extract counterparty from apoBank description
     */
    private fun extractApoBankCounterparty(description: String): String {
        // Pattern: "Kartenzahlung Debitkarte; MERCHANT_NAME//LOCATION/DE"
        val merchantPattern = Regex("""(?:Debitkarte|girocard)[;,]\s*(.+?)//""", RegexOption.IGNORE_CASE)
        val merchantMatch = merchantPattern.find(description)
        if (merchantMatch != null) {
            return merchantMatch.groupValues[1].trim()
        }

        // Pattern for SEPA transfers
        val sepaPattern = Regex("""(?:Überweisung|Lastschrift)\s+(?:von|an)\s+(.+?)(?:;|$)""", RegexOption.IGNORE_CASE)
        val sepaMatch = sepaPattern.find(description)
        if (sepaMatch != null) {
            return sepaMatch.groupValues[1].trim()
        }

        return ""
    }

    /**
     * Extract transaction type from description
     */
    private fun extractApoBankTransactionType(description: String): String {
        val lower = description.lowercase()
        return when {
            lower.contains("kartenzahlung") || lower.contains("debitkarte") -> "Kartenzahlung"
            lower.contains("lastschrift") -> "Lastschrift"
            lower.contains("überweisung") -> "Überweisung"
            lower.contains("gehalt") || lower.contains("lohn") -> "Gehalt"
            lower.contains("gutschrift") -> "Gutschrift"
            lower.contains("auszahlung") -> "Bargeldauszahlung"
            else -> "Sonstige"
        }
    }
}
