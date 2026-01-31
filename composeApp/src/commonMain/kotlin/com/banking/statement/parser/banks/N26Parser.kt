package com.banking.statement.parser.banks

import com.banking.statement.parser.ParseResult
import com.banking.statement.parser.ParsedTransaction
import kotlinx.datetime.LocalDate

/**
 * Parser for N26 bank statements
 *
 * Format:
 * PayPal Europe S.a.r.l. et Cie S.C.A          03.09.2025       +0,01€
 * Gutschriften
 * IBAN: LU897510001351042006 • BIC: PPLXLUL2
 * PP.4866.PP/YYR1044589788691
 * Wertstellung 03.09.2025
 *
 * Pattern: COUNTERPARTY_NAME    DD.MM.YYYY    +/-AMOUNT€
 */
class N26Parser : GermanBankParser() {
    override val bankName = "N26"

    // CERTAIN: unique identifiers
    private val certainIdentifiers = listOf(
        "ntsbdeb1",     // BIC
        "ntsbdeb1xxx",  // BIC full
        "n26 bank",
        "n26 gmbh"
    )

    // HIGH: strong indicators
    private val highIdentifiers = listOf(
        "n26",
        "number26"
    )

    // MEDIUM: patterns in N26 statements
    private val mediumIdentifiers = listOf(
        "kontoauszug nr.",
        "verbuchungsdatum",
        "wertstellung",
        "gutschriften",
        "belastungen"
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
            val statementPeriod = extractN26StatementPeriod(pdfText)

            // Try N26 specific format
            var transactions = parseN26Format(lines)

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
                    errorMessage = "Could not extract transactions from N26 PDF"
                )
            }
        } catch (e: Exception) {
            return ParseResult(
                success = false,
                bankName = bankName,
                errorMessage = "Error parsing N26 PDF: ${e.message}"
            )
        }
    }

    /**
     * Extract statement period from N26 header
     * Format: "02.09.2025 bis 30.09.2025"
     */
    private fun extractN26StatementPeriod(text: String): String? {
        val periodPattern = Regex("""(\d{2}\.\d{2}\.\d{4})\s+bis\s+(\d{2}\.\d{2}\.\d{4})""")
        val match = periodPattern.find(text)
        return match?.let { "${it.groupValues[1]} - ${it.groupValues[2]}" }
    }

    /**
     * Parse N26 format:
     * Line 1: Counterparty + Date + Amount (e.g., "PayPal Europe...    03.09.2025    +0,01€")
     * Line 2: Transaction type (Gutschriften, Belastungen)
     * Lines 3+: IBAN, BIC, description, Wertstellung
     */
    private fun parseN26Format(lines: List<String>): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()

        // Pattern for transaction line: text + date + amount with € sign
        // Amount formats: +0,01€, -800,00€, +1.234,56€
        val transactionLinePattern = Regex(
            """^(.+?)\s+(\d{2}\.\d{2}\.\d{4})\s+([+-])(\d{1,3}(?:\.\d{3})*,\d{2})€\s*$"""
        )

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            if (line.isEmpty() || isN26HeaderLine(line)) {
                i++
                continue
            }

            // Try to match transaction line
            val match = transactionLinePattern.find(line)

            if (match != null) {
                val counterpartyName = match.groupValues[1].trim()
                val dateStr = match.groupValues[2]
                val sign = match.groupValues[3]
                val amountStr = match.groupValues[4]

                val amount = parseGermanAmount(amountStr)
                val isDebit = sign == "-"

                if (amount != null) {
                    val finalAmount = if (isDebit) -kotlin.math.abs(amount) else kotlin.math.abs(amount)
                    val bookingDate = parseFullDate(dateStr)

                    if (bookingDate != null) {
                        // Collect description lines
                        val descriptionParts = mutableListOf<String>()
                        var transactionType: String? = null
                        var valueDate: LocalDate? = null
                        var j = i + 1

                        while (j < lines.size && j < i + 10) {
                            val nextLine = lines[j].trim()

                            if (nextLine.isEmpty()) {
                                j++
                                continue
                            }

                            // Stop at next transaction
                            if (transactionLinePattern.containsMatchIn(nextLine)) {
                                break
                            }

                            // Stop at page header
                            if (isN26HeaderLine(nextLine)) {
                                break
                            }

                            // Extract transaction type (Gutschriften, Belastungen)
                            if (nextLine == "Gutschriften" || nextLine == "Belastungen" ||
                                nextLine == "Gutschrift" || nextLine == "Belastung") {
                                transactionType = nextLine
                                j++
                                continue
                            }

                            // Extract value date from "Wertstellung DD.MM.YYYY"
                            val wertstellungMatch = Regex("""Wertstellung\s+(\d{2}\.\d{2}\.\d{4})""").find(nextLine)
                            if (wertstellungMatch != null) {
                                valueDate = parseFullDate(wertstellungMatch.groupValues[1])
                                j++
                                continue
                            }

                            // Skip IBAN/BIC lines but don't add to description
                            if (nextLine.startsWith("IBAN:") || nextLine.contains("• BIC:")) {
                                j++
                                continue
                            }

                            // Add other lines to description
                            descriptionParts.add(nextLine)
                            j++
                        }

                        val description = if (descriptionParts.isNotEmpty()) {
                            descriptionParts.joinToString(" ").take(100)
                        } else {
                            counterpartyName
                        }

                        transactions.add(
                            ParsedTransaction(
                                bookingDate = bookingDate,
                                valueDate = valueDate ?: bookingDate,
                                amount = finalAmount,
                                currency = "EUR",
                                description = counterpartyName,
                                counterpartyName = counterpartyName,
                                transactionType = transactionType ?: if (isDebit) "Belastung" else "Gutschrift",
                                rawText = "$line\n${descriptionParts.joinToString("\n")}"
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
     * Parse full date (DD.MM.YYYY)
     */
    private fun parseFullDate(dateStr: String): LocalDate? {
        return try {
            val parts = dateStr.split(".")
            if (parts.size == 3) {
                LocalDate(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if line is N26 header/footer line
     */
    private fun isN26HeaderLine(line: String): Boolean {
        val lower = line.lowercase()
        val trimmed = line.trim()

        return lower.contains("kontoauszug nr.") ||
               lower.contains("n26") && lower.length < 20 ||
               lower.startsWith("beschreibung") && lower.contains("verbuchungsdatum") ||
               lower.contains("betrag") && lower.length < 30 ||
               lower.startsWith("iban:") && lower.contains("bic:") && trimmed.length < 60 ||
               lower.contains("erstellt am") ||
               lower.contains("seite") && Regex("""\d+\s*/\s*\d+""").containsMatchIn(lower) ||
               Regex("""^\d{2}\.\d{2}\.\d{4}\s+bis\s+\d{2}\.\d{2}\.\d{4}$""").matches(trimmed) ||
               // Address lines
               Regex("""^\d{5}\s+\w+""").matches(trimmed) ||
               // Page numbers like "1 / 6"
               Regex("""^\d+\s*/\s*\d+$""").matches(trimmed) ||
               isHeaderOrFooter(line)
    }
}
