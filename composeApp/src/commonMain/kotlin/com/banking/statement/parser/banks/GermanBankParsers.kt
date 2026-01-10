package com.banking.statement.parser.banks

import com.banking.statement.parser.ParseResult
import com.banking.statement.parser.ParsedTransaction
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Base class for German bank parsers with common parsing logic
 */
abstract class GermanBankParser : BankPdfParser {

    // Common German transaction type keywords
    protected val germanTransactionTypes = listOf(
        "Lastschrift", "Gutschrift", "Überweisung", "Dauerauftrag",
        "Gehalt", "Lohn", "Kartenzahlung", "Bargeldauszahlung",
        "Abschluss", "Zinsen", "Entgelt", "Einzahlung", "Auszahlung",
        "SEPA-Lastschrift", "SEPA-Überweisung", "Kontoführung",
        "Geldautomat", "Echtzeitüberweisung", "Abrechnung",
        "Barauszahlung", "Bareinzahlung", "Scheckeinreichung",
        "Wertpapier", "Dividende", "Zinsabschluss",
        "SEPA Lastschrifteinzug", "SEPA-Basislastschrift", "Basislastschrift",
        "Zahlungseingang", "SDD Lastschr", "Wertstellung"
    )

    // Keywords that indicate INCOME (positive amount)
    protected val incomeKeywords = listOf(
        "gutschrift", "zahlungseingang", "gehalt", "lohn", "einzahlung",
        "geldeingang", "eingang", "haben", "überweisung von", "zahlung von",
        "erstattung", "rückerstattung", "zinsen", "dividende", "bonus"
    )

    // Keywords that indicate EXPENSE (negative amount)
    protected val expenseKeywords = listOf(
        "lastschrift", "abbuchung", "auszahlung", "geldausgang", "ausgang",
        "soll", "kartenzahlung", "überweisung an", "zahlung an", "entgelt",
        "gebühr", "kosten"
    )

    // ============================================================
    // German Banking Field Patterns (ported from jejik-mt940 PHP)
    // These patterns extract structured fields from transaction descriptions
    // ============================================================

    /**
     * Extract EREF (End-to-end reference) from description
     * Pattern: EREF+ followed by reference text
     */
    protected fun extractEref(text: String): String? {
        val pattern = Regex("""EREF\+([A-Za-z0-9äöüÄÖÜß./?\-\s,]+?)(?:\s+[A-Z]{4}\+|$)""", RegexOption.IGNORE_CASE)
        return pattern.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Extract KREF (Customer reference) from description
     * Pattern: KREF+ followed by reference text
     */
    protected fun extractKref(text: String): String? {
        val pattern = Regex("""KREF\+([A-Za-z0-9äöüÄÖÜß./?\-\s,]+?)(?:\s+[A-Z]{4}\+|$)""", RegexOption.IGNORE_CASE)
        return pattern.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Extract MREF (Mandate reference) from description
     * Pattern: MREF+ followed by mandate ID
     */
    protected fun extractMref(text: String): String? {
        val pattern = Regex("""MREF\+([A-Za-z0-9äöüÄÖÜß./?\-\s,]+?)(?:\s+[A-Z]{4}\+|$)""", RegexOption.IGNORE_CASE)
        return pattern.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Extract CRED (Creditor identifier) from description
     * Pattern: CRED+ followed by creditor ID (usually DE...)
     */
    protected fun extractCred(text: String): String? {
        val pattern = Regex("""CRED\+([A-Za-z0-9äöüÄÖÜß./?\-\s,]+?)(?:\s+[A-Z]{4}\+|$)""", RegexOption.IGNORE_CASE)
        return pattern.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Extract SVWZ (Verwendungszweck / Payment purpose) from description
     * This is the main payment description text
     */
    protected fun extractSvwz(text: String): String? {
        val pattern = Regex("""SVWZ\+(.+?)(?:\s+[A-Z]{4}\+|$)""", RegexOption.IGNORE_CASE)
        return pattern.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Extract IBAN from description
     */
    protected fun extractIbanFromDesc(text: String): String? {
        val pattern = Regex("""IBAN\+?:?\s*([A-Z]{2}\d{2}[A-Z0-9]{4,30})""", RegexOption.IGNORE_CASE)
        return pattern.find(text)?.groupValues?.get(1)?.uppercase()?.takeIf { it.length >= 15 }
    }

    /**
     * Extract BIC from description
     */
    protected fun extractBicFromDesc(text: String): String? {
        val pattern = Regex("""BIC\+?:?\s*([A-Z]{4}[A-Z]{2}[A-Z0-9]{2}(?:[A-Z0-9]{3})?)""", RegexOption.IGNORE_CASE)
        return pattern.find(text)?.groupValues?.get(1)?.uppercase()?.takeIf { it.length >= 8 }
    }

    /**
     * Extract account holder name from description
     * Common patterns: "Auftraggeber:" "Empfänger:" or just name after IBAN
     */
    protected fun extractAccountHolder(text: String): String? {
        // Try various patterns
        val patterns = listOf(
            Regex("""(?:Auftraggeber|Empfänger|Zahlungsempfänger|Zahlungspflichtiger)[:\s]+([A-Za-zäöüÄÖÜß\s.\-]+?)(?:\s+IBAN|\s+DE\d|$)""", RegexOption.IGNORE_CASE),
            Regex("""(?:von|an)[:\s]+([A-Za-zäöüÄÖÜß\s.\-]{3,50})""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                return match.groupValues[1].trim().takeIf { it.length > 2 }
            }
        }
        return null
    }

    /**
     * Parse all German banking fields from a description text
     * Returns a structured result with all extracted fields
     */
    protected data class GermanTransactionFields(
        val eref: String? = null,
        val kref: String? = null,
        val mref: String? = null,
        val cred: String? = null,
        val svwz: String? = null,
        val iban: String? = null,
        val bic: String? = null,
        val accountHolder: String? = null
    )

    protected fun parseGermanFields(text: String): GermanTransactionFields {
        return GermanTransactionFields(
            eref = extractEref(text),
            kref = extractKref(text),
            mref = extractMref(text),
            cred = extractCred(text),
            svwz = extractSvwz(text),
            iban = extractIbanFromDesc(text),
            bic = extractBicFromDesc(text),
            accountHolder = extractAccountHolder(text)
        )
    }

    /**
     * Build a clean description from German fields
     * Prioritizes SVWZ (purpose) over raw text
     */
    protected fun buildCleanDescription(rawDescription: String): String {
        val svwz = extractSvwz(rawDescription)
        if (svwz != null && svwz.length > 5) {
            return svwz
        }

        // Clean up the raw description by removing field markers
        var cleaned = rawDescription
        listOf("EREF+", "KREF+", "MREF+", "CRED+", "SVWZ+", "DEBT+", "IBAN+", "BIC+").forEach { marker ->
            cleaned = cleaned.replace(Regex("""$marker[^\s]*\s*""", RegexOption.IGNORE_CASE), " ")
        }
        return cleaned.replace(Regex("""\s+"""), " ").trim()
    }

    /**
     * Extract counterparty name using German banking patterns
     */
    protected fun extractCounterpartyGerman(description: String): String? {
        // First try to get account holder from structured fields
        val holder = extractAccountHolder(description)
        if (holder != null) return holder

        // Look for common patterns
        val patterns = listOf(
            // "PayPal Europe S.a.r.l. et Cie S.C.A"
            Regex("""^([A-Za-zäöüÄÖÜß][A-Za-zäöüÄÖÜß\s.\-&]+(?:GmbH|AG|KG|e\.V\.|S\.A\.|Ltd|Inc|SE))""", RegexOption.IGNORE_CASE),
            // First meaningful words before special chars or markers
            Regex("""^([A-Za-zäöüÄÖÜß][A-Za-zäöüÄÖÜß\s]{2,40})(?:\s+DE\d|\s+EREF|\s+SVWZ|/|,)""")
        )

        for (pattern in patterns) {
            val match = pattern.find(description)
            if (match != null) {
                val name = match.groupValues[1].trim()
                if (name.length > 2 && !germanTransactionTypes.any { name.equals(it, ignoreCase = true) }) {
                    return name
                }
            }
        }

        return null
    }

    /**
     * Generic German bank statement parser
     * Tries multiple strategies to extract transactions
     */
    protected fun parseGermanStatement(
        pdfText: String,
        fileName: String,
        bankIdentifier: String
    ): ParseResult {
        try {
            val lines = pdfText.lines()
            val accountIban = extractIban(pdfText)
            val statementPeriod = extractStatementPeriod(pdfText)

            // Try multiple parsing strategies - start with comprehensive
            var transactions = parseComprehensiveFormat(lines)

            if (transactions.size < 3) {
                val multiLineTransactions = parseMultiLineFormat(lines)
                if (multiLineTransactions.size > transactions.size) {
                    transactions = multiLineTransactions
                }
            }

            if (transactions.size < 3) {
                val tableTransactions = parseTableFormat(lines)
                if (tableTransactions.size > transactions.size) {
                    transactions = tableTransactions
                }
            }

            if (transactions.size < 3) {
                val dateAmountTransactions = parseDateAmountLines(lines)
                if (dateAmountTransactions.size > transactions.size) {
                    transactions = dateAmountTransactions
                }
            }

            if (transactions.size < 3) {
                val blockTransactions = parseBlockFormat(lines)
                if (blockTransactions.size > transactions.size) {
                    transactions = blockTransactions
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
                val sampleLines = lines.filter { it.isNotBlank() }.take(30).joinToString("\n")
                ParseResult(
                    success = false,
                    bankName = bankName,
                    errorMessage = "Could not extract transactions from $bankName PDF. Sample:\n$sampleLines"
                )
            }
        } catch (e: Exception) {
            return ParseResult(
                success = false,
                bankName = bankName,
                errorMessage = "Error parsing $bankName PDF: ${e.message}"
            )
        }
    }

    /**
     * Strategy 1: Parse table-like format with columns
     * Common format: Date | Date | Description | Amount | Balance
     */
    protected fun parseTableFormat(lines: List<String>): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()
        val datePattern = Regex("(\\d{2}\\.\\d{2}\\.(?:\\d{4}|\\d{2}))")
        // Updated pattern to capture signed amounts: "- 250,00" or "+ 1.043,44" or "-250,00"
        val amountPattern = Regex("([+-]\\s*)?(-?\\d{1,3}(?:[.]\\d{3})*,\\d{2})\\s*(?:EUR|€)?")

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            // Look for lines with at least one date and one amount
            val dates = datePattern.findAll(line).toList()
            val amounts = amountPattern.findAll(line).toList()

            if (dates.isNotEmpty() && amounts.isNotEmpty()) {
                val bookingDate = parseGermanDate(normalizeYear(dates[0].groupValues[1]))
                val valueDate = if (dates.size > 1) {
                    parseGermanDate(normalizeYear(dates[1].groupValues[1]))
                } else bookingDate

                // Take the first amount as transaction amount (second might be balance)
                // Handle signed amounts: "- 250,00" or "+ 1.043,44"
                val amountMatch = amounts[0]
                val signStr = amountMatch.groupValues[1].trim()
                val amountStr = amountMatch.groupValues[2]
                val sign = when {
                    signStr.startsWith("-") -> -1.0
                    signStr.startsWith("+") -> 1.0
                    else -> 1.0 // default to positive if no explicit sign
                }
                val amount = parseGermanAmount(amountStr)?.let { it * sign }

                if (bookingDate != null && amount != null) {
                    // Extract description - text between dates and amount
                    var description = line
                    dates.forEach { description = description.replace(it.value, "") }
                    amounts.forEach { description = description.replace(it.value, "") }
                    description = description.replace(Regex("[€\\s]+"), " ").trim()

                    // Collect additional description from following lines
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

                    // Skip balance entries
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
     * Strategy 2: Parse lines with date at start and amount at end
     */
    protected fun parseDateAmountLines(lines: List<String>): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()
        val dateStartPattern = Regex("^(\\d{2}\\.\\d{2}\\.(?:\\d{4}|\\d{2}))")
        // Updated pattern to capture signed amounts at end of line
        val amountEndPattern = Regex("([+-]\\s*)?(-?\\d{1,3}(?:[.]\\d{3})*,\\d{2})\\s*(?:EUR|€)?\\s*$")

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            val dateMatch = dateStartPattern.find(line)
            val amountMatch = amountEndPattern.find(line)

            if (dateMatch != null && amountMatch != null) {
                val date = parseGermanDate(normalizeYear(dateMatch.groupValues[1]))
                // Handle signed amounts
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

                    // Check for second date (value date)
                    val valueDateMatch = dateStartPattern.find(description)
                    val cleanDescription = if (valueDateMatch != null) {
                        description.substring(valueDateMatch.range.last + 1).trim()
                    } else description

                    val valueDate = valueDateMatch?.let {
                        parseGermanDate(normalizeYear(it.groupValues[1]))
                    } ?: date

                    // Collect additional description lines
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

                    // Skip balance entries
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
     * Strategy 3: Parse block format where transactions span multiple lines
     */
    protected fun parseBlockFormat(lines: List<String>): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()
        val datePattern = Regex("(\\d{2}\\.\\d{2}\\.(?:\\d{4}|\\d{2}))")
        // Updated pattern to capture signed amounts
        val amountPattern = Regex("([+-]\\s*)?(-?\\d{1,3}(?:[.]\\d{3})*,\\d{2})\\s*(?:EUR|€)?")

        // Group lines into blocks separated by empty lines
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

                // Handle signed amounts
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

                    // Skip balance entries
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
     * Strategy 4: Parse multi-line format where date, description, and amount are on separate lines
     * Format examples:
     * - "04.03.2023" on line 1, "Kartenzahlung" on line 2, "- 2,18" on a later line
     * - "27.01./27.01. SDD Lastschr" with amount "- 48,37" on separate line
     */
    protected fun parseMultiLineFormat(lines: List<String>): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()

        // Date patterns: "04.03.2023" or "27.01./27.01." (booking/value date)
        val dateOnlyPattern = Regex("^(\\d{2}\\.\\d{2}\\.(?:\\d{4}|\\d{2}))\\s*$")
        val datePairPattern = Regex("^(\\d{2}\\.\\d{2}\\.)/(\\d{2}\\.\\d{2}\\.)\\s*(.*)")
        val dateStartPattern = Regex("^(\\d{2}\\.\\d{2}\\.(?:\\d{4}|\\d{2}))")

        // Amount patterns: "- 250,00", "+ 1.043,44", "- 250,00 EUR"
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

            // Check for date-only line: "04.03.2023"
            val dateOnlyMatch = dateOnlyPattern.find(line)
            if (dateOnlyMatch != null) {
                bookingDate = parseGermanDate(normalizeYear(dateOnlyMatch.groupValues[1]))
                valueDate = bookingDate
                i++
            }
            // Check for date pair: "27.01./27.01. SDD Lastschr"
            else {
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
                }
                // Check for date at start of line: "04.03.2023 Kartenzahlung"
                else {
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

            // Now collect description lines and find the amount
            var amount: Double? = null
            var amountSign = 1.0
            var rawText = line

            // Look at following lines for description and amount
            while (i < lines.size && i < transactionStartLine + 15) {
                val nextLine = lines[i].trim()

                // Stop if we hit a new transaction (line starting with date)
                if (nextLine.isNotEmpty() && i > transactionStartLine) {
                    if (dateOnlyPattern.containsMatchIn(nextLine) ||
                        datePairPattern.containsMatchIn(nextLine) ||
                        (dateStartPattern.containsMatchIn(nextLine) && !amountAnywherePattern.containsMatchIn(nextLine))) {
                        break
                    }
                }

                // Check if this line contains only an amount: "- 250,00"
                val amountOnlyMatch = amountOnlyPattern.find(nextLine)
                if (amountOnlyMatch != null) {
                    amountSign = if (amountOnlyMatch.groupValues[1] == "-") -1.0 else 1.0
                    amount = parseGermanAmount(amountOnlyMatch.groupValues[2])?.let { it * amountSign }
                    rawText += "\n$nextLine"
                    i++
                    break
                }

                // Check if this line contains an amount somewhere
                val amountMatch = amountAnywherePattern.find(nextLine)
                if (amountMatch != null && amount == null) {
                    amountSign = if (amountMatch.groupValues[1] == "-") -1.0 else 1.0
                    amount = parseGermanAmount(amountMatch.groupValues[2])?.let { it * amountSign }
                    // Extract description part before the amount
                    val descPart = nextLine.substring(0, amountMatch.range.first).trim()
                    if (descPart.isNotEmpty() && !isHeaderOrFooter(descPart)) {
                        descriptionParts.add(descPart)
                    }
                    rawText += "\n$nextLine"
                    i++
                    break
                }

                // It's a description line
                if (nextLine.isNotEmpty() && !isHeaderOrFooter(nextLine)) {
                    descriptionParts.add(nextLine)
                    rawText += "\n$nextLine"
                }

                i++
            }

            // Create transaction if we have valid data
            if (bookingDate != null && amount != null) {
                val fullDescription = descriptionParts.joinToString(" ")
                    .replace(Regex("\\s+"), " ")
                    .trim()

                // Skip balance entries
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
     * Strategy 5: Comprehensive format parser for various German bank formats
     * Handles: split dates, short dates, suffix signs (S/H/+/-), amounts on separate lines
     */
    protected fun parseComprehensiveFormat(lines: List<String>): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()

        // Preprocess: Join split dates like "02.05.\n2024" -> "02.05.2024"
        val processedLines = preprocessLines(lines)

        // Comprehensive patterns
        // Date: DD.MM.YYYY or DD.MM.YY or DD.MM. (short)
        val datePattern = Regex("""(\d{2}\.\d{2}\.(?:\d{4}|\d{2})?)""")
        // Amount with optional sign suffix: "128,96 S", "2.500,00 H", "25,49+", "19,90-", "513,29 €"
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

            // Try to find a date in this line
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

            // Look for second date (value date) on same line
            val remainingAfterFirstDate = line.substring(dateMatch.range.last + 1)
            val secondDateMatch = datePattern.find(remainingAfterFirstDate)
            val valueDate = secondDateMatch?.let {
                parseGermanDate(normalizeYear(it.groupValues[1]))
            } ?: bookingDate

            // Collect transaction block (current line + following lines until next date)
            val blockLines = mutableListOf(line)
            var j = i + 1
            while (j < processedLines.size && j < i + 12) {
                val nextLine = processedLines[j].trim()
                if (nextLine.isEmpty()) {
                    j++
                    continue
                }
                // Stop if we hit a new transaction (line starting with date pattern that's not just an amount)
                val nextDateMatch = datePattern.find(nextLine)
                if (nextDateMatch != null && nextDateMatch.range.first < 3) {
                    // Check if this is likely a new transaction or continuation
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

            // Join block and find amount
            val blockText = blockLines.joinToString(" ")
            val amountMatch = amountWithSignPattern.findAll(blockText).lastOrNull()

            if (amountMatch != null) {
                val prefixSign = amountMatch.groupValues[1]
                val amountStr = amountMatch.groupValues[2]
                val suffixSign = amountMatch.groupValues[3].uppercase()

                val amountValue = parseGermanAmount(amountStr)
                if (amountValue != null) {
                    // Build description from block
                    var description = blockText
                    // Remove dates
                    datePattern.findAll(description).forEach {
                        description = description.replace(it.value, " ")
                    }
                    // Remove amounts
                    amountWithSignPattern.findAll(description).forEach {
                        description = description.replace(it.value, " ")
                    }
                    description = description
                        .replace(Regex("""[€SH+-−–]"""), " ")
                        .replace(Regex("""\s+"""), " ")
                        .trim()

                    // Determine sign
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

                    // Skip balance entries - these are not real transactions
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

    /**
     * Preprocess lines: Join split dates like "02.05.\n2024" -> "02.05.2024"
     */
    private fun preprocessLines(lines: List<String>): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            var line = lines[i]

            // Check if line ends with incomplete date "DD.MM." and next line is year "YYYY"
            val incompleteDatePattern = Regex("""(\d{2}\.\d{2}\.)\s*$""")
            val yearPattern = Regex("""^(\d{4})\s*$""")

            if (incompleteDatePattern.containsMatchIn(line) && i + 1 < lines.size) {
                val nextLine = lines[i + 1].trim()
                val yearMatch = yearPattern.find(nextLine)
                if (yearMatch != null) {
                    // Join the date
                    line = line.trimEnd() + yearMatch.groupValues[1]
                    i++ // Skip the year line
                }
            }

            result.add(line)
            i++
        }
        return result
    }

    // Helper functions
    protected fun parseGermanDate(dateStr: String): LocalDate? {
        return try {
            val parts = dateStr.split(".")
            if (parts.size == 3) {
                LocalDate(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
            } else if (parts.size == 2) {
                // Short date format: DD.MM. - use current year
                val currentYear = Clock.System.now()
                    .toLocalDateTime(TimeZone.currentSystemDefault()).year
                LocalDate(currentYear, parts[1].trimEnd('.').toInt(), parts[0].toInt())
            } else null
        } catch (e: Exception) {
            null
        }
    }

    protected fun parseGermanAmount(amountStr: String): Double? {
        return try {
            amountStr
                .replace("€", "")
                .replace("EUR", "")
                .replace(" ", "")
                .replace(".", "")
                .replace(",", ".")
                .replace("−", "-")  // Unicode minus to regular minus
                .replace("–", "-")  // En dash to minus
                .trim()
                .toDouble()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse amount with sign suffix/prefix: "25,49+", "128,96 S", "2.500,00 H", "- 492,00", "19,90 -"
     * Returns Pair(amount, isExpense)
     */
    protected fun parseGermanAmountWithSign(text: String, context: String = ""): Pair<Double, Boolean>? {
        // Normalize unicode characters
        val normalized = text
            .replace("−", "-")
            .replace("–", "-")
            .trim()

        // Pattern for amount with optional sign prefix/suffix
        // Matches: "25,49+", "128,96 S", "2.500,00 H", "- 492,00", "19,90-", "513,29 €"
        val amountPattern = Regex(
            """([+-])?\s*(\d{1,3}(?:[.]\d{3})*,\d{2})\s*(?:€|EUR)?\s*([+-SH])?""",
            RegexOption.IGNORE_CASE
        )

        val match = amountPattern.find(normalized) ?: return null
        val prefixSign = match.groupValues[1]
        val amountStr = match.groupValues[2]
        val suffixSign = match.groupValues[3].uppercase()

        val amount = parseGermanAmount(amountStr) ?: return null

        // Determine if expense based on signs
        val isExpense = when {
            prefixSign == "-" -> true
            prefixSign == "+" -> false
            suffixSign == "-" -> true
            suffixSign == "+" -> false
            suffixSign == "S" -> true   // Soll = debit = expense
            suffixSign == "H" -> false  // Haben = credit = income
            // Check context for keywords
            else -> isExpenseFromContext(context)
        }

        return Pair(amount, isExpense)
    }

    /**
     * Determine if transaction is expense based on description keywords
     */
    protected fun isExpenseFromContext(context: String): Boolean {
        val lower = context.lowercase()
        // Check income keywords first
        for (keyword in incomeKeywords) {
            if (lower.contains(keyword)) return false
        }
        // Check expense keywords
        for (keyword in expenseKeywords) {
            if (lower.contains(keyword)) return true
        }
        // Default to expense for unknown
        return true
    }

    /**
     * Determine sign multiplier from context and parsed sign
     */
    protected fun getSignMultiplier(signStr: String?, context: String): Double {
        val sign = signStr?.trim()?.uppercase() ?: ""
        return when {
            sign.startsWith("-") || sign == "S" -> -1.0
            sign.startsWith("+") || sign == "H" -> 1.0
            // Use context to determine
            !isExpenseFromContext(context) -> 1.0
            else -> -1.0
        }
    }

    protected fun normalizeYear(dateStr: String): String {
        val parts = dateStr.split(".")
        if (parts.size == 3 && parts[2].length == 2) {
            val year = parts[2].toIntOrNull() ?: return dateStr
            val fullYear = if (year > 50) 1900 + year else 2000 + year
            return "${parts[0]}.${parts[1]}.$fullYear"
        }
        // Handle short dates: "06.07" -> add current year
        if (parts.size == 2 || (parts.size == 3 && parts[2].isEmpty())) {
            val currentYear = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault()).year
            return "${parts[0]}.${parts[1]}.$currentYear"
        }
        return dateStr
    }

    protected fun extractIban(text: String): String? {
        val ibanPattern = Regex("([A-Z]{2}\\d{2}[\\s]?(?:\\d{4}[\\s]?){4}\\d{2})")
        val match = ibanPattern.find(text.uppercase())
        return match?.groupValues?.get(1)?.replace("\\s".toRegex(), "")
    }

    protected fun extractStatementPeriod(text: String): String? {
        // Various patterns for statement period
        val patterns = listOf(
            Regex("Kontoauszug\\s+(?:Nr\\.?\\s*\\d+\\s+)?(?:vom\\s+)?(\\d{2}\\.\\d{2}\\.\\d{4})(?:\\s*[-–bis]+\\s*(\\d{2}\\.\\d{2}\\.\\d{4}))?", RegexOption.IGNORE_CASE),
            Regex("Auszug\\s+(\\w+\\s+\\d{4})", RegexOption.IGNORE_CASE),
            Regex("Zeitraum[:\\s]+(\\d{2}\\.\\d{2}\\.\\d{4})\\s*[-–bis]+\\s*(\\d{2}\\.\\d{2}\\.\\d{4})", RegexOption.IGNORE_CASE),
            Regex("(\\w+)\\s+(\\d{4})\\s*Kontoauszug", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                return match.groupValues.drop(1).filter { it.isNotBlank() }.joinToString(" - ")
            }
        }
        return null
    }

    protected fun detectTransactionType(description: String): String {
        val trimmed = description.trim()
        val lower = trimmed.lowercase()

        // First, check if description STARTS with a known transaction type
        // This handles cases like "Kartenzahlung - 100,28" or "Lastschrift AMAZON"
        for (type in germanTransactionTypes) {
            val typeLower = type.lowercase()
            if (lower.startsWith(typeLower)) {
                return type
            }
        }

        // Check for SEPA variants at start
        if (lower.startsWith("sepa")) {
            if (lower.contains("lastschrift")) return "SEPA-Lastschrift"
            if (lower.contains("überweisung")) return "SEPA-Überweisung"
            return "SEPA"
        }

        // Extract first word/phrase and check if it's a known type
        val firstWord = trimmed.split(Regex("[\\s-]")).firstOrNull()?.trim()
        if (firstWord != null) {
            for (type in germanTransactionTypes) {
                if (firstWord.equals(type, ignoreCase = true)) {
                    return type
                }
            }
        }

        // Fallback: check if any keyword is contained anywhere in description
        for (type in germanTransactionTypes) {
            if (lower.contains(type.lowercase())) {
                return type
            }
        }

        return "Buchung"
    }

    protected fun extractCounterparty(description: String): String? {
        // First try German banking patterns (from jejik-mt940)
        val germanCounterparty = extractCounterpartyGerman(description)
        if (germanCounterparty != null) return germanCounterparty

        // Fallback: extract first meaningful words
        val words = description.split(Regex("\\s+"))
            .filter { word ->
                word.length > 2 &&
                !word.all { c -> c.isDigit() || c == '.' || c == ',' } &&
                !germanTransactionTypes.any { word.equals(it, ignoreCase = true) }
            }
            .take(5)

        return if (words.isNotEmpty()) {
            words.joinToString(" ").take(50)
        } else null
    }

    protected fun isHeaderOrFooter(line: String): Boolean {
        val lower = line.lowercase().trim()

        // Balance/summary lines - must be filtered out
        if (lower.contains("neuer saldo") ||
            lower.contains("alter saldo") ||
            lower.contains("anfangssaldo") ||
            lower.contains("endsaldo") ||
            lower.contains("kontosaldo") ||
            lower.contains("gesamtsaldo") ||
            lower.contains("kontostand") ||
            lower.contains("summe haben") ||
            lower.contains("summe soll") ||
            lower.contains("disponibel") ||
            lower.startsWith("saldo") ||
            lower == "kontonummer" ||
            lower == "filialnummer" ||
            lower == "iban" ||
            lower == "bic") {
            return true
        }

        // Page headers/footers
        if ((lower.contains("seite") && lower.contains("von")) ||
            (lower.contains("kontoauszug") && (lower.contains("nr") || lower.contains("nummer"))) ||
            (lower.contains("iban") && lower.contains("bic")) ||
            lower.contains("blz") ||
            (lower.contains("datum") && lower.contains("betrag") && lower.contains("saldo")) ||
            lower.contains("übertrag") ||
            lower.contains("buchungsdatum") && lower.contains("wertstellung") ||
            lower.contains("fortsetzung") ||
            lower.startsWith("buchungstext") ||
            lower.startsWith("verwendungszweck") && lower.length < 25) {
            return true
        }

        // Account number lines (just digits separated by spaces)
        if (Regex("""^\d+\s*\d*\s*$""").matches(line.trim()) && line.trim().length < 15) {
            return true
        }

        // Too short lines
        if (line.trim().length < 5) {
            return true
        }

        return false
    }

    /**
     * Check if this looks like a balance entry rather than a real transaction
     * These should not be included in the transaction list
     */
    protected fun isBalanceEntry(description: String, rawText: String): Boolean {
        val lowerDesc = description.lowercase()
        val lowerRaw = rawText.lowercase()

        // Check for balance keywords
        val balanceKeywords = listOf(
            "neuer saldo", "alter saldo", "anfangssaldo", "endsaldo",
            "kontosaldo", "gesamtsaldo", "kontostand", "saldo per",
            "disponibel", "verfügbar", "guthaben per", "summe haben",
            "summe soll", "kontonummer", "filialnummer"
        )

        for (keyword in balanceKeywords) {
            if (lowerDesc.contains(keyword) || lowerRaw.contains(keyword)) {
                return true
            }
        }

        // Check if description is just EUR or currency marker
        if (description.trim().uppercase() in listOf("EUR", "€", "EURO", "")) {
            return true
        }

        return false
    }
}

// ============================================================
// Deutsche Bank Parser
// ============================================================
class DeutscheBankParser : GermanBankParser() {
    override val bankName = "Deutsche Bank"

    private val identifiers = listOf(
        "deutsche bank",
        "deutdedb",
        "deutdeff",
        "deutsche-bank.de"
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "Deutsche Bank")
    }
}

// ============================================================
// Postbank Parser
// ============================================================
class PostbankParser : GermanBankParser() {
    override val bankName = "Postbank"

    private val identifiers = listOf(
        "postbank",
        "pbnkdeff",
        "postbank.de",
        "eine niederlassung der deutsche bank"
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "Postbank")
    }
}

// ============================================================
// Commerzbank Parser
// ============================================================
class CommerzbankParser : GermanBankParser() {
    override val bankName = "Commerzbank"

    private val identifiers = listOf(
        "commerzbank",
        "cobadeff",
        "dresdner bank",
        "commerzbank.de",
        "comdirect",  // Commerzbank subsidiary
        "cobadehd"    // BIC variant
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "Commerzbank")
    }
}

// ============================================================
// Volksbank Parser
// ============================================================
class VolksbankParser : GermanBankParser() {
    override val bankName = "Volksbank"

    private val identifiers = listOf(
        "volksbank",
        "vr-bank",
        "vr bank",
        "raiffeisenbank",
        "genossenschaftsbank",
        "genoded",  // BIC prefix for Genobanks
        "basislastschrift pn:",  // Common pattern in Volksbank statements
        "klarna bank ab"  // Often appears in Volksbank statements
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "Volksbank")
    }
}

// ============================================================
// C24 Bank Parser
// ============================================================
class C24Parser : GermanBankParser() {
    override val bankName = "C24 Bank"

    private val identifiers = listOf(
        "c24 bank",
        "c24bank",
        "c24-bank",
        "check24"
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "C24 Bank")
    }
}

// ============================================================
// Consorsbank Parser
// ============================================================
class ConsorsbankParser : GermanBankParser() {
    override val bankName = "Consorsbank"

    private val identifiers = listOf(
        "consorsbank",
        "consors",
        "bnp paribas",
        "csdbde71",
        "cortal consors",
        "visa 26466"  // Consorsbank VISA card pattern
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        // Also check for Consorsbank-specific format: "GUTSCHRIFT DD.MM. XXXX DD.MM. XX,XX+"
        val hasConsorsFormat = Regex("""(GUTSCHRIFT|LASTSCHRIFT)\s+\d{2}\.\d{2}\.\s+\d{4}\s+\d{2}\.\d{2}\.\s+[\d,]+[+-]""")
            .containsMatchIn(pdfText.uppercase())
        return identifiers.any { lower.contains(it) } || hasConsorsFormat
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "Consorsbank")
    }
}

// ============================================================
// N26 Parser
// ============================================================
class N26Parser : GermanBankParser() {
    override val bankName = "N26"

    private val identifiers = listOf(
        "n26",
        "n26 bank",
        "ntsbdeb1",
        "number26"
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "N26")
    }
}

// ============================================================
// TARGOBANK Parser
// ============================================================
class TargobankParser : GermanBankParser() {
    override val bankName = "TARGOBANK"

    private val identifiers = listOf(
        "targobank",
        "targo bank",
        "cmcidedd",
        "cmcideddxxx",
        "trbkdebb",
        "trbkdebbxxx",
        "targobank.de",
        "citibank"  // Former name
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        // Also check for Targo-specific format: "Datum Tag Buchungstext Ausgaben Einnahmen"
        val hasTargoFormat = lower.contains("ausgaben") && lower.contains("einnahmen") && lower.contains("guthaben/kredit")
        return identifiers.any { lower.contains(it) } || hasTargoFormat
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "TARGOBANK")
    }
}

// ============================================================
// 1822direkt Parser (Frankfurter Sparkasse subsidiary)
// ============================================================
class DirectBank1822Parser : GermanBankParser() {
    override val bankName = "1822direkt"

    private val identifiers = listOf(
        "1822direkt",
        "1822 direkt",
        "1822direct",
        "frankfurter sparkasse",
        "heaborh", // BIC contains this
        "dsl bank",  // DSL Bank is related to 1822direkt
        "sepa-basislastschrift"  // Common in 1822direkt statements
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "1822direkt")
    }
}

// ============================================================
// Apobank Parser (Deutsche Apotheker- und Ärztebank)
// ============================================================
class ApoBankParser : GermanBankParser() {
    override val bankName = "Apobank"

    private val identifiers = listOf(
        "apobank",
        "apo bank",
        "apotheker",
        "ärztebank",
        "daaededd",  // BIC
        "deutsche apotheker"
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        // Also check for Apobank-specific format: "Wertstellung: DD.MM.YYYY"
        val hasApobankFormat = lower.contains("wertstellung:") && lower.contains("kartenzahlung debitkarte")
        return identifiers.any { lower.contains(it) } || hasApobankFormat
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "Apobank")
    }
}

// ============================================================
// Tomorrow Bank Parser (Sustainable/Green Bank)
// ============================================================
class TomorrowBankParser : GermanBankParser() {
    override val bankName = "Tomorrow"

    private val identifiers = listOf(
        "tomorrow",
        "tomorrow bank",
        "tomorrow gmbh",
        "tomorrowbank",
        "sobkdehhxxx"  // BIC
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "Tomorrow")
    }
}

// ============================================================
// bunq Parser (Dutch neobank)
// ============================================================
class BunqParser : GermanBankParser() {
    override val bankName = "bunq"

    private val identifiers = listOf(
        "bunq",
        "bunq b.v",
        "bunq bank",
        "bunqnl2a"  // BIC
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "bunq")
    }
}

// ============================================================
// Generic German Bank Parser (Fallback)
// ============================================================
/**
 * Generic parser for German bank statements that weren't recognized by specific parsers.
 * Attempts to:
 * 1. Extract bank name from address block (e.g., "Deutsche Bank AG")
 * 2. Parse transactions using multiple strategies
 */
class GenericGermanBankParser : GermanBankParser() {
    override val bankName = "German Bank"

    // Keywords that indicate this is a German bank statement
    private val germanIndicators = listOf(
        "kontoauszug", "girokonto", "sparkonto", "tagesgeld",
        "verwendungszweck", "buchung", "lastschrift", "gutschrift",
        "überweisung", "dauerauftrag", "kartenzahlung", "bargeld",
        "sepa", "blz", "kontonummer", "rechnungsabschluss"
    )

    // Patterns to identify bank names in address blocks
    private val bankNamePatterns = listOf(
        Regex("([A-Za-zäöüÄÖÜß\\s]+(?:Bank|Sparkasse|Volksbank|Raiffeisenbank)(?:\\s+[A-Z]{2,})?)", RegexOption.IGNORE_CASE),
        Regex("([A-Za-zäöüÄÖÜß\\s]+(?:AG|GmbH|eG))", RegexOption.IGNORE_CASE),
        Regex("(Deutsche\\s+Bank)", RegexOption.IGNORE_CASE),
        Regex("(Commerzbank)", RegexOption.IGNORE_CASE),
        Regex("(Postbank)", RegexOption.IGNORE_CASE),
        Regex("(ING-DiBa|ING\\s+DiBa)", RegexOption.IGNORE_CASE),
        Regex("(Comdirect)", RegexOption.IGNORE_CASE),
        Regex("(HypoVereinsbank|HVB)", RegexOption.IGNORE_CASE),
        Regex("(Santander)", RegexOption.IGNORE_CASE)
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        // Check if this looks like a German bank statement
        val hasGermanIndicators = germanIndicators.count { lower.contains(it) } >= 2
        val hasIban = lower.contains("de") && Regex("[a-z]{2}\\d{2}\\s?\\d{4}").containsMatchIn(lower)
        val hasGermanDate = Regex("\\d{2}\\.\\d{2}\\.\\d{4}").containsMatchIn(pdfText)
        val hasGermanAmount = Regex("[+-]?\\s*\\d{1,3}(?:[.]\\d{3})*,\\d{2}").containsMatchIn(pdfText)

        return (hasGermanIndicators || hasIban) && hasGermanDate && hasGermanAmount
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        val detectedBankName = extractBankName(pdfText) ?: "German Bank"
        return parseGermanStatement(pdfText, fileName, detectedBankName)
    }

    /**
     * Try to extract bank name from the PDF text
     * Looks for patterns like "Deutsche Bank AG", "Sparkasse München", etc.
     */
    private fun extractBankName(pdfText: String): String? {
        // Try each pattern
        for (pattern in bankNamePatterns) {
            val match = pattern.find(pdfText)
            if (match != null) {
                val name = match.groupValues[1].trim()
                // Validate the name looks reasonable
                if (name.length in 3..50 && !name.all { it.isDigit() }) {
                    return cleanBankName(name)
                }
            }
        }

        // Try to find bank name in first 20 lines (usually in header/address block)
        val lines = pdfText.lines().take(20)
        for (line in lines) {
            val trimmed = line.trim()
            // Look for lines ending with "Bank", "AG", "Sparkasse", etc.
            if (trimmed.contains("Bank", ignoreCase = true) ||
                trimmed.contains("Sparkasse", ignoreCase = true) ||
                trimmed.endsWith("AG") ||
                trimmed.endsWith("eG")) {
                // Clean up the line
                val cleaned = trimmed
                    .replace(Regex("\\d+"), "")
                    .replace(Regex("[,;:]"), "")
                    .trim()
                if (cleaned.length in 5..50) {
                    return cleanBankName(cleaned)
                }
            }
        }

        return null
    }

    private fun cleanBankName(name: String): String {
        return name
            .replace(Regex("\\s+"), " ")
            .replace(Regex("^(Ihr|Ihre|Die|Der|Das)\\s+", RegexOption.IGNORE_CASE), "")
            .trim()
            .take(50)
    }
}
