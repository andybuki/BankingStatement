package com.bankwise.app.parser.banks

import com.bankwise.app.parser.ParseResult
import com.bankwise.app.parser.ParsedTransaction
import kotlinx.datetime.LocalDate

/**
 * Parser for Sparda Bank statements (SpardaGiro Online)
 *
 * Format:
 * Buchungstag    Buchungstext                                    Wertstellung    Betrag in EUR
 * 05.03.2019     37060590 GAA 13.36 KARTE 100050621...          05.03.2019      -200,00
 *
 * Multi-line descriptions with IBAN+, BIC+, SVWZ+, EREF+, MREF+, CRED+
 * Amount format: -200,00 or 75,00 (German: comma decimal, minus prefix for negative)
 */
class SpardaBankParser : GermanBankParser() {
    override val bankName = "Sparda-Bank"

    // CERTAIN: unique identifiers
    private val certainIdentifiers = listOf(
        "spardagiro",
        "sparda-bank",
        "sparda bank"
    )

    // HIGH: strong indicators (BIC prefixes for various Sparda banks)
    private val highIdentifiers = listOf(
        "genodef1s",    // BIC prefix for Sparda banks
        "genodef1p",    // Sparda-Bank Südwest
        "genodef1m",    // Sparda-Bank München
        "genoded1spk",  // Related
        "sparda"
    )

    // MEDIUM: common patterns in Sparda statements
    private val mediumIdentifiers = listOf(
        "buchungstag",
        "buchungstext",
        "wertstellung",
        "betrag in eur",
        "kontoauszug nr",
        "sepa-überweisung",
        "sepa-basislastschrift",
        "sepa-lohn/gehalt"
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
            val statementPeriod = extractSpardaPeriod(pdfText)

            // Try Sparda specific format first
            var transactions = parseSpardaFormat(lines)

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
                    errorMessage = "Could not extract transactions from Sparda-Bank PDF"
                )
            }
        } catch (e: Exception) {
            return ParseResult(
                success = false,
                bankName = bankName,
                errorMessage = "Error parsing Sparda-Bank PDF: ${e.message}"
            )
        }
    }

    /**
     * Extract statement period from Sparda header
     * Format: "Kontoauszug Nr. 3/2019"
     */
    private fun extractSpardaPeriod(text: String): String? {
        val periodPattern = Regex("""Kontoauszug\s+Nr\.?\s*(\d+/\d{4})""", RegexOption.IGNORE_CASE)
        val match = periodPattern.find(text)
        return match?.groupValues?.get(1)
    }

    /**
     * Parse Sparda format:
     * DD.MM.YYYY    Description text...    DD.MM.YYYY    Amount
     * (continuation lines for description)
     */
    private fun parseSpardaFormat(lines: List<String>): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()

        // Amount pattern: optional minus, digits with optional thousand separator, comma, 2 decimals
        // Examples: -200,00  75,00  -1.234,56
        // Handle Unicode minus (−) and ASCII minus (-)
        val amountPattern = Regex("""[−-]?\d{1,3}(?:\.\d{3})*,\d{2}$""")

        // Date pattern DD.MM.YYYY
        val datePattern = Regex("""^(\d{2}\.\d{2}\.\d{4})""")

        // Full line pattern: BookingDate ... ValueDate ... Amount
        val fullLinePattern = Regex("""^(\d{2}\.\d{2}\.\d{4})\s+(.+?)\s+(\d{2}\.\d{2}\.\d{4})\s+([−-]?\d{1,3}(?:\.\d{3})*,\d{2})$""")

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            // Skip empty lines and headers
            if (line.isEmpty() || isSpardaHeaderLine(line)) {
                i++
                continue
            }

            // Try to match a full transaction line
            val fullMatch = fullLinePattern.find(line)
            if (fullMatch != null) {
                val bookingDateStr = fullMatch.groupValues[1]
                val description = fullMatch.groupValues[2].trim()
                val valueDateStr = fullMatch.groupValues[3]
                val amountStr = fullMatch.groupValues[4]

                val bookingDate = parseGermanDate(bookingDateStr)
                val valueDate = parseGermanDate(valueDateStr)
                val amount = parseSpardaAmount(amountStr)

                if (bookingDate != null && amount != null) {
                    // Collect continuation lines
                    val descriptionParts = mutableListOf(description)
                    var j = i + 1

                    while (j < lines.size) {
                        val nextLine = lines[j].trim()

                        if (nextLine.isEmpty()) {
                            j++
                            continue
                        }

                        // Stop at next transaction (starts with date and ends with amount)
                        if (fullLinePattern.containsMatchIn(nextLine)) {
                            break
                        }

                        // Stop at header lines
                        if (isSpardaHeaderLine(nextLine)) {
                            break
                        }

                        // Stop if line starts with a date (might be new transaction)
                        if (datePattern.containsMatchIn(nextLine) && amountPattern.containsMatchIn(nextLine)) {
                            break
                        }

                        // This is a continuation line
                        descriptionParts.add(nextLine)
                        j++
                    }

                    val fullDescription = descriptionParts.joinToString(" ").trim()
                    val (counterparty, cleanDescription, counterpartyIban) = extractSpardaDetails(fullDescription)

                    transactions.add(
                        ParsedTransaction(
                            bookingDate = bookingDate,
                            valueDate = valueDate ?: bookingDate,
                            amount = amount,
                            currency = "EUR",
                            description = cleanDescription.ifEmpty { fullDescription },
                            counterpartyName = counterparty.ifEmpty { null },
                            counterpartyIban = counterpartyIban,
                            transactionType = extractSpardaTransactionType(fullDescription),
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
     * Parse Sparda amount string (German format with possible Unicode minus)
     */
    private fun parseSpardaAmount(amountStr: String): Double? {
        return try {
            val normalized = amountStr
                .replace("−", "-")     // Convert Unicode minus to ASCII
                .replace(".", "")      // Remove thousand separators
                .replace(",", ".")     // Convert decimal comma to dot
            normalized.toDoubleOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if line is a Sparda header line
     */
    private fun isSpardaHeaderLine(line: String): Boolean {
        val lower = line.lowercase()
        return lower.contains("buchungstag") && lower.contains("buchungstext") ||
               lower.contains("wertstellung") && lower.contains("betrag") ||
               lower.contains("kontostand") ||
               lower.contains("spardagiro") ||
               lower.contains("limit") && lower.contains("pa") ||
               lower.contains("iban:") && lower.contains("de")
    }

    /**
     * Extract counterparty, description, and IBAN from Sparda description
     */
    private fun extractSpardaDetails(description: String): Triple<String, String, String?> {
        var counterparty = ""
        var cleanDesc = description
        var counterpartyIban: String? = null

        // Extract IBAN if present (IBAN+ pattern)
        val ibanMatch = Regex("""IBAN\+?\s*([A-Z]{2}\d{2}[A-Z0-9]{10,30})""", RegexOption.IGNORE_CASE).find(description)
        if (ibanMatch != null) {
            counterpartyIban = ibanMatch.groupValues[1]
        }

        // Extract SVWZ+ (Verwendungszweck) for description
        val svwzMatch = Regex("""SVWZ\+\s*(.+?)(?=\s+(?:EREF|MREF|CRED|IBAN|BIC|$))""", RegexOption.IGNORE_CASE).find(description)
        if (svwzMatch != null) {
            cleanDesc = svwzMatch.groupValues[1].trim()
        }

        // Try to extract counterparty name
        // Pattern 1: Name before SEPA-type
        val nameBeforeSepa = Regex("""^([A-ZÄÖÜa-zäöüß\s\-\.]+?)\s+SEPA-""", RegexOption.IGNORE_CASE).find(description)
        if (nameBeforeSepa != null) {
            counterparty = nameBeforeSepa.groupValues[1].trim()
        }

        // Pattern 2: For GAA (ATM) transactions
        if (description.contains("GAA", ignoreCase = true)) {
            val gaaMatch = Regex("""GAA[−-]?(\w+)""", RegexOption.IGNORE_CASE).find(description)
            if (gaaMatch != null) {
                counterparty = "ATM ${gaaMatch.groupValues[1]}"
            }
        }

        // Pattern 3: MASTERCARD
        if (description.contains("MASTERCARD", ignoreCase = true)) {
            counterparty = "Mastercard"
        }

        // Pattern 4: For SEPA-LOHN/GEHALT - company name is at the beginning
        if (description.contains("SEPA-LOHN/GEHALT", ignoreCase = true)) {
            val salaryMatch = Regex("""^(.+?)\s+SEPA-LOHN/GEHALT""", RegexOption.IGNORE_CASE).find(description)
            if (salaryMatch != null) {
                counterparty = salaryMatch.groupValues[1].trim()
            }
        }

        return Triple(counterparty, cleanDesc, counterpartyIban)
    }

    /**
     * Extract transaction type from description
     */
    private fun extractSpardaTransactionType(description: String): String {
        return when {
            description.contains("GAA", ignoreCase = true) -> "Bargeldauszahlung"
            description.contains("SEPA-ÜBERWEISUNG", ignoreCase = true) -> "SEPA-Überweisung"
            description.contains("SEPA-BASISLASTSCHRIFT", ignoreCase = true) -> "SEPA-Lastschrift"
            description.contains("SEPA-LOHN/GEHALT", ignoreCase = true) -> "Gehalt"
            description.contains("MASTERCARD", ignoreCase = true) -> "Kartenzahlung"
            description.contains("KARTE", ignoreCase = true) -> "Kartenzahlung"
            else -> "Sonstige"
        }
    }
}
