package com.banking.statement.parser.banks

import com.banking.statement.parser.ParseResult
import com.banking.statement.parser.ParsedTransaction

/**
 * Parser for Deutsche Bank statements
 */
class DeutscheBankParser : GermanBankParser() {
    override val bankName = "Deutsche Bank"

    // CERTAIN: unique BIC codes and explicit name
    private val certainIdentifiers = listOf(
        "deutdedb",      // Deutsche Bank BIC
        "deutdeff",      // Deutsche Bank BIC Frankfurt
        "deutsche bank ag"
    )

    // HIGH: strong indicators
    private val highIdentifiers = listOf(
        "deutsche bank",
        "deutsche-bank.de"
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return certainIdentifiers.any { lower.contains(it) } ||
               highIdentifiers.any { lower.contains(it) }
    }

    override fun getConfidence(pdfText: String): Pair<DetectionConfidence, List<String>> {
        return calculateConfidence(pdfText, certainIdentifiers, highIdentifiers)
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        try {
            val lines = pdfText.lines()
            val accountIban = extractIban(pdfText)
            val statementPeriod = extractStatementPeriod(pdfText)

            // Try Deutsche Bank specific format first
            var transactions = parseDeutscheBankFormat(lines)

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
                    errorMessage = "Could not extract transactions from Deutsche Bank PDF"
                )
            }
        } catch (e: Exception) {
            return ParseResult(
                success = false,
                bankName = bankName,
                errorMessage = "Error parsing Deutsche Bank PDF: ${e.message}"
            )
        }
    }

    /**
     * Parse Deutsche Bank specific format:
     * Line 1: "14.11.    13.11.    SEPA Überweisung an                    - 78,90"
     * Line 2: "2024      2024      Zalando Payments GmbH"
     * Line 3+:                     "IBAN DE86210700200123010101"
     *                              "BIC DEUTDEHH210"
     *                              etc.
     */
    private fun parseDeutscheBankFormat(lines: List<String>): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()

        // Pattern for first line: "DD.MM.    DD.MM.    Description    +/- Amount"
        val firstLinePattern = Regex(
            """^(\d{2}\.\d{2}\.)\s+(\d{2}\.\d{2}\.)\s+(.+?)\s+([+-])\s*([\d.,]+)\s*$"""
        )
        // Pattern for year line: "YYYY      YYYY      Text"
        val yearLinePattern = Regex(
            """^(\d{4})\s+(\d{4})\s+(.*)$"""
        )
        // Pattern for amount at end of line
        val amountPattern = Regex("""([+-])\s*([\d.]+,\d{2})\s*€?\s*$""")

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            // Try to match the first line of a transaction
            val firstMatch = firstLinePattern.find(line)
            if (firstMatch != null) {
                val bookingDateShort = firstMatch.groupValues[1]  // "14.11."
                val valueDateShort = firstMatch.groupValues[2]    // "13.11."
                val descPart1 = firstMatch.groupValues[3].trim()  // "SEPA Überweisung an"
                val sign = firstMatch.groupValues[4]              // "+" or "-"
                val amountStr = firstMatch.groupValues[5]         // "78,90"

                // Look for year on next line
                var bookingYear = "2024"
                var valueYear = "2024"
                var descPart2 = ""

                if (i + 1 < lines.size) {
                    val nextLine = lines[i + 1].trim()
                    val yearMatch = yearLinePattern.find(nextLine)
                    if (yearMatch != null) {
                        bookingYear = yearMatch.groupValues[1]
                        valueYear = yearMatch.groupValues[2]
                        descPart2 = yearMatch.groupValues[3].trim()
                        i++
                    }
                }

                // Build full dates
                val bookingDateStr = "${bookingDateShort}$bookingYear"
                val valueDateStr = "${valueDateShort}$valueYear"

                val bookingDate = parseGermanDate(bookingDateStr)
                val valueDate = parseGermanDate(valueDateStr)

                // Parse amount
                val amount = parseGermanAmount(amountStr)?.let {
                    if (sign == "-") -it else it
                }

                // Collect additional description lines
                val descriptionParts = mutableListOf<String>()
                if (descPart1.isNotBlank()) descriptionParts.add(descPart1)
                if (descPart2.isNotBlank()) descriptionParts.add(descPart2)

                // Read continuation lines (indented lines or lines without dates)
                var j = i + 1
                while (j < lines.size && j < i + 15) {
                    val contLine = lines[j].trim()
                    if (contLine.isEmpty()) {
                        j++
                        continue
                    }
                    // Stop if we hit a new transaction (line starting with date pattern)
                    if (firstLinePattern.containsMatchIn(contLine)) break
                    // Stop if line starts with short date pattern like "DD.MM."
                    if (Regex("""^\d{2}\.\d{2}\.\s+\d{2}\.\d{2}\.""").containsMatchIn(contLine)) break

                    // Skip balance/summary lines
                    if (isHeaderOrFooter(contLine) || isBalanceEntry(contLine, contLine)) {
                        j++
                        continue
                    }

                    descriptionParts.add(contLine)
                    j++
                }

                if (bookingDate != null && amount != null) {
                    val fullDescription = descriptionParts.joinToString(" ")
                        .replace(Regex("""\s+"""), " ")
                        .trim()

                    // Skip if this is a balance entry
                    if (!isBalanceEntry(fullDescription, fullDescription)) {
                        // Extract transaction type from description
                        val transactionType = detectTransactionType(fullDescription)

                        // Extract counterparty (usually on line 2 - descPart2)
                        val counterparty = if (descPart2.isNotBlank() &&
                            !descPart2.startsWith("IBAN") &&
                            !descPart2.startsWith("BIC") &&
                            !descPart2.startsWith("Verwendungszweck")) {
                            descPart2
                        } else {
                            extractCounterparty(fullDescription)
                        }

                        transactions.add(
                            ParsedTransaction(
                                bookingDate = bookingDate,
                                valueDate = valueDate ?: bookingDate,
                                amount = amount,
                                currency = "EUR",
                                description = fullDescription,
                                counterpartyName = counterparty,
                                transactionType = transactionType,
                                rawText = descriptionParts.joinToString("\n")
                            )
                        )
                    }
                }

                i = j
                continue
            }

            // Alternative: Try to find lines with amount at end
            val amountMatch = amountPattern.find(line)
            if (amountMatch != null && line.length > 20) {
                // Look for dates in this line
                val datePattern = Regex("""(\d{2}\.\d{2}\.)""")
                val dates = datePattern.findAll(line).toList()

                if (dates.size >= 2) {
                    // We have at least two dates
                    val bookingDateShort = dates[0].groupValues[1]
                    val valueDateShort = dates[1].groupValues[1]

                    // Check next line for year
                    var bookingYear = "2024"
                    var valueYear = "2024"
                    var additionalDesc = ""

                    if (i + 1 < lines.size) {
                        val nextLine = lines[i + 1].trim()
                        val yearMatch = yearLinePattern.find(nextLine)
                        if (yearMatch != null) {
                            bookingYear = yearMatch.groupValues[1]
                            valueYear = yearMatch.groupValues[2]
                            additionalDesc = yearMatch.groupValues[3].trim()
                            i++
                        }
                    }

                    val bookingDate = parseGermanDate("${bookingDateShort}$bookingYear")
                    val valueDate = parseGermanDate("${valueDateShort}$valueYear")

                    val sign = amountMatch.groupValues[1]
                    val amountStr = amountMatch.groupValues[2]
                    val amount = parseGermanAmount(amountStr)?.let {
                        if (sign == "-") -it else it
                    }

                    // Extract description (text between dates and amount)
                    val descStart = dates[1].range.last + 1
                    val descEnd = amountMatch.range.first
                    var description = if (descStart < descEnd) {
                        line.substring(descStart, descEnd).trim()
                    } else ""

                    if (additionalDesc.isNotBlank()) {
                        description = "$description $additionalDesc".trim()
                    }

                    // Collect continuation lines
                    val descParts = mutableListOf<String>()
                    if (description.isNotBlank()) descParts.add(description)

                    var j = i + 1
                    while (j < lines.size && j < i + 10) {
                        val contLine = lines[j].trim()
                        if (contLine.isEmpty() || firstLinePattern.containsMatchIn(contLine)) break
                        if (Regex("""^\d{2}\.\d{2}\.\s+\d{2}\.\d{2}\.""").containsMatchIn(contLine)) break
                        if (!isHeaderOrFooter(contLine)) {
                            descParts.add(contLine)
                        }
                        j++
                    }

                    if (bookingDate != null && amount != null) {
                        val fullDescription = descParts.joinToString(" ")
                            .replace(Regex("""\s+"""), " ")
                            .trim()

                        if (!isBalanceEntry(fullDescription, fullDescription) && fullDescription.isNotBlank()) {
                            transactions.add(
                                ParsedTransaction(
                                    bookingDate = bookingDate,
                                    valueDate = valueDate ?: bookingDate,
                                    amount = amount,
                                    currency = "EUR",
                                    description = fullDescription,
                                    counterpartyName = extractCounterparty(fullDescription),
                                    transactionType = detectTransactionType(fullDescription),
                                    rawText = fullDescription
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
}
