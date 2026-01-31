package com.bankwise.app.parser.banks

import com.bankwise.app.parser.ParseResult
import com.bankwise.app.parser.ParsedTransaction
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
        "norisbank gmbh",
        "norsde51",           // BIC
        "norsde51xxx",        // BIC full
        "nors de 51",         // BIC with spaces (OCR issue)
        "www.norisbank.de"
    )

    // HIGH: strong indicators
    private val highIdentifiers = listOf(
        "noris bank",
        "noris-bank",
        "deutsche bank gruppe",  // Norisbank is part of Deutsche Bank
        "(030) 310 66 005",      // Phone number
        "10910 berlin"           // Postfach address
    )

    // MEDIUM: common patterns in Norisbank statements
    private val mediumIdentifiers = listOf(
        "kontoauszug vom",
        "kontoinhaber:",
        "alter saldo per",
        "neuer saldo",
        "verwendungszweck/ kundenreferenz",
        "verwendungszweck/kundenreferenz",
        "bargeldauszahlung gaa",
        "buchung",
        "valuta",
        "vorgang",
        "soll",
        "haben"
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()

        // Check for certain identifiers
        if (certainIdentifiers.any { lower.contains(it) }) {
            return true
        }

        // Check for "noris" with nearby "bank" (handles spacing issues)
        if (lower.contains("noris") && lower.contains("bank")) {
            return true
        }

        // Check for BIC pattern NORSDE51 with possible spaces
        if (Regex("""nors\s*de\s*51""", RegexOption.IGNORE_CASE).containsMatchIn(lower)) {
            return true
        }

        // Check for high + medium identifiers
        return highIdentifiers.count { lower.contains(it) } >= 1 &&
               mediumIdentifiers.count { lower.contains(it) } >= 2
    }

    override fun getConfidence(pdfText: String): Pair<DetectionConfidence, List<String>> {
        val lower = pdfText.lowercase()
        val matchedIdentifiers = mutableListOf<String>()

        // Check for "noris" + "bank" pattern (handles spacing issues)
        if (lower.contains("noris") && lower.contains("bank")) {
            matchedIdentifiers.add("norisbank")
            return Pair(DetectionConfidence.CERTAIN, matchedIdentifiers)
        }

        // Check for BIC pattern with possible spaces
        if (Regex("""nors\s*de\s*51""", RegexOption.IGNORE_CASE).containsMatchIn(pdfText)) {
            matchedIdentifiers.add("NORSDE51")
            return Pair(DetectionConfidence.CERTAIN, matchedIdentifiers)
        }

        // Fall back to standard calculation
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
     * Parse Norisbank format (actual format from PDF extraction):
     *
     * TransactionType                    Amount
     * [CounterpartyName]
     * DD.MM. DD.MM.
     * Verwendungszweck/ Kundenreferenz
     * Description lines...
     *
     * The dates come AFTER the transaction type/amount line!
     */
    private fun parseNorisbankFormat(lines: List<String>, year: Int): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()

        // Amount pattern at end of line: [+/-] Amount (with space between sign and number)
        val amountPattern = Regex("""([+−-])\s*(\d{1,3}(?:\.\d{3})*,\d{2})\s*$""")

        // Short date pattern: DD.MM. DD.MM. (booking and value date)
        val shortDatePattern = Regex("""^(\d{2}\.\d{2}\.)\s+(\d{2}\.\d{2}\.)\s*$""")

        // Transaction type patterns (lines that start a new transaction)
        val transactionTypePatterns = listOf(
            "Bargeldauszahlung",
            "SEPA Überweisung",
            "SEPA Lastschrift",
            "SEPA-Lastschrift",
            "Kartenzahlung",
            "Dauerauftrag",
            "Verwendungszweck/ Kundenreferenz"  // Sometimes this line has amount
        )

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            // Skip empty lines and headers
            if (line.isEmpty() || isNorisbankHeaderLine(line)) {
                i++
                continue
            }

            // Check if this line has an amount at the end (potential transaction start)
            val amountMatch = amountPattern.find(line)
            if (amountMatch != null) {
                val sign = amountMatch.groupValues[1]
                val amountStr = amountMatch.groupValues[2]
                val amount = parseNorisbankAmount(sign, amountStr)

                if (amount != null) {
                    // Get transaction type from before the amount
                    val transactionType = line.substringBefore(amountMatch.value).trim()

                    // Skip if this is just a header or non-transaction line
                    if (transactionType.contains("Saldo", ignoreCase = true) ||
                        transactionType.contains("EUR", ignoreCase = true) && transactionType.length < 10) {
                        i++
                        continue
                    }

                    // Now look for the date line and collect description
                    var bookingDate: LocalDate? = null
                    var valueDate: LocalDate? = null
                    val descriptionParts = mutableListOf<String>()
                    var counterparty = ""

                    var j = i + 1
                    var foundDate = false

                    while (j < lines.size && j < i + 15) {
                        val nextLine = lines[j].trim()

                        if (nextLine.isEmpty()) {
                            j++
                            continue
                        }

                        // Check for date line
                        val dateMatch = shortDatePattern.find(nextLine)
                        if (dateMatch != null && !foundDate) {
                            bookingDate = parseShortDate(dateMatch.groupValues[1], year)
                            valueDate = parseShortDate(dateMatch.groupValues[2], year)
                            foundDate = true
                            j++
                            continue
                        }

                        // Check if we hit a new transaction (line with amount)
                        if (amountPattern.containsMatchIn(nextLine) && foundDate) {
                            break
                        }

                        // Check for header lines
                        if (isNorisbankHeaderLine(nextLine)) {
                            break
                        }

                        // Skip "Verwendungszweck/ Kundenreferenz" header
                        if (nextLine.equals("Verwendungszweck/ Kundenreferenz", ignoreCase = true)) {
                            j++
                            continue
                        }

                        // Before finding date - might be counterparty name
                        if (!foundDate && !nextLine.startsWith("Verwendungszweck", ignoreCase = true)) {
                            if (counterparty.isEmpty()) {
                                counterparty = nextLine
                            }
                        } else if (foundDate) {
                            // After date - description lines
                            descriptionParts.add(nextLine)
                        }

                        j++
                    }

                    // Create transaction if we found a date
                    if (bookingDate != null) {
                        val fullDescription = descriptionParts.joinToString(" ").trim()

                        // Extract counterparty if not set and available from transaction type
                        if (counterparty.isEmpty()) {
                            counterparty = extractCounterpartyFromType(transactionType, fullDescription)
                        }

                        transactions.add(
                            ParsedTransaction(
                                bookingDate = bookingDate,
                                valueDate = valueDate ?: bookingDate,
                                amount = amount,
                                currency = "EUR",
                                description = fullDescription.ifEmpty { transactionType },
                                counterpartyName = counterparty.ifEmpty { null },
                                transactionType = extractNorisbankTransactionType(transactionType),
                                rawText = "$transactionType\n${descriptionParts.joinToString("\n")}"
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
     * Extract counterparty from transaction type or description
     */
    private fun extractCounterpartyFromType(transactionType: String, description: String): String {
        // For "SEPA Überweisung von NAME" pattern
        val vonMatch = Regex("""von\s*$""", RegexOption.IGNORE_CASE).find(transactionType)
        if (vonMatch != null) {
            // Counterparty should be on next line, already captured
            return ""
        }

        // For ATM transactions
        if (transactionType.contains("Bargeldauszahlung", ignoreCase = true) ||
            transactionType.contains("GAA", ignoreCase = true)) {
            val locationMatch = Regex("""//(.+?)/DE""").find(description)
            if (locationMatch != null) {
                return "ATM ${locationMatch.groupValues[1]}"
            }
            return "ATM"
        }

        // For Kartenzahlung - extract merchant from description
        if (transactionType.contains("Kartenzahlung", ignoreCase = true)) {
            val merchantMatch = Regex("""^(.+?)//""").find(description)
            if (merchantMatch != null) {
                return merchantMatch.groupValues[1].trim()
            }
        }

        return ""
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
