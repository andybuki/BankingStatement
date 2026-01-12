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

    /**
     * Calculate confidence based on matched identifiers
     * @param pdfText the PDF text to analyze
     * @param certainIdentifiers identifiers that give CERTAIN confidence (BIC codes, explicit bank name)
     * @param highIdentifiers identifiers that give HIGH confidence
     * @param mediumIdentifiers identifiers that give MEDIUM confidence (generic patterns)
     */
    protected fun calculateConfidence(
        pdfText: String,
        certainIdentifiers: List<String>,
        highIdentifiers: List<String> = emptyList(),
        mediumIdentifiers: List<String> = emptyList()
    ): Pair<DetectionConfidence, List<String>> {
        val lower = pdfText.lowercase()
        val matchedIdentifiers = mutableListOf<String>()

        // Check for CERTAIN identifiers (unique to this bank)
        for (id in certainIdentifiers) {
            if (lower.contains(id.lowercase())) {
                matchedIdentifiers.add(id)
            }
        }
        if (matchedIdentifiers.isNotEmpty()) {
            return Pair(DetectionConfidence.CERTAIN, matchedIdentifiers)
        }

        // Check for HIGH identifiers
        for (id in highIdentifiers) {
            if (lower.contains(id.lowercase())) {
                matchedIdentifiers.add(id)
            }
        }
        if (matchedIdentifiers.size >= 2) {
            return Pair(DetectionConfidence.HIGH, matchedIdentifiers)
        }
        if (matchedIdentifiers.size == 1) {
            // Check if we also have medium identifiers
            val mediumMatches = mediumIdentifiers.filter { lower.contains(it.lowercase()) }
            if (mediumMatches.isNotEmpty()) {
                matchedIdentifiers.addAll(mediumMatches)
                return Pair(DetectionConfidence.HIGH, matchedIdentifiers)
            }
            return Pair(DetectionConfidence.MEDIUM, matchedIdentifiers)
        }

        // Check for MEDIUM identifiers
        for (id in mediumIdentifiers) {
            if (lower.contains(id.lowercase())) {
                matchedIdentifiers.add(id)
            }
        }
        if (matchedIdentifiers.size >= 2) {
            return Pair(DetectionConfidence.MEDIUM, matchedIdentifiers)
        }
        if (matchedIdentifiers.isNotEmpty()) {
            return Pair(DetectionConfidence.LOW, matchedIdentifiers)
        }

        return Pair(DetectionConfidence.NONE, emptyList())
    }

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

    // CERTAIN: unique identifiers (BIC codes, official name)
    private val certainIdentifiers = listOf(
        "cobadeff",      // Commerzbank BIC Frankfurt
        "cobadehd",      // Commerzbank BIC variant
        "cobadehdxxx",   // Full BIC
        "cobadeffxxx",   // Full BIC
        "commerzbank ag" // Official legal name
    )

    // HIGH: strong indicators
    private val highIdentifiers = listOf(
        "commerzbank",
        "dresdner bank",  // Former name (merged with Commerzbank)
        "commerzbank.de"
    )

    // MEDIUM: patterns that might appear in Commerzbank statements
    private val mediumIdentifiers = listOf(
        "zu ihren lasten",  // Debit column header
        "zu ihren gunsten"  // Credit column header
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
// Landesbank Berlin (LBB) Parser
// ============================================================
class LandesbankBerlinParser : GermanBankParser() {
    override val bankName = "Landesbank Berlin"

    // BLZ codes from jejik-mt940 PHP library
    private val allowedBlz = listOf(
        "10050000", "10050005", "10050006", "10050007", "10050008"
    )

    private val identifiers = listOf(
        "landesbank berlin",
        "berliner sparkasse",
        "lbb",
        "beladebe"  // BIC prefix
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        // Check identifiers
        if (identifiers.any { lower.contains(it) }) return true
        // Check BLZ codes
        return allowedBlz.any { pdfText.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "Landesbank Berlin")
    }
}

// ============================================================
// HypoVereinsbank / UniCredit Parser
// ============================================================
class HypoVereinsbankParser : GermanBankParser() {
    override val bankName = "HypoVereinsbank"

    // BLZ codes from jejik-mt940 PHP library (partial list)
    private val allowedBlz = listOf(
        "10020890", "20030000", "70020270", "70020001",
        "20730001", "20730002", "20730003", "20730004", "20730005",
        "20730006", "20730007", "20730008", "20730009", "20730010"
    )

    private val identifiers = listOf(
        "hypovereinsbank",
        "hvb",
        "unicredit",
        "unicredit bank",
        "hypobank",
        "vereinsbank",
        "hvbkdeff",  // BIC
        "hyvedemmxxx"
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        // Check identifiers
        if (identifiers.any { lower.contains(it) }) return true
        // Check BLZ codes
        return allowedBlz.any { pdfText.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "HypoVereinsbank")
    }
}

// ============================================================
// DKB (Deutsche Kreditbank) Parser
// ============================================================
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

// ============================================================
// LBBW (Landesbank Baden-Württemberg) Parser
// ============================================================
class LbbwParser : GermanBankParser() {
    override val bankName = "LBBW"

    private val identifiers = listOf(
        "landesbank baden-württemberg",
        "lbbw",
        "bw bank",
        "bw-bank",
        "soladest",  // BIC prefix
        "60050101"  // BLZ
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "LBBW")
    }
}

// ============================================================
// Sparkasse Parser (with more identifiers)
// ============================================================
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

// ============================================================
// Comdirect Parser
// ============================================================
class ComdirectParser : GermanBankParser() {
    override val bankName = "comdirect"

    private val identifiers = listOf(
        "comdirect",
        "comdirect bank",
        "cobadehdxxx",  // BIC
        "cobadehd",
        "20041111"  // BLZ
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "comdirect")
    }
}

// ============================================================
// Santander Consumer Bank Parser
// ============================================================
class SantanderParser : GermanBankParser() {
    override val bankName = "Santander"

    private val identifiers = listOf(
        "santander",
        "santander consumer bank",
        "santander bank",
        "scfbde33",  // BIC
        "31010833"  // BLZ
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "Santander")
    }
}

// ============================================================
// Sparda Bank Parser
// ============================================================
class SpardaBankParser : GermanBankParser() {
    override val bankName = "Sparda-Bank"

    private val identifiers = listOf(
        "sparda",
        "sparda-bank",
        "sparda bank",
        "genodef1s"  // BIC prefix for Sparda banks
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "Sparda-Bank")
    }
}

// ============================================================
// PSD Bank Parser
// ============================================================
class PsdBankParser : GermanBankParser() {
    override val bankName = "PSD Bank"

    private val identifiers = listOf(
        "psd bank",
        "psd-bank",
        "psdbank",
        "genodef1p"  // BIC prefix for PSD banks
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "PSD Bank")
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
