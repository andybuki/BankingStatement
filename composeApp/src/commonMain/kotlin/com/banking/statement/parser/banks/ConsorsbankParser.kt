package com.banking.statement.parser.banks

import com.banking.statement.parser.ParseResult
import com.banking.statement.parser.ParsedTransaction
import kotlinx.datetime.LocalDate

/**
 * Parser for Consorsbank statements
 *
 * Format:
 * - Header: Kontoauszug | Datum | BIC | IBAN
 * - Columns: Text/Verwendungszweck | Datum | PNNr | Wert | Soll | Haben
 * - Transaction type at line start: EURO-UEBERW., GUTSCHRIFT, LASTSCHRIFT
 * - Date format: DD.MM. (short format, year from header "Datum 28.04.23")
 * - Amount format: Trailing +/- suffix: "39,99-" (debit) or "25,49+" (credit)
 * - Multi-line descriptions
 * - Footer: Bank legal text (BNP Paribas disclaimer)
 */
class ConsorsbankParser : GermanBankParser() {
    override val bankName = "Consorsbank"

    // CERTAIN: unique identifiers
    private val certainIdentifiers = listOf(
        "csdbde71",     // BIC
        "csdbde71xxx",  // BIC full
        "consorsbank"
    )

    // HIGH: strong indicators
    private val highIdentifiers = listOf(
        "consors",
        "bnp paribas",
        "cortal consors"
    )

    // MEDIUM: patterns in Consorsbank statements
    private val mediumIdentifiers = listOf(
        "text/verwendungszweck",
        "pnnr",
        "visa 26466"  // Consorsbank VISA card pattern
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return certainIdentifiers.any { lower.contains(it) } ||
               highIdentifiers.any { lower.contains(it) } ||
               (lower.contains("text/verwendungszweck") && lower.contains("pnnr"))
    }

    override fun getConfidence(pdfText: String): Pair<DetectionConfidence, List<String>> {
        return calculateConfidence(pdfText, certainIdentifiers, highIdentifiers, mediumIdentifiers)
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        try {
            val lines = pdfText.lines()
            val accountIban = extractIban(pdfText)
            val statementPeriod = extractStatementPeriod(pdfText)

            // Extract year from header "Datum DD.MM.YY" or "Datum DD.MM.YYYY"
            val year = extractYearFromHeader(pdfText)

            // Try Consorsbank specific format
            var transactions = parseConsorsbankFormat(lines, year)

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
                    errorMessage = "Could not extract transactions from Consorsbank PDF"
                )
            }
        } catch (e: Exception) {
            return ParseResult(
                success = false,
                bankName = bankName,
                errorMessage = "Error parsing Consorsbank PDF: ${e.message}"
            )
        }
    }

    /**
     * Extract year from header "Datum DD.MM.YY" or "Datum DD.MM.YYYY"
     */
    private fun extractYearFromHeader(text: String): Int {
        // Try "Datum DD.MM.YY" format first
        val shortYearPattern = Regex("""Datum\s+\d{2}\.\d{2}\.(\d{2})\b""", RegexOption.IGNORE_CASE)
        val shortMatch = shortYearPattern.find(text)
        if (shortMatch != null) {
            val shortYear = shortMatch.groupValues[1].toIntOrNull() ?: return 2024
            return if (shortYear > 50) 1900 + shortYear else 2000 + shortYear
        }

        // Try full year format
        val fullYearPattern = Regex("""\d{2}\.\d{2}\.(\d{4})""")
        val fullMatch = fullYearPattern.find(text)
        return fullMatch?.groupValues?.get(1)?.toIntOrNull() ?: 2024
    }

    /**
     * Parse Consorsbank format:
     *
     * EURO-UEBERW.                     04.04. 8420   04.04.           39,99-
     *     Breuninger Online Shop
     *     <GENODEFFXXX>    DE53500604000000146135
     *
     * GUTSCHRIFT                       04.04. 8999   04.04.                    25,49+
     *     PEEK & CLOPPENBURG
     *                          VISA 26466011 WEITERSTADT
     */
    private fun parseConsorsbankFormat(lines: List<String>, year: Int): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()

        // Transaction types that start a new transaction
        val transactionTypes = listOf(
            "EURO-UEBERW", "GUTSCHRIFT", "LASTSCHRIFT", "DAUERAUFTRAG",
            "UEBERWEISUNG", "ÜBERWEISUNG", "KARTENZAHLUNG", "GEHALT",
            "LOHN", "SEPA-LASTSCHRIFT", "SEPA-ÜBERWEISUNG"
        )

        // Pattern for line with transaction type, dates and amount
        // Example: "EURO-UEBERW.                     04.04. 8420   04.04.           39,99-"
        val transactionLinePattern = Regex(
            """^([A-ZÄÖÜ][A-ZÄÖÜ\-]+\.?)\s+(\d{2}\.\d{2}\.)\s+(\d+)\s+(\d{2}\.\d{2}\.)\s+(\d{1,3}(?:\.\d{3})*,\d{2})([+-])\s*$"""
        )

        // Simpler pattern: just look for amount with +/- at end
        val amountEndPattern = Regex("""(\d{1,3}(?:\.\d{3})*,\d{2})([+-])\s*$""")

        // Pattern for dates in line
        val datePattern = Regex("""(\d{2}\.\d{2}\.)""")

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            if (line.isEmpty() || isConsorsbankHeaderLine(line)) {
                i++
                continue
            }

            // Check if line starts with a transaction type
            val startsWithTxType = transactionTypes.any {
                line.uppercase().startsWith(it)
            }

            if (startsWithTxType) {
                // Check if this line has an amount at the end
                val amountMatch = amountEndPattern.find(line)
                val dates = datePattern.findAll(line).toList()

                if (amountMatch != null && dates.isNotEmpty()) {
                    val amountStr = amountMatch.groupValues[1]
                    val sign = amountMatch.groupValues[2]
                    val amount = parseGermanAmount(amountStr)
                    val isDebit = sign == "-"

                    // Get booking date (first date) and value date (second date if exists)
                    val bookingDateStr = dates[0].groupValues[1]
                    val valueDateStr = if (dates.size > 1) dates[1].groupValues[1] else bookingDateStr

                    val bookingDate = parseShortDateWithYear(bookingDateStr, year)
                    val valueDate = parseShortDateWithYear(valueDateStr, year)

                    if (bookingDate != null && amount != null) {
                        val finalAmount = if (isDebit) -kotlin.math.abs(amount) else kotlin.math.abs(amount)

                        // Extract transaction type from start of line
                        val txType = transactionTypes.find { line.uppercase().startsWith(it) } ?: "Buchung"

                        // Get description (text before dates/amount)
                        val descStart = line.uppercase().indexOf(txType) + txType.length
                        val descEnd = dates[0].range.first
                        var descriptionOnFirstLine = if (descEnd > descStart) {
                            line.substring(descStart, descEnd).trim().trimStart('.')
                        } else ""

                        // Collect continuation lines
                        val descriptionParts = mutableListOf<String>()
                        if (descriptionOnFirstLine.isNotBlank()) {
                            descriptionParts.add(descriptionOnFirstLine)
                        }

                        var j = i + 1
                        while (j < lines.size && j < i + 15) {
                            val nextLine = lines[j].trim()

                            if (nextLine.isEmpty()) {
                                j++
                                continue
                            }

                            // Stop at next transaction
                            if (transactionTypes.any { nextLine.uppercase().startsWith(it) }) {
                                break
                            }

                            // Stop at footer/header
                            if (isConsorsbankHeaderLine(nextLine)) {
                                break
                            }

                            // Add to description
                            descriptionParts.add(nextLine)
                            j++
                        }

                        val fullDescription = descriptionParts.joinToString(" ")
                            .replace(Regex("""\s+"""), " ")
                            .trim()

                        if (!isBalanceEntry(fullDescription, fullDescription)) {
                            transactions.add(
                                ParsedTransaction(
                                    bookingDate = bookingDate,
                                    valueDate = valueDate ?: bookingDate,
                                    amount = finalAmount,
                                    currency = "EUR",
                                    description = if (fullDescription.isNotBlank()) fullDescription else txType,
                                    counterpartyName = extractConsorsbankCounterparty(fullDescription),
                                    transactionType = txType.trimEnd('.'),
                                    rawText = descriptionParts.joinToString("\n")
                                )
                            )
                        }

                        i = j
                        continue
                    }
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
     * Check if line is a Consorsbank header/footer line
     */
    private fun isConsorsbankHeaderLine(line: String): Boolean {
        val lower = line.lowercase()
        return lower.contains("kontoauszug") && lower.contains("konto-nr") ||
               lower.contains("text/verwendungszweck") && lower.contains("datum") ||
               lower.contains("bankleitzahl") ||
               lower.contains("kontowährung") ||
               lower.contains("kontostand") ||
               lower.contains("saldo") ||
               lower.contains("summe") ||
               lower.contains("übertrag") ||
               lower.contains("blatt") && lower.contains("von") ||
               lower.startsWith("iban") ||
               lower.startsWith("bic") ||
               // Footer - BNP Paribas legal text
               lower.contains("consorsbank ist eine") ||
               lower.contains("bnp paribas") && lower.contains("niederlassung") ||
               lower.contains("standort nürnberg") ||
               lower.contains("bahnhofstraße") ||
               lower.contains("fon +49") ||
               lower.contains("sitz der bnp") ||
               lower.contains("président") ||
               lower.contains("registergericht") ||
               lower.contains("info@consorsbank") ||
               isHeaderOrFooter(line)
    }

    /**
     * Extract counterparty from Consorsbank transaction description
     */
    private fun extractConsorsbankCounterparty(description: String): String? {
        if (description.isBlank()) return null

        var cleanDesc = description

        // Remove technical patterns
        val techPatterns = listOf(
            Regex("""<[A-Z0-9]+>"""),  // BIC in angle brackets
            Regex("""DE\d{20,22}"""),   // IBAN
            Regex("""\d{9,12}"""),      // Account numbers
            Regex("""VISA\s+\d+"""),    // VISA card numbers
            Regex("""\d{2}\.\d{2}\.\s+\d+"""),  // Date + number
            Regex("""\d+,\d{2}\s*EUR""")  // Amount in EUR
        )

        for (pattern in techPatterns) {
            cleanDesc = pattern.replace(cleanDesc, " ")
        }

        cleanDesc = cleanDesc.replace(Regex("""\s+"""), " ").trim()

        // Take first meaningful segment
        val segments = cleanDesc.split(Regex("""\s{2,}"""))
        if (segments.isNotEmpty() && segments[0].length > 2) {
            return segments[0].trim().take(60)
        }

        if (cleanDesc.length > 60) {
            val spaceIdx = cleanDesc.indexOf(' ', 40)
            if (spaceIdx > 0) {
                cleanDesc = cleanDesc.substring(0, spaceIdx)
            }
        }

        return cleanDesc.takeIf { it.length > 2 }
    }
}
