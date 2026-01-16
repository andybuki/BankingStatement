package com.banking.statement.parser.banks

import com.banking.statement.parser.ParseResult
import com.banking.statement.parser.ParsedTransaction
import kotlinx.datetime.LocalDate

/**
 * Parser for bunq Bank statements (Dutch neobank)
 *
 * Format: Table with columns Datum | Zinsdatum | Gegenpartei | Beschreibung | Betrag
 * Date format: YYYY-MM-DD (ISO format)
 * Amount format: +€ 150.00 or -€ 23.12 (dot as decimal separator)
 */
class BunqParser : GermanBankParser() {
    override val bankName = "bunq"

    // CERTAIN: unique identifiers
    private val certainIdentifiers = listOf(
        "bunqnl82",           // BIC
        "bunq b.v.",
        "www.bunq.com"
    )

    // HIGH: strong indicators
    private val highIdentifiers = listOf(
        "bunq",
        "naritaweg 131-133",  // bunq address
        "1043bs amsterdam"
    )

    // MEDIUM: common patterns
    private val mediumIdentifiers = listOf(
        "zinsdatum",
        "gegenpartei"
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()

        // Check for certain identifiers
        if (certainIdentifiers.any { lower.contains(it) }) {
            return true
        }

        // Check for bunq IBAN pattern
        val hasBunqIban = Regex("""nl\d{2}bunq""", RegexOption.IGNORE_CASE).containsMatchIn(lower)
        if (hasBunqIban && lower.contains("bunq")) {
            return true
        }

        // Check for multiple high confidence identifiers
        val matchCount = highIdentifiers.count { lower.contains(it) }
        return matchCount >= 2
    }

    override fun getConfidence(pdfText: String): Pair<DetectionConfidence, List<String>> {
        return calculateConfidence(pdfText, certainIdentifiers, highIdentifiers, mediumIdentifiers)
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        try {
            val lines = pdfText.lines()
            val accountIban = extractIban(pdfText)
            val statementPeriod = extractBunqPeriod(pdfText)

            val transactions = parseBunqFormat(lines)

            return if (transactions.isNotEmpty()) {
                ParseResult(
                    success = true,
                    bankName = bankName,
                    accountIban = accountIban,
                    statementPeriod = statementPeriod,
                    transactions = transactions
                )
            } else {
                // Fallback to generic parser
                val genericTransactions = parseComprehensiveFormat(lines)
                if (genericTransactions.isNotEmpty()) {
                    ParseResult(
                        success = true,
                        bankName = bankName,
                        accountIban = accountIban,
                        statementPeriod = statementPeriod,
                        transactions = genericTransactions
                    )
                } else {
                    ParseResult(
                        success = false,
                        bankName = bankName,
                        errorMessage = "Could not extract transactions from bunq PDF"
                    )
                }
            }
        } catch (e: Exception) {
            return ParseResult(
                success = false,
                bankName = bankName,
                errorMessage = "Error parsing bunq PDF: ${e.message}"
            )
        }
    }

    /**
     * Extract statement period from bunq header
     * Format: "Kontostand per YYYY-MM-DD"
     */
    private fun extractBunqPeriod(text: String): String? {
        val periodPattern = Regex("""Kontostand\s+per\s+(\d{4}-\d{2}-\d{2}):\s+.+\nKontostand\s+per\s+(\d{4}-\d{2}-\d{2}):""")
        val match = periodPattern.find(text)
        if (match != null) {
            val startDate = convertIsoToGermanDate(match.groupValues[1])
            val endDate = convertIsoToGermanDate(match.groupValues[2])
            return "$startDate - $endDate"
        }

        // Alternative: just find any period dates
        val singleDatePattern = Regex("""Kontostand\s+per\s+(\d{4}-\d{2}-\d{2})""")
        val dates = singleDatePattern.findAll(text).map { it.groupValues[1] }.toList()
        if (dates.size >= 2) {
            val startDate = convertIsoToGermanDate(dates.first())
            val endDate = convertIsoToGermanDate(dates.last())
            return "$startDate - $endDate"
        }

        return null
    }

    /**
     * Parse bunq format:
     * Datum | Zinsdatum | Gegenpartei | Beschreibung | Betrag
     * 2024-01-07 | 2024-01-07 | J.A.N. Hagner NL60BUNQ... | | +€ 150.00
     */
    private fun parseBunqFormat(lines: List<String>): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()

        // Amount pattern: +€ or -€ followed by amount with dot decimal
        val amountPattern = Regex("""([+-])€\s*(\d{1,3}(?:[.,]\d{3})*[.,]\d{2})""")

        // ISO date pattern at start of line
        val datePattern = Regex("""^(\d{4}-\d{2}-\d{2})\s+(\d{4}-\d{2}-\d{2})?\s*(.*)$""")

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            // Skip empty lines and headers
            if (line.isEmpty() || isBunqHeaderLine(line)) {
                i++
                continue
            }

            // Try to match a line starting with ISO date
            val dateMatch = datePattern.find(line)
            if (dateMatch != null) {
                val bookingDateStr = dateMatch.groupValues[1]  // YYYY-MM-DD
                val valueDateStr = dateMatch.groupValues[2].ifEmpty { bookingDateStr }  // YYYY-MM-DD
                var restOfLine = dateMatch.groupValues[3].trim()

                // Look for amount in the rest of the line or following lines
                var amountMatch = amountPattern.find(restOfLine)
                val descriptionLines = mutableListOf<String>()

                if (amountMatch == null) {
                    // Amount might be on the same line but we need to search further
                    val fullLineAmount = amountPattern.find(line)
                    if (fullLineAmount != null) {
                        amountMatch = fullLineAmount
                        restOfLine = line.substringAfter(valueDateStr).substringBefore(fullLineAmount.value).trim()
                    }
                }

                // If still no amount, look in following lines
                var j = i + 1
                while (amountMatch == null && j < lines.size && j < i + 5) {
                    val nextLine = lines[j].trim()
                    if (nextLine.isEmpty()) {
                        j++
                        continue
                    }

                    // Check if next line starts with a new date (new transaction)
                    if (Regex("""^\d{4}-\d{2}-\d{2}""").containsMatchIn(nextLine)) {
                        break
                    }

                    amountMatch = amountPattern.find(nextLine)
                    if (amountMatch != null) {
                        val beforeAmount = nextLine.substringBefore(amountMatch.value).trim()
                        if (beforeAmount.isNotEmpty()) {
                            descriptionLines.add(beforeAmount)
                        }
                    } else {
                        descriptionLines.add(nextLine)
                    }
                    j++
                }

                if (amountMatch != null) {
                    val sign = amountMatch.groupValues[1]
                    // bunq uses DOT as decimal separator (e.g., +€ 150.00)
                    // and COMMA as thousand separator (e.g., +€ 1,000.00)
                    val amountStr = amountMatch.groupValues[2]
                        .replace(",", "")  // Remove thousand separators only

                    val amount = amountStr.toDoubleOrNull() ?: 0.0
                    val finalAmount = if (sign == "-") -amount else amount

                    // Parse dates
                    val bookingDate = parseIsoDate(bookingDateStr)
                    val valueDate = parseIsoDate(valueDateStr) ?: bookingDate

                    if (bookingDate != null) {
                        // Extract counterparty from rest of line
                        val beforeAmount = if (amountPattern.containsMatchIn(restOfLine)) {
                            restOfLine.substringBefore(amountMatch.value).trim()
                        } else {
                            restOfLine
                        }

                        val (counterparty, description) = extractBunqCounterpartyAndDescription(
                            beforeAmount,
                            descriptionLines
                        )

                        transactions.add(
                            ParsedTransaction(
                                bookingDate = bookingDate,
                                valueDate = valueDate,
                                amount = finalAmount,
                                currency = "EUR",
                                description = description.ifEmpty { counterparty },
                                counterpartyName = counterparty,
                                transactionType = if (finalAmount >= 0) "Gutschrift" else "Lastschrift",
                                rawText = line + descriptionLines.joinToString("\n", prefix = "\n")
                            )
                        )

                        i = j
                        continue
                    }
                }
            }

            i++
        }

        return transactions.distinctBy { "${it.bookingDate}_${it.amount}_${it.counterpartyName?.take(20)}" }
    }

    /**
     * Parse ISO date (YYYY-MM-DD) to LocalDate
     */
    private fun parseIsoDate(dateStr: String): LocalDate? {
        return try {
            val parts = dateStr.split("-")
            if (parts.size == 3) {
                LocalDate(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Convert ISO date to German format
     */
    private fun convertIsoToGermanDate(isoDate: String): String {
        val parts = isoDate.split("-")
        return if (parts.size == 3) {
            "${parts[2]}.${parts[1]}.${parts[0]}"
        } else {
            isoDate
        }
    }

    /**
     * Check if line is a bunq header line
     */
    private fun isBunqHeaderLine(line: String): Boolean {
        val lower = line.lowercase()
        return lower.contains("datum") && lower.contains("zinsdatum") ||
               lower.contains("gegenpartei") && lower.contains("betrag") ||
               lower.contains("kontoinhaber") ||
               lower.contains("bankangaben") ||
               lower.contains("bankverbindung") ||
               lower.contains("kontostand per") ||
               lower.contains("summe eingehende") ||
               lower.contains("summe ausgehende") ||
               lower.contains("downloaddatum")
    }

    /**
     * Extract counterparty and description from bunq format
     */
    private fun extractBunqCounterpartyAndDescription(
        mainText: String,
        additionalLines: List<String>
    ): Pair<String, String> {
        // bunq format: Counterparty name possibly with IBAN below
        // Split on multiple spaces (column separator)
        val parts = mainText.split(Regex("""\s{2,}"""))

        val counterpartyPart = if (parts.isNotEmpty()) parts[0].trim() else mainText.trim()
        val descriptionPart = if (parts.size > 1) parts.drop(1).joinToString(" ").trim() else ""

        // Clean counterparty - remove IBAN if present
        val cleanCounterparty = cleanBunqCounterparty(counterpartyPart)

        // Build full description
        val fullDescription = buildString {
            if (descriptionPart.isNotEmpty()) {
                append(descriptionPart)
            }
            for (line in additionalLines) {
                val cleanLine = cleanBunqCounterparty(line)
                if (cleanLine.isNotEmpty() && !cleanLine.equals(cleanCounterparty, ignoreCase = true)) {
                    if (isNotEmpty()) append(" ")
                    append(cleanLine)
                }
            }
        }

        return Pair(cleanCounterparty, fullDescription)
    }

    /**
     * Clean bunq counterparty name - remove IBAN patterns
     */
    private fun cleanBunqCounterparty(text: String): String {
        var cleaned = text.trim()

        // Remove IBAN patterns
        cleaned = cleaned.replace(Regex("""[A-Z]{2}\d{2}[A-Z0-9]{4,}"""), "").trim()

        // Remove trailing country codes
        cleaned = cleaned.replace(Regex("""\s+[A-Z]{2}$"""), "").trim()

        return cleaned
    }
}
