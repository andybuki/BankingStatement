package com.banking.statement.parser.banks

import com.banking.statement.parser.ParseResult
import com.banking.statement.parser.ParsedTransaction
import kotlinx.datetime.LocalDate

/**
 * Parser for ING DiBa (Germany) bank statements
 *
 * Format characteristics:
 * - Header: ING-DiBa AG, Frankfurt am Main
 * - Account info: IBAN, BIC, Saldo
 * - Transaction format varies by statement type:
 *   Format 1: DD.MM.YYYY TransactionType Description Amount
 *   Format 2: Date in separate column, then description, then amount
 */
class IngDiBaParser : BankPdfParser {

    override val bankName = "ING DiBa"

    // Primary identifiers that uniquely identify ING DiBa statements
    private val primaryIdentifiers = listOf(
        "ing-diba",
        "ing diba",
        "ing.de",
        "ingddeffxxx",  // BIC
        "ingddeff",
        "ing ag",
        "ing deutschland"
    )

    // Secondary identifiers (generic German terms) - only match if combined with ING indicators
    private val secondaryIdentifiers = listOf(
        "de29 5001 0517", // ING IBAN prefix
        "de50 5001 0517",
        "girokonto",
        "extra-konto",
        "tagesgeld"
    )

    // Transaction type keywords - expanded list
    private val transactionTypes = listOf(
        "Lastschrift", "Gutschrift", "Überweisung", "Dauerauftrag",
        "Gehalt", "Lohn", "Kartenzahlung", "Bargeldauszahlung",
        "Abschluss", "Zinsen", "Entgelt", "Einzahlung", "Auszahlung",
        "SEPA-Lastschrift", "SEPA-Überweisung", "Kontoführung",
        "Habenzinsen", "Sollzinsen", "Abgeltungsteuer", "Kapitalertragsteuer",
        "Geldautomat", "Echtzeitüberweisung", "Dauerauftrag/Terminüberw."
    )

    override fun canParse(pdfText: String): Boolean {
        val lowerText = pdfText.lowercase()

        // Primary identifiers are conclusive - if found, this is ING DiBa
        if (primaryIdentifiers.any { lowerText.contains(it) }) {
            return true
        }

        // Secondary identifiers need at least 2 to match (to avoid false positives)
        val secondaryMatches = secondaryIdentifiers.count { lowerText.contains(it) }
        return secondaryMatches >= 2
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        try {
            val lines = pdfText.lines()

            // Extract account info
            val accountIban = extractIban(pdfText)
            val statementPeriod = extractStatementPeriod(pdfText)

            // Try multiple parsing strategies
            var transactions = parseWithTypePrefix(lines)

            if (transactions.isEmpty()) {
                transactions = parseWithDateAmountPattern(lines)
            }

            if (transactions.isEmpty()) {
                transactions = parseGenericDatePattern(lines, pdfText)
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
                // Return debug info to help identify the format
                val sampleLines = lines.filter { it.isNotBlank() }.take(20).joinToString("\n")
                ParseResult(
                    success = false,
                    bankName = bankName,
                    errorMessage = "Could not extract transactions from ING DiBa PDF. " +
                            "Format not recognized. Sample text:\n$sampleLines"
                )
            }

        } catch (e: Exception) {
            return ParseResult(
                success = false,
                bankName = bankName,
                errorMessage = "Error parsing ING DiBa PDF: ${e.message}"
            )
        }
    }

    /**
     * Strategy 1: Parse lines with format "DD.MM.YYYY TransactionType Description Amount"
     */
    private fun parseWithTypePrefix(lines: List<String>): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            // Look for transaction start: date followed by transaction type
            val transactionMatch = findTransactionStart(line)
            if (transactionMatch != null) {
                val (bookingDate, type, initialDesc, amount) = transactionMatch

                // Collect additional description lines
                val descriptionParts = mutableListOf<String>()
                if (initialDesc.isNotBlank()) {
                    descriptionParts.add(initialDesc)
                }

                var valueDate: LocalDate? = null
                var mandate: String? = null
                var reference: String? = null

                // Look at following lines for more details
                var j = i + 1
                while (j < lines.size) {
                    val nextLine = lines[j].trim()

                    // Stop if we hit a new transaction or empty section
                    if (nextLine.isEmpty() && j > i + 1) {
                        val lookAhead = lines.drop(j + 1).firstOrNull { it.isNotBlank() }
                        if (lookAhead != null && findTransactionStart(lookAhead.trim()) != null) {
                            break
                        }
                    }

                    // Check if this is the value date line (second date)
                    val valueDateMatch = extractDateFromLine(nextLine)
                    if (valueDateMatch != null && valueDate == null) {
                        valueDate = valueDateMatch
                        val afterDate = nextLine.substringAfter(
                            Regex("\\d{2}\\.\\d{2}\\.\\d{4}").find(nextLine)?.value ?: ""
                        ).trim()
                        if (afterDate.isNotBlank()) {
                            descriptionParts.add(afterDate)
                        }
                        j++
                        continue
                    }

                    // Check for mandate/reference
                    if (nextLine.startsWith("Mandat:", ignoreCase = true)) {
                        mandate = nextLine.substringAfter(":").trim()
                        j++
                        continue
                    }
                    if (nextLine.startsWith("Referenz:", ignoreCase = true)) {
                        reference = nextLine.substringAfter(":").trim()
                        j++
                        continue
                    }

                    // Check if this line starts a new transaction
                    if (findTransactionStart(nextLine) != null) {
                        break
                    }

                    // Skip section headers
                    if (nextLine.contains("Buchung") && nextLine.contains("Verwendungszweck")) {
                        j++
                        continue
                    }

                    if (nextLine.isNotBlank() && !isPageFooter(nextLine)) {
                        descriptionParts.add(nextLine)
                    }

                    j++
                    if (j > i + 15) break
                }

                val fullDescription = buildDescription(type, descriptionParts)
                val counterparty = extractCounterparty(type, descriptionParts.firstOrNull() ?: "")

                if (amount != null && bookingDate != null) {
                    transactions.add(
                        ParsedTransaction(
                            bookingDate = bookingDate,
                            valueDate = valueDate ?: bookingDate,
                            amount = amount,
                            currency = "EUR",
                            description = fullDescription,
                            counterpartyName = counterparty,
                            transactionType = type,
                            remittanceInfo = mandate?.let { "Mandat: $it" }
                                ?: reference?.let { "Referenz: $it" },
                            rawText = lines.subList(i, minOf(j, lines.size)).joinToString("\n")
                        )
                    )
                }

                i = j
            } else {
                i++
            }
        }

        return transactions
    }

    /**
     * Strategy 2: Parse lines with amount pattern anywhere (more flexible)
     * Looks for: Date ... Amount pattern
     */
    private fun parseWithDateAmountPattern(lines: List<String>): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()

        // Pattern: line starting with date and containing an amount somewhere
        val datePattern = Regex("^(\\d{2}\\.\\d{2}\\.\\d{4})")
        // Updated pattern to capture signed amounts: "- 250,00" or "+ 1.043,44"
        val amountPattern = Regex("([+-]\\s*)?(-?\\d{1,3}(?:\\.\\d{3})*,\\d{2})(?:\\s|$)")

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            val dateMatch = datePattern.find(line)
            if (dateMatch != null) {
                val amountMatch = amountPattern.find(line)
                if (amountMatch != null) {
                    val date = parseGermanDate(dateMatch.groupValues[1])
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
                        // Extract description between date and amount
                        val afterDate = line.substring(dateMatch.range.last + 1)
                        val description = afterDate.substring(0,
                            maxOf(0, afterDate.indexOf(amountMatch.value))).trim()
                            .ifEmpty { afterDate.replace(amountMatch.value, "").trim() }

                        // Collect follow-up lines
                        val additionalDesc = mutableListOf<String>()
                        var j = i + 1
                        while (j < lines.size && j < i + 5) {
                            val nextLine = lines[j].trim()
                            if (nextLine.isEmpty() || datePattern.find(nextLine) != null) break
                            if (!isPageFooter(nextLine) && !amountPattern.containsMatchIn(nextLine)) {
                                additionalDesc.add(nextLine)
                            }
                            j++
                        }

                        val fullDescription = if (additionalDesc.isNotEmpty()) {
                            "$description ${additionalDesc.joinToString(" ")}"
                        } else description

                        transactions.add(
                            ParsedTransaction(
                                bookingDate = date,
                                valueDate = date,
                                amount = amount,
                                currency = "EUR",
                                description = fullDescription.trim().ifEmpty { "Transaction" },
                                counterpartyName = extractCounterpartyFromDesc(fullDescription),
                                transactionType = detectTransactionType(fullDescription),
                                rawText = line
                            )
                        )

                        i = j
                        continue
                    }
                }
            }
            i++
        }

        return transactions
    }

    /**
     * Strategy 3: Generic parsing - find any dates and amounts nearby
     */
    private fun parseGenericDatePattern(lines: List<String>, fullText: String): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()

        // Look for patterns like: date followed by text, then amount on same or next line
        val datePattern = Regex("(\\d{2}\\.\\d{2}\\.(?:\\d{4}|\\d{2}))")
        // Updated pattern to capture signed amounts
        val amountPattern = Regex("([+-]\\s*)?(-?\\d{1,3}(?:[.]\\d{3})*,\\d{2})\\s*(?:EUR|€)?")

        // Join lines and split into potential transaction blocks
        val transactionBlocks = mutableListOf<String>()
        var currentBlock = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                if (currentBlock.isNotEmpty()) {
                    transactionBlocks.add(currentBlock.toString())
                    currentBlock = StringBuilder()
                }
            } else if (!isPageFooter(trimmed)) {
                if (currentBlock.isNotEmpty()) currentBlock.append(" ")
                currentBlock.append(trimmed)
            }
        }
        if (currentBlock.isNotEmpty()) {
            transactionBlocks.add(currentBlock.toString())
        }

        for (block in transactionBlocks) {
            val dateMatches = datePattern.findAll(block)
            val amountMatches = amountPattern.findAll(block).toList()

            for (dateMatch in dateMatches) {
                // Find nearest amount after this date
                val dateEnd = dateMatch.range.last
                val nearestAmount = amountMatches.firstOrNull { it.range.first > dateEnd }

                if (nearestAmount != null) {
                    val date = parseGermanDate(normalizeDate(dateMatch.groupValues[1]))
                    // Handle signed amounts
                    val signStr = nearestAmount.groupValues[1]?.trim() ?: ""
                    val amountStr = nearestAmount.groupValues[2]
                    val sign = when {
                        signStr.startsWith("-") -> -1.0
                        signStr.startsWith("+") -> 1.0
                        else -> 1.0
                    }
                    // Parse German amount format: 1.234,56 -> 1234.56
                    val amount = parseGermanAmount(amountStr)?.let { it * sign }

                    if (date != null && amount != null) {
                        val description = block.substring(
                            minOf(dateMatch.range.last + 1, block.length),
                            nearestAmount.range.first
                        ).trim()

                        if (description.isNotBlank() || transactions.none { it.bookingDate == date && it.amount == amount }) {
                            transactions.add(
                                ParsedTransaction(
                                    bookingDate = date,
                                    valueDate = date,
                                    amount = amount,
                                    currency = "EUR",
                                    description = description.ifEmpty { "Transaction" },
                                    counterpartyName = extractCounterpartyFromDesc(description),
                                    transactionType = detectTransactionType(description),
                                    rawText = block
                                )
                            )
                        }
                    }
                    break // Only one transaction per block
                }
            }
        }

        return transactions.distinctBy { "${it.bookingDate}_${it.amount}_${it.description.take(20)}" }
    }

    private fun normalizeDate(dateStr: String): String {
        val parts = dateStr.split(".")
        if (parts.size == 3 && parts[2].length == 2) {
            // Convert YY to YYYY
            val year = parts[2].toIntOrNull() ?: return dateStr
            val fullYear = if (year > 50) 1900 + year else 2000 + year
            return "${parts[0]}.${parts[1]}.$fullYear"
        }
        return dateStr
    }

    private fun detectTransactionType(description: String): String {
        val lower = description.lowercase()
        return when {
            transactionTypes.any { lower.contains(it.lowercase()) } ->
                transactionTypes.first { lower.contains(it.lowercase()) }
            lower.contains("lastschrift") -> "Lastschrift"
            lower.contains("gutschrift") -> "Gutschrift"
            lower.contains("überweisung") -> "Überweisung"
            lower.contains("gehalt") || lower.contains("lohn") -> "Gehalt"
            else -> "Transaction"
        }
    }

    private fun extractCounterpartyFromDesc(description: String): String? {
        // Try to extract a company or person name from the description
        val words = description.split(Regex("\\s+"))
        if (words.isEmpty()) return null

        // Take first few meaningful words as counterparty
        val counterparty = words.take(4)
            .filter { it.length > 2 && !it.all { c -> c.isDigit() } }
            .joinToString(" ")
            .trim()

        return counterparty.takeIf { it.length > 2 }
    }

    private data class TransactionStart(
        val date: LocalDate?,
        val type: String,
        val description: String,
        val amount: Double?
    )

    private fun findTransactionStart(line: String): TransactionStart? {
        // Pattern: DD.MM.YYYY TransactionType Description Amount
        val datePattern = Regex("^(\\d{2}\\.\\d{2}\\.\\d{4})")
        val dateMatch = datePattern.find(line) ?: return null

        val afterDate = line.substring(dateMatch.range.last + 1).trim()
        if (afterDate.isEmpty()) return null

        // Find transaction type
        val type = transactionTypes.find { afterDate.startsWith(it, ignoreCase = true) }
            ?: return null

        val afterType = afterDate.substring(type.length).trim()

        // Try to extract amount from end of line
        val amountPattern = Regex("(-?[\\d.]+,\\d{2})\\s*$")
        val amountMatch = amountPattern.find(afterType)

        val amount = amountMatch?.groupValues?.get(1)?.let { parseGermanAmount(it) }
        val description = if (amountMatch != null) {
            afterType.substring(0, amountMatch.range.first).trim()
        } else {
            afterType
        }

        return TransactionStart(
            date = parseGermanDate(dateMatch.groupValues[1]),
            type = type,
            description = description,
            amount = amount
        )
    }

    private fun extractDateFromLine(line: String): LocalDate? {
        val datePattern = Regex("^(\\d{2}\\.\\d{2}\\.\\d{4})")
        val match = datePattern.find(line) ?: return null
        return parseGermanDate(match.groupValues[1])
    }

    private fun parseGermanDate(dateStr: String): LocalDate? {
        return try {
            val parts = dateStr.split(".")
            if (parts.size == 3) {
                LocalDate(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun parseGermanAmount(amountStr: String): Double? {
        return try {
            amountStr.replace(".", "").replace(",", ".").toDouble()
        } catch (e: Exception) {
            null
        }
    }

    private fun extractIban(text: String): String? {
        val ibanPattern = Regex("IBAN\\s*:?\\s*([A-Z]{2}\\d{2}[\\s\\d]+)")
        val match = ibanPattern.find(text.uppercase())
        return match?.groupValues?.get(1)?.replace("\\s".toRegex(), "")
    }

    private fun extractStatementPeriod(text: String): String? {
        // Look for "Kontoauszug März 2025" or similar
        val periodPattern = Regex("Kontoauszug\\s+(\\w+\\s+\\d{4})", RegexOption.IGNORE_CASE)
        val match = periodPattern.find(text)
        return match?.groupValues?.get(1)
    }

    private fun buildDescription(type: String, parts: List<String>): String {
        val relevantParts = parts.filter { part ->
            !part.startsWith("Mandat:", ignoreCase = true) &&
            !part.startsWith("Referenz:", ignoreCase = true) &&
            !isPageFooter(part)
        }
        return if (relevantParts.isNotEmpty()) {
            "$type: ${relevantParts.joinToString(" ")}"
        } else {
            type
        }
    }

    private fun extractCounterparty(type: String, firstDescPart: String): String? {
        // For Lastschrift/Gutschrift, the first part usually contains the company name
        return firstDescPart.takeIf { it.isNotBlank() }?.let {
            // Clean up common suffixes
            it.replace(Regex("\\s+(GmbH|AG|e\\.V\\.|KG|OHG).*", RegexOption.IGNORE_CASE), " $1")
                .trim()
                .takeIf { name -> name.length > 2 }
        }
    }

    private fun isPageFooter(line: String): Boolean {
        return line.contains("Seite") && line.contains("von") ||
               line.contains("ING-DiBa AG") ||
               line.contains("www.ing.de") ||
               line.contains("Theodor-Heuss-Allee")
    }
}
