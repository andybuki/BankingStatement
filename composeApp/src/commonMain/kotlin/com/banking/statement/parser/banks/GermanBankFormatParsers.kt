package com.banking.statement.parser.banks

import com.banking.statement.parser.ParsedTransaction
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// ============================================================
// Format-specific parsing strategies for German bank statements.
// All helpers (date/amount/keyword/header/balance) live in
// GermanBankParsingUtils.kt.
// ============================================================

/**
 * Strategy 1: Parse table-like format with columns.
 * Common format: Date | Date | Description | Amount | Balance
 */
internal fun parseTableFormat(lines: List<String>): List<ParsedTransaction> {
    val transactions = mutableListOf<ParsedTransaction>()
    val datePattern = Regex("(\\d{2}\\.\\d{2}\\.(?:\\d{4}|\\d{2}))")
    val amountPattern = Regex("([+-]\\s*)?(-?\\d{1,3}(?:[.]\\d{3})*,\\d{2})\\s*(?:EUR|€)?")

    var i = 0
    while (i < lines.size) {
        val line = lines[i].trim()

        val dates = datePattern.findAll(line).toList()
        val amounts = amountPattern.findAll(line).toList()

        if (dates.isNotEmpty() && amounts.isNotEmpty()) {
            val bookingDate = parseGermanDate(normalizeYear(dates[0].groupValues[1]))
            val valueDate = if (dates.size > 1) {
                parseGermanDate(normalizeYear(dates[1].groupValues[1]))
            } else bookingDate

            val amountMatch = amounts[0]
            val signStr = amountMatch.groupValues[1].trim()
            val amountStr = amountMatch.groupValues[2]
            val sign = when {
                signStr.startsWith("-") -> -1.0
                signStr.startsWith("+") -> 1.0
                else -> 1.0
            }
            val amount = parseGermanAmount(amountStr)?.let { it * sign }

            if (bookingDate != null && amount != null) {
                var description = line
                dates.forEach { description = description.replace(it.value, "") }
                amounts.forEach { description = description.replace(it.value, "") }
                description = description.replace(Regex("[€\\s]+"), " ").trim()

                val additionalDesc = mutableListOf<String>()
                var j = i + 1
                while (j < lines.size && j < i + 5) {
                    val nextLine = lines[j].trim()
                    if (nextLine.isEmpty()) break
                    if (datePattern.containsMatchIn(nextLine) && amountPattern.containsMatchIn(nextLine)) break
                    if (!isHeaderOrFooter(nextLine)) {
                        additionalDesc.add(nextLine)
                    }
                    j++
                }

                val fullDescription = if (additionalDesc.isNotEmpty()) {
                    "$description ${additionalDesc.joinToString(" ")}"
                } else description

                if (!isBalanceEntry(fullDescription, line) && fullDescription.isNotBlank()) {
                    transactions.add(
                        ParsedTransaction(
                            bookingDate = bookingDate,
                            valueDate = valueDate ?: bookingDate,
                            amount = amount,
                            currency = "EUR",
                            description = fullDescription.trim(),
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
 * Strategy 2: Parse lines with date at start and amount at end.
 */
internal fun parseDateAmountLines(lines: List<String>): List<ParsedTransaction> {
    val transactions = mutableListOf<ParsedTransaction>()
    val dateStartPattern = Regex("^(\\d{2}\\.\\d{2}\\.(?:\\d{4}|\\d{2}))")
    val amountEndPattern = Regex("([+-]\\s*)?(-?\\d{1,3}(?:[.]\\d{3})*,\\d{2})\\s*(?:EUR|€)?\\s*$")

    var i = 0
    while (i < lines.size) {
        val line = lines[i].trim()

        val dateMatch = dateStartPattern.find(line)
        val amountMatch = amountEndPattern.find(line)

        if (dateMatch != null && amountMatch != null) {
            val date = parseGermanDate(normalizeYear(dateMatch.groupValues[1]))
            val signStr = amountMatch.groupValues[1]?.trim() ?: ""
            val amountStr = amountMatch.groupValues[2]
            val sign = when {
                signStr.startsWith("-") -> -1.0
                signStr.startsWith("+") -> 1.0
                else -> 1.0
            }
            val amount = parseGermanAmount(amountStr)?.let { it * sign }

            if (date != null && amount != null) {
                val description = line
                    .substring(dateMatch.range.last + 1, amountMatch.range.first)
                    .trim()

                val valueDateMatch = dateStartPattern.find(description)
                val cleanDescription = if (valueDateMatch != null) {
                    description.substring(valueDateMatch.range.last + 1).trim()
                } else description

                val valueDate = valueDateMatch?.let {
                    parseGermanDate(normalizeYear(it.groupValues[1]))
                } ?: date

                val additionalDesc = mutableListOf<String>()
                var j = i + 1
                while (j < lines.size && j < i + 5) {
                    val nextLine = lines[j].trim()
                    if (nextLine.isEmpty()) break
                    if (dateStartPattern.containsMatchIn(nextLine)) break
                    if (!isHeaderOrFooter(nextLine)) {
                        additionalDesc.add(nextLine)
                    }
                    j++
                }

                val fullDescription = if (additionalDesc.isNotEmpty()) {
                    "$cleanDescription ${additionalDesc.joinToString(" ")}"
                } else cleanDescription

                if (!isBalanceEntry(fullDescription, line) && fullDescription.isNotBlank()) {
                    transactions.add(
                        ParsedTransaction(
                            bookingDate = date,
                            valueDate = valueDate,
                            amount = amount,
                            currency = "EUR",
                            description = fullDescription.trim(),
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

    return transactions
}

/**
 * Strategy 3: Parse block format where transactions span multiple lines.
 */
internal fun parseBlockFormat(lines: List<String>): List<ParsedTransaction> {
    val transactions = mutableListOf<ParsedTransaction>()
    val datePattern = Regex("(\\d{2}\\.\\d{2}\\.(?:\\d{4}|\\d{2}))")
    val amountPattern = Regex("([+-]\\s*)?(-?\\d{1,3}(?:[.]\\d{3})*,\\d{2})\\s*(?:EUR|€)?")

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
        val dates = datePattern.findAll(block).toList()
        val amounts = amountPattern.findAll(block).toList()

        if (dates.isNotEmpty() && amounts.isNotEmpty()) {
            val bookingDate = parseGermanDate(normalizeYear(dates[0].groupValues[1]))
            val valueDate = if (dates.size > 1) {
                parseGermanDate(normalizeYear(dates[1].groupValues[1]))
            } else bookingDate

            val amountMatch = amounts[0]
            val signStr = amountMatch.groupValues[1]?.trim() ?: ""
            val amountStr = amountMatch.groupValues[2]
            val sign = when {
                signStr.startsWith("-") -> -1.0
                signStr.startsWith("+") -> 1.0
                else -> 1.0
            }
            val amount = parseGermanAmount(amountStr)?.let { it * sign }

            if (bookingDate != null && amount != null) {
                var description = block
                dates.forEach { description = description.replace(it.value, " ") }
                amounts.forEach { description = description.replace(it.value, " ") }
                description = description.replace(Regex("\\s+"), " ").trim()

                if (!isBalanceEntry(description, block) && description.length > 3) {
                    transactions.add(
                        ParsedTransaction(
                            bookingDate = bookingDate,
                            valueDate = valueDate ?: bookingDate,
                            amount = amount,
                            currency = "EUR",
                            description = description,
                            counterpartyName = extractCounterparty(description),
                            transactionType = detectTransactionType(description),
                            rawText = block
                        )
                    )
                }
            }
        }
    }

    return transactions.distinctBy { "${it.bookingDate}_${it.amount}_${it.description.take(20)}" }
}

/**
 * Strategy 4: Parse multi-line format where date, description, and amount are on separate lines.
 * Format examples:
 * - "04.03.2023" on line 1, "Kartenzahlung" on line 2, "- 2,18" on a later line
 * - "27.01./27.01. SDD Lastschr" with amount "- 48,37" on separate line
 */
internal fun parseMultiLineFormat(lines: List<String>): List<ParsedTransaction> {
    val transactions = mutableListOf<ParsedTransaction>()

    val dateOnlyPattern = Regex("^(\\d{2}\\.\\d{2}\\.(?:\\d{4}|\\d{2}))\\s*$")
    val datePairPattern = Regex("^(\\d{2}\\.\\d{2}\\.)/(\\d{2}\\.\\d{2}\\.)\\s*(.*)")
    val dateStartPattern = Regex("^(\\d{2}\\.\\d{2}\\.(?:\\d{4}|\\d{2}))")

    val amountOnlyPattern = Regex("^\\s*([+-])\\s*(\\d{1,3}(?:[.]\\d{3})*,\\d{2})\\s*(?:EUR|€)?\\s*$")
    val amountAnywherePattern = Regex("([+-])\\s*(\\d{1,3}(?:[.]\\d{3})*,\\d{2})\\s*(?:EUR|€)?")

    var i = 0
    while (i < lines.size) {
        val line = lines[i].trim()
        if (line.isEmpty() || isHeaderOrFooter(line)) {
            i++
            continue
        }

        var bookingDate: LocalDate? = null
        var valueDate: LocalDate? = null
        var transactionStartLine = i
        var descriptionParts = mutableListOf<String>()

        val dateOnlyMatch = dateOnlyPattern.find(line)
        if (dateOnlyMatch != null) {
            bookingDate = parseGermanDate(normalizeYear(dateOnlyMatch.groupValues[1]))
            valueDate = bookingDate
            i++
        } else {
            val datePairMatch = datePairPattern.find(line)
            if (datePairMatch != null) {
                val currentYear = Clock.System.now()
                    .toLocalDateTime(TimeZone.currentSystemDefault()).year
                bookingDate = parseGermanDate("${datePairMatch.groupValues[1]}$currentYear")
                valueDate = parseGermanDate("${datePairMatch.groupValues[2]}$currentYear")
                val remainingText = datePairMatch.groupValues[3].trim()
                if (remainingText.isNotEmpty()) {
                    descriptionParts.add(remainingText)
                }
                i++
            } else {
                val dateStartMatch = dateStartPattern.find(line)
                if (dateStartMatch != null) {
                    bookingDate = parseGermanDate(normalizeYear(dateStartMatch.groupValues[1]))
                    valueDate = bookingDate
                    val remainingText = line.substring(dateStartMatch.range.last + 1).trim()
                    if (remainingText.isNotEmpty() && !amountOnlyPattern.containsMatchIn(remainingText)) {
                        descriptionParts.add(remainingText)
                    }
                    i++
                } else {
                    i++
                    continue
                }
            }
        }

        if (bookingDate == null) {
            continue
        }

        var amount: Double? = null
        var amountSign = 1.0
        var rawText = line

        while (i < lines.size && i < transactionStartLine + 15) {
            val nextLine = lines[i].trim()

            if (nextLine.isNotEmpty() && i > transactionStartLine) {
                if (dateOnlyPattern.containsMatchIn(nextLine) ||
                    datePairPattern.containsMatchIn(nextLine) ||
                    (dateStartPattern.containsMatchIn(nextLine) && !amountAnywherePattern.containsMatchIn(nextLine))) {
                    break
                }
            }

            val amountOnlyMatch = amountOnlyPattern.find(nextLine)
            if (amountOnlyMatch != null) {
                amountSign = if (amountOnlyMatch.groupValues[1] == "-") -1.0 else 1.0
                amount = parseGermanAmount(amountOnlyMatch.groupValues[2])?.let { it * amountSign }
                rawText += "\n$nextLine"
                i++
                break
            }

            val amountMatch = amountAnywherePattern.find(nextLine)
            if (amountMatch != null && amount == null) {
                amountSign = if (amountMatch.groupValues[1] == "-") -1.0 else 1.0
                amount = parseGermanAmount(amountMatch.groupValues[2])?.let { it * amountSign }
                val descPart = nextLine.substring(0, amountMatch.range.first).trim()
                if (descPart.isNotEmpty() && !isHeaderOrFooter(descPart)) {
                    descriptionParts.add(descPart)
                }
                rawText += "\n$nextLine"
                i++
                break
            }

            if (nextLine.isNotEmpty() && !isHeaderOrFooter(nextLine)) {
                descriptionParts.add(nextLine)
                rawText += "\n$nextLine"
            }

            i++
        }

        if (amount != null) {
            val fullDescription = descriptionParts.joinToString(" ")
                .replace(Regex("\\s+"), " ")
                .trim()

            if (isBalanceEntry(fullDescription, rawText)) {
                continue
            }

            if (fullDescription.isNotEmpty() || transactions.none { it.bookingDate == bookingDate && it.amount == amount }) {
                transactions.add(
                    ParsedTransaction(
                        bookingDate = bookingDate,
                        valueDate = valueDate ?: bookingDate,
                        amount = amount,
                        currency = "EUR",
                        description = fullDescription.ifEmpty { "Buchung" },
                        counterpartyName = extractCounterparty(fullDescription),
                        transactionType = detectTransactionType(fullDescription),
                        rawText = rawText
                    )
                )
            }
        }
    }

    return transactions.distinctBy { "${it.bookingDate}_${it.amount}_${it.description.take(20)}" }
}

/**
 * Strategy 5: Comprehensive format parser for various German bank formats.
 * Handles: split dates, short dates, suffix signs (S/H/+/-), amounts on separate lines.
 */
internal fun parseComprehensiveFormat(lines: List<String>): List<ParsedTransaction> {
    val transactions = mutableListOf<ParsedTransaction>()

    val processedLines = preprocessLines(lines)

    val datePattern = Regex("""(\d{2}\.\d{2}\.(?:\d{4}|\d{2})?)""")
    val amountWithSignPattern = Regex(
        """([+-−–])?\s*(\d{1,3}(?:[.]\d{3})*,\d{2})\s*(?:€|EUR)?\s*([+-−–SH])?""",
        RegexOption.IGNORE_CASE
    )

    var i = 0
    while (i < processedLines.size) {
        val line = processedLines[i].trim()
        if (line.isEmpty() || isHeaderOrFooter(line)) {
            i++
            continue
        }

        val dateMatch = datePattern.find(line)
        if (dateMatch == null) {
            i++
            continue
        }

        val dateStr = normalizeYear(dateMatch.groupValues[1])
        val bookingDate = parseGermanDate(dateStr)
        if (bookingDate == null) {
            i++
            continue
        }

        val remainingAfterFirstDate = line.substring(dateMatch.range.last + 1)
        val secondDateMatch = datePattern.find(remainingAfterFirstDate)
        val valueDate = secondDateMatch?.let {
            parseGermanDate(normalizeYear(it.groupValues[1]))
        } ?: bookingDate

        val blockLines = mutableListOf(line)
        var j = i + 1
        while (j < processedLines.size && j < i + 12) {
            val nextLine = processedLines[j].trim()
            if (nextLine.isEmpty()) {
                j++
                continue
            }
            val nextDateMatch = datePattern.find(nextLine)
            if (nextDateMatch != null && nextDateMatch.range.first < 3) {
                val hasAmountOnLine = amountWithSignPattern.containsMatchIn(nextLine)
                if (!hasAmountOnLine || nextLine.length > 20) {
                    break
                }
            }
            if (!isHeaderOrFooter(nextLine)) {
                blockLines.add(nextLine)
            }
            j++
        }

        val blockText = blockLines.joinToString(" ")
        val amountMatch = amountWithSignPattern.findAll(blockText).lastOrNull()

        if (amountMatch != null) {
            val prefixSign = amountMatch.groupValues[1]
            val amountStr = amountMatch.groupValues[2]
            val suffixSign = amountMatch.groupValues[3].uppercase()

            val amountValue = parseGermanAmount(amountStr)
            if (amountValue != null) {
                var description = blockText
                datePattern.findAll(description).forEach {
                    description = description.replace(it.value, " ")
                }
                amountWithSignPattern.findAll(description).forEach {
                    description = description.replace(it.value, " ")
                }
                description = description
                    .replace(Regex("""[€SH+-−–]"""), " ")
                    .replace(Regex("""\s+"""), " ")
                    .trim()

                val isExpense = when {
                    prefixSign in listOf("-", "−", "–") -> true
                    prefixSign == "+" -> false
                    suffixSign in listOf("-", "−", "–") -> true
                    suffixSign == "+" -> false
                    suffixSign == "S" -> true
                    suffixSign == "H" -> false
                    else -> isExpenseFromContext(description)
                }

                val finalAmount = if (isExpense) -kotlin.math.abs(amountValue) else kotlin.math.abs(amountValue)
                val rawText = blockLines.joinToString("\n")

                if (isBalanceEntry(description, rawText)) {
                    i = j
                    continue
                }

                if (description.length > 2 || transactions.none { it.bookingDate == bookingDate && it.amount == finalAmount }) {
                    transactions.add(
                        ParsedTransaction(
                            bookingDate = bookingDate,
                            valueDate = valueDate,
                            amount = finalAmount,
                            currency = "EUR",
                            description = description.ifEmpty { "Buchung" },
                            counterpartyName = extractCounterparty(description),
                            transactionType = detectTransactionType(description),
                            rawText = rawText
                        )
                    )
                }
            }
        }

        i = j
    }

    return transactions.distinctBy { "${it.bookingDate}_${it.amount}_${it.description.take(20)}" }
}
