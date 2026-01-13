package com.banking.statement.parser.banks

import com.banking.statement.parser.ParseResult
import com.banking.statement.parser.ParsedTransaction
import kotlinx.datetime.LocalDate

/**
 * Parser for Volksbank/VR-Bank/Raiffeisenbank statements
 *
 * Format:
 * - Header: Bu-Tag | Wert | Vorgang | Amount
 * - Date format: DD.MM.  DD.MM. (booking date, value date - space separated, short format)
 * - Amount format: AMOUNT S (Soll/debit) or AMOUNT H (Haben/credit)
 *   - Example: "38,99 S" = debit, "919,70 H" = credit
 * - Multi-line descriptions with continuation lines
 * - Balance: "alter Kontostand vom DD.MM.YYYY"
 */
class VolksbankParser : GermanBankParser() {
    override val bankName = "Volksbank"

    // CERTAIN: unique identifiers
    private val certainIdentifiers = listOf(
        "genoded",      // BIC prefix for Genobanks
        "genode",       // BIC variant
        "vr-bank",
        "vr bank"
    )

    // HIGH: strong indicators
    private val highIdentifiers = listOf(
        "volksbank",
        "raiffeisenbank",
        "genossenschaftsbank",
        "volks- und raiffeisenbank"
    )

    // MEDIUM: patterns in Volksbank statements
    private val mediumIdentifiers = listOf(
        "bu-tag",
        "kartenzahlung girocard",
        "basislastschrift pn:",
        "pn:931"  // Common card reference
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return certainIdentifiers.any { lower.contains(it) } ||
               highIdentifiers.any { lower.contains(it) } ||
               (lower.contains("bu-tag") && lower.contains("wert") && lower.contains("vorgang"))
    }

    override fun getConfidence(pdfText: String): Pair<DetectionConfidence, List<String>> {
        return calculateConfidence(pdfText, certainIdentifiers, highIdentifiers, mediumIdentifiers)
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        try {
            val lines = pdfText.lines()
            val accountIban = extractIban(pdfText)
            val statementPeriod = extractStatementPeriod(pdfText)

            // Extract year from "alter Kontostand vom DD.MM.YYYY" or statement period
            val year = extractYearFromText(pdfText)

            // Try Volksbank specific format
            var transactions = parseVolksbankFormat(lines, year)

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
                    errorMessage = "Could not extract transactions from Volksbank PDF"
                )
            }
        } catch (e: Exception) {
            return ParseResult(
                success = false,
                bankName = bankName,
                errorMessage = "Error parsing Volksbank PDF: ${e.message}"
            )
        }
    }

    /**
     * Extract year from statement text (from dates like "30.06.2023" or "vom DD.MM.YYYY")
     */
    private fun extractYearFromText(text: String): Int {
        // Try to find year in "alter Kontostand vom DD.MM.YYYY" or any full date
        val fullDatePattern = Regex("""\d{2}\.\d{2}\.(\d{4})""")
        val match = fullDatePattern.find(text)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 2024
    }

    /**
     * Parse Volksbank format:
     *
     * 03.07.  03.07.  Kartenzahlung girocard PN:931                     38,99 S
     *                 KAUFLAND
     *                 Kaufland Doebeln/Doebeln/DE
     *                 30.06.2023 um 16:50:24 Uhr 61340678/036842/ECTL/
     *
     * 31.07.  31.07.  UEBERWEISUNG PN:931                              919,70 H
     *                 Bundesagentur fuer Arbeit-Service-Haus
     */
    private fun parseVolksbankFormat(lines: List<String>, year: Int): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()

        // Pattern for transaction start: DD.MM.  DD.MM.  Description  AMOUNT S/H
        // Date pair pattern: DD.MM. followed by space(s) and another DD.MM.
        val datePairPattern = Regex("""^(\d{2}\.\d{2}\.)\s+(\d{2}\.\d{2}\.)\s+(.*)""")

        // Pattern for amount with S/H suffix at end of line: "38,99 S" or "919,70 H"
        val amountSHPattern = Regex("""(\d{1,3}(?:[.\s]\d{3})*,\d{2})\s*([SH])\s*$""")

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            if (line.isEmpty() || isVolksbankHeaderLine(line)) {
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
                    val amountMatch = amountSHPattern.find(line)
                    var amount: Double? = null
                    var isDebit = false
                    var descriptionOnFirstLine = restOfLine

                    if (amountMatch != null) {
                        val amountStr = amountMatch.groupValues[1].replace(" ", "")
                        amount = parseGermanAmount(amountStr)
                        isDebit = amountMatch.groupValues[2] == "S"

                        // Description is text before the amount
                        descriptionOnFirstLine = amountSHPattern.replace(restOfLine, "").trim()
                    }

                    // Collect description lines
                    val descriptionParts = mutableListOf<String>()
                    if (descriptionOnFirstLine.isNotBlank()) {
                        descriptionParts.add(descriptionOnFirstLine)
                    }

                    var j = i + 1

                    // Look for continuation lines
                    while (j < lines.size && j < i + 20) {
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
                        if (isVolksbankHeaderLine(nextLine)) {
                            break
                        }

                        // Check if this continuation line has an amount (if we don't have one yet)
                        if (amount == null) {
                            val nextAmountMatch = amountSHPattern.find(nextLine)
                            if (nextAmountMatch != null) {
                                val amountStr = nextAmountMatch.groupValues[1].replace(" ", "")
                                amount = parseGermanAmount(amountStr)
                                isDebit = nextAmountMatch.groupValues[2] == "S"

                                // Add text before amount to description
                                val textBeforeAmount = amountSHPattern.replace(nextLine, "").trim()
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
                                    counterpartyName = extractVolksbankCounterparty(fullDescription),
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
     * Check if line is a Volksbank header/footer line
     */
    private fun isVolksbankHeaderLine(line: String): Boolean {
        val lower = line.lowercase()
        return lower.contains("bu-tag") && lower.contains("wert") ||
               lower.contains("alter kontostand") ||
               lower.contains("neuer kontostand") ||
               lower.contains("kontostand") ||
               lower.contains("summe") ||
               lower.contains("übertrag") ||
               lower.contains("seite") && lower.contains("von") ||
               lower.startsWith("iban") ||
               lower.startsWith("bic") ||
               lower.contains("kontonummer") ||
               lower.contains("bankleitzahl") ||
               lower.contains("blz") ||
               isHeaderOrFooter(line)
    }

    /**
     * Extract counterparty from Volksbank transaction description
     */
    private fun extractVolksbankCounterparty(description: String): String? {
        // Transaction types to skip at start
        val txTypes = listOf(
            "Kartenzahlung girocard", "Kartenzahlung", "UEBERWEISUNG", "Überweisung",
            "Lastschrift", "Gutschrift", "Dauerauftrag", "SEPA-Lastschrift",
            "SEPA-Überweisung", "Basislastschrift", "SDD Lastschr",
            "PN:931", "PN:"
        )

        var cleanDesc = description
        for (txType in txTypes) {
            if (cleanDesc.startsWith(txType, ignoreCase = true)) {
                cleanDesc = cleanDesc.substring(txType.length).trim()
            }
            // Also remove if found with PN: pattern
            val pnPattern = Regex("""PN:\d+\s*""", RegexOption.IGNORE_CASE)
            cleanDesc = pnPattern.replace(cleanDesc, "").trim()
        }

        // The counterparty is often on the next part (e.g., "KAUFLAND" or "Bundesagentur...")
        // Stop at technical markers
        val techMarkers = listOf("/DE", "//", " um ", "Uhr", "ECTL", "REF ", "Einreicher")
        for (marker in techMarkers) {
            val idx = cleanDesc.indexOf(marker, ignoreCase = true)
            if (idx > 3) {
                cleanDesc = cleanDesc.substring(0, idx).trim()
                break
            }
        }

        // Take first meaningful segment
        val lines = cleanDesc.split(Regex("""\s{2,}"""))
        if (lines.isNotEmpty() && lines[0].length > 2) {
            return lines[0].trim().take(60)
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
