package com.bankwise.app.parser.banks

import com.bankwise.app.parser.ParseResult
import com.bankwise.app.parser.ParsedTransaction
import kotlinx.datetime.LocalDate

/**
 * Parser for Postbank statements
 *
 * Format:
 * - Header: Auszug | Jahr | Seite | von | IBAN | Alter Kontostand EUR
 * - Year extracted from "Jahr YYYY" in header
 * - Columns: Buchung/Wert | Vorgang/Buchungsinformation | Soll | Haben
 * - Transaction first line: "DD.MM./DD.MM. TransactionType Description"
 *   - DD.MM./DD.MM. = booking date / value date (short format, year from header)
 * - Amounts: prefix sign "+ 10,00" (Haben/credit) or "- 2,50" (Soll/debit)
 * - Description may span multiple lines
 */
class PostbankParser : GermanBankParser() {
    override val bankName = "Postbank"

    // CERTAIN: unique identifiers
    private val certainIdentifiers = listOf(
        "pbnkdeff",     // Postbank BIC
        "pbnkdeffxxx",  // Postbank BIC full
        "postbank ag"   // Official name
    )

    // HIGH: strong indicators
    private val highIdentifiers = listOf(
        "postbank",
        "postbank.de",
        "eine niederlassung der deutsche bank"
    )

    // MEDIUM: patterns in Postbank statements
    private val mediumIdentifiers = listOf(
        "buchung/wert",
        "vorgang/buchungsinformation",
        "alter kontostand"
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return certainIdentifiers.any { lower.contains(it) } ||
               highIdentifiers.any { lower.contains(it) }
    }

    override fun getConfidence(pdfText: String): Pair<DetectionConfidence, List<String>> {
        return calculateConfidence(pdfText, certainIdentifiers, highIdentifiers, mediumIdentifiers)
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        try {
            val lines = pdfText.lines()
            val accountIban = extractIban(pdfText)
            val statementPeriod = extractStatementPeriod(pdfText)

            // Extract year from header "Jahr YYYY"
            val year = extractYearFromHeader(pdfText)

            // Try Postbank specific format
            var transactions = parsePostbankFormat(lines, year)

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
                    errorMessage = "Could not extract transactions from Postbank PDF"
                )
            }
        } catch (e: Exception) {
            return ParseResult(
                success = false,
                bankName = bankName,
                errorMessage = "Error parsing Postbank PDF: ${e.message}"
            )
        }
    }

    /**
     * Extract year from "Jahr YYYY" in header
     */
    private fun extractYearFromHeader(text: String): Int {
        val yearPattern = Regex("""Jahr\s+(\d{4})""", RegexOption.IGNORE_CASE)
        val match = yearPattern.find(text)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 2024
    }

    /**
     * Parse Postbank format:
     *
     * 13.05./13.05.  Gutschr.SEPA                                        + 10,00
     *                yunus sen Referenz NOTPROVIDED SEMA NUR TURGUT
     *
     * 16.05./16.05.  SDD Lastschr                                        - 2,50
     *                NetCologne Gesellschaft fur Telekommunikation...
     */
    private fun parsePostbankFormat(lines: List<String>, year: Int): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()

        // Pattern for transaction start: DD.MM./DD.MM. followed by description and amount
        // The dates are booking/value date pair
        val datePairPattern = Regex("""^(\d{2}\.\d{2}\.)/(\d{2}\.\d{2}\.)\s*(.*)""")

        // Pattern for amount with prefix sign: "+ 10,00" or "- 2,50"
        val amountPattern = Regex("""([+-])\s*(\d{1,3}(?:[.\s]\d{3})*,\d{2})\s*$""")

        // Pattern for amount anywhere in line
        val amountAnywherePattern = Regex("""([+-])\s*(\d{1,3}(?:[.\s]\d{3})*,\d{2})""")

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            if (line.isEmpty() || isPostbankHeaderLine(line)) {
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
                    val amountMatch = amountPattern.find(line)
                    var amount: Double? = null
                    var isDebit = false
                    var descriptionOnFirstLine = restOfLine

                    if (amountMatch != null) {
                        val sign = amountMatch.groupValues[1]
                        // Don't pre-remove dots - parseGermanAmount handles thousand separators
                        val amountStr = amountMatch.groupValues[2].replace(" ", "")
                        amount = parseGermanAmount(amountStr)
                        isDebit = sign == "-"

                        // Description is between dates and amount
                        val amountStart = amountMatch.range.first
                        val descEnd = amountStart - datePairMatch.range.last - 1
                        if (descEnd > 0) {
                            descriptionOnFirstLine = restOfLine.substring(0, minOf(restOfLine.length, amountStart - datePairMatch.range.last - 1)).trim()
                        }
                    }

                    // Collect description lines
                    val descriptionParts = mutableListOf<String>()
                    if (descriptionOnFirstLine.isNotBlank()) {
                        // Remove amount from description if present
                        val cleanDesc = amountPattern.replace(descriptionOnFirstLine, "").trim()
                        if (cleanDesc.isNotBlank()) {
                            descriptionParts.add(cleanDesc)
                        }
                    }

                    var j = i + 1

                    // Look for continuation lines and amount
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

                        // Stop at header lines
                        if (isPostbankHeaderLine(nextLine)) {
                            break
                        }

                        // Check if this line has an amount
                        val nextAmountMatch = amountPattern.find(nextLine)
                        if (nextAmountMatch != null && amount == null) {
                            val sign = nextAmountMatch.groupValues[1]
                            val amountStr = nextAmountMatch.groupValues[2].replace(" ", "")
                            amount = parseGermanAmount(amountStr)
                            isDebit = sign == "-"

                            // Add text before amount to description
                            val textBeforeAmount = nextLine.substring(0, nextAmountMatch.range.first).trim()
                            if (textBeforeAmount.isNotBlank()) {
                                descriptionParts.add(textBeforeAmount)
                            }
                            j++
                            continue
                        }

                        // This is a continuation line (description only)
                        descriptionParts.add(nextLine)
                        j++
                    }

                    // If we still don't have an amount, try to find it in the collected text
                    if (amount == null) {
                        val fullText = descriptionParts.joinToString(" ")
                        val amountInText = amountAnywherePattern.find(fullText)
                        if (amountInText != null) {
                            val sign = amountInText.groupValues[1]
                            val amountStr = amountInText.groupValues[2].replace(" ", "")
                            amount = parseGermanAmount(amountStr)
                            isDebit = sign == "-"
                        }
                    }

                    if (amount != null) {
                        val finalAmount = if (isDebit) -kotlin.math.abs(amount) else kotlin.math.abs(amount)

                        val fullDescription = descriptionParts.joinToString(" ")
                            .replace(amountAnywherePattern, "")
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
                                    counterpartyName = extractPostbankCounterparty(fullDescription),
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
     * Check if line is a Postbank header/footer line
     */
    private fun isPostbankHeaderLine(line: String): Boolean {
        val lower = line.lowercase()
        return lower.contains("auszug") && lower.contains("jahr") ||
               lower.contains("buchung/wert") ||
               lower.contains("vorgang/buchungsinformation") ||
               lower.contains("alter kontostand") ||
               lower.contains("neuer kontostand") ||
               lower.contains("soll") && lower.contains("haben") && lower.length < 30 ||
               lower.contains("seite") && lower.contains("von") ||
               lower.contains("iban") && lower.contains("de") && lower.length < 50 ||
               lower.contains("kontostand") ||
               lower.contains("summe") ||
               lower.contains("übertrag") ||
               // Footer summary section
               lower.startsWith("kontonummer") ||
               lower.startsWith("blz") ||
               lower.contains("summe zahlungseingänge") ||
               lower.contains("summe zahlungsausgänge") ||
               lower.contains("zinssatz") ||
               lower.contains("kontoüberziehung") ||
               lower.startsWith("anlage") ||
               lower.startsWith("eur") && lower.length < 20 ||
               // Account numbers (just digits with spaces)
               Regex("""^\d[\d\s]{5,15}$""").matches(line.trim()) ||
               isHeaderOrFooter(line)
    }

    /**
     * Extract counterparty from Postbank transaction description
     */
    private fun extractPostbankCounterparty(description: String): String? {
        // Transaction types to skip at start
        val txTypes = listOf(
            "Gutschr.SEPA", "Gutschr. SEPA", "SDD Lastschr", "SEPA Überw. Einzel",
            "SEPA Überw.", "Lastschrift", "Gutschrift", "Dauerauftrag",
            "Kartenzahlung", "Überweisung", "Übertrag", "Einzahlung", "Auszahlung"
        )

        var cleanDesc = description
        for (txType in txTypes) {
            if (cleanDesc.startsWith(txType, ignoreCase = true)) {
                cleanDesc = cleanDesc.substring(txType.length).trim()
                break
            }
        }

        // Stop at technical markers
        val techMarkers = listOf("Referenz", "Mandat", "Einreicher-ID", "Auftrag:", "//", "RG")
        for (marker in techMarkers) {
            val idx = cleanDesc.indexOf(marker, ignoreCase = true)
            if (idx > 3) {
                cleanDesc = cleanDesc.substring(0, idx).trim()
                break
            }
        }

        cleanDesc = cleanDesc.trimEnd(',', '.', ' ')

        if (cleanDesc.length > 50) {
            val spaceIdx = cleanDesc.indexOf(' ', 30)
            if (spaceIdx > 0) {
                cleanDesc = cleanDesc.substring(0, spaceIdx)
            }
        }

        return cleanDesc.takeIf { it.length > 2 }
    }
}
