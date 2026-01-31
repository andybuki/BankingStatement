package com.banking.statement.parser.banks

import com.banking.statement.parser.ParseResult
import com.banking.statement.parser.ParsedTransaction
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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

    // Month name mappings for date parsing (English and German)
    private val months = mapOf(
        // English
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
        "january" to 1, "february" to 2, "march" to 3, "april" to 4, "june" to 6,
        "july" to 7, "august" to 8, "september" to 9, "october" to 10, "november" to 11, "december" to 12,
        // German
        "januar" to 1, "februar" to 2, "märz" to 3, "mär" to 3, "april" to 4, "mai" to 5, "juni" to 6,
        "juli" to 7, "august" to 8, "september" to 9, "oktober" to 10, "november" to 11, "dezember" to 12
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
            // Strategy 1: English format with Money out/Money in columns
            var transactions = parseRevolutEnglishFormat(lines, currency)

            // Strategy 2: German format with Geldausgang/Geldeingang columns
            if (transactions.size < 3) {
                val germanTransactions = parseRevolutGermanFormat(lines, currency)
                if (germanTransactions.size > transactions.size) {
                    transactions = germanTransactions
                }
            }

            // Strategy 3: Generic table format
            if (transactions.size < 3) {
                val tableTransactions = parseRevolutTableFormat(lines, currency)
                if (tableTransactions.size > transactions.size) {
                    transactions = tableTransactions
                }
            }

            if (transactions.size < 3) {
                val lineTransactions = parseRevolutLineFormat(lines, currency)
                if (lineTransactions.size > transactions.size) {
                    transactions = lineTransactions
                }
            }

            if (transactions.size < 3) {
                val blockTransactions = parseRevolutBlockFormat(lines, currency)
                if (blockTransactions.size > transactions.size) {
                    transactions = blockTransactions
                }
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
     * Parse English Revolut format with columns: Date, Description, Money out, Money in, Balance
     *
     * Date format: "Mar 19, 2024" (Month Day, Year)
     * Amount format: €700.00 (period decimal, optional comma thousands)
     * - Money out = expenses (negative)
     * - Money in = income (positive)
     *
     * Multi-line descriptions include: Reference:, From:, To:, Card: lines
     */
    private fun parseRevolutEnglishFormat(lines: List<String>, currency: String): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()

        // Check for English column headers
        val hasEnglishHeader = lines.any {
            val lower = it.lowercase()
            (lower.contains("money out") && lower.contains("money in")) ||
                (lower.contains("date") && lower.contains("description") && lower.contains("balance"))
        }

        if (!hasEnglishHeader) {
            return transactions
        }

        // Date pattern: "Mar 19, 2024" or "March 19, 2024"
        val datePattern = Regex("""^(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+(\d{1,2}),?\s+(\d{4})""", RegexOption.IGNORE_CASE)

        // Amount pattern: €700.00 or €1,234.56
        val euroAmountPattern = Regex("""€([\d,]+\.\d{2})""")

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty() || isHeaderOrFooterEnglish(line)) {
                i++
                continue
            }

            // Look for date at start of line
            val dateMatch = datePattern.find(line)
            if (dateMatch != null) {
                val monthStr = dateMatch.groupValues[1].lowercase().take(3)
                val day = dateMatch.groupValues[2].toIntOrNull()
                val year = dateMatch.groupValues[3].toIntOrNull()
                val month = months[monthStr]

                // Skip if any date component is invalid
                if (day == null || year == null || month == null) {
                    i++
                    continue
                }

                val date = try {
                    LocalDate(year, month, day)
                } catch (e: Exception) {
                    i++
                    continue
                }

                // Extract description and amounts from the line
                val afterDate = line.substring(dateMatch.range.last + 1).trim()
                val amounts = euroAmountPattern.findAll(afterDate).toList()

                if (amounts.isNotEmpty()) {
                    // Description is text before first amount
                    val firstAmountMatch = euroAmountPattern.find(afterDate)
                    var description = if (firstAmountMatch != null && firstAmountMatch.range.first > 0) {
                        afterDate.substring(0, firstAmountMatch.range.first).trim()
                    } else {
                        afterDate
                    }

                    // Collect continuation lines (Reference:, From:, To:, Card:, Revolut Rate, etc.)
                    val descriptionParts = mutableListOf(description)
                    var j = i + 1
                    while (j < lines.size) {
                        val nextLine = lines[j].trim()
                        // Stop if empty line, new transaction (starts with date), or is header
                        if (nextLine.isEmpty() || datePattern.find(nextLine) != null || isHeaderOrFooterEnglish(nextLine)) {
                            break
                        }
                        // Continuation lines typically start with these prefixes or don't have amounts
                        val isContinuation = nextLine.startsWith("Reference:") ||
                            nextLine.startsWith("From:") ||
                            nextLine.startsWith("To:") ||
                            nextLine.startsWith("Card:") ||
                            nextLine.startsWith("Revolut Rate") ||
                            (!euroAmountPattern.containsMatchIn(nextLine) && nextLine.length < 100)

                        if (isContinuation) {
                            descriptionParts.add(nextLine)
                            j++
                        } else {
                            break
                        }
                    }

                    val fullDescription = descriptionParts.joinToString(" ").trim()

                    // Parse the transaction amount (first amount, not balance)
                    val amountStr = amounts.first().groupValues[1].replace(",", "")
                    val amountValue = amountStr.toDoubleOrNull() ?: 0.0

                    // Determine if income or expense based on description keywords
                    // "Payment from X" = income, everything else is typically expense
                    val lowerDesc = fullDescription.lowercase()
                    val isIncome = lowerDesc.contains("payment from") ||
                        lowerDesc.contains("received from") ||
                        lowerDesc.contains("refund") ||
                        lowerDesc.contains("cashback") ||
                        lowerDesc.contains("interest")

                    val finalAmount = if (isIncome) amountValue else -amountValue

                    if (fullDescription.length > 2 && amountValue > 0) {
                        transactions.add(
                            ParsedTransaction(
                                bookingDate = date,
                                valueDate = date,
                                amount = finalAmount,
                                currency = currency,
                                description = fullDescription.take(200),
                                counterpartyName = extractCounterpartyEnglish(fullDescription),
                                transactionType = detectTransactionTypeEnglish(fullDescription),
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

    private fun isHeaderOrFooterEnglish(line: String): Boolean {
        val lower = line.lowercase()
        return (lower.contains("date") && lower.contains("description") && lower.contains("balance")) ||
            (lower.contains("money out") && lower.contains("money in")) ||
            lower.contains("page") && lower.contains("of") ||
            lower.contains("statement") && lower.contains("period") ||
            lower.contains("account number") ||
            lower.contains("sort code")
    }

    private fun extractCounterpartyEnglish(description: String): String? {
        // "Payment from ANDREY BUCHMANN . NATALIA BUCHMANN" -> extract names
        val fromPattern = Regex("""(?:Payment from|Received from|From:)\s+([A-Za-z\s.]+?)(?:\s+Reference:|$)""", RegexOption.IGNORE_CASE)
        fromPattern.find(description)?.let { match ->
            return match.groupValues[1].trim().take(50)
        }

        // "To: Booking.com" or company name at start
        val toPattern = Regex("""(?:To:|Payment to)\s+([A-Za-z0-9\s.]+)""", RegexOption.IGNORE_CASE)
        toPattern.find(description)?.let { match ->
            return match.groupValues[1].trim().take(50)
        }

        // First meaningful words as fallback
        val words = description.split(Regex("\\s+"))
            .filter { it.length > 2 && !it.all { c -> c.isDigit() || c == '.' || c == ',' || c == '€' } }
            .take(3)
        return if (words.isNotEmpty()) words.joinToString(" ").take(50) else null
    }

    private fun detectTransactionTypeEnglish(description: String): String {
        val lower = description.lowercase()
        return when {
            lower.contains("payment from") || lower.contains("received from") -> "Payment Received"
            lower.contains("transfer") -> "Transfer"
            lower.contains("card:") || lower.contains("pos") -> "Card Payment"
            lower.contains("atm") || lower.contains("withdrawal") -> "ATM Withdrawal"
            lower.contains("exchange") || lower.contains("exchanged") -> "Exchange"
            lower.contains("top-up") || lower.contains("topup") -> "Top Up"
            lower.contains("refund") -> "Refund"
            lower.contains("fee") || lower.contains("charge") -> "Fee"
            lower.contains("subscription") -> "Subscription"
            lower.contains("interest") -> "Interest"
            lower.contains("booking") || lower.contains("hotel") || lower.contains("flight") -> "Travel"
            else -> "Payment"
        }
    }

    /**
     * Parse German Revolut format with columns: Datum, Beschreibung, Geldausgang, Geldeingang, Kontostand
     *
     * German Revolut uses: €2,000.00 format (comma = thousands, period = decimal)
     * - Geldausgang = money out (expenses)
     * - Geldeingang = money in (income)
     */
    private fun parseRevolutGermanFormat(lines: List<String>, currency: String): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()

        // German date pattern: DD.MM.YYYY
        val datePattern = Regex("""^(\d{2}\.\d{2}\.\d{4})""")
        // Euro amount pattern: €2,000.00 (comma thousands, period decimal)
        val euroAmountPattern = Regex("""€([\d,]+\.\d{2})""")

        // Detect if this is German format by checking for column headers
        val hasGermanHeader = lines.any {
            val lower = it.lowercase()
            lower.contains("geldausgang") || lower.contains("geldeingang") || lower.contains("kontotransaktionen")
        }

        if (!hasGermanHeader) {
            return transactions
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty() || isHeaderOrFooterGerman(line)) {
                i++
                continue
            }

            // Look for German date at start of line
            val dateMatch = datePattern.find(line)
            if (dateMatch != null) {
                val dateStr = dateMatch.groupValues[1]
                val parts = dateStr.split(".")
                val date = try {
                    LocalDate(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
                } catch (e: Exception) {
                    i++
                    continue
                }

                // Find all euro amounts on this line
                val amounts = euroAmountPattern.findAll(line).toList()

                if (amounts.isNotEmpty()) {
                    // Extract description - text between date and first amount
                    val afterDate = line.substring(dateMatch.range.last + 1).trim()
                    val firstAmountMatch = euroAmountPattern.find(afterDate)
                    var description = if (firstAmountMatch != null) {
                        afterDate.substring(0, firstAmountMatch.range.first).trim()
                    } else {
                        afterDate
                    }

                    // Collect continuation lines (multi-line descriptions like "Von: SANITA...")
                    val descriptionParts = mutableListOf(description)
                    var j = i + 1
                    while (j < lines.size) {
                        val nextLine = lines[j].trim()
                        // Stop if empty line, new transaction (starts with date), or is header
                        if (nextLine.isEmpty() || datePattern.find(nextLine) != null || isHeaderOrFooterGerman(nextLine)) {
                            break
                        }
                        // Check if this line is a continuation (starts with Von:, IBAN, etc.)
                        if (nextLine.startsWith("Von:") || nextLine.startsWith("An:") ||
                            nextLine.contains("DE") && nextLine.length < 50 ||
                            !euroAmountPattern.containsMatchIn(nextLine)) {
                            descriptionParts.add(nextLine)
                            j++
                        } else {
                            break
                        }
                    }

                    val fullDescription = descriptionParts.joinToString(" ").trim()

                    // Parse amount: €2,000.00 -> 2000.00
                    val amountStr = amounts.first().groupValues[1]
                        .replace(",", "")  // Remove thousands separator
                    val amountValue = amountStr.toDoubleOrNull() ?: 0.0

                    // Determine if income or expense based on German keywords and context
                    // Income keywords (German)
                    val lowerDesc = fullDescription.lowercase()
                    val isIncome = lowerDesc.contains("zahlung von") ||
                        lowerDesc.contains("von:") ||
                        lowerDesc.contains("payment from") ||
                        lowerDesc.contains("received") ||
                        lowerDesc.contains("gutschrift") ||
                        lowerDesc.contains("eingang") ||
                        lowerDesc.contains("überweisung von")

                    // Expense keywords (German)
                    val isExpense = lowerDesc.contains("transfer to") ||
                        lowerDesc.contains("überweisung an") ||
                        lowerDesc.contains("zahlung an") ||
                        lowerDesc.contains("lastschrift") ||
                        lowerDesc.contains("purchase") ||
                        lowerDesc.contains("kauf") ||
                        lowerDesc.contains("abbuchung")

                    val finalAmount = when {
                        isIncome -> amountValue
                        isExpense -> -amountValue
                        else -> -amountValue  // Default to expense if unknown
                    }

                    if (fullDescription.length > 2 && amountValue > 0) {
                        transactions.add(
                            ParsedTransaction(
                                bookingDate = date,
                                valueDate = date,
                                amount = finalAmount,
                                currency = currency,
                                description = fullDescription.take(200),
                                counterpartyName = extractCounterpartyGerman(fullDescription),
                                transactionType = detectTransactionTypeGerman(fullDescription),
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

    private fun isHeaderOrFooterGerman(line: String): Boolean {
        val lower = line.lowercase()
        return lower.contains("datum") && lower.contains("beschreibung") ||
            lower.contains("geldausgang") && lower.contains("geldeingang") ||
            lower.contains("kontostand") && lower.contains("geld") ||
            lower.contains("kontotransaktionen") && lower.contains("bis") ||
            lower.contains("seite") && lower.contains("von") ||
            lower.contains("page") && lower.contains("of")
    }

    private fun extractCounterpartyGerman(description: String): String? {
        // Try to extract counterparty from "Zahlung von NAME" or "Von: NAME, IBAN"
        val vonPattern = Regex("""(?:Zahlung von|Von:|Überweisung von)\s*([A-ZÄÖÜa-zäöüß\s]+)""", RegexOption.IGNORE_CASE)
        vonPattern.find(description)?.let { match ->
            return match.groupValues[1].trim().take(50)
        }

        val anPattern = Regex("""(?:Transfer to|Zahlung an|Überweisung an|An:)\s*([A-Za-z\s]+)""", RegexOption.IGNORE_CASE)
        anPattern.find(description)?.let { match ->
            return match.groupValues[1].trim().take(50)
        }

        // Fallback: first few meaningful words
        val words = description.split(Regex("\\s+"))
            .filter { it.length > 2 && !it.all { c -> c.isDigit() || c == '.' || c == ',' || c == '€' } }
            .take(3)
        return if (words.isNotEmpty()) words.joinToString(" ").take(50) else null
    }

    private fun detectTransactionTypeGerman(description: String): String {
        val lower = description.lowercase()
        return when {
            lower.contains("zahlung von") || lower.contains("payment from") -> "Eingehende Zahlung"
            lower.contains("transfer to") || lower.contains("überweisung") -> "Überweisung"
            lower.contains("lastschrift") -> "Lastschrift"
            lower.contains("kartenzahlung") || lower.contains("card payment") || lower.contains("pos") -> "Kartenzahlung"
            lower.contains("bargeld") || lower.contains("atm") || lower.contains("geldautomat") -> "Bargeld"
            lower.contains("purchase") || lower.contains("kauf") -> "Kauf"
            lower.contains("gebühr") || lower.contains("fee") -> "Gebühr"
            lower.contains("erstattung") || lower.contains("refund") -> "Erstattung"
            lower.contains("gutschrift") -> "Gutschrift"
            else -> "Transaktion"
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
                    val year = groups[3].toIntOrNull() ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
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
        // English: "1 August 2025 to 12 September 2025" or "1 Aug 2025 - 12 Sep 2025"
        // German: "1. August 2025 bis 12. September 2025" or "Kontotransaktionen von 1. August 2025 bis 12. September 2025"
        val periodPatterns = listOf(
            // German format with "von ... bis"
            Regex("""von\s+(\d{1,2}\.?\s+\w+\s+\d{4})\s+bis\s+(\d{1,2}\.?\s+\w+\s+\d{4})""", RegexOption.IGNORE_CASE),
            // English format with "to" or dashes
            Regex("""(\d{1,2}\.?\s+\w+\s+\d{4})\s*[-–to]+\s*(\d{1,2}\.?\s+\w+\s+\d{4})""", RegexOption.IGNORE_CASE)
        )
        for (pattern in periodPatterns) {
            pattern.find(text)?.let { match ->
                return "${match.groupValues[1]} - ${match.groupValues[2]}"
            }
        }
        return null
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

// DkbParser and SparkasseParser moved to GermanBankParsers.kt
