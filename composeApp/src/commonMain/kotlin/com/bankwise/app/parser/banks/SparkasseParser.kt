package com.bankwise.app.parser.banks

import com.bankwise.app.parser.ParseResult
import com.bankwise.app.parser.ParsedTransaction

/**
 * Parser for Sparkasse bank statements
 */
class SparkasseParser : GermanBankParser() {
    override val bankName = "Sparkasse"

    // CERTAIN: unique Sparkasse identifiers
    private val certainIdentifiers = listOf(
        "sparkasse",
        "kreissparkasse",
        "stadtsparkasse",
        "landessparkasse",
        "nasspa",   // Nassauische Sparkasse
        "naspa",    // Also Nassauische Sparkasse
        "haspa",    // Hamburger Sparkasse
        "ospa",     // Ostdeutsche Sparkasse
        "nospa",    // Nord-Ostsee Sparkasse
        "s-girostart"  // S-GiroStart account type (unique to Sparkasse)
    )

    // HIGH: strong indicators
    private val highIdentifiers = listOf(
        "spk ",     // Sparkasse abbreviation with space
        "helaba"    // Landesbank Hessen-Thüringen (Sparkasse group)
    )

    // MEDIUM: generic patterns that could appear in other statements
    private val mediumIdentifiers = listOf(
        "kontoauszug"  // Common German term, not unique to Sparkasse
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
        val sparkasseName = extractSparkasseName(pdfText) ?: "Sparkasse"
        val lines = pdfText.lines()
        val accountIban = extractIban(pdfText)
        val statementPeriod = extractStatementPeriod(pdfText)

        // Try Sparkasse-specific format first (Datum | Erläuterung | Betrag EUR)
        var transactions = parseSparkasseFormat(lines)

        // Fall back to generic German parsing if not enough transactions
        if (transactions.size < 3) {
            val genericTransactions = parseGermanStatement(pdfText, fileName, sparkasseName).transactions
            if (genericTransactions.size > transactions.size) {
                transactions = genericTransactions
            }
        }

        return if (transactions.isNotEmpty()) {
            ParseResult(
                success = true,
                bankName = sparkasseName,
                accountIban = accountIban,
                statementPeriod = statementPeriod,
                transactions = transactions
            )
        } else {
            ParseResult(
                success = false,
                bankName = sparkasseName,
                errorMessage = "Could not extract transactions from Sparkasse PDF"
            )
        }
    }

    /**
     * Parse Sparkasse-specific format:
     * Format 1: Date | Transaction Type | ... whitespace ... | Amount
     *           (indented continuation lines with description details)
     * Format 2: Date | Description | Amount
     * Format 3: Date | Transaction Type (amount on separate line due to PDF extraction)
     *                                                           -35,93
     *           Telefonica Germany GmbH...
     *
     * Example:
     * 23.12.2022 Lastschrift                                    -35,93
     *            Telefonica Germany GmbH + Co. OHG Kd-Nr.: 6086102872
     */
    private fun parseSparkasseFormat(lines: List<String>): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()

        // Date pattern at start of line: DD.MM.YYYY
        val datePattern = Regex("""^(\d{2}\.\d{2}\.\d{4})""")

        // Pre-process lines: normalize Unicode minus signs to regular hyphen
        val normalizedLines = lines.map { line ->
            line.replace('−', '-')  // Unicode minus U+2212
                .replace('–', '-')  // En-dash U+2013
                .replace('—', '-')  // Em-dash U+2014
                .replace('\u00AD', '-') // Soft hyphen
        }

        // German amount pattern - matches: -58,28 or 168,03 or -1.234,56
        val amountPattern = Regex("""(-?\d{1,3}(?:\.\d{3})*,\d{2})""")
        // Standalone amount line (just the amount, possibly with trailing punctuation like "-35,93.")
        val standaloneAmountPattern = Regex("""^\s*(-?\d{1,3}(?:\.\d{3})*,\d{2})[.\s]*$""")

        var i = 0
        while (i < normalizedLines.size) {
            val line = normalizedLines[i].trim()

            // Skip empty lines and headers
            if (line.isEmpty() || isSparkasseHeader(line)) {
                i++
                continue
            }

            // Skip balance lines ("Kontostand am...")
            if (line.contains("Kontostand am") || line.contains("Auszug Nr")) {
                i++
                continue
            }

            // Look for date at start of line
            val dateMatch = datePattern.find(line)
            if (dateMatch != null) {
                val dateStr = dateMatch.groupValues[1]
                val date = parseGermanDate(dateStr)

                if (date != null) {
                    // Find ALL amounts in the current line
                    val amounts = amountPattern.findAll(line).toList()
                    var transactionAmount: Double? = null
                    var descriptionFromFirstLine = ""

                    if (amounts.isNotEmpty()) {
                        // Take the LAST amount on the line (rightmost = transaction amount)
                        val lastAmountMatch = amounts.last()
                        transactionAmount = parseGermanAmount(lastAmountMatch.groupValues[1])

                        // Extract description - text between date and last amount
                        val afterDate = line.substring(dateMatch.range.last + 1)
                        val amountStartIndex = afterDate.lastIndexOf(lastAmountMatch.value)
                        descriptionFromFirstLine = if (amountStartIndex > 0) {
                            afterDate.substring(0, amountStartIndex).trim()
                        } else {
                            afterDate.replace(lastAmountMatch.value, "").trim()
                        }
                    } else {
                        // No amount on date line - get description, look for amount in following lines
                        descriptionFromFirstLine = line.substring(dateMatch.range.last + 1).trim()
                    }

                    // Collect continuation lines and look for amount if not found yet
                    val descriptionParts = mutableListOf<String>()
                    if (descriptionFromFirstLine.isNotBlank()) {
                        descriptionParts.add(descriptionFromFirstLine)
                    }

                    var j = i + 1
                    while (j < normalizedLines.size) {
                        val nextLine = normalizedLines[j]
                        val trimmedNext = nextLine.trim()

                        // Stop if empty line
                        if (trimmedNext.isEmpty()) {
                            break
                        }
                        // Stop if next line starts with a date (new transaction)
                        if (datePattern.find(trimmedNext) != null) {
                            break
                        }
                        // Stop if it's a header or balance line
                        if (isSparkasseHeader(trimmedNext) || trimmedNext.contains("Kontostand")) {
                            break
                        }

                        // Check if this line is JUST an amount (PDF extracts amounts on separate lines)
                        val standaloneMatch = standaloneAmountPattern.find(trimmedNext)
                        if (standaloneMatch != null && transactionAmount == null) {
                            transactionAmount = parseGermanAmount(standaloneMatch.groupValues[1])
                            j++
                            continue
                        }

                        // Check if amount is at the END of this line (rightmost position)
                        val lineAmounts = amountPattern.findAll(trimmedNext).toList()
                        if (lineAmounts.isNotEmpty() && transactionAmount == null) {
                            val lastAmt = lineAmounts.last()
                            val amtEndPos = trimmedNext.lastIndexOf(lastAmt.value) + lastAmt.value.length
                            // Amount is at end if within last 5 chars of line
                            if (amtEndPos >= trimmedNext.length - 5) {
                                transactionAmount = parseGermanAmount(lastAmt.groupValues[1])
                                // Add text before the amount to description
                                val textBefore = trimmedNext.substring(0, trimmedNext.lastIndexOf(lastAmt.value)).trim()
                                if (textBefore.isNotBlank()) {
                                    descriptionParts.add(textBefore)
                                }
                                j++
                                continue
                            }
                        }

                        // Regular continuation line - add to description
                        descriptionParts.add(trimmedNext)
                        j++
                    }

                    val fullDescription = descriptionParts.joinToString(" ").trim()

                    // Only add transaction if we found a valid amount
                    if (transactionAmount != null && transactionAmount != 0.0 && fullDescription.isNotBlank()) {
                        transactions.add(
                            ParsedTransaction(
                                bookingDate = date,
                                valueDate = date,
                                amount = transactionAmount,
                                currency = "EUR",
                                description = fullDescription.take(200),
                                counterpartyName = extractCounterpartyFromSparkasse(fullDescription),
                                transactionType = detectTransactionType(fullDescription),
                                rawText = lines[i]  // Use original line for raw text
                            )
                        )
                    }
                    i = j
                    continue
                }
            }
            i++
        }

        return transactions.distinctBy { "${it.bookingDate}_${it.amount}_${it.description.take(20)}" }
    }

    private fun extractCounterpartyFromSparkasse(description: String): String? {
        // For Lastschrift/Debitkartenlastschr., the counterparty is usually after the transaction type
        val parts = description.split(Regex("\\s+"), limit = 2)
        if (parts.size > 1) {
            // Skip transaction type, get first few words of actual description
            val afterType = parts[1]
            // Extract company name (usually first part before special chars or dates)
            val companyPattern = Regex("""^([A-Za-zÄÖÜäöüß\s.&-]+)""")
            companyPattern.find(afterType)?.let {
                val name = it.groupValues[1].trim()
                if (name.length > 2) return name.take(50)
            }
        }
        return extractCounterparty(description)
    }

    private fun isSparkasseHeader(line: String): Boolean {
        val lower = line.lowercase()
        return (lower.contains("datum") && lower.contains("erläuterung")) ||
            (lower.contains("datum") && lower.contains("betrag")) ||
            lower.contains("seite") && lower.contains("von") ||
            lower.contains("kontoauszug") && lower.contains("/") ||
            lower.contains("s-girostart") ||
            lower.contains("blz") && lower.contains("konto")
    }

    private fun extractSparkasseName(text: String): String? {
        // Look for specific Sparkasse names like "Sparkasse Essen" or "Kreissparkasse München"
        // Limit to max 3 words after "Sparkasse" to avoid capturing account owner names
        val patterns = listOf(
            // "Sparkasse Essen" or "Sparkasse Köln Bonn"
            Regex("""((?:Stadt|Kreis|Landes)?[Ss]parkasse\s+[A-Za-zäöüÄÖÜß]+(?:\s+[A-Za-zäöüÄÖÜß]+)?(?:\s+[A-Za-zäöüÄÖÜß]+)?)"""),
            // Just "Sparkasse" followed by city
            Regex("""([Ss]parkasse\s+[A-Za-zäöüÄÖÜß\-]+)""")
        )

        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                val name = match.groupValues[1].trim()
                // Validate: should be reasonable length and not contain common non-bank words
                val lower = name.lowercase()
                if (name.length in 10..35 &&
                    !lower.contains("herr") && !lower.contains("frau") &&
                    !lower.contains("straße") && !lower.contains("str.") &&
                    !lower.contains("gmbh") && !lower.contains("inhaber")) {
                    return name
                }
            }
        }

        // Fallback: just return "Sparkasse"
        return "Sparkasse"
    }
}
