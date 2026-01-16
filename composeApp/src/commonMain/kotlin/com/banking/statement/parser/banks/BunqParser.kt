package com.banking.statement.parser.banks

import com.banking.statement.parser.BankStatementParser
import com.banking.statement.parser.ParseResult
import com.banking.statement.parser.Transaction

/**
 * Parser for bunq Bank statements (Dutch neobank)
 *
 * Format: Table with columns Datum | Zinsdatum | Gegenpartei | Beschreibung | Betrag
 * Date format: YYYY-MM-DD
 * Amount format: +€ 150.00 or -€ 23.12 (dot as decimal separator)
 */
class BunqParser : BankStatementParser {
    override val bankName = "bunq"

    // Confidence-based identifiers
    private val certainIdentifiers = listOf(
        "bunqnl82",           // BIC
        "bunq b.v.",
        "www.bunq.com"
    )

    private val highConfidenceIdentifiers = listOf(
        "bunq",
        "naritaweg 131-133",  // bunq address
        "1043bs amsterdam"
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()

        // Check for certain identifiers
        if (certainIdentifiers.any { lower.contains(it) }) {
            return true
        }

        // Check for high confidence identifiers with bunq IBAN pattern
        val hasBunqIban = lower.contains("bunq") && Regex("""nl\d{2}bunq""").containsMatchIn(lower)
        if (hasBunqIban) {
            return true
        }

        // Check for multiple high confidence identifiers
        val matchCount = highConfidenceIdentifiers.count { lower.contains(it) }
        return matchCount >= 2
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        val transactions = mutableListOf<Transaction>()
        val lines = pdfText.lines()

        // Extract year from statement - look for "Kontostand per YYYY-MM-DD" or dates in transactions
        val year = extractYear(pdfText)

        // bunq uses ISO date format: YYYY-MM-DD
        // Amount format: +€ 150.00 or -€ 23.12

        // Pattern for transaction lines starting with date
        val datePattern = Regex("""^(\d{4}-\d{2}-\d{2})\s+(\d{4}-\d{2}-\d{2})\s+(.+)$""")

        // Amount pattern: +€ or -€ followed by amount with dot decimal
        val amountPattern = Regex("""([+-])€\s*(\d{1,3}(?:[.,]\d{3})*[.,]\d{2})""")

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            // Skip empty lines and headers
            if (line.isEmpty() || line.startsWith("Datum") || line.contains("Zinsdatum") && line.contains("Gegenpartei")) {
                i++
                continue
            }

            // Try to match a transaction line starting with date
            val dateMatch = datePattern.find(line)
            if (dateMatch != null) {
                val bookingDateStr = dateMatch.groupValues[1]  // YYYY-MM-DD
                val valueDateStr = dateMatch.groupValues[2]    // YYYY-MM-DD (Zinsdatum)
                val restOfLine = dateMatch.groupValues[3]

                // Extract amount from the line
                val amountMatch = amountPattern.find(restOfLine)

                if (amountMatch != null) {
                    val sign = amountMatch.groupValues[1]
                    val amountStr = amountMatch.groupValues[2]
                        .replace(".", "")  // Remove thousand separators
                        .replace(",", ".") // Convert decimal comma to dot if present

                    val amount = amountStr.toDoubleOrNull() ?: 0.0
                    val finalAmount = if (sign == "-") -amount else amount

                    // Extract counterparty (everything before the amount)
                    val beforeAmount = restOfLine.substringBefore(amountMatch.value).trim()

                    // Parse counterparty and description
                    val (counterparty, description) = parseCounterpartyAndDescription(beforeAmount, lines, i)

                    // Convert date from YYYY-MM-DD to DD.MM.YYYY for consistency
                    val bookingDate = convertIsoDateToGerman(bookingDateStr)
                    val valueDate = convertIsoDateToGerman(valueDateStr)

                    transactions.add(
                        Transaction(
                            date = bookingDate,
                            valueDate = valueDate,
                            description = description.ifEmpty { counterparty },
                            amount = finalAmount,
                            counterparty = counterparty
                        )
                    )
                }
            } else {
                // Try alternative pattern: line might just have date and rest continues
                val simpleDatePattern = Regex("""^(\d{4}-\d{2}-\d{2})\s+(.*)$""")
                val simpleMatch = simpleDatePattern.find(line)

                if (simpleMatch != null) {
                    val dateStr = simpleMatch.groupValues[1]
                    var content = simpleMatch.groupValues[2]

                    // Look ahead for more content and amount
                    val descriptionLines = mutableListOf(content)
                    var j = i + 1
                    var foundAmount = false
                    var amountValue = 0.0

                    while (j < lines.size && j < i + 5) {
                        val nextLine = lines[j].trim()
                        if (nextLine.isEmpty()) {
                            j++
                            continue
                        }

                        // Check if this line has an amount
                        val nextAmountMatch = amountPattern.find(nextLine)
                        if (nextAmountMatch != null) {
                            val sign = nextAmountMatch.groupValues[1]
                            val amountStr = nextAmountMatch.groupValues[2]
                                .replace(".", "")
                                .replace(",", ".")
                            amountValue = amountStr.toDoubleOrNull() ?: 0.0
                            if (sign == "-") amountValue = -amountValue

                            val beforeAmt = nextLine.substringBefore(nextAmountMatch.value).trim()
                            if (beforeAmt.isNotEmpty()) {
                                descriptionLines.add(beforeAmt)
                            }
                            foundAmount = true
                            break
                        }

                        // Check if next line starts with a new date (new transaction)
                        if (simpleDatePattern.matches(nextLine) || datePattern.matches(nextLine)) {
                            break
                        }

                        descriptionLines.add(nextLine)
                        j++
                    }

                    if (foundAmount) {
                        val fullDescription = descriptionLines.joinToString(" ").trim()
                        val (counterparty, description) = extractCounterpartyFromDescription(fullDescription)
                        val bookingDate = convertIsoDateToGerman(dateStr)

                        transactions.add(
                            Transaction(
                                date = bookingDate,
                                valueDate = bookingDate,
                                description = description.ifEmpty { counterparty },
                                amount = amountValue,
                                counterparty = counterparty
                            )
                        )
                        i = j
                        continue
                    }
                }
            }

            i++
        }

        return ParseResult(
            bankName = "bunq",
            transactions = transactions,
            rawText = pdfText,
            fileName = fileName
        )
    }

    private fun extractYear(text: String): Int {
        // Look for "Kontostand per YYYY-MM-DD" pattern
        val kontostandPattern = Regex("""Kontostand\s+per\s+(\d{4})-\d{2}-\d{2}""")
        kontostandPattern.find(text)?.let {
            return it.groupValues[1].toIntOrNull() ?: 2024
        }

        // Look for any YYYY-MM-DD date
        val isoDatePattern = Regex("""(\d{4})-\d{2}-\d{2}""")
        isoDatePattern.find(text)?.let {
            return it.groupValues[1].toIntOrNull() ?: 2024
        }

        return 2024
    }

    private fun convertIsoDateToGerman(isoDate: String): String {
        // Convert YYYY-MM-DD to DD.MM.YYYY
        val parts = isoDate.split("-")
        return if (parts.size == 3) {
            "${parts[2]}.${parts[1]}.${parts[0]}"
        } else {
            isoDate
        }
    }

    private fun parseCounterpartyAndDescription(text: String, lines: List<String>, currentIndex: Int): Pair<String, String> {
        // bunq format often has: COUNTERPARTY IBAN on one part, description elsewhere
        // Or: COUNTERPARTY followed by description on same line

        val parts = text.split(Regex("""\s{2,}"""))  // Split on multiple spaces (column separator)

        return if (parts.size >= 2) {
            val counterpartyPart = parts[0].trim()
            val descriptionPart = parts.drop(1).joinToString(" ").trim()

            // Clean counterparty - remove IBAN if present on second line
            val cleanCounterparty = extractCleanCounterparty(counterpartyPart)

            Pair(cleanCounterparty, descriptionPart)
        } else {
            val cleanCounterparty = extractCleanCounterparty(text)
            Pair(cleanCounterparty, "")
        }
    }

    private fun extractCounterpartyFromDescription(text: String): Pair<String, String> {
        // Try to split counterparty from description
        val parts = text.split(Regex("""\s{2,}"""))

        return if (parts.size >= 2) {
            Pair(extractCleanCounterparty(parts[0]), parts.drop(1).joinToString(" "))
        } else {
            Pair(extractCleanCounterparty(text), "")
        }
    }

    private fun extractCleanCounterparty(text: String): String {
        // Remove IBAN patterns from counterparty
        val ibanPattern = Regex("""[A-Z]{2}\d{2}[A-Z0-9]{4,}""")
        var clean = text.replace(ibanPattern, "").trim()

        // Remove common suffixes
        clean = clean.replace(Regex("""\s+NL$"""), "")

        return clean.trim()
    }
}
