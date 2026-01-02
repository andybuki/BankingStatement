package com.banking.statement.parser.banks

import com.banking.statement.parser.ParseResult
import com.banking.statement.parser.ParsedTransaction
import kotlinx.datetime.LocalDate

/**
 * Parser for Revolut PDF statements
 * Handles multiple currencies and Revolut-specific date/amount formats
 */
class RevolutPdfParser : BankPdfParser {
    override val bankName = "Revolut"

    private val identifiers = listOf(
        "revolut",
        "revolt21",
        "revogb21",
        "revolut.com",
        "revolut ltd",
        "revolut payments"
    )

    // Month name mappings for date parsing
    private val months = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
        "january" to 1, "february" to 2, "march" to 3, "april" to 4, "june" to 6,
        "july" to 7, "august" to 8, "september" to 9, "october" to 10, "november" to 11, "december" to 12
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        try {
            val lines = pdfText.lines()
            val currency = detectCurrency(pdfText)
            val accountNumber = extractAccountNumber(pdfText)
            val statementPeriod = extractStatementPeriod(pdfText)

            // Try multiple parsing strategies
            var transactions = parseRevolutTableFormat(lines, currency)

            if (transactions.isEmpty()) {
                transactions = parseRevolutLineFormat(lines, currency)
            }

            if (transactions.isEmpty()) {
                transactions = parseRevolutBlockFormat(lines, currency)
            }

            return if (transactions.isNotEmpty()) {
                ParseResult(
                    success = true,
                    bankName = bankName,
                    accountIban = accountNumber,
                    statementPeriod = statementPeriod,
                    transactions = transactions
                )
            } else {
                ParseResult(
                    success = false,
                    bankName = bankName,
                    errorMessage = "Could not extract transactions from Revolut PDF. The PDF format may not be supported. Try exporting as CSV from the Revolut app."
                )
            }
        } catch (e: Exception) {
            return ParseResult(
                success = false,
                bankName = bankName,
                errorMessage = "Error parsing Revolut PDF: ${e.message}"
            )
        }
    }

    /**
     * Parse Revolut table format with columns: Date, Description, Money In, Money Out, Balance
     */
    private fun parseRevolutTableFormat(lines: List<String>, currency: String): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()

        // Date patterns: "25 Dec 2024", "Dec 25, 2024", "25/12/2024", "2024-12-25"
        val datePatternDMY = Regex("(\\d{1,2})\\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+(\\d{4})", RegexOption.IGNORE_CASE)
        val datePatternMDY = Regex("(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+(\\d{1,2}),?\\s+(\\d{4})", RegexOption.IGNORE_CASE)
        val datePatternNumeric = Regex("(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})")
        val datePatternISO = Regex("(\\d{4})[/.-](\\d{1,2})[/.-](\\d{1,2})")

        // Amount patterns: "1,234.56", "1234.56", "-1,234.56", "€1,234.56"
        val amountPattern = Regex("([€£$])?\\s*(-?)\\s*(\\d{1,3}(?:[,']\\d{3})*(?:\\.\\d{2})?|\\d+(?:\\.\\d{2})?)")

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty() || isHeaderOrFooter(line)) {
                i++
                continue
            }

            // Try to find a date in the line
            val date = extractDate(line, datePatternDMY, datePatternMDY, datePatternNumeric, datePatternISO)

            if (date != null) {
                // Found a date, now look for amounts
                val amounts = amountPattern.findAll(line).toList()

                if (amounts.isNotEmpty()) {
                    // Extract description - remove date and amounts from line
                    var description = line

                    // Remove date patterns
                    description = datePatternDMY.replace(description, "")
                    description = datePatternMDY.replace(description, "")
                    description = datePatternNumeric.replace(description, "")
                    description = datePatternISO.replace(description, "")

                    // Remove amounts
                    amounts.forEach { description = description.replace(it.value, "") }
                    description = description.replace(Regex("[€£$]"), "").trim()
                    description = description.replace(Regex("\\s+"), " ").trim()

                    // Collect additional description from following lines
                    val additionalDesc = mutableListOf<String>()
                    var j = i + 1
                    while (j < lines.size && j < i + 3) {
                        val nextLine = lines[j].trim()
                        if (nextLine.isEmpty()) break
                        if (extractDate(nextLine, datePatternDMY, datePatternMDY, datePatternNumeric, datePatternISO) != null) break
                        if (!isHeaderOrFooter(nextLine) && !amountPattern.containsMatchIn(nextLine)) {
                            additionalDesc.add(nextLine)
                        }
                        j++
                    }

                    val fullDescription = if (additionalDesc.isNotEmpty()) {
                        "$description ${additionalDesc.joinToString(" ")}"
                    } else description

                    // Determine amount - typically last amount before balance
                    val amount = parseAmount(amounts)

                    if (amount != null && fullDescription.isNotBlank() && fullDescription.length > 2) {
                        transactions.add(
                            ParsedTransaction(
                                bookingDate = date,
                                valueDate = date,
                                amount = amount,
                                currency = currency,
                                description = fullDescription.take(200),
                                counterpartyName = extractCounterparty(fullDescription),
                                transactionType = detectTransactionType(fullDescription),
                                rawText = line
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

    /**
     * Parse line-by-line format
     */
    private fun parseRevolutLineFormat(lines: List<String>, currency: String): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()

        val datePattern = Regex("(\\d{1,2})\\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*(?:\\s+(\\d{4}))?", RegexOption.IGNORE_CASE)
        val amountPattern = Regex("([+-])?\\s*([€£$])?\\s*(\\d{1,3}(?:[,']\\d{3})*\\.\\d{2})")

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || isHeaderOrFooter(trimmed)) continue

            val dateMatch = datePattern.find(trimmed)
            val amountMatches = amountPattern.findAll(trimmed).toList()

            if (dateMatch != null && amountMatches.isNotEmpty()) {
                val date = parseDateMatch(dateMatch)
                val amount = parseAmountMatches(amountMatches)

                if (date != null && amount != null) {
                    var description = trimmed
                        .replace(dateMatch.value, "")
                    amountMatches.forEach { description = description.replace(it.value, "") }
                    description = description.replace(Regex("[€£$+-]"), "").replace(Regex("\\s+"), " ").trim()

                    if (description.isNotBlank() && description.length > 2) {
                        transactions.add(
                            ParsedTransaction(
                                bookingDate = date,
                                valueDate = date,
                                amount = amount,
                                currency = currency,
                                description = description.take(200),
                                counterpartyName = extractCounterparty(description),
                                transactionType = detectTransactionType(description),
                                rawText = trimmed
                            )
                        )
                    }
                }
            }
        }

        return transactions.distinctBy { "${it.bookingDate}_${it.amount}_${it.description.take(20)}" }
    }

    /**
     * Parse block format where transactions span multiple lines
     */
    private fun parseRevolutBlockFormat(lines: List<String>, currency: String): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()

        val datePattern = Regex("(\\d{1,2})\\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s*(\\d{4})?", RegexOption.IGNORE_CASE)
        val amountPattern = Regex("([+-])?\\s*([€£$])?\\s*(\\d{1,3}(?:[,']\\d{3})*\\.\\d{2})")

        // Group lines into blocks
        val blocks = mutableListOf<String>()
        var currentBlock = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                if (currentBlock.isNotEmpty()) {
                    blocks.add(currentBlock.toString())
                    currentBlock = StringBuilder()
                }
            } else if (!isHeaderOrFooter(trimmed)) {
                if (currentBlock.isNotEmpty()) currentBlock.append(" ")
                currentBlock.append(trimmed)
            }
        }
        if (currentBlock.isNotEmpty()) {
            blocks.add(currentBlock.toString())
        }

        for (block in blocks) {
            val dateMatch = datePattern.find(block)
            val amountMatches = amountPattern.findAll(block).toList()

            if (dateMatch != null && amountMatches.isNotEmpty()) {
                val date = parseDateMatch(dateMatch)
                val amount = parseAmountMatches(amountMatches)

                if (date != null && amount != null) {
                    var description = block
                        .replace(dateMatch.value, "")
                    amountMatches.forEach { description = description.replace(it.value, "") }
                    description = description.replace(Regex("[€£$+-]"), "").replace(Regex("\\s+"), " ").trim()

                    if (description.length > 3) {
                        transactions.add(
                            ParsedTransaction(
                                bookingDate = date,
                                valueDate = date,
                                amount = amount,
                                currency = currency,
                                description = description.take(200),
                                counterpartyName = extractCounterparty(description),
                                transactionType = detectTransactionType(description),
                                rawText = block.take(100)
                            )
                        )
                    }
                }
            }
        }

        return transactions.distinctBy { "${it.bookingDate}_${it.amount}_${it.description.take(20)}" }
    }

    private fun extractDate(
        line: String,
        datePatternDMY: Regex,
        datePatternMDY: Regex,
        datePatternNumeric: Regex,
        datePatternISO: Regex
    ): LocalDate? {
        // Try DMY format: "25 Dec 2024"
        datePatternDMY.find(line)?.let { match ->
            return parseDateMatch(match)
        }

        // Try MDY format: "Dec 25, 2024"
        datePatternMDY.find(line)?.let { match ->
            val monthStr = match.groupValues[1].lowercase().take(3)
            val day = match.groupValues[2].toIntOrNull() ?: return null
            val year = match.groupValues[3].toIntOrNull() ?: return null
            val month = months[monthStr] ?: return null
            return try { LocalDate(year, month, day) } catch (e: Exception) { null }
        }

        // Try numeric format: "25/12/2024"
        datePatternNumeric.find(line)?.let { match ->
            val first = match.groupValues[1].toIntOrNull() ?: return null
            val second = match.groupValues[2].toIntOrNull() ?: return null
            val year = match.groupValues[3].toIntOrNull() ?: return null
            // Assume DD/MM/YYYY for Revolut (European format)
            return try { LocalDate(year, second, first) } catch (e: Exception) { null }
        }

        // Try ISO format: "2024-12-25"
        datePatternISO.find(line)?.let { match ->
            val year = match.groupValues[1].toIntOrNull() ?: return null
            val month = match.groupValues[2].toIntOrNull() ?: return null
            val day = match.groupValues[3].toIntOrNull() ?: return null
            return try { LocalDate(year, month, day) } catch (e: Exception) { null }
        }

        return null
    }

    private fun parseDateMatch(match: MatchResult): LocalDate? {
        return try {
            val groups = match.groupValues
            when {
                // "25 Dec 2024" format
                groups.size >= 4 && groups[2].length >= 3 -> {
                    val day = groups[1].toIntOrNull() ?: return null
                    val monthStr = groups[2].lowercase().take(3)
                    val year = groups[3].toIntOrNull() ?: kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).year
                    val month = months[monthStr] ?: return null
                    LocalDate(year, month, day)
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseAmount(amounts: List<MatchResult>): Double? {
        if (amounts.isEmpty()) return null

        // For Revolut, often we have Money In and Money Out columns
        // Take the first non-zero amount, or determine by sign
        for (match in amounts) {
            val sign = if (match.groupValues[2] == "-") -1 else 1
            val amountStr = match.groupValues[3]
                .replace(",", "")
                .replace("'", "")
            val amount = amountStr.toDoubleOrNull()
            if (amount != null && amount != 0.0) {
                return amount * sign
            }
        }
        return null
    }

    private fun parseAmountMatches(amounts: List<MatchResult>): Double? {
        if (amounts.isEmpty()) return null

        for (match in amounts) {
            val signStr = match.groupValues[1]
            val sign = when (signStr) {
                "-" -> -1
                "+" -> 1
                else -> -1  // Default to expense for Revolut
            }
            val amountStr = match.groupValues[3]
                .replace(",", "")
                .replace("'", "")
            val amount = amountStr.toDoubleOrNull()
            if (amount != null && amount != 0.0) {
                return amount * sign
            }
        }
        return null
    }

    private fun detectCurrency(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("£") || lower.contains("gbp") || lower.contains("pound") -> "GBP"
            lower.contains("$") || lower.contains("usd") -> "USD"
            lower.contains("chf") -> "CHF"
            lower.contains("pln") || lower.contains("zł") -> "PLN"
            else -> "EUR"  // Default
        }
    }

    private fun extractAccountNumber(text: String): String? {
        // IBAN pattern
        val ibanPattern = Regex("([A-Z]{2}\\d{2}[\\s]?(?:[A-Z0-9]{4}[\\s]?){2,7}[A-Z0-9]{1,4})")
        val match = ibanPattern.find(text.uppercase())
        return match?.groupValues?.get(1)?.replace("\\s".toRegex(), "")
    }

    private fun extractStatementPeriod(text: String): String? {
        val periodPattern = Regex("(\\d{1,2}\\s+\\w+\\s+\\d{4})\\s*[-–to]+\\s*(\\d{1,2}\\s+\\w+\\s+\\d{4})", RegexOption.IGNORE_CASE)
        val match = periodPattern.find(text)
        return match?.let { "${it.groupValues[1]} - ${it.groupValues[2]}" }
    }

    private fun extractCounterparty(description: String): String? {
        val words = description.split(Regex("\\s+"))
            .filter { it.length > 2 && !it.all { c -> c.isDigit() || c == '.' || c == ',' } }
            .take(5)
        return if (words.isNotEmpty()) words.joinToString(" ").take(50) else null
    }

    private fun detectTransactionType(description: String): String {
        val lower = description.lowercase()
        return when {
            lower.contains("card payment") || lower.contains("pos") -> "Card Payment"
            lower.contains("transfer") || lower.contains("sent") -> "Transfer"
            lower.contains("top-up") || lower.contains("topup") -> "Top Up"
            lower.contains("exchange") || lower.contains("exchanged") -> "Exchange"
            lower.contains("received") -> "Received"
            lower.contains("atm") || lower.contains("withdraw") -> "ATM"
            lower.contains("refund") -> "Refund"
            lower.contains("subscription") -> "Subscription"
            lower.contains("fee") || lower.contains("charge") -> "Fee"
            else -> "Transaction"
        }
    }

    private fun isHeaderOrFooter(line: String): Boolean {
        val lower = line.lowercase()
        return lower.contains("page") && lower.contains("of") ||
               lower.contains("statement") && lower.contains("account") ||
               lower.contains("opening balance") ||
               lower.contains("closing balance") ||
               lower.contains("date") && lower.contains("description") && lower.contains("amount") ||
               lower.contains("money in") && lower.contains("money out") ||
               lower.contains("total") && lower.contains("balance") ||
               lower.contains("revolut ltd") ||
               lower.contains("generated on") ||
               line.length < 5
    }
}

/**
 * Parser for DKB (Deutsche Kreditbank) statements
 */
class DkbParser : GermanBankParser() {
    override val bankName = "DKB"

    private val identifiers = listOf(
        "deutsche kreditbank",
        "dkb",
        "byladem1",
        "dkb.de"
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) } &&
               (lower.contains("kontoauszug") || lower.contains("kreditkarte") || lower.contains("girokonto"))
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "DKB")
    }
}

/**
 * Parser for Sparkasse statements
 */
class SparkasseParser : GermanBankParser() {
    override val bankName = "Sparkasse"

    // Sparkassen have many local BICs, but share common patterns
    private val identifiers = listOf(
        "sparkasse",
        "spk ",
        "landesbank",
        "lbbw",
        "helaba",
        "naspa"
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) } &&
               (lower.contains("kontoauszug") || lower.contains("girokonto"))
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "Sparkasse")
    }
}
