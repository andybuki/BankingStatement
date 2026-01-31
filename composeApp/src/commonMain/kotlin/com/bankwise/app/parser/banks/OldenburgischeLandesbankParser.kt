package com.bankwise.app.parser.banks

import com.bankwise.app.parser.ParseResult
import com.bankwise.app.parser.ParsedTransaction
import kotlinx.datetime.LocalDate

/**
 * Parser for Oldenburgische Landesbank AG (OLB) statements
 *
 * Format:
 * Buchung  Wert
 * 03.08.18 03.08. GELDAUTOMAT                                    50,00-
 *                 03.08/18.46UHR Freren AmNeu
 *                 enM8
 *
 * - Date format: DD.MM.YY (short year) for booking, DD.MM. for value date
 * - Amount format: 50,00- or 291,33+ (trailing sign!)
 * - Multi-line descriptions
 */
class OldenburgischeLandesbankParser : GermanBankParser() {
    override val bankName = "Oldenburgische Landesbank"

    // CERTAIN: unique identifiers
    private val certainIdentifiers = listOf(
        "oldenburgische landesbank",
        "olbodeh2",         // BIC
        "olbodeh2xxx",      // BIC full
        "olb ag"
    )

    // HIGH: strong indicators
    private val highIdentifiers = listOf(
        "landesbank ag",
        "49809 lingen",     // Bank address
        "0591 80013"        // Phone number
    )

    // MEDIUM: common patterns in OLB statements
    private val mediumIdentifiers = listOf(
        "euro-konto",
        "alter saldo",
        "neuer saldo",
        "buchung",
        "wert",
        "geldautomat",
        "ueberwsg",
        "lfd.lastsch",
        "gutschrift"
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return certainIdentifiers.any { lower.contains(it) } ||
               (lower.contains("oldenburgische") && lower.contains("landesbank")) ||
               (highIdentifiers.count { lower.contains(it) } >= 1 &&
                mediumIdentifiers.count { lower.contains(it) } >= 3)
    }

    override fun getConfidence(pdfText: String): Pair<DetectionConfidence, List<String>> {
        val lower = pdfText.lowercase()
        val matchedIdentifiers = mutableListOf<String>()

        // Check for "oldenburgische" + "landesbank" pattern
        if (lower.contains("oldenburgische") && lower.contains("landesbank")) {
            matchedIdentifiers.add("Oldenburgische Landesbank")
            return Pair(DetectionConfidence.CERTAIN, matchedIdentifiers)
        }

        return calculateConfidence(pdfText, certainIdentifiers, highIdentifiers, mediumIdentifiers)
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        try {
            val lines = pdfText.lines()
            val accountIban = extractIban(pdfText)
            val statementPeriod = extractOLBPeriod(pdfText)
            val year = extractYearFromPeriod(pdfText)

            // Try OLB specific format first
            var transactions = parseOLBFormat(lines, year)

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
                    errorMessage = "Could not extract transactions from OLB PDF"
                )
            }
        } catch (e: Exception) {
            return ParseResult(
                success = false,
                bankName = bankName,
                errorMessage = "Error parsing OLB PDF: ${e.message}"
            )
        }
    }

    /**
     * Extract statement period from OLB header
     * Format: "vom 02.08.18 bis 31.08.18"
     */
    private fun extractOLBPeriod(text: String): String? {
        val periodPattern = Regex("""vom\s+(\d{2}\.\d{2}\.\d{2,4})\s+bis\s+(\d{2}\.\d{2}\.\d{2,4})""", RegexOption.IGNORE_CASE)
        val match = periodPattern.find(text)
        return match?.let { "${it.groupValues[1]} - ${it.groupValues[2]}" }
    }

    /**
     * Extract year from statement period (handles 2-digit year)
     */
    private fun extractYearFromPeriod(text: String): Int {
        val periodPattern = Regex("""vom\s+\d{2}\.\d{2}\.(\d{2,4})""", RegexOption.IGNORE_CASE)
        val match = periodPattern.find(text)
        val yearStr = match?.groupValues?.get(1) ?: return 2018

        return if (yearStr.length == 2) {
            val twoDigit = yearStr.toIntOrNull() ?: 18
            if (twoDigit > 50) 1900 + twoDigit else 2000 + twoDigit
        } else {
            yearStr.toIntOrNull() ?: 2018
        }
    }

    /**
     * Parse OLB format:
     * DD.MM.YY DD.MM. TRANSACTION_TYPE    AMOUNT[+/-]
     *                 Description lines...
     */
    private fun parseOLBFormat(lines: List<String>, defaultYear: Int): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()

        // Pattern for transaction line:
        // DD.MM.YY DD.MM. TYPE    AMOUNT+/-
        val transactionPattern = Regex(
            """^(\d{2}\.\d{2}\.\d{2})\s+(\d{2}\.\d{2}\.)\s+(\S+.*?)\s+(\d{1,3}(?:\.\d{3})*,\d{2})([+−-])\s*$"""
        )

        // Alternative pattern without trailing sign on same line
        val dateStartPattern = Regex("""^(\d{2}\.\d{2}\.\d{2})\s+(\d{2}\.\d{2}\.)\s+(.+)$""")

        // Amount with trailing sign pattern
        val amountPattern = Regex("""(\d{1,3}(?:\.\d{3})*,\d{2})([+−-])\s*$""")

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            // Skip empty lines and headers
            if (line.isEmpty() || isOLBHeaderLine(line)) {
                i++
                continue
            }

            // Try to match full transaction line
            val fullMatch = transactionPattern.find(line)
            if (fullMatch != null) {
                val bookingDateStr = fullMatch.groupValues[1]
                val valueDateStr = fullMatch.groupValues[2]
                val transactionType = fullMatch.groupValues[3].trim()
                val amountStr = fullMatch.groupValues[4]
                val sign = fullMatch.groupValues[5]

                val bookingDate = parseShortYearDate(bookingDateStr, defaultYear)
                val valueDate = parseValueDate(valueDateStr, bookingDate, defaultYear)
                val amount = parseOLBAmount(amountStr, sign)

                if (bookingDate != null && amount != null) {
                    // Collect description lines
                    val descriptionParts = mutableListOf<String>()
                    var j = i + 1

                    while (j < lines.size) {
                        val nextLine = lines[j].trim()

                        if (nextLine.isEmpty()) {
                            j++
                            continue
                        }

                        // Stop at next transaction
                        if (transactionPattern.containsMatchIn(nextLine) ||
                            dateStartPattern.containsMatchIn(nextLine)) {
                            break
                        }

                        // Stop at header lines
                        if (isOLBHeaderLine(nextLine)) {
                            break
                        }

                        descriptionParts.add(nextLine)
                        j++
                    }

                    val fullDescription = descriptionParts.joinToString(" ").trim()
                    val counterparty = extractOLBCounterparty(transactionType, fullDescription)

                    transactions.add(
                        ParsedTransaction(
                            bookingDate = bookingDate,
                            valueDate = valueDate ?: bookingDate,
                            amount = amount,
                            currency = "EUR",
                            description = fullDescription.ifEmpty { transactionType },
                            counterpartyName = counterparty.ifEmpty { null },
                            transactionType = extractOLBTransactionType(transactionType),
                            rawText = "$line\n${descriptionParts.joinToString("\n")}"
                        )
                    )

                    i = j
                    continue
                }
            } else {
                // Try alternative pattern (date at start, amount might be at end)
                val dateMatch = dateStartPattern.find(line)
                if (dateMatch != null) {
                    val bookingDateStr = dateMatch.groupValues[1]
                    val valueDateStr = dateMatch.groupValues[2]
                    var restOfLine = dateMatch.groupValues[3].trim()

                    // Check for amount at end
                    val amountMatch = amountPattern.find(restOfLine)
                    if (amountMatch != null) {
                        val amountStr = amountMatch.groupValues[1]
                        val sign = amountMatch.groupValues[2]
                        val transactionType = restOfLine.substringBefore(amountMatch.value).trim()

                        val bookingDate = parseShortYearDate(bookingDateStr, defaultYear)
                        val valueDate = parseValueDate(valueDateStr, bookingDate, defaultYear)
                        val amount = parseOLBAmount(amountStr, sign)

                        if (bookingDate != null && amount != null) {
                            // Collect description lines
                            val descriptionParts = mutableListOf<String>()
                            var j = i + 1

                            while (j < lines.size) {
                                val nextLine = lines[j].trim()

                                if (nextLine.isEmpty()) {
                                    j++
                                    continue
                                }

                                if (transactionPattern.containsMatchIn(nextLine) ||
                                    dateStartPattern.containsMatchIn(nextLine)) {
                                    break
                                }

                                if (isOLBHeaderLine(nextLine)) {
                                    break
                                }

                                descriptionParts.add(nextLine)
                                j++
                            }

                            val fullDescription = descriptionParts.joinToString(" ").trim()
                            val counterparty = extractOLBCounterparty(transactionType, fullDescription)

                            transactions.add(
                                ParsedTransaction(
                                    bookingDate = bookingDate,
                                    valueDate = valueDate ?: bookingDate,
                                    amount = amount,
                                    currency = "EUR",
                                    description = fullDescription.ifEmpty { transactionType },
                                    counterpartyName = counterparty.ifEmpty { null },
                                    transactionType = extractOLBTransactionType(transactionType),
                                    rawText = "$line\n${descriptionParts.joinToString("\n")}"
                                )
                            )

                            i = j
                            continue
                        }
                    }
                }
            }

            i++
        }

        return transactions.distinctBy { "${it.bookingDate}_${it.amount}_${it.counterpartyName?.take(20)}" }
    }

    /**
     * Parse short year date (DD.MM.YY)
     */
    private fun parseShortYearDate(dateStr: String, defaultYear: Int): LocalDate? {
        return try {
            val parts = dateStr.split(".")
            if (parts.size >= 3) {
                val day = parts[0].toInt()
                val month = parts[1].toInt()
                val yearPart = parts[2].toInt()
                val year = if (yearPart < 100) {
                    if (yearPart > 50) 1900 + yearPart else 2000 + yearPart
                } else {
                    yearPart
                }
                LocalDate(year, month, day)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse value date (DD.MM.) using booking date's year
     */
    private fun parseValueDate(dateStr: String, bookingDate: LocalDate?, defaultYear: Int): LocalDate? {
        return try {
            val cleanDate = dateStr.trimEnd('.')
            val parts = cleanDate.split(".")
            if (parts.size >= 2) {
                val day = parts[0].toInt()
                val month = parts[1].toInt()
                val year = bookingDate?.year ?: defaultYear
                LocalDate(year, month, day)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse OLB amount with trailing sign
     */
    private fun parseOLBAmount(amountStr: String, sign: String): Double? {
        return try {
            val normalized = amountStr
                .replace(".", "")      // Remove thousand separators
                .replace(",", ".")     // Convert decimal comma to dot
            val value = normalized.toDoubleOrNull() ?: return null

            // Apply sign (- or − for negative/debit, + for positive/credit)
            if (sign == "-" || sign == "−") -value else value
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if line is an OLB header line
     */
    private fun isOLBHeaderLine(line: String): Boolean {
        val lower = line.lowercase()
        return lower.contains("buchung") && lower.contains("wert") ||
               lower.contains("alter saldo") ||
               lower.contains("neuer saldo") ||
               lower.contains("euro-konto") ||
               lower.contains("oldenburgische landesbank") ||
               lower.startsWith("---") ||
               lower.contains("es betreut sie") ||
               lower.contains("auszug") && lower.contains("nr.")
    }

    /**
     * Extract counterparty from OLB description
     */
    private fun extractOLBCounterparty(transactionType: String, description: String): String {
        // For GELDAUTOMAT - location is in description
        if (transactionType.contains("GELDAUTOMAT", ignoreCase = true)) {
            val locationMatch = Regex("""UHR\s+(.+?)(?:\s|$)""").find(description)
            if (locationMatch != null) {
                return "ATM ${locationMatch.groupValues[1]}"
            }
            return "ATM"
        }

        // For UEBERWSG, LFD.LASTSCH., GUTSCHRIFT - first line of description is usually counterparty
        val lines = description.split(Regex("""\s{2,}"""))
        if (lines.isNotEmpty()) {
            val firstPart = lines[0].trim()
            // Skip if it looks like a BIC or IBAN
            if (!firstPart.matches(Regex("""[A-Z]{6,}[0-9A-Z]*""")) &&
                !firstPart.startsWith("DE") &&
                firstPart.length > 2) {
                return firstPart
            }
        }

        return ""
    }

    /**
     * Extract transaction type
     */
    private fun extractOLBTransactionType(type: String): String {
        val upper = type.uppercase()
        return when {
            upper.contains("GELDAUTOMAT") -> "Bargeldauszahlung"
            upper.contains("UEBERWSG") -> "Überweisung"
            upper.contains("LFD.LASTSCH") -> "Lastschrift"
            upper.contains("GUTSCHRIFT") -> "Gutschrift"
            upper.contains("LASTSCH") -> "Lastschrift"
            upper.contains("DAUERAUFTRAG") -> "Dauerauftrag"
            else -> type
        }
    }
}
