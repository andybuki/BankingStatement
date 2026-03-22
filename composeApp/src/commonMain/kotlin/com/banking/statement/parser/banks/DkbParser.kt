package com.banking.statement.parser.banks

import com.banking.statement.parser.ParseResult
import com.banking.statement.parser.ParsedTransaction
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Parser for DKB (Deutsche Kreditbank) statements
 */
class DkbParser : GermanBankParser() {
    override val bankName = "DKB"

    // CERTAIN: unique identifiers (BIC codes, BLZ, official name)
    private val certainIdentifiers = listOf(
        "deutsche kreditbank ag",
        "byladem1",         // BIC
        "byladem1001",      // BIC variant
        "bylademmxxx",      // Full BIC
        "12030000"          // BLZ (Bankleitzahl)
    )

    // HIGH: strong indicators
    private val highIdentifiers = listOf(
        "deutsche kreditbank",
        "dkb ag",
        "dkb-ag",
        "dkb.de",
        "wir haben für sie gebucht"  // DKB-specific header
    )

    // MEDIUM: patterns that might appear in DKB statements
    private val mediumIdentifiers = listOf(
        "dkb",              // Short name, could match other things
        "belastung in eur", // Column headers
        "gutschrift in eur"
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        // Check for DKB-specific format with header columns
        val hasDkbHeader = lower.contains("belastung in eur") || lower.contains("gutschrift in eur")
        val hasDkbFormat = lower.contains("kref+") && lower.contains("svwz+")
        return certainIdentifiers.any { lower.contains(it) } ||
               highIdentifiers.any { lower.contains(it) } ||
               hasDkbHeader ||
               (hasDkbFormat && (lower.contains("kontoauszug") || lower.contains("überweisung")))
    }

    override fun getConfidence(pdfText: String): Pair<DetectionConfidence, List<String>> {
        return calculateConfidence(pdfText, certainIdentifiers, highIdentifiers, mediumIdentifiers)
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        try {
            val lines = pdfText.lines()
            val accountIban = extractIban(pdfText)
            val statementPeriod = extractStatementPeriod(pdfText)

            // Try to extract year from statement
            val year = extractYearFromStatement(pdfText)

            // Try DKB specific format first
            var transactions = parseDkbFormat(lines, year)

            // Fallback to generic parser if specific format didn't work well
            if (transactions.size < 3) {
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
                    errorMessage = "Could not extract transactions from DKB PDF"
                )
            }
        } catch (e: Exception) {
            return ParseResult(
                success = false,
                bankName = bankName,
                errorMessage = "Error parsing DKB PDF: ${e.message}"
            )
        }
    }

    /**
     * Extract year from statement text (look for patterns like "2020", "2024", etc.)
     */
    private fun extractYearFromStatement(text: String): Int {
        // Look for year patterns in common formats
        val yearPatterns = listOf(
            Regex("""Kontoauszug.*?(\d{4})"""),
            Regex("""Auszug.*?(\d{4})"""),
            Regex("""DATUM\s+\d{2}\.\d{2}\.(\d{4})"""),
            Regex("""(\d{2})\.(\d{2})\.(\d{4})""")
        )

        for (pattern in yearPatterns) {
            val match = pattern.find(text)
            if (match != null) {
                val yearStr = match.groupValues.last()
                val year = yearStr.toIntOrNull()
                if (year != null && year in 2000..2100) {
                    return year
                }
            }
        }

        // Default to current year
        return Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
    }

    /**
     * Parse DKB specific format:
     * Bu.Tag    Wert      Wir haben für Sie gebucht           Belastung in EUR    Gutschrift in EUR
     * 05.10.    05.10.    Überweisung                         81,87
     *                     QVC HANDEL S.A R.L. U. CO.KG
     *                     KREF+HKCCS25020SVWZ+...
     */
    private fun parseDkbFormat(lines: List<String>, defaultYear: Int): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()

        // Pattern for transaction start: "DD.MM.    DD.MM.    Description    [Amount]"
        // Amount can be in Belastung (debit) or Gutschrift (credit) column
        val transactionStartPattern = Regex(
            """^(\d{2}\.\d{2}\.)\s+(\d{2}\.\d{2}\.)\s+(.+?)(?:\s+([\d.]+,\d{2}))?\s*$"""
        )

        // Pattern for just two short dates at start
        val dateStartPattern = Regex("""^(\d{2}\.\d{2}\.)\s+(\d{2}\.\d{2}\.)\s+(.*)$""")

        // Pattern for amount (German format)
        val amountPattern = Regex("""([\d.]+,\d{2})""")

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            // Skip header lines
            if (line.lowercase().contains("bu.tag") ||
                line.lowercase().contains("belastung in eur") ||
                line.lowercase().contains("gutschrift in eur") ||
                line.lowercase().contains("wir haben für sie gebucht")) {
                i++
                continue
            }

            // Try to match transaction start
            val dateMatch = dateStartPattern.find(line)
            if (dateMatch != null) {
                val bookingDateShort = dateMatch.groupValues[1]  // "05.10."
                val valueDateShort = dateMatch.groupValues[2]    // "05.10."
                val restOfLine = dateMatch.groupValues[3].trim()

                // Build full dates with year
                val bookingDate = parseGermanDate("${bookingDateShort}$defaultYear")
                val valueDate = parseGermanDate("${valueDateShort}$defaultYear")

                // Check if amount is on this line (at the end)
                val amountMatches = amountPattern.findAll(restOfLine).toList()
                var amount: Double? = null
                var isDebit = true
                var descriptionText = restOfLine

                if (amountMatches.isNotEmpty()) {
                    // Amount is at the end - take the last one
                    val amountMatch = amountMatches.last()
                    val amountStr = amountMatch.groupValues[1]
                    amount = parseGermanAmount(amountStr)

                    // Remove amount from description
                    descriptionText = restOfLine.substring(0, amountMatch.range.first).trim()

                    // Determine if debit or credit based on position
                    // In DKB format, amounts on the right are typically credits
                    // We'll also check the description for clues
                    val lowerDesc = descriptionText.lowercase()
                    isDebit = !lowerDesc.contains("zahlungseingang") &&
                              !lowerDesc.contains("gutschrift") &&
                              !lowerDesc.contains("eingang")
                }

                // Collect description lines
                val descriptionParts = mutableListOf<String>()
                if (descriptionText.isNotBlank()) {
                    descriptionParts.add(descriptionText)
                }

                // Read continuation lines
                var j = i + 1
                while (j < lines.size && j < i + 20) {
                    val contLine = lines[j].trim()

                    // Stop if we hit a new transaction (line starting with date)
                    if (dateStartPattern.containsMatchIn(contLine)) break

                    // Stop if empty line followed by another date line
                    if (contLine.isEmpty()) {
                        val nextNonEmpty = lines.drop(j + 1).firstOrNull { it.isNotBlank() }
                        if (nextNonEmpty != null && dateStartPattern.containsMatchIn(nextNonEmpty.trim())) {
                            break
                        }
                        j++
                        continue
                    }

                    // Skip header/footer lines
                    if (isHeaderOrFooter(contLine) || isBalanceEntry(contLine, contLine)) {
                        j++
                        continue
                    }

                    // Check if this line has an amount (might be on a separate line)
                    if (amount == null) {
                        val contAmountMatch = amountPattern.find(contLine)
                        if (contAmountMatch != null && contLine.length < 20) {
                            // This looks like just an amount line
                            amount = parseGermanAmount(contAmountMatch.groupValues[1])
                            j++
                            continue
                        }
                    }

                    descriptionParts.add(contLine)
                    j++
                }

                // If we still don't have an amount, try to find it in the collected description
                if (amount == null) {
                    val fullText = descriptionParts.joinToString(" ")
                    val lastAmount = amountPattern.findAll(fullText).lastOrNull()
                    if (lastAmount != null) {
                        amount = parseGermanAmount(lastAmount.groupValues[1])
                    }
                }

                if (bookingDate != null && amount != null) {
                    val fullDescription = descriptionParts.joinToString(" ")
                        .replace(Regex("""\s+"""), " ")
                        .trim()

                    // Determine sign based on transaction type
                    val lowerDesc = fullDescription.lowercase()
                    val isIncome = lowerDesc.contains("zahlungseingang") ||
                                   lowerDesc.contains("gutschrift") ||
                                   lowerDesc.contains("eingang") ||
                                   lowerDesc.contains("gehalt") ||
                                   lowerDesc.contains("lohn")

                    val finalAmount = if (isIncome) kotlin.math.abs(amount) else -kotlin.math.abs(amount)

                    // Skip balance entries
                    if (!isBalanceEntry(fullDescription, fullDescription)) {
                        // Extract counterparty (usually second line)
                        val counterparty = if (descriptionParts.size > 1) {
                            val secondLine = descriptionParts[1]
                            if (!secondLine.startsWith("KREF") &&
                                !secondLine.startsWith("SVWZ") &&
                                !secondLine.startsWith("EREF") &&
                                !secondLine.startsWith("IBAN") &&
                                !secondLine.startsWith("BIC")) {
                                secondLine
                            } else {
                                extractCounterparty(fullDescription)
                            }
                        } else {
                            extractCounterparty(fullDescription)
                        }

                        transactions.add(
                            ParsedTransaction(
                                bookingDate = bookingDate,
                                valueDate = valueDate ?: bookingDate,
                                amount = finalAmount,
                                currency = "EUR",
                                description = fullDescription,
                                counterpartyName = counterparty,
                                transactionType = detectTransactionType(fullDescription),
                                rawText = descriptionParts.joinToString("\n")
                            )
                        )
                    }
                }

                i = j
                continue
            }

            i++
        }

        return transactions.distinctBy { "${it.bookingDate}_${it.amount}_${it.description.take(30)}" }
    }
}
