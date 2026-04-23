package com.banking.statement.parser.banks

import com.banking.statement.parser.ParsedTransaction
import kotlinx.datetime.LocalDate

// ============================================================
// Five format-specific parsing strategies for Revolut statements.
// Generic helpers (prefixed "revolut*") live in RevolutParsingHelpers.kt.
// ============================================================

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
internal fun parseRevolutEnglishFormat(lines: List<String>, currency: String): List<ParsedTransaction> {
    val transactions = mutableListOf<ParsedTransaction>()

    val hasEnglishHeader = lines.any {
        val lower = it.lowercase()
        (lower.contains("money out") && lower.contains("money in")) ||
            (lower.contains("date") && lower.contains("description") && lower.contains("balance"))
    }

    if (!hasEnglishHeader) {
        return transactions
    }

    val datePattern = Regex("""^(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+(\d{1,2}),?\s+(\d{4})""", RegexOption.IGNORE_CASE)
    val euroAmountPattern = Regex("""€([\d,]+\.\d{2})""")

    var i = 0
    while (i < lines.size) {
        val line = lines[i].trim()
        if (line.isEmpty() || revolutIsHeaderOrFooterEnglish(line)) {
            i++
            continue
        }

        val dateMatch = datePattern.find(line)
        if (dateMatch != null) {
            val monthStr = dateMatch.groupValues[1].lowercase().take(3)
            val day = dateMatch.groupValues[2].toIntOrNull()
            val year = dateMatch.groupValues[3].toIntOrNull()
            val month = revolutMonths[monthStr]

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

            val afterDate = line.substring(dateMatch.range.last + 1).trim()
            val amounts = euroAmountPattern.findAll(afterDate).toList()

            if (amounts.isNotEmpty()) {
                val firstAmountMatch = euroAmountPattern.find(afterDate)
                var description = if (firstAmountMatch != null && firstAmountMatch.range.first > 0) {
                    afterDate.substring(0, firstAmountMatch.range.first).trim()
                } else {
                    afterDate
                }

                val descriptionParts = mutableListOf(description)
                var j = i + 1
                while (j < lines.size) {
                    val nextLine = lines[j].trim()
                    if (nextLine.isEmpty() || datePattern.find(nextLine) != null || revolutIsHeaderOrFooterEnglish(nextLine)) {
                        break
                    }
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

                val amountStr = amounts.first().groupValues[1].replace(",", "")
                val amountValue = amountStr.toDoubleOrNull() ?: 0.0

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
                            counterpartyName = revolutExtractCounterpartyEnglish(fullDescription),
                            transactionType = revolutDetectTransactionTypeEnglish(fullDescription),
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

private fun revolutIsHeaderOrFooterEnglish(line: String): Boolean {
    val lower = line.lowercase()
    return (lower.contains("date") && lower.contains("description") && lower.contains("balance")) ||
        (lower.contains("money out") && lower.contains("money in")) ||
        lower.contains("page") && lower.contains("of") ||
        lower.contains("statement") && lower.contains("period") ||
        lower.contains("account number") ||
        lower.contains("sort code")
}

private fun revolutExtractCounterpartyEnglish(description: String): String? {
    val fromPattern = Regex("""(?:Payment from|Received from|From:)\s+([A-Za-z\s.]+?)(?:\s+Reference:|$)""", RegexOption.IGNORE_CASE)
    fromPattern.find(description)?.let { match ->
        return match.groupValues[1].trim().take(50)
    }

    val toPattern = Regex("""(?:To:|Payment to)\s+([A-Za-z0-9\s.]+)""", RegexOption.IGNORE_CASE)
    toPattern.find(description)?.let { match ->
        return match.groupValues[1].trim().take(50)
    }

    val words = description.split(Regex("\\s+"))
        .filter { it.length > 2 && !it.all { c -> c.isDigit() || c == '.' || c == ',' || c == '€' } }
        .take(3)
    return if (words.isNotEmpty()) words.joinToString(" ").take(50) else null
}

private fun revolutDetectTransactionTypeEnglish(description: String): String {
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
internal fun parseRevolutGermanFormat(lines: List<String>, currency: String): List<ParsedTransaction> {
    val transactions = mutableListOf<ParsedTransaction>()

    val datePattern = Regex("""^(\d{2}\.\d{2}\.\d{4})""")
    val euroAmountPattern = Regex("""€([\d,]+\.\d{2})""")

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
        if (line.isEmpty() || revolutIsHeaderOrFooterGerman(line)) {
            i++
            continue
        }

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

            val amounts = euroAmountPattern.findAll(line).toList()

            if (amounts.isNotEmpty()) {
                val afterDate = line.substring(dateMatch.range.last + 1).trim()
                val firstAmountMatch = euroAmountPattern.find(afterDate)
                var description = if (firstAmountMatch != null) {
                    afterDate.substring(0, firstAmountMatch.range.first).trim()
                } else {
                    afterDate
                }

                val descriptionParts = mutableListOf(description)
                var j = i + 1
                while (j < lines.size) {
                    val nextLine = lines[j].trim()
                    if (nextLine.isEmpty() || datePattern.find(nextLine) != null || revolutIsHeaderOrFooterGerman(nextLine)) {
                        break
                    }
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

                val amountStr = amounts.first().groupValues[1].replace(",", "")
                val amountValue = amountStr.toDoubleOrNull() ?: 0.0

                val lowerDesc = fullDescription.lowercase()
                val isIncome = lowerDesc.contains("zahlung von") ||
                    lowerDesc.contains("von:") ||
                    lowerDesc.contains("payment from") ||
                    lowerDesc.contains("received") ||
                    lowerDesc.contains("gutschrift") ||
                    lowerDesc.contains("eingang") ||
                    lowerDesc.contains("überweisung von")

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
                            counterpartyName = revolutExtractCounterpartyGerman(fullDescription),
                            transactionType = revolutDetectTransactionTypeGerman(fullDescription),
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

private fun revolutIsHeaderOrFooterGerman(line: String): Boolean {
    val lower = line.lowercase()
    return lower.contains("datum") && lower.contains("beschreibung") ||
        lower.contains("geldausgang") && lower.contains("geldeingang") ||
        lower.contains("kontostand") && lower.contains("geld") ||
        lower.contains("kontotransaktionen") && lower.contains("bis") ||
        lower.contains("seite") && lower.contains("von") ||
        lower.contains("page") && lower.contains("of")
}

private fun revolutExtractCounterpartyGerman(description: String): String? {
    val vonPattern = Regex("""(?:Zahlung von|Von:|Überweisung von)\s*([A-ZÄÖÜa-zäöüß\s]+)""", RegexOption.IGNORE_CASE)
    vonPattern.find(description)?.let { match ->
        return match.groupValues[1].trim().take(50)
    }

    val anPattern = Regex("""(?:Transfer to|Zahlung an|Überweisung an|An:)\s*([A-Za-z\s]+)""", RegexOption.IGNORE_CASE)
    anPattern.find(description)?.let { match ->
        return match.groupValues[1].trim().take(50)
    }

    val words = description.split(Regex("\\s+"))
        .filter { it.length > 2 && !it.all { c -> c.isDigit() || c == '.' || c == ',' || c == '€' } }
        .take(3)
    return if (words.isNotEmpty()) words.joinToString(" ").take(50) else null
}

private fun revolutDetectTransactionTypeGerman(description: String): String {
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
internal fun parseRevolutTableFormat(lines: List<String>, currency: String): List<ParsedTransaction> {
    val transactions = mutableListOf<ParsedTransaction>()

    val datePatternDMY = Regex("(\\d{1,2})\\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+(\\d{4})", RegexOption.IGNORE_CASE)
    val datePatternMDY = Regex("(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+(\\d{1,2}),?\\s+(\\d{4})", RegexOption.IGNORE_CASE)
    val datePatternNumeric = Regex("(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})")
    val datePatternISO = Regex("(\\d{4})[/.-](\\d{1,2})[/.-](\\d{1,2})")

    val amountPattern = Regex("([€£$])?\\s*(-?)\\s*(\\d{1,3}(?:[,']\\d{3})*(?:\\.\\d{2})?|\\d+(?:\\.\\d{2})?)")

    var i = 0
    while (i < lines.size) {
        val line = lines[i].trim()
        if (line.isEmpty() || revolutIsHeaderOrFooter(line)) {
            i++
            continue
        }

        val date = revolutExtractDate(line, datePatternDMY, datePatternMDY, datePatternNumeric, datePatternISO)

        if (date != null) {
            val amounts = amountPattern.findAll(line).toList()

            if (amounts.isNotEmpty()) {
                var description = line
                description = datePatternDMY.replace(description, "")
                description = datePatternMDY.replace(description, "")
                description = datePatternNumeric.replace(description, "")
                description = datePatternISO.replace(description, "")

                amounts.forEach { description = description.replace(it.value, "") }
                description = description.replace(Regex("[€£$]"), "").trim()
                description = description.replace(Regex("\\s+"), " ").trim()

                val additionalDesc = mutableListOf<String>()
                var j = i + 1
                while (j < lines.size && j < i + 3) {
                    val nextLine = lines[j].trim()
                    if (nextLine.isEmpty()) break
                    if (revolutExtractDate(nextLine, datePatternDMY, datePatternMDY, datePatternNumeric, datePatternISO) != null) break
                    if (!revolutIsHeaderOrFooter(nextLine) && !amountPattern.containsMatchIn(nextLine)) {
                        additionalDesc.add(nextLine)
                    }
                    j++
                }

                val fullDescription = if (additionalDesc.isNotEmpty()) {
                    "$description ${additionalDesc.joinToString(" ")}"
                } else description

                val amount = revolutParseAmount(amounts)

                if (amount != null && fullDescription.isNotBlank() && fullDescription.length > 2) {
                    transactions.add(
                        ParsedTransaction(
                            bookingDate = date,
                            valueDate = date,
                            amount = amount,
                            currency = currency,
                            description = fullDescription.take(200),
                            counterpartyName = revolutExtractCounterparty(fullDescription),
                            transactionType = revolutDetectTransactionType(fullDescription),
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
internal fun parseRevolutLineFormat(lines: List<String>, currency: String): List<ParsedTransaction> {
    val transactions = mutableListOf<ParsedTransaction>()

    val datePattern = Regex("(\\d{1,2})\\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*(?:\\s+(\\d{4}))?", RegexOption.IGNORE_CASE)
    val amountPattern = Regex("([+-])?\\s*([€£$])?\\s*(\\d{1,3}(?:[,']\\d{3})*\\.\\d{2})")

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || revolutIsHeaderOrFooter(trimmed)) continue

        val dateMatch = datePattern.find(trimmed)
        val amountMatches = amountPattern.findAll(trimmed).toList()

        if (dateMatch != null && amountMatches.isNotEmpty()) {
            val date = revolutParseDateMatch(dateMatch)
            val amount = revolutParseAmountMatches(amountMatches)

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
                            counterpartyName = revolutExtractCounterparty(description),
                            transactionType = revolutDetectTransactionType(description),
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
internal fun parseRevolutBlockFormat(lines: List<String>, currency: String): List<ParsedTransaction> {
    val transactions = mutableListOf<ParsedTransaction>()

    val datePattern = Regex("(\\d{1,2})\\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s*(\\d{4})?", RegexOption.IGNORE_CASE)
    val amountPattern = Regex("([+-])?\\s*([€£$])?\\s*(\\d{1,3}(?:[,']\\d{3})*\\.\\d{2})")

    val blocks = mutableListOf<String>()
    var currentBlock = StringBuilder()

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) {
            if (currentBlock.isNotEmpty()) {
                blocks.add(currentBlock.toString())
                currentBlock = StringBuilder()
            }
        } else if (!revolutIsHeaderOrFooter(trimmed)) {
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
            val date = revolutParseDateMatch(dateMatch)
            val amount = revolutParseAmountMatches(amountMatches)

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
                            counterpartyName = revolutExtractCounterparty(description),
                            transactionType = revolutDetectTransactionType(description),
                            rawText = block.take(100)
                        )
                    )
                }
            }
        }
    }

    return transactions.distinctBy { "${it.bookingDate}_${it.amount}_${it.description.take(20)}" }
}
