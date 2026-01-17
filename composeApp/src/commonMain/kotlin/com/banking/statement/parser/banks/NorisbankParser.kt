package com.banking.statement.parser.banks

import com.banking.statement.parser.ParseResult
import com.banking.statement.parser.ParsedTransaction
import kotlinx.datetime.LocalDate

/**
 * Parser for Norisbank statements
 *
 * Format:
 * Kontoauszug vom DD.MM.YYYY bis DD.MM.YYYY
 * Kontoinhaber: Name
 *
 * Buchung  Valuta   Vorgang                                    Soll        Haben
 * 01.03.   01.03.   Bargeldauszahlung GAA                     - 440,00
 *                   Verwendungszweck/ Kundenreferenz
 *                   Description lines...
 *
 * Amount format: - 440,00 (Soll) or + 150,00 (Haben)
 * Date format: DD.MM. (short format, year from header)
 */
class NorisbankParser : GermanBankParser() {
    override val bankName = "Norisbank"

    // CERTAIN: unique identifiers
    private val certainIdentifiers = listOf(
        "norisbank",
        "norsde51",      // BIC
        "norsde51xxx"    // BIC full
    )

    // HIGH: strong indicators
    private val highIdentifiers = listOf(
        "noris bank",
        "deutsche bank gruppe"  // Norisbank is part of Deutsche Bank
    )

    // MEDIUM: common patterns in Norisbank statements
    private val mediumIdentifiers = listOf(
        "kontoauszug vom",
        "kontoinhaber:",
        "alter saldo per",
        "verwendungszweck/ kundenreferenz",
        "bargeldauszahlung gaa"
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
            val statementPeriod = extractNorisbankPeriod(pdfText)
            val year = extractYearFromPeriod(pdfText)

            // Try Norisbank specific format first
            var transactions = parseNorisbankFormat(lines, year)

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
                    errorMessage = "Could not extract transactions from Norisbank PDF"
                )
            }
        } catch (e: Exception) {
            return ParseResult(
                success = false,
                bankName = bankName,
                errorMessage = "Error parsing Norisbank PDF: ${e.message}"
            )
        }
    }

    /**
     * Extract statement period from Norisbank header
     * Format: "Kontoauszug vom 27.02.2021 bis 31.03.2021"
     */
    private fun extractNorisbankPeriod(text: String): String? {
        val periodPattern = Regex("""Kontoauszug\s+vom\s+(\d{2}\.\d{2}\.\d{4})\s+bis\s+(\d{2}\.\d{2}\.\d{4})""", RegexOption.IGNORE_CASE)
        val match = periodPattern.find(text)
        return match?.let { "${it.groupValues[1]} - ${it.groupValues[2]}" }
    }

    /**
     * Extract year from statement period
     */
    private fun extractYearFromPeriod(text: String): Int {
        val periodPattern = Regex("""Kontoauszug\s+vom\s+\d{2}\.\d{2}\.(\d{4})""", RegexOption.IGNORE_CASE)
        val match = periodPattern.find(text)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: java.time.LocalDate.now().year
    }

    /**
     * Parse Norisbank format:
     * DD.MM.   DD.MM.   Description    Amount (in Soll or Haben column)
     */
    private fun parseNorisbankFormat(lines: List<String>, year: Int): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()

        // Pattern for transaction line starting with short date
        // DD.MM.   DD.MM.   Description   [- Amount] [+ Amount]
        val transactionPattern = Regex("""^(\d{2}\.\d{2}\.)\s+(\d{2}\.\d{2}\.)\s+(.+?)(?:\s+([+−-])\s*(\d{1,3}(?:\.\d{3})*,\d{2}))?$""")

        // Amount pattern for lines that have amount at the end
        val amountPattern = Regex("""([+−-])\s*(\d{1,3}(?:\.\d{3})*,\d{2})\s*$""")

        // Short date pattern
        val shortDatePattern = Regex("""^(\d{2}\.\d{2}\.)\s+(\d{2}\.\d{2}\.)\s+(.*)$""")

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            // Skip empty lines and headers
            if (line.isEmpty() || isNorisbankHeaderLine(line)) {
                i++
                continue
            }

            // Try to match a transaction line
            val dateMatch = shortDatePattern.find(line)
            if (dateMatch != null) {
                val bookingDateStr = dateMatch.groupValues[1]
                val valueDateStr = dateMatch.groupValues[2]
                var restOfLine = dateMatch.groupValues[3].trim()

                // Check for amount in this line
                var amount: Double? = null
                val amountMatch = amountPattern.find(restOfLine)
                if (amountMatch != null) {
                    val sign = amountMatch.groupValues[1]
                    val amountStr = amountMatch.groupValues[2]
                    amount = parseNorisbankAmount(sign, amountStr)
                    restOfLine = restOfLine.substringBefore(amountMatch.value).trim()
                }

                // Collect description lines
                val descriptionParts = mutableListOf(restOfLine)
                var j = i + 1

                while (j < lines.size) {
                    val nextLine = lines[j].trim()

                    if (nextLine.isEmpty()) {
                        j++
                        continue
                    }

                    // Stop at next transaction (starts with DD.MM.)
                    if (shortDatePattern.containsMatchIn(nextLine)) {
                        break
                    }

                    // Stop at header lines
                    if (isNorisbankHeaderLine(nextLine)) {
                        break
                    }

                    // Check if this line has an amount (continuation with amount)
                    val nextAmountMatch = amountPattern.find(nextLine)
                    if (nextAmountMatch != null && amount == null) {
                        val sign = nextAmountMatch.groupValues[1]
                        val amountStr = nextAmountMatch.groupValues[2]
                        amount = parseNorisbankAmount(sign, amountStr)
                        val beforeAmount = nextLine.substringBefore(nextAmountMatch.value).trim()
                        if (beforeAmount.isNotEmpty()) {
                            descriptionParts.add(beforeAmount)
                        }
                    } else {
                        // Regular description continuation
                        descriptionParts.add(nextLine)
                    }
                    j++
                }

                // Parse dates with year
                val bookingDate = parseShortDate(bookingDateStr, year)
                val valueDate = parseShortDate(valueDateStr, year)

                if (bookingDate != null && amount != null) {
                    val fullDescription = descriptionParts.joinToString(" ").trim()
                    val (counterparty, cleanDescription) = extractNorisbankDetails(fullDescription)

                    transactions.add(
                        ParsedTransaction(
                            bookingDate = bookingDate,
                            valueDate = valueDate ?: bookingDate,
                            amount = amount,
                            currency = "EUR",
                            description = cleanDescription.ifEmpty { fullDescription },
                            counterpartyName = counterparty.ifEmpty { null },
                            transactionType = extractNorisbankTransactionType(fullDescription),
                            rawText = descriptionParts.joinToString("\n")
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
     * Parse short date (DD.MM.) with year
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
     * Parse Norisbank amount (sign separate from amount)
     */
    private fun parseNorisbankAmount(sign: String, amountStr: String): Double? {
        return try {
            val normalized = amountStr
                .replace(".", "")      // Remove thousand separators
                .replace(",", ".")     // Convert decimal comma to dot
            val value = normalized.toDoubleOrNull() ?: return null

            // Apply sign (- or − for negative, + for positive)
            if (sign == "-" || sign == "−") -value else value
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if line is a Norisbank header line
     */
    private fun isNorisbankHeaderLine(line: String): Boolean {
        val lower = line.lowercase()
        return lower.contains("buchung") && lower.contains("valuta") ||
               lower.contains("vorgang") && (lower.contains("soll") || lower.contains("haben")) ||
               lower.contains("kontoauszug vom") ||
               lower.contains("kontoinhaber:") ||
               lower.contains("alter saldo") ||
               lower.contains("neuer saldo") ||
               lower.contains("auszug") && lower.contains("seite") && lower.contains("iban")
    }

    /**
     * Extract counterparty and description from Norisbank description
     */
    private fun extractNorisbankDetails(description: String): Pair<String, String> {
        var counterparty = ""
        var cleanDesc = description

        // Remove "Verwendungszweck/ Kundenreferenz" prefix
        cleanDesc = cleanDesc.replace(Regex("""Verwendungszweck/?\s*Kundenreferenz\s*""", RegexOption.IGNORE_CASE), "").trim()

        // For "SEPA Überweisung von NAME" pattern
        val sepaVonMatch = Regex("""SEPA\s+Überweisung\s+von\s+(.+?)(?:\s+Verwendungszweck|$)""", RegexOption.IGNORE_CASE).find(description)
        if (sepaVonMatch != null) {
            counterparty = sepaVonMatch.groupValues[1].trim()
        }

        // For "SEPA Überweisung an NAME" pattern
        val sepaAnMatch = Regex("""SEPA\s+Überweisung\s+an\s+(.+?)(?:\s+Verwendungszweck|$)""", RegexOption.IGNORE_CASE).find(description)
        if (sepaAnMatch != null) {
            counterparty = sepaAnMatch.groupValues[1].trim()
        }

        // For "Bargeldauszahlung GAA" pattern - extract location
        if (description.contains("Bargeldauszahlung GAA", ignoreCase = true)) {
            val gaaMatch = Regex("""Postbank//(.+?)/DE""", RegexOption.IGNORE_CASE).find(description)
            if (gaaMatch != null) {
                counterparty = "ATM ${gaaMatch.groupValues[1]}"
            } else {
                counterparty = "ATM"
            }
        }

        // For "SEPA Lastschrift" pattern
        val lastschriftMatch = Regex("""SEPA[−-]?Lastschrift\s+von\s+(.+?)(?:\s+Verwendungszweck|$)""", RegexOption.IGNORE_CASE).find(description)
        if (lastschriftMatch != null) {
            counterparty = lastschriftMatch.groupValues[1].trim()
        }

        return Pair(counterparty, cleanDesc)
    }

    /**
     * Extract transaction type from description
     */
    private fun extractNorisbankTransactionType(description: String): String {
        return when {
            description.contains("Bargeldauszahlung", ignoreCase = true) -> "Bargeldauszahlung"
            description.contains("SEPA Überweisung", ignoreCase = true) -> "SEPA-Überweisung"
            description.contains("SEPA-Lastschrift", ignoreCase = true) ||
            description.contains("SEPA Lastschrift", ignoreCase = true) -> "SEPA-Lastschrift"
            description.contains("Dauerauftrag", ignoreCase = true) -> "Dauerauftrag"
            description.contains("Gehalt", ignoreCase = true) ||
            description.contains("Lohn", ignoreCase = true) -> "Gehalt"
            description.contains("Kartenzahlung", ignoreCase = true) -> "Kartenzahlung"
            else -> "Sonstige"
        }
    }
}
