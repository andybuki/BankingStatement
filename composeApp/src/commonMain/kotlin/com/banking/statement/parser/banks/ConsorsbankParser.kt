package com.banking.statement.parser.banks

import com.banking.statement.parser.ParseResult
import com.banking.statement.parser.ParsedTransaction
import kotlinx.datetime.LocalDate

/**
 * Parser for Consorsbank statements
 *
 * Format from raw text:
 * EURO-UEBERW. 04.04. 8420 04.04. 39,99-
 * Breuninger Online Shop
 * <GENODEFFXXX> DE53500604000000146135
 * 811419765
 * GUTSCHRIFT 04.04. 8999 04.04. 25,49+
 * PEEK & CLOPPENBURG
 * ...
 *
 * Pattern: TYPE DD.MM. PNNR DD.MM. AMOUNT+/-
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

    // Transaction types that start a new transaction
    private val transactionTypes = listOf(
        "EURO-UEBERW", "GUTSCHRIFT", "LASTSCHRIFT", "DAUERAUFTRAG",
        "UEBERWEISUNG", "ÜBERWEISUNG", "KARTENZAHLUNG", "GEHALT",
        "LOHN", "SEPA-LASTSCHRIFT", "SEPA-ÜBERWEISUNG", "ABSCHLUSS"
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

            // Extract year from header "Datum DD.MM.YY" or any full date
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
     * Extract year from header "Datum DD.MM.YY" or any full date
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
     * EURO-UEBERW. 04.04. 8420 04.04. 39,99-
     * Breuninger Online Shop
     * <GENODEFFXXX> DE53500604000000146135
     */
    private fun parseConsorsbankFormat(lines: List<String>, year: Int): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()

        // Amount pattern: digits with optional thousand separator, comma, 2 decimals, then +/-
        // Matches: 39,99- or 25,49+ or 1.234,56+
        val amountPattern = Regex("""(\d{1,3}(?:\.\d{3})*,\d{2})([+-])$""")

        // Date pattern
        val datePattern = Regex("""(\d{2}\.\d{2}\.)""")

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            if (line.isEmpty() || isConsorsbankHeaderLine(line)) {
                i++
                continue
            }

            // Check if line starts with a transaction type
            val txTypeMatch = transactionTypes.find { line.uppercase().startsWith(it) }

            if (txTypeMatch != null) {
                // Check if this line has an amount at the end (with +/- suffix)
                val amountMatch = amountPattern.find(line)

                if (amountMatch != null) {
                    val amountStr = amountMatch.groupValues[1]
                    val sign = amountMatch.groupValues[2]
                    val amount = parseGermanAmount(amountStr)
                    val isDebit = sign == "-"

                    // Find dates in the line
                    val dates = datePattern.findAll(line).toList()

                    if (amount != null && dates.isNotEmpty()) {
                        val finalAmount = if (isDebit) -kotlin.math.abs(amount) else kotlin.math.abs(amount)

                        // Get booking date (first date) and value date (last date)
                        val bookingDateStr = dates.first().groupValues[1]
                        val valueDateStr = dates.last().groupValues[1]

                        val bookingDate = parseShortDateWithYear(bookingDateStr, year)
                        val valueDate = parseShortDateWithYear(valueDateStr, year)

                        if (bookingDate != null) {
                            // Collect continuation lines for description
                            val descriptionParts = mutableListOf<String>()
                            var j = i + 1

                            while (j < lines.size && j < i + 15) {
                                val nextLine = lines[j].trim()

                                if (nextLine.isEmpty()) {
                                    j++
                                    continue
                                }

                                // Stop at next transaction
                                val nextIsTx = transactionTypes.any { nextLine.uppercase().startsWith(it) }
                                if (nextIsTx && amountPattern.containsMatchIn(nextLine)) {
                                    break
                                }

                                // Stop at header/footer
                                if (isConsorsbankHeaderLine(nextLine)) {
                                    break
                                }

                                // Add to description (filter out amount-only lines)
                                if (!Regex("""^\d+,\d{2}\s*EUR$""").matches(nextLine)) {
                                    descriptionParts.add(nextLine)
                                }
                                j++
                            }

                            val fullDescription = descriptionParts.joinToString(" ")
                                .replace(Regex("""\s+"""), " ")
                                .trim()

                            transactions.add(
                                ParsedTransaction(
                                    bookingDate = bookingDate,
                                    valueDate = valueDate ?: bookingDate,
                                    amount = finalAmount,
                                    currency = "EUR",
                                    description = if (fullDescription.isNotBlank()) fullDescription else txTypeMatch,
                                    counterpartyName = extractConsorsbankCounterparty(fullDescription),
                                    transactionType = txTypeMatch.trimEnd('.'),
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
        return lower.contains("kontoauszug") && (lower.contains("konto-nr") || lower.contains("blatt")) ||
               lower.contains("text/verwendungszweck") && lower.contains("datum") && lower.contains("pnnr") ||
               lower.contains("bankleitzahl") ||
               lower.contains("kontowährung") ||
               lower.contains("buchungssaldo") ||
               lower.contains("kontostand zum") ||
               lower.contains("saldo") && lower.length < 30 ||
               lower.contains("summe") ||
               lower.contains("übertrag") ||
               lower.startsWith("blatt ") ||
               lower.startsWith("iban") && lower.length < 40 ||
               lower.startsWith("bic") && lower.length < 20 ||
               lower.startsWith("datum") && lower.length < 20 ||
               lower.startsWith("kontonummer") ||
               lower.startsWith("kontotyp") ||
               lower.startsWith("kontoinhaber") ||
               lower.startsWith("vermerk") ||
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
               lower.contains("consorsbank •") ||
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
            Regex("""<[A-Z0-9]+>"""),           // BIC in angle brackets
            Regex("""DE\d{18,22}"""),           // IBAN
            Regex("""\b\d{8,12}\b"""),          // Long account numbers
            Regex("""VISA\s+\d+\s+\w+"""),      // VISA card info
            Regex("""\d{2}\.\d{2}\.\s+\d+"""),  // Date + number
            Regex("""\d+,\d{2}\s*EUR""")        // Amount in EUR
        )

        for (pattern in techPatterns) {
            cleanDesc = pattern.replace(cleanDesc, " ")
        }

        cleanDesc = cleanDesc.replace(Regex("""\s+"""), " ").trim()

        // Take first meaningful segment (usually the counterparty name)
        if (cleanDesc.length > 60) {
            val spaceIdx = cleanDesc.indexOf(' ', 40)
            if (spaceIdx > 0) {
                cleanDesc = cleanDesc.substring(0, spaceIdx)
            }
        }

        return cleanDesc.trimEnd(',', '.', ' ').takeIf { it.length > 2 }
    }
}
