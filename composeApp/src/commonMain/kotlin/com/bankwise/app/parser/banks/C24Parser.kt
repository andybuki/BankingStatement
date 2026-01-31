package com.bankwise.app.parser.banks

import com.bankwise.app.parser.ParseResult
import com.bankwise.app.parser.ParsedTransaction
import kotlinx.datetime.LocalDate

/**
 * Parser for C24 Bank statements
 *
 * Format:
 * - Header: "Transaktionsübersicht"
 * - Columns: Buchung | Valuta | Transaktionsinformation | Betrag
 * - Date format: DD.MM.  DD.MM. (booking/value date, short format)
 * - Amount format: Prefix sign + Euro symbol: "- 32,00 €" or "+ 2.114,25 €"
 * - Multi-line descriptions with continuation lines
 */
class C24Parser : GermanBankParser() {
    override val bankName = "C24 Bank"

    // CERTAIN: unique identifiers
    private val certainIdentifiers = listOf(
        "c24 bank",
        "c24bank",
        "c24-bank"
    )

    // HIGH: strong indicators
    private val highIdentifiers = listOf(
        "check24",
        "c24 smart",
        "c24 plus"
    )

    // MEDIUM: patterns in C24 statements
    private val mediumIdentifiers = listOf(
        "transaktionsübersicht",
        "transaktionsinformation"
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return certainIdentifiers.any { lower.contains(it) } ||
               highIdentifiers.any { lower.contains(it) } ||
               (lower.contains("transaktionsübersicht") && lower.contains("buchung") && lower.contains("valuta"))
    }

    override fun getConfidence(pdfText: String): Pair<DetectionConfidence, List<String>> {
        return calculateConfidence(pdfText, certainIdentifiers, highIdentifiers, mediumIdentifiers)
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        try {
            val lines = pdfText.lines()
            val accountIban = extractIban(pdfText)
            val statementPeriod = extractStatementPeriod(pdfText)

            // Extract year from any full date in the document
            val year = extractYearFromText(pdfText)

            // Try C24 specific format
            var transactions = parseC24Format(lines, year)

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
                    errorMessage = "Could not extract transactions from C24 Bank PDF"
                )
            }
        } catch (e: Exception) {
            return ParseResult(
                success = false,
                bankName = bankName,
                errorMessage = "Error parsing C24 Bank PDF: ${e.message}"
            )
        }
    }

    /**
     * Extract year from any full date in text (DD.MM.YYYY)
     */
    private fun extractYearFromText(text: String): Int {
        val fullDatePattern = Regex("""\d{2}\.\d{2}\.(\d{4})""")
        val match = fullDatePattern.find(text)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 2024
    }

    /**
     * Parse C24 format:
     *
     * 30.10.    30.10.    Online-Kartenzahlung                    - 32,00 €
     *                     Lieferando.de lieferse
     *
     * 29.10.    29.10.    Überweisung                             + 2.114,25 €
     *                     Media-Saturn-Holding GmbH
     *                     Lohngehalt Gerald Laschke
     */
    private fun parseC24Format(lines: List<String>, year: Int): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()

        // Pattern for transaction start: DD.MM.  DD.MM.  Description  +/- AMOUNT €
        val datePairPattern = Regex("""^(\d{2}\.\d{2}\.)\s+(\d{2}\.\d{2}\.)\s+(.*)""")

        // Pattern for amount with prefix sign and Euro symbol: "- 32,00 €" or "+ 2.114,25 €"
        // Must have non-digit before amount to avoid matching dates
        val amountEuroPattern = Regex("""([+-])\s*(\d{1,3}(?:\.\d{3})*,\d{2})\s*€\s*$""")

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            if (line.isEmpty() || isC24HeaderLine(line)) {
                i++
                continue
            }

            // Check for transaction line starting with date pair
            val datePairMatch = datePairPattern.find(line)
            if (datePairMatch != null) {
                val bookingDateStr = datePairMatch.groupValues[1]
                val valueDateStr = datePairMatch.groupValues[2]
                val restOfLine = datePairMatch.groupValues[3].trim()

                val bookingDate = parseShortDateWithYear(bookingDateStr, year)
                val valueDate = parseShortDateWithYear(valueDateStr, year)

                if (bookingDate != null) {
                    // Check if amount is on same line
                    val amountMatch = amountEuroPattern.find(line)
                    var amount: Double? = null
                    var isDebit = false
                    var descriptionOnFirstLine = restOfLine

                    if (amountMatch != null) {
                        val sign = amountMatch.groupValues[1]
                        val amountStr = amountMatch.groupValues[2]
                        amount = parseGermanAmount(amountStr)
                        isDebit = sign == "-"

                        // Description is text before the amount
                        descriptionOnFirstLine = amountEuroPattern.replace(restOfLine, "").trim()
                    }

                    // Collect description lines
                    val descriptionParts = mutableListOf<String>()
                    if (descriptionOnFirstLine.isNotBlank()) {
                        descriptionParts.add(descriptionOnFirstLine)
                    }

                    var j = i + 1

                    // Look for continuation lines
                    while (j < lines.size && j < i + 15) {
                        val nextLine = lines[j].trim()

                        if (nextLine.isEmpty()) {
                            j++
                            continue
                        }

                        // Stop at next transaction (starts with date pair)
                        if (datePairPattern.containsMatchIn(nextLine)) {
                            break
                        }

                        // Stop at header/footer lines
                        if (isC24HeaderLine(nextLine)) {
                            break
                        }

                        // Check if this continuation line has an amount (if we don't have one yet)
                        if (amount == null) {
                            val nextAmountMatch = amountEuroPattern.find(nextLine)
                            if (nextAmountMatch != null) {
                                val sign = nextAmountMatch.groupValues[1]
                                val amountStr = nextAmountMatch.groupValues[2]
                                amount = parseGermanAmount(amountStr)
                                isDebit = sign == "-"

                                // Add text before amount to description
                                val textBeforeAmount = amountEuroPattern.replace(nextLine, "").trim()
                                if (textBeforeAmount.isNotBlank()) {
                                    descriptionParts.add(textBeforeAmount)
                                }
                                j++
                                continue
                            }
                        }

                        // This is a continuation line (description only)
                        descriptionParts.add(nextLine)
                        j++
                    }

                    if (amount != null) {
                        val finalAmount = if (isDebit) -kotlin.math.abs(amount) else kotlin.math.abs(amount)

                        val fullDescription = descriptionParts.joinToString(" ")
                            .replace(Regex("""\s+"""), " ")
                            .trim()

                        if (fullDescription.isNotBlank() && !isBalanceEntry(fullDescription, fullDescription)) {
                            transactions.add(
                                ParsedTransaction(
                                    bookingDate = bookingDate,
                                    valueDate = valueDate ?: bookingDate,
                                    amount = finalAmount,
                                    currency = "EUR",
                                    description = fullDescription,
                                    counterpartyName = extractC24Counterparty(fullDescription),
                                    transactionType = detectTransactionType(fullDescription),
                                    rawText = descriptionParts.joinToString("\n")
                                )
                            )
                        }
                    }

                    i = j
                    continue
                }
            }

            i++
        }

        return transactions.distinctBy { "${it.bookingDate}_${it.amount}_${it.description.take(30)}" }
    }

    /**
     * Parse short date (DD.MM.) with given year
     */
    private fun parseShortDateWithYear(dateStr: String, year: Int): LocalDate? {
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
     * Check if line is a C24 header/footer line
     */
    private fun isC24HeaderLine(line: String): Boolean {
        val lower = line.lowercase()
        return lower.contains("transaktionsübersicht") ||
               lower.contains("buchung") && lower.contains("valuta") && lower.contains("betrag") ||
               lower.contains("kontostand") ||
               lower.contains("saldo") ||
               lower.contains("summe") ||
               lower.contains("übertrag") ||
               lower.contains("seite") && lower.contains("von") ||
               lower.startsWith("iban") ||
               lower.startsWith("bic") ||
               lower.contains("c24 bank") ||
               lower.contains("check24") ||
               isHeaderOrFooter(line)
    }

    /**
     * Extract counterparty from C24 transaction description
     */
    private fun extractC24Counterparty(description: String): String? {
        // Transaction types to skip at start
        val txTypes = listOf(
            "Online-Kartenzahlung", "Kartenzahlung", "Überweisung", "Lastschrift",
            "Gutschrift", "Dauerauftrag", "SEPA-Lastschrift", "SEPA-Überweisung",
            "Bargeldauszahlung", "Einzahlung"
        )

        var cleanDesc = description
        for (txType in txTypes) {
            if (cleanDesc.startsWith(txType, ignoreCase = true)) {
                cleanDesc = cleanDesc.substring(txType.length).trim()
                break
            }
        }

        // Stop at technical markers
        val techMarkers = listOf("IBAN:", "BIC:", "Aktenzeichen", "Lohngehalt", "//")
        for (marker in techMarkers) {
            val idx = cleanDesc.indexOf(marker, ignoreCase = true)
            if (idx > 3) {
                cleanDesc = cleanDesc.substring(0, idx).trim()
                break
            }
        }

        // Take first meaningful segment (often the merchant/counterparty name)
        val segments = cleanDesc.split(Regex("""\s{2,}"""))
        if (segments.isNotEmpty() && segments[0].length > 2) {
            return segments[0].trim().take(60)
        }

        cleanDesc = cleanDesc.trimEnd(',', '.', '/', ' ')

        if (cleanDesc.length > 60) {
            val spaceIdx = cleanDesc.indexOf(' ', 40)
            if (spaceIdx > 0) {
                cleanDesc = cleanDesc.substring(0, spaceIdx)
            }
        }

        return cleanDesc.takeIf { it.length > 2 }
    }
}
